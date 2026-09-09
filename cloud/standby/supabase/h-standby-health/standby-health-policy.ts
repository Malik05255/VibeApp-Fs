export const MAX_REPLICATION_LAG_SECONDS = 120;
export const MAX_REPLICATION_OBSERVATION_AGE_MS = 180_000;

export type StandbyHealthInput = {
  runtime: Record<string, unknown>;
  replication: Record<string, unknown>;
  replicationObservedAt: string | null;
};

export type StandbyHealthDecision = {
  standbyReady: boolean;
  runtimeRole: string;
  hIdentity: string;
  promoted: boolean;
  dedicatedStandby: boolean;
  replicaWritesEnabled: boolean;
  restoreVerified: boolean;
  replicationMode: string;
  replicationProtocol: string;
  replicationFresh: boolean;
  replicationLagSeconds: number | null;
  replicationObservedAt: string | null;
};

export function evaluateStandbyHealth(input: StandbyHealthInput, now = Date.now()): StandbyHealthDecision {
  const runtime = input.runtime ?? {};
  const replication = input.replication ?? {};
  const runtimeRole = boundedString(runtime.runtime_role, 32) ?? "unknown";
  const hIdentity = boundedString(runtime.h_identity, 32) ?? "unknown";
  const promoted = runtime.promoted === true;
  const dedicatedStandby = runtime.dedicated_h_standby === true;
  const replicaWritesEnabled = runtime.allow_replica_writes === true;

  const replicationMode = boundedString(replication.mode, 32) ?? "none";
  const replicationProtocol = boundedString(replication.protocol, 64) ?? "none";
  const exactMirror = replication.exact_mirror === true;
  const digest = boundedString(replication.last_digest, 128);
  const sourceGeneratedAt = boundedString(replication.source_generated_at, 80);
  const storedLag = finiteNonNegative(replication.lag_seconds);
  const replicationLagSeconds = effectiveReplicationLagSeconds(sourceGeneratedAt, storedLag, now);
  const observationFresh = recentIso(input.replicationObservedAt, MAX_REPLICATION_OBSERVATION_AGE_MS, now);
  const restoreVerified = exactMirror && Boolean(digest && /^[0-9a-f]{64}$/i.test(digest));
  const replicationFresh = replicationMode === "continuous" &&
    replicationProtocol === "exact_mirror_v1" &&
    restoreVerified &&
    observationFresh &&
    replicationLagSeconds != null &&
    replicationLagSeconds <= MAX_REPLICATION_LAG_SECONDS;

  const standbyReady = runtimeRole === "standby" &&
    hIdentity === "H" &&
    dedicatedStandby &&
    replicaWritesEnabled &&
    !promoted &&
    replicationFresh;

  return {
    standbyReady,
    runtimeRole,
    hIdentity,
    promoted,
    dedicatedStandby,
    replicaWritesEnabled,
    restoreVerified,
    replicationMode,
    replicationProtocol,
    replicationFresh,
    replicationLagSeconds,
    replicationObservedAt: input.replicationObservedAt,
  };
}

export function effectiveReplicationLagSeconds(
  sourceGeneratedAt: string | null,
  storedLag: number | null,
  now = Date.now(),
): number | null {
  let liveLag: number | null = null;
  if (sourceGeneratedAt) {
    const generatedMs = Date.parse(sourceGeneratedAt);
    if (Number.isFinite(generatedMs) && generatedMs <= now + 5 * 60_000) {
      liveLag = Math.max(0, (now - generatedMs) / 1000);
    }
  }
  if (liveLag == null) return storedLag;
  if (storedLag == null) return liveLag;
  return Math.max(liveLag, storedLag);
}

function recentIso(value: string | null, maxAgeMs: number, now: number): boolean {
  if (!value) return false;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && parsed <= now + 5_000 && now - parsed <= maxAgeMs;
}

function finiteNonNegative(value: unknown): number | null {
  const number = Number(value);
  return Number.isFinite(number) && number >= 0 ? number : null;
}

function boundedString(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text ? text.slice(0, max) : null;
}

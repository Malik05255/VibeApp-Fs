import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const FUNCTION_NAME = "h-standby-health";
const MAX_REPLICATION_LAG_SECONDS = 120;
const MAX_REPLICATION_OBSERVATION_AGE_MS = 180_000;

type DbClient = any;

type StateRow = {
  key: string;
  value: Record<string, unknown> | null;
  updated_at: string | null;
};

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return reply({ ok: false, error: "method_not_allowed" }, 405);

  const supabaseUrl = String(Deno.env.get("SUPABASE_URL") || "").trim();
  const serviceRole = String(Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "").trim();
  if (!supabaseUrl || !serviceRole) return reply({ ok: false, error: "runtime_unavailable" }, 500);

  const db = createClient(supabaseUrl, serviceRole, { auth: { persistSession: false } });
  const runtimeSecret = await loadRuntimeSecret(db).catch(() => "");
  const provided = String(req.headers.get("x-h-runtime-secret") || "").trim();
  if (!runtimeSecret || !provided || !constantTimeEqual(runtimeSecret, provided)) {
    return reply({ ok: false, error: "Unauthorized" }, 401);
  }

  try {
    const [runtimeRow, replicationRow] = await Promise.all([
      loadState(db, "standby_runtime"),
      loadState(db, "standby_replication"),
    ]);

    const runtime = runtimeRow?.value ?? {};
    const replication = replicationRow?.value ?? {};
    const runtimeRole = boundedString(runtime.runtime_role, 32) ?? "unknown";
    const hIdentity = boundedString(runtime.h_identity, 32) ?? "unknown";
    const promoted = runtime.promoted === true;
    const dedicated = runtime.dedicated_h_standby === true;
    const replicaWrites = runtime.allow_replica_writes === true;

    const replicationMode = boundedString(replication.mode, 32) ?? "none";
    const replicationProtocol = boundedString(replication.protocol, 64) ?? "none";
    const exactMirror = replication.exact_mirror === true;
    const digest = boundedString(replication.last_digest, 128);
    const sourceGeneratedAt = boundedString(replication.source_generated_at, 80);
    const observedAt = boundedString(replication.last_replicated_at, 80) ?? replicationRow?.updated_at ?? null;
    const storedLag = finiteNonNegative(replication.lag_seconds);
    const effectiveLag = effectiveReplicationLagSeconds(sourceGeneratedAt, storedLag);
    const observationFresh = recentIso(observedAt, MAX_REPLICATION_OBSERVATION_AGE_MS);
    const restoreVerified = exactMirror && Boolean(digest && /^[0-9a-f]{64}$/i.test(digest));
    const replicationFresh = replicationMode === "continuous" &&
      replicationProtocol === "exact_mirror_v1" &&
      restoreVerified &&
      observationFresh &&
      effectiveLag != null &&
      effectiveLag <= MAX_REPLICATION_LAG_SECONDS;

    const standbyReady = runtimeRole === "standby" &&
      hIdentity === "H" &&
      dedicated &&
      replicaWrites &&
      !promoted &&
      replicationFresh;

    return reply({
      ok: true,
      service: FUNCTION_NAME,
      checkedAt: new Date().toISOString(),
      standbyReady,
      runtimeRole,
      hIdentity,
      promoted,
      dedicatedStandby: dedicated,
      replicaWritesEnabled: replicaWrites,
      restoreVerified,
      replicationMode,
      replicationProtocol,
      replicationFresh,
      replicationLagSeconds: effectiveLag,
      replicationObservedAt: observedAt,
      rawMessageBodiesReplicated: false,
      conversationHistoryReplicated: false,
      providerCredentialsReplicated: false,
      runtimeSecretsReplicated: false,
      rawMediaReplicated: false,
    });
  } catch (error) {
    console.error(`${FUNCTION_NAME} failed`, compactErrorCode(error));
    return reply({ ok: false, error: "standby_health_failed" }, 500);
  }
});

async function loadRuntimeSecret(db: DbClient): Promise<string> {
  const { data, error } = await db.from("h_runtime_config")
    .select("secret_value")
    .eq("key", "poll_secret")
    .maybeSingle();
  if (error) throw error;
  const value = String(data?.secret_value || "").trim();
  if (!value) throw new Error("runtime_secret_missing");
  return value;
}

async function loadState(db: DbClient, key: string): Promise<StateRow | null> {
  const { data, error } = await db.from("h_runtime_state")
    .select("key,value,updated_at")
    .eq("key", key)
    .maybeSingle();
  if (error) throw error;
  if (!data) return null;
  const value = data.value && typeof data.value === "object" && !Array.isArray(data.value)
    ? data.value as Record<string, unknown>
    : {};
  return {
    key: String(data.key || key),
    value,
    updated_at: boundedString(data.updated_at, 80),
  };
}

export function effectiveReplicationLagSeconds(sourceGeneratedAt: string | null, storedLag: number | null, now = Date.now()): number | null {
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

function recentIso(value: string | null, maxAgeMs: number, now = Date.now()): boolean {
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

function constantTimeEqual(left: string, right: string): boolean {
  if (left.length !== right.length) return false;
  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left.charCodeAt(index) ^ right.charCodeAt(index);
  }
  return diff === 0;
}

function compactErrorCode(error: unknown): string {
  const raw = (error instanceof Error ? error.message : String(error || "unknown_error"))
    .toLowerCase()
    .replace(/[^a-z0-9_:-]+/g, "_");
  return raw.slice(0, 120) || "standby_health_failed";
}

function reply(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}

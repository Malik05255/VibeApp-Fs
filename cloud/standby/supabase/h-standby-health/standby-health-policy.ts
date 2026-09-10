export const MAX_REPLICATION_LAG_SECONDS = 120;
export const MAX_REPLICATION_OBSERVATION_AGE_MS = 180_000;
export const STANDBY_EXECUTION_CONTRACT = "h_standby_execution_v1";
export const STANDBY_REPLICATION_PROTOCOL = "exact_mirror_v2";

export type StandbyHealthInput = {
  runtime: Record<string, unknown>;
  replication: Record<string, unknown>;
  execution: Record<string, unknown>;
  replicationObservedAt: string | null;
};

export type StandbyHealthDecision = {
  standbyReady: boolean;
  runtimeRole: string;
  hIdentity: string;
  promoted: boolean;
  dedicatedStandby: boolean;
  replicaWritesEnabled: boolean;
  runtimeExecutionFlag: boolean;
  executionContractReady: boolean;
  executionRuntimeReady: boolean;
  executionContract: string;
  executionMode: string;
  coreSchemaReady: boolean;
  functionInventoryReady: boolean;
  runtimeSecretReady: boolean;
  appIdentityRekeyReady: boolean;
  whatsappIdentityRekeyReady: boolean;
  aiCredentialsRekeyReady: boolean;
  freeAiRouteReady: boolean;
  paidAiBudgetContinuityReady: boolean;
  promotionControlsReady: boolean;
  schedulerActive: boolean;
  autonomousOutboundActive: boolean;
  executionValidatedAt: string | null;
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
  const execution = input.execution ?? {};
  const runtimeRole = boundedString(runtime.runtime_role, 32) ?? "unknown";
  const hIdentity = boundedString(runtime.h_identity, 32) ?? "unknown";
  const promoted = runtime.promoted === true;
  const dedicatedStandby = runtime.dedicated_h_standby === true;
  const replicaWritesEnabled = runtime.allow_replica_writes === true;
  const runtimeExecutionFlag = runtime.execution_runtime_ready === true;

  const executionContract = boundedString(execution.contract, 64) ?? "none";
  const executionMode = boundedString(execution.mode, 32) ?? "none";
  const coreSchemaReady = execution.core_schema_ready === true;
  const functionInventoryReady = execution.function_inventory_ready === true;
  const runtimeSecretReady = execution.runtime_secret_ready === true;
  const appIdentityRekeyReady = execution.app_identity_rekey_ready === true;
  const whatsappIdentityRekeyReady = execution.whatsapp_identity_rekey_ready === true;
  const aiCredentialsRekeyReady = execution.ai_credentials_rekey_ready === true;
  const freeAiRouteReady = execution.free_ai_route_ready === true;
  const paidAiBudgetContinuityReady = execution.paid_ai_budget_continuity_ready === true;
  const promotionControlsReady = execution.promotion_controls_ready === true;
  const schedulerActive = execution.scheduler_active === true;
  const autonomousOutboundActive = execution.autonomous_outbound_active === true;
  const executionValidatedAt = boundedString(execution.validated_at, 80);
  const executionContractReady = executionContract === STANDBY_EXECUTION_CONTRACT &&
    executionMode === "passive_preflight" &&
    coreSchemaReady &&
    functionInventoryReady &&
    runtimeSecretReady &&
    appIdentityRekeyReady &&
    whatsappIdentityRekeyReady &&
    aiCredentialsRekeyReady &&
    freeAiRouteReady &&
    paidAiBudgetContinuityReady &&
    promotionControlsReady &&
    !schedulerActive &&
    !autonomousOutboundActive;
  const executionRuntimeReady = runtimeExecutionFlag && executionContractReady;

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
    replicationProtocol === STANDBY_REPLICATION_PROTOCOL &&
    restoreVerified &&
    observationFresh &&
    replicationLagSeconds != null &&
    replicationLagSeconds <= MAX_REPLICATION_LAG_SECONDS;

  const standbyReady = runtimeRole === "standby" &&
    hIdentity === "H" &&
    dedicatedStandby &&
    replicaWritesEnabled &&
    executionRuntimeReady &&
    !promoted &&
    replicationFresh;

  return {
    standbyReady,
    runtimeRole,
    hIdentity,
    promoted,
    dedicatedStandby,
    replicaWritesEnabled,
    runtimeExecutionFlag,
    executionContractReady,
    executionRuntimeReady,
    executionContract,
    executionMode,
    coreSchemaReady,
    functionInventoryReady,
    runtimeSecretReady,
    appIdentityRekeyReady,
    whatsappIdentityRekeyReady,
    aiCredentialsRekeyReady,
    freeAiRouteReady,
    paidAiBudgetContinuityReady,
    promotionControlsReady,
    schedulerActive,
    autonomousOutboundActive,
    executionValidatedAt,
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

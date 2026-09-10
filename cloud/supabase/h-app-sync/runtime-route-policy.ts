export type AppRuntimeRouteInput = {
  runtime?: Record<string, unknown> | null;
  execution?: Record<string, unknown> | null;
  replication?: Record<string, unknown> | null;
  promotion?: Record<string, unknown> | null;
};

export type AppRuntimeRouteDecision = {
  runtimeRole: "primary" | "standby" | "invalid";
  requestReady: boolean;
  requestOnlyActive: boolean;
  promoted: boolean;
  replicaWritesFenced: boolean;
  promotionAttested: boolean;
};

const REQUEST_ID_PATTERN = /^[A-Za-z0-9_-]{16,128}$/;
const DIGEST_PATTERN = /^[0-9a-f]{64}$/i;

/**
 * Owner-authenticated app routing policy.
 *
 * A primary H runtime normally has no `standby_runtime` state row and remains request-ready.
 * A dedicated standby is request-ready only after the request-only promotion protocol has
 * atomically fenced replica writes. Scheduler/autonomous outbound must remain disabled.
 */
export function evaluateAppRuntimeRoute(input: AppRuntimeRouteInput): AppRuntimeRouteDecision {
  const runtime = objectOrEmpty(input.runtime);
  const execution = objectOrEmpty(input.execution);
  const replication = objectOrEmpty(input.replication);
  const promotion = objectOrEmpty(input.promotion);
  const runtimeRole = text(runtime.runtime_role);

  if (!runtimeRole || runtimeRole === "primary") {
    return {
      runtimeRole: "primary",
      requestReady: true,
      requestOnlyActive: false,
      promoted: false,
      replicaWritesFenced: false,
      promotionAttested: false,
    };
  }

  if (runtimeRole !== "standby") {
    return invalid();
  }

  const promoted = runtime.promoted === true;
  const replicaWritesFenced = runtime.allow_replica_writes === false;
  const requestId = text(runtime.promotion_request_id);
  const promotionRequestId = text(promotion.request_id);
  const sourceDigest = text(promotion.source_digest);
  const replicatedDigest = text(replication.last_digest);

  const promotionAttested =
    text(promotion.protocol) === "h_standby_promotion_v1" &&
    text(promotion.status) === "active" &&
    text(promotion.mode) === "request_only" &&
    Boolean(requestId && REQUEST_ID_PATTERN.test(requestId)) &&
    requestId === promotionRequestId &&
    text(runtime.promotion_mode) === "request_only" &&
    Boolean(sourceDigest && DIGEST_PATTERN.test(sourceDigest)) &&
    sourceDigest === replicatedDigest;

  const executionReady =
    text(runtime.h_identity) === "H" &&
    runtime.dedicated_h_standby === true &&
    runtime.execution_runtime_ready === true &&
    text(execution.contract) === "h_standby_execution_v1" &&
    text(execution.mode) === "request_active" &&
    execution.core_schema_ready === true &&
    execution.function_inventory_ready === true &&
    execution.runtime_secret_ready === true &&
    execution.app_identity_rekey_ready === true &&
    execution.whatsapp_identity_rekey_ready === true &&
    execution.ai_credentials_rekey_ready === true &&
    execution.free_ai_route_ready === true &&
    execution.paid_ai_budget_continuity_ready === true &&
    execution.promotion_controls_ready === true &&
    execution.execution_runtime_ready === true &&
    execution.scheduler_active !== true &&
    execution.autonomous_outbound_active !== true;

  const requestOnlyActive = promoted && replicaWritesFenced && promotionAttested && executionReady;
  return {
    runtimeRole: "standby",
    requestReady: requestOnlyActive,
    requestOnlyActive,
    promoted,
    replicaWritesFenced,
    promotionAttested,
  };
}

function invalid(): AppRuntimeRouteDecision {
  return {
    runtimeRole: "invalid",
    requestReady: false,
    requestOnlyActive: false,
    promoted: false,
    replicaWritesFenced: false,
    promotionAttested: false,
  };
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized || null;
}

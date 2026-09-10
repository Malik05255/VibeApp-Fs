type DbClient = any;

export class HStandbyExecutionBlockedError extends Error {
  readonly code = "standby_not_request_active";

  constructor() {
    super("standby_not_request_active");
    this.name = "HStandbyExecutionBlockedError";
  }
}

/**
 * Primary runtimes are unaffected. A dedicated H standby may serve ordinary user execution
 * only after request-only promotion has fenced replica writes. Background scheduler and
 * autonomous outbound remain disabled even while request traffic is active.
 */
export async function assertHRequestExecutionAllowed(db: DbClient): Promise<void> {
  const { data: runtimeRow, error: runtimeError } = await db.from("h_runtime_state")
    .select("value")
    .eq("key", "standby_runtime")
    .maybeSingle();
  if (runtimeError) throw runtimeError;
  if (!runtimeRow) return;

  const runtime = objectOrEmpty(runtimeRow.value);
  if (runtime.runtime_role !== "standby") return;
  if (runtime.h_identity !== "H" || runtime.dedicated_h_standby !== true) {
    throw new HStandbyExecutionBlockedError();
  }

  const [{ data: executionRow, error: executionError }, { data: promotionRow, error: promotionError }] = await Promise.all([
    db.from("h_runtime_state").select("value").eq("key", "standby_execution").maybeSingle(),
    db.from("h_runtime_state").select("value").eq("key", "standby_promotion").maybeSingle(),
  ]);
  if (executionError) throw executionError;
  if (promotionError) throw promotionError;

  const execution = objectOrEmpty(executionRow?.value);
  const promotion = objectOrEmpty(promotionRow?.value);
  const runtimeRequestId = boundedString(runtime.promotion_request_id, 128);
  const promotionRequestId = boundedString(promotion.request_id, 128);
  const sourceDigest = boundedString(promotion.source_digest, 128);

  const allowed = runtime.promoted === true &&
    runtime.allow_replica_writes === false &&
    runtime.execution_runtime_ready === true &&
    runtime.promotion_mode === "request_only" &&
    Boolean(runtimeRequestId && /^[A-Za-z0-9_-]{16,128}$/.test(runtimeRequestId)) &&
    execution.contract === "h_standby_execution_v1" &&
    execution.mode === "request_active" &&
    execution.promotion_controls_ready === true &&
    execution.execution_runtime_ready === true &&
    execution.scheduler_active === false &&
    execution.autonomous_outbound_active === false &&
    promotion.protocol === "h_standby_promotion_v1" &&
    promotion.status === "active" &&
    promotion.mode === "request_only" &&
    promotionRequestId === runtimeRequestId &&
    Boolean(sourceDigest && /^[0-9a-f]{64}$/i.test(sourceDigest)) &&
    promotion.replica_writes_fenced === true &&
    promotion.scheduler_active === false &&
    promotion.autonomous_outbound_active === false;

  if (!allowed) throw new HStandbyExecutionBlockedError();
}

export function isHStandbyExecutionBlocked(error: unknown): boolean {
  return error instanceof HStandbyExecutionBlockedError ||
    (error instanceof Error && error.message === "standby_not_request_active");
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function boundedString(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const text = value.trim();
  return text ? text.slice(0, max) : null;
}

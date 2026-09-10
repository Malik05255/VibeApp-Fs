type DbClient = any;

export class HStandbyExecutionBlockedError extends Error {
  readonly code = "standby_not_request_active";
  constructor() {
    super("standby_not_request_active");
    this.name = "HStandbyExecutionBlockedError";
  }
}

/**
 * Primary runtimes are unaffected. A dedicated standby may serve user execution only after
 * request-only promotion has fenced replica writes. Scheduler/autonomous outbound must
 * remain disabled even while request traffic is active.
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

  const { data: executionRow, error: executionError } = await db.from("h_runtime_state")
    .select("value")
    .eq("key", "standby_execution")
    .maybeSingle();
  if (executionError) throw executionError;
  const execution = objectOrEmpty(executionRow?.value);

  const allowed = runtime.promoted === true &&
    runtime.allow_replica_writes === false &&
    runtime.execution_runtime_ready === true &&
    runtime.promotion_mode === "request_only" &&
    execution.contract === "h_standby_execution_v1" &&
    execution.mode === "request_active" &&
    execution.execution_runtime_ready === true &&
    execution.scheduler_active !== true &&
    execution.autonomous_outbound_active !== true;

  if (!allowed) throw new HStandbyExecutionBlockedError();
}

export function isHStandbyExecutionBlocked(error: unknown): boolean {
  return error instanceof HStandbyExecutionBlockedError ||
    (error instanceof Error && error.message === "standby_not_request_active");
}

function objectOrEmpty(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

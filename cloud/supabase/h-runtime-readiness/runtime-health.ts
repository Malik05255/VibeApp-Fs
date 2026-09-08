export type RuntimeStateRow = {
  key: string;
  value: unknown;
  updated_at?: string | null;
};

export type InboxSchedulerStatus = "missing" | "healthy" | "in_flight" | "stale";

export type InboxSchedulerHealth = {
  status: InboxSchedulerStatus;
  ready: boolean;
  policyReady: boolean;
  overlapGuard: boolean;
  freeOnly: boolean;
  dispatchCompleted: boolean;
  lastDispatchAt: string | null;
  lastCompletedPollAt: string | null;
  dispatchAgeSeconds: number | null;
  pollAgeSeconds: number | null;
};

const DISPATCH_RECENT_MS = 150_000;
const POLL_RECENT_MS = 150_000;
const IN_FLIGHT_GRACE_MS = 90_000;
const MAX_FUTURE_SKEW_MS = 5_000;

export function summarizeInboxScheduler(
  rows: RuntimeStateRow[],
  now: Date = new Date(),
): InboxSchedulerHealth {
  const scheduler = rows.find((row) => row.key === "inbox_scheduler");
  const poll = rows.find((row) => row.key === "inbox_poll");
  const schedulerValue = asRecord(scheduler?.value);
  const pollValue = asRecord(poll?.value);

  const dispatchMs = parseTimestamp(schedulerValue?.last_dispatch_at);
  const pollMs = parseTimestamp(pollValue?.last_poll_at);
  const nowMs = now.getTime();
  const dispatchAgeMs = ageMs(nowMs, dispatchMs);
  const pollAgeMs = ageMs(nowMs, pollMs);
  const overlapGuard = schedulerValue?.overlap_guard === true;
  const freeOnly = schedulerValue?.free_only === true;
  const policyReady = overlapGuard && freeOnly;
  const dispatchCompleted = dispatchMs != null && pollMs != null && pollMs >= dispatchMs;
  const dispatchRecent = isRecent(dispatchAgeMs, DISPATCH_RECENT_MS);
  const pollRecent = isRecent(pollAgeMs, POLL_RECENT_MS);
  const inFlight = Boolean(
    dispatchMs != null &&
      dispatchRecent &&
      !dispatchCompleted &&
      dispatchAgeMs != null &&
      dispatchAgeMs <= IN_FLIGHT_GRACE_MS,
  );

  let status: InboxSchedulerStatus;
  if (!scheduler || dispatchMs == null) {
    status = "missing";
  } else if (policyReady && inFlight) {
    status = "in_flight";
  } else if (policyReady && dispatchRecent && pollRecent && dispatchCompleted) {
    status = "healthy";
  } else {
    status = "stale";
  }

  return {
    status,
    ready: status === "healthy" || status === "in_flight",
    policyReady,
    overlapGuard,
    freeOnly,
    dispatchCompleted,
    lastDispatchAt: toIso(dispatchMs),
    lastCompletedPollAt: toIso(pollMs),
    dispatchAgeSeconds: toSeconds(dispatchAgeMs),
    pollAgeSeconds: toSeconds(pollAgeMs),
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function parseTimestamp(value: unknown): number | null {
  if (typeof value !== "string" || !value.trim()) return null;
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function ageMs(nowMs: number, timestampMs: number | null): number | null {
  return timestampMs == null ? null : nowMs - timestampMs;
}

function isRecent(age: number | null, limit: number): boolean {
  return age != null && age >= -MAX_FUTURE_SKEW_MS && age <= limit;
}

function toIso(timestampMs: number | null): string | null {
  return timestampMs == null ? null : new Date(timestampMs).toISOString();
}

function toSeconds(age: number | null): number | null {
  return age == null ? null : Math.max(0, Math.round(age / 1000));
}

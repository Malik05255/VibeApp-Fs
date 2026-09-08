import { summarizeInboxScheduler, type RuntimeStateRow } from "./runtime-health.ts";

const NOW = new Date("2026-09-08T23:10:00.000Z");

Deno.test("scheduler is healthy after a recent completed guarded dispatch", () => {
  const health = summarizeInboxScheduler(rows(
    "2026-09-08T23:09:00.000Z",
    "2026-09-08T23:09:00.800Z",
  ), NOW);

  if (health.status !== "healthy" || !health.ready || !health.dispatchCompleted) {
    throw new Error(`unexpected health: ${JSON.stringify(health)}`);
  }
});

Deno.test("scheduler reports a recent guarded dispatch as in flight", () => {
  const health = summarizeInboxScheduler(rows(
    "2026-09-08T23:09:40.000Z",
    "2026-09-08T23:08:00.000Z",
  ), NOW);

  if (health.status !== "in_flight" || !health.ready || health.dispatchCompleted) {
    throw new Error(`unexpected health: ${JSON.stringify(health)}`);
  }
});

Deno.test("scheduler becomes stale when a dispatch never completes past grace", () => {
  const health = summarizeInboxScheduler(rows(
    "2026-09-08T23:08:20.000Z",
    "2026-09-08T23:07:00.000Z",
  ), NOW);

  if (health.status !== "stale" || health.ready) {
    throw new Error(`unexpected health: ${JSON.stringify(health)}`);
  }
});

Deno.test("scheduler fails readiness when safety policy flags are absent", () => {
  const health = summarizeInboxScheduler([
    {
      key: "inbox_scheduler",
      value: {
        last_dispatch_at: "2026-09-08T23:09:00.000Z",
        overlap_guard: false,
        free_only: true,
      },
    },
    {
      key: "inbox_poll",
      value: { last_poll_at: "2026-09-08T23:09:00.800Z" },
    },
  ], NOW);

  if (health.status !== "stale" || health.ready || health.policyReady) {
    throw new Error(`unexpected health: ${JSON.stringify(health)}`);
  }
});

Deno.test("scheduler reports missing when no dispatch state exists", () => {
  const health = summarizeInboxScheduler([], NOW);
  if (health.status !== "missing" || health.ready) {
    throw new Error(`unexpected health: ${JSON.stringify(health)}`);
  }
});

function rows(lastDispatchAt: string, lastPollAt: string): RuntimeStateRow[] {
  return [
    {
      key: "inbox_scheduler",
      value: {
        last_dispatch_at: lastDispatchAt,
        overlap_guard: true,
        free_only: true,
      },
    },
    {
      key: "inbox_poll",
      value: { last_poll_at: lastPollAt },
    },
  ];
}

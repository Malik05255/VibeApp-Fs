import { evaluatePeachReadiness } from "./readiness-policy.ts";

function assert(condition: unknown, message = "assertion failed"): asserts condition {
  if (!condition) throw new Error(message);
}

Deno.test("Peach owner messaging is ready only when credential scheduler and owner are ready", () => {
  const now = Date.parse("2026-09-08T23:20:00Z");
  const ready = evaluatePeachReadiness({
    accessTokenPresent: true,
    refreshTokenPresent: true,
    expiresAt: "2026-09-08T23:00:00Z",
    ownerIdentityCount: 1,
    schedulerCadence: "every_minute",
    schedulerOverlapGuard: true,
    schedulerUpdatedAt: "2026-09-08T23:19:30Z",
    pollUpdatedAt: "2026-09-08T23:19:31Z",
    nowMs: now,
  });
  assert(ready.peachCredentialReady);
  assert(ready.schedulerConfigured);
  assert(ready.schedulerRecent);
  assert(ready.peachPollingReady);
  assert(ready.peachOwnerMessagingReady);
});

Deno.test("Peach readiness fails closed when owner is not enrolled", () => {
  const now = Date.parse("2026-09-08T23:20:00Z");
  const value = evaluatePeachReadiness({
    accessTokenPresent: true,
    refreshTokenPresent: false,
    expiresAt: "2026-09-09T00:00:00Z",
    ownerIdentityCount: 0,
    schedulerCadence: "every_minute",
    schedulerOverlapGuard: true,
    schedulerUpdatedAt: "2026-09-08T23:19:30Z",
    pollUpdatedAt: "2026-09-08T23:19:31Z",
    nowMs: now,
  });
  assert(value.peachPollingReady);
  assert(!value.ownerIdentityConfigured);
  assert(!value.peachOwnerMessagingReady);
});

Deno.test("stale scheduler blocks Peach polling readiness", () => {
  const now = Date.parse("2026-09-08T23:20:00Z");
  const value = evaluatePeachReadiness({
    accessTokenPresent: true,
    refreshTokenPresent: true,
    expiresAt: null,
    ownerIdentityCount: 1,
    schedulerCadence: "every_minute",
    schedulerOverlapGuard: true,
    schedulerUpdatedAt: "2026-09-08T23:10:00Z",
    pollUpdatedAt: "2026-09-08T23:10:00Z",
    nowMs: now,
  });
  assert(!value.schedulerRecent);
  assert(!value.peachPollingReady);
  assert(!value.peachOwnerMessagingReady);
});

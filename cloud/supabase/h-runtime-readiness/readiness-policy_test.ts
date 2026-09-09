import { evaluateAppCloudReadiness, evaluatePeachReadiness } from "./readiness-policy.ts";

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

Deno.test("app cloud readiness distinguishes healthy infrastructure from an unlinked owner", () => {
  const value = evaluateAppCloudReadiness({
    appIdentityCount: 0,
    appIdentityStateReadable: true,
    encryptedPairingHandoffReadable: true,
    mediaCredentialCount: 1,
    mediaCredentialStateReadable: true,
    mediaStateReadable: true,
    mediaState: null,
  });

  assert(value.appLinkInfrastructureReady);
  assert(value.appCloudStateReadable);
  assert(!value.appOwnerLinked);
  assert(value.appLinkRequired);
  assert(!value.appCloudReady);
  assert(value.freeMediaCredentialConfigured);
  assert(value.appEphemeralMediaConfigured);
  assert(!value.appEphemeralMediaOwnerEligible);
  assert(!value.freeMediaStateObserved);
  assert(!value.appEphemeralMediaObservedReady);
});

Deno.test("linked owner with observed free-only media success is fully ready", () => {
  const value = evaluateAppCloudReadiness({
    appIdentityCount: 1,
    appIdentityStateReadable: true,
    encryptedPairingHandoffReadable: true,
    mediaCredentialCount: 1,
    mediaCredentialStateReadable: true,
    mediaStateReadable: true,
    mediaState: {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: true,
      selected_model: "openrouter/free",
    },
  });

  assert(value.appCloudReady);
  assert(!value.appLinkRequired);
  assert(value.appEphemeralMediaConfigured);
  assert(value.appEphemeralMediaOwnerEligible);
  assert(value.freeMediaStateObserved);
  assert(value.freeMediaLastReady);
  assert(value.appEphemeralMediaObservedReady);
});

Deno.test("media configuration remains visible when the last observed media attempt failed", () => {
  const value = evaluateAppCloudReadiness({
    appIdentityCount: 1,
    appIdentityStateReadable: true,
    encryptedPairingHandoffReadable: true,
    mediaCredentialCount: 1,
    mediaCredentialStateReadable: true,
    mediaStateReadable: true,
    mediaState: {
      connected: true,
      provider: "openrouter",
      free_only: true,
      ready: false,
      error: "no_strictly_zero_priced_audio_model",
    },
  });

  assert(value.appCloudReady);
  assert(value.appEphemeralMediaConfigured);
  assert(value.appEphemeralMediaOwnerEligible);
  assert(value.freeMediaStateObserved);
  assert(!value.freeMediaLastReady);
  assert(!value.appEphemeralMediaObservedReady);
});

Deno.test("app cloud readiness fails closed when encrypted pairing handoff schema is unreadable", () => {
  const value = evaluateAppCloudReadiness({
    appIdentityCount: 1,
    appIdentityStateReadable: true,
    encryptedPairingHandoffReadable: false,
    mediaCredentialCount: 1,
    mediaCredentialStateReadable: true,
    mediaStateReadable: true,
    mediaState: null,
  });

  assert(!value.appCloudStateReadable);
  assert(!value.appLinkInfrastructureReady);
  assert(!value.appCloudReady);
  assert(!value.appLinkRequired);
  assert(!value.appEphemeralMediaConfigured);
  assert(!value.appEphemeralMediaOwnerEligible);
});

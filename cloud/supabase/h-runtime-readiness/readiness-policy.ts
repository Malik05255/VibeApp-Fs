export type PeachReadinessInput = {
  accessTokenPresent: boolean;
  refreshTokenPresent: boolean;
  expiresAt: string | null;
  ownerIdentityCount: number;
  schedulerCadence: unknown;
  schedulerOverlapGuard: unknown;
  schedulerUpdatedAt: string | null;
  pollUpdatedAt: string | null;
  nowMs?: number;
};

export type PeachReadiness = {
  peachCredentialReady: boolean;
  ownerIdentityConfigured: boolean;
  schedulerConfigured: boolean;
  schedulerRecent: boolean;
  peachPollingReady: boolean;
  peachOwnerMessagingReady: boolean;
};

export type AppCloudReadinessInput = {
  appIdentityCount: number;
  appIdentityStateReadable: boolean;
  encryptedPairingHandoffReadable: boolean;
  mediaCredentialCount: number;
  mediaCredentialStateReadable: boolean;
  mediaStateReadable: boolean;
  mediaState: unknown;
};

export type AppCloudReadiness = {
  appCloudStateReadable: boolean;
  appLinkInfrastructureReady: boolean;
  appOwnerLinked: boolean;
  appLinkRequired: boolean;
  appCloudReady: boolean;
  freeMediaCredentialStateReadable: boolean;
  freeMediaCredentialConfigured: boolean;
  freeMediaStateReadable: boolean;
  freeMediaStateObserved: boolean;
  freeMediaLastReady: boolean;
  appEphemeralMediaConfigured: boolean;
  appEphemeralMediaOwnerEligible: boolean;
  appEphemeralMediaObservedReady: boolean;
};

export function evaluatePeachReadiness(input: PeachReadinessInput): PeachReadiness {
  const nowMs = Number.isFinite(input.nowMs) ? Number(input.nowMs) : Date.now();
  const expiresMs = input.expiresAt ? Date.parse(input.expiresAt) : Number.NaN;
  const accessStillUsable = input.accessTokenPresent && (
    !Number.isFinite(expiresMs) || expiresMs > nowMs + 60_000 || input.refreshTokenPresent
  );
  const schedulerConfigured = input.schedulerCadence === "every_minute" && input.schedulerOverlapGuard === true;
  const schedulerAt = newestTimestamp(input.schedulerUpdatedAt, input.pollUpdatedAt);
  const schedulerRecent = schedulerAt != null && schedulerAt >= nowMs - 3 * 60_000;
  const ownerIdentityConfigured = Number(input.ownerIdentityCount) > 0;
  const peachCredentialReady = accessStillUsable;
  const peachPollingReady = peachCredentialReady && schedulerConfigured && schedulerRecent;

  return {
    peachCredentialReady,
    ownerIdentityConfigured,
    schedulerConfigured,
    schedulerRecent,
    peachPollingReady,
    peachOwnerMessagingReady: peachPollingReady && ownerIdentityConfigured,
  };
}

/**
 * Static app/cloud prerequisites are separated from observed media health on purpose.
 * A configured OpenRouter credential is not enough to claim media is operational, and a
 * healthy cloud service is not enough to claim the owner is linked from Android.
 */
export function evaluateAppCloudReadiness(input: AppCloudReadinessInput): AppCloudReadiness {
  const appCloudStateReadable = input.appIdentityStateReadable && input.encryptedPairingHandoffReadable;
  const appLinkInfrastructureReady = appCloudStateReadable;
  const appOwnerLinked = Number(input.appIdentityCount) > 0;
  const appCloudReady = appLinkInfrastructureReady && appOwnerLinked;

  const freeMediaCredentialStateReadable = input.mediaCredentialStateReadable;
  const freeMediaCredentialConfigured = freeMediaCredentialStateReadable && Number(input.mediaCredentialCount) > 0;
  const freeMediaStateReadable = input.mediaStateReadable;
  const mediaState = asRecord(input.mediaState);
  const freeMediaStateObserved = freeMediaStateReadable && mediaState != null;
  const freeMediaLastReady = freeMediaStateObserved && mediaState?.ready === true && mediaState?.free_only === true;

  const appEphemeralMediaConfigured = appLinkInfrastructureReady && freeMediaCredentialConfigured;
  const appEphemeralMediaOwnerEligible = appCloudReady && appEphemeralMediaConfigured;
  const appEphemeralMediaObservedReady = appEphemeralMediaOwnerEligible && freeMediaLastReady;

  return {
    appCloudStateReadable,
    appLinkInfrastructureReady,
    appOwnerLinked,
    appLinkRequired: appLinkInfrastructureReady && !appOwnerLinked,
    appCloudReady,
    freeMediaCredentialStateReadable,
    freeMediaCredentialConfigured,
    freeMediaStateReadable,
    freeMediaStateObserved,
    freeMediaLastReady,
    appEphemeralMediaConfigured,
    appEphemeralMediaOwnerEligible,
    appEphemeralMediaObservedReady,
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  return value as Record<string, unknown>;
}

function newestTimestamp(...values: Array<string | null>): number | null {
  let newest: number | null = null;
  for (const value of values) {
    if (!value) continue;
    const parsed = Date.parse(value);
    if (!Number.isFinite(parsed)) continue;
    newest = newest == null ? parsed : Math.max(newest, parsed);
  }
  return newest;
}

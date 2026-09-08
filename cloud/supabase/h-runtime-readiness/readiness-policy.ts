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

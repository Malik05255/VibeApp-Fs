export type HEphemeralMediaKind = "image" | "audio" | "video" | "pdf" | "text";
export type HEphemeralMediaSource = "app" | "whatsapp";

export type HEphemeralMediaStrategy =
  | "local_derived"
  | "remote_reference"
  | "inline_free_helper"
  | "temporary_cloud"
  | "reject";

export type HEphemeralMediaCapabilities = {
  /** Can this channel/device derive the useful representation without cloud raw-media upload? */
  localDerivation: boolean;
  /** Does the source already expose a short-lived URL that a helper can consume directly? */
  remoteReference: boolean;
  /** Is a strictly zero-priced helper currently verified for this input kind? */
  inlineFreeHelper: boolean;
  /** Is private temporary object storage currently inside H's verified no-cost budget? */
  temporaryCloudFree: boolean;
};

export type HEphemeralMediaInput = {
  kind: HEphemeralMediaKind;
  source: HEphemeralMediaSource;
  sizeBytes: number;
  durationMs?: number | null;
};

export type HEphemeralMediaDecision = {
  allowed: boolean;
  strategy: HEphemeralMediaStrategy;
  reason: string;
  rawCloudUploadAllowed: boolean;
  rawDurablePersistenceAllowed: false;
  mustDeleteTemporaryRaw: true;
  maxRawCloudLifetimeSeconds: number;
};

/**
 * H attachment cost/privacy policy.
 *
 * This function deliberately knows nothing about provider names. It chooses only a
 * processing shape. The caller must separately verify that any remote helper/storage
 * route is genuinely no-cost before setting the corresponding capability to true.
 *
 * Raw media is never durable. Paid fallback is not a capability and therefore cannot
 * be selected by this policy.
 */
export function chooseEphemeralMediaStrategy(
  input: HEphemeralMediaInput,
  capabilities: HEphemeralMediaCapabilities,
): HEphemeralMediaDecision {
  const normalized = normalizeInput(input);
  if (!normalized) return reject("invalid_media_metadata");

  const { kind, sizeBytes, durationMs } = normalized;
  const timed = kind === "audio" || kind === "video";
  const rawCloudDurationAllowed = !timed || (durationMs != null && durationMs <= MAX_RAW_CLOUD_MEDIA_DURATION_MS);

  // Local derivation is always the first choice when it can eliminate raw cloud upload.
  // For long audio/video this is the only acceptable way to continue without asking the
  // user to trim the source: derive a transcript/key frames locally and discard the raw.
  if (capabilities.localDerivation) {
    return allow("local_derived", "local_derivation_preferred", false);
  }

  if (timed && durationMs == null) {
    return reject("duration_required_before_raw_cloud_processing");
  }

  if (timed && !rawCloudDurationAllowed) {
    return reject("raw_audio_video_over_180_seconds");
  }

  // Text should be decoded/chunked before remote processing. Sending a large raw text
  // object to storage is wasteful and makes retention harder to reason about.
  if (kind === "text") {
    return reject("text_requires_local_chunk_derivation");
  }

  if (sizeBytes > maxRawBytesForKind(kind)) {
    return reject("raw_media_exceeds_free_continuity_size_budget");
  }

  // A provider/source-owned short-lived reference avoids duplicating bytes into H cloud.
  if (capabilities.remoteReference) {
    return allow("remote_reference", "source_ephemeral_reference_preferred", false);
  }

  // Inline strictly-free helper processing is preferable to creating an H storage object.
  if (capabilities.inlineFreeHelper) {
    return allow("inline_free_helper", "strictly_free_inline_processing", false);
  }

  // Temporary H storage is the last automatic option and only when the storage budget is
  // explicitly known to remain no-cost. The object still needs immediate finally-cleanup.
  if (capabilities.temporaryCloudFree) {
    return allow("temporary_cloud", "verified_free_temporary_cloud", true);
  }

  return reject("no_verified_no_cost_processing_route");
}

export function maxRawBytesForKind(kind: HEphemeralMediaKind): number {
  switch (kind) {
    case "image": return 8 * 1024 * 1024;
    case "audio": return 8 * 1024 * 1024;
    case "video": return 8 * 1024 * 1024;
    case "pdf": return 8 * 1024 * 1024;
    case "text": return 512 * 1024;
  }
}

export function maxRawCloudMediaDurationMs(): number {
  return MAX_RAW_CLOUD_MEDIA_DURATION_MS;
}

function normalizeInput(input: HEphemeralMediaInput): HEphemeralMediaInput | null {
  if (!input || !Number.isFinite(input.sizeBytes) || input.sizeBytes <= 0) return null;
  const durationMs = input.durationMs == null ? null : Number(input.durationMs);
  if (durationMs != null && (!Number.isFinite(durationMs) || durationMs <= 0)) return null;
  return {
    kind: input.kind,
    source: input.source,
    sizeBytes: Math.trunc(input.sizeBytes),
    durationMs: durationMs == null ? null : Math.trunc(durationMs),
  };
}

function allow(
  strategy: Exclude<HEphemeralMediaStrategy, "reject">,
  reason: string,
  rawCloudUploadAllowed: boolean,
): HEphemeralMediaDecision {
  return {
    allowed: true,
    strategy,
    reason,
    rawCloudUploadAllowed,
    rawDurablePersistenceAllowed: false,
    mustDeleteTemporaryRaw: true,
    maxRawCloudLifetimeSeconds: MAX_RAW_CLOUD_LIFETIME_SECONDS,
  };
}

function reject(reason: string): HEphemeralMediaDecision {
  return {
    allowed: false,
    strategy: "reject",
    reason,
    rawCloudUploadAllowed: false,
    rawDurablePersistenceAllowed: false,
    mustDeleteTemporaryRaw: true,
    maxRawCloudLifetimeSeconds: MAX_RAW_CLOUD_LIFETIME_SECONDS,
  };
}

const MAX_RAW_CLOUD_MEDIA_DURATION_MS = 180_000;
// Defense-in-depth TTL only. Normal processing must delete immediately in finally.
const MAX_RAW_CLOUD_LIFETIME_SECONDS = 10 * 60;

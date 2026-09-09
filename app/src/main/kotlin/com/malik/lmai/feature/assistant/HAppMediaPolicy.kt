package com.malik.lmai.feature.assistant

enum class HAppMediaKind {
    IMAGE,
    PDF,
    TEXT,
    AUDIO,
    VIDEO,
    UNSUPPORTED,
}

data class HAppMediaDecision(
    val kind: HAppMediaKind,
    val allowed: Boolean,
    val localTextDerivation: Boolean = false,
    val transientCloudAllowed: Boolean = false,
    val localImageReductionRecommended: Boolean = false,
    val localVideoFramesRecommended: Boolean = false,
    val reason: String,
)

/**
 * Android half of H's free-continuity attachment policy.
 *
 * Raw attachments are never durable H memory. Text is derived locally. Raw cloud media
 * is bounded to 8 MiB and audio/video to three minutes. Oversized images can be reduced
 * locally, and videos that cannot be sent raw can be reduced to local key frames.
 * The app picker may be broad (all MIME types); this policy remains the authoritative allow-list.
 */
object HAppMediaPolicy {
    const val MAX_RAW_MEDIA_BYTES: Long = 8L * 1024L * 1024L
    const val MAX_TEXT_BYTES: Long = 512L * 1024L
    const val MAX_RAW_AUDIO_VIDEO_DURATION_MS: Long = 180_000L

    private val imageTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )
    private val textTypes = setOf(
        "text/plain",
        "text/csv",
        "text/markdown",
        "text/xml",
        "application/json",
        "application/xml",
        "application/csv",
    )
    private val audioTypes = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/wav",
        "audio/x-wav",
        "audio/flac",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "audio/webm",
    )
    private val videoTypes = setOf(
        "video/mp4",
        "video/mpeg",
        "video/quicktime",
        "video/webm",
    )

    fun classify(mimeType: String): HAppMediaKind {
        val normalized = mimeType.substringBefore(';').trim().lowercase()
        return when {
            normalized in imageTypes -> HAppMediaKind.IMAGE
            normalized == "application/pdf" -> HAppMediaKind.PDF
            normalized in textTypes -> HAppMediaKind.TEXT
            normalized in audioTypes -> HAppMediaKind.AUDIO
            normalized in videoTypes -> HAppMediaKind.VIDEO
            else -> HAppMediaKind.UNSUPPORTED
        }
    }

    fun isSupportedMimeType(mimeType: String): Boolean =
        classify(mimeType) != HAppMediaKind.UNSUPPORTED

    fun decide(
        mimeType: String,
        sizeBytes: Long,
        durationMs: Long? = null,
    ): HAppMediaDecision {
        val kind = classify(mimeType)
        if (sizeBytes <= 0L) {
            return reject(kind, "invalid_media_size")
        }

        if (kind == HAppMediaKind.UNSUPPORTED) {
            return reject(kind, "unsupported_media_type")
        }

        if (kind == HAppMediaKind.TEXT) {
            return if (sizeBytes <= MAX_TEXT_BYTES) {
                HAppMediaDecision(
                    kind = kind,
                    allowed = true,
                    localTextDerivation = true,
                    reason = "local_text_derivation",
                )
            } else {
                reject(kind, "text_exceeds_local_derivation_budget")
            }
        }

        if (kind == HAppMediaKind.IMAGE && sizeBytes > MAX_RAW_MEDIA_BYTES) {
            return HAppMediaDecision(
                kind = kind,
                allowed = true,
                localImageReductionRecommended = true,
                reason = "reduce_image_locally_before_cloud",
            )
        }

        if (kind == HAppMediaKind.VIDEO && sizeBytes > MAX_RAW_MEDIA_BYTES) {
            return HAppMediaDecision(
                kind = kind,
                allowed = true,
                localVideoFramesRecommended = true,
                reason = "derive_video_frames_locally",
            )
        }

        if (sizeBytes > MAX_RAW_MEDIA_BYTES) {
            return reject(kind, "raw_media_exceeds_free_continuity_size_budget")
        }

        if (kind == HAppMediaKind.AUDIO || kind == HAppMediaKind.VIDEO) {
            if (durationMs == null || durationMs <= 0L) {
                return reject(kind, "duration_required_before_raw_cloud_processing")
            }
            if (durationMs > MAX_RAW_AUDIO_VIDEO_DURATION_MS) {
                return if (kind == HAppMediaKind.VIDEO) {
                    HAppMediaDecision(
                        kind = kind,
                        allowed = true,
                        localVideoFramesRecommended = true,
                        reason = "derive_long_video_frames_locally",
                    )
                } else {
                    reject(kind, "raw_audio_over_180_seconds")
                }
            }
        }

        return HAppMediaDecision(
            kind = kind,
            allowed = true,
            transientCloudAllowed = true,
            reason = "bounded_transient_cloud_media",
        )
    }
}

private fun reject(kind: HAppMediaKind, reason: String) = HAppMediaDecision(
    kind = kind,
    allowed = false,
    reason = reason,
)

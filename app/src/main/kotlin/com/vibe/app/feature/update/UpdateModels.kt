package com.vibe.app.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class UpdateManifest(
    @SerialName("latestVersionCode") val versionCode: Int,
    val versionName: String,
    val minimumVersionCode: Int,
    val mandatory: Boolean,
    @SerialName("apkAsset") val apkAsset: String,
    @SerialName("sha256") val sha256: String,
    @Transient val resolvedApkUrl: String = "",
)

data class UpdateState(
    val checking: Boolean = true,
    val available: UpdateManifest? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
)

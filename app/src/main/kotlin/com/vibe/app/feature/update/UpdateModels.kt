package com.vibe.app.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val minimumVersionCode: Int,
    val mandatory: Boolean,
    val apkUrl: String,
    @SerialName("sha256") val sha256: String,
)

data class UpdateState(
    val checking: Boolean = true,
    val available: UpdateManifest? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
)

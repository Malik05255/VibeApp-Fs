package com.malik.lmai.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateManifest(
    @SerialName("latestVersionCode") val versionCode: Int,
    val versionName: String,
    val minimumVersionCode: Int,
    val mandatory: Boolean,
    @SerialName("apkAsset") val apkAsset: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("apkUrl") val apkUrl: String = "",
)

data class UpdateState(
    val checking: Boolean = true,
    val available: UpdateManifest? = null,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val error: String? = null,
)

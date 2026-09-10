package com.malik.lmai.feature.assistant

import java.net.URI

/** Pure validation/derivation for H's public runtime routing metadata. */
object HRuntimeRoutingPolicy {
    private val routableFunctions = setOf(
        "h-app-sync",
        "h-app-media",
        "h-reminder-sync",
        "h-portable-snapshot",
        "h-portable-restore",
    )

    fun normalizeStandbyBaseEndpoint(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.trim().orEmpty()
        if (uri.scheme?.lowercase() != "https") return null
        if (!host.endsWith(".supabase.co") || host.length <= ".supabase.co".length) return null
        if (!uri.userInfo.isNullOrBlank() || !uri.query.isNullOrBlank() || !uri.fragment.isNullOrBlank()) return null
        if (uri.port != -1 && uri.port != 443) return null
        val path = uri.path.orEmpty()
        if (path.isNotEmpty() && path != "/") return null
        return "https://$host"
    }

    fun standbyStatusUrl(baseEndpoint: String?): String? =
        normalizeStandbyBaseEndpoint(baseEndpoint)?.let { "$it/functions/v1/h-app-runtime-status" }

    fun standbyFunctionUrl(baseEndpoint: String?, primaryFunctionUrl: String): String? {
        val base = normalizeStandbyBaseEndpoint(baseEndpoint) ?: return null
        val uri = runCatching { URI(primaryFunctionUrl) }.getOrNull() ?: return null
        val path = uri.path.orEmpty()
        val prefix = "/functions/v1/"
        if (!path.startsWith(prefix)) return null
        val functionName = path.removePrefix(prefix)
        if (functionName !in routableFunctions || functionName.contains('/')) return null
        return "$base$prefix$functionName"
    }
}

package com.malik.lmai.feature.mcp

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PeachMcpOAuthCallbackBus {
    private val mutableCallbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 4)
    val callbacks: SharedFlow<Uri> = mutableCallbacks.asSharedFlow()

    fun publish(uri: Uri?) {
        uri ?: return

        val isLoopbackCallback =
            uri.scheme == "http" &&
                uri.host == "localhost" &&
                uri.path == PeachMcpLoopbackServer.CALLBACK_PATH

        val isLegacyAppCallback =
            uri.scheme == "lmai" &&
                uri.host == "peach-mcp-oauth" &&
                (uri.getQueryParameter("code") != null || uri.getQueryParameter("error") != null)

        if (isLoopbackCallback || isLegacyAppCallback) {
            mutableCallbacks.tryEmit(uri)
        }
    }
}

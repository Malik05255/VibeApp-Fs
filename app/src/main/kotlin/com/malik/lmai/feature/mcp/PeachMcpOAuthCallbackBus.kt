package com.malik.lmai.feature.mcp

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object PeachMcpOAuthCallbackBus {
    private val mutableCallbacks = MutableSharedFlow<Uri>(extraBufferCapacity = 4)
    val callbacks: SharedFlow<Uri> = mutableCallbacks.asSharedFlow()

    fun publish(uri: Uri?) {
        if (uri?.scheme == "lmai" && uri.host == "peach-mcp-oauth") {
            mutableCallbacks.tryEmit(uri)
        }
    }
}

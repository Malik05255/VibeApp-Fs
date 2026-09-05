package com.vibe.app.feature.ai.openrouter

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OpenRouterOAuthCallbackBus {
    private val _callback = MutableStateFlow<Uri?>(null)
    val callback: StateFlow<Uri?> = _callback.asStateFlow()

    fun publish(uri: Uri?) {
        if (uri?.scheme == CALLBACK_SCHEME && uri.host == CALLBACK_HOST) {
            _callback.value = uri
        }
    }

    fun consume(uri: Uri) {
        if (_callback.value == uri) _callback.value = null
    }

    const val CALLBACK_URI = "lmai://openrouter-oauth"
    private const val CALLBACK_SCHEME = "lmai"
    private const val CALLBACK_HOST = "openrouter-oauth"
}

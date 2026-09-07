package com.malik.lmai.feature.assistant

import android.content.Context
import com.malik.lmai.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Single owner identity used by every private H subsystem. */
@Singleton
class HOwnerIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val bootstrap by lazy {
        context.getSharedPreferences(CURRENT_BOOTSTRAP, Context.MODE_PRIVATE)
    }
    private val legacyBootstrap by lazy {
        context.getSharedPreferences(LEGACY_BOOTSTRAP, Context.MODE_PRIVATE)
    }

    fun currentOwnerKey(): String {
        val accountOwner = GoogleAccountSession.currentOwnerKey(context)
        if (accountOwner != GoogleAccountSession.LOCAL_OWNER_KEY) return accountOwner

        val currentId = bootstrap.getString(KEY_LOCAL_OWNER_ID, null)
            ?.takeIf { it.isNotBlank() }
        if (currentId != null) return "local:$currentId"

        // Preserve the local relationship id created before the assistant was renamed to H.
        val migratedId = legacyBootstrap.getString(KEY_LOCAL_OWNER_ID, null)
            ?.takeIf { it.isNotBlank() }
        val localId = migratedId ?: UUID.randomUUID().toString()
        bootstrap.edit().putString(KEY_LOCAL_OWNER_ID, localId).apply()
        return "local:$localId"
    }

    companion object {
        private const val CURRENT_BOOTSTRAP = "h_private_owner_bootstrap_v1"
        private const val LEGACY_BOOTSTRAP = "mohammed_private_bootstrap_v1"
        private const val KEY_LOCAL_OWNER_ID = "local_owner_id"
    }
}

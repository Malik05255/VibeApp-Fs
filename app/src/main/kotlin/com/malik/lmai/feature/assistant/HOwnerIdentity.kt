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
        context.getSharedPreferences("h_private_owner_bootstrap_v1", Context.MODE_PRIVATE)
    }

    fun currentOwnerKey(): String {
        val accountOwner = GoogleAccountSession.currentOwnerKey(context)
        if (accountOwner != GoogleAccountSession.LOCAL_OWNER_KEY) return accountOwner

        val localId = bootstrap.getString(KEY_LOCAL_OWNER_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also {
                bootstrap.edit().putString(KEY_LOCAL_OWNER_ID, it).apply()
            }
        return "local:$localId"
    }

    companion object {
        private const val KEY_LOCAL_OWNER_ID = "local_owner_id"
    }
}

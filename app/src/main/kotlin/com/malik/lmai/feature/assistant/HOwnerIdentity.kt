package com.malik.lmai.feature.assistant

import android.content.Context
import android.os.Build
import com.malik.lmai.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single owner identity used by every private H subsystem.
 *
 * A signed-in Google account is the only durable H owner identity. When Google is not
 * signed in, H gets a process-only local session id that is never written to Android
 * storage and therefore cannot become a second durable identity.
 */
@Singleton
class HOwnerIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val localSessionId = UUID.randomUUID().toString()

    init {
        purgeLegacyLocalOwnerPersistence()
    }

    fun currentOwnerKey(): String {
        val accountOwner = GoogleAccountSession.currentOwnerKey(context)
        return if (accountOwner != GoogleAccountSession.LOCAL_OWNER_KEY) {
            accountOwner
        } else {
            "local-session:$localSessionId"
        }
    }

    private fun purgeLegacyLocalOwnerPersistence() {
        listOf(CURRENT_BOOTSTRAP, LEGACY_BOOTSTRAP).forEach { name ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").delete()
            }
        }
    }

    companion object {
        private const val CURRENT_BOOTSTRAP = "h_private_owner_bootstrap_v1"
        private const val LEGACY_BOOTSTRAP = "mohammed_private_bootstrap_v1"
    }
}

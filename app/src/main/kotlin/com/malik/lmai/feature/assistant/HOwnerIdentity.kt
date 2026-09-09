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
        purgeLegacyLocalPersistence()
    }

    fun currentOwnerKey(): String {
        val accountOwner = GoogleAccountSession.currentOwnerKey(context)
        return if (accountOwner != GoogleAccountSession.LOCAL_OWNER_KEY) {
            accountOwner
        } else {
            "local-session:$localSessionId"
        }
    }

    /** Removes all former H/Mohammed durable owner/profile stores during app startup. */
    private fun purgeLegacyLocalPersistence() {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val names = buildSet {
            add(CURRENT_BOOTSTRAP)
            add(LEGACY_OWNER_BOOTSTRAP)
            add(LEGACY_ASSISTANT_BOOTSTRAP)
            sharedPrefsDir.listFiles().orEmpty().forEach { file ->
                val name = file.name.removeSuffix(".xml")
                if (
                    name.startsWith(CURRENT_OWNER_PREFIX) ||
                    name.startsWith(LEGACY_OWNER_PREFIX)
                ) {
                    add(name)
                }
            }
        }

        names.forEach { name ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(name)
            } else {
                context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                File(sharedPrefsDir, "$name.xml").delete()
            }
        }
    }

    companion object {
        private const val CURRENT_BOOTSTRAP = "h_private_owner_bootstrap_v1"
        private const val LEGACY_OWNER_BOOTSTRAP = "mohammed_private_bootstrap_v1"
        private const val LEGACY_ASSISTANT_BOOTSTRAP = "h_private_bootstrap_v1"
        private const val CURRENT_OWNER_PREFIX = "h_private_owner_v1_"
        private const val LEGACY_OWNER_PREFIX = "mohammed_private_owner_v1_"
    }
}

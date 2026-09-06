package com.malik.lmai.feature.assistant

import android.content.Context
import com.malik.lmai.BuildConfig
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * Private, owner-scoped personal context for the built-in assistant "محمد".
 *
 * Personal relationship state is stored only in this Android installation and is
 * partitioned by the current Google account (hashed) or by a stable local-install id.
 * Nothing in this class enumerates or merges other owners' state.
 */
@Singleton
class MohammedAssistantContext @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private val preferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Adds Mohammed's global identity + only the current owner's private memories to
     * the model request. A real user turn is learned at most once; tool iterations and
     * provider failover do not inflate relationship state.
     */
    fun prepare(request: AgentModelRequest): AgentModelRequest {
        val ownerKey = currentOwnerKey()
        val currentUserItem = request.conversation
            .lastOrNull { it.role == AgentMessageRole.USER }
            ?.takeIf { MohammedMemoryPolicy.isRealUserTurn(it.text.orEmpty()) }

        val relationship = synchronized(lock) {
            var state = readState(ownerKey)
            if (currentUserItem != null) {
                state = recordTurn(
                    ownerKey = ownerKey,
                    previous = state,
                    userText = currentUserItem.text.orEmpty(),
                    attachments = currentUserItem.attachments,
                )
            }
            state
        }

        val identity = MohammedIdentity(
            releaseName = BuildConfig.VERSION_NAME,
            generation = BuildConfig.VERSION_CODE.toLong(),
        )
        val account = GoogleAccountSession.get(context)
        val privateContext = MohammedContextBuilder.build(
            identity = identity,
            relationship = relationship,
            userDisplayName = account?.displayName,
            currentAttachmentCount = currentUserItem?.attachments?.size ?: 0,
        )

        val mergedInstructions = buildString {
            request.instructions
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    append(it)
                    append("\n\n")
                }
            append(privateContext)
        }

        return request.copy(instructions = mergedInstructions)
    }

    /** Deletes only the currently active owner's personal relationship/memory. */
    fun resetCurrentOwner() {
        val ownerKey = currentOwnerKey()
        synchronized(lock) {
            preferences.edit()
                .remove(MohammedOwnerScope.storageKey(ownerKey))
                .apply()
        }
    }

    /** Useful for privacy/settings UI without exposing any other owner's state. */
    fun currentRelationship(): MohammedRelationshipState =
        synchronized(lock) {
            readState(currentOwnerKey())
        }

    private fun currentOwnerKey(): String {
        val accountOwner = GoogleAccountSession.currentOwnerKey(context)
        if (accountOwner != GoogleAccountSession.LOCAL_OWNER_KEY) {
            return accountOwner
        }

        val localId = preferences.getString(KEY_LOCAL_OWNER_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit().putString(KEY_LOCAL_OWNER_ID, generated).apply()
            }

        return "local:$localId"
    }

    private fun recordTurn(
        ownerKey: String,
        previous: MohammedRelationshipState,
        userText: String,
        attachments: List<String>,
    ): MohammedRelationshipState {
        val semanticText = MohammedMemoryPolicy.semanticUserText(userText)
        val attachmentFingerprint = attachments
            .map { MohammedOwnerScope.fingerprint(it) }
            .sorted()
            .joinToString("|")
        val turnFingerprint = MohammedOwnerScope.fingerprint(
            semanticText + "\u0000" + attachmentFingerprint
        )

        if (turnFingerprint == previous.lastTurnFingerprint) {
            return previous
        }

        val now = System.currentTimeMillis()
        var memories = previous.memories
        MohammedMemoryPolicy.candidate(semanticText)?.let { candidate ->
            val normalizedCandidate = candidate
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
            val alreadyStored = memories.any { memory ->
                memory.text
                    .lowercase()
                    .replace(Regex("\\s+"), " ")
                    .trim() == normalizedCandidate
            }
            if (!alreadyStored) {
                memories = (memories + MohammedMemory(candidate, now))
                    .takeLast(MAX_MEMORIES)
            }
        }

        val updated = previous.copy(
            lastInteractionAtMs = now,
            turnCount = previous.turnCount + 1L,
            lastTurnFingerprint = turnFingerprint,
            memories = memories,
        )
        writeState(ownerKey, updated)
        return updated
    }

    private fun readState(ownerKey: String): MohammedRelationshipState {
        val key = MohammedOwnerScope.storageKey(ownerKey)
        val raw = preferences.getString(key, null)
        if (raw.isNullOrBlank()) {
            val now = System.currentTimeMillis()
            return MohammedRelationshipState(
                firstMetAtMs = now,
                lastInteractionAtMs = now,
                turnCount = 0L,
            )
        }

        return runCatching {
            val json = JSONObject(raw)
            val memoriesJson = json.optJSONArray(JSON_MEMORIES) ?: JSONArray()
            val memories = buildList {
                for (index in 0 until memoriesJson.length()) {
                    val item = memoriesJson.optJSONObject(index) ?: continue
                    val text = item.optString(JSON_MEMORY_TEXT).trim()
                    if (text.isBlank()) continue
                    add(
                        MohammedMemory(
                            text = text.take(280),
                            createdAtMs = item.optLong(JSON_MEMORY_CREATED_AT, 0L),
                        )
                    )
                }
            }.takeLast(MAX_MEMORIES)

            MohammedRelationshipState(
                firstMetAtMs = json.optLong(JSON_FIRST_MET_AT, System.currentTimeMillis()),
                lastInteractionAtMs = json.optLong(JSON_LAST_INTERACTION_AT, System.currentTimeMillis()),
                turnCount = json.optLong(JSON_TURN_COUNT, 0L).coerceAtLeast(0L),
                lastTurnFingerprint = json.optString(JSON_LAST_TURN_FINGERPRINT)
                    .takeIf { it.isNotBlank() },
                memories = memories,
            )
        }.getOrElse {
            val now = System.currentTimeMillis()
            MohammedRelationshipState(
                firstMetAtMs = now,
                lastInteractionAtMs = now,
                turnCount = 0L,
            )
        }
    }

    private fun writeState(
        ownerKey: String,
        state: MohammedRelationshipState,
    ) {
        val memoriesJson = JSONArray()
        state.memories.takeLast(MAX_MEMORIES).forEach { memory ->
            memoriesJson.put(
                JSONObject()
                    .put(JSON_MEMORY_TEXT, memory.text.take(280))
                    .put(JSON_MEMORY_CREATED_AT, memory.createdAtMs)
            )
        }

        val json = JSONObject()
            .put(JSON_FIRST_MET_AT, state.firstMetAtMs)
            .put(JSON_LAST_INTERACTION_AT, state.lastInteractionAtMs)
            .put(JSON_TURN_COUNT, state.turnCount)
            .put(JSON_LAST_TURN_FINGERPRINT, state.lastTurnFingerprint)
            .put(JSON_MEMORIES, memoriesJson)

        preferences.edit()
            .putString(MohammedOwnerScope.storageKey(ownerKey), json.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "mohammed_private_memory_v1"
        private const val KEY_LOCAL_OWNER_ID = "local_owner_id"
        private const val MAX_MEMORIES = 24

        private const val JSON_FIRST_MET_AT = "first_met_at"
        private const val JSON_LAST_INTERACTION_AT = "last_interaction_at"
        private const val JSON_TURN_COUNT = "turn_count"
        private const val JSON_LAST_TURN_FINGERPRINT = "last_turn_fingerprint"
        private const val JSON_MEMORIES = "memories"
        private const val JSON_MEMORY_TEXT = "text"
        private const val JSON_MEMORY_CREATED_AT = "created_at"
    }
}

package com.malik.lmai.feature.assistant

import android.content.Context
import android.os.Build
import com.malik.lmai.BuildConfig
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.presentation.ui.auth.GoogleAccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Private, owner-scoped personal context for the built-in assistant "المساعد الشخصي H".
 *
 * Durable H state belongs to the authenticated H Cloud owner identified by the signed-in
 * Google account. Android keeps only process-memory working state so reinstall, device loss,
 * backups, or filesystem inspection cannot become a second durable copy of H memory/learning.
 * Explicit durable memories and aggregate learning are synchronized through H Cloud; raw
 * conversation text and attachment contents are never persisted by this class.
 */
@Singleton
class HAssistantContext @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lock = Any()
    private val sessionStates = mutableMapOf<String, HRelationshipState>()

    init {
        purgeLegacyPersistentState()
    }

    /**
     * Adds H's global identity + only the current owner's private session memories and
     * adaptive profile to the model request. Cloud learning is hydrated by the H-owned
     * gateway before this method is called for an authenticated owner.
     */
    fun prepare(request: AgentModelRequest): AgentModelRequest {
        val ownerKey = currentOwnerKey()
        val currentUserItem = request.conversation
            .lastOrNull { it.role == AgentMessageRole.USER }
            ?.takeIf { HMemoryPolicy.isRealUserTurn(it.text.orEmpty()) }

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

        val identity = HIdentity(
            releaseName = BuildConfig.VERSION_NAME,
            generation = BuildConfig.VERSION_CODE.toLong(),
        )
        val account = GoogleAccountSession.get(context)
        val privateContext = HContextBuilder.build(
            identity = identity,
            relationship = relationship,
            userDisplayName = account?.displayName,
            currentAttachmentCount = currentUserItem?.attachments?.size ?: 0,
        )

        val mergedInstructions = buildString {
            request.instructions?.trim()?.takeIf { it.isNotBlank() }?.let {
                append(it)
                append("\n\n")
            }
            append(privateContext)
        }

        return request.copy(instructions = mergedInstructions)
    }

    /** Clears only RAM state for the active owner and removes any old H disk persistence. */
    fun resetCurrentOwner() {
        synchronized(lock) {
            sessionStates.remove(currentOwnerKey())
        }
        purgeLegacyPersistentState()
    }

    /** Useful for privacy/settings UI without exposing any other owner's state. */
    fun currentRelationship(): HRelationshipState =
        synchronized(lock) { readState(currentOwnerKey()) }

    /**
     * Returns only the portable aggregate learning fields safe for H Cloud sync.
     * Session memories, last-turn fingerprints, prompts and attachment data are excluded.
     */
    fun currentCloudLearningState(): HCloudLearningState = synchronized(lock) {
        readState(currentOwnerKey()).toCloudLearningState()
    }

    /**
     * Monotonically hydrates process-memory H learning from the linked owner's cloud state.
     * No hydrated value is written back to Android persistent storage.
     */
    fun mergeCloudLearningState(cloud: HCloudLearningState) {
        val ownerKey = currentOwnerKey()
        synchronized(lock) {
            val local = readState(ownerKey)
            val merged = local.toCloudLearningState().merge(cloud)
            val updated = local.copy(
                firstMetAtMs = merged.firstMetAtMs,
                lastInteractionAtMs = merged.lastInteractionAtMs,
                turnCount = merged.turnCount,
                adaptiveProfile = merged.toAdaptiveProfile(),
            )
            if (updated != local) writeState(ownerKey, updated)
        }
    }

    private fun HRelationshipState.toCloudLearningState(): HCloudLearningState {
        val profile = adaptiveProfile
        return HCloudLearningState(
            firstMetAtMs = firstMetAtMs.coerceAtLeast(1L),
            lastInteractionAtMs = lastInteractionAtMs.coerceAtLeast(firstMetAtMs.coerceAtLeast(1L)),
            turnCount = turnCount.coerceAtLeast(0L),
            directnessScore = profile.directnessScore,
            technicalDepthScore = profile.technicalDepthScore,
            programmingInterestScore = profile.programmingInterestScore,
            solutionBreadthScore = profile.solutionBreadthScore,
            arabicPreferenceScore = profile.arabicPreferenceScore,
            concisePreferenceScore = profile.concisePreferenceScore,
            codeReplacementPreferenceScore = profile.codeReplacementPreferenceScore,
            interactionSamples = profile.interactionSamples,
            interestTags = profile.interestTags,
        )
    }

    private fun HCloudLearningState.toAdaptiveProfile(): HAdaptiveProfile = HAdaptiveProfile(
        directnessScore = directnessScore.coerceIn(0, 20),
        technicalDepthScore = technicalDepthScore.coerceIn(0, 20),
        programmingInterestScore = programmingInterestScore.coerceIn(0, 20),
        solutionBreadthScore = solutionBreadthScore.coerceIn(0, 20),
        arabicPreferenceScore = arabicPreferenceScore.coerceIn(0, 20),
        concisePreferenceScore = concisePreferenceScore.coerceIn(0, 20),
        codeReplacementPreferenceScore = codeReplacementPreferenceScore.coerceIn(0, 20),
        interactionSamples = interactionSamples.coerceAtLeast(0L),
        interestTags = interestTags
            .filterKeys(HCloudLearningState.ALLOWED_INTEREST_TAGS::contains)
            .mapValues { (_, score) -> score.coerceIn(0, 1_000_000) }
            .filterValues { it > 0 },
    )

    private fun currentOwnerKey(): String = GoogleAccountSession.currentOwnerKey(context)

    private fun recordTurn(
        ownerKey: String,
        previous: HRelationshipState,
        userText: String,
        attachments: List<String>,
    ): HRelationshipState {
        val semanticText = HMemoryPolicy.semanticUserText(userText)
        val attachmentFingerprint = attachments
            .map { HOwnerScope.fingerprint(it) }
            .sorted()
            .joinToString("|")
        val turnFingerprint = HOwnerScope.fingerprint(
            semanticText + "\u0000" + attachmentFingerprint
        )

        if (turnFingerprint == previous.lastTurnFingerprint) {
            return previous
        }

        val now = System.currentTimeMillis()
        var memories = previous.memories
        HMemoryPolicy.candidate(semanticText)?.let { candidate ->
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
                // Automatic inferred memories are session-only. Durable memory requires
                // the explicit H Cloud remember path and its privacy validation.
                memories = (memories + HMemory(candidate, now)).takeLast(MAX_SESSION_MEMORIES)
            }
        }

        val adaptiveProfile = HAdaptiveLearner.learn(
            previous = previous.adaptiveProfile,
            rawText = semanticText,
        )

        val updated = previous.copy(
            lastInteractionAtMs = now,
            turnCount = previous.turnCount + 1L,
            lastTurnFingerprint = turnFingerprint,
            memories = memories,
            adaptiveProfile = adaptiveProfile,
        )
        writeState(ownerKey, updated)
        return updated
    }

    private fun readState(ownerKey: String): HRelationshipState =
        sessionStates.getOrPut(ownerKey) { emptyRelationship() }

    private fun writeState(ownerKey: String, state: HRelationshipState) {
        sessionStates[ownerKey] = state
    }

    private fun emptyRelationship(): HRelationshipState {
        val now = System.currentTimeMillis()
        return HRelationshipState(
            firstMetAtMs = now,
            lastInteractionAtMs = now,
            turnCount = 0L,
        )
    }

    /**
     * Deletes the former H/Mohammed SharedPreferences stores. This is intentionally
     * idempotent and does not write a migration marker, because such a marker would itself
     * become durable local H state.
     */
    private fun purgeLegacyPersistentState() {
        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val names = buildSet {
            add(BOOTSTRAP_PREFS_NAME)
            add(LEGACY_BOOTSTRAP_PREFS_NAME)
            sharedPrefsDir.listFiles().orEmpty().forEach { file ->
                val name = file.name.removeSuffix(".xml")
                if (
                    name.startsWith(OWNER_PREFS_PREFIX) ||
                    name.startsWith(LEGACY_OWNER_PREFS_PREFIX)
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
        private const val BOOTSTRAP_PREFS_NAME = "h_private_bootstrap_v1"
        private const val LEGACY_BOOTSTRAP_PREFS_NAME = "mohammed_private_bootstrap_v1"
        private const val OWNER_PREFS_PREFIX = "h_private_owner_v1_"
        private const val LEGACY_OWNER_PREFS_PREFIX = "mohammed_private_owner_v1_"
        private const val MAX_SESSION_MEMORIES = 24
    }
}

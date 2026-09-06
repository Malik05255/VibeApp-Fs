package com.malik.lmai.feature.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Independent offline inference path for محمد / مساعد H الرقمي.
 *
 * This does not use Gemini Nano or AICore. It loads an app-private Qwen2.5 0.5B
 * MediaPipe model downloaded by HLocalModelDownloadWorker. GPU is preferred for
 * responsive chat and automatically falls back to CPU on devices where GPU inference
 * is unavailable.
 */
@Singleton
class HMediaPipeAgentGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: HLocalModelManager,
) : AgentModelGateway {
    private val engineLock = Any()
    @Volatile private var engine: LlmInference? = null

    fun isReady(): Boolean = modelManager.isReady()

    fun schedulePreparation() = modelManager.scheduleBackgroundDownload()

    /** Load the verified model off the UI thread so the first chat turn avoids cold-start cost. */
    suspend fun warmUp() {
        if (!modelManager.isReady() || engine != null) return
        withContext(Dispatchers.IO) {
            runCatching { getOrCreateEngine() }
        }
    }

    override suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = callbackFlow {
        if (!modelManager.isReady()) {
            modelManager.scheduleBackgroundDownload()
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_MODEL_NOT_READY: local model is still being prepared"
                )
            )
            close()
            return@callbackFlow
        }

        if (request.tools.isNotEmpty()) {
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_TOOLS_UNAVAILABLE: project tools require a connected execution route"
                )
            )
            close()
            return@callbackFlow
        }

        if (request.fullConversation.lastOrNull { it.role == AgentMessageRole.USER }
                ?.attachments.orEmpty().isNotEmpty()
        ) {
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_IMAGE_UNAVAILABLE: image analysis requires a connected vision route"
                )
            )
            close()
            return@callbackFlow
        }

        val llm = try {
            withContext(Dispatchers.IO) { getOrCreateEngine() }
        } catch (error: Throwable) {
            modelManager.invalidate()
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_ENGINE_FAILED: ${error.message?.take(180) ?: "local engine failed"}"
                )
            )
            close()
            return@callbackFlow
        }

        val session = try {
            LlmInferenceSession.createFromOptions(
                llm,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(32)
                    .setTopP(0.9f)
                    .setTemperature(0.35f)
                    .build(),
            )
        } catch (error: Throwable) {
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_SESSION_FAILED: ${error.message?.take(180) ?: "local session failed"}"
                )
            )
            close()
            return@callbackFlow
        }

        val closed = AtomicBoolean(false)
        try {
            session.addQueryChunk(buildPrompt(request))
            val future = session.generateResponseAsync { partialResult, done ->
                if (partialResult.isNotEmpty()) {
                    trySend(AgentModelEvent.OutputDelta(partialResult))
                }
                if (done && closed.compareAndSet(false, true)) {
                    trySend(AgentModelEvent.Completed())
                    close()
                }
            }

            awaitClose {
                if (closed.compareAndSet(false, true)) {
                    runCatching { session.cancelGenerateResponseAsync() }
                    future.cancel(true)
                }
                runCatching { session.close() }
            }
        } catch (error: Throwable) {
            if (closed.compareAndSet(false, true)) {
                trySend(
                    AgentModelEvent.Failed(
                        "H_LOCAL_INFERENCE_FAILED: ${error.message?.take(180) ?: "local inference failed"}"
                    )
                )
            }
            runCatching { session.close() }
            close()
        }
    }

    private fun getOrCreateEngine(): LlmInference {
        engine?.let { return it }
        return synchronized(engineLock) {
            engine?.let { return@synchronized it }

            val created = runCatching {
                createEngine(LlmInference.Backend.GPU)
            }.getOrElse {
                createEngine(LlmInference.Backend.CPU)
            }

            created.also { engine = it }
        }
    }

    private fun createEngine(backend: LlmInference.Backend): LlmInference {
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelManager.modelFile.absolutePath)
            .setMaxTokens(1280)
            .setPreferredBackend(backend)
            .build()
        return LlmInference.createFromOptions(context, options)
    }

    private fun buildPrompt(request: AgentModelRequest): String {
        val systemBlock = buildString {
            append("<|im_start|>system\n")
            append("You are Mohammed, the user's long-running digital assistant. ")
            append("For ordinary chat, sound natural and human; do not mention programming unless the user raises a technical topic. ")
            append("If the user is venting, listen and engage before offering solutions. ")
            append("When the user asks about code or technology, behave as a precise senior cross-platform software engineer and put the useful diagnosis or corrected code early. ")
            append("Never claim code was built or tested unless it actually was. Reply in the user's language and register.\n")
            request.instructions
                ?.trim()
                ?.takeLast(MAX_PRIVATE_CONTEXT_CHARS)
                ?.takeIf { it.isNotBlank() }
                ?.let { append(it).append('\n') }
            append("<|im_end|>\n")
        }
        val assistantPrefix = "<|im_start|>assistant\n"
        val historyBlocks = request.fullConversation
            .filter { it.role == AgentMessageRole.USER || it.role == AgentMessageRole.ASSISTANT }
            .takeLast(MAX_HISTORY_MESSAGES)
            .mapNotNull { item ->
                val role = if (item.role == AgentMessageRole.USER) "user" else "assistant"
                val text = item.text.orEmpty().trim().take(MAX_MESSAGE_CHARS)
                text.takeIf { it.isNotBlank() }?.let {
                    buildString {
                        append("<|im_start|>").append(role).append('\n')
                        append(it).append('\n')
                        append("<|im_end|>\n")
                    }
                }
            }

        val remainingBudget =
            (MAX_PROMPT_CHARS - systemBlock.length - assistantPrefix.length).coerceAtLeast(0)
        val selectedHistory = ArrayDeque<String>()
        var usedChars = 0
        for (block in historyBlocks.asReversed()) {
            if (usedChars + block.length > remainingBudget) break
            selectedHistory.addFirst(block)
            usedChars += block.length
        }

        return buildString {
            append(systemBlock)
            selectedHistory.forEach { append(it) }
            append(assistantPrefix)
        }
    }

    companion object {
        private const val MAX_PRIVATE_CONTEXT_CHARS = 1_600
        private const val MAX_HISTORY_MESSAGES = 5
        private const val MAX_MESSAGE_CHARS = 1_100
        private const val MAX_PROMPT_CHARS = 6_500
    }
}

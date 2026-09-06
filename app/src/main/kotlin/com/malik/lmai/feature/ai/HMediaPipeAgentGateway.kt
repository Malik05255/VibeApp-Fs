package com.malik.lmai.feature.ai

import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
 * MediaPipe model downloaded by HLocalModelDownloadWorker. CPU is forced for
 * broad chipset compatibility; cloud routes remain preferred when available.
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

    override suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = callbackFlow {
        if (!modelManager.isReady()) {
            modelManager.scheduleBackgroundDownload()
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_MODEL_NOT_READY: محمد المحلي لم يكتمل تنزيله بعد. سيُجهز تلقائيًا على Wi‑Fi."
                )
            )
            close()
            return@callbackFlow
        }

        if (request.tools.isNotEmpty()) {
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_TOOLS_UNAVAILABLE: محمد يعمل بدون إنترنت الآن؛ تعديل ملفات المشروع وتشغيل أدوات البناء يحتاج المسار المتصل."
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
                    "H_LOCAL_IMAGE_UNAVAILABLE: يمكن إرفاق الصور، لكن تحليل الصور يحتاج المسار المتصل في هذه النسخة."
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
                    "H_LOCAL_ENGINE_FAILED: ${error.message?.take(180) ?: "تعذر تشغيل المحرك المحلي."}"
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
                    .setTemperature(0.25f)
                    .build(),
            )
        } catch (error: Throwable) {
            trySend(
                AgentModelEvent.Failed(
                    "H_LOCAL_SESSION_FAILED: ${error.message?.take(180) ?: "تعذر بدء جلسة محمد المحلية."}"
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
                        "H_LOCAL_INFERENCE_FAILED: ${error.message?.take(180) ?: "تعذر توليد الرد محليًا."}"
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
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelManager.modelFile.absolutePath)
                .setMaxTokens(1280)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            LlmInference.createFromOptions(context, options).also { engine = it }
        }
    }

    private fun buildPrompt(request: AgentModelRequest): String = buildString {
        append("<|im_start|>system\n")
        append("أنت محمد، مساعد H الرقمي. أنت مساعد تقني وبرمجي دقيق، مباشر، وتعمل الآن محليًا بدون إنترنت.\n")
        append("حلل الأخطاء والكود بعمق. عند الحاجة إلى استبدال كود كامل أعطه داخل fenced code block كاملًا بلا اختصار.\n")
        request.instructions
            ?.trim()
            ?.take(MAX_INSTRUCTION_CHARS)
            ?.takeIf { it.isNotBlank() }
            ?.let { append(it).append('\n') }
        append("<|im_end|>\n")

        request.fullConversation
            .filter { it.role == AgentMessageRole.USER || it.role == AgentMessageRole.ASSISTANT }
            .takeLast(MAX_HISTORY_MESSAGES)
            .forEach { item ->
                val role = if (item.role == AgentMessageRole.USER) "user" else "assistant"
                val text = item.text.orEmpty().trim().take(MAX_MESSAGE_CHARS)
                if (text.isNotBlank()) {
                    append("<|im_start|>").append(role).append('\n')
                    append(text).append('\n')
                    append("<|im_end|>\n")
                }
            }
        append("<|im_start|>assistant\n")
    }.takeLast(MAX_PROMPT_CHARS)

    companion object {
        private const val MAX_INSTRUCTION_CHARS = 3_500
        private const val MAX_HISTORY_MESSAGES = 6
        private const val MAX_MESSAGE_CHARS = 1_800
        private const val MAX_PROMPT_CHARS = 10_000
    }
}

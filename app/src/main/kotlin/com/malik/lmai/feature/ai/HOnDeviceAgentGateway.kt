package com.malik.lmai.feature.ai

import android.graphics.BitmapFactory
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.ImagePart
import com.google.mlkit.genai.prompt.TextPart
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelEvent
import com.malik.lmai.feature.agent.AgentModelGateway
import com.malik.lmai.feature.agent.AgentModelRequest
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Local runtime for مساعد H الرقمي.
 *
 * Uses Gemini Nano through Android AICore/ML Kit. Model weights are owned by the
 * system service rather than bundled in the APK, so supported phones get useful
 * offline generation without turning lm_AI into a multi-gigabyte application.
 *
 * Dynamic project tools are intentionally not executed by this local gateway.
 * File mutation stays on the normal agent/tool path where it can be validated and
 * rolled back. Local mode is used for conversation, explanation, code review and
 * image understanding when the model is available on-device.
 */
@Singleton
class HOnDeviceAgentGateway @Inject constructor() : AgentModelGateway {
    private val model by lazy { Generation.getClient() }
    private val preparationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preparationStarted = AtomicBoolean(false)

    suspend fun availability(): Availability = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> Availability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> Availability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> Availability.DOWNLOADING
            else -> Availability.UNAVAILABLE
        }
    }.getOrDefault(Availability.UNAVAILABLE)

    /**
     * When the phone has internet, quietly ask AICore to prepare Nano for future
     * offline use. No inference is performed in the background.
     */
    fun prepareForOfflineUse() {
        if (!preparationStarted.compareAndSet(false, true)) return
        preparationScope.launch {
            try {
                if (model.checkStatus() == FeatureStatus.DOWNLOADABLE) {
                    model.download().collect { status ->
                        if (status is DownloadStatus.DownloadFailed) {
                            preparationStarted.set(false)
                        }
                    }
                }
            } catch (_: CancellationException) {
                preparationStarted.set(false)
                throw
            } catch (_: Exception) {
                preparationStarted.set(false)
            }
        }
    }

    override suspend fun streamTurn(request: AgentModelRequest): Flow<AgentModelEvent> = flow {
        if (request.tools.isNotEmpty()) {
            emit(
                AgentModelEvent.Failed(
                    "H_LOCAL_TOOLS_UNAVAILABLE: محمد يعمل محليًا الآن، لكن تعديل ملفات المشروع تلقائيًا يحتاج مسار الأدوات المتصل."
                )
            )
            return@flow
        }

        if (availability() != Availability.AVAILABLE) {
            emit(
                AgentModelEvent.Failed(
                    "H_LOCAL_MODEL_UNAVAILABLE: النموذج المحلي غير جاهز على هذا الجهاز بعد."
                )
            )
            return@flow
        }

        val prompt = buildPrompt(request)
        val imagePath = request.fullConversation
            .lastOrNull { it.role == AgentMessageRole.USER }
            ?.attachments
            ?.firstOrNull()

        try {
            val bitmap = imagePath
                ?.takeIf { it.isNotBlank() }
                ?.let(BitmapFactory::decodeFile)

            val generationRequest = if (bitmap != null) {
                GenerateContentRequest.Builder(
                    ImagePart(bitmap),
                    TextPart(prompt),
                ).apply {
                    temperature = 0.25f
                    maxOutputTokens = 3072
                    enableThinking = true
                }.build()
            } else {
                GenerateContentRequest.Builder(TextPart(prompt)).apply {
                    temperature = 0.25f
                    maxOutputTokens = 3072
                    enableThinking = true
                }.build()
            }

            val complete = StringBuilder()
            model.generateContentStream(generationRequest).collect { chunk ->
                val delta = chunk.candidates.firstOrNull()?.text.orEmpty()
                if (delta.isNotEmpty()) {
                    complete.append(delta)
                    emit(AgentModelEvent.OutputDelta(delta))
                }
            }
            emit(AgentModelEvent.Completed(finalText = complete.toString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(
                AgentModelEvent.Failed(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "H_LOCAL_INFERENCE_FAILED: تعذر تشغيل محمد محليًا."
                )
            )
        }
    }

    private fun buildPrompt(request: AgentModelRequest): String = buildString {
        append("أنت محمد، مساعد H الرقمي داخل lm_AI. ")
        append("أنت قوي في التقنية والبرمجة وتشخيص الأخطاء. أجب مباشرة وبدقة. ")
        append("إذا أعطاك المستخدم كودًا، افهم السياق وحدد الخطأ واقترح كودًا كاملًا قابلًا للنسخ. ")
        append("لا تدّعِ أنك عدلت ملفًا فعليًا عندما لا تتوفر أدوات المشروع.\n\n")

        request.instructions
            ?.take(4500)
            ?.takeIf { it.isNotBlank() }
            ?.let {
                append(it)
                append("\n\n")
            }

        request.fullConversation
            .filter { it.role == AgentMessageRole.USER || it.role == AgentMessageRole.ASSISTANT }
            .takeLast(8)
            .forEach { item -> appendConversationItem(item) }
    }.takeLast(MAX_PROMPT_CHARS)

    private fun StringBuilder.appendConversationItem(item: AgentConversationItem) {
        val role = if (item.role == AgentMessageRole.USER) "المستخدم" else "محمد"
        val text = item.text.orEmpty().trim()
        if (text.isBlank()) return
        append(role)
        append(": ")
        append(text.take(3500))
        append("\n")
    }

    enum class Availability {
        AVAILABLE,
        DOWNLOADABLE,
        DOWNLOADING,
        UNAVAILABLE,
    }

    companion object {
        private const val MAX_PROMPT_CHARS = 14_000
    }
}

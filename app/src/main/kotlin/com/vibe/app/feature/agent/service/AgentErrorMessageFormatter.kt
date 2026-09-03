package com.vibe.app.feature.agent.service

import androidx.appcompat.app.AppCompatDelegate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts provider and network errors into concise messages for the chat UI.
 * The language follows the app locale selected by the user, not the device
 * system locale.
 */
object AgentErrorMessageFormatter {

    fun format(message: String): String {
        val raw = message.trim()
        if (raw.isBlank()) {
            return localized(
                arabic = "حدث خطأ. حاول مرة أخرى.",
                english = "Something went wrong. Please try again.",
            )
        }

        val normalized = raw.lowercase(Locale.US)

        if (
            normalized.contains("free-models-per-day") ||
            normalized.contains("openrouter_free_tier_daily")
        ) {
            val resetAt = extractRateLimitReset(raw)
            return if (resetAt != null) {
                val dateText = formatResetDate(resetAt)
                localized(
                    arabic = "انتهى الرصيد المجاني. يتجدد في $dateText.",
                    english = "Free quota ended. It resets on $dateText.",
                )
            } else {
                localized(
                    arabic = "انتهى الرصيد المجاني. حاول بعد التجديد.",
                    english = "Free quota ended. Try again after the reset.",
                )
            }
        }

        if (
            normalized.contains("insufficient credits") ||
            normalized.contains("insufficient balance") ||
            normalized.contains("credit balance") ||
            normalized.contains("credits exhausted") ||
            normalized.contains("balance exhausted") ||
            normalized.contains("not enough credits") ||
            normalized.contains("payment required") ||
            normalized.contains("billing")
        ) {
            return localized(
                arabic = "الرصيد غير كافٍ. يرجى شحن الحساب أو استخدام نموذج آخر.",
                english = "Insufficient balance. Please add credits or use another model.",
            )
        }

        if (
            normalized.contains("resource_exhausted") ||
            normalized.contains("resource exhausted") ||
            normalized.contains("quota exceeded") ||
            (
                normalized.contains("generativelanguage.googleapis.com") &&
                    normalized.contains("quota")
                )
        ) {
            return localized(
                arabic = "تم تجاوز حصة Google AI Studio. انتظر قليلًا أو تحقق من حدود حسابك.",
                english = "Google AI Studio quota exceeded. Wait or check your account limits.",
            )
        }

        if (
            containsHttpCode(normalized, 401) ||
            normalized.contains("unauthorized") ||
            normalized.contains("invalid api key") ||
            normalized.contains("invalid_api_key") ||
            normalized.contains("api key not valid") ||
            normalized.contains("api_key_invalid") ||
            normalized.contains("authentication failed")
        ) {
            return localized(
                arabic = "مفتاح API غير صالح أو لم يتم قبوله.",
                english = "The API key is invalid or was not accepted.",
            )
        }

        if (
            containsHttpCode(normalized, 403) ||
            normalized.contains("forbidden") ||
            normalized.contains("permission denied") ||
            normalized.contains("permission_denied") ||
            normalized.contains("access denied")
        ) {
            return localized(
                arabic = "لا يملك مفتاح API صلاحية استخدام هذا النموذج أو الخدمة.",
                english = "The API key does not have permission to use this model or service.",
            )
        }

        if (
            containsHttpCode(normalized, 404) ||
            normalized.contains("model not found") ||
            normalized.contains("model_not_found") ||
            normalized.contains("endpoint not found") ||
            normalized.contains("not found for api version") ||
            normalized.contains("is not found")
        ) {
            return localized(
                arabic = "النموذج أو نقطة الاتصال غير موجودة. تحقق من Model ID وAPI URL.",
                english = "Model or endpoint not found. Check the Model ID and API URL.",
            )
        }

        if (
            normalized.contains("no endpoints found that support tool use") ||
            normalized.contains("no endpoint found that supports tool use") ||
            normalized.contains("does not support tool use") ||
            normalized.contains("doesn't support tool use") ||
            normalized.contains("tool use is not supported") ||
            normalized.contains("tool calling is not supported") ||
            normalized.contains("tools are not supported") ||
            normalized.contains("function calling is not supported")
        ) {
            return localized(
                arabic = "النموذج المحدد لا يدعم أدوات إنشاء التطبيقات. اختر نموذجًا يدعم Tools / Function Calling.",
                english = "The selected model does not support app-building tools. Choose a model with Tools / Function Calling support.",
            )
        }

        if (
            normalized.contains("tool schema") ||
            normalized.contains("function schema") ||
            normalized.contains("invalid schema") ||
            normalized.contains("invalid_function_parameters") ||
            normalized.contains("invalid function parameters") ||
            normalized.contains("parameters schema") ||
            (
                normalized.contains("additionalproperties") &&
                    normalized.contains("schema")
                )
        ) {
            return localized(
                arabic = "رفض المزود تعريف إحدى أدوات Agent بسبب Tool Schema غير متوافق.",
                english = "The provider rejected an Agent tool because its Tool Schema is incompatible.",
            )
        }

        if (
            normalized.contains("tool_call_id") ||
            normalized.contains("tool call id") ||
            (
                normalized.contains("tool_calls") &&
                    (
                        normalized.contains("must") ||
                            normalized.contains("missing") ||
                            normalized.contains("required")
                        )
                )
        ) {
            return localized(
                arabic = "حدث خطأ في تسلسل Tool Calls بين النموذج والـAgent.",
                english = "The model and Agent produced an invalid Tool Call sequence.",
            )
        }

        if (
            containsHttpCode(normalized, 400) ||
            normalized.contains("bad request") ||
            normalized.contains("invalid_request_error") ||
            normalized.contains("invalid argument") ||
            normalized.contains("invalid_argument")
        ) {
            return localized(
                arabic = "رفض مزود الذكاء الاصطناعي الطلب. تحقق من إعداد النموذج أو Tools أو تنسيق الرسائل.",
                english = "The AI provider rejected the request. Check the model, tools, or message format.",
            )
        }

        if (
            containsHttpCode(normalized, 429) ||
            normalized.contains("rate limit") ||
            normalized.contains("rate_limit") ||
            normalized.contains("too many requests")
        ) {
            return localized(
                arabic = "تم تجاوز حد الطلبات. انتظر قليلًا ثم حاول مرة أخرى.",
                english = "Request rate limit reached. Wait briefly and try again.",
            )
        }

        if (
            normalized.contains("context length") ||
            normalized.contains("context_length_exceeded") ||
            normalized.contains("maximum context") ||
            normalized.contains("max context") ||
            normalized.contains("too many tokens") ||
            normalized.contains("token limit")
        ) {
            return localized(
                arabic = "تجاوزت المحادثة الحد الأقصى لسياق النموذج.",
                english = "The conversation exceeded the model's context limit.",
            )
        }

        if (
            normalized.contains("only available on agentic harnesses") ||
            normalized.contains("only available on agentic")
        ) {
            return localized(
                arabic = "هذا النموذج غير متاح عبر واجهة Chat Completions المستخدمة في التطبيق.",
                english = "This model is not available through the Chat Completions interface used by the app.",
            )
        }

        if (
            containsHttpCode(normalized, 500) ||
            containsHttpCode(normalized, 502) ||
            containsHttpCode(normalized, 503) ||
            containsHttpCode(normalized, 504) ||
            normalized.contains("temporarily overloaded") ||
            normalized.contains("temporarily unavailable") ||
            normalized.contains("upstream error") ||
            normalized.contains("service unavailable") ||
            normalized.contains("gateway timeout") ||
            normalized.contains("bad gateway") ||
            normalized.contains("server error")
        ) {
            return localized(
                arabic = "خدمة مزود الذكاء الاصطناعي غير متاحة مؤقتًا. حاول مرة أخرى.",
                english = "The AI provider is temporarily unavailable. Please try again.",
            )
        }

        if (
            normalized.contains("unable to resolve host") ||
            normalized.contains("unknownhostexception") ||
            normalized.contains("connectexception") ||
            normalized.contains("connection refused") ||
            normalized.contains("failed to connect") ||
            normalized.contains("network is unreachable")
        ) {
            return localized(
                arabic = "تعذر الاتصال بالخادم. تحقق من الإنترنت وعنوان API.",
                english = "Unable to connect to the server. Check your internet connection and API URL.",
            )
        }

        if (
            normalized.contains("timeout") ||
            normalized.contains("timed out") ||
            normalized.contains("sockettimeoutexception")
        ) {
            return localized(
                arabic = "انتهت مهلة الاتصال بمزود الذكاء الاصطناعي.",
                english = "The AI provider request timed out.",
            )
        }

        if (
            normalized.contains("invalid_stream_chunk") ||
            normalized.contains("failed parsing sse") ||
            (normalized.contains("sse") && normalized.contains("parse"))
        ) {
            return localized(
                arabic = "وصل رد Streaming غير متوافق من مزود الذكاء الاصطناعي.",
                english = "The AI provider returned an incompatible streaming response.",
            )
        }

        return localized(
            arabic = "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي. راجع تفاصيل Agent Error لمعرفة السبب.",
            english = "An error occurred while contacting the AI. Check Agent Error details for the cause.",
        )
    }

    private fun containsHttpCode(
        normalized: String,
        code: Int,
    ): Boolean {
        return normalized.contains("\"code\":$code") ||
            normalized.contains("\"code\": $code") ||
            normalized.contains("http $code") ||
            normalized.contains("http_$code") ||
            normalized.contains("status $code") ||
            normalized.contains("status=$code") ||
            normalized.contains("status: $code")
    }

    private fun localized(
        arabic: String,
        english: String,
    ): String {
        return if (currentAppLocale().language.equals("ar", ignoreCase = true)) {
            arabic
        } else {
            english
        }
    }

    private fun currentAppLocale(): Locale {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        return if (!appLocales.isEmpty) {
            appLocales[0] ?: Locale.getDefault()
        } else {
            Locale.getDefault()
        }
    }

    private fun extractRateLimitReset(message: String): Long? {
        val regex = Regex(
            pattern = """X-RateLimit-Reset["']?\s*[:=]\s*["']?(\d{10,13})""",
            option = RegexOption.IGNORE_CASE,
        )

        val match = regex.find(message) ?: return null
        val raw = match.groupValues.getOrNull(1)?.toLongOrNull() ?: return null

        return if (raw < 10_000_000_000L) raw * 1000L else raw
    }

    private fun formatResetDate(timestampMillis: Long): String {
        return SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            currentAppLocale(),
        ).format(Date(timestampMillis))
    }
}

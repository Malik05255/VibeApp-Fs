package com.almi.ai.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.almi.ai.data.network.NetworkClient
import com.almi.ai.data.preferences.AlmiPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class TryOnGenerationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkClient: NetworkClient,
    private val preferences: AlmiPreferences,
) {
    suspend fun generateImage(
        personImage: String,
        garmentImage: String,
        garmentDescription: String,
    ): Result<GeneratedTryOnImage> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireApiKey()
            val references = JSONArray()
                .put(imageReference(personImage))
                .put(imageReference(garmentImage))

            val request = JSONObject()
                .put("model", IMAGE_MODEL)
                .put("prompt", buildTryOnPrompt(garmentDescription))
                .put("aspect_ratio", "2:3")
                .put("n", 1)
                .put("input_references", references)

            val response = networkClient().post(OPENROUTER_IMAGES_URL) {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                applyOpenRouterHeaders()
                setBody(request.toString())
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(extractApiError(body, response.status.value))
            }

            val root = JSONObject(body)
            val first = root.optJSONArray("data")?.optJSONObject(0)
                ?: throw IllegalStateException("empty_image_response")
            val mediaType = first.optString("media_type").ifBlank { "image/png" }
            val bytes = when {
                first.optString("b64_json").isNotBlank() ->
                    Base64.decode(first.optString("b64_json"), Base64.DEFAULT)
                first.optString("url").isNotBlank() -> downloadBytes(first.optString("url"), apiKey = null)
                else -> throw IllegalStateException("empty_image_response")
            }

            GeneratedTryOnImage(
                uri = saveImage(bytes, mediaType),
                model = IMAGE_MODEL,
                costUsd = root.optJSONObject("usage")?.optDouble("cost")?.takeIf { !it.isNaN() },
            )
        }
    }

    suspend fun generateVideo(
        generatedImage: String,
        motion: MotionDirection,
        onStatus: (VideoGenerationStatus) -> Unit,
    ): Result<GeneratedTryOnVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = requireApiKey()
            val references = JSONArray().put(imageReference(generatedImage))
            val request = JSONObject()
                .put("model", VIDEO_MODEL)
                .put("prompt", buildVideoPrompt(motion))
                .put("duration", 4)
                .put("resolution", "720p")
                .put("aspect_ratio", "9:16")
                .put("generate_audio", false)
                .put("input_references", references)

            onStatus(VideoGenerationStatus.SUBMITTING)
            val submitResponse = networkClient().post(OPENROUTER_VIDEOS_URL) {
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                applyOpenRouterHeaders()
                setBody(request.toString())
            }
            val submitBody = submitResponse.bodyAsText()
            if (!submitResponse.status.isSuccess()) {
                throw IllegalStateException(extractApiError(submitBody, submitResponse.status.value))
            }

            val submitted = JSONObject(submitBody)
            val jobId = submitted.optString("id")
            require(jobId.isNotBlank()) { "missing_video_job" }
            var pollingUrl = submitted.optString("polling_url")
            if (pollingUrl.isBlank()) pollingUrl = "$OPENROUTER_VIDEOS_URL/$jobId"
            if (pollingUrl.startsWith("/")) pollingUrl = "https://openrouter.ai$pollingUrl"

            var completed: JSONObject? = null
            var attempt = 0
            while (attempt < MAX_VIDEO_POLLS && completed == null) {
                if (attempt > 0) delay(VIDEO_POLL_INTERVAL_MS)
                onStatus(VideoGenerationStatus.PROCESSING)
                val pollResponse = networkClient().get(pollingUrl) {
                    bearerAuth(apiKey)
                    applyOpenRouterHeaders()
                }
                val pollBody = pollResponse.bodyAsText()
                if (!pollResponse.status.isSuccess()) {
                    throw IllegalStateException(extractApiError(pollBody, pollResponse.status.value))
                }
                val job = JSONObject(pollBody)
                when (job.optString("status").lowercase()) {
                    "completed" -> completed = job
                    "failed", "cancelled", "expired" ->
                        throw IllegalStateException(job.optString("error").ifBlank { "video_failed" })
                }
                attempt++
            }

            val job = completed ?: throw IllegalStateException("video_timeout")
            onStatus(VideoGenerationStatus.DOWNLOADING)
            val unsignedUrls = job.optJSONArray("unsigned_urls")
            val downloadUrl = if (unsignedUrls != null && unsignedUrls.length() > 0) {
                unsignedUrls.optString(0)
            } else {
                "$OPENROUTER_VIDEOS_URL/$jobId/content?index=0"
            }
            val bytes = downloadBytes(
                url = downloadUrl,
                apiKey = apiKey.takeIf { downloadUrl.startsWith("https://openrouter.ai/") },
            )
            GeneratedTryOnVideo(
                uri = saveVideo(bytes),
                model = VIDEO_MODEL,
                costUsd = job.optJSONObject("usage")?.optDouble("cost")?.takeIf { !it.isNaN() },
            )
        }
    }

    private fun requireApiKey(): String = preferences.currentApiKey().ifBlank {
        throw IllegalStateException("api_key_missing")
    }

    private suspend fun imageReference(source: String): JSONObject =
        JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", sourceToDataUrl(source)))

    private suspend fun sourceToDataUrl(source: String): String {
        if (source.startsWith("data:image/")) return source
        val (bytes, mimeType) = when {
            source.startsWith("https://") || source.startsWith("http://") -> {
                val response = networkClient().get(source)
                if (!response.status.isSuccess()) throw IllegalStateException("reference_download_failed")
                val bytes = response.body<ByteArray>()
                val mime = response.headers[HttpHeaders.ContentType]
                    ?.substringBefore(';')
                    ?.takeIf { it.startsWith("image/") }
                    ?: guessImageMime(source)
                bytes to mime
            }
            else -> {
                val uri = Uri.parse(source)
                val resolver = context.contentResolver
                val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
                    ?: guessImageMime(source)
                val bytes = when (uri.scheme) {
                    "content", "android.resource" -> resolver.openInputStream(uri)?.use { it.readBytes() }
                    "file" -> File(requireNotNull(uri.path)).readBytes()
                    else -> File(source).takeIf { it.exists() }?.readBytes()
                } ?: throw IllegalStateException("reference_read_failed")
                bytes to mime
            }
        }
        if (bytes.size > MAX_REFERENCE_BYTES) throw IllegalStateException("reference_too_large")
        return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private suspend fun downloadBytes(url: String, apiKey: String?): ByteArray {
        val response = networkClient().get(url) {
            apiKey?.let { bearerAuth(it) }
        }
        if (!response.status.isSuccess()) throw IllegalStateException("download_failed")
        return response.body()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyOpenRouterHeaders() {
        header("HTTP-Referer", "https://almi.ai")
        header("X-Title", "ALMI_AI")
    }

    private fun saveImage(bytes: ByteArray, mediaType: String): String {
        val extension = when (mediaType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val directory = File(context.filesDir, "tryon_results").apply { mkdirs() }
        val file = File(directory, "almi_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun saveVideo(bytes: ByteArray): String {
        val directory = File(context.filesDir, "tryon_results").apply { mkdirs() }
        val file = File(directory, "almi_${System.currentTimeMillis()}.mp4")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun buildTryOnPrompt(description: String): String = """
        Create a photorealistic virtual try-on edit using exactly two references.
        Reference 1 is the real person. Preserve that person's identity, face, hair, skin tone, body proportions, hands, pose, camera angle and background as closely as possible.
        Reference 2 is the garment. Put that exact garment on the same person. Preserve its visible color, print, logo placement, neckline, sleeves, cut, proportions and material texture.
        Replace only the relevant clothing area. Do not beautify, slim, enlarge, reshape, age or otherwise modify the person's anatomy.
        Make the garment fit the pose naturally with realistic folds, shadows, occlusion and perspective. It must look physically worn, never pasted on.
        Garment context: ${description.ifBlank { "Use the garment reference exactly as shown." }}
        Output one high-detail realistic photo. No text, watermark, collage or before/after layout.
    """.trimIndent()

    private fun buildVideoPrompt(motion: MotionDirection): String {
        val motionText = when (motion) {
            MotionDirection.TURN -> "The person makes a slow natural quarter-turn and returns toward the camera."
            MotionDirection.WALK -> "The person takes a few natural slow steps toward the camera."
            MotionDirection.DETAIL -> "Use a subtle slow camera move while the person makes minimal natural movement to reveal garment fit and fabric."
        }
        return """
            Animate the exact person and exact outfit from the reference image into a realistic four-second fashion clip.
            $motionText
            Preserve identity, face, body proportions, garment color, logos, print, cut and fabric details. Keep motion stable and physically realistic.
            No morphing, outfit changes, body reshaping, extra limbs, jump cuts, text or watermark.
        """.trimIndent()
    }

    private fun extractApiError(body: String, statusCode: Int): String = runCatching {
        val root = JSONObject(body)
        root.optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
    }.getOrNull() ?: "http_$statusCode"

    private fun guessImageMime(source: String): String = when {
        source.substringBefore('?').endsWith(".png", ignoreCase = true) -> "image/png"
        source.substringBefore('?').endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }

    companion object {
        const val IMAGE_MODEL = "openai/gpt-image-1"
        const val VIDEO_MODEL = "bytedance/seedance-2.0-fast"
        private const val OPENROUTER_IMAGES_URL = "https://openrouter.ai/api/v1/images"
        private const val OPENROUTER_VIDEOS_URL = "https://openrouter.ai/api/v1/videos"
        private const val MAX_REFERENCE_BYTES = 18 * 1024 * 1024
        private const val MAX_VIDEO_POLLS = 45
        private const val VIDEO_POLL_INTERVAL_MS = 8_000L
    }
}

data class GeneratedTryOnImage(
    val uri: String,
    val model: String,
    val costUsd: Double?,
)

data class GeneratedTryOnVideo(
    val uri: String,
    val model: String,
    val costUsd: Double?,
)

enum class MotionDirection {
    TURN,
    WALK,
    DETAIL,
}

enum class VideoGenerationStatus {
    IDLE,
    SUBMITTING,
    PROCESSING,
    DOWNLOADING,
}

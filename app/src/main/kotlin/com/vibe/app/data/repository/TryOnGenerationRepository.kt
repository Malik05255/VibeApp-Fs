package com.vibe.app.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.vibe.app.data.model.ClientType
import com.vibe.app.data.network.NetworkClient
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
    private val settingRepository: SettingRepository,
) {

    suspend fun generateTryOnImage(
        personImage: String,
        garmentImages: List<String>,
        garmentDescriptions: List<String>,
    ): Result<GeneratedTryOnImage> = withContext(Dispatchers.IO) {
        runCatching {
            require(garmentImages.isNotEmpty()) { "Add at least one clothing item." }
            val credentials = resolveOpenRouterCredentials()
            val references = JSONArray()

            val allSources = listOf(personImage) + garmentImages.take(MAX_GARMENT_REFERENCES)
            allSources.forEach { source ->
                references.put(imageReference(source))
            }

            val request = JSONObject()
                .put("model", IMAGE_MODEL)
                .put("prompt", buildTryOnPrompt(garmentDescriptions))
                .put("aspect_ratio", "2:3")
                .put("n", 1)
                .put("input_references", references)

            val response = networkClient().post(OPENROUTER_IMAGES_URL) {
                bearerAuth(credentials.apiKey)
                contentType(ContentType.Application.Json)
                header("HTTP-Referer", "https://vibe.app")
                header("X-Title", "Vibe Virtual Try-On")
                setBody(request.toString())
            }

            val responseBody = response.bodyAsText()
            if (!response.status.isSuccess()) {
                throw IllegalStateException(extractApiError(responseBody, response.status.value))
            }

            val root = JSONObject(responseBody)
            val first = root.optJSONArray("data")?.optJSONObject(0)
                ?: throw IllegalStateException("The image API returned no image.")
            val encoded = first.optString("b64_json")
            if (encoded.isBlank()) {
                throw IllegalStateException("The image API returned an empty image.")
            }

            val mediaType = first.optString("media_type").ifBlank { "image/png" }
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val uri = saveImage(bytes, mediaType)
            val cost = root.optJSONObject("usage")?.optDouble("cost")
                ?.takeIf { !it.isNaN() }

            GeneratedTryOnImage(
                uri = uri,
                model = IMAGE_MODEL,
                costUsd = cost,
            )
        }
    }

    suspend fun generateTryOnVideo(
        generatedImage: String,
        motion: String,
        onStatus: (String) -> Unit = {},
    ): Result<GeneratedTryOnVideo> = withContext(Dispatchers.IO) {
        runCatching {
            val credentials = resolveOpenRouterCredentials()
            val referenceDataUrl = sourceToDataUrl(generatedImage)
            val references = JSONArray().put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", referenceDataUrl))
            )

            val request = JSONObject()
                .put("model", VIDEO_MODEL)
                .put("prompt", buildVideoPrompt(motion))
                .put("duration", 4)
                .put("resolution", "720p")
                .put("aspect_ratio", "9:16")
                .put("generate_audio", false)
                .put("input_references", references)

            onStatus("submitting")
            val submitResponse = networkClient().post(OPENROUTER_VIDEOS_URL) {
                bearerAuth(credentials.apiKey)
                contentType(ContentType.Application.Json)
                header("HTTP-Referer", "https://vibe.app")
                header("X-Title", "Vibe Virtual Try-On")
                setBody(request.toString())
            }
            val submitBody = submitResponse.bodyAsText()
            if (!submitResponse.status.isSuccess()) {
                throw IllegalStateException(extractApiError(submitBody, submitResponse.status.value))
            }

            val submitted = JSONObject(submitBody)
            val jobId = submitted.optString("id")
            require(jobId.isNotBlank()) { "Video API returned no job ID." }
            var pollingUrl = submitted.optString("polling_url")
            if (pollingUrl.isBlank()) pollingUrl = "$OPENROUTER_VIDEOS_URL/$jobId"
            if (pollingUrl.startsWith("/")) pollingUrl = "https://openrouter.ai$pollingUrl"

            var completed: JSONObject? = null
            for (attempt in 0 until MAX_VIDEO_POLLS) {
                if (attempt > 0) delay(VIDEO_POLL_INTERVAL_MS)
                onStatus("processing")

                val pollResponse = networkClient().get(pollingUrl) {
                    bearerAuth(credentials.apiKey)
                    header("HTTP-Referer", "https://vibe.app")
                    header("X-Title", "Vibe Virtual Try-On")
                }
                val pollBody = pollResponse.bodyAsText()
                if (!pollResponse.status.isSuccess()) {
                    throw IllegalStateException(extractApiError(pollBody, pollResponse.status.value))
                }

                val job = JSONObject(pollBody)
                when (job.optString("status").lowercase()) {
                    "completed" -> {
                        completed = job
                        break
                    }
                    "failed", "cancelled", "expired" -> {
                        throw IllegalStateException(
                            job.optString("error").ifBlank { "Video generation failed." }
                        )
                    }
                }
            }

            val job = completed ?: throw IllegalStateException("Video generation timed out. Try again.")
            onStatus("downloading")
            val unsignedUrls = job.optJSONArray("unsigned_urls")
            val downloadUrl = if (unsignedUrls != null && unsignedUrls.length() > 0) {
                unsignedUrls.optString(0)
            } else {
                "$OPENROUTER_VIDEOS_URL/$jobId/content?index=0"
            }
            require(downloadUrl.isNotBlank()) { "Video API returned no download URL." }

            val videoResponse = networkClient().get(downloadUrl) {
                if (downloadUrl.startsWith("https://openrouter.ai/")) {
                    bearerAuth(credentials.apiKey)
                }
            }
            if (!videoResponse.status.isSuccess()) {
                throw IllegalStateException(
                    extractApiError(videoResponse.bodyAsText(), videoResponse.status.value)
                )
            }
            val bytes: ByteArray = videoResponse.body()
            val videoUri = saveVideo(bytes)
            val cost = job.optJSONObject("usage")?.optDouble("cost")
                ?.takeIf { !it.isNaN() }

            GeneratedTryOnVideo(
                uri = videoUri,
                model = VIDEO_MODEL,
                costUsd = cost,
            )
        }
    }

    private suspend fun resolveOpenRouterCredentials(): OpenRouterCredentials {
        val platforms = settingRepository.fetchPlatformV2s()
        val platform = platforms.firstOrNull {
            it.enabled && it.compatibleType == ClientType.OPEN_ROUTER && !it.token.isNullOrBlank()
        } ?: platforms.firstOrNull {
            it.compatibleType == ClientType.OPEN_ROUTER && !it.token.isNullOrBlank()
        }

        val platformKey = platform?.token?.trim().orEmpty()
        if (platformKey.isNotBlank()) return OpenRouterCredentials(platformKey)

        val legacyProvider = runCatching { settingRepository.getApiProvider() }.getOrDefault("")
        val legacyKey = runCatching { settingRepository.getApiKey() }.getOrDefault("").trim()
        if (legacyProvider.contains("OPEN", ignoreCase = true) && legacyKey.isNotBlank()) {
            return OpenRouterCredentials(legacyKey)
        }

        throw IllegalStateException(
            "OpenRouter API key is not configured. Add an OpenRouter provider in Settings first."
        )
    }

    private suspend fun imageReference(source: String): JSONObject =
        JSONObject()
            .put("type", "image_url")
            .put("image_url", JSONObject().put("url", sourceToDataUrl(source)))

    private suspend fun sourceToDataUrl(source: String): String {
        if (source.startsWith("data:image/")) return source

        val (bytes, mimeType) = when {
            source.startsWith("http://") || source.startsWith("https://") -> {
                val response = networkClient().get(source)
                if (!response.status.isSuccess()) {
                    throw IllegalStateException("Could not download one of the reference images.")
                }
                val bytes: ByteArray = response.body()
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
                } ?: throw IllegalStateException("Could not read one of the selected images.")
                bytes to mime
            }
        }

        if (bytes.size > MAX_REFERENCE_BYTES) {
            throw IllegalStateException("One of the selected images is too large. Use an image under 18 MB.")
        }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:$mimeType;base64,$encoded"
    }

    private fun saveImage(bytes: ByteArray, mediaType: String): String {
        val extension = when (mediaType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            else -> "png"
        }
        val directory = File(context.filesDir, "tryon_results").apply { mkdirs() }
        val file = File(directory, "tryon_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun saveVideo(bytes: ByteArray): String {
        val directory = File(context.filesDir, "tryon_results").apply { mkdirs() }
        val file = File(directory, "tryon_${System.currentTimeMillis()}.mp4")
        file.writeBytes(bytes)
        return Uri.fromFile(file).toString()
    }

    private fun buildTryOnPrompt(garmentDescriptions: List<String>): String {
        val garmentList = garmentDescriptions
            .filter { it.isNotBlank() }
            .joinToString(separator = "; ")
            .ifBlank { "the clothing shown in the garment reference images" }

        return """
            Create a photorealistic virtual try-on edit.
            Reference image 1 is the real person and is the identity/body reference.
            Reference images 2 onward are garments that must be worn by that same person.
            Preserve the person's face, hair, skin tone, body proportions, hands, pose, camera angle and background as closely as possible.
            Replace only the relevant clothing areas. Do not beautify, slim, enlarge, reshape, age, or otherwise alter the person's anatomy.
            Preserve each garment's exact visible color, print, logo placement, neckline, sleeves, cut, material texture and design details from its reference image.
            Make the garment drape naturally on the body with realistic folds, shadows, occlusion and perspective.
            The clothing should look physically worn, not pasted on. Keep all non-garment details from the person photo unchanged whenever possible.
            Clothing references: $garmentList.
            Output a single high-detail realistic fashion photo of the same person wearing the referenced garments. No text, watermark, collage or before/after layout.
        """.trimIndent()
    }

    private fun buildVideoPrompt(motion: String): String {
        val motionInstruction = when (motion.uppercase()) {
            "WALK" -> "The person takes a few natural slow steps toward the camera."
            "DETAIL" -> "Use a subtle slow camera move that reveals the garment fabric, fit and details while the person makes minimal natural movement."
            else -> "The person makes a slow natural quarter-turn and returns toward camera, showing how the garment fits from slightly different angles."
        }
        return """
            Animate the exact person and exact outfit from the reference image into a realistic 4-second fashion try-on clip.
            $motionInstruction
            Preserve identity, facial features, body proportions, garment color, logos, print, cut and fabric details.
            Keep motion physically realistic and stable. No morphing, no outfit changes, no body reshaping, no extra limbs, no camera jump cuts, no text or watermark.
        """.trimIndent()
    }

    private fun extractApiError(body: String, statusCode: Int): String {
        return runCatching {
            val root = JSONObject(body)
            root.optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: root.optString("error").takeIf { it.isNotBlank() }
                ?: root.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull() ?: "AI request failed (HTTP $statusCode)."
    }

    private fun guessImageMime(source: String): String = when {
        source.substringBefore('?').endsWith(".png", ignoreCase = true) -> "image/png"
        source.substringBefore('?').endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }

    private data class OpenRouterCredentials(val apiKey: String)

    companion object {
        const val IMAGE_MODEL = "openai/gpt-image-1"
        const val VIDEO_MODEL = "bytedance/seedance-2.0-fast"
        private const val OPENROUTER_IMAGES_URL = "https://openrouter.ai/api/v1/images"
        private const val OPENROUTER_VIDEOS_URL = "https://openrouter.ai/api/v1/videos"
        private const val MAX_GARMENT_REFERENCES = 15
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

package com.vibe.app.data.repository

import com.vibe.app.data.model.ProductPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URI
import javax.inject.Inject

class ProductPreviewRepository @Inject constructor() {

    suspend fun load(url: String): Result<ProductPreview> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeUrl(url)
            val document = Jsoup.connect(normalizedUrl)
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                .referrer("https://www.google.com/")
                .timeout(15_000)
                .followRedirects(true)
                .get()

            val finalUrl = document.location().ifBlank { normalizedUrl }
            val finalUri = URI(finalUrl)
            val title = firstNonBlank(
                document.selectFirst("meta[property=og:title]")?.attr("content"),
                document.selectFirst("meta[name=twitter:title]")?.attr("content"),
                document.title(),
            ).orEmpty().trim().take(180)

            val rawImage = firstNonBlank(
                document.selectFirst("meta[property=og:image]")?.attr("content"),
                document.selectFirst("meta[property=og:image:secure_url]")?.attr("content"),
                document.selectFirst("meta[name=twitter:image]")?.attr("content"),
                document.selectFirst("img[src]")?.attr("src"),
            )

            val imageUrl = rawImage?.let { resolveUrl(finalUri, it) }
            val merchant = finalUri.host
                ?.removePrefix("www.")
                ?.substringBefore(':')
                .orEmpty()

            ProductPreview(
                sourceUrl = finalUrl,
                title = title.ifBlank { merchant.ifBlank { "Product" } },
                imageUrl = imageUrl,
                merchant = merchant,
            )
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotBlank()) { "Product URL is empty" }
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = URI(candidate)
        require(uri.scheme == "http" || uri.scheme == "https") { "Unsupported URL scheme" }
        require(!uri.host.isNullOrBlank()) { "Invalid product URL" }
        return uri.toString()
    }

    private fun resolveUrl(baseUri: URI, value: String): String? = runCatching {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return@runCatching null
        baseUri.resolve(trimmed).toString().takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }
    }.getOrNull()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}

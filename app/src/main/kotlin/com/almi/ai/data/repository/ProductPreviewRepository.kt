package com.almi.ai.data.repository

import com.almi.ai.data.model.ProductPreview
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

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
            val merchant = finalUri.host?.removePrefix("www.")?.substringBefore(':').orEmpty()
            val title = firstNonBlank(
                document.selectFirst("meta[property=og:title]")?.attr("content"),
                document.selectFirst("meta[name=twitter:title]")?.attr("content"),
                document.title(),
            ).orEmpty().trim().take(180).ifBlank { merchant.ifBlank { "Product" } }

            val rawImage = firstNonBlank(
                document.selectFirst("meta[property=og:image]")?.attr("content"),
                document.selectFirst("meta[property=og:image:secure_url]")?.attr("content"),
                document.selectFirst("meta[name=twitter:image]")?.attr("content"),
                document.selectFirst("img[src]")?.attr("src"),
            )

            ProductPreview(
                sourceUrl = finalUrl,
                title = title,
                imageUrl = rawImage?.let { resolveUrl(finalUri, it) },
                merchant = merchant,
            )
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.isNotBlank())
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = URI(candidate)
        require(uri.scheme == "http" || uri.scheme == "https")
        require(!uri.host.isNullOrBlank())
        return uri.toString()
    }

    private fun resolveUrl(baseUri: URI, value: String): String? = runCatching {
        baseUri.resolve(value.trim()).toString().takeIf {
            it.startsWith("http://") || it.startsWith("https://")
        }
    }.getOrNull()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}

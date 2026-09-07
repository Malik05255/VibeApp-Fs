package com.malik.lmai.feature.agent.loop

/**
 * Reconciles streamed text with providers that only expose the complete answer in
 * AgentModelEvent.Completed, then splits large fallback payloads into display-sized
 * chunks. Concatenating [chunks] always reproduces the original text exactly.
 */
internal object NaturalResponsePacer {

    fun missingCompletedText(
        streamedText: String,
        completedText: String?,
    ): String {
        val completed = completedText.orEmpty()
        if (completed.isBlank()) return ""
        if (streamedText.isEmpty()) return completed
        if (completed == streamedText) return ""
        if (completed.startsWith(streamedText)) {
            return completed.substring(streamedText.length)
        }

        // Some providers normalize whitespace between streaming and final payloads.
        // Never append the whole final answer in that case because it would duplicate
        // text already shown to the user.
        return ""
    }

    fun chunks(
        text: String,
        maxChunkChars: Int = DEFAULT_MAX_CHUNK_CHARS,
    ): List<String> {
        if (text.isEmpty()) return emptyList()
        require(maxChunkChars > 0)

        val result = mutableListOf<String>()
        val buffer = StringBuilder()

        TOKEN_REGEX.findAll(text).forEach { match ->
            val token = match.value
            if (
                buffer.isNotEmpty() &&
                buffer.length + token.length > maxChunkChars
            ) {
                result += buffer.toString()
                buffer.clear()
            }
            buffer.append(token)
        }

        if (buffer.isNotEmpty()) {
            result += buffer.toString()
        }

        return result
    }

    // Small word-sized fallback deltas make final-only providers look progressive
    // without intentionally slowing real provider streaming.
    private const val DEFAULT_MAX_CHUNK_CHARS = 10
    private val TOKEN_REGEX = Regex("""\S+\s*|\s+""")
}

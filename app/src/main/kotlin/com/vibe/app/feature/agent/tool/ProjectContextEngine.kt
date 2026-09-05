package com.vibe.app.feature.agent.tool

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Lightweight, local project-context selector.
 *
 * It deliberately does not embed or call an LLM. The engine scans the project,
 * scores text files against the current task, and returns only the most relevant
 * paths, symbols and short excerpts. This keeps provider prompts smaller while
 * giving the coding model a much better starting point than a full repository dump.
 */
@Singleton
class ProjectContextEngine @Inject constructor() {

    data class ContextFile(
        val path: String,
        val score: Int,
        val reasons: List<String>,
        val symbols: List<String>,
        val excerpts: List<String>,
    )

    data class ContextSelection(
        val queryTerms: List<String>,
        val filesExamined: Int,
        val selectedFiles: List<ContextFile>,
        val truncated: Boolean,
    )

    fun select(
        projectRoot: File,
        query: String,
        maxFiles: Int = DEFAULT_MAX_FILES,
    ): ContextSelection {
        val root = projectRoot.canonicalFile
        if (!root.exists() || !root.isDirectory) {
            return ContextSelection(emptyList(), 0, emptyList(), truncated = false)
        }

        val requestedMax = maxFiles.coerceIn(1, HARD_MAX_FILES)
        val terms = tokenizeQuery(query)
        val candidates = mutableListOf<ContextFile>()
        var examined = 0

        root.walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in EXCLUDED_DIRS }
            .filter { it.isFile }
            .filter { isEligibleTextFile(it) }
            .take(MAX_FILES_EXAMINED)
            .forEach { file ->
                examined++
                scoreFile(root, file, query, terms)?.let(candidates::add)
            }

        val selected = candidates
            .sortedWith(
                compareByDescending<ContextFile> { it.score }
                    .thenBy { pathPriority(it.path) }
                    .thenBy { it.path }
            )
            .take(requestedMax)

        return ContextSelection(
            queryTerms = terms,
            filesExamined = examined,
            selectedFiles = selected,
            truncated = candidates.size > selected.size || examined >= MAX_FILES_EXAMINED,
        )
    }

    private fun scoreFile(
        root: File,
        file: File,
        rawQuery: String,
        terms: List<String>,
    ): ContextFile? {
        val relative = file.toRelativeString(root).replace(File.separatorChar, '/')
        val pathLower = relative.lowercase(Locale.ROOT)
        val fileNameLower = file.name.lowercase(Locale.ROOT)
        val content = runCatching {
            file.readText(StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val contentLower = content.lowercase(Locale.ROOT)

        var score = baseImportance(relative)
        val reasons = linkedSetOf<String>()
        val matchedTerms = mutableListOf<String>()

        for (term in terms) {
            var termScore = 0
            if (pathLower.contains(term)) {
                termScore += if (fileNameLower.contains(term)) 9 else 6
                reasons += "path:$term"
            }

            val occurrences = countOccurrences(contentLower, term, MAX_TERM_OCCURRENCES)
            if (occurrences > 0) {
                termScore += min(occurrences, 4) * 2
                matchedTerms += term
            }
            score += termScore
        }

        val semanticBoosts = semanticBoosts(rawQuery, pathLower, contentLower)
        if (semanticBoosts.first > 0) {
            score += semanticBoosts.first
            reasons += semanticBoosts.second
        }

        if (score <= 0) return null

        if (matchedTerms.isNotEmpty()) {
            reasons += "content:${matchedTerms.distinct().take(4).joinToString(",")}" 
        }
        if (baseImportance(relative) > 0) {
            reasons += "project-core"
        }

        return ContextFile(
            path = relative,
            score = score,
            reasons = reasons.take(MAX_REASONS),
            symbols = extractSymbols(file.extension.lowercase(Locale.ROOT), content),
            excerpts = extractExcerpts(content, terms),
        )
    }

    private fun semanticBoosts(
        query: String,
        path: String,
        content: String,
    ): Pair<Int, String> {
        val q = query.lowercase(Locale.ROOT)
        var boost = 0
        val reasons = mutableListOf<String>()

        fun addIf(queryWords: Set<String>, pathHints: Set<String>, label: String, points: Int) {
            if (queryWords.none { q.contains(it) }) return
            if (pathHints.any { path.contains(it) } || pathHints.any { content.take(MAX_SEMANTIC_SCAN_CHARS).contains(it) }) {
                boost += points
                reasons += label
            }
        }

        addIf(
            setOf("ui", "screen", "layout", "button", "واجهة", "شاشة", "تصميم", "زر"),
            setOf("/layout/", "activity", "fragment", "view", "screen", "compose"),
            "ui",
            8,
        )
        addIf(
            setOf("login", "auth", "oauth", "token", "تسجيل", "دخول", "مصادقة"),
            setOf("auth", "oauth", "token", "credential", "login"),
            "auth",
            9,
        )
        addIf(
            setOf("github", "repository", "repo", "مستودع", "جيت"),
            setOf("github", "repository", "repo"),
            "github",
            9,
        )
        addIf(
            setOf("api", "network", "http", "provider", "نموذج", "مزود", "شبكة"),
            setOf("api", "network", "client", "provider", "ktor", "retrofit", "http"),
            "network",
            7,
        )
        addIf(
            setOf("permission", "manifest", "intent", "صلاحية", "مانيفست"),
            setOf("androidmanifest.xml", "manifest", "permission", "intent-filter"),
            "manifest",
            10,
        )
        addIf(
            setOf("database", "room", "dao", "data", "قاعدة", "بيانات"),
            setOf("database", "room", "dao", "entity", "repository"),
            "data",
            7,
        )
        addIf(
            setOf("build", "compile", "gradle", "dependency", "بناء", "تجميع", "اعتماد"),
            setOf("build.gradle", "gradle", "settings.gradle", "libs.versions", "pom.xml"),
            "build",
            10,
        )
        addIf(
            setOf("string", "translation", "language", "arabic", "english", "لغة", "ترجمة", "عربي", "انجليزي"),
            setOf("strings.xml", "values-ar", "locale", "language"),
            "localization",
            8,
        )

        return boost to reasons.joinToString(",")
    }

    private fun extractSymbols(extension: String, content: String): List<String> {
        val output = linkedSetOf<String>()
        val lines = content.lineSequence().take(MAX_SYMBOL_SCAN_LINES)

        when (extension) {
            "kt", "kts" -> {
                val regex = Regex("\\b(class|interface|object|enum\\s+class|data\\s+class|fun)\\s+([A-Za-z_][A-Za-z0-9_]*)")
                lines.forEach { line ->
                    regex.findAll(line).forEach { match -> output += match.groupValues[2] }
                }
            }
            "java" -> {
                val typeRegex = Regex("\\b(class|interface|enum|record)\\s+([A-Za-z_][A-Za-z0-9_]*)")
                val methodRegex = Regex("\\b([A-Za-z_][A-Za-z0-9_<>, ?\\[\\]]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(")
                lines.forEach { line ->
                    typeRegex.findAll(line).forEach { match -> output += match.groupValues[2] }
                    methodRegex.findAll(line).forEach { match ->
                        val name = match.groupValues[2]
                        if (name !in JAVA_CONTROL_WORDS) output += name
                    }
                }
            }
            "xml" -> {
                val idRegex = Regex("@\\+?id/([A-Za-z0-9_]+)")
                lines.forEach { line ->
                    idRegex.findAll(line).forEach { match -> output += "id/${match.groupValues[1]}" }
                }
            }
        }

        return output.take(MAX_SYMBOLS)
    }

    private fun extractExcerpts(content: String, terms: List<String>): List<String> {
        if (terms.isEmpty()) return emptyList()
        val lines = content.lines()
        val matchedIndices = mutableListOf<Int>()

        for ((index, line) in lines.withIndex()) {
            val lower = line.lowercase(Locale.ROOT)
            if (terms.any { lower.contains(it) }) matchedIndices += index
            if (matchedIndices.size >= MAX_EXCERPTS) break
        }

        return matchedIndices.map { index ->
            val from = (index - 1).coerceAtLeast(0)
            val to = (index + 1).coerceAtMost(lines.lastIndex)
            (from..to).joinToString("\n") { i ->
                val text = lines[i].let { if (it.length > MAX_EXCERPT_LINE) it.take(MAX_EXCERPT_LINE) + "…" else it }
                "${i + 1}:$text"
            }
        }
    }

    private fun tokenizeQuery(query: String): List<String> =
        query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}_./-]+"))
            .map { it.trim('.', '/', '-', '_') }
            .filter { it.length >= 3 }
            .filterNot { it in STOP_WORDS }
            .distinct()
            .take(MAX_QUERY_TERMS)

    private fun isEligibleTextFile(file: File): Boolean {
        if (file.length() <= 0L || file.length() > MAX_FILE_BYTES) return false
        val ext = file.extension.lowercase(Locale.ROOT)
        return ext in TEXT_EXTENSIONS || file.name in TEXT_FILENAMES
    }

    private fun baseImportance(path: String): Int {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            lower.endsWith("androidmanifest.xml") -> 5
            lower.endsWith("build.gradle.kts") || lower.endsWith("build.gradle") -> 4
            lower.endsWith("settings.gradle.kts") || lower.endsWith("settings.gradle") -> 3
            lower.endsWith("mainactivity.java") || lower.endsWith("mainactivity.kt") -> 4
            lower.endsWith("strings.xml") -> 2
            else -> 0
        }
    }

    private fun pathPriority(path: String): Int {
        val lower = path.lowercase(Locale.ROOT)
        return when {
            "/src/main/" in lower -> 0
            "/src/test/" in lower || "/src/androidtest/" in lower -> 1
            else -> 2
        }
    }

    private fun countOccurrences(text: String, term: String, cap: Int): Int {
        if (term.isBlank()) return 0
        var count = 0
        var start = 0
        while (count < cap) {
            val index = text.indexOf(term, start)
            if (index < 0) break
            count++
            start = index + term.length
        }
        return count
    }

    companion object {
        const val DEFAULT_MAX_FILES = 10
        private const val HARD_MAX_FILES = 20
        private const val MAX_FILES_EXAMINED = 2500
        private const val MAX_FILE_BYTES = 768L * 1024L
        private const val MAX_TERM_OCCURRENCES = 8
        private const val MAX_QUERY_TERMS = 18
        private const val MAX_REASONS = 6
        private const val MAX_SYMBOLS = 18
        private const val MAX_SYMBOL_SCAN_LINES = 2500
        private const val MAX_EXCERPTS = 3
        private const val MAX_EXCERPT_LINE = 260
        private const val MAX_SEMANTIC_SCAN_CHARS = 48_000

        private val EXCLUDED_DIRS = setOf(
            "build", ".gradle", ".idea", ".git", ".vibe", "node_modules", "dist", "out",
        )

        private val TEXT_EXTENSIONS = setOf(
            "kt", "kts", "java", "xml", "gradle", "properties", "toml", "json", "yaml", "yml",
            "md", "txt", "pro", "cfg", "conf", "ini", "html", "css", "js", "ts", "py", "rs",
        )

        private val TEXT_FILENAMES = setOf(
            "gradlew", "settings.gradle", "settings.gradle.kts", "build.gradle", "build.gradle.kts",
            "AndroidManifest.xml", "README", "README.md",
        )

        private val JAVA_CONTROL_WORDS = setOf(
            "if", "for", "while", "switch", "catch", "synchronized", "return", "new",
        )

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "this", "that", "from", "into", "app", "application",
            "please", "want", "make", "change", "add", "remove", "fix", "need",
            "ابي", "ابغى", "احتاج", "ممكن", "تطبيق", "عدل", "اضف", "احذف", "اصلح", "خلي", "يكون",
        )
    }
}

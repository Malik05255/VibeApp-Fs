package com.malik.lmai.build.engine.release

import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Prepares a generated app manifest for an installable update.
 *
 * Android only replaces an installed APK when the package name and signing
 * certificate stay the same and the new APK is not an older version. The
 * signing certificate is stable in the build engine; this helper enforces the
 * stable package from [expectedPackageName] and advances versionCode for every
 * successful standalone build.
 */
object GeneratedAppReleaseManager {

    private val versionCodeRegex = Regex("""android:versionCode\s*=\s*\"(\d+)\"""")
    private val packageRegex = Regex("""\bpackage\s*=\s*\"[^\"]*\"""")
    private val manifestTagRegex = Regex("""<manifest\b""")

    class PreparedManifest internal constructor(
        val manifestFile: File,
        private val originalText: String,
        val versionCode: Int,
        val packageName: String,
    ) {
        fun rollback() {
            manifestFile.writeText(originalText, StandardCharsets.UTF_8)
        }
    }

    fun prepare(
        manifestFile: File,
        expectedPackageName: String,
    ): PreparedManifest? {
        if (!manifestFile.exists() || !manifestFile.isFile) return null

        val original = manifestFile.readText(StandardCharsets.UTF_8)
        val currentVersionCode = versionCodeRegex.find(original)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
        val nextVersionCode = (currentVersionCode.toLong() + 1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        var updated = if (versionCodeRegex.containsMatchIn(original)) {
            versionCodeRegex.replaceFirst(
                original,
                "android:versionCode=\"$nextVersionCode\"",
            )
        } else {
            manifestTagRegex.replaceFirst(
                original,
                "<manifest android:versionCode=\"$nextVersionCode\"",
            )
        }

        updated = if (packageRegex.containsMatchIn(updated)) {
            packageRegex.replaceFirst(updated, "package=\"$expectedPackageName\"")
        } else {
            manifestTagRegex.replaceFirst(updated, "<manifest package=\"$expectedPackageName\"")
        }

        if (updated != original) {
            manifestFile.writeText(updated, StandardCharsets.UTF_8)
        }

        return PreparedManifest(
            manifestFile = manifestFile,
            originalText = original,
            versionCode = nextVersionCode,
            packageName = expectedPackageName,
        )
    }
}

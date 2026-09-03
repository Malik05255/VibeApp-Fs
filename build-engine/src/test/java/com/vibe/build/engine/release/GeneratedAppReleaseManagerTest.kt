package com.vibe.build.engine.release

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratedAppReleaseManagerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `prepare keeps package stable and increments version code`() {
        val manifest = manifestFile(
            packageName = "com.wrong.package",
            versionCode = 7,
        )

        val prepared = requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.vibe.generated.p123",
            )
        )

        val updated = manifest.readText()
        assertTrue(updated.contains("package=\"com.vibe.generated.p123\""))
        assertTrue(updated.contains("android:versionCode=\"8\""))
        assertEquals(8, prepared.versionCode)
    }

    @Test
    fun `rollback restores manifest after failed build`() {
        val manifest = manifestFile(
            packageName = "com.vibe.generated.p123",
            versionCode = 12,
        )
        val original = manifest.readText()

        val prepared = requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.vibe.generated.p123",
            )
        )
        prepared.rollback()

        assertEquals(original, manifest.readText())
    }

    @Test
    fun `successful prepares remain monotonically increasing`() {
        val manifest = manifestFile(
            packageName = "com.vibe.generated.p123",
            versionCode = 1,
        )

        requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.vibe.generated.p123",
            )
        )
        requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.vibe.generated.p123",
            )
        )

        assertTrue(manifest.readText().contains("android:versionCode=\"3\""))
    }

    private fun manifestFile(packageName: String, versionCode: Int): File {
        return temp.newFile("AndroidManifest.xml").apply {
            writeText(
                """<?xml version="1.0" encoding="utf-8"?>
                    |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                    |    package="$packageName"
                    |    android:versionCode="$versionCode"
                    |    android:versionName="1.0">
                    |    <application android:label="Test" />
                    |</manifest>
                """.trimMargin()
            )
        }
    }
}

package com.malik.lmai.feature.build

import com.malik.lmai.build.engine.release.GeneratedAppReleaseManager
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
    fun `successful releases keep package and raise version code`() {
        val manifest = manifestFile("com.old.package", 1)

        val first = requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.malik.lmai.generated.p123",
            )
        )
        val second = requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.malik.lmai.generated.p123",
            )
        )

        val text = manifest.readText()
        assertEquals(2, first.versionCode)
        assertEquals(3, second.versionCode)
        assertTrue(text.contains("package=\"com.malik.lmai.generated.p123\""))
        assertTrue(text.contains("android:versionCode=\"3\""))
    }

    @Test
    fun `failed release can restore previous version`() {
        val manifest = manifestFile("com.malik.lmai.generated.p123", 9)
        val original = manifest.readText()

        val prepared = requireNotNull(
            GeneratedAppReleaseManager.prepare(
                manifestFile = manifest,
                expectedPackageName = "com.malik.lmai.generated.p123",
            )
        )
        prepared.rollback()

        assertEquals(original, manifest.readText())
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

package com.almi.ai.update

import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlmiUpdatePolicyTest {
    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate $relative from ${File(".").absolutePath}")
    }

    @Test
    fun updaterIsLatestOnlyAndNeverFallsBackToFullApk() {
        val manager = source("src/main/kotlin/com/almi/ai/update/AlmiUpdateManager.kt")
        assertTrue(manager.contains("releases/latest/download/update-manifest.json"))
        assertTrue(manager.contains("There is deliberately no\n * full-APK network fallback"))
        assertTrue(manager.contains("patchForCurrent()"))
        assertTrue(manager.contains("KEY_ROLLED_BACK_RELEASE"))
        assertTrue(manager.contains("rolledBack != latestReleaseId"))
        assertFalse(manager.contains("downloadFullApk"))
        assertFalse(manager.contains("fallbackApkUrl"))
    }

    @Test
    fun manualCheckIsCancelableButAutomaticLaunchCanRemainMandatory() {
        val manager = source("src/main/kotlin/com/almi/ai/update/AlmiUpdateManager.kt")
        assertTrue(manager.contains("release.mandatory && !skipAllowed && !manual"))
        assertTrue(manager.contains("blockingAttempt"))
        assertTrue(manager.contains("blockingUpdate = blockingAttempt"))
        assertTrue(manager.contains("blockingUpdate = false"))
    }

    @Test
    fun automaticChannelPublishesARealLatestRelease() {
        val workflow = source("../.github/workflows/almi-update-channel.yml")
        assertTrue(workflow.contains("- almi-update-channel"))
        assertTrue(workflow.contains("permissions:\n  contents: write"))
        assertTrue(workflow.contains("BOOTSTRAP=true"))
        assertTrue(workflow.contains("gh release create"))
        assertTrue(workflow.contains("--latest"))
        assertTrue(workflow.contains("update-manifest.json"))
        assertTrue(workflow.contains("ALMI_rollback.apk"))
        assertTrue(workflow.contains("ALMI_reapply.apk"))
        assertTrue(workflow.contains("from-${'$'}{ROLLBACK_CODE}-to-${'$'}{REAPPLY_CODE}-reapply.alpatch"))
    }

    @Test
    fun releasePipelineBuildsRollbackReapplyAndDirectOldBasePatches() {
        val workflow = source("../.github/workflows/almi-update-release.yml")
        assertTrue(workflow.contains("per_page=6"))
        assertTrue(workflow.contains("ALMI_rollback.apk"))
        assertTrue(workflow.contains("ALMI_reapply.apk"))
        assertTrue(workflow.contains("from-${'$'}{ROLLBACK_CODE}-to-${'$'}{REAPPLY_CODE}-reapply.alpatch"))
        assertTrue(workflow.contains("MAX_CODE + 10"))
        assertTrue(workflow.contains("grep -c 'ALMI_AI.apk' release-out/update-manifest.json"))
    }

    @Test
    fun deltaReaderReconstructsExactTarget() {
        val dir = createTempDirectory("almi-delta-test-").toFile()
        try {
            val base = File(dir, "base.apk").apply { writeBytes("0123456789abcdefghij".toByteArray()) }
            val targetBytes = "01234-NEW-abcdefghij!".toByteArray()
            val targetReference = File(dir, "target-reference.apk").apply { writeBytes(targetBytes) }
            val patch = File(dir, "update.alpatch")
            val output = File(dir, "output.apk")

            DataOutputStream(FileOutputStream(patch).buffered()).use { out ->
                out.write(byteArrayOf('A'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(), 'I'.code.toByte(), 'D'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte()))
                out.writeInt(1)
                out.writeLong(base.length())
                out.writeLong(targetBytes.size.toLong())
                out.write(hash(base.readBytes()))
                out.write(hash(targetBytes))
                out.writeInt(4)

                out.writeByte(0)
                out.writeLong(0L)
                out.writeInt(5)

                val newBytes = "-NEW-".toByteArray()
                out.writeByte(1)
                out.writeInt(newBytes.size)
                out.write(newBytes)

                out.writeByte(0)
                out.writeLong(10L)
                out.writeInt(10)

                out.writeByte(1)
                out.writeInt(1)
                out.write('!'.code)
            }

            AlmiDeltaPatch.apply(base, patch, output)
            assertArrayEquals(targetReference.readBytes(), output.readBytes())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun hash(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
}

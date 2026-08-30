package com.almi.ai.ui.v12

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the v12 non-negotiable quality and memory-safety policy.
 *
 * Performance fixes must target streaming, memory, lifecycle, or scheduling. They must not silently
 * reduce the Digital Human render path on lower-end devices, and they must not restore the original
 * BODY + HEAD + HAIR launch-time allocation spike that crashed physical devices.
 */
class V12RenderQualityLockTest {
    private fun runtimeSource(): String {
        val relative = "src/main/kotlin/com/almi/ai/ui/v12/V12DigitalHumanRuntime.kt"
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not locate V12DigitalHumanRuntime.kt from ${File(".").absolutePath}")
    }

    @Test
    fun digitalHumanKeepsFullQualityPathOnEveryDevice() {
        val source = runtimeSource()

        assertFalse("Medium render quality must not return", source.contains("QualityLevel.MEDIUM"))
        assertFalse("Low render quality must not return", source.contains("QualityLevel.LOW"))
        assertFalse("Device-class quality downgrades must not return", source.contains("lowPowerDevice"))

        assertTrue(source.contains("hdrColorBuffer = View.QualityLevel.HIGH"))
        assertTrue(source.contains("view.dynamicResolutionOptions"))
        assertTrue(source.contains("enabled = false\n                    quality = View.QualityLevel.HIGH"))
        assertTrue(source.contains("view.antiAliasing = View.AntiAliasing.FXAA"))
        assertTrue(source.contains("view.multiSampleAntiAliasingOptions"))
        assertTrue(source.contains("view.ambientOcclusionOptions"))
        assertTrue(source.contains("view.bloomOptions"))
    }

    @Test
    fun performanceGuidanceExplicitlyRejectsQualityDowngrades() {
        val source = runtimeSource()
        assertTrue(source.contains("Performance work must come from"))
        assertTrue(source.contains("rather than silently reducing"))
        assertTrue(source.contains("render resolution"))
    }

    @Test
    fun digitalHumanLaunchIsStagedInsteadOfAllocatingAllHeavyAssetsTogether() {
        val source = runtimeSource()

        assertFalse(
            "Large GLBs must not be copied into transient direct buffers",
            source.contains("ByteBuffer.allocateDirect"),
        )
        assertTrue("GLBs should be mapped from the noCompress APK region", source.contains("FileChannel.MapMode.READ_ONLY"))
        assertTrue("Initial load must have a staged advance function", source.contains("advanceInitialLoadIfPossible"))
        assertTrue("Body must remain the first heavy asset", source.contains("body = loadPartAsync(BODY_ASSET)"))
        assertTrue("Head must be deferred until body resources are ready", source.contains("head = loadPartAsync(HEAD_ASSET)"))
        assertTrue("Initial hair must be deferred", source.contains("initialHairLoadStarted"))
        assertTrue("CPU-side resource payload must be evicted after GPU upload", source.contains("part.resources.evictResourceData()"))
    }

    @Test
    fun hairstyleReplacementDoesNotKeepTwoHeavyHairAssetsResident() {
        val source = runtimeSource()
        val functionStart = source.indexOf("private fun replaceHairIfNeeded")
        val functionEnd = source.indexOf("private fun destroyPart", startIndex = functionStart)
        assertTrue(functionStart >= 0 && functionEnd > functionStart)
        val function = source.substring(functionStart, functionEnd)

        val destroyIndex = function.indexOf("previous?.let(::destroyPart)")
        val loadIndex = function.indexOf("loadPartAsync(nextPath)")
        assertTrue("Old hair must be destroyed before new hair starts loading", destroyIndex >= 0 && loadIndex > destroyIndex)
    }
}

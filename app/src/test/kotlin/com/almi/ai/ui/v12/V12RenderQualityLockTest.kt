package com.almi.ai.ui.v12

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the v12 non-negotiable quality policy.
 *
 * Performance fixes must target streaming, memory, lifecycle, or scheduling. They must not silently
 * reduce the Digital Human render path on lower-end devices.
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
        assertTrue(source.contains("rather than silently reducing render resolution"))
    }
}

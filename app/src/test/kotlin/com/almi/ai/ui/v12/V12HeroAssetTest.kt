package com.almi.ai.ui.v12

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V12HeroAssetTest {
    @Test
    fun maleHeroChunksDecodeToValidWebp() {
        assertHeroAsset(prefix = "almi_v12_male_hero", chunks = 7, expectedBytes = 25_740)
    }

    @Test
    fun femaleHeroChunksDecodeToValidWebp() {
        assertHeroAsset(prefix = "almi_v12_female_hero", chunks = 7, expectedBytes = 19_408)
    }

    private fun assertHeroAsset(prefix: String, chunks: Int, expectedBytes: Int) {
        val rawDir = sequenceOf(
            File("src/main/res/raw"),
            File("app/src/main/res/raw"),
        ).firstOrNull(File::isDirectory)
            ?: error("Could not locate Android raw resources from ${File(".").absolutePath}")

        val encoded = buildString {
            repeat(chunks) { index ->
                val file = File(rawDir, "${prefix}_${index.toString().padStart(2, '0')}.txt")
                assertTrue("Missing hero chunk ${file.path}", file.isFile)
                append(file.readText().trim())
            }
        }

        val bytes = Base64.getDecoder().decode(encoded)
        assertEquals("$prefix decoded byte size changed", expectedBytes, bytes.size)
        assertTrue("$prefix is too small to be a WebP", bytes.size > 12)
        assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WEBP", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
    }
}

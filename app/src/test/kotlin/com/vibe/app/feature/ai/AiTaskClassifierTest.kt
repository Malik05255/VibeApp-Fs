package com.vibe.app.feature.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTaskClassifierTest {

    private val classifier = AiTaskClassifier()

    @Test
    fun `arabic greeting stays on lightweight chat path`() {
        val result = classifier.classifyText(
            latestUserText = "السلام عليكم",
            toolsAvailable = true,
        )

        assertEquals(AiTaskKind.LIGHT_CHAT, result.kind)
        assertFalse(result.requiresProjectTools)
    }

    @Test
    fun `english greeting stays on lightweight chat path`() {
        val result = classifier.classifyText(
            latestUserText = "Hello, how are you?",
            toolsAvailable = true,
        )

        assertEquals(AiTaskKind.LIGHT_CHAT, result.kind)
        assertFalse(result.requiresProjectTools)
    }

    @Test
    fun `natural arabic app request enters full project agent`() {
        val result = classifier.classifyText(
            latestUserText = "أبي تطبيق لتنظيم مهامي اليومية",
            toolsAvailable = true,
        )

        assertEquals(AiTaskKind.PROJECT_COMPLEX, result.kind)
        assertTrue(result.requiresProjectTools)
    }

    @Test
    fun `english app request enters full project agent`() {
        val result = classifier.classifyText(
            latestUserText = "I want an app for tracking my daily tasks",
            toolsAvailable = true,
        )

        assertEquals(AiTaskKind.PROJECT_COMPLEX, result.kind)
        assertTrue(result.requiresProjectTools)
    }
}

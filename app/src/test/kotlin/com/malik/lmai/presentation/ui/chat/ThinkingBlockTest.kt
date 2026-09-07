package com.malik.lmai.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingBlockTest {

    @Test
    fun `raw agent error is not rendered as thinking`() {
        val formatted = formatThoughtsForDisplay(
            thoughts = "[Agent Error] Gemini Nano is not available on this device.",
            toolCallingFmt = "Calling %s",
            toolOkFmt = "%s completed",
            toolErrorFmt = "%s failed",
        )

        assertEquals("", formatted)
    }

    @Test
    fun `diagnostic error is removed while real thinking and tool state remain`() {
        val formatted = formatThoughtsForDisplay(
            thoughts = """
                Checking project state
                [Tool] read_project_file
                [Tool Result] read_project_file: ok
                [Agent Error] provider diagnostic details
            """.trimIndent(),
            toolCallingFmt = "Calling %s",
            toolOkFmt = "%s completed",
            toolErrorFmt = "%s failed",
        )

        assertTrue(formatted.contains("Checking project state"))
        assertTrue(formatted.contains("read_project_file completed"))
        assertFalse(formatted.contains("Agent Error"))
        assertFalse(formatted.contains("provider diagnostic details"))
    }
}

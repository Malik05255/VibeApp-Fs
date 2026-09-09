package com.malik.lmai.feature.agent.loop

import com.malik.lmai.feature.agent.AgentToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnPolicyMemoryActionTest {

    @Test
    fun `plain save idea routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("احفظ هذه الفكرة: متجر عسل باشتراك شهري"),
        )
    }

    @Test
    fun `addressed save idea routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("يا H احفظ هذه الفكرة عندك للمستقبل"),
        )
    }

    @Test
    fun `remember command routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("تذكر اني افضل الردود المختصرة"),
        )
    }

    @Test
    fun `addressed English remember routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("Hey H remember this for me: I prefer concise replies"),
        )
    }

    @Test
    fun `project file save remains app execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("احفظ الملف في المشروع بعد التعديل"),
        )
    }

    @Test
    fun `ambiguous thing save with project file context remains app execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("احفظ هالشي في ملف المشروع"),
        )
    }

    @Test
    fun `explicit memory target wins even when memory mentions app`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("احفظ عندك ان فكرة التطبيق تعتمد على الاشتراكات"),
        )
    }

    @Test
    fun `H action allowlist includes cloud memory tool but excludes project writes`() {
        val definitions = listOf(
            "h_reminders",
            "h_cloud_link",
            "peach_whatsapp",
            "web_search",
            "fetch_web_page",
            "write_file",
            "run_build_pipeline",
        ).map { name ->
            AgentToolDefinition(
                name = name,
                description = name,
                inputSchema = buildJsonObject {},
            )
        }

        assertEquals(
            listOf(
                "h_reminders",
                "h_cloud_link",
                "peach_whatsapp",
                "web_search",
                "fetch_web_page",
            ),
            ChatTurnPolicy.actionTools(definitions).map { it.name },
        )
    }
}

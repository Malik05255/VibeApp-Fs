package com.malik.lmai.feature.ai

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolChoiceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiTaskClassifierTest {

    private val classifier = AiTaskClassifier()

    @Test
    fun `ordinary factual Arabic question stays local first light chat`() {
        val profile = classifier.classify(request("تعرف احمد زكي متى مات"))

        assertEquals(AiTaskKind.LIGHT_CHAT, profile.kind)
        assertFalse(profile.requiresProjectTools)
    }

    @Test
    fun `everyday personal problem is not classified as software bug`() {
        val profile = classifier.classify(request("عندي مشكلة مع زوجتي وابي افضفض"))

        assertEquals(AiTaskKind.LIGHT_CHAT, profile.kind)
        assertFalse(profile.requiresProjectTools)
    }

    @Test
    fun `ordinary change wording is not classified as code edit`() {
        val profile = classifier.classify(request("غيرت رأيي اليوم وودي اسولف معك"))

        assertEquals(AiTaskKind.LIGHT_CHAT, profile.kind)
    }

    @Test
    fun `general explanation stays local first`() {
        val profile = classifier.classify(request("اشرح لي ليش السماء زرقاء"))

        assertEquals(AiTaskKind.LIGHT_CHAT, profile.kind)
    }

    @Test
    fun `technical factual question uses explanation route`() {
        val profile = classifier.classify(request("وش هو OAuth وكيف يعمل في Android"))

        assertEquals(AiTaskKind.EXPLANATION, profile.kind)
    }

    @Test
    fun `technical generic problem is classified as bug fix`() {
        val profile = classifier.classify(request("عندي مشكلة في تطبيق Android وما يعمل تسجيل الدخول"))

        assertEquals(AiTaskKind.BUG_FIX, profile.kind)
    }

    @Test
    fun `strong stack trace signal is a bug without extra context`() {
        val profile = classifier.classify(request("Fatal Exception: IllegalStateException"))

        assertEquals(AiTaskKind.BUG_FIX, profile.kind)
    }

    @Test
    fun `mentioning gradle in a question is not project complex`() {
        val profile = classifier.classify(request("اشرح لي Gradle وش وظيفته"))

        assertEquals(AiTaskKind.EXPLANATION, profile.kind)
    }

    @Test
    fun `explicit full app build remains project complex`() {
        val profile = classifier.classify(request("انشئ تطبيق كامل وابن APK"))

        assertEquals(AiTaskKind.PROJECT_COMPLEX, profile.kind)
    }

    @Test
    fun `required tools always remain project complex`() {
        val profile = classifier.classify(
            request(
                text = "نفذ التعديلات",
                toolChoice = AgentToolChoiceMode.REQUIRED,
            )
        )

        assertEquals(AiTaskKind.PROJECT_COMPLEX, profile.kind)
        assertEquals(true, profile.requiresProjectTools)
    }

    private fun request(
        text: String,
        toolChoice: AgentToolChoiceMode = AgentToolChoiceMode.AUTO,
    ) = AgentModelRequest(
        platform = PlatformV2(
            name = "placeholder",
            compatibleType = ClientType.CUSTOM,
            apiUrl = "https://example.test/v1",
            token = "test-token",
            model = "test-model",
            enabled = true,
            enableConversationMemory = false,
        ),
        conversation = listOf(
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = text,
            )
        ),
        fullConversation = emptyList(),
        tools = emptyList(),
        policy = AgentLoopPolicy(toolChoiceMode = toolChoice),
    )
}

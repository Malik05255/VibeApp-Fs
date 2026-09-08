package com.malik.lmai.feature.agent.loop

import com.malik.lmai.data.database.entity.PlatformV2
import com.malik.lmai.data.model.ClientType
import com.malik.lmai.feature.agent.AgentConversationItem
import com.malik.lmai.feature.agent.AgentLoopPolicy
import com.malik.lmai.feature.agent.AgentMessageRole
import com.malik.lmai.feature.agent.AgentModelRequest
import com.malik.lmai.feature.agent.AgentToolDefinition
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnPolicyTest {

    @Test
    fun `greeting stays normal conversation`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("السلام عليكم"))
    }

    @Test
    fun `casual how are you stays conversation`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("كيفك انت وش اخبارك"))
    }

    @Test
    fun `venting request stays conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("انا متضايق وابي افضفض لك شوي"),
        )
    }

    @Test
    fun `generic word change inside casual sentence does not force execution`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("ما عندي غيرك افضفض له"),
        )
    }

    @Test
    fun `factual Arabic question stays normal conversation`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("تعرف عادل امام"))
    }

    @Test
    fun `technical explanation question stays conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("why does this Swift async function crash on cancellation"),
        )
    }

    @Test
    fun `inline code repair is treated as execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("اصلح هذا الكود وارجعه لي كامل"),
        )
    }

    @Test
    fun `app idea starts execution automatically`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("ابي تطبيق للمواعيد"),
        )
    }

    @Test
    fun `starter build app phrase enters execution`() {
        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect("بناء تطبيق"))
    }

    @Test
    fun `summarize project uses project tools`() {
        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect("لخص مشروعي"))
    }

    @Test
    fun `explicit planning only stays discovery`() {
        assertEquals(
            ChatTurnMode.APP_DISCOVERY,
            ChatTurnPolicy.detect("ابي تطبيق للمواعيد لكن لا تنفذ الحين خلنا نخطط"),
        )
    }

    @Test
    fun `explicit app fix enters execution`() {
        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect("اصلح التطبيق"))
    }

    @Test
    fun `ui edit without saying app still enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("عدل لون الزر الى ازرق"),
        )
    }

    @Test
    fun `screen movement command enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("ارفع الايقونه فوق شوي في الشاشه"),
        )
    }

    @Test
    fun `short command with explicit app target enters execution`() {
        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect("سوها في التطبيق"))
    }

    @Test
    fun `word starting with khal is not mistaken for command`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("خلفية التطبيق جميلة ولا؟"),
        )
    }

    @Test
    fun `word starting with su is not mistaken for command`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("سوال عن التطبيق وش فكرته؟"),
        )
    }

    @Test
    fun `ambiguous short followup alone remains conversation without project context`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("ارفعها فوق شوي"))
    }

    @Test
    fun `short followup inherits execution from recent project context`() {
        val conversation = listOf(
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = "عدل واجهة التطبيق وخلي الايقونة اصغر",
            ),
            AgentConversationItem(
                role = AgentMessageRole.ASSISTANT,
                text = "تم تعديل الواجهة.",
            ),
            AgentConversationItem(
                role = AgentMessageRole.USER,
                text = "ارفعها فوق شوي",
            ),
        )
        val request = AgentModelRequest(
            platform = PlatformV2(
                name = "test",
                compatibleType = ClientType.OPENAI,
                enabled = true,
                apiUrl = "https://example.invalid",
                model = "test-model",
            ),
            conversation = conversation,
            fullConversation = conversation,
            tools = emptyList(),
            policy = AgentLoopPolicy(),
        )

        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect(request))
    }

    @Test
    fun `repository connection request enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("اتصل بالمستودع واصلح الاخطاء"),
        )
    }

    @Test
    fun `common Arabic repository connection typo still enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("انصل بالمستودع واصلح الاخطاء"),
        )
    }

    @Test
    fun `english app request executes by default`() {
        assertEquals(ChatTurnMode.APP_EXECUTION, ChatTurnPolicy.detect("I need an app for reminders"))
    }

    @Test
    fun `english planning only remains discovery`() {
        assertEquals(
            ChatTurnMode.APP_DISCOVERY,
            ChatTurnPolicy.detect("I need an app for reminders but do not implement it, plan only"),
        )
    }

    @Test
    fun `english greeting stays conversation`() {
        assertEquals(ChatTurnMode.CONVERSATION, ChatTurnPolicy.detect("hello there"))
    }

    @Test
    fun `personal reminder request routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("يا H ذكرني بعد ساعة اشرب ماء"),
        )
    }

    @Test
    fun `local recommendation request routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("يا H شوف لي مطعم بخاري قريب وتقييماته عالية"),
        )
    }

    @Test
    fun `whatsapp send request routes to H action`() {
        assertEquals(
            ChatTurnMode.H_ACTION,
            ChatTurnPolicy.detect("يا H ارسل رسالة واتساب لمحمد اني بتأخر"),
        )
    }

    @Test
    fun `project search remains app execution instead of web action`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("ابحث في المشروع عن مكان تسجيل الادوات"),
        )
    }

    @Test
    fun `H action tool allowlist excludes project mutation tools`() {
        val names = listOf(
            "h_reminders",
            "peach_whatsapp",
            "web_search",
            "fetch_web_page",
            "run_build_pipeline",
            "write_file",
        )
        val definitions = names.map { name ->
            AgentToolDefinition(
                name = name,
                description = name,
                inputSchema = buildJsonObject {},
            )
        }

        assertEquals(
            listOf("h_reminders", "peach_whatsapp", "web_search", "fetch_web_page"),
            ChatTurnPolicy.actionTools(definitions).map { it.name },
        )
    }
}

package com.malik.lmai.feature.agent.loop

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTurnPolicyTest {

    @Test
    fun `greeting stays normal conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u0627\u0644\u0633\u0644\u0627\u0645 \u0639\u0644\u064a\u0643\u0645"),
        )
    }

    @Test
    fun `app idea without execution stays discovery`() {
        assertEquals(
            ChatTurnMode.APP_DISCOVERY,
            ChatTurnPolicy.detect("\u0627\u0628\u064a \u062a\u0637\u0628\u064a\u0642 \u0644\u0644\u0645\u0648\u0627\u0639\u064a\u062f"),
        )
    }

    @Test
    fun `explicit fix request enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("\u0627\u0635\u0644\u062d \u0627\u0644\u062a\u0637\u0628\u064a\u0642"),
        )
    }

    @Test
    fun `english greeting stays conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("hello there"),
        )
    }
}

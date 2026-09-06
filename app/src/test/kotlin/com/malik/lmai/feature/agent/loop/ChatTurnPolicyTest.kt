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
    fun `casual how are you stays conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u0643\u064a\u0641\u0643 \u0627\u0646\u062a \u0648\u0634 \u0627\u062e\u0628\u0627\u0631\u0643"),
        )
    }

    @Test
    fun `venting request stays conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u0627\u0646\u0627 \u0645\u062a\u0636\u0627\u064a\u0642 \u0648\u0627\u0628\u064a \u0627\u0641\u0636\u0641\u0636 \u0644\u0643 \u0634\u0648\u064a"),
        )
    }

    @Test
    fun `generic word change inside casual sentence does not force execution`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u0645\u0627 \u0639\u0646\u062f\u064a \u063a\u064a\u0631\u0643 \u0627\u0641\u0636\u0641\u0636 \u0644\u0647"),
        )
    }

    @Test
    fun `factual Arabic question stays normal conversation`() {
        assertEquals(
            ChatTurnMode.CONVERSATION,
            ChatTurnPolicy.detect("\u062a\u0639\u0631\u0641 \u0639\u0627\u062f\u0644 \u0627\u0645\u0627\u0645"),
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
    fun `explicit short edit command enters execution`() {
        assertEquals(
            ChatTurnMode.APP_EXECUTION,
            ChatTurnPolicy.detect("\u0639\u062f\u0644 \u0627\u0644\u0644\u0648\u0646 \u0627\u0644\u0649 \u0627\u0632\u0631\u0642"),
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

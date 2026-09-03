package com.vibe.app.feature.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopPolicyTest {

    @Test
    fun `default policy keeps enough headroom for complex app generation`() {
        val policy = AgentLoopPolicy()

        assertEquals(96, policy.maxIterations)
        assertTrue(policy.allowParallelToolCalls)
        assertEquals(AgentToolChoiceMode.AUTO, policy.toolChoiceMode)
    }
}

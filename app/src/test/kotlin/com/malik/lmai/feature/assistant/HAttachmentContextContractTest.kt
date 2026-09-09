package com.malik.lmai.feature.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HAttachmentContextContractTest {
    @Test
    fun contextAdvertisesTheActualSupportedAttachmentKinds() {
        val context = HContextBuilder.build(
            identity = HIdentity(releaseName = "test", generation = 1L),
            relationship = relationship(),
            userDisplayName = null,
            currentAttachmentCount = 2,
        )

        assertTrue(context.contains("images, PDFs, bounded text documents, audio, and video"))
        assertTrue(context.contains("limited to 180 seconds"))
        assertTrue(context.contains("2 supported attachment(s)"))
        assertTrue(context.contains("transient working data"))
        assertFalse(context.contains("attachments in this assistant are images only"))
        assertFalse(context.contains("2 image attachment(s)"))
    }

    @Test
    fun noAttachmentTurnDoesNotInventCurrentAttachmentMetadata() {
        val context = HContextBuilder.build(
            identity = HIdentity(releaseName = "test", generation = 1L),
            relationship = relationship(),
            userDisplayName = null,
            currentAttachmentCount = 0,
        )

        assertFalse(context.contains("The current turn includes"))
    }

    private fun relationship() = HRelationshipState(
        firstMetAtMs = 1L,
        lastInteractionAtMs = 1L,
        turnCount = 0L,
    )
}

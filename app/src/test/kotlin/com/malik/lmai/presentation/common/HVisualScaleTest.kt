package com.malik.lmai.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class HVisualScaleTest {

    @Test
    fun honorReferenceRemainsOneToOne() {
        assertEquals(1f, calculateHVisualScale(360f, 800f), 0.0001f)
    }

    @Test
    fun largerSameAspectDisplayScalesUniformly() {
        assertEquals(2f, calculateHVisualScale(720f, 1600f), 0.0001f)
    }

    @Test
    fun widerDisplayUsesHeightAsLimitInsteadOfStretching() {
        assertEquals(1.6f, calculateHVisualScale(720f, 1280f), 0.0001f)
    }

    @Test
    fun tallerDisplayUsesWidthAsLimitInsteadOfStretching() {
        assertEquals(1.5f, calculateHVisualScale(540f, 1400f), 0.0001f)
    }

    @Test
    fun invalidWindowFallsBackToReferenceScale() {
        assertEquals(1f, calculateHVisualScale(0f, 800f), 0.0001f)
    }
}

package com.malik.lmai.presentation.common

/** Canonical Honor-oriented visual reference used across H screens. */
internal const val H_REFERENCE_WIDTH_DP = 360f
internal const val H_REFERENCE_HEIGHT_DP = 800f

/**
 * Returns one uniform scale for the entire H composition.
 *
 * Using the smaller width/height ratio prevents stretching or reflow: every coordinate keeps the
 * same visual proportion as the Honor reference, while different aspect ratios only gain neutral
 * outer space. The bounds protect very small windows and unusually large displays from producing
 * unusable controls.
 */
internal fun calculateHVisualScale(widthDp: Float, heightDp: Float): Float {
    if (widthDp <= 0f || heightDp <= 0f) return 1f
    val widthScale = widthDp / H_REFERENCE_WIDTH_DP
    val heightScale = heightDp / H_REFERENCE_HEIGHT_DP
    return minOf(widthScale, heightScale).coerceIn(0.85f, 2.40f)
}

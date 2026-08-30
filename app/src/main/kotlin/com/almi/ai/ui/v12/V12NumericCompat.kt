package com.almi.ai.ui.v12

/**
 * Keeps mixed Float/Double camera math explicit at the renderer boundary.
 * The measurement UI reports normalized Float coordinates while Filament camera state is Double.
 */
internal fun Double.coerceIn(minimumValue: Float, maximumValue: Float): Double =
    coerceIn(minimumValue.toDouble(), maximumValue.toDouble())

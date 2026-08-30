@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.geometry

/**
 * Compatibility bridge for the v12 reference hero-art canvas.
 * Compose keeps the Path interface and its factory in ui.graphics; the original drawing source
 * imports geometry primitives together, so expose both the type and constructor-style factory.
 */
typealias Path = androidx.compose.ui.graphics.Path

fun Path(): Path = androidx.compose.ui.graphics.Path()

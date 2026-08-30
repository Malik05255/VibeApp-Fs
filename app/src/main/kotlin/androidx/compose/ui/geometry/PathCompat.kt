@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.geometry

/**
 * Compatibility alias for the v12 reference hero-art canvas.
 * Compose exposes Path from ui.graphics; this alias keeps the geometry-focused drawing source
 * concise without wrapping or copying the actual Path implementation.
 */
typealias Path = androidx.compose.ui.graphics.Path

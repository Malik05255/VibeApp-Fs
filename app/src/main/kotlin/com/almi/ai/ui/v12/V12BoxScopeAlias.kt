package com.almi.ai.ui.v12

/**
 * Classifier alias used by the V12 portal DSL. Compose exposes Box as a composable function while
 * alignment modifiers live on BoxScope; keeping the short Box receiver name makes the portal code
 * read like a scene description without shadowing the composable callable.
 */
internal typealias Box = androidx.compose.foundation.layout.BoxScope

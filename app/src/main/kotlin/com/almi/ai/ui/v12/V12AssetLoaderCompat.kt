package com.almi.ai.ui.v12

import com.google.android.filament.gltfio.AssetLoader

/**
 * Filament 1.71's Android AssetLoader binding does not expose the native gc() helper.
 * ResourceLoader ownership and explicit asset destruction already provide deterministic cleanup,
 * so this compatibility extension intentionally does nothing while keeping the async frame loop
 * source portable across Filament bindings that do expose gc().
 */
internal fun AssetLoader.gc() = Unit

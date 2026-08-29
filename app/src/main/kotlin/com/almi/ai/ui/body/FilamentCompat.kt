package com.almi.ai.ui.body

import com.google.android.filament.gltfio.AssetLoader

/**
 * Filament's Java AssetLoader API no longer exposes the historical gc() helper.
 * Resource reclamation is handled by explicit asset destruction in DirectFilamentBodyView.
 */
@Suppress("unused")
internal fun AssetLoader.gc() = Unit

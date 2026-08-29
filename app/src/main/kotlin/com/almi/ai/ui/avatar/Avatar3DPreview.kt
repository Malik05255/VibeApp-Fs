package com.almi.ai.ui.avatar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.ui.body.BodyShapeSolver
import com.google.android.filament.Colors
import com.google.android.filament.MaterialInstance
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.setMorphWeights
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader

/**
 * Live 3D avatar preview for ALMI v7.
 *
 * Body proportions come only from the measurement-driven digital twin. Face expression, hair
 * geometry/color, skin tone, glasses and facial hair are appearance layers and never alter the
 * measurement solver. Three bundled GLB hair meshes are switched at runtime so hairstyle choices
 * visibly change real geometry instead of only changing a prompt or remote thumbnail.
 */
@Composable
fun Avatar3DPreview(
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    modifier: Modifier = Modifier,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val shape = remember(bodyProfile) { BodyShapeSolver.solve(bodyProfile) }
    val hairConfig = remember(appearance.hairVariant) { hairConfigFor(appearance.hairVariant) }

    val frameMaterial = remember(materialLoader) {
        val rgb = parseRgb("171717")
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(rgb[0], rgb[1], rgb[2], 1f),
            metallic = 0.28f,
            roughness = 0.25f,
            reflectance = 0.72f,
        )
    }
    val beardMaterial = remember(materialLoader, appearance.hairColor) {
        val rgb = parseRgb(appearance.hairColor)
        materialLoader.createColorInstance(
            io.github.sceneview.math.Color(rgb[0], rgb[1], rgb[2], 1f),
            metallic = 0.02f,
            roughness = 0.62f,
            reflectance = 0.30f,
        )
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f), RoundedCornerShape(26.dp)),
    ) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            environmentLoader = environmentLoader,
            renderQuality = RenderQuality.Cinematic,
            autoCenterContent = true,
            cameraManipulator = rememberCameraManipulator(
                orbitHomePosition = Position(x = 0f, y = 0.75f, z = 3.1f),
                targetPosition = Position(x = 0f, y = 0.82f, z = 0f),
            ),
        ) {
            var body by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var head by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var shortHair by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var mediumHair by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }
            var longHair by remember(modelLoader) { mutableStateOf<ModelInstance?>(null) }

            LaunchedEffect(modelLoader) {
                body = modelLoader.loadModelInstance(BODY_ASSET)
                head = modelLoader.loadModelInstance(HEAD_ASSET)
                shortHair = modelLoader.loadModelInstance(HAIR_CARDS_ASSET)
                mediumHair = modelLoader.loadModelInstance(HAIR_MEDIUM_ASSET)
                longHair = modelLoader.loadModelInstance(HAIR_LONG_ASSET)
            }

            LaunchedEffect(body, head, appearance.skinColor) {
                val tone = parseRgb(appearance.skinColor)
                body?.tintMaterials(tone, names = listOf("vitbody", "skin"))
                head?.tintMaterials(tone, names = listOf("vitskin", "skin"))
            }

            LaunchedEffect(shortHair, mediumHair, longHair, appearance.hairColor) {
                val tone = parseRgb(appearance.hairColor)
                shortHair?.tintMaterials(tone)
                mediumHair?.tintMaterials(tone)
                longHair?.tintMaterials(tone)
            }

            LaunchedEffect(head, appearance.eyesVariant, appearance.eyebrowsVariant, appearance.mouthVariant) {
                val weights = expressionWeights(appearance)
                head?.let { instance -> runCatching { instance.setMorphWeights(weights, 0) } }
            }

            Node(
                scale = Scale(shape.widthScale, shape.heightScale, shape.depthScale),
            ) {
                body?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        apply = { name = "almi_avatar_body"; isHittable = false },
                    )
                }

                head?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scale = Scale(shape.headWidthCompensation, 1f, shape.headDepthCompensation),
                        apply = { name = "almi_avatar_head"; isHittable = false },
                    )
                }

                val selectedHair = when (hairConfig.asset) {
                    HAIR_CARDS_ASSET -> shortHair
                    HAIR_MEDIUM_ASSET -> mediumHair
                    else -> longHair
                }
                selectedHair?.let { instance ->
                    ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scale = Scale(
                            shape.headWidthCompensation * hairConfig.scaleX,
                            hairConfig.scaleY,
                            shape.headDepthCompensation * hairConfig.scaleZ,
                        ),
                        position = Position(y = hairConfig.offsetY, z = hairConfig.offsetZ),
                        apply = { name = "almi_avatar_hair"; isHittable = false },
                    )
                }

                if (appearance.accessoriesVariant != "none") {
                    Glasses3D(appearance.accessoriesVariant, frameMaterial)
                }
                if (appearance.facialHairVariant != "none") {
                    FacialHair3D(appearance.facialHairVariant, beardMaterial)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.62f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(Color(0xFF69D298), CircleShape))
                Text("LIVE 3D", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                "360° • DRAG • PINCH",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun io.github.sceneview.NodeScope.Glasses3D(
    variant: String,
    material: MaterialInstance,
) {
    val wide = variant == "wayfarers" || variant == "sunglasses"
    val round = variant == "round"
    val major = if (wide) 0.029f else if (round) 0.025f else 0.026f
    val thickness = if (variant == "sunglasses") 0.0041f else 0.0027f
    val xScale = if (wide) 1.20f else 1f
    val yScale = if (wide) 0.78f else if (round) 1f else 0.86f
    val faceY = 1.635f
    val faceZ = 0.105f

    listOf(-0.034f, 0.034f).forEach { x ->
        TorusNode(
            majorRadius = major,
            minorRadius = thickness,
            position = Position(x = x, y = faceY, z = faceZ),
            rotation = Rotation(x = 90f),
            scale = Scale(xScale, yScale, 1f),
            materialInstance = material,
            apply = { name = "almi_glasses_lens_$x"; isHittable = false },
        )
    }
    CubeNode(
        size = Size(x = 0.030f, y = 0.004f, z = 0.004f),
        position = Position(x = 0f, y = faceY, z = faceZ),
        materialInstance = material,
        apply = { name = "almi_glasses_bridge"; isHittable = false },
    )
}

@Composable
private fun io.github.sceneview.NodeScope.FacialHair3D(
    variant: String,
    material: MaterialInstance,
) {
    when (variant) {
        "moustacheFancy" -> {
            SphereNode(
                radius = 0.021f,
                position = Position(x = -0.016f, y = 1.595f, z = 0.108f),
                scale = Scale(1.15f, 0.33f, 0.25f),
                materialInstance = material,
                apply = { name = "almi_moustache_left"; isHittable = false },
            )
            SphereNode(
                radius = 0.021f,
                position = Position(x = 0.016f, y = 1.595f, z = 0.108f),
                scale = Scale(1.15f, 0.33f, 0.25f),
                materialInstance = material,
                apply = { name = "almi_moustache_right"; isHittable = false },
            )
        }
        else -> {
            val (radius, heightScale) = when (variant) {
                "beardLight" -> 0.046f to 0.34f
                "beardMedium" -> 0.058f to 0.45f
                else -> 0.072f to 0.58f
            }
            SphereNode(
                radius = radius,
                position = Position(x = 0f, y = 1.555f, z = 0.090f),
                scale = Scale(0.78f, heightScale, 0.24f),
                materialInstance = material,
                apply = { name = "almi_beard"; isHittable = false },
            )
        }
    }
}

private data class HairConfig(
    val asset: String,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f,
    val offsetY: Float = 0f,
    val offsetZ: Float = 0f,
)

private fun hairConfigFor(value: String): HairConfig = when (value) {
    "shortFlat" -> HairConfig(HAIR_CARDS_ASSET, 0.92f, 0.78f, 0.92f, -0.012f)
    "shortRound" -> HairConfig(HAIR_CARDS_ASSET, 0.98f, 0.84f, 0.98f, -0.008f)
    "shortCurly" -> HairConfig(HAIR_MEDIUM_ASSET, 1.02f, 0.76f, 1.02f, -0.004f)
    "shortWaved" -> HairConfig(HAIR_MEDIUM_ASSET, 0.98f, 0.82f, 1f)
    "theCaesar" -> HairConfig(HAIR_CARDS_ASSET, 0.96f, 0.72f, 0.94f, -0.016f, 0.002f)
    "sides" -> HairConfig(HAIR_CARDS_ASSET, 0.90f, 0.68f, 0.90f, -0.018f)
    "bob" -> HairConfig(HAIR_MEDIUM_ASSET, 0.96f, 0.92f, 0.98f)
    "bun" -> HairConfig(HAIR_MEDIUM_ASSET, 0.90f, 0.98f, 0.90f, 0.016f)
    "curvy" -> HairConfig(HAIR_LONG_ASSET, 1.04f, 1.02f, 1.04f)
    "straight01" -> HairConfig(HAIR_LONG_ASSET, 0.94f, 1.06f, 0.94f)
    "straight02" -> HairConfig(HAIR_LONG_ASSET, 0.98f, 1.10f, 0.96f)
    "longButNotTooLong" -> HairConfig(HAIR_LONG_ASSET, 1.00f, 1.14f, 1.00f)
    "bigHair" -> HairConfig(HAIR_LONG_ASSET, 1.13f, 1.07f, 1.13f, 0.008f)
    else -> HairConfig(HAIR_MEDIUM_ASSET)
}

private fun expressionWeights(appearance: AvatarAppearance): FloatArray {
    // Exported Vitruvian FACS order; unused targets remain at zero. If a head asset without morphs
    // is substituted later, the renderer safely ignores this through runCatching at the call site.
    val w = FloatArray(26)
    when (appearance.mouthVariant) {
        "smile" -> w[12] = 0.82f
        "twinkle" -> w[5] = 0.68f
        "serious" -> w[6] = 0.16f
        "eating" -> { w[0] = 0.24f; w[1] = 0.18f }
    }
    when (appearance.eyesVariant) {
        "happy", "squint" -> w[20] = 0.56f
        "surprised" -> { w[18] = 0.52f; w[19] = 0.52f }
        "wink" -> w[17] = 0.42f
    }
    when (appearance.eyebrowsVariant) {
        "raisedExcited" -> { w[13] = 0.56f; w[14] = 0.56f }
        "upDownNatural" -> { w[13] = 0.32f; w[16] = 0.24f }
    }
    return w
}

private fun ModelInstance.tintMaterials(
    rgb: FloatArray,
    names: List<String> = emptyList(),
) {
    materialInstances.forEach { material ->
        val matches = names.isEmpty() || names.any { token -> material.name.contains(token, ignoreCase = true) }
        if (matches) {
            runCatching {
                material.setParameter(
                    "baseColorFactor",
                    Colors.RgbaType.SRGB,
                    rgb[0], rgb[1], rgb[2], 1f,
                )
            }
        }
    }
}

private fun parseRgb(raw: String): FloatArray {
    val hex = raw.removePrefix("#").padStart(6, '0').takeLast(6)
    val value = hex.toLongOrNull(16) ?: 0x777777
    return floatArrayOf(
        ((value shr 16) and 0xFF) / 255f,
        ((value shr 8) and 0xFF) / 255f,
        (value and 0xFF) / 255f,
    )
}

private const val BODY_ASSET = "almi3d/vitruvian_body.glb"
private const val HEAD_ASSET = "almi3d/vitruvian_head.glb"
private const val HAIR_CARDS_ASSET = "almi3d/hairtool_cards.glb"
private const val HAIR_MEDIUM_ASSET = "almi3d/vitruvian_hair.glb"
private const val HAIR_LONG_ASSET = "almi3d/vitruvian_hair_rigged.glb"

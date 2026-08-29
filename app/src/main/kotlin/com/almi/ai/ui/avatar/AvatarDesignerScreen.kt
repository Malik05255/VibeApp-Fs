package com.almi.ai.ui.avatar

import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ThreeDRotation
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import kotlinx.coroutines.delay

private enum class AvatarCategory { SKIN, HAIR, HAIR_COLOR, ACCESSORY, FACE }

/**
 * v9 Filament avatar workshop.
 *
 * The first state behaves like a character-select screen: both locally-rendered avatars are visible,
 * gender is mandatory, the unselected character exits, and the selected character walks into the
 * center before customization controls become active.
 */
@Composable
fun AvatarDesignerScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onPresentation: (AvatarPresentation) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onSkinColor: (String) -> Unit,
    onAccessories: (String) -> Unit,
    onFacialHair: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onRandomize: () -> Unit,
    onComplete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<AvatarPresentation?>(null) }
    var category by remember { mutableStateOf(AvatarCategory.SKIN) }
    var controlsVisible by remember { mutableStateOf(false) }
    var maleRuntime by remember { mutableStateOf<AvatarFilamentRuntime?>(null) }
    var femaleRuntime by remember { mutableStateOf<AvatarFilamentRuntime?>(null) }

    LaunchedEffect(selected) {
        controlsVisible = false
        when (selected) {
            AvatarPresentation.MASCULINE -> maleRuntime?.playWalkIn(fromRight = false)
            AvatarPresentation.FEMININE -> femaleRuntime?.playWalkIn(fromRight = true)
            null -> Unit
        }
        if (selected != null) {
            delay(820)
            controlsVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A0F))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SpatialBackdrop()

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("ALMI / AVATAR LAB", color = Color(0xFF8EA1FF), style = MaterialTheme.typography.labelSmall)
            Text(
                if (selected == null) tr(language, "اختر شخصيتك", "Choose your character")
                else tr(language, "ابنِ نسختك الرقمية", "Build your digital self"),
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (selected == null) tr(language, "الاختيار إلزامي قبل التخصيص", "Choose male or female before customizing")
                else tr(language, "كل تغيير ينعكس مباشرة على مجسم Filament", "Every edit updates the Filament avatar live"),
                color = Color.White.copy(alpha = .56f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (controlsVisible) 510.dp else 650.dp)
                .align(Alignment.TopCenter)
                .padding(top = 88.dp),
        ) {
            val maleTarget by animateDpAsState(
                targetValue = when (selected) {
                    null -> -(maxWidth * .25f)
                    AvatarPresentation.MASCULINE -> 0.dp
                    AvatarPresentation.FEMININE -> -(maxWidth * .92f)
                },
                animationSpec = tween(760),
                label = "male-avatar-position",
            )
            val femaleTarget by animateDpAsState(
                targetValue = when (selected) {
                    null -> maxWidth * .25f
                    AvatarPresentation.FEMININE -> 0.dp
                    AvatarPresentation.MASCULINE -> maxWidth * .92f
                },
                animationSpec = tween(760),
                label = "female-avatar-position",
            )
            val viewportWidth = if (selected == null) maxWidth * .47f else maxWidth * .70f

            AvatarViewport(
                presentation = AvatarPresentation.MASCULINE,
                appearance = appearance.copy(presentation = AvatarPresentation.MASCULINE),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = maleTarget)
                    .width(viewportWidth)
                    .height(if (controlsVisible) 410.dp else 520.dp),
                onRuntime = { maleRuntime = it },
            )
            AvatarViewport(
                presentation = AvatarPresentation.FEMININE,
                appearance = appearance.copy(presentation = AvatarPresentation.FEMININE),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = femaleTarget)
                    .width(viewportWidth)
                    .height(if (controlsVisible) 410.dp else 520.dp),
                onRuntime = { femaleRuntime = it },
            )

            if (selected == null) {
                GenderPill(
                    label = tr(language, "ذكر", "Male"),
                    modifier = Modifier.align(Alignment.TopCenter).offset(x = -(maxWidth * .25f), y = 4.dp),
                    onClick = {
                        onPresentation(AvatarPresentation.MASCULINE)
                        selected = AvatarPresentation.MASCULINE
                    },
                )
                GenderPill(
                    label = tr(language, "أنثى", "Female"),
                    modifier = Modifier.align(Alignment.TopCenter).offset(x = maxWidth * .25f, y = 4.dp),
                    onClick = {
                        onPresentation(AvatarPresentation.FEMININE)
                        selected = AvatarPresentation.FEMININE
                    },
                )
            } else {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconButton(onClick = {
                        if (selected == AvatarPresentation.MASCULINE) maleRuntime?.rotatePreview() else femaleRuntime?.rotatePreview()
                    }) {
                        Icon(Icons.Outlined.ThreeDRotation, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onRandomize) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(220)) + slideInVertically(tween(360)) { it / 3 },
            exit = fadeOut(tween(160)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
                color = scheme.surface.copy(alpha = .97f),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryChip(tr(language, "البشرة", "Skin"), category == AvatarCategory.SKIN) { category = AvatarCategory.SKIN }
                        CategoryChip(tr(language, "الشعر", "Hair"), category == AvatarCategory.HAIR) { category = AvatarCategory.HAIR }
                        CategoryChip(tr(language, "لون الشعر", "Hair color"), category == AvatarCategory.HAIR_COLOR) { category = AvatarCategory.HAIR_COLOR }
                        CategoryChip(tr(language, "إكسسوارات", "Accessories"), category == AvatarCategory.ACCESSORY) { category = AvatarCategory.ACCESSORY }
                        CategoryChip(tr(language, "الملامح", "Face"), category == AvatarCategory.FACE) { category = AvatarCategory.FACE }
                    }

                    when (category) {
                        AvatarCategory.SKIN -> Swatches(
                            values = listOf("F6D7C3", "E9B992", "D89A72", "BC7752", "925B3D", "67402F"),
                            current = appearance.skinColor,
                            onSelect = onSkinColor,
                        )
                        AvatarCategory.HAIR -> OptionRow(
                            options = listOf(
                                "bald" to tr(language, "بدون", "Bald"),
                                "shortFlat" to tr(language, "قصير", "Short"),
                                "shortCurly" to tr(language, "كيرلي", "Curly"),
                                "bob" to tr(language, "بوب", "Bob"),
                                "longButNotTooLong" to tr(language, "طويل", "Long"),
                            ),
                            current = appearance.hairVariant,
                            onSelect = onHair,
                        )
                        AvatarCategory.HAIR_COLOR -> Swatches(
                            values = listOf("171312", "2C1B18", "5D382C", "8B4F2A", "C58A52", "D8C29A"),
                            current = appearance.hairColor,
                            onSelect = onHairColor,
                        )
                        AvatarCategory.ACCESSORY -> OptionRow(
                            options = listOf(
                                "none" to tr(language, "بدون", "None"),
                                "round" to tr(language, "نظارة دائرية", "Round glasses"),
                                "wayfarers" to tr(language, "نظارة مربعة", "Square glasses"),
                                "cap" to tr(language, "كاب", "Cap"),
                            ),
                            current = appearance.accessoriesVariant,
                            onSelect = onAccessories,
                        )
                        AvatarCategory.FACE -> FaceControls(
                            language = language,
                            appearance = appearance,
                            onEyes = onEyes,
                            onEyebrows = onEyebrows,
                            onMouth = onMouth,
                            onFacialHair = onFacialHair,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            modifier = Modifier.weight(.34f).height(54.dp).clickable {
                                controlsVisible = false
                                selected = null
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = scheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(tr(language, "تغيير الجنس", "Change"), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.weight(.66f).height(54.dp),
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                            Spacer(Modifier.width(7.dp))
                            Text(tr(language, "حفظ الأفاتار", "Save avatar"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarViewport(
    presentation: AvatarPresentation,
    appearance: AvatarAppearance,
    modifier: Modifier,
    onRuntime: (AvatarFilamentRuntime) -> Unit,
) {
    var runtime by remember(presentation) { mutableStateOf<AvatarFilamentRuntime?>(null) }
    DisposableEffect(presentation) {
        onDispose { runtime?.stop() }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF0B101A),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(34.dp)),
            factory = { context ->
                SurfaceView(context).also { surface ->
                    AvatarFilamentRuntime(
                        context = context,
                        surfaceView = surface,
                        initialPresentation = presentation,
                        initialAppearance = appearance,
                    ).also {
                        runtime = it
                        onRuntime(it)
                        it.initialize()
                        it.start()
                    }
                }
            },
            update = {
                runtime?.update(presentation, appearance)
            },
        )
    }
}

@Composable
private fun GenderPill(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.White,
        shadowElevation = 10.dp,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            color = Color(0xFF10131A),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SpatialBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 118.dp)
                .size(330.dp)
                .background(Color(0xFF23305C).copy(alpha = .22f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 100.dp)
                .size(260.dp)
                .background(Color(0xFF7A4B94).copy(alpha = .12f), CircleShape),
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) scheme.primary else scheme.surfaceVariant,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun Swatches(values: List<String>, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        values.forEach { value ->
            val selected = current.equals(value, ignoreCase = true)
            Surface(
                modifier = Modifier.size(48.dp).clickable { onSelect(value) },
                shape = CircleShape,
                color = hex(value),
                border = BorderStroke(
                    if (selected) 3.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {}
        }
    }
}

@Composable
private fun OptionRow(options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val selected = current == value
            Surface(
                modifier = Modifier.clickable { onSelect(value) },
                shape = RoundedCornerShape(15.dp),
                color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun FaceControls(
    language: String,
    appearance: AvatarAppearance,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onFacialHair: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OptionRow(
            options = listOf(
                "default" to tr(language, "عين طبيعية", "Natural eyes"),
                "wide" to tr(language, "واسعة", "Wide"),
                "sharp" to tr(language, "حادة", "Sharp"),
            ),
            current = appearance.eyesVariant,
            onSelect = onEyes,
        )
        OptionRow(
            options = listOf(
                "default" to tr(language, "حاجب طبيعي", "Natural brow"),
                "defined" to tr(language, "محدد", "Defined"),
            ),
            current = appearance.eyebrowsVariant,
            onSelect = onEyebrows,
        )
        OptionRow(
            options = listOf(
                "neutral" to tr(language, "محايد", "Neutral"),
                "smile" to tr(language, "ابتسامة", "Smile"),
                "full" to tr(language, "شفاه ممتلئة", "Full lips"),
            ),
            current = appearance.mouthVariant,
            onSelect = onMouth,
        )
        if (appearance.presentation == AvatarPresentation.MASCULINE) {
            OptionRow(
                options = listOf(
                    "none" to tr(language, "بدون لحية", "No beard"),
                    "beardLight" to tr(language, "لحية خفيفة", "Light beard"),
                ),
                current = appearance.facialHairVariant,
                onSelect = onFacialHair,
            )
        }
    }
}

private fun hex(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor("#$value"))
}.getOrDefault(Color.Gray)

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

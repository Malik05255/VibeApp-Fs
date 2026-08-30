package com.almi.ai.ui.v12

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.R
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile

private enum class EditorialAvatarStage { CHOOSE, EDIT }

/**
 * Spatial Editorial entry for the live Digital Human editor.
 *
 * This replaces the old cyan/pink scanner chooser without changing any Digital Human asset,
 * texture, PBR material or renderer setting. The static identity portraits remain the original
 * high-detail authored previews; only information hierarchy and interaction styling change.
 */
@Composable
internal fun V12EditorialAvatarScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onPresentation: (AvatarPresentation) -> Unit,
    onHair: (String) -> Unit,
    onHairColor: (String) -> Unit,
    onSkinColor: (String) -> Unit,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(EditorialAvatarStage.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val stage = runCatching { EditorialAvatarStage.valueOf(stageName) }.getOrDefault(EditorialAvatarStage.CHOOSE)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "editorial-avatar-entry",
    ) { destination ->
        when (destination) {
            EditorialAvatarStage.CHOOSE -> EditorialIdentityChooser(
                language = language,
                selected = selected,
                onSelect = { selectedName = it.name },
                onBack = onBack,
                onNext = {
                    selected?.let(onPresentation)
                    stageName = EditorialAvatarStage.EDIT.name
                },
            )

            EditorialAvatarStage.EDIT -> V12AvatarDigitalLabScreen(
                language = language,
                appearance = appearance.copy(presentation = selected ?: appearance.presentation),
                bodyProfile = bodyProfile,
                digitalTwinSnapshotUri = digitalTwinSnapshotUri,
                onPresentation = onPresentation,
                onHair = onHair,
                onHairColor = onHairColor,
                onSkinColor = onSkinColor,
                onEyes = onEyes,
                onEyebrows = onEyebrows,
                onMouth = onMouth,
                onBack = { stageName = EditorialAvatarStage.CHOOSE.name },
                onComplete = onComplete,
            )
        }
    }
}

@Composable
private fun EditorialIdentityChooser(
    language: String,
    selected: AvatarPresentation?,
    onSelect: (AvatarPresentation) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.primaryContainer.copy(alpha = .28f),
                        scheme.background,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp).clickable(onClick = onBack),
                    shape = CircleShape,
                    color = scheme.surface,
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = scheme.onSurface, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ALMI / IDENTITY",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (language == "ar") "الهوية الرقمية" else "Digital identity",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.size(46.dp))
            }

            Spacer(Modifier.height(30.dp))
            Text(
                if (language == "ar") "اختر نقطة البداية.\nالتفاصيل تصبح لك." else "Choose a starting point.\nMake every detail yours.",
                color = scheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
                textAlign = if (language == "ar") TextAlign.Right else TextAlign.Left,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (language == "ar") "الاختيار يحدد البنية الأولية فقط؛ الشعر والملامح والألوان قابلة للتخصيص بعد ذلك." else "This only sets the initial structure. Hair, features and colors remain fully editable.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(22.dp))

            val gap = 10.dp
            val cardWidth = (maxWidth - 40.dp - gap) / 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                EditorialIdentityCard(
                    width = cardWidth,
                    height = 390.dp,
                    presentation = AvatarPresentation.MASCULINE,
                    selected = selected == AvatarPresentation.MASCULINE,
                    language = language,
                    accent = scheme.tertiary,
                    onClick = { onSelect(AvatarPresentation.MASCULINE) },
                )
                EditorialIdentityCard(
                    width = cardWidth,
                    height = 390.dp,
                    presentation = AvatarPresentation.FEMININE,
                    selected = selected == AvatarPresentation.FEMININE,
                    language = language,
                    accent = scheme.secondary,
                    onClick = { onSelect(AvatarPresentation.FEMININE) },
                )
            }

            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .clickable(enabled = selected != null, onClick = onNext),
                shape = RoundedCornerShape(24.dp),
                color = if (selected == null) scheme.surfaceVariant else scheme.primary,
                border = BorderStroke(
                    1.dp,
                    if (selected == null) scheme.outlineVariant else scheme.primary,
                ),
                shadowElevation = if (selected == null) 0.dp else 8.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            if (language == "ar") "ابدأ التخصيص" else "Start personalizing",
                            color = if (selected == null) scheme.onSurfaceVariant else scheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (selected == null) {
                                if (language == "ar") "اختر هوية أولًا" else "Choose an identity first"
                            } else {
                                if (language == "ar") "انتقل إلى الاستوديو الرقمي" else "Enter the Digital Human studio"
                            },
                            color = if (selected == null) scheme.onSurfaceVariant.copy(alpha = .72f) else scheme.onPrimary.copy(alpha = .70f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        "↗",
                        color = if (selected == null) scheme.onSurfaceVariant else scheme.onPrimary,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditorialIdentityCard(
    width: Dp,
    height: Dp,
    presentation: AvatarPresentation,
    selected: Boolean,
    language: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.width(width).height(height).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surface,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) accent else scheme.outlineVariant,
        ),
        shadowElevation = if (selected) 10.dp else 2.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            EditorialIdentityBitmap(
                presentation = presentation,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Transparent,
                            scheme.onBackground.copy(alpha = .52f),
                        ),
                    ),
                ),
            )

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(11.dp),
                shape = RoundedCornerShape(999.dp),
                color = scheme.surface.copy(alpha = .88f),
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .8f)),
            ) {
                Text(
                    if (presentation == AvatarPresentation.FEMININE) {
                        if (language == "ar") "أنثى" else "FEMININE"
                    } else {
                        if (language == "ar") "ذكر" else "MASCULINE"
                    },
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(13.dp),
            ) {
                if (selected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(accent, CircleShape))
                        Text(
                            if (language == "ar") "تم الاختيار" else "SELECTED",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                }
                Text(
                    if (presentation == AvatarPresentation.FEMININE) {
                        if (language == "ar") "هوية ناعمة قابلة للتشكيل" else "Soft, fully shapeable identity"
                    } else {
                        if (language == "ar") "هوية متوازنة قابلة للتشكيل" else "Balanced, fully shapeable identity"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EditorialIdentityBitmap(
    presentation: AvatarPresentation,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(presentation) {
        val ids = if (presentation == AvatarPresentation.FEMININE) {
            intArrayOf(
                R.raw.almi_v12_female_hero_00,
                R.raw.almi_v12_female_hero_01,
                R.raw.almi_v12_female_hero_02,
                R.raw.almi_v12_female_hero_03,
                R.raw.almi_v12_female_hero_04,
                R.raw.almi_v12_female_hero_05,
                R.raw.almi_v12_female_hero_06,
            )
        } else {
            intArrayOf(
                R.raw.almi_v12_male_hero_00,
                R.raw.almi_v12_male_hero_01,
                R.raw.almi_v12_male_hero_02,
                R.raw.almi_v12_male_hero_03,
                R.raw.almi_v12_male_hero_04,
                R.raw.almi_v12_male_hero_05,
                R.raw.almi_v12_male_hero_06,
            )
        }
        val encoded = buildString {
            ids.forEach { id ->
                context.resources.openRawResource(id).bufferedReader().use { append(it.readText()) }
            }
        }
        runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            V12Glyph(
                type = V12GlyphType.AVATAR,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

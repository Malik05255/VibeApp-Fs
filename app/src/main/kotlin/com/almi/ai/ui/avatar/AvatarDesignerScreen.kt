package com.almi.ai.ui.avatar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.roundToInt

/** Image-only anime avatar editor. Every option redraws the same portrait locally. */
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "ALMI / AVATAR",
                color = scheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                tr(language, "اصنع أفاتارك", "Create your avatar"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                tr(
                    language,
                    "اختر من صور قليلة وواضحة. يتغير العنصر الذي اخترته فقط وتبقى هوية الأفاتار نفسها.",
                    "Choose from a few clear images. Only the selected feature changes while the avatar identity stays the same.",
                ),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            shadowElevation = 3.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.88f)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(24.dp)),
            ) {
                AnimeAvatarPortrait(
                    appearance = appearance,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(11.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        tr(language, "معاينة الأفاتار", "AVATAR PREVIEW"),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        SectionTitle(tr(language, "الشخصية", "Character"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IdentityCard(
                label = tr(language, "ذكر", "Male"),
                appearance = appearance.copy(
                    presentation = AvatarPresentation.MASCULINE,
                    hairVariant = if (appearance.hairVariant in feminineHair) "shortFlat" else appearance.hairVariant,
                    facialHairVariant = "none",
                ),
                selected = appearance.presentation == AvatarPresentation.MASCULINE,
                modifier = Modifier.weight(1f),
                onClick = { onPresentation(AvatarPresentation.MASCULINE) },
            )
            IdentityCard(
                label = tr(language, "أنثى", "Female"),
                appearance = appearance.copy(
                    presentation = AvatarPresentation.FEMININE,
                    hairVariant = if (appearance.hairVariant in masculineHair) "bob" else appearance.hairVariant,
                    facialHairVariant = "none",
                ),
                selected = appearance.presentation == AvatarPresentation.FEMININE,
                modifier = Modifier.weight(1f),
                onClick = { onPresentation(AvatarPresentation.FEMININE) },
            )
        }

        PortraitChoiceRow(
            title = tr(language, "الشعر", "Hair"),
            options = if (appearance.presentation == AvatarPresentation.FEMININE) {
                listOf(
                    AvatarOption("bob", tr(language, "بوب", "Bob")),
                    AvatarOption("shortCurly", tr(language, "كيرلي قصير", "Short curly")),
                    AvatarOption("longButNotTooLong", tr(language, "طويل", "Long")),
                    AvatarOption("shortFlat", tr(language, "قصير", "Short")),
                )
            } else {
                listOf(
                    AvatarOption("shortFlat", tr(language, "قصير", "Short")),
                    AvatarOption("shortCurly", tr(language, "كيرلي قصير", "Short curly")),
                    AvatarOption("bob", tr(language, "متوسط", "Medium")),
                )
            },
            current = appearance.hairVariant,
            previewFor = { value -> appearance.copy(hairVariant = value) },
            onSelect = onHair,
        )

        ColorChoiceRow(
            title = tr(language, "لون الشعر", "Hair colour"),
            values = listOf("241A19", "5D382C", "A45C32", "D8B06A"),
            current = appearance.hairColor,
            onSelect = onHairColor,
        )

        ColorChoiceRow(
            title = tr(language, "لون البشرة", "Skin tone"),
            values = listOf("F6D5C1", "E7B58E", "C9855B", "855134"),
            current = appearance.skinColor,
            onSelect = onSkinColor,
        )

        PortraitChoiceRow(
            title = tr(language, "النظارات", "Glasses"),
            options = listOf(
                AvatarOption("none", tr(language, "بدون", "None")),
                AvatarOption("round", tr(language, "دائرية", "Round")),
                AvatarOption("wayfarers", tr(language, "مربعة", "Square")),
            ),
            current = appearance.accessoriesVariant,
            previewFor = { value -> appearance.copy(accessoriesVariant = value) },
            onSelect = onAccessories,
        )

        if (appearance.presentation == AvatarPresentation.MASCULINE) {
            PortraitChoiceRow(
                title = tr(language, "اللحية", "Facial hair"),
                options = listOf(
                    AvatarOption("none", tr(language, "بدون", "None")),
                    AvatarOption("beardLight", tr(language, "خفيفة", "Light")),
                    AvatarOption("moustacheFancy", tr(language, "شارب", "Moustache")),
                ),
                current = appearance.facialHairVariant,
                previewFor = { value -> appearance.copy(facialHairVariant = value) },
                onSelect = onFacialHair,
            )
        }

        BodySyncCard(language, bodyProfile)

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(tr(language, "حفظ والمتابعة", "Save & continue"), fontWeight = FontWeight.SemiBold)
        }

        Text(
            tr(language, "يمكن تعديل الأفاتار لاحقًا بدون تغيير قياسات الجسم.", "You can edit the avatar later without changing body measurements."),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    }
}

private data class AvatarOption(val value: String, val label: String)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun IdentityCard(
    label: String,
    appearance: AvatarAppearance,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp),
        color = scheme.surface,
        border = BorderStroke(1.5.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimeAvatarPortrait(
                appearance = appearance,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.92f).clip(RoundedCornerShape(16.dp)),
            )
            Text(
                label,
                modifier = Modifier.padding(vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) scheme.tertiary else scheme.onSurface,
            )
        }
    }
}

@Composable
private fun PortraitChoiceRow(
    title: String,
    options: List<AvatarOption>,
    current: String,
    previewFor: (String) -> AvatarAppearance,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val selected = current == option.value
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(width = 112.dp, height = 126.dp).clickable { onSelect(option.value) },
                        shape = RoundedCornerShape(20.dp),
                        color = scheme.surface,
                        border = BorderStroke(1.5.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
                    ) {
                        Box(Modifier.padding(5.dp)) {
                            AnimeAvatarPortrait(
                                appearance = previewFor(option.value),
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)),
                            )
                            if (selected) {
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(23.dp),
                                    shape = CircleShape,
                                    color = scheme.tertiary,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        option.label,
                        modifier = Modifier.padding(top = 6.dp),
                        color = if (selected) scheme.tertiary else scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceRow(
    title: String,
    values: List<String>,
    current: String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            values.forEach { hex ->
                val selected = current.equals(hex, ignoreCase = true)
                val rgb = hex.toLongOrNull(16) ?: 0x777777
                Surface(
                    modifier = Modifier.size(54.dp).clickable { onSelect(hex) },
                    shape = CircleShape,
                    color = Color((0xFF000000L or rgb).toULong()),
                    border = BorderStroke(
                        if (selected) 3.dp else 1.dp,
                        if (selected) scheme.tertiary else scheme.outlineVariant,
                    ),
                ) {}
            }
        }
    }
}

@Composable
private fun BodySyncCard(language: String, profile: BodyProfile) {
    val scheme = MaterialTheme.colorScheme
    val facts = buildList {
        if (profile.hasExplicitHeight) add("${profile.heightCentimeters.roundToInt()} cm")
        if (profile.hasExplicitWeight) add("${profile.weightKilograms.roundToInt()} kg")
        if (profile.measurementsInches.isNotEmpty()) add("${profile.measurementsInches.size} ${tr(language, "قياسات", "measurements")}")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.tertiaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                tr(language, "الجسم متزامن", "BODY SYNCED"),
                color = scheme.tertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (facts.isEmpty()) tr(language, "أضف قياساتك من شاشة الجسم", "Add measurements in Body Map") else facts.joinToString("  •  "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                tr(language, "هذه القيم تنتقل لتجربة الملابس؛ شكل الوجه والشعر لا يغير قياسات الجسم.", "These values flow into try-on; face and hair styling never changes body measurements."),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val masculineHair = setOf("shortFlat", "shortCurly")
private val feminineHair = setOf("bob", "longButNotTooLong")

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

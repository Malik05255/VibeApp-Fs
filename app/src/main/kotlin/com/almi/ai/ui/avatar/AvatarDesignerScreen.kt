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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.roundToInt

/**
 * Deliberately simple image-only avatar creator.
 * No SceneView/Filament resources are allocated on this route.
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
                tr(language, "اختيارات قليلة وواضحة. جسمك يبقى مرتبطًا بقياساتك تلقائيًا.", "A few clear choices. Your body stays synced to your measurements automatically."),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.92f)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = appearance.previewUrl(720),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = scheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        tr(language, "معاينة ثابتة", "STATIC PREVIEW"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
                imageUrl = appearance.copy(
                    presentation = AvatarPresentation.MASCULINE,
                    hairVariant = "shortFlat",
                    facialHairVariant = "none",
                ).previewUrl(360),
                selected = appearance.presentation == AvatarPresentation.MASCULINE,
                modifier = Modifier.weight(1f),
                onClick = { onPresentation(AvatarPresentation.MASCULINE) },
            )
            IdentityCard(
                label = tr(language, "أنثى", "Female"),
                imageUrl = appearance.copy(
                    presentation = AvatarPresentation.FEMININE,
                    hairVariant = "bob",
                    facialHairVariant = "none",
                ).previewUrl(360),
                selected = appearance.presentation == AvatarPresentation.FEMININE,
                modifier = Modifier.weight(1f),
                onClick = { onPresentation(AvatarPresentation.FEMININE) },
            )
        }

        ImageChoiceRow(
            title = tr(language, "الشعر", "Hair"),
            values = listOf("shortFlat", "shortCurly", "bob", "longButNotTooLong"),
            current = appearance.hairVariant,
            imageFor = { appearance.copy(hairVariant = it).previewUrl(280) },
            onSelect = onHair,
        )

        ColorChoiceRow(
            title = tr(language, "لون الشعر", "Hair colour"),
            values = listOf("2C1B18", "724133", "A55728", "E6C17A"),
            current = appearance.hairColor,
            onSelect = onHairColor,
        )

        ColorChoiceRow(
            title = tr(language, "لون البشرة", "Skin tone"),
            values = listOf("F8D5C2", "EDB98A", "D08B5B", "8D5524"),
            current = appearance.skinColor,
            onSelect = onSkinColor,
        )

        ImageChoiceRow(
            title = tr(language, "النظارات", "Glasses"),
            values = listOf("none", "round", "wayfarers"),
            current = appearance.accessoriesVariant,
            imageFor = { appearance.copy(accessoriesVariant = it).previewUrl(280) },
            onSelect = onAccessories,
        )

        if (appearance.presentation == AvatarPresentation.MASCULINE) {
            ImageChoiceRow(
                title = tr(language, "اللحية", "Facial hair"),
                values = listOf("none", "beardLight", "moustacheFancy"),
                current = appearance.facialHairVariant,
                imageFor = { appearance.copy(facialHairVariant = it).previewUrl(280) },
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
            tr(language, "يمكن تعديل الأفاتار لاحقًا بدون تغيير أي قياس من جسمك.", "You can edit the avatar later without changing any body measurement."),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
    }
}

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
    imageUrl: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        border = BorderStroke(1.5.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(15.dp)),
                contentScale = ContentScale.Crop,
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
private fun ImageChoiceRow(
    title: String,
    values: List<String>,
    current: String,
    imageFor: (String) -> String,
    onSelect: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle(title)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            values.forEach { value ->
                val selected = current == value
                Surface(
                    modifier = Modifier.size(92.dp).clickable { onSelect(value) },
                    shape = RoundedCornerShape(18.dp),
                    color = scheme.surface,
                    border = BorderStroke(1.5.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
                ) {
                    Box(Modifier.padding(5.dp)) {
                        AsyncImage(
                            model = imageFor(value),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (selected) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                                shape = CircleShape,
                                color = scheme.tertiary,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
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
                Surface(
                    modifier = Modifier.size(52.dp).clickable { onSelect(hex) },
                    shape = CircleShape,
                    color = Color(hex.toLong(16) or 0xFF000000),
                    border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) scheme.tertiary else scheme.outlineVariant),
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
                tr(language, "هذه القيم تستخدم في تجربة الملابس، وليست خيارات شكل للأفاتار.", "These values are used for clothing fit, not as avatar styling options."),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

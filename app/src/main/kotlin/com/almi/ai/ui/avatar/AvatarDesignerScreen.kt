package com.almi.ai.ui.avatar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyMeasurePoint
import com.almi.ai.data.preferences.BodyProfile
import kotlin.math.roundToInt

/**
 * ALMI v7 image-first avatar workshop.
 *
 * Facial appearance is edited visually while the body remains linked to the user's measurement
 * digital twin. Every option card is a rendered image of the same deterministic avatar seed with
 * only the requested feature changed.
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("ALMI / AVATAR WORKSHOP", style = MaterialTheme.typography.labelSmall, color = scheme.error, fontWeight = FontWeight.Black)
                Text(tr(language, "اصنع أفاتارك", "Create your avatar"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = onRandomize) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(tr(language, "هوية جديدة", "New face"))
            }
        }

        Text(
            tr(
                language,
                "اختر كل تفصيلة بالصور. جسم الأفاتار يبقى مرتبطًا بالطول والوزن والقياسات التي أدخلتها في التوأم الرقمي.",
                "Choose every detail visually. The avatar body stays linked to the height, weight and measurements from your digital twin.",
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )

        AvatarHero(
            language = language,
            appearance = appearance,
            bodyProfile = bodyProfile,
            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
        )

        VisualSection(
            title = tr(language, "الشكل الأساسي", "Base look"),
            subtitle = tr(language, "اختر نقطة البداية. يمكنك تعديل كل شيء بعدها.", "Choose a starting look. Every feature can still be changed."),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LargeChoiceCard(
                    label = tr(language, "ذكر", "Masculine"),
                    imageUrl = appearance.copy(
                        presentation = AvatarPresentation.MASCULINE,
                        hairVariant = "shortFlat",
                        facialHairVariant = "none",
                    ).previewUrl(420),
                    selected = appearance.presentation == AvatarPresentation.MASCULINE,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresentation(AvatarPresentation.MASCULINE) },
                )
                LargeChoiceCard(
                    label = tr(language, "أنثى", "Feminine"),
                    imageUrl = appearance.copy(
                        presentation = AvatarPresentation.FEMININE,
                        hairVariant = "bob",
                        facialHairVariant = "none",
                    ).previewUrl(420),
                    selected = appearance.presentation == AvatarPresentation.FEMININE,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresentation(AvatarPresentation.FEMININE) },
                )
            }
        }

        VisualPickerSection(
            title = tr(language, "الشعر", "Hair"),
            subtitle = tr(language, "طويل، قصير، مموج، مربوط وغيرها — اضغط الصورة لترى التغيير فورًا.", "Long, short, wavy, tied and more — tap a picture to apply it instantly."),
            options = hairOptions,
            currentValue = appearance.hairVariant,
            imageFor = { value -> appearance.copy(hairVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onHair,
        )

        VisualPickerSection(
            title = tr(language, "لون الشعر", "Hair color"),
            subtitle = tr(language, "كل لون يظهر على نفس تسريحة الأفاتار الحالية.", "Every color is previewed on your current hairstyle."),
            options = hairColors,
            currentValue = appearance.hairColor,
            imageFor = { value -> appearance.copy(hairColor = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onHairColor,
        )

        VisualPickerSection(
            title = tr(language, "لون البشرة", "Skin tone"),
            subtitle = tr(language, "اختر الدرجة بصريًا بدل أسماء مجردة.", "Choose the tone visually instead of from abstract names."),
            options = skinTones,
            currentValue = appearance.skinColor,
            imageFor = { value -> appearance.copy(skinColor = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onSkinColor,
        )

        VisualPickerSection(
            title = tr(language, "النظارات", "Glasses"),
            subtitle = tr(language, "بدون نظارة أو عدة أشكال مرئية.", "No glasses or several visible frame styles."),
            options = glassesOptions,
            currentValue = appearance.accessoriesVariant,
            imageFor = { value -> appearance.copy(accessoriesVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onAccessories,
        )

        VisualPickerSection(
            title = tr(language, "اللحية والشارب", "Facial hair"),
            subtitle = tr(language, "اختيار اختياري ويمكن ترك الوجه بدون لحية.", "Optional; the face can stay clean-shaven."),
            options = facialHairOptions,
            currentValue = appearance.facialHairVariant,
            imageFor = { value -> appearance.copy(facialHairVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onFacialHair,
        )

        VisualPickerSection(
            title = tr(language, "العينان", "Eyes"),
            subtitle = tr(language, "تعبير العين يتغير على نفس الوجه.", "Eye expression changes on the same face."),
            options = eyeOptions,
            currentValue = appearance.eyesVariant,
            imageFor = { value -> appearance.copy(eyesVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onEyes,
        )

        VisualPickerSection(
            title = tr(language, "الحواجب", "Eyebrows"),
            subtitle = tr(language, "تفاصيل صغيرة لكنها تغير الشخصية بوضوح.", "Small detail, strong effect on expression."),
            options = eyebrowOptions,
            currentValue = appearance.eyebrowsVariant,
            imageFor = { value -> appearance.copy(eyebrowsVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onEyebrows,
        )

        VisualPickerSection(
            title = tr(language, "تعبير الفم", "Mouth expression"),
            subtitle = tr(language, "اختر ابتسامة أو تعبيرًا هادئًا أو غيره.", "Choose a smile, neutral expression, and more."),
            options = mouthOptions,
            currentValue = appearance.mouthVariant,
            imageFor = { value -> appearance.copy(mouthVariant = value).previewUrl(320) },
            labelFor = { option -> if (language == "ar") option.ar else option.en },
            onSelect = onMouth,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = scheme.primaryContainer,
        ) {
            Row(
                Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Straighten, contentDescription = null, tint = scheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(tr(language, "الجسم مقفول على قياساتك", "Body locked to your measurements"), fontWeight = FontWeight.Black)
                    Text(
                        tr(
                            language,
                            "تغيير الشعر أو البشرة أو النظارات لن ينحف الجسم أو يكبره. قياسات التوأم الرقمي هي المرجع.",
                            "Hair, skin tone and glasses never slim or enlarge the body. Your digital-twin measurements remain the reference.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp),
        ) {
            Icon(Icons.Outlined.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(tr(language, "حفظ أفاتاري والمتابعة", "Save my avatar & continue"), fontWeight = FontWeight.Black)
        }

        Text(
            tr(
                language,
                "يمكنك الرجوع وتعديل الأفاتار لاحقًا دون فقد قياسات الجسم.",
                "You can edit the avatar later without losing body measurements.",
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AvatarHero(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1.15f).aspectRatio(0.82f),
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.55f),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        AnimatedContent(
                            targetState = appearance.previewUrl(),
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "avatar-live-preview",
                        ) { imageUrl ->
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.55f),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                                Text(tr(language, "معاينة حية", "LIVE"), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.weight(0.82f).aspectRatio(0.82f),
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (digitalTwinSnapshotUri != null) {
                            AsyncImage(
                                model = digitalTwinSnapshotUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Outlined.Straighten, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                Text(tr(language, "جسم التوأم\nالرقمي", "Digital-twin\nbody"), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = scheme.surface.copy(alpha = 0.90f),
                        ) {
                            Text(
                                tr(language, "BODY LINK", "BODY LINK"),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = scheme.primary,
                            )
                        }
                    }
                }
            }

            BodyFacts(language, bodyProfile)
        }
    }
}

@Composable
private fun BodyFacts(language: String, profile: BodyProfile) {
    val values = buildList {
        if (profile.hasExplicitHeight) add(tr(language, "${profile.heightCentimeters.roundToInt()} سم", "${profile.heightCentimeters.roundToInt()} cm"))
        if (profile.hasExplicitWeight) add(tr(language, "${profile.weightKilograms.roundToInt()} كجم", "${profile.weightKilograms.roundToInt()} kg"))
        profile.measurementsInches[BodyMeasurePoint.CHEST]?.let { add(tr(language, "صدر ${(it * 2.54f).roundToInt()} سم", "Chest ${(it * 2.54f).roundToInt()} cm")) }
        profile.measurementsInches[BodyMeasurePoint.WAIST]?.let { add(tr(language, "خصر ${(it * 2.54f).roundToInt()} سم", "Waist ${(it * 2.54f).roundToInt()} cm")) }
        profile.measurementsInches[BodyMeasurePoint.HIPS]?.let { add(tr(language, "ورك ${(it * 2.54f).roundToInt()} سم", "Hips ${(it * 2.54f).roundToInt()} cm")) }
    }
    if (values.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        values.forEach { value ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            ) {
                Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VisualSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun VisualPickerSection(
    title: String,
    subtitle: String,
    options: List<VisualOption>,
    currentValue: String,
    imageFor: (String) -> String,
    labelFor: (VisualOption) -> String,
    onSelect: (String) -> Unit,
) {
    VisualSection(title, subtitle) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            options.forEach { option ->
                ImageChoiceCard(
                    label = labelFor(option),
                    imageUrl = imageFor(option.value),
                    selected = option.value == currentValue,
                    onClick = { onSelect(option.value) },
                )
            }
        }
    }
}

@Composable
private fun LargeChoiceCard(
    label: String,
    imageUrl: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(2.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
            Text(label, modifier = Modifier.padding(10.dp), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ImageChoiceCard(
    label: String,
    imageUrl: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(width = 112.dp, height = 142.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(2.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                if (selected) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        shape = CircleShape,
                        color = scheme.primary,
                    ) {
                        Icon(Icons.Outlined.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.padding(4.dp).size(14.dp))
                    }
                }
            }
            Text(
                label,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 7.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            )
        }
    }
}

private data class VisualOption(val value: String, val ar: String, val en: String)

private val hairOptions = listOf(
    VisualOption("shortFlat", "قصير ناعم", "Short flat"),
    VisualOption("shortRound", "قصير دائري", "Short round"),
    VisualOption("shortCurly", "قصير كيرلي", "Short curly"),
    VisualOption("shortWaved", "قصير مموج", "Short wavy"),
    VisualOption("theCaesar", "قيصر", "Caesar"),
    VisualOption("sides", "جوانب قصيرة", "Short sides"),
    VisualOption("bob", "بوب", "Bob"),
    VisualOption("bun", "كعكة", "Bun"),
    VisualOption("curvy", "طويل مموج", "Long wavy"),
    VisualOption("straight01", "طويل مستقيم 1", "Long straight 1"),
    VisualOption("straight02", "طويل مستقيم 2", "Long straight 2"),
    VisualOption("longButNotTooLong", "طويل", "Long"),
    VisualOption("bigHair", "كثيف", "Voluminous"),
)

private val hairColors = listOf(
    VisualOption("191919", "أسود", "Black"),
    VisualOption("4A312C", "بني داكن", "Dark brown"),
    VisualOption("8B5A2B", "بني", "Brown"),
    VisualOption("D6B370", "أشقر", "Blonde"),
    VisualOption("D24B3A", "أحمر", "Red"),
    VisualOption("B7B7B7", "فضي", "Silver"),
    VisualOption("D88FB3", "وردي", "Pink"),
)

private val skinTones = listOf(
    VisualOption("FCE4D6", "فاتح جدًا", "Very light"),
    VisualOption("F8D5C2", "فاتح", "Light"),
    VisualOption("E6B894", "قمحي", "Warm"),
    VisualOption("C98E68", "متوسط", "Medium"),
    VisualOption("9B6446", "أسمر", "Brown"),
    VisualOption("6B4430", "داكن", "Deep"),
)

private val glassesOptions = listOf(
    VisualOption("none", "بدون", "None"),
    VisualOption("prescription01", "طبية 1", "Optical 1"),
    VisualOption("prescription02", "طبية 2", "Optical 2"),
    VisualOption("round", "دائرية", "Round"),
    VisualOption("wayfarers", "وايفيرر", "Wayfarer"),
    VisualOption("sunglasses", "شمسية", "Sunglasses"),
)

private val facialHairOptions = listOf(
    VisualOption("none", "بدون", "None"),
    VisualOption("beardLight", "لحية خفيفة", "Light beard"),
    VisualOption("beardMedium", "لحية متوسطة", "Medium beard"),
    VisualOption("beardMagestic", "لحية كثيفة", "Full beard"),
    VisualOption("moustacheFancy", "شارب", "Moustache"),
)

private val eyeOptions = listOf(
    VisualOption("default", "طبيعي", "Default"),
    VisualOption("happy", "سعيد", "Happy"),
    VisualOption("squint", "مبتسم", "Squint"),
    VisualOption("surprised", "متفاجئ", "Surprised"),
    VisualOption("wink", "غمزة", "Wink"),
)

private val eyebrowOptions = listOf(
    VisualOption("default", "طبيعي", "Default"),
    VisualOption("defaultNatural", "طبيعي ناعم", "Natural"),
    VisualOption("flatNatural", "مستقيم", "Flat"),
    VisualOption("raisedExcited", "مرفوع", "Raised"),
    VisualOption("upDownNatural", "متدرج", "Up/down"),
)

private val mouthOptions = listOf(
    VisualOption("smile", "ابتسامة", "Smile"),
    VisualOption("default", "هادئ", "Neutral"),
    VisualOption("serious", "جدي", "Serious"),
    VisualOption("twinkle", "مرِح", "Playful"),
    VisualOption("eating", "عفوي", "Casual"),
)

private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

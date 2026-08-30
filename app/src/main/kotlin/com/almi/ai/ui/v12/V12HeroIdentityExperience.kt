package com.almi.ai.ui.v12

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almi.ai.R
import com.almi.ai.data.preferences.AvatarAppearance
import com.almi.ai.data.preferences.AvatarPresentation
import com.almi.ai.data.preferences.BodyProfile
import com.almi.ai.data.preferences.JourneyMode

private val HeroInk = Color(0xFF16334E)
private val HeroBlue = Color(0xFF42B9F1)
private val HeroBlueDeep = Color(0xFF369FE9)
private val HeroCyan = Color(0xFF63E3F2)
private val HeroPink = Color(0xFFFF7DA4)
private val HeroMint = Color(0xFF58D9C5)
private val HeroGlass = Color(0xF4FFFFFF)

private enum class HeroAvatarStage { CHOOSE, EDIT }
private enum class HeroOnboardingStage { LANGUAGE, PATH, AVATAR }

@Composable
internal fun V12HeroAvatarScreen(
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
    var stageName by rememberSaveable { mutableStateOf(HeroAvatarStage.CHOOSE.name) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val stage = runCatching { HeroAvatarStage.valueOf(stageName) }.getOrDefault(HeroAvatarStage.CHOOSE)
    val selected = selectedName?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }

    when (stage) {
        HeroAvatarStage.CHOOSE -> HeroIdentityChooser(
            language = language,
            selected = selected,
            onSelect = { selectedName = it.name },
            onBack = onBack,
            onNext = {
                selected?.let(onPresentation)
                stageName = HeroAvatarStage.EDIT.name
            },
        )

        HeroAvatarStage.EDIT -> V12AvatarDigitalLabScreen(
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
            onBack = { stageName = HeroAvatarStage.CHOOSE.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun HeroIdentityChooser(
    language: String,
    selected: AvatarPresentation?,
    onSelect: (AvatarPresentation) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFE7F6FF),
                            Color(0xFFF8FCFF),
                            Color(0xFFF5FBFF),
                        ),
                    ),
                )
                .statusBarsPadding(),
        ) {
            HeroLivingBackdrop()

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp, top = 10.dp)
                    .clickable(onClick = onBack),
                shape = RoundedCornerShape(26.dp),
                color = HeroGlass,
                border = BorderStroke(1.dp, HeroBlue.copy(alpha = .25f)),
                shadowElevation = 10.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("‹", color = HeroInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(if (language == "ar") "رجوع" else "Back", color = HeroInk, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 7.dp, start = 92.dp, end = 92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "ALMI / IDENTITY",
                    color = Color(0xFF80BCE0),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.15.sp,
                )
                Text(
                    if (language == "ar") "اختر نسختك" else "CHOOSE YOUR TWIN",
                    modifier = Modifier.padding(top = 3.dp),
                    color = HeroInk,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (language == "ar") "كل التفاصيل قابلة للتخصيص في الخطوة التالية" else "Every detail becomes editable next",
                    modifier = Modifier.padding(top = 3.dp),
                    color = HeroInk.copy(alpha = .48f),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            val side = 16.dp
            val gap = 12.dp
            val cardWidth = (maxWidth - side * 2 - gap) / 2
            val cardHeight = (maxHeight * .66f).coerceAtMost(610.dp)

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 28.dp)
                    .padding(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                HeroIdentityCard(
                    width = cardWidth,
                    height = cardHeight,
                    presentation = AvatarPresentation.MASCULINE,
                    accent = HeroBlue,
                    selected = selected == AvatarPresentation.MASCULINE,
                    language = language,
                    onClick = { onSelect(AvatarPresentation.MASCULINE) },
                )
                HeroIdentityCard(
                    width = cardWidth,
                    height = cardHeight,
                    presentation = AvatarPresentation.FEMININE,
                    accent = HeroPink,
                    selected = selected == AvatarPresentation.FEMININE,
                    language = language,
                    onClick = { onSelect(AvatarPresentation.FEMININE) },
                )
            }

            val nextAccent = when (selected) {
                AvatarPresentation.MASCULINE -> HeroBlueDeep
                AvatarPresentation.FEMININE -> HeroPink
                null -> Color(0xFFD9E2E9)
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 18.dp)
                    .fillMaxWidth()
                    .height(66.dp)
                    .clickable(enabled = selected != null, onClick = onNext),
                shape = RoundedCornerShape(31.dp),
                color = nextAccent,
                border = BorderStroke(1.2.dp, Color.White.copy(alpha = .92f)),
                shadowElevation = if (selected == null) 7.dp else 20.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (language == "ar") "ابدأ التخصيص" else "START CUSTOMIZING",
                        color = if (selected == null) HeroInk.copy(alpha = .36f) else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "›",
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
                        color = if (selected == null) HeroInk.copy(alpha = .22f) else Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroIdentityCard(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    presentation: AvatarPresentation,
    accent: Color,
    selected: Boolean,
    language: String,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.025f else .992f, tween(250), label = "hero-card-scale")
    val lift by animateFloatAsState(if (selected) -8f else 0f, tween(250), label = "hero-card-lift")

    Surface(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer(scaleX = scale, scaleY = scale, translationY = lift)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 2.4.dp else 1.2.dp, accent.copy(alpha = if (selected) .92f else .30f)),
        shadowElevation = if (selected) 22.dp else 12.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            HeroCardBitmap(
                presentation = presentation,
                modifier = Modifier.fillMaxSize(),
            )
            HeroCardScan(accent = accent, selected = selected)

            if (selected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = .88f),
                    border = BorderStroke(1.dp, accent.copy(alpha = .35f)),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(HeroMint, CircleShape))
                        Text(
                            if (language == "ar") "تم الاختيار" else "SELECTED",
                            color = HeroInk.copy(alpha = .66f),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = .7.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCardBitmap(presentation: AvatarPresentation, modifier: Modifier = Modifier) {
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
            contentScale = ContentScale.FillBounds,
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.verticalGradient(
                    if (presentation == AvatarPresentation.FEMININE) {
                        listOf(Color(0xFFFFF2F7), Color(0xFFFFE8F0), Color.White)
                    } else {
                        listOf(Color(0xFFEEFBFF), Color(0xFFE3F7FF), Color.White)
                    },
                ),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (presentation == AvatarPresentation.FEMININE) "أنثى" else "ذكر",
                color = if (presentation == AvatarPresentation.FEMININE) HeroPink else HeroBlue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun HeroCardScan(accent: Color, selected: Boolean) {
    val sweep by rememberInfiniteTransition(label = "hero-card-scan")
        .animateFloat(
            initialValue = -.08f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(if (selected) 1800 else 3300), RepeatMode.Restart),
            label = "hero-card-scan-value",
        )

    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, accent.copy(alpha = if (selected) .13f else .055f), Color.White.copy(alpha = .20f), Color.Transparent),
                startY = y - 48f,
                endY = y + 48f,
            ),
            topLeft = Offset(0f, y - 48f),
            size = Size(size.width, 96f),
        )
        if (selected) {
            drawLine(accent.copy(alpha = .50f), Offset(size.width * .08f, y), Offset(size.width * .92f, y), 1.2f)
        }
    }
}

@Composable
private fun HeroLivingBackdrop() {
    val sweep by rememberInfiniteTransition(label = "hero-world-sweep")
        .animateFloat(
            initialValue = .10f,
            targetValue = .90f,
            animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
            label = "hero-world-sweep-value",
        )

    Canvas(Modifier.fillMaxSize()) {
        val grid = Color(0xFF7BC9EC).copy(alpha = .13f)
        val step = 44f
        var x = 0f
        while (x <= size.width) {
            drawLine(grid, Offset(x, size.height * .17f), Offset(x, size.height * .90f), 1f)
            x += step
        }
        var y = size.height * .17f
        while (y <= size.height * .90f) {
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawCircle(HeroBlue.copy(alpha = .075f), size.minDimension * .55f, Offset(size.width * .08f, size.height * .38f))
        drawCircle(HeroPink.copy(alpha = .060f), size.minDimension * .48f, Offset(size.width * .92f, size.height * .60f))
        val beamY = size.height * sweep
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, HeroCyan.copy(alpha = .07f), Color.White.copy(alpha = .18f), Color.Transparent),
                beamY - 75f,
                beamY + 75f,
            ),
            topLeft = Offset(0f, beamY - 75f),
            size = Size(size.width, 150f),
        )
    }
}

@Composable
internal fun V12HeroOnboardingScreen(
    language: String,
    appearance: AvatarAppearance,
    bodyProfile: BodyProfile,
    digitalTwinSnapshotUri: String?,
    onLanguageChange: (String) -> Unit,
    onJourneyMode: (JourneyMode) -> Unit,
    onAvatarPresentation: (AvatarPresentation) -> Unit,
    onAvatarHair: (String) -> Unit,
    onAvatarHairColor: (String) -> Unit,
    onAvatarSkinColor: (String) -> Unit,
    onAvatarEyes: (String) -> Unit,
    onAvatarEyebrows: (String) -> Unit,
    onAvatarMouth: (String) -> Unit,
    onComplete: () -> Unit,
) {
    var stageName by rememberSaveable { mutableStateOf(HeroOnboardingStage.LANGUAGE.name) }
    val stage = runCatching { HeroOnboardingStage.valueOf(stageName) }.getOrDefault(HeroOnboardingStage.LANGUAGE)

    when (stage) {
        HeroOnboardingStage.LANGUAGE -> HeroLanguageEntry(
            language = language,
            onPick = {
                onLanguageChange(it)
                stageName = HeroOnboardingStage.PATH.name
            },
        )

        HeroOnboardingStage.PATH -> HeroJourneyEntry(
            language = language,
            onBack = { stageName = HeroOnboardingStage.LANGUAGE.name },
            onAvatar = {
                onJourneyMode(JourneyMode.AVATAR)
                stageName = HeroOnboardingStage.AVATAR.name
            },
            onPhoto = {
                onJourneyMode(JourneyMode.PHOTO)
                onComplete()
            },
        )

        HeroOnboardingStage.AVATAR -> V12HeroAvatarScreen(
            language = language,
            appearance = appearance,
            bodyProfile = bodyProfile,
            digitalTwinSnapshotUri = digitalTwinSnapshotUri,
            onPresentation = onAvatarPresentation,
            onHair = onAvatarHair,
            onHairColor = onAvatarHairColor,
            onSkinColor = onAvatarSkinColor,
            onEyes = onAvatarEyes,
            onEyebrows = onAvatarEyebrows,
            onMouth = onAvatarMouth,
            onBack = { stageName = HeroOnboardingStage.PATH.name },
            onComplete = onComplete,
        )
    }
}

@Composable
private fun HeroLanguageEntry(language: String, onPick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE8F8FF), Color(0xFFF9FDFF), Color(0xFFF2FAFF))))
            .statusBarsPadding(),
    ) {
        HeroLivingBackdrop()
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(94.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = .88f),
                border = BorderStroke(1.5.dp, HeroCyan.copy(alpha = .42f)),
                shadowElevation = 20.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("A", color = HeroBlueDeep, fontSize = 45.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("ALMI / FILAMENT", color = Color(0xFF80BCE0), fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text(
                if (language == "ar") "اختر لغة عالمك" else "Choose your world language",
                modifier = Modifier.padding(top = 6.dp),
                color = HeroInk,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp).offset(y = 68.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            HeroChoicePanel("العربية", "AR", HeroBlue, language == "ar") { onPick("ar") }
            HeroChoicePanel("English", "EN", HeroPink, language != "ar") { onPick("en") }
        }
    }
}

@Composable
private fun HeroJourneyEntry(
    language: String,
    onBack: () -> Unit,
    onAvatar: () -> Unit,
    onPhoto: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE9F8FF), Color(0xFFFCFDFF), Color(0xFFFFF8FC))))
            .statusBarsPadding(),
    ) {
        HeroLivingBackdrop()
        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable(onClick = onBack),
            shape = RoundedCornerShape(24.dp),
            color = HeroGlass,
            border = BorderStroke(1.dp, HeroBlue.copy(alpha = .25f)),
        ) {
            Text("‹", modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = HeroInk, fontSize = 28.sp)
        }
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 75.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALMI / ENTRY", color = HeroBlueDeep, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text(
                if (language == "ar") "كيف تريد الدخول؟" else "How do you want to enter?",
                modifier = Modifier.padding(top = 5.dp),
                color = HeroInk,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp).offset(y = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroJourneyPanel(
                title = if (language == "ar") "أنشئ نسختك الرقمية" else "Build your digital twin",
                subtitle = if (language == "ar") "هوية وجسم وقياسات وتخصيص ثلاثي الأبعاد" else "Identity, body, measurements and 3D customization",
                code = "TWIN",
                accent = HeroBlue,
                onClick = onAvatar,
            )
            HeroJourneyPanel(
                title = if (language == "ar") "ابدأ من صورتك" else "Start from your photo",
                subtitle = if (language == "ar") "ادخل مباشرة إلى تجربة الملابس بالذكاء الاصطناعي" else "Jump directly into AI try-on",
                code = "PHOTO",
                accent = HeroPink,
                onClick = onPhoto,
            )
        }
    }
}

@Composable
private fun HeroChoicePanel(title: String, code: String, accent: Color, active: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(82.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = .84f),
        border = BorderStroke(if (active) 2.dp else 1.dp, accent.copy(alpha = if (active) .72f else .27f)),
        shadowElevation = if (active) 16.dp else 9.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, color = HeroInk, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Text(code, color = accent, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            }
            Text(if (active) "✓" else "→", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun HeroJourneyPanel(
    title: String,
    subtitle: String,
    code: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(132.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(31.dp),
        color = Color.White.copy(alpha = .84f),
        border = BorderStroke(1.4.dp, accent.copy(alpha = .32f)),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Surface(
                modifier = Modifier.size(69.dp),
                shape = RoundedCornerShape(24.dp),
                color = accent.copy(alpha = .14f),
                border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(code.take(1), color = accent, fontSize = 29.sp, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = HeroInk, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, color = HeroInk.copy(alpha = .56f), fontSize = 10.5.sp, lineHeight = 15.sp)
            }
            Text("→", color = accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

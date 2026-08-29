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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
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

private enum class V11AvatarTab { SKIN, HAIR, HAIR_COLOR, FACE }

@Composable
fun V11AvatarScreen(
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
    onComplete: () -> Unit,
) {
    var selected by remember { mutableStateOf<AvatarPresentation?>(null) }
    var controlsVisible by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(V11AvatarTab.SKIN) }
    var maleRuntime by remember { mutableStateOf<V11AvatarRuntime?>(null) }
    var femaleRuntime by remember { mutableStateOf<V11AvatarRuntime?>(null) }

    LaunchedEffect(selected) {
        controlsVisible = false
        when (selected) {
            null -> {
                maleRuntime?.start()
                femaleRuntime?.start()
            }
            AvatarPresentation.MASCULINE -> {
                maleRuntime?.start()
                femaleRuntime?.start()
                maleRuntime?.move(-.13f, 0f, 780L)
                femaleRuntime?.move(0f, .70f, 650L)
            }
            AvatarPresentation.FEMININE -> {
                maleRuntime?.start()
                femaleRuntime?.start()
                femaleRuntime?.move(.13f, 0f, 780L)
                maleRuntime?.move(0f, -.70f, 650L)
            }
        }
        if (selected != null) {
            delay(760)
            if (selected == AvatarPresentation.MASCULINE) femaleRuntime?.stop() else maleRuntime?.stop()
            controlsVisible = true
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0A09))) {
        Column(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("ALMI / AVATAR", color = Color(0xFFD17A57), style = MaterialTheme.typography.labelSmall)
            Text(
                if (selected == null) tr(language, "اختر الشخصية", "Choose the character") else tr(language, "اصنع نسختك", "Shape your version"),
                color = Color(0xFFF4EEE7),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (selected == null) tr(language, "مجسمان Filament حقيقيان — اختر أولًا", "Two real Filament characters — choose first")
                else tr(language, "كل تعديل يظهر مباشرة على المجسم", "Every edit updates the model live"),
                color = Color.White.copy(alpha = .45f),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(if (controlsVisible) 525.dp else 650.dp).align(Alignment.TopCenter).padding(top = 74.dp),
        ) {
            val maleX by animateDpAsState(
                targetValue = when (selected) {
                    null -> -(maxWidth * .23f)
                    AvatarPresentation.MASCULINE -> 0.dp
                    AvatarPresentation.FEMININE -> -(maxWidth * 1.05f)
                },
                animationSpec = tween(700),
                label = "v11-male-x",
            )
            val femaleX by animateDpAsState(
                targetValue = when (selected) {
                    null -> maxWidth * .23f
                    AvatarPresentation.FEMININE -> 0.dp
                    AvatarPresentation.MASCULINE -> maxWidth * 1.05f
                },
                animationSpec = tween(700),
                label = "v11-female-x",
            )
            val viewportWidth = if (selected == null) maxWidth * .50f else maxWidth * .82f

            AvatarViewport(
                presentation = AvatarPresentation.MASCULINE,
                appearance = appearance.copy(presentation = AvatarPresentation.MASCULINE),
                modifier = Modifier.align(Alignment.Center).offset(x = maleX).width(viewportWidth).height(if (controlsVisible) 430.dp else 535.dp),
                onRuntime = { maleRuntime = it },
            )
            AvatarViewport(
                presentation = AvatarPresentation.FEMININE,
                appearance = appearance.copy(presentation = AvatarPresentation.FEMININE),
                modifier = Modifier.align(Alignment.Center).offset(x = femaleX).width(viewportWidth).height(if (controlsVisible) 430.dp else 535.dp),
                onRuntime = { femaleRuntime = it },
            )

            if (selected == null) {
                SelectPill(
                    label = tr(language, "ذكر", "Male"),
                    modifier = Modifier.align(Alignment.TopCenter).offset(x = -(maxWidth * .23f), y = 8.dp),
                ) {
                    onPresentation(AvatarPresentation.MASCULINE)
                    selected = AvatarPresentation.MASCULINE
                }
                SelectPill(
                    label = tr(language, "أنثى", "Female"),
                    modifier = Modifier.align(Alignment.TopCenter).offset(x = maxWidth * .23f, y = 8.dp),
                ) {
                    onPresentation(AvatarPresentation.FEMININE)
                    selected = AvatarPresentation.FEMININE
                }
            } else {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = .48f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .08f)),
                ) {
                    IconButton(onClick = {
                        if (selected == AvatarPresentation.MASCULINE) maleRuntime?.turntable() else femaleRuntime?.turntable()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, tint = Color.White)
                    }
                }

                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = .50f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .07f)),
                ) {
                    Text(
                        if (digitalTwinSnapshotUri != null || bodyProfile.hasExplicitHeight) tr(language, "BODY SYNC • مرتبط بقياساتك", "BODY SYNC • measurements linked")
                        else tr(language, "AVATAR MODE", "AVATAR MODE"),
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        color = Color.White.copy(alpha = .66f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(180)) + slideInVertically(tween(300)) { it / 4 },
            exit = fadeOut(tween(120)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 16.dp,
            ) {
                Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        TabPill(tr(language, "البشرة", "Skin"), tab == V11AvatarTab.SKIN) { tab = V11AvatarTab.SKIN }
                        TabPill(tr(language, "الشعر", "Hair"), tab == V11AvatarTab.HAIR) { tab = V11AvatarTab.HAIR }
                        TabPill(tr(language, "لون الشعر", "Hair color"), tab == V11AvatarTab.HAIR_COLOR) { tab = V11AvatarTab.HAIR_COLOR }
                        TabPill(tr(language, "الوجه", "Face"), tab == V11AvatarTab.FACE) { tab = V11AvatarTab.FACE }
                    }

                    Box(Modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.CenterStart) {
                        when (tab) {
                            V11AvatarTab.SKIN -> ColorRail(
                                values = listOf("F3D0BA", "E4B58F", "CF936B", "B97752", "8E583D", "603A2D"),
                                current = appearance.skinColor,
                                onSelect = onSkinColor,
                            )
                            V11AvatarTab.HAIR -> ChoiceRail(
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
                            V11AvatarTab.HAIR_COLOR -> ColorRail(
                                values = listOf("151210", "281916", "4D3025", "774227", "A46C3E", "D0B184"),
                                current = appearance.hairColor,
                                onSelect = onHairColor,
                            )
                            V11AvatarTab.FACE -> FaceRail(
                                language = language,
                                appearance = appearance,
                                onEyes = onEyes,
                                onEyebrows = onEyebrows,
                                onMouth = onMouth,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            modifier = Modifier.weight(.34f).height(50.dp).clickable {
                                controlsVisible = false
                                selected = null
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Box(contentAlignment = Alignment.Center) { Text(tr(language, "تغيير", "Change"), fontWeight = FontWeight.Bold) }
                        }
                        Button(onClick = onComplete, modifier = Modifier.weight(.66f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Outlined.Check, null)
                            Spacer(Modifier.width(6.dp))
                            Text(tr(language, "اعتماد الشخصية", "Use character"), fontWeight = FontWeight.Bold)
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
    onRuntime: (V11AvatarRuntime) -> Unit,
) {
    var runtime by remember(presentation) { mutableStateOf<V11AvatarRuntime?>(null) }
    DisposableEffect(presentation) { onDispose { runtime?.stop() } }
    Box(modifier.clip(RoundedCornerShape(26.dp))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView(context).also { surface ->
                    V11AvatarRuntime(context, surface, presentation, appearance).also {
                        runtime = it
                        onRuntime(it)
                        it.initialize()
                        it.start()
                    }
                }
            },
            update = { runtime?.update(presentation, appearance) },
        )
    }
}

@Composable
private fun SelectPill(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier.clickable(onClick = onClick), RoundedCornerShape(999.dp), color = Color(0xFFF4EEE7), shadowElevation = 9.dp) {
        Text(label, Modifier.padding(horizontal = 17.dp, vertical = 8.dp), color = Color(0xFF171411), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ColorRail(values: List<String>, current: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        values.forEach { value ->
            val selected = current.equals(value, true)
            Surface(
                modifier = Modifier.size(52.dp).clickable { onSelect(value) },
                shape = CircleShape,
                color = hex(value),
                border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
            ) {}
        }
    }
}

@Composable
private fun ChoiceRail(options: List<Pair<String, String>>, current: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { (value, label) -> ChoiceCard(label, current == value) { onSelect(value) } }
    }
}

@Composable
private fun FaceRail(
    language: String,
    appearance: AvatarAppearance,
    onEyes: (String) -> Unit,
    onEyebrows: (String) -> Unit,
    onMouth: (String) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ChoiceCard(tr(language, "طبيعي", "Natural"), appearance.eyesVariant == "default") { onEyes("default") }
        ChoiceCard(tr(language, "عين واسعة", "Wide eyes"), appearance.eyesVariant == "wide") { onEyes("wide") }
        ChoiceCard(tr(language, "نظرة حادة", "Sharp eyes"), appearance.eyesVariant == "sharp") { onEyes("sharp") }
        ChoiceCard(tr(language, "حاجب محدد", "Defined brow"), appearance.eyebrowsVariant == "defined") { onEyebrows("defined") }
        ChoiceCard(tr(language, "ابتسامة", "Smile"), appearance.mouthVariant == "smile") { onMouth("smile") }
        ChoiceCard(tr(language, "شفاه ممتلئة", "Full lips"), appearance.mouthVariant == "full") { onMouth("full") }
    }
}

@Composable
private fun ChoiceCard(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.height(50.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, Modifier.padding(horizontal = 12.dp), textAlign = TextAlign.Center, color = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun hex(value: String): Color = runCatching { Color(android.graphics.Color.parseColor("#$value")) }.getOrDefault(Color.Gray)
private fun tr(language: String, ar: String, en: String): String = if (language == "ar") ar else en

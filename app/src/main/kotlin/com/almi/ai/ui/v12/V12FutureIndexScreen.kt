package com.almi.ai.ui.v12

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
internal fun V12FutureIndexScreen(
    language: String,
    personImage: String?,
    bodyReady: Boolean,
    avatarReady: Boolean,
    aiReady: Boolean,
    onFit: () -> Unit,
    onAvatar: () -> Unit,
    onBody: () -> Unit,
    onAi: () -> Unit,
    onControl: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.background,
                        scheme.primaryContainer.copy(alpha = .30f),
                        scheme.background,
                    ),
                ),
            )
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "ALMI",
                        color = scheme.onBackground,
                        style = MaterialTheme.typography.headlineLarge,
                        letterSpacing = (-1.2).sp,
                    )
                    Text(
                        if (language == "ar") "نظامك البشري الشخصي" else "YOUR PERSONAL HUMAN SYSTEM",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Surface(
                    modifier = Modifier.size(48.dp).clickable(onClick = onControl),
                    shape = CircleShape,
                    color = scheme.surface.copy(alpha = .92f),
                    border = BorderStroke(1.dp, scheme.outlineVariant),
                    shadowElevation = 5.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        V12Glyph(V12GlyphType.CONTROL, scheme.onSurface, Modifier.size(21.dp))
                    }
                }
            }

            Spacer(Modifier.height(34.dp))

            Text(
                "ALMI / HUMAN ATELIER",
                color = scheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (language == "ar") "نسختك الرقمية،\nلكن أقرب إليك." else "Your digital self,\nmade personal.",
                color = scheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (language == "ar") "القياسات، الهوية، التجربة والذكاء في مساحة واحدة هادئة." else "Body, identity, fit and intelligence in one calm space.",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(22.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().height(248.dp),
                shape = RoundedCornerShape(36.dp),
                color = scheme.surface,
                border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .72f)),
                shadowElevation = 9.dp,
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        scheme.primaryContainer.copy(alpha = .72f),
                                        scheme.surface,
                                        scheme.secondaryContainer.copy(alpha = .48f),
                                    ),
                                ),
                            ),
                    )

                    if (personImage != null) {
                        AsyncImage(
                            model = personImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent, scheme.onBackground.copy(alpha = .34f)),
                                ),
                            ),
                        )
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(
                                modifier = Modifier.size(92.dp),
                                shape = CircleShape,
                                color = scheme.surface.copy(alpha = .78f),
                                border = BorderStroke(1.dp, scheme.outlineVariant),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    V12Glyph(V12GlyphType.AVATAR, scheme.primary, Modifier.size(45.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (language == "ar") "أنشئ نسختك الرقمية" else "Create your digital twin",
                                color = scheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                            )
                            Text(
                                if (language == "ar") "هوية عالية التفاصيل جاهزة للتخصيص" else "A high-detail identity ready to personalize",
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.BottomStart).padding(15.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        EditorialPill(
                            text = if (avatarReady) "IDENTITY / LINKED" else "IDENTITY / CREATE",
                            active = avatarReady,
                        )
                        EditorialPill(
                            text = if (bodyReady) "BODY / READY" else "BODY / MAP",
                            active = bodyReady,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Surface(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onFit),
                shape = RoundedCornerShape(28.dp),
                color = scheme.primary,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = scheme.onPrimary.copy(alpha = .13f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            V12Glyph(V12GlyphType.FIT, scheme.onPrimary, Modifier.size(25.dp))
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (language == "ar") "جرّب الإطلالة على نسختك" else "Try a look on your twin",
                            color = scheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            if (language == "ar") "استوديو الملاءمة بالذكاء الاصطناعي" else "AI FIT STUDIO",
                            color = scheme.onPrimary.copy(alpha = .72f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text("↗", color = scheme.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                EditorialModule(
                    modifier = Modifier.weight(1f),
                    glyph = V12GlyphType.AVATAR,
                    title = if (language == "ar") "الهوية" else "Identity",
                    status = if (avatarReady) "LINKED" else "CREATE",
                    accent = scheme.secondary,
                    onClick = onAvatar,
                )
                EditorialModule(
                    modifier = Modifier.weight(1f),
                    glyph = V12GlyphType.BODY,
                    title = if (language == "ar") "الجسم" else "Body",
                    status = if (bodyReady) "READY" else "MAP",
                    accent = scheme.tertiary,
                    onClick = onBody,
                )
                EditorialModule(
                    modifier = Modifier.weight(1f),
                    glyph = V12GlyphType.AI,
                    title = if (language == "ar") "الذكاء" else "AI",
                    status = if (aiReady) "LIVE" else "SETUP",
                    accent = scheme.primary,
                    onClick = onAi,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EditorialPill(text: String, active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = scheme.surface.copy(alpha = .90f),
        border = BorderStroke(1.dp, if (active) scheme.tertiary.copy(alpha = .5f) else scheme.outlineVariant),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            color = if (active) scheme.tertiary else scheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun EditorialModule(
    modifier: Modifier,
    glyph: V12GlyphType,
    title: String,
    status: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.height(88.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .75f)),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                V12Glyph(glyph, accent, Modifier.size(21.dp))
                Box(Modifier.size(6.dp).background(accent, CircleShape))
            }
            Column {
                Text(
                    title,
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
                Text(
                    status,
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

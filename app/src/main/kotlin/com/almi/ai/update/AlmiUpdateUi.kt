package com.almi.ai.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Automatic discovery is intentionally notification-only. The in-app update surface is entered by
 * tapping that Android notification or by explicitly checking from Settings -> Update management.
 */
@Composable
internal fun AlmiUpdateGate(manager: AlmiUpdateManager, language: String) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    when (val current = state) {
        AlmiUpdateState.Idle,
        AlmiUpdateState.Checking,
        AlmiUpdateState.Current,
        -> Unit

        is AlmiUpdateState.Available -> {
            val release = current.release
            if (!current.manualCheck) {
                LaunchedEffect(release.releaseId, release.versionCode, language) {
                    AlmiUpdateNotifier.notifyOnce(context, release, language)
                }
            } else {
                AlertDialog(
                    onDismissRequest = {
                        if (current.skipAllowed) manager.skipCurrentRelease(release) else manager.dismissNonBlocking()
                    },
                    properties = DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
                    title = {
                        Text(
                            if (language == "ar") release.titleAr else release.titleEn,
                            fontWeight = FontWeight.Black,
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (language == "ar") release.notesAr else release.notesEn,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (language == "ar") "الإصدار ${release.versionName} • يتم تنزيل فرق التحديث فقط" else "Version ${release.versionName} • delta download only",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (current.skipAllowed) {
                                Text(
                                    if (language == "ar") "أنت متراجع عن هذا الإصدار، لذلك يمكنك تخطيه. إذا صدر إصدار أحدث فسيحل محله تلقائيًا." else "You rolled back this release, so you may skip it. A newer release will replace it automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { scope.launch { manager.installLatest(release) } }) {
                            Text(if (language == "ar") "تحديث الآن" else "Update now")
                        }
                    },
                    dismissButton = {
                        if (current.skipAllowed) {
                            TextButton(onClick = { manager.skipCurrentRelease(release) }) {
                                Text(if (language == "ar") "تخطي" else "Skip")
                            }
                        } else {
                            TextButton(onClick = manager::dismissNonBlocking) {
                                Text(if (language == "ar") "إلغاء" else "Cancel")
                            }
                        }
                    },
                )
            }
        }

        is AlmiUpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = {
                    Text(
                        if (language == "ar") {
                            if (current.rollback) "جاري تجهيز التراجع" else "جاري تجهيز التحديث"
                        } else {
                            if (current.rollback) "Preparing rollback" else "Preparing update"
                        },
                        fontWeight = FontWeight.Black,
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { current.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${(current.progress * 100f).toInt().coerceIn(0, 100)}%",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (language == "ar") "يتم تنزيل ملف الفرق فقط ثم إعادة بناء APK الموقّع محليًا." else "Only the delta is downloaded; the signed APK is reconstructed locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {},
            )
        }

        is AlmiUpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                title = {
                    Text(
                        if (language == "ar") "جاهز للتثبيت" else "Ready to install",
                        fontWeight = FontWeight.Black,
                    )
                },
                text = {
                    Text(
                        if (language == "ar") "سيظهر مثبت Android لتأكيد استبدال النسخة الحالية. بعد التراجع سيُغلق التطبيق أثناء الاستبدال بشكل طبيعي." else "Android Package Installer will confirm replacement. During rollback the app will close as Android swaps the package.",
                    )
                },
                confirmButton = {
                    Button(onClick = { manager.resumePreparedInstall() }) {
                        Text(if (language == "ar") "فتح المثبّت" else "Open installer")
                    }
                },
            )
        }

        is AlmiUpdateState.Message -> {
            AlertDialog(
                onDismissRequest = { if (!current.blocking) manager.dismissNonBlocking() },
                properties = DialogProperties(
                    dismissOnBackPress = !current.blocking,
                    dismissOnClickOutside = !current.blocking,
                ),
                title = { Text(if (language == "ar") "إدارة التحديث" else "Update management", fontWeight = FontWeight.Black) },
                text = { Text(if (language == "ar") current.textAr else current.textEn) },
                confirmButton = {
                    if (!current.blocking) {
                        Button(onClick = manager::dismissNonBlocking) {
                            Text(if (language == "ar") "حسنًا" else "OK")
                        }
                    } else {
                        Button(onClick = { manager.resumePreparedInstall() }) {
                            Text(if (language == "ar") "متابعة التثبيت" else "Continue install")
                        }
                    }
                },
            )
        }
    }
}

@Composable
internal fun AlmiUpdateManagementDialog(
    language: String,
    onCheckLatest: () -> Unit,
    onRollback: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = scheme.surface,
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .7f)),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.surface,
                                scheme.surfaceVariant.copy(alpha = .22f),
                                scheme.surface,
                            ),
                        ),
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "ALMI / RELEASE CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (language == "ar") "إدارة التحديث" else "Update management",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (language == "ar") "يظهر أحدث إصدار فقط. التنبيه التلقائي يصل مرة واحدة كإشعار Android، ولا تفتح نافذة فوق التطبيق من تلقاء نفسها." else "Only the latest release is shown. Automatic discovery is announced once as an Android notification and never opens a modal by itself.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                UpdateActionCard(
                    title = if (language == "ar") "البحث عن تحديث" else "Check for update",
                    subtitle = if (language == "ar") "تحقق يدويًا من أحدث إصدار ثم اختر تحديث أو إلغاء." else "Check the newest release manually, then choose update or cancel.",
                    meta = "LATEST / DELTA",
                    onClick = onCheckLatest,
                )
                UpdateActionCard(
                    title = if (language == "ar") "التراجع عن آخر تحديث" else "Roll back last update",
                    subtitle = if (language == "ar") "ارجع إلى النسخة المستقرة السابقة بحزمة فرق موقعة، بدون تنزيل التطبيق كاملًا." else "Return to the previous stable build using a signed delta package without downloading the whole app.",
                    meta = "ROLLBACK",
                    onClick = onRollback,
                )

                Spacer(Modifier.height(2.dp))
                TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text(if (language == "ar") "إغلاق" else "Close")
                }
            }
        }
    }
}

@Composable
private fun UpdateActionCard(title: String, subtitle: String, meta: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceVariant.copy(alpha = .34f),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .72f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Box(
                    Modifier
                        .background(scheme.primaryContainer, RoundedCornerShape(99.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = scheme.onPrimaryContainer)
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

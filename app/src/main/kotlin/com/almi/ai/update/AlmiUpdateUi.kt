package com.almi.ai.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
internal fun AlmiUpdateGate(manager: AlmiUpdateManager, language: String) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val current = state) {
        AlmiUpdateState.Idle,
        AlmiUpdateState.Checking,
        AlmiUpdateState.Current,
        -> Unit

        is AlmiUpdateState.Available -> {
            val release = current.release
            AlertDialog(
                onDismissRequest = {
                    if (!current.mandatory) {
                        if (current.skipAllowed) manager.skipCurrentRelease(release) else manager.dismissNonBlocking()
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = !current.mandatory,
                    dismissOnClickOutside = !current.mandatory,
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
                        if (current.mandatory) {
                            Text(
                                if (language == "ar") "هذا التحديث مطلوب للمتابعة داخل التطبيق." else "This update is required to continue using the app.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                            )
                        } else if (current.skipAllowed) {
                            Text(
                                if (language == "ar") "أنت متراجع عن هذا الإصدار، لذلك يمكنك تخطيه هذه المرة. إذا صدر إصدار أحدث سيحل محله تلقائيًا." else "You rolled back this release, so you may skip it. A newer release will replace it automatically.",
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
                    when {
                        current.skipAllowed -> TextButton(onClick = { manager.skipCurrentRelease(release) }) {
                            Text(if (language == "ar") "تخطي" else "Skip")
                        }
                        !current.mandatory -> TextButton(onClick = manager::dismissNonBlocking) {
                            Text(if (language == "ar") "إلغاء" else "Cancel")
                        }
                    }
                },
            )
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
    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (language == "ar") "إدارة التحديث" else "Update management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    if (language == "ar") "ALMI يعرض أحدث إصدار فقط ولا يحتفظ بتحديثات قديمة في هذه الشاشة." else "ALMI shows the newest release only; older updates never remain in this screen.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                UpdateActionCard(
                    title = if (language == "ar") "تحديث جديد" else "New update",
                    subtitle = if (language == "ar") "ابحث الآن عن أحدث إصدار. إذا وجد تحديثًا يمكنك بدء التحديث أو إلغاؤه." else "Check the latest release now. If one exists, you can update or cancel.",
                    meta = "LATEST / DELTA",
                    onClick = onCheckLatest,
                )
                UpdateActionCard(
                    title = if (language == "ar") "التراجع عن تحديث سابق" else "Roll back previous update",
                    subtitle = if (language == "ar") "استبدل الإصدار الحالي بنقطة الرجوع الموقعة. بعد التثبيت سيغلق Android التطبيق، وعند الفتح يظهر التحديث المتراجع عنه مع خيار تخطي." else "Install the signed rollback point. Android closes the app while replacing it; on next launch the rolled-back update appears with Skip.",
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
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceVariant.copy(alpha = .55f),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(meta, style = MaterialTheme.typography.labelSmall, color = scheme.primary)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

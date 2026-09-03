package com.vibe.app.presentation.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.AccountPicker
import com.vibe.app.presentation.ui.setting.LanguageViewModel

@Composable
fun WelcomeSignInScreen(
    languageViewModel: LanguageViewModel,
    onSignedIn: (String) -> Unit,
) {
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isArabic = selectedLanguage == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when {
            result.resultCode == Activity.RESULT_CANCELED -> {
                errorMessage = null
            }

            result.resultCode != Activity.RESULT_OK -> {
                errorMessage = if (isArabic) {
                    "تعذر تسجيل الدخول. حاول مرة أخرى."
                } else {
                    "Sign-in failed. Please try again."
                }
            }

            else -> {
                val email = result.data
                    ?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    ?.trim()

                if (email.isNullOrBlank()) {
                    errorMessage = if (isArabic) {
                        "لم يتم اختيار حساب Google."
                    } else {
                        "No Google account was selected."
                    }
                } else {
                    errorMessage = null
                    onSignedIn(email)
                }
            }
        }
    }

    fun openGoogleAccountPicker() {
        errorMessage = null
        val intent = AccountPicker.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            true,
            null,
            null,
            null,
            null,
        )
        accountPickerLauncher.launch(intent)
    }

    fun switchLanguage() {
        errorMessage = null
        languageViewModel.selectLanguage(if (isArabic) "en" else "ar")
    }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            TextButton(
                onClick = ::switchLanguage,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Text(
                    text = if (isArabic) "English" else "العربية",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = if (isArabic) "مرحبًا بك" else "Welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = if (isArabic) "سجّل الدخول للمتابعة" else "Sign in to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(30.dp))

                Button(
                    onClick = ::openGoogleAccountPicker,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                ) {
                    GoogleMark()
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = if (isArabic) {
                            "المتابعة باستخدام Google"
                        } else {
                            "Continue with Google"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                errorMessage?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleMark() {
    Surface(
        modifier = Modifier.size(24.dp),
        shape = CircleShape,
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE1E5EA)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                color = Color(0xFF4285F4),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

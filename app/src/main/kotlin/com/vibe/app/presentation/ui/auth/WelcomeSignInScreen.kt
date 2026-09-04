package com.vibe.app.presentation.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.vibe.app.BuildConfig
import com.vibe.app.R
import com.vibe.app.data.preferences.AppText
import com.vibe.app.presentation.ui.setting.LanguageViewModel

private const val GOOGLE_AUTH_TAG = "GoogleAuth"
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

@Composable
fun WelcomeSignInScreen(
    languageViewModel: LanguageViewModel,
    onSignedIn: (GoogleAccount) -> Unit,
    onContinueLocally: () -> Unit,
    externalErrorMessage: String? = null,
) {
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isArabic = selectedLanguage == "ar"
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(externalErrorMessage) {
        if (!externalErrorMessage.isNullOrBlank()) loading = false
    }

    val legacySignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        loading = false
        try {
            val google = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val email = google.email?.trim()
            val idToken = google.idToken?.trim()
            if (email.isNullOrEmpty()) {
                errorMessage = AppText.get(R.string.google_no_email)
            } else if (idToken.isNullOrEmpty()) {
                errorMessage = AppText.get(R.string.google_no_id_token)
            } else {
                errorMessage = null
                onSignedIn(
                    GoogleAccount(
                        email = email,
                        displayName = google.displayName,
                        profilePictureUrl = google.photoUrl?.toString(),
                        idToken = idToken,
                    ),
                )
            }
        } catch (error: ApiException) {
            Log.e(GOOGLE_AUTH_TAG, "Legacy Google sign-in failed: ${error.statusCode}", error)
            errorMessage = googleSignInErrorMessage(error.statusCode)
        } catch (error: Exception) {
            Log.e(GOOGLE_AUTH_TAG, "Unexpected legacy Google sign-in failure", error)
            val detail = error.message?.takeIf { it.isNotBlank() }?.take(160) ?: error::class.java.simpleName
            errorMessage = AppText.get(R.string.google_sign_in_failed_detail, detail)
        }
    }

    fun signInWithGoogle() {
        val hostActivity = activity ?: run {
            errorMessage = AppText.get(R.string.google_unable_open_signin)
            return
        }
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            errorMessage = AppText.get(R.string.google_web_client_missing)
            return
        }

        errorMessage = null
        loading = true
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestIdToken(clientId)
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        legacySignInLauncher.launch(GoogleSignIn.getClient(hostActivity, options).signInIntent)
    }

    fun switchLanguage() {
        errorMessage = null
        languageViewModel.setLanguage(if (isArabic) "en" else "ar")
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        TextButton(onClick = ::switchLanguage, modifier = Modifier.align(Alignment.TopEnd)) {
            Text(if (isArabic) "English" else "العربية", fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 3.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(34.dp), MaterialTheme.colorScheme.onPrimary)
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("lm_AI", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_google_or_local),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = ::signInWithGoogle,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.continue_with_google), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onContinueLocally, enabled = !loading, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.continue_without_signing_in), fontWeight = FontWeight.SemiBold)
            }
            (errorMessage ?: externalErrorMessage)?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private fun googleSignInErrorMessage(statusCode: Int): String =
    if (statusCode == 10) {
        AppText.get(R.string.google_config_code_10)
    } else {
        AppText.get(R.string.google_signin_status_error, statusCode)
    }

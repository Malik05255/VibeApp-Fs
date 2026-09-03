package com.vibe.app.presentation.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.vibe.app.BuildConfig
import com.vibe.app.presentation.ui.setting.LanguageViewModel
import java.security.SecureRandom
import kotlinx.coroutines.launch

private const val GOOGLE_AUTH_TAG = "GoogleAuth"

@Composable
fun WelcomeSignInScreen(
    languageViewModel: LanguageViewModel,
    onSignedIn: (GoogleAccount) -> Unit,
    onContinueLocally: () -> Unit,
) {
    val selectedLanguage by languageViewModel.selectedLanguage.collectAsStateWithLifecycle()
    val isArabic = selectedLanguage == "ar"
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    val legacySignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        loading = false
        try {
            val google = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            val email = google.email?.trim()
            if (email.isNullOrEmpty()) {
                errorMessage = if (isArabic) "لم يُرجع حساب Google بريدًا إلكترونيًا." else "Google did not return an email address."
            } else {
                errorMessage = null
                onSignedIn(
                    GoogleAccount(
                        email = email,
                        displayName = google.displayName,
                        profilePictureUrl = google.photoUrl?.toString(),
                    ),
                )
            }
        } catch (error: ApiException) {
            Log.e(GOOGLE_AUTH_TAG, "Legacy Google sign-in failed: ${error.statusCode}", error)
            errorMessage = googleSignInErrorMessage(error.statusCode, isArabic)
        }
    }

    fun signInWithGoogle() {
        val hostActivity = activity
        if (hostActivity == null) {
            errorMessage = if (isArabic) "تعذر فتح تسجيل الدخول." else "Unable to open sign-in."
            return
        }

        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (clientId.isEmpty()) {
            errorMessage = null
            loading = true
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build()
            legacySignInLauncher.launch(GoogleSignIn.getClient(hostActivity, options).signInIntent)
            return
        }

        scope.launch {
            loading = true
            errorMessage = null
            try {
                val option = GetSignInWithGoogleOption.Builder(clientId)
                    .setNonce(generateSecureRandomNonce())
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = credentialManager.getCredential(
                    context = hostActivity,
                    request = request,
                ).credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    error("Unexpected Google credential type: ${credential.type}")
                }
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val email = google.id.trim()
                if (email.isEmpty()) {
                    error("Google credential did not contain an account id")
                }
                errorMessage = null
                onSignedIn(
                    GoogleAccount(
                        email = email,
                        displayName = google.displayName,
                        profilePictureUrl = google.profilePictureUri?.toString(),
                    ),
                )
            } catch (_: GetCredentialCancellationException) {
                errorMessage = if (isArabic) "تم إلغاء تسجيل الدخول بحساب Google." else "Google sign-in was cancelled."
            } catch (error: GetCredentialException) {
                Log.e(GOOGLE_AUTH_TAG, "Credential Manager Google sign-in failed: ${error.type}: ${error.message}", error)
                val detail = buildString {
                    append(error.type.substringAfterLast('.').take(80))
                    error.message?.takeIf { it.isNotBlank() }?.let {
                        append(": ")
                        append(it.take(140))
                    }
                }
                errorMessage = if (isArabic) {
                    "تعذر تسجيل الدخول بحساب Google. تفاصيل الخطأ: $detail"
                } else {
                    "Google sign-in failed. Error: $detail"
                }
            } catch (error: Exception) {
                Log.e(GOOGLE_AUTH_TAG, "Unexpected Google sign-in failure", error)
                val detail = error.message?.takeIf { it.isNotBlank() }?.take(160)
                    ?: error::class.java.simpleName
                errorMessage = if (isArabic) {
                    "تعذر تسجيل الدخول بحساب Google. تفاصيل الخطأ: $detail"
                } else {
                    "Google sign-in failed. Error: $detail"
                }
            } finally {
                loading = false
            }
        }
    }

    fun switchLanguage() {
        errorMessage = null
        languageViewModel.selectLanguage(if (isArabic) "en" else "ar")
    }

    CompositionLocalProvider(LocalLayoutDirection provides if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 14.dp),
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
                    if (isArabic) "اربط حساب Google أو تابع محليًا" else "Connect Google or continue locally",
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
                    else Text(if (isArabic) "المتابعة باستخدام Google" else "Continue with Google", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onContinueLocally,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        if (isArabic) "المتابعة بدون تسجيل" else "Continue without signing in",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                errorMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
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

private fun generateSecureRandomNonce(byteLength: Int = 32): String {
    val bytes = ByteArray(byteLength)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

private fun googleSignInErrorMessage(statusCode: Int, isArabic: Boolean): String {
    return if (statusCode == 10) {
        if (isArabic) {
            "إعداد Google لهذه النسخة غير مكتمل (رمز 10). تحقق من package name وSHA-1 وWeb client ID."
        } else {
            "Google is not configured for this build (code 10). Check package name, SHA-1 and Web client ID."
        }
    } else {
        if (isArabic) {
            "لم يكتمل تسجيل الدخول بحساب Google (رمز $statusCode). حاول مرة أخرى أو تابع محليًا."
        } else {
            "Google sign-in did not complete (code $statusCode). Try again or continue locally."
        }
    }
}

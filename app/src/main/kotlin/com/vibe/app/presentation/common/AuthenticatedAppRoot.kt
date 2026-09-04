package com.vibe.app.presentation.common

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vibe.app.presentation.ui.auth.AuthViewModel
import com.vibe.app.presentation.ui.auth.GoogleAccount
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import com.vibe.app.presentation.ui.auth.WelcomeSignInScreen
import com.vibe.app.presentation.ui.setting.LanguageViewModel

@Composable
fun AuthenticatedAppRoot(navController: NavHostController) {
    val context = LocalContext.current
    val languageViewModel: LanguageViewModel = hiltViewModel()
    val authViewModel: AuthViewModel = hiltViewModel()
    var hasAccess by remember {
        mutableStateOf(
            GoogleAccountSession.get(context) != null || GoogleAccountSession.isLocalMode(context),
        )
    }
    var authError by remember { mutableStateOf<String?>(null) }

    if (hasAccess && languageViewModel.isLanguageSelected()) {
        SetupNavGraph(navController = navController)
    } else {
        WelcomeSignInScreen(
            languageViewModel = languageViewModel,
            externalErrorMessage = authError,
            onSignedIn = { account: GoogleAccount ->
                authError = null
                authViewModel.completeGoogleSignIn(
                    account = account,
                    onSuccess = {
                        languageViewModel.confirmLanguage()
                        hasAccess = true
                    },
                    onError = { message -> authError = message },
                )
            },
            onContinueLocally = {
                authError = null
                GoogleAccountSession.enableLocalMode(context)
                languageViewModel.confirmLanguage()
                hasAccess = true
            },
        )
    }
}

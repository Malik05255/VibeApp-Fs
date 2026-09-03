package com.vibe.app.presentation.common

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vibe.app.presentation.ui.auth.GoogleAccount
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import com.vibe.app.presentation.ui.auth.WelcomeSignInScreen
import com.vibe.app.presentation.ui.setting.LanguageViewModel

@Composable
fun AuthenticatedAppRoot(navController: NavHostController) {
    val context = LocalContext.current
    val languageViewModel: LanguageViewModel = hiltViewModel()
    var hasAccess by remember {
        mutableStateOf(
            GoogleAccountSession.get(context) != null || GoogleAccountSession.isLocalMode(context),
        )
    }

    if (hasAccess && languageViewModel.isLanguageSelected()) {
        SetupNavGraph(navController = navController)
    } else {
        WelcomeSignInScreen(
            languageViewModel = languageViewModel,
            onSignedIn = { account: GoogleAccount ->
                GoogleAccountSession.save(context, account)
                languageViewModel.confirmLanguage()
                hasAccess = true
            },
            onContinueLocally = {
                GoogleAccountSession.enableLocalMode(context)
                languageViewModel.confirmLanguage()
                hasAccess = true
            },
        )
    }
}

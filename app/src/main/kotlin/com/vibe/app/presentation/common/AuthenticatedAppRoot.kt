package com.vibe.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vibe.app.presentation.ui.auth.GoogleAccountSession
import com.vibe.app.presentation.ui.auth.WelcomeSignInScreen
import com.vibe.app.presentation.ui.setting.LanguageViewModel

@Composable
fun AuthenticatedAppRoot(
    navController: NavHostController,
) {
    val context = LocalContext.current
    val languageViewModel: LanguageViewModel = hiltViewModel()

    var linkedGoogleEmail by remember {
        mutableStateOf(GoogleAccountSession.getEmail(context))
    }

    val languageSelected = languageViewModel.isLanguageSelected()

    if (!linkedGoogleEmail.isNullOrBlank() && languageSelected) {
        SetupNavGraph(navController = navController)
    } else {
        WelcomeSignInScreen(
            languageViewModel = languageViewModel,
            onSignedIn = { email ->
                GoogleAccountSession.save(context, email)
                languageViewModel.confirmLanguage()
                linkedGoogleEmail = email
            },
        )
    }
}

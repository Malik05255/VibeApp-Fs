package com.vibe.app.presentation.ui.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.R
import com.vibe.app.presentation.ui.setting.LanguageViewModel

@Composable
fun LanguageSelectionScreen(
    languageViewModel: LanguageViewModel,
    onLanguageConfirmed: () -> Unit
) {
    val currentLanguage by
        languageViewModel.language
            .collectAsStateWithLifecycle()

    var selectedLanguage by remember {
        mutableStateOf(currentLanguage)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = stringResource(
                R.string.language
            ),
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        LanguageOption(
            title = stringResource(
                R.string.arabic
            ),
            selected = selectedLanguage == "ar",
            onClick = {
                selectedLanguage = "ar"
                languageViewModel.selectLanguage("ar")
            }
        )

        LanguageOption(
            title = stringResource(
                R.string.english
            ),
            selected = selectedLanguage == "en",
            onClick = {
                selectedLanguage = "en"
                languageViewModel.selectLanguage("en")
            }
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                languageViewModel.confirmLanguage()
                onLanguageConfirmed()
            }
        ) {
            Text(
                text = stringResource(
                    R.string.confirm
                )
            )
        }
    }
}

@Composable
private fun LanguageOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            RadioButton(
                selected = selected,
                onClick = onClick
            )

            Text(
                text = title,
                modifier = Modifier.padding(
                    start = 8.dp
                )
            )
        }
    }
}

package com.malik.lmai.presentation.ui.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.malik.lmai.R

@Composable
fun LanguageDialog(
    currentLanguage: String,
    selectedLanguage: String = currentLanguage,
    onLanguageSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Text(
                text = stringResource(
                    R.string.language
                )
            )
        },

        text = {
            Column {

                TextButton(
                    onClick = {
                        onLanguageSelected("ar")
                    }
                ) {
                    RadioButton(
                        selected = selectedLanguage == "ar",
                        onClick = {
                            onLanguageSelected("ar")
                        }
                    )

                    Text(
                        text = stringResource(
                            R.string.arabic
                        )
                    )
                }

                TextButton(
                    onClick = {
                        onLanguageSelected("en")
                    }
                ) {
                    RadioButton(
                        selected = selectedLanguage == "en",
                        onClick = {
                            onLanguageSelected("en")
                        }
                    )

                    Text(
                        text = stringResource(
                            R.string.english
                        )
                    )
                }
            }
        },

        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                }
            ) {
                Text(
                    text = stringResource(
                        R.string.confirm
                    )
                )
            }
        },

        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(
                    text = stringResource(
                        R.string.cancel
                    )
                )
            }
        }
    )
}

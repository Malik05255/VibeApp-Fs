package com.vibe.app.presentation.ui.github

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vibe.app.data.database.entity.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubSettingsScreen(
    onBack: () -> Unit,
    onProjectClick: (Project) -> Unit,
    viewModel: GitHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var repositoriesExpanded by remember { mutableStateOf(false) }
    var lastOpenedVerificationUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.verificationUri, state.deviceUserCode) {
        val uri = state.verificationUri
        if (!uri.isNullOrBlank() && state.deviceUserCode != null && uri != lastOpenedVerificationUri) {
            lastOpenedVerificationUri = uri
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("إعدادات GITHUB") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.connectedLogin == null) {
                Spacer(Modifier.height(24.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF161719),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.loading) { viewModel.startSignIn() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "◉",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = "ربط مع GITHUB",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (state.loading && state.deviceUserCode == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                state.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (state.deviceUserCode != null) {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("أكمل الربط في GitHub", fontWeight = FontWeight.SemiBold)
                            Text(
                                state.deviceUserCode!!,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("تم فتح صفحة GitHub تلقائيًا. استخدم هذا الرمز عند الطلب.")
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub code", state.deviceUserCode))
                                },
                            ) {
                                Icon(Icons.Outlined.ContentCopy, null)
                                Text("نسخ الرمز")
                            }
                            TextButton(
                                onClick = {
                                    state.verificationUri?.let { uri ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                    }
                                },
                            ) {
                                Icon(Icons.Outlined.OpenInBrowser, null)
                                Text("فتح GitHub مجددًا")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Text("بانتظار الموافقة")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("@${state.connectedLogin}", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = viewModel::disconnect) { Text("فصل") }
                }

                ExposedDropdownMenuBox(
                    expanded = repositoriesExpanded,
                    onExpandedChange = { repositoriesExpanded = !repositoriesExpanded },
                ) {
                    val selectedRepository = state.repositories.firstOrNull {
                        it.fullName == state.selectedRepositoryFullName
                    }
                    OutlinedTextField(
                        value = selectedRepository?.fullName.orEmpty(),
                        onValueChange = {},
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        label = { Text("المستودع") },
                        placeholder = { Text("اختر مستودعًا من حسابك") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(repositoriesExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = repositoriesExpanded,
                        onDismissRequest = { repositoriesExpanded = false },
                    ) {
                        state.repositories.forEach { repository ->
                            DropdownMenuItem(
                                text = { Text(repository.fullName) },
                                onClick = {
                                    viewModel.selectRepository(repository.fullName)
                                    repositoriesExpanded = false
                                },
                            )
                        }
                    }
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                state.activeRepositoryFullName?.let { activeRepo ->
                    Text("المشاريع المرتبطة بـ $activeRepo", style = MaterialTheme.typography.titleMedium)
                    if (state.linkedProjects.isEmpty()) {
                        Text(
                            "لا توجد مشاريع مرتبطة بهذا المستودع حتى الآن",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.linkedProjects, key = { it.projectId }) { project ->
                                ListItem(
                                    headlineContent = { Text(project.name) },
                                    supportingContent = { Text(project.githubBranch ?: "main") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onProjectClick(project) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

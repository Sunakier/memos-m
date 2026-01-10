package org.example.memosm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Shortcut
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.example.memosm.model.*
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun ProfileScreen(viewModel: MemosViewModel, onLogout: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val stats = uiState.userStats
    val shortcuts = uiState.shortcuts
    val webhooks = uiState.webhooks
    val instance = uiState.instanceProfile
    val userSettings = uiState.userSettings

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp + WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                end = 16.dp,
                bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (user != null) {
                item {
                    ProfileHeader(user)
                }

                item {
                    StatsCard(stats)
                }

                if (userSettings != null) {
                    item {
                        SettingsCard(
                            settings = userSettings, onUpdate = { locale, visibility ->
                                viewModel.updateUserGeneralSetting(locale, visibility)
                            })
                    }
                }

                item {
                    TagsCard(stats?.tagCount ?: emptyMap())
                }

                item {
                    ShortcutsCard(shortcuts)
                }

                item {
                    WebhooksCard(webhooks)
                }

                if (instance != null) {
                    item {
                        InstanceCard(instance)
                    }
                }

                item {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                item {
                    LogoutCard(onLogout)
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            } else if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("User information not available")
                        Button(onClick = { viewModel.refreshAll() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(
                        text = user.displayName ?: user.username ?: "Unknown",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${user.username ?: "unknown"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!user.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = user.description, style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun StatsCard(stats: UserStats?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // First Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Memos",
                    value = stats?.totalMemoCount?.toString() ?: "N/A",
                    icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Tags",
                    value = stats?.tagCount?.size?.toString() ?: "N/A",
                    icon = Icons.Outlined.Tag,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Pinned",
                    value = stats?.pinnedMemos?.size?.toString() ?: "N/A",
                    icon = Icons.Outlined.PushPin,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Second Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    label = "Links",
                    value = stats?.memoTypeStats?.linkCount?.toString() ?: "N/A",
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Code",
                    value = stats?.memoTypeStats?.codeCount?.toString() ?: "N/A",
                    icon = Icons.Outlined.Code,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "Todo",
                    value = stats?.memoTypeStats?.todoCount?.toString() ?: "N/A",
                    icon = Icons.Outlined.TaskAlt,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsCard(tagCount: Map<String, Int>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (tagCount.isEmpty()) {
                Text(
                    text = "No tags found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tagCount.forEach { (tag, count) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = 0.7f
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCard(settings: UserGeneralSetting, onUpdate: (String?, String?) -> Unit) {
    var showLocaleDialog by remember { mutableStateOf(false) }
    var tempLocale by remember { mutableStateOf(settings.locale ?: "") }
    var showVisibilityMenu by remember { mutableStateOf(false) }

    if (showLocaleDialog) {
        AlertDialog(
            onDismissRequest = { showLocaleDialog = false },
            title = { Text("Edit Locale") },
            text = {
                OutlinedTextField(
                    value = tempLocale,
                    onValueChange = { tempLocale = it },
                    label = { Text("Locale (e.g. en, zh-Hans)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(tempLocale, null)
                    showLocaleDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocaleDialog = false }) {
                    Text("Cancel")
                }
            })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                "General Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Locale
            ListItem(
                headlineContent = { Text("Locale") },
                supportingContent = {
                    Text(
                        text = if (settings.locale.isNullOrBlank()) "Default" else settings.locale,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable {
                    tempLocale = settings.locale ?: ""
                    showLocaleDialog = true
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Memo Visibility
            Box {
                ListItem(
                    headlineContent = { Text("Default Memo Visibility") },
                    supportingContent = {
                        Text(
                            text = if (settings.memoVisibility.isNullOrBlank()) "PRIVATE" else settings.memoVisibility,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.Outlined.ArrowDropDown, contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { showVisibilityMenu = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                DropdownMenu(
                    expanded = showVisibilityMenu,
                    onDismissRequest = { showVisibilityMenu = false },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    listOf("PRIVATE", "PROTECTED", "PUBLIC").forEach { visibility ->
                        DropdownMenuItem(text = { Text(visibility) }, onClick = {
                            onUpdate(null, visibility)
                            showVisibilityMenu = false
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutsCard(shortcuts: List<Shortcut>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Shortcuts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (shortcuts.isEmpty()) {
                Text(
                    text = "No shortcuts configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                shortcuts.forEach { shortcut ->
                    ListItem(
                        headlineContent = { Text(shortcut.title ?: "") }, leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Outlined.Shortcut, contentDescription = null
                        )
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun WebhooksCard(webhooks: List<UserWebhook>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Webhooks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (webhooks.isEmpty()) {
                Text(
                    text = "No webhooks configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                webhooks.forEach { webhook ->
                    ListItem(
                        headlineContent = {
                        Text(
                            webhook.displayName ?: webhook.name ?: "Unknown"
                        )
                    }, supportingContent = {
                        Text(
                            text = webhook.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }, leadingContent = {
                        Icon(
                            Icons.Outlined.Webhook, contentDescription = null
                        )
                    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun InstanceCard(instance: InstanceProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Instance Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow("Version", instance.version ?: "Unknown")
            InfoRow("Mode", instance.mode ?: "Unknown")
            InfoRow("URL", instance.instanceUrl ?: "Unknown")
        }
    }
}

@Composable
fun LogoutCard(onLogout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ), onClick = onLogout
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Logout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

package org.example.memosm.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.example.memosm.R
import org.example.memosm.model.*
import org.example.memosm.ui.components.ArchivedMemosScreen
import org.example.memosm.ui.components.composer.getVisibilityLabel
import org.example.memosm.viewmodel.MemosViewModel

@Composable
fun ProfileScreen(
    viewModel: MemosViewModel,
    onLogout: () -> Unit,
    onToggleNavBar: (Boolean) -> Unit
) {
    var isArchivedVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isArchivedVisible) {
        isArchivedVisible = false
    }

    LaunchedEffect(isArchivedVisible) {
        onToggleNavBar(!isArchivedVisible)
    }

    AnimatedContent(
        targetState = isArchivedVisible,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(initialOffsetX = { it }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 2 }) + fadeOut())
            } else {
                (slideInHorizontally(initialOffsetX = { -it / 2 }) + fadeIn())
                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut())
            }
        },
        label = "ProfileArchiveTransition"
    ) { showArchived ->
        if (showArchived) {
            ArchivedMemosScreen(
                viewModel = viewModel,
                onBack = { isArchivedVisible = false },
                onToggleNavBar = onToggleNavBar
            )
        } else {
            ProfileListPane(
                viewModel = viewModel,
                onLogout = onLogout,
                onShowArchived = { isArchivedVisible = true }
            )
        }
    }
}

@Composable
private fun ProfileListPane(
    viewModel: MemosViewModel,
    onLogout: () -> Unit,
    onShowArchived: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val stats = uiState.userStats
    val shortcuts = uiState.shortcuts
    val webhooks = uiState.webhooks
    val instance = uiState.instanceProfile
    val userSettings = uiState.userSettings
    val accounts = uiState.accounts

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
            // Always show accounts at the top
            item {
                AccountsCard(
                    accounts = accounts,
                    onSwitchAccount = { viewModel.switchAccount(it) },
                    onRemoveAccount = { viewModel.removeAccount(it) },
                    onAddAccount = onLogout
                )
            }

            if (user != null) {
                item {
                    ProfileHeader(user)
                }

                item {
                    StatsCard(stats)
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth(), onClick = onShowArchived) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.profile_archived)) },
                            leadingContent = {
                                Icon(
                                    Icons.Outlined.Archive,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(stringResource(R.string.profile_user_info_not_available))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshAll() }) {
                            Text(stringResource(R.string.profile_retry))
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
                    contentDescription = stringResource(R.string.profile_avatar_description),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(
                        text = user.displayName ?: user.username
                        ?: stringResource(R.string.memo_unknown_user),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${user.username ?: stringResource(R.string.memo_unknown_user)}",
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
fun AccountsCard(
    accounts: List<Account>,
    onSwitchAccount: (Account) -> Unit,
    onRemoveAccount: (Account) -> Unit,
    onAddAccount: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onAddAccount) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add Account")
                }
            }

            accounts.forEach { account ->
                ListItem(
                    modifier = Modifier.clickable { if (!account.isActive) onSwitchAccount(account) },
                    headlineContent = {
                        Text(
                            account.displayName ?: account.name ?: "Unknown User",
                            fontWeight = if (account.isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Text(
                            account.hostUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        AsyncImage(
                            model = account.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    },
                    trailingContent = {
                        if (account.isActive) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = "Active",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(onClick = { onRemoveAccount(account) }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Remove Account",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (account.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                    )
                )
            }
        }
    }
}

@Composable
fun StatsCard(stats: UserStats?) {
    val notAvailable = stringResource(R.string.common_not_available)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(R.string.profile_statistics),
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
                    label = stringResource(R.string.profile_stats_memos),
                    value = stats?.totalMemoCount?.toString() ?: notAvailable,
                    icon = Icons.AutoMirrored.Outlined.LibraryBooks,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_tags),
                    value = stats?.tagCount?.size?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Tag,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_pinned),
                    value = stats?.pinnedMemos?.size?.toString() ?: notAvailable,
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
                    label = stringResource(R.string.profile_stats_links),
                    value = stats?.memoTypeStats?.linkCount?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Link,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_code),
                    value = stats?.memoTypeStats?.codeCount?.toString() ?: notAvailable,
                    icon = Icons.Outlined.Code,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = stringResource(R.string.profile_stats_todo),
                    value = stats?.memoTypeStats?.todoCount?.toString() ?: notAvailable,
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
                stringResource(R.string.profile_stats_tags),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (tagCount.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_tags_no_tags),
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
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
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
            title = { Text(stringResource(R.string.profile_settings_locale_edit)) },
            text = {
                OutlinedTextField(
                    value = tempLocale,
                    onValueChange = { tempLocale = it },
                    label = { Text(stringResource(R.string.profile_settings_locale_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdate(tempLocale, null)
                    showLocaleDialog = false
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocaleDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.profile_settings_general),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Locale
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_settings_locale)) },
                supportingContent = {
                    Text(
                        text = if (settings.locale.isNullOrBlank()) stringResource(R.string.profile_settings_locale_default) else settings.locale,
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
                    headlineContent = { Text(stringResource(R.string.profile_settings_visibility)) },
                    supportingContent = {
                        Text(
                            text = if (settings.memoVisibility.isNullOrBlank()) getVisibilityLabel("PRIVATE") else getVisibilityLabel(
                                settings.memoVisibility
                            ),
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
                        DropdownMenuItem(
                            text = { Text(getVisibilityLabel(visibility)) },
                            onClick = {
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
                stringResource(R.string.profile_shortcuts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (shortcuts.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_shortcuts_none),
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
                stringResource(R.string.profile_webhooks),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (webhooks.isEmpty()) {
                Text(
                    text = stringResource(R.string.profile_webhooks_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                webhooks.forEach { webhook ->
                    ListItem(
                        headlineContent = {
                            Text(
                                webhook.displayName ?: webhook.name
                                ?: stringResource(R.string.memo_unknown_user)
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
                stringResource(R.string.profile_instance_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            val unknown = stringResource(R.string.memo_unknown_user)
            InfoRow(
                stringResource(R.string.profile_instance_version),
                instance.version ?: unknown
            )
            InfoRow(
                stringResource(R.string.profile_instance_mode),
                instance.mode ?: unknown
            )
            InfoRow(
                stringResource(R.string.profile_instance_url),
                instance.instanceUrl ?: unknown
            )
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
                stringResource(R.string.profile_logout),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
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

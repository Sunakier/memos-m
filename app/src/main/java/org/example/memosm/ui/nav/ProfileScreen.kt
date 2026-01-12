package org.example.memosm.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import org.example.memosm.ui.components.ErrorView
import org.example.memosm.ui.components.composer.getVisibilityLabel
import org.example.memosm.viewmodel.MemosViewModel

private val SUPPORTED_LANGUAGES = listOf(
    "ar" to "العربية",
    "cs" to "Čeština",
    "de" to "Deutsch",
    "en" to "English",
    "en-GB" to "British English",
    "es" to "Español",
    "fa" to "فارسی",
    "fr" to "Français",
    "hi" to "हिन्दी",
    "hr" to "Hrvatski",
    "hu" to "Magyar",
    "id" to "Indonesia",
    "it" to "Italiano",
    "ja" to "日本語",
    "ka" to "ქართული (საქართველო)",
    "ko" to "한국어",
    "mr" to "मराठी",
    "nb" to "Norsk bokmål",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "pt-BR" to "Português (Brasil)",
    "pt" to "Português europeu",
    "ru" to "Русский",
    "sl" to "Slovenščina",
    "sv" to "Svenska",
    "th" to "ไทย",
    "tr" to "Türkçe",
    "uk" to "Українська",
    "vi" to "Tiếng Việt",
    "zh-Hans" to "简体中文",
    "zh-Hant" to "繁體中文"
)

@OptIn(ExperimentalSharedTransitionApi::class)
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

    SharedTransitionLayout {
        AnimatedContent(
            targetState = isArchivedVisible,
            transitionSpec = {
                if (targetState) {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ))
                        .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)))
                } else {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow))
                        .togetherWith(
                            fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                                    scaleOut(
                                        targetScale = 0.92f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    )
                        )
                }
            },
            label = "ProfileArchiveTransition"
        ) { showArchived ->
            if (showArchived) {
                ArchivedMemosScreen(
                    viewModel = viewModel,
                    onBack = { isArchivedVisible = false },
                    onToggleNavBar = onToggleNavBar,
                    modifier = Modifier.sharedBounds(
                        rememberSharedContentState(key = "archived_container"),
                        animatedVisibilityScope = this@AnimatedContent,
                        boundsTransform = { _, _ ->
                            spring(dampingRatio = 0.8f, stiffness = 380f)
                        }
                    )
                )
            } else {
                ProfileListPane(
                    viewModel = viewModel,
                    onLogout = onLogout,
                    onShowArchived = { isArchivedVisible = true },
                    animatedVisibilityScope = this@AnimatedContent,
                    sharedTransitionScope = this@SharedTransitionLayout
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProfileListPane(
    viewModel: MemosViewModel,
    onLogout: () -> Unit,
    onShowArchived: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val stats = uiState.userStats
    val shortcuts = uiState.shortcuts
    val webhooks = uiState.webhooks
    val instance = uiState.instanceProfile
    val userSettings = uiState.userSettings
    val accounts = uiState.accounts

    var showAccountSwitcher by remember { mutableStateOf(false) }

    if (showAccountSwitcher) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSwitcher = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AccountsList(
                accounts = accounts,
                onSwitchAccount = {
                    viewModel.switchAccount(it)
                    showAccountSwitcher = false
                },
                onRemoveAccount = { viewModel.removeAccount(it) },
                onAddAccount = {
                    onLogout()
                    showAccountSwitcher = false
                },
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

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
            item {
                if (user != null) {
                    ProfileHeader(user, onClick = { showAccountSwitcher = true })
                } else {
                    val activeAccount = accounts.find { it.isActive }
                    if (activeAccount != null) {
                        ProfileHeader(
                            User(
                                username = activeAccount.name ?: "",
                                displayName = activeAccount.displayName,
                                avatarUrl = activeAccount.avatarUrl
                            ),
                            onClick = { showAccountSwitcher = true }
                        )
                    } else if (uiState.isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }

            if (user != null || accounts.any { it.isActive }) {
                item {
                    StatsCard(stats)
                }

                item {
                    with(sharedTransitionScope) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sharedBounds(
                                    rememberSharedContentState(key = "archived_container"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        spring(dampingRatio = 0.8f, stiffness = 380f)
                                    }
                                ),
                            onClick = onShowArchived
                        ) {
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
                }

                item {
                    SettingsCard(
                        settings = userSettings ?: UserGeneralSetting(), 
                        onUpdate = { locale, visibility ->
                            viewModel.updateUserGeneralSetting(locale, visibility)
                        }
                    )
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
                
                if (uiState.error != null) {
                    item {
                        ErrorView(
                            title = stringResource(R.string.common_error_failed_to_load_profile),
                            message = uiState.error!!,
                            onRetry = { viewModel.refreshAll() }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            } else if (!uiState.isLoading) {
                item {
                    ErrorView(
                        message = uiState.error ?: stringResource(R.string.profile_user_info_not_available),
                        onRetry = { viewModel.refreshAll() },
                        modifier = Modifier.fillParentMaxHeight(0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        onClick = onClick
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
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        text = if (!user.username.isNullOrBlank()) "@${user.username}" else stringResource(R.string.memo_unknown_user),
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
fun AccountsList(
    accounts: List<Account>,
    onSwitchAccount: (Account) -> Unit,
    onRemoveAccount: (Account) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 16.dp)) {
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
                    containerColor = if (account.isActive) MaterialTheme.colorScheme.primaryContainer.copy(
                        alpha = 0.3f
                    ) else Color.Transparent
                )
            )
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
        var expanded by remember { mutableStateOf(false) }
        val initialDisplayName =
            SUPPORTED_LANGUAGES.find { it.first == tempLocale }?.second ?: tempLocale
        var textFieldValue by remember { mutableStateOf(initialDisplayName) }

        val filteredOptions = if (textFieldValue.isEmpty()) {
            SUPPORTED_LANGUAGES
        } else {
            SUPPORTED_LANGUAGES.filter {
                it.second.contains(textFieldValue, ignoreCase = true) ||
                        it.first.contains(textFieldValue, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showLocaleDialog = false },
            title = { Text(stringResource(R.string.profile_settings_locale_edit)) },
            text = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                    ) {
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                expanded = true
                                val exactMatch = SUPPORTED_LANGUAGES.find { lang ->
                                    lang.second.equals(
                                        it,
                                        ignoreCase = true
                                    )
                                }
                                if (exactMatch != null) {
                                    tempLocale = exactMatch.first
                                } else {
                                    tempLocale = it
                                }
                            },
                            label = { Text(stringResource(R.string.profile_settings_locale_label)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        if (filteredOptions.isNotEmpty()) {
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                filteredOptions.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption.second) },
                                        onClick = {
                                            textFieldValue = selectionOption.second
                                            tempLocale = selectionOption.first
                                            expanded = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                }
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
                    val displayName =
                        SUPPORTED_LANGUAGES.find { it.first == settings.locale }?.second
                            ?: if (settings.locale.isNullOrBlank()) stringResource(R.string.profile_settings_locale_default) else settings.locale
                    Text(
                        text = displayName,
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

package org.example.memosm.ui.nav

import ProfileHeader
import SettingsCard
import StatsCard
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.core.net.toUri

private val KAOMOJIS = listOf(
    "(ﾉ´ з `)ノ", "(o^ ^o)", "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "(⁄ ⁄>⁄ ▽ ⁄<⁄ ⁄)", "(￣▽￣*)ゞ"
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ProfileScreen(
    viewModel: MemosViewModel, onLogout: () -> Unit, onToggleNavBar: (Boolean) -> Unit
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
            targetState = isArchivedVisible, transitionSpec = {
                if (targetState) {
                    (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(
                        initialScale = 0.92f, animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )).togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMediumLow)))
                } else {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)).togetherWith(
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) + scaleOut(
                            targetScale = 0.92f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    )
                }
            }, label = "ProfileArchiveTransition"
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
                        })
                )
            } else {
                ProfileListPane(
                    viewModel = viewModel,
                    onLogout = onLogout,
                    onShowArchived = { isArchivedVisible = true },
                    animatedVisibilityScope = this@AnimatedContent,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    onToggleNavBar = onToggleNavBar
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
    sharedTransitionScope: SharedTransitionScope,
    onToggleNavBar: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.currUser
    val stats = uiState.userStats
    val shortcuts = uiState.shortcuts
    val webhooks = uiState.webhooks
    val instance = uiState.instanceProfile
    val userSettings = uiState.userSettings
    val accounts = uiState.accounts

    val listState = rememberLazyListState()

    // Scroll direction tracking for nav bar visibility
    var isScrollingDown by remember { mutableStateOf(false) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (currentIndex, currentOffset) ->
            val wasScrollingDown = isScrollingDown
            if (currentIndex > previousIndex) {
                isScrollingDown = true
            } else if (currentIndex < previousIndex) {
                isScrollingDown = false
            } else if (currentOffset > previousScrollOffset + 10) {
                isScrollingDown = true
            } else if (currentOffset < previousScrollOffset - 10) {
                isScrollingDown = false
            }

            if (wasScrollingDown != isScrollingDown || currentIndex == 0) {
                onToggleNavBar(!isScrollingDown || currentIndex == 0)
            }

            previousIndex = currentIndex
            previousScrollOffset = currentOffset
        }
    }

    // Double tap refresh logic: scroll to top
    var lastProcessedTrigger by rememberSaveable { mutableLongStateOf(uiState.refreshTrigger) }
    LaunchedEffect(uiState.refreshTrigger) {
        if (uiState.refreshTrigger > lastProcessedTrigger) {
            listState.animateScrollToItem(0)
        }
        lastProcessedTrigger = uiState.refreshTrigger
    }

    var showAccountSwitcher by remember { mutableStateOf(false) }

    if (showAccountSwitcher) {
        ModalBottomSheet(
            onDismissRequest = { showAccountSwitcher = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AccountsList(
                accounts = accounts, onSwitchAccount = {
                    viewModel.switchAccount(it)
                    showAccountSwitcher = false
                }, onRemoveAccount = { viewModel.removeAccount(it) }, onAddAccount = {
                    onLogout()
                    showAccountSwitcher = false
                }, modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshAll() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
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
            val itemModifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()

            item {
                Box(itemModifier) {
                    if (user != null) {
                        ProfileHeader(user, onClick = { showAccountSwitcher = true })
                    } else {
                        val activeAccount = accounts.find { it.isActive }
                        if (activeAccount != null) {
                            ProfileHeader(
                                User(
                                    name = activeAccount.name?.let { "users/$it" },
                                    username = activeAccount.name ?: "",
                                    displayName = activeAccount.displayName,
                                    avatarUrl = activeAccount.avatarUrl
                                ), onClick = { showAccountSwitcher = true })
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
            }

            if (user != null || accounts.any { it.isActive }) {
                item {
                    Box(itemModifier) {
                        StatsCard(stats)
                    }
                }

                item {
                    Box(itemModifier) {
                        with(sharedTransitionScope) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .sharedBounds(
                                        rememberSharedContentState(key = "archived_container"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { _, _ ->
                                            spring(dampingRatio = 0.8f, stiffness = 380f)
                                        }), onClick = onShowArchived
                            ) {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.profile_archived)) },
                                    leadingContent = {
                                        Icon(
                                            Icons.Outlined.Archive, contentDescription = null
                                        )
                                    },
                                    trailingContent = {
                                        Icon(
                                            Icons.Outlined.ChevronRight, contentDescription = null
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }

                item {
                    Box(itemModifier) {
                        SettingsCard(
                            settings = userSettings ?: UserGeneralSetting(),
                            onUpdate = { locale, visibility ->
                                viewModel.updateUserGeneralSetting(locale, visibility)
                            })
                    }
                }

                item {
                    Box(itemModifier) {
                        ShortcutsCard(shortcuts)
                    }
                }

                item {
                    Box(itemModifier) {
                        WebhooksCard(webhooks)
                    }
                }

                if (instance != null) {
                    item {
                        Box(itemModifier) {
                            InstanceCard(instance)
                        }
                    }
                }

                item {
                    Box(itemModifier) {
                        AboutCard()
                    }
                }

                if (uiState.error != null) {
                    item {
                        Box(itemModifier) {
                            ErrorView(
                                title = stringResource(R.string.common_error_failed_to_load_profile),
                                message = uiState.error!!,
                                onRetry = { viewModel.refreshAll() })
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(64.dp))
                }
            } else if (!uiState.isLoading) {
                item {
                    ErrorView(
                        message = uiState.error
                            ?: stringResource(R.string.profile_user_info_not_available),
                        onRetry = { viewModel.refreshAll() },
                        modifier = itemModifier.fillParentMaxHeight(0.7f)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsList(
    accounts: List<Account>,
    onSwitchAccount: (Account) -> Unit,
    onRemoveAccount: (Account) -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accountToRemove by remember { mutableStateOf<Account?>(null) }

    if (accountToRemove != null) {
        AlertDialog(
            onDismissRequest = { accountToRemove = null },
            title = { Text(stringResource(R.string.profile_remove_account_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.profile_remove_account_confirm,
                        accountToRemove?.displayName ?: accountToRemove?.name ?: "Unknown"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    accountToRemove?.let { onRemoveAccount(it) }
                    accountToRemove = null
                }) {
                    Text(
                        stringResource(R.string.common_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToRemove = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    Column(modifier = modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.profile_accounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onAddAccount) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.profile_add_account)
                )
            }
        }

        accounts.forEach { account ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        accountToRemove = account
                        false // Don't dismiss immediately, wait for confirmation
                    } else {
                        false
                    }
                }
            )

            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        val color by animateColorAsState(
                            when (dismissState.targetValue) {
                                SwipeToDismissBoxValue.Settled -> Color.Transparent
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                else -> Color.Transparent
                            }, label = "dismiss_background"
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CardDefaults.shape)
                                .background(color)
                                .padding(horizontal = 24.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (account.isActive)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        onClick = { if (!account.isActive) onSwitchAccount(account) }
                    ) {
                        ListItem(
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
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
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
                stringResource(R.string.profile_instance_version), instance.version ?: unknown
            )
            InfoRow(
                stringResource(R.string.profile_instance_mode), instance.mode ?: unknown
            )
            InfoRow(
                stringResource(R.string.profile_instance_url), instance.instanceUrl ?: unknown
            )
        }
    }
}

@Composable
fun AboutCard() {
    val context = LocalContext.current
    val packageInfo = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "1.0"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.profile_about),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(
                stringResource(R.string.profile_about_version),
                versionName,
                modifier = Modifier
                    .clickable {
                        Toast.makeText(context, KAOMOJIS.random(), Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp))

            val repoUrl = stringResource(R.string.profile_about_repo_url)
            val issuesUrl = stringResource(R.string.profile_about_issues_url)

            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_about_repo)) },
                leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, repoUrl.toUri())
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_about_issues)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, issuesUrl.toUri())
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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

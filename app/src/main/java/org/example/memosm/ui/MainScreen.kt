package org.example.memosm.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.network.httpHeaders
import kotlinx.coroutines.launch
import org.example.memosm.R
import org.example.memosm.data.DataStoreManager
import org.example.memosm.data.DraftManager
import org.example.memosm.model.Attachment
import org.example.memosm.model.Location
import org.example.memosm.model.ShareIntentData
import org.example.memosm.ui.component.LoginDialog
import org.example.memosm.ui.nav.AttachmentsScreen
import org.example.memosm.ui.nav.ExploreScreen
import org.example.memosm.ui.nav.MemosScreen
import org.example.memosm.ui.nav.ProfileScreen
import org.example.memosm.viewmodel.MemosViewModel

import org.example.memosm.ui.component.resolveResourceUrl
import org.example.memosm.ui.component.item.media.MemoImage
import androidx.core.net.toUri
import org.example.memosm.ui.component.composer.MemoComposerDialog

enum class MainDestination(
    val labelRes: Int
) {
    MEMOS(R.string.nav_memos), EXPLORE(R.string.nav_explore), ATTACHMENTS(R.string.nav_attachments), PROFILE(
        R.string.nav_profile
    )
}

@Composable
fun MainScreen(
    baseUrl: String,
    token: String,
    dataStoreManager: DataStoreManager,
    draftManager: DraftManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    shareIntentData: ShareIntentData? = null,
    onShareIntentConsumed: () -> Unit = {},
    shouldOpenComposer: Boolean = false,
    onComposerOpened: () -> Unit = {}
) {
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.MEMOS) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val viewModel: MemosViewModel =
        viewModel(factory = MemosViewModel.provideFactory(dataStoreManager, draftManager))
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val saveableStateHolder = rememberSaveableStateHolder()

    var isNavBarVisible by remember { mutableStateOf(true) }
    var isAddingAccount by remember { mutableStateOf(false) }

    // Share intent composer dialog state
    var showShareComposerDialog by remember { mutableStateOf(false) }
    var shareText by remember { mutableStateOf<String?>(null) }
    var shareUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var shareAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var shareVisibility by remember { mutableStateOf<String?>(null) }
    var shareLocation by remember { mutableStateOf<Location?>(null) }
    
    // Track if we've already processed the current share intent
    var processedShareData by remember { mutableStateOf<ShareIntentData?>(null) }

    // Trigger composer when share data is received - APPEND to existing draft
    // Wait for draft to be loaded before processing to avoid race condition
    val isDraftLoaded = uiState.draft.isDraftLoaded
    
    // Switch to Memos tab if widget triggered composer
    LaunchedEffect(shouldOpenComposer) {
        if (shouldOpenComposer) {
            currentDestination = MainDestination.MEMOS
        }
    }
    
    LaunchedEffect(shareIntentData, isDraftLoaded) {
        // Only process if:
        // 1. We have share data
        // 2. Draft has been loaded
        // 3. We haven't already processed this exact share data
        if (shareIntentData != null && !shareIntentData.isEmpty && isDraftLoaded && processedShareData != shareIntentData) {
            // Use the latest draft if available
            val latestDraft = uiState.draft.drafts.maxByOrNull { it.updatedAt }

            // Append shared text to existing draft content
            val existingContent = latestDraft?.content ?: ""
            val sharedText = shareIntentData.text ?: ""
            shareText = if (existingContent.isNotBlank() && sharedText.isNotBlank()) {
                "$existingContent\n\n$sharedText"
            } else {
                existingContent + sharedText
            }

            // Combine shared URIs with existing draft attachments
            shareUris = shareIntentData.uris
            shareAttachments = latestDraft?.attachments ?: emptyList()
            shareVisibility = latestDraft?.visibility
            shareLocation = latestDraft?.location

            // Set the draft as current editing draft if exists, otherwise initialize new session
            if (latestDraft != null) {
                viewModel.setCurrentEditingDraft(latestDraft.id)
            } else {
                viewModel.initializeNewDraftSession()
            }

            processedShareData = shareIntentData
            showShareComposerDialog = true
            onShareIntentConsumed()
        }
    }

    DisposableEffect(currentDestination) {
        focusManager.clearFocus()
        onDispose { }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isMobile = layoutType == NavigationSuiteType.NavigationBar

    val toggleNavBar: ((Boolean) -> Unit)? = if (isMobile) {
        { isNavBarVisible = it }
    } else null


    if (isAddingAccount) {
        LoginDialog(onLoginSuccess = { newBaseUrl, newToken ->
            scope.launch {
                dataStoreManager.addAccount(newBaseUrl, newToken)
                viewModel.updateCurrentAccountInList()
                isAddingAccount = false
            }
        }, onDismiss = { isAddingAccount = false })
    }

    // Share intent composer dialog
    if (showShareComposerDialog && uiState.session.currUser != null) {
        MemoComposerDialog(
            onDismiss = {
                showShareComposerDialog = false
                shareText = null
                shareUris = emptyList()
                shareAttachments = emptyList()
                shareVisibility = null
                shareLocation = null
            },
            viewModel = viewModel,
            hostUrl = uiState.session.hostUrl,
            title = stringResource(R.string.memo_composer_fab_new_memo),
            initialContent = shareText ?: "",
            initialUris = shareUris,
            initialAttachments = shareAttachments,
            initialVisibility = shareVisibility,
            initialLocation = shareLocation
        )
    }

    @Composable
    fun NavigationIcon(
        destination: MainDestination, isSelected: Boolean, modifier: Modifier = Modifier.size(24.dp)
    ) {
        when (destination) {
            MainDestination.MEMOS -> Icon(
                if (isSelected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks,
                contentDescription = null,
                modifier = modifier
            )

            MainDestination.EXPLORE -> Icon(
                if (isSelected) Icons.Default.Public else Icons.Outlined.Public,
                contentDescription = null,
                modifier = modifier
            )

            MainDestination.ATTACHMENTS -> Icon(
                if (isSelected) Icons.Default.Attachment else Icons.Outlined.Attachment,
                contentDescription = null,
                modifier = modifier
            )

            MainDestination.PROFILE -> {
                val user = uiState.session.currUser
                val account = uiState.accounts.find { it.isActive }
                val rawAvatarUrl = user?.avatarUrl ?: account?.avatarUrl
                val hostUrl = uiState.session.hostUrl

                val avatarUri = remember(rawAvatarUrl, hostUrl) {
                    if (rawAvatarUrl.isNullOrBlank()) Uri.EMPTY
                    else (resolveResourceUrl(hostUrl, rawAvatarUrl) ?: "").toUri()
                }

                MemoImage(
                    attachment = null,
                    token = uiState.session.token,
                    hostUrl = hostUrl,
                    uri = avatarUri,
                    filename = "avatar",
                    isRound = true,
                    placeholderIcon = if (isSelected) Icons.Default.Person else Icons.Outlined.Person,
                    modifier = modifier
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, CircleShape
                            ) else Modifier
                        )
                        .padding(if (isSelected) 1.dp else 0.dp)
                )
            }
        }
    }

    fun handleDestinationClick(destination: MainDestination) {
        focusManager.clearFocus()
        val currentTime = System.currentTimeMillis()
        if (currentDestination == destination && currentTime - lastTapTime < 500) {
            when (destination) {
                MainDestination.MEMOS -> viewModel.fetchUserMemos(refresh = true)
                MainDestination.EXPLORE -> viewModel.fetchExploreMemos(refresh = true)
                MainDestination.ATTACHMENTS -> viewModel.fetchAttachments(refresh = true)
                else -> {}
            }
        }
        currentDestination = destination
        lastTapTime = currentTime
    }

    Surface(
        modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                // Navigation Rail for tablets/desktops
                if (!isMobile) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Spacer(Modifier.height(12.dp))
                        // Items
                        MainDestination.entries.forEach { destination ->
                            if (destination == MainDestination.PROFILE) {
                                Spacer(Modifier.weight(1f))
                            }
                            NavigationRailItem(
                                selected = currentDestination == destination,
                                onClick = { handleDestinationClick(destination) },
                                icon = {
                                    NavigationIcon(
                                        destination, currentDestination == destination
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) })
                        }
                    }
                }

                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Content
                    AnimatedContent(
                        targetState = currentDestination,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(
                                animationSpec = tween(220)
                            )
                        },
                        label = "MainScreenDestinationTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { targetDestination ->
                        saveableStateHolder.SaveableStateProvider(targetDestination) {
                            when (targetDestination) {
                                MainDestination.MEMOS -> MemosScreen(
                                    viewModel = viewModel, 
                                    onToggleNavBar = toggleNavBar,
                                    openComposer = shouldOpenComposer,
                                    onComposerOpened = onComposerOpened
                                )

                                MainDestination.EXPLORE -> ExploreScreen(
                                    viewModel = viewModel, onToggleNavBar = toggleNavBar
                                )

                                MainDestination.ATTACHMENTS -> AttachmentsScreen(
                                    viewModel = viewModel, onToggleNavBar = toggleNavBar
                                )

                                MainDestination.PROFILE -> ProfileScreen(
                                    viewModel = viewModel,
                                    onLogout = onLogout,
                                    onAddAccount = { isAddingAccount = true },
                                    onToggleNavBar = toggleNavBar
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Navigation Bar for mobile
            if (isMobile) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    AnimatedVisibility(
                        visible = isNavBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            MainDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = currentDestination == destination,
                                    onClick = { handleDestinationClick(destination) },
                                    icon = {
                                        NavigationIcon(
                                            destination, currentDestination == destination
                                        )
                                    },
                                    label = { Text(stringResource(destination.labelRes)) })
                            }
                        }
                    }
                }
            }
        }
    }
}

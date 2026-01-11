package org.example.memosm.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import org.example.memosm.R
import org.example.memosm.data.DataStoreManager
import org.example.memosm.ui.nav.AttachmentsScreen
import org.example.memosm.ui.nav.ExploreScreen
import org.example.memosm.ui.nav.MemosScreen
import org.example.memosm.ui.nav.ProfileScreen
import org.example.memosm.viewmodel.MemosViewModel

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
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.MEMOS) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val viewModel: MemosViewModel =
        viewModel(factory = MemosViewModel.provideFactory(baseUrl, token, dataStoreManager))
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val saveableStateHolder = rememberSaveableStateHolder()

    var isNavBarVisible by remember { mutableStateOf(true) }

    DisposableEffect(currentDestination) {
        focusManager.clearFocus()
        onDispose { }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isMobile = layoutType == NavigationSuiteType.NavigationBar

    @Composable
    fun NavigationIcon(destination: MainDestination, isSelected: Boolean) {
        when (destination) {
            MainDestination.MEMOS -> Icon(
                if (isSelected) Icons.AutoMirrored.Filled.LibraryBooks else Icons.AutoMirrored.Outlined.LibraryBooks,
                contentDescription = null
            )

            MainDestination.EXPLORE -> Icon(
                if (isSelected) Icons.Default.Public else Icons.Outlined.Public,
                contentDescription = null
            )

            MainDestination.ATTACHMENTS -> Icon(
                if (isSelected) Icons.Default.Attachment else Icons.Outlined.Attachment,
                contentDescription = null
            )

            MainDestination.PROFILE -> {
                val avatarUrl = uiState.user?.avatarUrl
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary, CircleShape
                                ) else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (isSelected) Icons.Default.Person else Icons.Outlined.Person,
                        contentDescription = null
                    )
                }
            }
        }
    }

    fun handleDestinationClick(destination: MainDestination) {
        focusManager.clearFocus()
        val currentTime = System.currentTimeMillis()
        if (currentDestination == destination && currentTime - lastTapTime < 500) {
            when (destination) {
                MainDestination.MEMOS -> viewModel.refreshAll()
                MainDestination.EXPLORE -> viewModel.fetchExplore(refresh = true)
                MainDestination.ATTACHMENTS -> viewModel.fetchAttachments()
                else -> {}
            }
        }
        currentDestination = destination
        lastTapTime = currentTime
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isMobile) {
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
                                        destination,
                                        currentDestination == destination
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) }
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Row(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Navigation Rail for tablets/desktops
            if (!isMobile) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    MainDestination.entries.filter { it != MainDestination.PROFILE }.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { handleDestinationClick(destination) },
                            icon = {
                                NavigationIcon(
                                    destination,
                                    currentDestination == destination
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MainDestination.entries.filter { it == MainDestination.PROFILE }.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { handleDestinationClick(destination) },
                            icon = {
                                NavigationIcon(
                                    destination,
                                    currentDestination == destination
                                )
                            },
                            label = { Text(stringResource(destination.labelRes)) }
                        )
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
                                onToggleNavBar = { if (isMobile) isNavBarVisible = it }
                            )

                            MainDestination.EXPLORE -> ExploreScreen(
                                viewModel = viewModel,
                                onToggleNavBar = { if (isMobile) isNavBarVisible = it }
                            )

                            MainDestination.ATTACHMENTS -> AttachmentsScreen(
                                viewModel = viewModel,
                                onToggleNavBar = { if (isMobile) isNavBarVisible = it }
                            )

                            MainDestination.PROFILE -> ProfileScreen(
                                viewModel = viewModel,
                                onLogout = onLogout,
                                onToggleNavBar = { visible -> if (isMobile) isNavBarVisible = visible }
                            )
                        }
                    }
                }
            }
        }
    }
}

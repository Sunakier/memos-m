package org.example.memosm.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.example.memosm.R
import org.example.memosm.data.DataStoreManager
import org.example.memosm.viewmodel.MemosViewModel

enum class MainDestination(
    val labelRes: Int
) {
    MEMOS(R.string.nav_memos),
    EXPLORE(R.string.nav_explore),
    ATTACHMENTS(R.string.nav_attachments),
    PROFILE(R.string.nav_profile)
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

    // Ensure focus is cleared whenever we switch screens
    DisposableEffect(currentDestination) {
        focusManager.clearFocus()
        onDispose { }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MainDestination.entries.forEach { destination ->
                item(selected = currentDestination == destination, onClick = {
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
                }, icon = {
                    val isSelected = currentDestination == destination
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
                }, label = { Text(stringResource(destination.labelRes)) })
            }
        }, modifier = modifier
    ) {
        Box(Modifier.fillMaxSize()) {
            // Use Box to keep all screens in memory but only show the active one.
            // This preserves scroll state and other UI state within each screen.
            
            // Memos Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = currentDestination == MainDestination.MEMOS,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MemosListScreen(viewModel)
            }
            
            // Explore Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = currentDestination == MainDestination.EXPLORE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ExploreScreen(viewModel)
            }
            
            // Attachments Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = currentDestination == MainDestination.ATTACHMENTS,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                AttachmentsScreen(viewModel)
            }
            
            // Profile Screen
            androidx.compose.animation.AnimatedVisibility(
                visible = currentDestination == MainDestination.PROFILE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ProfileScreen(viewModel, onLogout)
            }
        }
    }
}

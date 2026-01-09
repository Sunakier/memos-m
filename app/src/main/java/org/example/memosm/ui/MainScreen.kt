package org.example.memosm.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.example.memosm.data.DataStoreManager
import org.example.memosm.viewmodel.MemosViewModel

enum class MainDestination(
    val label: String
) {
    MEMOS("Memos"), EXPLORE("Explore"), ATTACHMENTS("Attachments"), PROFILE("Profile")
}

@Composable
fun MainScreen(
    baseUrl: String,
    token: String,
    dataStoreManager: DataStoreManager,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(MainDestination.MEMOS) }
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
                item(
                    selected = currentDestination == destination,
                    onClick = {
                        focusManager.clearFocus()
                        currentDestination = destination
                    },
                    icon = {
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
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
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
                    },
                    label = { Text(destination.label) })
            }
        }, modifier = modifier
    ) {
        when (currentDestination) {
            MainDestination.MEMOS -> MemosListScreen(viewModel)
            MainDestination.EXPLORE -> ExploreScreen(viewModel)
            MainDestination.ATTACHMENTS -> AttachmentsScreen(viewModel)
            MainDestination.PROFILE -> ProfileScreen(viewModel, onLogout)
        }
    }
}

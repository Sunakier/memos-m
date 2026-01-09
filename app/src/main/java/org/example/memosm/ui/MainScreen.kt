package org.example.memosm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import org.example.memosm.model.*
import org.example.memosm.viewmodel.MemosViewModel

enum class MainDestination(
    val label: String
) {
    MEMOS("Memos"), EXPLORE("Explore"), ATTACHMENTS("Attachments"), PROFILE("Profile")
}

@Composable
fun MainScreen(
    baseUrl: String, token: String, onLogout: () -> Unit, modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf(MainDestination.MEMOS) }
    val viewModel: MemosViewModel =
        viewModel(factory = MemosViewModel.provideFactory(baseUrl, token))
    val uiState by viewModel.uiState.collectAsState()

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MainDestination.entries.forEach { destination ->
                item(
                    selected = currentDestination == destination,
                    onClick = { currentDestination = destination },
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
            MainDestination.EXPLORE -> PlaceholderScreen("Explore")
            MainDestination.ATTACHMENTS -> PlaceholderScreen("Attachments")
            MainDestination.PROFILE -> ProfileScreen(viewModel, onLogout)
        }
    }
}

@Composable
fun MemosListScreen(viewModel: MemosViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleItem =
                listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadMore()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            uiState.isLoading && uiState.memos.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error != null && uiState.memos.isEmpty() -> {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 800.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        CreateMemoCard(
                            onPublish = { content, visibility ->
                                viewModel.createMemo(content, visibility)
                            },
                            isPosting = uiState.isPosting,
                            availableTags = uiState.userStats?.tagCount?.keys ?: emptySet()
                        )
                    }

                    items(uiState.memos) { memo ->
                        MemoItem(memo)
                    }

                    if (uiState.isLoading && uiState.memos.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(viewModel: MemosViewModel, onLogout: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val user = uiState.user
    val stats = uiState.userStats
    val shortcuts = uiState.shortcuts
    val instance = uiState.instanceProfile

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (user != null) {
                item {
                    ProfileHeader(user)
                }

                if (stats != null) {
                    item {
                        StatsCard(stats)
                    }
                }

                if (shortcuts.isNotEmpty()) {
                    item {
                        ShortcutsCard(shortcuts)
                    }
                }

                if (instance != null) {
                    item {
                        InstanceCard(instance)
                    }
                }

                item {
                    LogoutCard(onLogout)
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
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
                        text = "@${user.username}",
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
fun StatsCard(stats: UserStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatItem(
                    label = "Memos",
                    value = stats.totalMemoCount.toString(),
                    icon = Icons.Default.Description
                )
                StatItem(
                    label = "Tags", value = stats.tagCount.size.toString(), icon = Icons.Default.Tag
                )
                StatItem(
                    label = "Pinned",
                    value = stats.pinnedMemos.size.toString(),
                    icon = Icons.Default.PushPin
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Content Breakdown", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatSubItem("Links", stats.memoTypeStats.linkCount)
                StatSubItem("Code", stats.memoTypeStats.codeCount)
                StatSubItem("Todo", stats.memoTypeStats.todoCount)
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
fun StatSubItem(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall)
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
            shortcuts.forEach { shortcut ->
                ListItem(
                    headlineContent = { Text(shortcut.title) }, leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Shortcut, contentDescription = null
                    )
                }, colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
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
            InfoRow("Version", instance.version)
            InfoRow("Mode", instance.mode)
            InfoRow("URL", instance.instanceUrl)
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
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMemoCard(
    onPublish: (String, String) -> Unit, isPosting: Boolean, availableTags: Set<String>
) {
    val contentState = rememberTextFieldState("")
    var visibility by remember { mutableStateOf("PRIVATE") }
    var expanded by remember { mutableStateOf(false) }
    val visibilityOptions = listOf("PRIVATE", "PROTECTED", "PUBLIC")

    // Tag autocomplete logic
    var showTagPopup by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf("") }
    var tagStartIndex by remember { mutableStateOf(-1) }

    // To track cursor position
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(contentState.text, contentState.selection) {
        val text = contentState.text.toString()
        val selection = contentState.selection
        val cursorIndex = selection.start
        if (cursorIndex > 0 && selection.collapsed) {
            val textBeforeCursor = text.substring(0, cursorIndex)
            val lastHashIndex = textBeforeCursor.lastIndexOf('#')

            if (lastHashIndex != -1) {
                val potentialTag = textBeforeCursor.substring(lastHashIndex + 1)
                if (!potentialTag.contains(' ') && !potentialTag.contains('\n')) {
                    showTagPopup = true
                    tagFilter = potentialTag
                    tagStartIndex = lastHashIndex
                } else {
                    showTagPopup = false
                }
            } else {
                showTagPopup = false
            }
        } else {
            showTagPopup = false
        }
    }

    val filteredTags = remember(tagFilter, availableTags) {
        if (tagFilter.isEmpty()) {
            availableTags.toList()
        } else {
            availableTags.filter { it.contains(tagFilter, ignoreCase = true) }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box {
                OutlinedTextField(
                    state = contentState,
                    onTextLayout = { getLayout -> textLayoutResult = getLayout() },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What's on your mind?") },
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3),
                    enabled = !isPosting
                )

                if (showTagPopup && filteredTags.isNotEmpty()) {
                    val popupOffset = remember(textLayoutResult, contentState.selection, density) {
                        val layout = textLayoutResult
                        if (layout != null) {
                            val cursorRect = layout.getCursorRect(
                                contentState.selection.start.coerceIn(0, contentState.text.length)
                            )
                            // Approximate padding of OutlinedTextField
                            val horizontalPadding = with(density) { 16.dp.roundToPx() }
                            val verticalPadding = with(density) { 16.dp.roundToPx() }

                            IntOffset(
                                x = cursorRect.left.toInt() + horizontalPadding,
                                y = cursorRect.bottom.toInt() + verticalPadding
                            )
                        } else {
                            IntOffset(0, 150)
                        }
                    }

                    Popup(
                        alignment = Alignment.TopStart, offset = popupOffset
                    ) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .heightIn(max = 200.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            tonalElevation = 8.dp,
                            shadowElevation = 4.dp
                        ) {
                            LazyColumn {
                                items(filteredTags) { tag ->
                                    Text(
                                        text = "#$tag",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                contentState.edit {
                                                    val replacement = "#$tag "
                                                    replace(
                                                        tagStartIndex,
                                                        contentState.selection.start,
                                                        replacement
                                                    )
                                                    val newCursor =
                                                        tagStartIndex + replacement.length
                                                    selection = TextRange(newCursor)
                                                }
                                                showTagPopup = false
                                            }
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.width(160.dp)
                ) {
                    OutlinedTextField(
                        value = visibility,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Visibility") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.menuAnchor(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = expanded, onDismissRequest = { expanded = false }) {
                        visibilityOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                visibility = option
                                expanded = false
                            })
                        }
                    }
                }

                Button(
                    onClick = {
                        onPublish(contentState.text.toString(), visibility)
                        contentState.clearText()
                    }, enabled = contentState.text.isNotBlank() && !isPosting
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publish")
                    }
                }
            }
        }
    }
}

@Composable
fun MemoItem(memo: Memo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = memo.content, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = memo.displayTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = memo.visibility,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}

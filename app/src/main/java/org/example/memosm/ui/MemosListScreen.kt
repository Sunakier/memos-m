package org.example.memosm.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import org.example.memosm.model.Memo
import org.example.memosm.viewmodel.MemosViewModel

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
                            val cursorIndex = contentState.selection.start
                            // Ensure the index is within the bounds of the text that produced this layout
                            val safeIndex = cursorIndex.coerceIn(0, layout.layoutInput.text.length)
                            val cursorRect = layout.getCursorRect(safeIndex)
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

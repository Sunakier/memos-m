package org.example.memosm.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.example.memosm.model.Attachment
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

    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
    ) {
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
                        .fillMaxSize()
                        .statusBarsPadding(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CreateMemoCard(
                                onPublish = { content, visibility, attachments ->
                                    viewModel.createMemo(content, visibility, attachments)
                                },
                                onUploadFile = { uri, context ->
                                    viewModel.uploadAttachment(uri, context)
                                },
                                isPosting = uiState.isPosting,
                                availableTags = uiState.userStats?.tagCount?.keys ?: emptySet(),
                                token = uiState.token,
                                modifier = Modifier.widthIn(max = 800.dp)
                            )
                        }
                    }

                    items(uiState.memos) { memo ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MemoItem(
                                memo = memo,
                                token = uiState.token,
                                modifier = Modifier.widthIn(max = 800.dp)
                            )
                        }
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
    onPublish: (String, String, List<Attachment>) -> Unit,
    onUploadFile: suspend (Uri, android.content.Context) -> Attachment?,
    isPosting: Boolean,
    availableTags: Set<String>,
    token: String,
    modifier: Modifier = Modifier
) {
    val contentState = rememberTextFieldState("")
    var visibility by remember { mutableStateOf("PRIVATE") }
    var expanded by remember { mutableStateOf(false) }
    
    // We store Uri for local display, and Attachment for publishing
    var draftAttachments by remember { mutableStateOf(emptyList<Pair<Uri, Attachment?>>()) }
    var isUploadingCount by remember { mutableIntStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                draftAttachments = draftAttachments + (uri to null)
                isUploadingCount++
                scope.launch {
                    val attachment = onUploadFile(uri, context)
                    if (attachment != null) {
                        draftAttachments = draftAttachments.map { 
                            if (it.first == uri) uri to attachment else it 
                        }
                    } else {
                        // Optional: Handle error, remove from list or show error state
                        draftAttachments = draftAttachments.filter { it.first != uri }
                    }
                    isUploadingCount--
                }
            }
        }
    }

    // Tag autocomplete logic
    var showTagPopup by remember { mutableStateOf(false) }
    var tagFilter by remember { mutableStateOf("") }
    var tagStartIndex by remember { mutableIntStateOf(-1) }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    LaunchedEffect(contentState.text, contentState.selection) {
        val text = contentState.text.toString()
        val selection = contentState.selection
        val cursorIndex = selection.start
        if (cursorIndex > 0 && selection.collapsed) {
            val textBeforeCursor = text.take(cursorIndex)
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
        if (tagFilter.isEmpty()) availableTags.toList()
        else availableTags.filter { it.contains(tagFilter, ignoreCase = true) }
    }

    Card(modifier = modifier.fillMaxWidth()) {
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
                            val safeIndex = cursorIndex.coerceIn(0, layout.layoutInput.text.length)
                            val cursorRect = layout.getCursorRect(safeIndex)
                            val horizontalPadding = with(density) { 16.dp.roundToPx() }
                            val verticalPadding = with(density) { 16.dp.roundToPx() }
                            IntOffset(
                                x = cursorRect.left.toInt() + horizontalPadding,
                                y = cursorRect.bottom.toInt() + verticalPadding
                            )
                        } else IntOffset(0, 150)
                    }

                    Popup(alignment = Alignment.TopStart, offset = popupOffset) {
                        Surface(
                            modifier = Modifier
                                .widthIn(max = 200.dp)
                                .heightIn(max = 200.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
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
                                                    replace(tagStartIndex, contentState.selection.start, replacement)
                                                    selection = TextRange(tagStartIndex + replacement.length)
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

            if (draftAttachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(draftAttachments) { (uri, attachment) ->
                        val isImage = remember(uri) {
                            context.contentResolver.getType(uri)?.startsWith("image/") == true
                        }
                        
                        Box(modifier = Modifier.size(80.dp)) {
                            if (isImage) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uri) // Use local URI for immediate display
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.InsertDriveFile, contentDescription = null)
                                }
                            }
                            
                            if (attachment == null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                }
                            }

                            IconButton(
                                onClick = { draftAttachments = draftAttachments.filter { it.first != uri } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { pickerLauncher.launch("*/*") },
                        enabled = !isPosting
                    ) {
                        Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach File")
                    }
                    IconButton(
                        onClick = { pickerLauncher.launch("image/*") },
                        enabled = !isPosting
                    ) {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "Add Image")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { expanded = true }, enabled = !isPosting) {
                            Text(visibility)
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            listOf("PRIVATE", "PROTECTED", "PUBLIC").forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        visibility = option
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val finalAttachments = draftAttachments.mapNotNull { it.second }
                            onPublish(contentState.text.toString(), visibility, finalAttachments)
                            contentState.edit { replace(0, length, "") }
                            draftAttachments = emptyList()
                        },
                        enabled = (contentState.text.isNotBlank() || draftAttachments.isNotEmpty()) && !isPosting && isUploadingCount == 0
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Publish"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publish")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoItem(memo: Memo, token: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = memo.content, style = MaterialTheme.typography.bodyLarge)
            
            val attachments = remember(memo.attachments) {
                memo.attachments ?: emptyList()
            }
            
            if (attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(attachments) { attachment ->
                        val isImage = remember(attachment.displayType) {
                            attachment.displayType.startsWith("image/", ignoreCase = true) || 
                            attachment.displayType.contains("image", ignoreCase = true)
                        }
                        
                        if (isImage) {
                            val context = LocalContext.current
                            val imageRequest = remember(attachment.externalLink, token) {
                                ImageRequest.Builder(context)
                                    .data(attachment.externalLink)
                                    .addHeader("Authorization", "Bearer $token")
                                    .crossfade(true)
                                    .build()
                            }
                            
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = attachment.filename,
                                modifier = Modifier
                                    .size(width = 240.dp, height = 160.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Card(
                                modifier = Modifier
                                    .size(width = 200.dp, height = 100.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = attachment.filename,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = attachment.displayType,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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

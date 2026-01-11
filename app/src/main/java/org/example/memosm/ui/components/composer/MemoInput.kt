package org.example.memosm.ui.components.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.delay
import org.example.memosm.R
import kotlin.math.roundToInt

@Composable
fun MemoInput(
    modifier: Modifier = Modifier,
    contentState: TextFieldState,
    placeholder: String = stringResource(R.string.memo_composer_placeholder),
    availableTags: Set<String>,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    minHeightInLines: Int = 3,
    maxHeightInLines: Int = Int.MAX_VALUE
) {
    val focusRequester = remember { FocusRequester() }

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

    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(100)
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            state = contentState,
            onTextLayout = { getLayout -> textLayoutResult = getLayout() },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text(placeholder) },
            lineLimits = TextFieldLineLimits.MultiLine(
                minHeightInLines = minHeightInLines, maxHeightInLines = maxHeightInLines
            ),
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
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
                        x = cursorRect.left.roundToInt() + horizontalPadding,
                        y = cursorRect.bottom.roundToInt() + verticalPadding
                    )
                } else IntOffset(0, 150)
            }

            Popup(alignment = Alignment.TopStart, offset = popupOffset) {
                Surface(
                    modifier = Modifier
                        .widthIn(min = 100.dp, max = 200.dp)
                        .heightIn(max = 200.dp),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(filteredTags) { tag ->
                            DropdownMenuItem(text = { Text(text = "#$tag") }, onClick = {
                                contentState.edit {
                                    val replacement = "#$tag "
                                    replace(
                                        tagStartIndex, contentState.selection.start, replacement
                                    )
                                    selection = TextRange(tagStartIndex + replacement.length)
                                }
                                showTagPopup = false
                            })
                        }
                    }
                }
            }
        }
    }
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

package org.example.memosm.ui.component.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
    contentState: androidx.compose.ui.text.input.TextFieldValue,
    onContentChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
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
        val text = contentState.text
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
        val interactionSource = remember { MutableInteractionSource() }
        val colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )

        BasicTextField(
            value = contentState,
            onValueChange = { newValue ->
                val processedValue = processMarkdownInput(contentState, newValue)
                onContentChange(processedValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            minLines = minHeightInLines,
            maxLines = maxHeightInLines,
            enabled = enabled,
            onTextLayout = { result -> textLayoutResult = result },
            visualTransformation = rememberMarkdownVisualTransformation(),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = contentState.text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = false,
                    visualTransformation = rememberMarkdownVisualTransformation(),
                    interactionSource = interactionSource,
                    placeholder = { Text(placeholder) },
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    colors = colors,
                    label = null
                )
            })

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
                                val replacement = "#$tag "
                                val text = contentState.text
                                val newText = text.replaceRange(
                                    tagStartIndex, contentState.selection.start, replacement
                                )
                                val newSelection = TextRange(tagStartIndex + replacement.length)
                                onContentChange(
                                    contentState.copy(
                                        text = newText, selection = newSelection
                                    )
                                )
                                showTagPopup = false
                            })
                        }
                    }
                }
            }
        }
    }
}

// Helper for markdown list auto-completion
private fun processMarkdownInput(
    oldValue: androidx.compose.ui.text.input.TextFieldValue,
    newValue: androidx.compose.ui.text.input.TextFieldValue
): androidx.compose.ui.text.input.TextFieldValue {
    if (newValue.text.length > oldValue.text.length && newValue.selection.start == oldValue.selection.start + 1) {
        val insertedChar = newValue.text[newValue.selection.start - 1]
        if (insertedChar == '\n') {
            val caretIndex = newValue.selection.start
            // Find start of the current line (the line just completed)
            // Caret is at the start of the NEW line.
            // So we look back from caretIndex - 2 (skip \n)

            // Example:
            // "Item 1\n|"
            // caretIndex is at end.

            val newlineIndex = caretIndex - 1
            var lineStartIndex = newlineIndex - 1
            while (lineStartIndex >= 0 && newValue.text[lineStartIndex] != '\n') {
                lineStartIndex--
            }
            lineStartIndex++

            if (lineStartIndex < newlineIndex) {
                val previousLine = newValue.text.substring(lineStartIndex, newlineIndex)
                val prefix = getMarkdownPrefix(previousLine)
                if (prefix != null) {
                    val newText =
                        newValue.text.substring(0, caretIndex) + prefix + newValue.text.substring(
                            caretIndex
                        )
                    val newSelection = TextRange(caretIndex + prefix.length)
                    return newValue.copy(text = newText, selection = newSelection)
                }
            }
        }
    }
    return newValue
}

private fun getMarkdownPrefix(line: String): String? {
    // 1. Task List: "- [ ] " or "- [x] "
    val taskRegex = Regex("^(\\s*[-*+]\\s+\\[)[ xX]?(\\]\\s+)")
    val taskMatch = taskRegex.find(line)
    if (taskMatch != null) {
        val (prefixStart, prefixEnd) = taskMatch.destructured
        return "$prefixStart $prefixEnd" // Always uncheck
    }

    // 2. Bullet List: "- " or "* " or "+ "
    val bulletRegex = Regex("^(\\s*[-*+]\\s+)")
    val bulletMatch = bulletRegex.find(line)
    if (bulletMatch != null) {
        return bulletMatch.groupValues[1]
    }

    // 3. Numbered List: "1. "
    val numberedRegex = Regex("^(\\s*)(\\d+)(\\.\\s+)")
    val numberedMatch = numberedRegex.find(line)
    if (numberedMatch != null) {
        val (indent, numberStr, suffix) = numberedMatch.destructured
        try {
            val number = numberStr.toInt()
            return "$indent${number + 1}$suffix"
        } catch (e: NumberFormatException) {
            return numberedMatch.groupValues[0]
        }
    }

    // 4. Blockquote: "> "
    val quoteRegex = Regex("^(\\s*>\\s+)")
    val quoteMatch = quoteRegex.find(line)
    if (quoteMatch != null) {
        return quoteMatch.groupValues[1]
    }

    // 5. Brackets/Parens
    val bracketRegex = Regex("^(\\s*\\[\\]\\s+)")
    if (bracketRegex.containsMatchIn(line)) {
        return bracketRegex.find(line)!!.groupValues[1]
    }

    val parenRegex = Regex("^(\\s*\\(\\)\\s+)")
    if (parenRegex.containsMatchIn(line)) {
        return parenRegex.find(line)!!.groupValues[1]
    }

    return null
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import org.example.memosm.R
import kotlin.math.roundToInt

@Composable
fun rememberMarkdownLanguageHandler(): MarkdownLanguageHandler {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    return remember(colorScheme, typography) {
        MarkdownLanguageHandler(colorScheme, typography)
    }
}

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

    val markdownHandler = rememberMarkdownLanguageHandler()

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
                val processedValue = markdownHandler.processInput(contentState, newValue)
                onContentChange(processedValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            minLines = minHeightInLines,
            maxLines = maxHeightInLines,
            enabled = enabled,
            onTextLayout = { result -> textLayoutResult = result },
            visualTransformation = markdownHandler,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = contentState.text,
                    innerTextField = innerTextField,
                    enabled = enabled,
                    singleLine = false,
                    visualTransformation = markdownHandler,
                    interactionSource = interactionSource,
                    placeholder = { Text(placeholder) },
                    contentPadding = OutlinedTextFieldDefaults.contentPadding(),
                    colors = colors,
                    label = null
                )
            })

        if (showTagPopup && filteredTags.isNotEmpty()) {
            val imeBottom = WindowInsets.ime.getBottom(density)
            val cursorRect = remember(textLayoutResult, contentState.selection) {
                val layout = textLayoutResult ?: return@remember IntRect.Zero
                val cursorIndex = contentState.selection.start
                val safeIndex = cursorIndex.coerceIn(0, layout.layoutInput.text.length)
                val rect = layout.getCursorRect(safeIndex)
                IntRect(
                    left = rect.left.roundToInt(),
                    top = rect.top.roundToInt(),
                    right = rect.right.roundToInt(),
                    bottom = rect.bottom.roundToInt()
                )
            }

            val popupPositionProvider = remember(cursorRect, imeBottom, density) {
                CursorPopupPositionProvider(
                    cursorRect = cursorRect,
                    imeBottom = imeBottom,
                    density = density
                )
            }

            Popup(
                popupPositionProvider = popupPositionProvider
            ) {
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


fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}

private class CursorPopupPositionProvider(
    private val cursorRect: IntRect,
    private val imeBottom: Int,
    private val density: androidx.compose.ui.unit.Density
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val horizontalPadding = with(density) { 16.dp.roundToPx() }
        val paddingBelow = with(density) { 8.dp.roundToPx() }
        val paddingAbove = with(density) { (-4).dp.roundToPx() }

        val targetX = anchorBounds.left + cursorRect.left + horizontalPadding
        val targetYBelow = anchorBounds.top + cursorRect.bottom + paddingBelow
        val targetYAbove = anchorBounds.top + cursorRect.top - popupContentSize.height - paddingAbove

        val effectiveWindowBottom = windowSize.height - imeBottom

        val isSpaceBelow = (targetYBelow + popupContentSize.height) <= effectiveWindowBottom

        return if (isSpaceBelow) {
            IntOffset(targetX, targetYBelow)
        } else {
            // Priority is to show above if blocked below
            IntOffset(targetX, targetYAbove)
        }
    }
}

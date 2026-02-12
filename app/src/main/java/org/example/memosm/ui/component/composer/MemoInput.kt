package org.example.memosm.ui.component.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
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
//    maxHeightInLines: Int = Int.MAX_VALUE
) {
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

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

    Column(modifier = modifier) {
        val interactionSource = remember { MutableInteractionSource() }
        val paddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        val textStyle = LocalTextStyle.current

        var boxPositionInWindow by remember { mutableStateOf(IntOffset.Zero) }
        var boxHeightPx by remember { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coordinates ->
                    val pos = coordinates.positionInWindow()
                    boxPositionInWindow = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                    boxHeightPx = coordinates.size.height
                }
        ) {
            if (contentState.text.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }

            BasicTextField(
                value = contentState,
                onValueChange = { newValue ->
                    val processedValue = markdownHandler.processInput(contentState, newValue)
                    onContentChange(processedValue)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .focusRequester(focusRequester),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                enabled = enabled,
                onTextLayout = { result -> textLayoutResult = result },
                visualTransformation = markdownHandler,
                interactionSource = interactionSource,
            )

            // Auto-scroll to keep cursor visible
            LaunchedEffect(contentState.selection, contentState.text) {
//                if (maxHeightInLines == Int.MAX_VALUE) return@LaunchedEffect
                val layout = textLayoutResult ?: return@LaunchedEffect
                val cursorIndex =
                    contentState.selection.start.coerceIn(0, layout.layoutInput.text.length)
                val cursorRect = layout.getCursorRect(cursorIndex)
                val cursorBottom = cursorRect.bottom.roundToInt()
                val cursorTop = cursorRect.top.roundToInt()
                val viewportTop = scrollState.value
                val viewportBottom = viewportTop + scrollState.viewportSize

                if (cursorBottom > viewportBottom) {
                    scrollState.animateScrollTo(cursorBottom - scrollState.viewportSize)
                } else if (cursorTop < viewportTop) {
                    scrollState.animateScrollTo(cursorTop)
                }
            }

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

                val effectiveScrollTop = scrollState.value

                // Compute available space below and above the cursor in window coords
                val cursorBottomInWindow = boxPositionInWindow.y + cursorRect.bottom - effectiveScrollTop
                val cursorTopInWindow = boxPositionInWindow.y + cursorRect.top - effectiveScrollTop
                val effectiveWindowBottom = with(density) {
                    // Use a reasonable estimate; actual window size comes from calculatePosition
                    // but we need an approximation here for height constraint
                    (boxPositionInWindow.y + boxHeightPx + with(density) { 12.dp.roundToPx() })
                }
                val spaceBelow = max(0, effectiveWindowBottom - cursorBottomInWindow - with(density) { 12.dp.roundToPx() })
                val spaceAbove = max(0, cursorTopInWindow - with(density) { 4.dp.roundToPx() })
                val popupMaxHeightPx = max(spaceBelow, spaceAbove)
                val popupMaxHeightDp = with(density) { popupMaxHeightPx.toDp() }
                val constrainedMaxHeight = min(popupMaxHeightDp.value, 200f).dp

                val popupPositionProvider =
                    remember(cursorRect, imeBottom, density, effectiveScrollTop) {
                        CursorPopupPositionProvider(
                            cursorRect = cursorRect,
                            imeBottom = imeBottom,
                            density = density,
                            scrollTop = effectiveScrollTop
                        )
                    }

                Popup(
                    popupPositionProvider = popupPositionProvider
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(min = 100.dp, max = 200.dp)
                            .heightIn(max = constrainedMaxHeight),
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
    private val density: androidx.compose.ui.unit.Density,
    private val scrollTop: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val horizontalPadding = with(density) { 4.dp.roundToPx() }
        // Account for custom inner padding (vertical = 12dp)
        val paddingBelow = with(density) { 12.dp.roundToPx() }
        val paddingAbove = with(density) { 4.dp.roundToPx() }

        val targetX = anchorBounds.left + cursorRect.left + horizontalPadding
        val targetYBelow = anchorBounds.top + cursorRect.bottom - scrollTop + paddingBelow
        val targetYAbove =
            anchorBounds.top + cursorRect.top - scrollTop - popupContentSize.height + paddingAbove

        val effectiveWindowBottom = windowSize.height - imeBottom

        val isSpaceBelow = (targetYBelow + popupContentSize.height) <= effectiveWindowBottom
        val isSpaceAbove = targetYAbove >= 0
        val y = when {
            isSpaceBelow -> targetYBelow
            isSpaceAbove -> targetYAbove
            else -> {
                // Neither side fits fully. Pick the side with more room.
                val cursorBottomInWindow = anchorBounds.top + cursorRect.bottom - scrollTop
                val cursorTopInWindow = anchorBounds.top + cursorRect.top - scrollTop
                val spaceBelow = effectiveWindowBottom - cursorBottomInWindow
                val spaceAbove = cursorTopInWindow
                if (spaceBelow >= spaceAbove) {
                    // More space below cursor: place below
                    targetYBelow
                } else {
                    // More space above cursor: place above, clamp to screen top
                    targetYAbove.coerceAtLeast(0)
                }
            }
        }

        return IntOffset(targetX, y)
    }
}

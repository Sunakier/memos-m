package org.example.memosm.ui.component.composer

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import org.example.memosm.R
import kotlin.math.max
import kotlin.math.min
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

    // Suggestion Logic
    var currentSuggestionResult by remember { mutableStateOf<SuggestionResult?>(null) }
    var showSuggestionPopup by remember { mutableStateOf(false) }
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    val markdownHandler = rememberMarkdownLanguageHandler()

    // Monitor text/selection changes to trigger suggestions
    LaunchedEffect(contentState.text, contentState.selection) {
        val result = SuggestionProvider.getSuggestions(
            contentState.text,
            contentState.selection,
            availableTags
        )
        currentSuggestionResult = result
        if (result != null && result.type.isAutoShown) {
            showSuggestionPopup = true
        } else if (result == null) {
            showSuggestionPopup = false
        }
        // For non-auto types (Markdown, Code), showSuggestionPopup remains false (showing icon)
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

            // Suggestion UI (Popup OR Hint Icon)
            currentSuggestionResult?.let { result ->
                if (result.suggestions.isNotEmpty()) {
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
                    val cursorBottomInWindow =
                        boxPositionInWindow.y + cursorRect.bottom - effectiveScrollTop
                    val cursorTopInWindow = boxPositionInWindow.y + cursorRect.top - effectiveScrollTop

                    val configuration = LocalConfiguration.current
                    val screenHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() }
                    val effectiveWindowBottom = screenHeight - imeBottom

                    val spaceBelow = max(
                        0,
                        effectiveWindowBottom - cursorBottomInWindow - with(density) { 12.dp.roundToPx() })
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
                    
                    // Determine if we are in "List" mode (Expanded) or "Icon" mode (Collapsed)
                    // Auto-shown types (Hashtag) start expanded.
                    // Others start collapsed (Icon) and expand on click.
                    val isExpanded = result.type.isAutoShown || showSuggestionPopup

                    Popup(
                        popupPositionProvider = popupPositionProvider,
                        onDismissRequest = {
                            showSuggestionPopup = false
                            if (result.type.isAutoShown) currentSuggestionResult = null
                        }
                    ) {
                         // Use AnimatedContent to transition between Icon and List
                         // We need a wrapper to provide scope for animation if needed, but AnimatedContent provides its own scope.
                         
                         androidx.compose.animation.AnimatedContent(
                             targetState = isExpanded,
                             transitionSpec = {
                                 if (targetState && !initialState) {
                                     // Icon -> List (Expand/Morph)
                                     (fadeIn() + androidx.compose.animation.expandIn(expandFrom = androidx.compose.ui.Alignment.Center))
                                         .togetherWith(fadeOut() + androidx.compose.animation.shrinkOut(shrinkTowards = androidx.compose.ui.Alignment.Center))
                                 } else if (!targetState && initialState) {
                                     // List -> Icon (Collapse) - unlikely to happen as dismiss clears, but symmetric
                                     (fadeIn() + androidx.compose.animation.scaleIn())
                                         .togetherWith(fadeOut() + androidx.compose.animation.scaleOut())
                                 } else {
                                     // Initial state or same state
                                      androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                                 }
                             },
                             label = "SuggestionAnimation"
                         ) { expanded ->
                             if (expanded) {
                                 // LIST CONTENT
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
                                         items(result.suggestions) { item ->
                                             val displayText =
                                                 if (result.type == SuggestionType.HASHTAG) "#$item" else item
                                             DropdownMenuItem(
                                                 text = { Text(text = displayText) },
                                                 onClick = {
                                                     val replacement =
                                                         if (result.type == SuggestionType.HASHTAG) "#$item " else item
                                                     val text = contentState.text

                                                     val replaceStart = result.startIndex
                                                     val replaceEnd = contentState.selection.start

                                                     val newText = text.replaceRange(
                                                         replaceStart, replaceEnd, replacement
                                                     )
                                                     val newSelection =
                                                         TextRange(replaceStart + replacement.length)
                                                     onContentChange(
                                                         contentState.copy(
                                                             text = newText, selection = newSelection
                                                         )
                                                     )
                                                     currentSuggestionResult = null
                                                     showSuggestionPopup = false
                                                 }
                                             )
                                         }
                                     }
                                 }
                             } else {
                                 // ICON CONTENT
                                 // Only animate scaleIn for the icon initially if desired, but AnimatedContent handles state change.
                                 // User asked for "ZoomIn" for circle hint.
                                 // Since we are inside AnimatedContent, when this becomes visible (false state), it runs its enter transition.
                                 // But we want a specific initial entry animation for the icon itself when it first appears.
                                 // AnimatedContent handles transitions *between* states. 
                                 // If the popup just appeared in "false" state, AnimatedContent emits a generic enter.
                                 // Let's rely on a separate AnimatedVisibility for the Icon *inside* this branch to ensure it zooms in on creation?
                                 // Actually, standard AnimatedVisibility inside here might be safer for initial appearance.
                                 
                                 // Wait, if we are here, isExpanded is false. 
                                 // If the popup just appeared, we want the icon to Zoom In.
                                 
                                 Surface(
                                     shape = androidx.compose.foundation.shape.CircleShape,
                                     tonalElevation = 6.dp,
                                     shadowElevation = 6.dp,
                                     color = MaterialTheme.colorScheme.surfaceContainer,
                                     modifier = Modifier
                                         .padding(4.dp)
                                         .clickable { showSuggestionPopup = true }
                                         .animateEnterExit(
                                             enter = scaleIn(initialScale = 0.0f) + fadeIn(),
                                             exit = scaleOut() + fadeOut()
                                         )
                                 ) {
                                     Box(modifier = Modifier.padding(8.dp)) {
                                         Icon(
                                             imageVector = Icons.Outlined.Add,
                                             contentDescription = "Show suggestions",
                                             modifier = Modifier.size(20.dp),
                                             tint = MaterialTheme.colorScheme.primary
                                         )
                                     }
                                 }
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

        val effectiveWindowBottom = windowSize.height

        val isSpaceBelow = (targetYBelow + popupContentSize.height) <= effectiveWindowBottom
        val isSpaceAbove = targetYAbove >= 0

        Log.d("PopupDebug", "=== calculatePosition ===")
        Log.d("PopupDebug", "anchorBounds=$anchorBounds")
        Log.d("PopupDebug", "windowSize=$windowSize")
        Log.d("PopupDebug", "cursorRect=$cursorRect, scrollTop=$scrollTop")
        Log.d("PopupDebug", "popupContentSize=$popupContentSize")
        Log.d("PopupDebug", "imeBottom=$imeBottom, effectiveWindowBottom=$effectiveWindowBottom")
        Log.d("PopupDebug", "targetYBelow=$targetYBelow, targetYAbove=$targetYAbove")
        Log.d(
            "PopupDebug",
            "isSpaceBelow=$isSpaceBelow (${targetYBelow + popupContentSize.height} <= $effectiveWindowBottom)"
        )
        Log.d("PopupDebug", "isSpaceAbove=$isSpaceAbove (targetYAbove=$targetYAbove >= 0)")

        val y = when {
            isSpaceBelow -> targetYBelow
            isSpaceAbove -> targetYAbove
            else -> {
                // Neither side fits fully. Pick the side with more room.
                val cursorBottomInWindow = anchorBounds.top + cursorRect.bottom - scrollTop
                val cursorTopInWindow = anchorBounds.top + cursorRect.top - scrollTop
                val spaceBelow = effectiveWindowBottom - cursorBottomInWindow
                val spaceAbove = cursorTopInWindow
                Log.d("PopupDebug", "else branch: spaceBelow=$spaceBelow, spaceAbove=$spaceAbove")
                if (spaceBelow >= spaceAbove) {
                    targetYBelow
                } else {
                    targetYAbove.coerceAtLeast(0)
                }
            }
        }

        Log.d(
            "PopupDebug",
            "FINAL y=$y (chose=${if (isSpaceBelow) "below" else if (isSpaceAbove) "above" else "else"})"
        )

        return IntOffset(targetX, y)
    }
}

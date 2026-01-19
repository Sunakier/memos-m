package org.example.memosm.ui.component.item.markdown

import ClickableCheckbox
import CustomMarkdownBlockQuote
import CustomMarkdownTable
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.rememberSquigglyUnderlineAnimator
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownExtendedSpans


@Composable
fun MemoMarkdown(
    modifier: Modifier = Modifier,
    content: String,
    markdownState: MarkdownState,
    token: String,
    hostUrl: String,
    onContentUpdate: ((String) -> Unit)? = null,
    selectable: Boolean = false,
) {
    Log.d(
        "MemosDebug",
        "MemoMarkdown: content length=${content.length}, hasImage=${content.contains("![")}"
    )
    val markdownContent: @Composable () -> Unit = {
        Markdown(
            markdownState = markdownState,
            imageTransformer = Coil3ImageTransformerImpl,
            animations = markdownAnimations(
                animateTextSize = {
                    this
                    /** No animation */
                }),
            annotator = markdownAnnotator(
                config = markdownAnnotatorConfig(eolAsNewLine = true)
            ),
            extendedSpans = markdownExtendedSpans {
                val animator = rememberSquigglyUnderlineAnimator()
                remember {
                    ExtendedSpans(
                        RoundedCornerSpanPainter(),
//                    SquigglyUnderlineSpanPainter(animator = animator)
                    )
                }
            },
            components = markdownComponents(
                checkbox = { model ->
                    ClickableCheckbox(
                        model = model, content = content, onToggle = onContentUpdate
                    )
                },
                blockQuote = { model ->
                    CustomMarkdownBlockQuote(
                        content = model.content, node = model.node, style = model.typography.quote
                    )
                },
                table = { model ->
                    CustomMarkdownTable(
                        content = model.content, node = model.node
                    )
                },
                horizontalRule = {
                    MarkdownDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                },
                codeBlock = highlightedCodeBlock,
                codeFence = highlightedCodeFence,

                ),
            modifier = modifier
        )
    }

    if (selectable) {
        SelectionContainer {
            markdownContent()
        }
    } else {
        markdownContent()
    }

}
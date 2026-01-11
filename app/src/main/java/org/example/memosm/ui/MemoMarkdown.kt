package org.example.memosm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun MemoMarkdown(
    content: String,
    markdownState: MarkdownState,
    modifier: Modifier = Modifier,
    onContentUpdate: ((String) -> Unit)? = null,
) {
    Markdown(
        markdownState = markdownState,
        imageTransformer = Coil3ImageTransformerImpl,
        annotator = markdownAnnotator(
            config = markdownAnnotatorConfig(eolAsNewLine = true)
        ),
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
                    content = model.content,
                    node = model.node
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClickableCheckbox(
    model: MarkdownComponentModel, content: String, onToggle: ((String) -> Unit)?
) {
    val nodeText = model.node.getTextInNode(content)
    val isChecked = nodeText.contains("[x]") || nodeText.contains("[X]")

    val isClickable = onToggle != null

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(checked = isChecked, onCheckedChange = if (isClickable) { _ ->
            // Find the checkbox pattern in the content and toggle it
            val startOffset = model.node.startOffset
            val endOffset = model.node.endOffset

            // Get the text of this specific checkbox node
            val checkboxText = content.substring(startOffset, endOffset)

            // Toggle the checkbox state
            val newCheckboxText = if (isChecked) {
                checkboxText.replace("[x]", "[ ]", ignoreCase = true)
            } else {
                checkboxText.replace("[ ]", "[x]")
            }

            // Create the new content with the toggled checkbox
            val newContent =
                content.take(startOffset) + newCheckboxText + content.substring(endOffset)
            onToggle?.invoke(newContent)
        } else null, modifier = Modifier
            .padding(end = 4.dp)
            .size(20.dp), enabled = isClickable)
    }
}

@Composable
fun CustomMarkdownBlockQuote(
    content: String, node: ASTNode, style: TextStyle
) {
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {

        // Vertical bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            val components = LocalMarkdownComponents.current

            node.children.forEach { child ->

                when (child.type) {

                    // ✅ Recurse for nested blockquotes
                    MarkdownElementTypes.BLOCK_QUOTE -> {
                        CustomMarkdownBlockQuote(
                            content = content, node = child, style = style
                        )
                    }

                    // Normal markdown content
                    else -> {
                        MarkdownElement(
                            node = child,
                            components = components,
                            content = content,
                            includeSpacer = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomMarkdownTable(
    content: String,
    node: ASTNode
) {
    val tableMaxWidth = LocalMarkdownDimens.current.tableMaxWidth
    val tableCellWidth = LocalMarkdownDimens.current.tableCellWidth
    val tableCornerSize = LocalMarkdownDimens.current.tableCornerSize
    val tableCellPadding = LocalMarkdownDimens.current.tableCellPadding

    val header = remember(node) { node.findChildOfType(GFMElementTypes.HEADER) }
    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }

    val columnsCount = remember(header) {
        header?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
    }

    if (columnsCount == 0) return

    val tableWidth = tableCellWidth * columnsCount
    val backgroundCodeColor = LocalMarkdownColors.current.tableBackground
    val markdownComponents = LocalMarkdownComponents.current

    BoxWithConstraints(
        modifier = Modifier
            .background(backgroundCodeColor, RoundedCornerShape(tableCornerSize))
            .widthIn(max = tableMaxWidth)
    ) {
        val scrollable = maxWidth <= tableWidth
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            modifier = (if (scrollable) {
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .requiredWidth(tableWidth)
            } else {
                Modifier.fillMaxWidth()
            }).heightIn(max = 2000.dp),
            userScrollEnabled = false
        ) {
            // Header
            header?.let { h ->
                val cells = h.children.filter { it.type == GFMTokenTypes.CELL }
                items(cells) { cell ->
                    Box(modifier = Modifier.padding(tableCellPadding)) {
                        MarkdownElement(
                            node = cell,
                            components = markdownComponents,
                            content = content,
                            includeSpacer = false
                        )
                    }
                }
            }

            // Divider line across all columns
            item(span = { GridItemSpan(columnsCount) }) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
            }

            // Rows
            rows.forEach { row ->
                val cells = row.children.filter { it.type == GFMTokenTypes.CELL }
                items(cells) { cell ->
                    Box(modifier = Modifier.padding(tableCellPadding)) {
                        MarkdownElement(
                            node = cell,
                            components = markdownComponents,
                            content = content,
                            includeSpacer = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalMarkdownColors.current.dividerColor,
    thickness: Dp = LocalMarkdownDimens.current.dividerThickness,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

@Composable
fun VerticalMarkdownDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalMarkdownColors.current.dividerColor,
    thickness: Dp = LocalMarkdownDimens.current.dividerThickness,
) {
    VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

package org.example.memosm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClickableCheckbox(
    model: MarkdownComponentModel, content: String, onToggle: ((String) -> Unit)?
) {
    val nodeText = model.node.getTextInNode(content)
    val isChecked = nodeText.contains("[x]") || nodeText.contains("[X]")

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = if (onToggle != null) { _ ->
                val startOffset = model.node.startOffset
                val endOffset = model.node.endOffset
                val checkboxText = content.substring(startOffset, endOffset)
                val newCheckboxText = if (isChecked) {
                    checkboxText.replace("[x]", "[ ]", ignoreCase = true)
                } else {
                    checkboxText.replace("[ ]", "[x]")
                }
                val newContent = content.take(startOffset) + newCheckboxText + content.substring(endOffset)
                onToggle(newContent)
            } else null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            enabled = onToggle != null
        )
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
    val header = remember(node) { node.findChildOfType(GFMElementTypes.HEADER) }
    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }

    val headerCells = remember(header) {
        header?.children?.filter { it.type == GFMTokenTypes.CELL } ?: emptyList()
    }

    val rowCells = remember(rows) {
        rows.map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    }

    val columnCount = headerCells.size
    if (columnCount == 0) return

    val markdownComponents = LocalMarkdownComponents.current
    val tableCornerSize = LocalMarkdownDimens.current.tableCornerSize
    val tableCellPadding = LocalMarkdownDimens.current.tableCellPadding
    val backgroundColor = LocalMarkdownColors.current.tableBackground
    
    // 1. Significantly higher contrast header background
    val headerBackground = MaterialTheme.colorScheme.primaryContainer

    val horizontalScrollState = rememberScrollState()
    val columnWidths = remember(node) { mutableStateMapOf<Int, Int>() }

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(tableCornerSize))
            .background(backgroundColor)
            .horizontalScroll(horizontalScrollState)
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth() // 2. Cover entire row width to avoid gaps on the right
                    .height(IntrinsicSize.Min)
                    .background(headerBackground) // 3. Single background for the entire row
            ) {
                (0 until columnCount).forEach { columnIndex ->
                    val cellNode = headerCells.getOrNull(columnIndex)

                    TableCell(
                        columnIndex = columnIndex,
                        columnWidths = columnWidths
                    ) { widthModifier ->
                        Box(
                            modifier = widthModifier
                                .padding(tableCellPadding)
                                .fillMaxHeight()
                        ) {
                            if (cellNode != null) {
                                MarkdownElement(
                                    node = cellNode,
                                    components = markdownComponents,
                                    content = content,
                                    includeSpacer = false
                                )
                            }
                        }
                    }
                }
            }
            // ---------- BODY ----------
            rowCells.forEach { row ->
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    (0 until columnCount).forEach { columnIndex ->
                        val cellNode = row.getOrNull(columnIndex)

                        TableCell(
                            columnIndex = columnIndex,
                            columnWidths = columnWidths
                        ) { widthModifier ->
                            Box(
                                modifier = widthModifier
                                    .padding(tableCellPadding)
                                    .fillMaxHeight()
                            ) {
                                if (cellNode != null) {
                                    MarkdownElement(
                                        node = cellNode,
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
        }
    }
}

@Composable
private fun TableCell(
    columnIndex: Int,
    columnWidths: MutableMap<Int, Int>,
    content: @Composable (Modifier) -> Unit
) {
    val widthModifier = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        val existingWidth = columnWidths[columnIndex] ?: 0
        val maxWidth = maxOf(existingWidth, placeable.width)

        if (maxWidth > existingWidth) {
            columnWidths[columnIndex] = maxWidth
        }

        // Match row height: Use constraints.minHeight provided by Row(Modifier.height(IntrinsicSize.Min))
        val height = constraints.minHeight.coerceAtLeast(placeable.height)

        layout(width = maxWidth, height = height) {
            placeable.placeRelative(0, 0)
        }
    }

    content(widthModifier)
}

@Composable
fun MarkdownDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalMarkdownColors.current.dividerColor,
    thickness: Dp = LocalMarkdownDimens.current.dividerThickness,
) {
    HorizontalDivider(
        modifier = modifier, thickness = thickness, color = color
    )
}

@Composable
fun VerticalMarkdownDivider(
    modifier: Modifier = Modifier,
    color: Color = LocalMarkdownColors.current.dividerColor,
    thickness: Dp = LocalMarkdownDimens.current.dividerThickness,
) {
    VerticalDivider(
        modifier = modifier, thickness = thickness, color = color
    )
}

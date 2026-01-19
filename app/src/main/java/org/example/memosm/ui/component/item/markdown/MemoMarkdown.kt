package org.example.memosm.ui.component.item.markdown

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
//import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
//import com.mikepenz.markdown.compose.extendedspans.RoundedCornerSpanPainter
import com.mikepenz.markdown.compose.extendedspans.rememberSquigglyUnderlineAnimator
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.example.memosm.model.Attachment
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes



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
//            image = { model ->
//                Log.d("MemosDebug", "MemoMarkdown: image component triggered")
//                MarkdownAttachmentImage(
//                    content = model.content,
//                    node = model.node,
//                    token = token,
//                    hostUrl = hostUrl
//                )
//            },
                codeBlock = highlightedCodeBlock,
                codeFence = highlightedCodeFence,
//                paragraph = { model ->
//                    // First render any images collected before this paragraph
//                    RenderCollectedImages(
//                        token = token, hostUrl = hostUrl
//                    )
//
//                    // Then render the paragraph normally (this preserves inline markdown!)
//                    MarkdownParagraph(
//                        content = model.content,
//                        node = model.node,
//                        style = model.typography.paragraph
//                    )
//                }


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

@Composable
fun MarkdownAttachmentImage(content: String, node: ASTNode, token: String, hostUrl: String) {
    val link = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
        ?.getUnescapedTextInNode(content)

    Log.d("MemosDebug", "MarkdownAttachmentImage: link=$link")

    if (link == null) return

    // Maintain aspect ratio state, distinct from the default "16/9" if unknown
    // We start with a default but allow it to change.
    var aspectRatio by remember { mutableFloatStateOf(1.777f) }

    AttachmentCard(
        attachment = Attachment(
            externalLink = link, filename = link, type = "image", mimeType = "image/auto"
        ),
        token = token,
        hostUrl = hostUrl,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        showInfo = false,
        showActions = false,
        showSize = false,
        showFilename = false,
        compactMode = AttachmentCompactMode.Never,
        onRatioAvailable = {
            if (it > 0) {
                aspectRatio = it
            }
        })
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
                val newContent =
                    content.take(startOffset) + newCheckboxText + content.substring(endOffset)
                onToggle(newContent)
            } else null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            enabled = onToggle != null)
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
fun CustomMarkdownParagraph(
    content: String, node: ASTNode, components: MarkdownComponents, token: String, hostUrl: String
) {
    // Basic paragraph container, usually a wrapping logic or just a Column/FlowRow depending on implementation.
    // Since images might need to be full width, we probably use a Column or let them flow.
    // Standard markdown paragraphs are usually text flows.
    // However, for AttachmentCard we want it to be a block if it's a standalone image.
    // If it's mixed with text, it's tricky.
    // Let's assume for now we iterate and render.

    // Using FlowRow-like behavior or just standard traversal?
    // The library's default paragraph likely uses a Text composable with Spans.
    // But since we want to render Composables (AttachmentCard), we can't be inside a Text.
    // So we must break the paragraph into Composables.

    // A simple approach: Column of elements?
    // But text should flow.
    // "Text Image Text" -> "Text" (break) "Image" (break) "Text".
    // This breaks inline flow but allows AttachmentCard.

    Column(modifier = Modifier.fillMaxWidth()) {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownElementTypes.IMAGE -> {
                    Log.d("MemosDebug", "CustomMarkdownParagraph: Found IMAGE node")
                    MarkdownAttachmentImage(
                        content = content, node = child, token = token, hostUrl = hostUrl
                    )
                }

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

@Composable
fun CustomMarkdownTable(
    content: String, node: ASTNode
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
    val tableCellPadding = LocalMarkdownDimens.current.tableCellPadding
    val headerBackground = MaterialTheme.colorScheme.secondaryContainer
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) // Subtle lines
    // Define the spacing between inline elements within a cell
    val inlineSpacing = 4.dp

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(LocalMarkdownDimens.current.tableCornerSize))
            .background(LocalMarkdownColors.current.tableBackground)
            .border(1.dp, lineColor, RoundedCornerShape(8.dp)) // Outer border
            .horizontalScroll(rememberScrollState())
    ) {
        SubcomposeLayout { constraints ->
            val columnWidths = IntArray(columnCount)
            val allRows = listOf(headerCells) + rowCells

            // 1. MEASURE PASS
            allRows.forEach { row ->
                row.forEachIndexed { index, cellNode ->
                    val placeable = subcompose("measure_${row.hashCode()}_$index") {
                        // ADDED: spacedBy ensures that split nodes don't touch
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(inlineSpacing)) {
                            cellNode.children.forEach { child ->
                                MarkdownElement(child, markdownComponents, content, false)
                            }
                        }
                    }.first().measure(Constraints())

                    val totalPadding = tableCellPadding.roundToPx() * 2
                    columnWidths[index] = maxOf(columnWidths[index], placeable.width + totalPadding)
                }
            }

            val tableWidth = columnWidths.sum()

            // 2. COMPOSITION PASS
            val contentPlaceables = subcompose("content") {
                Column {
                    allRows.forEachIndexed { rowIndex, row ->
                        val isHeader = rowIndex == 0
                        Row(
                            modifier = Modifier
                                .width(with(LocalDensity.current) { tableWidth.toDp() })
                                .background(if (isHeader) headerBackground else Color.Transparent)
                                .height(IntrinsicSize.Min)
                        ) {
                            row.forEachIndexed { columnIndex, cellNode ->
                                Box(
                                    modifier = Modifier
                                        .width(with(LocalDensity.current) { columnWidths[columnIndex].toDp() })
                                        .padding(tableCellPadding)
                                        .fillMaxHeight()
                                ) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(inlineSpacing),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        cellNode.children.forEach { child ->
                                            MarkdownElement(
                                                child, markdownComponents, content, false
                                            )
                                        }
                                    }
                                }
                                // VERTICAL LINE: Add if it's not the last column
                                if (columnIndex < columnCount - 1) {
                                    VerticalDivider(
                                        modifier = Modifier.fillMaxHeight(),
                                        thickness = 1.dp,
                                        color = lineColor
                                    )
                                }
                            }
                        }
                        // 2. Force the HorizontalDivider to match the calculated table width
                        if (rowIndex < allRows.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.width(with(LocalDensity.current) { tableWidth.toDp() }),
                                thickness = 1.dp,
                                color = lineColor
                            )
                        }
                    }
                }
            }.map { it.measure(constraints) }

            layout(tableWidth, contentPlaceables.first().height) {
                contentPlaceables.forEach { it.placeRelative(0, 0) }
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
        modifier = modifier, thickness = thickness, color = color
    )
}

fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    findChildOfType(type)?.let { return it }
    for (child in children) {
        child.findChildOfTypeRecursive(type)?.let { return it }
    }
    return null
}

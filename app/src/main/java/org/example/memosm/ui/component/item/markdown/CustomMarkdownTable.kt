import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.MarkdownElement
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

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

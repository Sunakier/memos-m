package org.example.memosm.ui.component.item.markdown

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
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

@Composable
fun NativeMarkdownTable(
    content: String, node: ASTNode
) {
    val header = remember(node) { node.children.find { it.type == GFMElementTypes.HEADER } }
    val rows = remember(node) { node.children.filter { it.type == GFMElementTypes.ROW } }

    val headerCells = remember(header) {
        header?.children?.filter { it.type == GFMTokenTypes.CELL } ?: emptyList()
    }
    val rowCells = remember(rows) {
        rows.map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    }

    val columnCount = headerCells.size
    if (columnCount == 0) return

    val tableCellPadding = 8.dp
    val headerBackground = MaterialTheme.colorScheme.secondaryContainer
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) // Subtle lines
    val inlineSpacing = 4.dp
    val tableCornerSize = 8.dp

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(tableCornerSize))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, lineColor, RoundedCornerShape(tableCornerSize))
            .horizontalScroll(rememberScrollState())
    ) {
        SubcomposeLayout { constraints ->
            val columnWidths = IntArray(columnCount)
            val allRows = listOf(headerCells) + rowCells

            // 1. MEASURE PASS
            allRows.forEach { row ->
                row.forEachIndexed { index, cellNode ->
                    if (index < columnCount) {
                        val placeable = subcompose("measure_${row.hashCode()}_$index") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(inlineSpacing)) {
                                cellNode.children.forEach { child ->
                                    NativeMarkdownNodeRecursive(child)
                                }
                            }
                        }.first().measure(Constraints())

                        val totalPadding = tableCellPadding.roundToPx() * 2
                        columnWidths[index] = maxOf(columnWidths[index], placeable.width + totalPadding)
                    }
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
                                if (columnIndex < columnCount) {
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
                                                NativeMarkdownNodeRecursive(child)
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

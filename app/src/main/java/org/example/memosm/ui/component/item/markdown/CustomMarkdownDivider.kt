package org.example.memosm.ui.component.item.markdown

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens


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
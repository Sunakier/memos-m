package org.example.memosm.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small opt-in hooks for components with hardcoded shapes/elevations outside MaterialTheme. */
@Composable
internal fun paperToneControlShape(default: Shape): Shape =
    if (LocalPaperTone.current) MaterialTheme.shapes.small else default

@Composable
internal fun paperToneElevation(default: Dp): Dp = if (LocalPaperTone.current) 0.dp else default

@Composable
internal fun Modifier.paperToneDivider(): Modifier {
    if (!LocalPaperTone.current) return this
    val color = MaterialTheme.colorScheme.outlineVariant
    return drawWithContent {
        drawContent()
        val stroke = 0.5.dp.toPx()
        val y = size.height - stroke / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }
}

@Composable
internal fun PaperToneSheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(width = 28.dp, height = 2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall))
    }
}

@Composable
internal fun PaperTonePageHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart) {
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

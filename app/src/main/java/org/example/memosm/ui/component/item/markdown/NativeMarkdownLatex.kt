package org.example.memosm.ui.component.item.markdown

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.agog.mathdisplay.MTMathView

@Composable
fun NativeMarkdownLatex(
    modifier: Modifier = Modifier,
    latex: String,
) {
    val c = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { context ->
        MTMathView(context, null).apply {
            fontSize = 36f
            textColor = c
            // labelMode = MTMathViewMode.KMathViewModeDisplay
        }
    }, update = { view ->
        view.latex = latex
    }, modifier = modifier
    )
}

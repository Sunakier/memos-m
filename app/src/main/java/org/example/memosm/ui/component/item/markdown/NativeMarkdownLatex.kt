package org.example.memosm.ui.component.item.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.agog.mathdisplay.MTMathView

@Composable
fun NativeMarkdownLatex(
    latex: String, modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
        MTMathView(context, null).apply {
//            fontSize = 50f // Default size, maybe adjustable?
            // labelMode = MTMathViewMode.KMathViewModeDisplay
        }
    }, update = { view ->
        view.latex = latex
    }, modifier = modifier
    )
}

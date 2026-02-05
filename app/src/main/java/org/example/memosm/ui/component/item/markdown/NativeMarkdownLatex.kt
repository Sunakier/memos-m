package org.example.memosm.ui.component.item.markdown

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.gregcockroft.androidmath.MathView

@Composable
fun NativeMarkdownLatex(
    latex: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            MathView(context, null).apply {
                setEngine(MathView.Engine.KATEX) // or MATHJAX, usually KATEX is faster
                setText(latex)
            }
        },
        update = { view ->
            view.setText(latex)
        },
        modifier = modifier
    )
}

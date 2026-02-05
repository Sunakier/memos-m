package org.example.memosm.ui.component.item.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agog.mathdisplay.MTMathView
import com.agog.mathdisplay.parse.MTMathListBuilder

@Composable
fun NativeMarkdownLatex(
    modifier: Modifier = Modifier,
    latex: String,
) {
    // 1. Pre-validate the LaTeX string
    val validationError by remember(latex) {
        mutableStateOf(
            try {
                val list = MTMathListBuilder.buildFromString(latex)
                if (list == null) "Invalid LaTeX syntax" else null
            } catch (e: Exception) {
                e.message ?: "Rendering Error"
            }
        )
    }

    if (validationError != null) {
        // 2. Pass the raw source string to the card
        LatexErrorCard(
            error = validationError!!,
            source = latex,
            modifier = modifier
        )
    } else {
        val c = MaterialTheme.colorScheme.onSurface.toArgb()
        AndroidView(
            factory = { context ->
                MTMathView(context, null).apply {
                    fontSize = 36f
                    textColor = c
                }
            },
            update = { view ->
                view.latex = latex
            },
            modifier = modifier
        )
    }
}

@Composable
private fun LatexErrorCard(
    error: String,
    source: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Rendering Error",
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Equation Rendering Failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Error Description
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            // Source Code Block
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
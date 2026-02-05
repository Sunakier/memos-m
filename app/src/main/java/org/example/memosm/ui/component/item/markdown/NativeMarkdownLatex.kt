package org.example.memosm.ui.component.item.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.agog.mathdisplay.MTMathView
import com.agog.mathdisplay.parse.MTMathListBuilder

@Composable
fun NativeMarkdownLatex(
    modifier: Modifier = Modifier,
    latex: String,
    inline: Boolean // New parameter to distinguish inline vs block
) {
    // 1. Pre-validate the LaTeX string
    val validationError by remember(latex) {
        mutableStateOf(
            try {
                val list = MTMathListBuilder.buildFromString(latex)
                if (list == null) "Invalid Syntax" else null
            } catch (e: Exception) {
                e.message ?: "Error"
            }
        )
    }

    if (validationError != null) {
        // 2. Choose error display based on inline status
        if (inline) {
            InlineLatexError(
                source = latex,
                modifier = modifier
            )
        } else {
            BlockLatexErrorCard(
                error = validationError!!,
                source = latex,
                modifier = modifier
            )
        }
    } else {
        // 3. Render Native View if valid
        val c = MaterialTheme.colorScheme.onSurface.toArgb()
        AndroidView(
            factory = { context ->
                MTMathView(context, null).apply {
                    fontSize = if (inline) 24f else 36f // Smaller font for inline
                    textColor = c
                    // labelMode = MTMathViewMode.KMathViewModeDisplay
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
private fun InlineLatexError(
    source: String,
    modifier: Modifier = Modifier
) {
    // Simple red text for inline errors
    Text(
        text = source,
        color = MaterialTheme.colorScheme.error,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun BlockLatexErrorCard(
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Equation Error",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

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
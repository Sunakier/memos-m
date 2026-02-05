package org.example.memosm.ui.component.item.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.intellij.markdown.ast.ASTNode

/**
 * Helper function to get text content from an AST node
 */
fun ASTNode.getTextInNode(content: String): CharSequence {
    return content.subSequence(startOffset, endOffset)
}

/**
 * Helper to unescape typical markdown escapes if needed.
 * intellij-markdown doesn't automatically unescape, so we might need basic unescaping.
 * For now, returning raw text or implementing basic unescape for links.
 */
fun CharSequence.unescape(): String {
    // Simple unescape for common markdown escapes
    // This can be expanded based on requirements
    return this.toString()
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        // Add more as needed
}

fun Color.toHex(): String {
    return "%08X".format(this.toArgb())
}

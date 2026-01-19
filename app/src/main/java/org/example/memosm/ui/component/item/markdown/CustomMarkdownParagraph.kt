package org.example.memosm.ui.component.item.markdown

import MarkdownAttachmentImage
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.MarkdownComponents
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

@Composable
fun CustomMarkdownParagraph(
    content: String, node: ASTNode, components: MarkdownComponents, token: String, hostUrl: String
) {
    // Basic paragraph container, usually a wrapping logic or just a Column/FlowRow depending on implementation.
    // Since images might need to be full width, we probably use a Column or let them flow.
    // Standard markdown paragraphs are usually text flows.
    // However, for AttachmentCard we want it to be a block if it's a standalone image.
    // If it's mixed with text, it's tricky.
    // Let's assume for now we iterate and render.

    // Using FlowRow-like behavior or just standard traversal?
    // The library's default paragraph likely uses a Text composable with Spans.
    // But since we want to render Composables (AttachmentCard), we can't be inside a Text.
    // So we must break the paragraph into Composables.

    // A simple approach: Column of elements?
    // But text should flow.
    // "Text Image Text" -> "Text" (break) "Image" (break) "Text".
    // This breaks inline flow but allows AttachmentCard.

    Column(modifier = Modifier.fillMaxWidth()) {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownElementTypes.IMAGE -> {
                    Log.d("MemosDebug", "CustomMarkdownParagraph: Found IMAGE node")
                    MarkdownAttachmentImage(
                        content = content, node = child, token = token, hostUrl = hostUrl
                    )
                }

                else -> {
                    MarkdownElement(
                        node = child,
                        components = components,
                        content = content,
                        includeSpacer = false
                    )
                }
            }
        }
    }
}

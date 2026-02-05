package org.example.memosm.ui.component.item.markdown

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.memosm.model.Attachment
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

@Composable
fun NativeMarkdownAttachmentImage(content: String, node: ASTNode) {
    // Find link destination. 
    // Image structure: ![LinkText](LinkDestination)
    // Children might include:
    // [ ![ ] (LINK_DESTINATION) ]
    // or INLINE_LINK with image?
    // Standard Image: [ !, [, LINK_TEXT, ], (, LINK_DESTINATION, ) ]

    val linkDestinationNode = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
    var link = linkDestinationNode?.getTextInNode(content)?.toString()
    
    if (link == null) {
        // Try to resolve as reference
        val labelNode = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_LABEL)
        if (labelNode != null) {
             val label = labelNode.getTextInNode(content).toString().lowercase()
             val references = LocalMarkdownReferences.current
             link = references[label]
        }
    }
    
    if (link == null) return

    // Maintain aspect ratio state
    var aspectRatio by remember { mutableFloatStateOf(1.777f) }

    val token = LocalToken.current
    val hostUrl = LocalHostUrl.current

    AttachmentCard(
        attachment = Attachment(
            externalLink = link, filename = link, type = "image", mimeType = "image/auto"
        ),
        token = token,
        hostUrl = hostUrl,

        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        showInfo = false,
        showActions = false,
        showSize = false,
        showFilename = false,
        compactMode = AttachmentCompactMode.Never,
        onRatioAvailable = { ratio, _ ->
            if (ratio > 0) {
                aspectRatio = ratio
            }
        })
}

// Helper to find child recursively
fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    children.forEach { child ->
        if (child.type == type) return child
        val found = child.findChildOfTypeRecursive(type)
        if (found != null) return found
    }
    return null
}

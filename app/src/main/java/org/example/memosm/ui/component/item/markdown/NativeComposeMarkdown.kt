package org.example.memosm.ui.component.item.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

val LocalToken = compositionLocalOf { "" }
val LocalHostUrl = compositionLocalOf { "" }
val LocalMarkdownReferences = compositionLocalOf { emptyMap<String, String>() }

@Composable
fun NativeComposeMarkdown(
    modifier: Modifier = Modifier,
    content: String,
    token: String = "",
    hostUrl: String = "",
    selectable: Boolean = false,
    onContentChange: ((String) -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null
) {
    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour) }
    val tree = remember(content, parser) { parser.buildMarkdownTreeFromString(content) }

    val references = remember(tree, content) {
        val refs = mutableMapOf<String, String>()
        tree.children.forEach { child ->
            if (child.type == org.intellij.markdown.MarkdownElementTypes.LINK_DEFINITION) {
                val labelNode =
                    child.findChildOfType(org.intellij.markdown.MarkdownElementTypes.LINK_LABEL)
                val destNode =
                    child.findChildOfType(org.intellij.markdown.MarkdownElementTypes.LINK_DESTINATION)
                if (labelNode != null && destNode != null) {
                    val label = labelNode.getTextInNode(content).toString().lowercase()
                    val dest = destNode.getTextInNode(content).toString()
                    refs[label] = dest
                }
            }
        }
        refs
    }

    CompositionLocalProvider(
        LocalToken provides token,
        LocalHostUrl provides hostUrl,
        LocalMarkdownReferences provides references
    ) {
        if (selectable) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                NativeMarkdownNode(
                    node = tree,
                    content = content,
                    modifier = modifier,
                    onContentChange = onContentChange,
                    onHashtagClick = onHashtagClick
                )
            }
        } else {
            NativeMarkdownNode(
                node = tree,
                content = content,
                modifier = modifier,
                onContentChange = onContentChange,
                onHashtagClick = onHashtagClick
            )
        }
    }
}

package org.example.memosm.ui.component.item.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.ast.findChildOfType
import toggleCheckbox

// Local provider for content and callbacks to avoid passing them deep
val LocalMarkdownContent = compositionLocalOf { "" }
val LocalOnContentChange = compositionLocalOf<((String) -> Unit)?> { null }

@Composable
fun NativeMarkdownNode(
    modifier: Modifier = Modifier,
    node: ASTNode,
    content: String,
    onContentChange: ((String) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalMarkdownContent provides content,
        LocalOnContentChange provides onContentChange
    ) {
        Column(modifier = modifier) {
            NativeMarkdownNodeRecursive(node)
        }
    }
}

@Composable
fun NativeMarkdownNodeRecursive(node: ASTNode) {
    val content = LocalMarkdownContent.current
    val onContentChange = LocalOnContentChange.current

    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> {
            // Render all children
            node.children.forEach { child ->
                NativeMarkdownNodeRecursive(child)
            }
        }

        MarkdownElementTypes.PARAGRAPH -> {
            // Render paragraph text with inline styling
            // This needs to collect all inline children and build an AnnotatedString
            val styledText = buildAnnotatedString {
                appendInlineChildren(node, content)
            }
            Text(
                text = styledText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val style = when (node.type) {
                MarkdownElementTypes.ATX_1 -> MaterialTheme.typography.displaySmall
                MarkdownElementTypes.ATX_2 -> MaterialTheme.typography.headlineMedium
                MarkdownElementTypes.ATX_3 -> MaterialTheme.typography.headlineSmall
                MarkdownElementTypes.ATX_4 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            // Headers contain inline elements usually, or just leaf text
            // Retrieve the text content excluding the # characters
            // BUT intellij-markdown structure for headers usually has: 
            // ATX_1 -> [ATX_CONTENT -> [TEXT]]
            // We can just recursively render inline content.
            // But headers are block elements, so we treat them as text with style.
            val styledText = buildAnnotatedString {
                // Skip the leading hashtags if they are separate tokens or part of content?
                // Usually children include HEADER_LEAD (#) and content.
                // We will filter only relevant text content.
                node.children.forEach { child ->
                    if (child.type != MarkdownTokenTypes.ATX_HEADER) {
                        // append recursively
                        appendInlineChildren(child, content)
                    }
                }
            }
            Text(
                text = styledText,
                style = style,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        MarkdownElementTypes.UNORDERED_LIST -> {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                node.children.forEach { child ->
                    if (child.type == MarkdownElementTypes.LIST_ITEM) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Check for Checkbox
                            val checkBoxNode = child.findChildOfType(GFMTokenTypes.CHECK_BOX)
                            if (checkBoxNode != null) {
                                val isChecked = checkBoxNode.getTextInNode(content)
                                    .contains("x", ignoreCase = true)
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        // Edit source string: replace [ ] with [x] or vice versa
                                        onContentChange?.invoke(
                                            toggleCheckbox(
                                                content,
                                                checkBoxNode.startOffset,
                                                checkBoxNode.endOffset,
                                                checked
                                            )
                                        )
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            } else {
                                Text("•", modifier = Modifier.padding(horizontal = 8.dp))
                            }

                            Column {
                                child.children.forEach { listChild ->
                                    if (listChild.type != GFMTokenTypes.CHECK_BOX) {
                                        NativeMarkdownNodeRecursive(listChild)
                                    }
                                }
                            }
                        }
                    } else {
                        NativeMarkdownNodeRecursive(child)
                    }
                }
            }
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            var index = 1
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                node.children.forEach { child ->
                    if (child.type == MarkdownElementTypes.LIST_ITEM) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("$index.", modifier = Modifier.padding(horizontal = 8.dp))
                            Column {
                                child.children.forEach { listChild ->
                                    NativeMarkdownNodeRecursive(listChild)
                                }
                            }
                            index++
                        }
                    } else {
                        NativeMarkdownNodeRecursive(child)
                    }
                }
            }
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            // Simple blockquote with left border/padding
            Row(modifier = Modifier.padding(vertical = 4.dp)) {
                Spacer(
                    modifier = Modifier
                        .width(4.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    node.children.forEach { child ->
                        if (child.type != MarkdownTokenTypes.BLOCK_QUOTE) {
                            NativeMarkdownNodeRecursive(child)
                        }
                    }
                }
            }
        }

        GFMElementTypes.TABLE -> {
            NativeMarkdownTable(content, node)
        }

        MarkdownElementTypes.IMAGE -> {
            // Image handling via AttachmentCard
            // Children: [ "![", LINK_TEXT, "](", LINK_DESTINATION, ")"]
            // or helper to find link
            // We can implement a specific NativeMarkdownAttachment component
            NativeMarkdownAttachmentImage(content, node)
        }

        // Handle other block types or fallthrough
        else -> {
            // If it's a composite node, visit children.
            // If it matches known inline types but we are in block context?
            // Usually PARAGRAPH wraps inline content.
            // If we encounter raw text or unknown nodes at block level:
            if (node.children.isNotEmpty()) {
                node.children.forEach { NativeMarkdownNodeRecursive(it) }
            }
        }
    }
}

// Inline content builder
@Composable
fun AnnotatedString.Builder.appendInlineChildren(node: ASTNode, content: String) {
    if (node.children.isEmpty()) {
        // Leaf node, append text
        append(node.getTextInNode(content).toString())
        return
    }

    node.children.forEach { child ->
        when (child.type) {
            MarkdownElementTypes.STRONG -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendInlineChildren(child, content)
                }
            }

            MarkdownElementTypes.EMPH -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineChildren(child, content)
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    // Usually wraps content in backticks, we might want to strip them or custom render
                    // Simple approach: append everything.
                    // Or finding children of type TEXT inside.
                    // Often CODE_SPAN has children [BACKTICK, TEXT, BACKTICK]
                    child.children.forEach { grandChild ->
                        if (grandChild.type != MarkdownTokenTypes.BACKTICK) {
                            append(grandChild.getTextInNode(content).toString())
                        }
                    }
                }
            }

            MarkdownElementTypes.LINK_DEFINITION, MarkdownElementTypes.INLINE_LINK -> {
                // For now, just append text. Links need ClickableText or Annotation.
                // We can use pushStringAnnotation
                val linkText =
                    child.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)
                        ?.toString() ?: "Link"
                val linkDest = child.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getTextInNode(content)?.toString() ?: ""

                pushStringAnnotation(tag = "URL", annotation = linkDest)
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    // The text might be wrapped in brackets in the AST?
                    // LINK_TEXT children: [ "[", TEXT, "]" ]
                    // We recursively append children of LINK_TEXT, avoiding brackets
                    val linkTextNode = child.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    if (linkTextNode != null) {
                        linkTextNode.children.forEach { lc ->
                            if (lc.type != MarkdownTokenTypes.LBRACKET && lc.type != MarkdownTokenTypes.RBRACKET) {
                                appendInlineChildren(lc, content)
                            }
                        }
                    } else {
                        append(linkText)
                    }
                }
                pop()
            }

            GFMElementTypes.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    child.children.forEach { c ->
                        if (c.type != GFMTokenTypes.TILDE) {
                            appendInlineChildren(c, content)
                        }
                    }
                }
            }

            else -> {
                appendInlineChildren(child, content)
            }
        }
    }
}

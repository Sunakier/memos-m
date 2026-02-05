package org.example.memosm.ui.component.item.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.background
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.ast.findChildOfType
import toggleCheckbox

// Helper data class for styles
data class MarkdownStyles(
    val codeBackground: Color,
    val linkColor: Color,
    val strikethroughStyle: SpanStyle,
    val boldStyle: SpanStyle,
    val italicStyle: SpanStyle,
    val codeFontFamily: FontFamily
)

@Composable
fun MarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val defaultColor = LocalContentColor.current
    val textColor = if (style.color.isSpecified) style.color else defaultColor

    ClickableText(
        text = text,
        style = style.copy(color = textColor),
        modifier = modifier,
        onClick = { offset ->
            text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }
    )
}

// Local provider for content and callbacks to avoid passing them deep
val LocalMarkdownContent = compositionLocalOf { "" }
val LocalOnContentChange = compositionLocalOf<((String) -> Unit)?> { null }
val LocalForceNoTopPadding = compositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeMarkdownNode(
    modifier: Modifier = Modifier,
    node: ASTNode,
    content: String,
    onContentChange: ((String) -> Unit)? = null
) {
    CompositionLocalProvider(
        LocalMarkdownContent provides content, LocalOnContentChange provides onContentChange
    ) {
        Column(modifier = modifier) {
            NativeMarkdownNodeRecursive(node)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeMarkdownNodeRecursive(node: ASTNode) {
    val content = LocalMarkdownContent.current
    val onContentChange = LocalOnContentChange.current

    val styles = MarkdownStyles(
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        linkColor = MaterialTheme.colorScheme.primary,
        strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
        boldStyle = SpanStyle(fontWeight = FontWeight.Bold),
        italicStyle = SpanStyle(fontStyle = FontStyle.Italic),
        codeFontFamily = FontFamily.Monospace
    )

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
                appendInlineChildren(node, content, styles)
            }
            val noTopPadding = LocalForceNoTopPadding.current
            val topPadding = if (noTopPadding) 0.dp else 4.dp
            
            MarkdownText(
                text = styledText,
                style = typography.bodyLarge,
                modifier = Modifier.padding(top = topPadding, bottom = 4.dp)
            )
        }

        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val style = when (node.type) {
                MarkdownElementTypes.ATX_1 -> typography.displaySmall
                MarkdownElementTypes.ATX_2 -> typography.headlineMedium
                MarkdownElementTypes.ATX_3 -> typography.headlineSmall
                MarkdownElementTypes.ATX_4 -> typography.titleLarge
                else -> typography.titleMedium
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
                         visitInlineChild(child, content, styles, this)
                     }
                 }
            }
            val noTopPadding = LocalForceNoTopPadding.current
            val topPadding = if (noTopPadding) 0.dp else 8.dp
            
            MarkdownText(
                text = styledText, style = style, modifier = Modifier.padding(top = topPadding, bottom = 8.dp)
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
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            onContentChange?.invoke(
                                                toggleCheckbox(
                                                    content,
                                                    checkBoxNode.startOffset,
                                                    checkBoxNode.endOffset,
                                                    checked
                                                )
                                            )
                                        },
                                        modifier = Modifier.scale(0.8f).offset(x = (-4).dp, y = (-2).dp).padding(end = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "•",
                                    style = typography.bodyLarge,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }

                            Column {
                                var firstChildProcessed = false
                                child.children.forEach { listChild ->
                                    if (listChild.type != GFMTokenTypes.CHECK_BOX &&
                                        listChild.type != MarkdownTokenTypes.LIST_BULLET &&
                                        listChild.type != org.intellij.markdown.MarkdownTokenTypes.EOL
                                    ) {
                                        if (!firstChildProcessed) {
                                            CompositionLocalProvider(LocalForceNoTopPadding provides true) {
                                                NativeMarkdownNodeRecursive(listChild)
                                            }
                                            firstChildProcessed = true
                                        } else {
                                            NativeMarkdownNodeRecursive(listChild)
                                        }
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
                            Text(
                                "$index.",
                                style = typography.bodyLarge,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column {
                                var firstChildProcessed = false
                                child.children.forEach { listChild ->
                                    if (listChild.type != MarkdownTokenTypes.LIST_NUMBER &&
                                        listChild.type != org.intellij.markdown.MarkdownTokenTypes.EOL
                                    ) {
                                        if (!firstChildProcessed) {
                                            CompositionLocalProvider(LocalForceNoTopPadding provides true) {
                                                NativeMarkdownNodeRecursive(listChild)
                                            }
                                            firstChildProcessed = true
                                        } else {
                                            NativeMarkdownNodeRecursive(listChild)
                                        }
                                    }
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

        MarkdownElementTypes.CODE_BLOCK, MarkdownElementTypes.CODE_FENCE -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                val sb = StringBuilder()
                node.children.forEach { child ->
                    val text = child.getTextInNode(content)
                    // Naive filtering of fence markers based on text content
                    // and ignoring language identifier token if recognizable
                    if (child.type != MarkdownTokenTypes.FENCE_LANG &&
                        !text.trim().startsWith("```") &&
                        !text.trim().startsWith("~~~")) {
                        sb.append(text)
                    }
                }
                Text(
                    text = sb.toString().trim(),
                    style = typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        MarkdownElementTypes.BLOCK_QUOTE -> {
            // Simple blockquote with left border/padding
            Row(modifier = Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
                Spacer(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(color = MaterialTheme.colorScheme.outlineVariant, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        node.children.forEach { child ->
                            if (child.type != MarkdownTokenTypes.BLOCK_QUOTE) {
                                NativeMarkdownNodeRecursive(child)
                            }
                        }
                    }
                }
            }
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
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

// Inline content builder, NOT Composable
fun AnnotatedString.Builder.appendInlineChildren(
    node: ASTNode, content: String, styles: MarkdownStyles
) {
    if (node.children.isEmpty()) {
        // Leaf node, append text
        append(node.getTextInNode(content).toString())
        return
    }

    node.children.forEach { child ->
        visitInlineChild(child, content, styles, this)
    }
}

fun visitInlineChild(child: ASTNode, content: String, styles: MarkdownStyles, builder: AnnotatedString.Builder) {
    with(builder) {
        when (child.type) {
            MarkdownElementTypes.STRONG -> {
                withStyle(styles.boldStyle) {
                    child.children.forEach { c ->
                        if (c.type != MarkdownTokenTypes.EMPH) {
                            visitInlineChild(c, content, styles, this)
                        }
                    }
                }
            }

            MarkdownElementTypes.EMPH -> {
                withStyle(styles.italicStyle) {
                    child.children.forEach { c ->
                        if (c.type != MarkdownTokenTypes.EMPH) {
                            visitInlineChild(c, content, styles, this)
                        }
                    }
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                withStyle(
                    SpanStyle(
                        fontFamily = styles.codeFontFamily, background = styles.codeBackground
                    )
                ) {
                    child.children.forEach { grandChild ->
                        if (grandChild.type != MarkdownTokenTypes.BACKTICK) {
                            append(grandChild.getTextInNode(content).toString())
                        }
                    }
                }
            }

            MarkdownElementTypes.LINK_DEFINITION, MarkdownElementTypes.INLINE_LINK -> {
                val linkText =
                    child.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)
                        ?.toString() ?: "Link"
                val linkDest = child.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getTextInNode(content)?.toString() ?: ""

                pushStringAnnotation(tag = "URL", annotation = linkDest)
                withStyle(SpanStyle(color = styles.linkColor)) {
                    val linkTextNode = child.findChildOfType(MarkdownElementTypes.LINK_TEXT)
                    if (linkTextNode != null) {
                        linkTextNode.children.forEach { lc ->
                            if (lc.type != MarkdownTokenTypes.LBRACKET && lc.type != MarkdownTokenTypes.RBRACKET) {
                                visitInlineChild(lc, content, styles, this)
                            }
                        }
                    } else {
                        append(linkText)
                    }
                }
                pop()
            }

            GFMElementTypes.STRIKETHROUGH -> {
                withStyle(styles.strikethroughStyle) {
                    child.children.forEach { c ->
                        if (c.type != GFMTokenTypes.TILDE) {
                            visitInlineChild(c, content, styles, this)
                        }
                    }
                }
            }

            else -> {
                appendInlineChildren(child, content, styles)
            }
        }
    }
}

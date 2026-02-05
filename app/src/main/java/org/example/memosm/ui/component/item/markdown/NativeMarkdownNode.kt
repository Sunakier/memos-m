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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.LinkAnnotation
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
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    val uriHandler = LocalUriHandler.current
    val defaultColor = LocalContentColor.current
    val textColor = if (style.color.isSpecified) style.color else defaultColor
    
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    
    val drawModifier = modifier.drawBehind {
        layoutResult?.let { layout ->
            text.getStringAnnotations("ROUNDED_BG_COLOR", 0, text.length).forEach { range ->
                try {
                    val color = Color(range.item.toLong(16))
                    val path = layout.getPathForRange(range.start, range.end)
                    val bounds = path.getBounds()
                    // Draw slightly inflated rounded rect for better visuals
                    drawRoundRect(
                        color = color,
                        topLeft = bounds.topLeft,
                        size = bounds.size,
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                } catch (e: Exception) {
                    // Ignore parsing errors
                }
            }
        }
    }

    Text(
        text = text,
        style = style.copy(color = textColor),
        modifier = drawModifier,
        onTextLayout = { layoutResult = it },
        textAlign = textAlign
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
        codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                var lang = ""
                val sb = StringBuilder()
                node.children.forEach { child ->
                    when (child.type) {
                        MarkdownTokenTypes.FENCE_LANG -> {
                            lang = child.getTextInNode(content).toString().trim()
                        }
                        MarkdownTokenTypes.CODE_FENCE_CONTENT,
                        MarkdownTokenTypes.CODE_LINE,
                        MarkdownTokenTypes.EOL -> {
                            sb.append(child.getTextInNode(content))
                        }
                        // Ignore fence delimiters (START/END) and other metadata
                    }
                }
                
                // Remove leading/trailing newlines to avoid extra padding, but preserve indentation
                val code = sb.toString().removePrefix("\n").removeSuffix("\n")
                val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val highlightedText = CodeHighlighter.highlightCode(code, lang, isDarkTheme)
                
                Text(
                    text = highlightedText,
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

            MarkdownElementTypes.AUTOLINK -> {
                val text = child.getTextInNode(content).toString()
                val destination = text.removePrefix("<").removeSuffix(">")
                
                pushLink(LinkAnnotation.Url(destination))
                withStyle(SpanStyle(color = styles.linkColor)) {
                    append(destination)
                }
                pop()
            }

            GFMTokenTypes.GFM_AUTOLINK -> {
               val text = child.getTextInNode(content).toString()
               pushLink(LinkAnnotation.Url(text))
               withStyle(SpanStyle(color = styles.linkColor)) {
                   append(text)
               }
               pop()
            }

            MarkdownElementTypes.CODE_SPAN -> {
                val hexColor = styles.codeBackground.toHex()
                pushStringAnnotation(tag = "ROUNDED_BG_COLOR", annotation = hexColor)
                withStyle(
                    SpanStyle(
                        fontFamily = styles.codeFontFamily
                    )
                ) {
                    child.children.forEach { grandChild ->
                        if (grandChild.type != MarkdownTokenTypes.BACKTICK) {
                            append(grandChild.getTextInNode(content).toString())
                        }
                    }
                }
                pop()
            }

            MarkdownElementTypes.LINK_DEFINITION, MarkdownElementTypes.INLINE_LINK -> {
                val linkText =
                    child.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)
                        ?.toString() ?: "Link"
                val linkDest = child.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)
                    ?.getTextInNode(content)?.toString() ?: ""

                pushLink(LinkAnnotation.Url(linkDest))
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

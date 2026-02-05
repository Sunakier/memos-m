package org.example.memosm.ui.component.item.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

val LocalToken = compositionLocalOf { "" }
val LocalHostUrl = compositionLocalOf { "" }

@Composable
fun NativeComposeMarkdown(
    modifier: Modifier = Modifier,
    content: String,
    token: String = "",
    hostUrl: String = "",
    selectable: Boolean = false,
    onContentChange: ((String) -> Unit)? = null
) {
    val flavour = remember { GFMFlavourDescriptor() }
    val parser = remember(flavour) { MarkdownParser(flavour) }
    val tree = remember(content, parser) { parser.buildMarkdownTreeFromString(content) }

    CompositionLocalProvider(
        LocalToken provides token, LocalHostUrl provides hostUrl
    ) {
        if (selectable) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                NativeMarkdownNode(
                    node = tree, content = content, modifier = modifier, onContentChange = onContentChange
                )
            }
        } else {
            NativeMarkdownNode(
                node = tree, content = content, modifier = modifier, onContentChange = onContentChange
            )
        }
    }
}

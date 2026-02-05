package org.example.memosm

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Test
import org.intellij.markdown.ast.ASTNode

class RecursiveMarkdownDebug {
    @Test
    fun testNestedStructure() {
        val content = "- __[pica](https://nodeca.github.io/pica/demo/)__ - high quality"
        val flavour = GFMFlavourDescriptor()
        val parser = MarkdownParser(flavour)
        val tree = parser.buildMarkdownTreeFromString(content)
        
        printTree(tree, content, "")
    }
    
    private fun printTree(node: ASTNode, content: String, indent: String) {
        val type = node.type.toString()
        val text = node.getTextInNode(content).toString().replace("\n", "\\n")
        java.io.File("ast_dump.txt").appendText("$indent$type: '$text'\n")
        node.children.forEach { printTree(it, content, "$indent  ") }
    }
    
    private fun ASTNode.getTextInNode(content: CharSequence): CharSequence {
        return content.subSequence(startOffset, endOffset)
    }
}

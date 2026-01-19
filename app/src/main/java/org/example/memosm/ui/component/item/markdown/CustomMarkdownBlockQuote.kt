import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.MarkdownElement
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode

@Composable
fun CustomMarkdownBlockQuote(
    content: String, node: ASTNode, style: TextStyle
) {
    Row(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {

        // Vertical bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(2.dp)
                )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            val components = LocalMarkdownComponents.current

            node.children.forEach { child ->

                when (child.type) {

                    // ✅ Recurse for nested blockquotes
                    MarkdownElementTypes.BLOCK_QUOTE -> {
                        CustomMarkdownBlockQuote(
                            content = content, node = child, style = style
                        )
                    }

                    // Normal markdown content
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
}
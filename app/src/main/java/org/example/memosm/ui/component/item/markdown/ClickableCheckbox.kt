import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import org.intellij.markdown.ast.getTextInNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickableCheckbox(
    model: MarkdownComponentModel, content: String, onToggle: ((String) -> Unit)?
) {
    val nodeText = model.node.getTextInNode(content)
    val isChecked = nodeText.contains("[x]") || nodeText.contains("[X]")

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = if (onToggle != null) { _ ->
                val startOffset = model.node.startOffset
                val endOffset = model.node.endOffset
                val checkboxText = content.substring(startOffset, endOffset)
                val newCheckboxText = if (isChecked) {
                    checkboxText.replace("[x]", "[ ]", ignoreCase = true)
                } else {
                    checkboxText.replace("[ ]", "[x]")
                }
                val newContent =
                    content.take(startOffset) + newCheckboxText + content.substring(endOffset)
                onToggle(newContent)
            } else null,
            modifier = Modifier
                .padding(end = 4.dp)
                .size(20.dp),
            enabled = onToggle != null)
    }
}
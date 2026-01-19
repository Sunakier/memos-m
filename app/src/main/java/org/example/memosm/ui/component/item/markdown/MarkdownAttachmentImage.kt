import android.util.Log
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
import com.mikepenz.markdown.utils.getUnescapedTextInNode
import org.example.memosm.model.Attachment
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType


@Composable
fun MarkdownAttachmentImage(content: String, node: ASTNode, token: String, hostUrl: String) {
    val link = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
        ?.getUnescapedTextInNode(content)

    Log.d("MemosDebug", "MarkdownAttachmentImage: link=$link")

    if (link == null) return

    // Maintain aspect ratio state, distinct from the default "16/9" if unknown
    // We start with a default but allow it to change.
    var aspectRatio by remember { mutableFloatStateOf(1.777f) }

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
        onRatioAvailable = {
            if (it > 0) {
                aspectRatio = it
            }
        })
}




fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    findChildOfType(type)?.let { return it }
    for (child in children) {
        child.findChildOfTypeRecursive(type)?.let { return it }
    }
    return null
}

package org.example.memosm.ui.component.item.markdown

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.memosm.model.Attachment
import org.example.memosm.ui.component.item.AttachmentCard
import org.example.memosm.ui.component.item.AttachmentCompactMode
import androidx.core.net.toUri

/**
 * Global collector used during markdown rendering.
 *
 * markdown-compose processes images BEFORE paragraphs,
 * so we must buffer image links here and let paragraphs consume them.
 */
object ImageBlockCollector {

    private val pendingImages: SnapshotStateList<String> = mutableStateListOf()

    fun add(link: String) {
        pendingImages += link
    }

    fun hasImages(): Boolean = pendingImages.isNotEmpty()

    fun consumeAll(): List<String> {
        val result = pendingImages.toList()
        pendingImages.clear()
        return result
    }
}

/**
 * Renders all collected images as AttachmentCards.
 */
@Composable
fun RenderCollectedImages(
    token: String?,
    hostUrl: String
) {
    val images = ImageBlockCollector.consumeAll()
    if (images.isEmpty()) return

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        images.forEach { link ->
            AttachmentCard(
                attachment = link.toAttachment(),
                token = token,
                hostUrl = hostUrl,
                showInfo = false,
                showActions = false,
                showSize = false,
                showFilename = false,
                compactMode = AttachmentCompactMode.Never
            )
        }
    }
}

/**
 * Converts an image URL into an Attachment that AttachmentCard understands.
 */
private fun String.toAttachment(): Attachment {
    val filename = this.toUri().lastPathSegment ?: "image"

    return Attachment(
        externalLink = this,
        filename = filename,
        type = "image",
        mimeType = "image/auto"
    )
}

package org.example.memosm.ui.component.item.markdown

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

/**
 * ImageTransformer that:
 * - intercepts markdown images
 * - records their URLs
 * - suppresses default image rendering
 */
object AttachmentCardImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        Log.d("MemosDebug", "AttachmentCardImageTransformer: transform link=$link")

        // Record the image link so the paragraph renderer can use it
        ImageBlockCollector.add(link)

        // Return a dummy ImageData so markdown-compose thinks the image was handled
        return ImageData(
            painter = ColorPainter(Color.Transparent)
        )
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        // Must be non-zero or layout may collapse
        return Size(1f, 1f)
    }
}

package org.example.memosm.ui.component.item

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer

object AttachmentCardImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        android.util.Log.d("MemosDebug", "AttachmentCardImageTransformer: transform link=$link")
        // We return a dummy ImageData because we handle the rendering in the component
        // explicitly using AttachmentCard. The library requires non-null ImageData
        // to invoke the image component.
        return ImageData(
            painter = ColorPainter(Color.Transparent)
        )
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        return Size(100f, 100f) // Return non-zero size to ensure layout
    }
}

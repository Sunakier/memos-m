package org.example.memosm

import android.graphics.drawable.Animatable
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.DrawableImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GifImageLoaderTest {
    @Test
    fun applicationImageLoaderDecodesGifAsAnimation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val application = context.applicationContext as MemosApplication
        val imageLoader = application.newImageLoader(context)
        try {
            // A looping 2x2 GIF with red and green frames, each lasting 100 ms.
            // Raw bytes exercise decoding independently of filenames and server MIME types.
            val gif = Base64.decode(
                "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAA" +
                    "AgACAAAIBgABCAQQEAAh+QQBCgABACwAAAAAAgACAIEAgAAAAAAAAAAAAAAIBgABCAQQEAA7",
                Base64.DEFAULT
            )
            val result = imageLoader.execute(ImageRequest.Builder(context).data(gif).build())

            assertTrue("GIF decoding failed: $result", result is SuccessResult)
            val image = (result as SuccessResult).image
            assertEquals(2, image.width)
            assertEquals(2, image.height)
            assertTrue("GIF must retain its animation", (image as? DrawableImage)?.drawable is Animatable)
        } finally {
            imageLoader.shutdown()
        }
    }
}

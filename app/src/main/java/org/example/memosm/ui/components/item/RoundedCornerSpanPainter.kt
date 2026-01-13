package org.example.memosm.ui.components.item

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpanPainter
import com.mikepenz.markdown.compose.extendedspans.SpanDrawInstructions

/**
 * A Material 3 optimized span painter that avoids the [Path.addRoundRect] crash
 * by using the more stable [Outline] API.
 */
class RoundedCornerSpanPainter(
    private val cornerRadius: TextUnit = 4.sp, // Material 3 "Extra Small" shape token
    private val stroke: Stroke? = null,
    private val padding: TextPaddingValues = TextPaddingValues(horizontal = 4.sp, vertical = 2.sp),
    private val topMargin: TextUnit = 1.sp,
    private val bottomMargin: TextUnit = 1.sp,
) : ExtendedSpanPainter() {

    override fun decorate(
        span: SpanStyle,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): SpanStyle {
        return if (span.background.isUnspecified) {
            span
        } else {
            builder.addStringAnnotation(TAG, annotation = span.background.serialize(), start = start, end = end)
            span.copy(background = Color.Unspecified)
        }
    }

    override fun decorate(
        linkAnnotation: LinkAnnotation,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): LinkAnnotation {
        return linkAnnotation
    }

    override fun drawInstructionsFor(layoutResult: TextLayoutResult, color: Color?): SpanDrawInstructions {
        val text = layoutResult.layoutInput.text
        if (text.isEmpty()) return SpanDrawInstructions { }

        val annotations = text.getStringAnnotations(TAG, start = 0, end = text.length)

        return SpanDrawInstructions {
            val cornerRadiusPx = CornerRadius(cornerRadius.toPx())

            annotations.fastForEach { annotation ->
                val backgroundColor = annotation.item.deserializeToColor() ?: return@fastForEach

                // Safety guard for indices
                val start = annotation.start.coerceIn(0, text.length)
                val end = annotation.end.coerceIn(0, text.length)
                if (start >= end) return@fastForEach

                val boxes = layoutResult.getBoundingBoxes(
                    startOffset = start,
                    endOffset = end,
                    flattenForFullParagraphs = true
                )

                boxes.fastForEachIndexed { index, box ->
                    // 1. Define the Rectangle with padding
                    val rect = box.copy(
                        left = box.left - padding.horizontal.toPx(),
                        right = box.right + padding.horizontal.toPx(),
                        top = box.top - padding.vertical.toPx() + topMargin.toPx(),
                        bottom = box.bottom + padding.vertical.toPx() - bottomMargin.toPx(),
                    )

                    // 2. Create the RoundRect (handles line-wrapping rounding logic)
                    val roundRect = RoundRect(
                        rect = rect,
                        topLeft = if (index == 0) cornerRadiusPx else CornerRadius.Zero,
                        bottomLeft = if (index == 0) cornerRadiusPx else CornerRadius.Zero,
                        topRight = if (index == boxes.lastIndex) cornerRadiusPx else CornerRadius.Zero,
                        bottomRight = if (index == boxes.lastIndex) cornerRadiusPx else CornerRadius.Zero
                    )

                    // 3. Draw using the stable Outline API
                    val outline = Outline.Rounded(roundRect)

                    drawOutline(
                        outline = outline,
                        color = backgroundColor,
                        style = Fill
                    )

                    stroke?.let {
                        drawOutline(
                            outline = outline,
                            color = it.color(backgroundColor),
                            style = Stroke(width = it.width.toPx())
                        )
                    }
                }
            }
        }
    }

    data class Stroke(val color: (background: Color) -> Color, val width: TextUnit = 1.sp) {
        constructor(color: Color, width: TextUnit = 1.sp) : this(color = { color }, width = width)
    }

    data class TextPaddingValues(val horizontal: TextUnit = 0.sp, val vertical: TextUnit = 0.sp)

    companion object {
        private const val TAG = "rounded_corner_span"
    }
}

private fun Color.serialize(): String = value.toString()

private fun String.deserializeToColor(): Color? = toULongOrNull()?.let { Color(it) }

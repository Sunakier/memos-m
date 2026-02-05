package org.example.memosm.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.ColorFilter
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.example.memosm.MainActivity
import org.example.memosm.R

class DraftWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val size = androidx.glance.LocalSize.current
            // Show text if width is >= 150dp (approx 2 cells), similar to original logic
            val showText = size.width >= 150.dp

            GlanceTheme {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(12.dp)
                        .clickable(actionRunCallback<OpenComposerAction>()),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.outline_ink_pen_24),
                            contentDescription = context.getString(R.string.memo_composer_fab_new_memo),
                            modifier = GlanceModifier.size(24.dp),
                            colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer)
                        )
                        if (showText) {
                            Spacer(GlanceModifier.width(8.dp))
                            Text(
                                text = context.getString(R.string.memo_composer_fab_new_memo),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onPrimaryContainer,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_COMPOSER = "org.example.memosm.action.OPEN_COMPOSER"
    }
}

class OpenComposerAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = DraftWidget.ACTION_OPEN_COMPOSER
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
}

class DraftWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DraftWidget()
}

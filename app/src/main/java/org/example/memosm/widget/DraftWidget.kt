package org.example.memosm.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.example.memosm.MainActivity
import org.example.memosm.R

import android.os.Bundle
import android.view.View

class DraftWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    companion object {
        const val ACTION_OPEN_COMPOSER = "org.example.memosm.action.OPEN_COMPOSER"

        internal fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Check widget width options
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            // minWidth is in dp
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            
            // Show text if width is >= 100dp (approx 2 cells)
            val showText = minWidth >= 150

            // Create an Intent to launch MainActivity with the specific action
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_COMPOSER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, R.layout.widget_draft)
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

            // Toggle text visibility
            if (showText) {
                views.setViewVisibility(R.id.widget_text, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_text, View.GONE)
            }

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

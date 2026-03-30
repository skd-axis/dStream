package com.lifestreams.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)

        mgr.getAppWidgetIds(ComponentName(context, GridWidget::class.java))
            .forEach { GridWidget.updateGridWidget(context, mgr, it) }

        mgr.getAppWidgetIds(ComponentName(context, TopStreamsWidget::class.java))
            .forEach { TopStreamsWidget.updateTopWidget(context, mgr, it) }

        mgr.getAppWidgetIds(ComponentName(context, DStreamWidget::class.java))
            .forEach { DStreamWidget.updateWidget(context, mgr, it) }
    }
}

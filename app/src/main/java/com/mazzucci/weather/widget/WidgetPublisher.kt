package com.mazzucci.weather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.mazzucci.weather.data.WidgetStore
import com.mazzucci.weather.domain.WidgetSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Where the app sends what its first page shows, for the widget. */
interface WidgetPublisher {
    fun publish(snapshot: WidgetSnapshot)

    /** No places at all any more: the widget goes back to "Open the app to choose a place". */
    fun clear()
}

/** Saves the snapshot and redraws any widgets on the home screen (a no-op when there are none). */
class GlanceWidgetPublisher(private val context: Context, private val store: WidgetStore) : WidgetPublisher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun publish(snapshot: WidgetSnapshot) {
        store.save(snapshot)
        redraw()
    }

    override fun clear() {
        store.clear()
        redraw()
    }

    private fun redraw() {
        scope.launch {
            val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(WeatherWidget::class.java) }.getOrDefault(emptyList())
            if (ids.isNotEmpty()) {
                runCatching { WeatherWidget().updateAll(context) }
                WidgetRefreshWorker.schedule(context) // in case the widget predates the worker (e.g. after an update)
            }
        }
    }
}

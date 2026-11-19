package dev.vishnu.wakawidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.vishnu.wakawidget.Stats

object WidgetUpdater {
    /** Writes new stats (if any) and the error line (null clears it) into every placed widget. */
    suspend fun push(context: Context, stats: Stats?, error: String?) {
        val widget = WakaWidget()
        GlanceAppWidgetManager(context).getGlanceIds(WakaWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs ->
                if (stats != null) prefs[WakaWidget.STATS_KEY] = stats.toJson()
                if (error != null) prefs[WakaWidget.ERROR_KEY] = error
                else prefs.remove(WakaWidget.ERROR_KEY)
            }
            widget.update(context, id)
        }
    }
}

package dev.vishnu.wakawidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.vishnu.wakawidget.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The ↻ button on the widget. Fetches right away, like Save in the app does:
 * a queued WorkManager job can be deferred for a long time while the app is in
 * the background, which made the button look dead. Falls back to the worker if
 * the fetch can't finish within the broadcast's time budget.
 */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdater.push(context, null, "Refreshing…")
        // Detached scope: a plain HttpURLConnection call can't be cancelled, so on timeout
        // we stop waiting (and hand over to the worker) instead of blocking on it.
        val fetch = CoroutineScope(Dispatchers.IO).async { RefreshWorker.refresh(context) }
        val done = withTimeoutOrNull(DIRECT_BUDGET_MS) { fetch.await() }
        if (done == null || done == RefreshWorker.Outcome.NETWORK_ERROR) {
            RefreshWorker.refreshNow(context, force = true)
        }
    }

    private companion object {
        const val DIRECT_BUDGET_MS = 8_000L
    }
}

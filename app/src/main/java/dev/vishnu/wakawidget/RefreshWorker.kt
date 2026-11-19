package dev.vishnu.wakawidget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.vishnu.wakawidget.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val apiKey = ApiKeyStore.load(ctx)
        if (apiKey == null) {
            WidgetUpdater.push(ctx, null, "Tap to add your API key")
            return Result.success()
        }
        return try {
            val stats = withContext(Dispatchers.IO) { WakaApi.fetchStats(apiKey) }
            StatsCache.save(ctx, stats)
            WidgetUpdater.push(ctx, stats, null)
            Result.success()
        } catch (e: AuthException) {
            WidgetUpdater.push(ctx, null, "API key rejected. Tap to fix")
            Result.success()
        } catch (e: Exception) {
            WidgetUpdater.push(ctx, null, "Offline. Showing last data")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "wakatime_periodic"
        private const val NOW = "wakatime_now"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Background refresh every 30 minutes. Safe to call repeatedly. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One refresh as soon as there's network. force = restart one already queued. */
        fun refreshNow(context: Context, force: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

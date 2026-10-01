package app.daybreak.widget

import android.content.Context
import android.text.format.DateFormat
import app.daybreak.domain.ClockFormat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.daybreak.data.OpenMeteoApi
import app.daybreak.data.SharedPrefsStore
import app.daybreak.data.UrlConnectionHttpClient
import app.daybreak.data.WidgetStore
import app.daybreak.domain.refreshedSnapshot
import app.daybreak.narration.NarrationInput
import app.daybreak.narration.TemplateNarrator
import java.util.concurrent.TimeUnit

/**
 * Refreshes the widget's numbers and summary every couple of hours while a widget exists: one forecast request for
 * the saved place. Location isn't touched in the background; the place is the one the app last showed.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The last widget may have gone while this was queued: stop for good rather than keep fetching.
        if (GlanceAppWidgetManager(applicationContext).getGlanceIds(WeatherWidget::class.java).isEmpty()) {
            cancel(applicationContext)
            return Result.success()
        }
        // Often a fresh process with no activity: read the phone's clock setting here too.
        ClockFormat.use24Hour = DateFormat.is24HourFormat(applicationContext)
        val store = WidgetStore(SharedPrefsStore(applicationContext, WidgetStore.PREFS_FILE))
        val old = store.load() ?: return Result.success() // nothing to refresh until the app has shown a place
        val forecast = try {
            OpenMeteoApi(UrlConnectionHttpClient()).forecast(old.latitude, old.longitude)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        val summary = TemplateNarrator().describe(NarrationInput(old.placeName, forecast, old.unit), withTotal = false)
        // The app may have published while this was fetching (another place, a newer forecast): theirs wins.
        store.update { current ->
            if (current == null || current.writtenAtMillis != old.writtenAtMillis || current.latitude != old.latitude ||
                current.longitude != old.longitude
            ) null
            else refreshedSnapshot(current, forecast, summary, System.currentTimeMillis())
        }
        WeatherWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(2, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}

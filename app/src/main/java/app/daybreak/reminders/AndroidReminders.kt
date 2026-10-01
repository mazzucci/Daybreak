package app.daybreak.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.daybreak.MainActivity
import app.daybreak.R
import app.daybreak.data.PERSONAL_DATES_KEY
import app.daybreak.data.PRIVATE_PREFS
import app.daybreak.data.SharedPrefsStore
import app.daybreak.data.decodePersonalDates
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.ReminderFire
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The app's side of reminders: the scheduler wired to AlarmManager and notifications, and what the system allows. */
object Reminders {
    const val CHANNEL_ID = "reminders"

    /** Set on the intent a reminder opens the app with, so it shows Home. */
    const val EXTRA_OPEN_HOME = "app.daybreak.OPEN_HOME"

    /** Your dates as they're stored, read fresh each time (the receiver often runs without the app). */
    fun scheduler(context: Context): ReminderScheduler {
        val app = context.applicationContext
        val store = SharedPrefsStore(app, PRIVATE_PREFS)
        return ReminderScheduler(
            store,
            dates = { store.getString(PERSONAL_DATES_KEY)?.let(::decodePersonalDates).orEmpty() },
            alarms = AlarmManagerScheduler(app),
            notifier = NotificationReminderNotifier(app),
        )
    }

    /**
     * Where every run happens: off the main thread (reading the dates and working out two years of reminders isn't
     * free), one at a time and in the order asked (the scheduler's lock serializes them anyway).
     */
    val executor: ExecutorService by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "reminders") } }

    /** Works out the alarm again after something changed (dates edited, the app opened), in the background. */
    fun reschedule(context: Context) {
        val app = context.applicationContext
        executor.execute { run(app, ReminderTrigger.CHANGED) }
    }

    /** One run for [trigger], on the calling thread. */
    fun run(context: Context, trigger: ReminderTrigger) {
        ClockFormat.use24Hour = DateFormat.is24HourFormat(context)
        scheduler(context).run(trigger)
    }

    /** Whether alarms go off on the minute; Android 12 and up can take that away (13 and up: off unless allowed). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Whether a reminder can show: the app's notifications (on 13 and up, the permission) and the channel are on. */
    fun notificationsAllowed(context: Context): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true // made the first time one shows
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Whether to ask for the notification permission: Android 13 and up, not yet granted. */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** The system page for "Alarms & reminders" for this app (Android 12 and up). */
    fun exactAlarmSettings(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }

    /** The app's notification settings. */
    fun notificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminders_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/**
 * One alarm, always the same PendingIntent, so setting it replaces the last. Where exact alarms are allowed it's
 * exact, with an inexact backup for the same moment under another request code: taking exact alarms away cancels
 * every exact one without telling the app (no broadcast), and the backup still comes, a little late. When both
 * arrive, the second finds nothing new (what's been dealt with is kept).
 */
class AlarmManagerScheduler(private val context: Context) : AlarmScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun set(at: Instant) {
        val millis = at.toEpochMilli()
        // Exact where allowed; otherwise the system may deliver it a little late, which the Settings hint explains.
        if (Reminders.canScheduleExact(context)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(EXACT))
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(BACKUP))
                return
            } catch (e: SecurityException) {
                // Taken away between the check and the call: fall through to inexact.
            }
        }
        manager.cancel(pendingIntent(BACKUP))
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pendingIntent(EXACT))
    }

    override fun cancel() {
        manager.cancel(pendingIntent(EXACT))
        manager.cancel(pendingIntent(BACKUP))
    }

    private fun pendingIntent(requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        /** The alarm itself: exact where allowed, else inexact. */
        const val EXACT = 0

        /** The inexact one alongside an exact alarm. */
        const val BACKUP = 1
    }
}

/** A notification on the Reminders channel per date and time round; tapping it opens Home. */
class NotificationReminderNotifier(private val context: Context) : ReminderNotifier {
    override fun show(fire: ReminderFire, text: String, id: Int): Boolean {
        Reminders.ensureChannel(context)
        if (Reminders.needsNotificationPermission(context) || !Reminders.notificationsAllowed(context)) return false
        val open = Intent(context, MainActivity::class.java)
            .putExtra(Reminders.EXTRA_OPEN_HOME, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(fire.date.title)
            .setContentText(text)
            // When the reminder was due, not when a late alarm got round to posting it.
            .setWhen(fire.instant.toEpochMilli())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (e: SecurityException) {
            false // The permission went away between the check and the post: nothing to show it with.
        }
    }
}

/**
 * The alarm, and the system broadcasts after which it must be set again: a restart (alarms don't survive one; some
 * phones say so with QUICKBOOT_POWERON), the clock or time zone changing (times are wall-clock), an app update, and
 * exact alarms being allowed. Not exported: only the system and the app itself can reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = reminderTriggerFor(intent.action) ?: return
        // Off the main thread, keeping the broadcast alive (and the process with it) until the run is done.
        val pending = goAsync()
        val app = context.applicationContext
        Reminders.executor.execute {
            try {
                Reminders.run(app, trigger)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "app.daybreak.action.REMINDER_ALARM"

        /** Some phones (HTC, some Xiaomi) send this instead of BOOT_COMPLETED after a quick restart. */
        const val ACTION_QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}

/** What a broadcast means for the reminders; null for anything else. */
fun reminderTriggerFor(action: String?): ReminderTrigger? = when (action) {
    ReminderReceiver.ACTION_ALARM -> ReminderTrigger.ALARM
    Intent.ACTION_BOOT_COMPLETED, ReminderReceiver.ACTION_QUICKBOOT -> ReminderTrigger.BOOT
    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    -> ReminderTrigger.CHANGED
    else -> null
}

package app.daybreak.reminders

import app.daybreak.data.KeyValueStore
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.ReminderFire
import app.daybreak.domain.fireAt
import app.daybreak.domain.nextReminder
import app.daybreak.domain.reminderFires
import app.daybreak.domain.reminderText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Why the reminders are being looked at. */
enum class ReminderTrigger {
    /** The alarm went off (the exact one or its inexact backup). */
    ALARM,

    /** The phone has just started (and been unlocked): alarms don't survive a restart. */
    BOOT,

    /**
     * Anything else that moves or may have lost the alarm: dates edited, the app opened (a force stop clears
     * alarms) or updated, the clock or time zone changed, exact alarms allowed or taken away.
     */
    CHANGED,
}

/** The one alarm that wakes the app for the next reminder. */
interface AlarmScheduler {
    /** Replaces any alarm already set. */
    fun set(at: Instant)
    fun cancel()
}

/** Shows a reminder: [text] is the line under the date's name ("Tomorrow", "In 15 minutes · 2:00 PM"). */
interface ReminderNotifier {
    /**
     * Posts it as notification [id] (the same for every reminder of a date's time round, so a later one replaces the
     * earlier). False if it couldn't be shown (notifications off, the permission missing).
     */
    fun show(fire: ReminderFire, text: String, id: Int): Boolean
}

/**
 * Keeps one alarm set for the soonest reminder of all your dates, and shows what's due when it goes off.
 *
 * One alarm rather than one per date and reminder: there's nothing to cancel or to keep in step when a date is edited
 * or removed (it's simply worked out again), never a stray alarm for something that's gone, and nowhere near the
 * system's cap on alarms per app. The cost is working out the next one after every change and alarm, which is a few
 * dozen date sums.
 *
 * What's been dealt with is kept in [store]: everything up to the last run (handled, shown or skipped), the alarm
 * that was set (the reminder it promised), the zone it was worked out in, and the reminders that went off in the last
 * few days by date, time round and local time, so a flight west doesn't bring one back. Past reminders are never
 * shown in a burst:
 * - when the alarm goes off or the phone has restarted, those of the last [ALARM_LATE_MAX] (an inexact alarm can be
 *   an hour late, and BOOT_COMPLETED only comes once the phone is unlocked);
 * - on anything else, only the reminders the alarm promised and hasn't delivered yet: the one it was set for and
 *   any since, once its time has passed. One added after its time was never promised, so it's skipped;
 * - whatever the trigger, the promised reminder still shows, however late, while its date hasn't ended (the system
 *   can hold an app's alarms back for hours when it's rarely used);
 * - after a change of zone, a reminder that was still to come where the phone was but has passed where it is now
 *   (flying east) shows once, while its date hasn't ended.
 */
class ReminderScheduler(
    private val store: KeyValueStore,
    private val dates: () -> List<PersonalDate>,
    private val alarms: AlarmScheduler,
    private val notifier: ReminderNotifier,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** A notification id held for a date's time round, until [until] (epoch millis, after the round is over). */
    private data class Slot(val id: Int, val until: Long)

    private data class State(
        /** Everything up to here has been dealt with. Null before the first run. */
        val handledUntil: Instant? = null,
        /** The alarm that's set, if any: the reminder it promises. */
        val alarmAt: Instant? = null,
        /** Reminders that went off (shown or skipped) recently, by [ReminderFire.key], with when. */
        val done: Map<String, Long> = emptyMap(),
        /** The zone the last run worked in, to tell when the phone has changed zone. */
        val zone: String? = null,
        /** Notification ids by [ReminderFire.notificationKey]. */
        val ids: Map<String, Slot> = emptyMap(),
        val nextId: Int = FIRST_ID,
    )

    /**
     * Shows what's due for [trigger], sets the alarm for the next reminder, and returns what was shown.
     *
     * Serialized across every scheduler in the process: the receiver, the app's start and an edit of your dates can
     * each run one on a different thread at the same moment, and two runs reading and writing the state in between
     * each other would show a reminder twice or lose what the other marked done.
     */
    fun run(trigger: ReminderTrigger): List<ReminderFire> = synchronized(LOCK) { runLocked(trigger) }

    private fun runLocked(trigger: ReminderTrigger): List<ReminderFire> {
        val now = now()
        val zone = zone()
        val state = load()
        val dates = dates()
        val today = now.atZone(zone).toLocalDate()
        val since = state.handledUntil ?: now
        val due = reminderFires(dates, since, now, zone).filter { it.key !in state.done }
        val promised = state.alarmAt

        fun ongoing(f: ReminderFire) = !f.occurrence.end.isBefore(today)
        fun recent(f: ReminderFire) = Duration.between(f.instant, now) <= ALARM_LATE_MAX
        // The reminder the alarm was set for; however late it comes, it's still worth showing while the date is on.
        fun promisedOne(f: ReminderFire) = f.instant == promised && ongoing(f)
        val picked = due.filter { f ->
            when (trigger) {
                ReminderTrigger.ALARM, ReminderTrigger.BOOT -> recent(f) || promisedOne(f)
                // The alarm should have gone off and hasn't yet (an inexact one can be late, or the app was opened
                // first): it's replaced below, so deliver what it would have, from its reminder on. Anything earlier
                // was added after its time and was never promised.
                ReminderTrigger.CHANGED ->
                    promised != null && !promised.isAfter(now) && !f.instant.isBefore(promised) && (recent(f) || promisedOne(f))
            }
        }
        val caughtUp = caughtUpAfterZoneChange(state, dates, now, zone).filter(::ongoing)
        val toShow = (picked + caughtUp).distinctBy { it.key }.sortedBy { it.instant }
            // One notification per date and time round: the latest says it best ("Tomorrow" over "In 1 week").
            .groupBy { it.notificationKey }.map { (_, fires) -> fires.last() }

        // Ids first, so they're saved with the rest before anything is posted.
        var nextId = state.nextId
        val ids = state.ids.filterValues { it.until >= now.toEpochMilli() }.toMutableMap()
        val posts = toShow.map { fire ->
            val until = fire.occurrence.end.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val slot = ids[fire.notificationKey]?.let { it.copy(until = maxOf(it.until, until)) }
                ?: Slot(nextId, until).also { nextId = if (nextId == Int.MAX_VALUE) FIRST_ID else nextId + 1 }
            ids[fire.notificationKey] = slot
            fire to slot.id
        }

        val keepFrom = now.minus(DONE_KEPT).toEpochMilli()
        val done = (state.done + (due + caughtUp).associate { it.key to it.instant.toEpochMilli() }).filterValues { it >= keepFrom }
        val next = nextReminder(dates, now, zone, done.keys)
        // The next alarm and what's been dealt with are settled before anything is posted, so a notification that
        // fails can't leave the reminders without an alarm.
        try {
            if (next != null) alarms.set(next.instant) else alarms.cancel()
        } finally {
            save(State(handledUntil = now, alarmAt = next?.instant, done = done, zone = zone.id, ids = ids, nextId = nextId))
        }
        val nowThere = now.atZone(zone)
        return posts.filter { (fire, id) ->
            try {
                notifier.show(fire, reminderText(fire.occurrence, nowThere), id)
            } catch (e: RuntimeException) {
                false // One that can't be posted doesn't keep the rest from showing.
            }
        }.map { it.first }
    }

    /**
     * After the phone changes zone: reminders not yet dealt with whose time was still to come in the old zone (after
     * the last run) but has already passed in the new one, as when flying east past a 9 AM. Empty when the zone
     * hasn't changed.
     */
    private fun caughtUpAfterZoneChange(state: State, dates: List<PersonalDate>, now: Instant, zone: ZoneId): List<ReminderFire> {
        val handled = state.handledUntil ?: return emptyList()
        val old = state.zone?.takeIf { it != zone.id }?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return emptyList()
        return reminderFires(dates, now.minus(DONE_KEPT), now, zone)
            .filter { it.key !in state.done }
            .filter { f -> f.reminder.fireAt(f.occurrence, old)?.toInstant()?.isAfter(handled) == true }
    }

    /** When the alarm is set for, if it is. */
    fun alarmAt(): Instant? = synchronized(LOCK) { load().alarmAt }

    private fun load(): State {
        val raw = store.getString(KEY) ?: return State()
        return try {
            val o = JSONObject(raw)
            val done = o.optJSONObject("done")
            val ids = o.optJSONObject("ids")
            State(
                handledUntil = if (o.has("handledUntil")) Instant.ofEpochMilli(o.getLong("handledUntil")) else null,
                alarmAt = if (o.has("alarmAt")) Instant.ofEpochMilli(o.getLong("alarmAt")) else null,
                done = done?.keys()?.asSequence()?.associateWith { done.getLong(it) }.orEmpty(),
                zone = o.optString("zone").ifBlank { null },
                ids = ids?.keys()?.asSequence()?.associateWith { k -> ids.getJSONArray(k).let { Slot(it.getInt(0), it.getLong(1)) } }.orEmpty(),
                nextId = o.optInt("nextId", FIRST_ID),
            )
        } catch (e: JSONException) {
            State() // Starts again from now: nothing old is shown.
        }
    }

    private fun save(s: State) {
        store.putString(
            KEY,
            JSONObject()
                .putOpt("handledUntil", s.handledUntil?.toEpochMilli())
                .putOpt("alarmAt", s.alarmAt?.toEpochMilli())
                .put("done", JSONObject(s.done.toMap()))
                .putOpt("zone", s.zone)
                .put("ids", JSONObject().apply { s.ids.forEach { (k, v) -> put(k, JSONArray().put(v.id).put(v.until)) } })
                .put("nextId", s.nextId)
                .toString(),
        )
    }

    companion object {
        const val KEY = "reminders_state"

        /** Inexact alarms can be delivered up to an hour late; past two, something else went wrong. */
        val ALARM_LATE_MAX: Duration = Duration.ofHours(2)

        /** Long enough to outlast any change of time zone. */
        val DONE_KEPT: Duration = Duration.ofDays(3)

        /** Reminders' notification ids count up from here (the app posts no other notifications). */
        const val FIRST_ID = 1000

        /** One lock for every scheduler: they share the stored state. */
        private val LOCK = Any()
    }
}

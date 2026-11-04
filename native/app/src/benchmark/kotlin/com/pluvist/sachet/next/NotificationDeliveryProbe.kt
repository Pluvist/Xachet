package com.pluvist.sachet.next

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Framework-only device checks. Never packaged in preview/production or given account data.
 * Run with the benchmark's notification and exact-alarm permissions already granted.
 * The alarm-delivery case uses the real clock: a synthetic third section starts ten
 * minutes and four seconds from now, so its ordinary reminder fires four seconds later.
 */
class NotificationDeliveryProbe : Instrumentation() {
    @Volatile private var phase = "created"
    @Volatile private var probeThread: Thread? = null
    private val finished = AtomicBoolean(false)
    private val finishReturned = AtomicBoolean(false)
    private var caseName = "startup"

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        // Self-instrumentation should not leave adb waiting forever if an OEM
        // framework Binder call or a shared-preference load fails to return.
        Thread({
            SystemClock.sleep(25_000)
            if (!finished.get() && BuildConfig.APPLICATION_ID.endsWith(".benchmark")) {
                // Emit the last known phase before any stack collection, Binder
                // status report, or finish call which might itself be stalled.
                Log.e(PROBE_TAG, "watchdog.timeout phase=$phase")
                // Arm escape before stack collection/status IPC as either may be
                // involved in the framework stall being investigated.
                Thread({
                    SystemClock.sleep(2_000)
                    if (!finishReturned.get()) android.os.Process.killProcess(android.os.Process.myPid())
                }, "CourseProbeFinishEscape").apply { isDaemon = true; start() }
                val result = Bundle().apply {
                    putBoolean("success", false)
                    putBoolean("case_cleanup", false)
                    putBoolean("watchdogTimedOut", true)
                    putString("watchdogPhase", phase)
                    putString("ownThreadStates", ownThreadStates())
                    putString("failure", "Probe exceeded 25 seconds; force-stop the isolated benchmark if cleanup is required")
                }
                Log.e(PROBE_TAG, "timeout phase=$phase threads=${result.getString("ownThreadStates")}")
                runCatching { sendStatus(1, result) }
                // A stuck finishInstrumentation Binder call also has a bounded
                // escape; this process is guarded to the synthetic benchmark UID.
                finishProbe(Activity.RESULT_CANCELED, result)
            }
        }, "CourseProbeWatchdog").apply { isDaemon = true; start() }
        progress("onCreate", status = false)
        start()
    }

    override fun onStart() {
        probeThread = Thread.currentThread()
        progress("onStart.beforeContext")
        val result = Bundle()
        val context = targetContext.applicationContext
        progress("onStart.afterContext")
        if (!BuildConfig.APPLICATION_ID.endsWith(".benchmark") ||
            context.packageName != "com.pluvist.sachet.next.benchmark") {
            result.putString("failure", "Benchmark package required")
            finishProbe(Activity.RESULT_CANCELED, result)
            return
        }
        progress("snapshot.beforePrefs")
        val shared = context.getSharedPreferences("course_widget", Context.MODE_PRIVATE)
        val reminderPrefs = context.getSharedPreferences("course_preclass_notifications", Context.MODE_PRIVATE)
        val oldData = shared.getString("data", null)
        val oldDismissed = shared.getString("liveDismissedDay", null)
        val oldReminderData = reminderPrefs.all.toMap()
        progress("snapshot.afterPrefs")
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        var host: Activity? = null
        var success = false
        try {
            progress("permissions.beforeQueries")
            val allowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                manager.areNotificationsEnabled()
            result.putBoolean("notificationPermission", allowed)
            result.putBoolean("exactAlarmPermission", requireNotNull(context.getSystemService(AlarmManager::class.java)).canScheduleExactAlarms())
            check(allowed) { "Grant benchmark POST_NOTIFICATIONS before running" }
            check(result.getBoolean("exactAlarmPermission")) { "Allow benchmark exact alarms before the bounded delivery test" }
            check(manager.getNotificationChannel(LIVE)?.importance != NotificationManager.IMPORTANCE_NONE) { "Benchmark live channel is blocked" }
            check(manager.getNotificationChannel(REMINDER)?.importance != NotificationManager.IMPORTANCE_NONE) { "Benchmark reminder channel is blocked" }
            progress("permissions.afterQueries")

            progress("host.launch.before")
            host = startActivitySync(Intent(context, NotificationProbeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            progress("host.launch.after")

            caseName = "countdown"
            progress("case.begin")
            reset(context, manager)
            val now = System.currentTimeMillis()
            val midnight = CourseTimeline.dayStart(now)
            val currentStart = (now - 1_000).coerceAtLeast(midnight)
            install(context, true, false, lesson(currentStart, now + 120_000, 3))
            val current = awaitNotification(manager, LIVE) ?: error("Current-class notification missing")
            check(current.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().matches(Regex("[0-9]{2}:[0-9]{2}(?::[0-9]{2})?"))) {
                "Countdown title contains non-numeric content"
            }
            check(current.notification.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrEmpty()) { "Countdown compact content contains a label" }
            check(current.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) { "Current class is not ongoing" }
            result.putBoolean("case_countdown", true)
            result.putBoolean("promotionSupported", CourseLiveActivity.supportsPromotion())
            result.putBoolean("promotedOnThisSystem", android.os.Build.VERSION.SDK_INT >= 36 &&
                current.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0)
            progress("case.passed")

            caseName = "waiting"
            progress("case.begin")
            reset(context, manager)
            val waitingNow = System.currentTimeMillis()
            val tomorrow = CourseTimeline.resolve(waitingNow, emptyList()).tomorrow
            val waitingStart = minOf(waitingNow + 120_000, tomorrow - 1_000)
            if (waitingStart > waitingNow + 1_000) {
                install(context, true, false, lesson(waitingStart, waitingStart + 45 * 60_000, 3))
                val waiting = awaitNotification(manager, LIVE) ?: error("Waiting-class notification missing")
                val title = waiting.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
                val body = waiting.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
                check(title.contains(time(waitingStart)) && title.contains(PLACE)) { "Waiting title must contain class time and place" }
                check(body.contains(TITLE) && body.contains("45分钟")) { "Waiting content must contain course and duration" }
                result.putBoolean("case_waiting", true)
            } else result.putString("case_waiting", "skipped_at_midnight_boundary")
            progress("case.passed")

            caseName = "reminder"
            progress("case.begin")
            reset(context, manager)
            val reminderNow = System.currentTimeMillis()
            val reminderAt = reminderNow + 4_000
            val classStart = reminderAt + 10 * 60_000
            install(context, false, true, lesson(classStart, classStart + 45 * 60_000, 3))
            progress("reminder.status.before")
            val reminderStatus = CourseReminder.status(context)
            progress("reminder.status.after")
            check(reminderStatus["scheduledAt"] == reminderAt) { "Reminder deadline did not match actual class start minus ten minutes" }
            result.putBoolean("case_reminder_schedule", true)
            result.putLong("reminderScheduledInMs", reminderAt - System.currentTimeMillis())
            progress("reminder.liveDisabledQuery.before")
            check(active(manager, LIVE).isEmpty()) { "Live opt-out did not remain independent" }
            progress("reminder.liveDisabledQuery.after")
            progress("reminder.await.before")
            val reminder = awaitNotification(manager, REMINDER, 8_000) ?: error("Natural reminder alarm did not deliver within eight seconds")
            progress("reminder.await.after")
            val reminderText = reminder.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
            check(reminder.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString().contains("分钟后上课")) { "Reminder title missing" }
            check(reminderText.contains(TITLE) && reminderText.contains(PLACE) && reminderText.contains(time(classStart))) { "Reminder content missing synthetic course fields" }
            result.putBoolean("case_reminder_delivery", true)
            result.putBoolean("case_reminder_independent", active(manager, LIVE).isEmpty())
            result.putLong("reminderDeliveryLatencyMs", reminder.postTime - reminderAt)
            repeat(3) {
                progress("dedup.refresh.$it.before")
                CourseLiveActivity.refresh(context)
                progress("dedup.refresh.$it.after")
            }
            val refreshed = active(manager, REMINDER)
            check(refreshed.size == 1 && refreshed.single().postTime == reminder.postTime) { "Reconciliation emitted the reminder again" }
            check(JSONObject(reminderPrefs.getString("delivered", "{}") ?: "{}").length() == 1) { "Reminder de-duplication record mismatch" }
            result.putBoolean("case_reminder_dedup", true)
            progress("case.passed")

            caseName = "completed"
            progress("case.begin")
            reset(context, manager)
            val completedNow = System.currentTimeMillis()
            val completedEnd = completedNow - 1_000
            val completedStart = maxOf(CourseTimeline.dayStart(completedNow), completedEnd - 60_000)
            if (completedEnd > completedStart) {
                install(context, true, false, lesson(completedStart, completedEnd, 3))
                val completed = awaitNotification(manager, LIVE) ?: error("Completed-day notification missing")
                check(completed.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() == "课程已完成") { "Completed-day title mismatch" }
                check(completed.notification.flags and Notification.FLAG_ONGOING_EVENT == 0) { "Completed notification must be dismissible" }
                result.putBoolean("case_completed", true)
            } else result.putString("case_completed", "skipped_at_midnight_boundary")
            progress("case.passed")

            caseName = "noCourses"
            progress("case.begin")
            reset(context, manager)
            val nextDay = CourseTimeline.resolve(System.currentTimeMillis(), emptyList()).tomorrow
            install(context, true, false, lesson(nextDay + 12 * 60 * 60_000, nextDay + 13 * 60 * 60_000, 5))
            SystemClock.sleep(200)
            check(active(manager, LIVE).isEmpty()) { "Class-free day created a live activity" }
            result.putBoolean("case_no_courses", true)
            progress("case.passed")
            success = true
        } catch (error: Exception) {
            // Only synthetic-fixture assertions/permission errors are reported. Never
            // dump notification extras, all shared preferences, or account contents.
            result.putString("failure", "${error.javaClass.simpleName}: ${error.message}")
        } finally {
            try {
                caseName = "cleanup"
                progress("case.begin")
                reset(context, manager)
                result.putBoolean("case_cleanup", manager.activeNotifications.isEmpty() &&
                    (CourseReminder.status(context)["scheduledAt"] as Long) == 0L)
                val editor = shared.edit()
                if (oldData == null) editor.remove("data") else editor.putString("data", oldData)
                if (oldDismissed == null) editor.remove("liveDismissedDay") else editor.putString("liveDismissedDay", oldDismissed)
                check(editor.commit())
                val oldReminders = reminderPrefs.edit().clear()
                for ((key, value) in oldReminderData) when (value) {
                    is String -> oldReminders.putString(key, value)
                    is Long -> oldReminders.putLong(key, value)
                    is Int -> oldReminders.putInt(key, value)
                    is Boolean -> oldReminders.putBoolean(key, value)
                    is Float -> oldReminders.putFloat(key, value)
                }
                check(oldReminders.commit())
                progress("case.passed")
            } catch (error: Exception) {
                success = false
                result.putBoolean("case_cleanup", false)
                result.putString("cleanupFailure", error.javaClass.simpleName)
            }
            progress("host.finish.before")
            runOnMainSync { host?.finish() }
            progress("host.finish.after")
        }
        result.putBoolean("success", success && result.getBoolean("case_cleanup"))
        progress("finish.before")
        finishProbe(if (result.getBoolean("success")) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun install(context: Context, live: Boolean, reminders: Boolean, vararg lessons: JSONObject) {
        val snapshot = JSONObject().put("ready", true).put("liveActivityEnabled", live).put("reminderEnabled", reminders)
            .put("lessons", JSONArray(lessons.toList()))
        progress("install.prefs.before")
        check(context.getSharedPreferences("course_widget", Context.MODE_PRIVATE).edit()
            .remove("liveDismissedDay").putString("data", snapshot.toString()).commit())
        progress("install.prefs.after")
        progress("install.refresh.before")
        CourseLiveActivity.refresh(context)
        progress("install.refresh.after")
    }

    private fun reset(context: Context, manager: NotificationManager) {
        val disabled = JSONObject().put("ready", false).put("liveActivityEnabled", false).put("reminderEnabled", false)
        progress("reset.prefs.before")
        check(context.getSharedPreferences("course_widget", Context.MODE_PRIVATE).edit()
            .remove("liveDismissedDay").putString("data", disabled.toString()).commit())
        progress("reset.prefs.after")
        progress("reset.refresh.before")
        CourseLiveActivity.refresh(context)
        progress("reset.refresh.after")
        progress("reset.stopService.before")
        context.stopService(Intent(context, CourseCountdownService::class.java))
        progress("reset.stopService.after")
        progress("reset.cancelNotifications.before")
        manager.cancelAll()
        // Let a previously started countdown detach before the following fixture.
        SystemClock.sleep(250)
        manager.cancelAll()
        progress("reset.cancelNotifications.after")
        progress("reset.reminderPrefs.before")
        context.getSharedPreferences("course_preclass_notifications", Context.MODE_PRIVATE).edit().clear().commit()
        progress("reset.reminderPrefs.after")
    }

    private fun lesson(start: Long, end: Long, section: Int) = JSONObject()
        .put("title", TITLE).put("place", PLACE).put("start", start).put("end", end).put("section", section).put("group", "synthetic-only")

    private fun active(manager: NotificationManager, channel: String): List<StatusBarNotification> {
        progress("active.$channel.before", status = false)
        val result = manager.activeNotifications.filter { it.notification.channelId == channel }
        progress("active.$channel.after", status = false)
        return result
    }

    private fun awaitNotification(manager: NotificationManager, channel: String, timeout: Long = 2_000): StatusBarNotification? {
        val deadline = SystemClock.elapsedRealtime() + timeout
        do {
            active(manager, channel).firstOrNull()?.let { return it }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        return null
    }

    private fun time(value: Long) = SimpleDateFormat("HH:mm", Locale.ROOT).format(value)

    private fun progress(value: String, status: Boolean = true) {
        phase = "$caseName.$value"
        Log.i(PROBE_TAG, phase)
        if (status) sendStatus(0, Bundle().apply { putString("phase", phase) })
    }

    private fun ownThreadStates(): String = Thread.getAllStackTraces().entries
        .filter { (thread, frames) -> thread === probeThread || frames.any { it.className.startsWith("com.pluvist.sachet.next.") } }
        .joinToString("; ") { (thread, frames) ->
            val own = frames.filter { it.className.startsWith("com.pluvist.sachet.next.") }.take(12)
                .joinToString(",") { "${it.className}.${it.methodName}:${it.lineNumber}" }
            "${thread.name}:${thread.state}[$own]"
        }

    private fun finishProbe(code: Int, result: Bundle) {
        if (finished.compareAndSet(false, true)) {
            finish(code, result)
            finishReturned.set(true)
        }
    }

    private companion object {
        const val LIVE = "course_live_activity"
        const val REMINDER = "course_preclass_reminders"
        const val TITLE = "通知验证课程"
        const val PLACE = "测试教学楼101"
        const val PROBE_TAG = "CourseNotificationProbe"
    }
}

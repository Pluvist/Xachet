package com.pluvist.sachet.next

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** One next-deadline alarm, re-armed from persisted lessons after each delivery. */
object CourseReminder {
    internal const val FIRE = "com.pluvist.xachet.COURSE_REMINDER"
    private const val CHANNEL = "course_preclass_reminders"
    private const val TAG = "course_preclass_reminder"
    private const val ID = -1959
    private const val REQUEST = 9911
    private fun prefs(context: Context) = context.getSharedPreferences("course_preclass_notifications", Context.MODE_PRIVATE)
    private fun manager(context: Context) = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private fun alarm(context: Context) = requireNotNull(context.getSystemService(AlarmManager::class.java))
    private fun alarmIntent(context: Context, key: String = "", at: Long = 0) = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, CourseReminderReceiver::class.java).setAction(FIRE).putExtra("key", key).putExtra("at", at),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelAlarm(context: Context) {
        // Lookup must not rewrite extras on a broadcast already dispatched by Android.
        val pending = PendingIntent.getBroadcast(context, REQUEST,
            Intent(context, CourseReminderReceiver::class.java).setAction(FIRE),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarm(context).cancel(pending)
        pending.cancel()
    }

    fun notificationsAllowed(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            manager(context).areNotificationsEnabled() &&
            manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun status(context: Context): Map<String, Any> = mapOf(
        "enabled" to CourseLiveActivity.data(context).optBoolean("reminderEnabled", true),
        "notifications" to notificationsAllowed(context),
        "exactAlarms" to alarm(context).canScheduleExactAlarms(),
        "scheduledAt" to prefs(context).getLong("at", 0))

    private fun delivered(context: Context, now: Long): JSONObject {
        val old = runCatching { JSONObject(prefs(context).getString("delivered", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val recent = JSONObject()
        old.keys().forEach { key -> if (old.optLong(key) >= now - 2 * 86_400_000L) recent.put(key, old.optLong(key)) }
        return recent
    }

    @Synchronized fun refresh(context: Context) {
        val now = System.currentTimeMillis()
        val data = CourseLiveActivity.data(context)
        val stored = prefs(context)
        if (!data.optBoolean("ready") || !data.optBoolean("reminderEnabled", true) || !notificationsAllowed(context)) {
            stored.edit().remove("key").remove("at").apply()
            cancelAlarm(context)
            manager(context).cancel(TAG, ID)
            return
        }
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "课前提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "普通节次提前10分钟，第1、5节提前20分钟；采用教学楼实际作息"
            enableVibration(true); setShowBadge(false)
        })
        val sent = delivered(context, now)
        val events = CourseReminderPlan.events(CourseTimeline.lessons(data))
        // A live-state boundary and its reminder can share an instant. Reconcile the
        // already-armed batch before replacing its alarm, whichever receiver wins.
        val due = CourseReminderPlan.due(now, stored.getString("key", "").orEmpty(), stored.getLong("at", 0),
            events, sent.keys().asSequence().toSet())
        if (due.isNotEmpty()) notifyDue(context, due, sent, now)
        val next = CourseReminderPlan.next(now, events, sent.keys().asSequence().toSet())
        val edit = stored.edit().putString("delivered", sent.toString())
        if (next == null) {
            edit.remove("key").remove("at").apply()
            cancelAlarm(context)
            return
        }
        if (!edit.putString("key", next.key).putLong("at", next.triggerAt).commit()) return
        val pending = alarmIntent(context, next.key, next.triggerAt)
        // Scheduling with the same PendingIntent replaces its existing alarm. Keep
        // that alarm alive until the replacement is accepted by the system.
        try {
            if (alarm(context).canScheduleExactAlarms())
                alarm(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pending)
            else alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pending)
        } catch (_: SecurityException) {
            // Permissions can change between query and Binder call. Preserve a best-effort
            // reminder without claiming exact delivery; late alarms never notify after class starts.
            alarm(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.triggerAt, pending)
        }
    }

    @Synchronized internal fun deliver(context: Context, key: String?, at: Long) {
        val stored = prefs(context)
        if (key.isNullOrBlank() || key != stored.getString("key", "") || at != stored.getLong("at", 0)) return
        refresh(context)
    }

    private fun notifyDue(context: Context, due: List<CourseReminderEvent>, sent: JSONObject, now: Long) {
        due.forEach { sent.put(it.key, it.lesson.start) }
        // Persist before notifying, so process restart/broadcast duplication cannot alert twice.
        if (!prefs(context).edit().putString("delivered", sent.toString()).remove("key").remove("at").commit()) return
        val format = SimpleDateFormat("HH:mm", Locale.ROOT)
        val remaining = ((due.minOf { it.lesson.start } - now + 59_999L) / 60_000L).coerceAtLeast(1)
        val lines = due.map {
            val section = if (it.section > 0) " · 第${it.section}节" else ""
            "${it.lesson.title}$section\n${format.format(it.lesson.start)} · ${it.lesson.place}"
        }
        val open = PendingIntent.getActivity(context, 9912,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_course_live).setColor(0xff3482ff.toInt())
            .setCategory(Notification.CATEGORY_REMINDER).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentTitle("${remaining}分钟后上课")
            .setContentText(due.joinToString("；") { "${it.lesson.title} · ${format.format(it.lesson.start)} · ${it.lesson.place}" })
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n\n")))
            .setContentIntent(open).setAutoCancel(true).setShowWhen(false)
            .setTimeoutAfter(due.maxOf { it.lesson.start } - now).build()
        try { manager(context).notify(TAG, ID, notification) } catch (_: SecurityException) { }
    }
}

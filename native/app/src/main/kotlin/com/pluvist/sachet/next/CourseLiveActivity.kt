package com.pluvist.sachet.next

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

object CourseLiveActivity {
    const val REFRESH = "com.pluvist.xachet.LIVE_REFRESH"
    const val DISMISS = "com.pluvist.xachet.LIVE_DISMISS"
    internal const val CHANNEL = "course_live_activity"
    private const val TAG = "course_live_activity"
    internal const val ID = -1958
    private fun prefs(context: Context) = context.getSharedPreferences("course_widget", Context.MODE_PRIVATE)
    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    private fun alarm(context: Context): AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context, 9901,
        Intent(context, CourseLiveActivityReceiver::class.java).setAction(REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun dayKey(now: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now)
    private fun time(time: Long) = SimpleDateFormat("HH:mm", Locale.ROOT).format(time)

    internal fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = manager(context) ?: return
        runCatching {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "课程实时活动", NotificationManager.IMPORTANCE_LOW).apply {
                description = "同步主页课程状态、下课倒计时和下一节课地点"; setSound(null, null)
                enableVibration(false); setShowBadge(false)
            })
        }
    }

    internal fun buildNotificationBuilder(
        context: Context,
        state: LiveCourseState,
        now: Long = System.currentTimeMillis(),
    ): Notification.Builder {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            9903,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pres = CourseTimeline.present(
            state = state,
            now = now,
            focusLiveActive = FocusLiveActivity.isActive(context) &&
                !FocusLiveActivity.supportsMultiplePromotedLiveActivities(),
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        builder.setSmallIcon(R.drawable.ic_course_live)
            .setColor(0xff3482ff.toInt())
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_EVENT)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(pres.ongoing)
            .setAutoCancel(!pres.ongoing)
            .setShowWhen(false)
            .setContentTitle(pres.title)
            .setContentText(pres.content)
            .setStyle(Notification.BigTextStyle().setBigContentTitle(pres.title).bigText(pres.content))
        if (pres.hasProgress) {
            builder.setProgress(pres.progressMax, pres.progressCurrent, false)
        }
        if (pres.shortText.isNotBlank()) {
            setShortText(builder, pres.shortText)
        }
        if (state.phase == LiveCoursePhase.COMPLETED && Build.VERSION.SDK_INT >= 26) {
            builder.setTimeoutAfter((state.tomorrow - now).coerceAtLeast(1000L))
        }
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching { builder.setRequestPromotedOngoing(pres.requestPromoted) }
        }
        return builder
    }

    internal fun buildFallbackForegroundNotification(context: Context): Notification {
        ensureChannel(context)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        return builder.setSmallIcon(R.drawable.ic_course_live)
            .setContentTitle("课程实时状态")
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun data(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString("data", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    internal fun notificationsAllowedFor(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            runCatching { context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false).not()
        ) return false
        val nm = manager(context) ?: return false
        if (Build.VERSION.SDK_INT >= 24 && runCatching { !nm.areNotificationsEnabled() }.getOrDefault(true)) return false
        if (Build.VERSION.SDK_INT >= 26 && runCatching { nm.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE }.getOrDefault(false)) return false
        return true
    }

    fun notificationsAllowed(context: Context): Boolean = notificationsAllowedFor(context, CHANNEL)
    fun hasToday(context: Context): Boolean {
        val data = data(context)
        return data.optBoolean("liveActivityEnabled") && data.optBoolean("ready") &&
            CourseTimeline.resolve(System.currentTimeMillis(), CourseTimeline.lessons(data)).phase != LiveCoursePhase.NONE
    }
    fun supportsPromotion(): Boolean = Build.VERSION.SDK_INT >= 36
    fun status(context: Context): Map<String, Any> {
        val nm = manager(context)
        val am = alarm(context)
        val promoted = Build.VERSION.SDK_INT >= 36 && nm != null &&
            runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
        val exactAlarms = Build.VERSION.SDK_INT < 31 ||
            runCatching { am?.canScheduleExactAlarms() == true }.getOrDefault(false)
        return mapOf(
            "supported" to supportsPromotion(),
            "notifications" to notificationsAllowed(context),
            "promoted" to promoted,
            "exactAlarms" to exactAlarms,
        )
    }

    @Synchronized fun dismissToday(context: Context, day: String?) {
        prefs(context).edit().putString("liveDismissedDay", day ?: dayKey(System.currentTimeMillis())).commit()
        refresh(context)
    }

    @Synchronized fun refresh(context: Context) {
        // Reminders remain independent of the live-activity toggle and of today's
        // live-card dismissal, while sharing the same offline lesson snapshot.
        try { CourseReminder.refresh(context) }
        catch (error: RuntimeException) { android.util.Log.w("CourseReminder", "Unable to reconcile reminders", error) }
        val now = System.currentTimeMillis()
        val data = data(context)
        val manager = manager(context) ?: return
        val alarm = alarm(context)
        // Migrate the earlier tagged notification to the single service notification.
        runCatching { manager.cancel(TAG, ID) }
        if (!data.optBoolean("liveActivityEnabled") || !data.optBoolean("ready") || !notificationsAllowed(context)) {
            stopCountdown(context); runCatching { manager.cancel(ID) }; runCatching { alarm?.cancel(alarmIntent(context)) }; return
        }
        val lessons = CourseTimeline.lessons(data)
        val state = CourseTimeline.resolve(now, lessons)
        val dismissed = prefs(context).getString("liveDismissedDay", "") == dayKey(now)
        if (dismissed || state.phase == LiveCoursePhase.NONE) {
            stopCountdown(context)
            runCatching { manager.cancel(ID) }
        } else {
            val builder = buildNotificationBuilder(context, state, now)
            val notification = builder.build()
            try { manager.notify(ID, notification) } catch (_: RuntimeException) { }
            val needsLiveTick = state.phase == LiveCoursePhase.IN_CLASS ||
                (state.phase == LiveCoursePhase.WAITING && CourseTimeline.promote(state, now))
            if (needsLiveTick) {
                startCountdown(context)
            } else {
                stopCountdown(context)
            }
        }
        val trigger = CourseTimeline.nextWake(now, lessons, state, dismissed)
        if (alarm != null) {
            runCatching { alarm.cancel(alarmIntent(context)) }
            if (trigger != null && trigger > now) {
                try {
                    if (Build.VERSION.SDK_INT >= 31 && !runCatching { alarm.canScheduleExactAlarms() }.getOrDefault(false))
                        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context))
                    else if (Build.VERSION.SDK_INT >= 23)
                        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context))
                    else alarm.setExact(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context))
                } catch (_: SecurityException) {
                    runCatching { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmIntent(context)) }
                } catch (_: RuntimeException) { }
            }
        }
    }
    private fun isAppInForeground(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val processes = runCatching { am.runningAppProcesses }.getOrNull() ?: return false
        val pkg = context.packageName
        return processes.any {
            it.processName == pkg &&
                it.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
        }
    }
    private fun startCountdown(context: Context) {
        if (!isAppInForeground(context)) return
        val intent = Intent(context, CourseCountdownService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        } catch (error: RuntimeException) {
            // Systems may restrict background starts (e.g. during permission overlay dismissal).
            // The live notification remains posted without crashing.
            android.util.Log.w("CourseActivity", "Countdown service start restricted", error)
        }
    }
    private fun stopCountdown(context: Context) {
        runCatching { context.stopService(Intent(context, CourseCountdownService::class.java)) }
    }
    internal fun activeDeadline(context: Context): Long? {
        val now = System.currentTimeMillis()
        val data = data(context)
        if (!data.optBoolean("liveActivityEnabled") || !data.optBoolean("ready") || !notificationsAllowed(context) ||
            prefs(context).getString("liveDismissedDay", "") == dayKey(now)) return null
        val state = CourseTimeline.resolve(now, CourseTimeline.lessons(data))
        return when {
            state.phase == LiveCoursePhase.IN_CLASS -> state.current?.end
            state.phase == LiveCoursePhase.WAITING && CourseTimeline.promote(state, now) -> state.next?.start
            else -> null
        }
    }
    /** A foreground notification can only be cancelled after the service detaches. */
    @Synchronized internal fun cleanupCountdown(context: Context) {
        val now = System.currentTimeMillis()
        val data = data(context)
        if (!data.optBoolean("liveActivityEnabled") || !data.optBoolean("ready") || !notificationsAllowed(context) ||
            prefs(context).getString("liveDismissedDay", "") == dayKey(now) ||
            CourseTimeline.resolve(now, CourseTimeline.lessons(data)).phase == LiveCoursePhase.NONE) {
            runCatching { manager(context)?.cancel(ID) }
        }
    }
    private fun setShortText(builder: Notification.Builder, text: String) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching { builder.setShortCriticalText(text) }
    }
}

internal data class FocusLiveActionSpec(
    val label: String,
    val action: String,
    val requestCode: Int,
)

/**
 * Manages the Tomato Focus Live Activity (Pomodoro & Long Timer).
 * When the system does not support concurrent promoted Live Activities alongside the course
 * Live Activity, posting an active Tomato Focus Live Activity temporarily demotes
 * [CourseLiveActivity] to a regular notification and restores it when focus stops.
 */
internal object FocusLiveActivity {
    internal const val CHANNEL = "focus_live_activity"
    internal const val ID = -1959
    const val ACTION_STOP = "com.pluvist.xachet.FOCUS_STOP"
    const val ACTION_SKIP = "com.pluvist.xachet.FOCUS_SKIP"
    const val ACTION_PAUSE = "com.pluvist.xachet.FOCUS_PAUSE"

    @Volatile
    private var activeFlag: Boolean = false

    @Volatile
    private var controllerRef: java.lang.ref.WeakReference<FocusTimerController>? = null

    fun bindController(controller: FocusTimerController) {
        controllerRef = java.lang.ref.WeakReference(controller)
    }

    fun isActive(context: Context? = null): Boolean = activeFlag

    fun supportsMultiplePromotedLiveActivities(): Boolean = false

    internal fun actionsFor(state: FocusUiState): List<FocusLiveActionSpec> = when (state.mode) {
        FocusTimerMode.Pomodoro -> listOf(
            FocusLiveActionSpec(label = "停止", action = ACTION_STOP, requestCode = 9911),
            FocusLiveActionSpec(label = "跳过", action = ACTION_SKIP, requestCode = 9912),
        )
        FocusTimerMode.LongTimer -> listOf(
            FocusLiveActionSpec(
                label = if (state.isRunning) "暂停" else "继续",
                action = ACTION_PAUSE,
                requestCode = 9913,
            ),
            FocusLiveActionSpec(label = "停止", action = ACTION_STOP, requestCode = 9911),
        )
    }

    fun handleAction(context: Context, action: String) {
        val controller = controllerRef?.get()
        if (controller == null) {
            if (action == ACTION_STOP) {
                activeFlag = false
                runCatching {
                    (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(ID)
                }
                CourseLiveActivity.refresh(context.applicationContext)
            }
            return
        }
        when (action) {
            ACTION_STOP -> controller.handleLiveActionStop()
            ACTION_SKIP -> controller.handleLiveActionSkip()
            ACTION_PAUSE -> controller.handleLiveActionPauseOrResume()
        }
    }

    fun present(state: FocusUiState): CourseLivePresentation {
        val clockText = FocusFormatters.clock(state.displaySeconds)
        val modeTitle = if (state.mode == FocusTimerMode.Pomodoro) state.phase.label else "长计时"
        val prefix = if (state.isRunning) "" else "已暂停 · "
        val title = "$prefix$modeTitle · $clockText"
        val content = if (state.mode == FocusTimerMode.Pomodoro) {
            "${state.selectedTag} · 第 ${state.currentRound}/${state.settings.roundsPerCycle} 轮"
        } else {
            "${state.selectedTag} · 正向累计专注"
        }
        val max = if (state.mode == FocusTimerMode.Pomodoro) {
            state.targetSeconds.toInt().coerceAtLeast(1)
        } else {
            3600
        }
        val cur = if (state.mode == FocusTimerMode.Pomodoro) {
            state.elapsedSeconds.toInt().coerceIn(0, max)
        } else {
            (state.elapsedSeconds % 3600L).toInt().coerceIn(0, max)
        }
        return CourseLivePresentation(
            title = title,
            content = content,
            shortText = clockText,
            progressMax = max,
            progressCurrent = cur,
            hasProgress = true,
            requestPromoted = state.hasActiveProgress,
            ongoing = state.hasActiveProgress,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "番茄专注实时活动", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "显示番茄钟与长计时实时专注进度与倒计时"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }
    }

    internal fun buildNotificationBuilder(context: Context, state: FocusUiState): Notification.Builder {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        val open = PendingIntent.getActivity(
            appContext,
            9905,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pres = present(state)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(appContext, CHANNEL) else Notification.Builder(appContext)
        builder.setSmallIcon(R.drawable.ic_focus_live)
            .setColor(0xff9f181e.toInt())
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOngoing(pres.ongoing)
            .setAutoCancel(!pres.ongoing)
            .setShowWhen(false)
            .setContentTitle(pres.title)
            .setContentText(pres.content)
            .setStyle(Notification.BigTextStyle().setBigContentTitle(pres.title).bigText(pres.content))
            .setProgress(pres.progressMax, pres.progressCurrent, false)
        actionsFor(state).forEach { spec ->
            val pending = PendingIntent.getBroadcast(
                appContext,
                spec.requestCode,
                Intent(appContext, CourseLiveActivityReceiver::class.java).setAction(spec.action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                Notification.Action.Builder(null as android.graphics.drawable.Icon?, spec.label, pending).build(),
            )
        }
        if (Build.VERSION.SDK_INT >= 36) {
            runCatching { builder.setShortCriticalText(pres.shortText) }
            runCatching { builder.setRequestPromotedOngoing(pres.requestPromoted) }
        }
        return builder
    }

    @Synchronized
    fun sync(context: Context, state: FocusUiState) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val shouldPromote = state.hasActiveProgress
        val prevPromote = activeFlag
        activeFlag = shouldPromote

        if (!state.hasActiveProgress) {
            runCatching { manager.cancel(ID) }
            if (prevPromote) {
                CourseLiveActivity.refresh(appContext)
            }
            return
        }

        if (!CourseLiveActivity.notificationsAllowedFor(appContext, CHANNEL)) {
            if (prevPromote != shouldPromote) {
                CourseLiveActivity.refresh(appContext)
            }
            return
        }

        val builder = buildNotificationBuilder(appContext, state)
        try {
            manager.notify(ID, builder.build())
        } catch (_: RuntimeException) { }

        if (prevPromote != shouldPromote && !supportsMultiplePromotedLiveActivities()) {
            CourseLiveActivity.refresh(appContext)
        }
    }
}

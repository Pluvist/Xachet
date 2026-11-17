package com.pluvist.sachet.next

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import java.util.concurrent.Executors

/** Keeps the live course notification title, progress bar, and short badge current while a lesson or promoted break is active. */
class CourseCountdownService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private var lessons: List<LiveLesson> = emptyList()
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as? PowerManager
    }
    private var registered = false
    private val screens = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { schedule() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (deadline <= now) {
                handler.removeCallbacks(this)
                val app = applicationContext
                worker.execute { CourseLiveActivity.refresh(app) }
                return
            }
            val state = CourseTimeline.resolve(now, lessons)
            val currentDeadline = when {
                state.phase == LiveCoursePhase.IN_CLASS -> state.current?.end ?: 0L
                state.phase == LiveCoursePhase.WAITING && CourseTimeline.promote(state, now) -> state.next?.start ?: 0L
                else -> 0L
            }
            if (currentDeadline <= now) {
                handler.removeCallbacks(this)
                val app = applicationContext
                worker.execute { CourseLiveActivity.refresh(app) }
                return
            }
            deadline = currentDeadline
            val interactive = powerManager?.isInteractive != false
            if (interactive) {
                val updated = CourseLiveActivity.buildNotificationBuilder(this@CourseCountdownService, state, now).build()
                try {
                    notificationManager?.notify(CourseLiveActivity.ID, updated)
                } catch (_: RuntimeException) {
                    stopSelf()
                    return
                }
            }
            handler.postDelayed(this, if (interactive) 1000 - now % 1000 else (deadline - now).coerceAtLeast(1000L))
        }
    }
    private fun schedule() { handler.removeCallbacks(tick); handler.post(tick) }
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screens, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screens, filter)
            registered = true
        }
    }
    private fun enterForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(CourseLiveActivity.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(CourseLiveActivity.ID, notification)
        }
        true
    } catch (error: RuntimeException) {
        android.util.Log.w("CourseActivity", "Foreground countdown unavailable", error)
        false
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val now = System.currentTimeMillis()
        val resolvedDeadline = CourseLiveActivity.activeDeadline(this)
        val parsedLessons = CourseTimeline.lessons(CourseLiveActivity.data(this))
        val state = CourseTimeline.resolve(now, parsedLessons)
        val valid = resolvedDeadline != null && resolvedDeadline > now &&
            (state.phase == LiveCoursePhase.IN_CLASS || (state.phase == LiveCoursePhase.WAITING && CourseTimeline.promote(state, now)))

        if (!valid) {
            // Android 12+ throws ForegroundServiceDidNotStartInTimeException if stopSelf() is called
            // after startForegroundService() without first invoking startForeground().
            if (enterForeground(CourseLiveActivity.buildFallbackForegroundNotification(this))) {
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val builder = CourseLiveActivity.buildNotificationBuilder(this, state, now)
        if (!enterForeground(builder.build())) {
            stopSelf()
            return START_NOT_STICKY
        }
        lessons = parsedLessons
        deadline = resolvedDeadline
        schedule()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (registered) runCatching { unregisterReceiver(screens) }
        if (Build.VERSION.SDK_INT >= 24) runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
        else @Suppress("DEPRECATION") runCatching { stopForeground(false) }
        val app = applicationContext
        worker.execute { CourseLiveActivity.cleanupCountdown(app) }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}

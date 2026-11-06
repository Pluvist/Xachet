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

/** Keeps the numeric notification title current while a lesson is running.
 * The native chronometer still drives the system chip. No wake lock or ticking
 * alarm is used, and screen-off pauses title updates until wake/class end. */
class CourseCountdownService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var end = 0L
    private var notificationBuilder: Notification.Builder? = null
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private var registered = false
    private val screens = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { schedule() }
    }
    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (end <= now) {
                handler.removeCallbacks(this)
                val app = applicationContext
                worker.execute { CourseLiveActivity.refresh(app) }
                return
            }
            val interactive = powerManager.isInteractive
            if (interactive) notificationBuilder?.let { builder ->
                val updated = builder.setContentTitle(CourseTimeline.countdown(end - now)).build()
                try {
                    notificationManager.notify(CourseLiveActivity.ID, updated)
                } catch (_: SecurityException) { stopSelf(); return }
            }
            handler.postDelayed(this, if (interactive) 1000 - now % 1000 else end - now)
        }
    }
    private fun schedule() { handler.removeCallbacks(tick); handler.post(tick) }
    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screens, filter, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screens, filter)
        registered = true
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val suppliedEnd = intent?.getLongExtra("end", 0L) ?: 0L
        val supplied = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("notification", Notification::class.java)
            else @Suppress("DEPRECATION") intent?.getParcelableExtra<Notification>("notification")
        // Reject a queued start after the user disabled/dismissed the activity.
        if (supplied == null || suppliedEnd <= System.currentTimeMillis() || CourseLiveActivity.activeEnd(this) != suppliedEnd) {
            stopSelf(); return START_NOT_STICKY
        }
        end = suppliedEnd
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(CourseLiveActivity.ID, supplied, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(CourseLiveActivity.ID, supplied)
        } catch (error: RuntimeException) {
            android.util.Log.w("CourseActivity", "Foreground countdown unavailable", error)
            stopSelf(); return START_NOT_STICKY
        }
        // Recover immutable notification details only when the source changes.
        // The one-second tick then changes just the numeric title.
        notificationBuilder = (if (Build.VERSION.SDK_INT >= 24) Notification.Builder.recoverBuilder(this, supplied)
            else Notification.Builder(this).setSmallIcon(R.drawable.ic_course_live)
                .setOngoing(true).setShowWhen(false).setContentIntent(supplied.contentIntent).setDeleteIntent(supplied.deleteIntent)
                .setStyle(Notification.BigTextStyle().setBigContentTitle(supplied.extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
                    .bigText(supplied.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))).apply {
                        supplied.actions?.forEach { action -> addAction(action) }
                    }).setOnlyAlertOnce(true)
        schedule()
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (registered) unregisterReceiver(screens)
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH)
        else @Suppress("DEPRECATION") stopForeground(false)
        val app = applicationContext
        worker.execute { CourseLiveActivity.cleanupCountdown(app) }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object { private val worker = Executors.newSingleThreadExecutor() }
}

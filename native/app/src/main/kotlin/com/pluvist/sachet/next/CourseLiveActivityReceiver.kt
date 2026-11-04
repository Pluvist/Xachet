package com.pluvist.sachet.next

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors

class CourseLiveActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        executor.execute {
            try {
                LegacyReminderCleanup.run(context)
                when (intent.action) {
                    CourseLiveActivity.DISMISS -> CourseLiveActivity.dismissToday(context, intent.getStringExtra("day"))
                    in rebuildActions -> {
                        try {
                            // Only local course/settings files are read. Rebuild wall-clock
                            // timestamps and the full-term snapshot without login or network.
                            runBlocking { AcademicRepository(context.applicationContext).republish() }
                        } catch (error: Exception) {
                            android.util.Log.w("CourseActivity", "Cached timeline restore failed (${error.javaClass.simpleName}); retaining published snapshot")
                            CourseLiveActivity.refresh(context)
                        }
                    }
                    else -> CourseLiveActivity.refresh(context)
                }
            } catch (error: RuntimeException) {
                android.util.Log.w("CourseActivity", "Unable to restore course notifications (${error.javaClass.simpleName})")
            } finally { pending.finish() }
        }
    }
    companion object {
        private val executor = Executors.newSingleThreadExecutor()
        private val rebuildActions = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED)
    }
}

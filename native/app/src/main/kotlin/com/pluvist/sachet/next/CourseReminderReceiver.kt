package com.pluvist.sachet.next

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.Executors

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CourseReminder.FIRE) return
        val pending = goAsync()
        executor.execute {
            try { CourseReminder.deliver(context, intent.getStringExtra("key"), intent.getLongExtra("at", 0)) }
            catch (error: RuntimeException) { android.util.Log.w("CourseReminder", "Unable to update reminder", error) }
            finally { pending.finish() }
        }
    }
    companion object { private val executor = Executors.newSingleThreadExecutor() }
}

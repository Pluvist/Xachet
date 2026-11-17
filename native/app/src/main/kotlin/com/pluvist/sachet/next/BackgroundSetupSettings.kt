package com.pluvist.sachet.next

import android.app.Activity
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** System permission queries use Binder; keep them out of composition/animation frames.
 * Resume immediately refreshes the snapshot after returning from a system settings page. */
@Composable internal fun rememberBackgroundSetupStatus(enabled:Boolean=true):State<Map<String,Any>> {
    val context=LocalContext.current.applicationContext
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    return produceState<Map<String,Any>>(emptyMap(),context,lifecycle,enabled) {
        if(enabled) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {
                value=withContext(Dispatchers.IO){
                    try { BackgroundSetupSettings.status(context) }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (_: RuntimeException) { emptyMap() }
                }
                delay(1000)
            }
        }
    }
}

/** Opens user-controlled system screens; it never grants or fabricates access. */
internal object BackgroundSetupSettings {
    private fun details(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"))
    private fun notificationSettings(context:Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
    private fun channelSettings(context:Context,channel:String) = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID,channel)
    private fun promotedSettings(context:Context):List<Intent> = if(Build.VERSION.SDK_INT<36)emptyList() else
        runCatching { Settings::class.java.getField("ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS").get(null) as String }
            .getOrNull()?.let { listOf(Intent(it).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)) }.orEmpty()
    internal fun candidates(context: Context, kind: String): List<Intent> = when (kind) {
        "notifications" -> listOf(notificationSettings(context),details(context))
        "live" -> promotedSettings(context)+listOf(notificationSettings(context),details(context))
        "liveNotifications" -> listOf(channelSettings(context,"course_live_activity"),notificationSettings(context),details(context))
        "reminders" -> listOf(channelSettings(context,"course_preclass_reminders"),notificationSettings(context),details(context))
        "exactAlarms" -> listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}")),details(context))
        "autostart" -> listOf(Intent().setComponent(ComponentName("com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.safecenter",
                "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity")), details(context))
        "battery" -> listOf(Intent("android.settings.APP_BATTERY_SETTINGS",
            Uri.parse("package:${context.packageName}")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), details(context))
        else -> listOf(details(context))
    }
    fun open(activity: Activity, kind: String): Boolean {
        for (intent in candidates(activity, kind)) {
            try {
                if (intent.resolveActivity(activity.packageManager) != null) {
                    activity.startActivity(intent)
                    return true
                }
            } catch (_: RuntimeException) { }
        }
        return false
    }
    /** A denied-again runtime permission needs the system settings screen instead of a no-op dialog. */
    fun requestNotifications(activity:Activity,launch:()->Unit) {
        val prefs=activity.getSharedPreferences("course_widget",Context.MODE_PRIVATE)
        val allowed=runCatching {
            activity.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val askedBefore=prefs.getBoolean("livePermissionAsked",false)
        val shouldShowRationale=runCatching {
            activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
        }.getOrDefault(false)
        if(allowed || (askedBefore && !shouldShowRationale)) {
            open(activity,"notifications")
        } else {
            prefs.edit().putBoolean("livePermissionAsked",true).apply()
            try {
                launch()
            } catch (_: RuntimeException) {
                open(activity, "notifications")
            }
        }
    }
    /** Permission callbacks must restore the cached schedule without waiting for the next network sync. */
    suspend fun refreshCourses(context:Context) = withContext(Dispatchers.IO) {
        runCatching { CourseLiveActivity.refresh(context.applicationContext) }
            .onFailure { android.util.Log.w("CourseActivity","Could not refresh course notifications",it) }
        Unit
    }
    fun status(context: Context): Map<String, Any> {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
        val postGranted = runCatching {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val notifyEnabled = runCatching { notifications?.areNotificationsEnabled() == true }.getOrDefault(false)
        val batteryUnrestricted = Build.VERSION.SDK_INT < 23 ||
            runCatching { power?.isIgnoringBatteryOptimizations(context.packageName) == true }.getOrDefault(false)
        val autostartAvailable = runCatching {
            candidates(context, "autostart").dropLast(1).any { it.resolveActivity(context.packageManager) != null }
        }.getOrDefault(false)
        val liveStatus = runCatching { CourseLiveActivity.status(context) }.getOrDefault(emptyMap())
        val reminderStatus = runCatching { CourseReminder.status(context) }.getOrDefault(emptyMap())
        val widgetCount = if (manager == null) {
            0
        } else {
            runCatching {
                manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java)).size +
                    manager.getAppWidgetIds(ComponentName(context, CourseWidgetWideProvider::class.java)).size
            }.getOrDefault(0)
        }
        return mapOf(
            "notifications" to (postGranted && notifyEnabled),
            "batteryUnrestricted" to batteryUnrestricted,
            "autostartAvailable" to autostartAvailable,
            "live" to liveStatus,
            "reminders" to reminderStatus,
            // There is no public API to read a vendor's auto-start permission.
            "widgetCount" to widgetCount,
        )
    }
}

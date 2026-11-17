package com.pluvist.sachet.next

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class CourseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) = refresh(context)

    override fun onDisabled(context: Context) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // The framework dispatches each of these to one refreshing callback.
            // Calling refresh again afterward regenerated every widget twice.
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED,
            AppWidgetManager.ACTION_APPWIDGET_DISABLED,
            AppWidgetManager.ACTION_APPWIDGET_RESTORED -> super.onReceive(context, intent)
            // Enabled/deleted callbacks have no additional work in these providers.
            // Reconcile boot, clock and custom events directly; this also collapses
            // the framework's combined enable/update event to one refresh.
            else -> refresh(context)
        }
    }

    companion object {
        private const val REFRESH = "com.pluvist.xachet.WIDGET_REFRESH"

        internal fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            772,
            Intent(context, CourseWidgetProvider::class.java).setAction(REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun refresh(context: Context, now: Long = System.currentTimeMillis()) {
            val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull() ?: return
            val squareIds = runCatching {
                manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java))
            }.getOrDefault(IntArray(0))
            val wideIds = runCatching {
                manager.getAppWidgetIds(ComponentName(context, CourseWidgetWideProvider::class.java))
            }.getOrDefault(IntArray(0))
            val widgets = squareIds.map { it to false } + wideIds.map { it to true }
            if (widgets.isEmpty()) {
                runCatching {
                    (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(refreshIntent(context))
                }
                return
            }

            val data = try {
                JSONObject(
                    context.getSharedPreferences("course_widget", Context.MODE_PRIVATE)
                        .getString("data", "{}") ?: "{}",
                )
            } catch (_: Exception) {
                JSONObject()
            }
            val midnight = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val tomorrow = (midnight.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            val array = data.optJSONArray("lessons")
            val all = (0 until (array?.length() ?: 0)).map { array!!.getJSONObject(it) }
            val ready = data.optBoolean("ready")
            val today = if (ready) {
                all.filter { it.getLong("start") >= midnight.timeInMillis && it.getLong("start") < tomorrow }
            } else {
                emptyList()
            }
            val current = today.firstOrNull { it.getLong("start") <= now && it.getLong("end") > now }
            val next = today.sortedBy { it.getLong("start") }.firstOrNull {
                it.getLong("start") > now && (current == null || it.getLong("start") >= current.getLong("end"))
            }
            val shown = current ?: next
            val remaining = today.filter { it.getLong("start") > now && it.optString("group") != shown?.optString("group") }
                .distinctBy { it.optString("group") }

            val tone = when {
                current != null -> WidgetCourseTone.InClass
                next != null -> WidgetCourseTone.Waiting
                else -> WidgetCourseTone.Idle
            }
            val tokens = CourseWidgetGlow.resolveThemeTokens(context, data, tone)

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
            val start = try {
                dateFormat.parse(data.optString("semester").take(10))?.time
            } catch (_: Exception) {
                null
            }
            val week = if (start == null || midnight.timeInMillis < start) {
                0
            } else {
                ((midnight.timeInMillis - start) / (7 * 86400000L) + 1).toInt()
            }
            val weekday = SimpleDateFormat("EEEE", Locale.CHINA).format(now)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.ROOT)

            for ((id, wide) in widgets) {
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
                if (wide) {
                    manager.updateAppWidget(id, CourseWidgetWideRenderer.render(context, data, now, width, height))
                    continue
                }

                val compact = width < 230 || height < 220
                val view = RemoteViews(
                    context.packageName,
                    if (compact) R.layout.course_widget_compact else R.layout.course_widget,
                )
                CourseWidgetGlow.apply(context, view, tokens, wide = false, width = width, height = height)
                CourseWidgetGlow.tintBackground(view, R.id.widget_hero_card, tokens.heroBg)
                CourseWidgetGlow.tintBackground(view, R.id.widget_week, tokens.weekPillBg)
                CourseWidgetGlow.tintBackground(view, R.id.widget_countdown, tokens.countdownPillBg)
                CourseWidgetGlow.tintBackground(view, R.id.widget_state_icon, tokens.actionBg)

                if (compact) {
                    val density = context.resources.displayMetrics.density
                    fun dp(value: Int) = (value * density + 0.5f).toInt()
                    val contentHeight = height - 2
                    val vertical = if (contentHeight < 130) 10 else 12
                    view.setViewPadding(R.id.widget_content, dp(12), dp(vertical), dp(12), dp(vertical))
                    view.setViewVisibility(R.id.widget_header, if (contentHeight < 145) View.GONE else View.VISIBLE)
                    view.setViewVisibility(R.id.widget_footer_row, if (contentHeight < 115) View.GONE else View.VISIBLE)
                    val scale = maxOf(1f, context.resources.configuration.fontScale / 1.25f)
                    view.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, 16f / scale)
                    for (text in listOf(R.id.widget_date, R.id.widget_week, R.id.widget_time, R.id.widget_place)) {
                        view.setTextViewTextSize(text, TypedValue.COMPLEX_UNIT_SP, 10f / scale)
                    }
                    view.setTextViewTextSize(R.id.widget_footer, TypedValue.COMPLEX_UNIT_SP, 9f / scale)
                }

                view.setTextViewText(
                    R.id.widget_date,
                    when {
                        compact && width < 150 -> weekday.replace("星期", "周")
                        compact -> "今天 · ${weekday.replace("星期", "周")}"
                        else -> "今天 · $weekday"
                    },
                )
                view.setTextViewText(R.id.widget_week, if (week > 0) "第${week}周" else "未开学")
                view.setTextViewText(
                    R.id.widget_title,
                    shown?.optString("title")
                        ?: when {
                            !ready -> "请先导入课表"
                            today.isEmpty() -> "今日无课安排"
                            else -> "今日课程已完成"
                        },
                )
                view.setTextViewText(
                    R.id.widget_time,
                    if (shown == null) {
                        ""
                    } else {
                        "${timeFormat.format(shown.getLong("start"))}–${timeFormat.format(shown.getLong("end"))}"
                    },
                )
                view.setTextViewText(R.id.widget_place, shown?.optString("place") ?: "")
                view.setViewVisibility(R.id.widget_details, if (shown == null) View.GONE else View.VISIBLE)
                view.setTextViewText(
                    R.id.widget_footer,
                    when {
                        !ready -> "点击导入本地或教务课表"
                        current != null -> if (remaining.isEmpty()) "正在上课 · 最后一门" else "正在上课 · 余${remaining.size}门"
                        next != null -> if (remaining.isEmpty()) "待上课 · 今日最后一门" else "待上课 · 其他${remaining.size}门"
                        else -> "好好享受今日自由时光"
                    },
                )

                val dots = SpannableString(if (remaining.isEmpty()) "" else "● ● ●")
                if (dots.isNotEmpty()) {
                    tokens.dotColors.forEachIndexed { index, color ->
                        dots.setSpan(
                            ForegroundColorSpan(color),
                            index * 2,
                            index * 2 + 1,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
                view.setTextViewText(R.id.widget_dots, dots)
                view.setViewVisibility(R.id.widget_dots, if (compact && width < 150) View.GONE else View.VISIBLE)

                if (compact) {
                    view.setTextViewText(
                        R.id.widget_footer,
                        when {
                            current != null -> "余${(current.getLong("end") - now + 59999) / 60000}分钟" +
                                if (remaining.isEmpty()) "" else " · 另${remaining.size}门"
                            next != null -> if (remaining.isEmpty()) "今日最后一门" else "待上课 · 另${remaining.size}门"
                            !ready -> "点击导入课表"
                            else -> "享受今日自由时光"
                        },
                    )
                }

                view.setViewVisibility(R.id.widget_countdown, if (current != null && !compact) View.VISIBLE else View.GONE)
                view.setTextViewText(
                    R.id.widget_countdown,
                    if (current == null) "" else "余 ${(current.getLong("end") - now + 59999) / 60000} 分",
                )

                view.setTextColor(R.id.widget_title, tokens.heroFg)
                view.setTextColor(R.id.widget_time, tokens.heroMuted)
                view.setTextColor(R.id.widget_place, tokens.heroMuted)
                view.setTextColor(R.id.widget_date, tokens.headerFg)
                view.setTextColor(R.id.widget_week, tokens.weekPillFg)
                view.setTextColor(R.id.widget_countdown, tokens.countdownPillFg)
                view.setTextColor(R.id.widget_footer, tokens.footerFg)
                view.setInt(R.id.widget_clock, "setColorFilter", tokens.heroMuted)
                view.setInt(R.id.widget_location, "setColorFilter", tokens.heroMuted)
                view.setInt(R.id.widget_state_icon, "setColorFilter", tokens.actionFg)
                view.setImageViewResource(
                    R.id.widget_state_icon,
                    if (shown == null) R.drawable.widget_check else R.drawable.widget_going,
                )

                val open = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                view.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        773,
                        open,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                view.setOnClickPendingIntent(R.id.widget_state_icon, refreshIntent(context))
                view.setContentDescription(R.id.widget_state_icon, "刷新课程状态")
                manager.updateAppWidget(id, view)
            }

            // Refresh at actual lesson boundaries and midnight; no permanent service.
            val boundary = all.flatMap { listOf(it.getLong("start"), it.getLong("end")) }
                .filter { it > now }.minOrNull() ?: tomorrow
            val trigger = minOf(
                boundary,
                tomorrow,
                if (current != null) now + 60000 else tomorrow,
            ) + 1000
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            try {
                if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                    alarm.setExact(AlarmManager.RTC, trigger, refreshIntent(context))
                } else {
                    alarm.set(AlarmManager.RTC, trigger, refreshIntent(context))
                }
            } catch (_: SecurityException) {
                alarm.set(AlarmManager.RTC, trigger, refreshIntent(context))
            }
        }
    }
}

package com.pluvist.sachet.next

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

data class ExamTiming(val start: LocalDateTime, val end: LocalDateTime)

data class ExamCountdown(val prefix: String, val value: String, val suffix: String)

/** Display-only exam timing. Invalid or unscheduled times never produce a countdown. */
object ExamPresentation {
    private val pattern = Regex(
        """(\d{4}-\d{1,2}-\d{1,2})\s*[（(]?\s*(\d{1,2}:\d{2})\s*[-–—－]\s*(\d{1,2}:\d{2})\s*[）)]?""",
    )
    private val dateFormat = DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("H:mm").withResolverStyle(ResolverStyle.STRICT)

    fun parse(raw: String): ExamTiming? = runCatching {
        val match = pattern.find(raw) ?: return null
        val date = LocalDate.parse(match.groupValues[1], dateFormat)
        val start = date.atTime(LocalTime.parse(match.groupValues[2], timeFormat))
        val end = date.atTime(LocalTime.parse(match.groupValues[3], timeFormat))
        require(end.isAfter(start))
        ExamTiming(start, end)
    }.getOrNull()

    fun weekday(timing: ExamTiming): String = when (timing.start.dayOfWeek.value) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        else -> "星期日"
    }

    fun countdown(timing: ExamTiming, now: LocalDateTime): ExamCountdown {
        if (!now.isBefore(timing.end)) return ExamCountdown("已结束", "", "")
        if (!now.isBefore(timing.start)) return ExamCountdown("正在考试", "", "")

        val calendarDays = ChronoUnit.DAYS.between(now.toLocalDate(), timing.start.toLocalDate())
        if (calendarDays >= 2) return ExamCountdown("还有", calendarDays.toString(), "天")

        val remaining = Duration.between(now, timing.start)
        if (remaining.toMinutes() >= 60) return ExamCountdown("还有", remaining.toHours().toString(), "小时")
        val minutes = ((remaining.seconds + 59) / 60).coerceAtLeast(1)
        return ExamCountdown("还有", minutes.toString(), "分钟")
    }
}

package com.pluvist.sachet.next

import java.util.Calendar
import java.util.TimeZone
import java.util.Locale
import org.json.JSONObject

data class LiveLesson(val title: String, val place: String, val start: Long, val end: Long,
    val section: Int = 0, val group: String = "")
enum class LiveCoursePhase { NONE, WAITING, IN_CLASS, COMPLETED }
data class LiveCourseState(val phase: LiveCoursePhase, val today: List<LiveLesson>,
    val current: LiveLesson?, val next: LiveLesson?, val midnight: Long, val tomorrow: Long)

/** Uses the same resolved section timestamps as the native homepage and widgets. */
object CourseTimeline {
    fun lessons(data: JSONObject): List<LiveLesson> {
        val array = data.optJSONArray("lessons") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val start = row.optLong("start"); val end = row.optLong("end")
            val title = row.optString("title").trim()
            if (title.isBlank() || start <= 0 || end <= start) null else LiveLesson(title,
                row.optString("place").ifBlank { "地点待确认" }, start, end,
                row.optInt("section"), row.optString("group"))
        }.sortedBy { it.start }
    }

    fun dayStart(time: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun resolve(now: Long, lessons: List<LiveLesson>, zone: TimeZone = TimeZone.getDefault()): LiveCourseState {
        val midnight = dayStart(now, zone)
        val tomorrow = Calendar.getInstance(zone).apply {
            timeInMillis = midnight; add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val today = lessons.filter { it.start >= midnight && it.start < tomorrow && it.end > it.start }
            .sortedBy { it.start }
        val current = today.firstOrNull { it.start <= now && it.end > now }
        val next = today.firstOrNull { if (current == null) it.start > now else it.start >= current.end }
        val phase = when {
            today.isEmpty() -> LiveCoursePhase.NONE
            current != null -> LiveCoursePhase.IN_CLASS
            next != null -> LiveCoursePhase.WAITING
            else -> LiveCoursePhase.COMPLETED
        }
        return LiveCourseState(phase, today, current, next, midnight, tomorrow)
    }

    fun promote(state: LiveCourseState, now: Long): Boolean =
        state.current != null || (state.next != null &&
            (state.today.any { it.start <= now } || state.next.start - now <= 30*60_000L))

    fun nextWake(now: Long, lessons: List<LiveLesson>, state: LiveCourseState,
        dismissed: Boolean = false, zone: TimeZone = TimeZone.getDefault()): Long? {
        val futureDay = lessons.filter { it.start >= state.tomorrow && it.end > it.start }
            .minByOrNull { it.start }?.let { dayStart(it.start, zone) }
        if (dismissed || state.phase == LiveCoursePhase.NONE) return futureDay
        if (state.phase == LiveCoursePhase.COMPLETED) return state.tomorrow
        val boundary = state.today.flatMap { listOf(it.start, it.end) }.filter { it > now }.minOrNull()
        val promoteAt = if (promote(state, now)) null else
            state.next?.start?.minus(30*60_000L)?.takeIf { it > now }
        return listOfNotNull(boundary, promoteAt, state.tomorrow).minOrNull()
    }

    /** Digits only; ceil prevents showing zero while a positive fraction remains. */
    fun countdown(remainingMillis: Long): String {
        val millis = remainingMillis.coerceIn(0, 86_400_000L)
        val seconds = millis / 1000 + if (millis % 1000 == 0L) 0 else 1
        return if (seconds >= 3600) String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    fun compactPlace(place: String): String = when {
        place.contains("信息科技") -> "信科"
        place.contains("逸夫") -> "逸夫"
        place.contains("第三教学") || place.contains("教三") -> "教三"
        place.contains("第二教学") || place.contains("教二") -> "教二"
        place.contains("第一教学") || place.contains("一教") -> "一教"
        else -> place.trim().take(2)
    }
}

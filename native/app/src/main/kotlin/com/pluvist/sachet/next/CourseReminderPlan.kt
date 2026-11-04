package com.pluvist.sachet.next

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.TimeZone

data class CourseReminderEvent(val key: String, val lesson: LiveLesson, val section: Int,
    val leadMinutes: Int, val triggerAt: Long)

data class CourseReminderBatch(val key: String, val triggerAt: Long, val events: List<CourseReminderEvent>)

/** Deterministic planning, independent of app visibility and of the live-activity opt-out. */
object CourseReminderPlan {
    private const val HEX = "0123456789abcdef"

    private fun key(identity: String, digest: MessageDigest): String {
        val bytes = digest.digest(identity.toByteArray(StandardCharsets.UTF_8))
        val hex = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 255
            hex[index * 2] = HEX[value ushr 4]
            hex[index * 2 + 1] = HEX[value and 15]
        }
        return String(hex)
    }

    fun events(lessons: List<LiveLesson>, zone: TimeZone = TimeZone.getDefault()): List<CourseReminderEvent> =
        MessageDigest.getInstance("SHA-256").let { digest -> lessons.asSequence()
            .filter { it.start > 0 && it.end > it.start && it.title.isNotBlank() }
            .map { lesson ->
                val section = lesson.section.takeIf { it in 1..11 } ?: inferSection(lesson.start, zone)
                val lead = if (section == 1 || section == 5) 20 else 10
                // Course IDs may change after a legacy-cache import or server refresh.
                // Actual class identity remains stable across either representation.
                val identity = listOf(lesson.start, lesson.end, section, lesson.title.trim(), lesson.place.trim()).joinToString("\u0000")
                CourseReminderEvent(key(identity, digest), lesson, section, lead, lesson.start - lead * 60_000L)
            }.distinctBy { it.key }.sortedWith(compareBy<CourseReminderEvent> { it.triggerAt }.thenBy { it.key }).toList() }

    fun next(now: Long, events: List<CourseReminderEvent>, delivered: Set<String>): CourseReminderBatch? {
        val future = events.filter { it.triggerAt > now && it.lesson.start > now && it.key !in delivered }
        val trigger = future.minOfOrNull { it.triggerAt } ?: return null
        val batch = future.filter { it.triggerAt == trigger }
        return CourseReminderBatch(batch.joinToString(":") { it.key }, trigger, batch)
    }

    /** Only an actual scheduled delivery can emit a late alarm, and only before class. */
    fun due(now: Long, scheduledKey: String, scheduledAt: Long, events: List<CourseReminderEvent>,
        delivered: Set<String>): List<CourseReminderEvent> {
        if (scheduledAt <= 0 || now < scheduledAt) return emptyList()
        val batch = events.filter { it.triggerAt == scheduledAt }
        if (batch.joinToString(":") { it.key } != scheduledKey) return emptyList()
        return batch.filter { it.lesson.start > now && it.key !in delivered }
    }

    private fun inferSection(start: Long, zone: TimeZone): Int {
        val time = Instant.ofEpochMilli(start).atZone(zone.toZoneId()).toLocalTime()
        return (1..11).firstOrNull { time == LessonTimes.start(it, false) || time == LessonTimes.start(it, true) } ?: 0
    }
}

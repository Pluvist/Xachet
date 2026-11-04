package com.pluvist.sachet.next

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.TimeZone

class CourseReminderPlanTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")
    private fun at(time: String) = LocalDateTime.parse("2026-11-02T$time").atZone(zone.toZoneId()).toInstant().toEpochMilli()
    private fun lesson(section: Int, start: String, title: String = "数学", place: String = "第一教学楼102") =
        LiveLesson(title, place, at(start), at(start) + 45 * 60_000L, section, "course-id")
    private fun events(vararg lessons: LiveLesson) = CourseReminderPlan.events(lessons.toList(), zone)

    @Test fun firstAndFifthSectionsUseTwentyMinutesInsteadOfTen() {
        val planned = events(lesson(1, "08:00"), lesson(3, "10:20"), lesson(5, "14:00"), lesson(9, "19:15"))
        assertEquals(listOf(at("07:40"), at("10:10"), at("13:40"), at("19:05")), planned.map { it.triggerAt })
        assertEquals(listOf(20, 10, 20, 10), planned.map { it.leadMinutes })
    }

    @Test fun everyActualSectionInOneCourseBlockHasItsOwnReminder() {
        val course = Course("math", "数学", "第一教学楼102", "", 1, 1, 2, setOf(1))
        val date = java.time.LocalDate.parse("2026-11-02")
        val resolved = CourseClock.onDate(listOf(course), date, date, zone.toZoneId())
        val planned = events(*resolved.map { LiveLesson(it.course.title, it.course.place, it.start.toEpochMilli(), it.end.toEpochMilli(), it.section) }.toTypedArray())
        assertEquals(listOf(at("07:40"), at("08:45")), planned.map { it.triggerAt })
        assertEquals(listOf(1, 2), planned.map { it.section })
    }

    @Test fun staggeredRoomsUseActualDelayedLessonStarts() {
        val planned = events(lesson(1, "08:10", place = "逸夫楼201"), lesson(5, "14:10", place = "信息科技大楼204"))
        assertEquals(listOf(at("07:50"), at("13:50")), planned.map { it.triggerAt })
    }

    @Test fun oldSnapshotsWithoutSectionMetadataInferBothTimetables() {
        val planned = events(lesson(1, "08:10").copy(section = 0), lesson(5, "14:00").copy(section = 0),
            lesson(6, "15:05").copy(section = 0))
        assertEquals(listOf(1, 5, 6), planned.map { it.section })
        assertEquals(listOf(20, 20, 10), planned.map { it.leadMinutes })
    }

    @Test fun sectionInferenceHonorsGivenTimeZoneRatherThanHostTimeZone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(20, events(lesson(1, "08:00").copy(section = 0)).single().leadMinutes)
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun repeatedRowsAndRefreshIdsDoNotCreateDuplicateReminders() {
        val one = lesson(3, "10:20")
        val planned = events(one, one.copy(group = "changed-server-id"))
        assertEquals(1, planned.size)
        assertEquals(events(one).single().key, planned.single().key)
        assertEquals(2, events(one, one.copy(place = "逸夫楼201")).size)
    }

    @Test fun newSchedulingNeverCatchesUpMissedOrAlreadyStartedLessons() {
        val planned = events(lesson(1, "08:00"), lesson(3, "10:20"))
        assertEquals(at("10:10"), CourseReminderPlan.next(at("07:55"), planned, emptySet())!!.triggerAt)
        assertNull(CourseReminderPlan.next(at("10:20"), planned, emptySet()))
        assertNull(CourseReminderPlan.next(at("07:55"), emptyList(), emptySet()))
    }

    @Test fun matchingAlarmMayArriveLateBeforeClassButNotAtOrAfterItsStart() {
        val planned = events(lesson(1, "08:00"))
        val batch = CourseReminderPlan.next(at("07:30"), planned, emptySet())!!
        assertTrue(CourseReminderPlan.due(at("07:39"), batch.key, batch.triggerAt, planned, emptySet()).isEmpty())
        assertEquals(1, CourseReminderPlan.due(at("07:40"), batch.key, batch.triggerAt, planned, emptySet()).size)
        assertEquals(1, CourseReminderPlan.due(at("07:59"), batch.key, batch.triggerAt, planned, emptySet()).size)
        assertTrue(CourseReminderPlan.due(at("08:00"), batch.key, batch.triggerAt, planned, emptySet()).isEmpty())
    }

    @Test fun staleCanceledAlarmsAndRemovedLessonsCannotNotify() {
        val planned = events(lesson(1, "08:00"))
        val batch = CourseReminderPlan.next(at("07:30"), planned, emptySet())!!
        assertTrue(CourseReminderPlan.due(at("07:40"), "old-key", batch.triggerAt, planned, emptySet()).isEmpty())
        assertTrue(CourseReminderPlan.due(at("07:40"), batch.key, batch.triggerAt, emptyList(), emptySet()).isEmpty())
        assertTrue(CourseReminderPlan.due(at("07:40"), batch.key, batch.triggerAt, events(lesson(1, "08:10")), emptySet()).isEmpty())
    }

    @Test fun simultaneousCoursesAreOneBatchAndAlreadyDeliveredKeysStaySilent() {
        val planned = events(lesson(1, "08:00"), lesson(1, "08:00", title = "物理"), lesson(3, "10:20"))
        val batch = CourseReminderPlan.next(at("07:30"), planned, emptySet())!!
        val due = CourseReminderPlan.due(at("07:40"), batch.key, batch.triggerAt, planned, emptySet())
        assertEquals(2, due.size)
        val sent = due.map { it.key }.toSet()
        assertTrue(CourseReminderPlan.due(at("07:40"), batch.key, batch.triggerAt, planned, sent).isEmpty())
        assertEquals(at("10:10"), CourseReminderPlan.next(at("07:40"), planned, sent)!!.triggerAt)
    }

    @Test fun lessonParserPreservesSectionAndSkipsCorruptRows() {
        val json = JSONObject().put("lessons", JSONArray()
            .put(JSONObject().put("title", "数学").put("place", "").put("start", at("08:00")).put("end", at("08:45")).put("section", 1).put("group", "math"))
            .put(JSONObject().put("title", "损坏").put("start", at("10:20")).put("end", at("10:10")))
            .put("not a lesson"))
        val parsed = CourseTimeline.lessons(json).single()
        assertEquals(1, parsed.section); assertEquals("math", parsed.group)
        assertEquals("地点待确认", parsed.place)
        assertEquals(at("07:40"), events(parsed).single().triggerAt)
    }

    @Test fun invalidAndBlankLessonsNeverScheduleAnAlarm() {
        val valid = lesson(1, "08:00")
        assertTrue(events(valid.copy(end = valid.start), valid.copy(start = -1), valid.copy(title = " ")).isEmpty())
    }
}

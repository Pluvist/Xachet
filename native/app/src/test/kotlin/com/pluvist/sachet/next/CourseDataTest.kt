package com.pluvist.sachet.next

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CourseDataTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val semester = LocalDate.of(2026, 10, 26)
    private val course = Course("1", "高等数学", "逸夫楼101", "教师", 1, 1, 2, setOf(1,2))
    private fun now(time: String) = LocalDateTime.parse("2026-10-26T$time").atZone(zone).toInstant()
    @Test fun buildingChangesDaytimeButNotEvening() {
        assertEquals(LocalTime.of(8,10), course.interval().first)
        assertEquals(LocalTime.of(9,50), course.interval().second)
        assertEquals(LessonTimes.start(9,false), LessonTimes.start(9,true))
        assertEquals(LocalTime.of(8,0), course.copy(routine="normal").interval().first)
    }
    @Test fun classBoundariesAreHalfOpen() {
        assertNotNull(CourseClock.status(listOf(course),semester,now("08:10:00"),zone).current)
        assertNull(CourseClock.status(listOf(course),semester,now("09:50:00"),zone).current)
        assertTrue(CourseClock.status(listOf(course),semester,now("09:50:00"),zone).completed)
    }
    @Test fun emptyDayDoesNotBecomeCompleted() {
        val status = CourseClock.status(emptyList(),semester,now("10:00:00"),zone)
        assertFalse(status.completed); assertNull(status.current); assertNull(status.next)
    }
    @Test fun nextCourseUsesActualBuildingTime() {
        val status = CourseClock.status(listOf(course),semester,now("08:05:00"),zone)
        assertNull(status.current); assertEquals(now("08:10:00"),status.next?.start)
    }
    @Test fun beforeSemesterHasNoFirstWeekLessons() {
        assertEquals(0,CourseClock.week(semester.minusDays(1),semester))
    }
    @Test fun breaksBetweenSectionsAreWaitingNotInClass() {
        val status=CourseClock.status(listOf(course),semester,now("09:00:00"),zone)
        assertNull(status.current);assertEquals(now("09:05:00"),status.next?.start)
        assertEquals(2,status.next?.section)
        assertEquals(now("09:50:00"),status.next?.end)
    }
    @Test fun oddAndEvenWeeks() {
        assertEquals(setOf(1,3,5,8),CourseParser.weeks("1-6周(单),8周"))
        assertEquals(setOf(2,4,6),CourseParser.weeks("1-6(双)"))
    }
    @Test(expected=IllegalArgumentException::class) fun invalidWeeksAreRejected() { CourseParser.weeks("12-1周") }
    @Test fun realServerShapeKeepsSingleSections() {
        val courses = CourseParser.fromServer("""{"kbList":[{"kcmc":"实验","cdmc":"信息科技大楼","xm":"老师","xqj":"1","jc":"2-2节","zcd":"1-2周"}]}""")
        assertEquals(2,courses.single().first); assertEquals(2,courses.single().last)
    }

    @Test fun customCoursePersistsAndDrivesHomeStatusTimelineAndReminders() {
        val custom = Course(
            id = "custom-test-1",
            title = "自定义实验课",
            place = "逸夫楼305",
            teacher = "王老师",
            weekday = 1,
            first = 3,
            last = 4,
            weeks = setOf(1, 3),
        )
        assertTrue(custom.isCustom)
        assertTrue(custom.staggered)
        assertEquals("逸夫楼", extractBuildingName(custom.place))
        assertTrue(resolveAvailableBuildings(listOf(custom, Course("2", "普通课", "明德楼101", "", 2, 1, 2, setOf(1)))).contains("明德楼"))

        val roundTrip = CustomCourseCodec.parse(CustomCourseCodec.serialize(listOf(custom))).single()
        assertEquals(custom, roundTrip)

        // Feeds Home Current & Next Course cards identically to synced courses
        val beforeClass = CourseClock.status(listOf(roundTrip), semester, now("10:00:00"), zone)
        assertNull(beforeClass.current)
        assertEquals("自定义实验课", beforeClass.next?.course?.title)
        assertEquals(now("10:30:00"), beforeClass.next?.start)

        val duringClass = CourseClock.status(listOf(roundTrip), semester, now("10:45:00"), zone)
        assertEquals("自定义实验课", duringClass.current?.course?.title)
        assertEquals("逸夫楼305", duringClass.current?.course?.place)

        // Feeds Live Activity & Reminders pipeline via remainingTerm -> LiveLesson -> CourseReminderPlan
        val remaining = CourseClock.remainingTerm(listOf(roundTrip), semester, semester, zone)
        val liveLessons = remaining.map {
            LiveLesson(it.course.title, it.course.place, it.start.toEpochMilli(), it.end.toEpochMilli(), it.section, it.course.id)
        }
        val state = CourseTimeline.resolve(now("10:45:00").toEpochMilli(), liveLessons, java.util.TimeZone.getTimeZone(zone))
        assertEquals(LiveCoursePhase.IN_CLASS, state.phase)
        assertEquals("自定义实验课", state.current?.title)
        assertEquals(4, CourseReminderPlan.events(liveLessons).size)
    }
}

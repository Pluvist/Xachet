package com.pluvist.sachet.next

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RemainingTermTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private val semester=LocalDate.of(2026,9,7)
    private fun course(day:Int,first:Int,last:Int,weeks:Set<Int>,place:String="一教楼")=
        Course("$day-$first", "测试课程",place,"教师",day,first,last,weeks)

    @Test fun fullRemainingTermMatchesDailyResolverIncludingDatesBeyondSixtyDays() {
        val courses=listOf(course(1,1,2,setOf(1,3,12,20,60)),course(5,5,6,setOf(2,12,60),"逸夫楼"))
        val first=semester.plusDays(8)
        val expected=(0L..419L).map{semester.plusDays(it)}.filter{!it.isBefore(first)}
            .flatMap{CourseClock.onDate(courses,semester,it,zone)}
        val actual=CourseClock.remainingTerm(courses,semester,first,zone)
        assertEquals(expected,actual)
        assertTrue(actual.any{it.start.atZone(zone).toLocalDate().isAfter(first.plusDays(60))})
    }

    @Test fun overlappingTeachingDatesAreNotPublishedTwiceAndEachSectionIsRetained() {
        val courses=listOf(course(1,1,2,setOf(1)),course(1,5,6,setOf(1)))
        val actual=CourseClock.remainingTerm(courses,semester,semester,zone)
        assertEquals(listOf(1,2,5,6),actual.map{it.section})
        assertEquals(4,actual.size)
    }

    @Test fun nonMondayLegacySemesterStillMatchesCanonicalWeekMembership() {
        val start=semester.plusDays(1)
        val courses=listOf(course(1,1,1,setOf(1,2)),course(5,5,5,setOf(1,2)))
        val expected=(0L..13L).flatMap{CourseClock.onDate(courses,start,start.plusDays(it),zone)}
        assertEquals(expected,CourseClock.remainingTerm(courses,start,start,zone))
    }

    @Test fun elapsedTermOrEmptyTimetableProducesNoFutureLessons() {
        assertTrue(CourseClock.remainingTerm(emptyList(),semester,semester,zone).isEmpty())
        assertTrue(CourseClock.remainingTerm(listOf(course(1,1,2,setOf(1))),semester,semester.plusWeeks(2),zone).isEmpty())
    }
}

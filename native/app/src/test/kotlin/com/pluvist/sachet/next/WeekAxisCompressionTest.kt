package com.pluvist.sachet.next

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekAxisCompressionTest {
    @Test fun bothTeachingSchedulesKeepTheirLessonDuration() {
        for(staggered in listOf(false,true))for(section in 1..11) {
            val start=LessonTimes.start(section,staggered)
            val end=LessonTimes.end(section,staggered)
            assertEquals("section $section, staggered=$staggered",45f,axisMinute(end)-axisMinute(start),.001f)
        }
        // This apparent break is occupied by staggered section 1 and normal section 2.
        assertEquals(20f,axisMinute(LocalTime.of(9,5))-axisMinute(LocalTime.of(8,45)),.001f)
    }

    @Test fun trulyEmptyGapsShrinkButPreserveCourseOrdering() {
        val gaps=listOf(
            Triple(LocalTime.of(9,50),LocalTime.of(10,20),10f),
            Triple(LocalTime.of(12,10),LocalTime.of(14,0),14f),
            Triple(LocalTime.of(15,50),LocalTime.of(16,20),10f),
            Triple(LocalTime.of(18,10),LocalTime.of(19,15),14f),
            Triple(LocalTime.of(20,0),LocalTime.of(20,10),6f),
            Triple(LocalTime.of(20,55),LocalTime.of(21,5),6f),
        )
        gaps.forEach{(start,end,visualGap)->assertEquals(visualGap,axisMinute(end)-axisMinute(start),.001f)}
        val lessons=(listOf(false,true)).flatMap{staggered->(1..11).map{section->
            LessonTimes.start(section,staggered) to LessonTimes.end(section,staggered)
        }}
        for((startA,endA) in lessons)for((startB,_) in lessons)if(!endA.isAfter(startB)) {
            assertTrue("Nonoverlapping lessons must remain nonoverlapping",axisMinute(endA)<=axisMinute(startB))
        }
    }

    @Test fun axisIsStrictlyIncreasingAcrossTheWholeTeachingDay() {
        val minutes=(8*60..22*60).map{axisMinute(LocalTime.of(it/60,it%60))}
        assertEquals(0f,minutes.first(),.001f)
        assertEquals(635f,axisMinute(LocalTime.of(21,50)),.001f)
        assertTrue(minutes.zipWithNext().all{(before,after)->after>before})
    }
}

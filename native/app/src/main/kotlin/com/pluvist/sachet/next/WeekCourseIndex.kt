package com.pluvist.sachet.next

import androidx.compose.runtime.Immutable
import java.time.LocalTime

/**
 * Only compress time that is free in the union of the normal and staggered schedules.
 * Keeping every possible lesson interval at 1:1 makes both axes and course bars share
 * one monotonic coordinate system, including courses in different buildings.
 */
private data class EmptyTeachingGap(val start:Int,val end:Int,val displayMinutes:Int)

private val emptyTeachingGaps=listOf(
    EmptyTeachingGap(9*60+50,10*60+20,10),
    EmptyTeachingGap(12*60+10,14*60,14),
    EmptyTeachingGap(15*60+50,16*60+20,10),
    EmptyTeachingGap(18*60+10,19*60+15,14),
    EmptyTeachingGap(20*60,20*60+10,6),
    EmptyTeachingGap(20*60+55,21*60+5,6),
)

internal fun axisMinute(time:LocalTime):Float {
    val minute=time.hour*60+time.minute
    var coordinate=(minute-8*60).toFloat()
    for(gap in emptyTeachingGaps) {
        val elapsed=(minute-gap.start).coerceIn(0,gap.end-gap.start)
        coordinate-=elapsed*(1f-gap.displayMinutes.toFloat()/(gap.end-gap.start))
    }
    return coordinate
}

@Immutable internal data class WeekCourseBlock(val course:Course,val topMinute:Float,val heightMinutes:Float)
@Immutable internal data class WeekCoursePage(val days:List<List<WeekCourseBlock>>)

/** Build once per schedule revision, rather than filtering/parsing during a pager drag. */
internal class WeekCourseIndex(courses:List<Course>) {
    private val blocks=courses.map { course ->
        val (start,end)=course.interval()
        val top=axisMinute(start)
        WeekCourseBlock(course,top,axisMinute(end)-top)
    }
    private val pages=List(60) { page ->
        WeekCoursePage(List(7) { day -> blocks.filter{it.course.weekday==day+1&&page+1 in it.course.weeks} })
    }
    operator fun get(week:Int)=pages[week-1]
}

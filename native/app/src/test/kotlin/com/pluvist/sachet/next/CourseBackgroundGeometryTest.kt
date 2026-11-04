package com.pluvist.sachet.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class CourseBackgroundGeometryTest {
    private val size=Size(1216f,2640f)
    private val sink=28f
    private fun visible(point:Offset,progress:Float):Offset {
        val fraction=CourseMorphGeometry.smooth(progress)
        val pivot=Offset(size.width/2,size.height/2)
        return pivot+(point-pivot)*CourseBackgroundGeometry.scale(fraction)+Offset(0f,sink*fraction)
    }
    @Test fun noDepthLeavesRetargetPositionsUnchanged() {
        val point=Offset(30f,700f)
        assertEquals(point,CourseBackgroundGeometry.canonicalTap(point,size,0f,sink))
    }
    @Test fun sinkingDoesNotMoveNearEdgeTapsToAdjacentCourse() {
        val course=Rect(100f,600f,240f,1100f)
        for(p in listOf(.1f,.5f,1f,1.03f)) {
            for(point in listOf(Offset(100.1f,600.1f),Offset(239.9f,1099.9f))) {
                val actual=CourseBackgroundGeometry.canonicalTap(visible(point,p),size,p,sink)
                assertTrue(course.contains(actual))
                assertEquals(point.x,actual.x,.001f);assertEquals(point.y,actual.y,.001f)
            }
        }
    }
    @Test fun repeatedInterruptedReversalsKeepTheOriginalSourceCoordinates() {
        val point=Offset(607f,2240f)
        for(p in listOf(0f,.8f,.4f,.9f,.2f,.7f,0f)) {
            val actual=CourseBackgroundGeometry.canonicalTap(visible(point,p),size,p,sink)
            assertEquals(point.x,actual.x,.001f);assertEquals(point.y,actual.y,.001f)
        }
        assertEquals(1f,CourseBackgroundGeometry.scale(CourseMorphGeometry.smooth(-.1f)),0f)
    }
}

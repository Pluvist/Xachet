package com.pluvist.sachet.next

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.SpringSpec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class CourseMorphMotionTest {
    private val lesson=CourseMorphContent.Lesson(Course("test","测试课程","一教101","教师",1,1,2,setOf(1)))
    private val kinds=listOf(CourseMorphContent.Status,lesson,CourseMorphContent.Day(LocalDate.of(2026,11,2),emptyList()))
    private fun motion(spec:SpringSpec<Float>,from:Float,to:Float,velocity:Float=0f)=
        TargetBasedAnimation(spec,Float.VectorConverter,from,to,AnimationVector1D(velocity))

    @Test fun lessonOpeningDoesNotBouncePastThePanel() {
        val motion=TargetBasedAnimation(CourseMorphMotion.open(lesson),Float.VectorConverter,0f,1f)
        val samples=(0..1000 step 2).map{motion.getValueFromNanos(it*1_000_000L)}
        assertTrue("Lesson opening must not cross its final size",samples.max()<=1.0001f)
        assertTrue("Lesson opening should move towards its target",samples.zipWithNext().all{(a,b)->b>=a-.0001f})
        assertEquals(1f,motion.getValueFromNanos(1_000_000_000L),.001f)
    }
    @Test fun lessonClosingDoesNotBounceVisiblyPastItsCard() {
        val motion=TargetBasedAnimation(CourseMorphMotion.close(lesson,false),Float.VectorConverter,1f,0f)
        val trough=(0..1000 step 2).minOf{motion.getValueFromNanos(it*1_000_000L)}
        assertTrue(trough>-.01f)
        assertEquals(0f,motion.getValueFromNanos(1_000_000_000L),.001f)
    }

    @Test fun everyOpeningSettlesWithoutALongVisibleTail() {
        for(kind in kinds) {
            val animation=motion(CourseMorphMotion.open(kind),0f,1f)
            val samples=(0..700 step 2).map{animation.getValueFromNanos(it*1_000_000L)}
            if(kind is CourseMorphContent.Lesson)
                assertTrue("Lesson opening should not overshoot",samples.max()<=1.0001f)
            else {
                assertTrue("Other openings retain their soft spring",samples.max()>1f)
                assertTrue("Opening overshoot must stay below 1%",samples.max()<1.01f)
            }
            assertTrue("Opening must make useful progress without a first-frame jump",
                animation.getValueFromNanos(100_000_000L) in .55f.. .9f)
            assertTrue("Opening should be nearly at its final size after 200ms",
                animation.getValueFromNanos(200_000_000L)>.95f)
            assertTrue("There should be no visible oscillation after 400ms",
                (400..700 step 2).all{abs(animation.getValueFromNanos(it*1_000_000L)-1f)<.002f})
        }
    }

    @Test fun normalReturnsSettleWithoutShrinkingVisiblyPastTheirSources() {
        for(kind in kinds) {
            val animation=motion(CourseMorphMotion.close(kind,false),1f,0f)
            val limit=if(kind is CourseMorphContent.Lesson).001f else .005f
            assertTrue("Closing should not bounce visibly through its source",
                (0..600 step 2).minOf{animation.getValueFromNanos(it*1_000_000L)}> -limit)
            assertTrue("Return should settle promptly",
                (350..600 step 2).all{abs(animation.getValueFromNanos(it*1_000_000L))<.002f})
        }
    }

    @Test fun replacingAWindowReturnsFasterWithoutAnExtraBounce() {
        for(kind in kinds) {
            val regular=motion(CourseMorphMotion.close(kind,false),1f,0f)
            val replacing=motion(CourseMorphMotion.close(kind,true),1f,0f)
            assertTrue("Replacement must release the previous window sooner",replacing.durationNanos<regular.durationNanos)
            assertTrue("Accelerated return must stay controlled",
                (0..600 step 2).minOf{replacing.getValueFromNanos(it*1_000_000L)}>-.002f)
            assertTrue("Replacement return must have no visible tail after 250ms",
                (250..600 step 2).all{abs(replacing.getValueFromNanos(it*1_000_000L))<.002f})
        }
    }

    @Test fun closingAndReopeningCarryTheirCurrentPositionAndVelocity() {
        for(kind in kinds) {
            val opening=motion(CourseMorphMotion.open(kind),0f,1f)
            val openingAt=80_000_000L
            val fromOpening=opening.getValueFromNanos(openingAt)
            val openingVelocity=opening.getVelocityVectorFromNanos(openingAt).value
            val closing=motion(CourseMorphMotion.close(kind,false),fromOpening,0f,openingVelocity)
            assertEquals(fromOpening,closing.getValueFromNanos(0),.00001f)
            assertEquals(openingVelocity,closing.getVelocityVectorFromNanos(0).value,.0001f)
            assertTrue("An interrupted spring must not snap its existing momentum",
                closing.getValueFromNanos(1_000_000L)>fromOpening)

            val closingAt=100_000_000L
            val fromClosing=closing.getValueFromNanos(closingAt)
            val closingVelocity=closing.getVelocityVectorFromNanos(closingAt).value
            val reopening=motion(CourseMorphMotion.open(kind),fromClosing,1f,closingVelocity)
            assertTrue(closingVelocity<0f)
            assertEquals(fromClosing,reopening.getValueFromNanos(0),.00001f)
            assertEquals(closingVelocity,reopening.getVelocityVectorFromNanos(0).value,.0001f)
            assertTrue("Reopening continues the returning velocity before turning around",
                reopening.getValueFromNanos(1_000_000L)<fromClosing)
            assertEquals(1f,reopening.getValueFromNanos(800_000_000L),.001f)
        }
    }
}

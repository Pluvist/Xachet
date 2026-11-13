package com.pluvist.sachet.next

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PageTitleMotionTest {
    @Test fun endpointsAndReversalsStayContinuous() {
        val a=Offset(300f,1200f);val b=Offset(50f,200f)
        assertEquals(a,pageTitlePosition(a,b,0f))
        assertEquals(b,pageTitlePosition(a,b,1f))
        var previous=a
        for(i in 1..100) {
            val current=pageTitlePosition(a,b,i/100f)
            assertTrue(current.x<=previous.x && current.y<=previous.y)
            assertTrue((current-previous).getDistance()<12f)
            previous=current
        }
        val before=pageTitlePosition(a,b,.53f)
        val after=pageTitlePosition(a,b,.5299f)
        assertTrue((before-after).getDistance()<.2f)
    }
    private fun flight()=PageTitleFlight(PageTitleSnapshot("设置",TextStyle.Default,Rect(20f,600f,120f,640f)))

    @Test fun titleAndWindowMustBothArriveBeforeNativeTextTakesOver() {
        for(titleFirst in listOf(true,false)) {
            val f=flight()
            if(titleFirst)f.finishTitle()else f.finishWindow()
            assertTrue(f.drawsOverlay)
            assertEquals(0f,f.nativeAlpha,0f)
            if(titleFirst)f.finishWindow()else f.finishTitle()
            assertFalse(f.drawsOverlay)
            assertEquals(1f,f.nativeAlpha,0f)
            f.begin(false)
            assertTrue(f.drawsOverlay)
            assertEquals(0f,f.nativeAlpha,0f)
            f.finishWindow();f.finishTitle()
            // Keep the returning text visible until its source is restored.
            assertTrue(f.drawsOverlay)
        }
    }

    @Test fun reversingTitleDoesNotResetItsIndependentProgress()=runBlocking {
        val f=flight()
        f.motion.snapTo(.46f)
        f.begin(false)
        assertEquals(.46f,f.motion.value,0f)
        f.begin(true)
        assertEquals(.46f,f.motion.value,0f)
        assertTrue(f.drawsOverlay)
    }

    @Test fun pageWithoutSourceKeepsItsNativeTitle() {
        val f=PageTitleFlight(null)
        f.begin(false)
        assertFalse(f.drawsOverlay)
        assertEquals(1f,f.nativeAlpha,0f)
    }
}

package com.pluvist.sachet.next

import androidx.compose.ui.geometry.Rect
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion
import org.junit.Assert.*
import org.junit.Test

class ProvisionMotionTest {
    @Test fun arrowAndStartButtonReachWholeViewportWithoutResidualCorners() {
        for (target in listOf(Rect(0f, 0f, 1216f, 2640f), Rect(0f, 0f, 2640f, 1216f))) {
            for (source in listOf(Rect(500f, 800f, 710f, 1010f), Rect(80f, 900f, 1130f, 1080f))) {
                val motion = LiquidMenuMorphMotion()
                motion.initialize(source, target, 0f, 3f)
                motion.retarget(true); motion.advanceBy(5f)
                assertEquals(target, motion.frame.bounds)
                assertEquals(0f, motion.frame.radiusX, .001f)
                assertEquals(1f, motion.frame.contentProgress, .001f)
            }
        }
    }

    @Test fun repeatedBackAndForwardPreserveLiveGeometry() {
        val motion = LiquidMenuMorphMotion()
        motion.initialize(Rect(160f, 610f, 230f, 680f), Rect(0f, 0f, 390f, 844f), 0f)
        repeat(8) { index ->
            val before = motion.frame
            motion.retarget(index % 2 == 0)
            assertEquals(before.bounds, motion.frame.bounds)
            motion.advanceBy(.07f)
            val rect = motion.frame.bounds
            assertTrue(rect.width > 0 && rect.height > 0 && rect.left.isFinite() && rect.top.isFinite())
        }
        motion.retarget(true); motion.advanceBy(5f)
        assertEquals(Rect(0f, 0f, 390f, 844f), motion.frame.bounds)
    }
}

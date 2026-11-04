package com.pluvist.sachet.next

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop

/** Switch the footer's source at draw time, without recomposing the schedule. */
internal class CourseDynamicBackdrop(
    private val resting: Backdrop,
    private val expanded: Backdrop,
    private val expandedNow: () -> Boolean,
) : Backdrop {
    override val isCoordinatesDependent: Boolean =
        resting.isCoordinatesDependent || expanded.isCoordinatesDependent

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit {
        val stopResting = resting.observeSourceChanges(observer)
        val stopExpanded = expanded.observeSourceChanges(observer)
        return { stopResting(); stopExpanded() }
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(if (expandedNow()) expanded else resting) {
            drawBackdrop(density, coordinates, layerBlock)
        }
    }
}

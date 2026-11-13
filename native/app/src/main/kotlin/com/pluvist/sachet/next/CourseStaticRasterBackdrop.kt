package com.pluvist.sachet.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop

/** Samples the click-time unblurred GPU layer in screen space. The glass applies
 * its own frost while its outline and refraction remain live. */
internal class CourseStaticRasterBackdrop(
    private val raster: CourseBackgroundRasterState,
    private val epoch: State<Int>,
    private val sceneRevision: Any,
    private val fallback: Backdrop,
) : Backdrop {
    var sourceCoordinates: LayoutCoordinates? = null
    override val isCoordinatesDependent = true
    private val sourceToConsumer = Matrix()
    private val screenToConsumer = Matrix()

    val ready: Boolean
        get() {
            val source = sourceCoordinates?.takeIf { it.isAttached } ?: return false
            val capture = raster.current ?: return false
            return capture.key.epoch == epoch.value && capture.key.sceneRevision === sceneRevision &&
                capture.key.size == source.size
        }

    override fun observeSourceChanges(observer: () -> Unit): () -> Unit = fallback.observeSourceChanges(observer)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val source = sourceCoordinates?.takeIf { it.isAttached }
        val consumer = coordinates?.takeIf { it.isAttached }
        val capture = raster.current
        if (source == null || consumer == null || capture == null ||
            capture.key.epoch != epoch.value || capture.key.sceneRevision !== sceneRevision ||
            capture.key.size != source.size || capture.key.density != density.density ||
            capture.key.fontScale != density.fontScale
        ) {
            with(fallback) { drawBackdrop(density, coordinates, layerBlock) }
            return
        }

        // Match the glass's animated position to the fixed page coordinates.
        sourceToConsumer.reset()
        try {
            if (source.findRootCoordinates() === consumer.findRootCoordinates()) {
                consumer.transformFrom(source, sourceToConsumer)
            } else {
                screenToConsumer.reset()
                source.transformToScreen(sourceToConsumer)
                consumer.transformToScreen(screenToConsumer)
                screenToConsumer.invert()
                sourceToConsumer *= screenToConsumer
            }
        } catch (_: Exception) {
            with(fallback) { drawBackdrop(density, coordinates, layerBlock) }
            return
        }
        if (!sourceToConsumer.values.all { it.isFinite() }) {
            with(fallback) { drawBackdrop(density, coordinates, layerBlock) }
            return
        }
        val logicalSize = capture.key.size
        withTransform({
            transform(sourceToConsumer)
            scale(logicalSize.width.toFloat() / capture.key.sampleSize.width,
                logicalSize.height.toFloat() / capture.key.sampleSize.height, Offset.Zero)
        }) {
            val image=capture.image
            if(image!=null)withTransform({
                scale(capture.key.sampleSize.width.toFloat()/logicalSize.width,
                    capture.key.sampleSize.height.toFloat()/logicalSize.height,Offset.Zero)
            }) { drawImage(image) } else drawLayer(capture.layer)
        }
    }
}

@Composable internal fun rememberCourseStaticRasterBackdrop(
    raster: CourseBackgroundRasterState,
    epoch: State<Int>,
    sceneRevision: Any,
    fallback: Backdrop,
): CourseStaticRasterBackdrop = remember(raster, epoch, sceneRevision, fallback) {
    CourseStaticRasterBackdrop(raster, epoch, sceneRevision, fallback)
}

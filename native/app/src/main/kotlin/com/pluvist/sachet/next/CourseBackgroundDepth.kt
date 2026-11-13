package com.pluvist.sachet.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

internal data class CourseBackgroundCaptureKey(
    val epoch:Int,
    val sceneRevision:Any,
    val size:IntSize,
    val sampleSize:IntSize,
    val density:Float,
    val fontScale:Float,
)
/** One click-time GPU capture for the panel's liquid glass. */
internal class CourseBackgroundRaster(val key:CourseBackgroundCaptureKey,val layer:GraphicsLayer) {
    var image by mutableStateOf<ImageBitmap?>(null)
}

/** Read [current] inside the backdrop draw callback. */
@Stable internal class CourseBackgroundRasterState {
    var current by mutableStateOf<CourseBackgroundRaster?>(null)
        internal set
}

/** Capture a separate optical source while the visible page keeps its position. */
@Composable internal fun rememberCourseBackgroundCapture(
    captureEpoch:State<Int>,sceneRevision:Any,
    pageLayer:GraphicsLayer,
    rasterState:CourseBackgroundRasterState=remember{CourseBackgroundRasterState()},
):Modifier {
    val panelCapture=rememberGraphicsLayer()
    val captureDensity=androidx.compose.ui.platform.LocalDensity.current
    val captureDirection=androidx.compose.ui.platform.LocalLayoutDirection.current
    DisposableEffect(rasterState){onDispose{rasterState.current=null}}
    val capture=rasterState.current
    val activeEpoch=captureEpoch.value
    LaunchedEffect(capture,activeEpoch) {
        if(activeEpoch<0) {
            rasterState.current=null
            panelCapture.record(captureDensity,captureDirection,IntSize(1,1)){}
        } else if(capture!=null&&capture.key.epoch==activeEpoch) {
            // A recorded layer still retains the timetable's entire RenderNode tree.
            // Materialize it once, asynchronously; the spring starts immediately with
            // the existing layer and switches to immutable pixels when they are ready.
            try {
                val image=pageLayer.toImageBitmap()
                if(rasterState.current===capture)capture.image=image
            } catch(cancelled:kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch(_:Exception) {
                // Keep the live layer fallback on devices that cannot snapshot it.
            }
        }
    }
    return remember(captureEpoch,sceneRevision,pageLayer,panelCapture,rasterState) {
        Modifier.drawWithCache {
            var recordedEpoch=Int.MIN_VALUE
            val sampleScale=.25f
            val originalSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1))
            val smallSize=IntSize(ceil(size.width*sampleScale).toInt().coerceAtLeast(1),ceil(size.height*sampleScale).toInt().coerceAtLeast(1))
            panelCapture.compositingStrategy=CompositingStrategy.Offscreen
            onDrawWithContent {
                val epoch=captureEpoch.value
                if(epoch<0 || pageLayer.size!=originalSize) {
                    recordedEpoch=Int.MIN_VALUE
                } else if(recordedEpoch!=epoch) {
                    val key=CourseBackgroundCaptureKey(epoch,sceneRevision,originalSize,smallSize,density,fontScale)
                    // Reuse the existing page display list for the glass sample.
                    panelCapture.record(smallSize){scale(sampleScale,sampleScale,Offset.Zero){drawLayer(pageLayer)}}
                    recordedEpoch=epoch
                    rasterState.current=CourseBackgroundRaster(key,panelCapture)
                }
                drawContent()
            }
        }
    }
}

package com.pluvist.sachet.next

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.glass.MultiSampleBlurRenderer
import kotlin.math.ceil

internal object CourseBackgroundGeometry {
    fun scale(fraction:Float)=1f-.015f*fraction
    fun canonicalTap(point:Offset,size:Size,progress:Float,sinkPixels:Float):Offset {
        val fraction=CourseMorphGeometry.smooth(progress)
        val factor=scale(fraction)
        val pivot=Offset(size.width*.5f,size.height*.5f)
        return (point-pivot-Offset(0f,sinkPixels*fraction))/factor+pivot
    }
}

internal data class CourseBackgroundCaptureKey(
    val epoch:Int,
    val sceneRevision:Any,
    val size:IntSize,
    val sampleSize:IntSize,
    val density:Float,
    val fontScale:Float,
)
/** One click-time GPU capture of the page at quarter resolution.
 * The panel applies its own frost to this sharp capture; the background depth
 * pass uses a separate blurred replay of the same pixels. */
internal data class CourseBackgroundRaster(val key:CourseBackgroundCaptureKey,val layer:GraphicsLayer)

/** Read [current] inside the backdrop draw callback. */
@Stable internal class CourseBackgroundRasterState {
    var current by mutableStateOf<CourseBackgroundRaster?>(null)
        internal set
}

/** A visual-only depth pass for the page behind a course window. Source layout
 * coordinates stay fixed, so returning panels still land on their original bar.
 * The low-resolution blur is blended over the sharp scene; text/icons in the
 * foreground panel never enter this capture or filter. */
@Composable internal fun rememberCourseBackgroundDepth(
    progress:State<Float>,captureEpoch:State<Int>,sceneRevision:Any,
    pageLayer:GraphicsLayer,
    rasterState:CourseBackgroundRasterState=remember{CourseBackgroundRasterState()},
):Modifier {
    val softened=rememberGraphicsLayer()
    val panelCapture=rememberGraphicsLayer()
    val softenedReplay=rememberGraphicsLayer()
    val softenedBlend=rememberGraphicsLayer()
    val sampler=remember{MultiSampleBlurRenderer()}
    DisposableEffect(rasterState){onDispose{rasterState.current=null}}
    return remember(progress,captureEpoch,sceneRevision,pageLayer,panelCapture,softened,softenedReplay,softenedBlend,sampler,rasterState) {
        Modifier.drawWithCache {
            var recordedEpoch=Int.MIN_VALUE
            val sampleScale=.25f
            val originalSize=IntSize(size.width.toInt().coerceAtLeast(1),size.height.toInt().coerceAtLeast(1))
            val sinkPixels=8.dp.toPx()
            val pivot=Offset(size.width*.5f,size.height*.5f)
            val smallSize=IntSize(ceil(size.width*sampleScale).toInt().coerceAtLeast(1),ceil(size.height*sampleScale).toInt().coerceAtLeast(1))
            softened.compositingStrategy=CompositingStrategy.Offscreen
            softened.alpha=1f
            panelCapture.compositingStrategy=CompositingStrategy.Offscreen
            panelCapture.renderEffect=null
            softenedReplay.compositingStrategy=CompositingStrategy.Offscreen
            softenedReplay.renderEffect=null
            softenedBlend.compositingStrategy=CompositingStrategy.Offscreen
            softened.renderEffect=sampler.effect(1.5.dp.toPx()*sampleScale,
                width=smallSize.width.toFloat(),height=smallSize.height.toFloat())?.asComposeRenderEffect()
            onDrawWithContent {
                val fraction=CourseMorphGeometry.smooth(progress.value)
                val epoch=captureEpoch.value
                if(epoch<0 || pageLayer.size!=originalSize) {
                    drawContent()
                } else {
                    val key=CourseBackgroundCaptureKey(epoch,sceneRevision,originalSize,smallSize,density,fontScale)
                    if(recordedEpoch!=epoch) {
                        // The page is already retained for the footer's glass.
                        // Reuse that display list instead of recording the full
                        // schedule a second time on the opening frame.
                        panelCapture.record(smallSize){scale(sampleScale,sampleScale,Offset.Zero){drawLayer(pageLayer)}}
                        softened.record(smallSize){drawLayer(panelCapture)}
                        softenedReplay.record(smallSize){drawLayer(softened)}
                        softenedBlend.record(smallSize){drawLayer(softenedReplay)}
                        recordedEpoch=epoch
                        rasterState.current=CourseBackgroundRaster(key,panelCapture)
                    }
                    if(fraction<=.001f)drawLayer(pageLayer)else {
                        withTransform({
                            translate(0f,sinkPixels*fraction)
                            scale(CourseBackgroundGeometry.scale(fraction),CourseBackgroundGeometry.scale(fraction),pivot)
                        }) {
                            drawLayer(pageLayer)
                            withTransform({scale(1f/sampleScale,1f/sampleScale,Offset.Zero)}) {
                                softenedBlend.alpha=fraction
                                drawLayer(softenedBlend)
                            }
                        }
                    }
                }
            }
        }
    }
}

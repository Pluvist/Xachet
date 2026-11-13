package com.pluvist.sachet.next

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import io.github.neoruaa.glasmio.core.LocalGlassColors
import kotlin.math.round
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class PageTitleSnapshot(val text:String,val style:TextStyle,val bounds:Rect)

/** Resolve transformed source bounds only on navigation, not for every scroll frame. */
internal class PageTitleSource(var coordinates:LayoutCoordinates,val text:String,val style:TextStyle) {
    fun snapshot():PageTitleSnapshot? {
        if(!coordinates.isAttached)return null
        val bounds=coordinates.findRootCoordinates().localBoundingBoxOf(coordinates,clipBounds=false)
        return PageTitleSnapshot(text,style,bounds).takeIf{bounds.width>0f&&bounds.height>0f}
    }
}

internal fun pageTitlePosition(source:Offset,target:Offset,p:Float):Offset {
    val t=p.coerceIn(0f,1f)
    // The title has its own spring; page resizing never drives this position.
    return Offset(source.x+(target.x-source.x)*(t*t*(3f-2f*t)),source.y+(target.y-source.y)*t)
}

internal class PageTitleFlight(val source:PageTitleSnapshot?) {
    private class Target(val layer:GraphicsLayer) {
        var coordinates:LayoutCoordinates?=null
        var text=""
        var style=TextStyle.Default
        var visibility=0f
    }
    private var root:LayoutCoordinates?=null
    private val targets=linkedMapOf<GraphicsLayer,Target>()
    val motion=Animatable(0f)
    private var opening by mutableStateOf(true)
    private var windowSettled by mutableStateOf(false)
    private var titleSettled by mutableStateOf(false)
    var target by mutableStateOf<PageTitleSnapshot?>(null)
        private set
    val drawsOverlay:Boolean get()=source!=null&&!(opening&&windowSettled&&titleSettled)
    val nativeAlpha:Float get()=if(drawsOverlay)0f else 1f
    fun begin(open:Boolean) {
        opening=open;windowSettled=false;titleSettled=false
        updateNativeAlpha()
    }
    fun finishWindow(){windowSettled=true;updateNativeAlpha()}
    fun finishTitle(){titleSettled=true;updateNativeAlpha()}
    private fun updateNativeAlpha(){targets.keys.forEach{it.alpha=nativeAlpha}}
    suspend fun animateTo(open:Boolean) {
        if(source==null)return
        if(open&&target==null)withTimeoutOrNull(300){snapshotFlow{target}.filterNotNull().first()}
        motion.animateTo(if(open)1f else 0f,
            spring(dampingRatio=.9f,stiffness=430f,visibilityThreshold=.0005f))
        finishTitle()
    }
    fun register(layer:GraphicsLayer){targets.getOrPut(layer){Target(layer)};layer.alpha=nativeAlpha}
    fun remove(layer:GraphicsLayer){targets.remove(layer);update()}
    fun setRoot(value:LayoutCoordinates){root=value;update()}
    fun place(layer:GraphicsLayer,value:LayoutCoordinates){targets.getOrPut(layer){Target(layer)}.coordinates=value;update()}
    fun describe(layer:GraphicsLayer,text:String,style:TextStyle,visibility:Float) {
        targets.getOrPut(layer){Target(layer)}.also{it.text=text;it.style=style;it.visibility=visibility}
        update()
    }
    private fun update() {
        // Freeze the visible header's last layout for return. Closing pages are no
        // longer placed, but their title continues drawing in the independent overlay.
        if(!opening)return
        val parent=root?.takeIf{it.isAttached}?:return
        val chosen=targets.values.filter{it.coordinates?.isAttached==true&&it.visibility>.01f}
            .maxByOrNull{it.visibility}?:return
        val child=chosen.coordinates?:return
        val b=parent.localBoundingBoxOf(child,clipBounds=false)
        // Cancel tiny inverse-transform noise while the parent window animates.
        fun snap(x:Float)=round(x*4f)/4f
        val bounds=Rect(snap(b.left),snap(b.top),snap(b.right),snap(b.bottom))
        if(bounds.width>0f&&bounds.height>0f)target=PageTitleSnapshot(chosen.text,chosen.style,bounds)
    }
}

@Composable internal fun Modifier.pageTitleSource(key:String,text:String,style:TextStyle):Modifier {
    val registry=LocalPageOrigins.current?:return this
    val resolved=style.copy(color=LocalGlassColors.current.content)
    val holder=remember(registry,key,text,resolved){arrayOfNulls<PageTitleSource>(1)}
    return onGloballyPositioned { coordinates ->
        val current=holder[0]
        if(current==null)holder[0]=PageTitleSource(coordinates,text,resolved).also{registry.titles[key]=it}
        else current.coordinates=coordinates
    }.graphicsLayer { alpha=if(registry.flyingTitles[key]==true)0f else 1f }
}

@Composable internal fun Modifier.pageTitleTarget(flight:PageTitleFlight,text:String,style:TextStyle,visibility:Float):Modifier {
    if(flight.source==null)return this
    val layer=rememberGraphicsLayer()
    DisposableEffect(flight,layer){flight.register(layer);onDispose{flight.remove(layer)}}
    SideEffect{flight.describe(layer,text,style,visibility)}
    return onGloballyPositioned{flight.place(layer,it)}.drawWithContent {
        // The page replay retains this layer. Changing its alpha also hides a title
        // in an already-recorded closing page, without waiting for another layout.
        layer.alpha=flight.nativeAlpha
        layer.record{this@drawWithContent.drawContent()}
        drawLayer(layer)
    }
}

@Composable internal fun PageTitleOverlay(flight:PageTitleFlight) {
    val source=flight.source?:return
    val measurer=rememberTextMeasurer(cacheSize=8)
    Box(Modifier.fillMaxSize().drawWithCache {
        val target=flight.target
        if(!flight.drawsOverlay)onDrawBehind{} else {
            val a=measurer.measure(AnnotatedString(source.text),source.style,
                constraints=Constraints(maxWidth=source.bounds.width.toInt().coerceAtLeast(1)),maxLines=2,overflow=TextOverflow.Ellipsis)
            if(target==null)onDrawBehind{drawText(a,topLeft=source.bounds.topLeft)}else {
            val b=measurer.measure(AnnotatedString(target.text),target.style,
                constraints=Constraints(maxWidth=target.bounds.width.toInt().coerceAtLeast(1)),maxLines=10,overflow=TextOverflow.Ellipsis)
            val ratio=(target.style.fontSize.value/source.style.fontSize.value).takeIf{it.isFinite()&&it>0f}?:1f
            onDrawBehind {
                val p=flight.motion.value.coerceIn(0f,1f)
                    val position=pageTitlePosition(source.bounds.topLeft,target.bounds.topLeft,p)
                    val scale=1f+(ratio-1f)*p
                    val mix=((p-.15f)/.7f).coerceIn(0f,1f)
                    if(mix<1f)withTransform({translate(position.x,position.y);scale(scale,scale,Offset.Zero)}) {
                        drawText(a,alpha=1f-mix)
                    }
                    if(mix>0f)withTransform({translate(position.x,position.y);scale(scale/ratio,scale/ratio,Offset.Zero)}) {
                        drawText(b,alpha=mix)
                    }
            }
            }
        }
    })
}

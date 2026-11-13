package com.pluvist.sachet.next

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.*
import io.github.neoruaa.glasmio.core.G2RoundedRectangle
import io.github.neoruaa.glasmio.glass.LiquidMenuContour
import io.github.neoruaa.glasmio.glass.LiquidMenuContourWorkspace
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphFrame
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val liquidShortcutRoutes = setOf("classrooms", "cultivation", "textbooks", "exams", "grades", "gpa", "transcript")

/** Coordinates are plain layout metadata; updating them never recomposes the page. */
internal data class PageOrigin(val bounds:Rect,val radius:Float,val rootSize:Size) {
    fun inViewport(size:Size):Rect {
        val sx=size.width/rootSize.width.coerceAtLeast(1f)
        val sy=size.height/rootSize.height.coerceAtLeast(1f)
        return Rect(bounds.left*sx,bounds.top*sy,bounds.right*sx,bounds.bottom*sy)
    }
}
@Stable internal class PageOrigins {
    private class Source(var coordinates:LayoutCoordinates,var radius:Float,var last:PageOrigin?=null)
    private val sources=mutableMapOf<String,Source>()
    internal val titles=mutableMapOf<String,PageTitleSource>()
    internal val flyingTitles=mutableStateMapOf<String,Boolean>()
    fun register(key:String,coordinates:LayoutCoordinates,radius:Float) {
        val source=sources[key]
        if(source==null)sources[key]=Source(coordinates,radius)
        else {source.coordinates=coordinates;source.radius=radius}
    }
    fun get(key:String):PageOrigin? {
        val source=sources[key]?:return null
        val coordinates=source.coordinates
        if(coordinates.isAttached) {
            val root=coordinates.findRootCoordinates()
            val rect=root.localBoundingBoxOf(coordinates,clipBounds=false)
            val size=root.size.toSize()
            if(rect.width>1f&&rect.height>1f) {
                val previous=source.last
                if(previous==null||previous.bounds!=rect||previous.radius!=source.radius||previous.rootSize!=size)
                    source.last=PageOrigin(rect,source.radius,size)
            }
        }
        return source.last
    }
}
internal val LocalPageOrigins=staticCompositionLocalOf<PageOrigins?>{null}

/** Attach to the complete clickable surface, before its content padding. */
@Composable internal fun Modifier.pageOrigin(key:String,radius:Dp=24.dp):Modifier {
    val registry=LocalPageOrigins.current
    val density=LocalDensity.current
    return if(registry==null)this else onGloballyPositioned { coordinates ->
        // Root-tab scaling reports global placement every frame. Retain coordinates here,
        // and calculate the full transform only for a page that actually flies.
        registry.register(key,coordinates,with(density){radius.toPx()})
    }
}

@Stable private class PageFlight(val route:String,val origin:PageOrigin?,val title:PageTitleFlight) {
    var open by mutableStateOf(true)
        private set
    fun request(open:Boolean){if(this.open!=open){this.open=open;title.begin(open)}}
    val progress=Animatable(0f)
    val liquid=if(route in liquidShortcutRoutes && origin!=null)LiquidMenuMorphMotion() else null
}

@Immutable private data class FlightClip(val bounds:Rect,val radiusPx:Float):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline {
        val outline=G2RoundedRectangle(with(density){radiusPx.toDp()}).createOutline(bounds.size,layoutDirection,density)
        return Outline.Generic(Path().apply {addOutline(outline);translate(bounds.topLeft)})
    }
}

private data class LiquidPageGeometry(val window:Rect,val contour:LiquidMenuContour)

/** Measurement, placement and drawing observe the same spring frame. Reuse its
 * contour metadata instead of rebuilding both rounded fields in every phase. */
private class LiquidPageGeometryCache {
    private val workspace=LiquidMenuContourWorkspace()
    private var previousFrame:LiquidMenuMorphFrame?=null
    private var previousOrigin:PageOrigin?=null
    private var previousSize=Size.Zero
    private var cached:LiquidPageGeometry?=null

    fun get(frame:LiquidMenuMorphFrame,origin:PageOrigin,size:Size):LiquidPageGeometry {
        if(previousFrame===frame&&previousOrigin==origin&&previousSize==size)return requireNotNull(cached)
        val source=origin.inViewport(size)
        val lobe=frame.sourceLobe
        val sourceScale=if(lobe==null)1f else minOf(lobe.width/source.width.coerceAtLeast(1f),lobe.height/source.height.coerceAtLeast(1f))
        val radius=origin.radius*minOf(size.width/origin.rootSize.width.coerceAtLeast(1f),
            size.height/origin.rootSize.height.coerceAtLeast(1f))*sourceScale
        val radii=Offset(frame.radiusX,frame.radiusY)
        val sourceRadii=Offset(radius,radius)
        val exterior=LiquidMenuContour(frame.bounds,radii,lobe,sourceRadii,frame.smoothing).exteriorBounds()
        val window=Rect(exterior.left.coerceIn(0f,size.width-1f),exterior.top.coerceIn(0f,size.height-1f),
            exterior.right.coerceIn(1f,size.width),exterior.bottom.coerceIn(1f,size.height))
        val result=LiquidPageGeometry(window,LiquidMenuContour(frame.bounds.translate(-window.topLeft),radii,
            lobe?.translate(-window.topLeft),sourceRadii,frame.smoothing,workspace))
        previousFrame=frame;previousOrigin=origin;previousSize=size;cached=result
        return result
    }
}

/** Each destination owns a cancellable spring and a replay layer. Closing pages are replayed
 * without placing their content, so they cannot intercept a new click underneath. Multiple
 * flights can therefore finish independently, and reopening reverses the existing spring. */
@Composable internal fun AnchoredPages(destination:String?,origins:PageOrigins,content:@Composable (String)->Unit) {
    val flights=remember{mutableStateListOf<PageFlight>()}
    LaunchedEffect(destination) {
        flights.forEach{it.request(it.route==destination)}
        if(destination!=null) {
            val existing=flights.firstOrNull{it.route==destination}
            if(existing==null) {
                val title=PageTitleFlight(origins.titles[destination]?.snapshot())
                flights.add(PageFlight(destination,origins.get(destination),title))
            }
            else {flights.remove(existing);flights.add(existing)}
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val fullSize=IntSize(constraints.maxWidth,constraints.maxHeight)
    for(flight in flights.toList()) key(flight) {
        DisposableEffect(flight) {
            if(flight.title.source!=null)origins.flyingTitles[flight.route]=true
            onDispose { origins.flyingTitles.remove(flight.route) }
        }
        val snapshot=rememberGraphicsLayer()
        val density=LocalDensity.current
        val liquid=flight.liquid
        val liquidGeometry=remember{LiquidPageGeometryCache()}
        LaunchedEffect(flight.open,fullSize) {
            coroutineScope {
            launch{flight.title.animateTo(flight.open)}
            val size=fullSize.toSize()
            if(liquid!=null && size.width>0f && size.height>0f) {
                val source=(if(flight.open)flight.origin else origins.get(flight.route)?:flight.origin)!!.inViewport(size)
                val target=Rect(Offset.Zero,size)
                val radius=with(density){28.dp.toPx()}
                if(!liquid.initialized)liquid.initialize(source,target,radius,density.density)
                else {liquid.updateSource(source);liquid.updateTarget(target,radius)}
                liquid.animateTo(flight.open)
            } else {
                flight.progress.animateTo(if(flight.open)1f else 0f,
                    spring(dampingRatio=.9f,stiffness=360f,visibilityThreshold=.0005f))
            }
            flight.title.finishWindow()
            }
            // Keep the floating text until both independent springs return. The
            // source title is restored in the same composition that removes it.
            if(!flight.open)flights.remove(flight)
        }
        fun frame(size:Size):Rect {
            if(liquid?.initialized==true)return liquid.frame.bounds
            val origin=(if(flight.open)flight.origin else origins.get(flight.route)?:flight.origin)
            val source=origin?.inViewport(size)?:Rect(size.width*.4f,size.height*.45f,size.width*.6f,size.height*.55f)
            return androidx.compose.ui.geometry.lerp(source,Rect(Offset.Zero,size),flight.progress.value.coerceIn(0f,1f))
        }
        fun windowFrame(size:Size):Rect {
            if(liquid?.initialized!=true)return frame(size)
            return liquidGeometry.get(liquid.frame,flight.origin!!,size).window
        }
        Layout(content={
            Box(Modifier.then(if(flight.open)Modifier else Modifier.clearAndSetSemantics{}).graphicsLayer {
                // A clip does not need a separate changing-size color surface. Keep
                // content and glass in their existing layers instead of copying the
                // whole window into another texture on every spring frame.
                compositingStrategy=CompositingStrategy.Auto
                clip=true;shape=RectangleShape
            }.drawWithContent {
                if(liquid?.initialized==true) {
                    val geometry=liquidGeometry.get(liquid.frame,flight.origin!!,fullSize.toSize())
                    val path=geometry.contour.path(size)
                    clipPath(path) {this@drawWithContent.drawContent()}
                } else {
                    val p=flight.progress.value.coerceIn(0f,1f)
                    val visibleSize=frame(fullSize.toSize()).size
                    val radius=((flight.origin?.radius?:with(density){24.dp.toPx()})*(1f-p)+with(density){28.dp.toPx()}*p)
                        .coerceAtMost(visibleSize.minDimension/2f)
                    val outline=FlightClip(Rect(Offset.Zero,visibleSize),radius).createOutline(size,layoutDirection,this)
                    clipPath((outline as Outline.Generic).path) {this@drawWithContent.drawContent()}
                }
            }) {
                Layout(content={
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val full=fullSize.toSize()
                        val rect=frame(full)
                        val window=windowFrame(full)
                        transformOrigin=TransformOrigin(0f,0f)
                        val uniform=maxOf(rect.width/full.width.coerceAtLeast(1f),rect.height/full.height.coerceAtLeast(1f))
                        scaleX=uniform;scaleY=uniform
                        translationX=rect.left-window.left
                        translationY=rect.top-window.top
                        alpha=((if(liquid?.initialized==true)liquid.frame.contentProgress else flight.progress.value)*6f).coerceIn(0f,1f)
                    }.drawWithContent {
                        if(flight.open||snapshot.size==IntSize.Zero)drawContent()else drawLayer(snapshot)
                    }) {
                        Box(Modifier.fillMaxSize().onGloballyPositioned { flight.title.setRoot(it) }.layout { measurable,constraints ->
                            val child=measurable.measure(constraints)
                            layout(child.width,child.height){if(flight.open||snapshot.size==IntSize.Zero)child.place(0,0)}
                        }.drawWithContent {
                            snapshot.record{this@drawWithContent.drawContent()}
                            drawLayer(snapshot)
                        }) {
                            CompositionLocalProvider(io.github.neoruaa.glasmio.core.LocalPageTitleDecoration provides
                                { text,style,visibility -> Modifier.pageTitleTarget(flight.title,text,style,visibility) }) { content(flight.route) }
                        }
                    }
                }) { measurables,windowConstraints ->
                    // The screen's layout constraints never follow the animated window.
                    // Forms, text and hit regions keep their full-size layout and uniform
                    // scale; only this cheap outer container changes its raster bounds.
                    val full=fullSize
                    val page=measurables.single().measure(Constraints.fixed(full.width,full.height))
                    layout(windowConstraints.maxWidth,windowConstraints.maxHeight){page.place(0,0)}
                }
            }
        },modifier=Modifier.fillMaxSize()) { measurables,constraints ->
            val rect=windowFrame(fullSize.toSize())
            val window=measurables.single().measure(Constraints.fixed(
                kotlin.math.ceil(rect.width).toInt().coerceAtLeast(1),
                kotlin.math.ceil(rect.height).toInt().coerceAtLeast(1)))
            layout(fullSize.width,fullSize.height){window.place(rect.left.toInt(),rect.top.toInt())}
        }
        PageTitleOverlay(flight.title)
    }
    }
}

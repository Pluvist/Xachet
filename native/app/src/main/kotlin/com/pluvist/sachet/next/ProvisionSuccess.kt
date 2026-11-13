package com.pluvist.sachet.next

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import io.github.neoruaa.glasmio.core.LocalGlassColors
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** Draw the supplied SMIL's foreground only, using native paths rather than a WebView.
 * All paths, keyframes and easing curves are parsed during the welcome page. */
internal class ProvisionSuccessArtwork private constructor(private val root: SvgNode) {
    fun draw(canvas: Canvas, width: Float, height: Float, time: Float, tint: Int) {
        canvas.save()
        val scale = minOf(width, height) / 180f
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-216f, -185f)
        root.draw(canvas, time, 1f, tint)
        canvas.restore()
    }
    companion object {
        fun read(parser: XmlPullParser): ProvisionSuccessArtwork {
            val root = SvgNode("g", emptyMap())
            val stack = mutableListOf(root)
            var skipped = 0
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    if (skipped > 0) { skipped++; continue }
                    val name = parser.name
                    if (name in setOf("defs", "filter") || parser.getAttributeValue(null, "id") == "i5") { skipped = 1; continue }
                    val attributes = (0 until parser.attributeCount).associate { parser.getAttributeName(it) to parser.getAttributeValue(it) }
                    when (name) {
                        "svg", "g", "path" -> {
                            val node = SvgNode(name, attributes)
                            stack.last().children.add(node); stack.add(node)
                        }
                        "animate", "animateTransform" -> stack.last().tracks[attributes.getValue("attributeName")] = SvgTrack(attributes)
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG) {
                    if (skipped > 0) { skipped--; continue }
                    if (parser.name in setOf("svg", "g", "path")) stack.removeAt(stack.lastIndex)
                }
            }
            root.prepare()
            return ProvisionSuccessArtwork(root)
        }
    }
}

private class SvgTrack(attributes: Map<String, String>) {
    val kind = attributes["type"]
    private val strings = attributes.getValue("values").split(';').map(String::trim)
    private val times = attributes.getValue("keyTimes").split(';').map { it.trim().toFloat() }
    private val easing = attributes["keySplines"].orEmpty().split(';').filter(String::isNotBlank).map {
        val points = it.trim().split(Regex("\\s+")).map(String::toFloat)
        CubicBezierEasing(points[0], points[1], points[2], points[3])
    }
    private val paths = if (attributes["attributeName"] == "d") strings.map { PathParser.createNodesFromPathData(it)!! } else null
    private val numbers = if (paths == null) strings.map { value -> value.split(Regex("[,\\s]+")).map { it.toFloatOrNull() ?: 0f } } else null
    private val workingPath = paths?.first()?.let(PathParser::deepCopyNodes)
    private var index = 0
    private var fraction = 0f
    fun at(time: Float) {
        index = (times.indexOfLast { it <= time }).coerceIn(0, times.size - 2)
        fraction = ((time - times[index]) / (times[index + 1] - times[index]).coerceAtLeast(.000001f)).coerceIn(0f, 1f)
        fraction = easing.getOrNull(index)?.transform(fraction) ?: fraction
    }
    fun value(axis: Int = 0): Float {
        val a = numbers!![index].getOrElse(axis) { 0f }; val b = numbers[index + 1].getOrElse(axis) { a }
        return a + (b - a) * fraction
    }
    fun path(output: Path) {
        val frames = paths ?: return
        PathParser.interpolatePathDataNodes(workingPath!!, frames[index], frames[index + 1], fraction)
        output.rewind(); PathParser.PathDataNode.nodesToPath(workingPath, output)
    }
}

private class SvgNode(private val type: String, attributes: Map<String, String>) {
    val children = mutableListOf<SvgNode>()
    val tracks = mutableMapOf<String, SvgTrack>()
    private val opacity = attributes["opacity"]?.toFloatOrNull() ?: 1f
    private val fillOpacity = attributes["fill-opacity"]?.toFloatOrNull() ?: 1f
    private val transform = attributes["transform"]?.let { value ->
        value.substringBefore('(') to value.substringAfter('(').substringBefore(')').split(Regex("[,\\s]+")).filter(String::isNotBlank).map(String::toFloat)
    }
    private val fill = attributes["fill"]?.takeIf { it != "none" }?.let(android.graphics.Color::parseColor)
    private val stroke = attributes["stroke"]?.let(android.graphics.Color::parseColor)
    private val strokeWidth = attributes["stroke-width"]?.toFloatOrNull() ?: 0f
    private val path = attributes["d"]?.let { PathParser.createPathFromPathData(it) } ?: Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun prepare() { children.forEach { it.prepare() }; tracks["d"]?.apply { at(.68f); path(path) } }
    fun draw(canvas: Canvas, time: Float, parentAlpha: Float, tint: Int) {
        tracks.values.forEach { it.at(time) }
        val alpha = parentAlpha * (tracks["opacity"]?.value() ?: opacity)
        if (alpha <= .001f) return
        canvas.save()
        val animatedTransform = tracks["transform"]
        val kind = animatedTransform?.kind ?: transform?.first
        fun axis(index: Int) = animatedTransform?.value(index) ?: transform?.second?.getOrNull(index) ?: 0f
        when (kind) { "translate" -> canvas.translate(axis(0), axis(1)); "rotate" -> canvas.rotate(axis(0)) }
        if (type == "path") {
            tracks["d"]?.path(path)
            fill?.let {
                paint.color = tint; paint.style = Paint.Style.FILL
                paint.alpha = (255 * alpha * (tracks["fill-opacity"]?.value() ?: fillOpacity)).toInt().coerceIn(0, 255)
                canvas.drawPath(path, paint)
            }
            stroke?.let {
                paint.color = tint; paint.style = Paint.Style.STROKE
                paint.strokeWidth = tracks["stroke-width"]?.value() ?: strokeWidth
                paint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
                canvas.drawPath(path, paint)
            }
        }
        children.forEach { it.draw(canvas, time, alpha, tint) }
        canvas.restore()
    }
}

@Composable internal fun rememberProvisionSuccess(): ProvisionSuccessArtwork? {
    val resources = LocalContext.current.resources
    val art by produceState<ProvisionSuccessArtwork?>(null, resources) {
        value = withContext(Dispatchers.Default) {
            resources.openRawResource(R.raw.provision_success).use { input ->
                val parser = android.util.Xml.newPullParser(); parser.setInput(input, "UTF-8")
                ProvisionSuccessArtwork.read(parser)
            }
        }
    }
    return art
}

@Composable internal fun ProvisionSuccessMark(art: ProvisionSuccessArtwork?, active: Boolean, modifier: Modifier = Modifier) {
    val tint = LocalGlassColors.current.accent.toArgb()
    val time = remember { Animatable(.09f) }
    LaunchedEffect(active, art) {
        if (active && art != null) {
            time.snapTo(.09f)
            // Speed up the complete foreground sequence without skipping its
            // SVG keyframes or the settled checkmark at the end of the range.
            time.animateTo(.68f, tween(900, easing = LinearEasing))
        }
    }
    Canvas(modifier.size(128.dp)) { art?.draw(drawContext.canvas.nativeCanvas, size.width, size.height, time.value, tint) }
}

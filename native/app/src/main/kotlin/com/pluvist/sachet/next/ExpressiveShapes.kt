@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle

/** Material 3 Expressive 10-step corner shape scale. */
internal val XachetShapes: Shapes = Shapes()

/** Maps UI feature keys to distinctive M3 Expressive geometric container shapes. */
internal fun expressiveFeaturePolygon(key: String): RoundedPolygon = when (key) {
    "cultivation" -> MaterialShapes.Cookie4Sided
    "textbooks" -> MaterialShapes.Clover4Leaf
    "exams" -> MaterialShapes.Sunny
    "grades" -> MaterialShapes.Pill
    "gpa" -> MaterialShapes.VerySunny
    "transcript" -> MaterialShapes.Cookie6Sided
    "library" -> MaterialShapes.ClamShell
    "physics" -> MaterialShapes.SoftBurst
    "network" -> MaterialShapes.Clover4Leaf
    "classrooms" -> MaterialShapes.Square
    else -> MaterialShapes.Circle
}

/**
 * Builds a normalized `[0..1]x[0..1]` Material 3 Expressive 4-sided [RoundedPolygon] matching
 * Compose corner order (`ShapesKt.rectangle` emits vertices `[bottomRight, bottomLeft, topLeft, topRight]`).
 */
internal fun expressiveQuadPolygon(
    topStart: CornerRounding,
    topEnd: CornerRounding,
    bottomEnd: CornerRounding,
    bottomStart: CornerRounding,
): RoundedPolygon = RoundedPolygon.rectangle(
    width = 1f,
    height = 1f,
    perVertexRounding = listOf(bottomEnd, bottomStart, topStart, topEnd),
).normalized()

/** Bento Left/Top ("当前课程") — In-Class Hero: large solid M3 circle. */
internal val BentoTodayInClassPolygon: RoundedPolygon by lazy(LazyThreadSafetyMode.NONE) {
    MaterialShapes.Circle.normalized()
}

/** Bento Left/Top ("当前课程") — Idle: compact resting circular silhouette. */
internal val BentoTodayIdlePolygon: RoundedPolygon by lazy(LazyThreadSafetyMode.NONE) {
    MaterialShapes.Circle.normalized()
}

/** Bento Right Top ("下一节课") — In-Class Compact: crisp fan squircle with prominent top-end curve. */
internal val BentoNextInClassPolygon: RoundedPolygon by lazy(LazyThreadSafetyMode.NONE) {
    expressiveQuadPolygon(
        topStart = CornerRounding(radius = 0.11f, smoothing = 0.60f),
        topEnd = CornerRounding(radius = 0.38f, smoothing = 0.90f),
        bottomEnd = CornerRounding(radius = 0.14f, smoothing = 0.60f),
        bottomStart = CornerRounding(radius = 0.11f, smoothing = 0.60f),
    )
}

/** Bento Right Top ("下一节课") — Idle Enlarged: sweeping right-side D-arch focus container. */
internal val BentoNextIdlePolygon: RoundedPolygon by lazy(LazyThreadSafetyMode.NONE) {
    expressiveQuadPolygon(
        topStart = CornerRounding(radius = 0.11f, smoothing = 0.55f),
        topEnd = CornerRounding(radius = 0.40f, smoothing = 0.92f),
        bottomEnd = CornerRounding(radius = 0.40f, smoothing = 0.92f),
        bottomStart = CornerRounding(radius = 0.11f, smoothing = 0.55f),
    )
}

/** [Shape] backed by an interpolated [Morph] between two normalized [RoundedPolygon]s. */
internal class MorphingPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val startAngle: Int = 0,
) : Shape {
    private val path = Path()
    private val matrix = Matrix()
    private val clampedProgress = progress.coerceIn(0f, 1f)

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        path.rewind()
        morph.toPath(progress = clampedProgress, path = path, startAngle = startAngle)
        matrix.reset()
        matrix.scale(x = size.width, y = size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MorphingPolygonShape) return false
        return morph === other.morph &&
            clampedProgress == other.clampedProgress &&
            startAngle == other.startAngle
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(morph)
        result = 31 * result + clampedProgress.hashCode()
        result = 31 * result + startAngle
        return result
    }
}

@Composable
internal fun rememberFeaturePolygonShape(key: String): Shape =
    expressiveFeaturePolygon(key).toShape()

@Composable
internal fun rememberMorphShape(
    from: RoundedPolygon,
    to: RoundedPolygon,
    progress: Float,
    startAngle: Int = 0,
): Shape {
    val morph = remember(from, to) { Morph(from, to) }
    return remember(morph, progress, startAngle) {
        MorphingPolygonShape(morph = morph, progress = progress, startAngle = startAngle)
    }
}

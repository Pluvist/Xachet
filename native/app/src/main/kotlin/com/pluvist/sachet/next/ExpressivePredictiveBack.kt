@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
internal class PredictiveBackVisualState {
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        internal set

    private var cachedCornerPx = -1f
    private var cachedCornerShape: RoundedCornerShape? = null

    fun shapeForCornerPx(cornerPx: Float): RoundedCornerShape {
        val prev = cachedCornerShape
        if (prev != null && abs(cornerPx - cachedCornerPx) < 0.75f) {
            return prev
        }
        val next = RoundedCornerShape(cornerPx)
        cachedCornerPx = cornerPx
        cachedCornerShape = next
        return next
    }
}

@Immutable
internal data class PredictiveBackMetrics(
    val scale: Float,
    val translationXFactor: Float,
    val cornerDp: Float,
)

internal fun computePredictiveBackMetrics(progress: Float, swipeEdge: Int): PredictiveBackMetrics {
    val p = progress.coerceIn(0f, 1f)
    val edgeDirection = if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    return PredictiveBackMetrics(
        scale = 1f - 0.12f * p,
        translationXFactor = edgeDirection * p,
        cornerDp = 28f * p,
    )
}

/**
 * Integrates system Android Predictive Back gestures into any composable route or sub-view.
 * While dragging, updates [PredictiveBackVisualState] directly inside `graphicsLayer` at 120Hz;
 * if cancelled, springs smoothly back using the Material 3 Expressive spatial spring.
 */
@Composable
internal fun rememberPredictiveBackState(
    enabled: Boolean,
    onBackCommitted: () -> Unit,
): PredictiveBackVisualState {
    val state = remember { PredictiveBackVisualState() }
    val scope = rememberCoroutineScope()
    val recoverySpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val latestCommit by rememberUpdatedState(onBackCommitted)

    LaunchedEffect(enabled) {
        if (!enabled && state.progress.value > 0.001f) {
            state.progress.animateTo(0f, animationSpec = recoverySpec)
        }
    }

    PredictiveBackHandler(enabled = enabled) { backEvents ->
        try {
            backEvents.collect { event ->
                state.swipeEdge = event.swipeEdge
                state.progress.snapTo(event.progress.coerceIn(0f, 1f))
            }
            latestCommit()
        } catch (e: CancellationException) {
            scope.launch {
                state.progress.animateTo(0f, animationSpec = recoverySpec)
            }
            throw e
        }
    }

    return state
}

internal fun Modifier.predictiveBackContainer(state: PredictiveBackVisualState): Modifier =
    this.graphicsLayer {
        val p = state.progress.value
        if (p > 0.001f) {
            val metrics = computePredictiveBackMetrics(p, state.swipeEdge)
            val cornerPx = (metrics.cornerDp * density)
            transformOrigin = TransformOrigin.Center
            scaleX = metrics.scale
            scaleY = metrics.scale
            translationX = metrics.translationXFactor * 22.dp.toPx()
            clip = cornerPx > 0.5f
            shape = state.shapeForCornerPx(cornerPx)
        }
    }

package com.pluvist.sachet.next

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.neoruaa.glasmio.core.LocalGlassColors
import kotlinx.coroutines.delay

/** Glasmio has no progress component. This small UIKit-style indicator uses its
 * semantic colors and suspends its 12 Hz ticker whenever the activity is hidden. */
@Composable internal fun XachetLoadingIndicator(modifier: Modifier = Modifier, color: Color = LocalGlassColors.current.secondaryContent) {
    var phase by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { delay(83); phase = (phase + 1) % 12 }
        }
    }
    Canvas(modifier.size(28.dp).semantics {
        contentDescription = "正在加载"; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }) {
        val radius = size.minDimension / 2
        repeat(12) { index ->
            rotate(index * 30f, center) {
                drawLine(color.copy(alpha = .18f + .82f * ((index - phase + 12) % 12) / 11),
                    Offset(center.x, center.y - radius * .48f), Offset(center.x, center.y - radius * .83f),
                    radius * .15f, StrokeCap.Round)
            }
        }
    }
}

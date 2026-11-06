package com.pluvist.sachet.next

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.IdentityHashMap

/** Depth shared by anchored menus. Menu geometry and page depth use matching springs. */
class AnchoredMenuDepthState {
    private val owners = IdentityHashMap<Any, Boolean>()
    private val motion = Animatable(0f)

    var target by mutableFloatStateOf(0f)
        private set

    // Read this only from graphics or draw layers. The spring can overshoot slightly.
    val progress: Float get() = motion.value

    fun setExpanded(owner: Any, expanded: Boolean) {
        if (expanded) owners[owner] = true else owners.remove(owner)
        target = if (owners.isEmpty()) 0f else 1f
    }

    fun remove(owner: Any) {
        if (owners.remove(owner) != null) target = if (owners.isEmpty()) 0f else 1f
    }

    internal suspend fun animateToTarget() {
        // Both directions are underdamped: the page passes the resting position
        // once, then settles. Animatable preserves velocity when a tap reverses it.
        motion.animateTo(target, spring(dampingRatio = .56f,
            stiffness = 438.64908f, visibilityThreshold = .0005f))
    }
}

@Composable
fun rememberAnchoredMenuDepthState(): AnchoredMenuDepthState {
    val state = remember { AnchoredMenuDepthState() }
    LaunchedEffect(state, state.target) { state.animateToTarget() }
    return state
}

val LocalAnchoredMenuDepth = staticCompositionLocalOf<AnchoredMenuDepthState?> { null }

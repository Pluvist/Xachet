@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * Helpers that bind app transitions to Material 3 Expressive motion physics:
 * - Spatial springs (`fastSpatialSpec`, `defaultSpatialSpec`, `slowSpatialSpec`) move bounds/position
 *   and may gently overshoot for playful expressiveness.
 * - Effects springs (`fastEffectsSpec`, `defaultEffectsSpec`, `slowEffectsSpec`) animate opacity/color
 *   with critical damping (`dampingRatio = 1f`) so alpha and color never flash past bounds.
 */
internal object ExpressiveMotion {
    @Composable
    fun <T> spatialFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastSpatialSpec()

    @Composable
    fun <T> spatialDefault(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultSpatialSpec()

    @Composable
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowSpatialSpec()

    @Composable
    fun <T> effectsFast(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.fastEffectsSpec()

    @Composable
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.defaultEffectsSpec()

    @Composable
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = MaterialTheme.motionScheme.slowEffectsSpec()

    fun directionalSlideTransform(motionScheme: MotionScheme, forward: Boolean): ContentTransform {
        val spatial = motionScheme.fastSpatialSpec<IntOffset>()
        val effects = motionScheme.fastEffectsSpec<Float>()
        val direction = if (forward) 1 else -1
        return (slideInHorizontally(animationSpec = spatial) { fullWidth -> direction * fullWidth / 5 } +
            fadeIn(animationSpec = effects)) togetherWith
            (slideOutHorizontally(animationSpec = spatial) { fullWidth -> -direction * fullWidth / 7 } +
                fadeOut(animationSpec = effects))
    }

    fun sharedAxisTransform(motionScheme: MotionScheme): ContentTransform {
        val effects = motionScheme.fastEffectsSpec<Float>()
        val scaleSpec = motionScheme.fastSpatialSpec<Float>()
        return (fadeIn(animationSpec = effects) +
            scaleIn(initialScale = 0.96f, animationSpec = scaleSpec)) togetherWith
            (fadeOut(animationSpec = effects) +
                scaleOut(targetScale = 0.98f, animationSpec = scaleSpec))
    }
}

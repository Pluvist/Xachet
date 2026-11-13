/* HyperCeiler glow/geometry adaptation, AGPL-3.0.
 * Copyright (C) 2023-2026 HyperCeiler Contributions; 2026 Pluvist.
 * Uses the original glow.glsl and GlowPainter uniforms. See third_party/hyperceiler/SOURCES.md. */
package com.pluvist.sachet.next

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable internal fun rememberProvisionShader(): RuntimeShader? {
    val resources = LocalContext.current.resources
    val shader by produceState<RuntimeShader?>(null, resources) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val original = resources.openRawResource(R.raw.provision_glow).bufferedReader().use { it.readText() }
                // Keep the exact upstream noise/time field in both appearances. Dark
                // mode emits coloured light over black instead of screening in white.
                val source = "uniform float uDarkMode;\n" + original.replace(
                    "vec3 finalColor = screenBlend(col2, vec3(v1));",
                    "vec3 finalColor = uDarkMode > 0.5 ? col2 * smoothstep(0.28, 0.80, v1) * 0.85 : screenBlend(col2, vec3(v1));")
                RuntimeShader(source).apply {
                    setFloatUniform("uDarkMode", 0f)
                    // Original GlowPainter defaults, including the opening ring.
                    mapOf(
                        "uScale2" to .82f, "uSpeed2" to .49f,
                        "uColorInMin" to .3f, "uColorInMax" to 1f, "uColorOutMin" to .3f, "uColorOutMax" to .86f,
                        "uColorMidPoint" to .47f, "uUseOklab" to 1f,
                        "uScale" to 1.3f, "uSpeed" to .4f,
                        "uBrightnessInMin" to .25f, "uBrightnessInMax" to 1f, "uBrightnessOutMin" to .25f, "uBrightnessOutMax" to 1f,
                        "uShowCircle" to 1f, "uCircleThickness" to .4f, "uCircleFinalRadius" to 1f, "uCircleYOffset" to .1f,
                        "uCircleSpeed" to .9f, "uCircleColorFreq" to 1f, "uCircleColorSpeed" to 0f, "uCircleEasing" to 1.4f,
                        "uCircleAnimationOffset" to 0f, "uMaskDelay" to .3f, "uMaskThickness" to .3f,
                        "uCircleScreenBlend" to 1f, "uCircleAddBlend" to .04f, "uCircleColorOffset" to .25f,
                        "uCircleUVDistort" to 0f, "uColorToDistortWidthRatio" to .6f,
                        "uDistortStartTime" to .2f, "uDistortEndTime" to .3f, "uDistortStart" to 0f, "uDistortEnd" to 1f,
                        "uStripeFrequency" to 0f, "uStripeStrengthX" to 0f, "uStripeStrengthY" to 0f, "uStripeUVDistort" to 0f,
                    ).forEach { (name, number) -> setFloatUniform(name, number) }
                    setFloatUniform("uColorBlack", .961f, .157f, .157f)
                    setFloatUniform("uColorMid", .604f, .659f, .961f)
                    setFloatUniform("uColorWhite", .302f, .29f, .843f)
                }
            }.onFailure { android.util.Log.w("ProvisionGlow", "Shader unavailable; using gradient fallback", it) }.getOrNull()
        }
    }
    return shader
}

@Composable internal fun ProvisionGlow(shader: RuntimeShader?, active: Boolean, admission: Boolean = true, dark: Boolean = false) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var time by remember { mutableFloatStateOf(if (admission) 0f else 6f) }
    var direction by remember { mutableFloatStateOf(1f) }
    val paint = remember(shader) { Paint().apply { this.shader = shader; isFilterBitmap = true } }
    LaunchedEffect(active, shader, lifecycle) {
        if (active && shader != null) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                time += ((now - previous) / 1_000_000_000f).coerceAtMost(.05f) * direction
                previous = now
                if (time >= 120f) direction = -1f
                else if (direction < 0f && time <= 2f) direction = 1f
            }
        }
    }
    // Upstream RenderViewLayout also samples at 0.2. Offscreen rasterization here
    // shades 1/25 of the display pixels; text remains full resolution above it.
    Canvas(Modifier.fillMaxSize().background(Brush.linearGradient(if (dark)
        listOf(Color(0xFF0D1D50), Color(0xFF251333), Color(0xFF071738))
        else listOf(Color(0xFFB4B8ED), Color(0xFFDFBBBB), Color(0xFFBAC4F5))))
        .layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val child = measurable.measure(Constraints.fixed((width * .2f).roundToInt().coerceAtLeast(1), (height * .2f).roundToInt().coerceAtLeast(1)))
            layout(width, height) {
                child.placeWithLayer(0, 0) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = width.toFloat() / child.width
                    scaleY = height.toFloat() / child.height
                    compositingStrategy = CompositingStrategy.Offscreen
                }
            }
        }) {
        if (shader != null) {
            shader.setFloatUniform("uDarkMode", if (dark) 1f else 0f)
            if (dark) {
                shader.setFloatUniform("uBrightnessOutMin", .0f)
                shader.setFloatUniform("uBrightnessOutMax", 1f)
                shader.setFloatUniform("uColorBlack", .025f, .16f, .68f)
                shader.setFloatUniform("uColorMid", .30f, .06f, .56f)
                shader.setFloatUniform("uColorWhite", .72f, .15f, .42f)
            } else {
                shader.setFloatUniform("uBrightnessOutMin", .25f)
                shader.setFloatUniform("uBrightnessOutMax", 1f)
                shader.setFloatUniform("uColorBlack", .961f, .157f, .157f)
                shader.setFloatUniform("uColorMid", .604f, .659f, .961f)
                shader.setFloatUniform("uColorWhite", .302f, .29f, .843f)
            }
            shader.setFloatUniform("uResolution", size.width, size.height)
            shader.setFloatUniform("uTime", time)
            shader.setFloatUniform("uShowCircle", if (admission && !dark) 1f else 0f)
            drawContext.canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

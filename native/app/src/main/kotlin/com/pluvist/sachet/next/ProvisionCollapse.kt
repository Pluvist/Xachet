package com.pluvist.sachet.next

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CollapseShader = """
uniform shader content;
uniform float2 resolution;
uniform float progress;
half4 main(float2 xy) {
    float p = clamp(progress, 0., 1.);
    float2 center = resolution * float2(.5, .37);
    float2 delta = xy - center;
    float sy = max(.015, 1. - .985 * p);
    float sx = max(.015, 1. - .985 * p);
    float2 source = center + delta / float2(sx, sy);
    float localY = (source.y - center.y) / resolution.y;
    float bend = sin(p * 3.141593) * .28;
    source.x = center.x + (source.x-center.x) * (1. + bend * abs(localY) * 3.);
    source.x += sin(localY * 3.141593) * bend * resolution.x * .12;
    if (source.x < 0. || source.y < 0. || source.x > resolution.x || source.y > resolution.y) return half4(0.);
    return content.eval(source) * (1. - smoothstep(.65, 1., p));
}
"""

@Composable internal fun rememberProvisionCollapseShader(): RuntimeShader? {
    val shader by produceState<RuntimeShader?>(null) {
        value = withContext(Dispatchers.Default) { RuntimeShader(CollapseShader) }
    }
    return shader
}

@Composable internal fun Modifier.provisionCollapse(shader: RuntimeShader?, progress: Animatable<Float, AnimationVector1D>, enabled: Boolean): Modifier {
    val effect = remember(shader) { shader?.let { RenderEffect.createRuntimeShaderEffect(it, "content").asComposeRenderEffect() } }
    return graphicsLayer {
        val p = if (enabled) progress.value else 0f
        if (p > 0f && shader != null) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("progress", p)
            renderEffect = effect
        } else renderEffect = null
    }
}

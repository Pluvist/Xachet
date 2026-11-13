package com.pluvist.sachet.next

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shader programs and the vector animation are prepared while the first page is visible. */
@Composable internal fun rememberProvisionGlassPrograms(): LiquidMenuShaderPrograms? {
    val programs by produceState<LiquidMenuShaderPrograms?>(null) {
        value = withContext(Dispatchers.Default) { LiquidMenuShaderPrograms() }
    }
    return programs
}

/** Full-screen variant of Glasmio's menu morph. Geometry is read in drawing, not
 * in composition/layout; the destination keeps its real size and aspect ratio. */
@Composable internal fun ProvisionLiquidWindow(
    motion: LiquidMenuMorphMotion,
    visible: Boolean,
    animating: Boolean,
    programs: LiquidMenuShaderPrograms?,
    modifier: Modifier = Modifier,
    expanded: Boolean = visible,
    animateForeground: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = LocalGlassColors.current
    val renderer = if (programs == null) null else rememberGlassMorphRenderer(LocalGlassBackdrop.current, animating,
        blurRadius = 4.dp, renderScale = .35f, programs = programs, isDark = colors.isDark)
    val workspace = remember { LiquidMenuContourWorkspace() }
    val replay = rememberGraphicsLayer()
    val contentSpring = if (animateForeground) {
        val springProgress = remember { Animatable(if (expanded && !animating) 1f else 0f) }
        LaunchedEffect(expanded) { springProgress.animateTo(if (expanded) 1f else 0f, spring(.62f, 260f)) }
        springProgress
    } else null
    val density = LocalDensity.current
    val blurEffects = if (animateForeground) remember(density) { (0..8).map { step ->
        if (step == 0) null else android.graphics.RenderEffect.createBlurEffect(
            step * density.density / 2f, step * density.density / 2f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
    } } else emptyList()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var warmed by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize().onGloballyPositioned { coordinates = it }
        .then(if (!visible) Modifier.clearAndSetSemantics {} else Modifier)
        .pointerInput(visible, animating) {
            if (!visible || animating) awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        }.drawWithContent {
            if (!visible) {
                // Warm one real draw (text, vectors and first home viewport) offscreen.
                if (!warmed) { replay.record { this@drawWithContent.drawContent() }; warmed = true }
            } else if (!animating || !motion.initialized) drawContent()
            else {
                val frame = motion.frame
                val contour = LiquidMenuContour(frame.bounds, Offset(frame.radiusX, frame.radiusY),
                    frame.sourceLobe, Offset(frame.sourceRadiusX, frame.sourceRadiusY), frame.smoothing, workspace)
                val path = contour.path(size)
                coordinates?.let { coords ->
                    renderer?.draw(this, coords, Rect(Offset.Zero, size), Offset.Zero,
                        Rect(Offset.Zero, size), frame.radiusX, contour, colors.elevatedBackground,
                        highlightAlpha = .5f, isDark = colors.isDark,
                        refractionHeight = (frame.lensHeight * 1.2f).toDp(), refractionAmount = (frame.lensAmount * 1.2f).toDp(),
                        cornerRadii = Offset(frame.radiusX, frame.radiusY), lensBounds = frame.bounds,
                        secondaryLensBounds = frame.sourceLobe,
                        secondaryLensRadii = Offset(frame.sourceRadiusX, frame.sourceRadiusY),
                        lensSmoothing = frame.smoothing, outlinePath = path)
                }
                clipPath(path) { this@drawWithContent.drawContent() }
            }
        }) {
        // Keep a retained display list while the outer clip changes shape.
        // Home cards animate in their own layers, so the home viewport needs
        // neither a second scale animation nor a full-screen blur pass.
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (visible && animating && motion.initialized) {
                val frame = motion.frame
                val progress = frame.contentProgress
                alpha = progress * progress
                if (contentSpring != null) {
                    val scale = .90f + .10f * contentSpring.value
                    scaleX = scale
                    scaleY = scale
                    renderEffect = blurEffects[(frame.contentBlur / density.density * 2).toInt().coerceIn(0, 8)]
                }
            } else {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
                renderEffect = null
            }
        }) { content() }
    }
}

@Stable internal class ProvisionEntrance {
    val progress = Animatable(1f)
}
internal val LocalProvisionEntrance = staticCompositionLocalOf<ProvisionEntrance?> { null }

/** The shared progress avoids a coroutine and recomposition per home item. */
@Composable internal fun Modifier.provisionEntrance(order: Int): Modifier {
    val entrance = LocalProvisionEntrance.current ?: return this
    return graphicsLayer {
        val p = ((entrance.progress.value - .08f - order.coerceAtMost(9) * .012f) / .80f).coerceIn(0f, 1f)
        val eased = EaseOutBack.transform(p)
        alpha = (p * 3f).coerceIn(0f, 1f)
        translationY = 32.dp.toPx() * (1f - eased)
        scaleX = .96f + .04f * eased; scaleY = scaleX
    }
}

/** Keep the home composition alive across completion. No activity recreation,
 * network wait or synchronous shader parsing is on the start button's path. */
@Composable internal fun ProvisionAppHost(
    showGuide: Boolean, data: AppData, vm: SachetViewModel,
    onFinished: () -> Unit, home: @Composable () -> Unit,
) {
    val motion = remember { LiquidMenuMorphMotion() }
    val programs = if (showGuide) rememberProvisionGlassPrograms() else null
    val entrance = remember { ProvisionEntrance() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var handoff by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val colors = LocalGlassColors.current
    val latestHome by rememberUpdatedState(home)
    val homeContent = remember { movableContentOf { latestHome() } }
    if (!showGuide) {
        // Move the preloaded composition without losing its scroll/navigation state;
        // disposing the guide host releases its full-screen capture layers/shaders.
        CompositionLocalProvider(LocalProvisionEntrance provides entrance) { homeContent() }
        return
    }
    GlasmioBackdropHost(Modifier.fillMaxSize().onGloballyPositioned { bounds = it.boundsInRoot() },
        sampledContent = { Box(Modifier.fillMaxSize().background(colors.groupedBackground)) },
        overlayContent = {
            CompositionLocalProvider(LocalProvisionEntrance provides entrance) {
                ProvisionLiquidWindow(motion, !showGuide || handoff, handoff, programs,
                    Modifier.zIndex(if (handoff) 2f else 0f), animateForeground = false) { homeContent() }
            }
            if (showGuide) Box(Modifier.fillMaxSize().zIndex(1f)) {
                OnboardingPage(data, vm, programs) { source ->
                    if (!handoff && !bounds.isEmpty && !source.isEmpty) {
                        motion.initialize(source.translate(-bounds.topLeft), Rect(Offset.Zero, bounds.size), 0f, density.density)
                        scope.launch {
                            entrance.progress.snapTo(0f)
                            handoff = true
                            launch { entrance.progress.animateTo(1f, tween(820, easing = LinearEasing)) }
                            motion.animateTo(true)
                            onFinished(); handoff = false
                        }
                    }
                }
            }
        })
}

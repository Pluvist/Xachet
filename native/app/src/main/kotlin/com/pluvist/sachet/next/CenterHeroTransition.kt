package com.pluvist.sachet.next

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Responsive reference curve preserved for easing telemetry and tests. */
internal val CenterHeroCurve: Easing = CubicBezierEasing(0.12f, 0.48f, 0.08f, 1f)

internal const val OVERLAY_TARGET_PAGE_CORNER_DP = 28f
internal const val CENTERED_CARD_CORNER_DP = 32f

internal val ContainerOpenSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.96f,
    stiffness = 460f,
    visibilityThreshold = 0.001f,
)

internal val ContainerCloseSpring: SpringSpec<Float> = spring(
    dampingRatio = 1.0f,
    stiffness = 560f,
    visibilityThreshold = 0.001f,
)

@Immutable
internal data class ContainerHeroFrame(
    val fraction: Float,
    val rect: Rect,
    val cornerPx: Float,
    val colorMix: Float,
    val sourceAlpha: Float,
    val targetAlpha: Float,
    val scrimFraction: Float,
)

private fun lerpFloat(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * Computes container morph frame geometry directly from current spring fraction [fraction] (`0f..1f`).
 * Because [Animatable] spring physics preserves exact position and velocity across open/close flips,
 * mid-flight reversal is C²-continuous with zero visual jumps, while S-curve fades keep open/close
 * content transitions gradual without sudden popping.
 */
internal fun computeContainerHeroFrameFromFraction(
    fraction: Float,
    srcRect: Rect,
    dstRect: Rect,
    srcCornerPx: Float,
    dstCornerPx: Float,
): ContainerHeroFrame {
    val f = fraction.coerceIn(0f, 1f)
    val rect = Rect(
        left = lerpFloat(srcRect.left, dstRect.left, f),
        top = lerpFloat(srcRect.top, dstRect.top, f),
        right = lerpFloat(srcRect.right, dstRect.right, f),
        bottom = lerpFloat(srcRect.bottom, dstRect.bottom, f),
    )
    val cornerPx = lerpFloat(srcCornerPx, dstCornerPx, f)
    val colorMix = smoothStep(f / 0.56f)
    val sourceAlpha = 1f - smoothStep(f / 0.42f)
    val targetAlpha = smoothStep((f - 0.08f) / 0.54f)

    return ContainerHeroFrame(
        fraction = f,
        rect = rect,
        cornerPx = cornerPx,
        colorMix = colorMix,
        sourceAlpha = sourceAlpha,
        targetAlpha = targetAlpha,
        scrimFraction = smoothStep(f / 0.50f),
    )
}

internal const val MATRIX_FLY_OPEN_DURATION_MS = 640
internal const val MATRIX_FLY_CLOSE_DURATION_MS = 580
internal const val MATRIX_FLY_REVEAL_THRESHOLD = 0.12f

/** Graceful nonlinear curve letting outer-to-inner peel read clearly while staying kinetic. */
internal val MatrixFlyOpenCurve: Easing = CubicBezierEasing(0.22f, 0.72f, 0.24f, 1.0f)

/** Mirrors open depth flow cleanly on back: prompt sink-out followed by cushioned dot dock-in. */
internal val MatrixFlyCloseCurve: Easing = CubicBezierEasing(0.24f, 0.70f, 0.26f, 1.0f)

@Immutable
internal data class BackgroundDepthFrame(
    val scale: Float,
    val offsetYDp: Float,
    val alpha: Float,
    val cornerDp: Float,
)

/**
 * Computes recessed depth transforms for underlying tabs while overlays open/close:
 * smoothly scales down (`1f -> 0.94f`), sinks (`0dp -> +14dp`), dims slightly, and rounds edges,
 * reversing symmetrically on return so background rises back to meet incoming blocks.
 */
internal fun computeBackgroundDepthFrame(progress: Float): BackgroundDepthFrame {
    val p = smoothStep(progress.coerceIn(0f, 1f))
    return BackgroundDepthFrame(
        scale = lerpFloat(1f, 0.94f, p),
        offsetYDp = lerpFloat(0f, 14f, p),
        alpha = lerpFloat(1f, 0.88f, p),
        cornerDp = lerpFloat(0f, 24f, p),
    )
}

@Immutable
internal data class MatrixFlyOutFrame(
    val fraction: Float,
    val flyProgress: Float,
    val morphProgress: Float,
    val targetAlpha: Float,
    val targetScale: Float,
    val targetOffsetYDp: Float,
    val sourceCardAlpha: Float,
    val dotBoost: Float,
    val dotAlpha: Float,
)

private fun easeOutCubic(t: Float): Float {
    val inv = 1f - t.coerceIn(0f, 1f)
    return 1f - inv * inv * inv
}

/**
 * Computes nonlinear matrix fly-out and origin-bound page expand/collapse parameters:
 * - Blocks detach and accelerate nonlinearly off-screen across `0f..0.92f`.
 * - Target page expands outward from the Course Status card bounds (`srcRect -> dstRect`) across
 *   `MATRIX_FLY_REVEAL_THRESHOLD..1f`, and symmetrically shrinks back into `srcRect` on return.
 */
internal fun computeMatrixFlyOutFrame(fraction: Float): MatrixFlyOutFrame {
    val f = fraction.coerceIn(0f, 1f)
    val normalizedFly = (f / 0.92f).coerceIn(0f, 1f)
    val flyProgress = lerpFloat(normalizedFly, easeOutCubic(normalizedFly), 0.35f).coerceIn(0f, 1f)
    val revealT = ((f - MATRIX_FLY_REVEAL_THRESHOLD) / (1f - MATRIX_FLY_REVEAL_THRESHOLD)).coerceIn(0f, 1f)
    val morphProgress = easeOutCubic(revealT)
    val targetAlpha = smoothStep((revealT / 0.68f).coerceIn(0f, 1f))
    val targetScale = lerpFloat(0.92f, 1f, morphProgress)
    val targetOffsetYDp = lerpFloat(44f, 0f, morphProgress)
    // Dissolve foreground text slightly before center blocks peel out, and fade it back in
    // strictly after center/inner blocks settle on return so moving squares never overlap status text.
    val sourceCardAlpha = 1f - smoothStep((f / 0.16f).coerceIn(0f, 1f))
    val dotBoost = smoothStep((f / 0.24f).coerceIn(0f, 1f))
    val dotAlpha = 1f - smoothStep(((f - 0.84f) / 0.16f).coerceIn(0f, 1f))
    return MatrixFlyOutFrame(
        fraction = f,
        flyProgress = flyProgress,
        morphProgress = morphProgress,
        targetAlpha = targetAlpha,
        targetScale = targetScale,
        targetOffsetYDp = targetOffsetYDp,
        sourceCardAlpha = sourceCardAlpha,
        dotBoost = dotBoost,
        dotAlpha = dotAlpha,
    )
}

internal enum class OverlayTransitionMode {
    None,
    CenterHero,
    SharedSlide,
    MatrixFlyOut,
}

internal class HeroSourceRegistration(
    val boundsProvider: () -> Rect?,
    var containerColor: Color,
    var cornerDp: Float,
    var preferredMode: OverlayTransitionMode = OverlayTransitionMode.CenterHero,
) {
    internal var pendingCaptureSession by mutableStateOf<OverlaySession?>(null)
    internal var isSourceHidden by mutableStateOf(false)
}

/**
 * Holds state for one container-transform or slide overlay lifecycle. Multiple instances may coexist
 * concurrently so a user can interrupt closing/opening transitions or launch another card in parallel.
 */
@Stable
internal class OverlaySession(
    val id: Long,
    initialStack: List<DetailRoute>,
    val sourceKey: String?,
    initialBounds: Rect?,
    initialColor: Color,
    initialCornerDp: Float,
    val initialMode: OverlayTransitionMode,
) {
    var stack by mutableStateOf(initialStack)
    var activeBounds by mutableStateOf(initialBounds)
    var activeColor by mutableStateOf(initialColor)
    var activeCornerDp by mutableStateOf(initialCornerDp)
    var targetBounds by mutableStateOf<Rect?>(null)
    var transitionMode by mutableStateOf(initialMode)
    var isClosing by mutableStateOf(false)
    var pendingCapture by mutableStateOf(initialMode == OverlayTransitionMode.CenterHero && initialBounds != null)
    var captureLayer: GraphicsLayer? = null
    val progress = Animatable(0f)
    var job: Job? = null

    private var cachedFraction = Float.NaN
    private var cachedSrc = Rect.Zero
    private var cachedDst = Rect.Zero
    private var cachedSrcCorner = -1f
    private var cachedDstCorner = -1f
    private var cachedFrame = ContainerHeroFrame(0f, Rect.Zero, 0f, 0f, 1f, 0f, 0f)

    fun resolveFrame(
        fraction: Float,
        srcRect: Rect,
        dstRect: Rect,
        srcCornerPx: Float,
        dstCornerPx: Float,
    ): ContainerHeroFrame {
        if (fraction == cachedFraction &&
            srcRect == cachedSrc &&
            dstRect == cachedDst &&
            srcCornerPx == cachedSrcCorner &&
            dstCornerPx == cachedDstCorner
        ) {
            return cachedFrame
        }
        cachedFraction = fraction
        cachedSrc = srcRect
        cachedDst = dstRect
        cachedSrcCorner = srcCornerPx
        cachedDstCorner = dstCornerPx
        cachedFrame = computeContainerHeroFrameFromFraction(
            fraction = fraction,
            srcRect = srcRect,
            dstRect = dstRect,
            srcCornerPx = srcCornerPx,
            dstCornerPx = dstCornerPx,
        )
        return cachedFrame
    }
}

@Stable
internal class HeroTransitionController {
    private val sources = HashMap<String, HeroSourceRegistration>(48)
    private var nextSessionId by mutableLongStateOf(1L)

    var rootCoordinates: LayoutCoordinates? = null
    var rootSize: IntSize by mutableStateOf(IntSize.Zero)
    var backgroundDensity: Float = 1f

    private var cachedBgCornerPx = -1f
    private var cachedBgShape: RoundedCornerShape? = null

    fun backgroundShapeForCornerPx(cornerPx: Float): RoundedCornerShape {
        val prev = cachedBgShape
        if (prev != null && abs(cornerPx - cachedBgCornerPx) < 0.75f) {
            return prev
        }
        val next = RoundedCornerShape(cornerPx)
        cachedBgCornerPx = cornerPx
        cachedBgShape = next
        return next
    }

    private fun untransformBoundsFromBackground(raw: Rect, depthProgress: Float): Rect {
        if (depthProgress <= 0.005f || rootSize.width <= 0 || rootSize.height <= 0) return raw
        val frame = computeBackgroundDepthFrame(depthProgress)
        if (frame.scale >= 0.999f && frame.offsetYDp <= 0.01f) return raw
        val cx = rootSize.width * 0.5f
        val cy = rootSize.height * 0.5f
        val dyPx = frame.offsetYDp * backgroundDensity
        val invScale = 1f / frame.scale.coerceAtLeast(0.5f)
        return Rect(
            left = cx + (raw.left - cx) * invScale,
            top = cy + (raw.top - dyPx - cy) * invScale,
            right = cx + (raw.right - cx) * invScale,
            bottom = cy + (raw.bottom - dyPx - cy) * invScale,
        )
    }

    val sessions = mutableStateListOf<OverlaySession>()

    val foregroundSession: OverlaySession?
        get() = sessions.lastOrNull { !it.isClosing }

    val hasForegroundSession: Boolean
        get() = sessions.any { !it.isClosing }

    fun maxProgress(): Float {
        var best = 0f
        for (i in sessions.indices) {
            val v = sessions[i].progress.value
            if (v > best) best = v
        }
        return best
    }

    fun foregroundProgress(): Float = foregroundSession?.progress?.value ?: maxProgress()

    fun registerSource(key: String, registration: HeroSourceRegistration) {
        sources[key] = registration
        updateSourceState(key)
    }

    fun unregisterSource(key: String, registration: HeroSourceRegistration) {
        if (sources[key] === registration) {
            sources.remove(key)
        }
    }

    private fun updateSourceState(key: String?) {
        if (key == null) return
        val reg = sources[key] ?: return
        val capture = sessions.lastOrNull {
            it.sourceKey == key &&
                it.pendingCapture &&
                it.transitionMode == OverlayTransitionMode.CenterHero
        }
        reg.pendingCaptureSession = capture
        reg.isSourceHidden = capture == null && sessions.any {
            it.sourceKey == key &&
                (it.transitionMode == OverlayTransitionMode.CenterHero ||
                    it.transitionMode == OverlayTransitionMode.MatrixFlyOut) &&
                !it.pendingCapture
        }
    }

    private fun refreshSessionBounds(session: OverlaySession) {
        val key = session.sourceKey ?: return
        val reg = sources[key] ?: return
        val latest = reg.boundsProvider()
        if (latest != null && latest.width > 1f && latest.height > 1f) {
            session.activeBounds = untransformBoundsFromBackground(latest, maxProgress())
            session.activeColor = reg.containerColor
            session.activeCornerDp = reg.cornerDp
        }
    }

    /**
     * Opens [route] expanding out from [key]'s bounds if registered (falling back to slide otherwise),
     * or catches and reverses an existing closing session for the same [key]. Any other currently open
     * session begins folding home concurrently in parallel.
     */
    fun openHero(key: String, route: DetailRoute, scope: CoroutineScope) {
        val existing = sessions.lastOrNull { it.sourceKey == key }
        if (existing != null) {
            sessions.filter { it !== existing && !it.isClosing }.forEach { other ->
                closeSession(other, scope)
            }
            if (sessions.lastOrNull() !== existing) {
                sessions.remove(existing)
                sessions.add(existing)
            }
            existing.job?.cancel()
            existing.isClosing = false
            existing.pendingCapture = false
            if (existing.stack.lastOrNull() != route) {
                existing.stack = listOf(route)
            }
            refreshSessionBounds(existing)
            updateSourceState(key)
            existing.job = scope.launch {
                animateSessionProgress(existing, target = 1f)
            }
            return
        }

        // Retiring any open foreground overlay into a parallel closing transition lets tapping a second
        // card immediately start expanding it without waiting for the previous one to disappear.
        val depthBeforeOpen = maxProgress()
        sessions.filter { !it.isClosing }.forEach { other ->
            closeSession(other, scope)
        }

        val reg = sources[key]
        val rawBounds = reg?.boundsProvider?.invoke()
        val hasValidBounds = rawBounds != null && rawBounds.width > 1f && rawBounds.height > 1f
        val bounds = if (hasValidBounds) untransformBoundsFromBackground(rawBounds, depthBeforeOpen) else null
        val mode = when {
            !hasValidBounds -> OverlayTransitionMode.SharedSlide
            reg.preferredMode == OverlayTransitionMode.MatrixFlyOut -> OverlayTransitionMode.MatrixFlyOut
            else -> OverlayTransitionMode.CenterHero
        }

        val session = OverlaySession(
            id = nextSessionId++,
            initialStack = listOf(route),
            sourceKey = key,
            initialBounds = bounds,
            initialColor = reg?.containerColor ?: Color.Transparent,
            initialCornerDp = reg?.cornerDp ?: 24f,
            initialMode = mode,
        )
        sessions.add(session)
        updateSourceState(key)
        session.job = scope.launch {
            if (mode == OverlayTransitionMode.CenterHero) {
                withFrameNanos {}
                val layer = session.captureLayer
                if (layer == null || layer.size.width <= 0) {
                    withFrameNanos {}
                }
                session.pendingCapture = false
                updateSourceState(key)
            }
            animateSessionProgress(session, target = 1f)
        }
    }

    private suspend fun animateSessionProgress(session: OverlaySession, target: Float) {
        if (session.transitionMode == OverlayTransitionMode.MatrixFlyOut) {
            val isOpen = target > 0.5f
            val baseDuration = if (isOpen) MATRIX_FLY_OPEN_DURATION_MS else MATRIX_FLY_CLOSE_DURATION_MS
            val delta = abs(target - session.progress.value).coerceIn(0.05f, 1f)
            val durationMs = (baseDuration * delta).roundToInt().coerceAtLeast(80)
            val easing = if (isOpen) MatrixFlyOpenCurve else MatrixFlyCloseCurve
            session.progress.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = durationMs, easing = easing),
            )
        } else {
            val spec = if (target > 0.5f) ContainerOpenSpring else ContainerCloseSpring
            session.progress.animateTo(target, animationSpec = spec)
        }
    }

    /** Opens [route] as a slide overlay or pushes onto the active foreground session stack. */
    fun openSlideOrPush(route: DetailRoute, scope: CoroutineScope) {
        val active = foregroundSession
        if (active != null) {
            if (active.stack.lastOrNull() != route) {
                active.stack = active.stack + route
            }
            return
        }
        val session = OverlaySession(
            id = nextSessionId++,
            initialStack = listOf(route),
            sourceKey = null,
            initialBounds = null,
            initialColor = Color.Transparent,
            initialCornerDp = OVERLAY_TARGET_PAGE_CORNER_DP,
            initialMode = OverlayTransitionMode.SharedSlide,
        )
        sessions.add(session)
        session.job = scope.launch {
            session.progress.animateTo(1f, animationSpec = ContainerOpenSpring)
        }
    }

    /** Restores a previously saved route stack immediately without opening animation. */
    fun restoreStackImmediate(stack: List<DetailRoute>, scope: CoroutineScope) {
        if (stack.isEmpty() || sessions.isNotEmpty()) return
        val session = OverlaySession(
            id = nextSessionId++,
            initialStack = stack,
            sourceKey = null,
            initialBounds = null,
            initialColor = Color.Transparent,
            initialCornerDp = OVERLAY_TARGET_PAGE_CORNER_DP,
            initialMode = OverlayTransitionMode.SharedSlide,
        )
        session.pendingCapture = false
        sessions.add(session)
        scope.launch {
            session.progress.snapTo(1f)
        }
    }

    fun pushToForeground(route: DetailRoute) {
        val active = foregroundSession ?: return
        if (active.stack.lastOrNull() != route) {
            active.stack = active.stack + route
        }
    }

    fun popForeground(): Boolean {
        val active = foregroundSession ?: return false
        if (active.stack.size > 1) {
            active.stack = active.stack.dropLast(1)
            return true
        }
        return false
    }

    fun closeForeground(scope: CoroutineScope) {
        val active = foregroundSession ?: return
        closeSession(active, scope)
    }

    private fun closeSession(session: OverlaySession, scope: CoroutineScope) {
        session.job?.cancel()
        session.isClosing = true
        session.pendingCapture = false
        refreshSessionBounds(session)
        if (session.progress.value <= 0.002f) {
            sessions.remove(session)
            updateSourceState(session.sourceKey)
            return
        }
        session.job = scope.launch {
            animateSessionProgress(session, target = 0f)
            if (session.isClosing) {
                sessions.remove(session)
                updateSourceState(session.sourceKey)
            }
        }
    }
}

internal val LocalHeroTransitionController = compositionLocalOf<HeroTransitionController?> { null }

private fun computeBoundsFromCoords(root: LayoutCoordinates?, coords: LayoutCoordinates?): Rect? {
    val c = coords ?: return null
    if (!c.isAttached) return null
    return if (root != null && root.isAttached) {
        root.localBoundingBoxOf(c, clipBounds = false)
    } else {
        c.boundsInRoot()
    }
}

/**
 * Registers a clickable card, course bar, button, or day tile for smooth container expansion and parallel
 * return morphing with zero composition slots, zero DisposableEffects, and O(1) draw invalidation.
 */
internal fun Modifier.heroFlightSource(
    key: String,
    containerColor: Color,
    cornerDp: Float,
    enabled: Boolean = true,
    controller: HeroTransitionController? = null,
    mode: OverlayTransitionMode = OverlayTransitionMode.CenterHero,
): Modifier = if (enabled) {
    this then HeroSourceElement(
        heroKey = key,
        containerColor = containerColor,
        cornerDp = cornerDp,
        explicitController = controller,
        preferredMode = mode,
    )
} else {
    this
}

private data class HeroSourceElement(
    val heroKey: String,
    val containerColor: Color,
    val cornerDp: Float,
    val explicitController: HeroTransitionController?,
    val preferredMode: OverlayTransitionMode,
) : ModifierNodeElement<HeroSourceNode>() {
    override fun create(): HeroSourceNode = HeroSourceNode(
        heroKey = heroKey,
        containerColor = containerColor,
        cornerDp = cornerDp,
        explicitController = explicitController,
        preferredMode = preferredMode,
    )

    override fun update(node: HeroSourceNode) {
        node.updateSpec(heroKey, containerColor, cornerDp, explicitController, preferredMode)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "heroFlightSource"
        properties["key"] = heroKey
        properties["cornerDp"] = cornerDp
    }
}

private class HeroSourceNode(
    private var heroKey: String,
    private var containerColor: Color,
    private var cornerDp: Float,
    private var explicitController: HeroTransitionController?,
    private var preferredMode: OverlayTransitionMode,
) : Modifier.Node(),
    CompositionLocalConsumerModifierNode,
    GlobalPositionAwareModifierNode,
    DrawModifierNode {

    private var activeController: HeroTransitionController? = null
    private var registeredKey: String = ""
    private var registration: HeroSourceRegistration? = null
    private var coords: LayoutCoordinates? = null

    override fun onAttach() {
        syncRegistration()
    }

    override fun onDetach() {
        unregisterCurrent()
        coords = null
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        coords = if (coordinates.isAttached) coordinates else null
    }

    fun updateSpec(
        newKey: String,
        newColor: Color,
        newCornerDp: Float,
        newExplicitController: HeroTransitionController?,
        newPreferredMode: OverlayTransitionMode,
    ) {
        heroKey = newKey
        containerColor = newColor
        cornerDp = newCornerDp
        explicitController = newExplicitController
        preferredMode = newPreferredMode
        if (isAttached) {
            syncRegistration()
        }
    }

    private fun syncRegistration() {
        val targetController = explicitController ?: currentValueOf(LocalHeroTransitionController)
        if (targetController == null) {
            unregisterCurrent()
            return
        }
        val existing = registration
        if (existing != null && activeController === targetController && registeredKey == heroKey) {
            existing.containerColor = containerColor
            existing.cornerDp = cornerDp
            existing.preferredMode = preferredMode
            return
        }
        unregisterCurrent()
        val reg = HeroSourceRegistration(
            boundsProvider = { computeBoundsFromCoords(targetController.rootCoordinates, coords) },
            containerColor = containerColor,
            cornerDp = cornerDp,
            preferredMode = preferredMode,
        )
        activeController = targetController
        registeredKey = heroKey
        registration = reg
        targetController.registerSource(heroKey, reg)
        invalidateDraw()
    }

    private fun unregisterCurrent() {
        val oldReg = registration ?: return
        activeController?.unregisterSource(registeredKey, oldReg)
        registration = null
        activeController = null
        registeredKey = ""
    }

    override fun ContentDrawScope.draw() {
        val reg = registration
        val capture = reg?.pendingCaptureSession?.captureLayer
        if (capture != null && !capture.isReleased) {
            capture.record {
                this@draw.drawContent()
            }
            capture.alpha = 1f
            drawLayer(capture)
        } else if (reg?.isSourceHidden != true) {
            drawContent()
        }
    }
}

private data class MorphRectBoundsShape(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)
        val radius = cornerPx.coerceIn(0f, minOf(w, h) * 0.5f)
        return Outline.Rounded(
            RoundRect(
                left = left,
                top = top,
                right = left + w,
                bottom = top + h,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
}

/**
 * Renders one [OverlaySession] with continuous spring container expansion/collapse, rounded target page
 * corners ([targetCornerDp]), per-session isolated source capture layer, and Predictive Back transform.
 */
@Composable
internal fun OverlaySessionHost(
    controller: HeroTransitionController,
    session: OverlaySession,
    predictiveBack: PredictiveBackVisualState,
    isCenteredCard: Boolean,
    targetContainerColor: Color,
    targetCornerDp: Float,
    modifier: Modifier = Modifier,
    matrixFlyOutLayer: (@Composable (srcRect: Rect, progressProvider: () -> Float) -> Unit)? = null,
    content: @Composable (contentModifier: Modifier, targetTrackerModifier: Modifier) -> Unit,
) {
    val settledShape = remember(targetCornerDp) { RoundedCornerShape(targetCornerDp.dp) }

    val targetTrackerModifier = Modifier.onGloballyPositioned { coords ->
        if (isCenteredCard && coords.isAttached) {
            val root = controller.rootCoordinates
            session.targetBounds = if (root != null && root.isAttached) {
                root.localBoundingBoxOf(coords, clipBounds = false)
            } else {
                coords.boundsInRoot()
            }
        }
    }

    fun currentDstRect(fullSize: Size): Rect {
        val recorded = session.targetBounds
        return if (isCenteredCard && recorded != null && recorded.width > 1f && recorded.height > 1f) {
            recorded
        } else {
            Rect(0f, 0f, fullSize.width.coerceAtLeast(1f), fullSize.height.coerceAtLeast(1f))
        }
    }

    val isMatrixFlyOut = session.transitionMode == OverlayTransitionMode.MatrixFlyOut &&
        session.activeBounds != null &&
        matrixFlyOutLayer != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(session) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (session.isClosing) {
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .then(
                if (isMatrixFlyOut) {
                    Modifier
                } else {
                    Modifier.predictiveBackContainer(predictiveBack)
                },
            ),
    ) {
        val isContainerMorph = session.transitionMode == OverlayTransitionMode.CenterHero &&
            session.activeBounds != null

        if (isMatrixFlyOut) {
            val srcRect = session.activeBounds ?: Rect.Zero
            // Layer 2: target page expands from / collapses back into the Course Status card bounds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveBackContainer(predictiveBack)
                    .graphicsLayer {
                        val u = session.progress.value
                        if (u <= MATRIX_FLY_REVEAL_THRESHOLD) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        val frame = computeMatrixFlyOutFrame(u)
                        val dst = currentDstRect(size)
                        val m = frame.morphProgress
                        val rect = Rect(
                            left = lerpFloat(srcRect.left, dst.left, m),
                            top = lerpFloat(srcRect.top, dst.top, m),
                            right = lerpFloat(srcRect.right, dst.right, m),
                            bottom = lerpFloat(srcRect.bottom, dst.bottom, m),
                        )
                        val cornerPx = lerpFloat(session.activeCornerDp * density, targetCornerDp * density, m)
                        alpha = frame.targetAlpha
                        if (u >= 0.999f) {
                            clip = !isCenteredCard && targetCornerDp > 0f
                            if (clip) shape = settledShape
                        } else {
                            clip = true
                            shape = MorphRectBoundsShape(rect.left, rect.top, rect.right, rect.bottom, cornerPx)
                        }
                    }
                    .drawBehind {
                        val u = session.progress.value
                        if (u <= MATRIX_FLY_REVEAL_THRESHOLD || u >= 0.999f) return@drawBehind
                        val frame = computeMatrixFlyOutFrame(u)
                        drawRect(color = lerp(session.activeColor, targetContainerColor, frame.morphProgress))
                    },
            ) {
                val flyTargetTransform = Modifier.graphicsLayer {
                    val u = session.progress.value
                    if (u <= MATRIX_FLY_REVEAL_THRESHOLD) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    if (u >= 0.999f) {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        translationY = 0f
                        return@graphicsLayer
                    }
                    val frame = computeMatrixFlyOutFrame(u)
                    val parentW = controller.rootSize.width.toFloat().takeIf { it > 1f } ?: size.width
                    val parentH = controller.rootSize.height.toFloat().takeIf { it > 1f } ?: size.height
                    val dst = currentDstRect(Size(parentW, parentH))
                    val m = frame.morphProgress
                    val rect = Rect(
                        left = lerpFloat(srcRect.left, dst.left, m),
                        top = lerpFloat(srcRect.top, dst.top, m),
                        right = lerpFloat(srcRect.right, dst.right, m),
                        bottom = lerpFloat(srcRect.bottom, dst.bottom, m),
                    )
                    val rawScale = (rect.width / dst.width.coerceAtLeast(1f)).coerceIn(0.68f, 1f)
                    val s = lerpFloat(rawScale, 1f, m)
                    alpha = frame.targetAlpha
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    scaleX = s
                    scaleY = s
                    translationX = rect.center.x - dst.center.x
                    translationY = rect.top - dst.top
                }
                content(flyTargetTransform, targetTrackerModifier)
            }
            // Layer 3: exploding/returning dot matrix + gently dissolving source card UI rendered above expanding page
            matrixFlyOutLayer.invoke(srcRect) { session.progress.value }
        } else if (isContainerMorph) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val u = session.progress.value
                        if (u <= 0.001f) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        if (u >= 0.999f) {
                            alpha = 1f
                            if (!isCenteredCard && targetCornerDp > 0f) {
                                clip = true
                                shape = settledShape
                            } else {
                                clip = false
                            }
                            shadowElevation = 0f
                            return@graphicsLayer
                        }
                        val src = session.activeBounds ?: return@graphicsLayer
                        val dst = currentDstRect(size)
                        val frame = session.resolveFrame(
                            fraction = u,
                            srcRect = src,
                            dstRect = dst,
                            srcCornerPx = session.activeCornerDp * density,
                            dstCornerPx = targetCornerDp * density,
                        )
                        alpha = 1f
                        clip = true
                        shape = MorphRectBoundsShape(
                            left = frame.rect.left,
                            top = frame.rect.top,
                            right = frame.rect.right,
                            bottom = frame.rect.bottom,
                            cornerPx = frame.cornerPx,
                        )
                    }
                    .drawBehind {
                        val u = session.progress.value
                        if (u <= 0.001f || u >= 0.999f) return@drawBehind
                        val src = session.activeBounds ?: return@drawBehind
                        val dst = currentDstRect(size)
                        val frame = session.resolveFrame(
                            fraction = u,
                            srcRect = src,
                            dstRect = dst,
                            srcCornerPx = session.activeCornerDp * density,
                            dstCornerPx = targetCornerDp * density,
                        )
                        val bg = lerp(session.activeColor, targetContainerColor, frame.colorMix)
                        drawRect(color = bg)
                    },
            ) {
                val childTransform = Modifier.graphicsLayer {
                    val u = session.progress.value
                    if (u <= 0.001f) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    if (u >= 0.999f) {
                        alpha = 1f
                        scaleX = 1f
                        scaleY = 1f
                        translationX = 0f
                        translationY = 0f
                        return@graphicsLayer
                    }
                    val src = session.activeBounds ?: return@graphicsLayer
                    val parentW = controller.rootSize.width.toFloat().takeIf { it > 1f } ?: size.width
                    val parentH = controller.rootSize.height.toFloat().takeIf { it > 1f } ?: size.height
                    val dst = currentDstRect(Size(parentW, parentH))
                    val frame = session.resolveFrame(
                        fraction = u,
                        srcRect = src,
                        dstRect = dst,
                        srcCornerPx = session.activeCornerDp * density,
                        dstCornerPx = targetCornerDp * density,
                    )
                    alpha = frame.targetAlpha
                    if (isCenteredCard) {
                        val s = if (dst.width > 1f) {
                            (frame.rect.width / dst.width).coerceIn(0.62f, 1f)
                        } else {
                            1f
                        }
                        transformOrigin = TransformOrigin.Center
                        scaleX = s
                        scaleY = s
                        translationX = frame.rect.center.x - dst.center.x
                        translationY = frame.rect.center.y - dst.center.y
                    } else {
                        val rawScale = frame.rect.width / dst.width.coerceAtLeast(1f)
                        val s = lerpFloat(rawScale.coerceIn(0.76f, 1f), 1f, smoothStep(frame.fraction))
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = s
                        scaleY = s
                        translationX = frame.rect.center.x - dst.center.x
                        translationY = frame.rect.top - dst.top
                    }
                }

                content(childTransform, targetTrackerModifier)

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val u = session.progress.value
                    if (u <= 0.001f || u > 0.43f) return@Canvas
                    val src = session.activeBounds ?: return@Canvas
                    val layer = session.captureLayer
                    if (layer == null || layer.isReleased || layer.size.width <= 0 || layer.size.height <= 0) {
                        return@Canvas
                    }
                    val dst = currentDstRect(size)
                    val frame = session.resolveFrame(
                        fraction = u,
                        srcRect = src,
                        dstRect = dst,
                        srcCornerPx = session.activeCornerDp * density,
                        dstCornerPx = targetCornerDp * density,
                    )
                    if (frame.sourceAlpha <= 0.005f) return@Canvas
                    layer.alpha = frame.sourceAlpha
                    val w = layer.size.width.toFloat()
                    val h = layer.size.height.toFloat()
                    val targetStretch = (frame.rect.width / w.coerceAtLeast(1f)).coerceIn(1f, 1.38f)
                    val gentleExpand = lerpFloat(1f, targetStretch, smoothStep(frame.fraction / 0.42f))
                    withTransform({
                        translate(
                            left = frame.rect.center.x - w * 0.5f,
                            top = frame.rect.center.y - h * 0.5f,
                        )
                        scale(
                            scaleX = gentleExpand,
                            scaleY = gentleExpand,
                            pivot = Offset(w * 0.5f, h * 0.5f),
                        )
                    }) {
                        drawLayer(layer)
                    }
                }
            }
        } else {
            val slideModifier = Modifier.graphicsLayer {
                if (session.transitionMode != OverlayTransitionMode.SharedSlide) return@graphicsLayer
                val u = session.progress.value
                if (u <= 0.001f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                if (u >= 0.999f) {
                    alpha = 1f
                    scaleX = 1f
                    scaleY = 1f
                    translationX = 0f
                    if (!isCenteredCard && targetCornerDp > 0f) {
                        clip = true
                        shape = settledShape
                    }
                    return@graphicsLayer
                }
                val f = u.coerceIn(0f, 1f)
                val parentW = controller.rootSize.width.toFloat().takeIf { it > 1f } ?: size.width
                transformOrigin = TransformOrigin.Center
                scaleX = 0.95f + 0.05f * f
                scaleY = 0.95f + 0.05f * f
                translationX = if (isCenteredCard) 0f else parentW * 0.10f * (1f - f)
                alpha = smoothStep(f / 0.38f)
                if (!isCenteredCard && targetCornerDp > 0f) {
                    clip = true
                    shape = settledShape
                }
            }
            content(slideModifier, targetTrackerModifier)
        }
    }
}

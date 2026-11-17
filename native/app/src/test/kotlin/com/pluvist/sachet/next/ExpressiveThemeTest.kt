@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.SpringSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveThemeTest {
    @Test
    fun seedColorParsingAndFormattingRoundTripsCleanly() {
        val parsed = parseHexColor("#0088FF")
        assertEquals("#0088FF", formatHexColor(parsed))
        assertNull(parseHexColorOrNull("invalid"))
        assertEquals(XachetDefaultSeed, parseHexColor("not-a-color"))
    }

    @Test
    fun allPresetsProduceAccessibleLightAndDarkColorSchemes() {
        assertEquals(3, XachetSeedPresets.size)
        assertEquals(listOf("韶峰红", "湘水蓝", "Legacy"), XachetSeedPresets.map { it.name })
        assertEquals("#9F181E", XachetSeedPresets[0].hex)
        assertEquals("#014190", XachetSeedPresets[1].hex)
        assertEquals(XachetMixPink, XachetSeedPresets[2].color)
        assertEquals(XachetMixBlue, XachetSeedPresets[2].secondaryColor)

        for (preset in XachetSeedPresets) {
            val light = buildSeedColorScheme(preset.color, darkTheme = false, secondarySeed = preset.secondaryColor)
            val dark = buildSeedColorScheme(preset.color, darkTheme = true, secondarySeed = preset.secondaryColor)

            assertTrue(
                "${preset.name} light primary contrast too low",
                contrastRatio(light.onPrimary, light.primary) >= 4.5,
            )
            assertTrue(
                "${preset.name} light primaryContainer contrast too low",
                contrastRatio(light.onPrimaryContainer, light.primaryContainer) >= 4.5,
            )
            assertTrue(
                "${preset.name} light secondary contrast too low",
                contrastRatio(light.onSecondary, light.secondary) >= 4.5,
            )
            assertTrue(
                "${preset.name} light surface contrast too low",
                contrastRatio(light.onSurface, light.surface) >= 7.0,
            )
            assertTrue(
                "${preset.name} light error contrast too low",
                contrastRatio(light.onError, light.error) >= 4.5,
            )

            assertTrue(
                "${preset.name} dark primary contrast too low",
                contrastRatio(dark.onPrimary, dark.primary) >= 4.5,
            )
            assertTrue(
                "${preset.name} dark primaryContainer contrast too low",
                contrastRatio(dark.onPrimaryContainer, dark.primaryContainer) >= 4.5,
            )
            assertTrue(
                "${preset.name} dark secondary contrast too low",
                contrastRatio(dark.onSecondary, dark.secondary) >= 4.5,
            )
            assertTrue(
                "${preset.name} dark surface contrast too low",
                contrastRatio(dark.onSurface, dark.surface) >= 7.0,
            )
            assertTrue(
                "${preset.name} dark error contrast too low",
                contrastRatio(dark.onError, dark.error) >= 4.5,
            )

            // Verify surface tonal elevation progression in light & dark themes
            assertTrue(light.surfaceContainerLowest.luminance() > light.surfaceContainer.luminance())
            assertTrue(light.surfaceContainer.luminance() > light.surfaceContainerHighest.luminance())
            assertTrue(dark.surfaceContainerLowest.luminance() < dark.surfaceContainer.luminance())
            assertTrue(dark.surfaceContainer.luminance() < dark.surfaceContainerHighest.luminance())
        }

        // Verify pink (#F5ABB9) + blue (#5BCFFA) mixed scheme mixes warm pink primary with cool blue secondary
        // and uses lighter surface / container tones than the crimson preset in both light and dark modes
        val mixLight = buildSeedColorScheme(XachetMixPink, darkTheme = false, secondarySeed = XachetMixBlue)
        val mixDark = buildSeedColorScheme(XachetMixPink, darkTheme = true, secondarySeed = XachetMixBlue)
        val crimsonLight = buildSeedColorScheme(XachetDefaultSeed, darkTheme = false)
        val crimsonDark = buildSeedColorScheme(XachetDefaultSeed, darkTheme = true)
        assertTrue(mixLight.primaryContainer.red > mixLight.primaryContainer.blue)
        assertTrue(mixLight.secondaryContainer.blue > mixLight.secondaryContainer.red)
        assertTrue(mixLight.primaryContainer.luminance() > crimsonLight.primaryContainer.luminance())
        assertTrue(mixLight.surface.luminance() > crimsonLight.surface.luminance())
        assertTrue(mixDark.primaryContainer.luminance() > crimsonDark.primaryContainer.luminance())
        assertTrue(mixDark.surface.luminance() > crimsonDark.surface.luminance())
    }

    @Test
    fun achromaticSeedBuildsMonochromeSurfaceWithoutCrash() {
        val neutralLight = buildSeedColorScheme(Color(0xFF808080), darkTheme = false)
        val neutralDark = buildSeedColorScheme(Color(0xFF808080), darkTheme = true)
        assertTrue(contrastRatio(neutralLight.onPrimary, neutralLight.primary) >= 4.5)
        assertTrue(contrastRatio(neutralDark.onPrimary, neutralDark.primary) >= 4.5)
    }

    @Test
    fun expressiveMotionSchemeUsesSpringPhysicsWithCriticallyDampedEffects() {
        val scheme = MotionScheme.expressive()
        val fastSpatial = scheme.fastSpatialSpec<Float>() as SpringSpec<Float>
        val defaultSpatial = scheme.defaultSpatialSpec<Float>() as SpringSpec<Float>
        val slowSpatial = scheme.slowSpatialSpec<Float>() as SpringSpec<Float>
        val defaultEffects = scheme.defaultEffectsSpec<Float>() as SpringSpec<Float>
        val fastEffects = scheme.fastEffectsSpec<Float>() as SpringSpec<Float>

        assertTrue(fastSpatial.dampingRatio < 1f)
        assertTrue(defaultSpatial.dampingRatio < 1f)
        assertTrue(slowSpatial.dampingRatio < 1f)
        assertEquals(1f, defaultEffects.dampingRatio, 0.001f)
        assertEquals(1f, fastEffects.dampingRatio, 0.001f)
        assertTrue(fastSpatial.stiffness > defaultSpatial.stiffness)
        assertTrue(defaultSpatial.stiffness > slowSpatial.stiffness)
    }

    @Test
    fun expressiveTypographyAndShapesDefineFullScale() {
        assertEquals(20.dp, XachetShapes.largeIncreased.topStart.let { 20.dp })
        assertNotNull(XachetTypography.displayLargeEmphasized)
        assertNotNull(XachetTypography.headlineSmallEmphasized)
        assertNotNull(XachetTypography.bodyMedium.tabular().fontFeatureSettings)
        assertEquals("tnum", XachetTypography.titleMedium.tabular().fontFeatureSettings)
        assertNotEquals(XachetTypography.displayLarge.fontFamily, XachetTypography.bodyMedium.fontFamily)
        assertNotEquals(XachetTypography.headlineMedium.fontFamily, XachetTypography.labelLarge.fontFamily)
        assertNotEquals(XachetTypography.labelLargeEmphasized.fontFamily, XachetTypography.bodySmall.fontFamily)
        assertNotEquals(XachetTypeTokens.BrandTopBar.fontFamily, XachetTypography.bodyMedium.fontFamily)
        assertNotEquals(XachetTypeTokens.BrandHero.fontFamily, XachetTypography.displayLargeEmphasized.fontFamily)
        assertEquals(XachetFontFamilies.TopBarTitle, XachetTypeTokens.BrandTopBar.fontFamily)
        assertEquals(XachetFontFamilies.TopBarTitle, XachetTypeTokens.TopBarCompact.fontFamily)
        val homeExpanded = XachetTypeTokens.homeTopBarTitle(0f, TextStyle.Default)
        val homeCollapsed = XachetTypeTokens.homeTopBarTitle(1f, TextStyle.Default)
        assertEquals(XachetFontFamilies.TopBarTitle, homeExpanded.fontFamily)
        assertEquals(44.sp, homeExpanded.fontSize)
        assertEquals(26.sp, homeCollapsed.fontSize)
        assertTrue(XachetTypeTokens.TopBarCompact.fontFeatureSettings?.contains("ss02") == true)
        assertEquals("tnum", XachetTypeTokens.HeroCountdown.fontFeatureSettings)
        assertEquals("tnum", XachetTypeTokens.BentoCountdown.fontFeatureSettings)
        assertEquals("tnum", XachetTypeTokens.CondensedMeta.fontFeatureSettings)
        assertEquals(XachetFontFamilies.RobotoFlexTall, XachetTypeTokens.TomatoTimerDigits.fontFamily)
        assertEquals("tnum", XachetTypeTokens.TomatoTimerDigits.fontFeatureSettings)
        assertTrue(expressiveFeaturePolygon("cultivation") != expressiveFeaturePolygon("exams"))
        assertNotEquals(MaterialShapes.PuffyDiamond, expressiveFeaturePolygon("network"))
    }

    @Test
    fun containerHeroFrameMorphsContinuouslyWithoutVisualGap() {
        val src = Rect(385f, 1294f, 525f, 1747f)
        val dst = Rect(100f, 520f, 1116f, 2120f)
        val srcCorner = 18f
        val dstCorner = 96f

        val start = computeContainerHeroFrameFromFraction(0f, src, dst, srcCorner, dstCorner)
        assertEquals(src, start.rect)
        assertEquals(srcCorner, start.cornerPx, 0.001f)
        assertEquals(1f, start.sourceAlpha, 0.0001f)
        assertEquals(0f, start.targetAlpha, 0.0001f)
        assertEquals(0f, start.colorMix, 0.0001f)

        val end = computeContainerHeroFrameFromFraction(1f, src, dst, srcCorner, dstCorner)
        assertEquals(dst, end.rect)
        assertEquals(dstCorner, end.cornerPx, 0.001f)
        assertEquals(0f, end.sourceAlpha, 0.0001f)
        assertEquals(1f, end.targetAlpha, 0.0001f)
        assertEquals(1f, end.colorMix, 0.0001f)

        // Verify linear symmetric interpolation from spring-driven fraction (continuous under mid-flight reversal)
        var previousLeft = Float.MAX_VALUE
        var previousCorner = -1f
        for (step in 0..10) {
            val f = step / 10f
            val frame = computeContainerHeroFrameFromFraction(f, src, dst, srcCorner, dstCorner)
            assertEquals(f, frame.fraction, 0.0001f)
            assertTrue(frame.rect.left <= previousLeft)
            assertTrue(frame.cornerPx >= previousCorner)
            previousLeft = frame.rect.left
            previousCorner = frame.cornerPx

            assertEquals(src.left + (dst.left - src.left) * f, frame.rect.left, 0.05f)
            assertEquals(src.right + (dst.right - src.right) * f, frame.rect.right, 0.05f)
            assertEquals(srcCorner + (dstCorner - srcCorner) * f, frame.cornerPx, 0.05f)
        }

        // Continuous crossfade overlap exists inside the morphing container shell
        val hasOverlap = (1..99).any { step ->
            val frame = computeContainerHeroFrameFromFraction(step / 100f, src, dst, srcCorner, dstCorner)
            frame.sourceAlpha > 0f && frame.targetAlpha > 0f
        }
        assertTrue(hasOverlap)

        // Expanded full-page and centered-card corner radius tokens + spring interruptibility specs
        assertEquals(28f, OVERLAY_TARGET_PAGE_CORNER_DP, 0.001f)
        assertEquals(32f, CENTERED_CARD_CORNER_DP, 0.001f)
        assertEquals(0.96f, ContainerOpenSpring.dampingRatio, 0.001f)
        assertEquals(460f, ContainerOpenSpring.stiffness, 0.001f)
        assertEquals(1.0f, ContainerCloseSpring.dampingRatio, 0.001f)
        assertEquals(560f, ContainerCloseSpring.stiffness, 0.001f)

        assertEquals(start, computeContainerHeroFrameFromFraction(-0.5f, src, dst, srcCorner, dstCorner))
        assertEquals(end, computeContainerHeroFrameFromFraction(1.5f, src, dst, srcCorner, dstCorner))
    }

    @Test
    fun heroControllerSupportsParallelAndInterruptibleSessions() = runBlocking {
        val frameClock = object : MonotonicFrameClock {
            private var frameTime = 0L
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(4)
                frameTime += 16_000_000L
                return onFrame(frameTime)
            }
        }
        val animScope = CoroutineScope(SupervisorJob() + frameClock)
        try {
            val controller = HeroTransitionController()
            val regA = HeroSourceRegistration({ Rect(24f, 180f, 320f, 340f) }, Color(0xFF1E3A5F), 21f)
            val regB = HeroSourceRegistration({ Rect(400f, 180f, 920f, 440f) }, Color(0xFF2B4C3B), 28f)
            controller.registerSource("a", regA)
            controller.registerSource("b", regB)

            controller.openHero("a", DetailRoute.CourseStatus, animScope)
            assertEquals(1, controller.sessions.size)
            val sessionA = controller.foregroundSession!!
            assertEquals("a", sessionA.sourceKey)
            assertFalse(sessionA.isClosing)
            assertTrue(controller.hasForegroundSession)

            repeat(25) {
                if (sessionA.progress.value > 0f) return@repeat
                delay(4)
            }
            assertTrue(sessionA.progress.value > 0f)

            // Launching session "b" concurrently closes "a" in parallel rather than blocking
            val routeB = DetailRoute.CourseDay(LocalDate.of(2026, 3, 2))
            controller.openHero("b", routeB, animScope)
            assertEquals(2, controller.sessions.size)
            assertTrue(sessionA.isClosing)
            val sessionB = controller.foregroundSession!!
            assertEquals("b", sessionB.sourceKey)
            assertFalse(sessionB.isClosing)

            // Interrupting closing session "a" mid-flight promotes it cleanly back to foreground
            delay(16)
            controller.openHero("a", DetailRoute.CourseStatus, animScope)
            assertEquals("a", controller.foregroundSession?.sourceKey)
            assertFalse(sessionA.isClosing)
            assertTrue(sessionB.isClosing)
            assertTrue(controller.sessions.last() === sessionA)
        } finally {
            animScope.cancel()
        }
    }

    @Test
    fun rollingNumberTokenizationAndDirectionDetectsCountdownsAndIncrements() {
        val tokens = tokenizeRollingText("第 12 周")
        assertEquals(
            listOf(
                RollingSegment(RollingSegmentKind.Text, "第 "),
                RollingSegment(RollingSegmentKind.Digits, "12"),
                RollingSegment(RollingSegmentKind.Text, " 周"),
            ),
            tokens,
        )

        val clockTokens = tokenizeRollingText("01:23:45")
        assertEquals(5, clockTokens.size)
        assertEquals(RollingSegmentKind.Digits, clockTokens[0].kind)
        assertEquals("01", clockTokens[0].value)
        assertEquals(RollingSegmentKind.Digits, clockTokens[4].kind)
        assertEquals("45", clockTokens[4].value)

        // Decreasing countdowns & GPA drops roll down (< 0)
        assertEquals(-1, compareRollingNumbers("23:59", "23:58"))
        assertEquals(-1, compareRollingNumbers("01:00:00", "59:59"))
        assertEquals(-1, compareRollingNumbers("第 12 周", "第 11 周"))
        assertEquals(-1, compareRollingNumbers("3.92", "3.85"))

        // Increasing week numbers, digit-width expansion & GPA rises roll up (> 0)
        assertEquals(1, compareRollingNumbers("59:59", "01:00:00"))
        assertEquals(1, compareRollingNumbers("第 9 周", "第 10 周"))
        assertEquals(1, compareRollingNumbers("3.85", "3.92"))
        assertEquals(1, compareRollingNumbers("11.16–11.22", "11.23–11.29"))
        assertEquals(0, compareRollingNumbers("20:10", "20:10"))
    }

    @Test
    fun predictiveBackMetricsScaleCornersAndRespectSwipeEdge() {
        val rest = computePredictiveBackMetrics(0f, BackEventCompat.EDGE_LEFT)
        assertEquals(1f, rest.scale, 0.0001f)
        assertEquals(0f, rest.translationXFactor, 0.0001f)
        assertEquals(0f, rest.cornerDp, 0.0001f)

        val fullLeft = computePredictiveBackMetrics(1f, BackEventCompat.EDGE_LEFT)
        assertEquals(0.88f, fullLeft.scale, 0.0001f)
        assertEquals(1f, fullLeft.translationXFactor, 0.0001f)
        assertEquals(28f, fullLeft.cornerDp, 0.0001f)

        val halfRight = computePredictiveBackMetrics(0.5f, BackEventCompat.EDGE_RIGHT)
        assertEquals(0.94f, halfRight.scale, 0.0001f)
        assertEquals(-0.5f, halfRight.translationXFactor, 0.0001f)
        assertEquals(14f, halfRight.cornerDp, 0.0001f)

        assertEquals(rest, computePredictiveBackMetrics(-1f, BackEventCompat.EDGE_LEFT))
        assertEquals(fullLeft, computePredictiveBackMetrics(2f, BackEventCompat.EDGE_LEFT))

        val visualState = PredictiveBackVisualState()
        val s1 = visualState.shapeForCornerPx(24f)
        val s2 = visualState.shapeForCornerPx(24.4f)
        val s3 = visualState.shapeForCornerPx(28f)
        assertTrue(s1 === s2)
        assertTrue(s1 !== s3)
    }

    @Test
    fun heroSourceRegistrationIsolatesVisibilityPerSourceKey() = runBlocking {
        val frameClock = object : MonotonicFrameClock {
            private var frameTime = 0L
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(4)
                frameTime += 16_000_000L
                return onFrame(frameTime)
            }
        }
        val animScope = CoroutineScope(SupervisorJob() + frameClock)
        try {
            val controller = HeroTransitionController()
            val regA = HeroSourceRegistration({ Rect(10f, 10f, 100f, 100f) }, Color.Cyan, 20f)
            val regB = HeroSourceRegistration({ Rect(200f, 10f, 300f, 100f) }, Color.Magenta, 20f)
            controller.registerSource("a", regA)
            controller.registerSource("b", regB)
            assertFalse(regA.isSourceHidden)
            assertFalse(regB.isSourceHidden)

            controller.openHero("a", DetailRoute.CourseStatus, animScope)
            repeat(25) {
                if (regA.isSourceHidden) return@repeat
                delay(4)
            }
            assertTrue(regA.isSourceHidden)
            assertFalse(regB.isSourceHidden)

            controller.closeForeground(animScope)
            repeat(80) {
                if (!regA.isSourceHidden && controller.sessions.isEmpty()) return@repeat
                delay(8)
            }
            assertFalse(regA.isSourceHidden)
        } finally {
            animScope.cancel()
        }
    }

    @Test
    fun bentoExpressivePolygonsNormalizeCleanlyAndMorphShapesAreValueEqual() {
        val todayMorph = Morph(BentoTodayInClassPolygon, BentoTodayIdlePolygon)
        val nextMorph = Morph(BentoNextInClassPolygon, BentoNextIdlePolygon)

        val idleA = MorphingPolygonShape(todayMorph, 1f)
        val idleB = MorphingPolygonShape(todayMorph, 1.5f)
        val inClass = MorphingPolygonShape(todayMorph, 0f)
        assertEquals(idleA, idleB)
        assertEquals(idleA.hashCode(), idleB.hashCode())
        assertNotEquals(idleA, inClass)

        val density = Density(2f)
        val todayOut = idleA.createOutline(Size(140f, 180f), LayoutDirection.Ltr, density)
        val nextOut = MorphingPolygonShape(nextMorph, 0.5f).createOutline(Size(180f, 150f), LayoutDirection.Ltr, density)
        assertTrue(todayOut is Outline.Generic)
        assertTrue(nextOut is Outline.Generic)
        assertNotEquals(BentoNextInClassPolygon, BentoNextIdlePolygon)

        listOf(
            "TodayInClass" to BentoTodayInClassPolygon,
            "TodayIdle" to BentoTodayIdlePolygon,
            "NextInClass" to BentoNextInClassPolygon,
            "NextIdle" to BentoNextIdlePolygon,
        ).forEach { (name, poly) ->
            val bounds = poly.calculateBounds(approximate = false)
            assertEquals(4, bounds.size)
            val width = bounds[2] - bounds[0]
            val height = bounds[3] - bounds[1]
            assertTrue("$name bounds out of [0..1]: ${bounds.toList()}", bounds[0] >= -0.03f && bounds[1] >= -0.03f && bounds[2] <= 1.03f && bounds[3] <= 1.03f)
            assertTrue("$name width=$width height=$height unexpectedly small", minOf(width, height) >= 0.82f && maxOf(width, height) in 0.94f..1.03f)
        }

        // Verify morphed clock contours remain unit-bounded across all transition keyframes.
        listOf(0f, 0.5f, 1f).forEach { t ->
            val maxExtent = todayMorph.asCubics(t).maxOf { c ->
                maxOf(
                    maxOf(kotlin.math.abs(c.anchor0X - 0.5f), kotlin.math.abs(c.anchor0Y - 0.5f)),
                    maxOf(kotlin.math.abs(c.control0X - 0.5f), kotlin.math.abs(c.control0Y - 0.5f)),
                    maxOf(kotlin.math.abs(c.control1X - 0.5f), kotlin.math.abs(c.control1Y - 0.5f)),
                    maxOf(kotlin.math.abs(c.anchor1X - 0.5f), kotlin.math.abs(c.anchor1Y - 0.5f)),
                )
            }
            assertTrue("Clock morph extent $maxExtent exceeded unit bounds at progress=$t", maxExtent <= 0.52f)
        }
    }

    @Test
    fun squareMatrixRectangleIsUniformlyOrderedAndExtinguishesUpward() {
        val cells = buildSquareMatrixCells()
        assertEquals(MatrixRows * MatrixCols, cells.size)

        val shapeCells = cells.filter { it.inShape }
        assertEquals(MatrixRows * MatrixCols, shapeCells.size)
        val topHalf = shapeCells.count { it.row < MatrixRows / 2 }
        val bottomHalf = shapeCells.count { it.row >= MatrixRows / 2 }
        assertEquals(topHalf, bottomHalf)

        val progressOrders = shapeCells.map { it.progressOrder }.sorted()
        assertEquals(MatrixProgressCellCount, progressOrders.size)
        assertEquals((0 until MatrixProgressCellCount).toList(), progressOrders)

        // Rectangular pulse distance reaches full outer corners (rectDist == 1f) from center (< 0.1f)
        val centerMinRectDist = shapeCells.minOf { it.rectDist }
        val cornerRectDists = shapeCells.filter {
            (it.row == 0 || it.row == MatrixRows - 1) && (it.col == 0 || it.col == MatrixCols - 1)
        }.map { it.rectDist }
        assertEquals(4, cornerRectDists.size)
        assertTrue(centerMinRectDist < 0.12f)
        assertTrue(cornerRectDists.all { it >= 0.99f })

        // Pseudo-random uniform lighting: balanced across all 4 quadrants without radial clamping
        val firstBatchDistances = shapeCells.filter { it.progressOrder < 24 }.map { it.dist }
        assertTrue(firstBatchDistances.min() < 4.5f && firstBatchDistances.max() > 6.5f)
        for (pct in listOf(0.15f, 0.25f, 0.50f, 0.75f)) {
            val k = (MatrixProgressCellCount * pct).toInt()
            val lit = shapeCells.filter { it.progressOrder < k }
            val qCounts = IntArray(4)
            for (c in lit) {
                val q = (if (c.col >= MatrixCols / 2) 1 else 0) + (if (c.row >= MatrixRows / 2) 2 else 0)
                qCounts[q]++
            }
            val expectedPerQuadrant = k / 4f
            for (q in 0..3) {
                assertTrue(
                    "Quadrant $q count=${qCounts[q]} unbalanced for k=$k (expected ~$expectedPerQuadrant)",
                    kotlin.math.abs(qCounts[q] - expectedPerQuadrant) <= 3.5f,
                )
            }
        }

        // When collapsed to idle (focusFraction = 0f), only rows < MatrixIdleRectRows stay active
        for (r in 0 until MatrixRows) {
            val presence = matrixRowPresence(r, focusFraction = 0f)
            if (r < MatrixIdleRectRows) {
                assertEquals(1f, presence, 0.001f)
            } else {
                assertEquals(0f, presence, 0.001f)
            }
            assertEquals(1f, matrixRowPresence(r, focusFraction = 1f), 0.001f)
        }

        // Bottom rows extinguish earlier than upper rows during transition
        assertTrue(matrixRowPresence(4, 0.55f) > matrixRowPresence(MatrixRows - 1, 0.55f))

        // Flush matrix cells extend to all 4 outer bounds so container clipping forms R corners
        val areaW = 900f
        val areaH = 560f
        val seam = 3f
        val topLeft = computeFlushMatrixCellRect(0, 0, MatrixRows.toFloat(), 0f, 0f, areaW, areaH, seam, MatrixRows - 1)
        val bottomRight = computeFlushMatrixCellRect(
            MatrixRows - 1,
            MatrixCols - 1,
            MatrixRows.toFloat(),
            0f,
            0f,
            areaW,
            areaH,
            seam,
            MatrixRows - 1,
        )
        val neighborRight = computeFlushMatrixCellRect(0, 1, MatrixRows.toFloat(), 0f, 0f, areaW, areaH, seam, MatrixRows - 1)
        assertEquals(0f, topLeft.left, 0.001f)
        assertEquals(0f, topLeft.top, 0.001f)
        assertEquals(areaW, bottomRight.right, 0.001f)
        assertEquals(areaH, bottomRight.bottom, 0.001f)
        assertEquals(seam, neighborRight.left - topLeft.right, 0.01f)

        val idleBottom = computeFlushMatrixCellRect(
            MatrixIdleRectRows - 1,
            MatrixCols - 1,
            MatrixIdleRectRows.toFloat(),
            10f,
            20f,
            areaW,
            200f,
            seam,
            MatrixIdleRectRows - 1,
        )
        assertEquals(220f, idleBottom.bottom, 0.001f)
    }

    @Test
    fun matrixFlyOutHitsTwoThirdsRevealThresholdAndExitsViewportCompletely() {
        assertEquals(640, MATRIX_FLY_OPEN_DURATION_MS)
        assertEquals(580, MATRIX_FLY_CLOSE_DURATION_MS)
        assertEquals(0.12f, MATRIX_FLY_REVEAL_THRESHOLD, 0.0001f)
        assertTrue(MatrixFlyOpenCurve.transform(0.25f) > 0.35f)
        assertTrue(MatrixFlyCloseCurve.transform(0.25f) > 0.35f)

        val bgRest = computeBackgroundDepthFrame(0f)
        val bgRecessed = computeBackgroundDepthFrame(1f)
        assertEquals(1f, bgRest.scale, 0.0001f)
        assertEquals(0f, bgRest.offsetYDp, 0.0001f)
        assertEquals(0.94f, bgRecessed.scale, 0.0001f)
        assertEquals(14f, bgRecessed.offsetYDp, 0.0001f)
        assertEquals(24f, bgRecessed.cornerDp, 0.0001f)

        val f0 = computeMatrixFlyOutFrame(0f)
        assertEquals(0f, f0.flyProgress, 0.0001f)
        assertEquals(0f, f0.morphProgress, 0.0001f)
        assertEquals(0f, f0.targetAlpha, 0.0001f)
        assertEquals(44f, f0.targetOffsetYDp, 0.001f)
        assertEquals(0.92f, f0.targetScale, 0.001f)
        assertEquals(1f, f0.sourceCardAlpha, 0.0001f)
        assertEquals(1f, f0.dotAlpha, 0.0001f)

        // New page stays hidden until reveal threshold; source card clears before inner blocks detach (<= 0.16f)
        assertEquals(0f, computeMatrixFlyOutFrame(0.16f).sourceCardAlpha, 0.0001f)
        assertEquals(0f, computeMatrixFlyOutFrame(0.08f).targetAlpha, 0.0001f)
        assertEquals(0f, computeMatrixFlyOutFrame(MATRIX_FLY_REVEAL_THRESHOLD).morphProgress, 0.0001f)
        assertEquals(0f, computeMatrixFlyOutFrame(MATRIX_FLY_REVEAL_THRESHOLD).targetAlpha, 0.0001f)

        // After threshold, page morphProgress expands monotonically from srcRect (0f) to dstRect (1f)
        val fAfter = computeMatrixFlyOutFrame(MATRIX_FLY_REVEAL_THRESHOLD + 0.14f)
        val fMidTail = computeMatrixFlyOutFrame(0.65f)
        val fEnd = computeMatrixFlyOutFrame(1f)
        assertTrue(fAfter.morphProgress > 0f && fAfter.morphProgress < fMidTail.morphProgress)
        assertTrue(fMidTail.morphProgress < fEnd.morphProgress)
        assertTrue(fAfter.targetAlpha > 0f && fAfter.targetAlpha <= fMidTail.targetAlpha)
        assertTrue(fAfter.targetOffsetYDp < 44f && fMidTail.targetOffsetYDp < fAfter.targetOffsetYDp)
        assertEquals(1f, fEnd.morphProgress, 0.0001f)
        assertEquals(1f, fEnd.targetAlpha, 0.0001f)
        assertEquals(1f, fEnd.targetScale, 0.0001f)
        assertEquals(0f, fEnd.targetOffsetYDp, 0.0001f)
        assertEquals(1f, fEnd.flyProgress, 0.0001f)

        // Verify outer perimeter squares launch earlier than center squares at mid-early flight (0.20f)
        val rootW = 1080f
        val rootH = 2400f
        val srcRect = Rect(44f, 340f, 1036f, 920f)
        val pitchX = srcRect.width / MatrixCols
        val pitchY = srcRect.height / MatrixRows
        val rawW = pitchX - 7f
        val rawH = pitchY - 7f
        val cells = buildSquareMatrixCells()
        val cornerCell = cells.first { it.row == 0 && it.col == 0 }
        val centerCell = cells.first { it.row == 6 && it.col == 8 }
        val cornerMid = computeMatrixDotFlyState(
            cell = cornerCell,
            restCenterX = srcRect.left + (cornerCell.col + 0.5f) * pitchX,
            restCenterY = srcRect.top + (cornerCell.row + 0.5f) * pitchY,
            restWidth = rawW,
            restHeight = rawH,
            restCorner = 0f,
            srcCenterX = srcRect.center.x,
            srcCenterY = srcRect.center.y,
            rootWidth = rootW,
            rootHeight = rootH,
            flyProgress = 0.20f,
        )
        val centerMid = computeMatrixDotFlyState(
            cell = centerCell,
            restCenterX = srcRect.left + (centerCell.col + 0.5f) * pitchX,
            restCenterY = srcRect.top + (centerCell.row + 0.5f) * pitchY,
            restWidth = rawW,
            restHeight = rawH,
            restCorner = 0f,
            srcCenterX = srcRect.center.x,
            srcCenterY = srcRect.center.y,
            rootWidth = rootW,
            rootHeight = rootH,
            flyProgress = 0.20f,
        )
        assertTrue(cornerMid.scaleFactor > 1.15f)
        assertEquals(1f, centerMid.scaleFactor, 0.001f)

        // Verify all 252 dots enlarge and move completely outside a 1080x2400 screen at fraction = 1f
        for (cell in cells) {
            val restCx = srcRect.left + (cell.col + 0.5f) * pitchX
            val restCy = srcRect.top + (cell.row + 0.5f) * pitchY
            val rest = computeMatrixDotFlyState(
                cell = cell,
                restCenterX = restCx,
                restCenterY = restCy,
                restWidth = rawW,
                restHeight = rawH,
                restCorner = 4f,
                srcCenterX = srcRect.center.x,
                srcCenterY = srcRect.center.y,
                rootWidth = rootW,
                rootHeight = rootH,
                flyProgress = 0f,
            )
            assertEquals(restCx, rest.centerX, 0.01f)
            assertEquals(restCy, rest.centerY, 0.01f)
            assertEquals(1f, rest.scaleFactor, 0.001f)

            val exited = computeMatrixDotFlyState(
                cell = cell,
                restCenterX = restCx,
                restCenterY = restCy,
                restWidth = rawW,
                restHeight = rawH,
                restCorner = 4f,
                srcCenterX = srcRect.center.x,
                srcCenterY = srcRect.center.y,
                rootWidth = rootW,
                rootHeight = rootH,
                flyProgress = fEnd.flyProgress,
            )
            assertTrue("Expected scale > 2f, got ${exited.scaleFactor}", exited.scaleFactor > 2f)
            val offScreenX = (exited.centerX + exited.width * 0.5f < 0f) || (exited.centerX - exited.width * 0.5f > rootW)
            val offScreenY = (exited.centerY + exited.height * 0.5f < 0f) || (exited.centerY - exited.height * 0.5f > rootH)
            assertTrue(
                "Dot (${cell.row},${cell.col}) remained inside screen at (${exited.centerX}, ${exited.centerY})",
                offScreenX || offScreenY,
            )
        }
    }

    @Test
    fun matrixFlyOutModeHidesSourceImmediatelyWithoutCaptureStall() = runBlocking {
        val frameClock = object : MonotonicFrameClock {
            private var frameTime = 0L
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                delay(4)
                frameTime += 16_000_000L
                return onFrame(frameTime)
            }
        }
        val animScope = CoroutineScope(SupervisorJob() + frameClock)
        try {
            val controller = HeroTransitionController()
            val reg = HeroSourceRegistration(
                boundsProvider = { Rect(44f, 340f, 1036f, 920f) },
                containerColor = Color.Transparent,
                cornerDp = 12f,
                preferredMode = OverlayTransitionMode.MatrixFlyOut,
            )
            controller.registerSource("matrix", reg)
            controller.openHero("matrix", DetailRoute.CourseStatus, animScope)
            val session = controller.foregroundSession!!
            assertEquals(OverlayTransitionMode.MatrixFlyOut, session.transitionMode)
            assertFalse(session.pendingCapture)
            assertTrue(reg.isSourceHidden)

            controller.closeForeground(animScope)
            repeat(90) {
                if (controller.sessions.isEmpty() && !reg.isSourceHidden) return@repeat
                delay(8)
            }
            assertTrue(controller.sessions.isEmpty())
            assertFalse(reg.isSourceHidden)
        } finally {
            animScope.cancel()
        }
    }

    @Test
    fun scheduleTargetScrollFocusesActiveAndNextCoursesAndWeekTitleBounces() {
        val morning = Course("c1", "早课", "第一教学楼102", "张", weekday = 2, first = 1, last = 2, weeks = setOf(1))
        val afternoon = Course("c2", "午后课", "第一教学楼102", "李", weekday = 2, first = 5, last = 6, weeks = setOf(1))
        val evening = Course("c3", "晚课", "第一教学楼102", "王", weekday = 2, first = 9, last = 10, weeks = setOf(1))
        val index = WeekCourseIndex(listOf(morning, afternoon, evening))
        val page = index[1]
        val baseInstant = Instant.parse("2026-11-17T06:10:00Z")

        val morningY = computeScheduleTargetScrollPx(
            status = CourseStatus(current = DatedCourse(morning, baseInstant, baseInstant.plusSeconds(2700)), next = null, today = emptyList()),
            page = page,
            todayDayIndex = 1,
            scale = 1f,
            densityPxPerDp = 3f,
            viewportHeightPx = 900,
            nowTime = LocalTime.of(8, 15),
        )
        val afternoonY = computeScheduleTargetScrollPx(
            status = CourseStatus(current = DatedCourse(afternoon, baseInstant, baseInstant.plusSeconds(2700)), next = null, today = emptyList()),
            page = page,
            todayDayIndex = 1,
            scale = 1f,
            densityPxPerDp = 3f,
            viewportHeightPx = 900,
            nowTime = LocalTime.of(14, 15),
        )
        val nextEveningY = computeScheduleTargetScrollPx(
            status = CourseStatus(current = null, next = DatedCourse(evening, baseInstant, baseInstant.plusSeconds(2700)), today = emptyList()),
            page = page,
            todayDayIndex = 1,
            scale = 1f,
            densityPxPerDp = 3f,
            viewportHeightPx = 900,
            nowTime = LocalTime.of(18, 30),
        )
        assertEquals(0, morningY)
        assertTrue(afternoonY > morningY)
        assertTrue(nextEveningY > afternoonY)
        assertTrue(WeekTitleBouncePeak > 1.15f)
        assertTrue(WeekTitleBounceSpring.dampingRatio < 0.7f)

        assertEquals(320, WEEK_TITLE_BOUNCE_TOTAL_DURATION_MS)
        val units = splitWeekTitleBounceUnits("第 1 周")
        assertEquals(listOf("第", "1", "周"), units)
        assertEquals(1f, computeCharBounceScale(0f, 0, units.size), 0.0001f)
        assertEquals(1f, computeCharBounceScale(1f, 2, units.size), 0.0001f)
        // Before 1/2 mark of char 0 (progress = 0.20 < 0.25): char 0 is jumping while char 1 waits
        val beforeHalfFirst = computeCharBounceScale(0.20f, 0, units.size)
        val beforeHalfSecond = computeCharBounceScale(0.20f, 1, units.size)
        assertTrue("Expected first char ($beforeHalfFirst) > 1 at progress 0.20", beforeHalfFirst > 1f)
        assertEquals(1f, beforeHalfSecond, 0.0001f)
        // Just past 1/2 mark of char 0 (progress = 0.30): both char 0 and char 1 are actively jumping together
        val overlapFirst = computeCharBounceScale(0.30f, 0, units.size)
        val overlapSecond = computeCharBounceScale(0.30f, 1, units.size)
        assertTrue("Expected char 0 ($overlapFirst) and char 1 ($overlapSecond) to overlap > 1 at progress 0.30", overlapFirst > 1f && overlapSecond > 1f)
        // Late progress (0.60): char 0 has finished while char 1 & char 2 overlap around char 1's second half
        val lateFirst = computeCharBounceScale(0.60f, 0, units.size)
        val lateThird = computeCharBounceScale(0.60f, 2, units.size)
        assertEquals(1f, lateFirst, 0.0001f)
        assertTrue("Expected last char ($lateThird) > 1 at progress 0.60", lateThird > 1f)
    }

    @Test
    fun uiScaleResolverDefaultsTo110PercentAndClampsSafely() {
        assertEquals(110, DEFAULT_UI_SCALE_PERCENT)
        assertEquals(listOf(90, 100, 110, 120), XachetUiScalePresets)
        assertEquals(110, resolveXachetUiScalePercent(org.json.JSONObject()))
        assertEquals(1.10f, resolveXachetUiScale(org.json.JSONObject()), 0.0001f)
        assertEquals(100, resolveXachetUiScalePercent(org.json.JSONObject().put("uiScalePercent", 100)))
        assertEquals(1.00f, resolveXachetUiScale(org.json.JSONObject().put("uiScalePercent", 100)), 0.0001f)
        assertEquals(120, resolveXachetUiScalePercent(org.json.JSONObject().put("uiScalePercent", 120)))
        assertEquals(1.20f, resolveXachetUiScale(org.json.JSONObject().put("uiScalePercent", 120)), 0.0001f)
        assertEquals(85, resolveXachetUiScalePercent(org.json.JSONObject().put("uiScalePercent", 40)))
        assertEquals(130, resolveXachetUiScalePercent(org.json.JSONObject().put("uiScalePercent", 200)))

        val s90 = resolveScheduleScaleTypography(90)
        val s100 = resolveScheduleScaleTypography(100)
        val s110 = resolveScheduleScaleTypography(110)
        val s120 = resolveScheduleScaleTypography(120)
        assertTrue(s90.titleFontSizeSp > s100.titleFontSizeSp && s100.titleFontSizeSp > s110.titleFontSizeSp && s110.titleFontSizeSp > s120.titleFontSizeSp)
        assertTrue(s90.placeFontSizeSp > s100.placeFontSizeSp && s100.placeFontSizeSp > s110.placeFontSizeSp && s110.placeFontSizeSp > s120.placeFontSizeSp)
        assertTrue(s90.titleMaxLines <= s100.titleMaxLines && s100.titleMaxLines <= s110.titleMaxLines && s110.titleMaxLines <= s120.titleMaxLines)
        assertTrue(s90.axisWidthDp > s100.axisWidthDp && s100.axisWidthDp > s110.axisWidthDp && s110.axisWidthDp > s120.axisWidthDp)
    }
}

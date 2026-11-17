@file:OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.pluvist.sachet.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay

internal const val MatrixCols: Int = 18
internal const val MatrixRows: Int = 14
internal const val MatrixIdleRectRows: Int = 4

internal data class MatrixCell(
    val row: Int,
    val col: Int,
    val dist: Float,
    val rectDist: Float,
    val inShape: Boolean,
    val progressOrder: Int,
)

/**
 * Builds the `18 x 14` (`252`-cell) rectangular dot matrix:
 * - Every cell is active (`inShape = true`), covering the full rectangular card region.
 * - [MatrixCell.rectDist] normalizes `[0..1]` from center out to all 4 edges & corners so
 *   1s pulses sweep cleanly across the entire rectangle.
 * - [MatrixCell.progressOrder] uses stratified `3 x 2` macro-blocks interleaved across 4 balanced
 *   quadrants + anti-clump scoring with deterministic PRNG for pseudo-random yet spatially uniform lighting.
 */
internal fun buildSquareMatrixCells(): List<MatrixCell> {
    val cx = (MatrixCols - 1) / 2f
    val cy = (MatrixRows - 1) / 2f
    val maxEuclidDist = hypot(cx, cy).coerceAtLeast(1f)

    val raw = buildList(MatrixRows * MatrixCols) {
        for (r in 0 until MatrixRows) {
            for (c in 0 until MatrixCols) {
                val dx = c - cx
                val dy = r - cy
                val dist = hypot(dx, dy)
                val chebyshevNorm = max(abs(dx) / cx, abs(dy) / cy)
                val euclidNorm = (dist / maxEuclidDist).coerceIn(0f, 1f)
                val rectDist = (chebyshevNorm * 0.78f + euclidNorm * 0.22f).coerceIn(0f, 1f)
                add(
                    MatrixCell(
                        row = r,
                        col = c,
                        dist = dist,
                        rectDist = rectDist,
                        inShape = true,
                        progressOrder = -1,
                    ),
                )
            }
        }
    }

    val rng = Random(0x58A7E757)
    val blockCols = 6
    val blockRows = 7
    val totalBlocks = blockCols * blockRows // 42 macro-blocks of 3x2 cells = 252 cells
    val remainingInBlock = Array(totalBlocks) { bIdx ->
        val br = bIdx / blockCols
        val bc = bIdx % blockCols
        buildList(6) {
            for (dr in 0 until 2) {
                for (dc in 0 until 3) {
                    add(br * 2 + dr to bc * 3 + dc)
                }
            }
        }.toMutableList()
    }
    val quadrantBlocks = Array(4) { mutableListOf<Int>() }
    for (br in 0 until blockRows) {
        for (bc in 0 until blockCols) {
            val q = (if (bc >= 3) 1 else 0) + (if (br >= 3 + (bc % 2)) 2 else 0)
            quadrantBlocks[q].add(br * blockCols + bc)
        }
    }

    val litFlags = BooleanArray(MatrixRows * MatrixCols)
    val orderByCoord = IntArray(MatrixRows * MatrixCols)
    var nextOrder = 0

    for (round in 0 until 6) {
        val qLists = Array(4) { q -> quadrantBlocks[q].toMutableList().also { it.shuffle(rng) } }
        val roundSequence = buildList(totalBlocks) {
            while (size < totalBlocks) {
                val qOrder = listOf(0, 1, 2, 3).shuffled(rng)
                for (q in qOrder) {
                    if (qLists[q].isNotEmpty()) {
                        add(qLists[q].removeAt(qLists[q].lastIndex))
                    }
                }
            }
        }

        for (bIdx in roundSequence) {
            val candidates = remainingInBlock[bIdx]
            var bestIdx = 0
            var bestScore = Float.MAX_VALUE
            for (i in candidates.indices) {
                val (r, c) = candidates[i]
                var crowding = 0f
                val rMin = (r - 2).coerceAtLeast(0)
                val rMax = (r + 2).coerceAtMost(MatrixRows - 1)
                val cMin = (c - 2).coerceAtLeast(0)
                val cMax = (c + 2).coerceAtMost(MatrixCols - 1)
                for (nr in rMin..rMax) {
                    for (nc in cMin..cMax) {
                        if (litFlags[nr * MatrixCols + nc]) {
                            val dSq = ((nr - r) * (nr - r) + (nc - c) * (nc - c)).toFloat()
                            crowding += 1f / (1f + dSq)
                        }
                    }
                }
                val score = crowding + rng.nextFloat() * 0.32f
                if (score < bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
            val (chosenR, chosenC) = candidates.removeAt(bestIdx)
            val flatIdx = chosenR * MatrixCols + chosenC
            litFlags[flatIdx] = true
            orderByCoord[flatIdx] = nextOrder++
        }
    }

    return raw.map { cell ->
        cell.copy(progressOrder = orderByCoord[cell.row * MatrixCols + cell.col])
    }
}

/** Extinguishes rows bottom-to-top from row 14 toward top rect rows 0..3 as focus drops 1f -> 0f. */
internal fun matrixRowPresence(row: Int, focusFraction: Float): Float {
    if (row < MatrixIdleRectRows) return 1f
    val cutoff = ((row - (MatrixIdleRectRows - 1)) / (MatrixRows - MatrixIdleRectRows).toFloat()) * 0.66f
    return ((focusFraction - cutoff) / 0.34f).coerceIn(0f, 1f)
}

private val HomeSquareMatrixCells: List<MatrixCell> = buildSquareMatrixCells()
internal val MatrixProgressCellCount: Int = HomeSquareMatrixCells.count { it.progressOrder >= 0 }

/**
 * Home Course Stage Bento:
 * - Upper stage ("当前课程"): In-class expands & centers into a large theme-colored square-grid
 *   circle with 1-second radial/clockwise square pulses; when idle/waiting, squares extinguish upward
 *   into a sleek horizontal rectangular strip beneath the Xachet header. Free Classrooms button is hidden.
 * - Lower stage ("下一节课"): Full-width [NextCourseCard] morphing between compact Fan Squircle
 *   (in-class) and enlarged Sweeping Right D-Arch (idle).
 */
@Composable
internal fun HomeCourseBentoSection(
    data: AppData,
    status: CourseStatus,
    bentoHeight: Dp,
    onOpenStatus: () -> Unit,
    onOpenNextCourse: (Course) -> Unit,
    onOpenNextFallback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = status.current
    val next = status.next
    val inClass = active != null
    val fontScale = LocalDensity.current.fontScale

    val spatialDpSpec = remember { spring<Dp>(dampingRatio = 0.85f, stiffness = 350f) }
    val spatialFloatSpec = remember { spring<Float>(dampingRatio = 0.85f, stiffness = 350f) }

    val focusT by animateFloatAsState(
        targetValue = if (inClass) 1f else 0f,
        animationSpec = spatialFloatSpec,
        label = "MatrixFocusT",
    )

    val heroFlightColor = when {
        active != null -> MaterialTheme.colorScheme.surfaceContainerLow
        data.hasSchedule -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val inClassContentColor = MaterialTheme.colorScheme.onSurface
    val nextContainerColor = MaterialTheme.colorScheme.surfaceContainerLow

    val inClassHeroHeight = (bentoHeight - 116.dp).coerceIn(176.dp, 256.dp)
    val idleRectHeight = 84.dp

    val heroHeight by animateDpAsState(
        targetValue = if (inClass) inClassHeroHeight else idleRectHeight,
        animationSpec = spatialDpSpec,
        label = "MatrixHeroHeight",
    )
    val nextHeight by animateDpAsState(
        targetValue = if (inClass) {
            (bentoHeight - inClassHeroHeight - 12.dp).coerceIn(96.dp, 132.dp)
        } else {
            if (bentoHeight < 330.dp) 136.dp else 182.dp
        },
        animationSpec = spatialDpSpec,
        label = "MatrixNextHeight",
    )
    val heroCorner = 28.dp
    val compactMatrixText = inClassHeroHeight < 210.dp || fontScale > 1.25f

        @Composable
        fun TodayMatrixHeroCard() {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .heroFlightSource(
                        key = "home-bento-status",
                        containerColor = heroFlightColor,
                        cornerDp = heroCorner.value,
                        mode = OverlayTransitionMode.MatrixFlyOut,
                    )
                    .clickable(onClick = onOpenStatus),
                contentAlignment = Alignment.Center,
            ) {
                MatrixSquareGridCanvas(
                    active = active,
                    hasSchedule = data.hasSchedule,
                    focusFraction = focusT,
                    modifier = Modifier.fillMaxSize(),
                )

                TodayMatrixCardForeground(
                    active = active,
                    next = next,
                    status = status,
                    hasSchedule = data.hasSchedule,
                    compactMatrixText = compactMatrixText,
                    inClassContentColor = inClassContentColor,
                    animateModeChange = true,
                    modifier = Modifier.zIndex(1f),
                )
            }
        }

        @Composable
        fun NextCourseCard(modifier: Modifier = Modifier) {
            Card(
                onClick = {
                    if (next != null) {
                        onOpenNextCourse(next.course)
                    } else {
                        onOpenNextFallback()
                    }
                },
                modifier = modifier
                    .fillMaxWidth()
                    .heroFlightSource(
                        key = "home-bento-next",
                        containerColor = nextContainerColor,
                        cornerDp = 28f,
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = nextContainerColor,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = if (!inClass) 20.dp else 18.dp,
                            vertical = if (!inClass) 18.dp else 12.dp,
                        ),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(if (!inClass) 18.dp else 16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "下一节课",
                                style = MaterialTheme.typography.labelMediumEmphasized,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (next != null) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Text(
                                    text = "${next.startTime()}",
                                    style = XachetTypeTokens.PillStatus,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(if (!inClass) 4.dp else 2.dp)) {
                        Text(
                            text = next?.course?.title ?: "暂无下一节课",
                            style = if (!inClass) {
                                MaterialTheme.typography.headlineSmallEmphasized
                            } else {
                                MaterialTheme.typography.titleMediumEmphasized
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (!inClass) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (next != null) {
                            RollingNumberText(
                                text = "${next.startTime()}–${next.endTime()} · ${next.course.place}",
                                style = if (!inClass) {
                                    MaterialTheme.typography.bodyMedium.tabular()
                                } else {
                                    XachetTypeTokens.CondensedMeta
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = if (status.completed) "今日课程安排已全部完成" else "点击查看当日完整课表",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TodayMatrixHeroCard()
        NextCourseCard(modifier = Modifier.height(nextHeight))
    }
}

@Composable
private fun TodayMatrixCardForeground(
    active: DatedCourse?,
    next: DatedCourse?,
    status: CourseStatus,
    hasSchedule: Boolean,
    compactMatrixText: Boolean,
    inClassContentColor: Color,
    animateModeChange: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentBlock: @Composable (Boolean) -> Unit = { isClassRunning ->
        if (isClassRunning && active != null) {
            Column(
                modifier = modifier.padding(
                    horizontal = if (compactMatrixText) 22.dp else 28.dp,
                    vertical = if (compactMatrixText) 16.dp else 22.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Text(
                        text = "正在上课",
                        style = XachetTypeTokens.PillStatus,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.height(if (compactMatrixText) 5.dp else 8.dp))
                Text(
                    text = active.course.title,
                    style = if (compactMatrixText) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleLargeEmphasized
                    },
                    color = inClassContentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${active.startTime()}–${active.endTime()}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = inClassContentColor.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                if (active.course.place.isNotBlank()) {
                    Text(
                        text = active.course.place,
                        style = MaterialTheme.typography.labelSmall,
                        color = inClassContentColor.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(if (compactMatrixText) 5.dp else 8.dp))
                LiveCourseCountdownText(
                    end = active.end,
                    style = XachetTypeTokens.BentoCountdown.copy(
                        fontSize = if (compactMatrixText) 21.sp else 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = inClassContentColor,
                    ),
                )
            }
        } else {
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    !hasSchedule -> Icons.Rounded.School
                                    status.completed -> Icons.Rounded.CheckCircle
                                    else -> Icons.Rounded.Weekend
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = when {
                                !hasSchedule -> "导入你的课表"
                                status.completed -> "今日课程已完成"
                                next != null -> "课间等待中"
                                else -> "今日无课安排"
                            },
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                !hasSchedule -> "登录或导入本地课表开启倒计时"
                                status.today.isEmpty() -> "今日无课，好好休息"
                                status.completed -> "今日 ${status.today.size} 节课均已结束"
                                else -> "今日共 ${status.today.size} 节 · 点击查看详情"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        text = if (status.completed) "已完 ${status.today.size} 节" else "详情",
                        style = XachetTypeTokens.PillStatus,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

    if (animateModeChange) {
        AnimatedContent(
            targetState = active != null,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(160))
            },
            label = "MatrixContentMode",
        ) { isClassRunning ->
            contentBlock(isClassRunning)
        }
    } else {
        contentBlock(active != null)
    }
}

@Immutable
private data class MatrixGridPalette(
    val litTileColor: Color,
    val pulsePeakColor: Color,
    val dimCellColor: Color,
    val dimPulseWaveColor: Color,
    val rectBaseColor: Color,
    val rectSquareTint: Color,
    val rectSquareAltTint: Color,
    val flyGlowLitColor: Color,
    val flyGlowDimColor: Color,
)

@Composable
private fun rememberMatrixGridPalette(hasSchedule: Boolean): MatrixGridPalette {
    val scheme = MaterialTheme.colorScheme
    return MatrixGridPalette(
        litTileColor = scheme.primaryContainer.copy(alpha = 0.72f),
        pulsePeakColor = lerp(scheme.primaryContainer, scheme.primary, 0.24f).copy(alpha = 0.86f),
        dimCellColor = scheme.surfaceContainerHigh.copy(alpha = 0.62f),
        dimPulseWaveColor = lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.56f).copy(alpha = 0.72f),
        rectBaseColor = scheme.surfaceContainerLow,
        rectSquareTint = if (hasSchedule) {
            scheme.secondaryContainer.copy(alpha = 0.36f)
        } else {
            scheme.surfaceContainerHigh.copy(alpha = 0.42f)
        },
        rectSquareAltTint = if (hasSchedule) {
            scheme.secondaryContainer.copy(alpha = 0.24f)
        } else {
            scheme.surfaceContainerHigh.copy(alpha = 0.30f)
        },
        flyGlowLitColor = lerp(scheme.primaryContainer, scheme.primary, 0.42f).copy(alpha = 0.94f),
        flyGlowDimColor = lerp(scheme.surfaceContainerHigh, scheme.primaryContainer, 0.45f).copy(alpha = 0.76f),
    )
}

/**
 * Computes edge-to-edge block bounds filling `[areaLeft..areaLeft+areaWidth] x [areaTop..areaTop+areaHeight]`.
 * Only interior boundaries keep a slim [seamPx] hairline while the 4 outer edges sit flush so
 * container clipping renders the card's rounded corners directly across corner blocks.
 */
internal fun computeFlushMatrixCellRect(
    row: Int,
    col: Int,
    visibleRows: Float,
    areaLeft: Float,
    areaTop: Float,
    areaWidth: Float,
    areaHeight: Float,
    seamPx: Float,
    lastVisibleRow: Int,
): Rect {
    val pitchX = areaWidth / MatrixCols.toFloat()
    val pitchY = areaHeight / visibleRows
    val halfSeam = seamPx * 0.5f

    val leftInset = if (col == 0) 0f else halfSeam
    val rightInset = if (col == MatrixCols - 1) 0f else halfSeam
    val topInset = if (row == 0) 0f else halfSeam
    val bottomInset = if (row >= lastVisibleRow) 0f else halfSeam

    val l = areaLeft + col * pitchX + leftInset
    val t = areaTop + row * pitchY + topInset
    val r = areaLeft + (col + 1) * pitchX - rightInset
    val b = areaTop + (row + 1) * pitchY - bottomInset
    return Rect(l, t, max(l + 1f, r), max(t + 1f, b))
}

internal data class MatrixDotFlyState(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val corner: Float,
    val scaleFactor: Float,
)

/**
 * Projects one square block radially outward from [srcCenterX], [srcCenterY] until its enlarged
 * bounds clear all edges of `[0..rootWidth] x [0..rootHeight]` at [flyProgress] = `1f`.
 * Perimeter blocks (`rectDist -> 1f`) launch first while center blocks (`rectDist -> 0f`) trail behind.
 */
internal fun computeMatrixDotFlyState(
    cell: MatrixCell,
    restCenterX: Float,
    restCenterY: Float,
    restWidth: Float,
    restHeight: Float,
    restCorner: Float,
    srcCenterX: Float,
    srcCenterY: Float,
    rootWidth: Float,
    rootHeight: Float,
    flyProgress: Float,
): MatrixDotFlyState {
    val fp = flyProgress.coerceIn(0f, 1f)
    val staggerDelay = (1f - cell.rectDist.coerceIn(0f, 1f)) * 0.28f
    val localRaw = ((fp - staggerDelay) / (1f - staggerDelay)).coerceIn(0f, 1f)
    if (localRaw <= 0.0001f) {
        return MatrixDotFlyState(
            centerX = restCenterX,
            centerY = restCenterY,
            width = restWidth,
            height = restHeight,
            corner = restCorner,
            scaleFactor = 1f,
        )
    }

    val inv = 1f - localRaw
    val localFly = (localRaw * 0.25f + (1f - inv * inv) * 0.75f).coerceIn(0f, 1f)

    val dx = restCenterX - srcCenterX
    val dy = restCenterY - srcCenterY
    val d0 = hypot(dx, dy).coerceAtLeast(1f)
    val ux = dx / d0
    val uy = dy / d0

    val targetScale = 2.8f + 1.3f * cell.rectDist
    val scaleFactor = lerp(1f, targetScale, localFly)
    val drawW = restWidth * scaleFactor
    val drawH = restHeight * scaleFactor

    val maxEnlargedHalf = max(restWidth, restHeight) * 0.5f * targetScale + 32f
    val minX = -maxEnlargedHalf
    val maxX = rootWidth + maxEnlargedHalf
    val minY = -maxEnlargedHalf
    val maxY = rootHeight + maxEnlargedHalf

    val tx = when {
        ux > 1e-4f -> (maxX - srcCenterX) / ux
        ux < -1e-4f -> (minX - srcCenterX) / ux
        else -> Float.MAX_VALUE
    }
    val ty = when {
        uy > 1e-4f -> (maxY - srcCenterY) / uy
        uy < -1e-4f -> (minY - srcCenterY) / uy
        else -> Float.MAX_VALUE
    }
    val exitDistance = minOf(tx, ty).coerceAtLeast(d0 + maxEnlargedHalf)
    val finalDistance = max(exitDistance, d0) + cell.rectDist * max(rootWidth, rootHeight) * 0.16f + d0 * 0.25f
    val currentDistance = lerp(d0, finalDistance, localFly)
    val shapeMorph = (localFly * 3.2f).coerceIn(0f, 1f)
    val corner = lerp(restCorner, minOf(drawW, drawH) * 0.24f, shapeMorph)

    return MatrixDotFlyState(
        centerX = srcCenterX + ux * currentDistance,
        centerY = srcCenterY + uy * currentDistance,
        width = drawW,
        height = drawH,
        corner = corner,
        scaleFactor = scaleFactor,
    )
}

private fun computeMatrixCellColorAndScale(
    cell: MatrixCell,
    active: DatedCourse?,
    focus: Float,
    presence: Float,
    pulse: Float,
    wavePos: Float,
    litTileCount: Int,
    elapsedFraction: Float,
    palette: MatrixGridPalette,
): Pair<Color, Float> {
    val extinguishedSquare = if ((cell.row + cell.col) % 2 == 0) palette.rectSquareTint else palette.rectSquareAltTint
    val activeSquareColor: Color
    if (active != null && focus > 0.04f) {
        val waveHit = (1f - abs(cell.rectDist - wavePos) / 0.28f).coerceIn(0f, 1f) * pulse
        val rectBreath = pulse * 0.42f
        val localPulse = max(waveHit, rectBreath).coerceIn(0f, 1f)
        val idx = cell.progressOrder
        activeSquareColor = when {
            idx in 0 until litTileCount -> lerp(
                palette.litTileColor,
                palette.pulsePeakColor,
                (localPulse * 0.85f).coerceIn(0f, 1f),
            )
            idx == litTileCount && elapsedFraction < 0.999f -> lerp(
                palette.dimCellColor,
                palette.pulsePeakColor,
                pulse.coerceIn(0f, 1f),
            )
            else -> lerp(
                palette.dimCellColor,
                palette.dimPulseWaveColor,
                (localPulse * 0.78f).coerceIn(0f, 1f),
            )
        }
    } else {
        activeSquareColor = extinguishedSquare
    }

    val finalColor = lerp(extinguishedSquare, activeSquareColor, focus)
        .let { c -> c.copy(alpha = c.alpha * presence) }
    val finalScale = (0.55f + 0.45f * presence).coerceIn(0.4f, 1f)
    return finalColor to finalScale
}

/**
 * Renders the `18 x 14` theme-colored flush block matrix:
 * - In class (`focusFraction -> 1f`): color blocks fill the entire rectangular container while outer
 *   corners are cleanly cropped by `MaterialTheme.shapes.extraLarge`; 1s pulse sweeps edge-to-edge.
 * - Idle / waiting (`focusFraction -> 0f`): bottom rows sequentially extinguish upward into a
 *   full-bleed horizontal rectangular strip beneath the title bar.
 */
@Composable
private fun MatrixSquareGridCanvas(
    active: DatedCourse?,
    hasSchedule: Boolean,
    focusFraction: Float,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val pulseAnim = remember { Animatable(0f) }
    var nowMillis by remember(active?.start, active?.end) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(active?.start, active?.end) {
        if (active == null) {
            pulseAnim.snapTo(0f)
            return@LaunchedEffect
        }
        var lastSecond = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).second
        while (true) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val currentSec = Instant.ofEpochMilli(now).atZone(zone).second
            if (currentSec != lastSecond) {
                lastSecond = currentSec
                pulseAnim.snapTo(1f)
                pulseAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 640, easing = FastOutSlowInEasing),
                )
            }
            val remainder = 1000L - (System.currentTimeMillis() % 1000L)
            delay(remainder.coerceIn(90L, 1000L))
        }
    }

    val elapsedFraction = if (active != null) {
        val total = Duration.between(active.start, active.end).toMillis().coerceAtLeast(1L)
        val elapsed = (nowMillis - active.start.toEpochMilli()).coerceIn(0L, total)
        elapsed.toFloat() / total.toFloat()
    } else {
        0f
    }

    val palette = rememberMatrixGridPalette(hasSchedule)

    Canvas(modifier = modifier) {
        val focus = focusFraction.coerceIn(0f, 1f)
        val rectAlpha = (1f - focus).coerceIn(0f, 1f)
        if (rectAlpha > 0.02f) {
            drawRect(
                color = palette.rectBaseColor.copy(alpha = 0.92f * rectAlpha),
                topLeft = Offset.Zero,
                size = size,
            )
        }

        val visibleRows = MatrixIdleRectRows + (MatrixRows - MatrixIdleRectRows) * focus
        val lastVisibleRow = (MatrixIdleRectRows - 1) + ((MatrixRows - MatrixIdleRectRows) * focus).roundToInt()
        val maxSeam = minOf(size.width / MatrixCols.toFloat(), size.height / visibleRows) * 0.35f
        val seamPx = 3.2.dp.toPx().coerceAtMost(maxSeam)

        val pulse = pulseAnim.value
        val wavePos = (1f - pulse) * 1.18f
        val litTileCount = (elapsedFraction.coerceIn(0f, 1f) * MatrixProgressCellCount)
            .toInt()
            .coerceIn(0, MatrixProgressCellCount)

        for (cell in HomeSquareMatrixCells) {
            val rowPresence = matrixRowPresence(cell.row, focus)
            if (rowPresence <= 0.01f) continue

            val presence = rowPresence.coerceIn(0f, 1f)
            val (finalColor, finalScale) = computeMatrixCellColorAndScale(
                cell = cell,
                active = active,
                focus = focus,
                presence = presence,
                pulse = pulse,
                wavePos = wavePos,
                litTileCount = litTileCount,
                elapsedFraction = elapsedFraction,
                palette = palette,
            )

            val slot = computeFlushMatrixCellRect(
                row = cell.row,
                col = cell.col,
                visibleRows = visibleRows,
                areaLeft = 0f,
                areaTop = 0f,
                areaWidth = size.width,
                areaHeight = size.height,
                seamPx = seamPx,
                lastVisibleRow = lastVisibleRow,
            )
            val drawW = slot.width * finalScale
            val drawH = slot.height * finalScale
            val left = slot.center.x - drawW * 0.5f
            val top = slot.center.y - drawH * 0.5f

            drawRect(
                color = finalColor,
                topLeft = Offset(left, top),
                size = Size(drawW, drawH),
            )
        }
    }
}

/**
 * Renders the Home matrix block explosion & symmetrical return transition inside [OverlaySessionHost].
 * At `progress == 0f`, flush color blocks and `28.dp` clipped corners match [TodayMatrixHeroCard]
 * pixel-for-pixel; across `0f -> 1f`, all blocks detach, enlarge, and fly radially off-screen.
 */
@Composable
internal fun HomeMatrixFlyOutOverlay(
    data: AppData,
    status: CourseStatus,
    srcRect: Rect,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val active = status.current
    val next = status.next
    val focus = if (active != null) 1f else 0f
    val elapsedFraction = remember(active?.start, active?.end) {
        if (active != null) {
            val total = Duration.between(active.start, active.end).toMillis().coerceAtLeast(1L)
            val elapsed = (System.currentTimeMillis() - active.start.toEpochMilli()).coerceIn(0L, total)
            elapsed.toFloat() / total.toFloat()
        } else {
            0f
        }
    }
    val palette = rememberMatrixGridPalette(data.hasSchedule)
    val density = LocalDensity.current
    val compactMatrixText = with(density) { srcRect.height.toDp() < 210.dp || fontScale > 1.25f }
    val inClassContentColor = MaterialTheme.colorScheme.onSurface
    val srcWidthDp = with(density) { srcRect.width.toDp() }
    val srcHeightDp = with(density) { srcRect.height.toDp() }
    val restClipPath = remember { Path() }

    Box(modifier = modifier.fillMaxSize()) {
        // Blocks render strictly BELOW foreground course status content (`zIndex(1f)`)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val u = progressProvider()
            if (u >= 0.995f) return@Canvas
            val frame = computeMatrixFlyOutFrame(u)
            val cornerPx = 28.dp.toPx()

            val visibleRows = MatrixIdleRectRows + (MatrixRows - MatrixIdleRectRows) * focus
            val lastVisibleRow = if (active != null) MatrixRows - 1 else MatrixIdleRectRows - 1
            val maxSeam = minOf(srcRect.width / MatrixCols.toFloat(), srcRect.height / visibleRows) * 0.35f
            val seamPx = 3.2.dp.toPx().coerceAtMost(maxSeam)
            val litTileCount = (elapsedFraction.coerceIn(0f, 1f) * MatrixProgressCellCount)
                .toInt()
                .coerceIn(0, MatrixProgressCellCount)
            val srcCx = srcRect.center.x
            val srcCy = srcRect.center.y

            val stationaryDots = ArrayList<Pair<MatrixDotFlyState, Color>>(HomeSquareMatrixCells.size)
            val launchedDots = ArrayList<Pair<MatrixDotFlyState, Color>>(HomeSquareMatrixCells.size)

            for (cell in HomeSquareMatrixCells) {
                val presence = matrixRowPresence(cell.row, focus).coerceIn(0f, 1f)
                if (presence <= 0.01f) continue

                val (restColor, restScale) = computeMatrixCellColorAndScale(
                    cell = cell,
                    active = active,
                    focus = focus,
                    presence = presence,
                    pulse = 0f,
                    wavePos = 0f,
                    litTileCount = litTileCount,
                    elapsedFraction = elapsedFraction,
                    palette = palette,
                )

                val slot = computeFlushMatrixCellRect(
                    row = cell.row,
                    col = cell.col,
                    visibleRows = visibleRows,
                    areaLeft = srcRect.left,
                    areaTop = srcRect.top,
                    areaWidth = srcRect.width,
                    areaHeight = srcRect.height,
                    seamPx = seamPx,
                    lastVisibleRow = lastVisibleRow,
                )
                val restW = slot.width * restScale
                val restH = slot.height * restScale

                val dot = computeMatrixDotFlyState(
                    cell = cell,
                    restCenterX = slot.center.x,
                    restCenterY = slot.center.y,
                    restWidth = restW,
                    restHeight = restH,
                    restCorner = 0f,
                    srcCenterX = srcCx,
                    srcCenterY = srcCy,
                    rootWidth = size.width,
                    rootHeight = size.height,
                    flyProgress = frame.flyProgress,
                )

                val isLit = active != null && cell.progressOrder in 0 until litTileCount
                val glowTarget = if (isLit) palette.flyGlowLitColor else palette.flyGlowDimColor
                val dotColor = lerp(restColor, glowTarget, frame.dotBoost)
                    .let { c -> c.copy(alpha = c.alpha * frame.dotAlpha) }

                if (dotColor.alpha > 0.005f) {
                    if (dot.scaleFactor <= 1.003f) {
                        stationaryDots.add(dot to dotColor)
                    } else {
                        launchedDots.add(dot to dotColor)
                    }
                }
            }

            fun drawDotList(dots: List<Pair<MatrixDotFlyState, Color>>) {
                for ((dot, dotColor) in dots) {
                    if (dot.corner <= 0.5f) {
                        drawRect(
                            color = dotColor,
                            topLeft = Offset(dot.centerX - dot.width * 0.5f, dot.centerY - dot.height * 0.5f),
                            size = Size(dot.width, dot.height),
                        )
                    } else {
                        drawRoundRect(
                            color = dotColor,
                            topLeft = Offset(dot.centerX - dot.width * 0.5f, dot.centerY - dot.height * 0.5f),
                            size = Size(dot.width, dot.height),
                            cornerRadius = CornerRadius(dot.corner, dot.corner),
                        )
                    }
                }
            }

            val rectAlpha = (1f - focus).coerceIn(0f, 1f) * frame.sourceCardAlpha
            if (rectAlpha > 0.02f || stationaryDots.isNotEmpty()) {
                restClipPath.rewind()
                restClipPath.addRoundRect(RoundRect(srcRect, CornerRadius(cornerPx, cornerPx)))
                clipPath(restClipPath) {
                    if (rectAlpha > 0.02f) {
                        drawRect(
                            color = palette.rectBaseColor.copy(alpha = 0.92f * rectAlpha),
                            topLeft = Offset(srcRect.left, srcRect.top),
                            size = Size(srcRect.width, srcRect.height),
                        )
                    }
                    drawDotList(stationaryDots)
                }
            }

            if (launchedDots.isNotEmpty()) {
                drawDotList(launchedDots)
            }
        }

        // Foreground course status content is always rendered strictly above the block layer (`zIndex(1f)`)
        Box(
            modifier = Modifier
                .zIndex(1f)
                .offset { IntOffset(srcRect.left.roundToInt(), srcRect.top.roundToInt()) }
                .width(srcWidthDp)
                .height(srcHeightDp)
                .graphicsLayer {
                    val frame = computeMatrixFlyOutFrame(progressProvider())
                    alpha = frame.sourceCardAlpha
                    val s = lerp(1f, 1.03f, 1f - frame.sourceCardAlpha)
                    scaleX = s
                    scaleY = s
                },
            contentAlignment = Alignment.Center,
        ) {
            TodayMatrixCardForeground(
                active = active,
                next = next,
                status = status,
                hasSchedule = data.hasSchedule,
                compactMatrixText = compactMatrixText,
                inClassContentColor = inClassContentColor,
                animateModeChange = false,
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction


/** Compact 4-tile tonal quick launch dock keeping top actions visible above the bottom bar. */
@Composable
internal fun HomeQuickLaunchDock(
    features: List<HomeFeature>,
    onOpenFeature: (HomeFeature, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        features.forEach { item ->
            val heroKey = if (item.key == "network") "home-network" else "home-feature-${item.key}"
            val shortLabel = when (item.key) {
                "network" -> "校园网"
                "grades" -> "查成绩"
                "exams" -> "考期"
                "gpa" -> "绩点"
                else -> item.title
            }
            Card(
                onClick = { onOpenFeature(item, heroKey) },
                modifier = Modifier
                    .weight(1f)
                    .height(72.dp)
                    .heroFlightSource(
                        key = heroKey,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        cornerDp = 24f,
                        enabled = item.url == null,
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppIconBadge(
                        key = item.key,
                        size = 32.dp,
                        iconSize = 16.dp,
                        useExpressivePolygon = true,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = shortLabel,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Redesigned Asymmetric Academic Workspace replacing uniform grid rows. */
@Composable
internal fun HomeAcademicWorkspace(
    spotlightLeft: HomeFeature,
    spotlightRight: HomeFeature,
    compactRow: List<HomeFeature>,
    onOpenFeature: (HomeFeature, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveSectionHeader(
            title = "学业工作区",
            subtitle = "修读计划、档案查询与实验工具",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AcademicSpotlightCard(
                feature = spotlightLeft,
                subtitle = "学分进度与修读计划",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                cornerDp = 28f,
                modifier = Modifier.weight(1.12f),
                onClick = { onOpenFeature(spotlightLeft, "home-feature-${spotlightLeft.key}") },
            )
            AcademicSpotlightCard(
                feature = spotlightRight,
                subtitle = "完整学业档案",
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                cornerDp = 28f,
                modifier = Modifier.weight(0.98f),
                onClick = { onOpenFeature(spotlightRight, "home-feature-${spotlightRight.key}") },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            compactRow.forEach { item ->
                val heroKey = "home-feature-${item.key}"
                Card(
                    onClick = { onOpenFeature(item, heroKey) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .heroFlightSource(
                            key = heroKey,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            cornerDp = 24f,
                            enabled = item.url == null,
                        ),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = if (item.url != null) {
                                Icons.AutoMirrored.Rounded.OpenInNew
                            } else {
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight
                            },
                            contentDescription = null,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AppIconBadge(
                                key = item.key,
                                size = 36.dp,
                                iconSize = 18.dp,
                                useExpressivePolygon = true,
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AcademicSpotlightCard(
    feature: HomeFeature,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    shape: Shape,
    cornerDp: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val heroKey = "home-feature-${feature.key}"
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .heroFlightSource(
                key = heroKey,
                containerColor = containerColor,
                cornerDp = cornerDp,
                enabled = feature.url == null,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconBadge(
                    key = feature.key,
                    size = 40.dp,
                    iconSize = 20.dp,
                    useExpressivePolygon = true,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor.copy(alpha = 0.78f),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = feature.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.76f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Compact sculpted portal card keeping all campus web shortcuts readable with zero title truncation. */
@Composable
internal fun HomeCampusPortalHub(
    primaryPortals: List<Pair<Int, HomeFeature>>,
    secondaryPortals: List<Pair<Int, HomeFeature>>,
    onOpenLink: (HomeFeature, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExpressiveSectionHeader(
            title = "校园服务",
            subtitle = "常用校内门户直达",
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    primaryPortals.forEach { (idx, link) ->
                        val hint = if (link.title == "新教务系统") "正方教务入口" else "统一身份认证"
                        Surface(
                            onClick = { onOpenLink(link, "home-campus-$idx") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIconBadge(
                                        key = link.key,
                                        size = 34.dp,
                                        iconSize = 17.dp,
                                        useExpressivePolygon = true,
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = link.title,
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    secondaryPortals.forEach { (idx, link) ->
                        Surface(
                            onClick = { onOpenLink(link, "home-campus-$idx") },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                AppIconBadge(
                                    key = link.key,
                                    size = 24.dp,
                                    iconSize = 13.dp,
                                )
                                Text(
                                    text = link.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

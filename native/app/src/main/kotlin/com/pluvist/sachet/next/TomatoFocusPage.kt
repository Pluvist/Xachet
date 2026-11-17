@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package com.pluvist.sachet.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Single-page Material 3 Expressive Tomato focus workspace:
 * - Upper fold: Pomodoro / Long-Timer dial (`CircularWavyProgressIndicator`), Idle Classrooms shortcut,
 *   morphing Expressive control buttons, and inline settings sheet (strictly one page).
 * - Scroll / slide straight down: Tomato-inspired Focus Statistics (daily goal progress, weekly/monthly
 *   bar charts, Golden Hours productivity insight, tag breakdown, and recent focus logs).
 */
@Composable
internal fun TomatoFocusPage(
    state: FocusUiState,
    status: CourseStatus,
    contentPadding: PaddingValues,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onFinishOrSkip: () -> Unit,
    onModeSelect: (FocusTimerMode) -> Unit,
    onUpdateSettings: (FocusSettings) -> Unit,
    onOpenClassrooms: () -> Unit,
    onOpenCourseStatus: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    onDismissFeedback: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val bottomClearance = contentPadding.calculateBottomPadding() + 20.dp

    var statsRange by remember { mutableStateOf(FocusStatsRange.Week) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val statsSummary = remember(state.records, statsRange, state.settings) {
        FocusStatsCalculator.compute(state.records, statsRange, state.settings)
    }

    val isBreakPhase = state.mode == FocusTimerMode.Pomodoro && state.phase != FocusPhase.Focus
    val accentColor by animateColorAsState(
        targetValue = if (isBreakPhase) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        label = "TomatoAccentColor",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Pinned top header so scrolling stats down never overlaps the system status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "专注",
                style = MaterialTheme.typography.headlineMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val classroomBg = MaterialTheme.colorScheme.secondaryContainer
                FilledTonalButton(
                    onClick = onOpenClassrooms,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = classroomBg,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.heroFlightSource(
                        key = "tomato-classrooms",
                        containerColor = classroomBg,
                        cornerDp = 24f,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MeetingRoom,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "空闲教室",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                FilledTonalIconButton(
                    onClick = { showSettingsSheet = true },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "专注参数设置")
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val viewportHeight = maxHeight
            val viewportWidth = maxWidth
            val upperFoldHeight = (viewportHeight - bottomClearance).coerceAtLeast(520.dp)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = bottomClearance),
            ) {
                // Upper Fold: Mode Switcher + Enlarged Centered Dial + Controls + Clickable Course Status Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = upperFoldHeight),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Mode Switcher: Pomodoro Timer vs Long Count-Up Timer
                    val modes = FocusTimerMode.entries
                    Box(modifier = Modifier.padding(top = 6.dp)) {
                        ExpressiveConnectedToggleRow(
                            options = modes.map { it.label },
                            selectedIndex = modes.indexOf(state.mode),
                            onSelect = { idx -> onModeSelect(modes[idx]) },
                        )
                    }

                    // Enlarged M3 Expressive Circular Wavy Timer Dial with dead-center tall bold Roboto Flex digits
                    val dialSize = minOf(viewportWidth - 48.dp, upperFoldHeight - 268.dp)
                        .coerceIn(244.dp, 284.dp)
                    val strokePx = with(density) { 10.dp.toPx() }
                    val dialStroke = remember(strokePx) { Stroke(width = strokePx, cap = StrokeCap.Round) }
                    Box(
                        modifier = Modifier
                            .padding(vertical = 32.dp)
                            .size(dialSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator(
                            progress = { state.dialProgress },
                            modifier = Modifier.fillMaxSize(),
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            stroke = dialStroke,
                            trackStroke = dialStroke,
                            amplitude = { if (state.isRunning) 1f else 0f },
                        )

                        RollingNumberText(
                            text = FocusFormatters.clock(state.displaySeconds),
                            style = XachetTypeTokens.TomatoTimerDigits.copy(
                                fontSize = when {
                                    state.displaySeconds >= 3600L -> 64.sp
                                    dialSize < 270.dp -> 76.sp
                                    else -> 88.sp
                                },
                                lineHeight = when {
                                    state.displaySeconds >= 3600L -> 68.sp
                                    dialSize < 270.dp -> 80.sp
                                    else -> 92.sp
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            maxLines = 1,
                            animateSize = false,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        if (state.mode == FocusTimerMode.Pomodoro) {
                            Text(
                                text = "第 ${state.currentRound}/${state.settings.roundsPerCycle} 轮 · ${state.phase.label}",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = (dialSize * 0.14f).coerceIn(28.dp, 44.dp)),
                            )
                        } else {
                            Text(
                                text = if (state.isRunning) "正向长计时中" else "长计时 · 正向累计",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = (dialSize * 0.14f).coerceIn(28.dp, 44.dp)),
                            )
                        }
                    }

                    // Controls + Course Status Box below Start button
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val playCorner by animateDpAsState(
                            targetValue = if (state.isRunning) 20.dp else 36.dp,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
                            label = "PlayButtonMorphCorner",
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            FilledTonalIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onReset()
                                },
                                enabled = state.hasActiveProgress,
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                            ) {
                                Icon(Icons.Rounded.Replay, contentDescription = "重置计时")
                            }

                            Button(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (state.isRunning) onPause() else onStart()
                                },
                                shape = RoundedCornerShape(playCorner),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = if (isBreakPhase) {
                                        MaterialTheme.colorScheme.onTertiary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                ),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                                modifier = Modifier.height(68.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (state.isRunning) "暂停" else if (state.elapsedSeconds > 0L) "继续" else "开始",
                                    style = MaterialTheme.typography.titleMediumEmphasized,
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onFinishOrSkip()
                                },
                                enabled = if (state.mode == FocusTimerMode.LongTimer) {
                                    state.hasActiveProgress
                                } else {
                                    true
                                },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                            ) {
                                Icon(
                                    imageVector = if (state.mode == FocusTimerMode.LongTimer) {
                                        Icons.Rounded.Check
                                    } else {
                                        Icons.Rounded.SkipNext
                                    },
                                    contentDescription = if (state.mode == FocusTimerMode.LongTimer) "完成并记录" else "下一阶段",
                                )
                            }
                        }

                        // Clickable Next-Course Detail Card below the Start button -> quick popup of Course Status Bar
                        val nextCourse = status.next
                        val statusCardBg = if (nextCourse != null) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                        val statusCardFg = if (nextCourse != null) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Surface(
                            onClick = onOpenCourseStatus,
                            shape = MaterialTheme.shapes.extraLarge,
                            color = statusCardBg,
                            contentColor = statusCardFg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heroFlightSource(
                                    key = "tomato-course-status",
                                    containerColor = statusCardBg,
                                    cornerDp = 28f,
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = when {
                                            nextCourse != null -> "下一节课 · ${nextCourse.course.title}"
                                            status.completed || status.current != null -> "今日已无下一节课"
                                            else -> "今日无下一节课"
                                        },
                                        style = MaterialTheme.typography.titleSmallEmphasized,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val sub = when {
                                        nextCourse != null -> "${nextCourse.startTime()}–${nextCourse.endTime()} · ${nextCourse.course.place.ifBlank { "地点待定" }} · 第${nextCourse.course.first}–${nextCourse.course.last}节"
                                        status.current != null -> "当前为今日最后一节课 · 点击查看今日日程"
                                        status.completed -> "今日课程安排已全部结束"
                                        else -> "点击查看今日课程日程"
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelMedium.tabular(),
                                        color = statusCardFg.copy(alpha = 0.82f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (nextCourse != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = statusCardFg.copy(alpha = 0.12f),
                                        contentColor = statusCardFg,
                                    ) {
                                        Text(
                                            text = nextCourse.startTime().toString(),
                                            style = XachetTypeTokens.PillStatus,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "展开课程栏",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(bottomClearance + 16.dp))

                // Lower Fold on the Exact Same Single Page: Tomato-inspired Focus Statistics
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                ExpressiveSectionHeader(
                    title = "专注时间统计",
                    subtitle = "每日目标、周月趋势与黄金专注时段分析",
                )

                val ranges = FocusStatsRange.entries
                ExpressiveConnectedToggleRow(
                    options = ranges.map { it.label },
                    selectedIndex = ranges.indexOf(statsRange),
                    onSelect = { idx -> statsRange = ranges[idx] },
                )

                // KPI Bento Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(
                        modifier = Modifier.weight(1.1f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "${statsRange.label}累计专注",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                            Text(
                                text = FocusFormatters.durationLabel(statsSummary.totalMinutes),
                                style = MaterialTheme.typography.headlineSmallEmphasized.tabular(),
                            )
                            LinearWavyProgressIndicator(
                                progress = { statsSummary.dailyGoalFraction },
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "今日目标 ${statsSummary.todayMinutes}/${state.settings.dailyGoalMinutes} 分钟",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f),
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(0.9f),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "连续 ${statsSummary.streakDays} 天",
                                    style = MaterialTheme.typography.labelLargeEmphasized,
                                )
                            }
                            Text(
                                text = "${statsSummary.sessionCount} 次专注",
                                style = MaterialTheme.typography.headlineSmallEmphasized.tabular(),
                            )
                            Text(
                                text = "番茄 ${statsSummary.pomodoroCount} · 长计时 ${statsSummary.longTimerCount}",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.86f),
                            )
                            Text(
                                text = "黄金时段 ${statsSummary.goldenHourLabel}",
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.86f),
                            )
                        }
                    }
                }

                // Focus Trend / Golden Hours Chart Card
                FocusHistoryChartCard(
                    range = statsRange,
                    summary = statsSummary,
                    dailyGoalMinutes = state.settings.dailyGoalMinutes,
                )

                // Recent Focus Sessions
                ExpressiveSectionHeader("近期专注记录", subtitle = "完成或结束满 15 秒自动写入统计")
                if (statsSummary.recentRecords.isEmpty()) {
                    ExpressiveEmptyCard(
                        icon = Icons.Rounded.Schedule,
                        title = "暂无专注统计记录",
                        subtitle = "开始一次番茄专注或长计时后，这里将展示你的每日与周月趋势图",
                    )
                } else {
                    val timeFmt = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
                    val zone = remember { ZoneId.systemDefault() }
                    ExpressiveSegmentedGroup {
                        statsSummary.recentRecords.forEachIndexed { idx, rec ->
                            val mins = (rec.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
                            val whenText = remember(rec.endedAtMillis) {
                                Instant.ofEpochMilli(rec.endedAtMillis).atZone(zone).format(timeFmt)
                            }
                            SegmentedListItem(
                                shapes = ListItemDefaults.segmentedShapes(idx, statsSummary.recentRecords.size),
                                colors = expressiveSegmentedColors(),
                                supportingContent = {
                                    Text(
                                        text = whenText,
                                        style = MaterialTheme.typography.bodySmall.tabular(),
                                    )
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = "${mins} 分钟",
                                            style = MaterialTheme.typography.labelLarge.tabular(),
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        IconButton(
                                            onClick = { onDeleteRecord(rec.id) },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "删除记录",
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                },
                                content = {
                                    Text(rec.mode.label, style = MaterialTheme.typography.titleSmallEmphasized)
                                },
                            )
                        }
                    }
                }
            }
        }
            LaunchedEffect(state.lastFeedback) {
                if (state.lastFeedback != null) {
                    delay(3200L)
                    onDismissFeedback()
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.lastFeedback != null,
                ) {
                    Surface(
                        onClick = onDismissFeedback,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            text = state.lastFeedback.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
    }
    }

    if (showSettingsSheet) {
        FocusSettingsSheet(
            current = state.settings,
            onDismiss = { showSettingsSheet = false },
            onSave = { updated ->
                onUpdateSettings(updated)
                showSettingsSheet = false
            },
        )
    }
}

@Composable
private fun FocusHistoryChartCard(
    range: FocusStatsRange,
    summary: FocusStatsSummary,
    dailyGoalMinutes: Int,
) {
    val barData: List<Pair<String, Int>> = when (range) {
        FocusStatsRange.Today -> (0 until 12).map { block ->
            val startH = block * 2
            val sumMins = summary.hourlyMinutes[startH] + summary.hourlyMinutes[startH + 1]
            String.format(java.util.Locale.ROOT, "%02d", startH) to sumMins
        }
        FocusStatsRange.Week -> summary.weekBuckets.map { it.label to it.minutes }
        FocusStatsRange.Month -> summary.monthBuckets.map { it.label to it.minutes }
    }
    var selectedIdx by remember(range, barData) { mutableIntStateOf(-1) }
    val peakMinutes = (barData.maxOfOrNull { it.second } ?: 0)
        .coerceAtLeast(if (range == FocusStatsRange.Today) 30 else 60)

    val barActiveColor = MaterialTheme.colorScheme.primary
    val barSoftColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
    val guideColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = when (range) {
                            FocusStatsRange.Today -> "今日时段分布（黄金时段）"
                            FocusStatsRange.Week -> "本周每日专注走势"
                            FocusStatsRange.Month -> "近 30 天专注走势"
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = if (selectedIdx in barData.indices) {
                            "${barData[selectedIdx].first}${if (range == FocusStatsRange.Today) ":00" else ""} · ${barData[selectedIdx].second} 分钟"
                        } else {
                            "平均每次 ${summary.averageSessionMinutes} 分钟 · 峰值 ${barData.maxOfOrNull { it.second } ?: 0} 分钟"
                        },
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Rounded M3 Expressive Bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                horizontalArrangement = Arrangement.spacedBy(if (range == FocusStatsRange.Month) 2.dp else 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                barData.forEachIndexed { idx, (label, mins) ->
                    val ratio = (mins.toFloat() / peakMinutes.toFloat()).coerceIn(0f, 1f)
                    val selected = selectedIdx == idx
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { selectedIdx = if (selected) -1 else idx },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (range != FocusStatsRange.Today && idx == 0 && dailyGoalMinutes in 1..peakMinutes) {
                                    val gy = size.height * (1f - dailyGoalMinutes.toFloat() / peakMinutes.toFloat())
                                    drawLine(
                                        color = guideColor,
                                        start = Offset(0f, gy),
                                        end = Offset(size.width * barData.size * 1.4f, gy),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                                    )
                                }
                                val barH = if (mins > 0) maxOf(8.dp.toPx(), size.height * ratio) else 4.dp.toPx()
                                val barW = (size.width * 0.72f).coerceAtLeast(3.dp.toPx())
                                val radius = (barW * 0.45f).coerceAtMost(barH * 0.5f)
                                drawRoundRect(
                                    color = when {
                                        selected -> barActiveColor
                                        mins > 0 -> barActiveColor.copy(alpha = 0.80f)
                                        else -> barSoftColor.copy(alpha = 0.35f)
                                    },
                                    topLeft = Offset((size.width - barW) * 0.5f, size.height - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(radius, radius),
                                )
                            }
                        }
                        if (range != FocusStatsRange.Month || idx % 5 == 0 || idx == barData.lastIndex) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.tabular(),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Text(" ", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusSettingsSheet(
    current: FocusSettings,
    onDismiss: () -> Unit,
    onSave: (FocusSettings) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var focusMin by remember(current) { mutableIntStateOf(current.focusMinutes) }
    var shortMin by remember(current) { mutableIntStateOf(current.shortBreakMinutes) }
    var longMin by remember(current) { mutableIntStateOf(current.longBreakMinutes) }
    var rounds by remember(current) { mutableIntStateOf(current.roundsPerCycle) }
    var goalMin by remember(current) { mutableIntStateOf(current.dailyGoalMinutes) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("番茄钟参数设置", style = MaterialTheme.typography.titleLargeEmphasized)

            FocusSliderRow("番茄专注时长", "$focusMin 分钟", focusMin.toFloat(), 5f..90f) {
                focusMin = it.roundToInt()
            }
            FocusSliderRow("短休息时长", "$shortMin 分钟", shortMin.toFloat(), 1f..30f) {
                shortMin = it.roundToInt()
            }
            FocusSliderRow("长休息时长", "$longMin 分钟", longMin.toFloat(), 5f..60f) {
                longMin = it.roundToInt()
            }
            FocusSliderRow("长休息轮次", "每 $rounds 轮", rounds.toFloat(), 2f..8f) {
                rounds = it.roundToInt()
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            FocusSliderRow("每日专注目标", "$goalMin 分钟", goalMin.toFloat(), 30f..360f) {
                goalMin = ((it / 10f).roundToInt() * 10).coerceIn(30, 360)
            }

            Button(
                onClick = {
                    onSave(
                        FocusSettings(
                            focusMinutes = focusMin,
                            shortBreakMinutes = shortMin,
                            longBreakMinutes = longMin,
                            roundsPerCycle = rounds,
                            dailyGoalMinutes = goalMin,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存设置")
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FocusSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(valueText, style = MaterialTheme.typography.labelLarge.tabular(), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

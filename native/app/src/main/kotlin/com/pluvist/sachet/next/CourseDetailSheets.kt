@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Leaf-isolated 1s countdown text so parent cards and pages do not recompose on clock ticks. */
@Composable
internal fun LiveCourseCountdownText(
    end: Instant,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember(end) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            val remainder = 1000L - (nowMillis % 1000L)
            delay(remainder.coerceIn(100L, 1000L))
        }
    }
    val remaining = remember(nowMillis, end) {
        Duration.between(Instant.ofEpochMilli(nowMillis), end).toMillis().coerceAtLeast(0L)
    }
    RollingNumberText(
        text = CourseTimeline.countdown(remaining),
        style = style.tabular(),
        maxLines = 1,
        animateSize = false,
        modifier = modifier,
    )
}

/** Leaf-isolated wavy progress bar tracking elapsed portion of an active class. */
@Composable
internal fun CourseProgressWavyBar(
    start: Instant,
    end: Instant,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember(start, end) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start, end) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val totalMs = remember(start, end) { Duration.between(start, end).toMillis().coerceAtLeast(1L) }
    val elapsedMs = (nowMillis - start.toEpochMilli()).coerceIn(0L, totalMs)
    val fraction = elapsedMs.toFloat() / totalMs.toFloat()
    LinearWavyProgressIndicator(
        progress = { fraction },
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

/**
 * Full-screen Material 3 Expressive host for [DetailRoute.CourseStatus], [DetailRoute.CourseLesson],
 * and [DetailRoute.CourseDay], supporting nested lesson drill-down with animated transitions and predictive back.
 */
@Composable
internal fun CourseDetailPageHost(
    rootMode: DetailRoute,
    status: CourseStatus,
    allCourses: List<Course>,
    semester: LocalDate,
    onRoutineChange: (Course, String) -> Unit,
    onUpdateCustomCourse: ((Course) -> Unit)? = null,
    onDeleteCustomCourse: ((String) -> Unit)? = null,
    onBack: () -> Unit,
) {
    var nestedLesson by remember(rootMode) { mutableStateOf<Course?>(null) }
    val backState = rememberPredictiveBackState(enabled = nestedLesson != null) {
        nestedLesson = null
    }
    val motionScheme = MaterialTheme.motionScheme

    val activeLesson = nestedLesson?.let { target ->
        allCourses.firstOrNull {
            it.id == target.id && it.weekday == target.weekday && it.first == target.first
        } ?: target
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (rootMode) {
            is DetailRoute.CourseStatus -> CourseStatusPage(
                status = status,
                onOpenLesson = { nestedLesson = it },
                onBack = onBack,
            )

            is DetailRoute.CourseDay -> {
                val lessons = remember(allCourses, semester, rootMode.date) {
                    CourseClock.onDate(allCourses, semester, rootMode.date, ZoneId.systemDefault())
                }
                CourseDayPage(
                    date = rootMode.date,
                    lessons = lessons,
                    onOpenLesson = { nestedLesson = it },
                    onBack = onBack,
                )
            }

            is DetailRoute.CourseLesson -> {
                val liveLesson = remember(allCourses, rootMode.course) {
                    allCourses.firstOrNull {
                        it.id == rootMode.course.id &&
                            it.weekday == rootMode.course.weekday &&
                            it.first == rootMode.course.first
                    } ?: rootMode.course
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CourseLessonCenteredCard(
                        course = liveLesson,
                        allCourses = allCourses,
                        onRoutineChange = { routine -> onRoutineChange(liveLesson, routine) },
                        onUpdateCustomCourse = onUpdateCustomCourse,
                        onDeleteCustomCourse = if (onDeleteCustomCourse != null) {
                            {
                                onDeleteCustomCourse(liveLesson.id)
                                onBack()
                            }
                        } else {
                            null
                        },
                        onBack = onBack,
                    )
                }
            }

            else -> Unit
        }

        AnimatedVisibility(
            visible = activeLesson != null,
            enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()),
            label = "NestedLessonScrim",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.52f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { nestedLesson = null },
                    ),
            )
        }

        AnimatedVisibility(
            visible = activeLesson != null,
            enter = fadeIn(animationSpec = motionScheme.fastEffectsSpec()) +
                scaleIn(initialScale = 0.72f, animationSpec = motionScheme.fastSpatialSpec()),
            exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                scaleOut(targetScale = 0.72f, animationSpec = motionScheme.fastSpatialSpec()),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            label = "NestedLessonCenteredCard",
        ) {
            val lesson = activeLesson ?: return@AnimatedVisibility
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CourseLessonCenteredCard(
                    course = lesson,
                    allCourses = allCourses,
                    onRoutineChange = { routine -> onRoutineChange(lesson, routine) },
                    onUpdateCustomCourse = onUpdateCustomCourse,
                    onDeleteCustomCourse = if (onDeleteCustomCourse != null) {
                        {
                            onDeleteCustomCourse(lesson.id)
                            nestedLesson = null
                        }
                    } else {
                        null
                    },
                    onBack = { nestedLesson = null },
                    modifier = Modifier.predictiveBackContainer(backState),
                )
            }
        }
    }
}

@Composable
private fun CourseStatusPage(
    status: CourseStatus,
    onOpenLesson: (Course) -> Unit,
    onBack: () -> Unit,
) {
    val active = status.current
    val next = status.next
    val pageTitle = active?.course?.title
        ?: next?.course?.title
        ?: if (status.completed) "今日课程已完成" else "今日无课喵~"

    ExpressiveSubPageScaffold(
        title = pageTitle,
        subtitle = "今日共 ${status.today.size} 节课程安排",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (active != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "正在上课 · ${active.startTime()}–${active.endTime()}",
                                style = XachetTypeTokens.PillStatus,
                            )
                            Text("下课倒计时", style = MaterialTheme.typography.labelMedium)
                        }
                        LiveCourseCountdownText(
                            end = active.end,
                            style = XachetTypeTokens.HeroCountdown,
                        )
                        CourseProgressWavyBar(
                            start = active.start,
                            end = active.end,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (active.course.place.isNotBlank()) {
                            Text(
                                text = "地点：${active.course.place}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else if (next != null) {
                Card(
                    onClick = { onOpenLesson(next.course) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("下一节课程", style = MaterialTheme.typography.labelLargeEmphasized)
                        Text(
                            text = next.course.title,
                            style = MaterialTheme.typography.headlineSmallEmphasized,
                        )
                        Text(
                            text = "${next.startTime()}–${next.endTime()} · ${next.course.place.ifBlank { "地点待定" }}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                        )
                    }
                }
            } else {
                ExpressiveEmptyCard(
                    icon = if (status.completed) Icons.Rounded.CheckCircle else Icons.Rounded.Weekend,
                    title = if (status.completed) "今天的课程已经全部完成" else "今天没有课程，休息一下喵~",
                    subtitle = if (status.completed) "可以查看明日或本周其余课程安排" else "去空闲教室逛逛或放松一下吧",
                )
            }

            if (status.today.isNotEmpty()) {
                ExpressiveSectionHeader("今日日程", subtitle = "点击任意课程可查看详情与调整作息")
                ExpressiveSegmentedGroup {
                    status.today.forEachIndexed { index, dated ->
                        SegmentedListItem(
                            onClick = { onOpenLesson(dated.course) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = status.today.size),
                            colors = ListItemDefaults.segmentedColors(
                                containerColor = if (dated == active) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ),
                            supportingContent = {
                                Text(
                                    "${dated.startTime()}–${dated.endTime()} · ${dated.course.place.ifBlank { "地点待定" }}",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = if (dated.course.staggered) "错峰" else "常规",
                                    style = XachetTypeTokens.PillStatus,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            content = {
                                Text(dated.course.title, style = MaterialTheme.typography.titleSmallEmphasized)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating M3 Expressive centered card displaying course schedule details and routine override controls.
 */
@Composable
internal fun CourseLessonCenteredCard(
    course: Course,
    allCourses: List<Course> = emptyList(),
    onRoutineChange: (String) -> Unit,
    onUpdateCustomCourse: ((Course) -> Unit)? = null,
    onDeleteCustomCourse: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isClosing: Boolean = false,
) {
    val weekdays = remember { listOf("一", "二", "三", "四", "五", "六", "日") }
    val interval = remember(course) { course.interval() }
    var selectedRoutine by remember(course) { mutableStateOf(course.routine) }
    var showEditCustomSheet by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .pointerInput(Unit) {
                detectTapGestures { /* Absorb card taps so backdrop dismiss only triggers outside the card. */ }
            },
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "星期${weekdays.getOrElse(course.weekday - 1) { "?" }} · 第${course.first}–${course.last}节",
                        style = XachetTypeTokens.PillStatus,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (course.staggered) "错峰作息" else "普通作息",
                                style = XachetTypeTokens.PillStatus,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (course.staggered) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            labelColor = if (course.staggered) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        ),
                    )
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "${interval.first}–${interval.second}",
                                style = XachetTypeTokens.PillStatus,
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }

                val weeksText = remember(course.weeks) {
                    course.weeks.sorted().joinToString("、").ifBlank { "未指定" }
                }
                ExpressiveSegmentedGroup {
                    ExpressiveSegmentedInfoRow(
                        index = 0,
                        count = 3,
                        title = "地点",
                        value = course.place.ifBlank { "未填写" },
                        leading = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                    )
                    ExpressiveSegmentedInfoRow(
                        index = 1,
                        count = 3,
                        title = "教师",
                        value = course.teacher.ifBlank { "未填写" },
                        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
                    )
                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(index = 2, count = 3),
                        modifier = Modifier.fillMaxWidth(),
                        colors = expressiveSegmentedColors(),
                        leadingContent = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        supportingContent = {
                            Text(
                                text = weeksText,
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        content = {
                            Text("授课周次", style = MaterialTheme.typography.titleSmall)
                        },
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExpressiveSectionHeader(
                    title = "作息安排",
                    subtitle = when (selectedRoutine) {
                        "normal" -> "当前手动固定为普通作息"
                        "staggered" -> "当前手动固定为错峰作息"
                        else -> "按教学楼自动识别（逸夫楼、信息科技大楼启用错峰）"
                    },
                )
                val options = remember { listOf("auto" to "自动识别", "normal" to "普通", "staggered" to "错峰") }
                val selectedIndex = options.indexOfFirst { it.first == selectedRoutine }.coerceAtLeast(0)
                ExpressiveConnectedToggleRow(
                    options = options.map { it.second },
                    selectedIndex = selectedIndex,
                    onSelect = { idx ->
                        val next = options[idx].first
                        if (next != selectedRoutine) {
                            selectedRoutine = next
                            onRoutineChange(next)
                        }
                    },
                )

                if (course.isCustom && (onUpdateCustomCourse != null || onDeleteCustomCourse != null)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (onUpdateCustomCourse != null) {
                            FilledTonalButton(
                                onClick = { showEditCustomSheet = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                                Text("编辑课程")
                            }
                        }
                        if (onDeleteCustomCourse != null) {
                            FilledTonalButton(
                                onClick = onDeleteCustomCourse,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                                Text("删除课程", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditCustomSheet && onUpdateCustomCourse != null) {
        CustomCourseEditorSheet(
            courses = allCourses,
            initialWeek = course.weeks.minOrNull() ?: 1,
            initialCourse = course,
            onDismiss = { showEditCustomSheet = false },
            onDelete = if (onDeleteCustomCourse != null) {
                {
                    showEditCustomSheet = false
                    onDeleteCustomCourse()
                }
            } else {
                null
            },
            onConfirm = { updated ->
                onUpdateCustomCourse(updated)
                showEditCustomSheet = false
            },
        )
    }
}

@Composable
private fun CourseDayPage(
    date: LocalDate,
    lessons: List<DatedCourse>,
    onOpenLesson: (Course) -> Unit,
    onBack: () -> Unit,
) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
    ExpressiveSubPageScaffold(
        title = "${date.monthValue}月${date.dayOfMonth}日课程",
        subtitle = "星期${weekdays.getOrElse(date.dayOfWeek.value - 1) { "?" }} · 共 ${lessons.size} 节",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (lessons.isEmpty()) {
                ExpressiveEmptyCard(
                    icon = Icons.Rounded.Weekend,
                    title = "当日无课喵~",
                    subtitle = "这一天没有安排任何课程",
                )
            } else {
                ExpressiveSegmentedGroup {
                    lessons.forEachIndexed { index, dated ->
                        SegmentedListItem(
                            onClick = { onOpenLesson(dated.course) },
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = lessons.size),
                            colors = expressiveSegmentedColors(),
                            supportingContent = {
                                Text(
                                    text = "${dated.startTime()}–${dated.endTime()} · ${dated.course.place.ifBlank { "地点待定" }}",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = "第${dated.course.first}–${dated.course.last}节",
                                    style = MaterialTheme.typography.labelMedium.tabular(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            },
                            content = {
                                Text(dated.course.title, style = MaterialTheme.typography.titleSmallEmphasized)
                            },
                        )
                    }
                }
            }
        }
    }
}

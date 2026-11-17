@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)

package com.pluvist.sachet.next

import android.content.res.Configuration
import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

private const val MAX_WEEKS = 60
private val WeekAxisHeightMinutes =
    maxOf(axisMinute(LessonTimes.end(11, false)), axisMinute(LessonTimes.end(11, true))) + 24f
private val WeekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val ScheduleSectionIndices = IntArray(11) { it + 1 }
private val SchedulePairIndices = intArrayOf(2, 4, 6, 8, 10)
private val MonthDayLabels = Array(32) { it.toString() }
private val MonthCountLabels = Array(24) { "${it}节" }

// Sachet's standard Material course card palette, preserving deterministic title-to-color mapping.
private val StandardCoursePalette = listOf(
    Color(0xFFFFCA28), // amber400
    Color(0xFF42A5F5), // blue400
    Color(0xFF2979FF), // blueAccent400
    Color(0xFFFF7043), // deepOrange400
    Color(0xFF7E57C2), // deepPurple400
    Color(0xFF651FFF), // deepPurpleAccent400
    Color(0xFF9CCC65), // lightGreen400
    Color(0xFF3D5AFE), // indigoAccent400
    Color(0xFFFF9100), // orangeAccent400
    Color(0xFF26C6DA), // cyan400
    Color(0xFF66BB6A), // green400
    Color(0xFF5C6BC0), // indigo400
    Color(0xFFFFA726), // orange400
    Color(0xFF26A69A), // teal400
)

internal const val WEEK_TITLE_BOUNCE_TOTAL_DURATION_MS = 320
internal const val WeekTitleBouncePeak = 1.26f
internal val WeekTitleBounceInEasing = CubicBezierEasing(0.18f, 0.90f, 0.28f, 1.0f)
internal val WeekTitleBounceSpring = spring<Float>(dampingRatio = 0.42f, stiffness = 400f)

internal fun splitWeekTitleBounceUnits(title: String): List<String> =
    title.map { it.toString() }.filter { it.isNotBlank() }

internal fun computeCharBounceScale(progress: Float, unitIndex: Int, unitCount: Int): Float {
    if (progress <= 0f || progress >= 1f) return 1f
    val count = unitCount.coerceAtLeast(1)
    val localSpan = if (count <= 1) 1f else 1f / (1f + 0.5f * (count - 1).toFloat())
    val start = if (count <= 1) 0f else unitIndex.toFloat() * 0.5f * localSpan
    val localT = ((progress - start) / localSpan).coerceIn(0f, 1f)
    if (localT <= 0f || localT >= 1f) return 1f
    return if (localT < 0.44f) {
        val u = WeekTitleBounceInEasing.transform((localT / 0.44f).coerceIn(0f, 1f))
        1f + (WeekTitleBouncePeak - 1f) * u
    } else {
        val d = ((localT - 0.44f) / 0.56f).coerceIn(0f, 1f)
        val env = (1f - d) * (1f - d)
        val wave = cos(d * 1.6f * PI.toFloat())
        1f + (WeekTitleBouncePeak - 1f) * env * wave
    }
}

internal fun computeScheduleTargetScrollPx(
    status: CourseStatus,
    page: WeekCoursePage,
    todayDayIndex: Int,
    scale: Float,
    densityPxPerDp: Float,
    viewportHeightPx: Int,
    bottomClearancePx: Int = 0,
    nowTime: LocalTime = LocalTime.now(),
): Int {
    val todayBlocks = if (todayDayIndex in page.days.indices) page.days[todayDayIndex] else emptyList()
    val matchingCurrent = status.current?.let { active ->
        todayBlocks.firstOrNull {
            it.course.first == active.course.first && it.course.title == active.course.title
        } ?: todayBlocks.firstOrNull { it.course.interval() == active.course.interval() }
    }
    val matchingNext = status.next?.let { upcoming ->
        todayBlocks.firstOrNull {
            it.course.first == upcoming.course.first && it.course.title == upcoming.course.title
        } ?: todayBlocks.firstOrNull { it.course.interval() == upcoming.course.interval() }
    }
    val targetBlock = when {
        matchingCurrent != null -> matchingCurrent
        matchingNext != null -> matchingNext
        todayBlocks.isNotEmpty() -> {
            val nextOrOngoingToday = todayBlocks
                .sortedBy { it.topMinute }
                .firstOrNull { it.course.interval().second.isAfter(nowTime) }
            nextOrOngoingToday ?: todayBlocks.maxBy { it.topMinute }
        }
        else -> null
    }
    val (targetTopMinute, targetHeightMinutes) = when {
        targetBlock != null -> targetBlock.topMinute to targetBlock.heightMinutes
        status.current != null -> status.current.course.interval().let { (s, e) ->
            val top = axisMinute(s)
            top to (axisMinute(e) - top).coerceAtLeast(30f)
        }
        status.next != null -> status.next.course.interval().let { (s, e) ->
            val top = axisMinute(s)
            top to (axisMinute(e) - top).coerceAtLeast(30f)
        }
        else -> axisMinute(nowTime).coerceAtLeast(0f) to 45f
    }
    val topPx = targetTopMinute * scale * densityPxPerDp + 6f * densityPxPerDp
    val bottomPx = topPx + targetHeightMinutes * scale * densityPxPerDp
    val visibleHeightPx = (viewportHeightPx - bottomClearancePx).coerceAtLeast(1)
    if (viewportHeightPx > 0 && bottomPx <= visibleHeightPx - 12f * densityPxPerDp) {
        return 0
    }
    val headRoomPx = if (viewportHeightPx > 0) {
        (viewportHeightPx * 0.22f).coerceIn(28f * densityPxPerDp, 120f * densityPxPerDp)
    } else {
        56f * densityPxPerDp
    }
    return (topPx - headRoomPx).roundToInt().coerceAtLeast(0)
}

@Immutable
internal data class ScheduleScaleTypography(
    val scalePercent: Int,
    val titleFontSizeSp: Float,
    val placeFontSizeSp: Float,
    val lineHeightFactor: Float,
    val titleMaxLines: Int,
    val placeMaxLines: Int,
    val axisNumberSp: Float,
    val axisTimeSp: Float,
    val axisWidthDp: Float,
    val headerWeekdaySp: Float,
    val headerDateSp: Float,
    val cardHorizontalPaddingDp: Float,
    val cardVerticalPaddingDp: Float,
    val cardMarginDp: Float,
)

internal fun resolveScheduleScaleTypography(uiScalePercent: Int): ScheduleScaleTypography = when {
    uiScalePercent <= 95 -> ScheduleScaleTypography(
        scalePercent = uiScalePercent,
        titleFontSizeSp = 14.5f,
        placeFontSizeSp = 11.8f,
        lineHeightFactor = 1.15f,
        titleMaxLines = 4,
        placeMaxLines = 3,
        axisNumberSp = 11.0f,
        axisTimeSp = 8.8f,
        axisWidthDp = 28f,
        headerWeekdaySp = 12.5f,
        headerDateSp = 11.2f,
        cardHorizontalPaddingDp = 2.0f,
        cardVerticalPaddingDp = 2.5f,
        cardMarginDp = 1.2f,
    )
    uiScalePercent <= 104 -> ScheduleScaleTypography(
        scalePercent = uiScalePercent,
        titleFontSizeSp = 13.5f,
        placeFontSizeSp = 11.0f,
        lineHeightFactor = 1.14f,
        titleMaxLines = 5,
        placeMaxLines = 3,
        axisNumberSp = 10.5f,
        axisTimeSp = 8.2f,
        axisWidthDp = 27f,
        headerWeekdaySp = 12.0f,
        headerDateSp = 10.6f,
        cardHorizontalPaddingDp = 1.8f,
        cardVerticalPaddingDp = 2.2f,
        cardMarginDp = 1.0f,
    )
    uiScalePercent <= 114 -> ScheduleScaleTypography(
        scalePercent = uiScalePercent,
        titleFontSizeSp = 12.4f,
        placeFontSizeSp = 10.0f,
        lineHeightFactor = 1.13f,
        titleMaxLines = 5,
        placeMaxLines = 4,
        axisNumberSp = 9.8f,
        axisTimeSp = 7.6f,
        axisWidthDp = 26f,
        headerWeekdaySp = 11.2f,
        headerDateSp = 10.0f,
        cardHorizontalPaddingDp = 1.5f,
        cardVerticalPaddingDp = 2.0f,
        cardMarginDp = 0.9f,
    )
    else -> ScheduleScaleTypography(
        scalePercent = uiScalePercent,
        titleFontSizeSp = 11.4f,
        placeFontSizeSp = 9.2f,
        lineHeightFactor = 1.12f,
        titleMaxLines = 6,
        placeMaxLines = 4,
        axisNumberSp = 9.0f,
        axisTimeSp = 7.0f,
        axisWidthDp = 25f,
        headerWeekdaySp = 10.5f,
        headerDateSp = 9.4f,
        cardHorizontalPaddingDp = 1.2f,
        cardVerticalPaddingDp = 1.8f,
        cardMarginDp = 0.8f,
    )
}

@Composable
internal fun SchedulePage(
    courses: List<Course>,
    semester: LocalDate,
    status: CourseStatus,
    appearanceSnapshot: String,
    colors: Map<String, String>,
    uiScalePercent: Int = DEFAULT_UI_SCALE_PERCENT,
    isActive: Boolean = true,
    refreshing: Boolean = false,
    refreshRevision: Int = 0,
    contentPadding: PaddingValues,
    onModeChange: (Int) -> Unit,
    onOpenStatus: (heroKey: String) -> Unit,
    onOpenLesson: (Course, heroKey: String) -> Unit,
    onOpenDay: (LocalDate, heroKey: String) -> Unit,
    onRoutineChange: (Course, String) -> Unit,
    onScaleCommit: (Float) -> Unit,
    onRefresh: () -> Unit,
    onAddCustomCourse: (Course) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var showAddCustomSheet by remember { mutableStateOf(false) }
    var openScrollTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(mode) { onModeChange(mode) }

    val appearance = remember(appearanceSnapshot) {
        runCatching { JSONObject(appearanceSnapshot) }.getOrElse { JSONObject() }
    }
    val storedCardHeight = appearance.optDouble("cardHeight", 60.0).toFloat().coerceIn(24f, 160f)
    var cardHeight by remember(storedCardHeight) { mutableFloatStateOf(storedCardHeight) }
    val scale = cardHeight / 60f

    val weekIndex = remember(courses) { WeekCourseIndex(courses) }
    val baseBodyStyle = XachetTypeTokens.ScheduleGridText
    val scaleTypography = remember(uiScalePercent) { resolveScheduleScaleTypography(uiScalePercent) }
    val cardStyle = remember(appearanceSnapshot, colors, courses, baseBodyStyle, scaleTypography) {
        WeekCardStyle(appearance, colors, courses, baseBodyStyle, scaleTypography)
    }
    val textMeasurer = rememberTextMeasurer()
    val blockLayoutCache = remember(cardStyle, textMeasurer) {
        CourseBlockLayoutCache(cardStyle, textMeasurer)
    }
    val normalAxisPaints = rememberTimeAxisPaints(scaleTypography)
    val staggeredAxisPaints = rememberTimeAxisPaints(scaleTypography)
    val dateHeaderPaints = rememberWeekHeaderPaints(textMeasurer, scaleTypography)

    val currentWeek = remember(semester) {
        CourseClock.week(LocalDate.now(), semester).coerceIn(1, MAX_WEEKS)
    }
    val weeksPerPage = if (landscape) 2 else 1
    val weekPager = rememberPagerState(
        initialPage = (currentWeek - 1) / weeksPerPage,
        pageCount = { MAX_WEEKS / weeksPerPage },
    )
    val monthStart = remember(semester) { YearMonth.from(semester) }
    val currentMonthOffset = remember(monthStart) {
        ChronoUnit.MONTHS.between(monthStart, YearMonth.now()).toInt().coerceIn(0, 11)
    }
    val monthPager = rememberPagerState(
        initialPage = currentMonthOffset / (if (landscape) 1 else 2),
        pageCount = { if (landscape) 12 else 6 },
    )

    val pullState = rememberPullToRefreshState()
    val pinchScaleModifier = if (mode == 0) {
        Modifier.pointerInput(storedCardHeight) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) {
                        val zoom = event.calculateZoom()
                        if (zoom != 1f) {
                            val next = (cardHeight * zoom).coerceIn(28f, 140f)
                            if (next != cardHeight) {
                                cardHeight = next
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        }
    } else {
        Modifier
    }

    // Commit scaled card height smoothly after user pauses pinch adjustment
    LaunchedEffect(cardHeight) {
        if (cardHeight != storedCardHeight) {
            kotlinx.coroutines.delay(450L)
            onScaleCommit(cardHeight)
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            if (mode == 0) {
                val targetPage = (currentWeek - 1) / weeksPerPage
                if (weekPager.currentPage != targetPage) {
                    weekPager.scrollToPage(targetPage)
                }
            }
            openScrollTrigger++
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ScheduleTopBar(
                    mode = mode,
                    landscape = landscape,
                    weeksPerPage = weeksPerPage,
                    currentWeek = currentWeek,
                    currentMonthOffset = currentMonthOffset,
                    semester = semester,
                    weekPager = weekPager,
                    monthPager = monthPager,
                    status = status,
                    isActive = isActive,
                    onOpenStatus = onOpenStatus,
                    onJumpToToday = {
                        scope.launch {
                            when (mode) {
                                0 -> weekPager.animateScrollToPage((currentWeek - 1) / weeksPerPage)
                                1 -> monthPager.animateScrollToPage(
                                    currentMonthOffset / (if (landscape) 1 else 2),
                                )
                            }
                        }
                    },
                    onAddCourse = { showAddCustomSheet = true },
                )
                ExpressiveConnectedToggleRow(
                    options = listOf("周视图", "月视图", "学期视图"),
                    selectedIndex = mode,
                    onSelect = { mode = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
                )
            }
        },
    ) { topPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding.calculateTopPadding())
                .then(pinchScaleModifier),
        ) {
            val bottomClearance = contentPadding.calculateBottomPadding() + 16.dp
            val motionScheme = MaterialTheme.motionScheme

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { ExpressiveMotion.sharedAxisTransform(motionScheme) },
                    modifier = Modifier.fillMaxSize(),
                    label = "ScheduleViewMode",
                ) { currentMode ->
                    when (currentMode) {
                        0 -> HorizontalPager(
                            state = weekPager,
                            key = { "week-$it-$weeksPerPage-$refreshRevision" },
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize(),
                        ) { pageIndex ->
                            if (landscape) {
                                val paneWidth = ((viewportWidth - 24.dp) * 0.5f).coerceAtLeast(160.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(2) { offset ->
                                        val week = pageIndex * 2 + offset + 1
                                        WeekGrid(
                                            page = weekIndex[week],
                                            semester = semester,
                                            week = week,
                                            status = status,
                                            scrollTrigger = if (week == currentWeek) openScrollTrigger else 0,
                                            availableWidth = paneWidth,
                                            style = cardStyle,
                                            scaleTypography = scaleTypography,
                                            layoutCache = blockLayoutCache,
                                            normalAxis = normalAxisPaints,
                                            staggeredAxis = staggeredAxisPaints,
                                            dateHeader = dateHeaderPaints,
                                            scale = scale,
                                            bottomClearance = bottomClearance,
                                            modifier = Modifier.weight(1f),
                                            onCourse = onOpenLesson,
                                        )
                                    }
                                }
                            } else {
                                val week = pageIndex + 1
                                WeekGrid(
                                    page = weekIndex[week],
                                    semester = semester,
                                    week = week,
                                    status = status,
                                    scrollTrigger = if (week == currentWeek) openScrollTrigger else 0,
                                    availableWidth = viewportWidth,
                                    style = cardStyle,
                                    scaleTypography = scaleTypography,
                                    layoutCache = blockLayoutCache,
                                    normalAxis = normalAxisPaints,
                                    staggeredAxis = staggeredAxisPaints,
                                    dateHeader = dateHeaderPaints,
                                    scale = scale,
                                    bottomClearance = bottomClearance,
                                    modifier = Modifier.fillMaxSize(),
                                    onCourse = onOpenLesson,
                                )
                            }
                        }

                        1 -> VerticalPager(
                            state = monthPager,
                            key = { "month-$it-$landscape-$refreshRevision" },
                            beyondViewportPageCount = 1,
                            modifier = Modifier.fillMaxSize(),
                        ) { pageIndex ->
                            if (landscape) {
                                MonthGrid(
                                    courses = courses,
                                    semester = semester,
                                    month = monthStart.plusMonths(pageIndex.toLong()),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = bottomClearance),
                                    fitHeight = true,
                                    compact = true,
                                    heroPrefix = "month",
                                    onDay = onOpenDay,
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = bottomClearance),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    repeat(2) { slot ->
                                        MonthGrid(
                                            courses = courses,
                                            semester = semester,
                                            month = monthStart.plusMonths((pageIndex * 2 + slot).toLong()),
                                            modifier = Modifier.weight(1f),
                                            fitHeight = true,
                                            heroPrefix = "month",
                                            onDay = onOpenDay,
                                        )
                                    }
                                }
                            }
                        }

                        else -> {
                            val overviewColumns = if (landscape) 3 else 2
                            val semesterRows = (6 + overviewColumns - 1) / overviewColumns
                            val availableHeight = (viewportHeight - bottomClearance - 16.dp).coerceAtLeast(240.dp)
                            val fontScale = LocalDensity.current.fontScale
                            val cellHeight = (((availableHeight - 10.dp * (semesterRows - 1)) / semesterRows - 56.dp) / 6)
                                .coerceIn((16f * fontScale).dp, 34.dp.coerceAtLeast((16f * fontScale).dp))

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(overviewColumns),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = bottomClearance,
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(6, key = { "semester-$it-$refreshRevision" }) { index ->
                                    MonthGrid(
                                        courses = courses,
                                        semester = semester,
                                        month = monthStart.plusMonths(index.toLong()),
                                        cellHeight = cellHeight,
                                        compact = true,
                                        heroPrefix = "semester",
                                        onDay = onOpenDay,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomSheet) {
        val activeWeek = (weekPager.currentPage * weeksPerPage + 1).coerceIn(1, 25)
        CustomCourseEditorSheet(
            courses = courses,
            initialWeek = activeWeek,
            initialCourse = null,
            onDismiss = { showAddCustomSheet = false },
            onConfirm = { newCourse ->
                onAddCustomCourse(newCourse)
                showAddCustomSheet = false
            },
        )
    }
}

/** Isolated top app bar so pager currentPage changes during swipes never recompose SchedulePage grids. */
@Composable
private fun ScheduleTopBar(
    mode: Int,
    landscape: Boolean,
    weeksPerPage: Int,
    currentWeek: Int,
    currentMonthOffset: Int,
    semester: LocalDate,
    weekPager: PagerState,
    monthPager: PagerState,
    status: CourseStatus,
    isActive: Boolean,
    onOpenStatus: (heroKey: String) -> Unit,
    onJumpToToday: () -> Unit,
    onAddCourse: () -> Unit,
) {
    val displayedWeekStart = weekPager.currentPage * weeksPerPage + 1
    val targetWeekPage = (currentWeek - 1) / weeksPerPage
    val isCurrentTarget = when (mode) {
        0 -> targetWeekPage == weekPager.currentPage
        1 -> (if (landscape) currentMonthOffset else currentMonthOffset / 2) == monthPager.currentPage
        else -> true
    }

    val bounceProgress = remember { Animatable(0f) }
    var previousPage by remember { mutableIntStateOf(weekPager.currentPage) }
    var previousActive by remember { mutableStateOf(isActive) }
    var lastBecameActiveAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(weekPager.currentPage, targetWeekPage, mode, isActive) {
        val justBecameActive = isActive && !previousActive
        if (justBecameActive) {
            lastBecameActiveAt = System.currentTimeMillis()
        }
        val current = weekPager.currentPage
        val pageChanged = previousPage != current
        previousPage = current
        previousActive = isActive
        val withinTabOpenReset = System.currentTimeMillis() - lastBecameActiveAt <= 220L
        if (!isActive || justBecameActive) {
            bounceProgress.snapTo(0f)
        } else if (mode == 0 && !withinTabOpenReset && pageChanged && current == targetWeekPage) {
            bounceProgress.snapTo(0f)
            bounceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = WEEK_TITLE_BOUNCE_TOTAL_DURATION_MS,
                    easing = LinearEasing,
                ),
            )
            bounceProgress.snapTo(0f)
        }
    }

    val topTitle = when (mode) {
        0 -> if (landscape) "第 $displayedWeekStart–${displayedWeekStart + 1} 周" else "第 $displayedWeekStart 周"
        1 -> "月视图"
        else -> "学期总览"
    }
    val titleUnitCount = remember(topTitle) { splitWeekTitleBounceUnits(topTitle).size }
    val weekMonday = remember(semester, displayedWeekStart) {
        semester.plusWeeks((displayedWeekStart - 1).toLong())
    }
    val weekSunday = remember(weekMonday, weeksPerPage) {
        weekMonday.plusDays((weeksPerPage * 7 - 1).toLong())
    }

    TopAppBar(
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics {
                        text = AnnotatedString(topTitle)
                    },
                ) {
                    var unitCursor = 0
                    topTitle.forEach { ch ->
                        if (ch.isWhitespace()) {
                            Spacer(Modifier.width(4.dp))
                        } else {
                            val unitIndex = unitCursor++
                            RollingNumberText(
                                text = ch.toString(),
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                maxLines = 1,
                                animateSize = false,
                                modifier = Modifier.graphicsLayer {
                                    val s = computeCharBounceScale(bounceProgress.value, unitIndex, titleUnitCount)
                                    scaleX = s
                                    scaleY = s
                                    translationY = -(s - 1f) * 6.dp.toPx()
                                },
                            )
                        }
                    }
                }
                if (mode == 0) {
                    RollingNumberText(
                        text = "${weekMonday.monthValue}.${weekMonday.dayOfMonth}–" +
                            "${weekSunday.monthValue}.${weekSunday.dayOfMonth}" +
                            if (displayedWeekStart == currentWeek) " · 本周" else "",
                        style = XachetTypeTokens.CondensedMeta,
                        color = if (displayedWeekStart == currentWeek) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        animateSize = false,
                    )
                }
            }
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusBgColor = when {
                    status.current != null -> MaterialTheme.colorScheme.primaryContainer
                    status.next != null -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                Surface(
                    onClick = { onOpenStatus("schedule-status") },
                    shape = MaterialTheme.shapes.large,
                    color = statusBgColor,
                    contentColor = when {
                        status.current != null -> MaterialTheme.colorScheme.onPrimaryContainer
                        status.next != null -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .heroFlightSource(
                            key = "schedule-status",
                            containerColor = statusBgColor,
                            cornerDp = 16f,
                        ),
                ) {
                    val active = status.current
                    val next = status.next
                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        if (active != null) {
                            LiveCourseCountdownText(
                                end = active.end,
                                style = XachetTypeTokens.PillStatus,
                            )
                        } else {
                            RollingNumberText(
                                text = when {
                                    next != null -> "下节 ${next.startTime()}"
                                    status.completed -> "今日 ${status.today.size} 节"
                                    else -> "无课"
                                },
                                style = XachetTypeTokens.PillStatus,
                                maxLines = 1,
                                animateSize = false,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !isCurrentTarget,
                    enter = expandHorizontally(
                        animationSpec = spring(dampingRatio = 0.86f, stiffness = 700f),
                        expandFrom = Alignment.CenterHorizontally,
                    ) + scaleIn(
                        initialScale = 0.72f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 820f),
                    ) + fadeIn(animationSpec = tween(120)),
                    exit = shrinkHorizontally(
                        animationSpec = spring(dampingRatio = 0.94f, stiffness = 700f),
                        shrinkTowards = Alignment.CenterHorizontally,
                    ) + scaleOut(
                        targetScale = 0.72f,
                        animationSpec = spring(dampingRatio = 0.94f, stiffness = 820f),
                    ) + fadeOut(animationSpec = tween(90)),
                ) {
                    TextButton(
                        onClick = onJumpToToday,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (mode == 0) "本周" else "本月",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                IconButton(onClick = onAddCourse) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加自定义课程")
                }
            }
        },
        windowInsets = WindowInsets.statusBars,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

private data class CourseBlockLayoutKey(
    val title: String,
    val place: String,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Shared text-layout cache across all 60 week pages so swiping horizontally reuses shaped glyph runs.
 */
private class CourseBlockLayoutCache(
    private val style: WeekCardStyle,
    private val textMeasurer: TextMeasurer,
) {
    private val cache = HashMap<CourseBlockLayoutKey, List<TextLayoutResult>>(128)

    fun measureOrGet(course: Course, widthPx: Int, heightPx: Int): List<TextLayoutResult> {
        if (widthPx <= 0 || heightPx <= 0) return emptyList()
        val key = CourseBlockLayoutKey(course.title, course.place, widthPx, heightPx)
        return cache.getOrPut(key) {
            val totalHeight = heightPx.toFloat()
            var chosen = measureAttempt(course, widthPx, totalHeight, fontStep = 1.0f, extraLines = 0)
            if (!chosen.second) {
                for ((step, extra) in listOf(0.92f to 1, 0.85f to 2, 0.78f to 3)) {
                    chosen = measureAttempt(course, widthPx, totalHeight, fontStep = step, extraLines = extra)
                    if (chosen.second) break
                }
            }
            chosen.first
        }
    }

    private fun measureAttempt(
        course: Course,
        widthPx: Int,
        totalHeight: Float,
        fontStep: Float,
        extraLines: Int,
    ): Pair<List<TextLayoutResult>, Boolean> {
        val titleStyle = if (fontStep == 1f) {
            style.title
        } else {
            style.title.copy(
                fontSize = style.title.fontSize * fontStep,
                lineHeight = style.title.lineHeight * fontStep,
            )
        }
        val placeStyle = if (fontStep == 1f) {
            style.place
        } else {
            style.place.copy(
                fontSize = style.place.fontSize * fontStep,
                lineHeight = style.place.lineHeight * fontStep,
            )
        }
        val maxTitleLines = (style.titleLines + extraLines).coerceAtMost(12)
        val maxPlaceLines = (style.placeLines + extraLines).coerceAtMost(8)

        val placeProbe = if (course.place.isNotBlank()) {
            textMeasurer.measure(
                text = AnnotatedString(course.place),
                style = placeStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxPlaceLines,
                constraints = Constraints(maxWidth = widthPx),
            )
        } else {
            null
        }
        val reservedPlaceHeight = if (placeProbe != null) {
            minOf(placeProbe.size.height.toFloat(), totalHeight * 0.45f)
        } else {
            0f
        }
        val layouts = ArrayList<TextLayoutResult>(2)
        var usedHeight = 0f
        var allComplete = true

        if (course.title.isNotBlank()) {
            val availableForTitle = (totalHeight - reservedPlaceHeight).coerceAtLeast(0f)
            val titleProbe = textMeasurer.measure(
                text = AnnotatedString(course.title),
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = maxTitleLines,
                constraints = Constraints(maxWidth = widthPx),
            )
            var wholeTitleLines = 0
            for (i in 0 until titleProbe.lineCount) {
                if (titleProbe.getLineBottom(i) <= availableForTitle) {
                    wholeTitleLines++
                } else {
                    break
                }
            }
            if (wholeTitleLines == 0 && titleProbe.lineCount > 0 && titleProbe.getLineBottom(0) <= totalHeight) {
                wholeTitleLines = 1
            }
            if (titleProbe.hasVisualOverflow || wholeTitleLines < titleProbe.lineCount) {
                allComplete = false
            }
            val titleLayout = when {
                wholeTitleLines == 0 -> null
                wholeTitleLines == titleProbe.lineCount -> titleProbe
                else -> textMeasurer.measure(
                    text = AnnotatedString(course.title),
                    style = titleStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = wholeTitleLines,
                    constraints = Constraints(maxWidth = widthPx),
                )
            }
            if (titleLayout != null) {
                layouts += titleLayout
                usedHeight += titleLayout.size.height.toFloat()
            }
        }

        if (placeProbe != null) {
            val remaining = (totalHeight - usedHeight).coerceAtLeast(0f)
            var wholePlaceLines = 0
            for (i in 0 until placeProbe.lineCount) {
                if (placeProbe.getLineBottom(i) <= remaining) {
                    wholePlaceLines++
                } else {
                    break
                }
            }
            if (placeProbe.hasVisualOverflow || wholePlaceLines < placeProbe.lineCount) {
                allComplete = false
            }
            val placeLayout = when {
                wholePlaceLines == 0 -> null
                wholePlaceLines == placeProbe.lineCount -> placeProbe
                else -> textMeasurer.measure(
                    text = AnnotatedString(course.place),
                    style = placeStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = wholePlaceLines,
                    constraints = Constraints(maxWidth = widthPx),
                )
            }
            if (placeLayout != null) {
                layouts += placeLayout
            }
        }

        return layouts to allComplete
    }
}

@Composable
private fun WeekGrid(
    page: WeekCoursePage,
    semester: LocalDate,
    week: Int,
    status: CourseStatus,
    scrollTrigger: Int,
    availableWidth: Dp,
    style: WeekCardStyle,
    scaleTypography: ScheduleScaleTypography,
    layoutCache: CourseBlockLayoutCache,
    normalAxis: TimeAxisPaints,
    staggeredAxis: TimeAxisPaints,
    dateHeader: WeekHeaderPaints,
    scale: Float,
    bottomClearance: Dp,
    modifier: Modifier = Modifier,
    onCourse: (Course, heroKey: String) -> Unit,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val layoutScale = ((fontScale - 1f) * 0.55f + 1f).coerceAtLeast(1f)
    val verticalScroll = rememberScrollState()
    val daysHorizontalScroll = rememberScrollState()
    val mondayDate = remember(semester, week) { semester.plusWeeks((week - 1).toLong()) }
    val today = remember { LocalDate.now() }
    val todayIndex = remember(mondayDate, today) {
        val diff = ChronoUnit.DAYS.between(mondayDate, today).toInt()
        if (diff in 0..6) diff else -1
    }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var appliedScrollTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(scrollTrigger, viewportHeightPx) {
        if (scrollTrigger > appliedScrollTrigger && viewportHeightPx > 0) {
            if (verticalScroll.maxValue == Int.MAX_VALUE) {
                withFrameNanos {}
            }
            val targetPx = computeScheduleTargetScrollPx(
                status = status,
                page = page,
                todayDayIndex = todayIndex,
                scale = scale,
                densityPxPerDp = density.density,
                viewportHeightPx = viewportHeightPx,
                bottomClearancePx = with(density) { bottomClearance.roundToPx() },
            )
            appliedScrollTrigger = scrollTrigger
            verticalScroll.scrollTo(targetPx.coerceIn(0, verticalScroll.maxValue))
        }
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val todayColumnHighlight = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
    val staggeredStrokeColor = MaterialTheme.colorScheme.primary

    val axisWidth = (scaleTypography.axisWidthDp * layoutScale.coerceIn(1f, 1.4f)).dp
    val dayWidth = ((availableWidth - axisWidth * 2) / 7).coerceAtLeast(
        if (fontScale >= 1.2f) (48f * fontScale).dp else 0.dp,
    )

    Column(modifier = modifier.fillMaxSize().graphicsLayer {}) {
            if (fontScale >= 1.2f) {
                Text(
                    text = "第 $week 周 · 左右滑动查看日期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(axisWidth))
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(daysHorizontalScroll, enabled = fontScale >= 1.2f),
                ) {
                    WeekDateHeader(
                        date = mondayDate,
                        todayIndex = todayIndex,
                        dayWidth = dayWidth,
                        style = dateHeader,
                    )
                }
                Spacer(Modifier.width(axisWidth))
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeightPx = it.height }
                    .verticalScroll(verticalScroll)
                    .padding(top = 6.dp, bottom = bottomClearance),
            ) {
                TimeAxis(staggered = false, scale = scale, paints = normalAxis, axisWidth = axisWidth)
                val softDividerColor = remember(dividerColor) { dividerColor.copy(alpha = 0.45f) }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(daysHorizontalScroll, enabled = fontScale >= 1.2f)
                        .drawWithCache {
                            val minorStroke = 0.4.dp.toPx()
                            val majorStroke = 0.6.dp.toPx()
                            val dayW = dayWidth.toPx()
                            val minorY = FloatArray(ScheduleSectionIndices.size) { idx ->
                                (axisMinute(LessonTimes.start(ScheduleSectionIndices[idx], false)) * scale).dp.toPx()
                            }
                            val majorY = FloatArray(SchedulePairIndices.size) { idx ->
                                (axisMinute(LessonTimes.end(SchedulePairIndices[idx], false)) * scale).dp.toPx()
                            }
                            onDrawBehind {
                                if (todayIndex in 0..6) {
                                    drawRect(
                                        color = todayColumnHighlight,
                                        topLeft = Offset(dayW * todayIndex, 0f),
                                        size = androidx.compose.ui.geometry.Size(dayW, size.height),
                                    )
                                }
                                for (i in minorY.indices) {
                                    val y = minorY[i]
                                    drawLine(
                                        color = softDividerColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = minorStroke,
                                    )
                                }
                                for (i in majorY.indices) {
                                    val y = majorY[i]
                                    drawLine(
                                        color = dividerColor,
                                        start = Offset(0f, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = majorStroke,
                                    )
                                }
                            }
                        },
                ) {
                    repeat(7) { day ->
                        Box(
                            modifier = Modifier
                                .width(dayWidth)
                                .height((WeekAxisHeightMinutes * scale).dp),
                        ) {
                            page.days[day].forEach { block ->
                                val course = block.course
                                val heroKey = remember(week, course.id, course.weekday, course.first, course.title) {
                                    "schedule-course-$week-${course.id}-${course.weekday}-${course.first}-${course.title.hashCode()}"
                                }
                                androidx.compose.runtime.key(course.id, course.weekday, course.first, course.title, course.routine) {
                                    val top = block.topMinute * scale
                                    val height = block.heightMinutes * scale
                                    val cardColor = style.colors.getValue(course.title)

                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = style.margin)
                                            .offset(y = top.dp)
                                            .fillMaxWidth()
                                            .height(height.dp)
                                            .heroFlightSource(
                                                key = heroKey,
                                                containerColor = cardColor,
                                                cornerDp = style.radiusDp,
                                            )
                                            .clip(style.shape)
                                            .background(cardColor, style.shape)
                                            .then(
                                                if (course.staggered) {
                                                    Modifier.border(1.5.dp, staggeredStrokeColor, style.shape)
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .semantics {
                                                contentDescription = "${course.title}，${course.place}，第${course.first}至${course.last}节"
                                            }
                                            .clickable { onCourse(course, heroKey) }
                                            .padding(horizontal = style.cardHorizontalPadding, vertical = style.cardVerticalPadding)
                                            .drawWithCache {
                                                val width = size.width.toInt().coerceAtLeast(0)
                                                val heightPx = size.height.toInt().coerceAtLeast(0)
                                                val layouts = layoutCache.measureOrGet(course, width, heightPx)
                                                onDrawBehind {
                                                    var y = 0f
                                                    for (layout in layouts) {
                                                        drawText(layout, topLeft = Offset(0f, y))
                                                        y += layout.size.height
                                                    }
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
                TimeAxis(staggered = true, scale = scale, paints = staggeredAxis, axisWidth = axisWidth)
            }
        }
}

@Immutable
private class WeekCardStyle(
    appearance: JSONObject,
    courseColors: Map<String, String>,
    courses: List<Course>,
    defaults: TextStyle,
    scaleTypography: ScheduleScaleTypography,
) {
    private val titleScaleFactor = if (appearance.has("TitleFontSize")) {
        (appearance.optDouble("TitleFontSize", 14.0).toFloat() / 14f).coerceIn(0.75f, 1.6f)
    } else {
        1f
    }
    private val placeScaleFactor = if (appearance.has("PlaceFontSize")) {
        (appearance.optDouble("PlaceFontSize", 11.0).toFloat() / 11f).coerceIn(0.75f, 1.6f)
    } else {
        1f
    }
    val title = defaults.merge(
        courseTextStyle(
            appearance = appearance,
            prefix = "Title",
            fontSizeSp = scaleTypography.titleFontSizeSp * titleScaleFactor,
            defaultWeight = 6,
            lineHeightFactor = scaleTypography.lineHeightFactor,
        ),
    )
    val place = defaults.merge(
        courseTextStyle(
            appearance = appearance,
            prefix = "Place",
            fontSizeSp = scaleTypography.placeFontSizeSp * placeScaleFactor,
            defaultWeight = 5,
            lineHeightFactor = scaleTypography.lineHeightFactor,
        ),
    )
    val titleLines = if (appearance.has("TitleMaxLines") && appearance.optInt("TitleMaxLines", 3) !in setOf(3, 5)) {
        appearance.optInt("TitleMaxLines", scaleTypography.titleMaxLines).coerceIn(1, 30)
    } else {
        scaleTypography.titleMaxLines
    }
    val placeLines = if (appearance.has("PlaceMaxLines") && appearance.optInt("PlaceMaxLines", 5) !in setOf(4, 5)) {
        appearance.optInt("PlaceMaxLines", scaleTypography.placeMaxLines).coerceIn(1, 30)
    } else {
        scaleTypography.placeMaxLines
    }
    val cardHorizontalPadding = scaleTypography.cardHorizontalPaddingDp.dp
    val cardVerticalPadding = scaleTypography.cardVerticalPaddingDp.dp
    val margin = if (appearance.has("cardMargin") && appearance.optDouble("cardMargin", 2.0) != 2.0) {
        appearance.optDouble("cardMargin", 2.0).toFloat().coerceIn(0f, 10f).dp
    } else {
        scaleTypography.cardMarginDp.dp
    }
    val radiusDp = appearance.optDouble("cardBorderRadius", 6.0).toFloat().coerceIn(0f, 100f)
    val shape: Shape = RoundedCornerShape(radiusDp.dp)

    private val customColors = courses.map { it.title }.distinct().mapNotNull { courseTitle ->
        courseColors[courseTitle]?.let { raw -> parseHexColorOrNull(raw)?.let { courseTitle to it } }
    }.toMap()
    private val fallbackColors = courses.map { it.title }.distinct()
        .filterNot(customColors::containsKey)
        .sorted()
        .mapIndexed { index, courseTitle -> courseTitle to StandardCoursePalette[index % StandardCoursePalette.size] }
        .toMap()
    val colors: Map<String, Color> = courses.associate { course ->
        course.title to (customColors[course.title] ?: fallbackColors.getValue(course.title))
    }
}

private fun courseTextStyle(
    appearance: JSONObject,
    prefix: String,
    fontSizeSp: Float,
    defaultWeight: Int,
    lineHeightFactor: Float,
): TextStyle {
    val color = parseHexColor(appearance.optString("${prefix}TextColor", "#FFFFFFFF"), Color.White)
    return TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightFactor).sp,
        letterSpacing = 0.sp,
        color = color,
        fontWeight = FontWeight(appearance.optInt("${prefix}FontWeight", defaultWeight).coerceIn(1, 9) * 100),
    )
}

private class WeekHeaderPaints(
    val weekday: Paint,
    val weekdayToday: Paint,
    val date: Paint,
    val dateToday: Paint,
    val weekdayBaseline: Float,
    val dateBaseline: Float,
    val height: Int,
)

@Composable
private fun rememberWeekHeaderPaints(
    measurer: TextMeasurer,
    scaleTypography: ScheduleScaleTypography,
): WeekHeaderPaints {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val context = LocalContext.current
    val weekdayStyle = MaterialTheme.typography.labelMedium.copy(fontSize = scaleTypography.headerWeekdaySp.sp)
    val dateStyle = MaterialTheme.typography.labelSmall.tabular().copy(fontSize = scaleTypography.headerDateSp.sp)
    val weekdayFace = remember(context) {
        buildScheduleCanvasTypeface(context, weight = 520, width = 100f, roundness = 60f, opticalSize = 12f)
    }
    val dateFace = remember(context) {
        buildScheduleCanvasTypeface(context, weight = 640, width = 90f, roundness = 54f, opticalSize = 11f)
    }

    return remember(onSurface, onSurfaceVariant, primary, density, weekdayStyle, dateStyle, weekdayFace, dateFace, measurer) {
        fun paint(style: TextStyle, color: Color) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = with(density) { style.fontSize.toPx() }
            typeface = if (style == dateStyle) dateFace else weekdayFace
            textAlign = Paint.Align.CENTER
            isSubpixelText = true
            fontFeatureSettings = style.fontFeatureSettings
        }
        val weekdayMeasured = measurer.measure(AnnotatedString("一"), weekdayStyle, maxLines = 1)
        val dateMeasured = measurer.measure(AnnotatedString("1/1"), dateStyle, maxLines = 1)
        WeekHeaderPaints(
            weekday = paint(weekdayStyle, onSurface),
            weekdayToday = paint(weekdayStyle.copy(fontWeight = FontWeight.Bold), primary),
            date = paint(dateStyle, onSurfaceVariant),
            dateToday = paint(dateStyle.copy(fontWeight = FontWeight.SemiBold), primary),
            weekdayBaseline = weekdayMeasured.firstBaseline,
            dateBaseline = weekdayMeasured.size.height + dateMeasured.firstBaseline,
            height = weekdayMeasured.size.height + dateMeasured.size.height + 4,
        )
    }
}

@Composable
private fun WeekDateHeader(
    date: LocalDate,
    todayIndex: Int,
    dayWidth: Dp,
    style: WeekHeaderPaints,
) {
    val density = LocalDensity.current
    val columnPx = with(density) { dayWidth.roundToPx() }
    val headerWidth = with(density) { (columnPx * 7).toDp() }
    val headerHeight = with(density) { style.height.toDp() }
    val todayPill = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val dates = remember(date) { List(7) { date.plusDays(it.toLong()).let { d -> "${d.monthValue}/${d.dayOfMonth}" } } }

    Row(
        modifier = Modifier
            .width(headerWidth)
            .height(headerHeight)
            .drawWithCache {
                onDrawBehind {
                    drawContext.canvas.nativeCanvas.apply {
                        repeat(7) { day ->
                            val isToday = day == todayIndex
                            if (isToday) {
                                drawRect(
                                    color = todayPill,
                                    topLeft = Offset(columnPx * day.toFloat(), 0f),
                                    size = androidx.compose.ui.geometry.Size(columnPx.toFloat(), size.height),
                                )
                            }
                            val center = columnPx * (day + 0.5f)
                            drawText(
                                WeekdayNames[day],
                                center,
                                style.weekdayBaseline,
                                if (isToday) style.weekdayToday else style.weekday,
                            )
                            drawText(
                                dates[day],
                                center,
                                style.dateBaseline,
                                if (isToday) style.dateToday else style.date,
                            )
                        }
                    }
                }
            },
    ) {
        repeat(7) { day ->
            Box(
                modifier = Modifier
                    .width(dayWidth)
                    .fillMaxHeight()
                    .semantics { contentDescription = "周${WeekdayNames[day]} ${dates[day]}" },
            )
        }
    }
}

private class TimeAxisPaints(val number: Paint, val time: Paint)

@Composable
private fun rememberTimeAxisPaints(scaleTypography: ScheduleScaleTypography): TimeAxisPaints {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val context = LocalContext.current
    val numberFace = remember(context) {
        buildScheduleCanvasTypeface(context, weight = 660, width = 90f, roundness = 64f, opticalSize = 11f)
    }
    val timeFace = remember(context) {
        buildScheduleCanvasTypeface(context, weight = 520, width = 88f, roundness = 52f, opticalSize = 10f)
    }

    return remember(onSurface, onSurfaceVariant, density, numberFace, timeFace, scaleTypography) {
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurface.toArgb()
            textSize = with(density) { scaleTypography.axisNumberSp.sp.toPx() }
            typeface = numberFace
            isSubpixelText = true
            fontFeatureSettings = "tnum"
        }
        val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = onSurfaceVariant.toArgb()
            textSize = with(density) { scaleTypography.axisTimeSp.sp.toPx() }
            typeface = timeFace
            isSubpixelText = true
            fontFeatureSettings = "tnum"
        }
        TimeAxisPaints(number = number, time = time)
    }
}

private data class TimeAxisEntry(
    val sectionLabel: String,
    val startLabel: String,
    val endLabel: String,
    val startMinute: Float,
)

@Composable
private fun TimeAxis(
    staggered: Boolean,
    scale: Float,
    paints: TimeAxisPaints,
    axisWidth: Dp,
) {
    val labels = remember(staggered) {
        (1..11).map { section ->
            val start = LessonTimes.start(section, staggered)
            TimeAxisEntry(
                sectionLabel = section.toString(),
                startLabel = start.toString(),
                endLabel = LessonTimes.end(section, staggered).toString(),
                startMinute = axisMinute(start),
            )
        }
    }
    val description = remember(labels) {
        labels.joinToString("；") { "第${it.sectionLabel}节 ${it.startLabel}至${it.endLabel}" }
    }

    Box(
        modifier = Modifier
            .width(axisWidth)
            .height((WeekAxisHeightMinutes * scale).dp)
            .semantics { contentDescription = description }
            .drawWithCache {
                val xPad = 1.5.dp.toPx()
                val gap1 = paints.number.textSize
                val gap2 = gap1 + paints.time.textSize + 3.dp.toPx()
                val gap3 = gap1 + paints.time.textSize * 2 + 5.5.dp.toPx()
                val baseY = FloatArray(labels.size) { idx ->
                    (labels[idx].startMinute * scale).dp.toPx()
                }
                onDrawBehind {
                    val canvas = drawContext.canvas.nativeCanvas
                    for (i in labels.indices) {
                        val entry = labels[i]
                        val y = baseY[i]
                        canvas.drawText(entry.sectionLabel, xPad, y + gap1, paints.number)
                        canvas.drawText(entry.startLabel, xPad, y + gap2, paints.time)
                        canvas.drawText(entry.endLabel, xPad, y + gap3, paints.time)
                    }
                }
            },
    )
}

@Composable
private fun MonthGrid(
    courses: List<Course>,
    semester: LocalDate,
    month: YearMonth,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 52.dp,
    fitHeight: Boolean = false,
    compact: Boolean = false,
    heroPrefix: String = "month",
    onDay: (LocalDate, heroKey: String) -> Unit = { _, _ -> },
) {
    val first = month.atDay(1)
    val cells = first.dayOfWeek.value - 1 + month.lengthOfMonth()
    val counts = remember(courses, semester, month) { CalendarMonthData.counts(courses, semester, month) }
    val today = remember { LocalDate.now() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 10.dp else 14.dp),
        ) {
            Text(
                text = "${month.year}年${month.monthValue}月",
                style = if (compact) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.padding(bottom = if (compact) 4.dp else 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayNames.forEach { weekday ->
                    Text(
                        text = weekday,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
            repeat(if (compact) 6 else (cells + 6) / 7) { row ->
                Row(
                    modifier = if (fitHeight) Modifier.weight(1f) else Modifier.height(cellHeight),
                ) {
                    repeat(7) { column ->
                        val day = row * 7 + column - (first.dayOfWeek.value - 1) + 1
                        val date = if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
                        val count = if (date != null) counts[day] ?: 0 else 0
                        val isToday = date == today
                        val dayHeroKey = if (date != null) "schedule-day-$heroPrefix-$date" else null
                        val dayBgColor = if (isToday) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.dp)
                                .let { base ->
                                    if (!compact && dayHeroKey != null) {
                                        base.heroFlightSource(
                                            key = dayHeroKey,
                                            containerColor = dayBgColor,
                                            cornerDp = 8f,
                                        )
                                    } else {
                                        base
                                    }
                                }
                                .clip(MaterialTheme.shapes.small)
                                .then(
                                    if (isToday) {
                                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        Modifier
                                    },
                                )
                                .then(
                                    if (date != null && dayHeroKey != null) {
                                        Modifier.clickable { onDay(date, dayHeroKey) }
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            if (date != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .semantics { contentDescription = "$date，${count}节课程" },
                                ) {
                                    Text(
                                        text = MonthDayLabels.getOrElse(day) { day.toString() },
                                        style = (if (compact) {
                                            MaterialTheme.typography.labelSmall
                                        } else if (fitHeight) {
                                            MaterialTheme.typography.labelMedium
                                        } else {
                                            MaterialTheme.typography.bodyMedium
                                        }).tabular(),
                                        fontWeight = if (isToday || count > 0) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                            count > 0 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                    )
                                    if (!compact && !fitHeight && count > 0 && cellHeight >= 32.dp) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                        ) {
                                            Text(
                                                text = MonthCountLabels.getOrElse(count) { "${count}节" },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp).tabular(),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive ModalBottomSheet for adding or editing a custom course directly in SchedulePage.
 * Supports picking start/end sections (`1..11`), teaching building + room, week set, and routine override.
 */
@Composable
internal fun CustomCourseEditorSheet(
    courses: List<Course>,
    initialWeek: Int,
    initialCourse: Course?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (Course) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val buildings = remember(courses) { resolveAvailableBuildings(courses) + "自定义地点" }
    val initialBuilding = remember(initialCourse, buildings) {
        if (initialCourse == null) {
            buildings.firstOrNull() ?: "第一教学楼"
        } else {
            val extracted = extractBuildingName(initialCourse.place)
            buildings.firstOrNull { it == extracted || initialCourse.place.startsWith(it) } ?: "自定义地点"
        }
    }
    val initialRoom = remember(initialCourse, initialBuilding) {
        if (initialCourse == null) {
            ""
        } else if (initialBuilding == "自定义地点") {
            initialCourse.place
        } else {
            initialCourse.place.removePrefix(initialBuilding).trim()
        }
    }

    var title by remember(initialCourse) { mutableStateOf(initialCourse?.title.orEmpty()) }
    var teacher by remember(initialCourse) { mutableStateOf(initialCourse?.teacher.orEmpty()) }
    var weekday by remember(initialCourse) {
        mutableIntStateOf(initialCourse?.weekday ?: LocalDate.now().dayOfWeek.value.coerceIn(1, 7))
    }
    var firstSection by remember(initialCourse) { mutableIntStateOf(initialCourse?.first ?: 1) }
    var lastSection by remember(initialCourse) { mutableIntStateOf(initialCourse?.last ?: 2) }
    var selectedBuilding by remember(initialCourse) { mutableStateOf(initialBuilding) }
    var roomText by remember(initialCourse) { mutableStateOf(initialRoom) }
    var selectedWeeks by remember(initialCourse, initialWeek) {
        mutableStateOf(
            initialCourse?.weeks?.toSet() ?: setOf(initialWeek.coerceIn(1, 25)),
        )
    }
    var routine by remember(initialCourse) { mutableStateOf(initialCourse?.routine ?: "auto") }
    var showExtraWeeks by remember { mutableStateOf((initialCourse?.weeks?.maxOrNull() ?: initialWeek) > 20) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val combinedPlace = remember(selectedBuilding, roomText) {
        val r = roomText.trim()
        if (selectedBuilding == "自定义地点") r else if (r.isBlank()) selectedBuilding else "$selectedBuilding$r"
    }
    val previewStaggered = routine == "staggered" ||
        (routine == "auto" && (combinedPlace.contains("逸夫楼") || combinedPlace.contains("信息科技大楼")))
    val timePreview = remember(firstSection, lastSection, previewStaggered) {
        val safeFirst = firstSection.coerceIn(1, 11)
        val safeLast = lastSection.coerceIn(safeFirst, 11)
        "${LessonTimes.start(safeFirst, previewStaggered)}–${LessonTimes.end(safeLast, previewStaggered)}"
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (initialCourse == null) "添加自定义课程" else "编辑自定义课程",
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text("课程名称 *") },
                placeholder = { Text("如：专业选修研讨、体育加练") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = teacher,
                onValueChange = { teacher = it },
                label = { Text("授课教师 / 备注（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Weekday picker
            Text("上课星期", style = MaterialTheme.typography.titleSmallEmphasized)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                WeekdayNames.forEachIndexed { idx, name ->
                    val dayVal = idx + 1
                    FilterChip(
                        selected = weekday == dayVal,
                        onClick = { weekday = dayVal },
                        label = {
                            Text(
                                text = name,
                                style = XachetTypeTokens.PillStatus,
                                maxLines = 1,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Section range picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("添加到的节数", style = MaterialTheme.typography.titleSmallEmphasized)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = "第 $firstSection–$lastSection 节 · $timePreview · ${if (previewStaggered) "错峰" else "普通"}",
                        style = XachetTypeTokens.PillStatus.tabular(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(1 to 2, 3 to 4, 5 to 6, 7 to 8, 9 to 10, 11 to 11).forEach { (s, e) ->
                    val label = if (s == e) "第${s}节" else "${s}-${e}节"
                    FilterChip(
                        selected = firstSection == s && lastSection == e,
                        onClick = {
                            firstSection = s
                            lastSection = e
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium.tabular()) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionStepSelector(
                    label = "开始节次",
                    value = firstSection,
                    range = 1..11,
                    onChange = { next ->
                        firstSection = next
                        if (lastSection < next) lastSection = next
                    },
                    modifier = Modifier.weight(1f),
                )
                SectionStepSelector(
                    label = "结束节次",
                    value = lastSection,
                    range = firstSection..11,
                    onChange = { next -> lastSection = next },
                    modifier = Modifier.weight(1f),
                )
            }

            // Teaching building picker + classroom number
            Text("选择教学楼与教室", style = MaterialTheme.typography.titleSmallEmphasized)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                buildings.forEach { b ->
                    FilterChip(
                        selected = selectedBuilding == b,
                        onClick = { selectedBuilding = b },
                        label = { Text(b, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            OutlinedTextField(
                value = roomText,
                onValueChange = { roomText = it },
                label = { Text(if (selectedBuilding == "自定义地点") "完整地点名称" else "教室号 / 具体房间（可选）") },
                placeholder = { Text(if (selectedBuilding == "自定义地点") "如：东坡田径场" else "如：201") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val routineOptions = listOf("auto" to "自动识别作息", "normal" to "普通作息", "staggered" to "错峰作息")
            ExpressiveConnectedToggleRow(
                options = routineOptions.map { it.second },
                selectedIndex = routineOptions.indexOfFirst { it.first == routine }.coerceAtLeast(0),
                onSelect = { idx -> routine = routineOptions[idx].first },
            )

            // Weeks picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "上课周数（已选 ${selectedWeeks.size} 周）",
                    style = MaterialTheme.typography.titleSmallEmphasized,
                )
                TextButton(onClick = { showExtraWeeks = !showExtraWeeks }) {
                    Text(if (showExtraWeeks) "收起 21–25周" else "扩展 21–25周")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val quickPresets = listOf(
                    "第${initialWeek}周" to setOf(initialWeek.coerceIn(1, 25)),
                    "1–16周" to (1..16).toSet(),
                    "全选 1–20" to (1..20).toSet(),
                    "单周" to (1..20).filter { it % 2 == 1 }.toSet(),
                    "双周" to (1..20).filter { it % 2 == 0 }.toSet(),
                    "清空" to emptySet(),
                )
                quickPresets.forEach { (label, preset) ->
                    OutlinedButton(
                        onClick = {
                            selectedWeeks = preset
                            validationError = null
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val weekCount = if (showExtraWeeks) 25 else 20
            val rowsNeeded = (weekCount + 4) / 5
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in 0 until rowsNeeded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (c in 0 until 5) {
                            val w = r * 5 + c + 1
                            if (w <= weekCount) {
                                val chosen = w in selectedWeeks
                                FilterChip(
                                    selected = chosen,
                                    onClick = {
                                        selectedWeeks = if (chosen) selectedWeeks - w else selectedWeeks + w
                                        validationError = null
                                    },
                                    label = {
                                        Text(
                                            text = "$w",
                                            style = MaterialTheme.typography.labelMedium.tabular(),
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (initialCourse != null && onDelete != null) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删除课程")
                    }
                }
                Button(
                    onClick = {
                        val cleanTitle = title.trim()
                        when {
                            cleanTitle.isBlank() -> validationError = "请填写课程名称"
                            selectedWeeks.isEmpty() -> validationError = "请至少选择一周上课周数"
                            else -> onConfirm(
                                Course(
                                    id = initialCourse?.id ?: "${CustomCourseCodec.ID_PREFIX}${java.util.UUID.randomUUID()}",
                                    title = cleanTitle,
                                    place = combinedPlace,
                                    teacher = teacher.trim(),
                                    weekday = weekday,
                                    first = firstSection,
                                    last = lastSection.coerceAtLeast(firstSection),
                                    weeks = selectedWeeks.toSortedSet(),
                                    routine = routine,
                                ),
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (initialCourse == null) "添加到课表" else "保存修改")
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun SectionStepSelector(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("第 $value 节", style = MaterialTheme.typography.titleSmallEmphasized.tabular())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { if (value > range.first) onChange(value - 1) },
                    enabled = value > range.first,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                ) { Text("-") }
                OutlinedButton(
                    onClick = { if (value < range.last) onChange(value + 1) },
                    enabled = value < range.last,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                ) { Text("+") }
            }
        }
    }
}

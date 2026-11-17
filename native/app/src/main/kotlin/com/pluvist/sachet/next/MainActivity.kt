@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
    ExperimentalFoundationApi::class,
)

package com.pluvist.sachet.next

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

@Immutable
private data class PrimaryTab(
    val key: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

@Immutable
internal sealed interface DetailRoute {
    val routeKey: String

    @Immutable
    data object CourseStatus : DetailRoute {
        override val routeKey: String = "status"
    }

    @Immutable
    data class CourseDay(val date: LocalDate) : DetailRoute {
        override val routeKey: String = "day:$date"
    }

    @Immutable
    data class CourseLesson(val course: Course) : DetailRoute {
        override val routeKey: String = "lesson:${course.id}:${course.weekday}:${course.first}"
    }

    @Immutable
    data class Query(val featureKey: String) : DetailRoute {
        override val routeKey: String = "query:$featureKey"
    }

    @Immutable
    data object Transcript : DetailRoute {
        override val routeKey: String = "transcript"
    }

    @Immutable
    data object Login : DetailRoute {
        override val routeKey: String = "login"
    }

    @Immutable
    data object WebLogin : DetailRoute {
        override val routeKey: String = "webLogin"
    }

    @Immutable
    data object Permissions : DetailRoute {
        override val routeKey: String = "permissions"
    }

    @Immutable
    data object Settings : DetailRoute {
        override val routeKey: String = "settings"
    }

    @Immutable
    data object Appearance : DetailRoute {
        override val routeKey: String = "appearance"
    }

    @Immutable
    data object Files : DetailRoute {
        override val routeKey: String = "files"
    }

    @Immutable
    data object FileManager : DetailRoute {
        override val routeKey: String = "fileManager"
    }

    @Immutable
    data object About : DetailRoute {
        override val routeKey: String = "about"
    }

    @Immutable
    data object Profile : DetailRoute {
        override val routeKey: String = "profile"
    }
}

internal fun routeFromDestination(destination: String): DetailRoute? = when (destination) {
    "profile" -> DetailRoute.Profile
    "login" -> DetailRoute.Login
    "webLogin" -> DetailRoute.WebLogin
    "permissions" -> DetailRoute.Permissions
    "settings" -> DetailRoute.Settings
    "appearance" -> DetailRoute.Appearance
    "files" -> DetailRoute.Files
    "fileManager" -> DetailRoute.FileManager
    "about" -> DetailRoute.About
    "transcript" -> DetailRoute.Transcript
    else -> QueryFeature.all.firstOrNull { it.key == destination }?.let { DetailRoute.Query(it.key) }
}

private val DetailStackSaver: Saver<List<DetailRoute>, ArrayList<String>> = Saver(
    save = { stack ->
        ArrayList<String>(stack.size).apply {
            stack.forEach { route ->
                val encoded = when (route) {
                    DetailRoute.CourseStatus -> "status"
                    is DetailRoute.CourseDay -> "day|${route.date}"
                    is DetailRoute.CourseLesson -> JSONObject().apply {
                        put("t", "lesson")
                        put("id", route.course.id)
                        put("title", route.course.title)
                        put("place", route.course.place)
                        put("teacher", route.course.teacher)
                        put("weekday", route.course.weekday)
                        put("first", route.course.first)
                        put("last", route.course.last)
                        put("weeks", JSONArray(route.course.weeks.sorted()))
                        put("routine", route.course.routine)
                    }.toString()

                    is DetailRoute.Query -> "query|${route.featureKey}"
                    DetailRoute.Transcript -> "transcript"
                    DetailRoute.Login -> "login"
                    DetailRoute.WebLogin -> "webLogin"
                    DetailRoute.Permissions -> "permissions"
                    DetailRoute.Settings -> "settings"
                    DetailRoute.Appearance -> "appearance"
                    DetailRoute.Files -> "files"
                    DetailRoute.FileManager -> "fileManager"
                    DetailRoute.About -> "about"
                    DetailRoute.Profile -> "profile"
                }
                add(encoded)
            }
        }
    },
    restore = { list ->
        list.mapNotNull { encoded ->
            when {
                encoded == "status" -> DetailRoute.CourseStatus
                encoded.startsWith("day|") -> runCatching {
                    DetailRoute.CourseDay(LocalDate.parse(encoded.removePrefix("day|")))
                }.getOrNull()

                encoded.startsWith("query|") -> DetailRoute.Query(encoded.removePrefix("query|"))
                encoded.startsWith("{") -> runCatching {
                    val obj = JSONObject(encoded)
                    val arr = obj.getJSONArray("weeks")
                    val weeks = sortedSetOf<Int>().apply {
                        for (i in 0 until arr.length()) add(arr.getInt(i))
                    }
                    DetailRoute.CourseLesson(
                        Course(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            place = obj.getString("place"),
                            teacher = obj.getString("teacher"),
                            weekday = obj.getInt("weekday"),
                            first = obj.getInt("first"),
                            last = obj.getInt("last"),
                            weeks = weeks,
                            routine = obj.optString("routine", "auto"),
                        ),
                    )
                }.getOrNull()

                else -> routeFromDestination(encoded)
            }
        }
    },
)

class MainActivity : ComponentActivity() {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (BuildConfig.BUILD_TYPE == "benchmark" && event.actionMasked == MotionEvent.ACTION_DOWN) {
            val started = System.nanoTime()
            android.view.Choreographer.getInstance().postFrameCallback {
                android.util.Log.w("XachetInputFrame", "dispatch_to_callback_ns=${System.nanoTime() - started}")
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.BUILD_TYPE == "preview" && intent.getBooleanExtra("academic_probe", false)) {
            lifecycleScope.launch { AcademicDiagnostics.run(AcademicRepository(this@MainActivity)) }
        }
        setContent {
            val vm: SachetViewModel = viewModel()
            LifecycleResumeEffect(vm) {
                vm.onForeground()
                onPauseOrDispose {}
            }
            val benchmarkFixture = BuildConfig.APPLICATION_ID.endsWith(".benchmark") &&
                intent.getBooleanExtra("load_fixture", false)
            val benchmarkGuide = BuildConfig.APPLICATION_ID.endsWith(".benchmark") &&
                intent.getBooleanExtra("show_onboarding", false)
            LaunchedEffect(benchmarkFixture) { if (benchmarkFixture) vm.loadBenchmarkFixture() }

            val data by vm.state.collectAsStateWithLifecycle()
            val settings = remember(data.settingsSnapshot) { JSONObject(data.settingsSnapshot) }
            val dark = when (settings.optInt("themeMode")) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val colorScheme = remember(data.settingsSnapshot, dark) {
                resolveXachetColorScheme(
                    settings = settings,
                    darkTheme = dark,
                    context = this@MainActivity,
                )
            }
            val uiScale = remember(data.settingsSnapshot) { resolveXachetUiScale(settings) }
            val baseDensity = LocalDensity.current
            val scaledDensity = remember(baseDensity.density, baseDensity.fontScale, uiScale) {
                Density(density = baseDensity.density * uiScale, fontScale = baseDensity.fontScale)
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            CompositionLocalProvider(LocalDensity provides scaledDensity) {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    motionScheme = MotionScheme.expressive(),
                    shapes = XachetShapes,
                    typography = XachetTypography,
                ) {
                    var onboarding by rememberSaveable {
                        mutableStateOf<Boolean?>(
                            when {
                                benchmarkGuide -> true
                                benchmarkFixture -> false
                                else -> null
                            },
                        )
                    }
                    LaunchedEffect(data.initialized) {
                        if (onboarding == null && data.initialized) {
                            onboarding = !settings.optBoolean("hasReadDisclaimer")
                        }
                    }
                    ReportDrawnWhen { data.initialized && onboarding != null }
                    LaunchedEffect(data.initialized, onboarding) {
                        if (data.initialized && onboarding != null) {
                            withFrameNanos {}
                            vm.onFirstContentFrame()
                        }
                    }

                    val notifications = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) {
                        vm.onNotificationPermissionChanged()
                    }
                    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                    val notificationsEnabled = settings.optBoolean("isEnableCourseLiveActivity", true) ||
                        settings.optBoolean("isEnableCourseReminders", true)
                    LaunchedEffect(
                        data.initialized,
                        data.hasSchedule,
                        data.courses.isNotEmpty(),
                        notificationsEnabled,
                        onboarding,
                        lifecycle,
                    ) {
                        if (onboarding == false && !BuildConfig.APPLICATION_ID.endsWith(".benchmark")) {
                            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                                if (vm.shouldRequestNotificationPermission()) {
                                    runCatching {
                                        notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    vm.markNotificationPermissionRequested()
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        val rootMotionScheme = MaterialTheme.motionScheme
                        AnimatedContent(
                            targetState = onboarding,
                            transitionSpec = { ExpressiveMotion.sharedAxisTransform(rootMotionScheme) },
                            label = "OnboardingToAppTransition",
                        ) { currentOnboarding ->
                            when (currentOnboarding) {
                                null -> Box(Modifier.fillMaxSize())
                                true -> OnboardingScreen(
                                    data = data,
                                    vm = vm,
                                    onFinished = { onboarding = false },
                                )

                                false -> SachetApp(vm = vm, data = data)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SachetApp(vm: SachetViewModel, data: AppData) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val widthSizeClass = if (activity != null) {
        calculateWindowSizeClass(activity).widthSizeClass
    } else {
        WindowWidthSizeClass.Compact
    }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scope = rememberCoroutineScope()

    var selected by rememberSaveable { mutableStateOf("home") }
    if (selected !in PrimaryTabOrder) {
        selected = "home"
    }
    var scheduleMode by rememberSaveable { mutableIntStateOf(0) }

    val hero = remember { HeroTransitionController() }

    val openHero: (String, DetailRoute) -> Unit = remember(hero, scope) {
        { heroKey, route -> hero.openHero(heroKey, route, scope) }
    }

    val openSlide: (DetailRoute) -> Unit = remember(hero, scope) {
        { route -> hero.openSlideOrPush(route, scope) }
    }

    val popDetail: () -> Unit = remember(hero, scope) {
        {
            if (!hero.popForeground()) {
                hero.closeForeground(scope)
            }
        }
    }

    val closeRootDetail: () -> Unit = remember(hero, scope) {
        { hero.closeForeground(scope) }
    }

    val useNavigationRail = widthSizeClass != WindowWidthSizeClass.Compact || landscape

    LaunchedEffect(useNavigationRail) {
        if (!useNavigationRail && selected == "profile") {
            selected = "home"
        }
    }

    val navigate: (String) -> Unit = remember(hero, scope, useNavigationRail) {
        { destination ->
            when {
                destination == "home" || destination == "schedule" || destination == "tomato" ||
                    (destination == "profile" && useNavigationRail) -> {
                    selected = destination
                    if (hero.hasForegroundSession) closeRootDetail()
                }

                else -> routeFromDestination(destination)?.let { route ->
                    openSlide(route)
                }
            }
        }
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val semester = remember(data.courses, data.settingsSnapshot) { vm.repository.semester() }
    val initialStatus = remember(data.courses, semester) {
        CourseClock.status(data.courses, semester, Instant.now())
    }
    val status by produceState(initialStatus, data.courses, semester, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = Instant.now()
                val next = CourseClock.status(data.courses, semester, now)
                value = next
                val wake = listOfNotNull(next.current?.end, next.next?.start)
                    .minOrNull()
                    ?.let { Instant.now().until(it, java.time.temporal.ChronoUnit.MILLIS).coerceIn(1000L, 60_000L) }
                    ?: 60_000L
                delay(wake)
            }
        }
    }

    val bottomTabs = remember {
        listOf(
            PrimaryTab("home", "主页", Icons.Rounded.Home, Icons.Outlined.Home),
            PrimaryTab("schedule", "课程表", Icons.Rounded.CalendarViewWeek, Icons.Outlined.CalendarViewWeek),
        )
    }
    val railTabs = remember {
        bottomTabs +
            PrimaryTab("tomato", "专注", Icons.Rounded.Timer, Icons.Outlined.Timer) +
            PrimaryTab("profile", "我", Icons.Rounded.Person, Icons.Outlined.Person)
    }

    val motionScheme = MaterialTheme.motionScheme
    val onScheduleModeChange: (Int) -> Unit = remember { { scheduleMode = it } }

    CompositionLocalProvider(LocalHeroTransitionController provides hero) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val depth = hero.maxProgress().coerceIn(0f, 1f)
                    if (depth > 0.005f) {
                        drawRect(color = Color.Black.copy(alpha = 0.24f * depth))
                    }
                }
                .onGloballyPositioned { coords ->
                    hero.rootCoordinates = coords
                    hero.rootSize = coords.size
                },
        ) {
            // Apply GPU-isolated sink/rise depth transform to underlying tabs while overlays open/close
            // so reading hero progress never recomposes Scaffold/HomePage at 120Hz.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        hero.backgroundDensity = density
                        val u = hero.maxProgress()
                        if (u > 0.001f) {
                            val frame = computeBackgroundDepthFrame(u)
                            transformOrigin = TransformOrigin.Center
                            scaleX = frame.scale
                            scaleY = frame.scale
                            translationY = frame.offsetYDp * density
                            alpha = frame.alpha
                            val cornerPx = frame.cornerDp * density
                            if (cornerPx > 0.5f) {
                                clip = true
                                shape = hero.backgroundShapeForCornerPx(cornerPx)
                            }
                        }
                    }
                    .focusProperties { canFocus = !hero.hasForegroundSession },
            ) {
                if (useNavigationRail) {
                    val railInsets = WindowInsets.displayCutout
                        .union(WindowInsets.navigationBars)
                        .union(WindowInsets.statusBars)
                        .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
                    Row(modifier = Modifier.fillMaxSize()) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = railInsets,
                            header = if (landscape) {
                                {
                                    Text(
                                        text = "Xachet",
                                        style = XachetTypeTokens.BrandRail,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                                    )
                                }
                            } else {
                                null
                            },
                        ) {
                            Spacer(Modifier.weight(0.25f))
                            railTabs.forEach { tab ->
                                val isSelected = selected == tab.key
                                NavigationRailItem(
                                    selected = isSelected,
                                    onClick = { selected = tab.key },
                                    icon = {
                                        Icon(
                                            if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = { Text(tab.label) },
                                )
                            }
                            Spacer(Modifier.weight(0.75f))
                        }
                        val railContentBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            RetainedTabs(selected = selected) { destination ->
                                PrimaryTabScreen(
                                    destination = destination,
                                    isActive = selected == destination,
                                    data = data,
                                    vm = vm,
                                    status = status,
                                    semester = semester,
                                    contentPadding = PaddingValues(bottom = railContentBottom),
                                    showProfileButton = false,
                                    navigate = navigate,
                                    onOpenSlide = openSlide,
                                    onOpenHero = openHero,
                                    onScheduleModeChange = onScheduleModeChange,
                                )
                            }
                        }
                    }
                } else {
                    val layoutDirection = LocalLayoutDirection.current
                    val cutoutInsets = WindowInsets.displayCutout.asPaddingValues()
                    val navigationInsets = WindowInsets.navigationBars.asPaddingValues()
                    val wide = widthSizeClass != WindowWidthSizeClass.Compact
                    var showTomatoFabMenu by remember { mutableStateOf(false) }
                    val haptics = LocalHapticFeedback.current

                    LaunchedEffect(selected, hero.hasForegroundSession) {
                        if (hero.hasForegroundSession) {
                            showTomatoFabMenu = false
                        }
                    }
                    BackHandler(enabled = showTomatoFabMenu) {
                        showTomatoFabMenu = false
                    }

                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        bottomBar = {
                            val colorScheme = MaterialTheme.colorScheme
                            val isDark = colorScheme.surface.luminance() < 0.5f
                            val scrimColor = if (isDark) Color.Black else Color.White
                            val capsuleContainerColor = colorScheme.surfaceContainerLow
                            val capsuleContentColor = colorScheme.onSurfaceVariant
                            val capsuleStrokeColor = if (isDark) {
                                Color(0xFF474B54).copy(alpha = 0.78f)
                            } else {
                                Color(0xFFD0D5DD).copy(alpha = 0.90f)
                            }
                            val selectedAccentColor = if (isDark) {
                                lerp(colorScheme.primary, colorScheme.primaryContainer, 0.72f)
                            } else {
                                lerp(colorScheme.primary, Color(0xFF0A101A), 0.24f)
                            }
                            val selectedContentColor = if (selectedAccentColor.luminance() < 0.36f) {
                                Color.White
                            } else {
                                Color(0xFF101318)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = FloatingToolbarDefaults.ScreenOffset),
                            ) {
                                val bottomScrimBrush = remember(scrimColor) {
                                    Brush.verticalGradient(
                                        0.00f to scrimColor.copy(alpha = 0.00f),
                                        0.32f to scrimColor.copy(alpha = 0.40f),
                                        0.68f to scrimColor.copy(alpha = 0.82f),
                                        1.00f to scrimColor.copy(alpha = 0.96f),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(bottomScrimBrush),
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = cutoutInsets.calculateStartPadding(layoutDirection) + 16.dp,
                                            end = cutoutInsets.calculateEndPadding(layoutDirection) + 16.dp,
                                            bottom = navigationInsets.calculateBottomPadding() +
                                                FloatingToolbarDefaults.ScreenOffset,
                                        )
                                        .zIndex(1f),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    HorizontalFloatingToolbar(
                                        expanded = true,
                                        shape = CircleShape,
                                        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                                            toolbarContainerColor = capsuleContainerColor,
                                            toolbarContentColor = capsuleContentColor,
                                        ),
                                        modifier = Modifier.drawWithContent {
                                            drawContent()
                                            val strokePx = 0.75.dp.toPx()
                                            val half = strokePx / 2f
                                            val radiusPx = (size.height / 2f - half).coerceAtLeast(0f)
                                            drawRoundRect(
                                                color = capsuleStrokeColor,
                                                topLeft = Offset(half, half),
                                                size = Size(size.width - strokePx, size.height - strokePx),
                                                cornerRadius = CornerRadius(radiusPx, radiusPx),
                                                style = Stroke(width = strokePx),
                                            )
                                        },
                                    ) {
                                        bottomTabs.forEach { tab ->
                                            val isSelected = selected == tab.key
                                            TooltipBox(
                                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                                    TooltipAnchorPosition.Above,
                                                ),
                                                tooltip = { PlainTooltip { Text(tab.label) } },
                                                state = rememberTooltipState(),
                                            ) {
                                                ToggleButton(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        showTomatoFabMenu = false
                                                        if (!isSelected) {
                                                            selected = tab.key
                                                        }
                                                    },
                                                    colors = ToggleButtonDefaults.colors(
                                                        containerColor = Color.Transparent,
                                                        contentColor = capsuleContentColor,
                                                        checkedContainerColor = selectedAccentColor,
                                                        checkedContentColor = selectedContentColor,
                                                    ),
                                                    shapes = ToggleButtonShapes(
                                                        shape = CircleShape,
                                                        pressedShape = CircleShape,
                                                        checkedShape = CircleShape,
                                                    ),
                                                    modifier = Modifier.height(56.dp),
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                            contentDescription = tab.label,
                                                        )
                                                        AnimatedVisibility(
                                                            visible = isSelected || wide,
                                                            enter = expandHorizontally(motionScheme.defaultSpatialSpec()),
                                                            exit = shrinkHorizontally(motionScheme.defaultSpatialSpec()),
                                                        ) {
                                                            Text(
                                                                text = tab.label,
                                                                fontSize = 16.sp,
                                                                lineHeight = 24.sp,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                overflow = TextOverflow.Clip,
                                                                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    TomatoBottomCircleButton(
                                        controller = vm.focus,
                                        selected = selected == "tomato",
                                        menuExpanded = showTomatoFabMenu,
                                        containerColor = capsuleContainerColor,
                                        contentColor = capsuleContentColor,
                                        selectedContainerColor = selectedAccentColor,
                                        selectedContentColor = selectedContentColor,
                                        strokeColor = capsuleStrokeColor,
                                        onClick = {
                                            if (showTomatoFabMenu) {
                                                showTomatoFabMenu = false
                                            } else {
                                                selected = "tomato"
                                            }
                                        },
                                        onLongClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            showTomatoFabMenu = !showTomatoFabMenu
                                        },
                                    )
                                }
                            }
                        },
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            RetainedTabs(selected = selected) { destination ->
                                PrimaryTabScreen(
                                    destination = destination,
                                    isActive = selected == destination,
                                    data = data,
                                    vm = vm,
                                    status = status,
                                    semester = semester,
                                    contentPadding = innerPadding,
                                    showProfileButton = true,
                                    navigate = navigate,
                                    onOpenSlide = openSlide,
                                    onOpenHero = openHero,
                                    onScheduleModeChange = onScheduleModeChange,
                                )
                            }

                            TomatoExpressiveFabMenu(
                                visible = showTomatoFabMenu,
                                endPadding = cutoutInsets.calculateEndPadding(layoutDirection) + 16.dp,
                                bottomPadding = navigationInsets.calculateBottomPadding() +
                                    FloatingToolbarDefaults.ScreenOffset + 76.dp,
                                onDismiss = { showTomatoFabMenu = false },
                                onQuickStartPomodoro = {
                                    vm.focus.quickStartDefault(FocusTimerMode.Pomodoro)
                                    selected = "tomato"
                                    showTomatoFabMenu = false
                                },
                                onQuickStartLongTimer = {
                                    vm.focus.quickStartDefault(FocusTimerMode.LongTimer)
                                    selected = "tomato"
                                    showTomatoFabMenu = false
                                },
                            )
                        }
                    }
                }
            }

            HeroOverlayHost(
                hero = hero,
                data = data,
                vm = vm,
                status = status,
                semester = semester,
                motionScheme = motionScheme,
                onOpenSlide = openSlide,
                onPopDetail = popDetail,
                onCloseRoot = closeRootDetail,
            )
        }
    }
}

@Composable
private fun HeroOverlayHost(
    hero: HeroTransitionController,
    data: AppData,
    vm: SachetViewModel,
    status: CourseStatus,
    semester: LocalDate,
    motionScheme: MotionScheme,
    onOpenSlide: (DetailRoute) -> Unit,
    onPopDetail: () -> Unit,
    onCloseRoot: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var savedForegroundStack by rememberSaveable(stateSaver = DetailStackSaver) {
        mutableStateOf<List<DetailRoute>>(emptyList())
    }

    LaunchedEffect(Unit) {
        if (savedForegroundStack.isNotEmpty() && hero.sessions.isEmpty()) {
            hero.restoreStackImmediate(savedForegroundStack, scope)
        }
    }

    val foregroundSession = hero.foregroundSession
    val foregroundStackSize = foregroundSession?.stack?.size ?: 0
    val activeTopRouteKey = foregroundSession?.stack?.lastOrNull()?.routeKey

    LaunchedEffect(foregroundSession?.stack) {
        savedForegroundStack = foregroundSession?.stack ?: emptyList()
    }

    LaunchedEffect(activeTopRouteKey) {
        focus.clearFocus(force = true)
        keyboard?.hide()
    }

    val rootBackState = rememberPredictiveBackState(enabled = foregroundSession != null && foregroundStackSize == 1) {
        onCloseRoot()
    }
    val nestedBackState = rememberPredictiveBackState(enabled = foregroundSession != null && foregroundStackSize > 1) {
        onPopDetail()
    }

    if (hero.sessions.isEmpty()) return

    val centeredSession = foregroundSession?.takeIf {
        it.stack.size == 1 && it.stack.lastOrNull() is DetailRoute.CourseLesson
    }
    val foregroundIsCentered = centeredSession != null
    val centeredState = rememberUpdatedState(foregroundIsCentered)
    val scrimColor = MaterialTheme.colorScheme.scrim

    Box(modifier = Modifier.fillMaxSize()) {
        // Combined backdrop dimming + background touch/accessibility shield on a single layer node.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics {}
                .graphicsLayer {
                    val u = hero.foregroundProgress()
                    val backP = rootBackState.progress.value
                    val targetMax = if (centeredState.value) 0.52f else 0.42f
                    alpha = ((u / 0.52f).coerceIn(0f, 1f) * targetMax * (1f - 0.55f * backP))
                        .coerceIn(0f, 0.56f)
                }
                .background(scrimColor)
                .pointerInput(hero) {
                    awaitPointerEventScope {
                        var downPos: Offset? = null
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (!hero.hasForegroundSession) {
                                downPos = null
                                continue
                            }
                            val first = event.changes.firstOrNull()
                            if (first != null) {
                                if (first.pressed && !first.previousPressed) {
                                    downPos = if (first.isConsumed) null else first.position
                                } else if (!first.pressed && first.previousPressed && downPos != null) {
                                    if (centeredState.value &&
                                        !first.isConsumed &&
                                        (first.position - downPos).getDistance() < viewConfiguration.touchSlop
                                    ) {
                                        onCloseRoot()
                                    }
                                    downPos = null
                                }
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        )

        hero.sessions.forEachIndexed { idx, session ->
            androidx.compose.runtime.key(session.id) {
                val sessionTopRoute = session.stack.lastOrNull()
                if (sessionTopRoute != null) {
                    val sessionCaptureLayer = rememberGraphicsLayer()
                    remember(sessionCaptureLayer) {
                        session.captureLayer = sessionCaptureLayer
                        true
                    }
                    val isCenteredSession = session.stack.size == 1 && sessionTopRoute is DetailRoute.CourseLesson
                    val isCurrentForeground = session === foregroundSession
                    val activeRootBack = if (isCurrentForeground && session.stack.size == 1) {
                        rootBackState
                    } else {
                        remember { PredictiveBackVisualState() }
                    }
                    val targetBg = if (isCenteredSession) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                    val targetCornerDp = if (isCenteredSession) {
                        CENTERED_CARD_CORNER_DP
                    } else {
                        OVERLAY_TARGET_PAGE_CORNER_DP
                    }

                    OverlaySessionHost(
                        controller = hero,
                        session = session,
                        predictiveBack = activeRootBack,
                        isCenteredCard = isCenteredSession,
                        targetContainerColor = targetBg,
                        targetCornerDp = targetCornerDp,
                        modifier = Modifier.zIndex(if (session.isClosing) idx * 0.01f else 10f + idx * 0.01f),
                        matrixFlyOutLayer = { srcRect, progressProvider ->
                            HomeMatrixFlyOutOverlay(
                                data = data,
                                status = status,
                                srcRect = srcRect,
                                progressProvider = progressProvider,
                            )
                        },
                    ) { contentModifier, targetTrackerModifier ->
                        if (isCenteredSession) {
                            val liveCourse = remember(data.courses, sessionTopRoute.course) {
                                data.courses.firstOrNull {
                                    it.id == sessionTopRoute.course.id &&
                                        it.weekday == sessionTopRoute.course.weekday &&
                                        it.first == sessionTopRoute.course.first
                                } ?: sessionTopRoute.course
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(modifier = targetTrackerModifier) {
                                    CourseLessonCenteredCard(
                                        course = liveCourse,
                                        allCourses = data.courses,
                                        onRoutineChange = { routine -> vm.setRoutine(liveCourse, routine) },
                                        onUpdateCustomCourse = { updated -> vm.saveCustomCourse(updated) },
                                        onDeleteCustomCourse = {
                                            vm.deleteCustomCourse(liveCourse.id)
                                            if (!session.isClosing) onCloseRoot()
                                        },
                                        onBack = { if (!session.isClosing) onCloseRoot() },
                                        modifier = contentModifier,
                                        isClosing = session.isClosing,
                                    )
                                }
                            }
                        } else {
                            val supportsNestedStack = session.stack.size > 1 ||
                                sessionTopRoute is DetailRoute.Profile ||
                                sessionTopRoute is DetailRoute.Login
                            Box(modifier = contentModifier.fillMaxSize()) {
                                if (supportsNestedStack) {
                                    AnimatedContent(
                                        targetState = session.stack,
                                        transitionSpec = {
                                            ExpressiveMotion.directionalSlideTransform(
                                                motionScheme = motionScheme,
                                                forward = targetState.size >= initialState.size,
                                            )
                                        },
                                        contentKey = { stack -> stack.lastOrNull()?.routeKey ?: "empty" },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (isCurrentForeground && session.stack.size > 1) {
                                                    Modifier.predictiveBackContainer(nestedBackState)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                        label = "DetailStackTransition-${session.id}",
                                    ) { stack ->
                                        val top = stack.lastOrNull()
                                        if (top != null) {
                                            val handleBack: () -> Unit = {
                                                if (!session.isClosing) {
                                                    if (stack.size > 1) onPopDetail() else onCloseRoot()
                                                }
                                            }
                                            DetailRouteHost(
                                                route = top,
                                                data = data,
                                                vm = vm,
                                                status = status,
                                                semester = semester,
                                                onBack = handleBack,
                                                onPush = { nextRoute -> onOpenSlide(nextRoute) },
                                            )
                                        }
                                    }
                                } else {
                                    val handleBack = remember(session, onCloseRoot) {
                                        {
                                            if (!session.isClosing) onCloseRoot()
                                        }
                                    }
                                    DetailRouteHost(
                                        route = sessionTopRoute,
                                        data = data,
                                        vm = vm,
                                        status = status,
                                        semester = semester,
                                        onBack = handleBack,
                                        onPush = onOpenSlide,
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

@Composable
private fun PrimaryTabScreen(
    destination: String,
    isActive: Boolean,
    data: AppData,
    vm: SachetViewModel,
    status: CourseStatus,
    semester: LocalDate,
    contentPadding: PaddingValues,
    showProfileButton: Boolean = true,
    navigate: (String) -> Unit,
    onOpenSlide: (DetailRoute) -> Unit,
    onOpenHero: (String, DetailRoute) -> Unit,
    onScheduleModeChange: (Int) -> Unit,
) {
    when (destination) {
        "home" -> HomePage(
            data = data,
            status = status,
            contentPadding = contentPadding,
            showProfileButton = showProfileButton,
            onOpenHero = onOpenHero,
        )

        "schedule" -> SchedulePage(
            courses = data.courses,
            semester = semester,
            status = status,
            appearanceSnapshot = data.appearanceSnapshot,
            colors = data.colors,
            uiScalePercent = remember(data.settingsSnapshot) {
                resolveXachetUiScalePercent(
                    runCatching { JSONObject(data.settingsSnapshot) }.getOrElse { JSONObject() },
                )
            },
            isActive = isActive,
            refreshing = data.loading && data.reloadFromClick,
            refreshRevision = data.reloadRevision,
            contentPadding = contentPadding,
            onModeChange = onScheduleModeChange,
            onOpenStatus = { heroKey -> onOpenHero(heroKey, DetailRoute.CourseStatus) },
            onOpenLesson = { course, heroKey -> onOpenHero(heroKey, DetailRoute.CourseLesson(course)) },
            onOpenDay = { date, heroKey -> onOpenHero(heroKey, DetailRoute.CourseDay(date)) },
            onRoutineChange = { course, routine -> vm.setRoutine(course, routine) },
            onScaleCommit = { vm.setCardHeight(it) },
            onRefresh = { vm.refresh(fromClick = true) },
            onAddCustomCourse = { course -> vm.saveCustomCourse(course) },
        )

        "tomato" -> {
            val focusState by vm.focus.state.collectAsStateWithLifecycle()
            TomatoFocusPage(
                state = focusState,
                status = status,
                contentPadding = contentPadding,
                onStart = { vm.focus.start() },
                onPause = { vm.focus.pause() },
                onReset = { vm.focus.reset() },
                onFinishOrSkip = { vm.focus.finishOrSkip() },
                onModeSelect = { vm.focus.setMode(it) },
                onUpdateSettings = { vm.focus.updateSettings(it) },
                onOpenClassrooms = { onOpenHero("tomato-classrooms", DetailRoute.Query("classrooms")) },
                onOpenCourseStatus = { onOpenHero("tomato-course-status", DetailRoute.CourseStatus) },
                onDeleteRecord = { vm.focus.deleteRecord(it) },
                onDismissFeedback = { vm.focus.dismissFeedback() },
            )
        }

        "profile" -> ProfilePage(
            data = data,
            vm = vm,
            contentPadding = contentPadding,
            navigate = navigate,
            onBack = null,
        )
    }
}

@Composable
private fun DetailRouteHost(
    route: DetailRoute,
    data: AppData,
    vm: SachetViewModel,
    status: CourseStatus,
    semester: LocalDate,
    onBack: () -> Unit,
    onPush: (DetailRoute) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        when (route) {
            DetailRoute.CourseStatus,
            is DetailRoute.CourseDay,
            is DetailRoute.CourseLesson -> CourseDetailPageHost(
                rootMode = route,
                status = status,
                allCourses = data.courses,
                semester = semester,
                onRoutineChange = { course, routine -> vm.setRoutine(course, routine) },
                onUpdateCustomCourse = { updated -> vm.saveCustomCourse(updated) },
                onDeleteCustomCourse = { courseId -> vm.deleteCustomCourse(courseId) },
                onBack = onBack,
            )

            DetailRoute.Profile -> ProfilePage(
                data = data,
                vm = vm,
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                navigate = { dest -> routeFromDestination(dest)?.let(onPush) },
                onBack = onBack,
            )

            DetailRoute.Login -> LoginPage(
                data = data,
                vm = vm,
                onWebLogin = { onPush(DetailRoute.WebLogin) },
                onBack = onBack,
            )

            DetailRoute.WebLogin -> WebLoginPage(vm = vm, onBack = onBack)
            DetailRoute.Permissions -> PermissionPage(onBack = onBack)
            DetailRoute.Settings -> SettingsPage(vm = vm, onBack = onBack)
            DetailRoute.Appearance -> AppearancePage(vm = vm, onBack = onBack)
            DetailRoute.Files -> DataPage(data = data, vm = vm, onBack = onBack)
            DetailRoute.FileManager -> FileManagerPage(vm = vm, onBack = onBack)
            DetailRoute.About -> AboutPage(onBack = onBack)
            DetailRoute.Transcript -> TranscriptPage(repository = vm.repository, onBack = onBack)
            is DetailRoute.Query -> {
                QueryFeature.all.firstOrNull { it.key == route.featureKey }?.let { feature ->
                    QueryPage(feature = feature, repository = vm.repository, onBack = onBack)
                }
            }
        }
    }
}

@Composable
private fun TomatoBottomCircleButton(
    controller: FocusTimerController,
    selected: Boolean,
    menuExpanded: Boolean,
    containerColor: Color,
    contentColor: Color,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    strokeColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val focusState by controller.state.collectAsStateWithLifecycle()
    val highlighted = selected || menuExpanded
    val ringColor = if (highlighted) selectedContentColor else MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = CircleShape,
        color = if (highlighted) selectedContainerColor else containerColor,
        contentColor = if (highlighted) selectedContentColor else contentColor,
        border = BorderStroke(0.75.dp, strokeColor),
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (focusState.hasActiveProgress && !menuExpanded) {
                Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                    val strokePx = 2.5.dp.toPx()
                    val inset = strokePx * 0.5f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    drawArc(
                        color = ringColor.copy(alpha = 0.22f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * focusState.dialProgress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }
            AnimatedContent(
                targetState = menuExpanded,
                label = "TomatoBottomIconMorph",
            ) { expanded ->
                Icon(
                    imageVector = when {
                        expanded -> Icons.Rounded.Close
                        selected -> Icons.Rounded.Timer
                        else -> Icons.Outlined.Timer
                    },
                    contentDescription = if (expanded) "关闭快捷菜单" else "专注计时（长按快捷开启）",
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun TomatoExpressiveFabMenu(
    visible: Boolean,
    endPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onQuickStartPomodoro: () -> Unit,
    onQuickStartLongTimer: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motionScheme.fastEffectsSpec()),
            exit = fadeOut(motionScheme.fastEffectsSpec()),
            label = "TomatoFabMenuScrim",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motionScheme.fastEffectsSpec()) +
                scaleIn(
                    initialScale = 0.82f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = motionScheme.fastSpatialSpec(),
                ) +
                slideInVertically(animationSpec = motionScheme.fastSpatialSpec()) { it / 3 },
            exit = fadeOut(motionScheme.fastEffectsSpec()) +
                scaleOut(
                    targetScale = 0.84f,
                    transformOrigin = TransformOrigin(1f, 1f),
                    animationSpec = motionScheme.fastEffectsSpec(),
                ) +
                slideOutVertically(animationSpec = motionScheme.fastEffectsSpec()) { it / 4 },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endPadding, bottom = bottomPadding),
            label = "TomatoFabMenuItems",
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TomatoFabMenuItemPill(
                    icon = Icons.Rounded.Schedule,
                    title = "开启长计时",
                    subtitle = "默认正向累计计时",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onQuickStartLongTimer,
                )
                TomatoFabMenuItemPill(
                    icon = Icons.Rounded.Timer,
                    title = "开启番茄钟",
                    subtitle = "默认 25 分钟专注",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onQuickStartPomodoro,
                )
            }
        }
    }
}

@Composable
private fun TomatoFabMenuItemPill(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.14f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private val PrimaryTabOrder = mapOf("home" to 0, "schedule" to 1, "tomato" to 2, "profile" to 3)

@Composable
private fun RetainedTabs(
    selected: String,
    content: @Composable (String) -> Unit,
) {
    val mounted = remember { mutableStateListOf(selected) }
    if (selected !in mounted) mounted.add(selected)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500L)
        if ("schedule" !in mounted) {
            mounted.add("schedule")
        }
    }
    var lastSelected by remember { mutableStateOf(selected) }
    var outgoingTab by remember { mutableStateOf<String?>(null) }
    if (selected != lastSelected) {
        outgoingTab = lastSelected
        lastSelected = selected
    }
    val activeIndex = PrimaryTabOrder[selected] ?: 0
    val selectedState = rememberUpdatedState(selected)
    val outgoingState = rememberUpdatedState(outgoingTab)
    val spatialSpec = ExpressiveMotion.spatialFast<Float>()
    val effectsSpec = ExpressiveMotion.effectsFast<Float>()
    val alphaStates = remember { HashMap<String, State<Float>>(4) }

    Box(modifier = Modifier.fillMaxSize()) {
        mounted.forEach { destination ->
            androidx.compose.runtime.key(destination) {
                val active = destination == selected
                val isOutgoing = !active && destination == outgoingTab
                val activeState = rememberUpdatedState(active)
                val tabIndex = PrimaryTabOrder[destination] ?: 0
                val targetSlot = (tabIndex - activeIndex).coerceIn(-1, 1).toFloat()

                val alphaState = animateFloatAsState(
                    targetValue = if (active) 1f else 0f,
                    animationSpec = effectsSpec,
                    label = "TabAlpha-$destination",
                )
                alphaStates[destination] = alphaState
                val scaleState = animateFloatAsState(
                    targetValue = if (active) 1f else 0.96f,
                    animationSpec = spatialSpec,
                    label = "TabScale-$destination",
                )
                val slideSlotState = animateFloatAsState(
                    targetValue = targetSlot,
                    animationSpec = spatialSpec,
                    label = "TabSlide-$destination",
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(
                            when {
                                active -> 1f
                                isOutgoing -> 0.5f
                                else -> 0f
                            },
                        )
                        .graphicsLayer {
                            val a = alphaState.value
                            val incomingAlpha = alphaStates[selectedState.value]?.value ?: 1f
                            // Only blend one offscreen alpha layer at a time (the incoming active tab)
                            // and keep strictly the outgoing tab behind it so a third mounted tab never flashes.
                            alpha = when {
                                activeState.value -> a
                                destination == outgoingState.value && incomingAlpha < 0.995f -> 1f
                                else -> 0f
                            }
                            val s = scaleState.value
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin.Center
                            translationX = slideSlotState.value * size.width * 0.08f
                        }
                        .focusProperties { canFocus = activeState.value }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (!activeState.value) {
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .then(if (!active) Modifier.clearAndSetSemantics {} else Modifier),
                ) {
                    content(destination)
                }
            }
        }
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Immutable
internal data class HomeFeature(val key: String, val title: String, val url: String? = null)

private val campusNetworkFeature = HomeFeature("network", "校园网认证", "http://zz.xtu.edu.cn/portal/portal-main")

private val gradesFeature = HomeFeature("grades", "成绩查询")
private val examsFeature = HomeFeature("exams", "考试时间")
private val gpaFeature = HomeFeature("gpa", "绩点排名")
private val cultivationFeature = HomeFeature("cultivation", "培养方案")
private val transcriptFeature = HomeFeature("transcript", "成绩单")
private val textbooksFeature = HomeFeature("textbooks", "教材预订")
private val libraryFeature = HomeFeature("library", "馆藏检索", "https://mfindxtu.libsp.cn/#/home")
private val physicsFeature = HomeFeature("physics", "大物实验", "https://wlsy.xtu.edu.cn")

private val quickDockFeatures = listOf(
    campusNetworkFeature,
    gradesFeature,
    examsFeature,
    gpaFeature,
)

private val compactWorkspaceFeatures = listOf(
    textbooksFeature,
    libraryFeature,
    physicsFeature,
)

private val campusLinks = listOf(
    HomeFeature("calendar", "湘大校历", "https://www.xtu.edu.cn/xysh/ggfw/xl.htm"),
    HomeFeature("news", "湘大新闻", "https://www.xtu.edu.cn/"),
    HomeFeature("mail", "湘大邮箱", "https://mail.smail.xtu.edu.cn/"),
    HomeFeature("devices", "校园网自助服务系统", "https://zz.xtu.edu.cn"),
    HomeFeature("portal", "信息门户", "https://portal2020.xtu.edu.cn/cas/login"),
    HomeFeature("website", "新教务系统", "https://jw.xtu.edu.cn/jwglxt/"),
    HomeFeature("portal", "旧教务系统", "https://jwxt.xtu.edu.cn/jsxsd/"),
)

@Composable
internal fun HomePage(
    data: AppData,
    status: CourseStatus,
    contentPadding: PaddingValues,
    showProfileButton: Boolean = true,
    onOpenHero: (String, DetailRoute) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val todayText = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE")) }
    val primaryPortals = remember { listOf(5 to campusLinks[5], 4 to campusLinks[4]) }
    val secondaryPortals = remember {
        listOf(0, 2, 1, 6, 3).map { idx -> idx to campusLinks[idx] }
    }
    val statusBarTopPx = WindowInsets.statusBars.getTop(density).toFloat()
    val compactBarPx = with(density) { 64.dp.toPx() }
    val collapseTravelPx = with(density) { if (landscape) 0f else 90.dp.toPx() }

    SideEffect {
        if (scrollBehavior.state.heightOffsetLimit != -collapseTravelPx) {
            scrollBehavior.state.heightOffsetLimit = -collapseTravelPx
        }
    }

    fun openFeature(feature: HomeFeature, heroKey: String) {
        val url = feature.url
        if (url != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        } else {
            val targetRoute = routeFromDestination(feature.key) ?: DetailRoute.Query(feature.key)
            onOpenHero(heroKey, targetRoute)
        }
    }

    val listContentPadding = remember(
        scrollBehavior,
        statusBarTopPx,
        compactBarPx,
        collapseTravelPx,
        contentPadding,
        density,
        landscape,
    ) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 16.dp
            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 16.dp
            override fun calculateTopPadding(): Dp = with(density) {
                if (landscape) {
                    maxOf(statusBarTopPx.toDp(), 28.dp) + 14.dp
                } else {
                    val expandedTotalPx = statusBarTopPx + compactBarPx + collapseTravelPx
                    (expandedTotalPx + scrollBehavior.state.heightOffset)
                        .coerceAtLeast(statusBarTopPx + compactBarPx)
                        .toDp() + 16.dp
                }
            }

            override fun calculateBottomPadding(): Dp = contentPadding.calculateBottomPadding() + 28.dp
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (!landscape) {
                    Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                } else {
                    Modifier
                },
            ),
    ) {
        val viewportHeight = maxHeight

        if (landscape) {
            val statusTopPad = with(density) { maxOf(statusBarTopPx.toDp(), 24.dp) }
            val landscapeBentoHeight = (viewportHeight - statusTopPad - 28.dp).coerceIn(244.dp, 360.dp)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.02f)
                        .fillMaxHeight()
                        .padding(top = statusTopPad + 12.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeCourseBentoSection(
                        data = data,
                        status = status,
                        bentoHeight = landscapeBentoHeight,
                        onOpenStatus = { onOpenHero("home-bento-status", DetailRoute.CourseStatus) },
                        onOpenNextCourse = { course ->
                            onOpenHero("home-bento-next", DetailRoute.CourseLesson(course))
                        },
                        onOpenNextFallback = { onOpenHero("home-bento-next", DetailRoute.CourseStatus) },
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(0.98f)
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        top = statusTopPad + 12.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "quick_dock") {
                        HomeQuickLaunchDock(
                            features = quickDockFeatures,
                            onOpenFeature = ::openFeature,
                        )
                    }
                    item(key = "academic_workspace") {
                        HomeAcademicWorkspace(
                            spotlightLeft = cultivationFeature,
                            spotlightRight = transcriptFeature,
                            compactRow = compactWorkspaceFeatures,
                            onOpenFeature = ::openFeature,
                        )
                    }
                    item(key = "campus_hub") {
                        HomeCampusPortalHub(
                            primaryPortals = primaryPortals,
                            secondaryPortals = secondaryPortals,
                            onOpenLink = ::openFeature,
                        )
                    }
                }
            }
        } else {
            val bentoHeight = (viewportHeight * 0.485f).coerceIn(316.dp, 420.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "bento") {
                    HomeCourseBentoSection(
                        data = data,
                        status = status,
                        bentoHeight = bentoHeight,
                        onOpenStatus = { onOpenHero("home-bento-status", DetailRoute.CourseStatus) },
                        onOpenNextCourse = { course ->
                            onOpenHero("home-bento-next", DetailRoute.CourseLesson(course))
                        },
                        onOpenNextFallback = { onOpenHero("home-bento-next", DetailRoute.CourseStatus) },
                    )
                }

                item(key = "quick_dock") {
                    HomeQuickLaunchDock(
                        features = quickDockFeatures,
                        onOpenFeature = ::openFeature,
                    )
                }

                item(key = "academic_workspace") {
                    HomeAcademicWorkspace(
                        spotlightLeft = cultivationFeature,
                        spotlightRight = transcriptFeature,
                        compactRow = compactWorkspaceFeatures,
                        onOpenFeature = ::openFeature,
                    )
                }

                item(key = "campus_hub") {
                    HomeCampusPortalHub(
                        primaryPortals = primaryPortals,
                        secondaryPortals = secondaryPortals,
                        onOpenLink = ::openFeature,
                    )
                }
            }

            HomeCollapsingHeader(
                collapsedFraction = { scrollBehavior.state.collapsedFraction },
                todayText = todayText,
                statusBarTopPx = statusBarTopPx,
                compactBarPx = compactBarPx,
                collapseTravelPx = collapseTravelPx,
                showProfileButton = showProfileButton,
                onOpenProfile = { onOpenHero("home-profile", DetailRoute.Profile) },
            )
        }
    }
}

@Composable
private fun HomeCollapsingHeader(
    collapsedFraction: () -> Float,
    todayText: String,
    statusBarTopPx: Float,
    compactBarPx: Float,
    collapseTravelPx: Float,
    showProfileButton: Boolean,
    onOpenProfile: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val scrimColor = if (isDark) Color.Black else Color.White
    val scrimBrush = remember(scrimColor) {
        Brush.verticalGradient(
            0.00f to scrimColor.copy(alpha = 0.98f),
            0.56f to scrimColor.copy(alpha = 0.92f),
            0.78f to scrimColor.copy(alpha = 0.48f),
            1.00f to scrimColor.copy(alpha = 0.00f),
        )
    }

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val t = collapsedFraction().coerceIn(0f, 1f)
                if (t > 0.001f) {
                    drawRect(brush = scrimBrush, alpha = t)
                }
            },
        content = {
            Text(
                text = "Xachet",
                style = XachetTypeTokens.homeTopBarTitle(
                    collapsedFraction = 0f,
                    base = LocalTextStyle.current,
                ).copy(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                maxLines = 1,
            )
            Text(
                text = todayText,
                style = XachetTypeTokens.CondensedMeta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (showProfileButton) {
                FilledTonalIconButton(
                    onClick = onOpenProfile,
                    modifier = Modifier
                        .size(42.dp)
                        .heroFlightSource(
                            key = "home-profile",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            cornerDp = 21f,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "我",
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val t = collapsedFraction().coerceIn(0f, 1f)
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val titlePlaceable = measurables[0].measure(loose)
        val subtitlePlaceable = measurables[1].measure(loose)
        val profilePlaceable = measurables.getOrNull(2)?.measure(loose)

        val expandedHeightPx = statusBarTopPx + compactBarPx + collapseTravelPx
        val collapsedGradientHeightPx = statusBarTopPx + compactBarPx + 30.dp.toPx()
        val heightPx = (expandedHeightPx + (collapsedGradientHeightPx - expandedHeightPx) * t)
            .roundToInt()
            .coerceAtLeast(1)

        val targetScale = 24f / 44f
        val currentScale = 1f + (targetScale - 1f) * t

        val expandedX = 20.dp.toPx()
        val collapsedX = (constraints.maxWidth - titlePlaceable.width * targetScale) / 2f
        val titleX = expandedX + (collapsedX - expandedX) * t

        val expandedY = statusBarTopPx + 62.dp.toPx()
        val collapsedY = statusBarTopPx + (compactBarPx - titlePlaceable.height * targetScale) / 2f + 1.dp.toPx()
        val titleY = expandedY + (collapsedY - expandedY) * t

        val subX = expandedX
        val subY = expandedY + titlePlaceable.height + 4.dp.toPx() - 12.dp.toPx() * t
        val subAlpha = (1f - t * 2.2f).coerceIn(0f, 1f)

        layout(constraints.maxWidth, heightPx) {
            if (subAlpha > 0.01f) {
                subtitlePlaceable.placeRelativeWithLayer(0, 0) {
                    translationX = subX
                    translationY = subY
                    alpha = subAlpha
                }
            }
            titlePlaceable.placeRelativeWithLayer(0, 0) {
                translationX = titleX
                translationY = titleY
                scaleX = currentScale
                scaleY = currentScale
                transformOrigin = TransformOrigin(0f, 0f)
            }
            if (profilePlaceable != null) {
                val profileX = (constraints.maxWidth - 20.dp.toPx() - profilePlaceable.width).roundToInt()
                val profileY = (statusBarTopPx + (compactBarPx - profilePlaceable.height) / 2f).roundToInt()
                profilePlaceable.placeRelative(profileX, profileY)
            }
        }
    }
}

@Composable
internal fun ProfilePage(
    data: AppData,
    vm: SachetViewModel,
    contentPadding: PaddingValues,
    navigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirmLogout by remember { mutableStateOf(false) }

    if (confirmLogout) {
        ExpressiveConfirmDialog(
            title = "退出登录",
            body = "确定要退出当前教务账号并清除会话凭证吗？已缓存的课表将保留在本地。",
            confirmText = "退出登录",
            destructive = true,
            onConfirm = {
                confirmLogout = false
                vm.logout()
            },
            onDismiss = { confirmLogout = false },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("我") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { topBarPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(topBarPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 26.dp,
                bottom = contentPadding.calculateBottomPadding() + 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "profile_account") {
                Card(
                    onClick = { navigate("login") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = MaterialShapes.Cookie6Sided.toShape(),
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(30.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = data.account.name.ifBlank { "登录教务账号" },
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = data.account.studentID.ifBlank { "同步课程与教务信息" },
                                style = MaterialTheme.typography.bodyMedium.tabular(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            item(key = "profile_primary") {
                val primaryItems = remember {
                    listOf(
                        Triple("permissions", "后台运行与通知权限", null),
                        Triple("settings", "设置", null),
                        Triple("appearance", "课表外观", null),
                        Triple("files", "导入与导出课表", null),
                        Triple("fileManager", "数据文件", null),
                    )
                }
                ExpressiveSegmentedGroup {
                    primaryItems.forEachIndexed { index, (route, title, sub) ->
                        ExpressiveSegmentedActionRow(
                            index = index,
                            count = primaryItems.size,
                            title = title,
                            subtitle = sub,
                            leading = { AppIconBadge(route, size = 36.dp, iconSize = 18.dp) },
                            onClick = { navigate(route) },
                        )
                    }
                }
            }

            item(key = "profile_widgets") {
                val widgets = remember {
                    listOf(false to "添加桌面小组件", true to "添加横向小组件")
                }
                ExpressiveSegmentedGroup {
                    widgets.forEachIndexed { index, (wide, title) ->
                        ExpressiveSegmentedActionRow(
                            index = index,
                            count = widgets.size,
                            title = title,
                            subtitle = if (wide) "4×2 周概览与即将上课组件" else "2×2 轻量课表小组件",
                            leading = { AppIconBadge("widget", size = 36.dp, iconSize = 18.dp) },
                            onClick = {
                                val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
                                if (manager != null && runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)) {
                                    val component = if (wide) {
                                        CourseWidgetWideProvider::class.java
                                    } else {
                                        CourseWidgetProvider::class.java
                                    }
                                    runCatching {
                                        manager.requestPinAppWidget(ComponentName(context, component), null, null)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            item(key = "profile_about") {
                val aboutCount = if (data.account.cookie.isNotBlank()) 3 else 2
                ExpressiveSegmentedGroup {
                    ExpressiveSegmentedActionRow(
                        index = 0,
                        count = aboutCount,
                        title = "Xachet",
                        titleStyle = XachetTypeTokens.BrandRail,
                        subtitle = "作者 Pluvist · 基于 Sachet 二次修改",
                        leading = { AppIconBadge("about", size = 36.dp, iconSize = 18.dp) },
                        onClick = { navigate("about") },
                    )
                    ExpressiveSegmentedActionRow(
                        index = 1,
                        count = aboutCount,
                        title = "开源致谢",
                        subtitle = "Sachet · Material 3 Expressive",
                        leading = { AppIconBadge("source", size = 36.dp, iconSize = 18.dp) },
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pluvist/Xachet")),
                            )
                        },
                    )
                    if (data.account.cookie.isNotBlank()) {
                        ExpressiveSegmentedActionRow(
                            index = 2,
                            count = aboutCount,
                            title = "退出登录",
                            leading = { AppIconBadge("logout", size = 36.dp, iconSize = 18.dp) },
                            showChevron = false,
                            onClick = { confirmLogout = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LoginPage(
    data: AppData,
    vm: SachetViewModel,
    onWebLogin: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var studentId by rememberSaveable { mutableStateOf(data.account.studentID) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by rememberSaveable { mutableStateOf(true) }
    var cookieMode by rememberSaveable { mutableStateOf(false) }
    var cookie by remember { mutableStateOf("") }

    ExpressiveSubPageScaffold(
        title = "登录教务账号",
        subtitle = "加密保存凭据，用于课表与教务数据同步",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExpressiveMessageBanner(
                message = "账号数据使用本机密钥加密保存，仅用于访问学校教务系统。",
            )
            ExpressiveConnectedToggleRow(
                options = listOf("学号密码", "Cookie 登录"),
                selectedIndex = if (cookieMode) 1 else 0,
                onSelect = { cookieMode = (it == 1) },
            )
            val loginMotionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = cookieMode,
                transitionSpec = {
                    ExpressiveMotion.directionalSlideTransform(loginMotionScheme, targetState)
                },
                label = "LoginMethodTransition",
            ) { useCookie ->
                if (useCookie) {
                    OutlinedTextField(
                        value = cookie,
                        onValueChange = { cookie = it },
                        label = { Text("教务 Cookie") },
                        placeholder = { Text("粘贴完整 Cookie") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "切换显示",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = { studentId = it },
                            label = { Text("学号") },
                            placeholder = { Text("请输入学号") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            placeholder = { Text("默认密码为 Xtu@身份证后六位") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "切换显示",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                        )
                        ExpressiveSegmentedGroup {
                            ExpressiveSwitchRow(
                                index = 0,
                                count = 1,
                                title = "记住密码",
                                checked = rememberPassword,
                                onCheckedChange = { rememberPassword = it },
                            )
                        }
                    }
                }
            }
            data.message?.let {
                ExpressiveMessageBanner(message = it, isError = true, onDismiss = vm::dismissMessage)
            }
            Button(
                onClick = {
                    if (cookieMode) {
                        vm.cookieLogin(cookie, onBack)
                    } else {
                        vm.login(studentId.trim(), password, rememberPassword, onBack)
                    }
                },
                enabled = !data.loading && if (cookieMode) cookie.isNotBlank() else studentId.isNotBlank() && password.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                if (data.loading) {
                    XachetInlineBusyIndicator("正在登录")
                } else {
                    Text("登录并同步")
                }
            }
            if (onWebLogin != null) {
                OutlinedButton(
                    onClick = onWebLogin,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Web, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("通过教务网页登录")
                }
            }
        }
    }
}

@Composable
internal fun PermissionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val status by rememberBackgroundSetupStatus()
    val live = status["live"] as? Map<*, *>
    val reminders = status["reminders"] as? Map<*, *>
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) scope.launch { BackgroundSetupSettings.refreshCourses(context) }
    }

    val rows = listOf(
        PermissionRowItem(
            icon = Icons.Rounded.Notifications,
            title = "通知权限",
            statusText = when {
                status.isEmpty() -> "正在读取"
                status["notifications"] == true -> "已允许"
                else -> "未允许"
            },
            granted = status["notifications"] == true,
            onClick = {
                activity?.let { act ->
                    BackgroundSetupSettings.requestNotifications(act) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.Timelapse,
            title = "课程实时通知",
            statusText = when {
                status.isEmpty() -> "正在读取"
                live?.get("notifications") == true -> "已允许"
                else -> "未允许"
            },
            granted = live?.get("notifications") == true,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "liveNotifications") } },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.Timer,
            title = "实时活动提升显示",
            statusText = when {
                status.isEmpty() -> "正在读取"
                live?.get("supported") != true -> "使用持续课程通知"
                live["promoted"] == true -> "已允许"
                else -> "未允许"
            },
            granted = live?.get("promoted") == true,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "live") } },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.NotificationsActive,
            title = "课前提醒通知",
            statusText = when {
                status.isEmpty() -> "正在读取"
                reminders?.get("notifications") == true -> "已允许"
                else -> "未允许"
            },
            granted = reminders?.get("notifications") == true,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "reminders") } },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.Alarm,
            title = "准时提醒",
            statusText = when {
                status.isEmpty() -> "正在读取"
                live?.get("exactAlarms") == true -> "已允许"
                else -> "未允许，提醒可能延迟"
            },
            granted = live?.get("exactAlarms") == true,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "exactAlarms") } },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "后台运行",
            statusText = when {
                status.isEmpty() -> "正在读取"
                status["batteryUnrestricted"] == true -> "无限制"
                else -> "受系统管理"
            },
            granted = status["batteryUnrestricted"] == true,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "battery") } },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.RestartAlt,
            title = "自启动",
            statusText = "在系统页面确认",
            granted = false,
            onClick = { activity?.let { BackgroundSetupSettings.open(it, "autostart") } },
        ),
    )

    ExpressiveSubPageScaffold(
        title = "后台与通知",
        subtitle = "保障实时活动与课前准时提醒",
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
            ExpressiveSegmentedGroup {
                rows.forEachIndexed { index, row ->
                    SegmentedListItem(
                        onClick = row.onClick,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = rows.size),
                        colors = expressiveSegmentedColors(),
                        leadingContent = {
                            Icon(
                                imageVector = row.icon,
                                contentDescription = null,
                                tint = if (row.granted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        supportingContent = {
                            Text(
                                text = row.statusText,
                                color = if (row.granted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        content = {
                            Text(row.title, style = MaterialTheme.typography.titleSmallEmphasized)
                        },
                    )
                }
            }
            ExpressiveMessageBanner(
                message = "课前提醒：第 1、5 节提前 20 分钟，其他节次提前 10 分钟。准时提醒权限关闭时，系统可能延迟通知；可在设置中独立关闭课前提醒。",
            )
        }
    }
}

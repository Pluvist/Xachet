/* HyperCeiler provision layout adapted to Compose (AGPL-3.0).
 * Copyright (C) 2023-2026 HyperCeiler Contributions; 2026 Pluvist.
 * Sources and adaptation boundaries: third_party/hyperceiler/SOURCES.md. */
package com.pluvist.sachet.next

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.glass.render.backdrops.rememberCanvasBackdrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The application/login state is kept outside the animated pages. Going back,
 * rotating or interrupting a transition never creates a second login request. */
@Composable internal fun OnboardingPage(data: AppData, vm: SachetViewModel,
    programs: LiquidMenuShaderPrograms?, onComplete: (Rect) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var id by rememberSaveable { mutableStateOf(data.account.studentID) }
    var password by remember { mutableStateOf("") }
    var rememberPassword by rememberSaveable { mutableStateOf(true) }
    var cookieMode by rememberSaveable { mutableStateOf(false) }
    var cookie by remember { mutableStateOf("") }
    var webLogin by rememberSaveable { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = LocalGlassColors.current
    val dark = colors.isDark
    val background = if (dark) Color.Black else Color.White
    val reveal = remember { LiquidMenuMorphMotion() }
    val density = LocalDensity.current
    var revealing by remember { mutableStateOf(false) }
    val collapse = remember { Animatable(0f) }
    var collapsing by remember { mutableStateOf(false) }
    val collapseShader = rememberProvisionCollapseShader()
    var revealJob by remember { mutableStateOf<Job?>(null) }
    var arrowBounds by remember { mutableStateOf(Rect.Zero) }
    var rootBounds by remember { mutableStateOf(Rect.Zero) }
    var finishBounds by remember { mutableStateOf(Rect.Zero) }
    val showWelcome = page == 0 || revealing
    val showSteps = page > 0 || revealing
    val completionStarted by remember { derivedStateOf { page == 4 || (collapsing && collapse.value > .62f) } }
    val shader = rememberProvisionShader()
    val success = rememberProvisionSuccess()
    val status by rememberBackgroundSetupStatus(page == 3 && !webLogin)
    val live = status["live"] as? Map<*, *>
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { BackgroundSetupSettings.refreshCourses(context) }
    }
    fun next(target: Int) {
        focus.clearFocus(); keyboard?.hide()
        if (page == 3 && target == 4) {
            if (collapsing) return
            revealJob?.cancel()
            collapsing = true
            revealJob = scope.launch {
                collapse.animateTo(1f, tween(620, easing = CubicBezierEasing(.6f, 0f, .25f, 1f)))
                page = 4; collapsing = false
            }
            return
        }
        if (collapsing) { revealJob?.cancel(); collapsing = false }
        if (target == 0 || page == 0) {
            if (arrowBounds.isEmpty || rootBounds.isEmpty) {
                // A restored non-welcome page has no measured arrow yet. Returning
                // must still work after process recreation; layout can repopulate it.
                if (target == 0) { revealJob?.cancel(); revealing = false; page = 0 }
                return
            }
            revealJob?.cancel()
            if (!reveal.initialized) reveal.initialize(arrowBounds.translate(-rootBounds.topLeft),
                Rect(Offset.Zero, rootBounds.size), 0f, density.density)
            else { reveal.updateSource(arrowBounds.translate(-rootBounds.topLeft)); reveal.updateTarget(Rect(Offset.Zero, rootBounds.size), 0f) }
            page = target
            revealing = true
            revealJob = scope.launch {
                reveal.animateTo(target != 0)
                revealing = false
            }
        } else {
            scope.launch { collapse.snapTo(0f); page = target }
        }
    }
    fun finish() {
        if (finishing || collapsing) return
        finishing = true; finishError = null
        scope.launch {
            try {
                vm.saveSettings(mapOf("hasReadDisclaimer" to true))
                onComplete(finishBounds)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { finishError = "保存设置失败，请重试"; finishing = false }
        }
    }
    BackHandler(page > 0 && !webLogin && !finishing) { next(page - 1) }
    BackHandler(webLogin) { webLogin = false }
    if (webLogin) {
        WebLoginPage(vm, onAuthenticated = { webLogin = false; next(3) }) { webLogin = false }
        return
    }
    GlasmioBackdropHost(Modifier.fillMaxSize().onGloballyPositioned { rootBounds = it.boundsInRoot() },
        sampledContent = {
            Box(Modifier.fillMaxSize().background(background)) {
                if (showWelcome) ProvisionGlow(shader, active = !revealing && !finishing, dark = dark)
            }
        }, overlayContent = {
        if (showWelcome) ProvisionWelcome(active = page == 0, onStart = { next(1) },
            onArrowBounds = { arrowBounds = it }, modifier = Modifier.zIndex(if (page == 0) 1f else 0f)
                .graphicsLayer { alpha = if (revealing && reveal.initialized) reveal.frame.sourceOpacity else 1f }
                .then(if (page == 0) Modifier else Modifier.clearAndSetSemantics {}))
        // Keep the completed page at one stable call site while the permissions
        // layer contracts over it. Its vector starts before the last collapse frame.
        if (page == 4 || collapsing) GuidePageBackdrop(background) { ProvisionComplete(success, active = completionStarted,
            busy = finishing || collapsing, error = finishError, onBack = { next(3) },
            onComplete = { finish() }, onButtonBounds = { finishBounds = it }) }
        if (showSteps && page != 4) {
            ProvisionLiquidWindow(reveal, showSteps, revealing, programs, expanded = page > 0) {
                GuidePageBackdrop(background) {
                AnimatedContent(targetState = page.coerceAtLeast(1), modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        if (targetState == 4) fadeIn(tween(280)) togetherWith fadeOut(tween(60))
                        else (fadeIn(tween(240)) + slideInHorizontally(spring(.92f, 330f)) { direction * it / 9 }) togetherWith
                            (fadeOut(tween(180)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -direction * it / 12 })
                    }, label = "Provision steps") { step ->
                    val busy = finishing || collapsing || (step == 2 && data.loading)
                    val enabled = !busy && page == step && (step != 1 || agreed) &&
                        (step != 2 || if (cookieMode) cookie.isNotBlank() else id.isNotBlank() && password.isNotBlank())
                    val submit: () -> Unit = {
                        when (step) {
                            2 -> if (cookieMode) vm.cookieLogin(cookie) { if (page == 2) next(3) }
                                else vm.login(id.trim(), password, rememberPassword) { if (page == 2) next(3) }
                            else -> next(step + 1)
                        }
                    }
                    Box(Modifier.fillMaxSize().provisionCollapse(collapseShader, collapse, step == 3)) {
                    ProvisionStep(
                        title = when (step) { 1 -> "协议与声明"; 2 -> "登录账号"; else -> "权限与服务" },
                        icon = when (step) { 1 -> Icons.AutoMirrored.Outlined.Article; 2 -> Icons.Outlined.AccountCircle; else -> Icons.Outlined.VerifiedUser },
                        button = when (step) { 1 -> "同意并继续"; 2 -> "登录并继续"; else -> "继续" },
                        enabled = enabled, busy = busy, background = background,
                        onBack = { next(step - 1) }, onNext = submit) {
                        when (step) {
                            1 -> {
                                GuideGroup(
                                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    GuideParagraph("欢迎使用 Xachet。请阅读以下说明，了解账号与课程数据的使用方式。")
                                    GuideParagraph("账号及记住的密码通过本机密钥加密保存，仅用于登录学校教务系统。课表缓存在本机，用于离线显示、桌面小组件和课程通知。")
                                    GuideParagraph("Xachet 由 Pluvist 基于开源项目 Sachet 二次开发，感谢上游作者与开源贡献者。")
                                    GuideTextAction("查看开源项目与许可", true) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pluvist/Xachet")))
                                    }
                                }
                                GuideCheck("我已阅读并同意以上声明", agreed, { agreed = it })
                            }
                            2 -> {
                                GuideParagraph("使用湘潭大学教务账号，同步课程与教务信息。")
                                if (cookieMode) {
                                    GuideGroup(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("教务 Cookie", style = IosTypography.callout, color = colors.content)
                                        GlasmioTextField(cookie, { cookie = it; vm.dismissMessage() }, placeholder = "请输入教务 Cookie", visualTransformation = PasswordVisualTransformation())
                                    }
                                } else {
                                    GuideGroup(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("学号", style = IosTypography.callout, color = colors.content)
                                        GlasmioTextField(id, { id = it; vm.dismissMessage() }, placeholder = "请输入学号", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                    }
                                    GuideGroup(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("密码", style = IosTypography.callout, color = colors.content)
                                        GlasmioTextField(password, { password = it; vm.dismissMessage() }, placeholder = "默认密码为 Xtu@身份证后六位", visualTransformation = PasswordVisualTransformation())
                                    }
                                    GuideCheck("记住密码", rememberPassword, { rememberPassword = it })
                                }
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    GuideTextAction(if (cookieMode) "学号密码登录" else "Cookie 登录", !busy && page == step) { cookieMode = !cookieMode; vm.dismissMessage() }
                                    GuideTextAction("网页登录", !busy && page == step) { webLogin = true }
                                    GuideTextAction("稍后登录", !busy && page == step) { vm.dismissMessage(); next(3) }
                                }
                                data.message?.let { Text(it, color = colors.destructive, style = IosTypography.footnote) }
                            }
                            3 -> {
                                GuideParagraph("开启所需权限，让课前提醒和课程实时状态及时出现。你也可以稍后在设置中调整。")
                                GlasmioGroupedList {
                                    GuidePermission(Icons.Outlined.Notifications, "通知权限",
                                        if (status.isEmpty()) "正在读取" else if (status["notifications"] == true) "已开启" else "未开启", status["notifications"] == true) {
                                        BackgroundSetupSettings.requestNotifications(context as Activity) { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                    }
                                    GuidePermission(Icons.Outlined.Timer, "实时活动",
                                        if (status.isEmpty()) "正在读取" else if (live?.get("supported") != true) "使用持续课程通知" else if (live["promoted"] == true) "已开启" else "前往开启", live?.get("promoted") == true) {
                                        BackgroundSetupSettings.open(context as Activity, "live")
                                    }
                                    GuidePermission(Icons.Outlined.Alarm, "准时提醒",
                                        if (status.isEmpty()) "正在读取" else if (live?.get("exactAlarms") == true) "已开启" else "允许闹钟和提醒", live?.get("exactAlarms") == true) {
                                        BackgroundSetupSettings.open(context as Activity, "exactAlarms")
                                    }
                                    GuidePermission(Icons.Outlined.BatteryChargingFull, "后台运行",
                                        if (status.isEmpty()) "正在读取" else if (status["batteryUnrestricted"] == true) "已允许" else "允许无限制运行", status["batteryUnrestricted"] == true) {
                                        BackgroundSetupSettings.open(context as Activity, "battery")
                                    }
                                    GuidePermission(Icons.Outlined.RestartAlt, "自启动", "请在系统设置中确认", false) {
                                        BackgroundSetupSettings.open(context as Activity, "autostart")
                                    }
                                }
                                GuideParagraph("第 1、5 节课提前 20 分钟提醒，其他节次提前 10 分钟提醒。")
                                GlasmioGroupedList {
                                    GuidePermission(Icons.Outlined.Widgets, "桌面小组件",
                                        if ((status["widgetCount"] as? Int ?: 0) > 0) "已添加 · 点击添加更多" else "添加课程到桌面", (status["widgetCount"] as? Int ?: 0) > 0) {
                                        val manager = AppWidgetManager.getInstance(context)
                                        if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(context, CourseWidgetProvider::class.java), null, null)
                                    }
                                }
                                GuideParagraph("也可以长按桌面，在小组件列表选择 Xachet。自启动状态请以系统设置为准。")
                            }
                        }
                    }
                    }
                }
                }
            }
        }
    })
}

@Composable private fun ProvisionStep(title: String, icon: ImageVector, button: String, enabled: Boolean, busy: Boolean,
    background: Color, onBack: () -> Unit, onNext: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalGlassColors.current
    BoxWithConstraints(Modifier.fillMaxSize().background(background).safeDrawingPadding().imePadding()) {
        val compact = maxHeight < 540.dp
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            GuideBack(onBack, enabled = !busy)
            Column(Modifier.weight(1f).widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp).padding(top = if (compact) 8.dp else 30.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, Modifier.size(if (compact) 52.dp else 72.dp), tint = colors.accent)
                Spacer(Modifier.height(if (compact) 14.dp else 24.dp))
                Text(title, style = IosTypography.title1.copy(fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal),
                    textAlign = TextAlign.Center, color = colors.content)
                Spacer(Modifier.height(if (compact) 24.dp else 38.dp))
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
            }
            GuidePrimaryButton(button, enabled, busy, onNext,
                Modifier.padding(horizontal = 28.dp).widthIn(max = 464.dp).fillMaxWidth())
            Spacer(Modifier.height(if (compact) 16.dp else 44.dp))
        }
    }
}

@Composable private fun ProvisionWelcome(active: Boolean, onStart: () -> Unit,
    onArrowBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(1100, easing = EaseOutCubic)) }
    val colors = LocalGlassColors.current
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxHeight < 500.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(if (compact) .7f else 1.2f))
                Text("Xachet", color = Color.White.copy(alpha = .86f),
                    style = IosTypography.largeTitle.copy(fontSize = 50.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(vertical = 8.dp).graphicsLayer {
                        alpha = intro.value; scaleX = .90f + .10f * intro.value; scaleY = scaleX
                        translationY = 18.dp.toPx() * (1f - intro.value)
                    })
                Spacer(Modifier.weight(1f))
                // Keep the press effect alive during the source fade. Disabling here
                // removes the interaction modifier before it can animate back.
                GlasmioIconButton({ if (active) onStart() }, size = 70.dp, style = GlassSurfaceStyle.Navigation,
                    modifier = Modifier.onGloballyPositioned { onArrowBounds(it.boundsInRoot()) }
                        .semantics { contentDescription = "开始设置" }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(30.dp), tint = colors.content)
                }
                Spacer(Modifier.weight(.65f))
            }
        }
    }
}

@Composable private fun ProvisionComplete(art: ProvisionSuccessArtwork?, active: Boolean, busy: Boolean,
    error: String?, onBack: () -> Unit, onComplete: () -> Unit, onButtonBounds: (Rect) -> Unit) {
    val colors = LocalGlassColors.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(active) { if (active) appear.animateTo(1f, tween(420, easing = EaseOutCubic)) }
    BoxWithConstraints(Modifier.fillMaxSize().background(if (colors.isDark) Color.Black else Color.White).safeDrawingPadding()) {
        GuideBack(onBack, !busy)
        val iconTop = (maxHeight * .37f - 64.dp).coerceAtLeast(64.dp)
        Column(Modifier.align(Alignment.TopCenter).padding(top = iconTop).widthIn(max = 520.dp)
            .fillMaxWidth().padding(horizontal = 28.dp).graphicsLayer { alpha = appear.value },
            horizontalAlignment = Alignment.CenterHorizontally) {
            ProvisionSuccessMark(art, active)
            Spacer(Modifier.height(18.dp))
            Text("一切准备就绪", style = IosTypography.title1.copy(fontSize = 30.sp, lineHeight = 42.sp), color = colors.content)
            error?.let { Text(it, color = colors.destructive, modifier = Modifier.padding(top = 16.dp)) }
        }
        GuidePrimaryButton("开始使用", !busy, busy, onComplete,
            Modifier.align(Alignment.BottomCenter).padding(horizontal = 28.dp).padding(bottom = 44.dp)
                .widthIn(max = 464.dp).fillMaxWidth().onGloballyPositioned { onButtonBounds(it.boundsInRoot()) })
    }
}

@Composable private fun GuideBack(onBack: () -> Unit, enabled: Boolean = true, tint: Color = LocalGlassColors.current.content) {
    Row(Modifier.widthIn(max = 576.dp).fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GlasmioIconButton(onBack, enabled = enabled, size = 48.dp, style = GlassSurfaceStyle.Navigation) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "上一步", Modifier.size(28.dp), tint = tint)
        }
    }
}

@Composable private fun GuidePrimaryButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit, modifier: Modifier) {
    GlasmioButton(onClick, modifier = modifier, enabled = enabled, emphasis = GlassEmphasis.Prominent,
        style = GlassSurfaceStyle.Navigation, size = GlasmioButtonSize.Large) {
        if (busy) XachetLoadingIndicator(Modifier.size(24.dp), color = LocalGlassColors.current.content)
        else Text(text, style = IosTypography.headline)
    }
}

@Composable private fun GuideParagraph(text: String) {
    Text(text, style = IosTypography.subheadline.copy(lineHeight = 22.sp), color = LocalGlassColors.current.secondaryContent)
}

@Composable private fun GuideCheck(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalGlassColors.current
    GlasmioListRow(text, minimumHeight = 52.dp, onClick = { onChange(!checked) },
        trailing = { GlasmioSwitch(checked = checked, onCheckedChange = onChange) })
}

@Composable private fun GuideTextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(text, style = IosTypography.subheadline,
        color = LocalGlassColors.current.accent.copy(alpha = if (enabled) 1f else .4f),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp).padding(vertical = 12.dp, horizontal = 4.dp))
}

@Composable private fun GuidePermission(icon: ImageVector, title: String, status: String, granted: Boolean, onClick: () -> Unit) {
    val colors = LocalGlassColors.current
    GlasmioListRow(title, subtitle = status, minimumHeight = 72.dp, onClick = onClick,
        leading = { Icon(icon, null, Modifier.size(26.dp), tint = colors.accent) },
        trailing = { Icon(if (granted) Icons.Default.Check else Icons.Default.ChevronRight, null,
            Modifier.size(20.dp), tint = if (granted) colors.accent else colors.secondaryContent) })
}

@Composable private fun GuideGroup(verticalArrangement: Arrangement.Vertical, content: @Composable ColumnScope.() -> Unit) {
    GlasmioCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = verticalArrangement, content = content)
    }
}

/** The incoming page owns a neutral optical source. The outer morph may still
 * refract the welcome glow, but nested buttons never inherit that cached scene. */
@Composable private fun GuidePageBackdrop(background: Color, content: @Composable () -> Unit) {
    // Supply a neutral optical source without drawing a second fullscreen layer:
    // the permissions page must uncover the completion page while it contracts.
    val drawSource: DrawScope.() -> Unit = remember(background) { { drawRect(background) } }
    val backdrop = rememberCanvasBackdrop(drawSource)
    GlasmioBackdropProvider(backdrop, content = content)
}

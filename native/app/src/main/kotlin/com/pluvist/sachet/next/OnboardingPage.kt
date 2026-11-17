@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingScreen(
    data: AppData,
    vm: SachetViewModel,
    onFinished: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var previousPage by rememberSaveable { mutableIntStateOf(0) }
    var agreed by rememberSaveable { mutableStateOf(false) }
    var studentId by rememberSaveable { mutableStateOf(data.account.studentID) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by rememberSaveable { mutableStateOf(true) }
    var cookieMode by rememberSaveable { mutableStateOf(false) }
    var cookie by remember { mutableStateOf("") }
    var webLogin by rememberSaveable { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val permissionStatus by rememberBackgroundSetupStatus(page == 3 && !webLogin)
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.onNotificationPermissionChanged() else vm.onForeground()
    }

    fun goTo(target: Int) {
        previousPage = page
        page = target.coerceIn(0, 4)
        vm.dismissMessage()
    }

    fun completeOnboarding() {
        if (finishing) return
        finishing = true
        finishError = null
        scope.launch {
            runCatching {
                vm.saveSettings(mapOf("hasReadDisclaimer" to true))
            }.onSuccess {
                onFinished()
            }.onFailure { err ->
                finishing = false
                finishError = err.message ?: "保存设置失败，请重试"
            }
        }
    }

    val stepBackState = rememberPredictiveBackState(enabled = !webLogin && page in 1..3) {
        goTo(page - 1)
    }
    val webBackState = rememberPredictiveBackState(enabled = webLogin) {
        webLogin = false
    }
    val outerMotionScheme = MaterialTheme.motionScheme

    AnimatedContent(
        targetState = webLogin,
        transitionSpec = { ExpressiveMotion.directionalSlideTransform(outerMotionScheme, targetState) },
        label = "OnboardingWebLoginTransition",
    ) { showWebLogin ->
        if (showWebLogin) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .predictiveBackContainer(webBackState),
                color = MaterialTheme.colorScheme.surface,
            ) {
                WebLoginPage(
                    vm = vm,
                    onAuthenticated = {
                        webLogin = false
                        goTo(3)
                    },
                    onBack = { webLogin = false },
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (page in 1..3) Modifier.predictiveBackContainer(stepBackState) else Modifier,
                    ),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (page in 1..3) {
                        val stepProgress by animateFloatAsState(
                            targetValue = page / 3f,
                            animationSpec = ExpressiveMotion.spatialDefault(),
                            label = "OnboardingStepProgress",
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 560.dp)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                IconButton(onClick = { goTo(page - 1) }) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "上一步")
                                }
                                Text(
                                    text = "第 $page / 3 步",
                                    style = MaterialTheme.typography.labelLargeEmphasized.tabular(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LinearWavyProgressIndicator(
                                progress = { stepProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        }
                    }

                    val motionScheme = MaterialTheme.motionScheme
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            ExpressiveMotion.directionalSlideTransform(motionScheme, targetState >= previousPage)
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "OnboardingPageTransition",
                    ) { currentStep ->
                        when (currentStep) {
                            0 -> OnboardingWelcomeStep(onStart = { goTo(1) })
                            1 -> OnboardingAgreementStep(
                                agreed = agreed,
                                onAgreedChange = { agreed = it },
                                onOpenSource = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Pluvist/Xachet")),
                                    )
                                },
                                onContinue = { goTo(2) },
                            )

                            2 -> OnboardingLoginStep(
                                studentId = studentId,
                                onStudentIdChange = {
                                    studentId = it
                                    vm.dismissMessage()
                                },
                                password = password,
                                onPasswordChange = {
                                    password = it
                                    vm.dismissMessage()
                                },
                                passwordVisible = passwordVisible,
                                onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                                rememberPassword = rememberPassword,
                                onRememberPasswordChange = { rememberPassword = it },
                                cookieMode = cookieMode,
                                onCookieModeChange = {
                                    cookieMode = it
                                    vm.dismissMessage()
                                },
                                cookie = cookie,
                                onCookieChange = {
                                    cookie = it
                                    vm.dismissMessage()
                                },
                                loading = data.loading,
                                errorMessage = data.message,
                                onDismissError = vm::dismissMessage,
                                onWebLogin = { webLogin = true },
                                onSkip = {
                                    vm.dismissMessage()
                                    goTo(3)
                                },
                                onSubmit = {
                                    if (cookieMode) {
                                        vm.cookieLogin(cookie) { goTo(3) }
                                    } else {
                                        vm.login(studentId.trim(), password, rememberPassword) { goTo(3) }
                                    }
                                },
                            )

                            3 -> OnboardingPermissionStep(
                                status = permissionStatus,
                                activity = activity,
                                onRequestNotifications = {
                                    activity?.let { act ->
                                        BackgroundSetupSettings.requestNotifications(act) {
                                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                },
                                onOpenSetting = { kind ->
                                    activity?.let { BackgroundSetupSettings.open(it, kind) }
                                },
                                onPinWidget = {
                                    val manager = runCatching { AppWidgetManager.getInstance(context) }.getOrNull()
                                    if (manager != null && runCatching { manager.isRequestPinAppWidgetSupported }.getOrDefault(false)) {
                                        runCatching {
                                            manager.requestPinAppWidget(
                                                ComponentName(context, CourseWidgetProvider::class.java),
                                                null,
                                                null,
                                            )
                                        }
                                    }
                                },
                                onContinue = { goTo(4) },
                            )

                            else -> OnboardingDoneStep(
                                finishing = finishing,
                                errorMessage = finishError,
                                onFinish = ::completeOnboarding,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "WelcomeShapeOrbit")
    val spin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "WelcomeHaloSpin",
    )
    val morphFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "WelcomeShapeMorph",
    )
    val heroBadgeShape = rememberMorphShape(
        from = MaterialShapes.Clover4Leaf,
        to = MaterialShapes.Cookie6Sided,
        progress = morphFraction,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier.size(164.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .size(164.dp)
                        .graphicsLayer { rotationZ = spin },
                    shape = MaterialShapes.VerySunny.toShape(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                ) {}
                Surface(
                    modifier = Modifier.size(116.dp),
                    shape = heroBadgeShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.School,
                            contentDescription = null,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Xachet",
                    style = XachetTypeTokens.BrandHero,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "湘潭大学教务助手",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "课表、错峰作息、实时活动与教务查询\n现在开启全新 Material 3 Expressive 体验",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.LargeContentPadding,
            ) {
                Text("开始配置", style = MaterialTheme.typography.titleMediumEmphasized)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun OnboardingAgreementStep(
    agreed: Boolean,
    onAgreedChange: (Boolean) -> Unit,
    onOpenSource: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "协议与声明",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "欢迎使用 Xachet。请阅读以下说明，了解账号与课程数据的使用方式。",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "账号及记住的密码通过本机密钥加密保存，仅用于登录学校教务系统。课表缓存在本机，用于离线显示、桌面小组件和课程通知。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Xachet 由 Pluvist 基于开源项目 Sachet 二次开发，感谢上游作者与开源贡献者。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onOpenSource,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("查看开源项目与许可")
                    }
                }
            }
            ExpressiveSegmentedGroup {
                ExpressiveSwitchRow(
                    index = 0,
                    count = 1,
                    title = "我已阅读并同意以上声明",
                    checked = agreed,
                    onCheckedChange = onAgreedChange,
                )
            }
            Button(
                onClick = onContinue,
                enabled = agreed,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text("同意并继续")
            }
        }
    }
}

@Composable
private fun OnboardingLoginStep(
    studentId: String,
    onStudentIdChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    rememberPassword: Boolean,
    onRememberPasswordChange: (Boolean) -> Unit,
    cookieMode: Boolean,
    onCookieModeChange: (Boolean) -> Unit,
    cookie: String,
    onCookieChange: (String) -> Unit,
    loading: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onWebLogin: () -> Unit,
    onSkip: () -> Unit,
    onSubmit: () -> Unit,
) {
    val canSubmit = !loading && if (cookieMode) cookie.isNotBlank() else studentId.isNotBlank() && password.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "登录账号",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "使用湘潭大学教务账号，同步课程与教务信息。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpressiveConnectedToggleRow(
                options = listOf("学号密码", "Cookie 登录"),
                selectedIndex = if (cookieMode) 1 else 0,
                onSelect = { onCookieModeChange(it == 1) },
            )
            if (cookieMode) {
                OutlinedTextField(
                    value = cookie,
                    onValueChange = onCookieChange,
                    label = { Text("教务 Cookie") },
                    placeholder = { Text("请输入教务 Cookie") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisible) {
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
                OutlinedTextField(
                    value = studentId,
                    onValueChange = onStudentIdChange,
                    label = { Text("学号") },
                    placeholder = { Text("请输入学号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    placeholder = { Text("默认密码为 Xtu@身份证后六位") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisible) {
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
                        subtitle = "加密保存在本机以便自动续期登录",
                        checked = rememberPassword,
                        onCheckedChange = onRememberPasswordChange,
                    )
                }
            }
            errorMessage?.let {
                ExpressiveMessageBanner(
                    message = it,
                    isError = true,
                    onDismiss = onDismissError,
                )
            }
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                if (loading) {
                    XachetInlineBusyIndicator("正在登录并同步")
                } else {
                    Text("登录并继续")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onWebLogin,
                    enabled = !loading,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("网页登录")
                }
                TextButton(
                    onClick = onSkip,
                    enabled = !loading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("稍后登录")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPermissionStep(
    status: Map<String, Any>,
    activity: Activity?,
    onRequestNotifications: () -> Unit,
    onOpenSetting: (String) -> Unit,
    onPinWidget: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "权限与服务",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "开启所需权限，让课前提醒和课程实时状态及时出现。你也可以稍后在设置中调整。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionStatusGroup(
                status = status,
                onRequestNotifications = onRequestNotifications,
                onOpenSetting = onOpenSetting,
            )
            Text(
                text = "第 1、5 节课提前 20 分钟提醒，其他节次提前 10 分钟提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val widgetCount = status["widgetCount"] as? Int ?: 0
            ExpressiveSegmentedGroup {
                ExpressiveSegmentedActionRow(
                    index = 0,
                    count = 1,
                    title = "桌面小组件",
                    subtitle = if (widgetCount > 0) "已添加 · 点击添加更多" else "添加课程到桌面",
                    value = if (widgetCount > 0) "$widgetCount 个" else null,
                    leading = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                    onClick = onPinWidget,
                )
            }
            Text(
                text = "也可以长按桌面，在小组件列表选择 Xachet。自启动状态请以系统设置为准。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onContinue,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Text("继续")
            }
        }
    }
}

@Composable
private fun OnboardingDoneStep(
    finishing: Boolean,
    errorMessage: String?,
    onFinish: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "CompletionMorph")
    val morphFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "CompletionShapeMorph",
    )
    val shape = rememberMorphShape(
        from = MaterialShapes.Sunny,
        to = MaterialShapes.SoftBurst,
        progress = morphFraction,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = shape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            Text(
                text = "一切准备就绪",
                style = MaterialTheme.typography.headlineLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "课程通知与离线课表环境已配置完成",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            errorMessage?.let {
                ExpressiveMessageBanner(message = it, isError = true)
            }
            Button(
                onClick = onFinish,
                enabled = !finishing,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.LargeContentPadding,
            ) {
                if (finishing) {
                    XachetInlineBusyIndicator("正在进入")
                } else {
                    Text("开始使用", style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
        }
    }
}

internal data class PermissionRowItem(
    val icon: ImageVector,
    val title: String,
    val statusText: String,
    val granted: Boolean,
    val onClick: () -> Unit,
)

@Composable
internal fun PermissionStatusGroup(
    status: Map<String, Any>,
    onRequestNotifications: () -> Unit,
    onOpenSetting: (String) -> Unit,
) {
    val live = status["live"] as? Map<*, *>
    val rows = listOf(
        PermissionRowItem(
            icon = Icons.Rounded.Notifications,
            title = "通知权限",
            statusText = when {
                status.isEmpty() -> "正在读取"
                status["notifications"] == true -> "已开启"
                else -> "未开启"
            },
            granted = status["notifications"] == true,
            onClick = onRequestNotifications,
        ),
        PermissionRowItem(
            icon = Icons.Rounded.Timer,
            title = "实时活动",
            statusText = when {
                status.isEmpty() -> "正在读取"
                live?.get("supported") != true -> "使用持续课程通知"
                live["promoted"] == true -> "已开启"
                else -> "前往开启"
            },
            granted = live?.get("promoted") == true,
            onClick = { onOpenSetting("live") },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.Alarm,
            title = "准时提醒",
            statusText = when {
                status.isEmpty() -> "正在读取"
                live?.get("exactAlarms") == true -> "已开启"
                else -> "允许闹钟和提醒"
            },
            granted = live?.get("exactAlarms") == true,
            onClick = { onOpenSetting("exactAlarms") },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.BatteryChargingFull,
            title = "后台运行",
            statusText = when {
                status.isEmpty() -> "正在读取"
                status["batteryUnrestricted"] == true -> "已允许"
                else -> "允许无限制运行"
            },
            granted = status["batteryUnrestricted"] == true,
            onClick = { onOpenSetting("battery") },
        ),
        PermissionRowItem(
            icon = Icons.Rounded.RestartAlt,
            title = "自启动",
            statusText = "请在系统设置中确认",
            granted = false,
            onClick = { onOpenSetting("autostart") },
        ),
    )

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
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (row.granted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (row.granted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    ) {
                        Text(
                            text = if (row.granted) "已就绪" else "去设置",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                },
                content = {
                    Text(row.title, style = MaterialTheme.typography.titleSmallEmphasized)
                },
            )
        }
    }
}

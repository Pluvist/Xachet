@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun SettingsPage(
    vm: SachetViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val settings = remember { vm.repository.settings() }

    var semester by remember { mutableStateOf(settings.optString("semesterStartDate").take(10)) }
    var live by remember { mutableStateOf(settings.optBoolean("isEnableCourseLiveActivity", true)) }
    var reminders by remember { mutableStateOf(settings.optBoolean("isEnableCourseReminders", true)) }
    var theme by remember { mutableIntStateOf(settings.optInt("themeMode")) }
    var accent by remember {
        mutableStateOf(
            XachetSeedPresets.firstOrNull {
                it.hex.equals(settings.optString("themeColor"), ignoreCase = true)
            }?.hex ?: XachetSeedPresets.first().hex,
        )
    }
    var useDynamicColor by remember { mutableStateOf(settings.optBoolean("useDynamicColor", false)) }
    var uiScalePercent by remember { mutableIntStateOf(resolveXachetUiScalePercent(settings)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        scope.launch { BackgroundSetupSettings.refreshCourses(context) }
    }

    if (showDatePicker) {
        val initialMillis = remember(semester) {
            runCatching {
                LocalDate.parse(semester).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrElse { Instant.now().toEpochMilli() }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            semester = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        }
                        showDatePicker = false
                    },
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DatePicker(state = datePickerState)
            }
        }
    }

    ExpressiveSubPageScaffold(
        title = "设置",
        subtitle = "学期日历、通知提醒与动态主题",
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
            ExpressiveSectionHeader("学期设置", subtitle = "用于推算当前教学周与倒计时")
            OutlinedTextField(
                value = semester,
                onValueChange = { semester = it },
                label = { Text("学期开始日期（周一）") },
                placeholder = { Text("YYYY-MM-DD") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.DateRange, contentDescription = "选择日期")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )

            ExpressiveSectionHeader("通知与实时状态")
            ExpressiveSegmentedGroup {
                ExpressiveSwitchRow(
                    index = 0,
                    count = 2,
                    title = "课程实时活动",
                    subtitle = "上课倒计时与下一节课程信息",
                    checked = live,
                    onCheckedChange = { live = it },
                    leading = { Icon(Icons.Rounded.Timelapse, contentDescription = null) },
                )
                ExpressiveSwitchRow(
                    index = 1,
                    count = 2,
                    title = "课前提醒",
                    subtitle = "第 1、5 节提前 20 分钟，其他节次提前 10 分钟",
                    checked = reminders,
                    onCheckedChange = { reminders = it },
                    leading = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                )
            }

            ExpressiveSectionHeader("外观与主题", subtitle = "Material 3 Expressive 色彩引擎")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("深浅模式", style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                    ExpressiveConnectedToggleRow(
                        options = listOf("跟随系统", "浅色", "深色"),
                        selectedIndex = theme.coerceIn(0, 2),
                        onSelect = { theme = it },
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        ExpressiveSegmentedGroup {
                            ExpressiveSwitchRow(
                                index = 0,
                                count = 1,
                                title = "壁纸动态取色",
                                subtitle = "根据系统壁纸自动生成 Material You 色板",
                                checked = useDynamicColor,
                                onCheckedChange = { useDynamicColor = it },
                                leading = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ColorLens,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                "主题色（动态取色开启时忽略）"
                            } else {
                                "主题色选择"
                            },
                            style = MaterialTheme.typography.titleSmallEmphasized,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        XachetSeedPresets.forEach { preset ->
                            val selectedPreset = accent.equals(preset.hex, ignoreCase = true)
                            val swatchCorner by animateDpAsState(
                                targetValue = if (selectedPreset) 18.dp else 28.dp,
                                animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
                                label = "ThemeSwatchCorner",
                            )
                            val swatchShape = RoundedCornerShape(swatchCorner)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(swatchShape)
                                        .then(
                                            if (selectedPreset) {
                                                Modifier.border(
                                                    width = 2.5.dp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    shape = swatchShape,
                                                )
                                            } else {
                                                Modifier.border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                    shape = swatchShape,
                                                )
                                            },
                                        )
                                        .semantics {
                                            contentDescription = if (selectedPreset) {
                                                "已选主题色：${preset.name}"
                                            } else {
                                                "选择主题色：${preset.name}"
                                            }
                                        }
                                        .clickable(role = Role.RadioButton) { accent = preset.hex },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val secondary = preset.secondaryColor
                                    if (secondary != null) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawRect(
                                                brush = Brush.linearGradient(
                                                    0.0f to preset.color,
                                                    0.48f to preset.color,
                                                    0.52f to secondary,
                                                    1.0f to secondary,
                                                ),
                                            )
                                        }
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = swatchShape,
                                            color = preset.color,
                                        ) {}
                                    }
                                    if (selectedPreset) {
                                        Surface(
                                            modifier = Modifier.size(24.dp),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedPreset) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedPreset) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.ZoomIn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("界面缩放", style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                    ExpressiveConnectedToggleRow(
                        options = XachetUiScalePresets.map { "$it%" },
                        selectedIndex = XachetUiScalePresets.indexOf(uiScalePercent).let {
                            if (it >= 0) it else XachetUiScalePresets.indexOf(DEFAULT_UI_SCALE_PERCENT)
                        },
                        onSelect = { index ->
                            uiScalePercent = XachetUiScalePresets.getOrElse(index) { DEFAULT_UI_SCALE_PERCENT }
                        },
                    )
                }
            }

            error?.let {
                ExpressiveMessageBanner(message = it, isError = true, onDismiss = { error = null })
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            android.graphics.Color.parseColor(accent.trim())
                            vm.saveSettings(
                                mapOf(
                                    "semesterStartDate" to semester.trim(),
                                    "isEnableCourseLiveActivity" to live,
                                    "isEnableCourseReminders" to reminders,
                                    "themeMode" to theme,
                                    "themeColor" to accent.trim(),
                                    "useDynamicColor" to useDynamicColor,
                                    "uiScalePercent" to uiScalePercent,
                                ),
                            )
                            if (activity != null) {
                                if ((live || reminders) &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    BackgroundSetupSettings.requestNotifications(activity) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else if (reminders &&
                                    !runCatching {
                                        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
                                    }.getOrDefault(false)
                                ) {
                                    BackgroundSetupSettings.open(activity, "exactAlarms")
                                }
                            }
                            onBack()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "输入参数无效"
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存设置")
            }
        }
    }
}

@Composable
internal fun DataPage(
    data: AppData,
    vm: SachetViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importKind by remember { mutableStateOf("schedule") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val body = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                            reader.readText()
                        } ?: error("无法打开文件")
                    }
                    if (importKind == "colors") {
                        vm.importColors(body)
                        statusMessage = "已导入课程配色方案"
                    } else {
                        vm.importSchedule(body)
                        statusMessage = "已导入课表数据"
                    }
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(it) ?: error("无法写入文件")
                        output.bufferedWriter().use { writer ->
                            writer.write(CalendarExport.ics(data.courses, vm.repository.semester()))
                        }
                    }
                    statusMessage = "已导出 ICS 日历文件"
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    ExpressiveSubPageScaffold(
        title = "导入与导出",
        subtitle = "支持 Sachet JSON 与标准 ICS 日历",
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
                ExpressiveSegmentedActionRow(
                    index = 0,
                    count = 3,
                    title = "导入课表 JSON",
                    subtitle = "支持原 Sachet 课表与教务原始数据",
                    leading = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                    onClick = {
                        importKind = "schedule"
                        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                )
                ExpressiveSegmentedActionRow(
                    index = 1,
                    count = 3,
                    title = "导入课程配色",
                    subtitle = "兼容原 Sachet 配色 JSON",
                    leading = { Icon(Icons.Rounded.Palette, contentDescription = null) },
                    onClick = {
                        importKind = "colors"
                        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                )
                ExpressiveSegmentedActionRow(
                    index = 2,
                    count = 3,
                    title = "导出到日历",
                    subtitle = "按教学楼真实作息生成 ICS",
                    leading = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    onClick = { exporter.launch("Xachet.ics") },
                )
            }
            statusMessage?.let {
                ExpressiveMessageBanner(message = it, onDismiss = { statusMessage = null })
            }
            error?.let {
                ExpressiveMessageBanner(message = it, isError = true, onDismiss = { error = null })
            }
        }
    }
}

private enum class TranscriptView { FORM, PDF }

@Composable
internal fun TranscriptPage(
    repository: AcademicRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var view by remember { mutableStateOf(TranscriptView.FORM) }
    var types by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selected by remember { mutableStateOf("") }
    var loadingTypes by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var fileFormat by remember { mutableStateOf<String?>(null) }
    var indexedFile by remember { mutableStateOf<File?>(null) }
    var indexedPages by remember { mutableStateOf<List<TranscriptTextPage>?>(null) }
    var matches by remember { mutableStateOf<List<TranscriptPageMatch>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var rendering by remember { mutableStateOf(false) }
    var renderRequest by remember { mutableIntStateOf(0) }
    var showTypePicker by remember { mutableStateOf(false) }

    val searchTextField = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    var submittedSearchTerm by remember { mutableStateOf("") }

    val navigateBack: () -> Unit = {
        if (view == TranscriptView.PDF) {
            view = TranscriptView.FORM
        } else {
            onBack()
        }
    }
    val pdfBackState = rememberPredictiveBackState(enabled = view == TranscriptView.PDF) {
        view = TranscriptView.FORM
    }

    LaunchedEffect(reload) {
        loadingTypes = true
        error = null
        try {
            types = repository.transcriptTypes()
            if (types.none { it.first == selected }) {
                selected = types.firstOrNull()?.first.orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loadingTypes = false
        }
    }

    fun runSearch(term: String) {
        val cleanTerm = term.trim()
        submittedSearchTerm = cleanTerm
        scope.launch {
            if (downloading || loadingTypes) return@launch
            searching = true
            error = null
            try {
                require(selected.isNotBlank()) { "请先选择成绩单格式" }
                val pdf = file?.takeIf { fileFormat == selected }
                    ?: repository.transcript(selected).also {
                        file = it
                        fileFormat = selected
                        indexedFile = null
                        indexedPages = null
                        page = 0
                        pageCount = 0
                        renderRequest++
                    }
                val pages = indexedPages?.takeIf { indexedFile == pdf }
                    ?: withContext(Dispatchers.IO) { TranscriptPdfSearch.index(context, pdf) }.also {
                        indexedFile = pdf
                        indexedPages = it
                    }
                pageCount = pages.size
                matches = withContext(Dispatchers.Default) { TranscriptPdfSearch.find(pages, cleanTerm) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                searching = false
            }
        }
    }

    val bitmap by produceState<Bitmap?>(null, file, page, renderRequest, view) {
        if (view != TranscriptView.PDF || file == null) {
            value = null
            return@produceState
        }
        val pdf = file ?: return@produceState
        rendering = true
        try {
            val (image, count) = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        require(renderer.pageCount > 0) { "成绩单没有可显示的页面" }
                        val rendered = renderer.openPage(page.coerceIn(0, renderer.pageCount - 1)).use { source ->
                            Bitmap.createBitmap(
                                1080,
                                (1080f * source.height / source.width).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            ).also { bmp ->
                                source.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        rendered to renderer.pageCount
                    }
                }
            }
            pageCount = count
            value = image
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            rendering = false
        }
    }

    if (showTypePicker) {
        ExpressiveChoiceSheet(
            title = "选择成绩单格式",
            options = types,
            selectedKey = selected,
            onSelect = { (key, _) ->
                if (selected != key) {
                    selected = key
                    file = null
                    fileFormat = null
                    indexedFile = null
                    indexedPages = null
                    page = 0
                    pageCount = 0
                    renderRequest++
                }
            },
            onDismiss = { showTypePicker = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            ExpressiveAcademicSearchAppBar(
                textFieldState = searchTextField,
                placeholder = "搜索成绩单内容",
                onBack = navigateBack,
                onSearchSubmit = { term ->
                    runSearch(term)
                },
                searchBarState = searchBarState,
            ) { collapse ->
                val activeTerm = searchTextField.text.toString().trim()
                LaunchedEffect(activeTerm, selected, file) {
                    if (selected.isNotBlank() && activeTerm != submittedSearchTerm) {
                        runSearch(activeTerm)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (searching) {
                        item(key = "transcript_searching") { XachetLoadingCard("正在检索 PDF 成绩单…") }
                    } else if (indexedPages?.all { it.text.isBlank() } == true && activeTerm.isNotBlank()) {
                        item(key = "transcript_scanned") {
                            ExpressiveMessageBanner(
                                message = "此成绩单没有可提取的文字，可能是扫描版 PDF，无法进行文字搜索。",
                            )
                        }
                    } else {
                        item(key = "transcript_count") {
                            Text(
                                text = "找到 ${matches.size} 页结果",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (matches.isEmpty()) {
                            item(key = "transcript_empty") {
                                ExpressiveEmptyCard(
                                    icon = Icons.Rounded.Search,
                                    title = "没有匹配的成绩单内容",
                                    subtitle = "尝试输入课程名称、学期或成绩关键字后提交搜索",
                                )
                            }
                        } else {
                            item(key = "transcript_matches") {
                                ExpressiveSegmentedGroup {
                                    matches.forEachIndexed { index, match ->
                                        SegmentedListItem(
                                            onClick = {
                                                page = (match.number - 1).coerceAtLeast(0)
                                                view = TranscriptView.PDF
                                                collapse()
                                            },
                                            shapes = ListItemDefaults.segmentedShapes(index = index, count = matches.size),
                                            colors = expressiveSegmentedColors(),
                                            supportingContent = { Text(match.snippet) },
                                            content = {
                                                Text(
                                                    text = "第 ${match.number} 页",
                                                    style = MaterialTheme.typography.titleSmallEmphasized.tabular(),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (view == TranscriptView.PDF && pageCount > 1) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                ) {
                    IconButton(
                        onClick = { if (page > 0) page-- },
                        enabled = page > 0,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = "上一页")
                    }
                    Text(
                        text = "${page + 1} / $pageCount",
                        style = MaterialTheme.typography.labelLargeEmphasized.tabular(),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    IconButton(
                        onClick = { if (page + 1 < pageCount) page++ },
                        enabled = page + 1 < pageCount,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = "下一页")
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val motionScheme = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    ExpressiveMotion.directionalSlideTransform(motionScheme, targetState == TranscriptView.PDF)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (view == TranscriptView.PDF) Modifier.predictiveBackContainer(pdfBackState) else Modifier,
                    ),
                label = "TranscriptView",
            ) { currentView ->
                when (currentView) {
                    TranscriptView.FORM -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ExpressiveSegmentedGroup {
                            ExpressiveSelectorRow(
                                index = 0,
                                count = 1,
                                label = "格式",
                                selectedLabel = types.firstOrNull { it.first == selected }?.second.orEmpty(),
                                onClick = { if (!loadingTypes && types.isNotEmpty()) showTypePicker = true },
                                leading = { Icon(Icons.Rounded.Description, contentDescription = null) },
                            )
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    downloading = true
                                    error = null
                                    try {
                                        val downloaded = repository.transcript(selected)
                                        file = downloaded
                                        fileFormat = selected
                                        indexedFile = null
                                        indexedPages = null
                                        page = 0
                                        pageCount = 0
                                        renderRequest++
                                        view = TranscriptView.PDF
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        downloading = false
                                    }
                                }
                            },
                            enabled = !loadingTypes && !downloading && selected.isNotBlank(),
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = ButtonDefaults.MediumContentPadding,
                        ) {
                            if (downloading) {
                                XachetInlineBusyIndicator("正在生成成绩单")
                            } else {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("下载并查看")
                            }
                        }
                        error?.let {
                            ExpressiveMessageBanner(
                                message = it,
                                isError = true,
                                actionLabel = "重新加载格式",
                                onAction = { reload++ },
                            )
                        }
                    }

                    TranscriptView.PDF -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .padding(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        error?.let {
                            ExpressiveMessageBanner(
                                message = it,
                                isError = true,
                                actionLabel = "重试打开",
                                onAction = { renderRequest++ },
                            )
                        }
                        bitmap?.let { image ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                ),
                            ) {
                                Image(
                                    bitmap = image.asImageBitmap(),
                                    contentDescription = "成绩单第${page + 1}页",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (loadingTypes || downloading || rendering) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    Box(modifier = Modifier.padding(24.dp)) {
                        XachetLoadingIndicator(contained = true, size = 48.dp)
                    }
                }
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

private enum class AcademicQueryScreen { Form, Results, Curriculum }
private enum class GradeTab { Overview, Records }

private class AcademicRecordSnapshot(
    val source: List<JSONObject>?,
    val type: String,
    val records: List<Pair<JSONObject, AcademicRecord>>,
    val searchTexts: List<String> = emptyList(),
)

private class AcademicFilteredRecords(
    val source: List<Pair<JSONObject, AcademicRecord>>,
    val keyword: String,
    val matches: List<Pair<JSONObject, AcademicRecord>>,
)

@Composable
internal fun QueryPage(
    feature: QueryFeature,
    repository: AcademicRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var form by remember(feature) { mutableStateOf<AcademicForm?>(null) }
    val values = remember(feature) { mutableStateMapOf<String, String>() }
    var dateText by remember(feature) { mutableStateOf(LocalDate.now().toString()) }
    var rows by remember(feature) { mutableStateOf<List<JSONObject>?>(null) }
    var curriculum by remember(feature) { mutableStateOf<List<JSONObject>?>(null) }
    var curriculumTitle by remember(feature) { mutableStateOf("") }
    var curriculumMajorId by remember(feature) { mutableStateOf<String?>(null) }
    var curriculumReturnScreen by remember(feature) { mutableStateOf(AcademicQueryScreen.Results) }
    var cachedAt by remember(feature) { mutableStateOf<Long?>(null) }
    var loading by remember(feature) { mutableStateOf(false) }
    var loadingChoices by remember(feature) { mutableStateOf(false) }
    var error by remember(feature) { mutableStateOf<String?>(null) }
    var dependencyError by remember(feature) { mutableStateOf(false) }
    var reload by remember(feature) { mutableIntStateOf(0) }
    var screen by remember(feature) {
        mutableStateOf(
            if (feature.key in setOf("grades", "exams", "textbooks", "cultivation")) {
                AcademicQueryScreen.Results
            } else {
                AcademicQueryScreen.Form
            },
        )
    }
    var gradeTab by remember(feature) { mutableStateOf(GradeTab.Overview) }
    var curriculumRequestId by remember(feature) { mutableIntStateOf(0) }
    var activeChoicePicker by remember { mutableStateOf<QueryChoice?>(null) }
    var showClassroomViewPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var examMenuExpanded by remember { mutableStateOf(false) }

    val examLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val examNow by produceState(LocalDateTime.now(), feature.key, examLifecycle) {
        if (feature.key == "exams") {
            examLifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    value = LocalDateTime.now()
                    delay(30_000L)
                }
            }
        }
    }

    val close: () -> Unit = {
        curriculumRequestId++
        when (screen) {
            AcademicQueryScreen.Curriculum -> {
                curriculum = null
                curriculumMajorId = null
                screen = curriculumReturnScreen
                error = null
            }

            AcademicQueryScreen.Results -> {
                screen = AcademicQueryScreen.Form
                error = null
            }

            AcademicQueryScreen.Form -> onBack()
        }
    }
    val screenBackState = rememberPredictiveBackState(enabled = screen != AcademicQueryScreen.Form) { close() }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { out ->
                            out.write(CalendarExport.exams(rows.orEmpty()))
                        } ?: error("无法写入日历文件")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    fun query(destination: AcademicQueryScreen = AcademicQueryScreen.Results) {
        scope.launch {
            if (loading || loadingChoices) return@launch
            val requestId = ++curriculumRequestId
            loading = true
            error = null
            curriculum = null
            try {
                if (feature.key == "classrooms" && values["view"] == "day") {
                    val date = runCatching { LocalDate.parse(dateText) }.getOrElse {
                        throw IllegalArgumentException("请输入有效日期，格式为 YYYY-MM-DD")
                    }
                    require(values["dm_cx"] == form?.choices?.firstOrNull { it.key == "dm_cx" }?.selected) {
                        "其他学期请使用按节次筛选，并指定周次与星期"
                    }
                    val week = CourseClock.week(date, repository.semester())
                    require(week in 1..60) { "所选日期不在当前课表学期内，请选择相应学期并使用按节次筛选" }
                    values["weeks"] = week.toString()
                    values["weekdays"] = date.dayOfWeek.value.toString()
                }
                val result = repository.query(feature, values.toMap())
                if (requestId == curriculumRequestId) {
                    rows = result
                    cachedAt = null
                    screen = destination
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == curriculumRequestId) error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun openCurriculum(row: JSONObject, title: String) {
        val majorId = row.optString("jxzxjhxx_id")
        if (majorId.isBlank() || loading) return
        scope.launch {
            val requestId = ++curriculumRequestId
            val sourceScreen = screen
            loading = true
            error = null
            try {
                val courses = repository.curriculum(majorId)
                if (requestId != curriculumRequestId || screen != sourceScreen) return@launch
                curriculumReturnScreen = screen
                curriculumTitle = title
                curriculumMajorId = majorId
                curriculum = courses
                screen = AcademicQueryScreen.Curriculum
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == curriculumRequestId) error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun retry() {
        if (feature.key == "cultivation" && curriculum != null) {
            val majorId = curriculumMajorId
            if (majorId == null) {
                error = "请重新选择培养方案"
                return
            }
            scope.launch {
                val requestId = ++curriculumRequestId
                loading = true
                error = null
                try {
                    val courses = repository.curriculum(majorId)
                    if (requestId == curriculumRequestId && curriculum != null) curriculum = courses
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (requestId == curriculumRequestId) error = e.message
                } finally {
                    loading = false
                }
            }
        } else if (form == null || dependencyError) {
            reload++
        } else {
            query(AcademicQueryScreen.Results)
        }
    }

    LaunchedEffect(feature, reload) {
        loading = true
        error = null
        try {
            if (reload == 0) {
                repository.cachedQuery(feature)?.let { cached ->
                    if (feature.key != "grades" ||
                        (cached.parameters["xnm"].isNullOrBlank() &&
                            cached.parameters["xqm"].isNullOrBlank() &&
                            cached.parameters["kcbj"] == "1")
                    ) {
                        rows = cached.rows
                        cachedAt = cached.savedAt
                    }
                    if (feature.key != "grades") values.putAll(cached.parameters)
                }
            }
            form = repository.form(feature)
            if (feature.key == "grades") {
                values["xnm"] = ""
                values["xqm"] = ""
                values["kcbj"] = "1"
            }
            form!!.choices.forEach { choice ->
                if (choice.values.none { it.first == values[choice.key] }) {
                    values[choice.key] = choice.selected
                }
            }
            if (feature.key == "classrooms") {
                values.putIfAbsent(
                    "weeks",
                    CourseClock.week(LocalDate.now(), repository.semester()).coerceIn(1, 60).toString(),
                )
                values.putIfAbsent("weekdays", LocalDate.now().dayOfWeek.value.toString())
                values.putIfAbsent("sessions", "1-2")
                values.putIfAbsent("view", "day")
            }
            if (feature.key in listOf("grades", "exams", "textbooks", "cultivation")) {
                rows = repository.query(feature, values.toMap())
                cachedAt = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(reload, form?.choices?.isNotEmpty(), values["jg_id"], values["dm_cx"], values["xqh_id"]) {
        if (form == null) return@LaunchedEffect
        val key = when (feature.key) {
            "cultivation" -> "zyh_id_cx"
            "classrooms" -> "lh"
            else -> return@LaunchedEffect
        }
        loadingChoices = true
        dependencyError = false
        val previous = values[key].orEmpty()
        values[key] = ""
        form = form?.copy(
            choices = form!!.choices.map {
                if (it.key == key) it.copy(values = listOf("" to "全部")) else it
            },
        )
        try {
            val options = if (feature.key == "cultivation") {
                val school = values["jg_id"].orEmpty()
                if (school.isBlank()) listOf("" to "全部") else listOf("" to "全部") + repository.schoolMajors(school)
            } else {
                repository.teachingBuildings(values["dm_cx"].orEmpty(), values["xqh_id"] ?: "02")
            }
            form = form?.copy(
                choices = form!!.choices.map {
                    if (it.key == key) it.copy(values = options) else it
                },
            )
            values[key] = previous.takeIf { old -> options.any { it.first == old } }.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            dependencyError = true
            error = e.message
        } finally {
            loadingChoices = false
        }
    }

    val visibleRows = curriculum ?: rows
    val recordType = if (curriculum != null) "curriculum" else feature.key
    val recordSnapshot by produceState(AcademicRecordSnapshot(null, "", emptyList()), visibleRows, recordType) {
        value = withContext(Dispatchers.Default) {
            val parsed = visibleRows.orEmpty().map { it to AcademicRecords.record(recordType, it) }
            AcademicRecordSnapshot(
                source = visibleRows,
                type = recordType,
                records = parsed,
                searchTexts = parsed.map { AcademicKeywordSearch.searchableText(it.second) },
            )
        }
    }

    val searchable = feature.key in setOf("grades", "gpa", "exams", "textbooks", "cultivation")
    val searchTextField = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    val searchQuery by remember { derivedStateOf { searchTextField.text.toString() } }

    val filtered by produceState(AcademicFilteredRecords(emptyList(), "", emptyList()), recordSnapshot, searchQuery) {
        if (searchQuery.isNotBlank()) delay(70)
        val terms = AcademicKeywordSearch.terms(searchQuery)
        val matches = if (terms.isEmpty()) {
            recordSnapshot.records
        } else {
            withContext(Dispatchers.Default) {
                recordSnapshot.records.filterIndexed { index, _ ->
                    if (index % 64 == 0) ensureActive()
                    AcademicKeywordSearch.matchesPrepared(recordSnapshot.searchTexts[index], terms)
                }
            }
        }
        value = AcademicFilteredRecords(recordSnapshot.records, searchQuery, matches)
    }

    val pageTitle = when (screen) {
        AcademicQueryScreen.Form -> feature.title
        AcademicQueryScreen.Results -> if (feature.key in setOf("grades", "exams")) feature.title else "${feature.title}结果"
        AcademicQueryScreen.Curriculum -> curriculumTitle
    }
    val searchPlaceholder = when (feature.key) {
        "grades" -> "搜索成绩"
        "gpa" -> "搜索绩点或排名"
        "exams" -> "搜索考试"
        "textbooks" -> "搜索教材"
        "cultivation" -> if (curriculum != null) "搜索课程" else "搜索专业"
        else -> "搜索记录"
    }

    activeChoicePicker?.let { choice ->
        ExpressiveChoiceSheet(
            title = choice.label,
            options = choice.values,
            selectedKey = values[choice.key].orEmpty(),
            onSelect = { (selectedValue, _) -> values[choice.key] = selectedValue },
            onDismiss = { activeChoicePicker = null },
        )
    }
    if (showClassroomViewPicker) {
        ExpressiveChoiceSheet(
            title = "查询方式",
            options = listOf("day" to "当日分时段总览", "filter" to "按节次筛选"),
            selectedKey = values["view"].orEmpty(),
            onSelect = { (selectedValue, _) -> values["view"] = selectedValue },
            onDismiss = { showClassroomViewPicker = false },
        )
    }
    if (showDatePicker) {
        val initialMillis = remember(dateText) {
            runCatching {
                LocalDate.parse(dateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrElse { Instant.now().toEpochMilli() }
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            dateText = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
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

    val topActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (feature.key == "exams") {
            Box {
                IconButton(onClick = { examMenuExpanded = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "更多考试操作")
                }
                DropdownMenu(
                    expanded = examMenuExpanded,
                    onDismissRequest = { examMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("筛选学年与学期") },
                        leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                        onClick = {
                            examMenuExpanded = false
                            screen = AcademicQueryScreen.Form
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("导出考试日历") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        enabled = !rows.isNullOrEmpty(),
                        onClick = {
                            examMenuExpanded = false
                            exporter.launch("Xachet-Exams.ics")
                        },
                    )
                }
            }
        }
    }

    val bodyContent: @Composable (AcademicQueryScreen, PaddingValues) -> Unit = { currentScreen, padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                error?.let { message ->
                    item(key = "query_error") {
                        ExpressiveMessageBanner(
                            message = message,
                            isError = true,
                            actionLabel = if (!loading && !loadingChoices) "重试" else null,
                            onAction = { retry() },
                        )
                    }
                }

                if (currentScreen == AcademicQueryScreen.Form) {
                    form?.let { currentForm ->
                        if (currentForm.choices.isNotEmpty()) {
                            item(key = "form_choices") {
                                ExpressiveSegmentedGroup {
                                    currentForm.choices.forEachIndexed { index, choice ->
                                        val selectedLabel = choice.values
                                            .firstOrNull { it.first == values[choice.key] }
                                            ?.second
                                            .orEmpty()
                                        ExpressiveSelectorRow(
                                            index = index,
                                            count = currentForm.choices.size,
                                            label = choice.label,
                                            selectedLabel = selectedLabel,
                                            onClick = {
                                                if (!loading && !loadingChoices) {
                                                    activeChoicePicker = choice
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (currentForm.notices.isNotEmpty()) {
                            item(key = "form_notices") {
                                ExpressiveMessageBanner(
                                    message = currentForm.notices.joinToString("\n"),
                                )
                            }
                        }

                        if (feature.key == "classrooms") {
                            item(key = "classroom_extra_fields") {
                                ClassroomExtraFieldsCard(
                                    values = values,
                                    dateText = dateText,
                                    onDateTextChange = { dateText = it },
                                    onOpenViewPicker = { showClassroomViewPicker = true },
                                    onOpenDatePicker = { showDatePicker = true },
                                )
                            }
                        }

                        item(key = "form_submit") {
                            Button(
                                onClick = {
                                    if (feature.key == "grades") gradeTab = GradeTab.Records
                                    query()
                                },
                                enabled = !loading && !loadingChoices,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = ButtonDefaults.MediumContentPadding,
                            ) {
                                Text("查询")
                            }
                        }
                    }
                } else {
                    if (feature.key == "grades" && currentScreen == AcademicQueryScreen.Results) {
                        item(key = "grades_tab_toggle") {
                            ExpressiveConnectedToggleRow(
                                options = listOf("学业总览", "成绩表单"),
                                selectedIndex = if (gradeTab == GradeTab.Overview) 0 else 1,
                                onSelect = { idx ->
                                    if (!loading) {
                                        val nextTab = if (idx == 0) GradeTab.Overview else GradeTab.Records
                                        gradeTab = nextTab
                                        if (nextTab == GradeTab.Overview &&
                                            (!values["xnm"].isNullOrBlank() || !values["xqm"].isNullOrBlank())
                                        ) {
                                            values["xnm"] = ""
                                            values["xqm"] = ""
                                            rows = null
                                            cachedAt = null
                                            query()
                                        }
                                    }
                                },
                            )
                        }
                    }

                    if (feature.key == "grades" && gradeTab == GradeTab.Overview && currentScreen == AcademicQueryScreen.Results) {
                        item(key = "grades_overview") {
                            GradeOverviewContent(
                                rows = visibleRows,
                                track = values["kcbj"].orEmpty().ifBlank { "1" },
                                loading = loading,
                                onTrackChange = { nextTrack ->
                                    if (!loading && nextTrack != values["kcbj"]) {
                                        values["kcbj"] = nextTrack
                                        values["xnm"] = ""
                                        values["xqm"] = ""
                                        rows = null
                                        cachedAt = null
                                        query()
                                    }
                                },
                            )
                        }
                    } else {
                        if (feature.key == "grades") {
                            item(key = "grades_filter") {
                                val filterLabel = listOfNotNull(
                                    form?.choices?.firstOrNull { it.key == "xnm" }?.values
                                        ?.firstOrNull { it.first == values["xnm"] }?.second,
                                    form?.choices?.firstOrNull { it.key == "xqm" }?.values
                                        ?.firstOrNull { it.first == values["xqm"] }?.second,
                                ).joinToString(" · ")
                                ExpressiveSegmentedGroup {
                                    ExpressiveSegmentedActionRow(
                                        index = 0,
                                        count = 1,
                                        title = "筛选学年与学期",
                                        value = filterLabel.ifBlank { "全部" },
                                        leading = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                                        onClick = { screen = AcademicQueryScreen.Form },
                                    )
                                }
                            }
                        }

                        cachedAt?.takeIf { curriculum == null }?.let { saved ->
                            item(key = "cached_timestamp") {
                                Text(
                                    text = "上次查询：" + DateFormat.getDateTimeInstance().format(Date(saved)),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (error == null && visibleRows?.isEmpty() == true) {
                            item(key = "results_empty") {
                                ExpressiveEmptyCard(
                                    icon = Icons.Rounded.Inbox,
                                    title = when (recordType) {
                                        "exams" -> "所选学期暂无考试安排"
                                        "grades" -> "所选学期暂无成绩，请尝试其他学期"
                                        "textbooks" -> "所选学期暂无教材记录"
                                        else -> "暂无符合条件的记录"
                                    },
                                )
                            }
                        }

                        if (feature.key == "classrooms" && visibleRows?.firstOrNull()?.has("_freeSessions") == true) {
                            item(key = "rooms_legend") {
                                Text(
                                    text = "空：该时段可用；满：该时段不满足空闲条件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            item(key = "rooms_header") {
                                ClassroomMatrixRow(
                                    name = "教室",
                                    columns = AcademicRecords.daySessions,
                                    header = true,
                                )
                            }
                            items(
                                count = visibleRows.size,
                                key = { index -> "room_${visibleRows[index].optString("cdmc")}_$index" },
                            ) { index ->
                                val row = visibleRows[index]
                                ClassroomMatrixRow(
                                    name = row.optString("cdmc"),
                                    columns = (0..4).map {
                                        if (row.optString("_freeSessions").contains(it.toString())) "空" else "满"
                                    },
                                )
                            }
                        } else {
                            val activeRecords = filtered.matches
                            if (searchQuery.isNotBlank()) {
                                item(key = "active_filter_count") {
                                    Text(
                                        text = "当前关键词“$searchQuery”共匹配 ${activeRecords.size} 条记录",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            items(
                                count = activeRecords.size,
                                key = { index -> "record_${activeRecords[index].second.title}_$index" },
                            ) { index ->
                                val (row, record) = activeRecords[index]
                                AcademicResultItem(
                                    recordType = recordType,
                                    row = row,
                                    record = record,
                                    examNow = examNow,
                                    clickableCurriculum = recordType == "cultivation" &&
                                        row.optString("jxzxjhxx_id").isNotBlank() &&
                                        !loading,
                                    onOpenCurriculum = { openCurriculum(row, record.title) },
                                )
                            }
                        }

                        if (feature.key == "exams") {
                            examSemesterLabel(form, values)?.let { sem ->
                                item(key = "exams_semester_footer") {
                                    Text(
                                        text = "查询学期：$sem",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (loading || loadingChoices) {
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

    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            ExpressiveMotion.directionalSlideTransform(motionScheme, targetState.ordinal >= initialState.ordinal)
        },
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (screen != AcademicQueryScreen.Form) Modifier.predictiveBackContainer(screenBackState) else Modifier,
            ),
        label = "AcademicQueryScreenTransition",
    ) { currentScreen ->
        if (searchable && currentScreen != AcademicQueryScreen.Form) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    ExpressiveAcademicSearchAppBar(
                        textFieldState = searchTextField,
                        placeholder = searchPlaceholder,
                        onBack = close,
                        onSearchSubmit = {
                            if (feature.key == "grades") gradeTab = GradeTab.Records
                        },
                        searchBarState = searchBarState,
                        actions = topActions,
                    ) { collapse ->
                        val matches = filtered.matches
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "search_header") {
                                Text(
                                    text = if (searchQuery.isBlank()) {
                                        "共 ${recordsTotalLabel(recordSnapshot.records.size)} · 输入关键词实时过滤"
                                    } else {
                                        "找到 ${matches.size} 条匹配结果"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (matches.isEmpty() && recordSnapshot.records.isNotEmpty()) {
                                item(key = "search_empty") {
                                    ExpressiveEmptyCard(
                                        icon = Icons.Rounded.Search,
                                        title = "没有匹配的记录",
                                        subtitle = "试试更换课程名、教师或代码关键词",
                                    )
                                }
                            }
                            items(
                                count = matches.size,
                                key = { index -> "search_${matches[index].second.title}_$index" },
                            ) { index ->
                                val (row, record) = matches[index]
                                AcademicResultItem(
                                    recordType = recordType,
                                    row = row,
                                    record = record,
                                    examNow = examNow,
                                    clickableCurriculum = recordType == "cultivation" &&
                                        row.optString("jxzxjhxx_id").isNotBlank() &&
                                        !loading,
                                    onOpenCurriculum = {
                                        collapse()
                                        openCurriculum(row, record.title)
                                    },
                                )
                            }
                        }
                    }
                },
                content = { padding -> bodyContent(currentScreen, padding) },
            )
        } else {
            ExpressiveSubPageScaffold(
                title = pageTitle,
                onBack = close,
                actions = topActions,
                content = { padding -> bodyContent(currentScreen, padding) },
            )
        }
    }
}

private fun recordsTotalLabel(count: Int): String = "$count 条记录"

@Composable
private fun ClassroomExtraFieldsCard(
    values: MutableMap<String, String>,
    dateText: String,
    onDateTextChange: (String) -> Unit,
    onOpenViewPicker: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf(
                "cdmc" to "教室名称或编号（可选）",
                "qszws" to "最小座位数（可选）",
                "jszws" to "最大座位数（可选）",
            ).forEach { (key, label) ->
                OutlinedTextField(
                    value = values[key].orEmpty(),
                    onValueChange = { values[key] = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
            }

            ExpressiveSegmentedGroup {
                ExpressiveSelectorRow(
                    index = 0,
                    count = 1,
                    label = "查询方式",
                    selectedLabel = if (values["view"] == "day") "当日分时段总览" else "按节次筛选",
                    onClick = onOpenViewPicker,
                )
            }

            if (values["view"] == "day") {
                OutlinedTextField(
                    value = dateText,
                    onValueChange = onDateTextChange,
                    label = { Text("查询日期") },
                    placeholder = { Text("YYYY-MM-DD") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = onOpenDatePicker) {
                            Icon(Icons.Rounded.DateRange, contentDescription = "选择日期")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onDateTextChange(LocalDate.now().toString()) },
                        label = { Text("今天") },
                        leadingIcon = {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                    )
                    AssistChip(
                        onClick = { onDateTextChange(LocalDate.now().plusDays(1).toString()) },
                        label = { Text("明天") },
                    )
                    AssistChip(
                        onClick = onOpenDatePicker,
                        label = { Text("选择日期") },
                    )
                }
            } else {
                listOf(
                    "weeks" to "周次（如 1-4,6）",
                    "weekdays" to "星期（1 为周一，7 为周日）",
                    "sessions" to "节次（如 1-2,5-6）",
                ).forEach { (key, label) ->
                    OutlinedTextField(
                        value = values[key].orEmpty(),
                        onValueChange = { values[key] = it },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                    )
                }
            }
        }
    }
}

@Composable
private fun AcademicResultItem(
    recordType: String,
    row: JSONObject,
    record: AcademicRecord,
    examNow: LocalDateTime,
    clickableCurriculum: Boolean,
    onOpenCurriculum: () -> Unit,
) {
    if (recordType == "exams") {
        ExamCard(record = record, now = examNow)
        return
    }
    val totalCount = 1 + record.fields.size
    ExpressiveSegmentedGroup {
        if (clickableCurriculum) {
            SegmentedListItem(
                onClick = onOpenCurriculum,
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = totalCount),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                ),
                trailingContent = {
                    Text(
                        text = "查看课程",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                content = {
                    Text(record.title, style = MaterialTheme.typography.titleMediumEmphasized)
                },
            )
        } else {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = totalCount),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                content = {
                    Text(record.title, style = MaterialTheme.typography.titleMediumEmphasized)
                },
            )
        }
        record.fields.forEachIndexed { fieldIndex, (label, value) ->
            ExpressiveSegmentedInfoRow(
                index = fieldIndex + 1,
                count = totalCount,
                title = label,
                value = value,
            )
        }
    }
}

@Composable
private fun ClassroomMatrixRow(
    name: String,
    columns: List<String>,
    header: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (header) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
        color = if (header) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = name,
                modifier = Modifier.weight(2.5f),
                style = if (header) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            columns.forEach { value ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (header) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        val free = value == "空"
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (free) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        ) {
                            Text(
                                text = value,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (free) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (free) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

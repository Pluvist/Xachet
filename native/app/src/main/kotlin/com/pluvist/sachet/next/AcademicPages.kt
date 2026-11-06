package com.pluvist.sachet.next

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.scaffold.*
import io.github.neoruaa.glasmio.ui.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

private enum class AcademicQueryScreen { Form, Results, Curriculum, SearchResults }
private enum class GradeTab { Overview, Records }
private data class AcademicRecordSnapshot(
    val source:List<JSONObject>?,
    val type:String,
    val records:List<Pair<JSONObject,AcademicRecord>>,
    val searchTexts:List<String> = emptyList(),
)
private data class AcademicFilteredRecords(
    val source:List<Pair<JSONObject,AcademicRecord>>,
    val keyword:String,
    val matches:List<Pair<JSONObject,AcademicRecord>>,
)

@Composable internal fun QueryPage(feature:QueryFeature,repository:AcademicRepository,back:()->Unit) {
    var form by remember(feature){mutableStateOf<AcademicForm?>(null)}
    val values=remember(feature){mutableStateMapOf<String,String>()}
    var rows by remember(feature){mutableStateOf<List<JSONObject>?>(null)}
    var curriculum by remember(feature){mutableStateOf<List<JSONObject>?>(null)}
    var curriculumTitle by remember(feature){mutableStateOf("")}
    var curriculumMajorId by remember(feature){mutableStateOf<String?>(null)}
    var loading by remember(feature){mutableStateOf(true)}
    var loadingForm by remember(feature){mutableStateOf(true)}
    var formReloads by remember(feature){mutableIntStateOf(0)}
    var loadingChoices by remember(feature){mutableStateOf(false)}
    var error by remember(feature){mutableStateOf<String?>(null)}
    var cachedAt by remember(feature){mutableStateOf<Long?>(null)}
    var examMenu by remember(feature){mutableStateOf(false)}
    var reload by remember(feature){mutableIntStateOf(0)}
    var completedReloads by remember(feature){mutableIntStateOf(0)}
    var dependencyError by remember(feature){mutableStateOf(false)}
    var dateText by remember(feature){mutableStateOf(LocalDate.now().toString())}
    // Features that already loaded and showed rows on entry continue to open
    // straight into results. Their filters remain one Back action away.
    var screen by remember(feature){mutableStateOf(if(feature.key in setOf("grades","exams","textbooks","cultivation"))
        AcademicQueryScreen.Results else AcademicQueryScreen.Form)}
    var gradeTab by remember(feature){mutableStateOf(GradeTab.Overview)}
    var listSearchReturnScreen by remember(feature){mutableStateOf(AcademicQueryScreen.Form)}
    var curriculumSearchReturnScreen by remember(feature){mutableStateOf(AcademicQueryScreen.Curriculum)}
    var curriculumReturnScreen by remember(feature){mutableStateOf(AcademicQueryScreen.Results)}
    var listKeyword by remember(feature){mutableStateOf("")}
    var curriculumKeyword by remember(feature){mutableStateOf("")}
    var pendingSearch by remember(feature){mutableStateOf<String?>(null)}
    var failedDestination by remember(feature){mutableStateOf<AcademicQueryScreen?>(null)}
    var curriculumRequestId by remember(feature){mutableIntStateOf(0)}
    val scope=rememberCoroutineScope()
    val examNow by produceState(LocalDateTime.now(),feature.key){
        if(feature.key=="exams")while(true){delay(30_000);value=LocalDateTime.now()}
    }
    val context=LocalContext.current
    val close:()->Unit={curriculumRequestId++;when(screen) {
        AcademicQueryScreen.SearchResults->{screen=if(curriculum!=null)curriculumSearchReturnScreen else listSearchReturnScreen;error=null}
        AcademicQueryScreen.Curriculum->{curriculum=null;curriculumMajorId=null;screen=curriculumReturnScreen;error=null}
        AcademicQueryScreen.Results->{screen=AcademicQueryScreen.Form;error=null}
        AcademicQueryScreen.Form->back()
    }}
    BackHandler(screen!=AcademicQueryScreen.Form){close()}
    val exporter=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/calendar")){uri->uri?.let{scope.launch{
        try{withContext(Dispatchers.IO){
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(CalendarExport.exams(rows.orEmpty()))}?:error("无法写入日历文件")
        }}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
    }}}
    fun query(destination:AcademicQueryScreen=AcademicQueryScreen.Results) { scope.launch {
        if(loading || loadingChoices)return@launch
        val requestId=++curriculumRequestId
        loading=true;error=null;curriculum=null
        try {
            if(feature.key=="classrooms" && values["view"]=="day") {
                val date=runCatching{LocalDate.parse(dateText)}.getOrElse{throw IllegalArgumentException("请输入有效日期，格式为 YYYY-MM-DD")}
                require(values["dm_cx"]==form?.choices?.firstOrNull{it.key=="dm_cx"}?.selected){ "其他学期请使用按节次筛选，并指定周次与星期" }
                val week=CourseClock.week(date,repository.semester())
                require(week in 1..60){ "所选日期不在当前课表学期内，请选择相应学期并使用按节次筛选" }
                values["weeks"]=week.toString();values["weekdays"]=date.dayOfWeek.value.toString()
            }
            val result=repository.query(feature,values.toMap())
            if(requestId==curriculumRequestId){rows=result;cachedAt=null;screen=destination;failedDestination=null}
        }catch(e:CancellationException){throw e}catch(e:Exception){if(requestId==curriculumRequestId){failedDestination=destination;error=e.message}}finally{loading=false;completedReloads++}
    } }
    fun retry(visibleScreen:AcademicQueryScreen) {
        if(feature.key=="cultivation" && curriculum!=null){
            val majorId=curriculumMajorId
            if(majorId==null){error="请重新选择培养方案";return}
            scope.launch {
                val requestId=++curriculumRequestId
                loading=true;error=null
                try{val courses=repository.curriculum(majorId)
                    if(requestId==curriculumRequestId && curriculum!=null)curriculum=courses}
                catch(e:CancellationException){throw e}catch(e:Exception){if(requestId==curriculumRequestId)error=e.message}
                finally{loading=false;completedReloads++}
            }
        }else if(form==null || dependencyError)reload++
        else query(failedDestination?:if(visibleScreen==AcademicQueryScreen.SearchResults)AcademicQueryScreen.SearchResults else AcademicQueryScreen.Results)
    }
    LaunchedEffect(feature,reload) {
        loading=true;loadingForm=true;error=null
        try {
            if(reload==0)repository.cachedQuery(feature)?.let{cached->
                if(feature.key!="grades" || (cached.parameters["xnm"].isNullOrBlank() &&
                    cached.parameters["xqm"].isNullOrBlank() && cached.parameters["kcbj"]=="1")) {
                    rows=cached.rows;cachedAt=cached.savedAt
                }
                if(feature.key!="grades")values.putAll(cached.parameters)
            }
            form=repository.form(feature)
            if(feature.key=="grades"){
                values["xnm"]="";values["xqm"]="";values["kcbj"]="1"
            }
            form!!.choices.forEach{choice->if(choice.values.none{it.first==values[choice.key]})values[choice.key]=choice.selected}
            if(feature.key=="classrooms") {
                values.putIfAbsent("weeks",CourseClock.week(LocalDate.now(),repository.semester()).coerceIn(1,60).toString())
                values.putIfAbsent("weekdays",LocalDate.now().dayOfWeek.value.toString())
                values.putIfAbsent("sessions","1-2");values.putIfAbsent("view","day")
            }
            if(feature.key in listOf("grades","exams","textbooks","cultivation")) {
                rows=repository.query(feature,values.toMap());cachedAt=null
            }
            if(failedDestination==AcademicQueryScreen.SearchResults){
                if(rows==null){rows=repository.query(feature,values.toMap());cachedAt=null}
                screen=AcademicQueryScreen.SearchResults;failedDestination=null
            }
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{loading=false;loadingForm=false;completedReloads++;formReloads++}
    }
    LaunchedEffect(reload,form?.choices?.isNotEmpty(),values["jg_id"],values["dm_cx"],values["xqh_id"]) {
        if(form==null)return@LaunchedEffect
        val key=when(feature.key){"cultivation"->"zyh_id_cx";"classrooms"->"lh";else->return@LaunchedEffect}
        loadingChoices=true;dependencyError=false
        val previous=values[key].orEmpty()
        values[key]=""
        form=form?.copy(choices=form!!.choices.map{if(it.key==key)it.copy(values=listOf("" to "全部"))else it})
        try {
            val options=if(feature.key=="cultivation") {
                val school=values["jg_id"].orEmpty()
                if(school.isBlank())listOf("" to "全部") else listOf("" to "全部")+repository.schoolMajors(school)
            }else repository.teachingBuildings(values["dm_cx"].orEmpty(),values["xqh_id"]?:"02")
            form=form?.copy(choices=form!!.choices.map{if(it.key==key)it.copy(values=options)else it})
            values[key]=previous.takeIf{old->options.any{it.first==old}}.orEmpty()
        }catch(e:CancellationException){throw e}catch(e:Exception){dependencyError=true;error=e.message}finally{loadingChoices=false}
    }
    val visibleRows=curriculum?:rows
    val recordType=if(curriculum!=null)"curriculum" else feature.key
    val recordSnapshot by produceState(AcademicRecordSnapshot(null,"",emptyList()),visibleRows,recordType) {
        value=withContext(Dispatchers.Default){
            val parsed=visibleRows.orEmpty().map{it to AcademicRecords.record(recordType,it)}
            AcademicRecordSnapshot(visibleRows,recordType,parsed,parsed.map{AcademicKeywordSearch.searchableText(it.second)})
        }
    }
    val records=recordSnapshot.records
    val searchTexts=recordSnapshot.searchTexts
    val recordsReady=recordSnapshot.source===visibleRows && recordSnapshot.type==recordType
    val search=LocalAcademicSearch.current
    val searchPlaceholder=when(feature.key){
        "grades"->"搜索成绩"
        "gpa"->"搜索绩点或排名"
        "exams"->"搜索考试"
        "textbooks"->"搜索教材"
        "cultivation"->if(curriculum!=null)"搜索课程" else "搜索专业"
        else->"搜索记录"
    }
    val searchSession=remember(feature,searchPlaceholder){AcademicSearchSession(feature.key,searchPlaceholder)}
    val searchable=feature.key in setOf("grades","gpa","exams","textbooks","cultivation")
    val searchAvailable=searchable&&!loading&&!loadingChoices
    SideEffect { searchSession.onSubmit={term->
        // A major-to-curriculum fetch changes the search domain. Ignore an
        // input event from the old domain until the new list is visible.
        if(!loading&&!loadingChoices){
            error=null;failedDestination=null
            if(curriculum!=null){
                curriculumKeyword=term.trim()
                if(screen!=AcademicQueryScreen.SearchResults)curriculumSearchReturnScreen=screen
            }else{
                listKeyword=term.trim()
                if(screen!=AcademicQueryScreen.SearchResults)listSearchReturnScreen=screen
                if(feature.key=="grades")gradeTab=GradeTab.Records
            }
            pendingSearch=term
        }
    } }
    DisposableEffect(search,searchAvailable,searchSession){
        if(searchAvailable)search?.attach(searchSession)
        onDispose{search?.detach(searchSession);searchSession.onSubmit=null;searchSession.close()}
    }
    LaunchedEffect(pendingSearch,loading,loadingChoices,rows,curriculum) {
        if(pendingSearch!=null&&!loading&&!loadingChoices){
            pendingSearch=null
            if(curriculum!=null || rows!=null)screen=AcademicQueryScreen.SearchResults
            else query(AcademicQueryScreen.SearchResults)
        }
    }
    val keyword=when {
        !searchable->""
        searchSession.expanded->searchSession.query.text
        pendingSearch!=null->pendingSearch.orEmpty()
        screen==AcademicQueryScreen.SearchResults->if(curriculum!=null)curriculumKeyword else listKeyword
        else->""
    }
    val filtered by produceState(AcademicFilteredRecords(emptyList(),"",emptyList()),recordSnapshot,keyword) {
        if(keyword.isNotBlank())delay(70)
        val terms=AcademicKeywordSearch.terms(keyword)
        val matches=if(terms.isEmpty())records else withContext(Dispatchers.Default){
            records.filterIndexed{index,_->
                if(index%64==0)ensureActive()
                AcademicKeywordSearch.matchesPrepared(searchTexts[index],terms)
            }
        }
        value=AcademicFilteredRecords(records,keyword,matches)
    }
    val filtering=!recordsReady || filtered.source!==records || filtered.keyword!=keyword
    val filteredRecords=if(filtering)emptyList()else filtered.matches
    val resultsAlpha=rememberCrestoReloadAlpha(loading,completedReloads)
    val formAlpha=rememberCrestoReloadAlpha(loadingForm,formReloads)
    val errorAlpha=rememberCrestoReloadAlpha(loading||loadingChoices,error to completedReloads)
    var idleError by remember(feature){mutableStateOf<String?>(null)}
    SideEffect{if(!loading&&!loadingChoices)idleError=error}
    val shownError=error?:idleError.takeIf{loading||loadingChoices}
    Box(Modifier.fillMaxSize()) {
    AnimatedContent(targetState=screen,transitionSpec={
        fadeIn(animationSpec=spring(dampingRatio=.85f,stiffness=450f)) togetherWith
            fadeOut(animationSpec=spring(dampingRatio=.9f,stiffness=550f))
    },label="Academic query and results") { visibleScreen ->
    val title=when(visibleScreen){
        AcademicQueryScreen.Form->feature.title
        AcademicQueryScreen.Results->if(feature.key in setOf("grades","exams"))feature.title else "${feature.title}结果"
        AcademicQueryScreen.Curriculum->curriculumTitle
        AcademicQueryScreen.SearchResults->"搜索结果"
    }
    GlasmioPinnedListPage(title,bottomPadding=180.dp,onNavigateBack=close,actions={
        if(feature.key=="exams") {
            val menuBackdrop=LocalAnchoredMenuBackdrop.current?:LocalGlassBackdrop.current
            val menuDepth=LocalAnchoredMenuDepth.current
            val depthOwner=remember{Any()}
            DisposableEffect(menuDepth,depthOwner){onDispose{menuDepth?.remove(depthOwner)}}
            LaunchedEffect(menuDepth,depthOwner,examMenu){menuDepth?.setExpanded(depthOwner,examMenu)}
            val density=LocalDensity.current
            val textMeasurer=rememberTextMeasurer(cacheSize=8)
            val menuWidth=minOf(228.dp*AnchoredMenuSizeScale,
                (LocalConfiguration.current.screenWidthDp-24).coerceAtLeast(160).dp)
            val menuPadding=10.dp*AnchoredMenuSizeScale
            val baseRowHeight=44.dp*AnchoredMenuSizeScale
            val titleStyle=IosTypography.body.let{it.copy(fontSize=it.fontSize*AnchoredMenuSizeScale,
                lineHeight=it.lineHeight*AnchoredMenuSizeScale)}
            val titleWidthPx=with(density){(menuWidth-menuPadding*2-32.dp).toPx().toInt().coerceAtLeast(1)}
            val lineHeight=with(density){titleStyle.lineHeight.toDp()}
            val menuLines=remember(textMeasurer,titleWidthPx,titleStyle){
                listOf("筛选学年与学期","导出考试日历").map{label->
                    textMeasurer.measure(AnnotatedString(label),style=titleStyle,maxLines=10,
                        constraints=Constraints(maxWidth=titleWidthPx)).lineCount.coerceAtLeast(1)
                }
            }
            val menuRowHeights=menuLines.map{lines->
                maxOf(baseRowHeight,lineHeight+8.dp*AnchoredMenuSizeScale)+lineHeight*(lines-1)
            }
            CompositionLocalProvider(LocalGlassAppearance provides footerGlassAppearance(LocalGlassAppearance.current,LocalGlassColors.current.isDark)) {
                GlasmioMenu(expanded=examMenu,onExpandedChange={examMenu=it},itemCount=2,
                    backdrop=menuBackdrop,menuWidth=menuWidth,keepAnchorVisible=true,
                    heightOverride=menuPadding*2+menuRowHeights[0]+menuRowHeights[1],
                    contentPadding=menuPadding,exportChildBackdrop=false,
                    anchor={open->GlasmioButton(onClick=open){
                        Icon(Icons.Default.MoreVert,"更多考试操作",Modifier.size(20.dp))
                    }}) { childBackdrop,dismiss->
                    GlasmioMenuItem("筛选学年与学期",onClick={dismiss();screen=AcademicQueryScreen.Form},backdrop=childBackdrop,
                        rowHeight=menuRowHeights[0],textScale=AnchoredMenuSizeScale,
                        maxTitleLines=menuLines[0],reserveLeadingIconSpace=false)
                    GlasmioMenuItem("导出考试日历",onClick={dismiss();exporter.launch("Xachet-Exams.ics")},
                        enabled=!rows.isNullOrEmpty(),backdrop=childBackdrop,
                        rowHeight=menuRowHeights[1],textScale=AnchoredMenuSizeScale,
                        maxTitleLines=menuLines[1],reserveLeadingIconSpace=false)
                }
            }
        }
    }) {
        if(visibleScreen==AcademicQueryScreen.Form) {
            item { GlasmioGroupedList { form?.choices?.forEach { choice -> key(choice.key) {
                val dependent=choice.key=="zyh_id_cx"||choice.key=="lh"
                val choiceAlpha=rememberCrestoReloadAlpha(loadingForm||(loadingChoices&&dependent),formReloads to choice.values)
                AnchoredGlassChoiceRow(choice.label,modifier=Modifier.reloadFade(choiceAlpha),
                    detail=choice.values.firstOrNull{it.first==values[choice.key]}?.second,
                    options=choice.values,selected=values[choice.key],enabled=!loading&&!loadingChoices,
                    onSelect={values[choice.key]=it})
            } } } }
            form?.notices?.forEach{notice->item{Text(notice,modifier=Modifier.reloadFade(formAlpha),style=IosTypography.footnote,color=LocalGlassColors.current.secondaryContent)}}
            if(feature.key=="classrooms") {
                item{GlasmioGroupedList {
                    listOf("cdmc" to "教室名称或编号（可选）","qszws" to "最小座位数（可选）","jszws" to "最大座位数（可选）").forEach{(key,label)->
                        GlasmioTextField(values[key].orEmpty(),{values[key]=it},placeholder=label)
                    }
                    AnchoredGlassChoiceRow("查询方式",detail=if(values["view"]=="day")"当日分时段总览"else "按节次筛选",
                        options=listOf("day" to "当日分时段总览","filter" to "按节次筛选"),selected=values["view"],
                        enabled=!loading&&!loadingChoices,onSelect={values["view"]=it})
                    if(values["view"]=="day") {
                        Text("查询日期",style=IosTypography.footnote,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp))
                        GlasmioTextField(dateText,{dateText=it},placeholder="YYYY-MM-DD")
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                            GlasmioButton(onClick={dateText=LocalDate.now().toString()}){Text("今天")}
                            GlasmioButton(onClick={dateText=LocalDate.now().plusDays(1).toString()}){Text("明天")}
                        }
                    }else listOf("weeks" to "周次（如 1-4,6）","weekdays" to "星期（1 为周一，7 为周日）","sessions" to "节次（如 1-2,5-6）").forEach{(key,label)->
                        Text(label,style=IosTypography.footnote,modifier=Modifier.padding(horizontal=16.dp,vertical=8.dp))
                        GlasmioTextField(values[key].orEmpty(),{values[key]=it},placeholder=label)
                    }
                }}
            }
            if(form!=null)item{GlasmioButton(onClick={if(feature.key=="grades")gradeTab=GradeTab.Records;query()},enabled=!loading&&!loadingChoices,modifier=Modifier.fillMaxWidth(),emphasis=GlassEmphasis.Prominent){Text("查询")}}
        }
        shownError?.let{message->item{GlasmioGroupedList(Modifier.reloadFade(errorAlpha)){
            Text(message,color=LocalGlassColors.current.destructive,modifier=Modifier.padding(16.dp))
            GlasmioListRow("重试",onClick=if(loading||loadingChoices)null else {{retry(visibleScreen)}})
        }}}
        if(visibleScreen!=AcademicQueryScreen.Form) {
        if(feature.key=="grades")item{
            GlasmioSegmentedControl(
                items=listOf(GradeTab.Overview to "学业总览",GradeTab.Records to "成绩表单"),
                selected=gradeTab,
                onSelected={selected->
                    gradeTab=selected
                    if(selected==GradeTab.Overview){
                        if(screen==AcademicQueryScreen.SearchResults)screen=AcademicQueryScreen.Results
                        if(!values["xnm"].isNullOrBlank() || !values["xqm"].isNullOrBlank()){
                            values["xnm"]="";values["xqm"]="";rows=null;cachedAt=null;query()
                        }
                    }
                },
                modifier=Modifier.fillMaxWidth().padding(bottom=12.dp),
                height=42.dp,
                enabled=!loading,
            )
        }
        if(feature.key=="grades" && gradeTab==GradeTab.Overview && visibleScreen==AcademicQueryScreen.Results){
            item{GradeOverviewContent(
                rows=visibleRows,
                track=values["kcbj"].orEmpty().ifBlank{"1"},
                loading=loading,
                onTrackChange={track->
                    if(!loading && track!=values["kcbj"]){
                        values["kcbj"]=track;values["xnm"]="";values["xqm"]="";rows=null;cachedAt=null;query()
                    }
                },
            )}
        }else{
        if(feature.key=="grades" && visibleScreen==AcademicQueryScreen.Results)item{
            GlasmioGroupedList{
                GlasmioListRow("筛选学年与学期",detail=listOfNotNull(
                    form?.choices?.firstOrNull{it.key=="xnm"}?.values?.firstOrNull{it.first==values["xnm"]}?.second,
                    form?.choices?.firstOrNull{it.key=="xqm"}?.values?.firstOrNull{it.first==values["xqm"]}?.second,
                ).joinToString(" · "),onClick={screen=AcademicQueryScreen.Form})
            }
        }
        cachedAt?.takeIf{curriculum==null}?.let{saved->item{Text("上次查询："+java.text.DateFormat.getDateTimeInstance().format(java.util.Date(saved)),modifier=Modifier.reloadFade(resultsAlpha),style=IosTypography.caption1)}}
        if(error==null && visibleRows?.isEmpty()==true)item{Text(when(recordType){"exams"->"所选学期暂无考试安排";"grades"->"所选学期暂无成绩，请尝试其他学期";"textbooks"->"所选学期暂无教材记录";else->"暂无符合条件的记录"},style=IosTypography.body,modifier=Modifier.padding(vertical=20.dp).reloadFade(resultsAlpha))}
        if(feature.key=="classrooms" && visibleRows?.firstOrNull()?.has("_freeSessions")==true) {
            item{Text("空：该时段可用；满：该时段不满足空闲条件",modifier=Modifier.reloadFade(resultsAlpha),style=IosTypography.footnote)}
            item{ClassroomMatrixRow("教室",AcademicRecords.daySessions,header=true,modifier=Modifier.reloadFade(resultsAlpha))}
            items(visibleRows.size){index->val row=visibleRows[index]
                ClassroomMatrixRow(row.optString("cdmc"),(0..4).map{if(row.optString("_freeSessions").contains(it.toString()))"空"else "满"},modifier=Modifier.reloadFade(resultsAlpha))
            }
        }else {
            if(visibleScreen==AcademicQueryScreen.SearchResults && filtering)item{Text("正在筛选…",style=IosTypography.footnote)}
            if(visibleScreen==AcademicQueryScreen.SearchResults && !filtering && visibleRows!=null)item{Text("找到 ${filteredRecords.size} 条结果",modifier=Modifier.reloadFade(resultsAlpha),style=IosTypography.footnote)}
            if(visibleScreen==AcademicQueryScreen.SearchResults && !filtering && filteredRecords.isEmpty() && !visibleRows.isNullOrEmpty())item{Text("没有匹配的记录",style=IosTypography.body,modifier=Modifier.padding(vertical=20.dp).reloadFade(resultsAlpha))}
            items(filteredRecords.size){index->val (row,record)=filteredRecords[index]
            if(recordType=="exams")ExamCard(record,examNow,Modifier.reloadFade(resultsAlpha))
            else GlasmioGroupedList(Modifier.reloadFade(resultsAlpha)) {
                GlasmioListRow(record.title,showTopSeparator=false,onClick=if(recordType=="cultivation"&&row.optString("jxzxjhxx_id").isNotBlank()&&!loading){{scope.launch{
                    val requestId=++curriculumRequestId
                    val sourceScreen=screen
                    loading=true;error=null
                    try{
                        val courses=repository.curriculum(row.getString("jxzxjhxx_id"))
                        if(requestId!=curriculumRequestId || screen!=sourceScreen)return@launch
                        curriculumReturnScreen=visibleScreen;curriculumTitle=record.title;curriculumMajorId=row.getString("jxzxjhxx_id");curriculum=courses;screen=AcademicQueryScreen.Curriculum
                    }catch(e:CancellationException){throw e}catch(e:Exception){if(requestId==curriculumRequestId)error=e.message}finally{loading=false;completedReloads++}
                }}}else null)
                record.fields.forEach{(label,value)->GlasmioListRow(label,detail=value)}
            }
        }
            if(feature.key=="exams")examSemesterLabel(form,values)?.let{semester->item{
                Text("查询学期：$semester",style=IosTypography.footnote,
                    color=LocalGlassColors.current.secondaryContent,modifier=Modifier.padding(top=12.dp).reloadFade(resultsAlpha))
            }}
        }
    }
    }
    }
    }
    if(loading||loadingChoices)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
    }
}

@Composable private fun ClassroomMatrixRow(name:String,columns:List<String>,header:Boolean=false,modifier:Modifier=Modifier) {
    val colors=LocalGlassColors.current
    GlasmioGroupedList(modifier) { Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(name,Modifier.weight(2.5f),style=IosTypography.footnote)
        columns.forEach{value->Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(value,style=IosTypography.caption1,
            color=if(header)colors.secondaryContent else if(value=="空")colors.accent else colors.secondaryContent)}}
    } }
}

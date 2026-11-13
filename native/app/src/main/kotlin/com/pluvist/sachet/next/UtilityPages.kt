package com.pluvist.sachet.next

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.ui.*
import io.github.neoruaa.glasmio.scaffold.*
import kotlinx.coroutines.*
import java.io.File
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

@Composable internal fun SettingsPage(vm:SachetViewModel,back:()->Unit) {
    val context=LocalContext.current
    val settings=remember{vm.repository.settings()}
    var semester by remember{mutableStateOf(settings.optString("semesterStartDate").take(10))}
    var live by remember{mutableStateOf(settings.optBoolean("isEnableCourseLiveActivity",true))}
    var reminders by remember{mutableStateOf(settings.optBoolean("isEnableCourseReminders",true))}
    var theme by remember{mutableIntStateOf(settings.optInt("themeMode"))}
    var accent by remember{mutableStateOf(settings.optString("themeColor","#0088FF"))}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        scope.launch{BackgroundSetupSettings.refreshCourses(context)}
    }
    GlasmioPinnedListPage("设置",bottomPadding=180.dp,onNavigateBack=back) {
        item{Text("学期开始日期（周一）",style=IosTypography.footnote)}
        item{GlasmioGroupedList{
            SettingsIconTextField("semester",semester,{semester=it},"YYYY-MM-DD")
            GlasmioListRow("课程实时活动",subtitle="上课倒计时与下一节课程信息",leading={AppIconBadge("activity",compact=true)},trailing={GlasmioSwitch(live,{live=it})})
            GlasmioListRow("课前提醒",subtitle="第 1、5 节提前 20 分钟，其他节次提前 10 分钟",leading={AppIconBadge("reminders",compact=true)},trailing={GlasmioSwitch(reminders,{reminders=it})})
            AnchoredGlassChoiceRow("主题",detail=listOf("跟随系统","浅色","深色")[theme.coerceIn(0,2)],
                options=listOf("0" to "跟随系统","1" to "浅色","2" to "深色"),selected=theme.toString(),
                leading={AppIconBadge("theme",compact=true)},onSelect={theme=it.toIntOrNull()?:theme})
            SettingsIconTextField("accent",accent,{accent=it},"主题色，例如 #0088FF")
        }}
        item{GlasmioButton(onClick={scope.launch{
            try{
                android.graphics.Color.parseColor(accent)
                vm.saveSettings(mapOf("semesterStartDate" to semester,"isEnableCourseLiveActivity" to live,"isEnableCourseReminders" to reminders,"themeMode" to theme,"themeColor" to accent))
                if((live||reminders) && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                    BackgroundSetupSettings.requestNotifications(context as android.app.Activity){permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)}
                else if(reminders && !(context.getSystemService(android.app.AlarmManager::class.java)?.canScheduleExactAlarms()?:false))
                    BackgroundSetupSettings.open(context as android.app.Activity,"exactAlarms")
                back()
            }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        }},modifier=Modifier.fillMaxWidth()){Text("保存")}}
        error?.let{item{Text(it,color=LocalGlassColors.current.destructive)}}
    }
}

@Composable private fun SettingsIconTextField(key:String,value:String,onValueChange:(String)->Unit,placeholder:String) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.padding(start=16.dp),contentAlignment=Alignment.Center) { AppIconBadge(key,compact=true) }
        GlasmioTextField(value,onValueChange,modifier=Modifier.weight(1f),placeholder=placeholder)
    }
}

@Composable internal fun DataPage(data:AppData,vm:SachetViewModel,back:()->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var importKind by remember{mutableStateOf("schedule")}
    var error by remember{mutableStateOf<String?>(null)}
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{scope.launch{
        try{val body=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}?:error("无法打开文件")};if(importKind=="colors")vm.importColors(body)else vm.importSchedule(body)}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
    }}}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")){uri->uri?.let{scope.launch{
        try{withContext(Dispatchers.IO){val output=context.contentResolver.openOutputStream(uri)?:error("无法写入文件");output.bufferedWriter().use{it.write(CalendarExport.ics(data.courses,vm.repository.semester()))}}}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
    }}}
    GlasmioPinnedListPage("导入与导出",bottomPadding=180.dp,onNavigateBack=back) {
        item{GlasmioGroupedList{
            GlasmioListRow("导入课表 JSON",subtitle="支持原 Sachet 课表与教务原始数据",onClick={importKind="schedule";importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
            GlasmioListRow("导入课程配色",subtitle="兼容原 Sachet 配色 JSON",onClick={importKind="colors";importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
            GlasmioListRow("导出到日历",subtitle="按教学楼真实作息生成 ICS",onClick={exporter.launch("Xachet.ics")})
        }}
        error?.let{item{Text(it,color=LocalGlassColors.current.destructive)}}
    }
}

private enum class TranscriptView { FORM, RESULTS, PDF }

@Composable internal fun TranscriptPage(repository:AcademicRepository,back:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val search=LocalAcademicSearch.current
    val searchSession=remember{AcademicSearchSession("transcript","搜索成绩单")}
    var view by remember{mutableStateOf(TranscriptView.FORM)}
    var searchReturnView by remember{mutableStateOf(TranscriptView.FORM)}
    var documentReturnView by remember{mutableStateOf(TranscriptView.FORM)}
    var searchTerm by remember{mutableStateOf("")}
    var searchRequest by remember{mutableIntStateOf(0)}
    var matches by remember{mutableStateOf<List<TranscriptPageMatch>>(emptyList())}
    var indexedPages by remember{mutableStateOf<List<TranscriptTextPage>?>(null)}
    var indexedFile by remember{mutableStateOf<File?>(null)}
    var types by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())}
    var selected by remember{mutableStateOf("")}
    var loadingTypes by remember{mutableStateOf(true)}
    var downloading by remember{mutableStateOf(false)}
    var searching by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var file by remember{mutableStateOf<File?>(null)}
    var fileFormat by remember{mutableStateOf<String?>(null)}
    var page by remember{mutableIntStateOf(0)}
    var pageCount by remember{mutableIntStateOf(0)}
    var reload by remember{mutableIntStateOf(0)}
    var typeReloads by remember{mutableIntStateOf(0)}
    var pdfReloads by remember{mutableIntStateOf(0)}
    var searchReloads by remember{mutableIntStateOf(0)}
    var rendering by remember{mutableStateOf(false)}
    var renderRequest by remember{mutableIntStateOf(0)}
    var renderedPage by remember{mutableStateOf<Pair<Pair<File,Int>,Bitmap>?>(null)}
    var renderedRequest by remember{mutableIntStateOf(-1)}
    val formList=rememberLazyListState()
    val resultsList=rememberLazyListState()
    val pdfList=rememberLazyListState()
    val loading=loadingTypes||downloading||searching
    val navigateBack:()->Unit={when(view){
        TranscriptView.FORM->back()
        TranscriptView.RESULTS->view=searchReturnView
        TranscriptView.PDF->view=documentReturnView
    }}
    BackHandler(view!=TranscriptView.FORM&&!searchSession.expanded){navigateBack()}

    DisposableEffect(search,searchSession){
        search?.attach(searchSession)
        onDispose{search?.detach(searchSession);searchSession.onSubmit=null;searchSession.close()}
    }
    SideEffect{searchSession.onSubmit={term->
        if(view!=TranscriptView.RESULTS)searchReturnView=view
        searchTerm=term.trim();matches=emptyList();error=null
        view=TranscriptView.RESULTS;searchRequest++
    }}
    LaunchedEffect(reload){
        loadingTypes=true;error=null
        try{types=repository.transcriptTypes();if(types.none{it.first==selected})selected=types.firstOrNull()?.first.orEmpty()}
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{loadingTypes=false;typeReloads++}
    }
    LaunchedEffect(searchRequest,downloading,loadingTypes,selected){
        if(searchRequest==0||downloading||loadingTypes)return@LaunchedEffect
        val requestedFormat=selected
        val requestedTerm=searchTerm
        searching=true;error=null
        try{
            require(requestedFormat.isNotBlank()){ "请先选择成绩单格式" }
            val pdf=file?.takeIf{fileFormat==requestedFormat}?:repository.transcript(requestedFormat).also{
                file=it;fileFormat=requestedFormat;indexedFile=null;indexedPages=null;page=0;pageCount=0
                renderedPage=null;renderedRequest=-1;renderRequest++
            }
            val pages=indexedPages?.takeIf{indexedFile==pdf}?:withContext(Dispatchers.IO){TranscriptPdfSearch.index(context,pdf)}.also{
                indexedFile=pdf;indexedPages=it
            }
            pageCount=pages.size
            matches=withContext(Dispatchers.Default){TranscriptPdfSearch.find(pages,requestedTerm)}
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{searching=false;searchReloads++}
    }
    val bitmap by produceState<Bitmap?>(null,file,page,renderRequest,view) {
        if(view!=TranscriptView.PDF || file==null)return@produceState
        file?.let{pdf->
            val cached=renderedPage
            if(cached?.first==(pdf to page) && renderedRequest==renderRequest){value=cached.second;return@produceState}
            value=null;rendering=true;try{
            val (image,count)=withContext(Dispatchers.IO){
                ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY).use{descriptor->PdfRenderer(descriptor).use{renderer->
                    require(renderer.pageCount>0){ "成绩单没有可显示的页面" }
                    val image=renderer.openPage(page.coerceIn(0,renderer.pageCount-1)).use{source->
                        Bitmap.createBitmap(1080,(1080f*source.height/source.width).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                            .also{source.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)}
                    }
                    image to renderer.pageCount
                }}
            }
            renderedPage=(pdf to page) to image;renderedRequest=renderRequest
            value=image;pageCount=count
        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
        finally{rendering=false;pdfReloads++}}
    }
    val typesAlpha=rememberCrestoReloadAlpha(loadingTypes,typeReloads)
    val pdfAlpha=rememberCrestoReloadAlpha(downloading||rendering,pdfReloads)
    val resultAlpha=rememberCrestoReloadAlpha(searching,searchReloads)
    val errorAlpha=rememberCrestoReloadAlpha(loading||rendering,error to (typeReloads+pdfReloads+searchReloads))
    var idleError by remember{mutableStateOf<String?>(null)}
    SideEffect{if(!loading&&!rendering)idleError=error}
    val shownError=error?:idleError.takeIf{loading||rendering}

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState=view,
            transitionSpec={crestoEnter() togetherWith crestoExit()},
            modifier=Modifier.fillMaxSize(),
            label="成绩单页面切换",
        ){targetView->
            GlasmioPinnedListPage(when(targetView){
                TranscriptView.FORM->"成绩单"
                TranscriptView.RESULTS->"搜索结果"
                TranscriptView.PDF->"成绩单预览"
            },bottomPadding=180.dp,onNavigateBack=navigateBack,
                listState=when(targetView){TranscriptView.FORM->formList;TranscriptView.RESULTS->resultsList;TranscriptView.PDF->pdfList}) {
                when(targetView){
                TranscriptView.FORM->{
                    item{GlasmioGroupedList(Modifier.reloadFade(typesAlpha)){
                        AnchoredGlassChoiceRow("格式",detail=types.firstOrNull{it.first==selected}?.second,
                            options=types,selected=selected,enabled=!loadingTypes&&types.isNotEmpty(),onSelect={key->
                                if(selected!=key){selected=key;file=null;fileFormat=null;indexedFile=null;indexedPages=null;page=0;pageCount=0
                                    renderedPage=null;renderedRequest=-1;renderRequest++}
                            })
                    }}
                    item{GlasmioButton(onClick={scope.launch{
                        downloading=true;error=null
                        try{
                            val downloaded=repository.transcript(selected)
                            file=downloaded;fileFormat=selected;indexedFile=null;indexedPages=null
                            renderedPage=null;renderedRequest=-1;renderRequest++
                            page=0;pageCount=0
                            if(view==TranscriptView.FORM){documentReturnView=TranscriptView.FORM;view=TranscriptView.PDF}
                        }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
                        finally{downloading=false}
                    }},enabled=!loading&&selected.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("下载并查看")}}
                    shownError?.let{item{GlasmioGroupedList(Modifier.reloadFade(errorAlpha)){
                        Text(it,color=LocalGlassColors.current.destructive,modifier=Modifier.padding(16.dp))
                        GlasmioListRow("重新加载成绩单格式",onClick={reload++})
                    }}}
                }
                TranscriptView.RESULTS->{
                    item{Text(if(searchTerm.isBlank())"全部页面"else"关键词：$searchTerm",style=IosTypography.body)}
                    if(!searching&&shownError==null){
                        item{Text("找到 ${matches.size} 页结果",modifier=Modifier.reloadFade(resultAlpha),style=IosTypography.footnote)}
                        if(indexedPages?.all{it.text.isBlank()}==true && searchTerm.isNotBlank())
                            item{Text("此成绩单没有可提取的文字，可能是扫描版 PDF，无法进行文字搜索。",style=IosTypography.body)}
                        else if(matches.isEmpty())item{Text("没有匹配的成绩单内容",style=IosTypography.body)}
                        items(matches.size){index->val match=matches[index]
                            GlasmioGroupedList(Modifier.reloadFade(resultAlpha)){
                                GlasmioListRow("第${match.number}页",subtitle=match.snippet,onClick={
                                     // The source PDF and a result PDF share this screen type.
                                     // Clear that source hop before opening a result to avoid a Back loop.
                                     if(searchReturnView==TranscriptView.PDF)searchReturnView=TranscriptView.FORM
                                    page=match.number-1;documentReturnView=TranscriptView.RESULTS;view=TranscriptView.PDF
                                })
                            }
                        }
                    }
                    shownError?.let{item{GlasmioGroupedList(Modifier.reloadFade(errorAlpha)){
                        Text(it,color=LocalGlassColors.current.destructive,modifier=Modifier.padding(16.dp))
                        GlasmioListRow("重试搜索",onClick={searchRequest++})
                    }}}
                }
                TranscriptView.PDF->{
                    shownError?.let{item{GlasmioGroupedList(Modifier.reloadFade(errorAlpha)){
                        Text(it,color=LocalGlassColors.current.destructive,modifier=Modifier.padding(16.dp))
                        GlasmioListRow("重试打开",onClick={renderRequest++})
                    }}}
                    bitmap?.let{image->item{Image(image.asImageBitmap(),"成绩单第${page+1}页",Modifier.fillMaxWidth().height(600.dp).reloadFade(pdfAlpha))}}
                    if(pageCount>1)item{Row(Modifier.reloadFade(pdfAlpha),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        GlasmioButton(onClick={page--},enabled=page>0){Text("上一页")}
                        Text("${page+1}/$pageCount")
                        GlasmioButton(onClick={page++},enabled=page+1<pageCount){Text("下一页")}
                    }}
                }
                }
            }
        }
        if(loading||rendering)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){XachetLoadingIndicator()}
    }
}

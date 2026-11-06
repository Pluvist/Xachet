package com.pluvist.sachet.next
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val coursePublicationSettingKeys=setOf("semesterStartDate","themeMode","isEnableCourseLiveActivity","isEnableCourseReminders")

data class AppData(val courses:List<Course> = emptyList(),val account:Account=Account(),val loading:Boolean=true,val message:String?=null,val settingsSnapshot:String="{}",val appearanceSnapshot:String="{}",val hasSchedule:Boolean=false,val colors:Map<String,String> = emptyMap(),val reloadRevision:Int=0,val reloadFromClick:Boolean=false,val initialized:Boolean=false)
class SachetViewModel(app:Application):AndroidViewModel(app) {
    val repository=AcademicRepository(app)
    private val mutable=MutableStateFlow(AppData())
    val state=mutable.asStateFlow()
    private val firstContentFrame=CompletableDeferred<Unit>()
    private var foregroundRestore:Job?=null
    private var accountRevision=0
    init { viewModelScope.launch {
        val restoreRevision=accountRevision
        // Keystore access and local JSON loading run independently. Neither a slow
        // account restore nor the network may keep a cached home page invisible.
        val accountTask=async(Dispatchers.IO) {
            try { Result.success(repository.accounts.read()) }
            catch(error:CancellationException){throw error}
            catch(error:Exception){Result.failure<Account>(error)}
        }
        val local=try { withContext(Dispatchers.IO) {
            var failure:String?=null
            suspend fun <T> restore(fallback:T,read:suspend ()->T):T = try{read()}
                catch(error:CancellationException){throw error}
                catch(error:Exception){failure=failure?:error.message?:"本地数据读取失败";fallback}
            val courses=restore(emptyList()){repository.cached()}
            val colors=restore(emptyMap()){repository.colors()}
            AppData(courses=courses,loading=false,initialized=true,message=failure,
                settingsSnapshot=repository.settings().toString(),appearanceSnapshot=repository.appearance().toString(),
                hasSchedule=repository.hasSchedule(),colors=colors)
        } } catch(error:CancellationException){throw error}
        catch(error:Exception){AppData(loading=false,initialized=true,message=error.message?:"本地课表读取失败")}
        mutable.value=local
        val restored=accountTask.await()
        if(accountRevision==restoreRevision) {
            restored.onSuccess{account->mutable.update{it.copy(account=account)}}
                .onFailure{error->mutable.update{it.copy(message=error.message?:"本地账号读取失败，请重新登录")}}
        }
        firstContentFrame.await()
        if(accountRevision==restoreRevision&&restored.getOrNull()?.let{it.cookie.isNotBlank()||it.password.isNotBlank()}==true)refresh()
    } }
    fun onFirstContentFrame(){firstContentFrame.complete(Unit)}
    suspend fun awaitFirstContentFrame(){firstContentFrame.await()}
    /** Restores alarms/notifications from disk even if automatic network refresh fails.
     * Reopening a permission screen uses this same path; no Binder/disk work runs on UI. */
    fun onForeground() {
        foregroundRestore?.cancel()
        foregroundRestore=launch {
            state.first{it.initialized};firstContentFrame.await()
            repository.republish()
        }
    }
    fun onNotificationPermissionChanged()=launch {
        state.first{it.initialized};firstContentFrame.await()
        repository.republish()
    }
    suspend fun shouldRequestNotificationPermission():Boolean=withContext(Dispatchers.IO) {
        val data=mutable.value
        if(!data.initialized||!data.hasSchedule||data.courses.isEmpty())return@withContext false
        val settings=repository.settings()
        if(!settings.optBoolean("isEnableCourseLiveActivity",true)&&!settings.optBoolean("isEnableCourseReminders",true))return@withContext false
        val context=getApplication<Application>()
        if(context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)return@withContext false
        val prefs=context.getSharedPreferences("course_widget",android.content.Context.MODE_PRIVATE)
        !prefs.getBoolean("livePermissionAsked",false)
    }
    fun markNotificationPermissionRequested() {
        // The preceding IO eligibility check has already loaded this preferences
        // object. Persist only after the foreground launcher accepted the request.
        getApplication<Application>().getSharedPreferences("course_widget",android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("livePermissionAsked",true).apply()
    }
    private fun launch(block:suspend ()->Unit) = viewModelScope.launch {
        try{block()}catch(error:CancellationException){throw error}catch(error:Exception){mutable.update{it.copy(loading=false,message=error.message?:"操作失败")}}
    }
    fun dismissMessage(){mutable.update{it.copy(message=null)}}
    fun refresh(fromClick:Boolean=false) = launch {
        if(mutable.value.loading) return@launch
        mutable.update{it.copy(loading=true,message=null,reloadFromClick=fromClick)}
        try {
            val courses=repository.refresh()
            val account=withContext(Dispatchers.IO){repository.accounts.read()}
            mutable.update{it.copy(courses=courses,account=account,hasSchedule=true,settingsSnapshot=repository.settings().toString())}
        }finally{mutable.update{it.copy(loading=false,reloadRevision=it.reloadRevision+1,reloadFromClick=false)}}
    }
    fun login(id:String,password:String,remember:Boolean,onDone:()->Unit) { accountRevision++;launch {
        mutable.update{it.copy(loading=true,message=null)}
        val account=repository.login(id,password,remember)
        mutable.update{it.copy(account=account,loading=false)}
        onDone(); refresh()
    } }
    fun logout() {accountRevision++;launch {
        repository.logout()
        mutable.update{it.copy(account=Account())}
    } }
    fun cookieLogin(cookie:String,onDone:()->Unit) {accountRevision++;launch {
        mutable.update{it.copy(loading=true,message=null)}
        val account=repository.cookieLogin(cookie)
        mutable.update{it.copy(account=account,loading=false)};onDone();refresh()
    } }
    fun importSchedule(body:String)=launch {
        val courses=repository.importSchedule(body)
        mutable.update{it.copy(courses=courses,hasSchedule=true,reloadRevision=it.reloadRevision+1)}
    }
    suspend fun saveSettings(values:Map<String,Any>) {
        val changed=repository.setSettings(values)
        mutable.update{it.copy(settingsSnapshot=repository.settings().toString())}
        // Onboarding and purely visual settings do not alter the offline timeline.
        if(changed&&values.keys.any{it in coursePublicationSettingKeys})
            repository.republish()
    }
    suspend fun saveAppearance(json:org.json.JSONObject) {
        withContext(Dispatchers.IO){repository.saveAppearance(json)}
        mutable.update{it.copy(appearanceSnapshot=json.toString())}
    }
    fun setRoutine(course:Course,routine:String)=launch {
        val courses=repository.setRoutine(course,routine)
        mutable.update{it.copy(courses=courses,settingsSnapshot=repository.settings().toString())}
    }
    fun setCardHeight(height:Float)=launch { saveAppearance(repository.appearance().put("cardHeight",height.toDouble())) }
    fun importColors(body:String)=launch { val colors=repository.importColors(body);mutable.update{it.copy(colors=colors,reloadRevision=it.reloadRevision+1)} }
    fun reloadLocal()=launch {
        val courses=repository.cached();val colors=repository.colors()
        repository.republish()
        mutable.update{it.copy(courses=courses,colors=colors,hasSchedule=repository.hasSchedule(),reloadRevision=it.reloadRevision+1)}
    }
    fun loadBenchmarkFixture()=launch {
        if(!BuildConfig.APPLICATION_ID.endsWith(".benchmark"))return@launch
        state.first{!it.loading}
        withContext(Dispatchers.IO){repository.installUiBenchmark()}
        repository.setSetting("semesterStartDate",java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString())
        repository.setSetting("hasReadDisclaimer",true)
        repository.setSetting("isEnableCourseLiveActivity",false)
        repository.setSetting("isEnableCourseReminders",false)
        val body=withContext(Dispatchers.IO){getApplication<Application>().assets.open("benchmark-schedule.json").bufferedReader().use{it.readText()}}
        val courses=repository.importSchedule(body)
        mutable.update{it.copy(courses=courses,hasSchedule=true,settingsSnapshot=repository.settings().toString())}
    }
}

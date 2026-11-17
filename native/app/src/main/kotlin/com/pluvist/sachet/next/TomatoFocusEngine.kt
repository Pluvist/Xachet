package com.pluvist.sachet.next

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.Immutable
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal enum class FocusTimerMode(val key: String, val label: String) {
    Pomodoro("pomodoro", "番茄计时"),
    LongTimer("long", "长计时"),
}

internal enum class FocusPhase(val key: String, val label: String) {
    Focus("focus", "专注"),
    ShortBreak("shortBreak", "短休"),
    LongBreak("longBreak", "长休"),
}

internal enum class FocusStatsRange(val label: String) {
    Today("今日"),
    Week("本周"),
    Month("本月"),
}

@Immutable
internal data class FocusSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val roundsPerCycle: Int = 4,
    val dailyGoalMinutes: Int = 120,
) {
    fun phaseDurationSeconds(phase: FocusPhase): Long = when (phase) {
        FocusPhase.Focus -> focusMinutes.coerceIn(1, 180) * 60L
        FocusPhase.ShortBreak -> shortBreakMinutes.coerceIn(1, 60) * 60L
        FocusPhase.LongBreak -> longBreakMinutes.coerceIn(1, 90) * 60L
    }
}

@Immutable
internal data class FocusSessionRecord(
    val id: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val mode: FocusTimerMode,
    val tag: String,
)

@Immutable
internal data class FocusDayBucket(
    val date: LocalDate,
    val label: String,
    val minutes: Int,
)

@Immutable
internal data class FocusTagBreakdown(
    val tag: String,
    val minutes: Int,
    val count: Int,
    val fraction: Float,
)

@Immutable
internal data class FocusStatsSummary(
    val todayMinutes: Int,
    val dailyGoalFraction: Float,
    val totalMinutes: Int,
    val sessionCount: Int,
    val pomodoroCount: Int,
    val longTimerCount: Int,
    val averageSessionMinutes: Int,
    val streakDays: Int,
    val weekBuckets: List<FocusDayBucket>,
    val monthBuckets: List<FocusDayBucket>,
    val hourlyMinutes: IntArray,
    val goldenHourLabel: String,
    val tagBreakdown: List<FocusTagBreakdown>,
    val recentRecords: List<FocusSessionRecord>,
)

@Immutable
internal data class FocusUiState(
    val mode: FocusTimerMode = FocusTimerMode.Pomodoro,
    val phase: FocusPhase = FocusPhase.Focus,
    val selectedTag: String = "专注学习",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0L,
    val currentRound: Int = 1,
    val settings: FocusSettings = FocusSettings(),
    val records: List<FocusSessionRecord> = emptyList(),
    val lastFeedback: String? = null,
) {
    val targetSeconds: Long
        get() = settings.phaseDurationSeconds(phase)

    val displaySeconds: Long
        get() = if (mode == FocusTimerMode.Pomodoro) {
            (targetSeconds - elapsedSeconds).coerceAtLeast(0L)
        } else {
            elapsedSeconds
        }

    val dialProgress: Float
        get() = if (mode == FocusTimerMode.Pomodoro) {
            val total = targetSeconds.coerceAtLeast(1L)
            (displaySeconds.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            // Long timer smoothly wraps a 60-minute loop while counting up indefinitely
            ((elapsedSeconds % 3600L).toFloat() / 3600f).coerceIn(0f, 1f)
        }

    val hasActiveProgress: Boolean
        get() = isRunning || isPaused || elapsedSeconds > 0L
}

internal val DefaultFocusTags: List<String> = listOf("专注学习", "专业课", "作业实验", "外语阅读", "考研考公")

internal object FocusFormatters {
    fun clock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0L)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    fun durationLabel(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0 分钟"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0 -> "${m} 分钟"
            m == 0 -> "${h} 小时"
            else -> "${h} 小时 ${m} 分钟"
        }
    }
}

/**
 * Computes daily, weekly, monthly, Golden Hours, and tag analytics purely from stored
 * [FocusSessionRecord] items so statistics remain deterministic across UI & unit tests.
 */
internal object FocusStatsCalculator {
    private val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    fun compute(
        records: List<FocusSessionRecord>,
        range: FocusStatsRange,
        settings: FocusSettings,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): FocusStatsSummary {
        val valid = records.filter { it.durationSeconds >= 5L }.sortedByDescending { it.endedAtMillis }
        val todayMinutes = valid
            .filter { Instant.ofEpochMilli(it.endedAtMillis).atZone(zone).toLocalDate() == today }
            .sumOf { (it.durationSeconds / 60.0).roundToInt().coerceAtLeast(1) }

        val weekStart = today.with(java.time.DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        val monthStart = today.minusDays(29)

        val scoped = valid.filter { rec ->
            val date = Instant.ofEpochMilli(rec.endedAtMillis).atZone(zone).toLocalDate()
            when (range) {
                FocusStatsRange.Today -> date == today
                FocusStatsRange.Week -> !date.isBefore(weekStart) && !date.isAfter(weekEnd)
                FocusStatsRange.Month -> !date.isBefore(monthStart) && !date.isAfter(today)
            }
        }

        val totalSeconds = scoped.sumOf { it.durationSeconds }
        val totalMinutes = (totalSeconds / 60.0).roundToInt().let { m ->
            if (scoped.isNotEmpty() && m == 0) 1 else m
        }
        val pomodoroCount = scoped.count { it.mode == FocusTimerMode.Pomodoro }
        val longTimerCount = scoped.count { it.mode == FocusTimerMode.LongTimer }
        val avgMinutes = if (scoped.isEmpty()) 0 else (totalMinutes.toFloat() / scoped.size).roundToInt()

        val minutesByDate = HashMap<LocalDate, Int>(32)
        for (rec in valid) {
            val d = Instant.ofEpochMilli(rec.endedAtMillis).atZone(zone).toLocalDate()
            val mins = (rec.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
            minutesByDate[d] = (minutesByDate[d] ?: 0) + mins
        }

        val weekBuckets = (0 until 7).map { offset ->
            val d = weekStart.plusDays(offset.toLong())
            FocusDayBucket(
                date = d,
                label = weekdayLabels[offset],
                minutes = minutesByDate[d] ?: 0,
            )
        }

        val monthBuckets = (0 until 30).map { offset ->
            val d = monthStart.plusDays(offset.toLong())
            FocusDayBucket(
                date = d,
                label = "${d.monthValue}/${d.dayOfMonth}",
                minutes = minutesByDate[d] ?: 0,
            )
        }

        val hourlySec = LongArray(24)
        for (rec in scoped) {
            val midMillis = (rec.startedAtMillis + rec.endedAtMillis) / 2L
            val hour = Instant.ofEpochMilli(midMillis).atZone(zone).hour.coerceIn(0, 23)
            hourlySec[hour] += rec.durationSeconds
        }
        val hourlyMinutes = IntArray(24) { h ->
            val s = hourlySec[h]
            if (s <= 0L) 0 else (s / 60.0).roundToInt().coerceAtLeast(1)
        }
        val peakHour = hourlySec.indices.maxByOrNull { hourlySec[it] } ?: 0
        val goldenHourLabel = if (hourlySec[peakHour] > 0L) {
            String.format(java.util.Locale.ROOT, "%02d:00–%02d:00", peakHour, (peakHour + 1) % 24)
        } else {
            "待记录"
        }

        val tagBreakdown = scoped
            .groupBy { it.tag.ifBlank { "专注学习" } }
            .map { (tag, list) ->
                val sec = list.sumOf { it.durationSeconds }
                val mins = (sec / 60.0).roundToInt().coerceAtLeast(1)
                FocusTagBreakdown(
                    tag = tag,
                    minutes = mins,
                    count = list.size,
                    fraction = if (totalSeconds > 0L) (sec.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f,
                )
            }
            .sortedByDescending { it.minutes }

        var streak = 0
        var cursor = if ((minutesByDate[today] ?: 0) > 0) today else today.minusDays(1)
        while ((minutesByDate[cursor] ?: 0) > 0) {
            streak++
            cursor = cursor.minusDays(1)
        }

        val goal = settings.dailyGoalMinutes.coerceAtLeast(1)
        return FocusStatsSummary(
            todayMinutes = todayMinutes,
            dailyGoalFraction = (todayMinutes.toFloat() / goal.toFloat()).coerceIn(0f, 1f),
            totalMinutes = totalMinutes,
            sessionCount = scoped.size,
            pomodoroCount = pomodoroCount,
            longTimerCount = longTimerCount,
            averageSessionMinutes = avgMinutes,
            streakDays = streak,
            weekBuckets = weekBuckets,
            monthBuckets = monthBuckets,
            hourlyMinutes = hourlyMinutes,
            goldenHourLabel = goldenHourLabel,
            tagBreakdown = tagBreakdown,
            recentRecords = scoped.take(20),
        )
    }
}

internal object FocusCodec {
    fun settingsToJson(s: FocusSettings): String = JSONObject()
        .put("focusMinutes", s.focusMinutes)
        .put("shortBreakMinutes", s.shortBreakMinutes)
        .put("longBreakMinutes", s.longBreakMinutes)
        .put("roundsPerCycle", s.roundsPerCycle)
        .put("dailyGoalMinutes", s.dailyGoalMinutes)
        .toString()

    fun settingsFromJson(raw: String): FocusSettings = runCatching {
        val obj = JSONObject(raw)
        FocusSettings(
            focusMinutes = obj.optInt("focusMinutes", 25).coerceIn(5, 180),
            shortBreakMinutes = obj.optInt("shortBreakMinutes", 5).coerceIn(1, 60),
            longBreakMinutes = obj.optInt("longBreakMinutes", 15).coerceIn(5, 90),
            roundsPerCycle = obj.optInt("roundsPerCycle", 4).coerceIn(2, 8),
            dailyGoalMinutes = obj.optInt("dailyGoalMinutes", 120).coerceIn(15, 720),
        )
    }.getOrDefault(FocusSettings())

    fun recordsToJson(records: List<FocusSessionRecord>): String {
        val arr = JSONArray()
        records.takeLast(500).forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("start", r.startedAtMillis)
                    .put("end", r.endedAtMillis)
                    .put("dur", r.durationSeconds)
                    .put("mode", r.mode.key)
                    .put("tag", r.tag),
            )
        }
        return arr.toString()
    }

    fun recordsFromJson(raw: String): List<FocusSessionRecord> = runCatching {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val dur = obj.optLong("dur", 0L)
                if (dur < 5L) continue
                val modeKey = obj.optString("mode", FocusTimerMode.Pomodoro.key)
                add(
                    FocusSessionRecord(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        startedAtMillis = obj.optLong("start"),
                        endedAtMillis = obj.optLong("end"),
                        durationSeconds = dur,
                        mode = FocusTimerMode.entries.firstOrNull { it.key == modeKey } ?: FocusTimerMode.Pomodoro,
                        tag = obj.optString("tag").ifBlank { "专注学习" },
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * ViewModel-owned Tomato focus controller persisting settings + session records under `filesDir`
 * so running timers continue seamlessly while users switch between Home, Schedule, and Tomato.
 */
internal class FocusTimerController(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, "native-focus-config.json")
    private val historyFile = File(appContext.filesDir, "native-focus-history.json")
    private val ioLock = Any()
    private val controlLock = Any()

    private val mutable = MutableStateFlow(FocusUiState())
    val state: StateFlow<FocusUiState> = mutable.asStateFlow()

    private var runStartedAtMillis: Long = 0L
    private var accumulatedBeforePauseMillis: Long = 0L
    private var tickerJob: Job? = null

    init {
        FocusLiveActivity.bindController(this)
        scope.launch(Dispatchers.IO) {
            val (settings, records) = synchronized(ioLock) {
                val cfg = if (configFile.isFile) {
                    FocusCodec.settingsFromJson(String(AtomicFile(configFile).readFully()))
                } else {
                    FocusSettings()
                }
                val hist = if (historyFile.isFile) {
                    FocusCodec.recordsFromJson(String(AtomicFile(historyFile).readFully()))
                } else {
                    emptyList()
                }
                cfg to hist
            }
            mutable.update { it.copy(settings = settings, records = records) }
        }
    }

    private fun syncLiveActivity() {
        FocusLiveActivity.sync(appContext, mutable.value)
    }

    fun quickStartDefault(mode: FocusTimerMode) {
        synchronized(controlLock) {
            tickerJob?.cancel()
            tickerJob = null
            val defaults = FocusSettings()
            runStartedAtMillis = System.currentTimeMillis()
            accumulatedBeforePauseMillis = 0L
            mutable.update {
                it.copy(
                    mode = mode,
                    phase = FocusPhase.Focus,
                    currentRound = 1,
                    elapsedSeconds = 0L,
                    isRunning = true,
                    isPaused = false,
                    settings = defaults,
                    lastFeedback = if (mode == FocusTimerMode.Pomodoro) {
                        "已按默认设置（${defaults.focusMinutes} 分钟）开启番茄钟"
                    } else {
                        "已快速开启长计时"
                    },
                )
            }
            ensureTickerLocked()
        }
        syncLiveActivity()
    }

    fun start() {
        val started = synchronized(controlLock) {
            if (mutable.value.isRunning) return@synchronized false
            runStartedAtMillis = System.currentTimeMillis()
            mutable.update { it.copy(isRunning = true, isPaused = false, lastFeedback = null) }
            ensureTickerLocked()
            true
        }
        if (started) syncLiveActivity()
    }

    fun pause() {
        val paused = synchronized(controlLock) {
            if (!mutable.value.isRunning) return@synchronized false
            val now = System.currentTimeMillis()
            accumulatedBeforePauseMillis += (now - runStartedAtMillis).coerceAtLeast(0L)
            runStartedAtMillis = 0L
            tickerJob?.cancel()
            tickerJob = null
            mutable.update {
                it.copy(
                    isRunning = false,
                    isPaused = true,
                    elapsedSeconds = accumulatedBeforePauseMillis / 1000L,
                )
            }
            true
        }
        if (paused) syncLiveActivity()
    }

    fun reset() {
        synchronized(controlLock) {
            tickerJob?.cancel()
            tickerJob = null
            runStartedAtMillis = 0L
            accumulatedBeforePauseMillis = 0L
            mutable.update {
                it.copy(isRunning = false, isPaused = false, elapsedSeconds = 0L, lastFeedback = null)
            }
        }
        syncLiveActivity()
    }

    fun handleLiveActionStop() {
        synchronized(controlLock) {
            val snap = mutable.value
            val now = System.currentTimeMillis()
            val elapsedSec = currentTotalElapsedMillisLocked(now) / 1000L
            tickerJob?.cancel()
            tickerJob = null
            runStartedAtMillis = 0L
            accumulatedBeforePauseMillis = 0L
            if (snap.mode == FocusTimerMode.LongTimer) {
                val recorded = commitSessionIfEligible(snap, elapsedSec, now, minSeconds = 15L)
                mutable.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        elapsedSeconds = 0L,
                        lastFeedback = if (recorded) {
                            "已记录 ${FocusFormatters.durationLabel((elapsedSec / 60.0).roundToInt().coerceAtLeast(1))}专注"
                        } else {
                            "计时不足 15 秒，已停止"
                        },
                    )
                }
            } else {
                mutable.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        elapsedSeconds = 0L,
                        lastFeedback = null,
                    )
                }
            }
        }
        syncLiveActivity()
    }

    fun handleLiveActionSkip() {
        synchronized(controlLock) {
            val snap = mutable.value
            if (snap.mode != FocusTimerMode.Pomodoro) return@synchronized
            val now = System.currentTimeMillis()
            val elapsedSec = currentTotalElapsedMillisLocked(now) / 1000L
            val wasRunning = snap.isRunning
            tickerJob?.cancel()
            tickerJob = null
            accumulatedBeforePauseMillis = 0L
            runStartedAtMillis = if (wasRunning) now else 0L
            val recorded = if (snap.phase == FocusPhase.Focus) {
                commitSessionIfEligible(snap, elapsedSec, now, minSeconds = 15L)
            } else {
                false
            }
            val (nextPhase, nextRound) = advancePomodoroPhase(snap.phase, snap.currentRound, snap.settings)
            mutable.update {
                it.copy(
                    phase = nextPhase,
                    currentRound = nextRound,
                    isRunning = wasRunning,
                    isPaused = false,
                    elapsedSeconds = 0L,
                    lastFeedback = when {
                        recorded -> "已完成专注并记入统计，下一阶段：${nextPhase.label}"
                        snap.phase == FocusPhase.Focus -> "已切换至${nextPhase.label}"
                        else -> "休息结束，准备第 $nextRound 轮专注"
                    },
                )
            }
            if (wasRunning) ensureTickerLocked()
        }
        syncLiveActivity()
    }

    fun handleLiveActionPauseOrResume() {
        if (mutable.value.isRunning) pause() else start()
    }

    fun setMode(mode: FocusTimerMode) {
        if (mutable.value.mode == mode) return
        reset()
        mutable.update {
            it.copy(
                mode = mode,
                phase = FocusPhase.Focus,
                elapsedSeconds = 0L,
                lastFeedback = null,
            )
        }
        syncLiveActivity()
    }

    fun setPhase(phase: FocusPhase) {
        if (mutable.value.mode != FocusTimerMode.Pomodoro || mutable.value.phase == phase) return
        reset()
        mutable.update { it.copy(phase = phase, elapsedSeconds = 0L) }
        syncLiveActivity()
    }

    fun setTag(tag: String) {
        val cleaned = tag.trim().ifBlank { "专注学习" }
        mutable.update { it.copy(selectedTag = cleaned) }
        syncLiveActivity()
    }

    fun updateSettings(next: FocusSettings) {
        mutable.update { it.copy(settings = next) }
        syncLiveActivity()
        scope.launch(Dispatchers.IO) {
            synchronized(ioLock) {
                val atomic = AtomicFile(configFile)
                val out = atomic.startWrite()
                try {
                    out.write(FocusCodec.settingsToJson(next).toByteArray())
                    atomic.finishWrite(out)
                } catch (e: Throwable) {
                    atomic.failWrite(out)
                    throw e
                }
            }
        }
    }

    /** Commits current Focus session (when elapsed >= 15s) or skips break/advances phase cleanly. */
    fun finishOrSkip() {
        synchronized(controlLock) {
            val snap = mutable.value
            val now = System.currentTimeMillis()
            val totalElapsedMs = currentTotalElapsedMillisLocked(now)
            val elapsedSec = totalElapsedMs / 1000L
            tickerJob?.cancel()
            tickerJob = null
            runStartedAtMillis = 0L
            accumulatedBeforePauseMillis = 0L

            if (snap.mode == FocusTimerMode.LongTimer) {
                val recorded = commitSessionIfEligible(snap, elapsedSec, now, minSeconds = 15L)
                mutable.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        elapsedSeconds = 0L,
                        lastFeedback = if (recorded) {
                            "已记录 ${FocusFormatters.durationLabel((elapsedSec / 60.0).roundToInt().coerceAtLeast(1))}专注"
                        } else {
                            "计时不足 15 秒，已重置"
                        },
                    )
                }
            } else {
                val recorded = if (snap.phase == FocusPhase.Focus) {
                    commitSessionIfEligible(snap, elapsedSec, now, minSeconds = 15L)
                } else {
                    false
                }
                val (nextPhase, nextRound) = advancePomodoroPhase(snap.phase, snap.currentRound, snap.settings)
                mutable.update {
                    it.copy(
                        phase = nextPhase,
                        currentRound = nextRound,
                        isRunning = false,
                        isPaused = false,
                        elapsedSeconds = 0L,
                        lastFeedback = when {
                            recorded -> "已完成专注并记入统计，下一阶段：${nextPhase.label}"
                            snap.phase == FocusPhase.Focus -> "已切换至${nextPhase.label}"
                            else -> "休息结束，准备开始第 $nextRound 轮专注"
                        },
                    )
                }
            }
        }
        syncLiveActivity()
    }

    fun deleteRecord(id: String) {
        val nextRecords = mutable.value.records.filterNot { it.id == id }
        mutable.update { it.copy(records = nextRecords) }
        persistRecordsAsync(nextRecords)
    }

    fun dismissFeedback() {
        mutable.update { it.copy(lastFeedback = null) }
    }

    private fun currentTotalElapsedMillisLocked(now: Long): Long {
        val activeDelta = if (mutable.value.isRunning && runStartedAtMillis > 0L) {
            (now - runStartedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        return accumulatedBeforePauseMillis + activeDelta
    }

    private fun ensureTickerLocked() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && mutable.value.isRunning) {
                val now = System.currentTimeMillis()
                var reached = false
                var tickChanged = false
                synchronized(controlLock) {
                    val snap = mutable.value
                    if (!snap.isRunning) return@synchronized
                    val elapsedSec = currentTotalElapsedMillisLocked(now) / 1000L
                    if (snap.mode == FocusTimerMode.Pomodoro && elapsedSec >= snap.targetSeconds) {
                        onPomodoroTargetReachedLocked(snap, now)
                        reached = true
                    } else if (elapsedSec != snap.elapsedSeconds) {
                        mutable.update { it.copy(elapsedSeconds = elapsedSec) }
                        tickChanged = true
                    }
                }
                if (reached) {
                    syncLiveActivity()
                    break
                }
                if (tickChanged) {
                    syncLiveActivity()
                }
                delay(250L)
            }
        }
    }

    private fun onPomodoroTargetReachedLocked(snap: FocusUiState, now: Long) {
        tickerJob?.cancel()
        tickerJob = null
        runStartedAtMillis = 0L
        accumulatedBeforePauseMillis = 0L
        if (snap.phase == FocusPhase.Focus) {
            commitSessionIfEligible(snap, snap.targetSeconds, now, minSeconds = 5L)
        }
        val (nextPhase, nextRound) = advancePomodoroPhase(snap.phase, snap.currentRound, snap.settings)
        mutable.update {
            it.copy(
                phase = nextPhase,
                currentRound = nextRound,
                isRunning = false,
                isPaused = false,
                elapsedSeconds = 0L,
                lastFeedback = if (snap.phase == FocusPhase.Focus) {
                    "本轮专注完成！已计入统计 · 下一阶段：${nextPhase.label}"
                } else {
                    "休息时间到，准备第 $nextRound 轮专注"
                },
            )
        }
    }

    private fun advancePomodoroPhase(
        current: FocusPhase,
        round: Int,
        settings: FocusSettings,
    ): Pair<FocusPhase, Int> {
        val cycle = settings.roundsPerCycle.coerceIn(2, 8)
        return when (current) {
            FocusPhase.Focus -> {
                if (round >= cycle) FocusPhase.LongBreak to 1 else FocusPhase.ShortBreak to round + 1
            }
            FocusPhase.ShortBreak -> FocusPhase.Focus to round
            FocusPhase.LongBreak -> FocusPhase.Focus to 1
        }
    }

    private fun commitSessionIfEligible(
        snap: FocusUiState,
        elapsedSec: Long,
        endMillis: Long,
        minSeconds: Long,
    ): Boolean {
        if (elapsedSec < minSeconds) return false
        val rec = FocusSessionRecord(
            id = UUID.randomUUID().toString(),
            startedAtMillis = (endMillis - elapsedSec * 1000L).coerceAtLeast(1L),
            endedAtMillis = endMillis,
            durationSeconds = elapsedSec,
            mode = snap.mode,
            tag = snap.selectedTag,
        )
        val updated = (listOf(rec) + mutable.value.records).take(500)
        mutable.update { it.copy(records = updated) }
        persistRecordsAsync(updated)
        return true
    }

    private fun persistRecordsAsync(records: List<FocusSessionRecord>) {
        scope.launch(Dispatchers.IO) {
            synchronized(ioLock) {
                val atomic = AtomicFile(historyFile)
                val out = atomic.startWrite()
                try {
                    out.write(FocusCodec.recordsToJson(records).toByteArray())
                    atomic.finishWrite(out)
                } catch (e: Throwable) {
                    atomic.failWrite(out)
                    throw e
                }
            }
        }
    }
}

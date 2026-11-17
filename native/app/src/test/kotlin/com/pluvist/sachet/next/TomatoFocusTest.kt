package com.pluvist.sachet.next

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TomatoFocusTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val today = LocalDate.of(2026, 11, 17)

    private fun epoch(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date.year, date.month, date.dayOfMonth, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun focusUiStateSupportsPomodoroAndLongTimerProgressAndClocks() {
        assertEquals("25:00", FocusFormatters.clock(25 * 60L))
        assertEquals("01:05:09", FocusFormatters.clock(3600L + 5 * 60L + 9L))
        assertEquals("1 小时 30 分钟", FocusFormatters.durationLabel(90))

        val pomo = FocusUiState(
            mode = FocusTimerMode.Pomodoro,
            phase = FocusPhase.Focus,
            isRunning = true,
            elapsedSeconds = 15 * 60L,
            settings = FocusSettings(focusMinutes = 25),
        )
        assertEquals(10 * 60L, pomo.displaySeconds)
        assertEquals(0.4f, pomo.dialProgress, 0.001f)
        assertTrue(pomo.hasActiveProgress)

        val longTimer = FocusUiState(
            mode = FocusTimerMode.LongTimer,
            isRunning = true,
            elapsedSeconds = 45 * 60L,
        )
        assertEquals(45 * 60L, longTimer.displaySeconds)
        assertEquals(0.75f, longTimer.dialProgress, 0.001f)
        assertFalse(FocusUiState().hasActiveProgress)
    }

    @Test
    fun focusCodecAndStatsCalculatorSummarizeWeekMonthGoldenHoursAndStreak() {
        val settings = FocusSettings(focusMinutes = 30, shortBreakMinutes = 5, longBreakMinutes = 20, roundsPerCycle = 4, dailyGoalMinutes = 120)
        assertEquals(settings, FocusCodec.settingsFromJson(FocusCodec.settingsToJson(settings)))

        val r1 = FocusSessionRecord(
            id = "a",
            startedAtMillis = epoch(today, 19, 0),
            endedAtMillis = epoch(today, 19, 30),
            durationSeconds = 30 * 60L,
            mode = FocusTimerMode.Pomodoro,
            tag = "专业课",
        )
        val r2 = FocusSessionRecord(
            id = "b",
            startedAtMillis = epoch(today, 20, 0),
            endedAtMillis = epoch(today, 21, 0),
            durationSeconds = 60 * 60L,
            mode = FocusTimerMode.LongTimer,
            tag = "专注学习",
        )
        val rYesterday = FocusSessionRecord(
            id = "c",
            startedAtMillis = epoch(today.minusDays(1), 10, 0),
            endedAtMillis = epoch(today.minusDays(1), 10, 25),
            durationSeconds = 25 * 60L,
            mode = FocusTimerMode.Pomodoro,
            tag = "专注学习",
        )
        val encoded = FocusCodec.recordsToJson(listOf(r1, r2, rYesterday))
        assertEquals(listOf(r1, r2, rYesterday), FocusCodec.recordsFromJson(encoded))

        val todaySummary = FocusStatsCalculator.compute(
            records = listOf(r1, r2, rYesterday),
            range = FocusStatsRange.Today,
            settings = settings,
            today = today,
            zone = zone,
        )
        assertEquals(90, todaySummary.todayMinutes)
        assertEquals(90, todaySummary.totalMinutes)
        assertEquals(0.75f, todaySummary.dailyGoalFraction, 0.001f)
        assertEquals(2, todaySummary.sessionCount)
        assertEquals(1, todaySummary.pomodoroCount)
        assertEquals(1, todaySummary.longTimerCount)
        assertEquals(2, todaySummary.streakDays)
        assertEquals("20:00–21:00", todaySummary.goldenHourLabel)
        assertEquals("专注学习", todaySummary.tagBreakdown.first().tag)

        val weekSummary = FocusStatsCalculator.compute(
            records = listOf(r1, r2, rYesterday),
            range = FocusStatsRange.Week,
            settings = settings,
            today = today,
            zone = zone,
        )
        assertEquals(7, weekSummary.weekBuckets.size)
        assertEquals(30, weekSummary.monthBuckets.size)
        assertEquals(115, weekSummary.totalMinutes)
        assertEquals(3, weekSummary.sessionCount)
    }

    @Test
    fun focusLiveActivityFormatsPomodoroAndLongTimerPresentations() {
        val pomoRunning = FocusUiState(
            mode = FocusTimerMode.Pomodoro,
            phase = FocusPhase.Focus,
            selectedTag = "专业课",
            isRunning = true,
            elapsedSeconds = 5 * 60L,
            currentRound = 2,
            settings = FocusSettings(focusMinutes = 25, roundsPerCycle = 4),
        )
        val pomoPres = FocusLiveActivity.present(pomoRunning)
        assertEquals("专注 · 20:00", pomoPres.title)
        assertEquals("专业课 · 第 2/4 轮", pomoPres.content)
        assertEquals("20:00", pomoPres.shortText)
        assertTrue(pomoPres.hasProgress)
        assertEquals(1500, pomoPres.progressMax)
        assertEquals(300, pomoPres.progressCurrent)
        assertTrue(pomoPres.requestPromoted)
        assertTrue(pomoPres.ongoing)

        val pomoPaused = pomoRunning.copy(isRunning = false, isPaused = true)
        val pausedPres = FocusLiveActivity.present(pomoPaused)
        assertTrue(pausedPres.title.startsWith("已暂停 · "))
        assertTrue(pausedPres.requestPromoted)
        assertTrue(pausedPres.ongoing)

        val longRunning = FocusUiState(
            mode = FocusTimerMode.LongTimer,
            selectedTag = "论文冲刺",
            isRunning = true,
            elapsedSeconds = 125L,
        )
        val longPres = FocusLiveActivity.present(longRunning)
        assertEquals("长计时 · 02:05", longPres.title)
        assertEquals("论文冲刺 · 正向累计专注", longPres.content)
        assertEquals("02:05", longPres.shortText)
        assertEquals(3600, longPres.progressMax)
        assertEquals(125, longPres.progressCurrent)
        assertTrue(longPres.requestPromoted)
        assertFalse(FocusLiveActivity.supportsMultiplePromotedLiveActivities())

        val pomoActions = FocusLiveActivity.actionsFor(pomoRunning)
        assertEquals(listOf("停止", "跳过"), pomoActions.map { it.label })
        assertEquals(listOf(FocusLiveActivity.ACTION_STOP, FocusLiveActivity.ACTION_SKIP), pomoActions.map { it.action })

        val longRunningActions = FocusLiveActivity.actionsFor(longRunning)
        assertEquals(listOf("暂停", "停止"), longRunningActions.map { it.label })
        assertEquals(listOf(FocusLiveActivity.ACTION_PAUSE, FocusLiveActivity.ACTION_STOP), longRunningActions.map { it.action })

        val longImmediatePaused = FocusUiState(
            mode = FocusTimerMode.LongTimer,
            isRunning = false,
            isPaused = true,
            elapsedSeconds = 0L,
        )
        assertTrue(longImmediatePaused.hasActiveProgress)
        val longPausedActions = FocusLiveActivity.actionsFor(longImmediatePaused)
        assertEquals(listOf("继续", "停止"), longPausedActions.map { it.label })
    }
}

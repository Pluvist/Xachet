package com.pluvist.sachet.next

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ExamPresentationTest {
    @Test fun parsesTheOriginalExamCardFormatAndComputesWeekday() {
        val timing = requireNotNull(ExamPresentation.parse("2026-12-15(14:00-16:00)"))
        assertEquals(LocalDateTime.of(2026, 12, 15, 14, 0), timing.start)
        assertEquals(LocalDateTime.of(2026, 12, 15, 16, 0), timing.end)
        assertEquals("星期二", ExamPresentation.weekday(timing))
    }

    @Test fun acceptsFullwidthParenthesesSpacesAndEnDash() {
        val timing = requireNotNull(ExamPresentation.parse("2027-1-4 （ 10:30 – 12:30 ）"))
        assertEquals(LocalDateTime.of(2027, 1, 4, 10, 30), timing.start)
        assertEquals("星期一", ExamPresentation.weekday(timing))
        assertNotNull(ExamPresentation.parse("2026-12-01 09:00–11:00"))
    }

    @Test fun rejectsInvalidDatesAndUnscheduledTimes() {
        assertNull(ExamPresentation.parse("待安排"))
        assertNull(ExamPresentation.parse("2026-02-30(09:00-11:00)"))
        assertNull(ExamPresentation.parse("2026-12-15(16:00-14:00)"))
        assertNull(ExamPresentation.parse("2026-12-15(25:00-26:00)"))
    }

    @Test fun countdownChangesFromDaysToHoursMinutesOngoingAndFinished() {
        val timing = requireNotNull(ExamPresentation.parse("2026-12-15(14:00-16:00)"))
        assertEquals(ExamCountdown("还有", "39", "天"), ExamPresentation.countdown(timing, LocalDateTime.of(2026, 11, 6, 20, 0)))
        assertEquals(ExamCountdown("还有", "15", "小时"), ExamPresentation.countdown(timing, LocalDateTime.of(2026, 12, 14, 22, 15)))
        assertEquals(ExamCountdown("还有", "1", "分钟"), ExamPresentation.countdown(timing, LocalDateTime.of(2026, 12, 15, 13, 59, 59)))
        assertEquals(ExamCountdown("正在考试", "", ""), ExamPresentation.countdown(timing, timing.start))
        assertEquals(ExamCountdown("已结束", "", ""), ExamPresentation.countdown(timing, timing.end))
    }
}

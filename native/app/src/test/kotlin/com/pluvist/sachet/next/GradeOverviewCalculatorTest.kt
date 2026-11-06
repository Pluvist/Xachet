package com.pluvist.sachet.next

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GradeOverviewCalculatorTest {
    private fun grade(
        code: String,
        score: String,
        point: String,
        credit: String,
        nature: String = "",
        requirement: String = "",
    ) = JSONObject()
        .put("kch", code).put("kcmc", "课程 $code")
        .put("cj", score).put("jd", point).put("xf", credit)
        .put("kcxzmc", nature).put("xfyqjdmc", requirement)

    @Test fun weightedStatisticsRespectThreeScopesAndDoNotInventUnknownCategory() {
        val rows = listOf(
            grade("R1", "90", "4.0", "3", "必修"),
            grade("R2", "50", "0", "2", "必修"),
            grade("E1", "60", "1.0", "1", "选修"),
            grade("X1", "85", "3.7", "2", "选修", "跨学科选修课"),
            grade("U1", "70", "2.0", "1"),
        )
        val all = GradeOverviewCalculator.calculate(rows)
        assertEquals(22.4 / 9, all.averageGradePoint!!, 1e-7)
        assertEquals(670.0 / 9, all.averageScore!!, 1e-7)
        assertEquals(7.0, all.earnedCredits, 0.0)
        assertEquals(3.0, all.requiredCredits, 0.0)
        assertEquals(1.0, all.electiveCredits, 0.0)
        assertEquals(2.0, all.interdisciplinaryElectiveCredits, 0.0)
        assertEquals(1.0, all.unclassifiedEarnedCredits, 0.0)

        val required = GradeOverviewCalculator.calculate(rows, GradeScope.REQUIRED)
        assertEquals(2.4, required.averageGradePoint!!, 1e-7)
        assertEquals(74.0, required.averageScore!!, 1e-7)
        assertEquals(3.0, required.earnedCredits, 0.0)
        assertEquals(2, required.countedCourses)

        val known = GradeOverviewCalculator.calculate(rows, GradeScope.REQUIRED_AND_ELECTIVE)
        assertEquals(2.55, known.averageGradePoint!!, 1e-7)
        assertEquals(75.0, known.averageScore!!, 1e-7)
        assertEquals(6.0, known.earnedCredits, 0.0)
        assertEquals(4, known.countedCourses)
    }

    @Test fun passLabelsCountCreditsButNotNumericAverageAndEmptyHasNoFakeZero() {
        val passed = GradeOverviewCalculator.calculate(listOf(grade("P", "合格", "2.0", "1.5", "必修")))
        assertEquals(2.0, passed.averageGradePoint!!, 0.0)
        assertNull(passed.averageScore)
        assertEquals(1.5, passed.earnedCredits, 0.0)
        assertTrue(passed.warnings.any { it.contains("不是数字成绩") })

        val empty = GradeOverviewCalculator.calculate(emptyList())
        assertNull(empty.averageGradePoint)
        assertNull(empty.averageScore)
        assertEquals(0.0, empty.earnedCredits, 0.0)
    }

    @Test fun duplicateAttemptsAreOmittedEvenIfTheirCategoriesDisagree() {
        val rows = listOf(
            grade("R", "59", "0", "3", "必修"),
            grade("R", "90", "4", "3", "选修"),
            grade("S", "80", "3", "2", "必修"),
        )
        val required = GradeOverviewCalculator.calculate(rows, GradeScope.REQUIRED)
        assertEquals(1, required.excludedDuplicateGroups)
        assertEquals(2, required.excludedRows)
        assertEquals(2.0, required.earnedCredits, 0.0)
        assertEquals(3.0, required.averageGradePoint!!, 0.0)
        assertTrue(required.warnings.any { it.contains("重复课程") })
    }

    @Test fun invalidAndUnknownRecordsNeverBecomeEarnedCredits() {
        val rows = listOf(
            JSONObject().put("cj", "90").put("jd", "4").put("xf", "3").put("kcxzmc", "必修"),
            grade("BAD", "90", "4", "-1", "必修"),
            grade("WAIT", "待录入", "", "2", "必修"),
            grade("FAIL", "不及格", "0", "1", "必修"),
            grade("HTML", "<span>88</span>", "3.7", "1", "必修"),
        )
        val stats = GradeOverviewCalculator.calculate(rows)
        assertEquals(2, stats.excludedRows)
        assertEquals(1.0, stats.earnedCredits, 0.0)
        assertEquals(3, stats.countedCourses)
        assertTrue(stats.warnings.any { it.contains("无法判断") })
        assertTrue(stats.warnings.any { it.contains("缺少有效绩点") })
    }
}

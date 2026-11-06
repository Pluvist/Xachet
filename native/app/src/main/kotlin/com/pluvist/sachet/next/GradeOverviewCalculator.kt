package com.pluvist.sachet.next

import org.json.JSONObject
import org.jsoup.Jsoup
import java.math.BigDecimal
import java.text.Normalizer

/**
 * A local overview of one server-filtered grade track (for example kcbj=1 or kcbj=2).
 *
 * No graduation targets, CET results, or peer rankings are inferred from personal grades.
 * Average score includes numeric failures; earned credits include only clearly passing rows.
 * Repeated course identities are omitted as a group: selecting a particular attempt would
 * require the academic system's retake/recognition rule, which this response does not contain.
 */
data class GradeOverviewStats(
    val averageGradePoint: Double?,
    val averageScore: Double?,
    val earnedCredits: Double,
    val requiredCredits: Double,
    val electiveCredits: Double,
    val interdisciplinaryElectiveCredits: Double,
    val unclassifiedEarnedCredits: Double,
    val countedCourses: Int,
    val excludedDuplicateGroups: Int,
    val excludedRows: Int,
    val warnings: List<String>,
)

enum class GradeScope { ALL, REQUIRED, REQUIRED_AND_ELECTIVE }

object GradeOverviewCalculator {
    private val passLabels = setOf("优", "优秀", "良", "良好", "中", "中等", "及格", "合格", "通过")
    private val failLabels = setOf("不及格", "不合格", "未通过", "缺考", "旷考", "缓考", "取消")

    fun calculate(rows: List<JSONObject>, scope: GradeScope = GradeScope.ALL): GradeOverviewStats {
        var missingIdentity = 0
        val identified = linkedMapOf<String, MutableList<JSONObject>>()
        rows.forEach { row ->
            val key = identity(row)
            if (key == null) {
                if (selected(row, scope)) missingIdentity++
            } else identified.getOrPut(key) { mutableListOf() }.add(row)
        }

        // Group before scope selection: a retake with conflicting categories must not
        // become a seemingly unique required/elective course after filtering.
        val groups = identified.values.filter { group -> group.any { selected(it, scope) } }
        val duplicateGroups = groups.count { it.size > 1 }
        val duplicateRows = groups.filter { it.size > 1 }.sumOf { it.size }
        var invalidCredits = 0
        var missingGradePoints = 0
        var nonNumericScores = 0
        var unknownOutcomes = 0
        var unclassifiedCourses = 0
        var counted = 0
        var gpaWeighted = BigDecimal.ZERO
        var gpaCredits = BigDecimal.ZERO
        var scoreWeighted = BigDecimal.ZERO
        var scoreCredits = BigDecimal.ZERO
        var earned = BigDecimal.ZERO
        var required = BigDecimal.ZERO
        var elective = BigDecimal.ZERO
        var interdisciplinary = BigDecimal.ZERO
        var unclassified = BigDecimal.ZERO

        groups.filter { it.size == 1 }.forEach { group ->
            val row = group.single()
            val credit = number(row, "xf")?.takeIf { it > BigDecimal.ZERO && it.toDouble().isFinite() }
            if (credit == null) {
                invalidCredits++
                return@forEach
            }
            counted++

            val point = number(row, "jd")?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("4") }
            if (point == null) missingGradePoints++
            else {
                gpaWeighted += point * credit
                gpaCredits += credit
            }

            val scoreText = text(row, "cj")
            val score = scoreText.toBigDecimalOrNull()?.takeIf { it >= BigDecimal.ZERO && it <= BigDecimal("100") }
            if (score == null) nonNumericScores++
            else {
                scoreWeighted += score * credit
                scoreCredits += credit
            }

            val passed = when {
                score != null -> score >= BigDecimal("60")
                scoreText in passLabels -> true
                scoreText in failLabels -> false
                else -> null
            }
            if (passed == null) unknownOutcomes++
            if (passed != true) return@forEach

            earned += credit
            when (category(row)) {
                Category.REQUIRED -> required += credit
                Category.ELECTIVE -> elective += credit
                Category.INTERDISCIPLINARY -> interdisciplinary += credit
                Category.UNKNOWN -> {
                    unclassified += credit
                    unclassifiedCourses++
                }
            }
        }

        val warnings = buildList {
            if (duplicateGroups > 0) add("有 $duplicateGroups 组重复课程，因无法确认重修认定规则，整组未计入统计。")
            if (missingIdentity > 0) add("有 $missingIdentity 条成绩缺少课程号和名称，未计入统计。")
            if (invalidCredits > 0) add("有 $invalidCredits 条成绩缺少有效学分，未计入统计。")
            if (missingGradePoints > 0) add("有 $missingGradePoints 门课程缺少有效绩点，平均学分绩点仅计算其余课程。")
            if (nonNumericScores > 0) add("有 $nonNumericScores 门课程不是数字成绩，平均成绩仅计算其余课程。")
            if (unknownOutcomes > 0) add("有 $unknownOutcomes 门课程无法判断是否取得学分，未计入已修学分。")
            if (unclassifiedCourses > 0) add("有 $unclassifiedCourses 门已通过课程缺少明确类别，仅计入已修总学分。")
        }
        return GradeOverviewStats(
            averageGradePoint = ratio(gpaWeighted, gpaCredits),
            averageScore = ratio(scoreWeighted, scoreCredits),
            earnedCredits = earned.toDouble(),
            requiredCredits = required.toDouble(),
            electiveCredits = elective.toDouble(),
            interdisciplinaryElectiveCredits = interdisciplinary.toDouble(),
            unclassifiedEarnedCredits = unclassified.toDouble(),
            countedCourses = counted,
            excludedDuplicateGroups = duplicateGroups,
            excludedRows = duplicateRows + missingIdentity + invalidCredits,
            warnings = warnings,
        )
    }

    private fun ratio(numerator: BigDecimal, denominator: BigDecimal): Double? =
        if (denominator == BigDecimal.ZERO) null
        else numerator.divide(denominator, 8, java.math.RoundingMode.HALF_UP).toDouble()

    private fun number(row: JSONObject, key: String): BigDecimal? = text(row, key).toBigDecimalOrNull()

    private fun text(row: JSONObject, key: String): String =
        if (row.isNull(key)) "" else Jsoup.parseBodyFragment(row.optString(key)).text().trim()

    private fun identity(row: JSONObject): String? {
        val code = text(row, "kch").ifBlank { text(row, "kch_id") }
        if (code.isNotBlank()) return "code:${normalize(code)}"
        val title = text(row, "kcmc")
        if (title.isBlank()) return null
        // Without a course code, repeated titles are ambiguous even if credits differ.
        // Exclude such a group instead of guessing whether it is a retake.
        return "title:${normalize(title)}"
    }

    private fun normalize(value: String) =
        Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replace(Regex("\\s+"), " ").uppercase()

    private enum class Category { REQUIRED, ELECTIVE, INTERDISCIPLINARY, UNKNOWN }

    private fun selected(row: JSONObject, scope: GradeScope): Boolean = when (scope) {
        GradeScope.ALL -> true
        GradeScope.REQUIRED -> category(row) == Category.REQUIRED
        GradeScope.REQUIRED_AND_ELECTIVE -> category(row) != Category.UNKNOWN
    }

    private fun category(row: JSONObject): Category {
        val labels = listOf(text(row, "kcxzmc"), text(row, "xfyqjdmc"))
        val required = labels.any { "必修" in it }
        val elective = labels.any { "选修" in it }
        val interdisciplinary = labels.any { "跨学科" in it }
        return when {
            required && elective -> Category.UNKNOWN
            interdisciplinary && required -> Category.UNKNOWN
            interdisciplinary -> Category.INTERDISCIPLINARY
            required -> Category.REQUIRED
            elective -> Category.ELECTIVE
            else -> Category.UNKNOWN
        }
    }
}

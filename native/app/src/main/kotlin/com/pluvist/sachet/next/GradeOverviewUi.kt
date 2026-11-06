package com.pluvist.sachet.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.G2RoundedRectangle
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.ui.GlasmioGroupedList
import io.github.neoruaa.glasmio.ui.GlasmioSegmentedControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

private data class GradeOverviewComputed(
    val source: List<JSONObject>,
    val all: GradeOverviewStats,
    val required: GradeOverviewStats,
    val requiredAndElective: GradeOverviewStats,
)

/** One item in the surrounding academic LazyColumn; cards do not own another scroll view. */
@Composable
internal fun GradeOverviewContent(
    rows: List<JSONObject>?,
    track: String,
    loading: Boolean,
    onTrackChange: (String) -> Unit,
) {
    val colors = LocalGlassColors.current
    var scope by remember(track) { mutableStateOf(GradeScope.REQUIRED_AND_ELECTIVE) }
    val computed by produceState<GradeOverviewComputed?>(initialValue = null, key1 = rows) {
        value = rows?.let { source ->
            withContext(Dispatchers.Default) {
                GradeOverviewComputed(
                    source = source,
                    all = GradeOverviewCalculator.calculate(source, GradeScope.ALL),
                    required = GradeOverviewCalculator.calculate(source, GradeScope.REQUIRED),
                    requiredAndElective = GradeOverviewCalculator.calculate(source, GradeScope.REQUIRED_AND_ELECTIVE),
                )
            }
        }
    }
    val result = computed?.takeIf { it.source === rows }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GlasmioGroupedList {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("总览", style = IosTypography.title2, fontWeight = FontWeight.SemiBold)
                if (result == null) {
                    Text(
                        if (loading || rows != null) "正在计算成绩…" else "暂无成绩数据",
                        style = IosTypography.body,
                        color = colors.secondaryContent,
                    )
                } else if (rows.isNullOrEmpty()) {
                    Text("暂无可统计的成绩", style = IosTypography.body, color = colors.secondaryContent)
                } else {
                    GradeMetricTile("平均学分绩点", formatMetric(result.all.averageGradePoint), colors.accent)
                    GradeMetricTile("平均成绩", formatMetric(result.all.averageScore), colors.accent)
                    GradeMetricTile("已修总学分", formatMetric(result.all.earnedCredits), colors.accent)
                    GradeMetricTile("必修学分", formatMetric(result.all.requiredCredits), colors.accent)
                    GradeMetricTile("选修学分", formatMetric(result.all.electiveCredits), colors.accent)
                    GradeMetricTile("跨学科选修学分", formatMetric(result.all.interdisciplinaryElectiveCredits), colors.accent)
                    if (result.all.unclassifiedEarnedCredits > 0.0) {
                        GradeMetricTile("未分类已修学分", formatMetric(result.all.unclassifiedEarnedCredits), colors.accent)
                    }
                    Text(
                        "按当前成绩与学分计算 · 已读取 ${result.all.countedCourses} 门含学分课程；平均成绩按学分加权",
                        style = IosTypography.footnote,
                        color = colors.secondaryContent,
                    )
                }
                GlasmioSegmentedControl(
                    items = listOf("1" to "主修", "2" to "辅修"),
                    selected = track,
                    onSelected = onTrackChange,
                    enabled = !loading,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        GlasmioGroupedList {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("统计", style = IosTypography.title2, fontWeight = FontWeight.SemiBold)
                GlasmioSegmentedControl(
                    items = listOf(GradeScope.REQUIRED to "必修", GradeScope.REQUIRED_AND_ELECTIVE to "必+选"),
                    selected = scope,
                    onSelected = { scope = it },
                    enabled = result != null && !loading,
                    height = 42.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (result != null) {
                    val stats = if (scope == GradeScope.REQUIRED) result.required else result.requiredAndElective
                    GradeMetricTile("平均学分绩点", formatMetric(stats.averageGradePoint), colors.accent)
                    GradeMetricTile("平均成绩", formatMetric(stats.averageScore), colors.accent)
                    GradeMetricTile("含学分课程", "${stats.countedCourses} 门", colors.accent)
                    GradeMetricTile("班级排名", "—", colors.secondaryContent)
                    GradeMetricTile("年级专业排名", "—", colors.secondaryContent)
                    Text("排名需在“绩点排名”中查询", style = IosTypography.footnote, color = colors.secondaryContent)
                }
            }
        }

        if (result != null && result.all.warnings.isNotEmpty()) {
            GlasmioGroupedList {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("统计说明", style = IosTypography.headline, fontWeight = FontWeight.SemiBold)
                    result.all.warnings.forEach { warning ->
                        Text(warning, style = IosTypography.footnote, color = colors.secondaryContent)
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeMetricTile(label: String, value: String, valueColor: Color) {
    val colors = LocalGlassColors.current
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.groupedBackground, G2RoundedRectangle(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(6.dp).background(colors.accent, G2RoundedRectangle(3.dp)))
        Text(label, style = IosTypography.body, modifier = Modifier.weight(1f))
        Text(value, style = IosTypography.headline, color = valueColor, textAlign = TextAlign.End)
    }
}

private fun formatMetric(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
    BigDecimal.valueOf(it).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
} ?: "—"

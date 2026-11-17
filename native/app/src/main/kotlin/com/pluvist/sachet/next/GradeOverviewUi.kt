@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

private class GradeOverviewComputed(
    val source: List<JSONObject>,
    val all: GradeOverviewStats,
    val required: GradeOverviewStats,
    val requiredAndElective: GradeOverviewStats,
)

@Composable
internal fun GradeOverviewContent(
    rows: List<JSONObject>?,
    track: String,
    loading: Boolean,
    onTrackChange: (String) -> Unit,
) {
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
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "总览",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExpressiveConnectedToggleRow(
                    options = listOf("主修", "辅修"),
                    selectedIndex = if (track == "2") 1 else 0,
                    onSelect = { idx ->
                        if (!loading) onTrackChange(if (idx == 1) "2" else "1")
                    },
                )
                if (result == null) {
                    Text(
                        text = if (loading || rows != null) "正在计算成绩…" else "暂无成绩数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (rows.isNullOrEmpty()) {
                    Text(
                        text = "暂无可统计的成绩",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GradeHighlightCard(
                            label = "平均学分绩点",
                            value = formatMetric(result.all.averageGradePoint),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        GradeHighlightCard(
                            label = "平均成绩",
                            value = formatMetric(result.all.averageScore),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val breakdown = buildList {
                        add("已修总学分" to formatMetric(result.all.earnedCredits))
                        add("必修学分" to formatMetric(result.all.requiredCredits))
                        add("选修学分" to formatMetric(result.all.electiveCredits))
                        add("跨学科选修学分" to formatMetric(result.all.interdisciplinaryElectiveCredits))
                        if (result.all.unclassifiedEarnedCredits > 0.0) {
                            add("未分类已修学分" to formatMetric(result.all.unclassifiedEarnedCredits))
                        }
                    }
                    ExpressiveSegmentedGroup {
                        breakdown.forEachIndexed { index, (label, value) ->
                            ExpressiveSegmentedInfoRow(
                                index = index,
                                count = breakdown.size,
                                title = label,
                                value = value,
                            )
                        }
                    }
                    Text(
                        text = "按当前成绩与学分计算 · 已读取 ${result.all.countedCourses} 门含学分课程；平均成绩按学分加权",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

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
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "统计",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExpressiveConnectedToggleRow(
                    options = listOf("必修", "必+选"),
                    selectedIndex = if (scope == GradeScope.REQUIRED) 0 else 1,
                    onSelect = { idx ->
                        if (result != null && !loading) {
                            scope = if (idx == 0) GradeScope.REQUIRED else GradeScope.REQUIRED_AND_ELECTIVE
                        }
                    },
                )
                if (result != null) {
                    val stats = if (scope == GradeScope.REQUIRED) result.required else result.requiredAndElective
                    val rowsList = listOf(
                        "平均学分绩点" to formatMetric(stats.averageGradePoint),
                        "平均成绩" to formatMetric(stats.averageScore),
                        "含学分课程" to "${stats.countedCourses} 门",
                        "班级排名" to "—",
                        "年级专业排名" to "—",
                    )
                    ExpressiveSegmentedGroup {
                        rowsList.forEachIndexed { index, (label, value) ->
                            ExpressiveSegmentedInfoRow(
                                index = index,
                                count = rowsList.size,
                                title = label,
                                value = value,
                            )
                        }
                    }
                    Text(
                        text = "排名需在“绩点排名”中查询",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (result != null && result.all.warnings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "统计说明",
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    result.all.warnings.forEach { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GradeHighlightCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.85f),
            )
            RollingNumberText(
                text = value,
                style = MaterialTheme.typography.headlineMediumEmphasized.copy(
                    fontFamily = XachetFontFamilies.DisplayEmphasized,
                    fontWeight = FontWeight.ExtraBold,
                ).tabular(),
                color = contentColor,
            )
        }
    }
}

private fun formatMetric(value: Double?): String = value?.takeIf { it.isFinite() }?.let {
    String.format(Locale.US, "%.2f", it)
} ?: "—"

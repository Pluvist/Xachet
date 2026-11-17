@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime

@Composable
internal fun ExamCard(
    record: AcademicRecord,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val time = record.fields.firstOrNull { it.first == "考试时间" }?.second.orEmpty()
    val place = record.fields.firstOrNull { it.first == "地点" }?.second.orEmpty()
    val timing = remember(time) { ExamPresentation.parse(time) }
    val countdown = timing?.let { ExamPresentation.countdown(it, now) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = record.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (countdown != null) {
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            if (countdown.prefix.isNotEmpty()) {
                                Text(
                                    text = if (countdown.value.isNotEmpty()) "${countdown.prefix} " else countdown.prefix,
                                    style = XachetTypeTokens.PillStatus,
                                    maxLines = 1,
                                )
                            }
                            if (countdown.value.isNotEmpty()) {
                                RollingNumberText(
                                    text = countdown.value,
                                    style = XachetTypeTokens.PillStatus.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    ),
                                )
                            }
                            if (countdown.suffix.isNotEmpty()) {
                                Text(
                                    text = if (countdown.value.isNotEmpty()) " ${countdown.suffix}" else countdown.suffix,
                                    style = XachetTypeTokens.PillStatus,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
            if (time.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = time + (timing?.let { "  ${ExamPresentation.weekday(it)}" } ?: ""),
                        style = XachetTypeTokens.CondensedMeta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal fun examSemesterLabel(form: AcademicForm?, values: Map<String, String>): String? {
    val yearCode = values["cx_xnm"].orEmpty()
    val termCode = values["cx_xqm"].orEmpty()
    if (yearCode.isBlank() && termCode.isBlank()) return null
    val year = yearCode.toIntOrNull()?.takeIf { yearCode.length == 4 }
        ?.let { "$it-${it + 1}" }
        ?: form?.choices?.firstOrNull { it.key == "cx_xnm" }?.values
            ?.firstOrNull { it.first == yearCode }?.second.orEmpty()
    val term = when (termCode) {
        "3" -> "1"
        "12" -> "2"
        "16" -> "3"
        else -> form?.choices?.firstOrNull { it.key == "cx_xqm" }?.values
            ?.firstOrNull { it.first == termCode }?.second.orEmpty()
    }
    return listOf(year, term).filter { it.isNotBlank() }.joinToString("-").takeIf { it.isNotBlank() }
}

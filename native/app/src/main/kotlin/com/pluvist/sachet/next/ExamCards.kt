package com.pluvist.sachet.next

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.G2RoundedRectangle
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import java.time.LocalDateTime

/** Compact exam card: title/countdown above the time and place, as in the earlier exam page. */
@Composable
internal fun ExamCard(
    record: AcademicRecord,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    val time = record.fields.firstOrNull { it.first == "考试时间" }?.second.orEmpty()
    val place = record.fields.firstOrNull { it.first == "地点" }?.second.orEmpty()
    val timing = remember(time) { ExamPresentation.parse(time) }
    val countdown = timing?.let { ExamPresentation.countdown(it, now) }
    Column(
        modifier.fillMaxWidth().background(colors.elevatedBackground, G2RoundedRectangle(26.dp))
            .padding(horizontal = 17.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                record.title,
                modifier = Modifier.weight(1f),
                style = IosTypography.callout.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (countdown != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    buildAnnotatedString {
                        append(countdown.prefix)
                        if (countdown.value.isNotEmpty()) {
                            append(" ")
                            withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold,
                                fontSize = IosTypography.title3.fontSize)) { append(countdown.value) }
                            append(" ")
                        }
                        append(countdown.suffix)
                    },
                    style = IosTypography.footnote,
                    color = colors.content,
                    maxLines = 1,
                )
            }
        }
        if (time.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.AccessTime, contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = colors.secondaryContent)
                Spacer(Modifier.width(6.dp))
                Text(time + (timing?.let { "  ${ExamPresentation.weekday(it)}" } ?: ""),
                    style = IosTypography.footnote, color = colors.secondaryContent)
            }
        }
        if (place.isNotBlank()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Place, contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(16.dp), tint = colors.secondaryContent)
                Spacer(Modifier.width(6.dp))
                Text(place, style = IosTypography.footnote, color = colors.secondaryContent)
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

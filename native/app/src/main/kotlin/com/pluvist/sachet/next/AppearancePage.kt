@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class AppearanceField(val key: String, val label: String, val defaultValue: Double)

private val numericFields = listOf(
    AppearanceField("cardHeight", "卡片高度", 60.0),
    AppearanceField("cardBorderRadius", "卡片圆角", 6.0),
    AppearanceField("cardMargin", "卡片边距", 2.0),
    AppearanceField("TitleFontSize", "课程名字号", 14.0),
    AppearanceField("PlaceFontSize", "地点字号", 11.0),
    AppearanceField("TitleFontWeight", "课程名字重 (1-9)", 6.0),
    AppearanceField("PlaceFontWeight", "地点字重 (1-9)", 5.0),
    AppearanceField("TitleMaxLines", "课程名最大行数", 5.0),
    AppearanceField("PlaceMaxLines", "地点最大行数", 4.0),
)

private val colorFields = listOf(
    "TitleTextColor" to "课程名颜色",
    "PlaceTextColor" to "地点颜色",
)

@Composable
internal fun AppearancePage(
    vm: SachetViewModel,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val original = remember { vm.repository.appearance() }
    val values = remember {
        mutableStateMapOf<String, String>().apply {
            numericFields.forEach { field ->
                put(field.key, original.optDouble(field.key, field.defaultValue).toString())
            }
        }
    }
    val colors = remember {
        mutableStateMapOf<String, String>().apply {
            colorFields.forEach { (key, _) ->
                put(key, original.optString(key, "#FFFFFFFF"))
            }
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    ExpressiveSubPageScaffold(
        title = "课表外观",
        subtitle = "实时预览课程卡片尺寸与字体",
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceLivePreview(values = values, colors = colors)

            ExpressiveSectionHeader("尺寸与排版")
            numericFields.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { field ->
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { values[field.key] = it },
                            label = { Text(field.label, maxLines = 1) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            ExpressiveSectionHeader("文本颜色", subtitle = "支持 #AARRGGBB 或 #RRGGBB")
            colorFields.forEach { (key, label) ->
                val raw = colors[key].orEmpty()
                OutlinedTextField(
                    value = raw,
                    onValueChange = { colors[key] = it },
                    label = { Text(label) },
                    placeholder = { Text("#FFFFFFFF") },
                    singleLine = true,
                    leadingIcon = {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = MaterialShapes.Circle.toShape(),
                            color = parseHexColorOrNull(raw) ?: MaterialTheme.colorScheme.outlineVariant,
                        ) {}
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                )
            }

            error?.let {
                ExpressiveMessageBanner(message = it, isError = true, onDismiss = { error = null })
            }

            Button(
                onClick = {
                    scope.launch {
                        try {
                            val result = JSONObject()
                            values.forEach { (key, text) ->
                                val parsed = text.toDoubleOrNull() ?: error("请输入有效数值")
                                require(parsed.isFinite() && parsed in 0.0..300.0) { "数值应在 0 到 300 之间" }
                                result.put(key, parsed)
                            }
                            require(result.optDouble("cardHeight") >= 24) { "卡片高度至少为 24" }
                            colors.forEach { (key, hex) ->
                                android.graphics.Color.parseColor(hex.trim())
                                result.put(key, hex.trim())
                            }
                            vm.saveAppearance(result)
                            onBack()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "请检查输入的数值或颜色格式"
                        }
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("保存外观")
            }
        }
    }
}

@Composable
private fun AppearanceLivePreview(
    values: Map<String, String>,
    colors: Map<String, String>,
) {
    val cardHeight = (values["cardHeight"]?.toFloatOrNull() ?: 60f).coerceIn(24f, 140f)
    val radius = (values["cardBorderRadius"]?.toFloatOrNull() ?: 6f).coerceIn(0f, 36f)
    val titleSize = (values["TitleFontSize"]?.toFloatOrNull() ?: 14f).coerceIn(8f, 32f)
    val placeSize = (values["PlaceFontSize"]?.toFloatOrNull() ?: 11f).coerceIn(8f, 28f)
    val titleWeight = FontWeight(((values["TitleFontWeight"]?.toIntOrNull() ?: 6).coerceIn(1, 9)) * 100)
    val placeWeight = FontWeight(((values["PlaceFontWeight"]?.toIntOrNull() ?: 5).coerceIn(1, 9)) * 100)
    val titleLines = (values["TitleMaxLines"]?.toIntOrNull() ?: 5).coerceIn(1, 8)
    val placeLines = (values["PlaceMaxLines"]?.toIntOrNull() ?: 4).coerceIn(1, 8)

    val titleColor = parseHexColor(colors["TitleTextColor"].orEmpty(), Color.White)
    val placeColor = parseHexColor(colors["PlaceTextColor"].orEmpty(), Color.White)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "卡片实时预览",
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .width(120.dp)
                        .height((cardHeight * 1.45f).dp),
                    shape = RoundedCornerShape(radius.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "高等数学 A",
                            color = titleColor,
                            fontSize = titleSize.sp,
                            fontWeight = titleWeight,
                            maxLines = titleLines,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = MiSansFamily,
                        )
                        Text(
                            text = "逸夫楼 302",
                            color = placeColor,
                            fontSize = placeSize.sp,
                            fontWeight = placeWeight,
                            maxLines = placeLines,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = MiSansFamily,
                        )
                    }
                }
            }
        }
    }
}

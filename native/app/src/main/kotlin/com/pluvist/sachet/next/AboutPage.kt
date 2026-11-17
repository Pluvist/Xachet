@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AboutReference(val title: String, val subtitle: String, val url: String)

private val references = listOf(
    AboutReference("上游 Sachet", "基于 Sachet 二次修改，感谢 wyvern1723 及其贡献者", "https://github.com/wyvern1723/sachet"),
    AboutReference("MeiloX", "界面与交互参考", "https://github.com/NEORUAA/MeiloX"),
    AboutReference("Glasmio", "历史原生组件与玻璃渲染参考", "https://github.com/NEORUAA/Glasmio"),
    AboutReference("HyperCeiler", "引导页布局参考 · AGPL-3.0", "https://github.com/ReChronoRain/HyperCeiler"),
    AboutReference("Cresto", "页面转场参考 · Apache-2.0", "https://github.com/Nevodev/Cresto"),
    AboutReference("Tomato", "浮窗底栏与标题栏字体参考 · GPL-3.0", "https://github.com/nsh07/Tomato"),
    AboutReference("Google Sans Flex", "英文字形与可变数字排印 · SIL OFL 1.1", "https://github.com/google/fonts"),
    AboutReference("MiSans 可变字体", "本应用使用小米 MiSans 字体，字体版权归小米所有", "https://hyperos.mi.com/font/"),
)

private val legalDocs = listOf(
    "LICENSE" to "AGPL-3.0 许可",
    "LICENSE-MIT" to "Sachet 上游 MIT 许可",
    "THIRD_PARTY_NOTICES.md" to "第三方声明",
    "Cresto-LICENSE" to "Cresto Apache-2.0 许可",
    "GoogleSansFlex-LICENSE" to "Google Sans Flex OFL-1.1 许可",
)

@Composable
internal fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var document by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val motionScheme = MaterialTheme.motionScheme

    val docBackState = rememberPredictiveBackState(enabled = document != null) { document = null }

    LaunchedEffect(document) {
        val file = document
        if (file != null) {
            text = withContext(Dispatchers.IO) {
                context.assets.open("legal/$file").bufferedReader().use { it.readText() }
            }
        }
    }

    ExpressiveSubPageScaffold(
        title = document ?: "关于",
        subtitle = if (document == null) "Material 3 Expressive 原生界面" else "开源许可正文",
        onBack = {
            if (document != null) document = null else onBack()
        },
        modifier = if (document != null) Modifier.predictiveBackContainer(docBackState) else Modifier,
    ) { padding ->
        AnimatedContent(
            targetState = document,
            transitionSpec = {
                ExpressiveMotion.directionalSlideTransform(motionScheme, targetState != null)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "AboutLegalDocTransition",
        ) { currentDoc ->
            if (currentDoc != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    item(key = "doc_$currentDoc") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "about_header_card") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLargeIncreased,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Surface(
                                    modifier = Modifier.size(56.dp),
                                    shape = MaterialShapes.VerySunny.toShape(),
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.School,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Xachet",
                                        style = XachetTypeTokens.BrandCard,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = "版本 ${BuildConfig.VERSION_NAME} · 作者 Pluvist",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }

                    item(key = "about_refs_header") { ExpressiveSectionHeader("致谢与开源参考") }
                    item(key = "about_refs_group") {
                        ExpressiveSegmentedGroup {
                            references.forEachIndexed { index, ref ->
                                ExpressiveSegmentedActionRow(
                                    index = index,
                                    count = references.size,
                                    title = ref.title,
                                    subtitle = ref.subtitle,
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    },
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ref.url)))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    item(key = "about_legal_header") { ExpressiveSectionHeader("许可与声明") }
                    item(key = "about_legal_group") {
                        ExpressiveSegmentedGroup {
                            legalDocs.forEachIndexed { index, (file, label) ->
                                ExpressiveSegmentedActionRow(
                                    index = index,
                                    count = legalDocs.size,
                                    title = label,
                                    leading = { Icon(Icons.Rounded.Description, contentDescription = null) },
                                    onClick = { document = file },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

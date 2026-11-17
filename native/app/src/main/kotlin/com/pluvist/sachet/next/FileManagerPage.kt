@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun FileManagerPage(
    vm: SachetViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var files by remember { mutableStateOf<List<ManagedDataFile>>(emptyList()) }
    var selected by remember { mutableStateOf<ManagedDataFile?>(null) }
    var body by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var loadingFiles by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val motionScheme = MaterialTheme.motionScheme

    val editorBackState = rememberPredictiveBackState(enabled = selected != null) {
        selected = null
        confirmDelete = false
    }

    LaunchedEffect(Unit) {
        try {
            files = vm.repository.managedFiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        } finally {
            loadingFiles = false
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(it) ?: error("无法导出文件")
                        output.bufferedWriter().use { writer -> writer.write(body) }
                    }
                    error = null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message
                }
            }
        }
    }

    if (confirmDelete && selected != null) {
        ExpressiveConfirmDialog(
            title = "删除数据文件",
            body = "确定要永久删除 ${selected?.name} 吗？此操作无法撤销。",
            confirmText = "确认删除",
            destructive = true,
            onConfirm = {
                val target = selected ?: return@ExpressiveConfirmDialog
                confirmDelete = false
                scope.launch {
                    loadingFiles = true
                    try {
                        vm.repository.deleteManagedFile(target)
                        files = vm.repository.managedFiles()
                        vm.reloadLocal()
                        selected = null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loadingFiles = false
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    ExpressiveSubPageScaffold(
        title = selected?.name ?: "数据文件",
        subtitle = selected?.let { "${it.kind} · ${it.bytes} B" } ?: "管理本地课表、配色与缓存 JSON",
        onBack = {
            if (selected != null) {
                selected = null
                confirmDelete = false
            } else {
                onBack()
            }
        },
        modifier = if (selected != null) Modifier.predictiveBackContainer(editorBackState) else Modifier,
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            transitionSpec = {
                ExpressiveMotion.directionalSlideTransform(motionScheme, targetState != null)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "FileManagerEditorTransition",
        ) { currentSelected ->
            if (loadingFiles && files.isEmpty() && currentSelected == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    XachetLoadingCard("正在加载本地数据文件…")
                }
            } else if (currentSelected == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    error?.let { msg ->
                        item(key = "files_error") {
                            ExpressiveMessageBanner(
                                message = msg,
                                isError = true,
                                onDismiss = { error = null },
                            )
                        }
                    }
                    if (files.isEmpty()) {
                        item(key = "files_empty") {
                            ExpressiveEmptyCard(
                                icon = Icons.Rounded.FolderOpen,
                                title = "还没有课程或查询数据文件",
                                subtitle = "同步课表或使用教务查询后会在这里保留离线 JSON 缓存",
                            )
                        }
                    } else {
                        item(key = "files_group") {
                            ExpressiveSegmentedGroup {
                                files.forEachIndexed { index, file ->
                                    ExpressiveSegmentedActionRow(
                                        index = index,
                                        count = files.size,
                                        title = file.name,
                                        subtitle = "${file.kind} · ${file.bytes} B",
                                        leading = { Icon(Icons.Rounded.Description, contentDescription = null) },
                                        onClick = {
                                            selected = file
                                            confirmDelete = false
                                            error = null
                                            scope.launch {
                                                loadingFiles = true
                                                try {
                                                    body = withContext(Dispatchers.IO) { File(file.path).readText() }
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    error = e.message
                                                } finally {
                                                    loadingFiles = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val activeFile = currentSelected
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                error?.let { msg ->
                    ExpressiveMessageBanner(message = msg, isError = true, onDismiss = { error = null })
                }

                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("JSON 内容") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = 440.dp),
                    shape = MaterialTheme.shapes.large,
                )

                Button(
                    onClick = {
                        scope.launch {
                            loadingFiles = true
                            try {
                                vm.repository.writeManagedFile(activeFile, body)
                                files = vm.repository.managedFiles()
                                vm.reloadLocal()
                                selected = null
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loadingFiles = false
                            }
                        }
                    },
                    enabled = !loadingFiles,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("保存修改")
                }

                if (activeFile.kind in setOf("schedule", "colors")) {
                    OutlinedButton(
                        onClick = {
                            if (activeFile.kind == "schedule") vm.importSchedule(body) else vm.importColors(body)
                            selected = null
                        },
                        enabled = !loadingFiles,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("启用此文件")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { exporter.launch(activeFile.name) },
                        enabled = !loadingFiles,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("导出文件")
                    }
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        enabled = !loadingFiles,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除")
                    }
                }
                }
            }
        }
    }
}

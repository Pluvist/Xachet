@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.pluvist.sachet.next

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text as semanticsText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Standard sub-page scaffold using M3 Expressive [MediumFlexibleTopAppBar] with back navigation. */
@Composable
internal fun ExpressiveSubPageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text ->
                    {
                        Text(
                            text = text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
    ) { innerPadding ->
        val extraTopPadding = object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
                innerPadding.calculateLeftPadding(layoutDirection)

            override fun calculateTopPadding(): Dp = innerPadding.calculateTopPadding() + 18.dp

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
                innerPadding.calculateRightPadding(layoutDirection)

            override fun calculateBottomPadding(): Dp = innerPadding.calculateBottomPadding()
        }
        content(extraTopPadding)
    }
}

/** Compact single-row pinned top app bar when screen space should stay focused on dense content. */
@Composable
internal fun ExpressivePinnedScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@Composable
internal fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun expressiveSegmentedColors(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
): ListItemColors = ListItemDefaults.segmentedColors(
    containerColor = containerColor,
    selectedContainerColor = selectedContainerColor,
    selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

/** Segmented vertical group spacing matching Material 3 Expressive [ListItemDefaults.SegmentedGap]. */
@Composable
internal fun ExpressiveSegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

@Composable
internal fun ExpressiveSegmentedActionRow(
    index: Int,
    count: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmallEmphasized,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    showChevron: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    colors: ListItemColors = expressiveSegmentedColors(),
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = colors,
        leadingContent = leading,
        supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = {
            if (trailing != null) {
                trailing()
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!value.isNullOrBlank()) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        content = {
            Text(
                text = title,
                style = titleStyle,
            )
        },
    )
}

@Composable
internal fun ExpressiveSegmentedInfoRow(
    index: Int,
    count: Int,
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(),
        colors = expressiveSegmentedColors(),
        leadingContent = leading,
        supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = {
            if (value.any { it.isDigit() }) {
                RollingNumberText(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
        },
    )
}

@Composable
internal fun ExpressiveSwitchRow(
    index: Int,
    count: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        colors = expressiveSegmentedColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        leadingContent = leading,
        supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else Icons.Rounded.Close,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                },
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmallEmphasized,
            )
        },
    )
}

/** Connected Material 3 Expressive toggle button group using asymmetric outer/inner corner morphs. */
@Composable
internal fun ExpressiveConnectedToggleRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, label ->
            val checked = index == selectedIndex
            val shapes = when {
                options.size == 1 -> ToggleButtonDefaults.shapesFor(ToggleButtonDefaults.size)
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            FilledTonalToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shapes = shapes,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** Clickable segmented selector field that opens a modal M3 Expressive choice sheet. */
@Composable
internal fun ExpressiveSelectorRow(
    index: Int,
    count: Int,
    label: String,
    selectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(),
        colors = expressiveSegmentedColors(),
        leadingContent = leading,
        supportingContent = {
            Text(
                text = selectedLabel.ifBlank { "未选择" },
                style = MaterialTheme.typography.bodyLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = "更改$label",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Modal bottom sheet presenting choices in an M3 Expressive segmented radio group. */
@Composable
internal fun ExpressiveChoiceSheet(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (Pair<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndex = remember(options, selectedKey) {
        options.indexOfFirst { it.first == selectedKey }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                items(
                    count = options.size,
                    key = { index -> "${options[index].first}_$index" },
                ) { index ->
                    val option = options[index]
                    val selected = option.first == selectedKey
                    SegmentedListItem(
                        selected = selected,
                        onClick = {
                            onSelect(option)
                            onDismiss()
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        trailingContent = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "已选中",
                                )
                            }
                        } else null,
                        content = {
                            Text(
                                text = option.second.ifBlank { option.first },
                                style = MaterialTheme.typography.titleSmallEmphasized,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpressiveMessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, color = content, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭提示",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExpressiveEmptyCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
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
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun ExpressiveConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.headlineSmallEmphasized) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(dismissText)
            }
        },
    )
}

internal enum class RollingSegmentKind {
    Text,
    Digits,
}

internal data class RollingSegment(
    val kind: RollingSegmentKind,
    val value: String,
)

internal fun tokenizeRollingText(text: String): List<RollingSegment> {
    if (text.isEmpty()) return emptyList()
    val result = ArrayList<RollingSegment>(6)
    var start = 0
    var inDigits = text[0].isDigit()
    for (i in 1 until text.length) {
        val isDigit = text[i].isDigit()
        if (isDigit != inDigits) {
            result += RollingSegment(
                kind = if (inDigits) RollingSegmentKind.Digits else RollingSegmentKind.Text,
                value = text.substring(start, i),
            )
            start = i
            inDigits = isDigit
        }
    }
    result += RollingSegment(
        kind = if (inDigits) RollingSegmentKind.Digits else RollingSegmentKind.Text,
        value = text.substring(start),
    )
    return result
}

internal fun compareRollingNumbers(previous: String, current: String): Int {
    if (previous == current) return 0
    val oldNums = extractNumericGroups(previous)
    val newNums = extractNumericGroups(current)
    if (oldNums.isEmpty() || newNums.isEmpty()) {
        return current.compareTo(previous).coerceIn(-1, 1)
    }
    if (oldNums.size != newNums.size) {
        return newNums.size.compareTo(oldNums.size)
    }
    for (i in oldNums.indices) {
        val cmp = compareDigitStrings(newNums[i], oldNums[i])
        if (cmp != 0) return cmp
    }
    return current.compareTo(previous).coerceIn(-1, 1)
}

private fun extractNumericGroups(text: String): List<String> {
    val groups = ArrayList<String>(4)
    var start = -1
    for (i in text.indices) {
        if (text[i].isDigit()) {
            if (start < 0) start = i
        } else if (start >= 0) {
            groups += text.substring(start, i)
            start = -1
        }
    }
    if (start >= 0) groups += text.substring(start)
    return groups
}

private fun compareDigitStrings(a: String, b: String): Int {
    val trimmedA = a.trimStart('0').ifEmpty { "0" }
    val trimmedB = b.trimStart('0').ifEmpty { "0" }
    if (trimmedA.length != trimmedB.length) {
        return trimmedA.length.compareTo(trimmedB.length)
    }
    return trimmedA.compareTo(trimmedB).coerceIn(-1, 1)
}

private class RollingTextHistory(var lastText: String)

private val RollingDigitStrings = Array(10) { ('0' + it).toString() }
private val RollingFadeIn = fadeIn(tween(150))
private val RollingFadeOut = fadeOut(tween(110))
private val RollingTextFadeIn = fadeIn(tween(140))
private val RollingTextNoClipSizeTransform = SizeTransform(clip = false)

private val RollingSlideSpec = tween<androidx.compose.ui.unit.IntOffset>(
    durationMillis = 220,
    easing = FastOutSlowInEasing,
)

private val RollingSizeSpec = spring<androidx.compose.ui.unit.IntSize>(
    dampingRatio = 0.9f,
    stiffness = 700f,
)

/**
 * Renders numeric values and live countdowns with per-digit vertical slot-machine rolling transitions
 * using tabular numerals to keep widths steady and preserve unified accessibility semantics.
 */
@Composable
internal fun RollingNumberText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    animateSize: Boolean = true,
) {
    val tracker = remember { RollingTextHistory(text) }
    val direction = remember(text) {
        if (text == tracker.lastText) {
            0
        } else {
            compareRollingNumbers(tracker.lastText, text).let { if (it == 0) 1 else it }
        }
    }
    SideEffect { tracker.lastText = text }

    val resolvedColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val resolvedStyle = remember(style) { style.tabular() }
    val segments = remember(text) { tokenizeRollingText(text) }
    val slideForward = direction >= 0

    Row(
        modifier = modifier
            .let { base -> if (animateSize) base.animateContentSize(animationSpec = RollingSizeSpec) else base }
            .clearAndSetSemantics {
                semanticsText = AnnotatedString(text)
                contentDescription = text
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEachIndexed { segIndex, segment ->
            key(segIndex, segment.kind) {
                when (segment.kind) {
                    RollingSegmentKind.Text -> {
                        val textModifier = if (segIndex == segments.lastIndex && overflow == TextOverflow.Ellipsis) {
                            Modifier.weight(1f, fill = false)
                        } else {
                            Modifier
                        }
                        val v = segment.value
                        val isStaticPunctuation = v.length == 1 && (v[0] == ':' || v[0] == '.' || v[0] == '–' || v[0] == '/')
                        if (isStaticPunctuation) {
                            Text(
                                text = v,
                                style = resolvedStyle,
                                color = resolvedColor,
                                maxLines = maxLines,
                                softWrap = false,
                                overflow = overflow,
                                modifier = textModifier,
                            )
                        } else {
                            AnimatedContent(
                                targetState = v,
                                transitionSpec = {
                                    (RollingTextFadeIn togetherWith RollingFadeOut)
                                        .using(RollingTextNoClipSizeTransform)
                                },
                                modifier = textModifier,
                                label = "RollingStaticSeg-$segIndex",
                            ) { value ->
                                Text(
                                    text = value,
                                    style = resolvedStyle,
                                    color = resolvedColor,
                                    maxLines = maxLines,
                                    softWrap = false,
                                    overflow = overflow,
                                )
                            }
                        }
                    }

                    RollingSegmentKind.Digits -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val len = segment.value.length
                            for (i in 0 until len) {
                                val placeFromRight = len - 1 - i
                                val ch = segment.value[i]
                                key(placeFromRight) {
                                    AnimatedContent(
                                        targetState = ch,
                                        transitionSpec = {
                                            (slideInVertically(RollingSlideSpec) { h ->
                                                if (slideForward) h else -h
                                            } + RollingFadeIn) togetherWith
                                                (slideOutVertically(RollingSlideSpec) { h ->
                                                    if (slideForward) -h else h
                                                } + RollingFadeOut)
                                        },
                                        modifier = Modifier.clipToBounds(),
                                        label = "RollingDigit-$segIndex-$placeFromRight",
                                    ) { digit ->
                                        val label = RollingDigitStrings.getOrNull(digit - '0') ?: digit.toString()
                                        Text(
                                            text = label,
                                            style = resolvedStyle,
                                            color = resolvedColor,
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

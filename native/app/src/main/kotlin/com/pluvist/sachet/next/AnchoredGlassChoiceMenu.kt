package com.pluvist.sachet.next

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem

/**
 * Supply the live page backdrop at the page root. A menu is drawn in a Popup window,
 * so sampling the page's static backdrop would otherwise omit its visible content.
 * The fallback allows this control to be used outside the main page host as well.
 */
val LocalAnchoredMenuBackdrop = staticCompositionLocalOf<Backdrop?> { null }

internal const val AnchoredMenuSizeScale = 0.9f

private data class ChoiceMenuRow(val option: Pair<String, String>, val height: Dp, val lines: Int)

/**
 * A selector attached to the bounds of [anchor]. Each pair is value to visible label.
 * [anchorBounds], when supplied, must be in window coordinates. Both opening and
 * closing use GlasmioMenu's interruptible spring; no independent state delays reopening.
 */
@Composable
fun AnchoredGlassChoiceMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    anchorBounds: Rect? = null,
    onClosed: (() -> Unit)? = null,
    baseWidth: Dp = 268.dp,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer(cacheSize=128)
    val backdrop = LocalAnchoredMenuBackdrop.current ?: LocalGlassBackdrop.current
    val depth = LocalAnchoredMenuDepth.current
    val depthOwner = remember { Any() }
    DisposableEffect(depth, depthOwner) { onDispose { depth?.remove(depthOwner) } }
    LaunchedEffect(depth, depthOwner, expanded) { depth?.setExpanded(depthOwner, expanded) }
    val colors = LocalGlassColors.current
    val menuAppearance = footerGlassAppearance(LocalGlassAppearance.current, colors.isDark)
    val rowHeight = 44.dp * AnchoredMenuSizeScale
    val contentPadding = 10.dp * AnchoredMenuSizeScale
    val width = minOf(baseWidth * AnchoredMenuSizeScale, (configuration.screenWidthDp - 24).coerceAtLeast(160).dp)
    val textStyle = IosTypography.body.let { it.copy(fontSize=it.fontSize*AnchoredMenuSizeScale,
        lineHeight=it.lineHeight*AnchoredMenuSizeScale) }
    val textWidthPx = with(density) { (width-contentPadding*2-32.dp-20.dp*AnchoredMenuSizeScale).toPx() }
        .toInt().coerceAtLeast(1)
    val lineHeight = with(density) { textStyle.lineHeight.toDp() }
    val rows = remember(options,width,density.fontScale,textMeasurer) {
        options.map { option ->
            val lines=textMeasurer.measure(AnnotatedString(option.second),style=textStyle,
                maxLines=10,constraints=Constraints(maxWidth=textWidthPx)).lineCount.coerceAtLeast(1)
            ChoiceMenuRow(option,rowHeight+lineHeight*(lines-1),lines)
        }
    }
    // Leave room for the system bars and an on-screen keyboard. The list, unlike the
    // Popup shell, scrolls when a year, building, or major picker has many entries.
    val height = minOf(
        rows.fold(contentPadding * 2) { sum, row -> sum + row.height },
        (configuration.screenHeightDp * 0.72f * AnchoredMenuSizeScale).coerceAtLeast(100f).dp,
    )
    val listState = rememberLazyListState()
    LaunchedEffect(expanded, selected, options) {
        if (expanded && options.size > 6) {
            val index = options.indexOfFirst { it.first == selected }
            if (index >= 0) listState.scrollToItem(index)
        }
    }

    CompositionLocalProvider(LocalGlassAppearance provides menuAppearance) {
        GlasmioMenu(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            itemCount = options.size,
            modifier = modifier,
            backdrop = backdrop,
            keepAnchorVisible = true,
            fullScreen = true,
            externalAnchorBounds = anchorBounds,
            menuWidth = width,
            heightOverride = height,
            contentPadding = contentPadding,
            exportChildBackdrop = false,
            onClosed = onClosed,
            morphFromAnchor = true,
            anchor = anchor,
        ) { childBackdrop, close ->
            if (rows.size <= 6) rows.forEach { row ->
                AnchoredGlassChoiceItem(row, selected, childBackdrop, colors.content,
                    onClick = { onSelect(row.option.first); close() })
            } else LazyColumn(Modifier.fillMaxSize(), state = listState) {
                itemsIndexed(rows) { _, row ->
                    AnchoredGlassChoiceItem(row, selected, childBackdrop, colors.content,
                        onClick = { onSelect(row.option.first); close() })
                }
            }
        }
    }
}

@Composable
private fun AnchoredGlassChoiceItem(
    row: ChoiceMenuRow, selected: String?,
    backdrop: Backdrop, iconColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit,
) {
    GlasmioMenuItem(
        title = row.option.second,
        onClick = onClick,
        backdrop = backdrop,
        rowHeight = row.height,
        maxTitleLines = row.lines,
        textScale = AnchoredMenuSizeScale,
        reserveLeadingIconSpace = false,
        trailing = if (row.option.first == selected) {
            { SfIcon("checkmark", contentDescription = "已选择", size = 20.dp * AnchoredMenuSizeScale, tint = iconColor) }
        } else null,
    )
}

/** A settings-style choice row that opens the shared anchored glass menu in place. */
@Composable
fun AnchoredGlassChoiceRow(
    title: String,
    detail: String?,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    showTopSeparator: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    var sourceBounds by remember { mutableStateOf<Rect?>(null) }
    var transitionActive by remember { mutableStateOf(false) }
    val coordinates=remember { arrayOfNulls<LayoutCoordinates>(1) }
    val density=LocalDensity.current
    val sourceSizePx=with(density){40.dp.toPx()}
    val canOpen = enabled && options.isNotEmpty()
    LaunchedEffect(canOpen){if(!canOpen)expanded=false}
    AnchoredGlassChoiceMenu(
        expanded = expanded,
        onExpandedChange = { if (canOpen || !it) {expanded=it;if(it)transitionActive=true} },
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        anchorBounds=sourceBounds,
        onClosed={transitionActive=false},
        anchor = { open ->
            GlasmioListRow(
                title = title,
                modifier=Modifier.onGloballyPositioned{coordinates[0]=it},
                detail = detail,
                leading = leading,
                enabled = canOpen,
                showTopSeparator = showTopSeparator,
                onClick = if (canOpen) {{
                    if(!transitionActive)coordinates[0]?.boundsInWindow()?.let{bounds->
                        val side=minOf(sourceSizePx,bounds.width,bounds.height)
                        sourceBounds=Rect(bounds.right-side,bounds.center.y-side/2f,
                            bounds.right,bounds.center.y+side/2f)
                    }
                    if(sourceBounds!=null)open()
                }} else null,
            )
        },
    )
}

package com.pluvist.sachet.next

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.LruCache
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

internal enum class WidgetCourseTone {
    InClass,
    Waiting,
    Idle,
}

internal data class WidgetThemeTokens(
    val scheme: ColorScheme,
    val dark: Boolean,
    val surfaceBg: Int,
    val outlineBorder: Int,
    val heroBg: Int,
    val heroFg: Int,
    val heroMuted: Int,
    val secondaryCardBg: Int,
    val secondaryCardFg: Int,
    val secondaryCardMuted: Int,
    val weekPillBg: Int,
    val weekPillFg: Int,
    val statusPillBg: Int,
    val statusPillFg: Int,
    val countdownPillBg: Int,
    val countdownPillFg: Int,
    val nextLabelBg: Int,
    val nextLabelFg: Int,
    val actionBg: Int,
    val actionFg: Int,
    val headerFg: Int,
    val footerFg: Int,
    val accentBloom: Int,
    val secondaryBloom: Int,
    val dotColors: List<Int>,
)

/**
 * Generates Material 3 Expressive tonal backdrop & role tokens for both widget sizes
 * (`2x2` and `4x2`), staying synchronized with the active app theme seed and light/dark mode.
 */
internal object CourseWidgetGlow {
    private val images = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    internal fun resolveDarkMode(context: Context, data: JSONObject): Boolean {
        val mode = data.optInt("themeMode", 0)
        return mode == 2 || (
            mode == 0 &&
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            )
    }

    internal fun resolveThemeTokens(
        context: Context,
        data: JSONObject,
        tone: WidgetCourseTone,
    ): WidgetThemeTokens {
        val dark = resolveDarkMode(context, data)
        val scheme = resolveXachetColorScheme(settings = data, darkTheme = dark, context = context)
        val (heroBgColor, heroFgColor) = when (tone) {
            WidgetCourseTone.InClass -> scheme.primaryContainer to scheme.onPrimaryContainer
            WidgetCourseTone.Waiting -> scheme.secondaryContainer to scheme.onSecondaryContainer
            WidgetCourseTone.Idle -> scheme.surfaceContainer to scheme.onSurface
        }
        val (statusBg, statusFg) = when (tone) {
            WidgetCourseTone.InClass -> scheme.primary to scheme.onPrimary
            WidgetCourseTone.Waiting -> scheme.tertiaryContainer to scheme.onTertiaryContainer
            WidgetCourseTone.Idle -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        }
        val (countdownBg, countdownFg) = when (tone) {
            WidgetCourseTone.InClass -> scheme.primary to scheme.onPrimary
            WidgetCourseTone.Waiting -> scheme.secondary to scheme.onSecondary
            WidgetCourseTone.Idle -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        }
        return WidgetThemeTokens(
            scheme = scheme,
            dark = dark,
            surfaceBg = scheme.surfaceContainerLow.toArgb(),
            outlineBorder = scheme.outlineVariant.copy(alpha = if (dark) 0.55f else 0.72f).toArgb(),
            heroBg = heroBgColor.toArgb(),
            heroFg = heroFgColor.toArgb(),
            heroMuted = lerp(heroFgColor, heroBgColor, 0.24f).toArgb(),
            secondaryCardBg = scheme.surfaceContainerHigh.toArgb(),
            secondaryCardFg = scheme.onSurface.toArgb(),
            secondaryCardMuted = scheme.onSurfaceVariant.toArgb(),
            weekPillBg = scheme.tertiaryContainer.toArgb(),
            weekPillFg = scheme.onTertiaryContainer.toArgb(),
            statusPillBg = statusBg.toArgb(),
            statusPillFg = statusFg.toArgb(),
            countdownPillBg = countdownBg.toArgb(),
            countdownPillFg = countdownFg.toArgb(),
            nextLabelBg = scheme.secondaryContainer.toArgb(),
            nextLabelFg = scheme.onSecondaryContainer.toArgb(),
            actionBg = scheme.surfaceContainerHigh.toArgb(),
            actionFg = scheme.primary.toArgb(),
            headerFg = scheme.onSurfaceVariant.toArgb(),
            footerFg = scheme.onSurfaceVariant.toArgb(),
            accentBloom = scheme.primaryContainer.copy(alpha = if (dark) 0.16f else 0.34f).toArgb(),
            secondaryBloom = scheme.tertiaryContainer.copy(alpha = if (dark) 0.14f else 0.28f).toArgb(),
            dotColors = listOf(
                scheme.primary.toArgb(),
                scheme.secondary.toArgb(),
                scheme.tertiary.toArgb(),
            ),
        )
    }

    internal fun tintBackground(view: RemoteViews, viewId: Int, argb: Int) {
        view.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(argb))
    }

    @Synchronized
    private fun surfaceBitmap(
        tokens: WidgetThemeTokens,
        wide: Boolean,
        widthDp: Int,
        heightDp: Int,
    ): Bitmap {
        val key = "${tokens.surfaceBg}/${tokens.outlineBorder}/${tokens.accentBloom}/${tokens.dark}/$wide/$widthDp/$heightDp"
        images.get(key)?.let { return it }

        val width = 384
        val height = (width * heightDp / widthDp).coerceIn(96, 512)
        val sx = width.toFloat() / widthDp
        val sy = height.toFloat() / heightDp
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val outline = Path().apply {
            addRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()),
                28 * sx,
                28 * sy,
                Path.Direction.CW,
            )
        }
        canvas.clipPath(outline)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tokens.surfaceBg
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        // Subtle Material 3 Expressive corner tonal blooms behind bento sub-cards.
        fillPaint.color = tokens.accentBloom
        canvas.drawCircle(width * 0.92f, height * 0.14f, minOf(width, height) * 0.42f, fillPaint)
        fillPaint.color = tokens.secondaryBloom
        canvas.drawCircle(width * 0.08f, height * 0.90f, minOf(width, height) * 0.36f, fillPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.25f * sx
            color = tokens.outlineBorder
        }
        canvas.drawRoundRect(
            RectF(0.75f * sx, 0.75f * sy, width - 0.75f * sx, height - 0.75f * sy),
            27.2f * sx,
            27.2f * sy,
            borderPaint,
        )

        images.put(key, bitmap)
        return bitmap
    }

    fun apply(
        context: Context,
        view: RemoteViews,
        tokens: WidgetThemeTokens,
        wide: Boolean,
        width: Int,
        height: Int,
    ) {
        view.setInt(R.id.widget_surface, "setBackgroundResource", R.drawable.course_widget_surface)
        tintBackground(view, R.id.widget_surface, tokens.surfaceBg)
        view.setImageViewBitmap(
            R.id.widget_glow,
            surfaceBitmap(tokens, wide, maxOf(80, width), maxOf(60, height - 2)),
        )
        if (wide) {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density + 0.5f).toInt()
            val vertical = if (height - 2 < 120) 10 else 12
            view.setViewPadding(R.id.widget_content, dp(12), dp(vertical), dp(12), dp(vertical))
        }
    }
}

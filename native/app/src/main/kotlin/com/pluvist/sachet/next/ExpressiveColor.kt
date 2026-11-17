package com.pluvist.sachet.next

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import org.json.JSONObject

internal val XachetDefaultSeed = Color(0xFF9F181E)
internal val XachetMixPink = Color(0xFFF5ABB9)
internal val XachetMixBlue = Color(0xFF5BCFFA)

internal const val DEFAULT_UI_SCALE_PERCENT = 110
internal val XachetUiScalePresets = listOf(90, 100, 110, 120)

internal fun resolveXachetUiScalePercent(settings: JSONObject): Int {
    if (!settings.has("uiScalePercent")) return DEFAULT_UI_SCALE_PERCENT
    val raw = settings.optInt("uiScalePercent", DEFAULT_UI_SCALE_PERCENT)
    return raw.coerceIn(85, 130)
}

internal fun resolveXachetUiScale(settings: JSONObject): Float =
    resolveXachetUiScalePercent(settings) / 100f

internal data class SeedColorPreset(
    val name: String,
    val subtitle: String,
    val hex: String,
    val color: Color,
    val secondaryColor: Color? = null,
)

internal val XachetSeedPresets = listOf(
    SeedColorPreset("韶峰红", "#9F181E", "#9F181E", Color(0xFF9F181E)),
    SeedColorPreset("湘水蓝", "#014190", "#014190", Color(0xFF014190)),
    SeedColorPreset("Legacy", "#F5ABB9 · #5BCFFA", "#F5ABB9", XachetMixPink, XachetMixBlue),
)

/** Harmonized Material 3 course card swatches (fallback when course has no user-defined hex color). */
internal val ExpressiveCoursePalette = listOf(
    Color(0xFF2B6CB0), // cobalt
    Color(0xFF2F855A), // emerald
    Color(0xFF6B46C1), // violet
    Color(0xFFB7791F), // amber-ochre
    Color(0xFFC53030), // crimson
    Color(0xFF00838F), // teal
    Color(0xFFD53F8C), // berry
    Color(0xFF4C51BF), // indigo
    Color(0xFFDD6B20), // tangerine
    Color(0xFF38A169), // leaf
    Color(0xFF805AD5), // amethyst
    Color(0xFF3182CE), // azure
)

internal fun parseHexColorOrNull(raw: String): Color? {
    val clean = raw.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    val argb = if (clean.length == 6) (0xFF000000L or parsed).toInt() else parsed.toInt()
    return Color(argb)
}

internal fun parseHexColor(raw: String, fallback: Color = XachetDefaultSeed): Color =
    parseHexColorOrNull(raw) ?: fallback

internal fun formatHexColor(color: Color, includeAlpha: Boolean = false): String {
    val argb = color.toArgb()
    return if (includeAlpha) {
        "#%08X".format(argb)
    } else {
        "#%06X".format(argb and 0xFFFFFF)
    }
}

internal fun contrastRatio(foreground: Color, background: Color): Double {
    val l1 = foreground.luminance().toDouble()
    val l2 = background.luminance().toDouble()
    val lighter = max(l1, l2)
    val darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

/**
 * Generates a full 48-role Material 3 Expressive [ColorScheme].
 *
 * When [useDynamicColor] is enabled on Android 12+ and [context] is non-null, system wallpaper
 * dynamic colors are used. Otherwise a perceptually uniform tonal palette engine (Oklab hue/chroma
 * locked onto exact CIE L* relative luminance stops) derives every role from [seedColor].
 */
internal fun xachetColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    useDynamicColor: Boolean = false,
    context: Context? = null,
    secondarySeed: Color? = null,
): ColorScheme {
    if (useDynamicColor && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    return buildSeedColorScheme(seedColor = seedColor, darkTheme = darkTheme, secondarySeed = secondarySeed)
}

internal fun resolveXachetColorScheme(
    settings: JSONObject,
    darkTheme: Boolean,
    context: Context? = null,
): ColorScheme {
    val rawThemeHex = settings.optString("themeColor", "#9F181E")
    val matched = XachetSeedPresets.firstOrNull { it.hex.equals(rawThemeHex, ignoreCase = true) }
    val seedColor = when {
        matched != null -> matched.color
        rawThemeHex.equals("#0088FF", ignoreCase = true) -> XachetDefaultSeed
        else -> parseHexColor(rawThemeHex, fallback = XachetDefaultSeed)
    }
    val useDynamicColor = settings.optBoolean("useDynamicColor", false)
    return xachetColorScheme(
        seedColor = seedColor,
        darkTheme = darkTheme,
        useDynamicColor = useDynamicColor,
        context = context,
        secondarySeed = matched?.secondaryColor,
    )
}

internal fun buildSeedColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    secondarySeed: Color? = null,
): ColorScheme {
    val mixSecondary = secondarySeed ?: if ((seedColor.toArgb() and 0xFFFFFF) == (XachetMixPink.toArgb() and 0xFFFFFF)) {
        XachetMixBlue
    } else {
        null
    }
    val (seedHue, seedChroma) = colorToOklabHueChroma(seedColor)
    val (errorHue, errorChroma) = colorToOklabHueChroma(Color(0xFFB3261E))

    val isAchromatic = seedChroma < 0.012
    val p: TonalRamp
    val s: TonalRamp
    val t: TonalRamp
    val nv: TonalRamp
    val isLightMix = mixSecondary != null
    if (mixSecondary != null) {
        val (blueHue, blueChroma) = colorToOklabHueChroma(mixSecondary)
        p = TonalRamp(seedHue, seedChroma.coerceIn(0.08, 0.14))
        s = TonalRamp(blueHue, blueChroma.coerceIn(0.08, 0.13))
        t = TonalRamp(blueHue, max(0.09, blueChroma * 0.9).coerceAtMost(0.14))
        nv = TonalRamp((seedHue + blueHue) * 0.5, 0.012)
    } else {
        val primaryChroma = if (isAchromatic) 0.0 else seedChroma.coerceIn(0.11, 0.22)
        val secondaryChroma = if (isAchromatic) 0.0 else (seedChroma * 0.38).coerceIn(0.045, 0.085)
        val tertiaryHue = seedHue + PI / 3.0 // +60° analogous accent for M3 Expressive vibrancy
        val tertiaryChroma = if (isAchromatic) 0.0 else max(0.08, seedChroma * 0.52).coerceAtMost(0.16)
        val neutralVariantChroma = if (isAchromatic) 0.0 else 0.018

        p = TonalRamp(seedHue, primaryChroma)
        s = TonalRamp(seedHue, secondaryChroma)
        t = TonalRamp(tertiaryHue, tertiaryChroma)
        nv = TonalRamp(seedHue, neutralVariantChroma)
    }
    val e = TonalRamp(errorHue, errorChroma.coerceAtLeast(0.16))

    return if (!darkTheme) {
        lightColorScheme(
            primary = p.tone(if (isLightMix) 48.0 else 40.0),
            onPrimary = p.tone(100.0),
            primaryContainer = p.tone(if (isLightMix) 94.0 else 90.0),
            onPrimaryContainer = p.tone(if (isLightMix) 28.0 else 30.0),
            inversePrimary = p.tone(80.0),
            secondary = s.tone(if (isLightMix) 48.0 else 40.0),
            onSecondary = s.tone(100.0),
            secondaryContainer = s.tone(if (isLightMix) 94.0 else 90.0),
            onSecondaryContainer = s.tone(if (isLightMix) 28.0 else 30.0),
            tertiary = t.tone(if (isLightMix) 48.0 else 40.0),
            onTertiary = t.tone(100.0),
            tertiaryContainer = t.tone(if (isLightMix) 94.0 else 90.0),
            onTertiaryContainer = t.tone(if (isLightMix) 28.0 else 30.0),
            background = nv.tone(if (isLightMix) 99.0 else 98.0),
            onBackground = nv.tone(10.0),
            surface = nv.tone(if (isLightMix) 99.0 else 98.0),
            onSurface = nv.tone(10.0),
            surfaceVariant = nv.tone(if (isLightMix) 93.5 else 90.0),
            onSurfaceVariant = nv.tone(30.0),
            surfaceTint = p.tone(if (isLightMix) 48.0 else 40.0),
            inverseSurface = nv.tone(20.0),
            inverseOnSurface = nv.tone(95.0),
            error = e.tone(40.0),
            onError = e.tone(100.0),
            errorContainer = e.tone(90.0),
            onErrorContainer = e.tone(30.0),
            outline = nv.tone(50.0),
            outlineVariant = nv.tone(80.0),
            scrim = nv.tone(0.0),
            surfaceBright = nv.tone(if (isLightMix) 99.5 else 98.0),
            surfaceDim = nv.tone(if (isLightMix) 90.0 else 87.0),
            surfaceContainerLowest = nv.tone(100.0),
            surfaceContainerLow = nv.tone(if (isLightMix) 97.5 else 96.0),
            surfaceContainer = nv.tone(if (isLightMix) 96.0 else 94.0),
            surfaceContainerHigh = nv.tone(if (isLightMix) 94.5 else 92.0),
            surfaceContainerHighest = nv.tone(if (isLightMix) 93.0 else 90.0),
            primaryFixed = p.tone(if (isLightMix) 94.0 else 90.0),
            primaryFixedDim = p.tone(80.0),
            onPrimaryFixed = p.tone(10.0),
            onPrimaryFixedVariant = p.tone(30.0),
            secondaryFixed = s.tone(if (isLightMix) 94.0 else 90.0),
            secondaryFixedDim = s.tone(80.0),
            onSecondaryFixed = s.tone(10.0),
            onSecondaryFixedVariant = s.tone(30.0),
            tertiaryFixed = t.tone(if (isLightMix) 94.0 else 90.0),
            tertiaryFixedDim = t.tone(80.0),
            onTertiaryFixed = t.tone(10.0),
            onTertiaryFixedVariant = t.tone(30.0),
        )
    } else {
        darkColorScheme(
            primary = p.tone(if (isLightMix) 86.0 else 80.0),
            onPrimary = p.tone(20.0),
            primaryContainer = p.tone(if (isLightMix) 42.0 else 30.0),
            onPrimaryContainer = p.tone(if (isLightMix) 95.0 else 90.0),
            inversePrimary = p.tone(40.0),
            secondary = s.tone(if (isLightMix) 86.0 else 80.0),
            onSecondary = s.tone(20.0),
            secondaryContainer = s.tone(if (isLightMix) 42.0 else 30.0),
            onSecondaryContainer = s.tone(if (isLightMix) 95.0 else 90.0),
            tertiary = t.tone(if (isLightMix) 86.0 else 80.0),
            onTertiary = t.tone(20.0),
            tertiaryContainer = t.tone(if (isLightMix) 42.0 else 30.0),
            onTertiaryContainer = t.tone(if (isLightMix) 95.0 else 90.0),
            background = nv.tone(if (isLightMix) 14.0 else 6.0),
            onBackground = nv.tone(if (isLightMix) 94.0 else 90.0),
            surface = nv.tone(if (isLightMix) 14.0 else 6.0),
            onSurface = nv.tone(if (isLightMix) 94.0 else 90.0),
            surfaceVariant = nv.tone(if (isLightMix) 36.0 else 30.0),
            onSurfaceVariant = nv.tone(if (isLightMix) 88.0 else 80.0),
            surfaceTint = p.tone(if (isLightMix) 86.0 else 80.0),
            inverseSurface = nv.tone(90.0),
            inverseOnSurface = nv.tone(20.0),
            error = e.tone(80.0),
            onError = e.tone(20.0),
            errorContainer = e.tone(30.0),
            onErrorContainer = e.tone(90.0),
            outline = nv.tone(60.0),
            outlineVariant = nv.tone(30.0),
            scrim = nv.tone(0.0),
            surfaceBright = nv.tone(if (isLightMix) 30.0 else 24.0),
            surfaceDim = nv.tone(if (isLightMix) 12.0 else 6.0),
            surfaceContainerLowest = nv.tone(if (isLightMix) 10.0 else 4.0),
            surfaceContainerLow = nv.tone(if (isLightMix) 17.0 else 10.0),
            surfaceContainer = nv.tone(if (isLightMix) 20.0 else 12.0),
            surfaceContainerHigh = nv.tone(if (isLightMix) 24.0 else 17.0),
            surfaceContainerHighest = nv.tone(if (isLightMix) 28.0 else 22.0),
            primaryFixed = p.tone(90.0),
            primaryFixedDim = p.tone(80.0),
            onPrimaryFixed = p.tone(10.0),
            onPrimaryFixedVariant = p.tone(30.0),
            secondaryFixed = s.tone(90.0),
            secondaryFixedDim = s.tone(80.0),
            onSecondaryFixed = s.tone(10.0),
            onSecondaryFixedVariant = s.tone(30.0),
            tertiaryFixed = t.tone(90.0),
            tertiaryFixedDim = t.tone(80.0),
            onTertiaryFixed = t.tone(10.0),
            onTertiaryFixedVariant = t.tone(30.0),
        )
    }
}

private class TonalRamp(private val hue: Double, private val chroma: Double) {
    private val cache = HashMap<Int, Color>(24)

    fun tone(lStar: Double): Color {
        val key = (lStar * 100.0).roundToInt()
        cache[key]?.let { return it }
        val color = solveOklabAtTone(hue = hue, targetChroma = chroma, tone = lStar)
        cache[key] = color
        return color
    }
}

private fun srgbToLinear(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun linearToSrgb(c: Double): Double {
    val clamped = c.coerceIn(0.0, 1.0)
    return if (clamped <= 0.0031308) clamped * 12.92 else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
}

private fun yFromLstar(lStar: Double): Double {
    if (lStar <= 8.0) return lStar / 903.2962962962963
    val ft = (lStar + 16.0) / 116.0
    return ft * ft * ft
}

private fun colorToOklabHueChroma(color: Color): Pair<Double, Double> {
    val r = srgbToLinear(color.red.toDouble())
    val g = srgbToLinear(color.green.toDouble())
    val b = srgbToLinear(color.blue.toDouble())
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bVal = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return atan2(bVal, a) to hypot(a, bVal)
}

private data class LinearRgb(val r: Double, val g: Double, val b: Double) {
    val luminance: Double get() = 0.2126 * r + 0.7152 * g + 0.0722 * b
    val inGamut: Boolean get() = r >= -1e-5 && r <= 1.00001 && g >= -1e-5 && g <= 1.00001 && b >= -1e-5 && b <= 1.00001
}

private fun oklabToLinearRgb(lOk: Double, a: Double, bVal: Double): LinearRgb {
    val l = lOk + 0.3963377774 * a + 0.2158037573 * bVal
    val m = lOk - 0.1055613458 * a - 0.0638541728 * bVal
    val s = lOk - 0.0894841775 * a - 1.2914855480 * bVal
    val l3 = l * l * l
    val m3 = m * m * m
    val s3 = s * s * s
    return LinearRgb(
        r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3,
        g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3,
        b = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3,
    )
}

private fun solveLForTargetY(cosH: Double, sinH: Double, chroma: Double, targetY: Double): LinearRgb {
    val a = chroma * cosH
    val bVal = chroma * sinH
    var lo = 0.0
    var hi = 1.0
    var best = oklabToLinearRgb(0.5, a, bVal)
    repeat(22) {
        val mid = (lo + hi) * 0.5
        best = oklabToLinearRgb(mid, a, bVal)
        if (best.luminance < targetY) lo = mid else hi = mid
    }
    return best
}

private fun solveOklabAtTone(hue: Double, targetChroma: Double, tone: Double): Color {
    if (tone <= 0.0) return Color.Black
    if (tone >= 100.0) return Color.White
    val targetY = yFromLstar(tone).coerceIn(0.0, 1.0)
    if (targetChroma <= 1e-6) {
        val g = linearToSrgb(targetY).toFloat().coerceIn(0f, 1f)
        return Color(g, g, g, 1f)
    }
    val cosH = cos(hue)
    val sinH = sin(hue)
    val full = solveLForTargetY(cosH, sinH, targetChroma, targetY)
    val resolved = if (full.inGamut) {
        full
    } else {
        var lo = 0.0
        var hi = targetChroma
        var candidate = solveLForTargetY(cosH, sinH, 0.0, targetY)
        repeat(18) {
            val mid = (lo + hi) * 0.5
            val attempt = solveLForTargetY(cosH, sinH, mid, targetY)
            if (attempt.inGamut) {
                candidate = attempt
                lo = mid
            } else {
                hi = mid
            }
        }
        candidate
    }
    return Color(
        red = linearToSrgb(resolved.r).toFloat().coerceIn(0f, 1f),
        green = linearToSrgb(resolved.g).toFloat().coerceIn(0f, 1f),
        blue = linearToSrgb(resolved.b).toFloat().coerceIn(0f, 1f),
        alpha = 1f,
    )
}

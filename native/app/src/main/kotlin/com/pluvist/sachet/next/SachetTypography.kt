@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalTextApi::class)

package com.pluvist.sachet.next

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle as PlatformFontStyle
import androidx.annotation.FontRes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class FlexAxisProfile(
    val width: Float = 100f,
    val roundness: Float = 0f,
    val opticalSize: Float = 18f,
    val weightBias: Int = 0,
    val forcedWeight: Int? = null,
    val slant: Float = 0f,
    val useSystemRobotoFlex: Boolean = false,
)

private data class TypefaceCacheKey(
    val gsfWeight: Int,
    val misansWeight: Int,
    val width: Int,
    val roundness: Int,
    val opticalSize: Int,
    val slant: Int,
    val platformSlant: Int,
    val useSystemRobotoFlex: Boolean,
)

private object BilingualFlexTypefaceLoader : AndroidFont.TypefaceLoader {
    private val rawFontCache = ConcurrentHashMap<Int, PlatformFont>()
    private val typefaceCache = ConcurrentHashMap<TypefaceCacheKey, Typeface>()
    private val systemRobotoFlexFile = java.io.File("/system/fonts/RobotoFlex-Regular.ttf")

    private fun baseFont(context: Context, @FontRes resId: Int): PlatformFont =
        rawFontCache.getOrPut(resId) {
            PlatformFont.Builder(context.resources, resId).build()
        }

    private fun systemRobotoFlexBase(): PlatformFont? {
        if (!systemRobotoFlexFile.isFile || !systemRobotoFlexFile.canRead()) return null
        return rawFontCache[-1] ?: runCatching {
            PlatformFont.Builder(systemRobotoFlexFile).build().also { built ->
                rawFontCache.putIfAbsent(-1, built)
            }
        }.getOrNull()
    }

    fun getOrBuildTypeface(
        context: Context,
        weight: Int,
        axes: FlexAxisProfile,
        fontStyle: FontStyle = FontStyle.Normal,
    ): Typeface {
        val targetWeight = axes.forcedWeight ?: (weight + axes.weightBias)
        val gsfWeight = targetWeight.coerceIn(100, if (axes.useSystemRobotoFlex) 1000 else 950)
        val misansWeight = targetWeight.coerceIn(150, 700)
        val effectiveSlant = when {
            axes.slant != 0f -> axes.slant.coerceIn(-10f, 0f)
            fontStyle == FontStyle.Italic -> -8f
            else -> 0f
        }
        val platformSlant = if (fontStyle == FontStyle.Italic) {
            PlatformFontStyle.FONT_SLANT_ITALIC
        } else {
            PlatformFontStyle.FONT_SLANT_UPRIGHT
        }

        val key = TypefaceCacheKey(
            gsfWeight = gsfWeight,
            misansWeight = misansWeight,
            width = (axes.width * 10f).toInt(),
            roundness = (axes.roundness * 10f).toInt(),
            opticalSize = (axes.opticalSize * 10f).toInt(),
            slant = (effectiveSlant * 10f).toInt(),
            platformSlant = platformSlant,
            useSystemRobotoFlex = axes.useSystemRobotoFlex,
        )

        return typefaceCache.getOrPut(key) {
            runCatching {
                val robotoBase = if (axes.useSystemRobotoFlex) systemRobotoFlexBase() else null
                val primaryFont = if (robotoBase != null) {
                    val robotoAxisString = String.format(
                        Locale.US,
                        "'wght' %d, 'wdth' %.1f, 'opsz' %.1f, 'YTFI' 788.0, 'YTAS' 820.0, 'slnt' %.1f",
                        gsfWeight,
                        axes.width.coerceIn(25f, 151f),
                        axes.opticalSize.coerceIn(8f, 144f),
                        effectiveSlant,
                    )
                    PlatformFont.Builder(robotoBase)
                        .setWeight(gsfWeight)
                        .setSlant(platformSlant)
                        .setFontVariationSettings(robotoAxisString)
                        .build()
                } else {
                    val gsfAxisString = String.format(
                        Locale.US,
                        "'wght' %d, 'wdth' %.1f, 'ROND' %.1f, 'opsz' %.1f, 'slnt' %.1f",
                        gsfWeight,
                        axes.width.coerceIn(25f, 151f),
                        axes.roundness.coerceIn(0f, 100f),
                        axes.opticalSize.coerceIn(1f, 144f),
                        effectiveSlant,
                    )
                    PlatformFont.Builder(baseFont(context, R.font.google_sans_flex_vf))
                        .setWeight(gsfWeight)
                        .setSlant(platformSlant)
                        .setFontVariationSettings(gsfAxisString)
                        .build()
                }

                val misansAxisString = String.format(Locale.US, "'wght' %d", misansWeight)
                val miSans = PlatformFont.Builder(baseFont(context, R.font.misans_vf))
                    .setWeight(misansWeight)
                    .setSlant(platformSlant)
                    .setFontVariationSettings(misansAxisString)
                    .build()

                val primaryFamily = PlatformFontFamily.Builder(primaryFont).build()
                val fallbackFamily = PlatformFontFamily.Builder(miSans).build()

                Typeface.CustomFallbackBuilder(primaryFamily)
                    .addCustomFallback(fallbackFamily)
                    .setStyle(PlatformFontStyle(gsfWeight, platformSlant))
                    .build()
            }.getOrElse {
                Typeface.DEFAULT
            }
        }
    }

    override fun loadBlocking(context: Context, font: AndroidFont): Typeface {
        val axes = (font as? BilingualFlexFont)?.axes ?: FlexAxisProfile()
        return getOrBuildTypeface(context, font.weight.weight, axes, font.style)
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface =
        loadBlocking(context, font)
}

private class BilingualFlexFont(
    override val weight: FontWeight,
    override val style: FontStyle,
    val axes: FlexAxisProfile,
    variationSettings: FontVariation.Settings,
) : AndroidFont(FontLoadingStrategy.Blocking, BilingualFlexTypefaceLoader, variationSettings) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BilingualFlexFont) return false
        return weight == other.weight && style == other.style && axes == other.axes
    }

    override fun hashCode(): Int {
        var result = weight.hashCode()
        result = 31 * result + style.hashCode()
        result = 31 * result + axes.hashCode()
        return result
    }
}

private val StandardFontWeights = listOf(
    FontWeight.W300,
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
    FontWeight.ExtraBold,
    FontWeight.Black,
)

@OptIn(ExperimentalTextApi::class)
private fun flexBilingualFamily(axes: FlexAxisProfile): FontFamily = FontFamily(
    StandardFontWeights.flatMap { weight ->
        val gsfWeight = (axes.forcedWeight ?: (weight.weight + axes.weightBias)).coerceIn(100, 950).toFloat()
        val normalSettings = FontVariation.Settings(
            FontVariation.weight(gsfWeight.toInt()),
            FontVariation.width(axes.width.coerceIn(25f, 151f)),
            FontVariation.Setting("ROND", axes.roundness.coerceIn(0f, 100f)),
            FontVariation.opticalSizing(axes.opticalSize.sp),
        )
        val italicSettings = FontVariation.Settings(
            FontVariation.weight(gsfWeight.toInt()),
            FontVariation.width(axes.width.coerceIn(25f, 151f)),
            FontVariation.Setting("ROND", axes.roundness.coerceIn(0f, 100f)),
            FontVariation.opticalSizing(axes.opticalSize.sp),
            FontVariation.slant(if (axes.slant != 0f) axes.slant else -8f),
        )
        listOf<Font>(
            BilingualFlexFont(weight, FontStyle.Normal, axes, normalSettings),
            BilingualFlexFont(weight, FontStyle.Italic, axes, italicSettings),
        )
    },
)

/** Role-specific Google Sans Flex + MiSans CJK fallback FontFamilies mapped per design purpose. */
internal object XachetFontFamilies {
    /**
     * Ported from nsh07/Tomato AppFonts.topBarTitle:
     * Google Sans Flex Black Extended ('wght' 900, 'wdth' 112.5, 'ROND' 35) with bold MiSans CJK fallback.
     */
    val TopBarTitle: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 112.5f, roundness = 35f, opticalSize = 32f, forcedWeight = 900),
    )

    /** Extra-wide chunky face for hero "Xachet" brand wordmarks (Welcome, About card). */
    val BrandWide: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 138f, roundness = 48f, opticalSize = 40f, forcedWeight = 900),
    )

    /** Slightly narrower wide face tailored for compact navigation rail headers. */
    val BrandCompact: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 128f, roundness = 44f, opticalSize = 20f, forcedWeight = 900),
    )

    val Display: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 108f, roundness = 45f, opticalSize = 44f),
    )

    val DisplayEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 122f, roundness = 78f, opticalSize = 48f, weightBias = 80),
    )

    val Headline: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 104f, roundness = 34f, opticalSize = 26f),
    )

    val HeadlineEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 114f, roundness = 62f, opticalSize = 28f, weightBias = 70),
    )

    val Title: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 100f, roundness = 22f, opticalSize = 20f),
    )

    val TitleEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 106f, roundness = 55f, opticalSize = 22f, weightBias = 65),
    )

    /** High-legibility neutral sans with crisp non-rounded terminals for long body copy. */
    val Body: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 100f, roundness = 0f, opticalSize = 14f),
    )

    val BodyEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 102f, roundness = 26f, opticalSize = 14f, weightBias = 60),
    )

    /** Playful rounded pill & control label face matching M3 Expressive containers. */
    val LabelPill: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 102f, roundness = 80f, opticalSize = 14f),
    )

    val LabelPillEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 106f, roundness = 88f, opticalSize = 14f, weightBias = 70),
    )

    /** Space-efficient condensed face for dense schedule metadata, room codes & dates. */
    val CondensedMeta: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 90f, roundness = 46f, opticalSize = 12f),
    )

    val CondensedMetaEmphasized: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 92f, roundness = 54f, opticalSize = 12f, weightBias = 70),
    )

    /** Compact course-cell body combining legible room digits with MiSans CJK course titles. */
    val ScheduleCell: FontFamily = flexBilingualFamily(
        FlexAxisProfile(width = 95f, roundness = 24f, opticalSize = 13f),
    )

    /** Bold, tall condensed Roboto Flex face dedicated to central Focus timer numerals. */
    val RobotoFlexTall: FontFamily = flexBilingualFamily(
        FlexAxisProfile(
            width = 54f,
            roundness = 0f,
            opticalSize = 72f,
            forcedWeight = 760,
            useSystemRobotoFlex = true,
        ),
    )
}

/** Legacy alias retained for existing references; uses the crisp Body Google Sans Flex + MiSans chain. */
internal val MiSansFamily: FontFamily = XachetFontFamilies.Body

private val BaseM3Scale = Typography()

private const val TOMATO_FEATURES = "ss02, dlig"

/**
 * Full 30-style Material 3 Expressive typography scale combining variable-axis Google Sans Flex
 * (Latin, digits, punctuation) with automatic MiSans CJK fallback via Android CustomFallbackBuilder.
 */
internal val XachetTypography: Typography = BaseM3Scale.copy(
    displayLarge = BaseM3Scale.displayLarge.copy(
        fontFamily = XachetFontFamilies.Display,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    displayMedium = BaseM3Scale.displayMedium.copy(
        fontFamily = XachetFontFamilies.Display,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    displaySmall = BaseM3Scale.displaySmall.copy(
        fontFamily = XachetFontFamilies.Display,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineLarge = BaseM3Scale.headlineLarge.copy(
        fontFamily = XachetFontFamilies.Headline,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineMedium = BaseM3Scale.headlineMedium.copy(
        fontFamily = XachetFontFamilies.Headline,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineSmall = BaseM3Scale.headlineSmall.copy(
        fontFamily = XachetFontFamilies.Headline,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleLarge = BaseM3Scale.titleLarge.copy(
        fontFamily = XachetFontFamilies.Title,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleMedium = BaseM3Scale.titleMedium.copy(
        fontFamily = XachetFontFamilies.Title,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleSmall = BaseM3Scale.titleSmall.copy(
        fontFamily = XachetFontFamilies.Title,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodyLarge = BaseM3Scale.bodyLarge.copy(
        fontFamily = XachetFontFamilies.Body,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodyMedium = BaseM3Scale.bodyMedium.copy(
        fontFamily = XachetFontFamilies.Body,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodySmall = BaseM3Scale.bodySmall.copy(
        fontFamily = XachetFontFamilies.Body,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelLarge = BaseM3Scale.labelLarge.copy(
        fontFamily = XachetFontFamilies.LabelPill,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelMedium = BaseM3Scale.labelMedium.copy(
        fontFamily = XachetFontFamilies.LabelPill,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelSmall = BaseM3Scale.labelSmall.copy(
        fontFamily = XachetFontFamilies.CondensedMeta,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    displayLargeEmphasized = BaseM3Scale.displayLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.DisplayEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    displayMediumEmphasized = BaseM3Scale.displayMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.DisplayEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    displaySmallEmphasized = BaseM3Scale.displaySmallEmphasized.copy(
        fontFamily = XachetFontFamilies.DisplayEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineLargeEmphasized = BaseM3Scale.headlineLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.HeadlineEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineMediumEmphasized = BaseM3Scale.headlineMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.HeadlineEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    headlineSmallEmphasized = BaseM3Scale.headlineSmallEmphasized.copy(
        fontFamily = XachetFontFamilies.HeadlineEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleLargeEmphasized = BaseM3Scale.titleLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.TitleEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleMediumEmphasized = BaseM3Scale.titleMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.TitleEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    titleSmallEmphasized = BaseM3Scale.titleSmallEmphasized.copy(
        fontFamily = XachetFontFamilies.TitleEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodyLargeEmphasized = BaseM3Scale.bodyLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.BodyEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodyMediumEmphasized = BaseM3Scale.bodyMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.BodyEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    bodySmallEmphasized = BaseM3Scale.bodySmallEmphasized.copy(
        fontFamily = XachetFontFamilies.BodyEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelLargeEmphasized = BaseM3Scale.labelLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.LabelPillEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelMediumEmphasized = BaseM3Scale.labelMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.LabelPillEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
    labelSmallEmphasized = BaseM3Scale.labelSmallEmphasized.copy(
        fontFamily = XachetFontFamilies.CondensedMetaEmphasized,
        fontFeatureSettings = TOMATO_FEATURES,
    ),
)

/**
 * Component-specific typographic profiles extending [XachetTypography] for Tomato-ported top-bar titles,
 * wide brand marks, countdown timers, rounded status capsules, and condensed timetable grids.
 */
internal object XachetTypeTokens {
    /**
     * Ported nsh07/Tomato FlexibleTopAppBar title font ([TextUnit.Unspecified] fontSize/lineHeight so
     * [LargeFlexibleTopAppBar] & [MediumFlexibleTopAppBar] animate expanded->collapsed scale cleanly).
     */
    val TopBarFlexible: TextStyle = TextStyle(
        fontFamily = XachetFontFamilies.TopBarTitle,
        fontWeight = FontWeight.Black,
        fontFeatureSettings = TOMATO_FEATURES,
    )

    /** Ported nsh07/Tomato compact/single-row TopAppBar title (wght=900, wdth=112.5, ROND=35, 30sp). */
    val TopBarCompact: TextStyle = TextStyle(
        fontFamily = XachetFontFamilies.TopBarTitle,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Black,
        fontFeatureSettings = TOMATO_FEATURES,
    )

    /** Home TopBar "Xachet" mark adopting Tomato's ported TopBarTitle font with expanded->collapsed scaling. */
    val BrandTopBar: TextStyle = TopBarFlexible

    /**
     * Enlarged home TopBar title style adopting Tomato's TopBarTitle font (Google Sans Flex wght=900, wdth=112.5, ROND=35).
     */
    fun homeTopBarTitle(collapsedFraction: Float, base: TextStyle): TextStyle {
        val t = collapsedFraction.coerceIn(0f, 1f)
        return base.copy(
            fontFamily = XachetFontFamilies.TopBarTitle,
            fontWeight = FontWeight.Black,
            fontSize = lerp(44.sp, 26.sp, t),
            lineHeight = lerp(48.sp, 30.sp, t),
            fontFeatureSettings = TOMATO_FEATURES,
        )
    }

    /** Hero welcome "Xachet" mark: unmistakably wide ("扁") & heavy ("粗") with soft terminals. */
    val BrandHero: TextStyle = XachetTypography.displayLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.BrandWide,
        fontSize = 46.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.5).sp,
    )

    /** About-page "Xachet" mark. */
    val BrandCard: TextStyle = XachetTypography.headlineSmallEmphasized.copy(
        fontFamily = XachetFontFamilies.BrandWide,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.3).sp,
    )

    /** Compact landscape navigation rail "Xachet" mark. */
    val BrandRail: TextStyle = XachetTypography.titleMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.BrandCompact,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-0.2).sp,
    )

    /** Wide rounded tabular countdown numerals for live course status hero cards. */
    val HeroCountdown: TextStyle = XachetTypography.displaySmallEmphasized.copy(
        fontFamily = XachetFontFamilies.DisplayEmphasized,
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.4).sp,
    )

    /** Wide rounded tabular numerals sized for Home Bento live countdowns. */
    val BentoCountdown: TextStyle = XachetTypography.titleMediumEmphasized.copy(
        fontFamily = XachetFontFamilies.DisplayEmphasized,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

    /** High-roundness pill style for status capsules, AssistChips, and exam badges. */
    val PillStatus: TextStyle = XachetTypography.labelLargeEmphasized.copy(
        fontFamily = XachetFontFamilies.LabelPillEmphasized,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    )

    /** Condensed tabular metadata for date ranges, period clocks, and grid headers. */
    val CondensedMeta: TextStyle = XachetTypography.bodySmall.copy(
        fontFamily = XachetFontFamilies.CondensedMeta,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    )

    /** Schedule weekly course block body combining compact Google Sans Flex digits & MiSans CJK. */
    val ScheduleGridText: TextStyle = XachetTypography.bodyMedium.copy(
        fontFamily = XachetFontFamilies.ScheduleCell,
    )

    /** Bold & tall tabular Roboto Flex numerals centered inside the Tomato Focus dial. */
    val TomatoTimerDigits: TextStyle = TextStyle(
        fontFamily = XachetFontFamilies.RobotoFlexTall,
        fontWeight = FontWeight.Bold,
        fontSize = 84.sp,
        lineHeight = 90.sp,
        letterSpacing = 1.5.sp,
        fontFeatureSettings = "tnum",
    )
}

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * Builds an Android [Typeface] using Google Sans Flex variable axes with MiSans CJK fallback
 * for custom Canvas/Paint surfaces such as the timetable time axis and weekday date strip.
 */
internal fun buildScheduleCanvasTypeface(
    context: Context,
    weight: Int,
    width: Float = 88f,
    roundness: Float = 54f,
    opticalSize: Float = 11f,
): Typeface = BilingualFlexTypefaceLoader.getOrBuildTypeface(
    context = context,
    weight = weight,
    axes = FlexAxisProfile(
        width = width,
        roundness = roundness,
        opticalSize = opticalSize,
    ),
)

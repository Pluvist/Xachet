package com.pluvist.sachet.next

import io.github.neoruaa.glasmio.core.GlassAppearance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.glass.GlassSurface
import io.github.neoruaa.glasmio.glass.GlassSurfaceStyle
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop

/** One material for the persistent footer and both source-to-course panels.
 * Keep the footer's multiplier >=3 so its reduced-resolution optical path stays enabled.
 * The renderer's regular base blur is 1dp; these surfaces therefore use 4.5dp. */
internal fun courseGlassAppearance(base:GlassAppearance)=base.copy(
    blurRadiusMultiplier=4.5f,
    chromaticStrength=1f,
    refractionAmountMultiplier=2f,
    materialFaceOpacity=0f,
)

/** Remove the light footer's white wash while retaining its blur and refraction. */
internal fun footerGlassAppearance(base:GlassAppearance,isDark:Boolean)=courseGlassAppearance(base).copy(
    frostOverlayColor=if(isDark)base.frostOverlayColor else Color.White.copy(alpha=0f),
    frostOverlayColorProvider=null,
)

/** The original footer surface, reused by the resizing course window unchanged. */
@Composable internal fun CourseGlassSurface(
    modifier:Modifier=Modifier,
    backdrop:Backdrop=LocalGlassBackdrop.current,
    shape:Shape=com.kyant.shapes.Capsule(),
    blurRadius:Dp?=null,
    refractionMultiplier:Float=2f,
    coatingColorProvider:(()->Color)?=null,
    exportedBackdrop:LayerBackdrop?=null,
    onClick:(()->Unit)?=null,
    content:@Composable BoxScope.()->Unit,
) {
    val base=courseGlassAppearance(LocalGlassAppearance.current).copy(
        refractionAmountMultiplier=refractionMultiplier,
    )
    // The renderer reads the provider while drawing, keeping the fade out of
    // composition and in the exported material sampled by nested controls.
    val appearance=if(coatingColorProvider!=null)base.copy(frostOverlayColorProvider=coatingColorProvider)else base
    CompositionLocalProvider(LocalGlassAppearance provides appearance) {
        GlassSurface(modifier=modifier,backdrop=backdrop,shape=shape,
            frostRadius=blurRadius?.let{(it.value/appearance.blurRadiusMultiplier).dp},
            exportedBackdrop=exportedBackdrop,style=GlassSurfaceStyle.Navigation,onClick=onClick,content=content)
    }
}

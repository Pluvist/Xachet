package io.github.neoruaa.glasmio.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import io.github.neoruaa.glasmio.core.GlassAppearance
import org.junit.Assert.*
import org.junit.Test

class MaterialFaceOpacityTest {
    private fun uniforms(recipe:NavigationMaterialRecipe,alpha:Float=1f):Map<String,FloatArray> =
        mutableMapOf<String,FloatArray>().also{values->updateNavigationMaterialUniforms(recipe,alpha){name,value->values[name]=value}}

    @Test fun defaultAppearanceKeepsReferenceCoating() {
        val original=NavigationMaterialRecipe.resolve(false,null)
        val scoped=original.withFaceOpacity(GlassAppearance().materialFaceOpacity)
        assertSame(original,scoped)
        assertEquals(1f,uniforms(scoped).getValue("materialOpacity").single(),0f)
    }

    @Test fun transparentRegularFaceSurvivesThemeBlendingAndCachedFadeUpdates() {
        val scoped=NavigationMaterialRecipe.resolve(.5f,null).withFaceOpacity(0f)
        val full=uniforms(scoped)
        val faded=mutableMapOf<String,FloatArray>()
        updateNavigationMaterialOpacityUniforms(scoped,.6f){name,value->faded[name]=value}
        for(name in listOf("materialOpacity","appearance_materialOpacity")) {
            assertEquals(0f,full.getValue(name).single(),0f)
            assertEquals(0f,faded.getValue(name).single(),0f)
        }
        assertFalse(faded.containsKey("nativeEdgeAlpha"))
        assertFalse(faded.containsKey("nativeTopHighlight"))
    }

    @Test fun prominentAndExplicitActionTintsRetainTheirContrast() {
        val blue=Color(0xff0066cc)
        val prominent=NavigationMaterialRecipe.resolve(false,null,prominentTint=blue).withFaceOpacity(0f)
        val explicit=NavigationMaterialRecipe.resolve(true,null,materialTint=blue).withFaceOpacity(0f)
        for(recipe in listOf(prominent,explicit))assertEquals(.7f,uniforms(recipe,.7f).getValue("materialOpacity").single(),0f)
        assertNotNull(prominent.tintMatrix)
        assertNotNull(explicit.overlay)
    }

    @Test fun partialFaceOpacityIsSeparateFromAnimationAndOverlayColor() {
        val recipe=NavigationMaterialRecipe.resolve(false,null).withFaceOpacity(.2f)
        assertEquals(.1f,uniforms(recipe,.5f).getValue("materialOpacity").single(),.00001f)
        val appearance=GlassAppearance(materialFaceOpacity=0f,frostOverlayColor=Color.White.copy(alpha=.1f))
        assertEquals(.1f,appearance.frostOverlayColor.alpha,.005f)
        assertTrue(appearance.usesRefraction)
        assertTrue(appearance.usesBackdrop)
    }

    @Test fun facePolicyIsPartOfMaterialCacheIdentity() {
        val original=NavigationMaterialRecipe.resolve(false,null)
        val transparent=original.withFaceOpacity(0f)
        assertFalse(original.matches(transparent))
        assertSame(transparent,transparent.withFaceOpacity(0f))
        assertEquals(1f,original.withFaceOpacity(Float.NaN).faceOpacity,0f)
        assertEquals(0f,original.withFaceOpacity(-1f).faceOpacity,0f)
    }

    @Test fun regularFrostIsReducedWithoutChangingLensGeometryOrClearMaterial() {
        for(size in listOf(48f,150f,420f)) {
            for(material in listOf(null,NavigationGlassMaterial.RegularSmall,NavigationGlassMaterial.RegularMedium)) {
                val profile=navigationGlassGeometryProfile(size,material)
                assertEquals(1f,profile.blurRadius,0f)
                assertEquals(minOf(size*.25f,20f),profile.innerHeight,0f)
                assertEquals(minOf(size*.5f,60f),profile.innerAmount,0f)
            }
            assertEquals(.75f,navigationGlassGeometryProfile(size,NavigationGlassMaterial.Clear).blurRadius,0f)
        }
    }

    @Test fun dynamicCoatingIsObservedAtDrawTimeWithoutReplacingAppearance() {
        val progress=mutableStateOf(0f)
        val resting=Color.Black.copy(alpha=.1f)
        val expanded=Color(0xff2c2c2e).copy(alpha=.75f)
        val appearance=GlassAppearance(materialFaceOpacity=0f,frostOverlayColor=resting,
            frostOverlayColorProvider={lerp(resting,expanded,progress.value)})
        val observer=SnapshotStateObserver{it()}
        var invalidations=0
        val target=Any()
        observer.start()
        try {
            observer.observeReads(target,{_:Any->invalidations++}){appearance.resolvedFrostOverlayColor()}
            Snapshot.withMutableSnapshot{progress.value=1f}
            Snapshot.sendApplyNotifications()
            assertEquals(1,invalidations)
            val result=appearance.resolvedFrostOverlayColor()
            assertEquals(expanded.red,result.red,.008f)
            assertEquals(expanded.green,result.green,.008f)
            assertEquals(expanded.blue,result.blue,.008f)
            assertEquals(expanded.alpha,result.alpha,.005f)
            assertEquals(.1f,appearance.frostOverlayColor.alpha,.005f)
            assertEquals(0f,uniforms(NavigationMaterialRecipe.resolve(false,null)
                .withFaceOpacity(appearance.materialFaceOpacity)).getValue("materialOpacity").single(),0f)
        } finally {observer.stop();observer.clear()}
    }

    @Test fun absentProviderKeepsStaticCoatingAndProviderCanFadeItDownAgain() {
        val white=Color.White.copy(alpha=.1f)
        assertEquals(white,GlassAppearance(frostOverlayColor=white).resolvedFrostOverlayColor())
        val progress=mutableStateOf(1f)
        val appearance=GlassAppearance(frostOverlayColor=white,
            frostOverlayColorProvider={Color.White.copy(alpha=.1f+.68f*progress.value)})
        assertEquals(.78f,appearance.resolvedFrostOverlayColor().alpha,.005f)
        progress.value=.5f
        assertEquals(.44f,appearance.resolvedFrostOverlayColor().alpha,.005f)
        progress.value=0f
        assertEquals(.1f,appearance.resolvedFrostOverlayColor().alpha,.005f)
    }
}

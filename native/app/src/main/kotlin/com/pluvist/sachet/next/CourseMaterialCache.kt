package com.pluvist.sachet.next

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// Geometry only. Both course windows now resize the original footer glass surface.
internal fun coursePanelTarget(width:Float,height:Float,top:Float,bottom:Float,lower:Boolean,empty:Boolean,density:Density):Rect=with(density) {
    val w=minOf(width-32.dp.toPx(),(if(lower)560 else 480).dp.toPx()).coerceAtLeast(1f)
    val h=minOf((if(empty)240 else if(lower)420 else 440).dp.toPx(),height-top-bottom-64.dp.toPx()).coerceAtLeast(100.dp.toPx())
    val x=(width-w)/2f
    val y=if(lower)height-bottom-16.dp.toPx()-h else top+(height-top-bottom-h)/2f
    Rect(x,y,x+w,y+h)
}

package com.pluvist.sachet.next

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import android.widget.RemoteViews

/** An opaque theme-colored face and neutral glass rim for both widget sizes.
 * Rendered once per theme/aspect; never starts a ticker, service or extra alarm.
 */
internal object CourseWidgetGlow {
    private val images = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    @Synchronized private fun image(dark: Boolean, wide: Boolean, widthDp: Int, heightDp: Int): Bitmap {
        val key = "$dark/$wide/$widthDp/$heightDp"
        images.get(key)?.let { return it }
        // The optical rim needs little resolution; keep each RemoteViews payload
        // comfortably below the Binder transaction budget, even on old Android.
        val width = 384
        val height = (width * heightDp / widthDp).coerceIn(96, 512)
        val sx = width.toFloat() / widthDp
        val sy = height.toFloat() / heightDp
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val outline = Path().apply { addRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
            28 * sx, 28 * sy, Path.Direction.CW) }
        canvas.clipPath(outline)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // RemoteViews cannot sample and blur the live wallpaper behind them.
        // The theme-specific drawable supplies the opaque face. Blur only the
        // neutral optical edges here so their glow does not double the fill.
        // Directional light and shadow evoke a refracted edge.
        val edge = Path().apply { addRoundRect(
            RectF(.7f * sx, .7f * sy, width - .7f * sx, height - .7f * sy),
            27.3f * sx, 27.3f * sy, Path.Direction.CW) }
        paint.style = Paint.Style.STROKE
        if (dark) {
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xadffffff.toInt(), 0x387f7f7f, 0x143c3c3c, 0x8cffffff.toInt()),
                floatArrayOf(0f, .36f, .65f, 1f), Shader.TileMode.CLAMP)
            paint.strokeWidth = 3.5f * sx
            paint.alpha = 80
            paint.maskFilter = BlurMaskFilter(2f * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(edge, paint)
            paint.maskFilter = null
            paint.alpha = 255
            paint.strokeWidth = .8f * sx
            canvas.drawPath(edge, paint)
        } else {
            // On a white widget face a white-only rim disappears. A neutral,
            // directional shadow and a thin reflected highlight reveal the edge.
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0x286C7A89, 0x346C7A89, 0x486C7A89, 0x3A6C7A89),
                floatArrayOf(0f, .36f, .72f, 1f), Shader.TileMode.CLAMP)
            paint.strokeWidth = 3.2f * sx
            paint.maskFilter = BlurMaskFilter(2.5f * sx, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(edge, paint)
            paint.maskFilter = null
            paint.strokeWidth = 1.15f * sx
            canvas.drawPath(edge, paint)

            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(0xb3ffffff.toInt(), 0x50ffffff, 0x00ffffff, 0x75ffffff),
                floatArrayOf(0f, .28f, .72f, 1f), Shader.TileMode.CLAMP)
            paint.strokeWidth = .65f * sx
            canvas.drawPath(edge, paint)
        }
        images.put(key, bitmap)
        return bitmap
    }

    fun apply(context: Context, view: RemoteViews, dark: Boolean, wide: Boolean, width: Int, height: Int) {
        view.setInt(R.id.widget_surface, "setBackgroundResource",
            if (dark) R.drawable.course_widget_dark else R.drawable.course_widget_light)
        view.setImageViewBitmap(R.id.widget_glow, image(dark, wide, maxOf(80, width), maxOf(60, height - 12)))
        val vertical = if (height - 12 < 120) 12 else 20
        if (wide) {
            val density = context.resources.displayMetrics.density
            fun dp(value: Int) = (value * density + .5f).toInt()
            view.setViewPadding(R.id.widget_content, dp(20), dp(vertical), dp(20), dp(vertical))
        }
    }
}

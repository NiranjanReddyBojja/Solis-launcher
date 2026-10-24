package com.solis.launcher.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders the current quote as white monospace text on pure black and sets it as the
 * lock screen wallpaper only (FLAG_LOCK), leaving the home wallpaper untouched.
 */
class LockscreenQuoteWriter(private val context: Context) {

    suspend fun write(quote: String): Boolean = withContext(Dispatchers.Default) {
        runCatching {
            val (width, height) = screenSize()
            val bitmap = render(quote, width, height)
            WallpaperManager.getInstance(context)
                .setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun render(quote: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val density = context.resources.displayMetrics.density
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.MONOSPACE
            textSize = 20f * density
        }
        val margin = (32 * density).toInt()
        val layout = StaticLayout.Builder
            .obtain(quote, 0, quote.length, paint, width - margin * 2)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.3f)
            .build()

        // Sit slightly below centre so the system clock on the lock screen never overlaps it.
        val top = (height * 0.55f - layout.height / 2f)
        canvas.save()
        canvas.translate(margin.toFloat(), top)
        layout.draw(canvas)
        canvas.restore()
        return bitmap
    }
}

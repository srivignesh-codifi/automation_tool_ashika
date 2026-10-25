package com.mobileautomation.tool

import android.graphics.BitmapFactory

/**
 * Looks at a saved screenshot to tell a rendered page from an empty one, for content the accessibility tree cannot
 * describe (a chart inside a WebView).
 *
 * It is a pixel statistic, not image recognition: a chart paints a grid, candles or a line in several colours, while
 * a WebView that is still loading, blank or failed is one flat colour with at most a spinner or a message. It cannot
 * say that the candles are the right ones, only that something was drawn; the screenshot is kept for a person to look.
 */
object ScreenshotInspector {

    class Content(val significantColors: Int, val busyPercent: Double) {
        /** Enough different colours and enough non-background area for a drawn chart. */
        val rendered: Boolean get() = significantColors >= MIN_COLORS && busyPercent >= MIN_BUSY_PERCENT

        override fun toString() =
            "$significantColors colours, ${"%.1f".format(busyPercent)} % of the area is not background"
    }

    private const val MIN_COLORS = 3
    private const val MIN_BUSY_PERCENT = 1.5

    /** A colour has to cover at least this share of the area to count, so anti-aliasing noise is ignored. */
    private const val SIGNIFICANT_SHARE = 0.001

    /**
     * The statistics of the band between [topShare] and [bottomShare] of the screen height (the app bar and the
     * navigation sit outside it). Null when the file cannot be decoded.
     */
    fun content(path: String, topShare: Double = 0.18, bottomShare: Double = 0.12): Content? {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bitmap = try {
            BitmapFactory.decodeFile(path, options)
        } catch (_: Throwable) {
            null
        } ?: return null
        try {
            val width = bitmap.width
            val height = bitmap.height
            val from = (height * topShare).toInt().coerceIn(0, height - 1)
            val to = (height * (1 - bottomShare)).toInt().coerceIn(from + 1, height)
            val counts = HashMap<Int, Int>()
            var total = 0
            val row = IntArray(width)
            for (y in from until to) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1)
                for (pixel in row) {
                    // Four bits per channel: one colour covers a small range of shades.
                    val r = (pixel shr 20) and 0xF
                    val g = (pixel shr 12) and 0xF
                    val b = (pixel shr 4) and 0xF
                    val key = (r shl 8) or (g shl 4) or b
                    counts[key] = (counts[key] ?: 0) + 1
                    total++
                }
            }
            if (total == 0) return null
            val dominant = counts.values.max()
            val significant = counts.values.count { it >= total * SIGNIFICANT_SHARE }
            return Content(significant, 100.0 * (total - dominant) / total)
        } finally {
            bitmap.recycle()
        }
    }
}

package com.mobileautomation.tool

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * A banner drawn over the target app when the run needs a person, for example to type a new OTP after the session
 * expired. It never takes touches (FLAG_NOT_TOUCHABLE), so the person can use the app beneath it, and it sits just
 * below the [StopOverlay] band. Like that button it is an accessibility overlay and needs no extra permission.
 */
class NoticeOverlay(private val service: AccessibilityService) {

    private val main = Handler(Looper.getMainLooper())
    private var view: TextView? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()

    /** Shows [text], or replaces the text of the banner already showing. */
    fun show(text: String) {
        main.post {
            view?.let {
                it.text = text
                return@post
            }
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val banner = TextView(service).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E6B71C1C"))
                        cornerRadius = dp(10f).toFloat()
                    }
                    elevation = dp(4f).toFloat()
                    // Read by nobody: the tool reads only the target app's windows.
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = dp(40f)
                    horizontalMargin = 0.03f
                }
                wm.addView(banner, params)
                view = banner
            } catch (_: Throwable) {
                // No banner is not a reason to stop: the step note says the same thing.
                view = null
            }
        }
    }

    /** Removes the banner. Safe to call when it is not showing. */
    fun hide() {
        main.post {
            val v = view ?: return@post
            view = null
            try {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (_: Throwable) {
            }
        }
    }
}

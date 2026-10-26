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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * A small floating "Stop" button drawn over the target app while a run is going, so a run can be stopped without
 * going back to this tool (the target app is what is on screen).
 *
 * It is an accessibility overlay window: the accessibility service may add one without any extra permission. It sits in
 * the status bar band at the top of the screen, where the engine never taps, so a gesture the engine dispatches can
 * never land on it. It can be dragged elsewhere; a tap, not a drag, stops the run.
 */
class StopOverlay(private val service: AccessibilityService) {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()

    /** Shows the button; [onStop] runs once, when it is tapped. Does nothing when it is already showing. */
    fun show(onStop: () -> Unit) {
        main.post {
            if (view != null) return@post
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val button = TextView(service).apply {
                    text = "■  Stop test"
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    gravity = Gravity.CENTER
                    setPadding(dp(14f), dp(5f), dp(14f), dp(5f))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#D32F2F"))
                        cornerRadius = dp(16f).toFloat()
                    }
                    elevation = dp(4f).toFloat()
                    contentDescription = "Stop the automation run"
                }
                val metrics = service.resources.displayMetrics
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = (metrics.widthPixels * 0.58f).toInt()
                    y = dp(4f)
                }
                var downX = 0f
                var downY = 0f
                var startX = 0
                var startY = 0
                var dragged = false
                var stopped = false
                button.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX
                            downY = event.rawY
                            startX = params.x
                            startY = params.y
                            dragged = false
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.rawX - downX
                            val dy = event.rawY - downY
                            if (dragged || dx * dx + dy * dy > dp(8f) * dp(8f)) {
                                dragged = true
                                params.x = (startX + dx).toInt()
                                params.y = (startY + dy).toInt()
                                try {
                                    wm.updateViewLayout(button, params)
                                } catch (_: Throwable) {
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!dragged && !stopped) {
                                stopped = true
                                button.text = "Stopping…"
                                button.alpha = 0.7f
                                onStop()
                            }
                            true
                        }
                        else -> false
                    }
                }
                wm.addView(button, params)
                view = button
            } catch (_: Throwable) {
                // No overlay is not a reason to stop a run: the Stop button on the tool's own screen still works.
                view = null
            }
        }
    }

    /** Removes the button. Safe to call when it is not showing, or after the service has gone. */
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

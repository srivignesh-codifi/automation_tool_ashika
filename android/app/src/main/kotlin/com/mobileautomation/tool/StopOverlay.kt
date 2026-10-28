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
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A small floating control bar drawn over the target app while a run is going, like a video player's: Pause / Play,
 * Skip (the module that is running) and Stop, as small icons in one row. The run can be controlled without going
 * back to this tool (the target app is what is on screen).
 *
 * It is an accessibility overlay window: the accessibility service may add one without any extra permission. It sits in
 * the status bar band at the top of the screen, where the engine never taps, so a gesture the engine dispatches can
 * never land on it. It can be dragged elsewhere by any icon; a tap, not a drag, presses the icon.
 */
class StopOverlay(private val service: AccessibilityService) {

    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var pauseButton: TextView? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, service.resources.displayMetrics).toInt()

    private fun icon(glyph: String, description: String) = TextView(service).apply {
        text = glyph
        setTextColor(Color.WHITE)
        setTypeface(typeface, Typeface.BOLD)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        gravity = Gravity.CENTER
        width = dp(34f)
        height = dp(30f)
        contentDescription = description
    }

    private fun paint(button: TextView, paused: Boolean) {
        button.text = if (paused) "▶" else "❚❚"
        button.setTextColor(Color.parseColor(if (paused) "#69F0AE" else "#FFFFFF"))
        button.contentDescription = if (paused) "Resume the automation run" else "Pause the automation run"
    }

    /** Shows Pause or Play after the run was paused or resumed from elsewhere (the tool's own screen). */
    fun showPaused(paused: Boolean) {
        main.post { pauseButton?.let { paint(it, paused) } }
    }

    /**
     * Shows the bar. [onStop] runs once, when Stop is tapped; [onPauseToggle] on each tap of Pause / Play and returns
     * whether the run is now paused; [onSkip] on each tap of Skip. Does nothing when the bar is already showing.
     */
    fun show(onStop: () -> Unit, onPauseToggle: () -> Boolean, onSkip: () -> Unit) {
        main.post {
            if (view != null) return@post
            try {
                val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val pause = icon("❚❚", "Pause the automation run")
                val skip = icon("⏭", "Skip the module that is running")
                val stopButton = icon("■", "Stop the automation run").apply {
                    setTextColor(Color.parseColor("#FF5252"))
                }
                val bar = LinearLayout(service).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4f), dp(1f), dp(4f), dp(1f))
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#D9212121"))
                        cornerRadius = dp(16f).toFloat()
                    }
                    elevation = dp(4f).toFloat()
                    addView(pause)
                    addView(skip)
                    addView(stopButton)
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
                    x = (metrics.widthPixels * 0.55f).toInt()
                    y = dp(4f)
                }
                var downX = 0f
                var downY = 0f
                var startX = 0
                var startY = 0
                var dragged = false
                var stopped = false
                // Any icon drags the bar; a tap presses the icon touched.
                fun touch(onTap: () -> Unit) = View.OnTouchListener { _, event ->
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
                                    wm.updateViewLayout(bar, params)
                                } catch (_: Throwable) {
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!dragged && !stopped) onTap()
                            true
                        }
                        else -> false
                    }
                }
                stopButton.setOnTouchListener(touch {
                    stopped = true
                    bar.alpha = 0.6f
                    pause.visibility = View.GONE
                    skip.visibility = View.GONE
                    onStop()
                })
                pause.setOnTouchListener(touch { paint(pause, onPauseToggle()) })
                skip.setOnTouchListener(touch {
                    // A short flash says the tap was taken; the module ends at its next step.
                    skip.alpha = 0.4f
                    main.postDelayed({ skip.alpha = 1f }, 600L)
                    paint(pause, false)
                    onSkip()
                })
                wm.addView(bar, params)
                view = bar
                pauseButton = pause
            } catch (_: Throwable) {
                // No overlay is not a reason to stop a run: the buttons on the tool's own screen still work.
                view = null
            }
        }
    }

    /** Removes the bar. Safe to call when it is not showing, or after the service has gone. */
    fun hide() {
        main.post {
            val v = view ?: return@post
            view = null
            pauseButton = null
            try {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
            } catch (_: Throwable) {
            }
        }
    }
}

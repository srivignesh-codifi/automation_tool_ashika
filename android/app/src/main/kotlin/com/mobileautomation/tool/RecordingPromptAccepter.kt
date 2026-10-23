package com.mobileautomation.tool

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Taps "Share screen" on Android's screen-recording prompt for the operator.
 *
 * Deliberately narrow, because auto-accepting a system consent is exactly what
 * a hostile accessibility service would do:
 *
 * * it is armed only by [start], which `MainActivity.requestScreenRecording`
 *   calls when the operator has just pressed Start with "Record the screen" on,
 *   and it disarms after one tap, an answer, or [WINDOW_MS];
 * * it reads only System UI's windows, and only touches a dialog that names this
 *   tool (the app label) and talks about the screen, so any other System UI
 *   window, notifications included, is left alone;
 * * it presses only the positive button of that one dialog.
 *
 * If Android ignores the automated tap the dialog simply stays up and the
 * operator can press it; the run waits for the answer either way.
 */
object RecordingPromptAccepter {

    private const val WINDOW_MS = 20_000L
    private const val POLL_MS = 250L
    private val SYSTEM_UI = setOf("com.android.systemui")
    private val BUTTON_TEXTS = listOf("Share screen", "Start now", "Start")

    @Volatile
    private var generation = 0

    fun start(context: Context) {
        val service = AutomationAccessibilityService.connected() ?: return
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val mine = ++generation
        Thread({
            val deadline = SystemClock.elapsedRealtime() + WINDOW_MS
            while (mine == generation && SystemClock.elapsedRealtime() < deadline) {
                if (tryAccept(service, label)) return@Thread
                try {
                    Thread.sleep(POLL_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "recording-prompt").apply { isDaemon = true }.start()
    }

    /** Called once the operator's answer is in, or the request is abandoned. */
    fun cancel() {
        generation++
    }

    private fun tryAccept(service: AutomationAccessibilityService, label: String): Boolean {
        val root = service.windowRootOf(SYSTEM_UI) ?: return false
        if (!isOurPrompt(root, label)) return false
        val button = AccessibilityNodeFinder.byText(root, BUTTON_TEXTS) ?: return false
        val clickable = AccessibilityNodeFinder.resolveClickable(button)
        if (clickable != null && try {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Throwable) {
                false
            }
        ) {
            return true
        }
        val bounds = Rect()
        (clickable ?: button).getBoundsInScreen(bounds)
        return !bounds.isEmpty && service.tapAt(bounds.exactCenterX(), bounds.exactCenterY())
    }

    /** The dialog names this tool and is about sharing the screen. */
    private fun isOurPrompt(root: AccessibilityNodeInfo, label: String): Boolean {
        val texts = ArrayList<String>()
        collectTexts(root, texts, 0)
        val joined = texts.joinToString(" ")
        return joined.contains(label) &&
            (joined.contains("screen", ignoreCase = true) || joined.contains("record", ignoreCase = true))
    }

    private fun collectTexts(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > 12 || out.size > 120) return
        node.text?.toString()?.let(out::add)
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Throwable) {
                null
            } ?: continue
            collectTexts(child, out, depth + 1)
        }
    }
}

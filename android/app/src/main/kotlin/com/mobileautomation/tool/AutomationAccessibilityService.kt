package com.mobileautomation.tool

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The automation driver.
 *
 * Scoped by res/xml/accessibility_service_config.xml to the allowlisted UAT
 * package plus Settings and the permission controller (used only to reset the
 * target app before a run): it receives no events from, and can read no window
 * belonging to, any other application on the device. It does not observe notifications, does
 * not filter key events and never touches SMS.
 *
 * The service holds no automation logic of its own. It exposes the four device
 * capabilities the engine needs — read the tree, tap a point, take a screenshot,
 * report liveness — and [AutomationEngine] drives them from a worker thread.
 */
class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: AutomationAccessibilityService? = null

        /** The connected service, or null when it is not currently bound. */
        fun connected(): AutomationAccessibilityService? = instance

        /**
         * True when the user has enabled this service in Settings *and* the
         * system has bound it. Both halves matter: the settings entry can name a
         * service the system has since stopped, and a run started in that state
         * would fail on the first tree read.
         */
        fun isReady(context: Context): Boolean =
            isEnabledInSettings(context) && instance != null

        fun isEnabledInSettings(context: Context): Boolean {
            val expected = ComponentName(
                context.packageName,
                AutomationAccessibilityService::class.java.name,
            )
            val enabled = try {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                )
            } catch (_: Throwable) {
                null
            } ?: return false
            return enabled.split(':').any {
                val parsed = ComponentName.unflattenFromString(it)
                parsed != null &&
                    parsed.packageName == expected.packageName &&
                    parsed.className == expected.className
            }
        }
    }

    /** Wall clock of the most recent window event from the target package. */
    @Volatile
    var lastTargetEventAtMs: Long = 0L
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // android:packageNames also admits the Settings and permission screens
        // used for the pre-run reset; only target app events count here. The
        // engine polls the tree; this only records liveness.
        if (event == null) return
        if (event.packageName?.toString() !in AutomationConfig.ALLOWED_PACKAGES) return
        lastTargetEventAtMs = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        // Nothing to interrupt — the service performs no long running feedback.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        AutomationEngine.abortBecauseServiceGone()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Root of the active window, or null when unreadable. This can be Settings
     * or the permission prompt as well as the target app; the engine checks
     * the package before using it.
     */
    fun rootNode(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (_: Throwable) {
        null
    }

    /**
     * Root of the first on-screen window owned by one of [packages], whether or
     * not it is the active window. Needed for the permission prompt: it sits on
     * top of the target app while [rootNode] still returns the app.
     */
    fun windowRootOf(packages: Set<String>): AccessibilityNodeInfo? = try {
        windows.firstNotNullOfOrNull { window ->
            window.root?.takeIf { it.packageName?.toString() in packages }
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Taps a point. Only ever called with the centre of a node this tool has
     * already resolved from the accessibility tree — never a hard coded
     * coordinate. Blocks the calling (worker) thread until the gesture settles.
     */
    fun tapAt(x: Float, y: Float): Boolean {
        if (x < 0 || y < 0) return false
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        val latch = CountDownLatch(1)
        var completed = false
        val dispatched = try {
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(description: GestureDescription?) {
                        completed = true
                        latch.countDown()
                    }

                    override fun onCancelled(description: GestureDescription?) {
                        latch.countDown()
                    }
                },
                null,
            )
        } catch (_: Throwable) {
            false
        }
        if (!dispatched) return false
        return try {
            latch.await(2, TimeUnit.SECONDS) && completed
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * Dispatches the system "back" action. Used to close a screen (Search) or
     * back out of one (an already-empty watchlist's Edit screen) without
     * needing a dedicated in-app close button.
     */
    fun pressBack(): Boolean = try {
        performGlobalAction(GLOBAL_ACTION_BACK)
    } catch (_: Throwable) {
        false
    }

    /** Outcome of a best-effort screenshot. */
    sealed class ShotResult {
        data class Saved(val path: String) : ShotResult()
        data class Unavailable(val reason: String) : ShotResult()
    }

    /**
     * Best effort screen capture. A failure here — an unsupported API level, a
     * FLAG_SECURE window in the target app, a rate limited request — is reported
     * as [ShotResult.Unavailable] and never fails the automation run.
     */
    fun captureScreenshot(directory: File, label: String): ShotResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ShotResult.Unavailable(
                "Screenshot capture needs Android 11 (API 30); this device is API ${Build.VERSION.SDK_INT}.",
            )
        }
        val latch = CountDownLatch(1)
        var bitmap: Bitmap? = null
        var error: String? = null
        val executor = Executors.newSingleThreadExecutor()
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val buffer = screenshot.hardwareBuffer
                            val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            // Copy off the hardware buffer so it can be closed
                            // and so compress() works on every device.
                            bitmap = wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                            wrapped?.recycle()
                            buffer.close()
                        } catch (t: Throwable) {
                            error = "Could not decode the screenshot: ${t.javaClass.simpleName}"
                        } finally {
                            latch.countDown()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        error = "The system refused the screenshot request (error $errorCode). " +
                            "A secure screen in the target application will do this."
                        latch.countDown()
                    }
                },
            )
            if (!latch.await(6, TimeUnit.SECONDS)) {
                return ShotResult.Unavailable("The screenshot request timed out.")
            }
        } catch (t: Throwable) {
            return ShotResult.Unavailable("Screenshot capture failed: ${t.javaClass.simpleName}")
        } finally {
            executor.shutdown()
        }

        error?.let { return ShotResult.Unavailable(it) }
        val shot = bitmap ?: return ShotResult.Unavailable("The system returned an empty screenshot.")
        return try {
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, "$label.png")
            FileOutputStream(file).use { out ->
                shot.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            shot.recycle()
            ShotResult.Saved(file.absolutePath)
        } catch (t: Throwable) {
            ShotResult.Unavailable("Could not write the screenshot: ${t.javaClass.simpleName}")
        }
    }
}

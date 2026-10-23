package com.mobileautomation.tool

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.mobileautomation.tool.AutomationConfig.SystemIds
import java.io.File
import java.util.UUID

/**
 * Device level automation primitives, plus the lifecycle of a single run.
 *
 * The engine owns no knowledge of the login flow — [LoginAutomationFlow] does.
 * It runs on a dedicated worker thread: accessibility tree reads block, and the
 * Flutter platform thread must stay responsive so `stopAutomation` can arrive
 * mid-run.
 *
 * Waiting is always polling on [AutomationConfig.POLL_INTERVAL_MS] against a
 * deadline. There are no long blocking sleeps.
 */
class AutomationEngine private constructor(
    val appContext: Context,
    private val service: AutomationAccessibilityService,
    val targetPackage: String,
    private val sink: (Map<String, Any?>) -> Unit,
) {

    /**
     * A run request. Holds credentials, so it is created from the platform call,
     * consumed by the worker thread, and never logged or persisted.
     */
    data class StartRequest(
        val targetPackage: String,
        val clientId: String,
        val otp: String,
        val mpin: String,
        val password: String = "",
        /** The operator accepted the system recording dialog for this run. */
        val recordScreen: Boolean = false,
        /** Ids of the modules to run; null runs all of them. See [AppModules]. */
        val modules: List<String>? = null,
        /** Clear the target app's data first. Off for a phone whose Settings cannot be driven. */
        val clearData: Boolean = true,
    )

    // ── Run lifecycle (single run at a time) ─────────────────────────────────

    companion object {
        private const val RECORDING_TAIL_MS = 1_500L

        @Volatile
        private var worker: Thread? = null

        @Volatile
        private var active: AutomationEngine? = null

        /** Serialised result of the most recent run, for `getLastReport`. */
        @Volatile
        var lastResult: Map<String, Any?>? = null
            private set

        fun isRunning(): Boolean = worker?.isAlive == true

        /**
         * Validates and starts a run. Returns null on success, or a message
         * explaining why the run cannot start. Never includes a credential.
         */
        @Synchronized
        fun start(
            context: Context,
            request: StartRequest,
            sink: (Map<String, Any?>) -> Unit,
        ): String? {
            if (isRunning()) return "An automation run is already in progress."

            if (!AutomationConfig.ALLOWED_PACKAGES.contains(request.targetPackage)) {
                return "Refusing to run: '${request.targetPackage}' is not in the automation " +
                    "allowlist. Allowed: ${AutomationConfig.ALLOWED_PACKAGES.joinToString()}."
            }
            val app = TargetApp.forPackage(request.targetPackage)
                ?: return "'${request.targetPackage}' has no automation flow."
            if (request.clientId.isBlank()) {
                return if (app == TargetApp.TRADION) "User ID is required." else "Client ID is required."
            }
            if (request.otp.isBlank()) return "OTP is required."
            when (app.secret) {
                TargetApp.Secret.MPIN -> if (request.mpin.isBlank()) return "MPIN is required."
                TargetApp.Secret.PASSWORD -> if (request.password.isBlank()) return "Password is required."
            }

            val service = AutomationAccessibilityService.connected()
                ?: return "The Automation Tool accessibility service is not running. " +
                    "Enable it in Settings > Accessibility and try again."

            val engine = AutomationEngine(
                appContext = context.applicationContext,
                service = service,
                targetPackage = request.targetPackage,
                sink = sink,
            )
            engine.clearAppData = request.clearData
            active = engine

            val thread = Thread({
                try {
                    if (request.recordScreen) engine.beginRecording()
                    val result = when (app) {
                        TargetApp.ASHIKA -> LoginAutomationFlow(
                            engine,
                            ModuleSelection(AppModules.ASHIKA, request.modules?.toSet()),
                        ).run(
                            clientId = request.clientId,
                            otp = request.otp,
                            mpin = request.mpin,
                        )
                        TargetApp.TRADION -> TradionAutomationFlow(
                            engine,
                            ModuleSelection(AppModules.TRADION, request.modules?.toSet()),
                        ).run(
                            userId = request.clientId,
                            password = request.password,
                            otp = request.otp,
                        )
                    }
                    engine.endRecording()?.let { recording ->
                        result.videoPath = recording.file?.absolutePath
                        result.recordingStartedAtMs = recording.startedAtMs
                        result.recordingNote = recording.note
                    }
                    lastResult = result.toMap()
                    engine.emit(
                        if (result.stopped) "automation_stopped" else "automation_completed",
                        mapOf("result" to result.toMap()),
                    )
                } catch (t: Throwable) {
                    // A crash in the driver must still produce a report.
                    val recording = engine.endRecording()
                    engine.emit(
                        "automation_completed",
                        mapOf(
                            "result" to mapOf(
                                "runId" to "unknown",
                                "passed" to false,
                                "failureMessage" to
                                    "The automation driver failed unexpectedly: " +
                                    "${t.javaClass.simpleName}: ${t.message ?: "no detail"}",
                                "steps" to emptyList<Any>(),
                                "videoPath" to recording?.file?.absolutePath,
                                "recordingStartedAtMs" to recording?.startedAtMs,
                                "recordingNote" to recording?.note,
                            ),
                        ),
                    )
                } finally {
                    engine.endRecording()
                    active = null
                    worker = null
                    // Whatever stage the run ended at — completed, aborted,
                    // operator-stopped, or crashed — leave the operator looking
                    // at this tool's report rather than stranded in the target
                    // app.
                    bringToolToForeground(context)
                }
            }, "automation-engine")
            thread.isDaemon = true
            worker = thread
            thread.start()
            return null
        }

        /** Requests cancellation. Returns true when a run was actually active. */
        fun stop(): Boolean {
            val engine = active ?: return false
            engine.cancelled = true
            return true
        }

        /** Called when the accessibility service is unbound mid-run. */
        fun abortBecauseServiceGone() {
            active?.let {
                it.serviceGone = true
                it.cancelled = true
            }
        }

        /**
         * Re-launches this tool's own launcher activity, bringing its
         * existing task to the foreground (the same effect as the operator
         * tapping its icon again) rather than leaving the target app in
         * front. Best effort: a failure here never affects the run's result.
         */
        private fun bringToolToForeground(context: Context) {
            try {
                val intent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?: return
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
                )
                context.startActivity(intent)
            } catch (_: Throwable) {
                // The operator can always switch back manually.
            }
        }
    }

    // ── Engine state ────────────────────────────────────────────────────────

    @Volatile
    var cancelled: Boolean = false

    @Volatile
    var serviceGone: Boolean = false

    /**
     * Set once the target app's window has been read at least once. Until then a
     * null root just means "still launching" rather than "another app took over".
     */
    @Volatile
    private var everSawTarget: Boolean = false

    val runId: String = UUID.randomUUID().toString()

    /** False when the operator turned off "Clear the app's data first". */
    @Volatile
    var clearAppData: Boolean = true

    val launcher = TargetAppLauncher(appContext)

    // ── Screen recording ────────────────────────────────────────────────────

    /** True once [beginRecording] ran, so [endRecording] never reads an older run's result. */
    private var recordingRequested = false
    private var recordingResult: ScreenRecorder.Finished? = null

    private val recordingFile: File
        get() {
            val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            return File(File(base, "reports"), "recording_${runId.take(8)}.mp4")
        }

    /**
     * Starts the screen recording before anything else happens, so the video
     * covers the data reset and launch too. A recording that cannot start is
     * not a failed run: the reason travels in the result's `recordingNote`.
     */
    fun beginRecording() {
        recordingRequested = true
        val started = ScreenRecorder.start(appContext, recordingFile)
        if (!started.ok) {
            recordingResult = ScreenRecorder.Finished(null, null, "not recorded: ${started.detail}")
        }
    }

    /**
     * Stops the recording and returns what it produced, or null when this run
     * was not recorded. Holds the last screen for a moment first so the video
     * ends on the final state rather than mid-transition. Safe to call twice.
     */
    fun endRecording(): ScreenRecorder.Finished? {
        if (!recordingRequested) return null
        recordingResult?.let { return it }
        if (!cancelled) sleep(RECORDING_TAIL_MS)
        return ScreenRecorder.stop().also { recordingResult = it }
    }

    // ── Events ──────────────────────────────────────────────────────────────

    fun emit(event: String, extra: Map<String, Any?> = emptyMap()) {
        val payload = HashMap<String, Any?>(extra.size + 3)
        payload["event"] = event
        payload["runId"] = runId
        payload["atMs"] = System.currentTimeMillis()
        payload.putAll(extra)
        sink(payload)
    }

    // ── Target window ───────────────────────────────────────────────────────

    /** Notification permission prompts answered with "Allow" during this run. */
    @Volatile
    var notificationPromptsAllowed: Int = 0
        private set

    /** A notification permission prompt is on screen right now. */
    @Volatile
    var promptVisible: Boolean = false
        private set

    /**
     * A notification prompt stayed up after the tool clicked Allow and then
     * tapped it: Android ignored the automated input. Reported so a failure
     * that follows can say so, rather than blaming the app.
     */
    @Volatile
    var promptStuck: Boolean = false
        private set

    private var promptFirstSeenMs = 0L
    private var promptLastGestureMs = 0L

    /**
     * Root of the target app's window, or null when anything else is in front.
     *
     * The service can also read Settings and the permission prompt (for the
     * pre-run reset), so the package is checked here to keep every flow lookup
     * confined to the target app. A notification permission prompt is answered
     * on the way: clearing app data resets that permission, so the target app
     * asks again on every run and the prompt would otherwise cover it.
     */
    private fun targetRoot(): AccessibilityNodeInfo? {
        // Checked across all windows: the prompt is drawn over the target app
        // while the system still reports the app as the active window, and
        // while it is up, coordinate taps land on the prompt instead.
        service.windowRootOf(AutomationConfig.PERMISSION_CONTROLLER_PACKAGES)?.let { prompt ->
            promptVisible = true
            allowNotificationPrompt(prompt)
            return null
        }
        promptVisible = false
        promptFirstSeenMs = 0L
        val root = service.rootNode() ?: return null
        return root.takeIf { it.packageName?.toString() == targetPackage }
    }

    /**
     * Taps "Allow" on the prompt only if it is asking about notifications.
     *
     * ACTION_CLICK first. If the prompt is still up a few seconds later the
     * click was ignored, so the button is tapped as a gesture at its own bounds,
     * and if it is still up after that it is flagged [promptStuck]. Whatever the
     * outcome nothing but the Allow button of a *notification* prompt is touched.
     */
    private fun allowNotificationPrompt(root: AccessibilityNodeInfo) {
        val message = AccessibilityNodeFinder.byIdentifier(root, SystemIds.PERMISSION_MESSAGE)
            ?.text?.toString() ?: return
        if (!message.contains("notification", ignoreCase = true)) return
        val allow = AccessibilityNodeFinder.byIdentifier(root, SystemIds.PERMISSION_ALLOW_BUTTON) ?: return
        val now = SystemClock.elapsedRealtime()
        if (promptFirstSeenMs == 0L) promptFirstSeenMs = now
        val waited = now - promptFirstSeenMs
        when {
            waited < AutomationConfig.Timeouts.PROMPT_GESTURE_AFTER -> {
                val clickable = AccessibilityNodeFinder.resolveClickable(allow)
                if (clickable != null && performClick(clickable)) notificationPromptsAllowed++
            }
            now - promptLastGestureMs >= 1_000L -> {
                promptLastGestureMs = now
                if (tapNode(allow)) notificationPromptsAllowed++
                if (waited >= AutomationConfig.Timeouts.PROMPT_STUCK) promptStuck = true
            }
        }
    }

    // ── App data reset ──────────────────────────────────────────────────────

    /**
     * Clears all of the target app's data the way an operator would: App info
     * > Storage > Clear storage > confirm. No API lets one app clear another's
     * data without shell or system privileges, so the Settings UI is driven
     * instead. Settings is left on screen; the caller launches the target next.
     */
    fun clearTargetAppData(): ActionOutcome {
        val appInfo = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", targetPackage, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            appContext.startActivity(appInfo)
        } catch (t: Throwable) {
            return ActionOutcome(false, "could not open the target app's App info: ${t.javaClass.simpleName}")
        }

        // Some phones (MIUI, for one) put the clear button on App info itself; others
        // nest it one level down under a Storage entry. Whichever is there is used,
        // the button first. What was found is remembered, not re-read from its text:
        // a button spelled "Clear Data", or exposed only as a content description,
        // must not be mistaken for the Storage row and tapped as one.
        val first = waitForSettings(scroll = true) { root ->
            clearButton(root)?.let { Found(it, isClear = true) }
                ?: storageEntry(root)?.let { Found(it, isClear = false) }
        } ?: return ActionOutcome(false, settingsMiss("a Clear data button or a Storage entry on App info"))

        var clear = first.node
        if (!first.isClear) {
            if (!clickResolved(clear)) return ActionOutcome(false, "could not tap the Storage entry on App info")
            if (!sleep(AutomationConfig.Timeouts.SETTLE)) return ActionOutcome(false, "cancelled")
            clear = waitForSettings(scroll = true) { root -> clearButton(root) }
                ?: return ActionOutcome(false, settingsMiss("the Clear data button on the Storage page"))
        }
        if (isDisabled(clear)) {
            return ActionOutcome(true, "Clear storage is disabled in Settings: the app already holds no data")
        }
        if (!clickResolved(clear)) return ActionOutcome(false, "could not tap Clear data")

        // Usually one confirmation dialog; MIUI asks which data first, then OK.
        var confirmations = 0
        while (confirmations < 2) {
            val budget = if (confirmations == 0) AutomationConfig.Timeouts.SETTINGS_SCREEN else 1_500L
            val confirm = waitForSettings(scroll = false, timeoutMs = budget) { root -> confirmButton(root) } ?: break
            if (!clickResolved(confirm)) break
            confirmations++
            if (!sleep(AutomationConfig.Timeouts.SETTLE)) return ActionOutcome(false, "cancelled")
        }
        if (cancelled) return ActionOutcome(false, "cancelled")
        if (confirmations == 0) return ActionOutcome(false, settingsMiss("the clear-data confirmation button"))
        return ActionOutcome(true, "cleared via App info > Storage > Clear data and confirmed")
    }

    private class Found(val node: AccessibilityNodeInfo, val isClear: Boolean)

    private fun labelled(node: AccessibilityNodeInfo, matches: (CharSequence?) -> Boolean): Boolean =
        matches(node.text) || matches(node.contentDescription)

    private fun clearButton(root: AccessibilityNodeInfo) =
        AccessibilityNodeFinder.firstWhere(root) { labelled(it, SettingsMatch::isClearData) }

    private fun storageEntry(root: AccessibilityNodeInfo) =
        AccessibilityNodeFinder.firstWhere(root) { labelled(it, SettingsMatch::isStorageEntry) }

    private fun confirmButton(root: AccessibilityNodeInfo) =
        AccessibilityNodeFinder.firstWhere(root) { labelled(it, SettingsMatch::isConfirm) }

    /**
     * Polls the Settings window with [find] until it returns something. With
     * [scroll], scrolls the page forward after a few misses in case the entry
     * sits below the fold; not straight away, so a page still loading is not
     * scrolled past its top.
     */
    private fun <T : Any> waitForSettings(
        scroll: Boolean,
        timeoutMs: Long = AutomationConfig.Timeouts.SETTINGS_SCREEN,
        find: (AccessibilityNodeInfo) -> T?,
    ): T? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var misses = 0
        var scrolls = 0
        while (true) {
            if (cancelled) return null
            val root = service.rootNode()
            if (root != null && root.packageName?.toString() in AutomationConfig.SETTINGS_PACKAGES) {
                find(root)?.let { return it }
                if (scroll && ++misses % 4 == 0 && scrolls < 3) {
                    val scrolled = try {
                        AccessibilityNodeFinder.firstScrollable(root)
                            ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                    } catch (_: Throwable) {
                        false
                    }
                    if (scrolled) scrolls++
                }
            }
            if (SystemClock.elapsedRealtime() >= deadline) return null
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return null
        }
    }

    /**
     * Why a Settings control was not found, with what the screen did show: the
     * package in front and its labels (Settings pages hold no secret), so the
     * next report says which wording to teach [SettingsMatch].
     */
    private fun settingsMiss(what: String): String {
        val root = service.rootNode()
        val front = root?.packageName?.toString()
        return if (front !in AutomationConfig.SETTINGS_PACKAGES) {
            "Settings did not come to the front (the foreground window belongs to ${front ?: "nothing readable"}). " +
                "If Settings opened, reinstall the tool or toggle its accessibility service off and on so " +
                "it picks up access to Settings, and check that this phone's Settings package is listed in " +
                "accessibility_service_config.xml."
        } else {
            val texts = AccessibilityNodeFinder.visibleTexts(root, 30)
            "$what was not found in Settings within ${AutomationConfig.Timeouts.SETTINGS_SCREEN / 1000} seconds. " +
                "Settings package: $front. " +
                (if (texts.isEmpty()) "No text was readable on that screen. " else "It showed: ${texts.joinToString(" | ")}. ") +
                "If the wording is not recognised, add it to SettingsMatch; or turn off " +
                "\"Clear the app's data first\" on the run form and clear the app by hand."
        }
    }

    /** A Settings button greys out its label and its container; check both. */
    private fun isDisabled(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 4) {
            if (!current.isEnabled) return true
            if (current.isClickable) return false
            current = try {
                current.parent
            } catch (_: Throwable) {
                null
            }
            hops++
        }
        return false
    }

    // ── Waiting ─────────────────────────────────────────────────────────────

    sealed class WaitOutcome {
        /** [identifier] is the one that matched, out of the requested set. */
        class Found(val identifier: String, val node: AccessibilityNodeInfo) : WaitOutcome()
        class TimedOut(val visibleIdentifiers: List<String>, val windowReadable: Boolean) : WaitOutcome()
        object Cancelled : WaitOutcome()
        object TargetWindowLost : WaitOutcome()
    }

    /**
     * Polls until one of [identifiers] appears, the budget runs out, the run is
     * cancelled, or the target app stops being the readable foreground window.
     */
    fun waitForAny(identifiers: List<String>, timeoutMs: Long): WaitOutcome {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var nullRootSinceMs = 0L
        var lastIdentifiers: List<String> = emptyList()
        var lastReadable = false

        while (true) {
            if (cancelled) return WaitOutcome.Cancelled

            val root = targetRoot()
            if (root != null) {
                everSawTarget = true
                nullRootSinceMs = 0L
                lastReadable = true
                for (identifier in identifiers) {
                    val node = AccessibilityNodeFinder.byIdentifier(root, identifier)
                    if (node != null) return WaitOutcome.Found(identifier, node)
                }
                lastIdentifiers = AccessibilityNodeFinder.collectIdentifiers(root)
            } else {
                lastReadable = false
                // The service is scoped to a few packages, so another app in the
                // foreground shows up as an unreadable window rather than as a
                // different package name. A sustained gap means we lost the app —
                // unless the gap is a notification prompt, which is waited out.
                if (promptVisible) {
                    nullRootSinceMs = 0L
                } else if (everSawTarget) {
                    val now = SystemClock.elapsedRealtime()
                    if (nullRootSinceMs == 0L) {
                        nullRootSinceMs = now
                    } else if (now - nullRootSinceMs >= AutomationConfig.FOREIGN_WINDOW_ABORT_MS) {
                        return WaitOutcome.TargetWindowLost
                    }
                }
            }

            if (SystemClock.elapsedRealtime() >= deadline) {
                return WaitOutcome.TimedOut(lastIdentifiers, lastReadable)
            }
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return WaitOutcome.Cancelled
        }
    }

    /**
     * Polls until the target app's window becomes readable at all, which is the
     * signal that it reached the foreground after a launch.
     */
    fun waitForTargetWindow(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (cancelled) return false
            if (targetRoot() != null) {
                everSawTarget = true
                return true
            }
            if (SystemClock.elapsedRealtime() >= deadline) return false
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return false
        }
    }

    /** Single, immediate lookup. Null when absent or the window is unreadable. */
    fun findNow(identifier: String): AccessibilityNodeInfo? {
        val root = targetRoot() ?: return null
        everSawTarget = true
        return AccessibilityNodeFinder.byIdentifier(root, identifier)
    }

    fun isPresent(identifier: String): Boolean = findNow(identifier) != null

    /** True when [identifier] is on screen and its tappable node is enabled. Taps nothing. */
    fun isTappable(identifier: String): Boolean {
        val anchor = findNow(identifier) ?: return false
        return AccessibilityNodeFinder.resolveClickable(anchor)?.isEnabled == true
    }

    /** The first of [identifiers] currently present, if any. */
    fun firstPresent(identifiers: List<String>): String? {
        val root = targetRoot() ?: return null
        return identifiers.firstOrNull { AccessibilityNodeFinder.byIdentifier(root, it) != null }
    }

    /** How many nodes currently carry [identifier] (only rows Flutter has built count). */
    fun countOf(identifier: String): Int =
        AccessibilityNodeFinder.countByIdentifier(targetRoot(), identifier)

    /**
     * The visible label of the node carrying [identifier]. Only for static
     * display text such as a scrip count — never call this on an input, whose
     * text could be a credential.
     */
    fun labelOf(identifier: String): String? {
        val node = findNow(identifier) ?: return null
        return (node.text ?: node.contentDescription)?.toString()
    }

    /** Every label under nodes carrying [identifier]; see [AccessibilityNodeFinder.labelsOf]. */
    fun labelsOf(identifier: String): List<String> =
        AccessibilityNodeFinder.labelsOf(targetRoot(), identifier)

    /** The own text of each node carrying [identifier], top to bottom. For static list rows only. */
    fun ownLabelsOf(identifier: String): List<String> =
        AccessibilityNodeFinder.ownLabelsOf(targetRoot(), identifier)

    /** One combined, non-secret display label per tagged static list row. */
    fun groupedLabelsOf(identifier: String): List<String> =
        AccessibilityNodeFinder.groupedLabelsOf(targetRoot(), identifier)

    /**
     * Reads the current watchlist-name field through its actual editable node.
     * This accessor is deliberately restricted to that one non-credential field
     * so a login value cannot accidentally be copied into a report.
     */
    fun watchlistName(): String? {
        val identifier = AutomationConfig.TradionIds.REORDER_RENAME_INPUT
        val anchor = findNow(identifier) ?: return null
        return AccessibilityNodeFinder.editableText(anchor)
    }

    data class DescendantActionOutcome(
        val ok: Boolean,
        val detail: String,
        val containerIndex: Int? = null,
    )

    /**
     * Taps [descendantIdentifier] inside the first [containerIdentifier] row
     * that carries it, returning that row's index. Search uses the index to tap
     * the *same* result after its + changes into an added icon.
     */
    fun tapFirstDescendant(
        containerIdentifier: String,
        descendantIdentifier: String,
    ): DescendantActionOutcome {
        val root = targetRoot()
            ?: return DescendantActionOutcome(false, "the target application window is not readable")
        val containers = AccessibilityNodeFinder.allByIdentifier(root, containerIdentifier)
        val index = containers.indexOfFirst {
            AccessibilityNodeFinder.byIdentifier(it, descendantIdentifier) != null
        }
        if (index < 0) {
            return DescendantActionOutcome(
                false,
                "no '$containerIdentifier' row contains '$descendantIdentifier'",
            )
        }
        return tapDescendantAt(containers, index, containerIdentifier, descendantIdentifier)
    }

    /** True when the indexed tagged row currently contains [descendantIdentifier]. */
    fun hasDescendantAt(
        containerIdentifier: String,
        containerIndex: Int,
        descendantIdentifier: String,
    ): Boolean {
        val containers = AccessibilityNodeFinder.allByIdentifier(targetRoot(), containerIdentifier)
        val container = containers.getOrNull(containerIndex) ?: return false
        return AccessibilityNodeFinder.byIdentifier(container, descendantIdentifier) != null
    }

    /** Taps a tagged descendant in the indexed tagged row. */
    fun tapDescendantAt(
        containerIdentifier: String,
        containerIndex: Int,
        descendantIdentifier: String,
    ): DescendantActionOutcome {
        val containers = AccessibilityNodeFinder.allByIdentifier(targetRoot(), containerIdentifier)
        return tapDescendantAt(containers, containerIndex, containerIdentifier, descendantIdentifier)
    }

    private fun tapDescendantAt(
        containers: List<AccessibilityNodeInfo>,
        containerIndex: Int,
        containerIdentifier: String,
        descendantIdentifier: String,
    ): DescendantActionOutcome {
        val container = containers.getOrNull(containerIndex)
            ?: return DescendantActionOutcome(
                false,
                "'$containerIdentifier' row $containerIndex is no longer built",
                containerIndex,
            )
        val anchor = AccessibilityNodeFinder.byIdentifier(container, descendantIdentifier)
            ?: return DescendantActionOutcome(
                false,
                "'$containerIdentifier' row $containerIndex has no '$descendantIdentifier'",
                containerIndex,
            )
        val clickable = AccessibilityNodeFinder.resolveClickable(anchor)
        val ok = clickable?.let(::performClick) == true || tapNode(clickable ?: anchor)
        val detail = if (ok) {
            "tapped '$descendantIdentifier' in '$containerIdentifier' row $containerIndex"
        } else {
            "found '$descendantIdentifier' in '$containerIdentifier' row $containerIndex, but the tap failed"
        }
        return DescendantActionOutcome(ok, detail, containerIndex)
    }

    /**
     * Performs the custom accessibility action whose label contains [labelPart]
     * on the first node carrying [identifier] (e.g. "Move down" on a reorderable
     * row). No coordinate fallback: a drag is never guessed at.
     */
    fun customAction(identifier: String, labelPart: String): ActionOutcome {
        val root = targetRoot() ?: return ActionOutcome(false, "the target application window is not readable")
        val anchor = AccessibilityNodeFinder.byIdentifier(root, identifier)
            ?: return ActionOutcome(false, "no node matched the identifier '$identifier'")
        val available = mutableListOf<String>()
        val (node, action) = AccessibilityNodeFinder.findCustomAction(anchor, labelPart, available)
            ?: return ActionOutcome(
                false,
                "'$identifier' offers no '$labelPart' action; custom actions seen: " +
                    "${available.distinct().ifEmpty { listOf("none") }.joinToString()}",
            )
        val ok = try { node.performAction(action.id) } catch (_: Throwable) { false }
        return if (ok) ActionOutcome(true, "performed '${action.label}' on '$identifier'")
        else ActionOutcome(false, "'${action.label}' was rejected by '$identifier'")
    }

    /** Short texts on screen now, for failure messages on screens with no secret. See [AccessibilityNodeFinder.visibleTexts]. */
    fun screenTexts(): List<String> = AccessibilityNodeFinder.visibleTexts(targetRoot())

    /** Identifiers on screen right now, for failure messages. */
    fun visibleIdentifiers(): List<String> = AccessibilityNodeFinder.collectIdentifiers(targetRoot())

    /** Polls until [identifier] has left the screen. False on timeout or cancel. */
    fun waitForGone(identifier: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (cancelled) return false
            if (targetRoot() != null && !isPresent(identifier)) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return false
        }
    }

    /**
     * Taps [identifier] and waits for one of [expect] to appear.
     *
     * Flutter drops input for about a frame around every route push or pop:
     * the Navigator absorbs pointers, which also strips the semantic actions
     * from every node. A tap that lands in that window is lost without any
     * error, so a single tap-and-hope cannot tell "opened" from "ignored". If
     * nothing from [expect] appears within [retryAfterMs] while [identifier] is
     * still on screen — a page that really opened would have covered it — the
     * tap is repeated, until [timeoutMs]. With [tapAtLeastOnce], [expect]
     * already being on screen does not excuse the tap (e.g. another watchlist
     * tab already shows the same controls). When any [stopOn] identifier
     * appears, the wait fails immediately. This lets a flow distinguish a
     * target-app redirect (for example, an expired session returning to Login)
     * from an ordinary navigation timeout.
     */
    fun tapAndConfirm(
        identifier: String,
        expect: List<String>,
        timeoutMs: Long,
        retryAfterMs: Long = AutomationConfig.Timeouts.CONFIRM_RETRY,
        tapAtLeastOnce: Boolean = false,
        longPress: Boolean = false,
        stopOn: List<String> = emptyList(),
    ): ActionOutcome {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var taps = 0
        var lastTap: ActionOutcome? = null
        var nextTapAt = 0L
        while (true) {
            if (cancelled) return ActionOutcome(false, "cancelled")
            if (!tapAtLeastOnce || taps > 0) firstPresent(expect)?.let { found ->
                val how = lastTap?.detail ?: "no tap was needed"
                val retries = if (taps > 1) " after $taps taps (earlier ones were dropped)" else ""
                return ActionOutcome(true, "$how; '$found' appeared$retries")
            }
            firstPresent(stopOn)?.let { found ->
                val attempted = if (taps == 0) {
                    "before '$identifier' could be tapped"
                } else {
                    "after tapping '$identifier' $taps time(s)"
                }
                return ActionOutcome(
                    false,
                    "'$found' appeared $attempted instead of " +
                        expect.joinToString(" or ") { "'$it'" } + ".",
                )
            }
            val now = SystemClock.elapsedRealtime()
            if (now >= nextTapAt && isPresent(identifier)) {
                val outcome = if (longPress) longTap(identifier) else tap(identifier)
                if (outcome.ok) {
                    taps++
                    lastTap = outcome
                    nextTapAt = now + retryAfterMs
                }
            }
            if (now >= deadline) {
                val what = expect.joinToString(" or ") { "'$it'" }
                val tapped = if (taps == 0) {
                    "'$identifier' could never be tapped (${lastTap?.detail ?: "not on screen"})"
                } else {
                    "tapped '$identifier' $taps time(s)"
                }
                return ActionOutcome(
                    false,
                    "$tapped but $what did not appear within ${timeoutMs / 1000} seconds. " +
                        "Identifiers on screen: ${visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
                )
            }
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return ActionOutcome(false, "cancelled")
        }
    }

    /**
     * Scrolls the page's main vertical list one screenful. Flutter reports a
     * horizontal scrollable as HorizontalScrollView and a vertical one as
     * ScrollView, so chip strips and carousels are never picked; of the
     * vertical ones the largest on screen is the page itself.
     */
    fun scrollPage(forward: Boolean): Boolean {
        val list = AccessibilityNodeFinder.largestVerticalScrollable(targetRoot()) ?: return false
        return try {
            list.performAction(
                if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            )
        } catch (_: Throwable) {
            false
        }
    }

    /** Polls [condition] until it holds. False on timeout or cancel. */
    fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (cancelled) return false
            if (condition()) return true
            if (SystemClock.elapsedRealtime() >= deadline) return false
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return false
        }
    }

    /**
     * Brings [identifier] into the built range of the scrollable list holding
     * one of [siblings], scrolling forward and then back. Flutter only builds
     * list items near the viewport, so a chip far to one side has no node at
     * all until the list is scrolled towards it.
     */
    fun revealInList(identifier: String, siblings: List<String>, maxScrolls: Int = 4): Boolean {
        for (action in listOf(
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
        )) {
            repeat(maxScrolls) {
                if (isPresent(identifier)) return true
                val anchor = siblings.firstNotNullOfOrNull { findNow(it) } ?: return false
                val list = AccessibilityNodeFinder.scrollableAncestor(anchor) ?: return false
                val scrolled = try {
                    list.performAction(action)
                } catch (_: Throwable) {
                    false
                }
                if (!scrolled) return@repeat
                if (!sleep(AutomationConfig.Timeouts.SETTLE)) return false
            }
        }
        return isPresent(identifier)
    }

    /** Interruptible sleep. Returns false when the run was cancelled. */
    fun sleep(millis: Long): Boolean {
        val end = SystemClock.elapsedRealtime() + millis
        while (SystemClock.elapsedRealtime() < end) {
            if (cancelled) return false
            try {
                Thread.sleep(minOf(60L, end - SystemClock.elapsedRealtime()).coerceAtLeast(1L))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return !cancelled
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    /** [detail] is a note when [ok], and the failure reason otherwise. */
    data class ActionOutcome(val ok: Boolean, val detail: String)

    /**
     * Types [value] into the field anchored by [identifier].
     *
     * [value] is a credential: it is passed straight to the platform and is
     * never logged, echoed into [ActionOutcome.detail], or read back.
     */
    fun enterText(identifier: String, value: String): ActionOutcome {
        val root = targetRoot()
            ?: return ActionOutcome(false, "the target application window is not readable")
        val anchor = AccessibilityNodeFinder.byIdentifier(root, identifier)
            ?: return ActionOutcome(false, "no node with the identifier '$identifier' is present")
        var editable = AccessibilityNodeFinder.resolveEditable(anchor)
            ?: return ActionOutcome(
                false,
                "'$identifier' exposes no editable node (no ACTION_SET_TEXT in its subtree)",
            )

        // A Flutter text field only takes ACTION_SET_TEXT while it has input
        // focus (RenderEditable registers the handler only when focused). An
        // unfocused field still reports the action as accepted and silently
        // drops the text, so focus it with a tap first — ACTION_FOCUS is not
        // enough on Flutter — and wait until it advertises SET_TEXT.
        var focusedByTap = false
        if (!AccessibilityNodeFinder.supportsAction(editable, AccessibilityNodeInfo.ACTION_SET_TEXT)) {
            val clickable = AccessibilityNodeFinder.resolveClickable(editable) ?: editable
            val tapped = try {
                clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } catch (_: Throwable) {
                false
            } || tapNode(clickable)
            if (!tapped) return ActionOutcome(false, "could not tap '$identifier' to focus it")
            focusedByTap = true
            var ready: AccessibilityNodeInfo? = null
            waitUntil(AutomationConfig.Timeouts.FIELD_FOCUS) {
                ready = findNow(identifier)?.let { AccessibilityNodeFinder.resolveEditable(it) }
                    ?.takeIf { AccessibilityNodeFinder.supportsAction(it, AccessibilityNodeInfo.ACTION_SET_TEXT) }
                ready != null
            }
            editable = ready ?: return ActionOutcome(
                false,
                "tapped '$identifier' but it never took focus (it still offers no ACTION_SET_TEXT)",
            )
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                value,
            )
        }
        val accepted = try {
            editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (t: Throwable) {
            return ActionOutcome(false, "ACTION_SET_TEXT threw ${t.javaClass.simpleName}")
        }
        if (!accepted) {
            return ActionOutcome(false, "ACTION_SET_TEXT was rejected by '$identifier'")
        }

        // "Accepted" is not proof the text landed. Where the field exposes its
        // text, check the length (never the value) caught up.
        var landed: Boolean? = null
        waitUntil(AutomationConfig.Timeouts.FIELD_FOCUS) {
            landed = verifyLength(identifier, value.length)
            landed != false
        }
        if (landed == false) {
            return ActionOutcome(false, "'$identifier' accepted ACTION_SET_TEXT but does not hold the entered text")
        }
        val how = if (focusedByTap) "tapped to focus, then " else ""
        return ActionOutcome(true, "${how}entered via ACTION_SET_TEXT on '$identifier'")
    }

    /**
     * Confirms a field holds the expected number of characters. Only lengths are
     * compared, never values. Returns null when the node exposes no text at all,
     * which is the normal case for a wrapper node that implements setText itself.
     */
    fun verifyLength(identifier: String, expected: Int): Boolean? {
        val anchor = findNow(identifier) ?: return null
        val editable = AccessibilityNodeFinder.resolveEditable(anchor) ?: return null
        val length = AccessibilityNodeFinder.textLength(editable) ?: return null
        return length == expected
    }

    /**
     * Taps the control anchored by [identifier], falling back to [fallbackTexts]
     * if the identifier is absent, and finally to a gesture at the resolved
     * node's own bounds if it advertises no ACTION_CLICK.
     */
    fun tap(identifier: String, fallbackTexts: List<String> = emptyList()): ActionOutcome {
        val root = targetRoot()
            ?: return ActionOutcome(false, "the target application window is not readable")

        var anchor = AccessibilityNodeFinder.byIdentifier(root, identifier)
        var how = "identifier '$identifier'"
        if (anchor == null) {
            anchor = AccessibilityNodeFinder.byContentDescription(root, identifier)
            if (anchor != null) how = "content description '$identifier'"
        }
        if (anchor == null && fallbackTexts.isNotEmpty()) {
            anchor = AccessibilityNodeFinder.byText(root, fallbackTexts)
            if (anchor != null) how = "visible text fallback"
        }
        if (anchor == null) {
            return ActionOutcome(false, "no node matched the identifier '$identifier'")
        }

        val clickable = AccessibilityNodeFinder.resolveClickable(anchor)
        if (clickable != null && performClick(clickable)) {
            return ActionOutcome(true, "tapped via ACTION_CLICK, located by $how")
        }

        return if (tapNode(clickable ?: anchor)) {
            ActionOutcome(
                true,
                "tapped at the resolved node's own bounds centre (it advertised no " +
                    "ACTION_CLICK), located by $how",
            )
        } else {
            ActionOutcome(false, "found by $how but neither ACTION_CLICK nor a bounds tap worked")
        }
    }

    /**
     * Long-presses the control anchored by [identifier] through its
     * ACTION_LONG_CLICK (Flutter publishes an `onLongPress` handler as that
     * action). There is deliberately no coordinate fallback: a long press that
     * the node does not advertise is reported rather than guessed at.
     */
    fun longTap(identifier: String): ActionOutcome {
        val root = targetRoot()
            ?: return ActionOutcome(false, "the target application window is not readable")
        val anchor = AccessibilityNodeFinder.byIdentifier(root, identifier)
            ?: return ActionOutcome(false, "no node matched the identifier '$identifier'")
        val target = AccessibilityNodeFinder.resolveLongClickable(anchor)
            ?: return ActionOutcome(false, "'$identifier' advertises no ACTION_LONG_CLICK")
        val accepted = try {
            target.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        } catch (_: Throwable) {
            false
        }
        return if (accepted) {
            ActionOutcome(true, "long-pressed via ACTION_LONG_CLICK, located by identifier '$identifier'")
        } else {
            ActionOutcome(false, "ACTION_LONG_CLICK was rejected by '$identifier'")
        }
    }

    /**
     * Polls for a control and taps it as soon as it appears, up to [timeoutMs].
     * Unlike [tap] (one immediate attempt), this is for controls whose screen
     * cannot be confirmed by [waitForAny] first — [identifier] carries no
     * identifier of its own, so there is nothing else to wait on.
     */
    fun waitAndTap(identifier: String, fallbackTexts: List<String>, timeoutMs: Long): ActionOutcome {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            if (cancelled) return ActionOutcome(false, "cancelled")
            val outcome = tap(identifier, fallbackTexts)
            if (outcome.ok) return outcome
            if (SystemClock.elapsedRealtime() >= deadline) return outcome
            if (!sleep(AutomationConfig.POLL_INTERVAL_MS)) return ActionOutcome(false, "cancelled")
        }
    }

    /**
     * Taps [identifier] repeatedly (up to [maxTimes]), stopping at the first
     * miss. This is how a row that disappears or loses its identifier on
     * success (a deleted list row, a search result that switches to a
     * checkmark once added) gets processed N times without any "find the Nth
     * node" capability: each tap naturally lands on the next remaining match.
     * Returns the number of successful taps.
     */
    fun tapUpTo(
        identifier: String,
        fallbackTexts: List<String> = emptyList(),
        maxTimes: Int,
        settleMs: Long,
    ): Int {
        var successes = 0
        while (successes < maxTimes) {
            if (cancelled) return successes
            val outcome = tap(identifier, fallbackTexts)
            if (!outcome.ok) return successes
            successes++
            if (successes < maxTimes && !sleep(settleMs)) return successes
        }
        return successes
    }

    /**
     * Taps each of [identifiers] once, in order, tolerating individual misses
     * (no abort on failure). For rows that toggle in place rather than
     * disappearing, so each needs its own positional identifier rather than
     * relying on [tapUpTo]'s repeated-first-match trick. Returns the number of
     * successful taps.
     *
     * Each tap polls up to [perTapTimeoutMs] rather than checking once: the
     * screen these identifiers live on (e.g. a just-opened modal bottom sheet)
     * can still be mid entrance-animation when the first identifier is looked
     * up, and a single immediate miss must not be treated the same as the
     * identifier never appearing at all.
     */
    fun tapEach(
        identifiers: List<String>,
        settleMs: Long,
        perTapTimeoutMs: Long = AutomationConfig.Timeouts.DISCOVER_ADD,
    ): Int {
        var successes = 0
        for (identifier in identifiers) {
            if (cancelled) return successes
            if (waitAndTap(identifier, emptyList(), perTapTimeoutMs).ok) successes++
            if (!sleep(settleMs)) return successes
        }
        return successes
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean = try {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    } catch (_: Throwable) {
        false
    }

    /** [tap]'s resolution for a node already in hand: ACTION_CLICK, else a bounds tap. */
    private fun clickResolved(anchor: AccessibilityNodeInfo): Boolean {
        val clickable = AccessibilityNodeFinder.resolveClickable(anchor)
        if (clickable != null && performClick(clickable)) return true
        return tapNode(clickable ?: anchor)
    }

    /** Dispatches the system "back" action. See [AutomationAccessibilityService.pressBack]. */
    fun pressBack(): Boolean = service.pressBack()

    /**
     * Last resort tap. The coordinates come from the node this tool already
     * resolved from the accessibility tree, so they follow the layout — this is
     * not a hard coded screen position. It is still less reliable than
     * ACTION_CLICK: it can miss if the view scrolls between the bounds read and
     * the gesture, and it will hit whatever is on top if the node is covered.
     */
    private fun tapNode(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        try {
            node.getBoundsInScreen(bounds)
        } catch (_: Throwable) {
            return false
        }
        if (bounds.isEmpty) return false
        return service.tapAt(bounds.exactCenterX(), bounds.exactCenterY())
    }

    /**
     * Enters [digits] on a custom in-app numeric keypad, tapping `mpin_key_<d>`
     * per digit. Used when the MPIN screen has no text input.
     */
    fun tapKeypadDigits(digits: String): ActionOutcome {
        for ((index, ch) in digits.withIndex()) {
            if (cancelled) return ActionOutcome(false, "cancelled")
            if (!ch.isDigit()) {
                return ActionOutcome(false, "value contains a non digit at position ${index + 1}")
            }
            val outcome = tap("${AutomationConfig.Ids.MPIN_KEY_PREFIX}$ch")
            if (!outcome.ok) {
                return ActionOutcome(
                    false,
                    "could not tap keypad key ${index + 1} of ${digits.length}: ${outcome.detail}",
                )
            }
            if (!sleep(140L)) return ActionOutcome(false, "cancelled")
        }
        return ActionOutcome(true, "entered on the custom in-app keypad via mpin_key_<digit> taps")
    }

    /** True when the MPIN screen presents a custom keypad rather than a field. */
    fun hasCustomMpinKeypad(): Boolean =
        isPresent("${AutomationConfig.Ids.MPIN_KEY_PREFIX}1") ||
            isPresent("${AutomationConfig.Ids.MPIN_KEY_PREFIX}0")

    // ── Evidence ────────────────────────────────────────────────────────────

    private val screenshotDir: File
        get() {
            val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
            return File(File(base, "reports"), "screenshots")
        }

    /** Best effort. Returns the saved path, or null with a reason in [onUnavailable]. */
    fun screenshot(label: String, onUnavailable: (String) -> Unit): String? {
        val safeLabel = "${runId.take(8)}_$label".replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return when (val result = service.captureScreenshot(screenshotDir, safeLabel)) {
            is AutomationAccessibilityService.ShotResult.Saved -> result.path
            is AutomationAccessibilityService.ShotResult.Unavailable -> {
                onUnavailable(result.reason)
                null
            }
        }
    }

    fun deviceModel(): String = Build.MODEL ?: "unknown"

    fun deviceManufacturer(): String = Build.MANUFACTURER ?: "unknown"

    fun androidVersion(): String = Build.VERSION.RELEASE ?: "unknown"

    fun sdkInt(): Int = Build.VERSION.SDK_INT
}

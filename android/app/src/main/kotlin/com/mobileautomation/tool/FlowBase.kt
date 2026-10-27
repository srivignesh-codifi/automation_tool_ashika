package com.mobileautomation.tool

import com.mobileautomation.tool.AutomationEngine.WaitOutcome

/**
 * Bookkeeping shared by the test flows: the checklist, the step events, aborts,
 * evidence capture, and the "does every section finish loading" check.
 *
 * [LoginAutomationFlow] (Ashika) predates this class and keeps its own private
 * copies of the same helpers; [TradionAutomationFlow] is the first user.
 */
abstract class FlowBase(
    protected val engine: AutomationEngine,
    protected val steps: List<AutomationStepResult>,
) {

    protected lateinit var result: AutomationRunResult

    internal fun step(id: String): AutomationStepResult = steps.first { it.id == id }

    /**
     * Creates the run result and announces the checklist to the UI. Returns
     * what was found out about the target app so the caller can validate it.
     */
    protected fun startRun(testName: String): TargetAppLauncher.TargetInfo {
        val info = engine.launcher.inspect(engine.targetPackage)
        result = AutomationRunResult(
            runId = engine.runId,
            testName = testName,
            targetPackage = engine.targetPackage,
            targetMainActivity = info.mainActivity,
            targetVersionName = info.versionName,
            deviceModel = engine.deviceModel(),
            deviceManufacturer = engine.deviceManufacturer(),
            androidVersion = engine.androidVersion(),
            sdkInt = engine.sdkInt(),
            startedAtMs = System.currentTimeMillis(),
            steps = steps,
        )
        engine.emit(
            "automation_started",
            mapOf(
                "testName" to testName,
                "targetPackage" to engine.targetPackage,
                "totalSteps" to steps.size,
                "steps" to steps.map { mapOf("id" to it.id, "name" to it.name) },
            ),
        )
        return info
    }

    // ── Step bookkeeping ────────────────────────────────────────────────────

    /** The step in progress; a load check is recorded against it. */
    private var activeStep: AutomationStepResult? = null

    /** The run's paused time when each step began, to say in the step how long it was paused. */
    private val pausedAtBegin = HashMap<String, Long>()

    /** The step begun last: tells which module a skip belongs to. */
    protected var lastStarted: AutomationStepResult? = null

    internal fun begin(step: AutomationStepResult) {
        activeStep = step
        lastStarted = step
        pausedAtBegin[step.id] = engine.pausedMs()
        step.start()
        engine.emit(
            "step_started",
            mapOf(
                "stepId" to step.id,
                "stepIndex" to steps.indexOf(step),
                "name" to step.name,
            ),
        )
    }

    internal fun pass(step: AutomationStepResult, note: String? = null) {
        step.pass(note)
        emitStep(step)
    }

    /** Skips [step] on purpose and says which kind of skip it is: blocked, not applicable or not tested. */
    internal fun skipAs(step: AutomationStepResult, kind: String, note: String) {
        step.skipKind = kind
        val label = when (kind) {
            "blocked" -> "BLOCKED"
            "not_applicable" -> "NOT APPLICABLE"
            else -> "NOT TESTED"
        }
        skip(step, "$label: $note")
    }

    internal fun skip(step: AutomationStepResult, note: String) {
        step.skip(note)
        emitStep(step)
    }

    /**
     * Emits the terminal event for a step. Skipped steps ride on `step_passed`
     * with `status: "skipped"`, so the event vocabulary stays the six documented
     * names while the UI can still tell the two apart.
     */
    /** Called after each step has ended (passed, failed or skipped). */
    protected open fun afterStep(step: AutomationStepResult) {}

    /** Called once as the run's result is completed, whatever ended it. */
    protected open fun beforeComplete() {}

    protected fun emitStep(step: AutomationStepResult) {
        afterStep(step)
        pausedAtBegin.remove(step.id)?.let { before ->
            val paused = engine.pausedMs() - before
            if (paused >= 1_000L) {
                step.note = listOfNotNull(step.note, "paused by the tester for ${paused / 1000} s (its time includes the pause)")
                    .joinToString("; ")
            }
        }
        engine.emit(
            if (step.status == StepStatus.FAILED) "step_failed" else "step_passed",
            mapOf(
                "stepId" to step.id,
                "stepIndex" to steps.indexOf(step),
                "name" to step.name,
                "status" to step.status.wire,
                "durationMs" to step.durationMs,
                "note" to Redactor.apply(step.note),
                "reason" to Redactor.apply(step.failureReason),
            ),
        )
    }

    /**
     * Marks [step] failed but lets the run continue: used where one broken
     * tab or feature should not stop the remaining ones from being checked.
     * The run's verdict still ends FAILED.
     */
    internal fun failAndContinue(step: AutomationStepResult, reason: String) {
        step.fail(reason)
        emitStep(step)
        if (result.failureMessage == null) result.failureMessage = reason
        captureScreenshot("failed_${step.id}")
    }

    protected fun reasonFor(outcome: WaitOutcome, what: String, timeoutMs: Long): String = when (outcome) {
        is WaitOutcome.TimedOut -> buildString {
            append(what).append(" was not detected within ").append(timeoutMs / 1000)
            append(" seconds.")
            if (!outcome.windowReadable) {
                append(
                    " The target application's window was not readable at the end of the wait — " +
                        "it may have been closed, or another application may be in the foreground.",
                )
            }
            if (engine.promptStuck) {
                append(
                    " A notification permission prompt was on screen and stayed there after the " +
                        "tool clicked and tapped Allow — Android can ignore automated input on " +
                        "permission dialogs. Install the current build of the app, which skips " +
                        "this prompt when the tool launches it, or allow it by hand while the run waits.",
                )
            }
            if (outcome.visibleIdentifiers.isNotEmpty()) {
                append(" Identifiers visible when the wait expired: ")
                append(outcome.visibleIdentifiers.joinToString(", "))
                append('.')
            } else if (outcome.windowReadable) {
                append(
                    " No accessibility identifiers were visible at all, which usually means the " +
                        "installed build of the target app does not carry the Semantics " +
                        "identifiers this tool looks for.",
                )
            }
        }
        is WaitOutcome.TargetWindowLost -> "The target application stopped being the readable " +
            "foreground window while waiting for $what. The run was stopped because another " +
            "application appears to have taken over, or the target app closed."
        else -> "$what could not be confirmed."
    }

    protected fun abort(step: AutomationStepResult, reason: String): AutomationRunResult {
        step.fail(reason)
        emitStep(step)
        result.failureMessage = reason
        result.passed = false
        captureScreenshot("failed_${step.id}")
        return complete()
    }

    protected fun abort(
        outcome: WaitOutcome,
        step: AutomationStepResult,
        what: String,
        timeoutMs: Long,
        extra: String = "",
    ): AutomationRunResult = abort(step, reasonFor(outcome, what, timeoutMs) + extra)

    protected fun stopped(step: AutomationStepResult): AutomationRunResult {
        if (engine.skipping) {
            // The operator skipped the module: this step ends NOT TESTED and the run goes on (see skipCurrentModule).
            if (step.status == StepStatus.PENDING || step.status == StepStatus.RUNNING) {
                skipAs(step, "not_tested", "skipped by the tester")
            }
            return result
        }
        val reason = if (engine.serviceGone) {
            "The accessibility service was turned off while the run was in progress."
        } else {
            "The run was stopped by the operator."
        }
        step.skip(reason)
        emitStep(step)
        result.stopped = true
        result.passed = false
        result.failureMessage = reason
        return complete()
    }

    protected fun finish(): AutomationRunResult {
        result.passed = steps.none { it.status == StepStatus.FAILED } &&
            steps.none { it.status == StepStatus.PENDING || it.status == StepStatus.RUNNING }
        captureScreenshot("completed")
        return complete()
    }

    private fun complete(): AutomationRunResult {
        beforeComplete()
        StepApi.finalize(steps)
        result.finishedAtMs = System.currentTimeMillis()
        return result
    }

    /**
     * Evidence capture is best effort by design: a secure screen in the target
     * app, or an Android 10 device, means no image. That never changes the
     * verdict of the run.
     */
    internal fun captureScreenshot(label: String): String? {
        val path = engine.screenshot(label) { reason ->
            if (result.screenshotUnavailableReason == null) {
                result.screenshotUnavailableReason = reason
            }
        }
        if (path != null) result.screenshots[label] = path
        return path
    }

    // ── Load check ──────────────────────────────────────────────────────────

    protected data class LoadCheck(
        val settled: Boolean,
        val elapsedMs: Long,
        val pages: Int,
        val empty: Int,
        val errors: Int,
        val stillLoading: Int,
    )

    /**
     * Waits, within [budgetMs] in total, until no `ui_loading` marker is on
     * screen, then scrolls a screenful and repeats, so sections below the fold
     * — which Flutter only builds once they are near the viewport — are
     * checked too. Scrolls back to the top afterwards so the tab strip is
     * reachable again.
     *
     * [scrollPages] is how many screenfuls to check at most (1 = the visible
     * part only).
     */
    protected fun checkPageLoads(
        budgetMs: Long,
        scrollPages: Int = AutomationConfig.Timeouts.TAB_MAX_PAGES,
    ): LoadCheck {
        val ids = AutomationConfig.Ids
        val start = engine.clock()
        val deadline = start + budgetMs
        var pages = 1
        var empty = 0
        var errors = 0
        var settled = true
        var stillLoading = 0
        var scrolled = 0
        while (true) {
            val remaining = (deadline - engine.clock()).coerceAtLeast(0L)
            val clear = engine.waitUntil(remaining) { engine.countOf(ids.UI_LOADING) == 0 }
            empty = maxOf(empty, engine.countOf(ids.UI_EMPTY))
            errors = maxOf(errors, engine.countOf(ids.UI_ERROR))
            if (!clear) {
                settled = false
                stillLoading = engine.countOf(ids.UI_LOADING)
                break
            }
            if (engine.cancelled || pages >= scrollPages) break
            if (!engine.scrollPage(forward = true)) break
            scrolled++
            pages++
            // Sections scrolled into view start their own fetch on the next frame.
            if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) break
        }
        val elapsed = engine.clock() - start
        var back = 0
        while (back < scrolled + 2 && engine.scrollPage(forward = false)) back++
        engine.sleep(AutomationConfig.Timeouts.SETTLE)
        activeStep?.let { StepApi.recordLoad(it, settled, elapsed, pages, empty, errors, stillLoading) }
        return LoadCheck(settled, elapsed, pages, empty, errors, stillLoading)
    }

    /** Human readable verdict for a finished [LoadCheck], or null when it passed. */
    protected fun loadFailure(check: LoadCheck, budgetMs: Long, what: String = ""): String? {
        val where = if (check.pages > 1) " across ${check.pages} screenfuls" else ""
        val empties = if (check.empty > 0) "; ${check.empty} section(s) show no data" else ""
        val prefix = if (what.isEmpty()) "" else "$what: "
        return when {
            check.errors > 0 ->
                "${prefix}Showed the 'Something went wrong / Retry' screen (${check.errors} on screen) instead of data."
            !check.settled ->
                "${prefix}${check.stillLoading} section(s) were still loading after ${budgetMs / 1000} seconds$where$empties."
            else -> null
        }
    }

    protected fun loadSummary(check: LoadCheck): String {
        val seconds = "%.1f".format(check.elapsedMs / 1000.0)
        val where = if (check.pages > 1) " across ${check.pages} screenfuls" else ""
        val empties = if (check.empty > 0) "; ${check.empty} section(s) show no data" else ""
        return "every section loaded in $seconds s$where$empties"
    }

    // ── Navigation helpers ──────────────────────────────────────────────────

    /**
     * Presses Back until [identifier] leaves the screen, at most twice.
     *
     * Only ever call this with [identifier] on screen: on the app's home screen
     * Back exits Tradion (its exit dialog's `WillPopScope` returns true), and a
     * Back that finds nothing to close would take the app with it. So this
     * refuses to press anything while the thing to close is not there.
     */
    internal fun closeWithBack(identifier: String): Boolean {
        if (!engine.isPresent(identifier)) return false
        repeat(2) {
            if (!engine.pressBack()) return false
            if (engine.waitForGone(identifier, AutomationConfig.Timeouts.CONFIRM_RETRY)) return true
            if (engine.cancelled) return false
        }
        return false
    }
}

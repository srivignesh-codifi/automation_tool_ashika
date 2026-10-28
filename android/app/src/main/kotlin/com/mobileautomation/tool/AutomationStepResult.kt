package com.mobileautomation.tool

enum class StepStatus {
    PENDING,
    RUNNING,
    PASSED,
    FAILED,
    SKIPPED;

    /** Lower case form used on the platform channel and in reports. */
    val wire: String get() = name.lowercase()
}

/**
 * One line of the automation checklist.
 *
 * Note: this class never holds a credential. `failureReason` and `note` are
 * written from fixed strings and locator names only.
 */
class AutomationStepResult(
    val id: String,
    val name: String,
) {
    var status: StepStatus = StepStatus.PENDING
    var startedAtMs: Long? = null
    var finishedAtMs: Long? = null
    var failureReason: String? = null
    var note: String? = null

    /** A test case's own id ("WL-05"), what it expects, how serious a failure is, and why it was skipped. */
    var code: String? = null
    var expected: String? = null
    var severity: String? = null

    /** The area a step belongs to (an [AppModule] id), or null for the sign-in / sign-out steps every run has. */
    var module: String? = null

    /**
     * What a failed or blocked case turned out to be: `app_defect` (readable content contradicts the expectation),
     * `unconfirmed` (an expected label was not in readable text: needs a look at the screenshot), `automation`
     * (a locator or flow problem), `accessibility` (the screen exposes no readable content), `api_data` (the data
     * or service did not answer) or `test_data` (the account's state does not allow the case).
     */
    var cause: String? = null

    /** The UI hierarchy at the moment of failure: identifiers on screen and the readable texts. */
    var evidence: String? = null

    /** What the app needs so the case can be verified (for example a Semantics identifier). */
    var recommendation: String? = null

    /** Whether the case put back what it changed: "restored", "nothing changed" or "FAILED: ...". */
    var cleanup: String? = null

    /** "blocked", "not_applicable" or "not_tested" for a step that was skipped on purpose; null for a plain skip. */
    var skipKind: String? = null

    /** What kind of server interaction this step exercises; see [StepApi]. */
    var apiKind: String? = null
    var apiStatus: String? = null
    var apiDetail: String? = null

    /** The longest load time recorded for the step, and how many loads were checked. */
    var apiMs: Long? = null
    var apiChecks: Int = 0

    /**
     * The calls the target app reported as unauthorised (HTTP 401 / "Session Expired") while this step ran, and
     * what the run did about it. Kept when the step is run again after signing in, so the report still shows it.
     */
    var unauthorized: String? = null

    /** How many times this step was run again after the session ended part way through it. */
    var reruns: Int = 0

    val durationMs: Long?
        get() {
            val start = startedAtMs ?: return null
            val end = finishedAtMs ?: return null
            return end - start
        }

    fun start() {
        status = StepStatus.RUNNING
        startedAtMs = System.currentTimeMillis()
    }

    /** Puts the step back in the queue, to be run again from the start (after signing in again). */
    fun resetForRerun() {
        status = StepStatus.PENDING
        startedAtMs = null
        finishedAtMs = null
        failureReason = null
        note = null
        cause = null
        evidence = null
        recommendation = null
        skipKind = null
        apiKind = null
        apiStatus = null
        apiDetail = null
        apiMs = null
        apiChecks = 0
        reruns++
    }

    fun pass(note: String? = null) {
        status = StepStatus.PASSED
        this.note = note
        finishedAtMs = System.currentTimeMillis()
    }

    fun fail(reason: String) {
        status = StepStatus.FAILED
        failureReason = reason
        finishedAtMs = System.currentTimeMillis()
    }

    fun skip(note: String? = null) {
        status = StepStatus.SKIPPED
        this.note = note
        if (startedAtMs == null) startedAtMs = System.currentTimeMillis()
        finishedAtMs = System.currentTimeMillis()
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "status" to status.wire,
        "startedAtMs" to startedAtMs,
        "finishedAtMs" to finishedAtMs,
        "durationMs" to durationMs,
        "failureReason" to Redactor.apply(failureReason),
        "note" to Redactor.apply(note),
        "code" to code,
        "expected" to expected,
        "severity" to severity,
        "skipKind" to skipKind,
        "module" to module,
        "cause" to cause,
        "evidence" to Redactor.apply(evidence),
        "recommendation" to Redactor.apply(recommendation),
        "cleanup" to Redactor.apply(cleanup),
        "apiKind" to apiKind,
        "apiStatus" to apiStatus,
        "apiDetail" to Redactor.apply(apiDetail),
        "apiMs" to apiMs,
        "apiChecks" to apiChecks,
        "unauthorized" to Redactor.apply(unauthorized),
        "reruns" to reruns,
    )
}

/** Result of a whole automation run. Serialised straight into the reports. */
class AutomationRunResult(
    val runId: String,
    val testName: String,
    val targetPackage: String,
    val targetMainActivity: String?,
    val targetVersionName: String?,
    val deviceModel: String,
    val deviceManufacturer: String,
    val androidVersion: String,
    val sdkInt: Int,
    val startedAtMs: Long,
    val steps: List<AutomationStepResult>,
) {
    var finishedAtMs: Long? = null
    var passed: Boolean = false
    var stopped: Boolean = false
    var failureMessage: String? = null

    /** label -> absolute file path of a captured screenshot. */
    val screenshots: MutableMap<String, String> = LinkedHashMap()

    /** Why a screenshot could not be captured, when that happened. */
    var screenshotUnavailableReason: String? = null

    /** The modules this run covered and the ones the operator switched off. */
    var modulesRun: List<String> = emptyList()
    var modulesSkipped: List<String> = emptyList()

    /** Absolute path of the run's screen recording, when one was made. */
    var videoPath: String? = null

    /** When the recording began, so a step's start maps to a position in it. */
    var recordingStartedAtMs: Long? = null

    /** Why there is no video, or what the video does not cover. */
    var recordingNote: String? = null

    /**
     * Each time the target app's session ended mid-run: which step, which API answered 401, and whether the tool
     * signed in again and resumed. See [TradionAutomationFlow].
     */
    val sessionBreaks: MutableList<Map<String, Any?>> = mutableListOf()

    /** The market-data feed over the run (live market hours), or the BOD note; see [FeedMonitor]. */
    var feed: Map<String, Any?>? = null

    val durationMs: Long?
        get() = finishedAtMs?.let { it - startedAtMs }

    val completedCount: Int
        get() = steps.count { it.status == StepStatus.PASSED || it.status == StepStatus.SKIPPED }

    fun toMap(): Map<String, Any?> = mapOf(
        "runId" to runId,
        "testName" to testName,
        "targetPackage" to targetPackage,
        "targetMainActivity" to targetMainActivity,
        "targetVersionName" to targetVersionName,
        "deviceModel" to deviceModel,
        "deviceManufacturer" to deviceManufacturer,
        "androidVersion" to androidVersion,
        "sdkInt" to sdkInt,
        "startedAtMs" to startedAtMs,
        "finishedAtMs" to finishedAtMs,
        "durationMs" to durationMs,
        "passed" to passed,
        "stopped" to stopped,
        "failureMessage" to Redactor.apply(failureMessage),
        "totalSteps" to steps.size,
        "completedSteps" to completedCount,
        "steps" to steps.map { it.toMap() },
        "screenshots" to screenshots.toMap(),
        "screenshotUnavailableReason" to screenshotUnavailableReason,
        "modulesRun" to modulesRun,
        "modulesSkipped" to modulesSkipped,
        "videoPath" to videoPath,
        "recordingStartedAtMs" to recordingStartedAtMs,
        "recordingNote" to recordingNote,
        "sessionBreaks" to sessionBreaks.toList(),
        "feed" to feed,
    )
}

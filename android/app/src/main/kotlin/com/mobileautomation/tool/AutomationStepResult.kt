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
        "failureReason" to failureReason,
        "note" to note,
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
        "failureMessage" to failureMessage,
        "totalSteps" to steps.size,
        "completedSteps" to completedCount,
        "steps" to steps.map { it.toMap() },
        "screenshots" to screenshots.toMap(),
        "screenshotUnavailableReason" to screenshotUnavailableReason,
    )
}

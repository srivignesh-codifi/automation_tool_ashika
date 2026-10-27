package com.mobileautomation.tool

/**
 * The "API / data check" of a step, for the report.
 *
 * The tool cannot see a target app's network traffic, so this is inferred from
 * what the screen shows, and the report says so. Three kinds of step touch the
 * server:
 *
 * * **read**: a screen that loads data. Checked through the app's own loading,
 *   empty and error markers: finished in time, empty, slow, still loading, or
 *   showing its "Something went wrong" screen.
 * * **write**: an add, delete or save. The flows re-read the server's state
 *   afterwards (the item is still there, the count moved), so a passed step
 *   means the server confirmed it.
 * * **auth**: sign-in and sign-out; the next screen following is the proof.
 */
object StepApi {

    const val READ = "read"
    const val WRITE = "write"
    const val AUTH = "auth"

    const val OK = "ok"
    const val EMPTY = "empty"
    const val SLOW = "slow"
    const val TIMEOUT = "timeout"
    const val ERROR = "error"
    const val CONFIRMED = "confirmed"
    const val NOT_CONFIRMED = "not_confirmed"
    const val NOT_CHECKED = "not_checked"
    const val SKIPPED = "skipped"

    /** The server answered HTTP 401 / "Session Expired" while the step ran, as reported by the target app. */
    const val UNAUTHORIZED = "unauthorized"

    /** A read that took this long is flagged slow even though it finished. */
    const val SLOW_MS = 5_000L

    private val AUTH_IDS = setOf(
        "login_tapped", "password_submitted", "otp_submitted",
        "continue_tapped", "mpin_submitted", "logged_out", "biometric_confirmed",
    )
    private val WRITE_IDS = setOf(
        "watchlist_edit_saved", "watchlist_edit_restored",
        "watchlist_add_saved", "watchlist_scrips_trimmed",
    )
    private val READ_IDS = setOf(
        "home_displayed", "scrip_more_details_tabs",
        "watchlist_edit_verified", "watchlist_count_updated",
    )

    /** Which kind of server interaction the step with [id] exercises, if any. */
    fun kindOf(id: String): String? = when {
        id in AUTH_IDS -> AUTH
        id in WRITE_IDS || id.endsWith("_added") || id.endsWith("_deleted") -> WRITE
        id in READ_IDS || id.endsWith("_loaded") || id.endsWith("_results") -> READ
        else -> null
    }

    private fun rank(status: String?): Int = when (status) {
        OK -> 0
        EMPTY -> 1
        SLOW -> 2
        TIMEOUT -> 3
        ERROR -> 4
        UNAUTHORIZED -> 5
        else -> -1
    }

    /**
     * Records one load check on [step]. A step that checks several screens
     * (every tab of a section) keeps the worst result and the longest time.
     */
    fun recordLoad(
        step: AutomationStepResult,
        settled: Boolean,
        elapsedMs: Long,
        pages: Int,
        empty: Int,
        errors: Int,
        stillLoading: Int,
    ) {
        val status = when {
            errors > 0 -> ERROR
            !settled -> TIMEOUT
            elapsedMs >= SLOW_MS -> SLOW
            empty > 0 -> EMPTY
            else -> OK
        }
        val seconds = "%.1f".format(elapsedMs / 1000.0)
        val where = if (pages > 1) " across $pages screenfuls" else ""
        val detail = when (status) {
            ERROR -> "'Something went wrong' screen shown ($errors on screen)"
            TIMEOUT -> "$stillLoading section(s) still loading after $seconds s$where"
            SLOW -> "loaded, but took $seconds s$where"
            EMPTY -> "loaded in $seconds s$where; $empty section(s) show no data"
            else -> "loaded in $seconds s$where"
        }
        step.apiKind = step.apiKind ?: READ
        step.apiChecks += 1
        step.apiMs = maxOf(step.apiMs ?: 0L, elapsedMs)
        if (rank(status) >= rank(step.apiStatus)) {
            step.apiStatus = status
            step.apiDetail = detail
        }
    }

    /**
     * Fills in what the load checks did not: the kind of every step, and for a
     * write or sign-in step, or a read that was not load-checked, the outcome of
     * the step itself. Steps that never ran stay blank.
     */
    fun finalize(steps: List<AutomationStepResult>) {
        for (step in steps) {
            // A 401 outranks whatever the step's own checks found: it is the first thing the report must show.
            val unauthorized = step.unauthorized
            if (unauthorized != null) {
                val after = step.apiDetail?.let { "; run again after signing in: $it" }.orEmpty()
                step.apiKind = step.apiKind ?: kindOf(step.id) ?: READ
                step.apiStatus = UNAUTHORIZED
                step.apiDetail = "HTTP 401 / session expired: $unauthorized$after"
                continue
            }
            step.apiKind = step.apiKind ?: kindOf(step.id)
            val kind = step.apiKind ?: continue
            if (step.apiStatus != null) {
                if (step.apiChecks > 1) step.apiDetail = "${step.apiChecks} screens checked; ${step.apiDetail}"
                continue
            }
            when (step.status) {
                StepStatus.PASSED -> {
                    step.apiStatus = if (kind == WRITE) CONFIRMED else OK
                    step.apiDetail = when (kind) {
                        WRITE -> "the change was re-read from the server and matched"
                        AUTH -> "the next screen followed"
                        else -> "the screen was reached"
                    }
                }
                StepStatus.FAILED -> {
                    step.apiStatus = when (kind) {
                        WRITE -> NOT_CONFIRMED
                        AUTH -> ERROR
                        else -> NOT_CHECKED
                    }
                    step.apiDetail = when (kind) {
                        WRITE -> "the server's state did not match the change"
                        AUTH -> "the next screen did not follow"
                        else -> "the step failed before the data could be checked"
                    }
                }
                StepStatus.SKIPPED -> {
                    step.apiStatus = SKIPPED
                    step.apiDetail = "step skipped"
                }
                else -> Unit
            }
        }
    }
}

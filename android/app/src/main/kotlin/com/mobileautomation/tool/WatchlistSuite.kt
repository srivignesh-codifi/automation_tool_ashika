package com.mobileautomation.tool

import android.os.SystemClock
import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T

/**
 * The Watchlist module of the Tradion regression pass, as a short list of independent test cases.
 *
 * Rules it keeps (they are the point of the rewrite):
 *
 * * **Each scenario runs once, on one representative instrument.** No loops over symbols, exchanges or tabs.
 * * **A normal click or navigation must answer within [UI] (3 s).** Data that comes from the server (rows, search
 *   results, a tab's content) gets [LOAD] (10 s) and the chart's WebView [Timeouts.CHART_LOAD] (15 s); the time a
 *   slow response took is recorded.
 * * **Every case is independent.** A case ends PASS, FAIL, BLOCKED (a prerequisite is missing), NOT APPLICABLE (the
 *   feature does not exist for this instrument) or NOT TESTED (deliberately outside this pass), and then the app is
 *   brought back to the Watchlist landing screen, using only a close control or a Back whose own marker was just
 *   seen. One failure never cascades into the next case.
 * * **Nothing is ordered and nothing the user owns is deleted.** BUY and SELL only open the order pad, which is left
 *   with Back; the Alert page is never submitted; the Edit cases delete only an instrument the tool added itself;
 *   a renamed watchlist and the display settings are put back.
 */
class WatchlistSuite(
    private val flow: FlowBase,
    private val engine: AutomationEngine,
    private val modules: ModuleSelection,
    private val stopped: (AutomationStepResult) -> AutomationRunResult,
    private val sessionLost: (AutomationStepResult) -> AutomationRunResult?,
) {

    class Case(
        val code: String,
        val id: String,
        val module: String,
        val name: String,
        val expected: String,
        val severity: String,
    ) {
        fun toStep(): AutomationStepResult = AutomationStepResult(id, name).also {
            it.code = code
            it.expected = expected
            it.severity = severity
        }
    }

    companion object {
        private fun c(code: String, id: String, module: String, name: String, expected: String, severity: String) =
            Case(code, id, module, name, expected, severity)

        val CASES: List<Case> = listOf(
            // ── Watchlist: landing, switching, predefined, ticker ──────────────────────────────────
            c("WL-01", "wl_landing_loaded", "watchlist", "Watchlist landing screen loads",
                "The selected user watchlist shows its instruments with name, exchange, LTP and change, and the count reads n / 50.", "Critical"),
            c("WL-02", "wl_switch_watchlist", "watchlist", "Switch between two user watchlists",
                "Tapping another user watchlist tab shows that watchlist's instruments (or its empty state).", "High"),
            c("WL-03", "wl_predefined_lists", "watchlist", "Predefined lists load and switch",
                "Predefined mode shows predefined instruments and a count; a second predefined tab shows different instruments; Watchlist mode comes back.", "High"),
            c("WL-04", "wl_index_panel", "watchlist", "Top indices ticker and the funds panel",
                "The header shows the pinned indices; its arrow opens a panel with the indices and Funds / Equity; Close returns to the list.", "Medium"),
            // ── Instrument actions ─────────────────────────────────────────────────────────────────
            c("WL-05", "wl_instrument_sheet", "watchlist", "Instrument action sheet",
                "Tapping an equity opens a sheet with its name, exchange, LTP, Chart / Alert / Op.Chain / More Details, market depth and BUY / SELL.", "High"),
            c("WL-06", "wl_chart", "watchlist", "Chart WebView renders",
                "The chart opens and its WebView reports ready within 15 s.", "High"),
            c("WL-07", "wl_alert_page", "watchlist", "New Alert page opens prefilled",
                "The Alert page shows the instrument, takes a name, offers a value and a Create button (nothing is created).", "Medium"),
            c("WL-08", "wl_alert_condition", "watchlist", "Alert condition can be changed",
                "The condition dropdown opens, offers another condition and shows it once picked.", "Low"),
            c("WL-09", "wl_option_chain", "watchlist", "Option chain opens with data",
                "Op.Chain opens the option chain and its table loads (not applicable where the instrument has none).", "Medium"),
            c("WL-10", "wl_buy_sell", "watchlist", "BUY and SELL open the order pad",
                "BUY and SELL each open the order pad, which is left with Back; nothing is placed.", "High"),
            c("WL-11", "wl_more_market_depth", "watchlist", "More Details › Market Depth",
                "Bid / Offer ladder with totals, Open / High / Low / Close, Today's Low / High and BUY / SELL.", "Medium"),
            c("WL-12", "wl_more_technicals", "watchlist", "More Details › Technicals",
                "EMA & SMA summary with bullish / bearish counts; the EMA / SMA toggle works.", "Medium"),
            c("WL-13", "wl_more_fundamentals", "watchlist", "More Details › Fundamentals",
                "Stats (market cap, PE, PB, face value, sector PE, PEG, EPS, dividend yield) and the share-holding chart load.", "Medium"),
            c("WL-14", "wl_more_info", "watchlist", "More Details › Info",
                "Board lot, instrument name, precision, tick size, exchange, symbol, trading symbol, company name and ISIN match the instrument opened.", "Medium"),
            // ── Predefined ─────────────────────────────────────────────────────────────────────────
            c("WL-15", "wl_predefined_instrument", "watchlist", "Predefined instrument opens its sheet",
                "Tapping a predefined instrument opens its sheet with Chart / Alert and BUY / SELL; the list is read-only.", "Medium"),
            c("WL-16", "wl_predefined_search", "watchlist", "Search within a Predefined list",
                "Typing in 'Search within watchlist' narrows the predefined instruments.", "Low"),
            // ── Search and add ─────────────────────────────────────────────────────────────────────
            c("WL-20", "wl_search_results", "watchlist_search", "Search finds INFY (All)",
                "Results load with exchange badge, symbol, description and an add (+) or green tick for each; the four categories exist.", "Critical"),
            c("WL-21", "wl_search_fno", "watchlist_search", "Search category F&O (TCS)",
                "F&O results (NFO / BFO) for TCS load with add / tick icons.", "Medium"),
            c("WL-22", "wl_search_commodity", "watchlist_search", "Search category Commodity (CRU)",
                "Commodity results (MCX) for CRU load with add / tick icons.", "Medium"),
            c("WL-23", "wl_search_stocks", "watchlist_search", "Search category Stocks (INFY)",
                "Equity results (NSE / BSE) for INFY load with add / tick icons.", "Medium"),
            c("WL-24", "wl_search_add_remove", "watchlist_search", "Add / remove with the green tick",
                "An instrument already in the watchlist shows a tick: tapping it removes it (tick becomes +), tapping + adds it back (tick again) and the watchlist shows it.", "Critical"),
            c("WL-26", "wl_search_segment_types", "watchlist_search", "One of each type per segment",
                "An -EQ stock, a futures, call and put contract (F&O) and a commodity futures contract can each be added from search and show in the watchlist; they are removed again.", "High"),
            c("WL-25", "wl_capacity_limit", "watchlist_search", "50-instrument limit",
                "On a full watchlist (50 / 50) an add is refused.", "Low"),
            // ── Edit watchlist ─────────────────────────────────────────────────────────────────────
            c("WL-30", "wl_edit_rename", "watchlist_edit", "Edit › rename and restore",
                "A temporary name saved on the Edit screen shows on the watchlist's tab; the original name is then restored.", "High"),
            c("WL-31", "wl_edit_reorder", "watchlist_edit", "Edit › reorder a test instrument",
                "A test instrument added by this run moves one place and the new order shows on the watchlist.", "Medium"),
            c("WL-32", "wl_edit_delete", "watchlist_edit", "Edit › delete the test instrument",
                "The test instrument is deleted on the Edit screen and is gone from the watchlist after Save; the count drops by one.", "High"),
            // ── Manage Watchlist (settings) ────────────────────────────────────────────────────────
            c("WL-40", "wl_settings_view", "watchlist_settings", "Manage Watchlist › view, tile and change basis",
                "Depth, Grid and Open Price can be chosen and Applied (the list turns into a grid, the choices persist); the original choices are restored.", "Medium"),
            c("WL-41", "wl_settings_filter_sort", "watchlist_settings", "Manage Watchlist › sort, filter and Clear",
                "Alphabetical sort and the INDEX filter change the list; Clear restores the default order.", "Medium"),
            c("WL-90", "wl_state_restored", "watchlist", "Everything the Watchlist cases changed is back",
                "The working watchlist has its original name, instrument count and tile layout again.", "High"),
        )

        fun steps(): List<AutomationStepResult> = CASES.map { it.toStep() }

        const val STATE_ID = "wl_state_restored"

        fun idsOf(module: String): List<String> = CASES.filter { it.module == module && it.id != STATE_ID }.map { it.id }

        /** Every step of the suite, for a run that skips the whole Watchlist. */
        val ALL_IDS: List<String> = CASES.map { it.id }

        private val EXCHANGES = setOf("NSE", "BSE", "NFO", "BFO", "MCX", "CDS", "INDICES")
        private val PRICE = Regex("""^\d[\d,]*\.\d{2}$""")
        private val COUNT = Regex("""(\d+)\s*/\s*(\d+)""")
        private val TAB_POSITION = Regex("""Tab \d+ of \d+""", RegexOption.IGNORE_CASE)
    }

    // ── Time limits ────────────────────────────────────────────────────────────────────────────

    private val UI = Timeouts.UI_RESPONSE
    private val RETRY = Timeouts.UI_RETRY
    private val LOAD = Timeouts.DATA_LOAD

    // ── Case outcomes ──────────────────────────────────────────────────────────────────────────

    private enum class Kind { FAIL, BLOCKED, NOT_APPLICABLE, NOT_TESTED }

    /** What a failure or block turned out to be; the report groups by it (see [AutomationStepResult.cause]). */
    private object Cause {
        const val APP = "app_defect"
        const val UNCONFIRMED = "unconfirmed"
        const val AUTOMATION = "automation"
        const val ACCESSIBILITY = "accessibility"
        const val API = "api_data"
        const val TEST_DATA = "test_data"
    }

    private class CaseEnd(
        val kind: Kind,
        message: String,
        val category: String? = null,
        val evidence: String? = null,
        val recommendation: String? = null,
    ) : RuntimeException(message, null, false, false)

    private class Stopped : RuntimeException(null, null, false, false)

    /** The UI hierarchy right now: what is on screen by identifier, and what is readable on it. */
    private fun snapshot(): String {
        val ids = engine.visibleIdentifiers()
        val texts = engine.screenTexts(15)
        return "identifiers: ${ids.take(30).joinToString(", ").ifEmpty { "none" }}; " +
            "readable text: ${if (texts.isEmpty()) "none" else texts.joinToString(" | ")}"
    }

    /**
     * A first guess at the class of a failure from what the tool was doing, for the many failures that are an action
     * or a wait running out. A case that has evidence of an application defect says so explicitly.
     */
    private fun classify(message: String): String = when {
        Regex("could not be (tapped|typed|picked|performed)|could not (tap|type|select)|Could not (tap|type|select)|Long-pressing|no node matched|did not take a value|Tapping '", RegexOption.IGNORE_CASE)
            .containsMatchIn(message) -> Cause.AUTOMATION
        Regex("still loading|did not load|results took|did not (answer|return)|within \\d+ s", RegexOption.IGNORE_CASE)
            .containsMatchIn(message) && message.contains("load", ignoreCase = true) -> Cause.API
        else -> Cause.UNCONFIRMED
    }

    /**
     * Ends the case as FAIL. The screenshot and the UI hierarchy are taken now, before any clean-up moves the app off
     * the failing screen.
     */
    private fun fail(message: String, cause: String? = null, recommend: String? = null): Nothing {
        var evidence: String? = null
        if (!engine.cancelled) {
            evidence = "screen: ${screenName()}; " + snapshot()
            evidenceShot("failure")
        }
        throw CaseEnd(Kind.FAIL, message, cause ?: classify(message), evidence, recommend)
    }

    private fun blocked(message: String, cause: String = Cause.TEST_DATA, recommend: String? = null): Nothing =
        throw CaseEnd(Kind.BLOCKED, message, cause, null, recommend)

    /**
     * The screen is open (its own identifier was confirmed) but exposes nothing readable, so its content cannot be
     * verified. That is an accessibility limitation of the build, not evidence of a defect: BLOCKED, with the screenshot
     * and hierarchy as evidence and what the app would need to publish.
     */
    private fun unverifiable(what: String, recommend: String): Nothing {
        var evidence: String? = null
        if (!engine.cancelled) {
            evidence = "screen: ${screenName()}; " + snapshot()
            evidenceShot("unreadable")
        }
        throw CaseEnd(
            Kind.BLOCKED,
            "Could not verify $what: the accessibility tree returned no readable text for it, so this is not reported as " +
                "a defect. The screenshot shows what was on screen.",
            Cause.ACCESSIBILITY, evidence, recommend,
        )
    }

    private fun notApplicable(message: String): Nothing = throw CaseEnd(Kind.NOT_APPLICABLE, message)
    private fun notTested(message: String): Nothing = throw CaseEnd(Kind.NOT_TESTED, message)

    private val notes = mutableListOf<String>()
    private fun note(text: String) {
        notes += text
    }

    private fun alive() {
        if (engine.cancelled) throw Stopped()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val reached = engine.waitUntil(timeoutMs, condition)
        alive()
        return reached
    }

    private fun pause(ms: Long) {
        engine.sleep(ms)
        alive()
    }

    // ── State shared between cases ─────────────────────────────────────────────────────────────

    private val tabIds = (0 until Timeouts.MAX_WATCHLIST_TABS).map { "${T.WATCHLIST_TAB_PREFIX}$it" }
    private val predefinedTabIds = (0 until Timeouts.MAX_WATCHLIST_TABS).map { "${T.PREDEFINED_TAB_PREFIX}$it" }

    /** The user watchlist tabs this account has (the last header tab, Recently Viewed, is not one of them). */
    private var editableTabs: List<String> = emptyList()
    private var workTab: String? = null
    private var capacity = 50
    private var startCount: Int? = null

    /** The working watchlist as the run found it, compared again at the end (see WL-90). */
    private var initialName: String? = null
    private var initialGrid: Boolean? = null

    /** Row count of each user tab seen by WL-02, to find a watchlist with enough instruments to check sorting. */
    private val tabRows = mutableMapOf<String, Int>()

    /** The tab the Manage Watchlist cases work on, when it is not the usual working tab. */
    private var settingsTab: String? = null

    private class Rep(val name: String, val exchange: String)

    private var rep: Rep? = null

    /** The instrument this run added itself for the Edit cases. */
    private var testScrip: Rep? = null

    private var current: AutomationStepResult? = null

    /** What the running case did about putting back what it changed; recorded on its step. */
    private var cleanup: String? = null
    private var currentCase: Case? = null

    // ── Reading the screen ─────────────────────────────────────────────────────────────────────

    private fun tokens(label: String): List<String> = label.split(Regex("""\s+""")).filter { it.isNotEmpty() }

    private fun exchangeOf(label: String): String? = tokens(label).firstOrNull { it.uppercase() in EXCHANGES }?.uppercase()

    private fun nameOf(label: String): String? =
        tokens(label).firstOrNull { it.uppercase() !in EXCHANGES && it.any { ch -> ch.isLetter() } }

    private fun rowLabels(): List<String> = engine.groupedLabelsOf(T.WATCHLIST_SCRIP_ROW)

    private fun readCount(): Pair<Int, Int>? =
        engine.labelsOf(T.WATCHLIST_SCRIP_COUNT).firstNotNullOfOrNull {
            COUNT.find(it)?.let { m -> m.groupValues[1].toInt() to m.groupValues[2].toInt() }
        }

    private fun texts(limit: Int = 150): List<String> = engine.screenTexts(limit)

    /** Everything readable on screen: the visible texts plus the labels under [container], one line each. */
    private fun readScreen(container: String? = null, limit: Int = 150): List<String> {
        val out = LinkedHashSet<String>()
        out += engine.screenTexts(limit)
        if (container != null) {
            for (label in engine.labelsOf(container)) {
                label.lines().map { it.trim() }.filter { it.isNotEmpty() && it.length <= 90 }.forEach { out += it }
            }
        }
        return out.toList()
    }

    /**
     * Waits (within the UI budget) until [container] has readable content other than [chrome] (tab names and the
     * like), then returns what is readable. When nothing readable ever appears the case is BLOCKED as unverifiable.
     */
    private fun readContent(
        container: String?,
        what: String,
        recommend: String,
        chrome: Set<String> = emptySet(),
        scoped: Boolean = false,
    ): List<String> {
        // Scoped: only what lies under [container] counts (a tab body, not the header and tabs around it).
        fun read(): List<String> =
            if (scoped && container != null) {
                engine.labelsOf(container).flatMap { it.lines() }.map { it.trim() }
                    .filter { it.isNotEmpty() && it.length <= 90 && !TAB_POSITION.matches(it) }.distinct()
            } else readScreen(container)
        var all = read()
        val readable = waitUntil(UI) {
            all = read()
            all.count { it !in chrome } >= 2
        }
        if (!readable) unverifiable(what, recommend)
        return all
    }

    private fun tabName(label: String): String =
        label.lines().map { it.trim() }.filter { it.isNotEmpty() && !TAB_POSITION.matches(it) }.joinToString(" ")

    /** The part of an instrument name that every screen shows: 'INFY' for 'INFY-EQ'. */
    private fun base(r: Rep): String = r.name.substringBefore('-')

    private fun hasText(texts: List<String>, wanted: String): Boolean =
        texts.any { it.contains(wanted, ignoreCase = true) }

    private fun missing(texts: List<String>, wanted: List<String>): List<String> =
        wanted.filterNot { hasText(texts, it) }

    /** What screen is showing, by its own identifier, for naming evidence. */
    private fun screenName(): String {
        val known = listOf(
            T.ORDER_WINDOW_SCREEN to "order_pad", T.ALERT_SCREEN to "new_alert", T.CHART_SCREEN to "chart",
            T.MORE_INFO_SCREEN to "more_details", T.OPTION_CHAIN_SCREEN to "option_chain", T.SEARCH_SCREEN to "search",
            T.WATCHLIST_FILTER_SCREEN to "manage_watchlist", T.REORDER_SCREEN to "edit_watchlist",
            T.SCRIP_DETAILS_SHEET to "instrument_sheet", T.INDEX_DETAILS_SHEET to "index_sheet",
            T.DASHBOARD_INDEX_CLOSE to "index_panel", T.WATCHLIST_SCREEN to "watchlist",
        )
        return known.firstOrNull { engine.isPresent(it.first) }?.second ?: "unknown_screen"
    }

    private var evidenceCount = 0

    /**
     * Failure evidence: taken at the moment of failure, named `<case>_<kind>_<n>_<screen>` so no case can reuse
     * another's picture and a later clean-up cannot overwrite it. The screen name is read from the live screen, so the
     * file name says what the picture shows.
     */
    private fun evidenceShot(kind: String): String? {
        val case = currentCase ?: return null
        evidenceCount++
        return flow.captureScreenshot("${case.code}_${kind}_$evidenceCount${"_"}${screenName()}")
    }

    private fun shot(label: String): String? {
        val case = currentCase ?: return null
        return flow.captureScreenshot("${case.code}_$label")
    }

    /** Records how long a response took on the case's own step, so the report can list the slow ones. */
    private fun timing(startedAtMs: Long, what: String, slowMs: Long = 5_000L) {
        val took = SystemClock.elapsedRealtime() - startedAtMs
        val step = current ?: return
        step.apiKind = step.apiKind ?: StepApi.READ
        step.apiMs = maxOf(step.apiMs ?: 0L, took)
        step.apiChecks += 1
        step.apiStatus = if (took >= slowMs) StepApi.SLOW else StepApi.OK
        step.apiDetail = "$what in ${"%.1f".format(took / 1000.0)} s"
        note("$what in ${"%.1f".format(took / 1000.0)} s")
    }

    // ── Running a case ─────────────────────────────────────────────────────────────────────────

    private fun case(id: String): Case = CASES.first { it.id == id }

    /**
     * Runs [body] as the step of case [id] and records how it ended. With [recoverAfter] the app is then taken back
     * to the Watchlist landing screen. Returns a result only when the run has to end (cancelled, or the session
     * is gone).
     */
    private fun runCase(id: String, recoverAfter: Boolean = true, body: () -> Unit): AutomationRunResult? {
        val c = case(id)
        val step = flow.step(id)
        if (step.status != StepStatus.PENDING) return null
        current = step
        currentCase = c
        notes.clear()
        flow.begin(step)
        var end: CaseEnd? = null
        cleanup = null
        try {
            body()
        } catch (e: CaseEnd) {
            end = e
        } catch (_: Stopped) {
            return stopped(step)
        } catch (e: Exception) {
            // A defect in the automation itself must cost one case, not the whole run, and say where it happened.
            val at = e.stackTrace.firstOrNull { it.className.startsWith("com.mobileautomation") }
                ?.let { " (at ${it.methodName}, line ${it.lineNumber})" }.orEmpty()
            end = CaseEnd(
                Kind.FAIL,
                "Automation error: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}$at",
                Cause.AUTOMATION, if (engine.cancelled) null else "screen: ${screenName()}; " + snapshot(),
            )
        }
        if (engine.cancelled) return stopped(step)
        val detail = notes.joinToString("; ")
        step.cleanup = cleanup
        if (end != null && (end.kind == Kind.FAIL || end.kind == Kind.BLOCKED)) {
            step.cause = end.category
            step.evidence = end.evidence
            step.recommendation = end.recommendation
        }
        val message = end?.message.orEmpty()
        when (end?.kind) {
            null -> flow.pass(step, detail.ifEmpty { null })
            Kind.FAIL -> flow.failAndContinue(step, message + if (detail.isEmpty()) "" else " (before that: $detail)")
            Kind.BLOCKED -> flow.skipAs(step, "blocked", message)
            Kind.NOT_APPLICABLE -> flow.skipAs(step, "not_applicable", message)
            Kind.NOT_TESTED -> flow.skipAs(step, "not_tested", message)
        }
        if (recoverAfter) {
            try {
                recover()
            } catch (_: Stopped) {
                return stopped(step)
            }
        }
        return sessionLost(step)
    }

    // ── Getting back to the Watchlist ──────────────────────────────────────────────────────────

    private val overlays = listOf(
        T.ORDER_WINDOW_SCREEN, T.ALERT_SCREEN, T.CHART_SCREEN, T.MORE_INFO_SCREEN, T.OPTION_CHAIN_SCREEN,
        T.SEARCH_RESULT_SHEET, T.SEARCH_SCREEN, T.WATCHLIST_FILTER_SCREEN, T.REORDER_SCREEN,
        T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET,
    )

    /**
     * Closes whatever is open on top of the Watchlist and leaves it in Watchlist mode on the landing screen. Back
     * is only ever pressed for an overlay whose own marker was just seen (on the app's home screen Back would
     * leave the app). True when the landing screen is showing with nothing on it.
     */
    private fun recover(): Boolean {
        repeat(8) {
            alive()
            if (engine.isPresent(T.DASHBOARD_INDEX_CLOSE)) {
                engine.tap(T.DASHBOARD_INDEX_CLOSE)
                engine.waitForGone(T.DASHBOARD_INDEX_CLOSE, UI)
                return@repeat
            }
            val overlay = engine.firstPresent(overlays)
            if (overlay != null) {
                when (overlay) {
                    T.REORDER_SCREEN -> escapeEdit()
                    // Clear leaves this screen without the "save changes?" question that Back raises.
                    T.WATCHLIST_FILTER_SCREEN -> {
                        engine.tap(T.WATCHLIST_FILTER_RESET_BUTTON)
                        if (!engine.waitForGone(T.WATCHLIST_FILTER_SCREEN, UI)) flow.closeWithBack(overlay)
                    }
                    else -> flow.closeWithBack(overlay)
                }
                return@repeat
            }
            if (engine.firstPresent(predefinedTabIds) != null && engine.isPresent(T.WATCHLIST_MODE_WATCHLIST)) {
                engine.tap(T.WATCHLIST_MODE_WATCHLIST)
                waitUntil(UI) { engine.firstPresent(predefinedTabIds) == null }
                return@repeat
            }
            if (engine.isPresent(T.WATCHLIST_SCREEN)) return true
            val back = engine.tapAndConfirm(T.BOTTOMNAV_WATCHLIST_TAB, listOf(T.WATCHLIST_SCREEN), UI, retryAfterMs = RETRY)
            alive()
            if (!back.ok) return false
        }
        return engine.isPresent(T.WATCHLIST_SCREEN) && engine.firstPresent(overlays) == null
    }

    private fun ensureLanding() {
        if (!recover()) blocked("The app could not be brought back to the Watchlist screen: ${where()}")
    }

    private fun where(): String =
        "identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}"

    /** Leaves an Edit screen that is still open; Discard is tapped when Back asks "save changes?". */
    private fun escapeEdit(): Boolean {
        repeat(3) {
            if (!engine.isPresent(T.REORDER_SCREEN)) return true
            if (!engine.pressBack()) return false
            when (val found = engine.waitForAny(listOf(T.REORDER_DISCARD_BUTTON, T.WATCHLIST_SCREEN), RETRY)) {
                is AutomationEngine.WaitOutcome.Found -> {
                    if (found.identifier == T.REORDER_DISCARD_BUTTON && !engine.tap(T.REORDER_DISCARD_BUTTON).ok) return false
                    if (engine.waitForGone(T.REORDER_SCREEN, UI)) return true
                }
                is AutomationEngine.WaitOutcome.Cancelled -> return false
                else -> Unit
            }
        }
        return engine.waitForGone(T.REORDER_SCREEN, UI)
    }

    // ── Landing, tabs and modes ────────────────────────────────────────────────────────────────

    /**
     * Taps once and waits for the next screen. Never taps again: a second tap on a page that was merely slow to appear
     * is what opens it twice. Only a tap made while the app was still animating can be lost, and the callers wait for
     * the previous screen to settle before they tap.
     */
    private fun tapOpen(id: String, expect: List<String>, what: String, timeoutMs: Long = UI) {
        val outcome = engine.tapAndConfirm(id, expect, timeoutMs, retryAfterMs = timeoutMs)
        alive()
        if (!outcome.ok) fail("$what: ${outcome.detail}")
    }

    private fun rowsOrEmpty(): Boolean = engine.isPresent(T.WATCHLIST_SCRIP_ROW) || engine.isPresent(T.WATCHLIST_EMPTY_STATE)

    /** Opens the Watchlist tab, puts it in Watchlist mode, learns the tabs and selects one to work on. */
    private fun establishLanding() {
        if (!recover()) {
            tapOpen(T.BOTTOMNAV_WATCHLIST_TAB, listOf(T.WATCHLIST_SCREEN), "The Watchlist tab did not open", LOAD)
        }
        if (workTab != null && engine.isPresent(workTab!!)) return
        // Learn the tabs by scrolling the strip once.
        var count = 0
        for (id in tabIds) {
            if (!engine.revealInList(id, tabIds)) break
            count++
        }
        alive()
        if (count == 0) fail("No watchlist tab was found; ${where()}")
        editableTabs = tabIds.take((count - 1).coerceAtLeast(0))
        if (editableTabs.isEmpty()) blocked("The account has no user-created watchlist (only Recently Viewed).")
        val target = engine.firstPresent(editableTabs)
            ?: editableTabs.firstOrNull { engine.revealInList(it, tabIds) }
            ?: fail("No user watchlist tab could be reached; ${where()}")
        selectTab(target)
        workTab = target
        captureBaseline()
    }

    private fun captureBaseline() {
        if (initialName != null) return
        initialName = tabLabel()
        initialGrid = engine.isPresent(T.WATCHLIST_GRID_VIEW)
        readCount()?.let {
            startCount = it.first
            capacity = it.second
        }
    }

    private fun selectTab(id: String) {
        engine.revealInList(id, tabIds)
        val tap = engine.tap(id)
        if (!tap.ok) fail("The watchlist tab '$id' could not be tapped: ${tap.detail}")
        pause(Timeouts.SETTLE)
        if (!waitUntil(LOAD) { rowsOrEmpty() }) fail("After selecting '$id' neither instruments nor the empty state appeared within ${LOAD / 1000} s")
    }

    private fun needWork(): String = workTab ?: blocked("No user watchlist was selected to work on (see WL-01).")

    /** Makes sure the working watchlist is the selected one; every dependent scenario starts here. */
    /** Makes sure the working watchlist is the selected one; every dependent scenario starts here. */
    private fun toWorkTab() {
        val tab = needWork()
        if (!engine.isPresent(tab) || !engine.isSelected(tab)) selectTab(tab)
    }

    // ── The representative instrument ──────────────────────────────────────────────────────────

    /** The part of a name every layout shows: 'INFY' for 'INFY-EQ' (a Grid card drops the suffix). */
    private fun baseName(name: String?): String = name.orEmpty().substringBefore('-')

    /** Whether a watchlist / search row is this instrument, in either layout. */
    private fun sameScrip(label: String, name: String, exchange: String?): Boolean =
        baseName(nameOf(label)) == baseName(name) && (exchange.isNullOrEmpty() || exchangeOf(label) == exchange)

    private val preferredEquities = listOf("INFY", "TCS", "RELIANCE", "HDFCBANK", "SBIN", "ICICIBANK")

    private fun findRow(r: Rep): Int = rowLabels().indexOfFirst { sameScrip(it, r.name, r.exchange) }

    private fun needRep(): Rep {
        // Once chosen it stays the representative; the rows can only be read while the Watchlist is showing
        // (More Details cases run with their own screen open).
        rep?.let { if (!engine.isPresent(T.WATCHLIST_SCREEN) || findRow(it) >= 0) return it }
        val labels = rowLabels()
        if (labels.isEmpty()) blocked("The working watchlist is empty, so there is no instrument to open.")
        // A plain equity: NSE, a name of letters only (not a bond such as 750TCSL28 or a contract), a well-known one first.
        // Chart, Technicals, Fundamentals and Op.Chain only make sense for those.
        val nse = labels.filter { exchangeOf(it) == "NSE" }
        val plain = nse.filter { l -> nameOf(l)?.let { n -> baseName(n).isNotEmpty() && baseName(n).all { it.isLetter() } } == true }
        val pick = plain.firstOrNull { baseName(nameOf(it)) in preferredEquities } ?: plain.firstOrNull()
            ?: nse.firstOrNull() ?: labels.firstOrNull { exchangeOf(it) == "BSE" } ?: labels.first()
        val r = Rep(nameOf(pick) ?: fail("The first row has no readable name: '$pick'"), exchangeOf(pick) ?: "NSE")
        if (plain.isEmpty()) note("no plain NSE equity is in the working watchlist; using '${r.name}', which may lack chart, Technicals and Fundamentals data")
        rep = r
        return r
    }

    /** Opens the action sheet of [r] with a single tap; returns which sheet it is. */
    private fun openSheet(r: Rep): String {
        val sheets = listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)
        val i = findRow(r)
        if (i < 0) fail("'${r.name}' is no longer in the list")
        val tap = engine.tapAt(T.WATCHLIST_SCRIP_ROW, i)
        if (!tap.ok) fail("Tapping '${r.name}' failed: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(UI) { engine.firstPresent(sheets) != null }) {
            fail("The sheet for '${r.name}' did not open within ${UI / 1000} s; ${where()}")
        }
        pause(300)
        return engine.firstPresent(sheets) ?: sheets.first()
    }

    private fun sheetOpen(): Boolean =
        engine.firstPresent(listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)) != null && engine.isPresent(T.TRADE_BUY_BUTTON)

    /** What WL-05 saw on the sheet, so later cases do not open it again just to find out. */
    private var sheetHasOpChain: Boolean? = null

    /**
     * Makes the instrument sheet available: an open one is reused, otherwise the Watchlist is reached (closing only
     * what is on top of it) and the sheet is opened once. Consecutive cases that start from the sheet therefore
     * open it only when the app has closed it.
     */
    private fun sheetFor(r: Rep) {
        if (sheetOpen()) return
        if (!engine.isPresent(T.WATCHLIST_SCREEN) || engine.firstPresent(overlays) != null) ensureLanding()
        openSheet(r)
    }

    // ═══ The run ═══════════════════════════════════════════════════════════════════════════════

    /** Runs every selected case. Returns a result only when the run has to end. */
    fun run(): AutomationRunResult? {
        val view = modules.isOn("watchlist")
        val search = modules.isOn("watchlist_search")
        val edit = modules.isOn("watchlist_edit")
        val settings = modules.isOn("watchlist_settings")
        for ((module, on) in listOf("watchlist" to view, "watchlist_search" to search, "watchlist_edit" to edit, "watchlist_settings" to settings)) {
            if (!on) skipModule(module)
        }
        if (!view && !search && !edit && !settings) {
            flow.skipAs(flow.step(STATE_ID), "not_tested", "no Watchlist module selected")
            return null
        }

        // Landing is needed by every module, so it is set up even when WL-01 itself is not selected.
        if (view) {
            runCase("wl_landing_loaded", recoverAfter = false) { landingCase() }?.let { return it }
        } else {
            try {
                currentCase = null
                establishLanding()
            } catch (e: CaseEnd) {
                note("setup: ${e.message}")
            } catch (_: Stopped) {
                return flow.step(CASES.first().id).let(stopped)
            }
        }
        if (view) {
            for (id in listOf("wl_switch_watchlist", "wl_predefined_lists", "wl_index_panel")) {
                runCase(id) { viewCase(id) }?.let { return it }
            }
            // From the instrument sheet on, each case starts from what the previous one left (the open sheet, the
            // Alert page): the app is brought back to the Watchlist only before a case that needs it, so the
            // sheet and the Alert page are not opened again and again.
            for (id in listOf(
                "wl_instrument_sheet", "wl_chart", "wl_alert_page", "wl_alert_condition", "wl_option_chain", "wl_buy_sell",
            )) {
                runCase(id, recoverAfter = false) { viewCase(id) }?.let { return it }
            }
            // More Details: one screen, four tabs; the screen stays open between them.
            for ((id, last) in listOf("wl_more_market_depth" to false, "wl_more_technicals" to false, "wl_more_fundamentals" to false, "wl_more_info" to true)) {
                runCase(id, recoverAfter = last) { moreCase(id) }?.let { return it }
            }
            for (id in listOf("wl_predefined_instrument", "wl_predefined_search")) {
                runCase(id) { viewCase(id) }?.let { return it }
            }
        }
        if (search) {
            for ((id, last) in listOf(
                "wl_search_results" to false, "wl_search_fno" to false, "wl_search_commodity" to false,
                "wl_search_stocks" to false, "wl_search_add_remove" to false, "wl_search_segment_types" to false,
                "wl_capacity_limit" to true,
            )) {
                runCase(id, recoverAfter = last) { searchCase(id) }?.let { return it }
            }
        }
        if (edit) {
            for (id in listOf("wl_edit_rename", "wl_edit_reorder", "wl_edit_delete")) {
                runCase(id) { editCase(id) }?.let { return it }
            }
            // Whatever happened above, the instrument this run added must not be left behind.
            try {
                cleanupTestScrip()
                val leftover = testScrip
                flow.step("wl_edit_delete").let { s ->
                    if (leftover != null) s.cleanup = "FAILED: the test instrument ${leftover.name} is still in the watchlist; remove it by hand"
                    else if (s.cleanup == null) s.cleanup = "restored: no test instrument left behind"
                }
            } catch (_: CaseEnd) {
            } catch (_: Stopped) {
                return flow.step("wl_edit_delete").let(stopped)
            }
        }
        if (settings) {
            for (id in listOf("wl_settings_view", "wl_settings_filter_sort")) {
                runCase(id) { settingsCase(id) }?.let { return it }
            }
        }
        // Last: is everything the cases above changed back as it was found?
        runCase(STATE_ID) { stateRestored() }?.let { return it }
        return null
    }

    private fun skipModule(module: String) {
        for (id in idsOf(module)) {
            val step = flow.step(id)
            if (step.status == StepStatus.PENDING) flow.skipAs(step, "not_tested", modules.skipNote(module))
        }
    }

    // ═══ WL-01 ═════════════════════════════════════════════════════════════════════════════════

    private fun landingCase() {
        establishLanding()
        val tab = needWork()
        val labels = rowLabels()
        val count = readCount() ?: fail("The 'n / 50' count label could not be read; ${where()}")
        capacity = count.second
        startCount = count.first
        note("count ${count.first} / ${count.second}")
        engine.labelsOf(tab).map(::tabName).firstOrNull { it.isNotBlank() }?.let { note("watchlist '$it'") }
        if (labels.isEmpty()) {
            note("the watchlist is empty (empty state shown)")
        } else {
            val first = labels.first()
            val toks = tokens(first)
            val problems = mutableListOf<String>()
            if (exchangeOf(first) == null) problems += "an exchange"
            if (toks.none { PRICE.matches(it) }) problems += "an LTP"
            if (!engine.isPresent(T.WATCHLIST_GRID_VIEW) && toks.none { it.contains("%") }) problems += "a % change"
            if (problems.isNotEmpty()) fail("The first instrument row lacks ${problems.joinToString(" and ")} (row text: '$first')")
            note("${labels.size} row(s) built, first '${first.take(60)}'")
        }
        shot("user_watchlist_loaded")
    }

    // ═══ WL-02 … WL-10, WL-15, WL-16 ═══════════════════════════════════════════════════════════

    private fun viewCase(id: String) {
        when (id) {
            "wl_switch_watchlist" -> switchWatchlist()
            "wl_predefined_lists" -> predefinedLists()
            "wl_index_panel" -> indexPanel()
            "wl_instrument_sheet" -> instrumentSheet()
            "wl_chart" -> chart()
            "wl_alert_page" -> alertPage()
            "wl_alert_condition" -> alertCondition()
            "wl_option_chain" -> optionChain()
            "wl_buy_sell" -> buySell()
            "wl_predefined_instrument" -> predefinedInstrument()
            "wl_predefined_search" -> predefinedSearch()
        }
    }

    private fun switchWatchlist() {
        ensureLanding()
        val from = needWork()
        val to = editableTabs.firstOrNull { it != from } ?: notApplicable("The account has only one user watchlist.")
        val beforeRows = rowLabels().take(3)
        val beforeCount = readCount()
        selectTab(to)
        val changed = waitUntil(UI) {
            engine.isSelected(to) || rowLabels().take(3) != beforeRows || readCount() != beforeCount || engine.isPresent(T.WATCHLIST_EMPTY_STATE)
        }
        note("switched to '$to', now ${readCount()?.let { "${it.first} / ${it.second}" } ?: "count unreadable"}")
        readCount()?.let { tabRows[to] = it.first }
        selectTab(from)
        if (!changed) fail("Tapping '$to' changed neither the rows, the count nor the selected tab.")
    }

    private fun enterPredefined() {
        tapOpen(T.WATCHLIST_MODE_PREDEFINED, predefinedTabIds, "The Predefined switch did not show the predefined tabs")
        if (!waitUntil(LOAD) { engine.isPresent(T.WATCHLIST_SCRIP_ROW) }) fail("The predefined instruments did not load within ${LOAD / 1000} s")
    }

    private fun predefinedLists() {
        ensureLanding()
        val started = SystemClock.elapsedRealtime()
        enterPredefined()
        timing(started, "predefined instruments loaded")
        val first = rowLabels()
        readCount()?.let { note("count ${it.first} / ${it.second}") }
        shot("predefined_loaded")
        val second = predefinedTabIds.getOrNull(1)
        if (second != null && engine.revealInList(second, predefinedTabIds)) {
            val before = first.take(3)
            val tap = engine.tap(second)
            if (!tap.ok) fail("The second predefined tab could not be tapped: ${tap.detail}")
            if (!waitUntil(LOAD) { rowLabels().take(3) != before }) fail("The second predefined tab showed the same instruments as the first")
            note("second predefined list shows '${rowLabels().firstOrNull()?.take(40)}'")
        } else {
            note("only one predefined list is offered")
        }
        tapOpen(T.WATCHLIST_MODE_WATCHLIST, tabIds, "The Watchlist switch did not bring the user tabs back")
    }

    private fun indexPanel() {
        ensureLanding()
        val ticker = engine.labelsOf(T.WATCHLIST_TICKER).filter { it.isNotBlank() }
        if (ticker.isEmpty()) {
            blocked(
                "The header ticker ('${T.WATCHLIST_TICKER}') is not on screen; the installed Tradion build may predate this identifier.",
                Cause.AUTOMATION, "Rebuild Tradion with the '${T.WATCHLIST_TICKER}' and '${T.WATCHLIST_INDEX_DROPDOWN}' tags (docs/AUTOMATION_SEMANTICS.md).",
            )
        }
        note("ticker: ${ticker.take(2).joinToString(" | ") { it.replace('\n', ' ').take(30) }}")
        tapOpen(T.WATCHLIST_INDEX_DROPDOWN, listOf(T.DASHBOARD_INDEX_CLOSE), "The header arrow did not open the index panel")
        val all = readContent(
            null, "the index panel",
            "Publish the panel's index tiles and its Funds / Equity lines as labels (Semantics identifier 'index_panel_funds').",
            chrome = setOf("Close"),
        )
        val gaps = missing(all, listOf("Funds", "Equity"))
        if (gaps.isNotEmpty()) {
            fail("The panel opened and is readable but shows no ${gaps.joinToString(" or ")} (texts: ${all.take(8).joinToString(" | ")})", Cause.UNCONFIRMED,
                "If the label exists on screen, publish it with a Semantics identifier ('index_panel_funds').")
        }
        note("${engine.countOf(T.DASHBOARD_INDEX_ITEM)} index(es) and the funds line shown")
        shot("index_panel")
        engine.tap(T.DASHBOARD_INDEX_CLOSE)
        if (!waitUntil(UI) { !engine.isPresent(T.DASHBOARD_INDEX_CLOSE) }) fail("The panel did not close when its Close button was tapped")
        if (!waitUntil(UI) { engine.isPresent(T.WATCHLIST_SCREEN) }) fail("The Watchlist did not come back after the panel closed")
    }

    private fun instrumentSheet() {
        val r = needRep()
        sheetFor(r)
        val sheet = engine.firstPresent(listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)) ?: T.SCRIP_DETAILS_SHEET
        // The sheet's own identifier says it opened; its content is checked separately, from readable labels.
        val buttons = mutableListOf<String>()
        for ((id, label) in listOf(
            T.SCRIP_SHEET_CHART_BUTTON to "Chart", T.SCRIP_SHEET_ALERT_BUTTON to "Alert",
            T.SCRIP_SHEET_MORE_BUTTON to "More Details", T.TRADE_BUY_BUTTON to "BUY", T.TRADE_SELL_BUTTON to "SELL",
        )) {
            if (!engine.isTappable(id)) buttons += "a tappable $label"
        }
        if (buttons.isNotEmpty()) fail("The sheet for ${r.name} lacks ${buttons.joinToString(", ")}; ${where()}", Cause.UNCONFIRMED)
        val all = readContent(
            sheet, "the instrument sheet's name, exchange and LTP",
            "Publish the sheet's name, exchange and LTP as labels (Semantics identifiers 'scrip_sheet_name', 'scrip_sheet_exchange', 'scrip_sheet_ltp').",
            chrome = setOf("Chart", "Alert", "Op.Chain", "More Details", "BUY", "SELL"),
        )
        val problems = mutableListOf<String>()
        if (!hasText(all, base(r))) problems += "the instrument name '${base(r)}'"
        if (all.none { it.equals(r.exchange, ignoreCase = true) }) problems += "the exchange '${r.exchange}'"
        if (all.none { PRICE.matches(it.trim()) }) problems += "an LTP"
        if (problems.isNotEmpty()) {
            fail("The sheet for ${r.name} is readable but shows no ${problems.joinToString(", ")} (texts: ${all.take(10).joinToString(" | ")})", Cause.UNCONFIRMED,
                "If the values are visible, publish them with the identifiers 'scrip_sheet_name', 'scrip_sheet_exchange' and 'scrip_sheet_ltp'.")
        }
        sheetHasOpChain = engine.isPresent(T.SCRIP_SHEET_OPCHAIN_BUTTON)
        note(if (sheetHasOpChain == true) "Op.Chain offered" else "no Op.Chain button")
        note(if (missing(all, listOf("Bid", "Offer")).isEmpty()) "market depth shown" else "market depth labels not found")
        shot("instrument_sheet")
    }

    private fun chart() {
        val r = needRep()
        sheetFor(r)
        tapOpen(T.SCRIP_SHEET_CHART_BUTTON, listOf(T.CHART_SCREEN), "The Chart button did not open the chart")
        val started = SystemClock.elapsedRealtime()
        val ready = waitUntil(Timeouts.CHART_LOAD) { engine.isPresent(T.CHART_READY) }
        if (!ready) {
            fail("The chart opened but its WebView had not reported ready after ${Timeouts.CHART_LOAD / 1000} s (see the screenshot for what it showed).", Cause.API)
        }
        timing(started, "chart WebView ready", slowMs = 8_000L)
        // 'Ready' only says the page finished loading. Whether something was drawn is read from the screenshot: a
        // chart paints a grid and candles in several colours, a blank or failed WebView is one flat colour. A few
        // captures, each waiting for the paint, never a blind retry.
        var content: ScreenshotInspector.Content? = null
        var decoded = false
        for (look in 1..3) {
            pause(300)
            val path = shot("chart_loaded") ?: break
            content = ScreenshotInspector.content(path)
            if (content != null) decoded = true
            if (content?.rendered == true) break
        }
        when {
            !decoded -> blocked(
                "The chart reported ready but its screenshot could not be inspected, so the drawn content is not verified.",
                Cause.AUTOMATION, "Check the screenshot by eye; no accessibility content exists inside the chart WebView.",
            )
            content?.rendered != true -> fail(
                "The chart reported ready but the screen is not drawn: ${content} (a chart shows a grid and candles in several colours).",
                Cause.UNCONFIRMED, "Confirm in the screenshot: either the instrument has no chart history or the WebView reported ready without painting.",
            )
            else -> note("chart drawn (${content})")
        }
        note("candles and the timeframe bar are inside the WebView, which the tool cannot read; a timeframe change was not tested")
    }

    private fun openAlert(r: Rep) {
        // The Alert page left open by the previous case is used as it is: opening it again is what showed twice.
        if (engine.isPresent(T.ALERT_SCREEN)) return
        sheetFor(r)
        if (!engine.isPresent(T.SCRIP_SHEET_ALERT_BUTTON)) notApplicable("The sheet of ${r.name} has no Alert button.")
        tapOpen(T.SCRIP_SHEET_ALERT_BUTTON, listOf(T.ALERT_SCREEN), "The Alert button did not open the Alert page")
    }

    private fun alertPage() {
        val r = needRep()
        openAlert(r)
        val all = readContent(
            T.ALERT_SCREEN, "the New Alert page's instrument",
            "Publish the instrument field's value as a label (Semantics identifier 'alert_scrip_field' already exists: give it the value as its label).",
            chrome = setOf("New Alert", "Create"),
        )
        if (!hasText(all, base(r)) && engine.labelsOf("alert_scrip_field").none { it.contains(base(r), true) }) {
            fail("The Alert page is readable but does not show the instrument '${base(r)}' (texts: ${all.take(10).joinToString(" | ")})", Cause.UNCONFIRMED,
                "Publish the prefilled instrument as the label of 'alert_scrip_field'.")
        }
        // Everything that is looked at comes before typing: the keyboard that the name field raises covers the rest.
        val anchors = listOf(T.ALERT_NAME_INPUT, "alert_scrip_field", T.ALERT_VALUE_INPUT)
        if (!engine.isPresent(T.ALERT_VALUE_INPUT) && !engine.revealInList(T.ALERT_VALUE_INPUT, anchors)) {
            fail("The Alert page shows no value field (identifier '${T.ALERT_VALUE_INPUT}'); ${where()}", Cause.UNCONFIRMED)
        }
        if (!engine.isTappable(T.ALERT_CREATE_BUTTON)) fail("The Create button is missing or disabled")
        val name = engine.enterText(T.ALERT_NAME_INPUT, "AutoCheck")
        if (!name.ok) fail("The Alert Name field did not take a value: ${name.detail}", Cause.AUTOMATION)
        note("instrument prefilled, name accepted, value field and Create present (Create was not tapped)")
        shot("new_alert")
    }

    private fun alertCondition() {
        val r = needRep()
        openAlert(r)
        val anchors = listOf(T.ALERT_NAME_INPUT, "alert_scrip_field", T.ALERT_VALUE_INPUT)
        val found = waitUntil(UI) { engine.isPresent(T.ALERT_CONDITION) || engine.revealInList(T.ALERT_CONDITION, anchors, maxScrolls = 1) }
        if (!found) {
            blocked(
                "The condition dropdown ('${T.ALERT_CONDITION}') is not on screen; the installed Tradion build may predate this identifier.",
                Cause.AUTOMATION,
                "Rebuild Tradion with CustomDropdownButton(automationId: 'alert_condition') (docs/AUTOMATION_SEMANTICS.md).",
            )
        }
        val before = engine.labelsOf(T.ALERT_CONDITION).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        tapOpen(T.ALERT_CONDITION, listOf(T.ALERT_CONDITION_ITEM_PREFIX + "0"), "The condition dropdown did not open")
        val items = (0 until 8).map { "${T.ALERT_CONDITION_ITEM_PREFIX}$it" }.filter { engine.isPresent(it) }
        val labelled = items.associateWith { engine.labelsOf(it).firstOrNull { l -> l.isNotBlank() }?.trim().orEmpty() }
        val pick = labelled.entries.firstOrNull { it.value.isNotEmpty() && !it.value.equals(before, true) }
            ?: fail("The dropdown opened with ${items.size} option(s) but none other than the current '$before'", Cause.UNCONFIRMED)
        val chosen = engine.tap(pick.key)
        if (!chosen.ok) fail("The condition '${pick.value}' could not be picked: ${chosen.detail}", Cause.AUTOMATION)
        val shown = waitUntil(UI) { engine.labelsOf(T.ALERT_CONDITION).any { it.trim().equals(pick.value, true) } }
        if (!shown) {
            fail("After picking '${pick.value}' the dropdown shows '${engine.labelsOf(T.ALERT_CONDITION).firstOrNull()?.trim()}'", Cause.APP)
        }
        note("condition changed from '$before' to '${pick.value}'")
    }

    private fun optionChain() {
        val r = needRep()
        // WL-05 already saw whether the sheet offers Op.Chain: no need to open the sheet again to find out it does not.
        if (sheetHasOpChain == false) notApplicable("The sheet of ${r.name} shows no Op.Chain button.")
        sheetFor(r)
        if (!engine.isPresent(T.SCRIP_SHEET_OPCHAIN_BUTTON)) notApplicable("The sheet of ${r.name} shows no Op.Chain button.")
        tapOpen(T.SCRIP_SHEET_OPCHAIN_BUTTON, listOf(T.OPTION_CHAIN_SCREEN), "Op.Chain did not open the option chain")
        val started = SystemClock.elapsedRealtime()
        if (!waitUntil(LOAD) { engine.isPresent(T.OPTION_CHAIN_TABLE) }) fail("The option chain opened but its table did not load within ${LOAD / 1000} s")
        timing(started, "option chain loaded")
        shot("option_chain")
    }

    private fun buySell() {
        val r = needRep()
        for ((button, label) in listOf(T.TRADE_BUY_BUTTON to "BUY", T.TRADE_SELL_BUTTON to "SELL")) {
            sheetFor(r)
            if (!waitUntil(UI) { engine.isPresent(button) }) fail("The sheet showed no $label button")
            // One tap only: the sheet closes itself, so a retry could only ever reach a second pad.
            val pad = engine.tapAndConfirm(button, listOf(T.ORDER_WINDOW_SCREEN), UI, retryAfterMs = UI)
            alive()
            if (!pad.ok) fail("$label did not open the order pad: ${pad.detail}")
            pause(300)
            if (!flow.closeWithBack(T.ORDER_WINDOW_SCREEN)) fail("The order pad did not close on Back after $label")
            note("$label opened the order pad and was left with Back")
        }
    }

    private fun predefinedInstrument() {
        ensureLanding()
        enterPredefined()
        val first = rowLabels().firstOrNull() ?: fail("No predefined instrument is listed", Cause.API)
        val name = nameOf(first) ?: fail("The first predefined row has no readable name: '$first'")
        val r = Rep(name, exchangeOf(first) ?: "NSE")
        openSheet(r)
        val sheet = engine.firstPresent(listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)) ?: T.SCRIP_DETAILS_SHEET
        val missingButtons = listOf(T.SCRIP_SHEET_CHART_BUTTON to "Chart", T.SCRIP_SHEET_ALERT_BUTTON to "Alert")
            .filter { !engine.isTappable(it.first) }.map { "a tappable ${it.second}" }
        if (missingButtons.isNotEmpty()) fail("The predefined instrument's sheet lacks ${missingButtons.joinToString(", ")}", Cause.UNCONFIRMED)
        val all = readContent(
            sheet, "the predefined instrument's name",
            "Publish the sheet's name as a label (Semantics identifier 'scrip_sheet_name').",
            chrome = setOf("Chart", "Alert", "Op.Chain", "More Details", "BUY", "SELL"),
        )
        if (!hasText(all, name.substringBefore('-'))) {
            fail("The predefined instrument's sheet is readable but does not show '${name.substringBefore('-')}' (texts: ${all.take(8).joinToString(" | ")})", Cause.UNCONFIRMED,
                "Publish the sheet's name as 'scrip_sheet_name'.")
        }
        note(if (engine.isTappable(T.TRADE_BUY_BUTTON) && engine.isTappable(T.TRADE_SELL_BUTTON)) "BUY / SELL present" else "BUY / SELL not both present")
    }

    private fun predefinedSearch() {
        ensureLanding()
        enterPredefined()
        val before = rowLabels().take(3)
        val typed = engine.enterText(T.WATCHLIST_SEARCH_BUTTON, "BEL")
        if (typed.ok) {
            if (!waitUntil(UI) { rowLabels().take(3) != before }) fail("Typing in 'Search within watchlist' did not narrow the list")
            note("typing narrowed the list to '${rowLabels().firstOrNull()?.take(40)}'")
            engine.enterText(T.WATCHLIST_SEARCH_BUTTON, "")
            return
        }
        if (engine.isPresent(T.SEARCH_SCREEN)) fail("Tapping 'Search within watchlist' opened the add-search on a read-only Predefined list")
        val message = texts(40).firstOrNull { it.contains("can not add", true) || it.contains("cannot add", true) }
        if (message != null) {
            notApplicable("In this build 'Search within watchlist' is not a filter: tapping it shows '$message', so a Predefined list is read-only (the behaviour accepted earlier). Raise it if filtering is expected.")
        }
        blocked("Tapping 'Search within watchlist' produced no field and no message within ${UI / 1000} s.")
    }

    // ═══ WL-11 … WL-14: More Details ═══════════════════════════════════════════════════════════

    private fun openMore() {
        val r = needRep()
        sheetFor(r)
        if (!engine.isPresent(T.SCRIP_SHEET_MORE_BUTTON)) notApplicable("${r.name} has no More Details (indices and some instruments have none).")
        tapOpen(T.SCRIP_SHEET_MORE_BUTTON, listOf(T.MORE_INFO_SCREEN), "More Details did not open")
    }

    /** Scrolls the page to its top (bounded) so top-section content is what is checked first. */
    private fun scrollToTop() {
        repeat(6) {
            if (!engine.scrollPage(forward = false)) return
            pause(Timeouts.SETTLE)
        }
    }

    private val moreTabNames = setOf("Market Depth", "Technicals", "Fundamentals", "Info", "More Details", "BUY", "SELL")

    /**
     * Selects a More Details tab, waits for its data and returns what is readable. The tab's own identifier is the
     * proof that it was selected; its content is read from labels, and a screen that exposes none is BLOCKED.
     */
    private fun moreTab(name: String): List<String> {
        if (!engine.isPresent(T.MORE_INFO_SCREEN)) openMore()
        val tab = "${T.MORE_INFO_TAB_PREFIX}$name"
        val tabStarted = SystemClock.elapsedRealtime()
        val tap = engine.tap(tab)
        if (!tap.ok) fail("The '$name' tab could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        pause(Timeouts.SETTLE)
        val started = SystemClock.elapsedRealtime()
        val ids = AutomationConfig.Ids
        if (!waitUntil(LOAD) { engine.countOf(ids.UI_LOADING) == 0 }) fail("The '$name' tab was still loading after ${LOAD / 1000} s", Cause.API)
        if (engine.countOf(ids.UI_ERROR) > 0) fail("The '$name' tab shows the 'Something went wrong / Retry' screen", Cause.API)
        scrollToTop()
        // The tab's own body is read (the header and tab names above it are not its content).
        val content = readContent(
            "more_info_body_$name", "the $name tab's content",
            "Publish each row of the $name tab inside its body (identifier 'more_info_body_$name' exists; its rows need labels).",
            chrome = moreTabNames, scoped = true,
        )
        timing(tabStarted, "$name tab loaded", slowMs = 5_000L)
        return content
    }

    private fun moreFail(tab: String, gaps: List<String>, all: List<String>): Nothing =
        fail(
            "$tab is readable but lacks ${gaps.joinToString(", ")} (texts: ${all.take(10).joinToString(" | ")})", Cause.UNCONFIRMED,
            "If these rows are visible, publish them with Semantics labels so they can be verified.",
        )

    private fun moreCase(id: String) {
        when (id) {
            "wl_more_market_depth" -> {
                if (!engine.isPresent(T.MORE_INFO_SCREEN)) openMore()
                val all = moreTab("market_depth")
                val gaps = missing(all, listOf("Bid", "Offer", "Total", "Open", "High", "Low", "Close", "Today"))
                if (gaps.isNotEmpty()) moreFail("Market Depth", gaps, all)
                // The BUY / SELL bar sits below the tab body, so it is looked for on the screen, by identifier first.
                val bar = readScreen()
                val buySell = (engine.isPresent(T.TRADE_BUY_BUTTON) || hasText(bar, "BUY")) && (engine.isPresent(T.TRADE_SELL_BUTTON) || hasText(bar, "SELL"))
                if (!buySell) fail("Market Depth is readable but the BUY / SELL bar is not on the screen", Cause.UNCONFIRMED)
                note(if (all.count { it.trim() == "0.00" } >= 8) "values are zero (no feed): not a UI defect" else "values present")
                shot("more_market_depth")
            }
            "wl_more_technicals" -> {
                val all = moreTab("technicals")
                val gaps = missing(all, listOf("EMA", "Bullish Moving Average", "Bearish Moving Average"))
                if (gaps.isNotEmpty()) moreFail("Technicals", gaps, all)
                shot("more_technicals")
                val toSma = engine.tap("technicals_sma_button", listOf("SMA"))
                if (!toSma.ok) fail("The SMA toggle could not be tapped: ${toSma.detail}")
                pause(Timeouts.SETTLE * 2)
                val sma = texts()
                if (!hasText(sma, "Bullish Moving Average")) fail("After switching to SMA the Technicals summary disappeared")
                note(if (sma != all) "SMA values differ from EMA" else "SMA shows the same values as EMA")
                engine.tap("technicals_ema_button", listOf("EMA"))
                pause(Timeouts.SETTLE)
            }
            "wl_more_fundamentals" -> {
                // Top section first (the page is scrolled to its top by moreTab), then what lies below it.
                var all = moreTab("fundamentals")
                // Labels differ slightly between builds ("Market Cap" / "Market Capitalization"), so the stems are matched.
                val stats = listOf("Market Cap", "PE", "PB", "Face Value", "Sector PE", "PEG", "EPS", "Dividend")
                var gaps = missing(all, stats)
                if (gaps.isNotEmpty() && engine.scrollPage(forward = true)) {
                    pause(Timeouts.SETTLE)
                    all = (all + readScreen(T.MORE_INFO_SCREEN)).distinct()
                    gaps = missing(all, gaps)
                }
                val holding = listOf("Share Holding", "Shareholding", "Promoter", "FII", "MF").filter { hasText(all, it) }
                var below = readScreen(T.MORE_INFO_SCREEN)
                if (holding.isEmpty() && engine.scrollPage(forward = true)) {
                    pause(Timeouts.SETTLE)
                    below = (below + readScreen(T.MORE_INFO_SCREEN)).distinct()
                }
                val holders = listOf("Share Holding", "Shareholding", "Promoter", "FII", "MF", "Other").filter { hasText(all + below, it) }
                val financials = hasText(all + below, "Financials") || hasText(all + below, "Particulars")
                if (gaps.size == stats.size && holders.isEmpty() && !financials) {
                    moreFail("Fundamentals", gaps, all)
                }
                if (gaps.isNotEmpty()) note("stat labels not found by stem: ${gaps.joinToString()}")
                if (holders.isEmpty()) fail("Fundamentals shows no share-holding section (texts: ${(all + below).take(10).joinToString(" | ")})", Cause.UNCONFIRMED,
                    "If the donut and its legend are visible, publish them with Semantics labels.")
                note("share holding: ${holders.joinToString()}; financials table ${if (financials) "present" else "not seen"}")
                shot("more_fundamentals")
            }
            "wl_more_info" -> {
                val r = needRep()
                val all = moreTab("info")
                note("Info tab loaded with ${all.size} labelled line(s)")
                val gaps = missing(all, listOf("Board Lot", "Instrument Name", "Precision", "Tick Size", "Max Quantity", "Exchange", "Symbol", "Trading Symbol", "Company Name", "Isin"))
                if (gaps.isNotEmpty()) moreFail("Info", gaps, all)
                if (!hasText(all, base(r))) fail("Info does not show the opened instrument '${base(r)}' as its Trading Symbol")
                if (all.none { it.equals(r.exchange, ignoreCase = true) }) fail("Info does not show the exchange '${r.exchange}' of the opened instrument")
                shot("more_info")
            }
        }
    }

    // ═══ Search and add ════════════════════════════════════════════════════════════════════════

    private fun openSearch() {
        ensureLanding()
        toWorkTab()
        tapOpen(T.WATCHLIST_SEARCH_BUTTON, listOf(T.SEARCH_SCREEN), "'Search & add scrips' did not open the search screen")
        if (!waitUntil(UI) { engine.isPresent(T.SEARCH_SCRIP_INPUT) }) fail("The search field did not appear")
    }

    private fun ensureSearchOpen() {
        if (!engine.isPresent(T.SEARCH_SCREEN)) openSearch()
    }

    private fun leaveSearch() {
        val close = engine.tapAndConfirm(T.SEARCH_BACK_BUTTON, listOf(T.WATCHLIST_SCREEN), UI, retryAfterMs = RETRY, tapAtLeastOnce = true)
        alive()
        if (!close.ok || !waitUntil(UI) { !engine.isPresent(T.SEARCH_SCREEN) }) {
            if (engine.isPresent(T.SEARCH_SCREEN)) flow.closeWithBack(T.SEARCH_SCREEN)
            if (engine.isPresent(T.SEARCH_SCREEN)) fail("The search screen did not close from its back arrow")
        }
        if (!waitUntil(UI) { engine.isPresent(T.WATCHLIST_SCREEN) }) fail("The Watchlist did not come back after search")
    }

    /** Selects a search category, types [query] and waits for results. */
    private fun searchQuery(tab: String, tabName: String, query: String) {
        if (!engine.revealInList(tab, T.SEARCH_TABS)) fail("The '$tabName' category is not on the search screen")
        val typed = engine.enterText(T.SEARCH_SCRIP_INPUT, query)
        if (!typed.ok) fail("Could not type '$query': ${typed.detail}")
        val select = engine.tap(tab)
        if (!select.ok) fail("Could not select '$tabName': ${select.detail}")
        pause(Timeouts.SETTLE)
        val started = SystemClock.elapsedRealtime()
        // The "No result found" page is what the screen shows until the request returns, so it is not an
        // answer by itself: wait for rows that belong to this query (results of the previous query do not).
        val arrived = waitUntil(LOAD) {
            !engine.isPresent(T.SEARCH_LOADING) && engine.isPresent(T.SEARCH_RESULTS_LIST) &&
                resultRows().any { r -> tokens(r).any { it.startsWith(query, ignoreCase = true) } }
        }
        if (!arrived) {
            if (engine.isPresent(T.SEARCH_NO_RESULT)) fail("Searching '$query' under $tabName still showed 'No result found' after ${LOAD / 1000} s")
            fail("Searching '$query' under $tabName showed no '$query' result within ${LOAD / 1000} s")
        }
        val took = SystemClock.elapsedRealtime() - started
        if (took > 2_000) note("results took ${"%.1f".format(took / 1000.0)} s")
    }

    private fun resultRows(): List<String> = engine.groupedLabelsOf(T.SEARCH_RESULT_ROW)

    private fun hasIcon(row: Int): Boolean =
        engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADD_BUTTON) ||
            engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)

    private fun searchCase(id: String) {
        when (id) {
            "wl_search_results" -> {
                openSearch()
                try {
                    searchQuery(T.SEARCH_TAB_ALL, "All", "INFY")
                } catch (e: CaseEnd) {
                    if (e.kind == Kind.FAIL) allEmptyCrossCheck(e)
                    throw e
                }
                val rows = resultRows()
                if (rows.none { r -> tokens(r).any { it.startsWith("INFY", ignoreCase = true) } }) fail("No INFY result is listed (rows: ${rows.take(3).joinToString(" | ") { it.take(40) }})")
                val withIcon = rows.indices.count { hasIcon(it) }
                if (withIcon == 0) fail("None of the ${rows.size} result row(s) offers a + or a tick")
                val exchanges = rows.mapNotNull { exchangeOf(it) }.toSet()
                if (exchanges.isEmpty()) fail("No result row shows an exchange badge")
                if (exchanges.none { it == "NSE" || it == "BSE" }) fail("All lists INFY but no NSE / BSE stock among ${rows.size} result(s) (exchanges: ${exchanges.joinToString("/")})", Cause.APP)
                val missingTabs = T.SEARCH_TABS.filterNot { engine.revealInList(it, T.SEARCH_TABS) }
                if (missingTabs.isNotEmpty()) fail("These categories are missing: ${missingTabs.joinToString()}")
                note("${rows.size} result(s) across ${exchanges.joinToString("/")}; $withIcon with an add or tick icon; All, F&O, Commodity and Stocks present")
                shot("search_results")
            }
            "wl_search_fno" -> categoryCase(T.SEARCH_TAB_FNO, "F&O", "TCS", setOf("NFO", "BFO"))
            "wl_search_commodity" -> categoryCase(T.SEARCH_TAB_COMMODITY, "Commodity", "CRU", setOf("MCX"))
            "wl_search_stocks" -> categoryCase(T.SEARCH_TAB_STOCKS, "Stocks", "INFY", setOf("NSE", "BSE"))
            "wl_search_add_remove" -> addRemove()
            "wl_search_segment_types" -> segmentTypes()
            "wl_capacity_limit" -> capacityLimit()
        }
    }

    /**
     * 'All' answered nothing for INFY. Whether that is the application or the data is decided by asking the Stocks
     * category once, with the same query on the same screen: results there and none under All is an application
     * defect; none anywhere means the data service has nothing for the query.
     */
    private fun allEmptyCrossCheck(allFailure: CaseEnd) {
        val allSelected = engine.isSelected(T.SEARCH_TAB_ALL)
        val stocks = try {
            searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", "INFY")
            resultRows().filter { r -> tokens(r).any { it.startsWith("INFY", ignoreCase = true) } }
        } catch (_: CaseEnd) {
            emptyList()
        }
        if (stocks.isNotEmpty()) {
            evidenceShot("all_empty_stocks_has_results")
            throw CaseEnd(
                Kind.FAIL,
                "Searching INFY under All returned no result (All tab ${if (allSelected) "selected" else "not reported selected"}), " +
                    "while the same query under Stocks returned ${stocks.size} INFY result(s): All does not show the stocks it should. " +
                    "(${allFailure.message})",
                Cause.APP, snapshot(),
                "Developers: the All category should include the Stocks results for the same query.",
            )
        }
        throw CaseEnd(
            Kind.FAIL,
            "Searching INFY returned nothing under All and nothing under Stocks: the search service has no result for the query right now. (${allFailure.message})",
            Cause.API, snapshot(), "Check the scrip-search response for 'INFY'.",
        )
    }

    private fun categoryCase(tab: String, name: String, query: String, exchanges: Set<String>) {
        ensureSearchOpen()
        searchQuery(tab, name, query)
        val rows = resultRows()
        val matching = rows.filter { r -> tokens(r).any { it.startsWith(query, ignoreCase = true) } }
        if (matching.isEmpty()) fail("No '$query' result under $name (rows: ${rows.take(3).joinToString(" | ") { it.take(40) }})")
        val wrong = matching.mapNotNull { exchangeOf(it) }.filter { it !in exchanges }
        if (wrong.isNotEmpty()) fail("Under $name, '$query' results appear on ${wrong.toSet().joinToString("/")} instead of ${exchanges.joinToString("/")}")
        if (matching.indices.none { hasIcon(it) } && rows.indices.none { hasIcon(it) }) fail("The $name results offer neither + nor a tick")
        note("${matching.size} '$query' result(s) on ${matching.mapNotNull { exchangeOf(it) }.toSet().joinToString("/")}")
    }

    /** Waits for row [row] to show [wanted] (and not [unwanted]); true when it does. */
    private fun rowShows(row: Int, wanted: String): Boolean = waitUntil(LOAD) { engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, wanted) }

    private fun addRemove() {
        ensureSearchOpen()
        searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", "INFY")
        val labels = resultRows()
        val existing = labels.indices.firstOrNull { engine.hasDescendantAt(T.SEARCH_RESULT_ROW, it, T.SEARCH_SCRIP_ADDED_ICON) }
        if (existing != null) {
            val label = labels[existing]
            val name = nameOf(label) ?: fail("The result row has no readable name: '$label'")
            val exchange = exchangeOf(label) ?: ""
            shot("search_existing_tick")
            // Remove it (tick becomes +), then add it back (+ becomes tick).
            deleteResult(existing, name)
            shot("search_add_icon")
            addResult(existing, name)
            note("$name ($exchange) was already in the watchlist: tick removed it, + added it back")
            cleanup = "restored: $name is in the watchlist again"
            leaveSearch()
            if (!waitUntil(LOAD) { rowLabels().any { sameScrip(it, name, exchange) } }) {
                fail("$name is not in the watchlist after being added back")
            }
            return
        }
        // Not in the watchlist yet: add it, check it, then take it out again.
        val addRow = labels.indices.firstOrNull { engine.hasDescendantAt(T.SEARCH_RESULT_ROW, it, T.SEARCH_SCRIP_ADD_BUTTON) }
            ?: fail("No INFY result offers + or a tick")
        val count = startCount
        if (count != null && count >= capacity) {
            blocked("The watchlist is full ($count / $capacity) and INFY is not in it, so there is nothing safe to remove and re-add.")
        }
        val label = labels[addRow]
        val name = nameOf(label) ?: fail("The result row has no readable name: '$label'")
        val exchange = exchangeOf(label) ?: ""
        addResult(addRow, name)
        shot("search_tick_added")
        leaveSearch()
        if (!waitUntil(LOAD) { rowLabels().any { sameScrip(it, name, null) } }) fail("$name was added but is not in the watchlist")
        note("$name was not in the watchlist: + added it and the watchlist shows it")
        // Clean up: remove it again through the same tick.
        openSearch()
        searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", "INFY")
        val again = resultRows().indexOfFirst { sameScrip(it, name, exchange) }
        if (again < 0 || !engine.hasDescendantAt(T.SEARCH_RESULT_ROW, again, T.SEARCH_SCRIP_ADDED_ICON)) fail("Could not find $name again to remove it")
        deleteResult(again, name)
        leaveSearch()
        if (!waitUntil(LOAD) { rowLabels().none { sameScrip(it, name, exchange) } }) {
            cleanup = "FAILED: $name was added by this run and is still in the watchlist; remove it by hand"
            fail("$name was removed again but is still in the watchlist", Cause.APP)
        }
        cleanup = "restored: $name was added and removed again"
    }

    /** What kind of instrument a search row is, from its text: EQ, FUT, CE, PE (a strike carries digits), or null. */
    private fun scripType(label: String): String? {
        val toks = tokens(label)
        return when {
            toks.any { it.endsWith("-EQ", ignoreCase = true) } -> "EQ"
            toks.any { it.equals("FUT", true) || (it.endsWith("FUT", true) && it.any(Char::isDigit)) } -> "FUT"
            toks.any { it.equals("CE", true) || (it.endsWith("CE", true) && it.any(Char::isDigit)) } -> "CE"
            toks.any { it.equals("PE", true) || (it.endsWith("PE", true) && it.any(Char::isDigit)) } -> "PE"
            else -> null
        }
    }

    private class Wanted(val segment: String, val tab: String, val tabName: String, val queries: List<String>, val type: String, val exchanges: Set<String>)

    private class Added(val wanted: Wanted, val query: String, val label: String, val name: String)

    /**
     * A search row's identity without anything that changes between two looks (prices). Long enough to reach the CE / PE
     * suffix of an option (a call and a put of the same strike share every token before it) and carries the type, so
     * two contracts of one underlying are never taken for each other.
     */
    private fun labelKey(label: String): String =
        tokens(label).filterNot { PRICE.matches(it) || it.contains('%') }.take(12).joinToString(" ") + "|" + scripType(label)

    /**
     * One instrument of each type per segment (an -EQ stock; futures, call and put of F&O; a commodity future): each is
     * added from search, the watchlist is checked for them, and every one is removed again, even when the case fails.
     * Bonds and other odd instruments are never taken: the type is read from the row.
     */
    private fun segmentTypes() {
        val start = startCount?.let { readCount()?.first ?: it } ?: blocked("The working watchlist's count is unknown.")
        val baseline = rowLabels()
        val wanted = listOf(
            Wanted("Stocks", T.SEARCH_TAB_STOCKS, "Stocks", listOf("RELIANCE", "TCS", "HDFCBANK"), "EQ", setOf("NSE")),
            Wanted("F&O", T.SEARCH_TAB_FNO, "F&O", listOf("TCS", "RELIANCE"), "FUT", setOf("NFO")),
            Wanted("F&O", T.SEARCH_TAB_FNO, "F&O", listOf("TCS", "RELIANCE", "NIFTY"), "CE", setOf("NFO")),
            Wanted("F&O", T.SEARCH_TAB_FNO, "F&O", listOf("TCS", "RELIANCE", "NIFTY"), "PE", setOf("NFO")),
            Wanted("Commodity", T.SEARCH_TAB_COMMODITY, "Commodity", listOf("CRUDEOIL", "CRU"), "FUT", setOf("MCX")),
        )
        if (capacity - start < wanted.size) blocked("The watchlist has room for ${capacity - start} more instrument(s); this case adds up to ${wanted.size}.")
        val added = mutableListOf<Added>()
        var failure: CaseEnd? = null
        try {
            ensureSearchOpen()
            for (w in wanted) {
                var found = false
                for (query in w.queries) {
                    try {
                        searchQuery(w.tab, w.tabName, query)
                    } catch (e: CaseEnd) {
                        if (e.kind != Kind.FAIL) throw e
                        continue // nothing for this query in this segment: try the next one
                    }
                    // The result list only builds the rows near the screen, so the contracts further down (calls and puts
                    // follow the futures) are reached by scrolling the list, a few screens at most.
                    var scrolls = 0
                    while (true) {
                        val rows = resultRows()
                        val row = rows.indices.firstOrNull {
                            scripType(rows[it]) == w.type && exchangeOf(rows[it]) in w.exchanges &&
                                engine.hasDescendantAt(T.SEARCH_RESULT_ROW, it, T.SEARCH_SCRIP_ADD_BUTTON)
                        }
                        if (row != null) {
                            val name = nameOf(rows[row]) ?: "scrip"
                            added += Added(w, query, rows[row], name)
                            addResult(row, name)
                            note("${w.segment} ${w.type}: added '$name' (${exchangeOf(rows[row])})")
                            found = true
                            break
                        }
                        if (w.type == "EQ" || w.type == "FUT" && w.segment != "F&O" || scrolls >= 3) break
                        if (!engine.scrollPage(forward = true)) break
                        scrolls++
                        pause(Timeouts.SETTLE)
                    }
                    if (found) break
                }
                if (!found) note("${w.segment} ${w.type}: no instrument of that type with a + was found for ${w.queries.joinToString("/")}")
            }
            if (added.isEmpty()) fail("No instrument of any type could be added from search", Cause.API)
            leaveSearch()
            val expected = start + added.size
            if (!waitUntil(LOAD) { readCount()?.first == expected }) {
                fail("After adding ${added.size} instrument(s) the watchlist count should read $expected but reads ${readCount()?.first}", Cause.APP)
            }
            val exchangesNow = rowLabels().mapNotNull { exchangeOf(it) }.toSet()
            val lacking = added.map { it.wanted.exchanges.first() }.distinct().filter { it !in exchangesNow }
            if (lacking.isNotEmpty()) fail("The count rose to $expected but no ${lacking.joinToString("/")} row is listed", Cause.UNCONFIRMED)
            note("${added.size} added, the watchlist shows ${added.size + start} instrument(s)")
            shot("segment_types_added")
        } catch (e: CaseEnd) {
            failure = e
        }
        // Every instrument this case added is taken out again, whatever happened above.
        val restored = removeAdded(added, start) || removeExtrasOnEdit(baseline, start)
        cleanup = if (restored) "restored: the ${added.size} instrument(s) added by this case are removed (count back to $start)"
        else "FAILED: instruments added by this case may still be in the watchlist (count should be $start, reads ${readCount()?.first}); remove them by hand"
        failure?.let { throw it }
        if (!restored) fail("The instruments were added but could not all be removed again", Cause.TEST_DATA)
    }

    /**
     * Removes one instrument this case added from the open search results (scrolling to it if it lies below the first
     * rows). A row that is mid-reload shows a spinner instead of its tick; the row is read again until it shows a tick
     * or a +, never skipped on a transient look. True when the instrument is not in the watchlist afterwards.
     */
    private fun removeOne(a: Added): Boolean {
        val key = labelKey(a.label)
        fun find(): Int = resultRows().indexOfFirst { labelKey(it) == key }
        var idx = find()
        var scrolls = 0
        while (idx < 0 && scrolls < 3 && engine.scrollPage(forward = true)) {
            scrolls++
            pause(Timeouts.SETTLE)
            idx = find()
        }
        if (idx < 0) return false
        var state: String? = null
        waitUntil(UI) {
            val i = find()
            if (i >= 0) {
                idx = i
                state = when {
                    engine.hasDescendantAt(T.SEARCH_RESULT_ROW, i, T.SEARCH_SCRIP_ADDED_ICON) -> "added"
                    engine.hasDescendantAt(T.SEARCH_RESULT_ROW, i, T.SEARCH_SCRIP_ADD_BUTTON) -> "removed"
                    else -> null
                }
            }
            state != null
        }
        if (state == "added") deleteResult(idx, a.name)
        return state != null
    }

    /**
     * Fallback when the tick in search could not take an instrument out again: the Edit screen lists the whole watchlist,
     * so every row that was not among [baseline] (the rows before the case) is deleted there and saved. Only done when
     * the number of such rows is exactly what the count says is extra, so an original instrument is never deleted.
     */
    private fun removeExtrasOnEdit(baseline: List<String>, start: Int): Boolean {
        if (baseline.size != start) return false
        try {
            if (!recover()) return false
            val now = readCount()?.first ?: return false
            if (now == start) return true
            if (now < start) return false
            openEdit()
            val known = baseline.map { b -> tokens(b).map { it.uppercase() }.toSet() }
            val rows = editRows()
            val extra = rows.indices.filter { i ->
                val t = tokens(rows[i]).map { it.uppercase() }.filterNot { PRICE.matches(it) || it.contains('%') }.toSet()
                known.none { it.containsAll(t) }
            }
            if (rows.size != now || extra.size != now - start) {
                escapeEdit()
                return false
            }
            for (i in extra.sortedDescending()) {
                if (!engine.tapAt(T.REORDER_DELETE_BUTTON, i).ok) {
                    escapeEdit()
                    return false
                }
                pause(Timeouts.SETTLE)
            }
            if (editRows().size != start) {
                escapeEdit()
                return false
            }
            saveEdit("removing the instruments added by the segment case")
            return waitUntil(LOAD) { readCount()?.first == start }
        } catch (_: CaseEnd) {
            return false
        }
    }

    private fun removeAdded(added: List<Added>, start: Int): Boolean {
        if (added.isEmpty()) return true
        try {
            // One search per (category, query): the instruments of one search are removed together. A second pass only
            // when the count shows something is left, and then only the instruments that still show a tick.
            repeat(2) {
                if (!engine.isPresent(T.SEARCH_SCREEN)) {
                    if (!recover()) return false
                    openSearch()
                }
                for ((_, group) in added.groupBy { it.wanted.tab to it.query }) {
                    val first = group.first()
                    searchQuery(first.wanted.tab, first.wanted.tabName, first.query)
                    group.forEach { removeOne(it) }
                }
                leaveSearch()
                if (waitUntil(UI) { readCount()?.first == start }) return true
            }
            return false
        } catch (_: CaseEnd) {
            return false
        }
    }

    private fun addResult(row: Int, name: String) {
        val tap = engine.tapDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADD_BUTTON)
        if (!tap.ok) fail("Could not tap + for $name: ${tap.detail}")
        confirm(row, name, becomes = T.SEARCH_SCRIP_ADDED_ICON, leaves = T.SEARCH_SCRIP_ADD_BUTTON, what = "add")
    }

    private fun deleteResult(row: Int, name: String) {
        val tap = engine.tapDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)
        if (!tap.ok) fail("Could not tap the tick for $name: ${tap.detail}")
        confirm(row, name, becomes = T.SEARCH_SCRIP_ADD_BUTTON, leaves = T.SEARCH_SCRIP_ADDED_ICON, what = "remove")
    }

    /**
     * Waits for result [row] to turn from one icon into the other. A row that goes blank (a request in flight) and
     * then returns to its old icon means the app refused the change.
     */
    private fun confirm(row: Int, name: String, becomes: String, leaves: String, what: String) {
        var inFlight = false
        var refused = false
        val done = waitUntil(LOAD) {
            when {
                engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, becomes) -> true
                !engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, leaves) -> {
                    inFlight = true
                    false
                }
                inFlight -> {
                    refused = true
                    true
                }
                else -> false
            }
        }
        if (refused) fail("The $what request for $name finished but the row went back to its old icon: the app refused it")
        if (!done) fail("Tapping to $what $name did not change its icon within ${LOAD / 1000} s")
    }

    private fun capacityLimit() {
        val count = startCount
        if (count == null || count < capacity) {
            notTested("No full watchlist is available (the working one holds ${count ?: "?"} / $capacity); the tool does not create $capacity instruments to test the limit.")
        }
        ensureSearchOpen()
        searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", "TCS")
        val row = resultRows().indices.firstOrNull { engine.hasDescendantAt(T.SEARCH_RESULT_ROW, it, T.SEARCH_SCRIP_ADD_BUTTON) }
            ?: blocked("Every TCS result is already in the full watchlist.")
        engine.tapDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADD_BUTTON)
        pause(UI)
        if (engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)) {
            // The limit was not enforced: put things back.
            engine.tapDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)
            fail("An instrument was added to a full watchlist ($count / $capacity): the limit is not enforced")
        }
        note("the add was refused on a full watchlist ($count / $capacity); app message: ${texts(30).firstOrNull { it.contains("50") || it.contains("limit", true) || it.contains("maximum", true) } ?: "none readable"}")
    }

    // ═══ Edit watchlist ════════════════════════════════════════════════════════════════════════

    private fun editRows(): List<String> = engine.groupedLabelsOf(T.REORDER_ROW).map { it.trim() }.filter { it.isNotEmpty() }

    private fun openEdit() {
        ensureLanding()
        toWorkTab()
        val anchor = if (engine.isPresent(T.WATCHLIST_SCRIP_ROW)) T.WATCHLIST_SCRIP_ROW else needWork()
        val open = engine.tapAndConfirm(anchor, listOf(T.REORDER_SCREEN), UI, retryAfterMs = RETRY, longPress = true)
        alive()
        if (!open.ok) fail("Long-pressing '$anchor' did not open the Edit screen: ${open.detail}")
    }

    /** True when the last Save started no request at all (the label stayed, no dialog, no message). */
    private var lastSaveNoop = false

    /** Leaves the Watchlist tab and returns to it, so the app reloads its watchlists; the working tab is selected again. */
    private fun reloadWatchlist() {
        engine.tap(T.BOTTOMNAV_ORDER_TAB)
        waitUntil(UI) { !engine.isPresent(T.WATCHLIST_SCREEN) }
        ensureLanding()
        selectTab(needWork())
    }

    /** True once a Save was tapped in this case; a second tap is never made to "try again". */
    private var saveTapped = false

    private val validationHints = listOf("already exists", "can't be empty", "cannot be empty", "please enter", "invalid", "not allowed")

    /**
     * Taps Save once. If the app answers with the "Do you want to save changes?" dialog, that dialog's own Save is
     * tapped (never Discard). It then waits for the resulting state: the Edit screen closing, or a validation
     * message. No fixed wait: the bound is [Timeouts.EDIT_SAVE] only because the rename is a real server call.
     * When the screen stays, the report gets the request state, the validation text and a screenshot.
     */
    private fun saveEdit(what: String) {
        val save = engine.tap(T.REORDER_SAVE_BUTTON)
        if (!save.ok) {
            escapeEdit()
            fail("Save could not be tapped after $what: ${save.detail}", Cause.AUTOMATION)
        }
        saveTapped = true
        lastSaveNoop = false
        var validation: String? = null
        var confirmed = false
        var reacted = false
        val tappedAt = SystemClock.elapsedRealtime()
        // While a request runs, Save shows a spinner instead of its label.
        fun spinner() = engine.isPresent(T.REORDER_SAVE_BUTTON) && engine.labelsOf(T.REORDER_SAVE_BUTTON).none { it.contains("Save", true) }
        waitUntil(Timeouts.EDIT_SAVE) {
            if (!engine.isPresent(T.REORDER_SCREEN)) return@waitUntil true
            if (!confirmed && engine.isPresent(T.REORDER_DISCARD_BUTTON) && engine.isPresent(T.REORDER_CONFIRM_SAVE_BUTTON)) {
                // The confirmation dialog: confirm with its Save, once.
                confirmed = engine.tap(T.REORDER_CONFIRM_SAVE_BUTTON).ok
                if (confirmed) note("confirmed the 'Do you want to save changes?' dialog with Save")
                reacted = true
                return@waitUntil false
            }
            validation = readScreen().firstOrNull { line -> validationHints.any { line.contains(it, ignoreCase = true) } }
            if (validation != null) return@waitUntil true
            if (spinner()) reacted = true
            // Nothing happened within the normal UI time: no request was started, so waiting 20 s would only wait for nothing.
            !reacted && SystemClock.elapsedRealtime() - tappedAt > UI
        }
        val closed = !engine.isPresent(T.REORDER_SCREEN)
        if (!closed) {
            val inFlight = spinner()
            val field = engine.watchlistName()?.trim()
            val hint = readScreen().filterNot { it.equals("Edit watchlist", true) }.take(10).joinToString(" | ")
            val evidence = "screen: ${screenName()}; name field reads '${field.orEmpty()}'; " + snapshot()
            evidenceShot("save_stuck")
            val noop = validation == null && !reacted
            lastSaveNoop = noop
            escapeEdit()
            val why = when {
                validation != null -> "the app refused it: '${validation}'"
                noop -> "Save did nothing: no request started (its label stayed, no dialog, no message) within ${UI / 1000} s"
                else -> "no answer within ${Timeouts.EDIT_SAVE / 1000} s"
            }
            throw CaseEnd(
                Kind.FAIL,
                "Save after $what did not complete: $why; request in flight: ${if (inFlight) "yes (Save shows a spinner)" else "no"}; " +
                    "save-changes dialog ${if (confirmed) "was confirmed with Save" else "did not appear"}; " +
                    "name field reads '${field.orEmpty()}'; screen showed: ${hint.ifEmpty { "nothing readable" }}",
                if (validation != null) Cause.APP else if (inFlight) Cause.API else Cause.UNCONFIRMED,
                evidence,
                if (validation != null) "Developers: the rename validation refused a valid change; see the message above." else null,
            )
        }
        if (!waitUntil(UI) { engine.isPresent(T.WATCHLIST_SCREEN) }) fail("The Watchlist did not come back after saving $what")
    }

    private fun tabLabel(): String = engine.labelsOf(needWork()).map(::tabName).firstOrNull { it.isNotBlank() }.orEmpty()

    private fun editCase(id: String) {
        when (id) {
            "wl_edit_rename" -> rename()
            "wl_edit_reorder" -> reorder()
            "wl_edit_delete" -> deleteTest()
        }
    }

    private fun rename() {
        // 1-2. Read and keep the exact original name, then rename once.
        ensureLanding()
        toWorkTab()
        val shownBefore = tabLabel()
        openEdit()
        val original = engine.watchlistName().orEmpty()
        if (original.isBlank()) {
            escapeEdit()
            fail("The watchlist's current name could not be read on the Edit screen; it was left alone", Cause.AUTOMATION)
        }
        // A name that cannot be mistaken for the original or for another watchlist: "Wl" + ddMMyHHmmss (day, month, year,
        // hour, minute, second), e.g. Wl25102026105230. The name field takes letters, digits and spaces, 25 at most.
        val temporary = java.text.SimpleDateFormat("'Wl'ddMMyHHmmss", java.util.Locale.US).format(java.util.Date())
            .let { if (it.trim() == original.trim()) "${it}A".take(25) else it }
        typeName(temporary)?.let { problem ->
            escapeEdit()
            cleanup = "nothing changed"
            fail("The new name was not entered correctly: $problem", Cause.AUTOMATION)
        }
        shot("edit_watchlist")
        // 3. Save and verify the new name (the tab's "Tab 1 of 6" line is not part of the name).
        saveTapped = false
        var problem: CaseEnd? = null
        try {
            saveEdit("renaming '${original.trim()}'")
            if (!waitUntil(LOAD) { tabLabel() == temporary.trim() }) {
                problem = CaseEnd(Kind.FAIL, "After Save the tab reads '${tabLabel()}' instead of '$temporary'", Cause.APP, snapshot())
            }
        } catch (e: CaseEnd) {
            problem = e
        }
        val renamed = tabLabel() == temporary.trim()
        if (!renamed && !saveTapped) {
            cleanup = "nothing changed"
            throw problem ?: CaseEnd(Kind.FAIL, "The rename was not applied")
        }
        if (!renamed && tabLabel() == original.trim()) {
            // Save was refused: nothing changed, so there is nothing to restore.
            cleanup = "nothing changed (the rename was refused)"
            throw problem!!
        }
        // 4-7. Reopen Edit, put back the exact original, Save once and wait for it to finish. If the screen could not
        // be reached or typed into (no Save made), one more attempt; if a Save was made and failed it is not repeated.
        var restoreProblem: String? = null
        var restoreEnd: CaseEnd? = null
        var noopSeen = false
        for (attempt in 1..2) {
            if (tabLabel() == original.trim()) break
            saveTapped = false
            lastSaveNoop = false
            try {
                openEdit()
                val shown = engine.watchlistName()?.trim()
                if (shown == original.trim()) note("the Edit screen already shows '${original.trim()}' in its name field while the watchlist is named '${tabLabel().ifEmpty { temporary.trim() }}'")
                val problem = typeName(original)
                if (problem != null) {
                    escapeEdit()
                    restoreProblem = "the original name was not entered correctly: $problem"
                    continue
                }
                saveEdit("restoring '${original.trim()}'")
                // 8. Back on the Watchlist, verify the original name is what the tab shows.
                if (!waitUntil(LOAD) { tabLabel() == original.trim() }) restoreProblem = "the tab reads '${tabLabel()}' after Save"
            } catch (e: CaseEnd) {
                restoreEnd = e
                restoreProblem = e.message
            }
            // A Save that was made and answered is never repeated. A Save that did nothing (no request started) is tried
            // once more, but only after the app has reloaded its watchlist (leave the tab and come back), so the second
            // attempt starts from different state, not from the same one.
            if (lastSaveNoop) noopSeen = true
            if (saveTapped && !lastSaveNoop) break
            if (lastSaveNoop && attempt == 1) reloadWatchlist()
        }
        // 9. Cleanup is reported on its own.
        val restored = tabLabel() == original.trim()
        cleanup = if (restored) "restored: '${original.trim()}' (was '$shownBefore' at the start)"
        else "FAILED: the watchlist still reads '${tabLabel()}'; rename it back to '${original.trim()}' by hand (${restoreProblem.orEmpty()})"
        if (!restored) {
            throw CaseEnd(
                Kind.FAIL,
                "The rename to '$temporary' worked but the original name '${original.trim()}' could NOT be restored: ${restoreProblem.orEmpty()}",
                if (lastSaveNoop) Cause.APP else restoreEnd?.category ?: Cause.UNCONFIRMED, restoreEnd?.evidence ?: snapshot(),
                if (lastSaveNoop) "Developers: after a rename, Save on the Edit screen does nothing when the name is typed back to the previous one (the screen compares it with a remembered name; see reorder_screen.dart onChanged / oldMwName)." else restoreEnd?.recommendation,
            )
        }
        if (problem != null) throw problem
        if (noopSeen) {
            throw CaseEnd(
                Kind.FAIL,
                "The watchlist was renamed to '$temporary' and restored to '${original.trim()}', but the first Save of the previous name did " +
                    "nothing (no request started, no message); it worked only after the watchlist was reloaded (Orders tab and back).",
                Cause.APP, "screen: ${screenName()}; " + snapshot(),
                "Developers: after a rename, typing the previous name back leaves Save inert until the watchlist is reloaded " +
                    "(reorder_screen.dart compares the typed name with oldMwName; market_provider.dart marketWatchName()).",
            )
        }
        note("renamed '${original.trim()}' to '${temporary.trim()}' (the tab showed it) and back")
    }

    /**
     * Types [text] into the Edit screen's name field and checks that the field reads exactly that: the keyboard has been
     * seen to leave extra characters behind (a field reading 'testQRfh' after 'testQR'). One corrective retype, only
     * when the field does not match; null when it does, else what the field reads.
     */
    private fun typeName(text: String): String? {
        repeat(2) {
            val typed = engine.enterText(T.REORDER_RENAME_INPUT, text)
            if (!typed.ok) return "the name could not be typed: ${typed.detail}"
            val read = engine.watchlistName()?.trim()
            if (read == text.trim()) return null
            if (it == 1) return "the name field reads '${read.orEmpty()}' instead of '${text.trim()}'"
        }
        return null
    }

    private fun stillPresent(r: Rep): Boolean = findRow(r) >= 0

    /** Adds one instrument for the Edit cases to move and delete, unless the run already has one. */
    private fun ensureTestScrip(): Rep {
        testScrip?.let { if (engine.isPresent(T.WATCHLIST_SCREEN) && stillPresent(it)) return it }
        testScrip = null
        ensureLanding()
        toWorkTab()
        val count = readCount()
        if (count != null && count.first >= count.second) {
            blocked("The watchlist is full (${count.first} / ${count.second}), so no test instrument can be added and the Edit cases would have to touch the user's own instruments.")
        }
        openSearch()
        // A plain equity ('-EQ') that is not in the list yet: searching TCS lists bonds such as 750TCSL28-N0 before TCS-EQ,
        // and a bond would fill the account with instruments that have no data.
        for (query in listOf("RELIANCE", "TCS", "HDFCBANK")) {
            searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", query)
            val rows = resultRows()
            val row = rows.indices.firstOrNull {
                exchangeOf(rows[it]) == "NSE" && nameOf(rows[it]) == "$query-EQ" &&
                    engine.hasDescendantAt(T.SEARCH_RESULT_ROW, it, T.SEARCH_SCRIP_ADD_BUTTON)
            }
            if (row != null) {
                val name = nameOf(rows[row]) ?: continue
                val r = Rep(name, "NSE")
                // Recorded before the add, so the final clean-up knows about it whatever happens next.
                testScrip = r
                addResult(row, name)
                leaveSearch()
                if (!waitUntil(LOAD) { rowLabels().any { sameScrip(it, name, "NSE") } }) fail("The test instrument $name was added but is not in the watchlist")
                note("added $name as the test instrument")
                return r
            }
        }
        if (engine.isPresent(T.SEARCH_SCREEN)) flow.closeWithBack(T.SEARCH_SCREEN)
        blocked("No test instrument could be added: RELIANCE-EQ, TCS-EQ and HDFCBANK-EQ are already in the watchlist or offer no +.")
    }

    private fun rowIndexIn(edit: List<String>, r: Rep): Int =
        edit.indexOfFirst { line -> tokens(line).any { baseName(it) == baseName(r.name) } }

    private fun reorder() {
        val test = ensureTestScrip()
        openEdit()
        val before = editRows()
        val index = rowIndexIn(before, test)
        if (index < 0) fail("The test instrument ${test.name} is not on the Edit screen: ${before.take(5).joinToString(" | ")}")
        if (before.size < 2) blocked("The watchlist holds only the test instrument, so there is nothing to reorder it against.")
        val direction = if (index == before.lastIndex) "Move up" else "Move down"
        val target = if (direction == "Move up") index - 1 else index + 1
        val move = engine.customActionAt(T.REORDER_ROW, index, direction)
        if (!move.ok) {
            escapeEdit()
            fail("'$direction' could not be performed on ${test.name}: ${move.detail}")
        }
        if (!waitUntil(UI) { rowIndexIn(editRows(), test) == target }) {
            escapeEdit()
            fail("After '$direction' the test instrument should be at position ${target + 1} but is at ${rowIndexIn(editRows(), test) + 1}")
        }
        saveEdit("reordering ${test.name}")
        if (!waitUntil(LOAD) { rowIndexIn(rowLabels().map { it }, test) == target }) {
            fail("The new order was not kept: ${test.name} is at position ${rowIndexIn(rowLabels(), test) + 1}, expected ${target + 1}")
        }
        note("${test.name} moved from position ${index + 1} to ${target + 1} and the watchlist shows it")
        cleanup = "pending: the test instrument ${test.name} added for this case is removed by WL-32"
    }

    private fun deleteTest() {
        val test = ensureTestScrip()
        val countBefore = readCount()?.first
        openEdit()
        val rows = editRows()
        val index = rowIndexIn(rows, test)
        if (index < 0) fail("The test instrument ${test.name} is not on the Edit screen: ${rows.take(5).joinToString(" | ")}")
        val tap = engine.tapAt(T.REORDER_DELETE_BUTTON, index)
        if (!tap.ok) {
            escapeEdit()
            fail("The delete icon of ${test.name} could not be tapped: ${tap.detail}")
        }
        if (!waitUntil(UI) { rowIndexIn(editRows(), test) < 0 }) {
            escapeEdit()
            fail("${test.name} did not disappear from the Edit screen after its delete icon was tapped")
        }
        saveEdit("deleting ${test.name}")
        if (!waitUntil(LOAD) { findRow(test) < 0 }) fail("${test.name} is still in the watchlist after Save")
        testScrip = null
        cleanup = "restored: the test instrument ${test.name} added by this run is removed"
        val countAfter = readCount()?.first
        if (countBefore != null && countAfter != null && countAfter != countBefore - 1) {
            fail("${test.name} is gone but the count went from $countBefore to $countAfter instead of ${countBefore - 1}")
        }
        note("${test.name} deleted and gone from the watchlist" + if (countBefore != null && countAfter != null) " (count $countBefore → $countAfter)" else "")
    }

    /** Removes the test instrument if an earlier Edit case left it behind. */
    private fun cleanupTestScrip() {
        val test = testScrip ?: return
        if (!recover()) return
        if (!stillPresent(test)) {
            testScrip = null
            return
        }
        openSearch()
        searchQuery(T.SEARCH_TAB_STOCKS, "Stocks", test.name.substringBefore('-'))
        val rows = resultRows()
        val row = rows.indexOfFirst { sameScrip(it, test.name, test.exchange) && nameOf(it)?.endsWith("-EQ") == true }
        if (row >= 0 && engine.hasDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)) {
            engine.tapDescendantAt(T.SEARCH_RESULT_ROW, row, T.SEARCH_SCRIP_ADDED_ICON)
            pause(UI)
        }
        leaveSearch()
        if (!stillPresent(test)) testScrip = null
    }

    // ═══ WL-90: put-back check ═════════════════════════════════════════════════════════════════

    private fun stateRestored() {
        ensureLanding()
        toWorkTab()
        val problems = mutableListOf<String>()
        val name = initialName
        if (name != null && tabLabel() != name) problems += "the name reads '${tabLabel()}' instead of '$name'"
        val count = readCount()?.first
        if (startCount != null && count != null && count != startCount) problems += "the instrument count is $count instead of $startCount"
        val grid = initialGrid
        if (grid != null && engine.isPresent(T.WATCHLIST_GRID_VIEW) != grid) {
            problems += "the tiles show as ${if (grid) "a list" else "a grid"} instead of ${if (grid) "a grid" else "a list"}"
        }
        if (testScrip != null) problems += "the test instrument ${testScrip!!.name} is still in the watchlist"
        cleanup = if (problems.isEmpty()) "restored" else "FAILED: ${problems.joinToString("; ")}"
        if (problems.isNotEmpty()) {
            fail("The working watchlist was not put back: ${problems.joinToString("; ")}", Cause.TEST_DATA,
                "Set the watchlist back by hand before the next run, or the next run will take this state as its starting point.")
        }
        note("name '${name.orEmpty()}', ${count ?: "?"} instrument(s) and the original tile layout are back")
    }

    // ═══ Manage Watchlist (settings) ═══════════════════════════════════════════════════════════

    private val radioGroups = T.WATCHLIST_RADIO_GROUPS.map { group -> group.map { "${T.WATCHLIST_RADIO_PREFIX}$it" } }
    private val everyRadio = radioGroups.flatten()

    private fun openSettings() {
        ensureLanding()
        val tab = settingsTab ?: needWork()
        if (!engine.isPresent(tab)) selectTab(tab)
        tapOpen(T.WATCHLIST_FILTER_BUTTON, listOf(T.WATCHLIST_FILTER_SCREEN), "The settings icon did not open Manage Watchlist")
        // The Depth card preview animates open (500 ms) and moves the radios below it.
        pause(Timeouts.SETTLE)
    }

    private fun applySettings() {
        val apply = engine.tap(T.WATCHLIST_FILTER_APPLY_BUTTON)
        if (!apply.ok) fail("Apply could not be tapped: ${apply.detail}")
        if (!waitUntil(LOAD) { !engine.isPresent(T.WATCHLIST_FILTER_SCREEN) }) fail("Manage Watchlist did not close after Apply")
        if (!waitUntil(UI) { engine.isPresent(T.WATCHLIST_SCREEN) }) fail("The Watchlist did not come back after Apply")
    }

    private fun radioName(id: String) = id.removePrefix(T.WATCHLIST_RADIO_PREFIX)

    /** True once [id] is on screen (scrolled to if needed) and reports itself selected. */
    private fun chosen(id: String, timeoutMs: Long = UI): Boolean =
        waitUntil(timeoutMs) { engine.revealInList(id, everyRadio) && engine.isSelected(id) }

    /** The radio of each group that is selected now, or null where none reports it. */
    private fun selections(): List<String?> =
        radioGroups.map { ids -> ids.firstOrNull { engine.revealInList(it, everyRadio) && engine.isSelected(it) } }

    private fun choose(id: String) {
        engine.revealInList(id, everyRadio)
        val tap = engine.tap(id)
        if (!tap.ok) fail("'${radioName(id)}' could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        // A tap made while the screen is still settling is lost without any sign (seen in the recording: the Tile radio
        // stayed put for 3 s). The radio either reports itself selected within a second or the tap is made once more.
        if (chosen(id, 1_000)) return
        val again = engine.tap(id)
        if (!again.ok) fail("'${radioName(id)}' could not be tapped: ${again.detail}", Cause.AUTOMATION)
        if (!chosen(id)) fail("'${radioName(id)}' was tapped twice but does not report itself selected", Cause.UNCONFIRMED)
    }

    private fun settingsCase(id: String) {
        when (id) {
            "wl_settings_view" -> settingsView()
            "wl_settings_filter_sort" -> settingsFilterSort()
        }
    }

    private fun settingsView() {
        // The layout is read from the Watchlist itself, before Manage Watchlist covers it.
        ensureLanding()
        val startedAsGrid = engine.isPresent(T.WATCHLIST_GRID_VIEW)
        // Depth, Grid and Open Price: the first option of each group that is not the current one.
        openSettings()
        val original = selections()
        note("started as " + original.joinToString(" / ") { it?.let(::radioName) ?: "unreadable" } + if (startedAsGrid) " (grid showing)" else " (list showing)")
        shot("manage_watchlist")
        val defaults = listOf("basic", "list", "close_price").map { "${T.WATCHLIST_RADIO_PREFIX}$it" }
        val target = defaults.indices.map { g -> original[g] ?: defaults[g] }
        val wanted = listOf(
            radioGroups[0].firstOrNull { it != original[0] && it.endsWith("depth") } ?: radioGroups[0].first { it != target[0] },
            radioGroups[1].firstOrNull { it != original[1] && it.endsWith("grid") } ?: radioGroups[1].first { it != target[1] },
            radioGroups[2].firstOrNull { it != original[2] && it.endsWith("open_price") } ?: radioGroups[2].first { it != target[2] },
        )
        var problem: String? = null
        var persistDefect = false
        try {
            wanted.forEach { choose(it) }
            applySettings()
            val wantGrid = wanted[1].endsWith("grid")
            if (!waitUntil(UI) { engine.isPresent(T.WATCHLIST_GRID_VIEW) == wantGrid }) {
                problem = "Apply was tapped but the list did not turn into a ${if (wantGrid) "grid" else "list"}"
            }
            openSettings()
            // The state either shows or it does not: one second is enough to read it (not the full UI wait per radio).
            val lost = wanted.filterNot { chosen(it, 400) }
            if (lost.isNotEmpty()) {
                val shownNow = lost.map { id ->
                    radioGroups.first { id in it }.firstOrNull { engine.isSelected(it) }?.let(::radioName) ?: "nothing"
                }
                // The Watchlist itself showed the change (checked above), so a screen that disagrees with it is the app
                // contradicting itself: the layout changed, the settings screen still shows the old choice.
                persistDefect = problem == null
                problem = (problem?.plus("; ") ?: "") + "Apply changed the watchlist but reopening Manage Watchlist does not show " +
                    lost.joinToString { radioName(it) } + " as selected (it shows " + shownNow.joinToString() + ")"
            }
            shot("manage_watchlist_changed")
        } catch (e: CaseEnd) {
            problem = e.message
        }
        // Put the original choices back, on the screen that is already open. The list layout is the proof that the Tile
        // choice is back; reopening Manage Watchlist to look again would show its stale Tile state (the defect above)
        // and a second Apply from there could undo the restore.
        var restoreProblem: String? = null
        for (attempt in 1..2) {
            try {
                if (!engine.isPresent(T.WATCHLIST_FILTER_SCREEN)) openSettings()
                if (attempt == 2) {
                    // Selecting the other option first forces a real change if the first attempt did nothing.
                    wanted.forEachIndexed { g, id -> if (target[g] != id) choose(id) }
                }
                target.forEach { choose(it) }
                applySettings()
                val layoutBack = waitUntil(UI) { engine.isPresent(T.WATCHLIST_GRID_VIEW) == startedAsGrid }
                restoreProblem = if (layoutBack) null else "the list is still showing as ${if (startedAsGrid) "a list" else "a grid"}"
                if (restoreProblem == null) break
            } catch (e: CaseEnd) {
                restoreProblem = e.message
            }
        }
        cleanup = if (restoreProblem == null) "restored: " + target.joinToString(" / ") { radioName(it) }
        else "FAILED: ${restoreProblem}; set " + target.joinToString(" / ") { radioName(it) } + " by hand"
        if (restoreProblem != null) {
            fail("The Manage Watchlist settings were changed and could NOT be restored (${restoreProblem}); set " +
                target.joinToString(" / ") { radioName(it) } + " by hand. ${problem.orEmpty()}")
        }
        if (problem != null) fail(problem, if (persistDefect) Cause.APP else null,
            if (persistDefect) "Developers: the tile / view choice is applied to the list but not kept for the Manage Watchlist screen (compare isWSListView with pref.isListView in market_provider.dart)." else null)
        note("Depth, Grid and Open Price were applied and persisted, then the original choices were restored" +
            if (original.any { it == null }) " (the screen did not report every original choice, so defaults were used)" else "")
    }

    private fun settingsFilterSort() {
        // Sorting needs at least two instruments to show an order. If the working watchlist has fewer, use the other
        // watchlist WL-02 already saw with two or more (its tab is selected again at the end).
        val work = needWork()
        toWorkTab()
        if (rowLabels().size < 2) {
            val richer = tabRows.entries.firstOrNull { it.key != work && it.value >= 2 }?.key
            if (richer != null) {
                selectTab(richer)
                settingsTab = richer
                note("sorting is checked on '$richer' (${tabRows[richer]} instruments); '${work}' holds fewer than two")
            }
        }
        try {
            settingsFilterSortOn()
        } finally {
            if (settingsTab != null) {
                settingsTab = null
                try {
                    recover()
                    selectTab(work)
                } catch (_: CaseEnd) {
                }
            }
        }
    }

    private fun settingsFilterSortOn() {
        val before = rowLabels()
        val baseline = before.mapNotNull { nameOf(it) }.take(8)
        val exchanges = before.mapNotNull { exchangeOf(it) }.distinct()
        var problem: String? = null
        var sortChecked = false
        try {
            // Sort: alphabetical.
            openSettings()
            val tap = engine.tap("watchlist_sort_alphabetical")
            if (!tap.ok) fail("The alphabetical sort could not be tapped: ${tap.detail}")
            applySettings()
            val names = rowLabels().mapNotNull { nameOf(it) }
            if (names.size >= 2) {
                sortChecked = true
                val sorted = names.sortedBy { it.lowercase() }
                if (names != sorted) problem = "Alphabetical sort was applied but the list reads ${names.take(4).joinToString()} instead of ${sorted.take(4).joinToString()}" // an application defect: two or more rows were readable
                else note("alphabetical order verified on ${names.size} row(s)")
            } else {
                note("only ${names.size} row(s), so the sort order could not be seen")
            }
            // Filter: the chips are the exchanges the watchlist holds, so use one it has (INDEX for indices).
            val exchange = exchanges.firstOrNull()
            if (exchange == null) {
                note("the watchlist has no instrument, so no filter chip exists")
            } else {
                val chipText = if (exchange == "INDICES") "INDEX" else exchange
                val chipId = "${T.WATCHLIST_FILTER_CHIP_PREFIX}${chipText.lowercase()}"
                openSettings()
                // The Filter section is generated from the exchanges of the instruments in the list (plus INDEX when
                // an index is in it), so the chip to use is one the list can have.
                if (!engine.isPresent(chipId) && !engine.revealInList(chipId, listOf(chipId))) {
                    blocked(
                        "The Filter section has no '$chipText' chip (identifier '$chipId'); the installed Tradion build may predate this identifier.",
                        Cause.AUTOMATION, "Rebuild Tradion with the 'watchlist_filter_chip_<exchange>' tags (docs/AUTOMATION_SEMANTICS.md).",
                    )
                }
                if (!engine.isTappable(chipId)) fail("The '$chipText' chip is on screen but not tappable", Cause.AUTOMATION)
                val chip = engine.tap(chipId)
                if (!chip.ok) fail("The '$chipText' filter chip could not be tapped: ${chip.detail}", Cause.AUTOMATION)
                if (!waitUntil(UI) { engine.isSelected(chipId) }) {
                    fail("The '$chipText' chip was tapped but does not show as selected", Cause.APP)
                }
                note("'$chipText' chip selected")
                applySettings()
                val shown = rowLabels()
                val other = shown.mapNotNull { exchangeOf(it) }.filter { it != exchange }
                when {
                    other.isNotEmpty() -> problem = (problem?.plus("; ") ?: "") + "the $chipText filter was applied but ${other.toSet().joinToString("/")} instruments are still listed"
                    shown.isEmpty() -> problem = (problem?.plus("; ") ?: "") + "the $chipText filter was applied and emptied a list that holds $chipText instruments"
                    else -> note("$chipText filter left ${shown.size} of ${before.size} row(s), all $exchange" +
                        if (exchanges.size == 1) " (the only exchange here, so the list cannot shrink)" else "")
                }
                shot("settings_filtered")
            }
        } catch (e: CaseEnd) {
            problem = e.message
        }
        // Clear should bring the default order and the full list back.
        try {
            openSettings()
            val clear = engine.tap(T.WATCHLIST_FILTER_RESET_BUTTON)
            if (!clear.ok) fail("Clear could not be tapped: ${clear.detail}")
            if (!waitUntil(UI) { !engine.isPresent(T.WATCHLIST_FILTER_SCREEN) }) fail("Manage Watchlist did not close after Clear")
            if (!waitUntil(LOAD) { engine.isPresent(T.WATCHLIST_SCRIP_ROW) }) fail("The list did not come back after Clear")
            val after = rowLabels().mapNotNull { nameOf(it) }.take(8)
            if (baseline.isNotEmpty() && after != baseline) {
                problem = (problem?.plus("; ") ?: "") + "Clear did not restore the original list (before: ${baseline.take(4).joinToString()}; after: ${after.take(4).joinToString()})"
            } else {
                note("Clear restored the original list")
            }
        } catch (e: CaseEnd) {
            problem = (problem?.plus("; ") ?: "") + "Clear: ${e.message}"
        }
        if (problem != null) fail(problem)
        if (!sortChecked) notTested("The sort order could not be checked: this watchlist holds fewer than two instruments. The filter and Clear were checked (${notes.joinToString("; ")}).")
    }
}

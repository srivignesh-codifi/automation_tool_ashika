package com.mobileautomation.tool

import android.os.SystemClock
import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T

/**
 * The Dashboard module of the Tradion regression pass: the Trading and Investments tabs as independent test cases
 * (DB-01 ... DB-I25), each reached once, each verified by its content and not merely by a screen opening.
 *
 * Rules it keeps:
 *
 * * **Each distinct feature is visited once.** Shared screens (the instrument sheet, the chart, Option Chain) are
 *   only opened far enough to prove the Dashboard reaches them; their own coverage lives in the Watchlist suite.
 * * **A normal interaction answers within [UI] (3 s).** Server data gets [LOAD], an SDK or WebView [SDK]; the time
 *   a dependency took is recorded on the case.
 * * **Every case is independent.** It ends PASS, FAIL, BLOCKED, NOT APPLICABLE or NOT TESTED and the app is brought
 *   back to the Dashboard (closing only what the case opened); a failure never cascades.
 * * **Nothing is traded, applied for, paid, pledged or requested.** Order pads, IPO applications, Add Funds and
 *   Withdraw are opened and left with Back; the Customize screens are looked at, not saved; the one alert this suite
 *   creates is the owner-approved `AUTOCHECK` test alert, deleted again in the same case.
 * * **A screen the tool cannot read is not a defect.** No readable content is BLOCKED (accessibility), a missing label
 *   on a readable screen is "needs review", and only a contradiction between what was done and what the screen shows
 *   is an application defect.
 */
class DashboardSuite(
    private val flow: FlowBase,
    private val engine: AutomationEngine,
    private val stopped: (AutomationStepResult) -> AutomationRunResult,
    private val sessionLost: (AutomationStepResult) -> AutomationRunResult?,
) {

    class Case(
        val code: String,
        val id: String,
        val name: String,
        val expected: String,
        val severity: String,
    ) {
        fun toStep(): AutomationStepResult = AutomationStepResult(id, name).also {
            it.code = code
            it.expected = expected
            it.severity = severity
            it.module = "dashboard"
        }
    }

    companion object {
        private fun c(code: String, id: String, name: String, expected: String, severity: String = "Medium") =
            Case(code, id, name, expected, severity)

        /** In run order. */
        val CASES: List<Case> = listOf(
            c("DB-01", "db_initial_load", "Dashboard initial load",
                "The Dashboard opens with the index ticker (NIFTY 50 and a second index with LTP and % change), the Trading and Investments tabs and the bottom navigation, and its data finishes loading.", "Critical"),
            c("DB-02", "db_index_panel", "Top indices panel",
                "The ticker arrow opens a panel with the indices and Funds / Equity, and closing it returns to the same Dashboard state."),
            c("DB-03", "db_trading_tab", "Trading tab selected",
                "The Trading tab shows as selected and its Tools section loads."),
            c("DB-T06", "db_positions", "Positions widget",
                "Total P&L, Buy Value and Sell Value are shown with number formatting (a zero state is valid)."),
            c("DB-T01", "db_option_chain", "Option Chain from the Dashboard",
                "Option Chain opens with its underlying, strike prices, call / put prices and expiry; the OI / Price toggle answers; Back returns.", "High"),
            c("DB-T01b", "db_option_chain_segments", "Option Chain: every segment loads",
                "In each segment (NFO, MCX, CDS, BFO) the first underlying's option chain loads its table within 7 s.", "High"),
            c("DB-T02", "db_research_calls", "Research Calls / Trading Ideas",
                "Every Trading Ideas tab loads cards or a valid empty state; Intraday and Positional answer; a call card shows its fields; search and filter respond.", "High"),
            c("DB-T05", "db_tools_view_all", "Options Trading Tools: View All",
                "View All lists the tools (Strategy Finder, Builder, Instant Strategies, Screeners ...) and Back returns."),
            c("DB-T07", "db_futures_cards", "Futures cards",
                "Futures cards show an instrument, expiry, price and change."),
            c("DB-T08", "db_futures_view_all", "Futures: View All",
                "Every Futures category loads a list; a row shows name, exchange, price and change and opens its sheet; search and a sort respond and are restored.", "High"),
            c("DB-T09", "db_fii_widget", "FII / DII widget",
                "The FII / DII card shows FII, Net and DII values per date."),
            c("DB-T10", "db_fii_view_all", "FII / DII: View All",
                "The FII / DII page lists the history with FII, Net and DII columns, and more rows are reachable by scrolling."),
            c("DB-T11", "db_trading_customize", "Customize Trading dashboard",
                "The Customize screen lists Position, Futures and FII widgets with Save; leaving without a change does not alter the Dashboard."),
            c("DB-T12", "db_trading_customize_edit", "Customize Trading: one reversible change",
                "One widget is removed or moved, saved, seen on the Dashboard and restored."),
            c("DB-I01", "db_investments_landing", "Investments initial load",
                "The Investments tab shows as selected and its sections (Indian Indices, Quick Tools, Funds, Holdings, Scanners, Top Sectors, Customize) load.", "Critical"),
            c("DB-I02", "db_indices_cards", "Indian Indices cards",
                "NIFTY 50, NIFTY BANK and INDIA VIX cards show exchange, LTP and change."),
            c("DB-I03", "db_all_indices", "All Indices: search, filter, sort",
                "All Indices lists the indices; search finds NIFTY; the NSE filter keeps NSE only; a sort reorders; Clear restores.", "High"),
            c("DB-I04", "db_index_details", "Index instrument sheet",
                "An index opens its sheet with name and market data and a working Chart (More Details does not apply to indices)."),
            c("DB-I05", "db_alerts_list", "Alerts list",
                "The Alerts page lists the client's alerts (name, instrument, LTP, status) or a valid empty state; search responds.", "High"),
            c("DB-I06", "db_new_alert_form", "New Alert form",
                "The New Alert page has the name, property, instrument, condition, value and CREATE controls and they respond (nothing is created)."),
            c("DB-I07", "db_ipo_tabs", "IPO tabs",
                "The IPO page opens and every tab (Ongoing, Applied, Listed, Announced) shows its list or a valid empty state.", "High"),
            c("DB-I08", "db_ipo_details", "IPO search and details",
                "An IPO opens its details with company, price range, minimum investment, lot size, dates, application categories and its status button.", "High"),
            c("DB-I09", "db_ipo_apply_state", "IPO application action",
                "The IPO's action button matches its state; the application itself is never started."),
            c("DB-I10", "db_mutual_funds", "Mutual Funds",
                "Mutual Funds opens (an SDK) and its landing page shows content."),
            c("DB-I11", "db_reports", "Reports",
                "Reports opens a web page that shows content."),
            c("DB-I12", "db_etf", "ETF",
                "Every ETF category loads a list; an ETF opens its sheet; search and settings respond.", "High"),
            c("DB-I13", "db_pledge", "Pledge",
                "Pledge opens a web page that shows content (nothing is pledged)."),
            c("DB-I15", "db_other_request", "Other Request",
                "Other Request opens a web page that shows content (nothing is submitted)."),
            c("DB-I16", "db_funds_summary", "Funds summary",
                "The Funds card shows Available Margin, Opening Balance and Margin Used in currency format with the Add Funds amounts (zero is valid)."),
            c("DB-I17", "db_add_funds", "Add Funds screen",
                "Add Funds opens with the balance, an amount field, preset amounts and payment methods; nothing is paid.", "High"),
            c("DB-I18", "db_withdraw_funds", "Withdraw Funds screen",
                "Withdraw Funds opens with the balance, an amount field and the bank account; nothing is withdrawn.", "High"),
            c("DB-I19", "db_holdings", "Holdings widget",
                "Current Value, Invested Amount, Net P&L and Today's P&L are shown with number formatting and the widget opens Holdings."),
            c("DB-I20", "db_scanner_dashboard", "Scanners on the Dashboard",
                "Each scanner chip (Top Gainers, Top Losers, 52 Week High / Low) shows scanner cards or a valid empty state."),
            c("DB-I21", "db_scanners_view_all", "Scanners: View All",
                "Each segment (Equity, Futures, Options, Commodity) loads its list; a row opens its sheet; the sort sheet opens and closes.", "High"),
            c("DB-I22", "db_sector_cards", "Top Sectors cards",
                "Sector cards show the sector name and 3M, 6M and 1Y performance."),
            c("DB-I23", "db_sectors_view_all", "Top Sectors: View All",
                "The sectors page lists constituents; every sector tab changes the list and lists constituents."),
            c("DB-I24", "db_investments_customize", "Customize Investments dashboard",
                "The Customize screen lists the sections with Save; leaving without a change does not alter the Dashboard."),
            c("DB-I25", "db_investments_customize_edit", "Customize Investments: one reversible change",
                "One widget is removed or moved, saved, seen on the Dashboard and restored."),
            c("DB-I14", "db_back_office", "Back Office",
                "Back Office opens a web page that shows content."),
        )

        fun steps(): List<AutomationStepResult> = CASES.map { it.toStep() }

        val ALL_IDS: List<String> = CASES.map { it.id }

        private val MONEY = Regex("""[\d,]+\.\d{2}""")
        private val TAB_POSITION = Regex("""Tab \d+ of \d+""", RegexOption.IGNORE_CASE)
        private const val SDK = 20_000L
    }

    // ── Time limits ────────────────────────────────────────────────────────────────────────────

    private val UI = Timeouts.UI_RESPONSE
    private val LOAD = Timeouts.DATA_LOAD

    // ── Outcomes ───────────────────────────────────────────────────────────────────────────────

    private enum class Kind { FAIL, BLOCKED, NOT_APPLICABLE, NOT_TESTED }

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

    private val notes = mutableListOf<String>()
    private fun note(text: String) {
        notes += text
    }

    private var current: AutomationStepResult? = null
    private var currentCase: Case? = null
    private var cleanup: String? = null
    private var evidenceCount = 0

    private fun alive() {
        if (engine.cancelled) throw Stopped()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        // 150 ms between looks (the engine's default is 350 ms): every wait of the module ended up to 0.2 s late.
        val reached = engine.waitUntil(timeoutMs, 150L, condition)
        alive()
        return reached
    }

    /** For a change that shows within a fraction of a second: polls every 100 ms instead of every 350 ms. */
    private fun fastWait(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val reached = engine.waitUntil(timeoutMs, 100L, condition)
        alive()
        return reached
    }

    private fun pause(ms: Long) {
        engine.sleep(ms)
        alive()
    }

    private fun readScreen(limit: Int = 200): List<String> = engine.screenTexts(limit)

    /** The app under test, for what its Dashboard shows differently ([TargetApp]); Tradion when it is not known. */
    private val app: TargetApp get() = engine.app ?: TargetApp.TRADION

    private fun has(part: String): Boolean = engine.hasTextContaining(part)

    private fun norm(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /**
     * Labels are matched on the screen's text joined into one line: a label such as "Strategy Finder" reaches the tool as
     * two lines ("Strategy", "Finder") because the app breaks it, and a money amount as "+" and "5,000".
     */
    private fun missing(all: List<String>, wanted: List<String>): List<String> {
        val joined = norm(all.joinToString(" "))
        return wanted.filterNot { joined.contains(norm(it), ignoreCase = true) }
    }

    /** The lines of [all] from the line holding [from] up to (not including) the line holding [until]. */
    private fun section(all: List<String>, from: String, until: String? = null): List<String> {
        val start = all.indexOfFirst { it.contains(from, ignoreCase = true) }
        if (start < 0) return emptyList()
        val rest = all.drop(start + 1)
        val end = until?.let { u -> rest.indexOfFirst { it.contains(u, ignoreCase = true) } } ?: -1
        return if (end >= 0) rest.take(end) else rest
    }

    private val sheetIds = listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET, T.SCRIP_SHEET_CHART_BUTTON, T.SCRIP_SHEET_ALERT_BUTTON)

    private fun sheetOpen(): Boolean = engine.firstPresent(sheetIds) != null

    private fun screenName(): String {
        val known = listOf(
            T.DASHBOARD_INDEX_CLOSE to "index_panel", T.OPTION_CHAIN_SCREEN to "option_chain",
            T.RESEARCH_CALL_SCREEN to "trading_ideas", T.FUTURES_SCREEN to "futures", T.FII_DII_SCREEN to "fii_dii",
            T.DASHBOARD_CUSTOMIZE_SCREEN to "trading_customize", T.INVEST_CUSTOMIZE_SCREEN to "investments_customize",
            T.ALERT_SCREEN to "new_alert", T.ALERTS_SCREEN to "alerts", T.IPO_DETAILS_SCREEN to "ipo_details",
            T.IPO_SCREEN to "ipo", T.ETF_SCREEN to "etf", T.SCANNERS_SCREEN to "scanners", T.SECTORS_SCREEN to "top_sectors",
            T.WEB_VIEW_SCREEN to "web_view", T.CHART_SCREEN to "chart", T.SCRIP_DETAILS_SHEET to "instrument_sheet",
            T.INDEX_DETAILS_SHEET to "index_sheet", T.ORDER_WINDOW_SCREEN to "order_pad", T.SEARCH_SCREEN to "search",
            T.DASHBOARD_SCREEN to "dashboard",
        )
        return known.firstOrNull { engine.isPresent(it.first) }?.second ?: "unknown_screen"
    }

    private fun snapshot(): String {
        val ids = engine.visibleIdentifiers()
        val texts = Redactor.evidence(engine.screenTexts(25)).take(15)
        return "identifiers: ${ids.take(30).joinToString(", ").ifEmpty { "none" }}; " +
            "readable text: ${if (texts.isEmpty()) "none" else texts.joinToString(" | ")}"
    }

    private fun shot(label: String): String? {
        val case = currentCase ?: return null
        return flow.captureScreenshot("${case.code}_$label")
    }

    private fun evidenceShot(kind: String): String? {
        val case = currentCase ?: return null
        evidenceCount++
        return flow.captureScreenshot("${case.code}_${kind}_${evidenceCount}_${screenName()}")
    }

    private fun classify(message: String): String = when {
        Regex("could not be (tapped|typed)|could not (tap|type)|no control labelled|no field labelled|no node matched|Tapping '", RegexOption.IGNORE_CASE)
            .containsMatchIn(message) -> Cause.AUTOMATION
        Regex("still loading|did not load|within \\d+ s", RegexOption.IGNORE_CASE).containsMatchIn(message) &&
            message.contains("load", ignoreCase = true) -> Cause.API
        else -> Cause.UNCONFIRMED
    }

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

    private fun unverifiable(what: String, recommend: String): Nothing {
        var evidence: String? = null
        if (!engine.cancelled) {
            evidence = "screen: ${screenName()}; " + snapshot()
            evidenceShot("unreadable")
        }
        throw CaseEnd(
            Kind.BLOCKED,
            "Could not verify $what: the accessibility tree returned no readable content for it, so this is not reported as a defect.",
            Cause.ACCESSIBILITY, evidence, recommend,
        )
    }

    private fun notApplicable(message: String): Nothing = throw CaseEnd(Kind.NOT_APPLICABLE, message)
    private fun notTested(message: String): Nothing = throw CaseEnd(Kind.NOT_TESTED, message)

    /** Records how long a real dependency (server, SDK, WebView) took, separately from the 3 s UI limit. */
    private fun timing(startedAtMs: Long, what: String, slowMs: Long = 5_000L) {
        val took = engine.clock() - startedAtMs
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

    private fun runCase(id: String, recoverAfter: Boolean = true, body: () -> Unit): AutomationRunResult? {
        val c = case(id)
        val step = flow.step(id)
        if (step.status != StepStatus.PENDING) return null
        engine.app?.notApplicableCases?.get(id)?.let {
            flow.skipAs(step, "not_applicable", it)
            return null
        }
        current = step
        currentCase = c
        notes.clear()
        cleanup = null
        flow.begin(step)
        var end: CaseEnd? = null
        for (attempt in 0..1) {
            end = null
            try {
                body()
            } catch (e: CaseEnd) {
                end = e
            } catch (_: Stopped) {
                return stopped(step)
            } catch (e: Exception) {
                val at = e.stackTrace.firstOrNull { it.className.startsWith("com.mobileautomation") }
                    ?.let { " (at ${it.methodName}, line ${it.lineNumber})" }.orEmpty()
                end = CaseEnd(
                    Kind.FAIL, "Automation error: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}$at", Cause.AUTOMATION,
                    if (engine.cancelled) null else "screen: ${screenName()}; " + snapshot(),
                )
            }
            // A case that could not find its section (a scroll that missed, not a finding about the app) is run once more from
            // a clean start instead of being lost; a second miss is reported.
            val miss = end != null && (end.kind == Kind.FAIL || end.kind == Kind.BLOCKED) && attempt == 0 &&
                end.category != Cause.APP && !engine.cancelled &&
                Regex("""not on the (Dashboard|Trading|Investments)|paged into view|not found after scrolling|could not be tapped""").containsMatchIn(end.message.orEmpty())
            if (!miss) break
            val first = end?.message.orEmpty()
            try {
                if (loggedOut() || !recover()) break
            } catch (_: Stopped) {
                return stopped(step)
            }
            notes.clear()
            note("run again after the first attempt missed its section ($first)")
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

    // ── Getting back to the Dashboard ──────────────────────────────────────────────────────────

    /** The screens of the sign-in. When one is up the session is gone and nothing on it may be driven with Back. */
    private val loginIds = listOf(T.USER_ID_SCREEN, T.USER_ID_INPUT, T.PASSWORD_SCREEN, T.PASSWORD_INPUT, T.OTP_SCREEN, T.OTP_INPUT, T.BIOMETRIC_LOGIN_SCREEN)

    private fun loggedOut(): Boolean = engine.firstPresent(loginIds) != null

    /** Ends a case that left the app when the app comes back on a sign-in screen: that is the finding, not a navigation problem. */
    private fun failIfLoggedOut(after: String) {
        if (!loggedOut()) return
        fail("After $after the app shows the sign-in screen: the session ended", Cause.APP,
            "Developers: coming back from another app, the app lands on the login screen (check what the resume does with the feed / heartbeat session; the tool only resumed the app's task).")
    }

    private val overlays = listOf(
        T.ORDER_WINDOW_SCREEN, T.ALERT_SCREEN, T.CHART_SCREEN, T.IPO_DETAILS_SCREEN, T.OPTION_CHAIN_SCREEN,
        T.SEARCH_SCREEN, T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET, T.FUTURES_FILTER_SHEET, T.SCANNERS_FILTER_SHEET,
        T.RESEARCH_FILTER_SHEET, T.ETF_FILTER_SHEET, T.ALERTS_SCREEN, T.IPO_SCREEN, T.ETF_SCREEN, T.FUTURES_SCREEN,
        T.FII_DII_SCREEN, T.RESEARCH_CALL_SCREEN, T.SCANNERS_SCREEN, T.SECTORS_SCREEN, T.WEB_VIEW_SCREEN,
        T.DASHBOARD_CUSTOMIZE_SCREEN, T.INVEST_CUSTOMIZE_SCREEN,
    )

    /**
     * Brings the app back to the Dashboard, closing only what is on top of it. Back is pressed for an overlay whose
     * own marker was seen, or (a pushed screen with no marker, or another app in front) while the bottom navigation is
     * missing, which cannot exit the app because the home screen always shows it. True when the Dashboard shows alone.
     */
    private fun recover(): Boolean {
        // Something the app opened (a browser) may be in front: bring the app back before any Back is pressed, because
        // Back inside a browser would close its tab and not touch the app.
        if (!engine.targetInForeground()) engine.returnToTarget()
        engine.hideKeyboard()
        var triedSdkBack = false
        repeat(8) {
            alive()
            // A sign-in screen is not a page to back out of: Back there leaves the app (the recording of the Back Office case
            // spent 27 s pressing Back on the login screen). The caller learns it from the case end / the session check.
            if (loggedOut()) return false
            if (engine.isPresent(T.DASHBOARD_INDEX_CLOSE)) {
                engine.tap(T.DASHBOARD_INDEX_CLOSE)
                engine.waitForGone(T.DASHBOARD_INDEX_CLOSE, UI)
                return@repeat
            }
            val overlay = engine.firstPresent(overlays)
            if (overlay != null) {
                quickClose(overlay)
                return@repeat
            }
            if (!engine.isPresent(T.BOTTOMNAV_DASHBOARD_TAB)) {
                // A page with no marker (an SDK, a web page, another app's screen). Android Back first; some SDK pages
                // keep their own Back item in a bottom bar, tried once if Android Back left us where we were.
                engine.pressBack()
                val back = waitUntil(UI) { engine.isPresent(T.BOTTOMNAV_DASHBOARD_TAB) || engine.firstPresent(overlays) != null }
                if (!back && !triedSdkBack && engine.hasTextContaining("Back")) {
                    triedSdkBack = true
                    engine.tapText("Back", topmost = false)
                    waitUntil(UI) { engine.isPresent(T.BOTTOMNAV_DASHBOARD_TAB) || engine.firstPresent(overlays) != null }
                }
                return@repeat
            }
            if (!engine.isPresent(T.DASHBOARD_SCREEN)) {
                val open = engine.tapAndConfirm(T.BOTTOMNAV_DASHBOARD_TAB, listOf(T.DASHBOARD_SCREEN), UI, retryAfterMs = UI)
                alive()
                if (!open.ok) return false
                return@repeat
            }
            return true
        }
        return engine.isPresent(T.DASHBOARD_SCREEN) && engine.firstPresent(overlays) == null
    }

    private fun ensureDashboard() {
        if (loggedOut()) blocked("The app shows the sign-in screen: the session ended before this case", Cause.APP, "See the case that left the app (Back Office) or the earlier session ending.")
        if (!recover()) blocked("The app could not be brought back to the Dashboard: identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}")
    }

    /** Makes the Trading or the Investments view the selected one, and confirms it from the tab's own state. */
    private fun ensureView(trading: Boolean) {
        ensureDashboard()
        val tab = if (trading) T.DASHBOARD_TAB_TRADING else T.DASHBOARD_TAB_INVESTMENTS
        if (!engine.isSelected(tab)) {
            val tap = engine.tapAndConfirm(tab, listOf(if (trading) T.DASHBOARD_TRADING_VIEW else T.DASHBOARD_INVESTMENTS_VIEW), UI, retryAfterMs = UI, tapAtLeastOnce = true)
            alive()
            if (!tap.ok) fail("The ${if (trading) "Trading" else "Investments"} tab did not open: ${tap.detail}")
        }
        if (!waitUntil(UI) { engine.isSelected(tab) }) {
            fail("The ${if (trading) "Trading" else "Investments"} tab does not show as selected after it was tapped", Cause.UNCONFIRMED)
        }
    }

    private fun scrollTop() {
        engine.toTop()
        pause(150)
    }

    /** Scrolls (a few screens at most, forwards then backwards) until a label containing [part] is readable. */
    /**
     * Puts the heading [part] in the visible band of the page (see [AutomationEngine.scrollIntoView]), so the section
     * below it is on screen as well and its controls can be tapped. A heading Flutter has only built just outside the
     * viewport is readable as text but its controls are not tappable, which is why "present" is not enough.
     */
    /**
     * Pages the screen until the heading containing [part] sits in the upper two thirds, so its section is on screen
     * below it (see [AutomationEngine.scrollUntil]: one page at a time, never back and forth). Strict: a heading that is
     * merely in the tree (it can be present while clipped) does not count.
     */
    private fun reveal(part: String): Boolean {
        val ok = engine.scrollTextIntoView(part)
        alive()
        return ok
    }

    /** Pages until the control is on screen (not under the header or the bottom bar); true when it is there. */
    private fun revealId(id: String): Boolean {
        val ok = engine.scrollIdIntoView(id)
        alive()
        return ok
    }

    /** The lines between the heading containing [from] and the next heading containing [until], by position. */
    private fun band(from: String, until: String?): List<String> = engine.textsBetween(from, until)

    /** Pages until the control [id] (a link on the section's heading row) is in the upper third, the section below it on screen. */
    private fun revealAnchor(id: String, what: String, tab: String, target: Float = 0.30f) {
        if (!engine.scrollIdIntoPlace(id, target)) fail("The $what section is not on the $tab tab (its '$id' control was not found after scrolling: ${engine.placementLog})", Cause.UNCONFIRMED)
        alive()
    }

    private fun revealed(part: String, what: String = part): List<String> {
        if (!reveal(part)) fail("The $what section is not on the Dashboard (no label '$part' found after scrolling)", Cause.UNCONFIRMED)
        return readScreen()
    }

    private fun tapOpen(id: String, expect: List<String>, what: String, timeoutMs: Long = UI) {
        // One tap, scrolled into view first: a second tap on a page that is only slow to appear opens it twice, and a
        // control just outside the viewport is in the tree but cannot be tapped.
        if (!engine.isPresent(id)) engine.scrollIdIntoView(id)
        val outcome = engine.tapAndConfirm(id, expect, timeoutMs, retryAfterMs = timeoutMs)
        alive()
        if (!outcome.ok) fail("$what: ${outcome.detail}")
    }

    private fun tapLabel(part: String, what: String = part, topmost: Boolean = false) {
        val tap = engine.tapText(part, topmost)
        if (!tap.ok) fail("$what could not be tapped: ${tap.detail}", Cause.AUTOMATION)
    }

    /** Types into an identified field and closes the keyboard so what lies under it is reachable again. */
    /**
     * Back for a screen with its own marker: a second press after 1.5 s when the first was swallowed (by a keyboard that was
     * closing or a transition), instead of the flow-wide 4 s wait. True when the marker is gone.
     */
    private fun quickClose(marker: String): Boolean {
        if (!engine.isPresent(marker)) return true
        if (!engine.pressBack()) return false
        if (engine.waitForGone(marker, Timeouts.UI_RETRY)) return true
        alive()
        if (!engine.pressBack()) return false
        return engine.waitForGone(marker, UI).also { alive() }
    }

    /** Types into an identified field. The keyboard is left up: closing it is [closeKeyboard]'s job, once per form. */
    private fun type(id: String, value: String, what: String): Boolean {
        if (!engine.isPresent(id)) engine.scrollIdIntoView(id)
        val r = engine.enterText(id, value)
        if (!r.ok) note("$what could not be typed into: ${r.detail}")
        return r.ok
    }

    /**
     * Closes the keyboard with Back, which only closes it while it shows. [marker] is the screen's own identifier: if it
     * is gone afterwards, Back left the screen instead (the keyboard had already closed) and the case fails saying so.
     */
    private fun closeKeyboard(marker: String? = null) {
        if (!engine.hideKeyboard()) return
        if (marker != null && !engine.isPresent(marker)) {
            fail("Back was pressed to close the keyboard but the screen closed with it ('$marker' is gone)", Cause.AUTOMATION)
        }
    }

    private fun typeLabelled(label: String, value: String): AutomationEngine.ActionOutcome {
        val r = engine.enterTextLabelled(label, value)
        engine.hideKeyboard()
        return r
    }

    /** All Indices is open: its own index cards and its search field are on a page that replaced the Dashboard. */
    /** The pushed All Indices page: no Dashboard behind it, and its search box, or its own title with the index rows. */
    private fun onAllIndices(): Boolean = !engine.isPresent(T.DASHBOARD_SCREEN) && (
        (has("INDEX") && engine.hasFieldLabelled("Search")) ||
            (engine.hasExactText("All Indices") && has("NIFTY 50") && !engine.isPresent(T.BOTTOMNAV_DASHBOARD_TAB))
        )

    private fun leave() {
        if (!recover()) fail("The app did not come back to the Dashboard after leaving the screen", Cause.UNCONFIRMED)
    }

    private val emptyHints = listOf("no data", "no result", "no records", "nothing here", "no alerts", "no ipo", "no research", "not found", "no instruments")

    private fun emptyShown(): Boolean = readScreen(80).any { l -> emptyHints.any { l.contains(it, ignoreCase = true) } }

    /** A list either shows rows or says it is empty; neither within [LOAD] is a failure of the screen's data. */
    /** A card list's content on screen: an instrument line with its exchange (the cards of Futures, ETF, Scanners). */
    private fun cardContent(): Boolean = engine.onScreenLines().any { it.trim() in setOf("NSE", "BSE", "NFO", "BFO", "MCX", "CDS") || it.contains("Bid (Size)") }

    /**
     * A list either shows rows or says it is empty; neither within [LOAD] is a failure of the screen's data. The rows' own
     * identifier is the first proof, the card text the second (a tab whose rows are not tagged still shows its cards).
     */
    private fun rowsOrEmpty(rowId: String, what: String): Int {
        val started = engine.clock()
        val ok = waitUntil(LOAD) { engine.isPresent(rowId) || cardContent() || emptyShown() }
        if (!ok) fail("$what showed neither rows nor an empty state within ${LOAD / 1000} s", Cause.API)
        val took = engine.clock() - started
        if (took > 2_000) note("$what took ${"%.1f".format(took / 1000.0)} s")
        return engine.countOf(rowId)
    }

    private fun tapTab(prefix: String, i: Int, strip: List<String>): Boolean {
        val id = "$prefix$i"
        if (!engine.isPresent(id) && !engine.revealInList(id, strip)) return false
        val tap = engine.tap(id)
        if (!tap.ok) fail("Tab '$id' could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        pause(250L)
        return true
    }

    private fun stripOf(prefix: String): List<String> = (0 until 8).map { "$prefix$it" }

    /**
     * Landing check for a page the Dashboard reaches through an SDK, a WebView or another app: the Dashboard has to
     * leave within [SDK], then something has to be readable or drawn. A page's own container is not content.
     */
    private fun externalLanding(label: String, ds: String): Long {
        val started = engine.clock()
        val left = waitUntil(SDK) { !engine.isPresent(T.DASHBOARD_SCREEN) }
        if (!left) fail("$label did not start within ${SDK / 1000} s (the Dashboard is still showing)", Cause.API)
        // Content: readable text or, for a page the tree cannot describe, pixels (a flat page is a page that has not drawn).
        var readable = false
        val drawn = waitUntil(SDK) {
            readable = readScreen(60).count { it.length > 2 } >= 4
            readable
        }
        val took = engine.clock() - started
        pause(300)
        val path = shot("${ds}_${label.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_landing")
        val rendered = path?.let { ScreenshotInspector.content(it)?.rendered }
        when {
            drawn || rendered == true -> note("$label: landing shown (${if (readable) "readable content" else "drawn content"})")
            rendered == null && path == null -> blocked("$label opened but the screenshot could not be taken, so its content is not verified.", Cause.AUTOMATION)
            else -> fail("$label opened but its landing page shows no content (nothing readable and a flat screenshot)", Cause.UNCONFIRMED,
                "If the page is visible, it publishes no accessibility content; the screenshot is the evidence.")
        }
        timing(started, "$label initialisation", slowMs = 8_000L)
        return took
    }

    /**
     * Leaves an SDK / web page and measures how long the way back took. In order, each step bounded by 2.5 s on the
     * Dashboard's own state:
     *
     * 1. another app in front (a browser): resume the app's task;
     * 2. the page's own back arrow, by its exact label "Back": it pops the route directly. Android Back inside a web page
     *    walks the page's history (or is swallowed while it loads), which is why a Quick Tools page "did not get back";
     * 3. Android Back, twice at most;
     * 4. the generic recovery, with an evidence screenshot and an APPLICATION finding if even that was needed.
     */
    private fun backFromExternal(label: String) {
        val started = engine.clock()
        fun home() = engine.isPresent(T.DASHBOARD_SCREEN)
        if (!engine.targetInForeground()) engine.returnToTarget()
        failIfLoggedOut("leaving $label")
        var how = "the page's Back arrow"
        if (!home() && !engine.isPresent(T.BOTTOMNAV_DASHBOARD_TAB) && engine.tapExactText("Back").ok) waitUntil(Timeouts.UI_RESPONSE - 500) { home() }
        if (!home()) {
            how = "Android Back"
            repeat(2) {
                if (home()) return@repeat
                engine.pressBack()
                waitUntil(Timeouts.UI_RESPONSE - 500) { home() }
            }
        }
        if (!home()) {
            evidenceShot("did_not_return")
            if (!recover()) fail("$label did not return to the Dashboard (its Back arrow, Android Back twice and the recovery were all tried)", Cause.APP,
                "The SDK / web page keeps the user on it after Back; check its back handling.")
            note("$label: only the recovery got back to the Dashboard (${"%.1f".format((engine.clock() - started) / 1000.0)} s)")
            return
        }
        val took = engine.clock() - started
        note("$label: back on the Dashboard with $how in ${"%.1f".format(took / 1000.0)} s" + if (took > 5_000) " (slow)" else "")
    }

    /**
     * Taps every tab of a strip once (`<prefix><i>`) and checks that each shows rows, cards or a valid empty state. A tab
     * that fails is recorded with its own screenshot and the walk goes on: every sub tab is judged on its own. The list
     * of the previous tab is given a moment to be replaced, so its rows are not taken for the new tab's. Failures are
     * thrown together by [throwTabFailures] once the case has done its other checks.
     */
    private val tabFailures = mutableListOf<String>()

    private fun walkTabs(prefix: String, rowId: String, what: String, max: Int = 12): List<String> {
        val strip = (0 until max).map { "$prefix$it" }
        val out = mutableListOf<String>()
        for (i in 0 until max) {
            val id = "$prefix$i"
            if (!engine.isPresent(id) && !engine.revealInList(id, strip)) break
            val label = engine.labelsOf(id).map { l -> l.lines().map { it.trim() }.filter { it.isNotEmpty() && !TAB_POSITION.matches(it) }.joinToString(" ") }
                .firstOrNull { it.isNotEmpty() } ?: "tab ${i + 1}"
            val before = rowSignature(rowId)
            val tap = engine.tap(id)
            if (!tap.ok) {
                tabFailures += "$what › $label could not be tapped (${tap.detail})"
                continue
            }
            fastWait(800L) { rowSignature(rowId) != before || before.isEmpty() }
            try {
                val n = rowsOrEmpty(rowId, "$what › $label")
                out += "$label: ${if (n == 0) "cards" else "$n"}"
            } catch (e: CaseEnd) {
                if (e.kind != Kind.FAIL) throw e
                tabFailures += e.message.orEmpty()
                out += "$label: FAILED"
            }
        }
        return out
    }

    private fun rowSignature(rowId: String): String =
        engine.groupedLabelsOf(rowId).take(3).joinToString("|") { it.lines().firstOrNull().orEmpty().trim() }

    private fun throwTabFailures() {
        if (tabFailures.isEmpty()) return
        val all = tabFailures.joinToString("; ")
        tabFailures.clear()
        fail(all, if (all.contains("within")) Cause.API else Cause.UNCONFIRMED)
    }

    // ═══ The run ═══════════════════════════════════════════════════════════════════════════════

    fun run(): AutomationRunResult? {
        // The New Alert form raises the keyboard by itself and a search leaves it up: it covers and resizes the page, and Back
        // cannot close it safely (it can close the page). The soft keyboard is switched off for the module and always
        // switched back on, whatever happens.
        val suppressed = engine.suppressKeyboard(true)
        if (!suppressed) note("the keyboard could not be suppressed; Back will not be used to close it")
        try {
            return runCases()
        } finally {
            engine.suppressKeyboard(false)
        }
    }

    private fun runCases(): AutomationRunResult? {
        // Trading
        for (id in listOf(
            "db_initial_load", "db_index_panel", "db_trading_tab", "db_positions", "db_option_chain", "db_option_chain_segments", "db_research_calls",
            "db_tools_view_all", "db_futures_cards", "db_futures_view_all", "db_fii_widget",
            "db_fii_view_all", "db_trading_customize", "db_trading_customize_edit",
        )) {
            // The Option Chain stays open from DB-T01 for its segment case, which closes it.
            runCase(id, recoverAfter = id != "db_option_chain") { body(id) }?.let { return it }
        }
        // Investments. All Indices is left open by DB-I03 for DB-I04, the alert list by DB-I05 for DB-I06, the IPO page by
        // DB-I07 for DB-I08 and the details page by DB-I08 for DB-I09.
        val chained = setOf("db_all_indices", "db_alerts_list", "db_ipo_tabs", "db_ipo_details")
        for (id in listOf(
            "db_investments_landing", "db_indices_cards", "db_all_indices", "db_index_details", "db_alerts_list",
            "db_new_alert_form", "db_ipo_tabs", "db_ipo_details", "db_ipo_apply_state",
            "db_mutual_funds", "db_reports", "db_etf", "db_pledge", "db_other_request",
            "db_funds_summary", "db_add_funds", "db_withdraw_funds", "db_holdings", "db_scanner_dashboard",
            "db_scanners_view_all", "db_sector_cards", "db_sectors_view_all", "db_investments_customize",
            "db_investments_customize_edit",
            // Last: it leaves the app for a browser, and a session that ends on the way back must not cost any other case.
            "db_back_office",
        )) {
            runCase(id, recoverAfter = id !in chained) { body(id) }?.let { return it }
        }
        return null
    }

    private fun body(id: String) {
        when (id) {
            "db_initial_load" -> initialLoad()
            "db_index_panel" -> indexPanel()
            "db_trading_tab" -> tradingTab()
            "db_positions" -> positions()
            "db_option_chain" -> optionChain()
            "db_option_chain_segments" -> optionChainSegments()
            "db_research_calls" -> researchCalls()
            "db_tools_view_all" -> toolsViewAll()
            "db_futures_cards" -> futuresCards()
            "db_futures_view_all" -> futuresViewAll()
            "db_fii_widget" -> fiiWidget()
            "db_fii_view_all" -> fiiViewAll()
            "db_trading_customize" -> customize(T.DASHBOARD_CUSTOMIZE_BUTTON, T.DASHBOARD_CUSTOMIZE_SCREEN, true, "DS-09",
                listOf("Position", "Futures", "Fii"))
            "db_trading_customize_edit", "db_investments_customize_edit" -> notTested(
                "The widgets' remove (minus) and drag controls carry no identifiers and publish no state, so a reversible change " +
                    "(remove or move one widget, Save, check the Dashboard, restore) cannot be made and verified safely. The screen " +
                    "itself is covered by the Customize case. ${engine.appName} would need identifiers such as customize_remove_<widget> and " +
                    "customize_handle_<widget>, and Save's enabled state.",
            )
            "db_investments_landing" -> investmentsLanding()
            "db_indices_cards" -> indicesCards()
            "db_all_indices" -> allIndices()
            "db_index_details" -> indexDetails()
            "db_alerts_list" -> alertsList()
            "db_new_alert_form" -> newAlertForm()
            "db_ipo_tabs" -> ipoTabs()
            "db_ipo_details" -> ipoDetails()
            "db_ipo_apply_state" -> ipoApplyState()
            "db_mutual_funds" -> sdkTile(T.INVEST_TILE_MUTUAL, "Mutual Funds", "DS-16")
            "db_reports" -> webTile(T.INVEST_TILE_REPORTS, "Reports", "DS-17")
            "db_etf" -> etf()
            "db_pledge" -> webTile(T.INVEST_TILE_PLEDGE, "Pledge", "DS-19")
            "db_back_office" -> webTile(T.INVEST_TILE_BACK_OFFICE, "Back Office", "DS-19", skipIfSessionLost = true)
            "db_other_request" -> webTile(T.INVEST_TILE_OTHER, "Other Request", "DS-19")
            "db_funds_summary" -> fundsSummary()
            "db_add_funds" -> addFunds()
            "db_withdraw_funds" -> withdrawFunds()
            "db_holdings" -> holdings()
            "db_scanner_dashboard" -> scannerDashboard()
            "db_scanners_view_all" -> scannersViewAll()
            "db_sector_cards" -> sectorCards()
            "db_sectors_view_all" -> sectorsViewAll()
            "db_investments_customize" -> customize(T.INVEST_CUSTOMIZE_BUTTON, T.INVEST_CUSTOMIZE_SCREEN, false, "DS-25",
                listOf("Customi"))
        }
    }

    // ═══ DB-01 … DB-03 ═════════════════════════════════════════════════════════════════════════

    private fun initialLoad() {
        ensureView(true)
        val started = engine.clock()
        // Dashboard data arrives from the API: wait for the loading markers to clear, then read.
        waitUntil(LOAD) { engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 }
        timing(started, "Dashboard data loaded")
        val all = readScreen()
        if (all.isEmpty()) unverifiable("the Dashboard", "Publish the ticker and the section headings as labels.")
        val problems = mutableListOf<String>()
        // The ticker leads with the app's own index name: Tradion's "NIFTY 50", iTrade's "NIFTY" (run f00f716f).
        val tickerShown = all.any { it.contains(app.tickerIndex, ignoreCase = true) }
        // iTrade draws the ticker only with Profile › Settings › Fixed header on; otherwise its header is
        // "Welcome, <name>" and the arrow, whose panel DB-02 checks (run c6d9df84).
        val welcomeHeader = !tickerShown && all.any { it.startsWith("Welcome", ignoreCase = true) } &&
            engine.isPresent(T.DASHBOARD_INDEX_DROPDOWN)
        if (welcomeHeader) {
            note("the header shows the welcome line and the indices arrow, not a ticker (Fixed header is off); the indices are checked in DB-02")
        } else {
            if (!tickerShown) problems += "the ${app.tickerIndex} ticker"
            if (all.none { MONEY.containsMatchIn(it) }) problems += "an LTP"
            if (all.none { it.contains("%") }) problems += "a % change"
        }
        if (!has("Trading")) problems += "the Trading tab"
        if (!has("Investments")) problems += "the Investments tab"
        for (id in listOf(T.BOTTOMNAV_WATCHLIST_TAB, T.BOTTOMNAV_ORDER_TAB, T.BOTTOMNAV_DASHBOARD_TAB, T.BOTTOMNAV_PORTFOLIO_TAB)) {
            if (!engine.isPresent(id)) problems += "the bottom navigation item '$id'"
        }
        if (problems.isNotEmpty()) fail("The Dashboard is readable but does not show ${problems.joinToString(", ")}", Cause.UNCONFIRMED)
        shot("DS-01_trading_dashboard")
        note(if (welcomeHeader) "tabs and bottom navigation shown" else "ticker, tabs and bottom navigation shown")
    }

    private fun indexPanel() {
        ensureView(true)
        val before = readScreen(40).take(6)
        tapOpen(T.DASHBOARD_INDEX_DROPDOWN, listOf(T.DASHBOARD_INDEX_CLOSE), "The ticker arrow did not open the indices panel")
        val all = readScreen()
        val gaps = missing(all, listOf("Funds", "Equity"))
        if (gaps.isNotEmpty()) fail("The panel opened but shows no ${gaps.joinToString(" or ")} (texts: ${all.take(8).joinToString(" | ")})", Cause.UNCONFIRMED)
        val items = engine.countOf(T.DASHBOARD_INDEX_ITEM)
        if (items == 0) fail("The panel shows no index tile", Cause.UNCONFIRMED)
        if (all.none { MONEY.containsMatchIn(it) }) fail("The panel's indices show no price", Cause.UNCONFIRMED)
        note("$items index tile(s) with prices and Funds / Equity")
        shot("index_panel")
        engine.tap(T.DASHBOARD_INDEX_CLOSE)
        if (!waitUntil(UI) { !engine.isPresent(T.DASHBOARD_INDEX_CLOSE) }) fail("The panel did not close when its Close button was tapped")
        if (!waitUntil(UI) { engine.isPresent(T.DASHBOARD_SCREEN) && engine.isSelected(T.DASHBOARD_TAB_TRADING) }) {
            fail("After closing the panel the Dashboard is not back on the Trading tab")
        }
        note("closing returned to the Trading tab (${before.size} header line(s) read before and after)")
    }

    private fun tradingTab() {
        ensureView(true)
        if (engine.isSelected(T.DASHBOARD_TAB_INVESTMENTS)) fail("Both the Trading and Investments tabs report themselves selected", Cause.APP)
        // The Tools section is the top of the Trading page: the top is shown, no heading search.
        scrollTop()
        val all = readScreen()
        if (all.none { it.contains("Option Chain", true) } && !engine.isPresent(T.DASHBOARD_OPEN_OPTION_CHAIN)) fail("The Tools section shows no Option Chain", Cause.UNCONFIRMED)
        note("Trading selected; Tools section loaded")
    }

    // ═══ Trading tools ═════════════════════════════════════════════════════════════════════════

    private fun positions() {
        ensureView(true)
        // The Positions card sits under the Tools at the top of the Trading page and is fully on screen there.
        scrollTop()
        pause(Timeouts.SETTLE)
        // The card's lines can be merged into the Trading page's own node, which is taller than onScreenLines takes
        // (run f00f716f: "Positions | Total P&L" readable, no line on screen): its labels are unique, so read the page.
        val all = engine.onScreenLines().let { lines ->
            if (missing(lines, listOf("Total P&L", "Buy Value", "Sell Value")).isEmpty()) lines else readScreen()
        }
        val gaps = missing(all, listOf("Total P&L", "Buy Value", "Sell Value"))
        if (gaps.isNotEmpty()) fail("Positions is readable but shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        if (all.none { MONEY.containsMatchIn(it) }) fail("Positions shows no figure", Cause.UNCONFIRMED)
        note("the Positions statistics are loaded (Total P&L, Buy Value, Sell Value)")
    }

    private fun optionChain() {
        ensureView(true)
        scrollTop()
        tapOpen(T.DASHBOARD_OPEN_OPTION_CHAIN, listOf(T.OPTION_CHAIN_SCREEN), "Option Chain did not open")
        val started = engine.clock()
        if (!waitUntil(Timeouts.OPTION_CHAIN_LOAD) { engine.isPresent(T.OPTION_CHAIN_TABLE) }) {
            fail("Option Chain opened but its table did not load within ${Timeouts.OPTION_CHAIN_LOAD / 1000} s", Cause.API)
        }
        timing(started, "option chain loaded")
        val all = readScreen()
        val strikes = all.count { it.trim().matches(Regex("""\d{4,6}""")) }
        val expiry = all.any { Regex("""\d{2}[A-Z]{3}\d{2}""").containsMatchIn(it.replace(" ", "")) }
        val problems = mutableListOf<String>()
        if (strikes < 3) problems += "strike prices (found $strikes)"
        if (all.none { MONEY.containsMatchIn(it) }) problems += "call / put prices"
        if (!expiry) problems += "an expiry"
        if (all.none { it.contains("Call", true) } || all.none { it.contains("Put", true) }) problems += "the Call / Put headings"
        if (problems.isNotEmpty()) fail("Option Chain is readable but shows no ${problems.joinToString(", ")}", Cause.UNCONFIRMED)
        // The OI / Price toggle: one tap, then the table has to still be there (the tool cannot read which side is selected).
        val toggle = engine.tapText("OI")
        if (toggle.ok) {
            pause(Timeouts.SETTLE)
            if (!engine.isPresent(T.OPTION_CHAIN_TABLE)) fail("After the OI / Price toggle the option chain table is gone", Cause.APP)
            note("the OI / Price toggle answered and the table stayed")
        } else {
            note("the OI / Price toggle was not found by its label (${toggle.detail})")
        }
        shot("option_chain")
    }

    private val ocSegments = listOf("nfo", "mcx", "cds", "bfo")

    /**
     * Search, segment chip, first underlying, then the chain's table within [Timeouts.OPTION_CHAIN_LOAD]. One segment per
     * pass, each independent: a segment that fails is recorded and the next one still runs. The search screen is left
     * with Back so the next segment starts on the chain.
     */
    private fun ocLoadSegment(seg: String, chips: List<String>): String? {
        val name = seg.uppercase()
        val toSearch = engine.tapAndConfirm(T.OPTION_CHAIN_SEARCH_BUTTON, listOf(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN), UI, retryAfterMs = UI)
        alive()
        if (!toSearch.ok) return "$name: the search icon did not open the script search: ${toSearch.detail}"
        val chip = "${T.OPTION_CHAIN_SEGMENT_PREFIX}$seg"
        if (!engine.isPresent(chip) && !engine.revealInList(chip, chips)) return "$name: the segment chip is not on the search screen"
        val firstBefore = engine.ownLabelsOf(T.OPTION_CHAIN_SPOT_ROW).firstOrNull()
        if (!engine.tap(chip).ok) return "$name: the segment chip could not be tapped"
        // Changing segment refetches the list without a loader, so the previous segment's rows can linger: wait for the
        // first row to change (bounded) rather than for a fixed time.
        waitUntil(UI) { engine.isPresent(T.OPTION_CHAIN_SPOT_ROW) && engine.ownLabelsOf(T.OPTION_CHAIN_SPOT_ROW).firstOrNull() != firstBefore }
        if (!engine.isPresent(T.OPTION_CHAIN_SPOT_ROW)) {
            if (!waitUntil(UI) { engine.isPresent(T.OPTION_CHAIN_SPOT_ROW) }) return "$name: no underlying is listed"
        }
        val script = engine.ownLabelsOf(T.OPTION_CHAIN_SPOT_ROW).firstOrNull()?.lines()?.firstOrNull()?.trim().orEmpty()
        if (!engine.tap(T.OPTION_CHAIN_SPOT_ROW).ok) return "$name: the first underlying could not be chosen"
        val started = engine.clock()
        if (!engine.waitForGone(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN, UI)) return "$name: choosing the first underlying did not return to the option chain"
        if (!waitUntil(Timeouts.OPTION_CHAIN_LOAD) { engine.isPresent(T.OPTION_CHAIN_TABLE) }) {
            return "$name: the chain of '${script.ifEmpty { "the first underlying" }}' had not loaded within ${Timeouts.OPTION_CHAIN_LOAD / 1000} s (still loading, empty, or no expiry)"
        }
        val took = engine.clock() - started
        note("$name${if (script.isNotEmpty()) " ($script)" else ""} loaded in ${"%.1f".format(took / 1000.0)} s")
        current?.let {
            it.apiKind = it.apiKind ?: StepApi.READ
            it.apiMs = maxOf(it.apiMs ?: 0L, took)
            it.apiChecks += 1
            it.apiStatus = if (took >= 5_000) StepApi.SLOW else StepApi.OK
            it.apiDetail = "slowest segment ${"%.1f".format((it.apiMs ?: 0L) / 1000.0)} s"
        }
        return null
    }

    private fun optionChainSegments() {
        if (!engine.isPresent(T.OPTION_CHAIN_SCREEN)) {
            ensureView(true)
            scrollTop()
            tapOpen(T.DASHBOARD_OPEN_OPTION_CHAIN, listOf(T.OPTION_CHAIN_SCREEN), "Option Chain did not open")
        }
        val chips = ocSegments.map { "${T.OPTION_CHAIN_SEGMENT_PREFIX}$it" }
        val failures = mutableListOf<String>()
        for (seg in ocSegments) {
            val failure = ocLoadSegment(seg, chips)
            if (failure != null) {
                failures += failure
                if (!engine.isPresent(T.OPTION_CHAIN_SCREEN) && engine.isPresent(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)) quickClose(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)
                evidenceShot("segment_${seg}")
            }
            // Whatever happened, be on the option chain (not the search screen) for the next segment.
            if (engine.isPresent(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)) quickClose(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)
            alive()
        }
        shot("option_chain_segments")
        if (failures.isNotEmpty()) fail(failures.joinToString("; "), if (failures.any { it.contains("had not loaded") }) Cause.API else Cause.UNCONFIRMED)
        leave()
    }

    private fun researchCalls() {
        ensureView(true)
        scrollTop()
        tapOpen(T.DASHBOARD_OPEN_RESEARCH_CALLS, listOf(T.RESEARCH_CALL_SCREEN), "Research Calls did not open", LOAD)
        val strip = stripOf(T.RESEARCH_TAB_PREFIX)
        val tabs = mutableListOf<String>()
        for (i in 0 until 8) {
            if (!tapTab(T.RESEARCH_TAB_PREFIX, i, strip)) break
            val id = "${T.RESEARCH_TAB_PREFIX}$i"
            if (!waitUntil(UI) { engine.isSelected(id) }) {
                // The last tab sits at the strip's edge: the strip scrolls on the first tap and may swallow it.
                tapTab(T.RESEARCH_TAB_PREFIX, i, strip)
                if (!waitUntil(LOAD) { engine.isSelected(id) }) fail("Tab ${i + 1} was tapped twice but does not show as selected", Cause.UNCONFIRMED)
            }
            val cards = rowsOrEmpty(T.RESEARCH_CARD, "Trading Ideas tab ${i + 1}")
            tabs += "${i + 1}: ${if (cards == 0) "empty" else "$cards card(s)"}"
        }
        if (tabs.isEmpty()) fail("No Trading Ideas tab was found", Cause.AUTOMATION)
        note("tabs ${tabs.joinToString(", ")}")
        shot("DS-03_trading_ideas")
        // Intraday / Positional.
        for (i in listOf(1, 0)) {
            val id = "${T.RESEARCH_SUBTAB_PREFIX}$i"
            if (!engine.isPresent(id)) {
                note("sub tab $i is not offered")
                continue
            }
            engine.tap(id)
            if (!waitUntil(UI) { engine.isSelected(id) }) fail("Sub tab ${if (i == 0) "Intraday" else "Positional"} was tapped but does not show as selected", Cause.UNCONFIRMED)
        }
        // One representative card.
        val card = engine.groupedLabelsOf(T.RESEARCH_CARD).firstOrNull()
        if (card != null) {
            val gaps = missing(listOf(card), listOf("Entry", "Target"))
            if (gaps.isNotEmpty()) fail("The first research call is readable but shows no ${gaps.joinToString(", ")} (card: ${card.replace('\n', ' ').take(120)})", Cause.UNCONFIRMED)
            note("a card shows its instrument, entry, stop-loss and target")
            // Search: the first letters of the first call's own name.
            val name = engine.ownLabelsOf(T.RESEARCH_CARD_NAME).firstOrNull()?.trim().orEmpty()
            if (name.length >= 3 && engine.isPresent(T.RESEARCH_SEARCH_INPUT)) {
                val typed = engine.enterText(T.RESEARCH_SEARCH_INPUT, name.take(4))
                if (!typed.ok) fail("The Trading Ideas search field did not take text: ${typed.detail}", Cause.AUTOMATION)
                if (!waitUntil(UI) { engine.isPresent(T.RESEARCH_CARD) || emptyShown() }) fail("Searching '${name.take(4)}' showed neither a call nor an empty state", Cause.UNCONFIRMED)
                engine.enterText(T.RESEARCH_SEARCH_INPUT, "")
                note("search '${name.take(4)}' responded")
            }
        } else {
            note("no research call to read in the last tab")
        }
        // Filter.
        if (engine.isPresent(T.RESEARCH_FILTER_BUTTON)) {
            tapOpen(T.RESEARCH_FILTER_BUTTON, listOf(T.RESEARCH_FILTER_SHEET), "The filter icon did not open the Filter By sheet")
            if (!engine.isPresent(T.RESEARCH_FILTER_STATUS) && !engine.isPresent(T.RESEARCH_FILTER_EXCHANGE)) fail("The Filter By sheet shows no Status or Exchange options", Cause.UNCONFIRMED)
            if (engine.isPresent(T.RESEARCH_FILTER_CLEAR)) engine.tap(T.RESEARCH_FILTER_CLEAR)
            note("the Filter By sheet opened with its options and was cleared")
        }
        leave()
    }

    private fun toolsViewAll() {
        ensureView(true)
        // The Options Trading Tools row is on screen at the top of the page; its View All is the topmost one.
        scrollTop()
        pause(Timeouts.SETTLE)
        tapLabel("View All", "Options Trading Tools › View All", topmost = true)
        // View All opens the options SDK's own Explore page (Quick Access with the tools), not a native list.
        if (!waitUntil(UI) { !engine.isPresent(T.DASHBOARD_SCREEN) }) fail("View All did not open the tools page", Cause.UNCONFIRMED)
        val tools = listOf("Scalper Mode", "Strategy Builder", "Option Chain", "Strategy Finder", "Chart")
        var all = readScreen()
        waitUntil(SDK) {
            all = readScreen()
            missing(all, tools).size < tools.size
        }
        val gaps = missing(all, tools)
        if (tools.size - gaps.size < 3) fail("The tools page is readable but lists only ${(tools - gaps.toSet()).joinToString().ifEmpty { "none" }} of ${tools.joinToString()} (texts: ${all.take(10).joinToString(" | ")})", Cause.UNCONFIRMED)
        if (gaps.isNotEmpty()) note("not listed: ${gaps.joinToString()}")
        note("View All opened the options SDK's Explore page listing ${tools.size - gaps.size} tools")
        shot("tools_view_all")
        backFromExternal("Options tools")
    }

    // ═══ Futures, FII / DII ════════════════════════════════════════════════════════════════════

    private fun futuresCards() {
        ensureView(true)
        revealAnchor(T.DASHBOARD_FUTURES_VIEW_ALL, "Futures", "Trading")
        // The cards are found by position under the heading row (its View All link): the tree lists headings apart from the cards.
        val body = engine.textsBelow(T.DASHBOARD_FUTURES_VIEW_ALL, 0.40f).filter { it.isNotBlank() && !it.equals("View All", true) && !it.equals("Futures", true) }
        if (body.size < 3) fail("The Futures section shows ${body.size} line(s) under its heading (lines: ${body.take(10).joinToString(" | ")})", Cause.UNCONFIRMED)
        if (body.none { MONEY.containsMatchIn(it) }) fail("The Futures cards show no price (lines: ${body.take(8).joinToString(" | ")})", Cause.UNCONFIRMED)
        val months = Regex("""\b(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)\b""", RegexOption.IGNORE_CASE)
        if (body.none { months.containsMatchIn(it) }) note("no expiry month is readable in the cards")
        note("Futures cards: ${body.take(6).joinToString(" | ")}")
    }

    private fun futuresViewAll() {
        ensureView(true)
        revealAnchor(T.DASHBOARD_FUTURES_VIEW_ALL, "Futures", "Trading")
        tapOpen(T.DASHBOARD_FUTURES_VIEW_ALL, listOf(T.FUTURES_SCREEN), "Futures › View All did not open")
        val strip = stripOf(T.FUTURES_TAB_PREFIX)
        tabFailures.clear()
        val tabs = walkTabs(T.FUTURES_TAB_PREFIX, T.FUTURES_ROW, "Futures")
        if (tabs.isEmpty()) fail("No Futures tab was found", Cause.AUTOMATION)
        note("all ${tabs.size} tab(s): ${tabs.joinToString(", ")}")
        // Back on the first tab for the row, search and sort checks.
        tapTab(T.FUTURES_TAB_PREFIX, 0, strip)
        rowsOrEmpty(T.FUTURES_ROW, "Futures tab 1")
        val row = engine.groupedLabelsOf(T.FUTURES_ROW).firstOrNull()
            ?: blocked("The first Futures tab lists no instrument, so no row can be read or opened.", Cause.TEST_DATA)
        val gaps = mutableListOf<String>()
        if (!Regex("""\b(NFO|BFO|MCX|CDS|NSE|BSE)\b""").containsMatchIn(row)) gaps += "an exchange"
        if (!MONEY.containsMatchIn(row)) gaps += "a price"
        if (!row.contains("%")) gaps += "a % change"
        if (gaps.isNotEmpty()) fail("The first Futures row shows no ${gaps.joinToString(", ")} (row: ${row.replace('\n', ' ').take(100)})", Cause.UNCONFIRMED)
        shot("DS-07_futures_view_all")
        // The row opens the instrument sheet (covered in depth by the Watchlist suite).
        engine.tapAt(T.FUTURES_ROW, 0)
        if (!waitUntil(UI) { sheetOpen() }) fail("Tapping a Futures row did not open its sheet", Cause.UNCONFIRMED)
        note("a row opened its instrument sheet")
        quickClose(engine.firstPresent(sheetIds) ?: T.SCRIP_DETAILS_SHEET)
        // Search. The list is rebuilt after the text arrives: the rows of the moment before are not its answer (run
        // f00f716f read the CURRENCY tab's USDINR rows while the screen already said NO RESULTS).
        val unsearched = rowSignature(T.FUTURES_ROW)
        val typed = engine.enterTextLabelled("Search", "NIFTY")
        if (typed.ok) {
            fastWait(1_500L) { emptyShown() || rowSignature(T.FUTURES_ROW) != unsearched }
            if (!waitUntil(UI) { engine.isPresent(T.FUTURES_ROW) || emptyShown() }) fail("Searching NIFTY showed neither a row nor an empty state", Cause.UNCONFIRMED)
            val names = if (emptyShown()) emptyList() else engine.groupedLabelsOf(T.FUTURES_ROW)
            if (names.isNotEmpty() && names.none { it.contains("NIFTY", true) }) fail("Searching NIFTY lists rows that do not contain NIFTY (${names.first().take(40)})", Cause.APP)
            engine.enterTextLabelled("Search", "")
            note("search NIFTY responded")
        } else {
            note("search was not exercised: ${typed.detail}")
        }
        // Settings: the sort sheet, one option, then Clear.
        if (engine.isPresent(T.FUTURES_FILTER_BUTTON)) {
            tapOpen(T.FUTURES_FILTER_BUTTON, listOf(T.FUTURES_FILTER_SHEET), "The settings icon did not open the sort sheet")
            if (engine.isPresent(T.SORT_OPTIONS.first())) engine.tap(T.SORT_OPTIONS.first())
            pause(Timeouts.SETTLE)
            if (!engine.isPresent(T.FUTURES_FILTER_SHEET)) {
                tapOpen(T.FUTURES_FILTER_BUTTON, listOf(T.FUTURES_FILTER_SHEET), "The settings icon did not reopen the sheet")
            }
            if (engine.isPresent(T.FUTURES_FILTER_CLEAR)) engine.tap(T.FUTURES_FILTER_CLEAR)
            cleanup = "restored: the sort was cleared"
            if (!waitUntil(UI) { engine.isPresent(T.FUTURES_ROW) }) fail("After Clear the Futures list shows no rows", Cause.UNCONFIRMED)
            note("a sort was applied and cleared")
        }
        throwTabFailures()
        leave()
    }

    private fun fiiWidget() {
        ensureView(true)
        revealAnchor(T.DASHBOARD_FII_DII_VIEW_ALL, "FII / DII", "Trading")
        // The rows are read by position under the heading row (the tree lists the heading apart from the card).
        var body = engine.textsBelow(T.DASHBOARD_FII_DII_VIEW_ALL, 0.55f)
        waitUntil(UI) {
            body = engine.textsBelow(T.DASHBOARD_FII_DII_VIEW_ALL, 0.55f)
            Regex("""Cr\b""").findAll(norm(body.joinToString(" "))).count() >= 3
        }
        val joined = norm(body.joinToString(" "))
        // The FII / DII / Net names are part of the drawn bars, not of the readable text (the readable lines are the dates and
        // the three amounts in Cr), so the amounts are the proof.
        val crores = Regex("""Cr\b""").findAll(joined).count()
        if (crores < 3) fail("The FII / DII card shows $crores value(s) in Cr, expected FII, Net and DII (lines: ${body.take(8).joinToString(" | ")})", Cause.UNCONFIRMED)
        val dates = Regex("""\d{1,2}\s+[A-Za-z]{3}""").findAll(joined).count()
        note("$crores values in Cr${if (dates > 0) ", $dates date label(s)" else " (no date label readable)"}; the bars are drawn, not described, to the tool")
    }

    private fun fiiViewAll() {
        ensureView(true)
        revealAnchor(T.DASHBOARD_FII_DII_VIEW_ALL, "FII / DII", "Trading")
        tapOpen(T.DASHBOARD_FII_DII_VIEW_ALL, listOf(T.FII_DII_SCREEN), "FII / DII › View All did not open")
        val started = engine.clock()
        if (!waitUntil(LOAD) { engine.countOf(T.FII_DII_ROW) >= 3 }) fail("The FII / DII page lists ${engine.countOf(T.FII_DII_ROW)} row(s) after ${LOAD / 1000} s", Cause.API)
        timing(started, "FII / DII history loaded")
        val all = readScreen()
        val gaps = missing(all, listOf("FII", "Net", "DII"))
        if (gaps.isNotEmpty()) fail("The FII / DII page is readable but shows no ${gaps.joinToString(", ")} column", Cause.UNCONFIRMED)
        val firstDates = all.filter { Regex("""\d{2} [A-Z][a-z]{2} \d{4}""").containsMatchIn(it) }
        shot("DS-08_fii_dii_view_all")
        if (engine.scrollPage(forward = true)) {
            pause(Timeouts.SETTLE)
            val more = readScreen().filter { Regex("""\d{2} [A-Z][a-z]{2} \d{4}""").containsMatchIn(it) }
            val fresh = more.count { it !in firstDates }
            // The list builds rows near the screen, so the first read already holds several dates: a scroll that adds
            // none means the end of the history, which is noted, not failed.
            note(if (fresh > 0) "scrolling reached $fresh more date(s)" else "scrolling added no further date (the end of the history)")
        }
        leave()
    }

    private fun customize(button: String, screen: String, trading: Boolean, ds: String, rows: List<String>) {
        ensureView(trading)
        if (!engine.isPresent(button)) {
            val ok = reveal("Customi")
            if (!ok && !engine.isPresent(button)) fail("The Customize button is not on the Dashboard", Cause.UNCONFIRMED)
        }
        if (!engine.isPresent(button)) engine.revealInList(button, listOf(T.DASHBOARD_SCREEN))
        tapOpen(button, listOf(screen), "Customize did not open")
        val all = readScreen()
        if (all.isEmpty()) unverifiable("the Customize screen", "Publish the widget rows and Save as labels.")
        val gaps = missing(all, rows + listOf("Save"))
        if (gaps.isNotEmpty()) fail("Customize is readable but shows no ${gaps.joinToString(", ")} (texts: ${all.take(10).joinToString(" | ")})", Cause.UNCONFIRMED)
        shot("${ds}_customize")
        note("sections ${rows.joinToString()} and Save listed; drag handles and remove signs are icons without labels, so they are seen in the screenshot only")
        // Leave without a change: Back shows no 'save changes?' question when nothing changed.
        quickClose(screen)
        if (engine.hasTextContaining("Discard")) {
            engine.tapText("Discard")
            cleanup = "restored: nothing was saved"
        }
        if (!waitUntil(UI) { engine.isPresent(T.DASHBOARD_SCREEN) }) fail("After leaving Customize the Dashboard did not come back", Cause.UNCONFIRMED)
    }

    // ═══ Investments ═══════════════════════════════════════════════════════════════════════════

    private fun investmentsLanding() {
        ensureView(false)
        val started = engine.clock()
        waitUntil(LOAD) { engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 }
        timing(started, "Investments data loaded")
        if (engine.isSelected(T.DASHBOARD_TAB_TRADING)) fail("The Trading tab still reports itself selected on the Investments view", Cause.APP)
        val found = mutableSetOf<String>()
        val wanted = app.investmentsSections
        scrollTop()
        // iTrade heads Quick Tools "Invest" (run f00f716f), which the tab's own name contains: such a heading is a whole line.
        fun shows(line: String, w: String) = if ("Investments".startsWith(w, ignoreCase = true)) line.trim().equals(w, ignoreCase = true) else line.contains(w, ignoreCase = true)
        fun collect() = wanted.filter { w -> readScreen().any { shows(it, w) } }.forEach { found += it }
        collect()
        shot("DS-10_investments_dashboard")
        repeat(8) {
            if (found.size == wanted.size) return@repeat
            if (!engine.scrollPage(forward = true)) return@repeat
            pause(Timeouts.SETTLE)
            collect()
        }
        // Indian Indices is drawn only once its data arrives; when that is the only gap it is reported once, by DB-I02 to
        // DB-I04 (API), not again here (run 549102bb).
        val dataOnly = setOf("Indian Indices")
        val gaps = wanted.filterNot { it in found }
        val missingLayout = gaps.filterNot { it in dataOnly }
        if (missingLayout.isNotEmpty()) fail("The Investments tab is readable but these sections were not found while scrolling: ${gaps.joinToString()}", Cause.UNCONFIRMED)
        if (gaps.isNotEmpty()) note("${gaps.joinToString()} not shown: it appears only once its data arrives (see DB-I02)")
        note("Investments selected; ${found.size} of ${wanted.size} sections found by scrolling (the promotional banner is an image: seen in the screenshot, not tapped)")
        scrollTop()
    }

    /**
     * Indian Indices is the first section under the banner. Tradion hides the whole section (heading, All Indices and
     * the cards) until the index data has arrived, and a section shortcut also reads "Indian Indices", so the section
     * counts as shown only once its All Indices link is.
     */
    private fun revealIndices() {
        scrollTop()
        pause(Timeouts.SETTLE)
        val shown = waitUntil(LOAD) { has("All Indices") } || run {
            // A taller banner can push it below the first screen.
            var found = false
            repeat(2) { if (!found && engine.scrollPage(forward = true)) { pause(Timeouts.SETTLE); found = has("All Indices") } }
            found
        }
        // iTrade has no 'Indian Indices' shortcut: without the section nothing on the tab carries the name (run f00f716f).
        if (!shown && !has("Indian Indices")) fail(
            "The Indian Indices section is not shown: ${engine.appName} hides it (heading, All Indices and cards) until the index " +
                "data arrives, and none had arrived ${LOAD / 1000} s after the Investments tab opened (nothing titled Indian Indices is on the tab)",
            Cause.API, "Check the investment dashboard response: its preferences must enable 'indices' and carry indicesData " +
                "(dashboardProvider.indexData stayed empty).",
        )
        if (!shown) fail(
            "The Indian Indices section is not shown: ${engine.appName} hides it until the index data arrives, and none had " +
                "arrived ${LOAD / 1000} s after the Investments tab opened (only the 'Indian Indices' shortcut is on screen)",
            Cause.API, "Check the dashboard index data call (dashboardProvider.indexData stayed empty).",
        )
    }

    private fun indicesCards() {
        ensureView(false)
        revealIndices()
        val body = engine.onScreenLines()
        val joined = norm(body.joinToString(" "))
        val names = listOf("NIFTY 50", "NIFTY BANK", "INDIA VIX").filter { joined.contains(it) }
        if (names.size < 2) fail("Indian Indices is readable but shows only ${names.joinToString().ifEmpty { "none" }} of NIFTY 50, NIFTY BANK, INDIA VIX (lines: ${body.take(14).joinToString(" | ")})", Cause.UNCONFIRMED)
        // The row scrolls sideways, so the third card is only partly built: two complete cards are what is required.
        if (body.count { it.trim() == "NSE" || it.trim() == "BSE" } < 2) fail("The index cards do not show their exchange (NSE) (lines: ${body.take(24).joinToString(" | ")})", Cause.UNCONFIRMED)
        if (body.count { MONEY.containsMatchIn(it) } < 2) fail("The index cards do not show an LTP", Cause.UNCONFIRMED)
        if (!Regex("""[+-]?\d+\.\d{2}\s*\([+-]?\d+\.\d{2}\s*%?\s*\)""").containsMatchIn(joined)) fail("The index cards show no change and % change", Cause.UNCONFIRMED)
        note("${names.joinToString()} cards with exchange, LTP and change (colour is not exposed to the tool)")
    }

    private fun allIndices() {
        ensureView(false)
        revealIndices()
        tapLabel("All Indices", "All Indices", topmost = true)
        // A pushed page replaces the Dashboard in the tree; its search box is an editable field, which a plain text search skips.
        if (!waitUntil(UI) { onAllIndices() }) fail("All Indices did not open", Cause.UNCONFIRMED)
        val all = readScreen()
        val rows = all.count { it.trim() == "NSE" || it.trim() == "BSE" }
        if (rows < 2) fail("All Indices is readable but lists $rows index row(s) with an exchange", Cause.UNCONFIRMED)
        shot("DS-11_all_indices")
        val before = all.take(12)
        val typed = typeLabelled("Search", "NIFTY")
        if (!typed.ok) fail("The All Indices search field did not take text: ${typed.detail}", Cause.AUTOMATION)
        if (!waitUntil(UI) { readScreen().any { it.contains("NIFTY", true) } }) fail("Searching NIFTY lists no NIFTY index", Cause.UNCONFIRMED)
        typeLabelled("Search", "")
        pause(Timeouts.SETTLE)
        note("search NIFTY found NIFTY indices")
        val gear = engine.tapRightEdgeOf("Search")
        if (!gear.ok) fail("The settings icon could not be tapped: ${gear.detail}", Cause.AUTOMATION)
        if (!waitUntil(UI) { has("Sort") }) fail("The settings icon did not open the Filter / Sort sheet", Cause.UNCONFIRMED)
        // The sheet slides in and builds its Sort rows after "Sort" first appears: wait for all of them before judging.
        val sheetTexts = listOf("NSE", "BSE", "Alphabetically", "Last Traded Price", "CLEAR")
        waitUntil(UI) { missing(readScreen(), sheetTexts).isEmpty() }
        val sheet = readScreen()
        val gaps = missing(sheet, sheetTexts)
        if (gaps.isNotEmpty()) fail("The Filter / Sort sheet is readable but shows no ${gaps.joinToString(", ")} (texts: ${sheet.take(12).joinToString(" | ")})", Cause.UNCONFIRMED)
        shot("DS-12_indices_filter_sort")
        tapLabel("BSE", "the BSE filter")
        pause(Timeouts.SETTLE)
        if (has("Sort")) engine.pressBack()
        waitUntil(UI) { !has("Sort") }
        val bse = readScreen()
        val nseRows = bse.count { it.trim() == "NSE" }
        val bseRows = bse.count { it.trim() == "BSE" }
        if (nseRows > 0 && bseRows == 0) fail("The BSE filter was applied but only NSE indices are listed", Cause.UNCONFIRMED)
        if (bseRows == 0 && !emptyShown()) fail("The BSE filter shows neither BSE indices nor an empty state", Cause.UNCONFIRMED)
        note("the BSE filter ${if (bseRows > 0) "listed $bseRows BSE index(es)" else "gave a valid empty state"}")
        engine.tapRightEdgeOf("Search")
        if (!waitUntil(UI) { has("CLEAR") }) fail("The sheet did not reopen to clear the filter", Cause.UNCONFIRMED)
        tapLabel("CLEAR", "CLEAR")
        pause(Timeouts.SETTLE)
        if (has("Sort")) engine.pressBack()
        waitUntil(UI) { !has("Sort") }
        cleanup = "restored: the filter was cleared"
        val after = readScreen().take(12)
        note(if (before.take(4) != after.take(4)) "after Clear the first rows read ${after.take(3).joinToString(" | ")}" else "Clear restored the original list")
    }

    private fun indexDetails() {
        if (!onAllIndices()) {
            ensureView(false)
            revealIndices()
            tapLabel("All Indices", "All Indices", topmost = true)
            if (!waitUntil(UI) { onAllIndices() }) fail("All Indices did not open", Cause.UNCONFIRMED)
        }
        tapLabel("NIFTY 50", "the NIFTY 50 row")
        if (!waitUntil(UI) { sheetOpen() }) fail("Tapping NIFTY 50 did not open its sheet", Cause.UNCONFIRMED)
        pause(300)
        val all = readScreen()
        if (all.none { it.contains("NIFTY 50", true) } || all.none { MONEY.containsMatchIn(it) }) {
            fail("The index sheet is readable but shows no name or LTP (texts: ${all.take(8).joinToString(" | ")})", Cause.UNCONFIRMED)
        }
        note("sheet shows the index name and market data; More Details is not offered for indices: ${if (engine.isPresent(T.SCRIP_SHEET_MORE_BUTTON)) "it is present" else "not offered (expected)"}")
        if (engine.isPresent(T.SCRIP_SHEET_CHART_BUTTON)) {
            tapOpen(T.SCRIP_SHEET_CHART_BUTTON, listOf(T.CHART_SCREEN), "Chart did not open from the index sheet")
            val started = engine.clock()
            if (!waitUntil(Timeouts.CHART_LOAD) { engine.isPresent(T.CHART_READY) }) fail("The index chart did not report ready within ${Timeouts.CHART_LOAD / 1000} s", Cause.API)
            timing(started, "index chart ready", slowMs = 8_000L)
        } else {
            note("no Chart action on the index sheet")
        }
    }

    // ═══ Alerts ════════════════════════════════════════════════════════════════════════════════

    private fun openTile(tile: String, screen: String, what: String, timeoutMs: Long = UI) {
        ensureView(false)
        if (!revealTile(tile)) blocked("The $what tile ('$tile') is not on the Investments tab (it could not be paged into view).", Cause.AUTOMATION, "Rebuild ${engine.appName} with the invest_tile_<i> tags.")
        tapOpen(tile, listOf(screen), "$what did not open", timeoutMs)
    }

    /** Pages until a Quick Tools tile is on screen. The tiles are built only while on screen, so the heading is no proof. */
    private fun revealTile(tile: String): Boolean {
        if (engine.isPresent(tile) && engine.scrollIdIntoView(tile)) return true
        if (revealId(tile)) return true
        // The tiles are in the first two screens: from wherever the page was left, start again at the top.
        scrollTop()
        pause(Timeouts.SETTLE)
        return revealId(tile)
    }

    private var testAlertName: String? = null

    private fun alertsList() {
        openTile(T.INVEST_TILE_ALERTS, T.ALERTS_SCREEN, "Alerts")
        val started = engine.clock()
        waitUntil(LOAD) { engine.isPresent(T.ALERT_ROW) || emptyShown() }
        timing(started, "alerts loaded")
        val rows = engine.countOf(T.ALERT_ROW)
        if (rows > 0) {
            val first = engine.groupedLabelsOf(T.ALERT_ROW).firstOrNull().orEmpty()
            val gaps = mutableListOf<String>()
            if (!first.contains("LTP", true)) gaps += "an LTP"
            if (!Regex("""Active|Inactive|Triggered|Expired""", RegexOption.IGNORE_CASE).containsMatchIn(first)) gaps += "a status"
            if (gaps.isNotEmpty()) fail("The first alert is readable but shows no ${gaps.joinToString(", ")} (row: ${first.replace('\n', ' ').take(100)})", Cause.UNCONFIRMED)
            val name = engine.ownLabelsOf(T.ALERT_ROW_NAME).firstOrNull()?.trim().orEmpty()
            if (name.length >= 2) {
                val typed = engine.enterText(T.ALERTS_SEARCH_INPUT, name.take(4))
                if (!typed.ok) fail("The Alerts search field did not take text: ${typed.detail}", Cause.AUTOMATION)
                if (!waitUntil(UI) { engine.ownLabelsOf(T.ALERT_ROW_NAME).any { it.contains(name.take(4), true) } }) fail("Searching '${name.take(4)}' did not list the alert '$name'", Cause.UNCONFIRMED)
                engine.enterText(T.ALERTS_SEARCH_INPUT, "")
                engine.hideKeyboard()
                note("$rows alert(s); search found '$name'")
            }
        } else {
            note("no alert is listed (a valid empty state)")
        }
        shot("DS-13_alerts")
    }

    /**
     * The New Alert form's inputs, checked quickly and never submitted: each field takes what is typed (read back), the
     * instrument field opens its search, the condition dropdown offers and keeps another option. Nothing is created: CREATE
     * is not tapped and the form is left with whatever was typed discarded.
     */
    private fun newAlertForm() {
        if (!engine.isPresent(T.ALERTS_SCREEN)) openTile(T.INVEST_TILE_ALERTS, T.ALERTS_SCREEN, "Alerts")
        tapOpen(T.ALERTS_NEW_ALERT_LINK, listOf(T.ALERT_SCREEN), "+ New Alert did not open the create page")
        val missingIds = listOf(T.ALERT_NAME_INPUT to "Alert Name", T.ALERT_SCRIP_FIELD to "instrument", T.ALERT_VALUE_INPUT to "Value", T.ALERT_CONDITION to "condition", T.ALERT_CREATE_BUTTON to "CREATE")
            .filter { !engine.isPresent(it.first) }
        if (missingIds.isNotEmpty()) fail("The New Alert page lacks ${missingIds.joinToString { it.second }}", Cause.UNCONFIRMED)
        val gaps = missing(readScreen(), listOf("Alert Name", "Property"))
        if (gaps.isNotEmpty()) fail("The New Alert page is readable but shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        val checked = mutableListOf<String>()
        // Text inputs: typed and read back.
        typeChecked(T.ALERT_NAME_INPUT, "AutoCheck", "Alert Name")
        checked += "Alert Name"
        typeChecked(T.ALERT_VALUE_INPUT, "12345", "Value")
        checked += "Value"
        // Condition dropdown: opens and keeps another option.
        val before = engine.labelsOf(T.ALERT_CONDITION).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        tapOpen(T.ALERT_CONDITION, listOf("${T.ALERT_CONDITION_ITEM_PREFIX}0"), "The condition dropdown did not open")
        val option = (0 until 6).map { "${T.ALERT_CONDITION_ITEM_PREFIX}$it" }
            .firstOrNull { engine.isPresent(it) && engine.labelsOf(it).firstOrNull { l -> l.isNotBlank() }?.trim().let { l -> l != null && !l.equals(before, true) } }
            ?: fail("The condition dropdown opened but offers no option other than '$before'", Cause.UNCONFIRMED)
        val picked = engine.labelsOf(option).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        engine.tap(option)
        if (!waitUntil(UI) { engine.labelsOf(T.ALERT_CONDITION).any { it.trim().equals(picked, true) } }) {
            fail("After picking '$picked' the condition shows '${engine.labelsOf(T.ALERT_CONDITION).firstOrNull()?.trim()}'", Cause.APP)
        }
        checked += "Condition ($before to $picked)"
        // Instrument: the field opens the app's search and Back returns to the form.
        val toSearch = engine.tapAndConfirm(T.ALERT_SCRIP_FIELD, listOf(T.SEARCH_SCREEN), UI, retryAfterMs = UI)
        alive()
        if (!toSearch.ok) fail("The instrument field did not open the search screen: ${toSearch.detail}")
        if (!engine.isPresent(T.SEARCH_SCRIP_INPUT)) fail("The instrument search opened without its search field", Cause.UNCONFIRMED)
        quickClose(T.SEARCH_SCREEN)
        if (!waitUntil(UI) { engine.isPresent(T.ALERT_SCREEN) }) fail("Back from the instrument search did not return to the New Alert form")
        checked += "Instrument search"
        if (!engine.isPresent(T.ALERT_CREATE_BUTTON)) fail("CREATE is not on the New Alert page", Cause.UNCONFIRMED)
        shot("DS-14_new_alert")
        note("inputs work: ${checked.joinToString(", ")}; CREATE present and not tapped, nothing was created")
        cleanup = "nothing created: the form was left without CREATE"
    }

    private fun typeChecked(id: String, value: String, what: String) {
        repeat(2) { attempt ->
            if (!type(id, value, what)) fail("The $what field did not take text", Cause.AUTOMATION)
            if (waitUntil(UI) { engine.fieldText(id)?.trim() == value }) return
            if (attempt == 1) fail("The $what field was set to '$value' but reads '${engine.fieldText(id)?.trim().orEmpty()}': the app replaced it", Cause.APP)
        }
    }

    // ═══ IPO ═══════════════════════════════════════════════════════════════════════════════════

    private fun ipoTabs() {
        openTile(T.INVEST_TILE_IPO, T.IPO_SCREEN, "IPO")
        val strip = stripOf(T.IPO_TAB_PREFIX)
        val seen = mutableListOf<String>()
        for (i in 0 until 6) {
            val before = readScreen(120)
            if (!tapTab(T.IPO_TAB_PREFIX, i, strip)) break
            val id = "${T.IPO_TAB_PREFIX}$i"
            var selected = waitUntil(UI) { engine.isSelected(id) }
            if (!selected) {
                // The last tab sits at the strip's edge: the first tap can be swallowed by the strip's own scroll.
                tapTab(T.IPO_TAB_PREFIX, i, strip)
                selected = waitUntil(UI) { engine.isSelected(id) }
            }
            if (!selected) {
                // Selected state not published for this tab: a changed list is the proof that the tap landed.
                val changed = waitUntil(UI) { readScreen(120) != before }
                if (!changed) fail("IPO tab ${i + 1} was tapped but does not show as selected", Cause.UNCONFIRMED)
                note("IPO tab ${i + 1} does not publish its selected state; the changed list shows the tap landed")
            }
            // Only the Ongoing rows carry an identifier; the other tabs' rows are read as text: anything beyond the tab
            // strip and the search box is content.
            val started = engine.clock()
            val ok = waitUntil(LOAD) { engine.isPresent(T.IPO_ROW) || emptyShown() || ipoContentLines() >= 2 }
            if (!ok) fail("IPO tab ${i + 1} showed neither rows nor an empty state within ${LOAD / 1000} s", Cause.API)
            val took = engine.clock() - started
            if (took > 2_000) note("IPO tab ${i + 1} took ${"%.1f".format(took / 1000.0)} s")
            seen += "${i + 1}: ${if (emptyShown() && ipoContentLines() < 2) "empty" else "${maxOf(engine.countOf(T.IPO_ROW), ipoContentLines())} line(s)"}"
        }
        if (seen.isEmpty()) fail("No IPO tab was found", Cause.AUTOMATION)
        note("tabs ${seen.joinToString(", ")}")
        tapTab(T.IPO_TAB_PREFIX, 0, strip)
        shot("DS-15_ipo_listing")
    }

    private fun ipoContentLines(): Int = readScreen(120).count { l ->
        l.length > 3 && !TAB_POSITION.matches(l) && !Regex("""^(Ongoing|Applied|Listed|Announced)\b.*\(\d+\)$""").matches(l.trim()) &&
            !l.contains("Initial Public Offering", true) && !l.contains("(IPO)") && !l.contains("Search", true) && !l.equals("Back", true)
    }

    private fun ipoDetails() {
        if (!engine.isPresent(T.IPO_SCREEN)) openTile(T.INVEST_TILE_IPO, T.IPO_SCREEN, "IPO")
        tapTab(T.IPO_TAB_PREFIX, 0, stripOf(T.IPO_TAB_PREFIX))
        val rows = rowsOrEmpty(T.IPO_ROW, "the Ongoing IPOs")
        if (rows == 0) blocked("No ongoing IPO is listed, so there are no details to open.", Cause.TEST_DATA)
        // Search for the first IPO by its own name.
        val first = engine.groupedLabelsOf(T.IPO_ROW).firstOrNull().orEmpty()
        val name = first.lines().firstOrNull()?.trim().orEmpty().split(Regex("\\s+")).firstOrNull().orEmpty()
        if (name.length >= 3 && engine.isPresent(T.IPO_SEARCH_INPUT)) {
            val typed = engine.enterText(T.IPO_SEARCH_INPUT, name.take(4))
            if (typed.ok) {
                if (!waitUntil(UI) { engine.isPresent(T.IPO_ROW) || emptyShown() }) fail("Searching '${name.take(4)}' showed neither an IPO nor an empty state", Cause.UNCONFIRMED)
                engine.enterText(T.IPO_SEARCH_INPUT, "")
                note("search '${name.take(4)}' responded")
            }
        }
        var opened = false
        for (i in 0 until minOf(engine.countOf(T.IPO_ROW), 3)) {
            engine.tapAt(T.IPO_ROW, i)
            if (waitUntil(UI) { engine.isPresent(T.IPO_DETAILS_SCREEN) }) {
                opened = true
                break
            }
        }
        if (!opened) fail("None of the first IPOs opened a details page (an applied IPO does not)", Cause.UNCONFIRMED)
        pause(Timeouts.SETTLE * 2)
        val all = readScreen()
        val gaps = missing(all, listOf("Minimum Investment", "Price Range", "Issue Open", "Issue Close", "Lot size", "Issue Size", "Application Details", "Timeline"))
        if (gaps.isNotEmpty()) fail("IPO Details is readable but shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        val cta = Regex("""UPCOMING|PRE-APPLY|APPLY|APPLIED|CLOSED""").find(all.joinToString(" "))?.value
        shot("DS-15_ipo_details")
        note("details show company, price range, investment, dates, lot and issue size, categories and the timeline; button: ${cta ?: "not readable"}")
        ipoCta = cta
    }

    private var ipoCta: String? = null

    private fun ipoApplyState() {
        val cta = ipoCta ?: notTested("The IPO details case did not read a status button.")
        val tappable = engine.isTappable(T.IPO_APPLY_BUTTON)
        when (cta) {
            "UPCOMING", "APPLIED", "CLOSED" -> if (tappable) note("$cta: the button is tappable although the IPO is $cta (informational)") else note("$cta: the button is inactive, as expected")
            else -> note("$cta: the button is ${if (tappable) "active" else "inactive"}")
        }
        // The application is never started: the owner rule that IPO Apply is not tapped stands, and no eligibility screen is opened.
        notTested("The IPO's status button read '$cta'. The tool does not tap APPLY / PRE-APPLY (an application flow is real client activity), so the eligibility message and the next screen are not exercised.")
    }

    // ═══ SDK / WebView tiles ═══════════════════════════════════════════════════════════════════

    private fun sdkTile(tile: String, label: String, ds: String) {
        ensureView(false)
        if (!revealTile(tile)) blocked("The $label tile ('$tile') is not on the Investments tab (it could not be paged into view).", Cause.AUTOMATION, "Rebuild ${engine.appName} with the invest_tile_<i> tags.")
        val tap = engine.tap(tile)
        if (!tap.ok) fail("The $label tile could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        externalLanding(label, ds)
        backFromExternal(label)
    }

    private fun webTile(tile: String, label: String, ds: String, skipIfSessionLost: Boolean = false) {
        ensureView(false)
        if (!revealTile(tile)) blocked("The $label tile ('$tile') is not on the Investments tab (it could not be paged into view).", Cause.AUTOMATION, "Rebuild ${engine.appName} with the invest_tile_<i> tags.")
        val started = engine.clock()
        val tap = engine.tap(tile)
        if (!tap.ok) fail("The $label tile could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        // Where the page opens is the first thing to find out: inside the app (a web view), or in another app (a browser,
        // seen in the recording for Back Office) which the tool cannot read. Either way the app must react at once.
        // A vendor page is behind a sign-in request that can take well over the UI time without any loading marker (Reports
        // opened its page about 8 s after the tap in the recording), so the reaction window is the dependency's, not the UI's:
        // a page of any kind (an in-app page replaces the Dashboard, with or without the web-view marker), or another app.
        val opened = waitUntil(15_000L) { engine.isPresent(T.WEB_VIEW_SCREEN) || !engine.targetInForeground() || !engine.isPresent(T.DASHBOARD_SCREEN) }
        if (!opened) fail("Tapping $label opened nothing within 15 s: no page, no other app, and the Dashboard is unchanged", Cause.UNCONFIRMED,
            "If the page should open, check the tile's handler and the vendor sign-in call.")
        if (!engine.targetInForeground()) {
            // Another app is in front. The tool cannot read it, so the evidence is the screenshot; then the app is brought back.
            val other = engine.foregroundPackage().orEmpty()
            // One look after a short moment, then straight back: the app waits in the background meanwhile, and a long stay is
            // what the recording suggests the session cannot take.
            // A page that is still loading is flat: look once a second for up to 5 s until something is drawn.
            var rendered: Boolean? = null
            val shotName = "${ds}_${label.lowercase().replace(' ', '_')}_external"
            for (look in 1..5) {
                pause(if (look == 1) 1_200L else 1_000L)
                rendered = shot(shotName)?.let { ScreenshotInspector.content(it)?.rendered }
                if (rendered == true) break
            }
            val away = engine.clock()
            if (!engine.returnToTarget()) fail("$label opened '$other' and the app could not be brought back", Cause.APP, "The tool resumed the app's launcher intent and then pressed Back.")
            // The sign-in screen shows a moment after the app is back: wait for the Dashboard or the sign-in before judging.
            waitUntil(4_000L) { loggedOut() || engine.isPresent(T.DASHBOARD_SCREEN) }
            if (skipIfSessionLost && loggedOut()) {
                // The app cannot be driven on, so the case is skipped (not failed): this was the last case of the run.
                blocked("$label opened '$other'; coming back, ${engine.appName} showed the sign-in screen (the session ended), so the flow cannot continue from the page and the case is skipped.",
                    Cause.APP, "Developers: coming back from the browser, the app lands on the login screen (check what the resume does with the feed / heartbeat session).")
            }
            failIfLoggedOut("returning from '$other'")
            note("the app was away for ${"%.1f".format((engine.clock() - away) / 1000.0)} s")
            if (rendered != true) {
                // A page the tool cannot read and whose screenshot is still flat is not verified, which is not a failure of the page.
                unverifiable("the $label page in '$other'", "Another app's page is outside the accessibility scope of this tool; the screenshot is the evidence.")
            }
            timing(started, "$label opened in '$other'", slowMs = 10_000L)
            note("$label opens in another app ('$other') and not inside ${engine.appName}; its page cannot be read by the tool, the screenshot shows ${if (rendered == true) "a drawn page" else "no verdict"}; the app was brought back")
            return
        }
        var readable = false
        waitUntil(SDK) {
            readable = readScreen(80).count { it.length > 2 } >= 5
            readable
        }
        pause(300)
        val path = shot("${ds}_${label.lowercase().replace(' ', '_')}_page")
        val rendered = path?.let { ScreenshotInspector.content(it)?.rendered }
        when {
            readable || rendered == true -> note("$label: the page shows content (${if (readable) "readable" else "drawn"})")
            else -> fail("$label opened a web page container but the page shows no content (nothing readable, flat screenshot): a container is not a loaded page", Cause.API)
        }
        timing(started, "$label page loaded", slowMs = 10_000L)
        note("$label: the sub-sections of a vendor page are not described to the tool, so only the landing page is verified")
        backFromExternal(label)
    }

    // ═══ ETF ═══════════════════════════════════════════════════════════════════════════════════

    private fun etf() {
        openTile(T.INVEST_TILE_ETF, T.ETF_SCREEN, "ETF", LOAD)
        val strip = stripOf(T.ETF_TAB_PREFIX)
        tabFailures.clear()
        val tabs = walkTabs(T.ETF_TAB_PREFIX, T.ETF_ROW, "ETF")
        if (tabs.isEmpty()) fail("No ETF tab was found", Cause.AUTOMATION)
        note("all ${tabs.size} tab(s): ${tabs.joinToString(", ")}")
        shot("DS-18_etf")
        tapTab(T.ETF_TAB_PREFIX, 0, strip)
        rowsOrEmpty(T.ETF_ROW, "the first ETF tab")
        val row = engine.groupedLabelsOf(T.ETF_ROW).firstOrNull()
        if (row != null) {
            if (!Regex("""\b(NSE|BSE)\b""").containsMatchIn(row) || !MONEY.containsMatchIn(row)) fail("The first ETF row shows no exchange or price (row: ${row.replace('\n', ' ').take(100)})", Cause.UNCONFIRMED)
            var opened = false
            val symbol = row.lines().firstOrNull { it.length > 2 && !Regex("""\b(NSE|BSE)\b""").matches(it.trim()) }?.trim()
            for (attempt in 0..2) {
                when {
                    attempt < 2 && attempt < engine.countOf(T.ETF_ROW) -> engine.tapAt(T.ETF_ROW, attempt)
                    symbol != null -> engine.tapText(symbol, false)
                    else -> break
                }
                opened = waitUntil(UI) { sheetOpen() }
                if (opened) break
            }
            if (!opened) fail("Tapping an ETF did not open its sheet", Cause.UNCONFIRMED)
            note("an ETF row opened its instrument sheet")
            quickClose(engine.firstPresent(sheetIds) ?: T.SCRIP_DETAILS_SHEET)
        }
        if (engine.isPresent(T.ETF_SEARCH_INPUT)) {
            val typed = engine.enterText(T.ETF_SEARCH_INPUT, "NIFTY")
            if (!typed.ok) fail("The ETF search field did not take text: ${typed.detail}", Cause.AUTOMATION)
            if (!waitUntil(UI) { engine.isPresent(T.ETF_ROW) || emptyShown() }) fail("Searching NIFTY showed neither an ETF nor an empty state", Cause.UNCONFIRMED)
            engine.enterText(T.ETF_SEARCH_INPUT, "")
            note("search NIFTY responded")
        }
        if (engine.isPresent(T.ETF_FILTER_BUTTON)) {
            tapOpen(T.ETF_FILTER_BUTTON, listOf(T.ETF_FILTER_SHEET), "The settings icon did not open the ETF filter sheet")
            if (engine.isPresent(T.ETF_FILTER_CLEAR)) engine.tap(T.ETF_FILTER_CLEAR)
            cleanup = "restored: the ETF filter was cleared"
            note("the filter sheet opened and was cleared")
        }
        throwTabFailures()
        leave()
    }

    // ═══ Funds ═════════════════════════════════════════════════════════════════════════════════

    private fun fundsSummary() {
        ensureView(false)
        revealAnchor(T.INVEST_TILE_OTHER, "Funds", "Investments")
        val body = engine.textsBelow(T.INVEST_TILE_OTHER, 0.65f)
        val gaps = missing(body, listOf("Available Margin", "Opening Balance", "Margin Used"))
        if (gaps.isNotEmpty()) fail("The Funds card is readable but shows no ${gaps.joinToString(", ")} (lines: ${body.take(12).joinToString(" | ")})", Cause.UNCONFIRMED)
        val money = body.count { it.contains("INR", true) && MONEY.containsMatchIn(it) }
        if (money < 3) fail("The Funds card shows $money amount(s) in currency format, expected three (lines: ${body.take(12).joinToString(" | ")})", Cause.UNCONFIRMED)
        note("three amounts in INR format (zero is valid); the add-funds amounts and Custom chip are ${if (body.any { it.contains("Custom", true) }) "present" else "not readable"}")
    }

    private fun addFunds() {
        ensureView(false)
        revealAnchor(T.INVEST_TILE_OTHER, "Funds", "Investments")
        // The Custom amount chip opens the Add Funds screen without choosing any amount.
        if (!engine.scrollTextIntoView("Custom")) fail("The Funds card shows no Custom amount chip", Cause.UNCONFIRMED)
        tapLabel("Custom", "the Custom amount chip")
        val started = engine.clock()
        if (!waitUntil(UI) { has("Add Funds") && has("Balance") }) fail("The Custom amount chip did not open the Add Funds screen", Cause.UNCONFIRMED)
        waitUntil(LOAD) { has("Payment Method") }
        timing(started, "Add Funds loaded")
        val all = readScreen()
        val gaps = missing(all, listOf("Balance", "Payment Method"))
        if (gaps.isNotEmpty()) fail("Add Funds is readable but shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        val presets = Regex("""\+\s*[\d,]{4,}""").findAll(norm(all.joinToString(" "))).count()
        val methods = listOf("UPI", "Net banking").filter { m -> all.any { it.contains(m, true) } }
        if (methods.isEmpty()) fail("Add Funds shows no payment method (UPI or Net banking)", Cause.UNCONFIRMED)
        // The amount field's hint is not a readable label: it is found as an editable field and given one digit, then emptied.
        val typed = typeLabelled("amount", "1")
        if (typed.ok) {
            typeLabelled("amount", "")
            note("the amount field took a value and was emptied (nothing was paid: payment methods were not tapped)")
        } else {
            note("the amount field was not exercised: ${typed.detail}")
        }
        shot("DS-20_add_funds")
        note("$presets preset amount(s) and the methods ${methods.joinToString()}")
        cleanup = "restored: nothing was paid or transferred"
    }

    private fun withdrawFunds() {
        ensureView(false)
        revealAnchor(T.INVEST_TILE_OTHER, "Funds", "Investments")
        // The Funds screen holds the Withdraw button; the card is the only way in that the tool has seen.
        tapLabel("Available Margin", "the Funds card")
        if (!waitUntil(UI) { has("Amount Utilized") || has("View Breakup") }) {
            blocked("Tapping the Funds card did not open the Funds screen, and no other route to Withdraw Funds is known; none is invented.", Cause.TEST_DATA,
                "Tag the Funds card (invest_funds_card) and the Withdraw button (funds_withdraw_button) so the route is explicit.")
        }
        shot("DS-20_funds")
        tapLabel("Withdraw", "Withdraw")
        val started = engine.clock()
        if (!waitUntil(UI) { has("Withdraw Funds") }) fail("Withdraw did not open the Withdraw Funds screen", Cause.UNCONFIRMED)
        waitUntil(LOAD) { has("Bank Account") }
        timing(started, "Withdraw Funds loaded")
        val all = readScreen()
        val gaps = missing(all, listOf("Balance", "Amount", "Bank Account"))
        if (gaps.isNotEmpty()) fail("Withdraw Funds is readable but shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        shot("DS-21_withdraw_funds")
        // The button is drawn only when something can be withdrawn; at a zero balance the screen ends at the pay-out timings.
        val button = has("Withdraw Now") || has("WITHDRAW")
        note("balance, amount and bank account (masked) shown; " +
            (if (button) "Withdraw Now shown" else "no Withdraw Now is published (the zero balance leaves nothing to withdraw)") + "; nothing was submitted")
        cleanup = "restored: nothing was withdrawn"
    }

    // ═══ Holdings, Scanners, Top Sectors ═══════════════════════════════════════════════════════

    private fun holdings() {
        ensureView(false)
        // The Holdings card is the one above the Scanners heading: that row's View All link, placed low, shows it whole.
        revealAnchor(T.DASHBOARD_SCANNERS_VIEW_ALL, "Holdings", "Investments", target = 0.62f)
        // Statistics only: the card shows its figures. The widget is not opened and its formatting is not judged.
        val all = engine.onScreenLines()
        val gaps = missing(all, listOf("Current Value", "Invested Amount", "Net P&L"))
        // A label shown as its translation key ("investedAmount", run f00f716f) is there but untranslated: an app defect.
        val keys = gaps.map { g -> g.split(' ').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }.replaceFirstChar(Char::lowercaseChar) }
            .filter { k -> all.any { it.trim() == k } }
        if (keys.isNotEmpty()) fail(
            "The Holdings widget shows the untranslated key ${keys.joinToString { "'$it'" }} instead of its label " +
                "(${gaps.filter { g -> keys.any { k -> k.equals(g.replace(" ", ""), ignoreCase = true) } }.joinToString()})",
            Cause.APP, "Add ${keys.joinToString()} to the app's language file (assets/language/en-US.json): the label falls back to its key.",
        )
        if (gaps.isNotEmpty()) fail("The Holdings widget is readable but shows no ${gaps.joinToString(", ")} (lines: ${all.take(14).joinToString(" | ")})", Cause.UNCONFIRMED)
        if (all.count { MONEY.containsMatchIn(it) } < 2) fail("The Holdings widget shows no amount", Cause.UNCONFIRMED)
        note("the Holdings statistics are loaded (Current Value, Invested Amount, Net P&L); the widget was not opened")
    }

    private fun scannerDashboard() {
        ensureView(false)
        val slugs = listOf("topgainers", "toplosers", "52_week_high", "52_week_low")
        val ids = slugs.map { "${T.DASHBOARD_SCANNER_TAB_PREFIX}$it" }
        // The chips are built only while on screen: page until the first one is, not until the heading is merely in the tree.
        if (!revealId(ids.first())) fail("The Scanners chips are not on the Investments tab (the first chip could not be paged into view)", Cause.UNCONFIRMED)
        val seen = mutableListOf<String>()
        var selectedExposed = false
        for ((i, slug) in slugs.withIndex()) {
            val id = ids[i]
            if (!engine.isPresent(id) && !engine.revealInList(id, ids)) {
                note("$slug chip is not offered")
                continue
            }
            val before = engine.textsBelow(T.DASHBOARD_SCANNERS_VIEW_ALL, 0.5f).take(8)
            engine.tap(id)
            pause(Timeouts.SETTLE)
            if (waitUntil(UI) { engine.isSelected(id) }) selectedExposed = true
            else if (selectedExposed) fail("The $slug chip was tapped but does not show as selected", Cause.UNCONFIRMED)
            // Cards: an instrument with an exchange and a price, or a valid empty state.
            val cards = waitUntil(LOAD) {
                val lines = engine.textsBelow(T.DASHBOARD_SCANNERS_VIEW_ALL, 0.5f)
                lines.any { it.trim() == "NSE" || it.trim() == "BSE" || it.trim() == "NFO" || it.trim() == "MCX" } || emptyShown()
            }
            if (!cards) fail("The $slug scanner showed neither cards nor an empty state", Cause.API)
            seen += slug
            if (i == 0 && before.isEmpty()) note("the first scanner showed its cards")
        }
        if (seen.isEmpty()) fail("No scanner chip was found", Cause.AUTOMATION)
        // Back to Top Gainers, the state the Dashboard opens in.
        engine.tap(ids.first())
        cleanup = "restored: Top Gainers selected again"
        note("chips ${seen.joinToString()} each showed cards or a valid empty state (cards were not read one by one)" +
            if (!selectedExposed) "; the chips do not publish their selected state in this build (rebuild ${engine.appName})" else "")
    }

    private fun scannersViewAll() {
        ensureView(false)
        tabFailures.clear()
        revealAnchor(T.DASHBOARD_SCANNERS_VIEW_ALL, "Scanners", "Investments")
        tapOpen(T.DASHBOARD_SCANNERS_VIEW_ALL, listOf(T.SCANNERS_SCREEN), "Scanners › View All did not open")
        val segments = listOf("Equity", "Futures", "Options", "Commodity")
        val seen = mutableListOf<String>()
        for ((i, name) in segments.withIndex()) {
            val id = "${T.SCANNERS_SEGMENT_PREFIX}$i"
            if (!engine.isPresent(id)) {
                note("$name segment is not offered")
                continue
            }
            engine.tap(id)
            // Selected is published by newer Tradion builds; an older one shows none selected anywhere, which is not a defect.
            if (!waitUntil(UI) { engine.isSelected(id) } && (0..3).any { engine.isSelected("${T.SCANNERS_SEGMENT_PREFIX}$it") }) {
                fail("The $name segment was tapped but does not show as selected", Cause.UNCONFIRMED)
            }
            // Every sub tab of the segment (Top Gainers, Top Losers, 52 Week High ... for Equity; the tabs differ per segment).
            val tabs = walkTabs(T.SCANNERS_TAB_PREFIX, T.SCANNER_ROW, "Scanners › $name", max = 10)
            if (tabs.isEmpty()) tabFailures += "Scanners › $name shows no sub tab"
            seen += "$name (${tabs.size} tab(s): ${tabs.joinToString(", ")})"
            // Back to the segment's first tab for the next segment.
            if (engine.isPresent("${T.SCANNERS_TAB_PREFIX}0")) engine.tap("${T.SCANNERS_TAB_PREFIX}0")
        }
        if (seen.isEmpty()) fail("No scanner segment was found", Cause.AUTOMATION)
        note("segments: ${seen.joinToString("; ")}")
        shot("DS-23_scanners")
        // Back to Equity: one row opens its sheet; one sort sheet opens and closes.
        if (engine.isPresent("${T.SCANNERS_SEGMENT_PREFIX}0")) engine.tap("${T.SCANNERS_SEGMENT_PREFIX}0")
        if (engine.isPresent("${T.SCANNERS_TAB_PREFIX}0")) engine.tap("${T.SCANNERS_TAB_PREFIX}0")
        pause(300L)
        rowsOrEmpty(T.SCANNER_ROW, "the Equity scanner")
        if (engine.isPresent(T.SCANNER_ROW)) {
            // The first row, then the second if the first tap only scrolled or was swallowed by a list still settling.
            var opened = false
            for (row in 0..1) {
                if (row >= engine.countOf(T.SCANNER_ROW)) break
                engine.tapAt(T.SCANNER_ROW, row)
                opened = waitUntil(UI) { sheetOpen() }
                if (opened) break
            }
            if (!opened) fail("Tapping a scanner row did not open its sheet", Cause.UNCONFIRMED)
            note("a scanner row opened its instrument sheet")
            quickClose(engine.firstPresent(sheetIds) ?: T.SCRIP_DETAILS_SHEET)
        }
        if (engine.isPresent(T.SCANNERS_FILTER_BUTTON)) {
            tapOpen(T.SCANNERS_FILTER_BUTTON, listOf(T.SCANNERS_FILTER_SHEET), "The sort icon did not open the scanner sheet")
            if (engine.isPresent(T.SCANNERS_FILTER_CLOSE)) engine.tap(T.SCANNERS_FILTER_CLOSE) else engine.pressBack()
            waitUntil(UI) { !engine.isPresent(T.SCANNERS_FILTER_SHEET) }
            note("the sort sheet opened and closed")
        }
        throwTabFailures()
        leave()
    }

    private fun sectorCards() {
        ensureView(false)
        revealAnchor(T.DASHBOARD_TOP_SECTORS_VIEW_ALL, "Top Sectors", "Investments")
        val all = engine.textsBelow(T.DASHBOARD_TOP_SECTORS_VIEW_ALL, 0.60f)
        val periods = listOf("3M", "6M", "1Y").filter { p -> all.any { it.trim() == p } }
        if (periods.size < 3) fail("The sector cards are readable but show only ${periods.joinToString().ifEmpty { "none" }} of 3M, 6M, 1Y (lines: ${all.take(14).joinToString(" | ")})", Cause.UNCONFIRMED)
        val sectors = all.map { it.trim() }.filter { it.length > 3 && it == it.uppercase() && it.any(Char::isLetter) && !it.contains("%") && it !in listOf("NSE", "BSE", "VIEW ALL") && it != "3M" && it != "6M" && it != "1Y" }
        if (sectors.isEmpty()) fail("The sector cards show no sector name (lines: ${all.take(14).joinToString(" | ")})", Cause.UNCONFIRMED)
        // Every complete card shows three performance figures (3M, 6M, 1Y); the row scrolls sideways, so the last card may be partial.
        // iTrade prints bare figures ("-14.17", "-16.3", run f00f716f) under the periods; the Holdings amounts in the same
        // band are bare too, so only the three lines after a card's "1Y" count.
        val figures = if (app.sectorFiguresInPercent) all.count { Regex("""^[+-]?\d+\.\d{2}%$""").matches(it.trim()) }
        else all.indices.filter { all[it].trim() == "1Y" }.sumOf { i -> all.drop(i + 1).take(3).count { Regex("""^[+-]?\d+(\.\d+)?$""").matches(it.trim()) } }
        if (figures < 3) fail("The sector cards show $figures performance figure(s), expected 3M, 6M and 1Y for at least one card (lines: ${all.take(14).joinToString(" | ")})", Cause.UNCONFIRMED)
        shot("DS-24_top_sectors")
        note("sector card(s) ${sectors.take(3).joinToString()}: $figures performance figure(s) for 3M, 6M and 1Y")
    }

    private fun sectorsViewAll() {
        ensureView(false)
        revealAnchor(T.DASHBOARD_TOP_SECTORS_VIEW_ALL, "Top Sectors", "Investments")
        tapOpen(T.DASHBOARD_TOP_SECTORS_VIEW_ALL, listOf(T.SECTORS_SCREEN), "Top Sectors › View All did not open")
        val started = engine.clock()
        if (!waitUntil(LOAD) { constituents().isNotEmpty() }) fail("The sectors page lists no constituent within ${LOAD / 1000} s", Cause.API)
        timing(started, "sector constituents loaded")
        // Every sector tab, not one: each is tapped, must change the list (or show a valid empty state) and must list constituents.
        var previous = constituents().take(3)
        val strip = stripOf(T.SECTORS_TAB_PREFIX)
        val seen = mutableListOf<String>()
        val problems = mutableListOf<String>()
        for (i in 0 until 14) {
            val id = "${T.SECTORS_TAB_PREFIX}$i"
            if (!engine.isPresent(id) && !engine.revealInList(id, strip)) break
            val label = engine.labelsOf(id).map { l -> l.lines().map { it.trim() }.filter { it.isNotEmpty() && !TAB_POSITION.matches(it) }.joinToString(" ") }
                .firstOrNull { it.isNotEmpty() } ?: "tab ${i + 1}"
            if (i > 0) {
                val tap = engine.tap(id)
                if (!tap.ok) {
                    problems += "$label could not be tapped (${tap.detail})"
                    continue
                }
            }
            val changed = waitUntil(if (i == 0) 500L else LOAD) { i == 0 || constituents().take(3).let { it.isNotEmpty() && it != previous } }
            val rows = constituents()
            if (rows.isEmpty() && !emptyShown()) problems += "$label lists no constituent and no empty state"
            else if (!changed && rows.isNotEmpty() && seen.isNotEmpty()) problems += "$label shows the same constituents as the tab before"
            previous = rows.take(3)
            seen += "$label: ${if (rows.isEmpty()) "empty" else "${rows.size}"}"
        }
        if (seen.isEmpty()) fail("No sector tab was found", Cause.AUTOMATION)
        note("all ${seen.size} sector tab(s): ${seen.joinToString(", ")}")
        if (problems.isNotEmpty()) fail(problems.joinToString("; "), Cause.UNCONFIRMED)
        leave()
    }

    /**
     * The constituent lines of the sectors page: Tradion's NSE rows carry their series ("INFY-EQ"); iTrade's are BSE rows
     * without one ("INFY | 1118.00 | BSE | -43.95", run f00f716f), so there a constituent is a lettered line directly
     * followed by its price (the page's text is read once per distinct line, so the repeated "BSE" cannot anchor a row).
     */
    private fun constituents(): List<String> {
        val lines = readScreen(120)
        if (app.sectorRowsWithSeries) return lines.filter { it.contains("-EQ") }
        val price = Regex("""^[\d,]+\.\d{2}$""")
        return lines.zipWithNext().filter { (name, next) -> name.any(Char::isLetter) && !MONEY.containsMatchIn(name) && price.matches(next.trim()) }
            .map { it.first.trim() }
    }

}

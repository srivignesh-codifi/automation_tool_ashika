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

        /**
         * The Order placement module: the order pad of one instrument per segment (IDEA on NSE, BSE, NFO and BFO; CRUDEOIL
         * or GOLD on MCX), orders placed on NSE and BSE only, and the Orders tabs checked afterwards.
         */
        val ORDER_CASES: List<Case> = listOf(
            c("OW-01", "ow_nse_pad", "order_placement", "NSE › order pad fields and validation",
                "The first exchange gets the full checks: the three tabs, fields and products, typing, the quantity buttons, the Price box with its Market / Limit / SL / SL-M order types, Advanced Options, and invalid quantity, price and trigger refused. The other exchanges only get what differs by exchange.", "High"),
            c("OW-02", "ow_nse_buy", "order_placement", "NSE › BUY orders: Regular, Limit, CO, BO, AMO",
                "With Avail. Margin 0.00 each order is sent and refused for margin; with any other margin it is skipped.", "High"),
            c("OW-03", "ow_nse_sell", "order_placement", "NSE › SELL orders: Regular, Limit, CO, BO, AMO",
                "With Avail. Margin 0.00 each order is sent and refused for margin; with any other margin it is skipped.", "High"),
            c("GT-01", "ow_gtt_pad", "order_placement", "GTT › create pad: fields, dropdowns and validation",
                "Orders > GTT > Create GTT opens the pad of the instrument with Quantity, Price, Product, the alert and condition dropdowns and a Trigger Price; an empty or zero quantity, price or trigger is refused with a message.", "High"),
            c("GT-02", "ow_gtt_create", "order_placement", "GTT › create an order (trigger that cannot be reached)",
                "A GTT order is created with a trigger far from the price (it cannot be reached today); it shows in the GTT list.", "High"),
            c("GT-03", "ow_gtt_modify", "order_placement", "GTT › modify the order",
                "The row opens a sheet with MODIFY and CANCEL; MODIFY opens the pad filled with the order's values; a new trigger and price are saved and the list shows them.", "High"),
            c("GT-04", "ow_gtt_cancel", "order_placement", "GTT › cancel the order (No, then Yes)",
                "CANCEL asks for confirmation; No keeps the order; Yes removes it from the GTT list.", "High"),
            c("BK-01", "ow_basket_create", "order_placement", "Basket › create a new basket",
                "New Basket asks for a name (an empty one is refused); the new basket is in the Basket list.", "High"),
            c("BK-02", "ow_basket_add", "order_placement", "Basket › add the instrument",
                "Add Scrips > search > ADD BUY adds the instrument to the basket (nothing is sent); the detail shows it and its margin.", "High"),
            c("BK-03", "ow_basket_modify", "order_placement", "Basket › modify the scrip",
                "The scrip row opens a sheet with MODIFY; a new quantity is saved and the scrip row shows it.", "High"),
            c("BK-04", "ow_basket_edit", "order_placement", "Basket › edit the basket (rename)",
                "A long-press on the scrip opens Edit Basket; a new name is saved and the list shows it.", "Medium"),
            c("BK-05", "ow_basket_execute", "order_placement", "Basket › execute, then cancel what stays pending",
                "With Avail. Margin 0.00 the basket is executed with the slide; its orders are refused or cancelled from Pending; nothing is executed.", "High"),
            c("BK-06", "ow_basket_delete", "order_placement", "Basket › delete the basket",
                "Delete Basket removes the basket from the list; the Basket count is back to what it was.", "Medium"),
            c("OW-04", "ow_bse_pad", "order_placement", "BSE › order pad fields and validation",
                "The BSE radio is selected and the BSE listing shows its price, funds, products and fields (the form checks are not repeated).", "High"),
            c("OW-05", "ow_bse_buy", "order_placement", "BSE › BUY orders: Regular, Limit, CO, BO, AMO",
                "With Avail. Margin 0.00 each order is sent and refused for margin; with any other margin it is skipped.", "High"),
            c("OW-06", "ow_bse_sell", "order_placement", "BSE › SELL orders: Regular, Limit, CO, BO, AMO",
                "With Avail. Margin 0.00 each order is sent and refused for margin; with any other margin it is skipped.", "High"),
            c("OW-07", "ow_nfo_pad", "order_placement", "NFO › order pad fields and validation",
                "Price, funds, lot and tick size, products (MIS, NRML only) and fields are shown for the contract. No order is placed.", "Medium"),
            c("OW-08", "ow_bfo_pad", "order_placement", "BFO › order pad fields and validation",
                "Price, funds, lot and tick size, products (MIS, NRML only) and fields are shown for the contract. No order is placed.", "Medium"),
            c("OW-09", "ow_mcx_pad", "order_placement", "MCX › order pad fields and validation",
                "Price, funds, lot and tick size, products (MIS, NRML only) and fields are shown for the contract. No order is placed.", "Medium"),
            c("OW-20", "ow_pending_clean", "order_placement", "Orders › Pending holds no order of this run",
                "No order of this run is open: any that is pending is cancelled, and Pending is back to its count before the run.", "High"),
            c("OW-21", "ow_orderbook", "order_placement", "Orders › Order Book shows every order sent, none executed",
                "The Order Book grew by the number of orders sent, all of them rejected, each with the order type it was placed as (MKT / LIMIT); the Trade Book did not change.", "High"),
            c("OW-90", "ow_state_restored", "order_placement", "The watchlist used for the order checks is back",
                "The instruments added for the checks are removed again.", "Medium"),
        )

        fun orderSteps(): List<AutomationStepResult> = ORDER_CASES.map { it.toStep() }

        val ORDER_IDS: List<String> = ORDER_CASES.map { it.id }

        /** The GTT cases: they run inside the NSE segment, after its orders. */
        val GTT_IDS: List<String> = listOf("ow_gtt_pad", "ow_gtt_create", "ow_gtt_modify", "ow_gtt_cancel")

        /** The basket cases: they run after the GTT cases, on the Orders side. */
        val BASKET_IDS: List<String> = listOf(
            "ow_basket_create", "ow_basket_add", "ow_basket_modify", "ow_basket_edit", "ow_basket_execute", "ow_basket_delete",
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

    private fun case(id: String): Case = (CASES + ORDER_CASES).first { it.id == id }

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
        step.cleanup = cleanup ?: step.cleanup
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
        // One module covers the view, search, edit and settings cases (they are groups of the Watchlist module).
        val view = modules.isOn("watchlist")
        val search = view
        val edit = view
        val settings = view
        if (!view) {
            for (module in listOf("watchlist", "watchlist_search", "watchlist_edit", "watchlist_settings")) skipModule(module)
        }
        if (!view) {
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
            if (step.status == StepStatus.PENDING) flow.skipAs(step, "not_tested", modules.skipNote("watchlist"))
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

    // ═══ Order placement (OW-xx) ═══════════════════════════════════════════════════════════════
    //
    // The order pad and the order book, on one instrument per segment. The account is LIVE, so nothing is sent unless
    // the pad itself says it will be refused (OrderPad.Gate: Avail. Margin 0.00 and a margin shortfall). Orders are only
    // ever placed on NSE and BSE; NFO, BFO and MCX get their fields checked and, for validation messages, only an
    // order button tap with a quantity of 0 or empty (which the app refuses before sending anything).

    private val pad by lazy { OrderPad(engine) }

    private class OrderSeg(
        val key: String,
        val exchange: String,
        val tab: String,
        val tabName: String,
        val queries: List<String>,
        val padId: String,
        val buyId: String?,
        val sellId: String?,
        /** The settings: this exchange is checked at all, and orders are placed on it. */
        val enabled: Boolean,
        val placeOrders: Boolean,
        val accept: (String, String) -> Boolean,
    ) {
        val equity: Boolean get() = exchange == "NSE" || exchange == "BSE"
    }

    /** The exchanges of the Order placement module, as the settings of this run say (see [OrderPlan]). */
    private val orderSegments: List<OrderSeg> by lazy {
        fun same(l: String, q: String) = baseName(nameOf(l)).equals(q, ignoreCase = true)
        val plan = modules.orders
        fun queries(key: String, extra: String? = null): List<String> {
            val script = plan.of(key).script
            // IDEA has no BFO contract in the UAT data; SENSEX (BSE's own index) is the fallback and is named in the report.
            return if (extra != null && script == "IDEA") listOf(script, extra) else listOf(script)
        }
        fun seg(key: String, exchange: String, tab: String, tabName: String, extra: String? = null, accept: (String, String) -> Boolean): OrderSeg {
            val p = plan.of(key)
            val equity = key == "nse" || key == "bse"
            return OrderSeg(
                key, exchange, tab, tabName, queries(key, extra), "ow_${key}_pad",
                if (equity) "ow_${key}_buy" else null, if (equity) "ow_${key}_sell" else null,
                p.enabled, p.place && equity, accept,
            )
        }
        listOf(
            seg("nse", "NSE", T.SEARCH_TAB_STOCKS, "Stocks") { l, q ->
                exchangeOf(l) == "NSE" && scripType(l) == "EQ" && same(l, q)
            },
            seg("bse", "BSE", T.SEARCH_TAB_STOCKS, "Stocks") { l, q ->
                exchangeOf(l) == "BSE" && (scripType(l) == null || scripType(l) == "EQ") && same(l, q)
            },
            seg("nfo", "NFO", T.SEARCH_TAB_FNO, "F&O") { l, q ->
                exchangeOf(l) == "NFO" && scripType(l) == "FUT" && same(l, q)
            },
            seg("bfo", "BFO", T.SEARCH_TAB_FNO, "F&O", extra = "SENSEX") { l, q ->
                exchangeOf(l) == "BFO" && scripType(l) != null && same(l, q)
            },
            seg("mcx", "MCX", T.SEARCH_TAB_COMMODITY, "Commodity") { l, q ->
                exchangeOf(l) == "MCX" && scripType(l) == "FUT" && same(l, q)
            },
        )
    }

    /** The GTT test's own instrument (an NSE stock), set apart from the NSE order settings; it never places a normal order. */
    private val gttSegment: OrderSeg by lazy {
        OrderSeg(
            "gtt", "NSE", T.SEARCH_TAB_STOCKS, "Stocks", listOf(modules.orders.gttScript), "ow_gtt_pad", null, null,
            enabled = true, placeOrders = false,
        ) { l, q -> exchangeOf(l) == "NSE" && scripType(l) == "EQ" && baseName(nameOf(l)).equals(q, ignoreCase = true) }
    }

    /** The instruments the settings name: the Pending cleanup looks for orders on them. */
    private val orderNames: List<String> by lazy { orderSegments.filter { it.enabled }.flatMap { it.queries }.distinct() }

    private class OrderSession {
        var rep: Rep? = null
        var added: Added? = null
        var baseline: List<String> = emptyList()
        var start = 0
        var problem: String? = null
        var removed: Boolean? = null
    }

    private val orderSessions = mutableMapOf<String, OrderSession>()
    private var orderTab: String? = null
    private var orderListName: String? = null
    private var orderListStart: Int? = null
    private var orderCleared: List<String> = emptyList()

    /** Every order the tool tapped the button for (it may have been refused afterwards), to compare with the Order Book. */
    private val ordersSent = mutableListOf<String>()

    private class OrderCounts(val pending: Int?, val book: Int?, val trades: Int?)

    private var orderBase: OrderCounts? = null

    private fun orderSegment(key: String) = orderSegments.first { it.key == key }

    // ── The watchlist the checks work in ───────────────────────────────────────────────────────

    /**
     * Picks the user watchlist the order checks add their instrument to: an empty one when there is one, otherwise the
     * emptiest (only the instrument added here is touched). A watchlist that is full cannot take an instrument, so that
     * one is cleared (every instrument removed on the Edit screen) and the report says which were removed.
     */
    private fun prepareOrderList() {
        ensureLanding()
        if (orderTab == null) {
            establishLanding()
            var best: Pair<String, Int>? = null
            for (t in editableTabs) {
                selectTab(t)
                val n = readCount()?.first ?: continue
                if (best == null || n < best.second) best = t to n
                if (n == 0) break
            }
            val pick = best?.first ?: blocked("No user watchlist count could be read, so no watchlist could be chosen for the order checks.")
            orderTab = pick
            workTab = pick
            selectTab(pick)
            orderListName = tabLabel()
            readCount()?.let {
                orderListStart = it.first
                capacity = it.second
            }
            if ((orderListStart ?: 0) > 0) {
                note("no empty watchlist: using '${orderListName.orEmpty()}' with ${orderListStart} instrument(s); only the instruments added here are removed")
            } else {
                note("using the empty watchlist '${orderListName.orEmpty()}'")
            }
        }
        toWorkTab()
        val count = readCount()?.first ?: fail("The watchlist count could not be read before the order checks")
        if (count >= capacity) clearOrderList(count)
    }

    private fun clearOrderList(count: Int) {
        openEdit()
        val rows = editRows()
        if (rows.isEmpty()) {
            escapeEdit()
            fail("The watchlist is full ($count / $capacity) but its Edit screen lists no instrument to remove")
        }
        repeat(rows.size) {
            val tap = engine.tapAt(T.REORDER_DELETE_BUTTON, 0)
            if (!tap.ok) {
                escapeEdit()
                fail("Could not tap the delete icon while clearing the full watchlist: ${tap.detail}", Cause.AUTOMATION)
            }
            pause(Timeouts.SETTLE)
        }
        if (editRows().isNotEmpty()) {
            escapeEdit()
            fail("Instruments were left on the Edit screen after clearing the full watchlist")
        }
        saveEdit("clearing the full watchlist for the order checks")
        if (!waitUntil(LOAD) { readCount()?.first == 0 }) fail("After clearing the full watchlist its count reads ${readCount()?.first}, not 0")
        orderCleared = rows.map { it.lines().firstOrNull()?.trim().orEmpty() }
        note("the watchlist was full ($count / $capacity), so all ${rows.size} instruments were removed: ${orderCleared.joinToString(", ")}")
    }

    /** Searches [seg]'s instrument and adds it to the chosen watchlist (or notes that it was already in it). */
    private fun addOrderInstrument(seg: OrderSeg, s: OrderSession) {
        s.baseline = rowLabels()
        s.start = readCount()?.first ?: fail("The watchlist count could not be read before adding the ${seg.exchange} instrument")
        val wanted = Wanted(seg.exchange, seg.tab, seg.tabName, seg.queries, "ANY", setOf(seg.exchange))
        ensureSearchOpen()
        var found: String? = null
        for (query in seg.queries) {
            try {
                searchQuery(seg.tab, seg.tabName, query)
            } catch (e: CaseEnd) {
                if (e.kind != Kind.FAIL) throw e
                continue
            }
            var scrolls = 0
            while (found == null) {
                val rows = resultRows()
                val i = rows.indices.firstOrNull { seg.accept(rows[it], query) && hasIcon(it) }
                if (i != null) {
                    val name = nameOf(rows[i]) ?: query
                    if (engine.hasDescendantAt(T.SEARCH_RESULT_ROW, i, T.SEARCH_SCRIP_ADD_BUTTON)) {
                        s.added = Added(wanted, query, rows[i], name)
                        addResult(i, name)
                        note("${seg.exchange}: added '$name' to the watchlist")
                    } else {
                        note("${seg.exchange}: '$name' was already in the watchlist")
                    }
                    s.rep = Rep(name, seg.exchange)
                    found = name
                    break
                }
                if (scrolls >= 3 || !engine.scrollPage(forward = true)) break
                scrolls++
                pause(Timeouts.SETTLE)
            }
            if (found != null) break
        }
        if (found == null) {
            blocked(
                "Search found no ${seg.exchange} instrument for ${seg.queries.joinToString(" / ")} that can be added " +
                    "(${seg.tabName} category), so its order pad cannot be opened.",
                Cause.TEST_DATA,
            )
        }
        leaveSearch()
        val rep = s.rep!!
        if (!waitUntil(LOAD) { findRow(rep) >= 0 }) fail("'${rep.name}' (${rep.exchange}) was added but is not in the watchlist")
    }

    /** The segment's instrument, set up on first use; a segment that could not be set up blocks its later cases. */
    private fun orderRep(seg: OrderSeg): Rep {
        val s = orderSessions.getOrPut(seg.key) { OrderSession() }
        s.problem?.let { blocked("The ${seg.exchange} instrument could not be set up earlier: $it") }
        s.rep?.let { return it }
        try {
            prepareOrderList()
            addOrderInstrument(seg, s)
        } catch (e: CaseEnd) {
            s.problem = e.message
            throw e
        }
        return s.rep!!
    }

    /** Takes the instrument this segment added out of the watchlist again; the result is recorded on [lastId]'s step. */
    private fun finishSegment(seg: OrderSeg, lastId: String): AutomationRunResult? {
        val s = orderSessions[seg.key] ?: return null
        val added = s.added ?: return null
        var restored = false
        try {
            if (recover()) {
                toWorkTab()
                restored = removeAdded(listOf(added), s.start) || removeExtrasOnEdit(s.baseline, s.start)
            }
        } catch (_: CaseEnd) {
        } catch (_: Stopped) {
            return stopped(flow.step(lastId))
        }
        s.removed = restored
        s.added = null
        s.rep = null
        val text = if (restored) "restored: '${added.name}' is removed from the watchlist again" else
            "FAILED: '${added.name}' may still be in the watchlist (count should be ${s.start}, reads ${readCount()?.first}); remove it by hand"
        val step = flow.step(lastId)
        step.cleanup = listOfNotNull(step.cleanup, text).joinToString("; ")
        return null
    }

    // ── Opening and leaving the pad ────────────────────────────────────────────────────────────

    private fun openPad(seg: OrderSeg, buy: Boolean) {
        val r = orderRep(seg)
        val side = if (buy) "BUY" else "SELL"
        if (!pad.isOpen()) {
            if (!sheetOpen()) {
                ensureLanding()
                toWorkTab()
                openSheet(r)
            }
            val button = if (buy) T.TRADE_BUY_BUTTON else T.TRADE_SELL_BUTTON
            if (!waitUntil(UI) { engine.isPresent(button) }) fail("The instrument sheet showed no $side button")
            val open = engine.tapAndConfirm(button, listOf(T.ORDER_WINDOW_SCREEN), UI, retryAfterMs = UI)
            alive()
            if (!open.ok) fail("$side did not open the order pad: ${open.detail}")
            pause(300)
        }
        if (!waitUntil(UI) { pad.has(T.ORDER_SUBMIT_BUTTON) }) {
            blocked(
                "The order pad carries none of the order_* identifiers, so it cannot be driven reliably.",
                Cause.ACCESSIBILITY,
                "Install a Tradion build that has the order pad tags (tradion_mobile docs/AUTOMATION_SEMANTICS.md, Order pad).",
            )
        }
        // NSE and BSE share one pad with a radio for each exchange; the pad opens on NSE whichever row was tapped.
        if (seg.equity) {
            val radio = "${T.ORDER_EXCHANGE_PREFIX}${seg.exchange.lowercase()}"
            if (engine.isPresent(radio) && !engine.isSelected(radio)) {
                if (!engine.tap(radio).ok || !waitUntil(UI) { engine.isSelected(radio) }) {
                    fail("The ${seg.exchange} radio on the order pad could not be selected", Cause.AUTOMATION)
                }
                pause(Timeouts.SETTLE)
            }
        }
        if (!pad.waitForFunds(LOAD)) note("the funds strip did not fill within ${LOAD / 1000} s")
    }

    private fun closePad() {
        if (pad.isOpen() && !flow.closeWithBack(T.ORDER_WINDOW_SCREEN)) fail("The order pad did not close on Back")
    }

    private fun setSwitch(id: String, on: Boolean): Boolean {
        val state = pad.switchState(id) ?: return false
        if (state == on) return true
        if (!pad.tap(id)) return false
        return waitUntil(UI) { pad.switchState(id) == on }
    }

    private fun ensureMarket(market: Boolean): Boolean {
        if (pad.priceIsMarket() == market) return true
        if (!pad.toggleMarketLimit()) return false
        return waitUntil(UI) { pad.priceIsMarket() == market }
    }

    // ── Field checks and validation messages (no order is placed) ──────────────────────────────

    private var mayHaveSent = 0

    /** Waits for the previous validation snackbar to go (it shows for three seconds), so a stale one is never taken for the next. */
    private fun clearMessage() {
        waitUntil(4_000) { pad.message() == null }
    }

    /**
     * Taps the order button with an invalid value in a field and expects the app's own validation snackbar. The value is
     * read back first: when it is not the invalid one that was meant, the button is NOT tapped.
     */
    private fun invalidTap(
        name: String,
        expect: Regex,
        problems: MutableList<String>,
        onClosed: () -> Unit = { mayHaveSent++ },
        guard: () -> Boolean,
    ) {
        if (!guard()) {
            problems += "$name: the invalid value could not be entered, so no order button was tapped"
            return
        }
        clearMessage()
        if (!pad.tapOrderButton()) {
            problems += "$name: the order button could not be tapped"
            return
        }
        var shown: String? = null
        waitUntil(UI + 2_000) {
            shown = pad.message()?.takeIf { expect.containsMatchIn(it) }
            shown != null || !pad.isOpen()
        }
        if (!pad.isOpen()) {
            onClosed()
            problems += "$name: the pad closed, so the app may have sent the order (check the Order Book / GTT list)"
            return
        }
        if (shown == null) {
            problems += "$name: no validation message appeared (the snackbar read '${pad.message().orEmpty().ifEmpty { "nothing" }}')"
        } else {
            note("$name → '${shown!!.take(70)}'")
        }
        clearMessage()
    }

    /** True once the full pad checks have run (on the first exchange): the other exchanges only get the light ones. */
    private var fullPadChecksDone = false

    private fun padCase(seg: OrderSeg) {
        openPad(seg, buy = true)
        val full = !fullPadChecksDone
        fullPadChecksDone = true
        if (full) {
            note("order settings: " + orderSegments.filter { it.enabled }.joinToString(", ") {
                "${it.exchange} ${it.queries.first()}${if (it.placeOrders) " (orders placed)" else ""}"
            })
        }
        note(if (full) "full checks on ${seg.exchange} (the form, its switches and the validation messages are the same on every exchange, so they are checked here only)"
        else "light checks on ${seg.exchange}: what differs by exchange (header price, funds, lot and tick size, products, fields)")
        val problems = mutableListOf<String>()
        val hidden = engine.suppressKeyboard(true)
        try {
            padChecksCommon(seg, problems)
            if (full) padChecksFull(seg, problems)
        } finally {
            if (hidden) engine.suppressKeyboard(false)
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("; "), Cause.UNCONFIRMED)
        shot("pad_checked")
        closePad()
    }

    /** What can differ from one exchange to the next: the header, the funds strip, lot / tick size, products, which fields exist. */
    private fun padChecksCommon(seg: OrderSeg, problems: MutableList<String>) {
        fun need(ok: Boolean, what: String) {
            if (!ok) problems += what
        }
        val ex = seg.exchange
        need(pad.ltp(ex) != null, "the header shows no $ex price")
        val gate = pad.gate()
        gate.available?.let { knownAvailable = it }
        note("funds: ${gate.why}")
        note("lot size ${pad.lotSize() ?: "?"}, tick size ${pad.tickSize() ?: "?"}")
        for (t in OrderPad.TABS) need(pad.hasTab(t), "the '$t' tab is missing")
        need(pad.openTab("Regular"), "the Regular tab could not be opened")
        need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_QUANTITY) }, "Regular shows no Quantity box")
        need(waitUntil(UI) { pad.has(T.ORDER_FIELD_PRICE) }, "Regular shows no Price box")
        need(pad.has(T.ORDER_SWITCH_STOPLOSS), "Regular shows no Stoploss switch")
        for (p in if (seg.equity) OrderPad.EQUITY_PRODUCTS else OrderPad.DERIVATIVE_PRODUCTS) {
            need(pad.hasProduct(p), "the product ${p.uppercase()} is not offered")
        }
        if (!seg.equity) for (p in listOf("cnc", "mtf")) need(!pad.hasProduct(p), "the product ${p.uppercase()} is offered on $ex, where only MIS and NRML exist")
        // The Lots / Quantity switch exists on NFO and BFO only.
        val lotExchange = ex == "NFO" || ex == "BFO"
        need(pad.has(T.ORDER_QUANTITY_LOT_TOGGLE) == lotExchange, if (lotExchange) "$ex shows no Lots / Quantity switch" else "$ex shows a Lots / Quantity switch, which only NFO and BFO have")
        if (pad.openTab("BO / CO")) {
            need(pad.present(T.ORDER_FIELD_STOPLOSS), "BO / CO shows no Stoploss (Cover) box")
            need(pad.present(T.ORDER_SWITCH_TARGET), "BO / CO shows no Target (Bracket) switch")
        } else problems += "the BO / CO tab could not be opened"
        if (pad.openTab("AMO")) {
            need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_QUANTITY) }, "AMO shows no Quantity box")
            need(waitUntil(UI) { pad.has(T.ORDER_FIELD_PRICE) }, "AMO shows no Price box")
        } else problems += "the AMO tab could not be opened"
        pad.openTab("Regular")
        engine.toTop()
    }

    private fun tickMultiple(price: Double, tick: Double): Boolean = tick > 0 && kotlin.math.abs(Math.round(price / tick) * tick - price) < 1e-6

    /**
     * The Price box and the order type it stands for. The arrow in the box switches Market <-> Limit, and the rest of the
     * pad follows: a Market order is a disabled box that reads "Market" and offers Market Protection, a Limit order is an
     * enabled box with a price (a whole number of ticks) and no Market Protection. With Stoploss on, the Price box of the
     * pair does the same for SL (limit) and SL-M (market), and the pair needs a Trigger Price. Leaves Market, Stoploss off.
     */
    private fun priceAndOrderType(problems: MutableList<String>, tick: Double?) {
        fun need(ok: Boolean, what: String) {
            if (!ok) problems += what
        }
        // Market order.
        if (!ensureMarket(true)) {
            problems += "the Price box could not be set to Market"
            return
        }
        need(pad.present(T.ORDER_SWITCH_MARKET_PROTECTION), "a Market order offers no Market Protection switch")
        engine.toTop()
        // Limit order.
        if (!pad.toggleMarketLimit() || !waitUntil(UI) { !pad.priceIsMarket() }) {
            problems += "the arrow in the Price box did not turn the Market order into a Limit order"
            return
        }
        val price = pad.value(T.ORDER_FIELD_PRICE)?.toDoubleOrNull()
        need(price != null && price > 0, "a Limit order's Price box holds no price (reads '${pad.value(T.ORDER_FIELD_PRICE).orEmpty()}')")
        if (price != null && tick != null) {
            need(tickMultiple(price, tick), "the Limit price $price is not a whole number of ticks ($tick)")
            val next = "%.2f".format(price + tick)
            val typed = pad.setField(T.ORDER_FIELD_PRICE, next)
            need(typed.ok && pad.value(T.ORDER_FIELD_PRICE) == next, "typing the price $next reads '${pad.value(T.ORDER_FIELD_PRICE)}' (${typed.detail})")
        }
        need(!pad.present(T.ORDER_SWITCH_MARKET_PROTECTION), "a Limit order still offers the Market Protection switch, which is for Market orders only")
        engine.toTop()
        note("Market order: disabled Price box with Market Protection; Limit order: enabled Price box prefilled ${price ?: "?"}, no Market Protection")
        if (!ensureMarket(true)) problems += "the Price box could not be switched back to Market"

        // Stoploss: SL (limit) and SL-M (market).
        val hadTrigger = pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE)
        if (!pad.tap(T.ORDER_SWITCH_STOPLOSS)) {
            problems += "the Stoploss switch could not be tapped"
            return
        }
        need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) != hadTrigger }, "the Stoploss switch did not show / hide the Trigger Price box")
        need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_SL_PRICE) }, "Stoploss (limit) shows no Price box next to the Trigger Price")
        need(pad.hasLabel("(Limit)"), "Stoploss does not say (Limit)")
        if (engine.tap(T.ORDER_SL_PRICE_TYPE_TOGGLE).ok) {
            need(waitUntil(UI) { !pad.hasField(T.ORDER_FIELD_SL_PRICE) && pad.hasLabel("(Market)") }, "the arrow in the Stoploss price box did not turn SL into SL-M (Market)")
            engine.tap(T.ORDER_SL_PRICE_TYPE_TOGGLE)
            need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_SL_PRICE) && pad.hasLabel("(Limit)") }, "the Stoploss price box did not come back to (Limit)")
        } else problems += "the arrow in the Stoploss price box could not be tapped"
        note("Stoploss: SL (limit) shows Trigger Price and Price, SL-M (market) leaves the Trigger Price only")
        pad.tap(T.ORDER_SWITCH_STOPLOSS)
        waitUntil(UI) { pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) == hadTrigger }
    }

    /** The checks that are the same on every exchange (one widget serves them all): run once, on the first exchange. */
    private fun padChecksFull(seg: OrderSeg, problems: MutableList<String>) {
        fun need(ok: Boolean, what: String) {
            if (!ok) problems += what
        }
        val gate = pad.gate()
        val lot = pad.lotSize()
        val tick = pad.tickSize()
        pad.openTab("Regular")

        // Quantity: typed, and the two step buttons.
        val typed = pad.setField(T.ORDER_FIELD_QUANTITY, "2")
        need(typed.ok && pad.value(T.ORDER_FIELD_QUANTITY) == "2", "typing 2 in Quantity reads '${pad.value(T.ORDER_FIELD_QUANTITY)}' (${typed.detail})")
        val before = pad.value(T.ORDER_FIELD_QUANTITY)
        need(pad.stepQuantity(plus = true), "the Quantity + button could not be tapped")
        need(waitUntil(UI) { pad.value(T.ORDER_FIELD_QUANTITY) != before }, "the Quantity + button did not change the quantity (still '${pad.value(T.ORDER_FIELD_QUANTITY)}')")
        pad.stepQuantity(plus = false)

        // Price and the order type it stands for (Market, Limit, SL, SL-M).
        priceAndOrderType(problems, tick)

        // Advanced options: what it opens lies below the fold, so it is paged to.
        if (pad.tap(T.ORDER_ADVANCED_OPTIONS)) {
            need(pad.present("${T.ORDER_VALIDITY_PREFIX}day"), "Advanced Options did not show Validity DAY")
            need(pad.has("${T.ORDER_VALIDITY_PREFIX}ioc"), "Advanced Options did not show Validity IOC")
            if (seg.equity) need(pad.present(T.ORDER_FIELD_DISCLOSED_QUANTITY), "Advanced Options shows no Disclosed Quantity box")
            pad.tap(T.ORDER_ADVANCED_OPTIONS)
        } else problems += "'Advanced Options' could not be reached and opened"
        need(pad.present(T.ORDER_VIEW_BROKERAGE), "'View Brokerage' is not on the pad")
        engine.toTop()

        // Validation messages: only with Avail. Margin 0.00 (nothing could be accepted anyway).
        if (!gate.ok) {
            note("validation messages were not triggered: ${gate.why}")
            return
        }
        val validQty = (lot ?: 1).toString()
        fun qtyIs(v: String): Boolean =
            pad.setField(T.ORDER_FIELD_QUANTITY, v).ok && pad.value(T.ORDER_FIELD_QUANTITY).let { if (v.isEmpty()) it.isNullOrBlank() else it == v }
        // An invalid quantity is refused before anything is sent. Regular and AMO show a message; BO / CO names its Target first.
        for (tab in listOf("Regular", "AMO")) {
            if (!pad.openTab(tab)) {
                problems += "$tab: the tab could not be opened for the validation check"
                continue
            }
            invalidTap("$tab · Quantity 0", Regex("zero|empty", RegexOption.IGNORE_CASE), problems) { qtyIs("0") }
            invalidTap("$tab · Quantity empty", Regex("empty|zero", RegexOption.IGNORE_CASE), problems) { qtyIs("") }
            qtyIs(validQty)
        }
        pad.openTab("Regular")
        qtyIs(validQty)
        // A Limit order needs a price that is a whole number of ticks.
        if (ensureMarket(false)) {
            invalidTap("Limit · Price empty", Regex("price|empty|zero", RegexOption.IGNORE_CASE), problems) {
                pad.setField(T.ORDER_FIELD_PRICE, "").ok && pad.value(T.ORDER_FIELD_PRICE).isNullOrBlank()
            }
            invalidTap("Limit · Price 0", Regex("zero|price", RegexOption.IGNORE_CASE), problems) {
                pad.setField(T.ORDER_FIELD_PRICE, "0").ok && pad.value(T.ORDER_FIELD_PRICE) == "0"
            }
            if (tick != null && tick >= 0.02) {
                val bad = "%.2f".format(tick / 2)
                invalidTap("Limit · Price off the tick size ($bad)", Regex("tick", RegexOption.IGNORE_CASE), problems) {
                    pad.setField(T.ORDER_FIELD_PRICE, bad).ok && pad.value(T.ORDER_FIELD_PRICE) == bad
                }
            } else note("the tick size is ${tick ?: "unknown"}: the box takes two decimals, so no off-tick price can be typed")
            ensureMarket(true)
        } else problems += "the Price box could not be switched to Limit for the price checks"
        // A stoploss order needs its Trigger Price.
        if (pad.tap(T.ORDER_SWITCH_STOPLOSS) && waitUntil(UI) { pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) }) {
            invalidTap("Stoploss · Trigger Price empty", Regex("trig|empty|zero", RegexOption.IGNORE_CASE), problems) {
                pad.setField(T.ORDER_FIELD_TRIGGER_PRICE, "").ok && pad.value(T.ORDER_FIELD_TRIGGER_PRICE).isNullOrBlank()
            }
            pad.tap(T.ORDER_SWITCH_STOPLOSS)
            waitUntil(UI) { !pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) }
        } else problems += "the Stoploss switch could not be turned on for the Trigger Price check"
        if (pad.openTab("BO / CO")) {
            // BO / CO checks its Target (on by default, 0) and Stoploss (Cover) first, so the message names them whatever else is wrong.
            val bracket = Regex("target|stoploss|trig|trail|zero|empty", RegexOption.IGNORE_CASE)
            invalidTap("BO / CO · Quantity 0", bracket, problems) { qtyIs("0") }
            qtyIs(validQty)
            invalidTap("BO / CO · Stoploss (Cover) 0", bracket, problems) {
                pad.setField(T.ORDER_FIELD_STOPLOSS, "0").ok && pad.value(T.ORDER_FIELD_STOPLOSS) == "0"
            }
        }
    }

    // ── Placing orders (NSE and BSE only) ──────────────────────────────────────────────────────

    private val placeVariants = listOf("Regular · Market", "Regular · Limit", "CO", "BO", "AMO")

    private class Placed(val kind: Int, val text: String) {
        companion object {
            const val SENT = 0
            const val SKIPPED = 1
            const val FAILED = 2
        }
    }

    /** Sets the pad up for [variant]; null when it is ready, otherwise what could not be set. */
    private fun configureVariant(seg: OrderSeg, variant: String): String? {
        fun product(): String? =
            if (pad.tap("${T.ORDER_PRODUCT_PREFIX}mis") && waitUntil(UI) { pad.productSelected("mis") }) null else "the MIS product could not be selected"
        return when (variant) {
            "Regular · Market" -> if (!pad.openTab("Regular")) "the Regular tab could not be opened"
            else if (!ensureMarket(true)) "the Price box could not be set to Market" else product()
            "Regular · Limit" -> if (!pad.openTab("Regular")) "the Regular tab could not be opened"
            else if (!ensureMarket(false)) "the Price box could not be set to Limit" else product()
            "CO", "BO" -> {
                if (!pad.openTab("BO / CO")) return "the BO / CO tab could not be opened"
                val bracket = variant == "BO"
                if (!setSwitch(T.ORDER_SWITCH_TARGET, bracket)) return "the Target (Bracket) switch could not be read or turned ${if (bracket) "on" else "off"}"
                // The cover stoploss, and for a bracket the target and trailing stoploss, are distances from the price: a
                // percent of it, in whole ticks. The pad refuses an empty or zero one.
                val ltp = pad.ltp(seg.exchange) ?: return "the ${seg.exchange} price could not be read to size the stoploss and target"
                val tick = pad.tickSize() ?: 0.05
                fun ticks(points: Double) = "%.2f".format(maxOf(1.0, Math.round(points / tick).toDouble()) * tick)
                val points = maxOf(ltp * 0.01, tick * 2)
                if (!pad.setField(T.ORDER_FIELD_STOPLOSS, ticks(points)).ok) return "the Stoploss (Cover) box could not be filled"
                if (bracket) {
                    if (!pad.setField(T.ORDER_FIELD_TARGET, ticks(points)).ok) return "the Target box could not be filled"
                    if (!pad.setField(T.ORDER_FIELD_TRAILING_STOPLOSS, ticks(points / 2)).ok) return "the Trailing Stoploss box could not be filled"
                }
                null
            }
            else -> if (!pad.openTab("AMO")) "the AMO tab could not be opened" else product()
        }
    }

    private fun placeVariant(seg: OrderSeg, buy: Boolean, variant: String): Placed {
        val side = if (buy) "BUY" else "SELL"
        val label = "${seg.exchange} $side $variant"
        try {
            openPad(seg, buy)
            val hidden = engine.suppressKeyboard(true)
            // A pad that sent nothing is reused for the next variant (an order that is sent closes it).
            var keepOpen = false
            try {
                configureVariant(seg, variant)?.let { return Placed(Placed.FAILED, "$label: $it") }
                // The margin is worked out about a second after the last change; the gate reads it only once it has settled.
                pause(1_000)
                pad.waitForFunds(LOAD)
                val gate = pad.settledGate(6_000)
                if (!gate.ok) {
                    keepOpen = true
                    return Placed(Placed.SKIPPED, "$label: not sent, ${gate.why}" +
                        if (!buy && gate.available != null && gate.required != null && gate.required <= 0.0) {
                            " (the pad shows no margin for a SELL here, so the exchange might accept it; a SELL is only sent when the pad itself predicts the refusal)"
                        } else "")
                }
                if (!pad.submitIs(buy)) return Placed(Placed.FAILED, "$label: the pad's button is not $side ORDER")
                shot("${seg.key}_${side.lowercase()}_${variant.filter { it.isLetter() }.lowercase()}")
                clearMessage()
                if (!pad.tapOrderButton()) return Placed(Placed.FAILED, "$label: the order button could not be tapped")
                ordersSent += label
                var message: String? = null
                waitUntil(LOAD) {
                    message = pad.message()
                    !pad.isOpen() || message != null
                }
                return when {
                    !pad.isOpen() -> Placed(Placed.SENT, "$label: sent (${gate.why}); the pad closed")
                    message?.contains(Regex("margin|reject|fund|insufficient", RegexOption.IGNORE_CASE)) == true ->
                        Placed(Placed.SENT, "$label: sent and refused: '${message!!.take(80)}'")
                    message != null -> {
                        ordersSent.removeAt(ordersSent.lastIndex)
                        Placed(Placed.FAILED, "$label: the app's validation stopped it: '${message!!.take(80)}'")
                    }
                    else -> {
                        // A sent order closes the pad; an open pad with no message is a refusal that only outlines the box in red.
                        ordersSent.removeAt(ordersSent.lastIndex)
                        Placed(Placed.FAILED, "$label: the pad refused the values without a message (it stayed open for ${LOAD / 1000} s with the order not sent)")
                    }
                }
            } finally {
                if (hidden) engine.suppressKeyboard(false)
                if (pad.isOpen() && !keepOpen) flow.closeWithBack(T.ORDER_WINDOW_SCREEN)
            }
        } catch (e: CaseEnd) {
            if (e.kind != Kind.FAIL) throw e
            return Placed(Placed.FAILED, "$label: ${e.message}")
        }
    }

    private fun placeCase(seg: OrderSeg, buy: Boolean) {
        val results = mutableListOf<Placed>()
        for (variant in placeVariants) {
            // Two failures in a row mean the pad itself is not being driven: the rest would only repeat them.
            if (results.size >= 2 && results.takeLast(2).all { it.kind == Placed.FAILED }) {
                results += Placed(Placed.SKIPPED, "${seg.exchange} ${if (buy) "BUY" else "SELL"} $variant: not attempted after two failures")
                continue
            }
            results += placeVariant(seg, buy, variant)
        }
        val sent = results.filter { it.kind == Placed.SENT }
        val skipped = results.filter { it.kind == Placed.SKIPPED }
        val failed = results.filter { it.kind == Placed.FAILED }
        sent.forEach { note(it.text) }
        skipped.forEach { note(it.text) }
        if (failed.isNotEmpty()) {
            fail(failed.joinToString("; ") { it.text } + " (${sent.size} sent, ${skipped.size} skipped)", Cause.UNCONFIRMED)
        }
        if (sent.isEmpty()) notTested("No order was sent: ${skipped.joinToString("; ") { it.text.substringAfter(": ") }.take(300)}")
    }

    // ── GTT orders: create, modify, cancel (Orders > GTT) ──────────────────────────────────────
    //
    // A GTT order is an instruction that is placed when the price reaches a trigger. The test creates one with a trigger
    // that cannot be reached (far above the price for "Greater than", far below for "Less than"), only while Avail. Margin
    // is 0.00 (so even a triggered order would be refused), modifies it, and cancels it. Nothing is left behind: a sweep at
    // the end cancels any GTT of this run that is still listed.

    private class GttMade(val script: String, var price: Double, var trigger: Double, val condition: String) {
        var cancelled = false
    }

    private var gttMade: GttMade? = null
    private var gttBase: Int? = null
    private var gttMayExist = false

    /** The Avail. Margin the order pad last showed (any pad), for the GTT gate. */
    private var knownAvailable: Double? = null

    private fun fmt(v: Double) = "%.2f".format(v)

    /** Opens Orders > GTT and returns the number in the tab's title. */
    private fun openGttTab(): Int {
        openOrdersTab(T.ORDERS_TAB_GTT)
        waitUntil(LOAD) { tabCount(T.ORDERS_TAB_GTT) != null }
        waitUntil(UI + 2_000) { engine.isPresent(T.GTT_ROW) || engine.isPresent("ui_empty") || tabCount(T.ORDERS_TAB_GTT) == 0 }
        return tabCount(T.ORDERS_TAB_GTT) ?: fail("The GTT count could not be read on the Orders tab")
    }

    private fun gttRows(): List<String> = engine.groupedLabelsOf(T.GTT_ROW)

    private val gttValues = Regex("""(\d[\d,]*\.\d+)\s*/\s*(\d[\d,]*\.\d+)""")

    /** The row of this run's GTT order: named [script] and, when known, showing `price / trigger`. -1 when it is not listed. */
    private fun gttRowIndex(script: String, price: Double?, trigger: Double?): Int {
        val rows = gttRows()
        val named = rows.indices.filter { tokens(rows[it]).firstOrNull()?.uppercase()?.startsWith(script.uppercase()) == true }
        if (price == null || trigger == null) return named.firstOrNull() ?: -1
        return named.firstOrNull { i ->
            gttValues.find(rows[i])?.let { m ->
                val p = m.groupValues[1].replace(",", "").toDoubleOrNull()
                val t = m.groupValues[2].replace(",", "").toDoubleOrNull()
                p != null && t != null && kotlin.math.abs(p - price) < 0.005 && kotlin.math.abs(t - trigger) < 0.005
            } == true
        } ?: -1
    }

    /** Create GTT -> instrument search -> the GTT pad of [seg]'s instrument. */
    private fun openGttPad(seg: OrderSeg) {
        val script = seg.queries.first()
        openGttTab()
        val link = if (engine.isPresent(T.GTT_CREATE_LINK)) T.GTT_CREATE_LINK else T.GTT_CREATE_BUTTON
        val search = engine.tapAndConfirm(link, listOf(T.SEARCH_SCREEN), UI + 2_000, retryAfterMs = UI + 2_000)
        alive()
        if (!search.ok) fail("Create GTT did not open the instrument search: ${search.detail}")
        val typed = engine.enterText(T.SEARCH_SCRIP_INPUT, script)
        if (!typed.ok) fail("Could not type '$script' in the GTT instrument search: ${typed.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { resultRows().any { seg.accept(it, script) } }) {
            fail("Searching '$script' for a GTT showed no ${seg.exchange} result", Cause.API)
        }
        val index = resultRows().indexOfFirst { seg.accept(it, script) }
        val tap = engine.tapAt(T.SEARCH_RESULT_ROW, index)
        if (!tap.ok) fail("Could not open '$script' from the GTT search: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { pad.isOpen() && pad.has(T.ORDER_SUBMIT_BUTTON) }) {
            fail("The GTT pad did not open for '$script' (a search result opens it only for a stock, not an index)")
        }
        pause(500)
    }

    private fun gttSubmitSays(word: String): Boolean =
        engine.labelsOf(T.ORDER_SUBMIT_BUTTON).any { it.contains(word, ignoreCase = true) }

    private fun gttChoose(id: String, index: Int): Boolean {
        if (!engine.tap(id).ok) return false
        if (!waitUntil(UI) { engine.isPresent("${id}_item_$index") }) return false
        if (!engine.tap("${id}_item_$index").ok) return false
        return waitUntil(UI) { !engine.isPresent("${id}_item_$index") }
    }

    private fun gttCondition(): String = engine.labelsOf(T.GTT_CONDITION).joinToString(" ")

    // GT-01 ---------------------------------------------------------------------------------------

    private fun gttPadCase(seg: OrderSeg) {
        openGttPad(seg)
        val problems = mutableListOf<String>()
        val hidden = engine.suppressKeyboard(true)
        try {
            gttPadChecks(seg, problems)
        } finally {
            if (hidden) engine.suppressKeyboard(false)
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("; "), Cause.UNCONFIRMED)
        shot("gtt_pad_checked")
        if (pad.isOpen() && !flow.closeWithBack(T.ORDER_WINDOW_SCREEN)) fail("The GTT pad did not close on Back")
    }

    private fun gttPadChecks(seg: OrderSeg, problems: MutableList<String>) {
        fun need(ok: Boolean, what: String) {
            if (!ok) problems += what
        }
        need(gttSubmitSays("GTT"), "the pad's button does not say CREATE GTT ORDER")
        need(pad.ltp(seg.exchange) != null, "the header shows no ${seg.exchange} price")
        need(pad.hasField(T.ORDER_FIELD_QUANTITY), "the GTT pad shows no Quantity box")
        need(pad.hasField(T.ORDER_FIELD_SL_PRICE), "the GTT pad shows no Price box")
        need(pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE), "the GTT pad shows no Trigger Price box")
        need(pad.hasProduct("cnc"), "the GTT pad does not offer CNC for ${seg.exchange}")
        need(!pad.has(T.ORDER_MARGIN_AVAILABLE), "the GTT pad shows a funds strip, which a GTT does not use")
        need(engine.isPresent(T.GTT_ALERT_TYPE), "the GTT pad has no alert dropdown (LTP / 52W High)")
        need(engine.isPresent(T.GTT_CONDITION), "the GTT pad has no condition dropdown (Less than / Greater than)")
        // The '% of LTP' sits inside the Trigger Price text field, whose node swallows it: it is noted when readable, never required.
        waitUntil(UI) { engine.labelsOf(T.GTT_TRIGGER_PERCENT).any { it.contains("% of LTP") } }
        val price0 = pad.value(T.ORDER_FIELD_SL_PRICE).orEmpty()
        val trigger0 = pad.value(T.ORDER_FIELD_TRIGGER_PRICE).orEmpty()
        val qty0 = pad.value(T.ORDER_FIELD_QUANTITY).orEmpty()
        note("defaults: alert ${engine.labelsOf(T.GTT_ALERT_TYPE).joinToString(" ")}, condition ${gttCondition()}, quantity $qty0, price $price0, trigger $trigger0 (${engine.labelsOf(T.GTT_TRIGGER_PERCENT).firstOrNull().orEmpty()})")
        need(gttCondition().contains("Greater", ignoreCase = true), "the default condition is '${gttCondition()}', not Greater than")

        // The condition and alert dropdowns.
        if (gttChoose(T.GTT_CONDITION, 0)) {
            need(waitUntil(UI) { gttCondition().contains("Less", ignoreCase = true) }, "choosing 'Less than' left the dropdown reading '${gttCondition()}'")
            gttChoose(T.GTT_CONDITION, 1)
            need(waitUntil(UI) { gttCondition().contains("Greater", ignoreCase = true) }, "choosing 'Greater than' again left the dropdown reading '${gttCondition()}'")
        } else problems += "the condition dropdown could not be opened and a condition chosen"
        if (gttChoose(T.GTT_ALERT_TYPE, 1)) {
            // 52W High takes its trigger from the instrument, so the Trigger Price box stops being an input.
            need(waitUntil(UI) { !pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) }, "with the 52W High alert the Trigger Price box is still editable")
            gttChoose(T.GTT_ALERT_TYPE, 0)
            need(waitUntil(UI) { pad.hasField(T.ORDER_FIELD_TRIGGER_PRICE) }, "with the LTP alert back the Trigger Price box is not editable again")
        } else note("the alert dropdown could not be switched to 52W High")

        // Quantity buttons.
        val before = pad.value(T.ORDER_FIELD_QUANTITY)
        if (pad.stepQuantity(plus = true)) {
            need(waitUntil(UI) { pad.value(T.ORDER_FIELD_QUANTITY) != before }, "the Quantity + button did not change the quantity")
            pad.stepQuantity(plus = false)
        } else problems += "the Quantity + button could not be tapped"

        // What the pad refuses before anything is sent (client side: nothing reaches the server).
        val onClosed = { gttMayExist = true }
        fun restore() {
            pad.setField(T.ORDER_FIELD_QUANTITY, qty0.ifEmpty { "1" })
            pad.setField(T.ORDER_FIELD_SL_PRICE, price0)
            pad.setField(T.ORDER_FIELD_TRIGGER_PRICE, trigger0)
        }
        fun tryField(name: String, id: String, value: String, expect: Regex) {
            invalidTap(name, expect, problems, onClosed) {
                pad.setField(id, value).ok && pad.value(id).let { if (value.isEmpty()) it.isNullOrBlank() else it == value }
            }
            restore()
        }
        tryField("GTT · Quantity 0", T.ORDER_FIELD_QUANTITY, "0", Regex("zero|empty", RegexOption.IGNORE_CASE))
        tryField("GTT · Quantity empty", T.ORDER_FIELD_QUANTITY, "", Regex("empty|zero", RegexOption.IGNORE_CASE))
        tryField("GTT · Price empty", T.ORDER_FIELD_SL_PRICE, "", Regex("price|empty", RegexOption.IGNORE_CASE))
        tryField("GTT · Price 0", T.ORDER_FIELD_SL_PRICE, "0", Regex("zero|price", RegexOption.IGNORE_CASE))
        tryField("GTT · Trigger Price empty", T.ORDER_FIELD_TRIGGER_PRICE, "", Regex("trig|empty", RegexOption.IGNORE_CASE))
        tryField("GTT · Trigger Price 0", T.ORDER_FIELD_TRIGGER_PRICE, "0", Regex("zero|trig", RegexOption.IGNORE_CASE))
    }

    // GT-02 ---------------------------------------------------------------------------------------

    private fun gttCreate(seg: OrderSeg) {
        val script = seg.queries.first()
        // No funds read and no trip to the Watchlist: the order cannot be triggered, because the trigger is far from the
        // price (never nearer than 1.4x above or 0.6x below, beyond a day's price band) and the GTT is cancelled at once.
        knownAvailable?.let { note("funds seen on a pad in this run: Avail. Margin ${fmt(it)}") }
        val base = openGttTab()
        gttBase = base
        val refused = mutableListOf<String>()
        // A trigger the price cannot reach today: far above for "Greater than", far below for "Less than". The server may
        // refuse an unrealistic one, so a nearer one is tried next, never one that could be reached.
        for ((greater, factor) in listOf(true to 3.0, true to 1.6, false to 0.4)) {
            openGttPad(seg)
            val ltp = pad.ltp(seg.exchange) ?: fail("The GTT pad shows no ${seg.exchange} price to place the trigger against")
            val tick = pad.tickSize() ?: 0.05
            val trigger = Math.round(ltp * factor / tick) * tick
            val safe = trigger > 0 && (if (greater) trigger >= ltp * 1.4 else trigger <= ltp * 0.6)
            if (!safe) {
                closeGttPad()
                refused += "trigger ${fmt(trigger)} is too close to the price ${fmt(ltp)} to be safe"
                continue
            }
            if (!gttChoose(T.GTT_CONDITION, if (greater) 1 else 0) && !(greater && gttCondition().contains("Greater", ignoreCase = true))) {
                closeGttPad()
                fail("The condition could not be set to ${if (greater) "Greater than" else "Less than"}", Cause.AUTOMATION)
            }
            if (!pad.setField(T.ORDER_FIELD_SL_PRICE, fmt(trigger)).ok || !pad.setField(T.ORDER_FIELD_TRIGGER_PRICE, fmt(trigger)).ok) {
                closeGttPad()
                fail("The price and trigger could not be entered", Cause.AUTOMATION)
            }
            val label = "${if (greater) ">" else "<"} ${fmt(trigger)} (price ${fmt(ltp)})"
            shot("gtt_create_${if (greater) "above" else "below"}")
            clearMessage()
            if (!pad.tapOrderButton()) {
                closeGttPad()
                fail("The CREATE GTT ORDER button could not be tapped", Cause.AUTOMATION)
            }
            var message: String? = null
            waitUntil(LOAD) {
                message = pad.message()
                !pad.isOpen() || message != null
            }
            if (pad.isOpen()) {
                refused += "$label refused: '${message.orEmpty().take(80)}'"
                closeGttPad()
                continue
            }
            // The pad closes on success and on a server refusal alike: the list is what tells them apart.
            val now = openGttTab()
            val index = if (now > base) gttRowIndex(script, trigger, trigger) else -1
            if (index >= 0) {
                gttMade = GttMade(script, trigger, trigger, if (greater) ">" else "<")
                note("GTT created: $label; the GTT list went from $base to $now: '${gttRows()[index].replace(Regex("""\s+"""), " ").take(80)}'")
                return
            }
            refused += "$label: the pad closed but the GTT list shows $now (was $base)"
            if (now > base) gttMayExist = true
        }
        fail("No GTT order was created: ${refused.joinToString("; ")}", Cause.UNCONFIRMED)
    }

    private fun closeGttPad() {
        if (pad.isOpen() && !flow.closeWithBack(T.ORDER_WINDOW_SCREEN)) fail("The GTT pad did not close on Back")
    }

    // GT-03 ---------------------------------------------------------------------------------------

    private fun gttOpenSheet(made: GttMade) {
        openGttTab()
        val index = gttRowIndex(made.script, made.price, made.trigger)
        if (index < 0) fail("The GTT order (${fmt(made.price)} / ${fmt(made.trigger)}) is not in the GTT list; rows: ${gttRows().take(3).joinToString(" | ") { it.replace(Regex("""\s+"""), " ").take(50) }}")
        val tap = engine.tapAt(T.GTT_ROW, index)
        if (!tap.ok) fail("The GTT row could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.GTT_SHEET_MODIFY) && engine.isPresent(T.GTT_SHEET_CANCEL) }) {
            fail("Tapping the GTT row did not open the sheet with MODIFY and CANCEL")
        }
    }

    private fun gttModify(seg: OrderSeg) {
        val made = gttMade ?: notTested("No GTT order was created, so there is nothing to modify.")
        gttOpenSheet(made)
        val open = engine.tap(T.GTT_SHEET_MODIFY)
        if (!open.ok) fail("MODIFY could not be tapped: ${open.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { pad.isOpen() && pad.has(T.ORDER_SUBMIT_BUTTON) }) fail("MODIFY did not open the GTT pad")
        pause(500)
        val problems = mutableListOf<String>()
        val hidden = engine.suppressKeyboard(true)
        try {
            fun need(ok: Boolean, what: String) {
                if (!ok) problems += what
            }
            need(gttSubmitSays("MODIFY"), "the pad's button does not say MODIFY GTT ORDER")
            val price = pad.value(T.ORDER_FIELD_SL_PRICE)?.toDoubleOrNull()
            val trigger = pad.value(T.ORDER_FIELD_TRIGGER_PRICE)?.toDoubleOrNull()
            need(price != null && kotlin.math.abs(price - made.price) < 0.005, "the Price box reads '${pad.value(T.ORDER_FIELD_SL_PRICE)}', not the order's ${fmt(made.price)}")
            need(trigger != null && kotlin.math.abs(trigger - made.trigger) < 0.005, "the Trigger Price box reads '${pad.value(T.ORDER_FIELD_TRIGGER_PRICE)}', not the order's ${fmt(made.trigger)}")
            need(gttCondition().contains(if (made.condition == ">") "Greater" else "Less", ignoreCase = true), "the condition reads '${gttCondition()}', not the order's ${made.condition}")
            note("modify pad filled with price ${pad.value(T.ORDER_FIELD_SL_PRICE)}, trigger ${pad.value(T.ORDER_FIELD_TRIGGER_PRICE)}, condition ${gttCondition()}")
            // An invalid change is refused here too, before anything is sent.
            val qty0 = pad.value(T.ORDER_FIELD_QUANTITY).orEmpty().ifEmpty { "1" }
            invalidTap("Modify · Quantity empty", Regex("empty|zero", RegexOption.IGNORE_CASE), problems, { gttMayExist = true }) {
                pad.setField(T.ORDER_FIELD_QUANTITY, "").ok && pad.value(T.ORDER_FIELD_QUANTITY).isNullOrBlank()
            }
            pad.setField(T.ORDER_FIELD_QUANTITY, qty0)
            if (problems.isNotEmpty()) {
                closeGttPad()
                fail(problems.joinToString("; "), Cause.UNCONFIRMED)
            }
            // The trigger moves further from the price, so the order still cannot be reached.
            val tick = pad.tickSize() ?: 0.05
            val next = Math.round(made.trigger * (if (made.condition == ">") 1.1 else 0.9) / tick) * tick
            if (!pad.setField(T.ORDER_FIELD_SL_PRICE, fmt(next)).ok || !pad.setField(T.ORDER_FIELD_TRIGGER_PRICE, fmt(next)).ok) {
                closeGttPad()
                fail("The new price and trigger could not be entered", Cause.AUTOMATION)
            }
            shot("gtt_modify")
            clearMessage()
            if (!pad.tapOrderButton()) {
                closeGttPad()
                fail("The MODIFY GTT ORDER button could not be tapped", Cause.AUTOMATION)
            }
            var message: String? = null
            waitUntil(LOAD) {
                message = pad.message()
                !pad.isOpen() || message != null
            }
            if (pad.isOpen()) {
                val said = message.orEmpty().take(80)
                closeGttPad()
                fail("The modification was refused: '$said'")
            }
            openGttTab()
            if (!waitUntil(LOAD) { gttRowIndex(made.script, next, next) >= 0 }) {
                fail("After MODIFY the GTT list does not show the new price and trigger ${fmt(next)}; rows: ${gttRows().take(3).joinToString(" | ") { it.replace(Regex("""\s+"""), " ").take(50) }}")
            }
            note("modified: price and trigger ${fmt(made.trigger)} → ${fmt(next)}, shown in the GTT list")
            made.price = next
            made.trigger = next
        } finally {
            if (hidden) engine.suppressKeyboard(false)
        }
    }

    // GT-04 ---------------------------------------------------------------------------------------

    /** Cancels this run's GTT order from the list: CANCEL on its sheet, then the dialog. With [declineFirst] No is tried first. */
    private fun gttCancelOnce(made: GttMade, declineFirst: Boolean) {
        gttOpenSheet(made)
        if (!engine.tap(T.GTT_SHEET_CANCEL).ok) fail("CANCEL could not be tapped", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.GTT_CANCEL_YES) && engine.isPresent(T.GTT_CANCEL_NO) }) fail("CANCEL did not ask for confirmation")
        if (!pad.hasLabel("cancel this GTT order")) note("the confirmation text was not readable")
        if (declineFirst) {
            if (!engine.tap(T.GTT_CANCEL_NO).ok) fail("No could not be tapped", Cause.AUTOMATION)
            if (!waitUntil(UI + 2_000) { !engine.isPresent(T.GTT_CANCEL_YES) }) fail("No did not close the confirmation")
            if (gttRowIndex(made.script, made.price, made.trigger) < 0 && openGttTab() >= 0 && gttRowIndex(made.script, made.price, made.trigger) < 0) {
                fail("After No the GTT order is no longer in the list: it must be kept")
            }
            note("No kept the order in the list")
            gttOpenSheet(made)
            if (!engine.tap(T.GTT_SHEET_CANCEL).ok) fail("CANCEL could not be tapped the second time", Cause.AUTOMATION)
            if (!waitUntil(LOAD) { engine.isPresent(T.GTT_CANCEL_YES) }) fail("CANCEL did not ask for confirmation the second time")
        }
        if (!engine.tap(T.GTT_CANCEL_YES).ok) fail("Yes could not be tapped", Cause.AUTOMATION)
        waitUntil(LOAD) { !engine.isPresent(T.GTT_CANCEL_YES) }
        openGttTab()
        if (!waitUntil(LOAD) { gttRowIndex(made.script, made.price, made.trigger) < 0 }) {
            fail("After Yes the GTT order is still in the list")
        }
        made.cancelled = true
    }

    private fun gttCancel(seg: OrderSeg) {
        val made = gttMade ?: notTested("No GTT order was created, so there is nothing to cancel.")
        gttCancelOnce(made, declineFirst = true)
        val base = gttBase
        val now = tabCount(T.ORDERS_TAB_GTT)
        if (base != null && now != null && now != base) fail("After the cancel the GTT list holds $now orders; it held $base before this run")
        note("cancelled: the GTT list is back to ${now ?: "?"}")
    }

    /** Whatever happened above: a GTT of this run that is still listed is cancelled; what could not be is reported. */
    private fun gttSweep(lastId: String) {
        val base = gttBase ?: return
        var problem: String? = null
        try {
            var now = openGttTab()
            var guard = 0
            val script = gttMade?.script ?: modules.orders.gttScript
            while (now > base && guard++ < 3) {
                val made = gttMade
                val index = gttRowIndex(script, made?.price, made?.trigger).takeIf { it >= 0 } ?: gttRowIndex(script, null, null)
                if (index < 0) break
                val row = GttMade(script, made?.price ?: 0.0, made?.trigger ?: 0.0, made?.condition ?: ">")
                // Match the row that is there, whatever its values.
                gttValues.find(gttRows()[index])?.let {
                    row.price = it.groupValues[1].replace(",", "").toDoubleOrNull() ?: row.price
                    row.trigger = it.groupValues[2].replace(",", "").toDoubleOrNull() ?: row.trigger
                }
                gttCancelOnce(row, declineFirst = false)
                now = tabCount(T.ORDERS_TAB_GTT) ?: now
            }
            if (now > base) problem = "the GTT list holds $now orders, $base before the run"
        } catch (e: CaseEnd) {
            problem = e.message
        }
        val step = flow.step(lastId)
        val text = if (problem == null) "restored: no GTT order of this run is left" else "FAILED: $problem; cancel it by hand"
        step.cleanup = listOfNotNull(step.cleanup, text).joinToString("; ")
        if (problem != null) gttLeft = true
    }

    private var gttLeft = false

    /**
     * Runs the GTT cases and the sweep, all on the Orders screen: no watchlist, no normal order pad, no instrument added
     * to a watchlist. Returns a result only when the run has to end.
     */
    private fun runGtt(seg: OrderSeg): AutomationRunResult? {
        runCase("ow_gtt_pad", recoverAfter = false) { gttPadCase(seg) }?.let { return it }
        runCase("ow_gtt_create", recoverAfter = false) { gttCreate(seg) }?.let { return it }
        runCase("ow_gtt_modify", recoverAfter = false) { gttModify(seg) }?.let { return it }
        runCase("ow_gtt_cancel", recoverAfter = false) { gttCancel(seg) }?.let { return it }
        try {
            gttSweep("ow_gtt_cancel")
        } catch (_: Stopped) {
            return flow.step("ow_gtt_cancel").let(stopped)
        }
        return null
    }

    // ── Basket orders (Orders > Basket) ────────────────────────────────────────────────────────
    //
    // Create a basket, add the instrument, modify the scrip, rename the basket, execute it and delete it. No order pad
    // checks here: the pad is only the way the app adds a scrip to a basket (ADD BUY) and edits one (MODIFY), and neither
    // sends an order. EXECUTE does send the basket's orders to the exchange, so it only happens while the pad shows
    // Avail. Margin 0.00 and a margin shortfall (the exchange refuses them); the orders that stay pending are cancelled.

    private class BasketMade(var name: String, val script: String)

    private var basketMade: BasketMade? = null
    private var basketBase: Int? = null
    private var basketLeft = false

    /** What the margin strip of the basket's modify pad said: whether an EXECUTE may be sent. */
    private var basketGate: OrderPad.Gate? = null

    /** The basket test's own instrument (an NSE stock), apart from the order and GTT instruments. */
    private val basketSegment: OrderSeg by lazy {
        OrderSeg(
            "basket", "NSE", T.SEARCH_TAB_STOCKS, "Stocks", listOf(modules.orders.basketScript), "ow_basket_seg_unused", null, null,
            enabled = true, placeOrders = false,
        ) { l, q -> exchangeOf(l) == "NSE" && scripType(l) == "EQ" && baseName(nameOf(l)).equals(q, ignoreCase = true) }
    }

    private fun basketRows(): List<String> = engine.groupedLabelsOf(T.BASKET_ROW)

    private fun basketRowIndex(name: String): Int = basketRows().indexOfFirst { tokens(it).firstOrNull().equals(name, ignoreCase = true) }

    private fun squash(label: String) = label.replace(Regex("""\s+"""), " ").take(80)

    /** Orders > Basket: the number in the tab's title once the list has drawn. */
    private fun openBasketTab(): Int {
        openOrdersTab(T.ORDERS_TAB_BASKET)
        waitUntil(LOAD) { tabCount(T.ORDERS_TAB_BASKET) != null }
        waitUntil(UI + 2_000) { engine.isPresent(T.BASKET_ROW) || engine.isPresent(T.BASKET_NEW_BUTTON) }
        return tabCount(T.ORDERS_TAB_BASKET) ?: fail("The Basket count could not be read on the Orders tab")
    }

    /** From the list into the basket [made]. */
    private fun openBasket(made: BasketMade) {
        openBasketTab()
        val index = basketRowIndex(made.name)
        if (index < 0) fail("The basket '${made.name}' is not in the list; rows: ${basketRows().take(3).joinToString(" | ") { squash(it) }}")
        val tap = engine.tapAt(T.BASKET_ROW, index)
        if (!tap.ok) fail("The basket row could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_ADD_SCRIPS) }) fail("Tapping the basket did not open its detail screen")
        pause(500)
    }

    /** Back from the detail screen to the Basket list. */
    private fun leaveBasket() {
        if (engine.isPresent(T.BASKET_ADD_SCRIPS) || engine.isPresent(T.BASKET_EXECUTE_SLIDER)) {
            engine.pressBack()
            waitUntil(UI + 2_000) { !engine.isPresent(T.BASKET_ADD_SCRIPS) }
        }
    }

    // BK-01 ---------------------------------------------------------------------------------------

    private fun basketCreate() {
        val base = openBasketTab()
        basketBase = base
        val link = engine.tapAndConfirm(T.BASKET_NEW_BUTTON, listOf(T.BASKET_CREATE_BUTTON), UI + 2_000, retryAfterMs = UI + 2_000)
        alive()
        if (!link.ok) fail("New Basket did not open the create sheet: ${link.detail}")
        // An empty name is refused: the sheet stays open.
        if (!engine.tap(T.BASKET_CREATE_BUTTON).ok) fail("Create could not be tapped", Cause.AUTOMATION)
        pause(1_000)
        if (!engine.isPresent(T.BASKET_CREATE_BUTTON)) fail("Create with an empty name closed the sheet; it must be refused")
        note("an empty name is refused (the sheet stays open)")
        val name = "QA" + java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date())
        val typed = engine.enterText(T.BASKET_NAME_INPUT, name)
        if (!typed.ok) {
            engine.pressBack()
            fail("The basket name could not be typed: ${typed.detail}", Cause.AUTOMATION)
        }
        if (!engine.tap(T.BASKET_CREATE_BUTTON).ok) fail("Create could not be tapped", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { !engine.isPresent(T.BASKET_CREATE_BUTTON) }) fail("Create did not close the sheet (a duplicate name shows only a snackbar)")
        if (!waitUntil(LOAD) { basketRowIndex(name) >= 0 }) fail("The new basket '$name' is not in the list")
        basketMade = BasketMade(name, basketSegment.queries.first())
        note("basket '$name' created; the Basket count went ${base} → ${tabCount(T.ORDERS_TAB_BASKET) ?: "?"}: '${squash(basketRows()[basketRowIndex(name)])}'")
    }

    // BK-02 ---------------------------------------------------------------------------------------

    private fun basketAdd() {
        val made = basketMade ?: notTested("No basket was created, so there is nothing to add a scrip to.")
        val seg = basketSegment
        openBasket(made)
        if (!engine.isPresent(T.BASKET_DELETE_EMPTY_BUTTON)) note("the new basket is not empty (no Delete Basket footer)")
        val search = engine.tapAndConfirm(T.BASKET_ADD_SCRIPS, listOf(T.SEARCH_SCREEN), UI + 2_000, retryAfterMs = UI + 2_000)
        alive()
        if (!search.ok) fail("Add Scrips did not open the instrument search: ${search.detail}")
        val typed = engine.enterText(T.SEARCH_SCRIP_INPUT, made.script)
        if (!typed.ok) fail("Could not type '${made.script}' in the search: ${typed.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { resultRows().any { seg.accept(it, made.script) } }) fail("Searching '${made.script}' showed no ${seg.exchange} result", Cause.API)
        val index = resultRows().indexOfFirst { seg.accept(it, made.script) }
        if (!engine.tapAt(T.SEARCH_RESULT_ROW, index).ok) fail("The search result could not be opened", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_ADD_BUY_BUTTON) }) fail("The result's sheet shows no ADD BUY")
        if (!engine.isPresent(T.BASKET_ADD_SELL_BUTTON)) note("the sheet shows no ADD SELL")
        if (!engine.tap(T.BASKET_ADD_BUY_BUTTON).ok) fail("ADD BUY could not be tapped", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { pad.isOpen() && pad.has(T.ORDER_SUBMIT_BUTTON) }) fail("ADD BUY did not open the pad")
        val hidden = engine.suppressKeyboard(true)
        try {
            pad.waitForFunds(LOAD)
            val gate = pad.gate()
            basketGate = gate
            note("the add pad shows: ${gate.why}")
            // Adding a scrip sends nothing, but the button on this pad is the one that sends orders elsewhere: it is only
            // tapped while the pad itself shows that an order would be refused.
            if (!gate.ok) {
                closePad()
                notTested("The add pad does not show Avail. Margin 0.00 with a shortfall (${gate.why}), so its order button is not tapped.")
            }
            if (!pad.submitIs(true)) {
                closePad()
                fail("The add pad's button is not BUY ORDER")
            }
            shot("basket_add")
            if (!pad.tapOrderButton()) {
                closePad()
                fail("The pad's button could not be tapped", Cause.AUTOMATION)
            }
            if (!waitUntil(LOAD) { !pad.isOpen() }) fail("The scrip was not added: the pad stayed open (${pad.message().orEmpty().ifEmpty { "no message" }})")
        } finally {
            if (hidden) engine.suppressKeyboard(false)
        }
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_SCRIP_ROW) }) fail("The basket detail shows no scrip after ADD BUY; the app is on '${screenName()}'")
        val row = engine.groupedLabelsOf(T.BASKET_SCRIP_ROW).firstOrNull().orEmpty()
        if (!row.uppercase().contains(made.script.uppercase())) fail("The scrip row reads '${squash(row)}', not ${made.script}")
        note("scrip added: '${squash(row)}'; Required Margin ${engine.labelsOf(T.BASKET_MARGIN_REQUIRED).firstOrNull().orEmpty()}")
    }

    // BK-03 ---------------------------------------------------------------------------------------

    private fun basketModify() {
        val made = basketMade ?: notTested("No basket was created, so there is nothing to modify.")
        if (!engine.isPresent(T.BASKET_SCRIP_ROW)) openBasket(made)
        if (!engine.isPresent(T.BASKET_SCRIP_ROW)) notTested("The basket has no scrip to modify.")
        if (!engine.tapAt(T.BASKET_SCRIP_ROW, 0).ok) fail("The scrip row could not be tapped", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_SHEET_MODIFY_BUTTON) }) fail("Tapping the scrip did not open a sheet with MODIFY")
        if (!engine.tap(T.BASKET_SHEET_MODIFY_BUTTON).ok) fail("MODIFY could not be tapped", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { pad.isOpen() && pad.has(T.ORDER_SUBMIT_BUTTON) }) fail("MODIFY did not open the pad")
        val hidden = engine.suppressKeyboard(true)
        try {
            pad.waitForFunds(LOAD)
            val gate = pad.gate()
            basketGate = gate
            note("the modify pad shows: ${gate.why}; quantity ${pad.value(T.ORDER_FIELD_QUANTITY)}")
            if (!gate.ok) {
                closePad()
                notTested("The modify pad does not show Avail. Margin 0.00 with a shortfall (${gate.why}), so its order button is not tapped.")
            }
            val typed = pad.setField(T.ORDER_FIELD_QUANTITY, "2")
            if (!typed.ok || pad.value(T.ORDER_FIELD_QUANTITY) != "2") {
                closePad()
                fail("The new quantity could not be entered: ${typed.detail}", Cause.AUTOMATION)
            }
            shot("basket_modify")
            if (!pad.tapOrderButton()) {
                closePad()
                fail("The pad's button could not be tapped", Cause.AUTOMATION)
            }
            if (!waitUntil(LOAD) { !pad.isOpen() }) fail("The change was not saved: the pad stayed open (${pad.message().orEmpty().ifEmpty { "no message" }})")
        } finally {
            if (hidden) engine.suppressKeyboard(false)
        }
        if (!waitUntil(LOAD) { engine.groupedLabelsOf(T.BASKET_SCRIP_ROW).any { Regex("""\b2\b""").containsMatchIn(it) } }) {
            fail("After MODIFY the scrip row does not show quantity 2: '${squash(engine.groupedLabelsOf(T.BASKET_SCRIP_ROW).firstOrNull().orEmpty())}'")
        }
        note("modified: quantity 1 → 2 on the scrip row; Required Margin ${engine.labelsOf(T.BASKET_MARGIN_REQUIRED).firstOrNull().orEmpty()}")
    }

    // BK-04 ---------------------------------------------------------------------------------------

    /** Edit Basket (a long-press on a scrip): rename the basket and save. */
    private fun basketEdit() {
        val made = basketMade ?: notTested("No basket was created, so there is nothing to edit.")
        if (!engine.isPresent(T.BASKET_SCRIP_ROW)) openBasket(made)
        val press = engine.longTap(T.BASKET_SCRIP_ROW)
        if (!press.ok) fail("The long-press on the scrip row failed: ${press.detail}", Cause.AUTOMATION)
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_EDIT_NAME_INPUT) }) fail("A long-press on the scrip did not open Edit Basket")
        val renamed = (made.name + "X").take(24)
        val typed = engine.enterText(T.BASKET_EDIT_NAME_INPUT, renamed)
        if (!typed.ok) {
            engine.pressBack()
            fail("The new name could not be typed: ${typed.detail}", Cause.AUTOMATION)
        }
        if (!engine.tap(T.BASKET_EDIT_SAVE_BUTTON).ok) fail("Save could not be tapped", Cause.AUTOMATION)
        // Save renames and returns; where it lands is not fixed, so the list is the check.
        pause(Timeouts.SETTLE * 3)
        var guard = 0
        while (guard++ < 3 && (engine.isPresent(T.BASKET_EDIT_NAME_INPUT) || engine.isPresent(T.BASKET_ADD_SCRIPS))) {
            engine.pressBack()
            pause(UI)
        }
        openBasketTab()
        if (!waitUntil(LOAD) { basketRowIndex(renamed) >= 0 }) fail("After Save the list shows no basket named '$renamed'; rows: ${basketRows().take(3).joinToString(" | ") { squash(it) }}")
        note("renamed '${made.name}' → '$renamed' and saved")
        made.name = renamed
    }

    // BK-05 ---------------------------------------------------------------------------------------

    private fun basketExecute() {
        val made = basketMade ?: notTested("No basket was created, so there is nothing to execute.")
        val gate = basketGate
        if (gate == null || !gate.ok) {
            notTested("EXECUTE sends the basket's orders, so it is only slid while a pad showed Avail. Margin 0.00 with a shortfall (${gate?.why ?: "no pad was read"}).")
        }
        val before = readOrderCounts()
        openBasket(made)
        if (!waitUntil(UI + 2_000) { engine.isPresent(T.BASKET_EXECUTE_SLIDER) }) fail("The basket detail shows no EXECUTE slider")
        val slid = engine.slideAcross(T.BASKET_EXECUTE_SLIDER)
        if (!slid.ok) fail("The EXECUTE slider could not be slid: ${slid.detail}", Cause.AUTOMATION)
        // Executing sends the basket and returns to the list; the row's chip turns EXECUTED when the list reloads.
        val left = waitUntil(LOAD) { !engine.isPresent(T.BASKET_EXECUTE_SLIDER) && engine.isPresent(T.BASKET_ROW) }
        if (!left) fail("The slide did not execute the basket: the detail screen stayed open (the knob may not have reached the end)")
        val executed = waitUntil(LOAD) { basketRows().getOrNull(basketRowIndex(made.name))?.contains("EXECUTED", ignoreCase = true) == true }
        note(if (executed) "the basket shows EXECUTED" else "the basket row did not turn EXECUTED within ${LOAD / 1000} s (the orders may have been refused)")
        pause(2_000)
        val after = readOrderCounts()
        if (before.trades != null && after.trades != null && after.trades > before.trades) {
            fail("Trade Book grew from ${before.trades} to ${after.trades}: an order of the basket was EXECUTED", Cause.APP,
                "Check the account at once: the margin gate should have kept the orders from being accepted.")
        }
        note("Order Book ${before.book ?: "?"} → ${after.book ?: "?"}; Pending ${before.pending ?: "?"} → ${after.pending ?: "?"}; Trade Book unchanged")
        // Whatever is pending from the basket is cancelled on the Pending tab.
        cleanPendingAbove(before.pending ?: 0, listOf(made.script))
    }

    // BK-06 ---------------------------------------------------------------------------------------

    private fun basketDelete() {
        val made = basketMade ?: notTested("No basket was created, so there is nothing to delete.")
        openBasket(made)
        if (!engine.isPresent(T.BASKET_SCRIP_ROW)) {
            // An empty basket's footer is Delete Basket itself.
            if (!engine.tap(T.BASKET_DELETE_EMPTY_BUTTON).ok) fail("Delete Basket could not be tapped", Cause.AUTOMATION)
        } else {
            if (!engine.longTap(T.BASKET_SCRIP_ROW).ok) fail("The long-press on the scrip row failed", Cause.AUTOMATION)
            if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_DELETE_BUTTON) }) fail("A long-press on the scrip did not open Edit Basket")
            if (!engine.tap(T.BASKET_DELETE_BUTTON).ok) fail("Delete Basket could not be tapped", Cause.AUTOMATION)
        }
        // There is no confirmation: the app returns to the list.
        if (!waitUntil(LOAD) { engine.isPresent(T.BASKET_NEW_BUTTON) || engine.isPresent(T.BASKET_ROW) }) fail("Delete Basket did not return to the Basket list")
        if (!waitUntil(LOAD) { basketRowIndex(made.name) < 0 }) fail("The basket '${made.name}' is still in the list after Delete Basket")
        val base = basketBase
        val now = tabCount(T.ORDERS_TAB_BASKET)
        if (base != null && now != null && now != base) fail("After the delete the Basket count is $now, it was $base before the run")
        basketMade = null
        note("basket deleted; the Basket count is back to ${now ?: "?"}")
    }

    /** Whatever happened above: a basket of this run that is still listed is deleted; what could not be is reported. */
    private fun basketSweep(lastId: String) {
        val made = basketMade ?: return
        var problem: String? = null
        try {
            basketDelete()
        } catch (e: CaseEnd) {
            problem = e.message
        }
        val step = flow.step(lastId)
        val text = if (problem == null) "restored: the basket '${made.name}' is deleted" else "FAILED: $problem; delete '${made.name}' by hand"
        step.cleanup = listOfNotNull(step.cleanup, text).joinToString("; ")
        if (problem != null) basketLeft = true
    }

    /** Runs the basket cases and the sweep, all on the Orders side. Returns a result only when the run has to end. */
    private fun runBasket(): AutomationRunResult? {
        runCase("ow_basket_create", recoverAfter = false) { basketCreate() }?.let { return it }
        runCase("ow_basket_add", recoverAfter = false) { basketAdd() }?.let { return it }
        runCase("ow_basket_modify", recoverAfter = false) { basketModify() }?.let { return it }
        runCase("ow_basket_edit", recoverAfter = false) { basketEdit() }?.let { return it }
        runCase("ow_basket_execute", recoverAfter = false) { basketExecute() }?.let { return it }
        runCase("ow_basket_delete", recoverAfter = false) { basketDelete() }?.let { return it }
        try {
            basketSweep("ow_basket_delete")
        } catch (_: Stopped) {
            return flow.step("ow_basket_delete").let(stopped)
        }
        return null
    }

    // ── Orders: Pending, Order Book ────────────────────────────────────────────────────────────

    /**
     * Closes what a case can leave open on the Orders side (the GTT pad, the instrument search, the row's sheet, the cancel
     * confirmation) with its own control, without going to the Watchlist: a case that follows on the Orders screen then
     * starts from it instead of from a trip through the Watchlist.
     */
    private fun leaveOrderOverlays() {
        repeat(4) {
            alive()
            when {
                engine.isPresent(T.GTT_CANCEL_NO) -> engine.tap(T.GTT_CANCEL_NO)
                engine.isPresent(T.ORDER_WINDOW_SCREEN) -> flow.closeWithBack(T.ORDER_WINDOW_SCREEN)
                engine.isPresent(T.SEARCH_SCREEN) -> flow.closeWithBack(T.SEARCH_SCREEN)
                engine.isPresent(T.GTT_SHEET_MODIFY) -> engine.pressBack()
                engine.isPresent(T.BASKET_CREATE_BUTTON) || engine.isPresent(T.BASKET_ADD_BUY_BUTTON) ||
                    engine.isPresent(T.BASKET_SHEET_MODIFY_BUTTON) -> engine.pressBack()
                // The basket's own screens replace the Orders screen: Back returns to it.
                engine.isPresent(T.BASKET_ADD_SCRIPS) || engine.isPresent(T.BASKET_EXECUTE_SLIDER) ||
                    engine.isPresent(T.BASKET_DELETE_BUTTON) -> engine.pressBack()
                else -> return
            }
            pause(UI)
        }
    }

    private fun openOrders() {
        leaveOrderOverlays()
        if (!engine.isPresent(T.ORDERBOOK_SCREEN)) {
            val open = engine.tapAndConfirm(T.BOTTOMNAV_ORDER_TAB, listOf(T.ORDERBOOK_SCREEN), LOAD, retryAfterMs = LOAD)
            alive()
            if (!open.ok) fail("The Orders tab did not open: ${open.detail}")
        }
    }

    /** The six Orders tabs, left to right. The strip scrolls sideways: only the ones near the screen are in the tree. */
    private val ordersTabs = listOf(
        T.ORDERS_TAB_PENDING, T.ORDERS_TAB_ORDER_BOOK, T.ORDERS_TAB_TRADE_BOOK,
        T.ORDERS_TAB_BASKET, T.ORDERS_TAB_GTT, T.ORDERS_TAB_SIP,
    )

    /**
     * Brings an Orders tab into the tree. The strip is a scrollable TabBar, and its scroll view is not the scrollable
     * the accessibility tree reports (a scroll action on the nearest scrollable above it swipes the tab BODY instead, to
     * the next tab). What does move the strip is a tab selected: the TabBar scrolls the selected tab into view. So the
     * tab nearest the wanted one is tapped, which builds its neighbours, until the wanted one is there.
     */
    private fun revealOrdersTab(id: String): Boolean {
        val want = ordersTabs.indexOf(id)
        repeat(5) {
            if (engine.isPresent(id)) return true
            val present = ordersTabs.filter { engine.isPresent(it) }
            if (present.isEmpty()) return false
            val edge = if (want > ordersTabs.indexOf(present.last())) present.last() else present.first()
            engine.tap(edge)
            pause(Timeouts.SETTLE * 3)
        }
        return engine.isPresent(id)
    }

    /** The number in a tab's title (`GTT (1)`), bringing the tab into the tree first when it is off screen. */
    private fun tabCount(id: String): Int? {
        if (!engine.isPresent(id)) revealOrdersTab(id)
        return engine.labelsOf(id).firstNotNullOfOrNull { Regex("""\((\d+)\)""").find(it)?.groupValues?.get(1)?.toInt() }
    }

    private fun readOrderCounts(): OrderCounts {
        openOrders()
        waitUntil(LOAD) { tabCount(T.ORDERS_TAB_PENDING) != null }
        return OrderCounts(tabCount(T.ORDERS_TAB_PENDING), tabCount(T.ORDERS_TAB_ORDER_BOOK), tabCount(T.ORDERS_TAB_TRADE_BOOK))
    }

    private fun openOrdersTab(id: String) {
        openOrders()
        // GTT and SIP sit past the right edge of the strip: selecting the tab next to them scrolls the strip.
        if (!revealOrdersTab(id)) fail("The Orders tab '$id' did not appear on the tab strip after selecting its neighbour", Cause.AUTOMATION)
        val tap = engine.tap(id)
        if (!tap.ok) fail("The Orders tab '$id' could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        pause(Timeouts.SETTLE * 2)
    }

    private fun automationRow(names: List<String> = orderNames): String? =
        engine.onScreenLines().firstOrNull { l -> l.length <= 40 && names.any { l.trim().startsWith(it, ignoreCase = true) } }

    /** Cancels the pending order shown on [row]: long-press, Cancel Order, Yes. */
    private fun cancelPending(row: String): String? {
        val press = engine.longTapText(row)
        if (!press.ok) return "the long-press on '$row' failed: ${press.detail}"
        if (!waitUntil(UI) { engine.hasExactText("Cancel Order") }) return "the Cancel Order page did not open for '$row'"
        val cancel = engine.tapLowestExactText("Cancel Order")
        if (!cancel.ok) return "the Cancel Order button could not be tapped (${cancel.detail}); it may offer to cancel more than one order, so nothing was cancelled"
        if (!waitUntil(UI) { engine.hasExactText("Yes") }) return "the 'Are you sure' dialog did not appear"
        if (!engine.tapExactText("Yes").ok) return "the dialog's Yes could not be tapped"
        if (!waitUntil(LOAD) { !engine.hasExactText("Cancel Order") && engine.isPresent(T.ORDERBOOK_SCREEN) }) return "the Cancel Order page did not close after Yes"
        return null
    }

    private fun pendingClean() {
        cleanPendingAbove(orderBase?.pending ?: 0, orderNames)
    }

    /** Cancels the pending orders of [names] on Orders > Pending until its count is back to [base]. */
    private fun cleanPendingAbove(base: Int, names: List<String>) {
        openOrdersTab(T.ORDERS_TAB_PENDING)
        var now = tabCount(T.ORDERS_TAB_PENDING) ?: fail("The Pending count could not be read")
        if (now <= base) {
            note("Pending ($now): none of the orders of this run is pending")
            return
        }
        note("Pending went from $base to $now: cancelling this run's orders")
        var cancelled = 0
        repeat(now - base + 2) {
            if (now <= base) return@repeat
            val row = automationRow(names)
                ?: fail("Pending holds ${now - base} more order(s) than before but none of them is an ${names.joinToString("/")} order, so none was cancelled")
            cancelPending(row)?.let { fail("Cancelling '$row': $it", Cause.AUTOMATION) }
            cancelled++
            openOrdersTab(T.ORDERS_TAB_PENDING)
            waitUntil(LOAD) { (tabCount(T.ORDERS_TAB_PENDING) ?: now) < now }
            now = tabCount(T.ORDERS_TAB_PENDING) ?: now
        }
        if (now > base) fail("After cancelling $cancelled order(s) Pending still shows $now (it was $base before)")
        note("$cancelled pending order(s) cancelled; Pending is back to $now")
    }

    private val bookTypes = setOf("MKT", "LIMIT", "SL", "SL-M")
    private val bookExchanges = setOf("NSE", "BSE", "NFO", "BFO", "MCX", "CDS")
    private val bookTime = Regex("""^\d{2}:\d{2}:\d{2}$""")

    /**
     * The price type of the newest [rows] orders on the Order Book tab, newest first. A card reads `NSE  MKT  CNC` and its
     * time (`11:52:02`, one per order), so each type is paired with the next time. Empty when the cards cannot be read.
     */
    private fun readBookTypes(rows: Int): List<String> {
        engine.toTop()
        val found = LinkedHashMap<String, String>()
        repeat(8) {
            val tokens = engine.onScreenLines().flatMap { l -> l.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() } }
            var i = 0
            while (i < tokens.size - 1) {
                if (tokens[i] in bookExchanges && tokens[i + 1].uppercase() in bookTypes) {
                    val type = tokens[i + 1].uppercase()
                    val time = tokens.drop(i + 2).take(6).firstOrNull { bookTime.matches(it) }
                    if (time != null) found.putIfAbsent(time, type)
                }
                i++
            }
            if (found.size >= rows || !engine.scrollPage(forward = true)) return found.values.toList()
            pause(Timeouts.SETTLE)
        }
        return found.values.toList()
    }

    /**
     * The order type of what was placed is on the Order Book too: a Market order reads MKT and a Limit order LIMIT. The
     * newest rows are matched to the orders sent, newest first (one row per order, as the count shows).
     */
    private fun checkOrderTypes() {
        val wanted = ordersSent.reversed()
        if (wanted.none { it.endsWith("Regular · Market") || it.endsWith("Regular · Limit") }) return
        val types = readBookTypes(wanted.size)
        if (types.size < wanted.size) {
            note("the price type of ${wanted.size - types.size} of the newest rows could not be read, so Market / Limit were matched on ${types.size} of ${wanted.size}")
        }
        val wrong = mutableListOf<String>()
        for ((i, label) in wanted.withIndex()) {
            val type = types.getOrNull(i) ?: break
            val expected = when {
                label.endsWith("Regular · Market") -> "MKT"
                label.endsWith("Regular · Limit") -> "LIMIT"
                else -> continue
            }
            if (type != expected) wrong += "$label shows $type instead of $expected"
        }
        if (wrong.isNotEmpty()) fail("The Order Book shows a different order type than the pad was set to: ${wrong.joinToString("; ")}", Cause.APP)
        note("Order Book price types match: Market orders read MKT, Limit orders read LIMIT")
    }

    private fun orderBookCheck() {
        val sent = ordersSent.size + mayHaveSent
        val counts = readOrderCounts()
        openOrdersTab(T.ORDERS_TAB_ORDER_BOOK)
        shot("order_book")
        val base = orderBase
        if (base?.trades != null && counts.trades != null && counts.trades > base.trades) {
            fail("Trade Book grew from ${base.trades} to ${counts.trades}: an order of this run was EXECUTED", Cause.APP,
                "Check the account at once: the margin gate should have kept every order from being accepted.")
        }
        if (sent == 0) notTested("No order was sent in this run (every one was skipped), so there is nothing to find in the Order Book.")
        val rejected = engine.onScreenLines().count { it.trim().equals("REJECTED", ignoreCase = true) }
        val live = engine.onScreenLines().filter { l -> listOf("OPEN", "PENDING", "TRIGGER PENDING", "COMPLETE", "EXECUTED").any { l.trim().equals(it, ignoreCase = true) } }
        if (live.isNotEmpty()) fail("The Order Book shows ${live.joinToString(", ")} among its first rows: an order of this run is not rejected", Cause.APP)
        checkOrderTypes()
        if (base?.book != null && counts.book != null) {
            val delta = counts.book - base.book
            if (delta < sent) fail("$sent order(s) were sent but the Order Book grew by only $delta (from ${base.book} to ${counts.book})", Cause.APP)
            note("Order Book ${base.book} → ${counts.book} for $sent order(s) sent; $rejected REJECTED row(s) on the first screen; Trade Book unchanged")
        } else {
            note("Order Book (${counts.book ?: "?"}); the count before the run could not be read, so the number of new rows was not compared; $rejected REJECTED row(s) on the first screen")
        }
    }

    private fun orderStateRestored() {
        ensureLanding()
        toWorkTab()
        val problems = mutableListOf<String>()
        val count = readCount()?.first
        val start = orderListStart
        if (orderCleared.isEmpty() && start != null && count != null && count != start) {
            problems += "the watchlist '${orderListName.orEmpty()}' holds $count instrument(s) instead of $start"
        }
        for ((key, s) in orderSessions) if (s.removed == false) problems += "the ${key.uppercase()} instrument was not removed"
        if (gttLeft) problems += "a GTT order of this run could not be cancelled"
        if (basketLeft) problems += "a basket of this run could not be deleted"
        cleanup = if (problems.isEmpty()) {
            if (orderCleared.isEmpty()) "restored" else "the full watchlist was cleared on request (removed: ${orderCleared.joinToString(", ")})"
        } else "FAILED: ${problems.joinToString("; ")}"
        if (problems.isNotEmpty()) fail("The watchlist used for the order checks was not put back: ${problems.joinToString("; ")}", Cause.TEST_DATA, "Remove the leftover instrument by hand.")
        note("'${orderListName.orEmpty()}' holds ${count ?: "?"} instrument(s) again")
    }

    // ── The module ─────────────────────────────────────────────────────────────────────────────

    private fun skipOrderSteps(ids: List<String>, reason: String) {
        for (id in ids) {
            val step = flow.step(id)
            if (step.status == StepStatus.PENDING) flow.skipAs(step, "not_tested", reason)
        }
    }

    /** Runs the Order placement module. Returns a result only when the run has to end. */
    fun runOrders(): AutomationRunResult? {
        if (!modules.isOn("order_placement")) {
            for (id in ORDER_IDS) {
                val step = flow.step(id)
                if (step.status == StepStatus.PENDING) flow.skipAs(step, "not_tested", modules.skipNote("order_placement"))
            }
            return null
        }
        // With no exchange selected (only the GTT test) no order is sent and no watchlist is touched: nothing to compare.
        val exchanges = orderSegments.any { it.enabled }
        if (exchanges) {
            // What the order lists hold before anything is sent, to compare against afterwards.
            try {
                currentCase = null
                orderBase = readOrderCounts()
            } catch (e: CaseEnd) {
                note("setup: could not read the order counts (${e.message})")
            } catch (_: Stopped) {
                return flow.step(ORDER_CASES.first().id).let(stopped)
            }
        }
        for (seg in orderSegments) {
            if (!seg.enabled) {
                skipOrderSteps(listOfNotNull(seg.padId, seg.buyId, seg.sellId), "${seg.exchange} is switched off in the Order placement settings")
                continue
            }
            runCase(seg.padId) { padCase(seg) }?.let { return it }
            if (seg.buyId != null && seg.sellId != null) {
                if (seg.placeOrders) {
                    runCase(seg.buyId) { placeCase(seg, buy = true) }?.let { return it }
                    runCase(seg.sellId) { placeCase(seg, buy = false) }?.let { return it }
                } else {
                    skipOrderSteps(listOf(seg.buyId, seg.sellId), "\"Place orders\" is off for ${seg.exchange} in the Order placement settings")
                }
            }
            finishSegment(seg, seg.sellId ?: seg.padId)?.let { return it }
        }
        // The GTT test: Orders > GTT only, whatever the exchanges above did.
        if (modules.orders.gtt) {
            runGtt(gttSegment)?.let { return it }
        } else {
            skipOrderSteps(GTT_IDS, "The GTT test is off in the Order placement settings")
        }
        // The basket test: Orders > Basket, its own instrument.
        if (modules.orders.basket) runBasket()?.let { return it }
        else skipOrderSteps(BASKET_IDS, "The basket test is off in the Order placement settings")
        if (!exchanges) {
            skipOrderSteps(
                listOf("ow_pending_clean", "ow_orderbook", "ow_state_restored"),
                "No exchange is selected in the Order placement settings, so no order was sent and no watchlist was used",
            )
            return null
        }
        // Both are on the Orders screen: no trip to the Watchlist between them (the last case goes there itself).
        runCase("ow_pending_clean", recoverAfter = false) { pendingClean() }?.let { return it }
        runCase("ow_orderbook", recoverAfter = false) { orderBookCheck() }?.let { return it }
        runCase("ow_state_restored", recoverAfter = orderSessions.isNotEmpty()) { orderStateRestored() }?.let { return it }
        return null
    }
}

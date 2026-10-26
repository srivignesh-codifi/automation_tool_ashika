package com.mobileautomation.tool

/**
 * The parts of an app's run an operator can switch off. Signing in and out are
 * not modules: every other module needs the app signed in, and a run always
 * ends logged out.
 *
 * A module that is not selected has its steps marked skipped, so the report
 * still lists them. [stepIds] is the single list of what each module owns; the
 * flows skip by it and the UI shows [label] and [description].
 */
class AppModule(
    val id: String,
    val label: String,
    val description: String,
    val stepIds: List<String>,
)

object AppModules {

    val TRADION: List<AppModule> = listOf(
        AppModule(
            "login", "Login",
            "Login page checks: Guest User, Unblock Account, Join Now, Forgot Password, Switch Account, " +
                "Confirm OTP button, Enable 2FA. Signing in itself always runs. \"Direct login only\" skips the " +
                "Guest, Unblock, Join Now, Forgot Password and Switch Account detours.",
            listOf(
                "guest_user_checked", "unblock_account_checked", "join_now_checked",
                "forgot_password_checked", "switch_account_checked",
                "otp_confirm_button_checked", "enable_2fa_checked",
            ),
        ),
        AppModule(
            "watchlist", "Watchlist",
            "Landing, switching watchlists, Predefined lists, the indices / funds panel, an instrument's sheet (Chart, " +
                "Alert, Op.Chain, BUY / SELL opening the order pad) and More Details (Market Depth, Technicals, " +
                "Fundamentals, Info); Search and add (INFY, F&O, Commodity, Stocks, the green tick, one of each type per " +
                "segment, the 50-instrument limit); Edit watchlist (rename and restore, reorder and delete a test " +
                "instrument the run added itself); Manage Watchlist (View Depth / Grid / Open Price, sort, the INDEX " +
                "filter, Apply and Clear). Nothing is ordered; whatever it adds or changes is put back, and it ends with " +
                "a check that everything is back.",
            WatchlistSuite.ALL_IDS,
        ),
        AppModule(
            "order_placement", "Order placement",
            "The order pad and the order book, on a LIVE account. Choose below which exchanges to open the pad for and " +
                "which instrument to use (by default NSE with IDEA, and MCX with CRUDEOIL). Each takes an empty user watchlist " +
                "(a full one is cleared: every instrument in it is removed), adds the instrument and checks the pad's fields, " +
                "order types and validation messages. Orders (Regular Market / Limit, CO, BO, AMO, BUY and SELL) are placed only " +
                "where \"Place orders\" is on (NSE and BSE only; NSE by default), and only while the pad itself shows Avail. " +
                "Margin 0.00 and a margin shortfall, so the exchange refuses them; with any other margin they are skipped. " +
                "The GTT sub-module (its own switch and instrument) creates a GTT order from Orders > GTT (a trigger that cannot be reached), modifies it and cancels it, " +
                "all on the Orders screen: no watchlist and no order pad. The basket sub-module (its own switch and instrument) " +
                "creates a basket from Orders > Basket, adds the instrument, modifies the scrip, renames the basket, executes it " +
                "(only while a pad shows Avail. Margin 0.00 and a shortfall), cancels what stays pending and deletes the basket. " +
                "Afterwards Pending is cleaned of this run's orders and the Order Book is checked.",
            WatchlistSuite.ORDER_IDS,
        ),
        AppModule(
            "orders", "Orders",
            "Every Orders tab loads: Pending, Order Book, Trade Book, Basket, GTT, SIP.",
            listOf(
                "orders_pending_loaded", "orders_order_book_loaded", "orders_trade_book_loaded",
                "orders_basket_loaded", "orders_gtt_loaded", "orders_sip_loaded",
            ),
        ),
        AppModule(
            "dashboard", "Dashboard",
            "Trading tab: the ticker and indices panel, Option Chain, Research Calls, the Scalper and Options Trading Tools " +
                "(their landing pages), Positions, Futures, FII / DII and Customize. Investments tab: Indian Indices, Alerts " +
                "(a test alert is created and deleted), IPO, Mutual Funds, Reports, ETF, Pledge, Back Office, Other Request, " +
                "Funds, Add Funds, Withdraw Funds, Holdings, Scanners, Top Sectors and Customize. Opens order pads, payment and " +
                "withdrawal screens and the IPO page but never places an order, pays, withdraws, applies or pledges.",
            DashboardSuite.ALL_IDS,
        ),
        AppModule(
            "portfolio", "Portfolio",
            "Holdings and Positions load, their view switches and sort sheets.",
            listOf(
                "holdings_loaded", "holdings_mode_toggled", "holdings_switch_toggled",
                "holdings_sort_opened", "positions_loaded", "positions_mode_toggled",
                "positions_sort_opened",
            ),
        ),
        AppModule(
            "profile", "Profile",
            "Account and Trade Settings load.",
            listOf("profile_account_loaded", "profile_trade_settings_loaded"),
        ),
    )

    val ASHIKA: List<AppModule> = listOf(
        AppModule(
            "watchlist", "Watchlist and Search",
            "Add scrips, trim a watchlist, search every filter, the count, heat map, filter sheet and scrip " +
                "details. Changes the account's data.",
            listOf(
                "watchlist_tab_opened", "discover_scrip_added", "watchlist_checkboxes_toggled",
                "watchlist_add_saved", "first_watchlist_tab_opened", "watchlist_edit_opened",
                "watchlist_scrips_trimmed", "search_opened", "search_filter_chips_listed",
                "search_tcs_added", "search_nifty_futures_added", "search_nifty_options_added",
                "search_crudeoil_added", "search_all_filter_results", "search_mutual_fund_results",
                "search_closed", "watchlist_count_updated", "watchlist_view_toggled",
                "watchlist_filter_sheet_opened", "scrip_details_opened",
            ),
        ),
        AppModule(
            "home", "Home",
            "Overview, Stocks, F&O, Mutual Funds and Commodity load.",
            listOf(
                "home_overview_loaded", "home_stocks_loaded", "home_fno_loaded",
                "home_mutual_funds_loaded", "home_commodity_loaded",
            ),
        ),
        AppModule("research", "Research", "Research loads.", listOf("research_loaded")),
        AppModule(
            "portfolio", "Portfolio",
            "Holdings (Overview, Equity, Thematic Basket, Mutual Funds) and My Wealth load.",
            listOf(
                "holdings_overview_loaded", "holdings_equity_loaded", "holdings_thematic_loaded",
                "holdings_mutual_funds_loaded", "portfolio_my_wealth_loaded",
            ),
        ),
        AppModule(
            "orders", "Orders",
            "Open, Executed, GTT, SIP, Basket, Alerts and Positions load.",
            listOf(
                "orders_open_loaded", "orders_executed_loaded", "orders_gtt_loaded", "orders_sip_loaded",
                "orders_basket_loaded", "orders_alerts_loaded", "positions_loaded",
            ),
        ),
    )

    fun of(app: TargetApp): List<AppModule> = when (app) {
        TargetApp.ASHIKA -> ASHIKA
        TargetApp.TRADION -> TRADION
    }

    fun toMaps(app: TargetApp): List<Map<String, Any?>> = of(app).map {
        mapOf("id" to it.id, "label" to it.label, "description" to it.description)
    }
}

/** What the Order placement module does on one exchange; see [OrderPlan]. */
class OrderSegmentPlan(
    /** Open the order pad of this exchange's instrument and check it. */
    val enabled: Boolean,
    /** Also place orders (NSE and BSE only; the margin gate still applies). */
    val place: Boolean,
    /** The instrument to search for and open the pad of: IDEA, CRUDEOIL, ... */
    val script: String,
)

/**
 * The Order placement settings of a run: for each exchange whether its pad is checked, whether orders are placed there
 * (only NSE and BSE ever can be), and which instrument is used. The default places on NSE only, with IDEA, and checks
 * the MCX pad with CRUDEOIL.
 */
class OrderPlan(
    private val segments: Map<String, OrderSegmentPlan>,
    /** Also create, modify and cancel a GTT order on the NSE instrument (Orders > GTT). */
    val gtt: Boolean = true,
    /** The NSE stock the GTT test uses, apart from the NSE order instrument. */
    val gttScript: String = "IDEA",
    /** Also test a basket from Orders > Basket. */
    val basket: Boolean = true,
    /** The NSE stock the basket test adds. */
    val basketScript: String = "IDEA",
) {

    fun of(key: String): OrderSegmentPlan = segments[key] ?: DEFAULT.segments.getValue(key)

    companion object {
        val KEYS = listOf("nse", "bse", "nfo", "bfo", "mcx")
        private val PLACEABLE = setOf("nse", "bse")

        val DEFAULT = OrderPlan(
            mapOf(
                "nse" to OrderSegmentPlan(enabled = true, place = true, script = "IDEA"),
                "bse" to OrderSegmentPlan(enabled = false, place = false, script = "IDEA"),
                "nfo" to OrderSegmentPlan(enabled = false, place = false, script = "IDEA"),
                "bfo" to OrderSegmentPlan(enabled = false, place = false, script = "IDEA"),
                "mcx" to OrderSegmentPlan(enabled = true, place = false, script = "CRUDEOIL"),
            ),
        )

        /** Reads what the Flutter side sent; anything missing or odd falls back to the default for that exchange. */
        fun fromMap(raw: Map<*, *>?): OrderPlan {
            if (raw == null) return DEFAULT
            val gtt = (raw["gtt"] as? Boolean) ?: true
            val gttScript = (raw["gttScript"] as? String).orEmpty().uppercase().filter { it.isLetterOrDigit() || it == '&' || it == '-' }.take(24).ifEmpty { "IDEA" }
            val out = LinkedHashMap<String, OrderSegmentPlan>()
            for (key in KEYS) {
                val default = DEFAULT.segments.getValue(key)
                val m = raw[key] as? Map<*, *>
                if (m == null) {
                    out[key] = default
                    continue
                }
                val script = (m["script"] as? String).orEmpty().uppercase().filter { it.isLetterOrDigit() || it == '&' || it == '-' }.take(24)
                val enabled = (m["enabled"] as? Boolean) ?: default.enabled
                out[key] = OrderSegmentPlan(
                    enabled = enabled,
                    // Orders are never placed outside NSE and BSE, whatever was sent.
                    place = enabled && key in PLACEABLE && ((m["place"] as? Boolean) ?: default.place),
                    script = script.ifEmpty { default.script },
                )
            }
            val basket = (raw["basket"] as? Boolean) ?: true
            val basketScript = (raw["basketScript"] as? String).orEmpty().uppercase().filter { it.isLetterOrDigit() || it == '&' || it == '-' }.take(24).ifEmpty { "IDEA" }
            return OrderPlan(out, gtt, gttScript, basket, basketScript)
        }
    }
}

/**
 * Which modules a run covers. [selected] null means all of them, which is what
 * a run started without a choice does.
 */
class ModuleSelection(
    private val all: List<AppModule>,
    private val selected: Set<String>?,
    /** Login option: skip the detour checks (Guest User, Unblock Account, Join Now, Forgot Password, Switch Account). */
    val directLogin: Boolean = false,
    /** Order placement module settings: which exchanges, which instruments, where orders are placed. */
    val orders: OrderPlan = OrderPlan.DEFAULT,
) {

    fun isOn(id: String): Boolean = selected == null || id in selected

    fun label(id: String): String = all.firstOrNull { it.id == id }?.label ?: id

    fun stepIds(id: String): List<String> = all.firstOrNull { it.id == id }?.stepIds.orEmpty()

    val onLabels: List<String> get() = all.filter { isOn(it.id) }.map { it.label }

    val offLabels: List<String> get() = all.filterNot { isOn(it.id) }.map { it.label }

    val skipNote: (String) -> String = { id -> "module not selected: ${label(id)}" }
}

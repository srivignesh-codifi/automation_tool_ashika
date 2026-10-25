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
            "Landing, switching watchlists, Predefined lists, the indices / funds panel, an instrument's sheet " +
                "(Chart, Alert, Op.Chain, BUY / SELL opening the order pad) and More Details (Market Depth, " +
                "Technicals, Fundamentals, Info). One instrument per check; nothing is ordered or created. Ends with a check " +
                "that everything the Watchlist cases changed is back.",
            listOf(
                "wl_landing_loaded", "wl_switch_watchlist", "wl_predefined_lists", "wl_index_panel",
                "wl_instrument_sheet", "wl_chart", "wl_alert_page", "wl_alert_condition", "wl_option_chain",
                "wl_buy_sell", "wl_more_market_depth", "wl_more_technicals", "wl_more_fundamentals",
                "wl_more_info", "wl_predefined_instrument", "wl_predefined_search", "wl_state_restored",
            ),
        ),
        AppModule(
            "watchlist_search", "Search and add",
            "Search INFY (All), F&O, Commodity and Stocks, then remove and re-add an instrument with the green " +
                "tick, add one of each type per segment (an -EQ stock, F&O futures / call / put, a commodity future) and check the " +
                "50-instrument limit. Everything it adds is removed again; leaves the watchlist as found.",
            listOf(
                "wl_search_results", "wl_search_fno", "wl_search_commodity", "wl_search_stocks",
                "wl_search_add_remove", "wl_search_segment_types", "wl_capacity_limit",
            ),
        ),
        AppModule(
            "watchlist_edit", "Edit watchlist",
            "Rename and restore a watchlist, then reorder and delete a test instrument the run added itself. " +
                "Never deletes the account's own instruments.",
            listOf("wl_edit_rename", "wl_edit_reorder", "wl_edit_delete"),
        ),
        AppModule(
            "watchlist_settings", "Manage Watchlist",
            "View (Depth / Grid / Open Price), sort, the INDEX filter, Apply and Clear. The original " +
                "preferences are restored.",
            listOf("wl_settings_view", "wl_settings_filter_sort"),
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

/**
 * Which modules a run covers. [selected] null means all of them, which is what
 * a run started without a choice does.
 */
class ModuleSelection(
    private val all: List<AppModule>,
    private val selected: Set<String>?,
    /** Login option: skip the detour checks (Guest User, Unblock Account, Join Now, Forgot Password, Switch Account). */
    val directLogin: Boolean = false,
) {

    fun isOn(id: String): Boolean = selected == null || id in selected

    fun label(id: String): String = all.firstOrNull { it.id == id }?.label ?: id

    fun stepIds(id: String): List<String> = all.firstOrNull { it.id == id }?.stepIds.orEmpty()

    val onLabels: List<String> get() = all.filter { isOn(it.id) }.map { it.label }

    val offLabels: List<String> get() = all.filterNot { isOn(it.id) }.map { it.label }

    val skipNote: (String) -> String = { id -> "module not selected: ${label(id)}" }
}

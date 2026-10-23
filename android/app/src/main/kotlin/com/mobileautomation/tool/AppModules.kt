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
                "Confirm OTP button, Enable 2FA. Signing in itself always runs.",
            listOf(
                "guest_user_checked", "unblock_account_checked", "join_now_checked",
                "forgot_password_checked", "switch_account_checked",
                "otp_confirm_button_checked", "enable_2fa_checked",
            ),
        ),
        AppModule(
            "watchlist", "Watchlist",
            "Predefined list, basket mode, filter, scrip details, chart and More Details.",
            listOf(
                "watchlist_predefined_no_add", "watchlist_basket_toggled", "watchlist_filter_opened",
                "scrip_details_opened", "scrip_chart_loaded", "scrip_more_details_tabs",
            ),
        ),
        AppModule(
            "watchlist_edit", "Edit watchlist",
            "Rename, rearrange, delete a scrip, save, verify and rename back. Changes the account's data.",
            listOf(
                "watchlist_edit_opened", "watchlist_edit_rename", "watchlist_edit_reorder",
                "watchlist_edit_delete", "watchlist_edit_saved", "watchlist_edit_verified",
                "watchlist_edit_restored",
            ),
        ),
        AppModule(
            "watchlist_search", "Search and add",
            "Search every segment, add then delete a scrip, check the count. Changes the account's data.",
            listOf(
                "search_opened", "search_tabs_listed", "search_stocks_added", "search_stocks_deleted",
                "search_fno_added", "search_fno_deleted", "search_commodity_added",
                "search_commodity_deleted", "search_all_added", "search_details_opened",
                "search_closed", "watchlist_count_updated",
            ),
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
            "Trading (option chain per segment, research calls) and Investments (scanners).",
            listOf(
                "dashboard_trading_loaded", "option_chain_opened", "option_chain_nfo_loaded",
                "option_chain_mcx_loaded", "option_chain_cds_loaded", "option_chain_bfo_loaded",
                "research_calls_loaded", "dashboard_investments_loaded", "scanners_view_all_loaded",
            ),
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
class ModuleSelection(private val all: List<AppModule>, private val selected: Set<String>?) {

    fun isOn(id: String): Boolean = selected == null || id in selected

    fun label(id: String): String = all.firstOrNull { it.id == id }?.label ?: id

    fun stepIds(id: String): List<String> = all.firstOrNull { it.id == id }?.stepIds.orEmpty()

    val onLabels: List<String> get() = all.filter { isOn(it.id) }.map { it.label }

    val offLabels: List<String> get() = all.filterNot { isOn(it.id) }.map { it.label }

    val skipNote: (String) -> String = { id -> "module not selected: ${label(id)}" }
}

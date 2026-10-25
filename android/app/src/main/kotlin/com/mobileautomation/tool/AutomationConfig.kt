package com.mobileautomation.tool

/**
 * Static configuration for the UAT automation flows (Ashika's Phase 1 login
 * pass, and Tradion's regression pass).
 *
 * Everything security relevant lives here so it can be reviewed in one place.
 */
object AutomationConfig {

    /**
     * The ONLY packages this tool is permitted to drive — exactly the entries
     * of [TargetApp].
     *
     * `com.codifi.dhanush` (Ashika) and `com.codifi.rmoney` (Tradion) are the
     * applicationIds declared by the two target Flutter projects. Each builds
     * its UAT and its CUG/live variants from the same applicationId — they
     * differ only in the API endpoints baked into the build — so the allowlist
     * cannot tell them apart by package name. The operator is responsible for
     * installing the UAT build; see the "Safety restrictions" section of the
     * README.
     *
     * The AccessibilityService is additionally scoped to these same packages
     * via android:packageNames in res/xml/accessibility_service_config.xml, so
     * the service is technically incapable of reading any other application.
     */
    val ALLOWED_PACKAGES: Set<String> = TargetApp.values().map { it.packageName }.toSet()

    val DEFAULT_TARGET_PACKAGE: String = TargetApp.ASHIKA.packageName

    /**
     * Put on every launch of a target app. The apps read it (and the launching
     * package) to know the tool is driving them, and skip their own
     * notification permission request: the data reset at the start of each run
     * revokes that permission, so they would otherwise ask again every run.
     */
    const val AUTOMATION_RUN_EXTRA = "com.mobileautomation.tool.AUTOMATION_RUN"

    /**
     * System packages the service may also read, solely to reset the target
     * app before a run: App info / Storage live in Settings (MIUI moves App
     * info into Security), and the runtime permission prompt belongs to the
     * permission controller. Flow lookups never read these — see
     * [AutomationEngine.targetRoot] — and they must stay in step with
     * android:packageNames in accessibility_service_config.xml.
     */
    val SETTINGS_PACKAGES: Set<String> = setOf("com.android.settings", "com.miui.securitycenter")
    val PERMISSION_CONTROLLER_PACKAGES: Set<String> =
        setOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")

    /** Accessibility identifiers published by the target app's login module. */
    object Ids {
        /**
         * The pre-login carousel's (splash screen with "Invest rightly, Switch
         * timely") Login button, published via `Semantics(identifier:
         * "intro_login_button")`. [Texts.LOGIN] remains as a fallback for
         * builds predating this identifier.
         */
        const val INTRO_LOGIN_BUTTON = "intro_login_button"

        const val CLIENT_ID_SCREEN = "client_id_screen"
        const val CLIENT_ID_INPUT = "client_id_input"
        const val CLIENT_ID_CONTINUE_BUTTON = "client_id_continue_button"

        const val OTP_SCREEN = "otp_screen"
        const val OTP_INPUT = "otp_input"
        const val OTP_SUBMIT_BUTTON = "otp_submit_button"

        const val MPIN_SCREEN = "mpin_screen"
        const val MPIN_INPUT = "mpin_input"
        const val MPIN_SUBMIT_BUTTON = "mpin_submit_button"
        const val MPIN_KEY_PREFIX = "mpin_key_"
        const val MPIN_KEY_DELETE = "mpin_key_delete"

        const val BIOMETRIC_SCREEN = "biometric_screen"
        const val BIOMETRIC_SKIP_BUTTON = "biometric_skip_button"

        const val HOME_SCREEN = "home_screen"
        const val WATCHLIST_SCREEN = "watchlist_screen"

        /**
         * Not part of the login flow proper. The target app can raise a risk
         * disclosure dialog on top of Home immediately after login; its modal
         * barrier puts the routes below it inside BlockSemantics, which hides
         * `home_screen` from the accessibility tree. Seeing this dialog is
         * still proof that login succeeded.
         */
        const val RISK_DISCLOSURE_DIALOG = "risk_disclosure_dialog"

        /**
         * The disclosure dialog's confirm button ("Next" on earlier pages,
         * "I Understand" on the last). Same identifier on every page, so
         * [AutomationEngine.tapUpTo] can advance through however many pages
         * exist by tapping until the dialog closes.
         */
        const val DISCLOSURE_UNDERSTAND_BUTTON = "disclosure_understand_button"

        const val BOTTOMNAV_WATCHLIST_TAB = "bottomnav_watchlist_tab"

        /** The tab immediately after "Discover" — the user's first watchlist. */
        const val WATCHLIST_FIRST_TAB = "watchlist_first_tab"

        /** Discover row's "+" icon. Same identifier every row; first-match always lands on row 1. */
        const val DISCOVER_SCRIP_ADD_BUTTON = "discover_scrip_add_button"

        /** Positional: row N's checkbox in the "Add scrip to" dialog is "watchlist_checkbox_<N>". */
        const val WATCHLIST_CHECKBOX_PREFIX = "watchlist_checkbox_"

        const val WATCHLIST_ADD_SAVE_BUTTON = "watchlist_add_save_button"
        const val WATCHLIST_EDIT_BUTTON = "watchlist_edit_button"

        /** "N/50 Scrips" label on a user watchlist tab; read to verify adds and deletes. */
        const val WATCHLIST_SCRIP_COUNT = "watchlist_scrip_count"

        /** Heat map (grid) / list toggle, and the layout each one shows. */
        const val WATCHLIST_VIEW_TOGGLE_BUTTON = "watchlist_view_toggle_button"
        const val WATCHLIST_LIST_VIEW = "watchlist_list_view"
        const val WATCHLIST_GRID_VIEW = "watchlist_grid_view"
        const val WATCHLIST_EMPTY_STATE = "watchlist_empty_state"

        /** A scrip row on a user watchlist tab. Tapping one opens its details. */
        const val WATCHLIST_SCRIP_ROW = "watchlist_scrip_row"

        const val WATCHLIST_FILTER_BUTTON = "watchlist_filter_button"
        const val WATCHLIST_FILTER_SHEET = "watchlist_filter_sheet"

        /** Screen markers published from the target app's router. */
        const val EDIT_WATCHLIST_SCREEN = "edit_watchlist_screen"
        const val SEARCH_SCREEN = "search_screen"
        const val SCRIP_DETAILS_SCREEN = "scrip_details_screen"
        const val INDEX_DETAILS_SCREEN = "index_details_screen"

        /** Edit Watchlist per-row delete icon. Same identifier every row; the row disappears after a delete. */
        const val EDIT_WATCHLIST_DELETE_BUTTON = "edit_watchlist_delete_button"
        const val EDIT_WATCHLIST_SAVE_BUTTON = "edit_watchlist_save_button"

        const val WATCHLIST_SEARCH_BUTTON = "watchlist_search_button"
        const val SEARCH_SCRIP_INPUT = "search_scrip_input"
        const val SEARCH_BACK_BUTTON = "search_back_button"

        /** Filter chips: `search_filter_chip_<label>`, lower case, spaces as underscores. */
        const val SEARCH_FILTER_CHIP_ALL = "search_filter_chip_all"
        const val SEARCH_FILTER_CHIP_STOCK = "search_filter_chip_stock"
        const val SEARCH_FILTER_CHIP_FUTURES = "search_filter_chip_futures"
        const val SEARCH_FILTER_CHIP_OPTIONS = "search_filter_chip_options"
        const val SEARCH_FILTER_CHIP_COMMODITY = "search_filter_chip_commodity"
        const val SEARCH_FILTER_CHIP_MUTUAL_FUNDS = "search_filter_chip_mutual_funds"

        /** Every chip the search screen offers. Mutual Funds moves (after Options in market hours, else after Stock). */
        val SEARCH_FILTER_CHIPS = listOf(
            SEARCH_FILTER_CHIP_ALL,
            SEARCH_FILTER_CHIP_STOCK,
            SEARCH_FILTER_CHIP_FUTURES,
            SEARCH_FILTER_CHIP_OPTIONS,
            SEARCH_FILTER_CHIP_COMMODITY,
            SEARCH_FILTER_CHIP_MUTUAL_FUNDS,
        )

        /** Shown while a scrip query is in flight. */
        const val SEARCH_LOADING = "search_loading"

        /** Present only while results for a typed query are listed (never for the empty-query Trending rows). */
        const val SEARCH_RESULTS_LIST = "search_results_list"

        /** Search result row's "+" icon. Tagged only while not yet added, so repeated taps skip already-added rows. */
        const val SEARCH_SCRIP_ADD_BUTTON = "search_scrip_add_button"

        /** The tick that replaces "+" once a result is in the watchlist. */
        const val SEARCH_SCRIP_ADDED_ICON = "search_scrip_added_icon"

        /** Fund results under the Mutual Funds chip. */
        const val MF_SEARCH_RESULTS = "mf_search_results"

        // ── Bottom tabs, their screens and inner tabs ────────────────────────

        /** `bottomnav_<label>_tab`; the third tab is `sip` instead of `research` in MF mode. */
        const val BOTTOMNAV_HOME_TAB = "bottomnav_home_tab"
        const val BOTTOMNAV_RESEARCH_TAB = "bottomnav_research_tab"
        const val BOTTOMNAV_SIP_TAB = "bottomnav_sip_tab"
        const val BOTTOMNAV_PORTFOLIO_TAB = "bottomnav_portfolio_tab"
        const val BOTTOMNAV_ORDER_TAB = "bottomnav_order_tab"

        const val DASHBOARD_SCREEN = "dashboard_screen"
        const val RESEARCH_SCREEN = "research_screen"
        const val PORTFOLIO_SCREEN = "portfolio_screen"
        const val ORDERBOOK_SCREEN = "orderbook_screen"

        /** Inner tab prefixes; the suffix is the tab label in lower case with `_` for anything else. */
        const val HOME_TAB_PREFIX = "home_tab_"
        const val PORTFOLIO_TAB_PREFIX = "portfolio_tab_"
        const val HOLDINGS_TAB_PREFIX = "holdings_tab_"
        const val ORDERBOOK_TAB_PREFIX = "orderbook_tab_"
        const val ORDERS_TAB_PREFIX = "orders_tab_"

        /**
         * Load-state markers carried by the target app's shared shimmer,
         * spinner, empty and retry widgets. Any `ui_loading` on screen means a
         * section is still loading.
         */
        const val UI_LOADING = "ui_loading"
        const val UI_EMPTY = "ui_empty"
        const val UI_ERROR = "ui_error"

        const val PROFILE_BUTTON = "profile_button"
        const val PROFILE_SCREEN = "profile_screen"
        const val PROFILE_LOGOUT_BUTTON = "profile_logout_button"
        const val LOGOUT_CONFIRM_BUTTON = "logout_confirm_button"
    }


    /**
     * Identifiers published by the Tradion app (`com.codifi.rmoney`). Tradion's
     * screens are tagged in `lib/shared_widgets/automation_markers.dart` and
     * documented in `docs/AUTOMATION_SEMANTICS.md` of that project.
     *
     * Tab suffixes are keyed by position or role, never by label: the labels
     * carry live counts ("Pending (3)") and are localised.
     */
    object TradionIds {
        // Login
        const val USER_ID_SCREEN = "user_id_screen"
        const val USER_ID_INPUT = "user_id_input"
        const val USER_ID_LOGIN_BUTTON = "user_id_login_button"
        const val PASSWORD_SCREEN = "password_screen"
        const val PASSWORD_INPUT = "password_input"
        const val PASSWORD_SUBMIT_BUTTON = "password_submit_button"
        const val OTP_SCREEN = "otp_screen"
        const val OTP_INPUT = "otp_input"
        const val OTP_SUBMIT_BUTTON = "otp_submit_button"

        // Login side paths: links, the forms behind them, and their fields.
        const val USER_ID_GUEST_LINK = "user_id_guest_link"
        const val USER_ID_UNBLOCK_LINK = "user_id_unblock_link"
        const val USER_ID_JOIN_NOW_BUTTON = "user_id_join_now_button"
        const val GUEST_LOGIN_SCREEN = "guest_login_screen"
        const val GUEST_MOBILE_INPUT = "guest_mobile_input"
        const val UNBLOCK_SCREEN = "unblock_user_screen"
        const val UNBLOCK_PAN_INPUT = "unblock_pan_input"
        const val PASSWORD_FORGOT_LINK = "password_forgot_link"
        const val PASSWORD_SWITCH_ACCOUNT_LINK = "password_switch_account_link"
        const val FORGOT_PASSWORD_SCREEN = "forgot_password_screen"
        const val FORGOT_PAN_INPUT = "forgot_pan_input"

        /** The authenticator (TOTP) screen, shown instead of OTP for accounts that use one. */
        const val TOTP_SCREEN = "totp_screen"
        const val TOTP_LOGIN_WITH_OTP_LINK = "totp_login_with_otp_link"

        /** The biometric screen's "Enable 2FA security" / "Enable now" button. */
        const val BIOMETRIC_ENABLE_BUTTON = "biometric_enable_button"

        /** Offered only on a device with biometrics; has no Skip, so a run cannot pass it. */
        const val BIOMETRIC_SCREEN = "biometric_screen"

        /** "Risk Disclosure on Derivatives" and its "I understand" / "Next" button. */
        const val RISK_DISCLOSURE_DIALOG = "risk_disclosure_dialog"
        const val DISCLOSURE_UNDERSTAND_BUTTON = "disclosure_understand_button"

        // Shell
        const val HOME_SCREEN = "home_screen"
        const val BOTTOMNAV_WATCHLIST_TAB = "bottomnav_watchlist_tab"
        const val BOTTOMNAV_ORDER_TAB = "bottomnav_order_tab"
        const val BOTTOMNAV_DASHBOARD_TAB = "bottomnav_home_tab"
        const val BOTTOMNAV_PORTFOLIO_TAB = "bottomnav_portfolio_tab"
        const val BOTTOMNAV_PROFILE_TAB = "bottomnav_profile_tab"
        const val WATCHLIST_SCREEN = "watchlist_screen"
        const val ORDERBOOK_SCREEN = "orderbook_screen"
        const val DASHBOARD_SCREEN = "dashboard_screen"
        const val PORTFOLIO_SCREEN = "portfolio_screen"
        const val PROFILE_SCREEN = "profile_screen"

        // Watchlist
        const val WATCHLIST_TAB_PREFIX = "watchlist_tab_"
        const val PREDEFINED_TAB_PREFIX = "predefined_tab_"
        const val WATCHLIST_MODE_WATCHLIST = "watchlist_mode_watchlist"
        const val WATCHLIST_MODE_PREDEFINED = "watchlist_mode_predefined"
        const val WATCHLIST_SEARCH_BUTTON = "watchlist_search_button"
        const val WATCHLIST_SCRIP_COUNT = "watchlist_scrip_count"
        const val WATCHLIST_BASKET_BUTTON = "watchlist_basket_button"
        const val WATCHLIST_FILTER_BUTTON = "watchlist_filter_button"
        const val WATCHLIST_FILTER_SCREEN = "watchlist_filter_screen"
        const val WATCHLIST_FILTER_RESET_BUTTON = "watchlist_filter_reset_button"
        const val WATCHLIST_FILTER_APPLY_BUTTON = "watchlist_filter_apply_button"

        /** The Watchlist header: the two pinned indices, and the arrow that opens the index / funds panel. */
        const val WATCHLIST_TICKER = "watchlist_ticker"
        const val WATCHLIST_INDEX_DROPDOWN = "watchlist_index_dropdown"

        /** The New Alert page's condition dropdown, and its options by position (`alert_condition_item_<i>`). */
        const val ALERT_CONDITION = "alert_condition"

        /** A Manage Watchlist filter chip by lower-case exchange (`watchlist_filter_chip_nse`); publishes `selected`. */
        const val WATCHLIST_FILTER_CHIP_PREFIX = "watchlist_filter_chip_"
        const val ALERT_CONDITION_ITEM_PREFIX = "alert_condition_item_"
        const val WATCHLIST_RADIO_PREFIX = "watchlist_radio_"

        /** Radio groups of the Manage Watchlist screen: card type, tile, change basis, add position (Watchlist mode only). */
        val WATCHLIST_RADIO_GROUPS = listOf(
            listOf("basic", "depth", "all"),
            listOf("list", "grid"),
            listOf("open_price", "close_price"),
            listOf("top", "bottom"),
        )
        val WATCHLIST_SORT_ROWS = listOf("watchlist_sort_alphabetical", "watchlist_sort_ltp", "watchlist_sort_change")
        const val SCRIP_SHEET_ALERT_BUTTON = "scrip_sheet_alert_button"
        const val ALERT_SCREEN = "alert_screen"
        const val ALERT_NAME_INPUT = "alert_name_input"
        const val ALERT_VALUE_INPUT = "alert_value_input"
        const val ALERT_CREATE_BUTTON = "alert_create_button"
        const val WATCHLIST_LIST_VIEW = "watchlist_list_view"
        const val WATCHLIST_GRID_VIEW = "watchlist_grid_view"
        const val WATCHLIST_EMPTY_STATE = "watchlist_empty_state"
        const val WATCHLIST_SCRIP_ROW = "watchlist_scrip_row"
        const val SCRIP_DETAILS_SHEET = "scrip_details_sheet"
        const val INDEX_DETAILS_SHEET = "index_details_sheet"
        const val SCRIP_SHEET_CHART_BUTTON = "scrip_sheet_chart_button"
        const val SCRIP_SHEET_OPCHAIN_BUTTON = "scrip_sheet_opchain_button"
        const val SCRIP_SHEET_MORE_BUTTON = "scrip_sheet_more_button"
        const val CHART_SCREEN = "chart_screen"
        const val CHART_READY = "chart_ready"
        const val MORE_INFO_SCREEN = "more_info_screen"
        const val MORE_INFO_TAB_PREFIX = "more_info_tab_"
        const val REORDER_SCREEN = "watchlist_reorder_screen"
        const val REORDER_DELETE_BUTTON = "reorder_delete_button"
        const val REORDER_SAVE_BUTTON = "reorder_save_button"
        const val REORDER_ROW = "reorder_row"
        const val REORDER_RENAME_INPUT = "reorder_rename_input"
        const val REORDER_DISCARD_BUTTON = "reorder_discard_button"

        /** The Save button of the "Do you want to save changes?" dialog (the app bar's Save is `reorder_save_button`). */
        const val REORDER_CONFIRM_SAVE_BUTTON = "reorder_confirm_save_button"

        // Search
        const val SEARCH_SCREEN = "search_screen"
        const val SEARCH_SCRIP_INPUT = "search_scrip_input"
        const val SEARCH_BACK_BUTTON = "search_back_button"
        const val SEARCH_TAB_ALL = "search_tab_all"
        const val SEARCH_TAB_FNO = "search_tab_f_o"
        const val SEARCH_TAB_COMMODITY = "search_tab_commodity"
        const val SEARCH_TAB_STOCKS = "search_tab_stocks"
        const val SEARCH_LOADING = "search_loading"
        const val SEARCH_RESULTS_LIST = "search_results_list"
        const val SEARCH_NO_RESULT = "search_no_result"
        const val SEARCH_SCRIP_ADD_BUTTON = "search_scrip_add_button"
        const val SEARCH_SCRIP_ADDED_ICON = "search_scrip_added_icon"
        const val SEARCH_RESULT_ROW = "search_result_row"
        const val SEARCH_RESULT_SHEET = "search_result_sheet"

        val SEARCH_TABS = listOf(SEARCH_TAB_ALL, SEARCH_TAB_FNO, SEARCH_TAB_COMMODITY, SEARCH_TAB_STOCKS)

        // Orders
        const val ORDERS_TAB_PENDING = "orders_tab_pending"
        const val ORDERS_TAB_ORDER_BOOK = "orders_tab_order_book"
        const val ORDERS_TAB_TRADE_BOOK = "orders_tab_trade_book"
        const val ORDERS_TAB_BASKET = "orders_tab_basket"
        const val ORDERS_TAB_GTT = "orders_tab_gtt"
        const val ORDERS_TAB_SIP = "orders_tab_sip"

        // Dashboard
        const val DASHBOARD_TAB_TRADING = "dashboard_tab_trading"
        const val DASHBOARD_TAB_INVESTMENTS = "dashboard_tab_investments"
        const val DASHBOARD_SCANNER_TAB_PREFIX = "dashboard_scanner_tab_"
        const val DASHBOARD_OPEN_OPTION_CHAIN = "dashboard_open_option_chain"
        const val DASHBOARD_OPEN_RESEARCH_CALLS = "dashboard_open_research_calls"
        const val DASHBOARD_SCANNERS_VIEW_ALL = "dashboard_scanners_view_all"
        const val OPTION_CHAIN_SCREEN = "option_chain_screen"
        const val OPTION_CHAIN_SEARCH_BUTTON = "option_chain_search_button"
        const val OPTION_CHAIN_TABLE = "option_chain_table"
        const val OPTION_CHAIN_SPOT_SEARCH_SCREEN = "option_chain_spot_search_screen"
        const val OPTION_CHAIN_SEGMENT_PREFIX = "option_chain_segment_"
        const val OPTION_CHAIN_SPOT_ROW = "option_chain_spot_row"
        const val RESEARCH_CALL_SCREEN = "research_call_screen"
        const val RESEARCH_TAB_PREFIX = "research_tab_"
        const val RESEARCH_SUBTAB_PREFIX = "research_subtab_"
        const val SCANNERS_SCREEN = "scanners_screen"
        const val SCANNERS_TAB_PREFIX = "scanners_tab_"

        // Dashboard scenarios: index panel, Trading Ideas, Scanners, Futures, FII / DII, Customize
        const val DASHBOARD_INDEX_DROPDOWN = "dashboard_index_dropdown"
        const val DASHBOARD_INDEX_ITEM = "dashboard_index_item"
        const val DASHBOARD_INDEX_CLOSE = "dashboard_index_close"
        const val DASHBOARD_TRADING_VIEW = "dashboard_trading_view"
        const val DASHBOARD_INVESTMENTS_VIEW = "dashboard_investments_view"
        const val DASHBOARD_FUTURES_VIEW_ALL = "dashboard_futures_view_all"
        const val DASHBOARD_FII_DII_VIEW_ALL = "dashboard_fii_dii_view_all"
        const val DASHBOARD_CUSTOMIZE_BUTTON = "dashboard_customize_button"
        const val DASHBOARD_CUSTOMIZE_SCREEN = "dashboard_customize_screen"
        const val FII_DII_SCREEN = "fii_dii_screen"
        const val FII_DII_ROW = "fii_dii_row"

        /** Trading Ideas (the Research calls screen): search, calls, and the Filter By sheet. */
        const val RESEARCH_SEARCH_INPUT = "research_search_input"
        const val RESEARCH_CARD = "research_card"
        const val RESEARCH_CARD_NAME = "research_card_name"
        const val RESEARCH_FILTER_BUTTON = "research_filter_button"
        const val RESEARCH_FILTER_SHEET = "research_filter_sheet"
        const val RESEARCH_FILTER_CLEAR = "research_filter_clear_button"
        const val RESEARCH_FILTER_STATUS = "research_filter_status"
        const val RESEARCH_FILTER_EXCHANGE = "research_filter_exchange"
        const val RESEARCH_FILTER_RETURNS = "research_filter_returns"

        /** Scanners (the page the test scenarios call Screener). */
        const val SCANNERS_SEGMENT_PREFIX = "scanners_segment_"
        const val SCANNERS_SEARCH_INPUT = "scanners_search_input"
        const val SCANNERS_FILTER_BUTTON = "scanners_filter_button"
        const val SCANNERS_FILTER_SHEET = "scanners_filter_sheet"
        const val SCANNERS_FILTER_CLOSE = "scanners_filter_close_button"
        const val SCANNER_ROW = "scanner_row"
        const val SCANNER_ROW_NAME = "scanner_row_name"

        /** Futures › View All. */
        const val FUTURES_SCREEN = "futures_screen"
        const val FUTURES_TAB_PREFIX = "futures_tab_"
        const val FUTURES_ROW = "futures_row"
        const val FUTURES_FILTER_BUTTON = "futures_filter_button"
        const val FUTURES_FILTER_SHEET = "futures_filter_sheet"
        const val FUTURES_FILTER_CLEAR = "futures_filter_clear_button"

        /** The three sort rows shared by the Scanners and Futures sheets (`sort_option_<slug of title>`). */
        val SORT_OPTIONS = listOf(
            "sort_option_alphabetically", "sort_option_last_traded_price", "sort_option_change",
        )

        /**
         * The order pad and the plain Buy / Sell pair that opens it. The tool opens the pad and
         * leaves with Back; nothing on the pad is ever tapped.
         */
        const val ORDER_WINDOW_SCREEN = "order_window_screen"
        const val TRADE_BUY_BUTTON = "trade_buy_button"
        const val TRADE_SELL_BUTTON = "trade_sell_button"

        // Investments tab: Quick Tools tiles, Alerts, IPO, ETF, Top Sectors, Customize, vendor pages
        /** The Scalper banner of the Trading tab, and the Mutual Fund tile of Quick Tools (an SDK). */
        const val DASHBOARD_OPEN_SCALPER = "dashboard_open_scalper"
        const val INVEST_TILE_MUTUAL = "invest_tile_2"
        const val INVEST_TILE_ALERTS = "invest_tile_0"
        const val INVEST_TILE_IPO = "invest_tile_1"
        const val INVEST_TILE_REPORTS = "invest_tile_3"
        const val INVEST_TILE_ETF = "invest_tile_4"
        const val INVEST_TILE_PLEDGE = "invest_tile_5"
        const val INVEST_TILE_BACK_OFFICE = "invest_tile_6"
        const val INVEST_TILE_OTHER = "invest_tile_7"
        const val WEB_VIEW_SCREEN = "web_view_screen"
        const val ALERTS_SCREEN = "alerts_screen"
        const val ALERTS_NEW_ALERT_LINK = "alerts_new_alert_link"
        const val ALERTS_SEARCH_INPUT = "alerts_search_input"
        const val ALERT_ROW = "alert_row"
        const val ALERT_ROW_NAME = "alert_row_name"
        const val ALERT_DELETE_BUTTON = "alert_delete_button"
        const val ALERT_SCRIP_FIELD = "alert_scrip_field"
        const val IPO_SCREEN = "ipo_screen"
        const val IPO_TAB_PREFIX = "ipo_tab_"
        const val IPO_SEARCH_INPUT = "ipo_search_input"
        const val IPO_ROW = "ipo_row"
        const val IPO_DETAILS_SCREEN = "ipo_details_screen"
        const val IPO_TOGGLE_CHIP = "ipo_toggle_chip"
        const val IPO_APPLY_BUTTON = "ipo_apply_button"
        const val ETF_SCREEN = "etf_screen"
        const val ETF_TAB_PREFIX = "etf_tab_"
        const val ETF_ROW = "etf_row"
        const val ETF_SEARCH_INPUT = "etf_search_input"
        const val ETF_FILTER_BUTTON = "etf_filter_button"
        const val ETF_FILTER_SHEET = "etf_filter_sheet"
        const val ETF_FILTER_CLEAR = "etf_filter_clear_button"
        const val ETF_FILTER_EXCHANGE = "etf_filter_exchange"
        const val SCANNERS_FILTER_EXCHANGE = "scanners_filter_exchange"
        const val SCRIP_SHEET_BASKET_BUTTON = "scrip_sheet_basket_button"
        const val BASKET_SELECT_SCREEN = "basket_select_screen"
        const val DASHBOARD_TOP_SECTORS_VIEW_ALL = "dashboard_top_sectors_view_all"
        const val SECTORS_SCREEN = "sectors_screen"
        const val SECTORS_TAB_PREFIX = "sectors_tab_"
        const val INVEST_CUSTOMIZE_BUTTON = "invest_customize_button"
        const val INVEST_CUSTOMIZE_SCREEN = "invest_customize_screen"

        // Portfolio
        const val PORTFOLIO_TAB_HOLDINGS = "portfolio_tab_holdings"
        const val PORTFOLIO_TAB_POSITIONS = "portfolio_tab_positions"
        const val HOLDINGS_VIEW = "holdings_view"
        const val POSITIONS_VIEW = "positions_view"
        const val HOLDINGS_MODE_NET_PNL = "holdings_mode_net_pnl"
        const val HOLDINGS_MODE_DAY_PNL = "holdings_mode_day_pnl"
        const val HOLDINGS_SWITCH_TOGGLE = "holdings_switch_toggle"
        const val HOLDINGS_SORT_BUTTON = "holdings_sort_button"
        const val HOLDINGS_SORT_SHEET = "holdings_sort_sheet"
        const val POSITIONS_MODE_NET = "positions_mode_net_p_l"
        const val POSITIONS_MODE_DAY = "positions_mode_day_p_l"
        const val POSITIONS_SORT_BUTTON = "positions_sort_button"
        const val POSITIONS_SORT_SHEET = "positions_sort_sheet"

        // Profile and logout
        const val PROFILE_TAB_ACCOUNT = "profile_tab_account"
        const val PROFILE_TAB_TRADE_SETTINGS = "profile_tab_trade_settings"
        const val PROFILE_LOGOUT_BUTTON = "profile_logout_button"
        const val LOGOUT_CONFIRM_BUTTON = "logout_confirm_button"
    }

    /** View ids of the system runtime permission prompt (AOSP and Google builds alike). */
    object SystemIds {
        const val PERMISSION_MESSAGE = "permission_message"
        const val PERMISSION_ALLOW_BUTTON = "permission_allow_button"
    }

    /** Fallback visible-text labels, used only when an identifier is missing. */
    object Texts {
        val LOGIN = listOf("Login", "LOGIN", "Log In", "LOG IN")
        val PROCEED = listOf("Proceed", "PROCEED")
        val CONFIRM_OTP = listOf("Confirm OTP", "CONFIRM OTP", "Verify", "Submit")
        val CONTINUE = listOf("Continue", "CONTINUE", "Proceed")
        val OTP_SUBMIT = listOf("Verify", "VERIFY", "Submit", "Continue")
        val BIOMETRIC_SKIP = listOf("Skip", "SKIP", "Not Now", "Not now", "Later", "Maybe Later")
        val DISCLOSURE_UNDERSTAND = listOf("I Understand", "I UNDERSTAND", "Next", "NEXT")
        val WATCHLIST_NAV = listOf("Watchlist", "WATCHLIST")
    }

    /** Per step wait budgets, in milliseconds. */
    object Timeouts {
        const val CLIENT_ID_SCREEN = 15_000L
        const val OTP_SCREEN = 30_000L
        const val MPIN_SCREEN = 30_000L
        const val BIOMETRIC_SCREEN = 10_000L
        const val HOME_SCREEN = 30_000L

        /** Grace period for the target app process to come to the foreground. */
        const val APP_LAUNCH = 15_000L

        /**
         * How long to keep polling for the intro screen's Login button. Generous
         * because the notification permission prompt opens on top of this screen
         * after every data reset and has to be answered first.
         */
        const val INTRO_SCREEN = 20_000L

        /**
         * How long to wait for an auto-submitting pin field to move the app on
         * before falling back to tapping an explicit submit button.
         */
        const val AUTO_SUBMIT_PROBE = 4_000L

        /** Per-screen budget while driving Settings to clear the target app's data. */
        const val SETTINGS_SCREEN = 10_000L

        /** A tapped text field taking focus, and entered text showing up in it. */
        const val FIELD_FOCUS = 2_000L

        /**
         * A notification prompt that ACTION_CLICK did not dismiss is tapped as a
         * gesture after this long, and is reported as one the tool cannot answer
         * after [PROMPT_STUCK] (Android can ignore automated taps on permission
         * dialogs). While a prompt is up the run waits for it — a person may
         * allow it by hand — instead of treating it as the app being lost.
         */
        const val PROMPT_GESTURE_AFTER = 2_500L
        const val PROMPT_STUCK = 6_000L

        /** Settle time after a text entry or a tap, before the next lookup. */
        const val SETTLE = 500L

        const val WATCHLIST_NAV = 15_000L
        const val DISCOVER_ADD = 15_000L
        const val WATCHLIST_ADD_SAVE = 10_000L
        const val WATCHLIST_FIRST_TAB = 10_000L
        const val WATCHLIST_EDIT = 10_000L

        /** How long an opened Edit Watchlist gets to list its rows before it is treated as empty. */
        const val EDIT_ROWS = 3_000L
        const val SEARCH_SCREEN = 15_000L

        /**
         * How long [AutomationEngine.tapAndConfirm] waits for a tap to open
         * its screen before treating the tap as dropped and repeating it. Well
         * above a route transition (~300 ms), so a slow open is not tapped twice.
         */
        const val CONFIRM_RETRY = 4_000L

        /** A search query's results arriving. */
        const val SEARCH_RESULTS = 15_000L

        /** A "+" turning into a tick once the add reaches the watchlist. */
        const val ADD_CONFIRM = 8_000L

        /** The "N/50 Scrips" label catching up after adds or deletes. */
        const val COUNT_UPDATE = 10_000L

        /** A sheet, a layout switch, or a details screen appearing or closing. */
        const val SCREEN_CHANGE = 10_000L

        /** Per tab, for Home, Research and Portfolio: every section must stop loading within this. */
        const val TAB_LOAD = 8_000L

        /** Per tab, for the Orders tabs and Positions. */
        const val ORDERS_TAB_LOAD = 5_000L

        /** Screenfuls a tab is scrolled through, so sections below the fold are built and checked too. */
        const val TAB_MAX_PAGES = 5

        /** Logout's API call and the jump to the Client ID screen. */
        const val LOGOUT = 30_000L

        // ── Tradion ──────────────────────────────────────────────────────────

        /**
         * A fresh install checks the network and the app version on the splash
         * screen (each probe can take several seconds) before it routes.
         */
        const val USER_ID_SCREEN = 45_000L
        const val PASSWORD_SCREEN = 30_000L

        /**
         * Tradion's biometric enrolment: how long a person has to touch the sensor (or use the
         * phone's PIN) after Enable now. The tool never answers that prompt itself.
         */
        const val BIOMETRIC_CONFIRM = 90_000L

        /** After Join Now is tapped, how long Tradion may take to hand over to the browser. */
        const val JOIN_NOW_LEAVES = 8_000L

        /** OTP verification, then the disclosure fetch, then the jump to Home. */
        const val OTP_SUBMIT = 30_000L

        /**
         * After login Home is built first and the disclosure route is pushed in
         * the frame after it, so Home alone is not proof there is no dialog.
         */
        const val DISCLOSURE_APPEARS = 6_000L

        /** Per Tradion tab (watchlist, orders, dashboard, portfolio, profile). */
        const val TRADION_TAB_LOAD = 10_000L

        /** Per watchlist or predefined-watchlist tab: a quote fetch each. */
        const val WATCHLIST_TAB_LOAD = 10_000L

        /** An option chain's data after its underlying is chosen. */
        const val OPTION_CHAIN_LOAD = 7_000L

        /** The chart is a web page: how long it may take to finish loading. */
        const val CHART_LOAD = 15_000L

        /** Per More Details tab (Market Depth, Technicals, Fundamentals, Info). */
        const val MORE_INFO_LOAD = 7_000L

        /** Saving the edit screen sorts and deletes over the network before it closes. */
        const val EDIT_SAVE = 20_000L

        /** How long the restore Save is given before it is tapped a second time. */
        const val EDIT_SAVE_FIRST_TRY = 8_000L

        // ── The Watchlist suite (WatchlistSuite.kt) ────────────────────────────────────────────

        /** A click, a navigation or a local UI response: it must be there within this. */
        const val UI_RESPONSE = 3_000L

        /** Between taps of the same control while waiting for a response. */
        const val UI_RETRY = 1_500L

        /** Data that comes from the server (rows, search results, a tab's content). */
        const val DATA_LOAD = 10_000L

        /** Tabs probed on the watchlist strips; stops at the first missing index. */
        const val MAX_WATCHLIST_TABS = 20
    }

    /** Accessibility tree polling interval — no long blocking sleeps. */
    const val POLL_INTERVAL_MS = 350L

    /**
     * If the target app's window stays unreadable for this long the run is
     * aborted. Because the service is scoped to a single package, another app
     * coming to the foreground shows up as a sustained null root rather than as
     * a different package name.
     */
    const val FOREIGN_WINDOW_ABORT_MS = 8_000L

    /** Safety valve on tree walks. */
    const val MAX_NODES_PER_SCAN = 4_000
    const val MAX_TREE_DEPTH = 60

    /** Ashika's test name; the other apps carry theirs in [TargetApp.testName]. */
    const val TEST_NAME = "Phase 1 - Login Automation"
}

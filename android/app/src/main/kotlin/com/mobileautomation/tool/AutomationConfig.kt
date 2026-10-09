package com.mobileautomation.tool

/**
 * Static configuration for the Phase 1 UAT login automation.
 *
 * Everything security relevant lives here so it can be reviewed in one place.
 */
object AutomationConfig {

    /**
     * The ONLY packages this tool is permitted to drive.
     *
     * `com.codifi.dhanush` is the applicationId declared by the target Flutter
     * project (android/app/build.gradle.kts). That project builds its UAT and
     * its CUG/live variants from the same applicationId — they differ only in
     * the API endpoints baked into the build — so the allowlist cannot tell them
     * apart by package name. The operator is responsible for installing the UAT
     * build; see the "Safety restrictions" section of the README.
     *
     * The AccessibilityService is additionally scoped to this same package via
     * android:packageNames in res/xml/accessibility_service_config.xml, so the
     * service is technically incapable of reading any other application.
     */
    val ALLOWED_PACKAGES: Set<String> = setOf("com.codifi.dhanush")

    const val DEFAULT_TARGET_PACKAGE = "com.codifi.dhanush"

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

    /** View ids of the system runtime permission prompt (AOSP and Google builds alike). */
    object SystemIds {
        const val PERMISSION_MESSAGE = "permission_message"
        const val PERMISSION_ALLOW_BUTTON = "permission_allow_button"
    }

    /**
     * System Settings labels for clearing the target app's data. Settings has
     * no stable ids across OEMs, so these are matched by visible text, in
     * order. Add a device's label here if its Settings words it differently.
     */
    object SettingsTexts {
        /** App info entry that opens the storage page (Pixel/AOSP, Samsung, others). */
        val STORAGE_ENTRY = listOf("Storage & cache", "Storage and cache", "Storage", "Storage usage")

        /** Wipes all app data. MIUI shows "Clear data" directly on App info. */
        val CLEAR_STORAGE = listOf("Clear storage", "Clear data", "Clear all data", "CLEAR STORAGE", "CLEAR DATA")

        /**
         * Confirmation dialog buttons. MIUI first asks which data to clear
         * ("Clear all data") and then confirms with "OK".
         */
        val CONFIRM_CLEAR = listOf("Delete", "Clear all data", "OK", "Clear", "Yes", "DELETE")
    }

    /** Fallback visible-text labels, used only when an identifier is missing. */
    object Texts {
        val LOGIN = listOf("Login", "LOGIN", "Log In", "LOG IN")
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

    const val TEST_NAME = "Phase 1 - Login Automation"
}

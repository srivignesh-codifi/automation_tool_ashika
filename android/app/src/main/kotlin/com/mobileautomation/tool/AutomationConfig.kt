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

        /** Edit Watchlist per-row delete icon. Same identifier every row; the row disappears after a delete. */
        const val EDIT_WATCHLIST_DELETE_BUTTON = "edit_watchlist_delete_button"
        const val EDIT_WATCHLIST_SAVE_BUTTON = "edit_watchlist_save_button"

        const val WATCHLIST_SEARCH_BUTTON = "watchlist_search_button"
        const val SEARCH_SCRIP_INPUT = "search_scrip_input"
        const val SEARCH_FILTER_CHIP_ALL = "search_filter_chip_all"
        const val SEARCH_FILTER_CHIP_FUTURES = "search_filter_chip_futures"
        const val SEARCH_FILTER_CHIP_OPTIONS = "search_filter_chip_options"

        /** Search result row's "+" icon. Tagged only while not yet added, so repeated taps skip already-added rows. */
        const val SEARCH_SCRIP_ADD_BUTTON = "search_scrip_add_button"
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
         * because a system dialog (e.g. the notification permission prompt) can
         * sit on top of this screen for as long as the operator takes to notice
         * and dismiss it.
         */
        const val INTRO_SCREEN = 20_000L

        /**
         * How long to wait for an auto-submitting pin field to move the app on
         * before falling back to tapping an explicit submit button.
         */
        const val AUTO_SUBMIT_PROBE = 4_000L

        /** Settle time after a text entry or a tap, before the next lookup. */
        const val SETTLE = 500L

        const val WATCHLIST_NAV = 15_000L
        const val DISCOVER_ADD = 15_000L
        const val WATCHLIST_ADD_SAVE = 10_000L
        const val WATCHLIST_FIRST_TAB = 10_000L
        const val WATCHLIST_EDIT = 10_000L
        const val SEARCH_SCREEN = 15_000L
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

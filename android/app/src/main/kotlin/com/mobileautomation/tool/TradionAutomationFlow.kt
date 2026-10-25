package com.mobileautomation.tool

import com.mobileautomation.tool.AutomationConfig.Texts
import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T
import android.os.SystemClock
import com.mobileautomation.tool.AutomationEngine.WaitOutcome

/**
 * Regression pass for the Tradion app (`com.codifi.rmoney`).
 *
 *   User ID -> Password -> OTP -> (risk disclosure, if raised) -> Watchlist
 *
 * The login pages also have their side paths checked on the way: the Guest User,
 * Unblock Account and Join Now links on the User ID page, Forgot Password and
 * Switch Account on the password page, Login with OTP (authenticator accounts
 * only), the Confirm OTP button, and the Enable 2FA screen. The forms behind
 * those links get a fixed fake value typed into their editable fields and are
 * left with Back; no button on them is ever tapped, so nothing is sent.
 *
 * then, on Watchlist: count the user tabs, confirm Predefined refuses add, use
 * the basket toggle, Filter, scrip details, Edit (rename, reorder, delete,
 * persist, restore), and Search (every segment, exact add/delete row tracking)
 * with the count checked; then a
 * load check of every tab of Orders, Dashboard, Portfolio (Holdings and
 * Positions, with their view switches) and Profile, and finally Logout.
 *
 * Deliberately absent: invalid credential cases, and anything that places,
 * modifies or cancels an order or moves funds. On Orders and Portfolio the
 * tool only selects tabs, flips view switches, opens a sort sheet and scrolls
 * — it never taps a row, a buy / sell / exit button, or a basket action. On
 * the Dashboard it also opens a scrip's sheet and taps the plain Buy and Sell
 * buttons, only to see the order pad open; the pad is left with Back and
 * nothing on it is touched. It types only the three credential values plus the
 * fixed search terms baked into this flow and, for the Dashboard's search
 * checks, the first letters of a scrip that is already listed.
 *
 * Tradion's own Back behaviour shapes the flow: pressing Back on the home screen
 * exits the app, so Back is only ever pressed to close an overlay the flow has
 * just confirmed is open (see [FlowBase.closeWithBack]).
 *
 * The OTP field submits itself when the sixth digit lands (and the Confirm OTP
 * button ignores taps while that request is in flight), so the flow waits for
 * the next screen before it ever taps Confirm.
 */
class TradionAutomationFlow(
    engine: AutomationEngine,
    private val modules: ModuleSelection = ModuleSelection(AppModules.TRADION, null),
) : FlowBase(engine, buildSteps()) {

    init {
        // Lets the report show Login and Watchlist (and the rest) as separate results.
        for (module in AppModules.TRADION) for (id in module.stepIds) step(id).module = module.id
    }

    internal companion object {
        /** Typed into side forms and never submitted. Obviously not a real number or PAN. */
        const val FAKE_MOBILE = "9999999999"
        const val FAKE_PAN = "ABCDE1234F"

        /** The Login module's detours: steps that leave the sign-in path and come back. Skipped by "direct login only". */
        val LOGIN_DETOUR_STEPS = listOf(
            "guest_user_checked", "unblock_account_checked", "join_now_checked",
            "forgot_password_checked", "switch_account_checked",
        )

        fun s(id: String, name: String) = AutomationStepResult(id, name)

        fun buildSteps(): List<AutomationStepResult> = listOf(
            s("target_app_installed", "Target application installed"),
            s("accessibility_enabled", "Accessibility permission enabled"),
            s("target_app_data_cleared", "Target application data cleared"),
            s("target_app_launched", "Target application launched"),
            s("user_id_screen_displayed", "User ID screen displayed"),
            s("guest_user_checked", "Guest User link opens its form and returns to login"),
            s("unblock_account_checked", "Unblock Account link opens its form and returns to login"),
            s("join_now_checked", "Join Now link opens registration and returns to login"),
            s("user_id_entered", "User ID entered"),
            s("login_tapped", "Login button tapped"),
            s("password_screen_displayed", "Password screen displayed"),
            s("forgot_password_checked", "Forgot Password link opens its form and returns to the password page"),
            s("switch_account_checked", "Switch Account returns to login and back to the password page"),
            s("password_entered", "Password entered"),
            s("password_submitted", "Password submitted"),
            s("login_with_otp_checked", "Login with OTP link used (authenticator accounts)"),
            s("otp_screen_displayed", "OTP screen displayed"),
            s("otp_confirm_button_checked", "Confirm OTP button visible and clickable"),
            s("otp_entered", "OTP entered"),
            s("otp_submitted", "OTP submitted"),
            s("enable_2fa_checked", "Enable 2FA button visible and clickable"),
            s("disclosure_accepted", "Risk disclosure accepted"),
            s("home_displayed", "Home displayed"),
        ) + WatchlistSuite.steps() + listOf(
            s("orders_pending_loaded", "Orders › Pending loaded"),
            s("orders_order_book_loaded", "Orders › Order Book loaded"),
            s("orders_trade_book_loaded", "Orders › Trade Book loaded"),
            s("orders_basket_loaded", "Orders › Basket loaded"),
            s("orders_gtt_loaded", "Orders › GTT loaded"),
            s("orders_sip_loaded", "Orders › SIP loaded"),
            s("dashboard_trading_loaded", "Dashboard › Trading loaded"),
            s("dashboard_index_dropdown_checked", "Dashboard › index dropdown opens and lists the indices"),
            s("dashboard_trading_tab_selected", "Dashboard › Trading tab is clickable, opens Trading and shows as selected"),
            s("option_chain_opened", "Trading › Option Chain opened"),
            s("option_chain_nfo_loaded", "Option Chain › NFO: first script loads in 7 s"),
            s("option_chain_mcx_loaded", "Option Chain › MCX: first script loads in 7 s"),
            s("option_chain_cds_loaded", "Option Chain › CDS: first script loads in 7 s"),
            s("option_chain_bfo_loaded", "Option Chain › BFO: first script loads in 7 s"),
            s("research_calls_loaded", "Trading › Research calls: every tab and sub tab loads"),
            s("trading_ideas_tabs_clickable", "Research › Trading Ideas opens and every tab is clickable"),
            s("trading_ideas_subtabs_search", "Trading Ideas › Intraday / Positional tabs clickable and the scrip search finds a scrip"),
            s("trading_ideas_menu_opened", "Trading Ideas › the top-right menu is clickable and opens"),
            s("trading_ideas_filter_checked", "Trading Ideas › Filter By (Status, Exchange, Returns) clickable and Clear resets"),
            s("futures_view_all_checked", "Futures › View All: every tab clickable, sort options work and Clear resets"),
            s("futures_scrip_buy_sell_checked", "Futures › one scrip: Buy and Sell open the order pad (nothing placed)"),
            s("fii_dii_view_all_checked", "FII / DII › View All opens and shows the data"),
            s("dashboard_customize_checked", "Dashboard › Customize opens its options"),
            s("dashboard_investments_loaded", "Dashboard › Investments loaded"),
            s("investment_tab_selected", "Dashboard › Investment tab is clickable, opens Investment and shows as selected"),
            s("alerts_page_opened", "Investment › Alerts: the Alerts page opens and + New Alert opens the create page"),
            s("alert_created", "Investment › Alerts: the New Alert fields take values and an alert is created"),
            s("alerts_search_checked", "Investment › Alerts: the search box finds the alert"),
            s("alert_deleted", "Investment › Alerts: the test alert is deleted again"),
            s("ipo_page_checked", "Investment › IPO: the page opens, every tab is clickable and the search box finds an IPO"),
            s("ipo_details_checked", "Investment › IPO: one IPO's details open and its buttons are clickable (Apply is not tapped)"),
            s("reports_page_opened", "Investment › Reports: the button opens the Reports page"),
            s("etf_page_opened", "Investment › ETF: the button opens the ETF page"),
            s("etf_tabs_options", "ETF › every tab: one instrument's Chart, Alert, Basket and More Details open"),
            s("etf_tabs_buy_sell", "ETF › every tab: Buy and Sell open the order pad (nothing placed)"),
            s("etf_search_settings", "ETF › search finds an instrument; settings icon NSE / BSE and sort options work and Clear resets"),
            s("pledge_page_opened", "Investment › Pledge: the button opens the Pledge page"),
            s("back_office_opened", "Investment › Back Office: the button opens the page and Back returns to the dashboard"),
            s("other_request_opened", "Investment › Other request: the button opens its page"),
            s("top_sectors_view_all_checked", "Investment › Top Sectors: View All opens the sectors page and every tab is clickable"),
            s("scanners_view_all_loaded", "Investments › Scanners › View All: every tab loads"),
            s("screener_tabs_checked", "Scanners › opens, every tab is clickable and shows data"),
            s("screener_dropdown_checked", "Scanners › segment dropdown shows its options and each is clickable"),
            s("screener_search_checked", "Scanners › search accepts a value and lists the matching scrips"),
            s("screener_sort_checked", "Scanners › sort sheet: every sort option clickable, Close restores the default"),
            s("screener_scrip_checked", "Scanners › one scrip: Chart, Op.Chain, More Details open; Buy and Sell open the order pad (nothing placed)"),
            s("scanners_equity_tab_options", "Scanners › Equity: every tab, one instrument's Chart, Alert, Op.Chain and More Details open"),
            s("scanners_equity_tab_buy_sell", "Scanners › Equity: every tab, Buy and Sell open the order pad (nothing placed)"),
            s("scanners_equity_search_settings", "Scanners › Equity: search finds a scrip; settings icon NSE / BSE and sort options work and Clear resets"),
            s("scanners_futures_tab_options", "Scanners › Futures: every tab, one instrument's Chart, Alert, Op.Chain and More Details open"),
            s("scanners_futures_tab_buy_sell", "Scanners › Futures: every tab, Buy and Sell open the order pad (nothing placed)"),
            s("scanners_options_tab_options", "Scanners › Options: every tab, one instrument's Chart, Alert, Op.Chain and More Details open"),
            s("scanners_options_tab_buy_sell", "Scanners › Options: every tab, Buy and Sell open the order pad (nothing placed)"),
            s("scanners_commodity_tab_options", "Scanners › Commodity: every tab, one instrument's Chart, Alert, Op.Chain and More Details open"),
            s("scanners_commodity_tab_buy_sell", "Scanners › Commodity: every tab, Buy and Sell open the order pad (nothing placed)"),
            s("investment_customize_checked", "Investment › Customize opens its options"),
            s("holdings_loaded", "Portfolio › Holdings loaded"),
            s("holdings_mode_toggled", "Holdings › Net P&L / Day P&L toggled"),
            s("holdings_switch_toggled", "Holdings › Stock / MTF switched and restored"),
            s("holdings_sort_opened", "Holdings › Sort sheet opened and closed"),
            s("positions_loaded", "Portfolio › Positions loaded"),
            s("positions_mode_toggled", "Positions › Net P&L / Day P&L toggled"),
            s("positions_sort_opened", "Positions › Sort sheet opened and closed"),
            s("profile_account_loaded", "Profile › Account loaded"),
            s("profile_trade_settings_loaded", "Profile › Trade Settings loaded"),
            s("logged_out", "Logged out"),
        )

        /** Scrips the edit step deletes; the All-segment add that stays puts one back. */
        const val TRIM_COUNT = 1


        const val DASHBOARD_MAX_PAGES = 8
    }

    // Each screen is detected by its container marker OR by a control that only
    // exists on that screen (see LoginAutomationFlow for why).
    private val userIdScreenIds = listOf(T.USER_ID_SCREEN, T.USER_ID_INPUT)
    private val passwordScreenIds = listOf(T.PASSWORD_SCREEN, T.PASSWORD_INPUT)
    private val otpScreenIds = listOf(T.OTP_SCREEN, T.OTP_INPUT)
    private val loginScreenIds = userIdScreenIds + passwordScreenIds + otpScreenIds

    /** "Login already finished": Home, Watchlist, or the disclosure that covers them. */
    private val postLoginIds = listOf(T.HOME_SCREEN, T.WATCHLIST_SCREEN, T.RISK_DISCLOSURE_DIALOG)

    /** The bottom bar: on screen means signed in, whichever tab is showing. */
    private val bottomNavIds = listOf(
        T.BOTTOMNAV_WATCHLIST_TAB, T.BOTTOMNAV_ORDER_TAB, T.BOTTOMNAV_DASHBOARD_TAB,
        T.BOTTOMNAV_PORTFOLIO_TAB, T.BOTTOMNAV_PROFILE_TAB,
    )

    private val predefinedTabIds = (0 until Timeouts.MAX_WATCHLIST_TABS).map { "${T.PREDEFINED_TAB_PREFIX}$it" }

    private val ordersTabIds = listOf(
        T.ORDERS_TAB_PENDING,
        T.ORDERS_TAB_ORDER_BOOK,
        T.ORDERS_TAB_TRADE_BOOK,
        T.ORDERS_TAB_BASKET,
        T.ORDERS_TAB_GTT,
        T.ORDERS_TAB_SIP,
    )

    /**
     * [userId], [password] and [otp] are credentials: they go straight into the
     * app and are never logged, echoed into a note, or put in a report.
     */
    fun run(userId: String, password: String, otp: String): AutomationRunResult {
        val info = startRun(TargetApp.TRADION.testName)
        result.modulesRun = modules.onLabels
        result.modulesSkipped = modules.offLabels

        // 1 — Target application installed ------------------------------------
        val installed = step("target_app_installed")
        begin(installed)
        if (!info.allowlisted) {
            return abort(
                installed,
                "'${engine.targetPackage}' is not in the automation allowlist, so the tool " +
                    "refused to inspect or launch it.",
            )
        }
        if (!info.installed) return abort(installed, "Target application is not installed")
        pass(
            installed,
            buildString {
                append(engine.targetPackage)
                info.versionName?.let { append(" version ").append(it) }
                info.mainActivity?.let { append(", main activity ").append(it) }
            },
        )

        // 2 — Accessibility permission enabled --------------------------------
        val a11y = step("accessibility_enabled")
        begin(a11y)
        if (!AutomationAccessibilityService.isReady(engine.appContext)) {
            return abort(
                a11y,
                "The Automation Tool accessibility service is not connected. Enable it under " +
                    "Settings > Accessibility > Installed apps > Automation Tool. On Android 13 " +
                    "and above you may first need App info > (menu) > Allow restricted settings.",
            )
        }
        pass(a11y, "service connected, scoped to ${engine.targetPackage}")

        // 3 — Target application data cleared ---------------------------------
        // Every run starts from a fresh install state, which also resets the
        // notification permission; the engine answers that prompt with Allow.
        val dataCleared = step("target_app_data_cleared")
        begin(dataCleared)
        if (!engine.clearAppData) {
            resetFromInside = true
            skip(
                dataCleared,
                "\"Clear the app's data first\" was turned off; if the app opens signed in or on a remembered " +
                    "user, it is reset from inside (log out, Switch Account)",
            )
        } else {
            val clearOutcome = engine.clearTargetAppData()
            if (!clearOutcome.ok) {
                if (engine.cancelled) return stopped(dataCleared)
                // Newer Android can keep Settings' action buttons from a service that is not an
                // accessibility tool, so the Clear button may never be reachable. The run goes on
                // and resets the app from inside instead, rather than ending here.
                resetFromInside = true
                skip(
                    dataCleared,
                    "Settings could not be driven on this phone, so the data was not cleared; if the app opens " +
                        "signed in or on a remembered user it is reset from inside (log out, Switch Account). " +
                        "Reason: ${clearOutcome.detail}",
                )
            } else {
                pass(dataCleared, clearOutcome.detail)
            }
        }

        // 4 — Target application launched -------------------------------------
        val launched = step("target_app_launched")
        begin(launched)
        engine.launcher.launch(engine.targetPackage, freshTask = true)?.let { return abort(launched, it) }
        if (!engine.waitForTargetWindow(Timeouts.APP_LAUNCH)) {
            if (engine.cancelled) return stopped(launched)
            return abort(
                launched,
                "The target application's window did not become readable within " +
                    "${Timeouts.APP_LAUNCH / 1000} seconds of launching it.",
            )
        }
        pass(launched, "launcher activity started and its window became readable")

        // 5 — User ID screen displayed ------------------------------------------
        // Tradion has no intro carousel: after the splash's network and version
        // checks a fresh install routes straight to the User ID screen.
        val userIdScreen = step("user_id_screen_displayed")
        begin(userIdScreen)
        val entry = engine.waitForAny(
            userIdScreenIds + passwordScreenIds + otpScreenIds + postLoginIds + bottomNavIds,
            Timeouts.USER_ID_SCREEN,
        )
        when (entry) {
            is WaitOutcome.Found -> when {
                userIdScreenIds.contains(entry.identifier) -> pass(
                    userIdScreen,
                    "'${entry.identifier}' located" + if (engine.notificationPromptsAllowed > 0) {
                        "; the app still asked for the notification permission (answered with Allow). " +
                            "Install the current Tradion build: it skips that prompt when the tool launches it"
                    } else {
                        ""
                    },
                )
                passwordScreenIds.contains(entry.identifier) -> if (resetFromInside) {
                    val problem = switchAccountFromInside()
                    if (engine.cancelled) return stopped(userIdScreen)
                    if (problem != null) return abort(userIdScreen, problem)
                    pass(userIdScreen, "the app remembered a user; Switch Account took it back to the User ID screen")
                } else {
                    return abort(
                        userIdScreen,
                        "The app opened straight onto the password screen, so it still remembers a " +
                            "previous user even though its data was cleared. Use Switch account in the " +
                            "app (or clear its storage by hand) and start the run again.",
                    )
                }
                otpScreenIds.contains(entry.identifier) -> return abort(
                    userIdScreen,
                    "The app opened straight onto the OTP screen, so a login was already under way.",
                )
                else -> if (resetFromInside) {
                    val problem = logOutFromInside()
                    if (engine.cancelled) return stopped(userIdScreen)
                    if (problem != null) return abort(userIdScreen, problem)
                    pass(userIdScreen, "the app opened signed in; it was logged out from inside, then reached the User ID screen")
                } else {
                    return abort(
                        userIdScreen,
                        "The app opened already logged in ('${entry.identifier}' is displayed). " +
                            "Log out or clear the app's storage and start the run again.",
                    )
                }
            }
            is WaitOutcome.Cancelled -> return stopped(userIdScreen)
            else -> return abort(
                userIdScreen,
                reasonFor(entry, "The User ID screen", Timeouts.USER_ID_SCREEN) +
                    " The splash screen checks the network and the app version first; an update " +
                    "prompt or a no-internet screen would stop it here.",
            )
        }

        if (!moduleOn("login")) skipModule("login")
        else if (modules.directLogin) skipSteps(LOGIN_DETOUR_STEPS, "direct login only")
        else {
        // 5a — Guest User, Unblock Account, Join Now ------------------------------
        val guest = step("guest_user_checked")
        begin(guest)
        conclude(
            guest,
            checkSideForm(
                linkId = T.USER_ID_GUEST_LINK,
                formId = T.GUEST_LOGIN_SCREEN,
                inputs = listOf(T.GUEST_MOBILE_INPUT to FAKE_MOBILE),
                returnIds = userIdScreenIds,
                what = "The Guest User form",
            ),
        )?.let { return it }

        val unblock = step("unblock_account_checked")
        begin(unblock)
        // The User ID field on this form is read-only and prefilled, so only the
        // PAN field takes typing.
        conclude(
            unblock,
            checkSideForm(
                linkId = T.USER_ID_UNBLOCK_LINK,
                formId = T.UNBLOCK_SCREEN,
                inputs = listOf(T.UNBLOCK_PAN_INPUT to FAKE_PAN),
                returnIds = userIdScreenIds,
                what = "The Unblock Account form",
            ),
        )?.let { return it }

        val joinNow = step("join_now_checked")
        begin(joinNow)
        conclude(joinNow, checkJoinNow())?.let { return it }

        }

        // 6 — User ID entered ----------------------------------------------------
        val userIdEntered = step("user_id_entered")
        begin(userIdEntered)
        val userIdOutcome = engine.enterText(T.USER_ID_INPUT, userId)
        if (!userIdOutcome.ok) {
            return abort(userIdEntered, "Could not enter the User ID: ${userIdOutcome.detail}")
        }
        if (!engine.sleep(Timeouts.SETTLE)) return stopped(userIdEntered)
        val userIdLengthOk = engine.verifyLength(T.USER_ID_INPUT, userId.length)
        if (userIdLengthOk == false) {
            return abort(
                userIdEntered,
                "The User ID field did not accept the whole value (a length check after entry did " +
                    "not match). An input formatter on the field may be rejecting it.",
            )
        }
        pass(userIdEntered, userIdOutcome.detail + if (userIdLengthOk == true) ", length verified" else "")

        // 7 — Login button tapped --------------------------------------------------
        val loginTapped = step("login_tapped")
        begin(loginTapped)
        val loginOutcome = engine.tap(T.USER_ID_LOGIN_BUTTON, Texts.LOGIN)
        if (!loginOutcome.ok) return abort(loginTapped, "Could not tap Login: ${loginOutcome.detail}")
        pass(loginTapped, loginOutcome.detail)

        // 8 — Password screen displayed -----------------------------------------------
        val passwordScreen = step("password_screen_displayed")
        begin(passwordScreen)
        when (val outcome = engine.waitForAny(passwordScreenIds, Timeouts.PASSWORD_SCREEN)) {
            is WaitOutcome.Found -> pass(passwordScreen, "'${outcome.identifier}' located")
            is WaitOutcome.Cancelled -> return stopped(passwordScreen)
            else -> return abort(
                passwordScreen,
                reasonFor(outcome, "The password screen", Timeouts.PASSWORD_SCREEN) +
                    " The User ID may have been rejected, the account may be blocked, or it may " +
                    "still need its password set (the app sends such a user to a set-password flow).",
            )
        }

        if (moduleOn("login") && !modules.directLogin) {
        // 8a — Forgot Password, Switch Account ---------------------------------------
        val forgot = step("forgot_password_checked")
        begin(forgot)
        // Like Unblock, its User ID field is read-only; only PAN takes typing.
        conclude(
            forgot,
            checkSideForm(
                linkId = T.PASSWORD_FORGOT_LINK,
                formId = T.FORGOT_PASSWORD_SCREEN,
                inputs = listOf(T.FORGOT_PAN_INPUT to FAKE_PAN),
                returnIds = passwordScreenIds,
                what = "The Forgot Password form",
            ),
        )?.let { return it }

        val switchAccount = step("switch_account_checked")
        begin(switchAccount)
        conclude(switchAccount, checkSwitchAccount(userId))?.let { return it }

        }

        // 9 — Password entered ------------------------------------------------------------
        val passwordEntered = step("password_entered")
        begin(passwordEntered)
        val passwordOutcome = engine.enterText(T.PASSWORD_INPUT, password)
        if (!passwordOutcome.ok) {
            return abort(passwordEntered, "Could not enter the password: ${passwordOutcome.detail}")
        }
        pass(passwordEntered, passwordOutcome.detail)

        // 10 — Password submitted --------------------------------------------------------------
        val passwordSubmitted = step("password_submitted")
        begin(passwordSubmitted)
        val submitOutcome = engine.tap(T.PASSWORD_SUBMIT_BUTTON, Texts.PROCEED)
        if (!submitOutcome.ok) {
            return abort(passwordSubmitted, "Could not tap Proceed: ${submitOutcome.detail}")
        }
        pass(passwordSubmitted, submitOutcome.detail)

        // 10a — Login with OTP -------------------------------------------------------------------
        // An account that uses an authenticator lands on the TOTP screen, which
        // carries the Login with OTP link; any other goes straight to OTP.
        val loginWithOtp = step("login_with_otp_checked")
        begin(loginWithOtp)
        when (val outcome = engine.waitForAny(listOf(T.TOTP_SCREEN) + otpScreenIds, Timeouts.OTP_SCREEN)) {
            is WaitOutcome.Found -> if (outcome.identifier == T.TOTP_SCREEN) {
                val link = engine.tapAndConfirm(T.TOTP_LOGIN_WITH_OTP_LINK, otpScreenIds, Timeouts.SCREEN_CHANGE)
                if (engine.cancelled) return stopped(loginWithOtp)
                if (!link.ok) return abort(loginWithOtp, "Login with OTP could not be used: ${link.detail}")
                pass(loginWithOtp, "the authenticator screen offered Login with OTP, and tapping it opened the OTP screen")
            } else {
                skip(
                    loginWithOtp,
                    "this account goes straight to the OTP screen after the password; the Login with " +
                        "OTP link only exists on the authenticator (TOTP) screen",
                )
            }
            is WaitOutcome.Cancelled -> return stopped(loginWithOtp)
            else -> return abort(
                outcome,
                loginWithOtp,
                "The OTP screen",
                Timeouts.OTP_SCREEN,
                " The password may have been rejected.",
            )
        }

        // 11 — OTP screen displayed ------------------------------------------------------------------
        val otpScreen = step("otp_screen_displayed")
        begin(otpScreen)
        when (val outcome = engine.waitForAny(otpScreenIds, Timeouts.OTP_SCREEN)) {
            is WaitOutcome.Found -> pass(otpScreen, "'${outcome.identifier}' located")
            is WaitOutcome.Cancelled -> return stopped(otpScreen)
            else -> return abort(
                otpScreen,
                reasonFor(outcome, "The OTP screen", Timeouts.OTP_SCREEN) +
                    " The password may have been rejected. An account with an authenticator (TOTP) " +
                    "shows a different screen that this tool does not drive.",
            )
        }

        if (moduleOn("login")) {
        // 11a — Confirm OTP button ---------------------------------------------------------------
        // Looked at, never tapped: the field submits itself on the sixth digit,
        // and a second submit could spend the one-time code twice.
        val confirmButton = step("otp_confirm_button_checked")
        begin(confirmButton)
        val confirmReady = engine.waitUntil(Timeouts.SCREEN_CHANGE / 2) { engine.isTappable(T.OTP_SUBMIT_BUTTON) }
        if (engine.cancelled) return stopped(confirmButton)
        if (confirmReady) {
            pass(
                confirmButton,
                "'${T.OTP_SUBMIT_BUTTON}' is on screen, enabled and tappable; not tapped, because the OTP auto-submits",
            )
        } else {
            failAndContinue(
                confirmButton,
                "The Confirm OTP button ('${T.OTP_SUBMIT_BUTTON}') is not on screen as an enabled, tappable " +
                    "control. Identifiers visible: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
            )
        }

        }

        // 12 — OTP entered -----------------------------------------------------------------------------
        val otpEntered = step("otp_entered")
        begin(otpEntered)
        val otpOutcome = engine.enterText(T.OTP_INPUT, otp)
        if (!otpOutcome.ok) return abort(otpEntered, "Could not enter the OTP: ${otpOutcome.detail}")
        pass(otpEntered, otpOutcome.detail)

        // 13 — OTP submitted ------------------------------------------------------------------------------
        // The field submits itself on the sixth digit. Wait for the next screen
        // before touching Confirm OTP; it ignores taps while a submit is in
        // flight, but there is no reason to risk spending the one-time code twice.
        val otpSubmitted = step("otp_submitted")
        begin(otpSubmitted)
        val afterOtpIds = listOf(T.BIOMETRIC_SCREEN) + postLoginIds
        var afterOtp = engine.waitForAny(afterOtpIds, Timeouts.OTP_SUBMIT / 4)
        var submitNote = "submitted automatically when the sixth digit was entered"
        if (afterOtp is WaitOutcome.TimedOut) {
            if (!engine.isPresent(T.OTP_SUBMIT_BUTTON)) {
                return abort(
                    otpSubmitted,
                    "The OTP did not auto submit and '${T.OTP_SUBMIT_BUTTON}' is not on screen to tap.",
                )
            }
            val tap = engine.tap(T.OTP_SUBMIT_BUTTON, Texts.CONFIRM_OTP)
            if (!tap.ok) return abort(otpSubmitted, "Could not tap Confirm OTP: ${tap.detail}")
            submitNote = tap.detail
            afterOtp = engine.waitForAny(afterOtpIds, Timeouts.OTP_SUBMIT)
        }
        val afterOtpId = when (afterOtp) {
            is WaitOutcome.Found -> {
                pass(otpSubmitted, "$submitNote; '${afterOtp.identifier}' followed")
                afterOtp.identifier
            }
            is WaitOutcome.Cancelled -> return stopped(otpSubmitted)
            else -> return abort(
                outcome = afterOtp,
                step = otpSubmitted,
                what = "The screen after the OTP",
                timeoutMs = Timeouts.OTP_SUBMIT,
                extra = " The OTP may have been rejected or have expired.",
            )
        }

        var enableNowTapped = false
        if (moduleOn("login")) {
        // 13a — Enable 2FA --------------------------------------------------------------------------------------
        // Tradion's biometric screen is titled "Enable 2FA security". It appears only on a
        // device with biometrics and has no Skip. Tapping Enable now raises Android's own
        // prompt; a person answers it, and the disclosure step below waits for that.
        val enable2fa = step("enable_2fa_checked")
        begin(enable2fa)
        if (afterOtpId != T.BIOMETRIC_SCREEN) {
            skip(enable2fa, "no Enable 2FA screen was offered; it appears only on a device with biometrics")
        } else if (!engine.isTappable(T.BIOMETRIC_ENABLE_BUTTON)) {
            failAndContinue(
                enable2fa,
                "The Enable 2FA screen is displayed but its Enable now button " +
                    "('${T.BIOMETRIC_ENABLE_BUTTON}') is not on screen as an enabled, tappable control. " +
                    "Identifiers visible: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
            )
        } else {
            val tap = engine.tap(T.BIOMETRIC_ENABLE_BUTTON, listOf("Enable now"))
            if (!tap.ok) {
                failAndContinue(enable2fa, "Enable now could not be tapped: ${tap.detail}")
            } else {
                enableNowTapped = true
                pass(
                    enable2fa,
                    "Enable now was visible and clickable and was tapped (${tap.detail}); it starts Android's " +
                        "own authentication prompt, which this tool does not read or drive: touch the " +
                        "fingerprint sensor (or use the phone's PIN) when it asks, and the run carries on",
                )
            }
        }

        }

        // 14 — Risk disclosure accepted -----------------------------------------------------------------------
        // Optional: it is raised over Home only when the server returns disclosure
        // text. Home is built first and the disclosure route is pushed a frame
        // later, so seeing Home is not proof that no dialog is coming.
        val disclosure = step("disclosure_accepted")
        begin(disclosure)
        var arrivedId = afterOtpId
        var biometricNote = ""
        if (afterOtpId == T.BIOMETRIC_SCREEN) {
            // The enrolment screen is mandatory and has no Skip. The tool never answers the
            // fingerprint / face / PIN prompt itself, so it waits for a person to, the way it
            // waits for a person to allow a permission. While the prompt is up the app's window
            // is unreadable, which is why this is a plain poll and not a wait that treats a lost
            // window as the app having gone.
            val signedIn = postLoginIds + bottomNavIds
            val started = SystemClock.elapsedRealtime()
            var retaps = 0
            var onScreenSince = 0L
            if (!enableNowTapped) {
                val tap = engine.tap(T.BIOMETRIC_ENABLE_BUTTON, listOf("Enable now"))
                if (!tap.ok) {
                    return abort(disclosure, "The biometric screen is showing but Enable now could not be tapped: ${tap.detail}")
                }
            }
            val confirmed = engine.waitUntil(Timeouts.BIOMETRIC_CONFIRM) {
                if (engine.firstPresent(signedIn) != null) return@waitUntil true
                if (engine.isPresent(T.BIOMETRIC_SCREEN)) {
                    // Back on the screen: the prompt is not up (dismissed, or never appeared).
                    val now = SystemClock.elapsedRealtime()
                    if (onScreenSince == 0L) onScreenSince = now
                    if (now - onScreenSince > 6_000L && retaps < 2) {
                        engine.tap(T.BIOMETRIC_ENABLE_BUTTON, listOf("Enable now"))
                        retaps++
                        onScreenSince = 0L
                    }
                } else {
                    onScreenSince = 0L
                }
                false
            }
            if (engine.cancelled) return stopped(disclosure)
            if (!confirmed) {
                return abort(
                    disclosure,
                    "The app is offering to enable biometric login (it is mandatory and has no Skip), and nobody " +
                        "confirmed it within ${Timeouts.BIOMETRIC_CONFIRM / 1000} seconds. This tool never answers " +
                        "the fingerprint, face or PIN prompt itself: touch the sensor when Android asks, or use a " +
                        "device or emulator with no biometrics enrolled.",
                )
            }
            arrivedId = engine.firstPresent(signedIn) ?: T.HOME_SCREEN
            val seconds = (SystemClock.elapsedRealtime() - started) / 1000
            biometricNote = "biometric enrolment was confirmed by hand after $seconds s" +
                (if (retaps > 0) " (Enable now was tapped again $retaps time(s))" else "") + "; "
        }
        var disclosureShown = arrivedId == T.RISK_DISCLOSURE_DIALOG
        if (!disclosureShown) {
            when (engine.waitForAny(listOf(T.RISK_DISCLOSURE_DIALOG), Timeouts.DISCLOSURE_APPEARS)) {
                is WaitOutcome.Found -> disclosureShown = true
                is WaitOutcome.Cancelled -> return stopped(disclosure)
                else -> Unit
            }
        }
        if (!disclosureShown) {
            skip(disclosure, biometricNote + "the risk disclosure did not appear, so there was nothing to accept")
        } else {
            val taps = engine.tapUpTo(
                T.DISCLOSURE_UNDERSTAND_BUTTON,
                Texts.DISCLOSURE_UNDERSTAND,
                maxTimes = 6,
                settleMs = 700L,
            )
            if (engine.cancelled) return stopped(disclosure)
            if (taps == 0) {
                return abort(
                    disclosure,
                    "The risk disclosure is displayed but its confirm button " +
                        "('${T.DISCLOSURE_UNDERSTAND_BUTTON}') could not be tapped even once.",
                )
            }
            if (!engine.waitForGone(T.RISK_DISCLOSURE_DIALOG, Timeouts.SCREEN_CHANGE)) {
                if (engine.cancelled) return stopped(disclosure)
                return abort(
                    disclosure,
                    "Tapped the disclosure's confirm button $taps time(s) but the dialog is still " +
                        "open. A disclosure with a 'blocked' type opens an external link instead of " +
                        "closing; that cannot be passed from here.",
                )
            }
            pass(disclosure, biometricNote + "confirmed across $taps page(s)")
        }

        // 15 — Home displayed ----------------------------------------------------------------------------------
        val home = step("home_displayed")
        begin(home)
        when (val outcome = engine.waitForAny(listOf(T.HOME_SCREEN, T.WATCHLIST_SCREEN), Timeouts.HOME_SCREEN)) {
            is WaitOutcome.Found -> pass(home, "'${outcome.identifier}' located")
            is WaitOutcome.Cancelled -> return stopped(home)
            else -> return abort(
                outcome = outcome,
                step = home,
                what = "The Home screen",
                timeoutMs = Timeouts.HOME_SCREEN,
            )
        }

        // ── Watchlist ───────────────────────────────────────────────────────────────────────────────
        runWatchlist()?.let { return it }

        // ── Orders, Dashboard, Portfolio, Profile ────────────────────────────────────────────────────
        runTabs()?.let { return it }

        // Logged out ------------------------------------------------------------------------------------------
        logout()?.let { return it }

        return finish()
    }

    // ── Login side paths ────────────────────────────────────────────────────────────────────────

    /**
     * What a side-path check found. [stranded] means the flow is no longer on
     * the page it needs to continue from, so the run cannot go on.
     */
    private class SideCheck(val reason: String?, val stranded: Boolean = false, val note: String? = null)

    /** Records [check] on [step]. Returns a result only when the run has to end here. */
    private fun conclude(step: AutomationStepResult, check: SideCheck): AutomationRunResult? {
        if (engine.cancelled) return stopped(step)
        val reason = check.reason
        return when {
            reason == null -> {
                pass(step, check.note)
                null
            }
            check.stranded -> abort(step, reason)
            else -> {
                failAndContinue(step, reason)
                null
            }
        }
    }

    private fun missingTagHint(): String =
        " If no identifier for it is listed, the installed Tradion build predates these login " +
            "identifiers; build it with the current tradion_mobile sources."

    /**
     * Opens the form behind [linkId], types a fixed fake value into each of
     * [inputs], and leaves with Back, expecting one of [returnIds]. Nothing on
     * the form is tapped, so nothing is submitted. A field that rejects the value
     * fails the check but the form is still left, so the run carries on.
     */
    private fun checkSideForm(
        linkId: String,
        formId: String,
        inputs: List<Pair<String, String>>,
        returnIds: List<String>,
        what: String,
    ): SideCheck {
        val open = engine.tapAndConfirm(linkId, listOf(formId), Timeouts.SCREEN_CHANGE)
        if (!open.ok) return SideCheck("$what did not open: ${open.detail}${missingTagHint()}")

        var failure: String? = null
        for ((inputId, value) in inputs) {
            if (!engine.sleep(Timeouts.SETTLE)) return SideCheck("cancelled", stranded = true)
            val typed = engine.enterText(inputId, value)
            if (!typed.ok) {
                failure = "$what opened, but its field '$inputId' did not take input: ${typed.detail}"
                break
            }
            if (engine.verifyLength(inputId, value.length) == false) {
                failure = "$what opened, but its field '$inputId' did not keep the whole value typed into it."
                break
            }
        }

        val closed = closeWithBack(formId)
        val back = if (closed) engine.waitForAny(returnIds, Timeouts.SCREEN_CHANGE) else null
        if (back !is WaitOutcome.Found) {
            return SideCheck(
                (failure?.plus(" It also ") ?: "$what could not be left: it ") +
                    "did not go back to the page it was opened from with Back.",
                stranded = true,
            )
        }
        return SideCheck(failure, note = "opened, field(s) took input, Back returned to '${back.identifier}'")
    }

    /**
     * Join Now hands over to the browser (the registration page), which this
     * tool cannot read, so what is verified is that Tradion left the foreground
     * and could be brought back to the login page.
     */
    private fun checkJoinNow(): SideCheck {
        val tap = engine.tap(T.USER_ID_JOIN_NOW_BUTTON)
        if (!tap.ok) return SideCheck("Join Now could not be tapped: ${tap.detail}${missingTagHint()}")

        var goneSince = 0L
        val left = engine.waitUntil(Timeouts.JOIN_NOW_LEAVES) {
            if (engine.isPresent(T.USER_ID_SCREEN)) {
                goneSince = 0L
                false
            } else {
                val now = SystemClock.elapsedRealtime()
                if (goneSince == 0L) goneSince = now
                now - goneSince >= 1_500L
            }
        }
        if (engine.cancelled) return SideCheck("cancelled", stranded = true)
        if (!left) {
            return SideCheck(
                "Join Now was tapped but Tradion stayed in front for ${Timeouts.JOIN_NOW_LEAVES / 1000} " +
                    "seconds. The registration link opens in the browser; there may be no browser to " +
                    "open it, or the link failed to launch.",
            )
        }

        if (!engine.sleep(2_000L)) return SideCheck("cancelled", stranded = true)
        engine.launcher.launch(engine.targetPackage)?.let {
            return SideCheck("Tradion could not be brought back after Join Now: $it", stranded = true)
        }
        val back = engine.waitForAny(userIdScreenIds, Timeouts.APP_LAUNCH)
        if (back !is WaitOutcome.Found) {
            return SideCheck(
                reasonFor(back, "The User ID screen after Join Now", Timeouts.APP_LAUNCH),
                stranded = true,
            )
        }
        return SideCheck(
            null,
            note = "Tradion handed over to the browser for registration (its page is outside what this " +
                "tool can read), then was brought back to the login page",
        )
    }

    /**
     * Switch Account clears the app's saved user and returns to the User ID
     * page; the User ID is then entered again to get back to the password page.
     * [userId] is a credential: it goes only into the field.
     */
    private fun checkSwitchAccount(userId: String): SideCheck {
        val open = engine.tapAndConfirm(T.PASSWORD_SWITCH_ACCOUNT_LINK, userIdScreenIds, Timeouts.SCREEN_CHANGE)
        if (!open.ok) {
            return SideCheck("Switch Account did not lead to the login page: ${open.detail}${missingTagHint()}")
        }
        val entered = engine.enterText(T.USER_ID_INPUT, userId)
        if (!entered.ok) {
            return SideCheck("Back on the login page, the User ID could not be entered: ${entered.detail}", stranded = true)
        }
        if (!engine.sleep(Timeouts.SETTLE)) return SideCheck("cancelled", stranded = true)
        val login = engine.tap(T.USER_ID_LOGIN_BUTTON, Texts.LOGIN)
        if (!login.ok) return SideCheck("Back on the login page, Login could not be tapped: ${login.detail}", stranded = true)
        val back = engine.waitForAny(passwordScreenIds, Timeouts.PASSWORD_SCREEN)
        if (back !is WaitOutcome.Found) {
            return SideCheck(
                reasonFor(back, "The password page after switching account", Timeouts.PASSWORD_SCREEN),
                stranded = true,
            )
        }
        return SideCheck(null, note = "Switch Account led to the login page, and signing in again returned to the password page")
    }

    // ── Watchlist ───────────────────────────────────────────────────────────────────────────────

    /** The rows of the open edit screen, top to bottom, as the app labels them. */
    private fun editRows(): List<String> =
        engine.groupedLabelsOf(T.REORDER_ROW).map { it.trim() }.filter { it.isNotEmpty() }

    private data class EditOutcome(
        val countAfter: Int?,
        val persistedDeletes: Int,
    )

    /**
     * True when the app's data was not cleared (switched off, or Settings could not be driven), so a
     * remembered user or a signed-in session is undone from inside the app instead.
     */
    private var resetFromInside = false

    /** On the password screen of a remembered user: Switch Account, back to the User ID screen. */
    private fun switchAccountFromInside(): String? {
        val switch = engine.tapAndConfirm(T.PASSWORD_SWITCH_ACCOUNT_LINK, userIdScreenIds, Timeouts.SCREEN_CHANGE)
        return if (switch.ok) null else "Switch Account could not be used to reset the app: ${switch.detail}"
    }

    /** Signed in: Profile > Account > Logout > confirm, then Switch Account if a user is remembered. */
    private fun logOutFromInside(): String? {
        // A risk disclosure can sit over Home first.
        if (engine.isPresent(T.RISK_DISCLOSURE_DIALOG)) {
            engine.tapUpTo(T.DISCLOSURE_UNDERSTAND_BUTTON, Texts.DISCLOSURE_UNDERSTAND, maxTimes = 6, settleMs = 700L)
            engine.waitForGone(T.RISK_DISCLOSURE_DIALOG, Timeouts.SCREEN_CHANGE)
        }
        val profile = engine.tapAndConfirm(T.BOTTOMNAV_PROFILE_TAB, listOf(T.PROFILE_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!profile.ok) return "The app opened signed in and Profile could not be opened to log out: ${profile.detail}"
        engine.tap(T.PROFILE_TAB_ACCOUNT)
        if (!engine.sleep(Timeouts.SETTLE)) return "cancelled"
        var scrolls = 0
        while (!engine.isPresent(T.PROFILE_LOGOUT_BUTTON) && scrolls < 10 && engine.scrollPage(forward = true)) {
            scrolls++
            if (!engine.sleep(Timeouts.SETTLE)) return "cancelled"
        }
        val ask = engine.tapAndConfirm(T.PROFILE_LOGOUT_BUTTON, listOf(T.LOGOUT_CONFIRM_BUTTON), Timeouts.SCREEN_CHANGE)
        if (!ask.ok) return "The app opened signed in and the logout could not be started: ${ask.detail}"
        val confirm = engine.tapAndConfirm(
            T.LOGOUT_CONFIRM_BUTTON,
            userIdScreenIds + passwordScreenIds,
            Timeouts.LOGOUT,
            retryAfterMs = Timeouts.LOGOUT,
        )
        if (!confirm.ok) return "Logout was confirmed but the login screen never appeared: ${confirm.detail}"
        return if (engine.isPresent(T.PASSWORD_SCREEN)) switchAccountFromInside() else null
    }

    private fun moduleOn(id: String): Boolean = modules.isOn(id)

    /** Marks the steps of a module the operator switched off as skipped. */
    private fun skipModule(id: String) = skipSteps(modules.stepIds(id), modules.skipNote(id))

    /** Marks every step in [ids] skipped, for work that cannot be done because of an earlier failure. */
    private fun skipSteps(ids: List<String>, reason: String) {
        for (id in ids) {
            val pending = step(id)
            if (pending.status == StepStatus.PENDING) skip(pending, reason)
        }
    }

    /** The Watchlist module: [WatchlistSuite] runs its independent test cases and reports one step per case. */
    private fun runWatchlist(): AutomationRunResult? =
        WatchlistSuite(
            flow = this,
            engine = engine,
            modules = modules,
            stopped = { stopped(it) },
            sessionLost = { sessionEnded(it, "checking ${it.name}") },
        ).run()


    // ── Orders, Dashboard, Portfolio, Profile ─────────────────────────────────────────────────

    /**
     * One inner tab to check. [tabId] is tapped first (null: the page has no
     * strip); [strip] lists its siblings so a scrolled-off tab can be brought
     * into view. [chips] are in-page switches (scanner tabs) that are tapped in
     * turn when they are present; a missing chip is not an error because some
     * only exist when the server sends the section.
     */
    private class TabStep(
        val stepId: String,
        val tabId: String?,
        val strip: List<String> = emptyList(),
        val chips: List<String> = emptyList(),
        val maxPages: Int = AutomationConfig.Timeouts.TAB_MAX_PAGES,
    )

    private class Page(val name: String, val bottomTab: String, val screen: String)

    private fun runTabs(): AutomationRunResult? {
        val budget = Timeouts.TRADION_TAB_LOAD

        // Orders ----------------------------------------------------------------------------------------
        val orderTabs = listOf(
            "orders_pending_loaded" to T.ORDERS_TAB_PENDING,
            "orders_order_book_loaded" to T.ORDERS_TAB_ORDER_BOOK,
            "orders_trade_book_loaded" to T.ORDERS_TAB_TRADE_BOOK,
            "orders_basket_loaded" to T.ORDERS_TAB_BASKET,
            "orders_gtt_loaded" to T.ORDERS_TAB_GTT,
            "orders_sip_loaded" to T.ORDERS_TAB_SIP,
        ).map { (stepId, tab) -> TabStep(stepId, tab, strip = ordersTabIds) }
        if (!moduleOn("orders")) skipModule("orders") else {
        withPage(Page("Orders", T.BOTTOMNAV_ORDER_TAB, T.ORDERBOOK_SCREEN), orderTabs.map { it.stepId }) {
            for (tab in orderTabs) checkTab(tab, budget)?.let { return@withPage it }
            null
        }?.let { return it }
        }

        // Dashboard -----------------------------------------------------------------------------------------
        val dashboardStrip = listOf(T.DASHBOARD_TAB_TRADING, T.DASHBOARD_TAB_INVESTMENTS)
        val dashboardTabs = listOf(
            TabStep(
                "dashboard_trading_loaded", T.DASHBOARD_TAB_TRADING, dashboardStrip,
                chips = listOf("short_buildup", "long_buildup").map { "${T.DASHBOARD_SCANNER_TAB_PREFIX}$it" },
                maxPages = DASHBOARD_MAX_PAGES,
            ),
            TabStep(
                "dashboard_investments_loaded", T.DASHBOARD_TAB_INVESTMENTS, dashboardStrip,
                chips = listOf("topgainers", "toplosers", "52_week_high", "52_week_low")
                    .map { "${T.DASHBOARD_SCANNER_TAB_PREFIX}$it" },
                maxPages = DASHBOARD_MAX_PAGES,
            ),
        )
        val dashboardStepIds = dashboardTabs.map { it.stepId } + AppModules.DASHBOARD_SCENARIO_STEPS
        if (!moduleOn("dashboard")) skipModule("dashboard") else {
        withPage(Page("Dashboard", T.BOTTOMNAV_DASHBOARD_TAB, T.DASHBOARD_SCREEN), dashboardStepIds) {
            checkTab(dashboardTabs[0], budget)?.let { return@withPage it }
            indexDropdownStep()?.let { return@withPage it }
            tradingTabSelectedStep()?.let { return@withPage it }
            optionChainSteps()?.let { return@withPage it }
            researchCallsStep()?.let { return@withPage it }
            tradingIdeasSteps()?.let { return@withPage it }
            futuresSteps()?.let { return@withPage it }
            fiiDiiStep()?.let { return@withPage it }
            customizeStep()?.let { return@withPage it }
            checkTab(dashboardTabs[1], budget)?.let { return@withPage it }
            investmentTabSelectedStep()?.let { return@withPage it }
            alertsSteps()?.let { return@withPage it }
            ipoSteps()?.let { return@withPage it }
            vendorTileStep("reports_page_opened", T.INVEST_TILE_REPORTS, "Reports")?.let { return@withPage it }
            etfSteps()?.let { return@withPage it }
            vendorTileStep("pledge_page_opened", T.INVEST_TILE_PLEDGE, "Pledge")?.let { return@withPage it }
            vendorTileStep("back_office_opened", T.INVEST_TILE_BACK_OFFICE, "Back Office")?.let { return@withPage it }
            vendorTileStep("other_request_opened", T.INVEST_TILE_OTHER, "Other request")?.let { return@withPage it }
            topSectorsStep()?.let { return@withPage it }
            scannersViewAllStep()?.let { return@withPage it }
            screenerSteps()?.let { return@withPage it }
            investmentCustomizeStep()
        }?.let { return it }
        }

        // Portfolio -------------------------------------------------------------------------------------------
        val portfolioStrip = listOf(T.PORTFOLIO_TAB_HOLDINGS, T.PORTFOLIO_TAB_POSITIONS)
        val portfolioSteps = listOf(
            "holdings_loaded", "holdings_mode_toggled", "holdings_switch_toggled", "holdings_sort_opened",
            "positions_loaded", "positions_mode_toggled", "positions_sort_opened",
        )
        if (!moduleOn("portfolio")) skipModule("portfolio") else {
        withPage(Page("Portfolio", T.BOTTOMNAV_PORTFOLIO_TAB, T.PORTFOLIO_SCREEN), portfolioSteps) {
            checkTab(TabStep("holdings_loaded", T.PORTFOLIO_TAB_HOLDINGS, portfolioStrip), budget)
                ?.let { return@withPage it }
            switchStep(
                "holdings_mode_toggled", T.HOLDINGS_VIEW,
                listOf(T.HOLDINGS_MODE_DAY_PNL, T.HOLDINGS_MODE_NET_PNL),
                "Net P&L / Day P&L",
                "there are no holdings, so the Net P&L / Day P&L switch is not shown",
            )?.let { return@withPage it }
            holdingsSwitchStep(budget)?.let { return@withPage it }
            sheetStep(
                "holdings_sort_opened", T.HOLDINGS_SORT_BUTTON, T.HOLDINGS_SORT_SHEET, T.HOLDINGS_VIEW,
                "there are no holdings to sort",
            )?.let { return@withPage it }
            checkTab(TabStep("positions_loaded", T.PORTFOLIO_TAB_POSITIONS, portfolioStrip), budget)
                ?.let { return@withPage it }
            switchStep(
                "positions_mode_toggled", T.POSITIONS_VIEW,
                listOf(T.POSITIONS_MODE_DAY, T.POSITIONS_MODE_NET),
                "Net P&L / Day P&L",
                "there are no open positions, so the Net P&L / Day P&L switch is not shown",
            )?.let { return@withPage it }
            sheetStep(
                "positions_sort_opened", T.POSITIONS_SORT_BUTTON, T.POSITIONS_SORT_SHEET, T.POSITIONS_VIEW,
                "there are no open positions to sort",
            )
        }?.let { return it }
        }

        // Profile ----------------------------------------------------------------------------------------------
        val profileStrip = listOf(T.PROFILE_TAB_ACCOUNT, T.PROFILE_TAB_TRADE_SETTINGS)
        val profileTabs = listOf(
            TabStep("profile_account_loaded", T.PROFILE_TAB_ACCOUNT, profileStrip, maxPages = 3),
            TabStep("profile_trade_settings_loaded", T.PROFILE_TAB_TRADE_SETTINGS, profileStrip, maxPages = 3),
        )
        if (!moduleOn("profile")) skipModule("profile") else {
        withPage(Page("Profile", T.BOTTOMNAV_PROFILE_TAB, T.PROFILE_SCREEN), profileTabs.map { it.stepId }) {
            for (tab in profileTabs) checkTab(tab, budget)?.let { return@withPage it }
            null
        }?.let { return it }
        }
        return null
    }

    /**
     * Opens [page]'s bottom tab and runs [body] on it. When the tab cannot be
     * opened, the first check fails and its dependent checks are skipped. This
     * keeps one navigation problem from becoming several duplicate failures.
     * A redirect to a login screen ends the run immediately because every
     * remaining authenticated check would be invalid. [body] returns a result
     * only when the run has to end.
     */
    private fun withPage(
        page: Page,
        stepIds: List<String>,
        body: () -> AutomationRunResult?,
    ): AutomationRunResult? {
        val first = step(stepIds.first())
        begin(first)
        sessionEnded(first, "opening the ${page.name} tab")?.let { return it }
        val open = engine.tapAndConfirm(
            page.bottomTab,
            listOf(page.screen),
            Timeouts.SCREEN_CHANGE,
            stopOn = loginScreenIds,
        )
        if (!open.ok) {
            if (engine.cancelled) return stopped(first)
            sessionEnded(first, "opening the ${page.name} tab")?.let { return it }
            failAndContinue(first, "Could not open the ${page.name} tab: ${open.detail}")
            skipSteps(stepIds.drop(1), "the ${page.name} tab did not open")
            return null
        }
        return body()
    }

    /**
     * Finishes on an unexpected return to an authentication screen. A fresh
     * OTP is required to establish another Tradion session, so retrying or
     * continuing the remaining tabs would only create misleading failures.
     */
    private fun sessionEnded(
        current: AutomationStepResult,
        context: String,
    ): AutomationRunResult? {
        val identifier = engine.firstPresent(loginScreenIds) ?: return null
        val screen = when (identifier) {
            in passwordScreenIds -> "Password"
            in otpScreenIds -> "OTP"
            else -> "User ID"
        }
        val reason = "Tradion returned to the $screen screen while $context, so its " +
            "authenticated session ended before this check. This is a target-app/session " +
            "failure (commonly an API 401), not a missing automation identifier. Start a " +
            "new run with a fresh OTP and make sure the same account is not being used by " +
            "another active session."
        failAndContinue(current, reason)
        skipSteps(
            steps.filter { it.status == StepStatus.PENDING }.map { it.id },
            "not run because Tradion's authenticated session ended",
        )
        return finish()
    }

    /**
     * Selects [tab] (and each of its chips that is on screen) and waits for
     * every section to finish loading. A tab still loading at its deadline, or
     * showing the retry screen, fails its own step and the run moves on.
     */
    private fun checkTab(tab: TabStep, budgetMs: Long): AutomationRunResult? {
        val current = step(tab.stepId)
        if (current.status == StepStatus.PENDING) begin(current)
        sessionEnded(current, "checking ${current.name}")?.let { return it }
        if (tab.tabId != null) {
            if (!engine.revealInList(tab.tabId, tab.strip.ifEmpty { listOf(tab.tabId) })) {
                sessionEnded(current, "checking ${current.name}")?.let { return it }
                failAndContinue(
                    current,
                    "The '${tab.tabId}' tab is not on screen. Identifiers on screen: " +
                        "${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
                )
                return null
            }
            val select = engine.tap(tab.tabId)
            if (!select.ok) {
                if (engine.cancelled) return stopped(current)
                failAndContinue(current, "Could not select '${tab.tabId}': ${select.detail}")
                return null
            }
            if (!engine.sleep(Timeouts.SETTLE)) return stopped(current)
            sessionEnded(current, "checking ${current.name}")?.let { return it }
        }

        val check = checkPageLoads(budgetMs, tab.maxPages)
        if (engine.cancelled) return stopped(current)
        sessionEnded(current, "checking ${current.name}")?.let { return it }
        val failures = mutableListOf<String>()
        loadFailure(check, budgetMs)?.let { failures += it }

        // In-page switches (the scanner tabs), each checked once it is tapped.
        val visited = mutableListOf<String>()
        for (chip in tab.chips) {
            if (!revealDown(chip)) continue
            val select = engine.tap(chip)
            if (!select.ok) {
                failures += "'${chip.removePrefix(T.DASHBOARD_SCANNER_TAB_PREFIX)}' could not be selected (${select.detail})"
                continue
            }
            if (!engine.sleep(Timeouts.SETTLE)) return stopped(current)
            sessionEnded(current, "checking ${current.name}")?.let { return it }
            val chipCheck = checkPageLoads(budgetMs, 1)
            if (engine.cancelled) return stopped(current)
            sessionEnded(current, "checking ${current.name}")?.let { return it }
            val name = chip.removePrefix(T.DASHBOARD_SCANNER_TAB_PREFIX)
            loadFailure(chipCheck, budgetMs, name)?.let { failures += it }
            visited += name
        }
        val chipNote = if (visited.isEmpty()) "" else "; scanner tab(s) ${visited.joinToString()} also loaded"
        if (failures.isNotEmpty()) failAndContinue(current, failures.joinToString("; "))
        else pass(current, loadSummary(check) + chipNote)
        return null
    }

    // ── Dashboard: option chain, research calls, scanners ────────────────────────────────────────

    /** Scrolls the page back to its top so the tab strip in the header is reachable again. */
    private fun scrollToTop() {
        var n = 0
        while (n < 12 && engine.scrollPage(forward = false)) n++
        engine.sleep(Timeouts.SETTLE)
    }

    /**
     * Visits tabs `<prefix>0`, `<prefix>1`, ... until one is missing, selecting
     * each and checking it loads within [budgetMs]. Returns the number of tabs
     * seen and the failures.
     */
    private fun walkTabs(prefix: String, budgetMs: Long, maxTabs: Int = 8, perTab: (Int) -> List<String> = { emptyList() }): Pair<Int, List<String>> {
        val all = (0 until maxTabs).map { "$prefix$it" }
        val failures = mutableListOf<String>()
        var seen = 0
        for (i in all.indices) {
            if (!engine.revealInList(all[i], all)) break
            seen++
            val select = engine.tap(all[i])
            if (!select.ok) { failures += "tab ${i + 1} could not be selected (${select.detail})"; continue }
            if (!engine.sleep(Timeouts.SETTLE)) break
            val check = checkPageLoads(budgetMs, 1)
            loadFailure(check, budgetMs, "tab ${i + 1}")?.let { failures += it }
            failures += perTab(i)
            if (engine.cancelled) break
        }
        return seen to failures
    }

    private fun optionChainSteps(): AutomationRunResult? {
        val opened = step("option_chain_opened")
        val segments = listOf("nfo", "mcx", "cds", "bfo")
        val segmentSteps = segments.map { step("option_chain_${it}_loaded") }
        begin(opened)
        scrollToTop()
        if (!revealDown(T.DASHBOARD_OPEN_OPTION_CHAIN)) {
            failAndContinue(opened, "The Option Chain tile is not on the Trading tab.")
            skipSteps(segmentSteps.map { it.id }, "the Option Chain did not open")
            scrollToTop()
            return null
        }
        val open = engine.tapAndConfirm(T.DASHBOARD_OPEN_OPTION_CHAIN, listOf(T.OPTION_CHAIN_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!open.ok) {
            if (engine.cancelled) return stopped(opened)
            failAndContinue(opened, "The Option Chain did not open: ${open.detail}")
            skipSteps(segmentSteps.map { it.id }, "the Option Chain did not open")
            scrollToTop()
            return null
        }
        pass(opened, open.detail)

        val chips = segments.map { "${T.OPTION_CHAIN_SEGMENT_PREFIX}$it" }
        for ((index, seg) in segments.withIndex()) {
            val current = segmentSteps[index]
            begin(current)
            val failure = loadOptionChainSegment(chips[index], chips, seg.uppercase())
            if (engine.cancelled) return stopped(current)
            if (failure != null) failAndContinue(current, failure) else pass(current, lastOptionChainNote)
            // Whatever happened, be on the option chain (not a search screen) for the next segment.
            if (engine.isPresent(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)) closeWithBack(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN)
        }
        // Leave: the in-app arrow if there is one, else Back (it is a route, so Back is safe).
        if (engine.isPresent(T.OPTION_CHAIN_SCREEN)) {
            val back = engine.tapAndConfirm(
                "option_chain_back_button", listOf(T.DASHBOARD_SCREEN), Timeouts.SCREEN_CHANGE,
            )
            if (!back.ok && engine.isPresent(T.OPTION_CHAIN_SCREEN)) closeWithBack(T.OPTION_CHAIN_SCREEN)
        }
        scrollToTop()
        return null
    }

    private var lastOptionChainNote = ""

    /** Search → segment chip → first underlying → the chain's table within the budget. Null on success. */
    private fun loadOptionChainSegment(chip: String, chips: List<String>, name: String): String? {
        val toSearch = engine.tapAndConfirm(T.OPTION_CHAIN_SEARCH_BUTTON, listOf(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!toSearch.ok) return "The search icon did not open the script search: ${toSearch.detail}"
        if (!engine.revealInList(chip, chips)) return "The $name segment chip is not on the search screen."
        val select = engine.tap(chip)
        if (!select.ok) return "Could not select the $name chip: ${select.detail}"
        // Changing segment refetches the list without a loader, so stale rows can linger; let it land.
        if (!engine.sleep(2_500L)) return "cancelled"
        val rows = engine.waitForAny(listOf(T.OPTION_CHAIN_SPOT_ROW), Timeouts.SCREEN_CHANGE)
        if (rows !is WaitOutcome.Found) return "No scripts were listed under $name."
        val tap = engine.tap(T.OPTION_CHAIN_SPOT_ROW)
        if (!tap.ok) return "Could not choose the first $name script: ${tap.detail}"
        val start = android.os.SystemClock.elapsedRealtime()
        if (!engine.waitForGone(T.OPTION_CHAIN_SPOT_SEARCH_SCREEN, Timeouts.SCREEN_CHANGE)) {
            return "Choosing the first $name script did not return to the option chain."
        }
        val table = engine.waitForAny(listOf(T.OPTION_CHAIN_TABLE), Timeouts.OPTION_CHAIN_LOAD)
        if (table !is WaitOutcome.Found) {
            return "The first $name script's option chain had not loaded within ${Timeouts.OPTION_CHAIN_LOAD / 1000} seconds " +
                "(still loading, empty, or that script has no expiry)."
        }
        lastOptionChainNote = "the first $name script's chain loaded in %.1f s".format((android.os.SystemClock.elapsedRealtime() - start) / 1000.0)
        return null
    }

    private fun researchCallsStep(): AutomationRunResult? {
        val current = step("research_calls_loaded")
        begin(current)
        scrollToTop()
        if (!revealDown(T.DASHBOARD_OPEN_RESEARCH_CALLS)) {
            failAndContinue(current, "The Research calls tile is not on the Trading tab.")
            scrollToTop()
            return null
        }
        val open = engine.tapAndConfirm(T.DASHBOARD_OPEN_RESEARCH_CALLS, listOf(T.RESEARCH_CALL_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!open.ok) {
            if (engine.cancelled) return stopped(current)
            failAndContinue(current, "Research calls did not open: ${open.detail}")
            scrollToTop()
            return null
        }
        var subTotal = 0
        val (tabs, failures) = walkTabs(T.RESEARCH_TAB_PREFIX, Timeouts.TRADION_TAB_LOAD) { tabIndex ->
            val (subs, subFailures) = walkTabs(T.RESEARCH_SUBTAB_PREFIX, Timeouts.TRADION_TAB_LOAD)
            subTotal += subs
            subFailures.map { "tab ${tabIndex + 1}: $it" }
        }
        if (engine.cancelled) return stopped(current)
        when {
            tabs == 0 -> failAndContinue(current, "No Research calls tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
            failures.isNotEmpty() -> failAndContinue(current, failures.joinToString("; ") + ".")
            else -> pass(current, "$tabs tab(s) and $subTotal sub tab(s) each finished loading")
        }
        if (!closeWithBack(T.RESEARCH_CALL_SCREEN) && !engine.cancelled && current.status == StepStatus.PASSED) {
            failAndContinue(current, "Research calls opened but did not close on Back.")
        }
        scrollToTop()
        return null
    }

    private fun scannersViewAllStep(): AutomationRunResult? {
        val current = step("scanners_view_all_loaded")
        begin(current)
        if (!revealDown(T.DASHBOARD_SCANNERS_VIEW_ALL)) {
            failAndContinue(current, "The Scanners 'View All' link is not on the Investments tab.")
            scrollToTop()
            return null
        }
        val open = engine.tapAndConfirm(T.DASHBOARD_SCANNERS_VIEW_ALL, listOf(T.SCANNERS_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!open.ok) {
            if (engine.cancelled) return stopped(current)
            failAndContinue(current, "Scanners 'View All' did not open: ${open.detail}")
            scrollToTop()
            return null
        }
        val (tabs, failures) = walkTabs(T.SCANNERS_TAB_PREFIX, Timeouts.TRADION_TAB_LOAD)
        if (engine.cancelled) return stopped(current)
        when {
            tabs == 0 -> failAndContinue(current, "No scanner tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
            failures.isNotEmpty() -> failAndContinue(current, failures.joinToString("; ") + ".")
            else -> pass(current, "$tabs scanner tab(s) each finished loading")
        }
        if (!closeWithBack(T.SCANNERS_SCREEN) && !engine.cancelled && current.status == StepStatus.PASSED) {
            failAndContinue(current, "The scanners screen opened but did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // ── Watchlist scenarios ───────────────────────────────────────────────────────────────────
    //
    // Nothing here keeps a change: the gear screen's radios are put back and it is left with Clear,
    // the Alert page is typed into and left with Back (Create is only checked, never tapped), and every
    // scrip a step adds is removed again.

    // Search extras: one scrip per exchange, then a FUT, CE and PE contract ------------------------------

    /** The words of a result row that name the scrip: tokens with a letter in them (prices and changes have none). */
    private fun stableTokens(label: String): List<String> =
        label.split(Regex("""\s+""")).filter { token -> token.any { it.isLetter() } }

    // ── Investment tab scenarios ──────────────────────────────────────────────────────────────
    //
    // As with the Trading tab: Buy and Sell only open the order pad (left with Back), the IPO's Apply and the
    // Alert page's Delete-less flows never touch anything else. The one thing that is created is a test alert
    // (Greater than 9999999, a value no scrip reaches), found again through the Alerts search and deleted.

    private val scannerDeepIds = listOf(
        "scanners_equity_tab_options", "scanners_equity_tab_buy_sell", "scanners_equity_search_settings",
        "scanners_futures_tab_options", "scanners_futures_tab_buy_sell", "scanners_options_tab_options",
        "scanners_options_tab_buy_sell", "scanners_commodity_tab_options", "scanners_commodity_tab_buy_sell",
    )

    private class SheetTarget(val button: String, val screen: String, val label: String)

    private val sheetChart = SheetTarget(T.SCRIP_SHEET_CHART_BUTTON, T.CHART_SCREEN, "Chart")
    private val sheetAlert = SheetTarget(T.SCRIP_SHEET_ALERT_BUTTON, T.ALERT_SCREEN, "Alert")
    private val sheetBasket = SheetTarget(T.SCRIP_SHEET_BASKET_BUTTON, T.BASKET_SELECT_SCREEN, "Basket")
    private val sheetOpChain = SheetTarget(T.SCRIP_SHEET_OPCHAIN_BUTTON, T.OPTION_CHAIN_SCREEN, "Op.Chain")
    private val sheetMore = SheetTarget(T.SCRIP_SHEET_MORE_BUTTON, T.MORE_INFO_SCREEN, "More Details")

    /**
     * For the first [rowId] row of the list on [home]: opens its sheet once per target, taps the target's box, expects
     * its screen and leaves with Back. A box the sheet does not offer is only noted. Returns the labels opened, the
     * labels not offered, and the problems.
     */
    private fun optionsFromFirstRow(
        rowId: String, home: String, targets: List<SheetTarget>,
    ): Triple<List<String>, List<String>, List<String>> {
        val opened = mutableListOf<String>()
        val notOffered = mutableListOf<String>()
        val problems = mutableListOf<String>()
        val boxes = targets.map { it.button }
        for (t in targets) {
            if (!backTo(home, orderPadOverlays)) {
                problems += "could not get back to the list before ${t.label}"
                break
            }
            if (!engine.isPresent(rowId)) {
                problems += "${t.label}: no instrument row is on screen"
                break
            }
            val row = engine.tap(rowId)
            if (!row.ok) {
                problems += "${t.label}: the first instrument could not be tapped (${row.detail})"
                continue
            }
            if (engine.waitForAny(boxes, Timeouts.SCREEN_CHANGE) !is WaitOutcome.Found) {
                problems += "${t.label}: the instrument's sheet showed none of ${targets.joinToString(" / ") { it.label }}.${dashTagHint()}"
                continue
            }
            if (!engine.isPresent(t.button)) {
                notOffered += t.label
                continue
            }
            val open = engine.tapAndConfirm(t.button, listOf(t.screen), Timeouts.SCREEN_CHANGE, retryAfterMs = Timeouts.SCREEN_CHANGE)
            if (!open.ok) {
                problems += "${t.label} did not open: ${open.detail}"
                continue
            }
            opened += t.label
            if (!engine.sleep(Timeouts.SETTLE)) break
            if (!closeWithBack(t.screen) && !engine.cancelled) problems += "${t.label} did not close on Back"
        }
        backTo(home, orderPadOverlays)
        if (opened.isEmpty() && problems.isEmpty()) problems += "none of ${targets.joinToString(" / ") { it.label }} is offered for the first instrument"
        return Triple(opened, notOffered, problems)
    }

    /**
     * Taps each tab `<prefix>0..` that exists, waits for the list to load and for a [rowId] row, then runs [body]
     * (which returns problems). Returns the number of tabs and every problem, each prefixed with its tab.
     */
    private fun forEachTab(prefix: String, maxTabs: Int, rowId: String, body: () -> List<String>): Pair<Int, List<String>> {
        val all = (0 until maxTabs).map { "$prefix$it" }
        val problems = mutableListOf<String>()
        var seen = 0
        for ((i, id) in all.withIndex()) {
            if (!engine.revealInList(id, all)) break
            seen++
            val label = "tab ${i + 1}"
            val tap = engine.tap(id)
            if (!tap.ok) {
                problems += "$label could not be tapped (${tap.detail})"
                continue
            }
            if (!engine.sleep(Timeouts.SETTLE * 2)) break
            checkPageLoads(Timeouts.TRADION_TAB_LOAD, 1)
            if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(rowId) }) {
                problems += "$label lists no instrument"
                continue
            }
            problems += body().map { "$label: $it" }
            if (engine.cancelled) break
        }
        return seen to problems
    }

    /** The first name word of each built [rowId] row (the words with letters; prices have none). */
    private fun rowNames(rowId: String): List<String> =
        engine.groupedLabelsOf(rowId).mapNotNull { stableTokens(it).firstOrNull() }

    /**
     * Types the first three letters of the first [rowId] row's name into [inputId] and checks the list narrowed to
     * rows starting with it (or shrank), then clears the field.
     */
    private fun searchVerdict(inputId: String, rowId: String, what: String): Verdict {
        if (!engine.isPresent(inputId)) return bad("The $what search field is not on screen.${dashTagHint()}")
        val before = rowNames(rowId)
        val term = before.firstOrNull()?.take(3)
        if (term.isNullOrEmpty()) return bad("No $what is listed to search for.${dashTagHint()}")
        val typed = engine.enterText(inputId, term)
        if (!typed.ok) return bad("The $what search field did not take '$term': ${typed.detail}")
        val narrowed = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
            val now = rowNames(rowId)
            now.isNotEmpty() && (now.all { it.startsWith(term, ignoreCase = true) } || now.size < before.size)
        }
        val shown = rowNames(rowId).take(3).joinToString(" | ") { clip(it) }
        engine.enterText(inputId, "")
        engine.sleep(Timeouts.SETTLE * 2)
        return if (narrowed) ok("searching '$term' listed: $shown")
        else bad("Searching '$term' did not narrow the list (showing: ${shown.ifEmpty { "none" }}).")
    }

    private fun investmentTabSelectedStep(): AutomationRunResult? = runCheck("investment_tab_selected") {
        scrollToTop()
        val toTrading = engine.tap(T.DASHBOARD_TAB_TRADING)
        if (!toTrading.ok) return@runCheck bad("The Trading tab could not be tapped to move the selection: ${toTrading.detail}")
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isSelected(T.DASHBOARD_TAB_TRADING) }) {
            return@runCheck bad("Tapping Trading did not select the Trading tab.")
        }
        val tap = engine.tap(T.DASHBOARD_TAB_INVESTMENTS)
        if (!tap.ok) return@runCheck bad("The Investment tab could not be tapped: ${tap.detail}")
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(T.DASHBOARD_INVESTMENTS_VIEW) }) {
            return@runCheck bad("Tapping Investment did not open the Investment page.")
        }
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isSelected(T.DASHBOARD_TAB_INVESTMENTS) }) {
            return@runCheck bad("The Investment page is showing but its tab does not report itself as selected.")
        }
        ok("moved to Trading and back; the Investment page opened and its tab reports selected")
    }

    // Alerts ---------------------------------------------------------------------------------------------

    private var testAlertName: String? = null

    private fun alertsSteps(): AutomationRunResult? {
        val ids = listOf("alerts_page_opened", "alert_created", "alerts_search_checked", "alert_deleted")
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening Alerts")?.let { return it }
        openRoute(T.INVEST_TILE_ALERTS, T.ALERTS_SCREEN, "The Alerts page", "The Alerts tile is not on the Investment tab.")?.let { why ->
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1), "the Alerts page did not open")
            scrollToTop()
            return null
        }
        testAlertName = null

        runCheck(ids[0]) {
            val open = engine.tapAndConfirm(T.ALERTS_NEW_ALERT_LINK, listOf(T.ALERT_SCREEN), Timeouts.SCREEN_CHANGE)
            if (!open.ok) return@runCheck bad("+ New Alert did not open the create page: ${open.detail}${dashTagHint()}")
            val closed = closeWithBack(T.ALERT_SCREEN)
            if (!closed && !engine.cancelled) bad("The create page opened but did not close on Back.")
            else ok("the Alerts page opened and + New Alert opened the create page")
        }?.let { return it }

        runCheck(ids[1]) {
            val open = engine.tapAndConfirm(T.ALERTS_NEW_ALERT_LINK, listOf(T.ALERT_SCREEN), Timeouts.SCREEN_CHANGE)
            if (!open.ok) return@runCheck bad("+ New Alert did not open the create page: ${open.detail}")
            // The scrip comes from the app's own search screen.
            val toSearch = engine.tapAndConfirm(T.ALERT_SCRIP_FIELD, listOf(T.SEARCH_SCREEN), Timeouts.SCREEN_CHANGE, retryAfterMs = Timeouts.SCREEN_CHANGE)
            if (!toSearch.ok) {
                closeWithBack(T.ALERT_SCREEN)
                return@runCheck bad("The scrip field did not open the search screen: ${toSearch.detail}${dashTagHint()}")
            }
            val typed = engine.enterText(T.SEARCH_SCRIP_INPUT, "tcs")
            val results = typed.ok && engine.waitForAny(listOf(T.SEARCH_RESULTS_LIST), Timeouts.SEARCH_RESULTS) is WaitOutcome.Found
            if (!results) {
                backTo(T.ALERT_SCREEN, listOf(T.SEARCH_SCREEN))
                closeWithBack(T.ALERT_SCREEN)
                return@runCheck bad("Searching 'tcs' for the alert's scrip listed no result.")
            }
            engine.tap(T.SEARCH_RESULT_ROW)
            if (!engine.waitForGone(T.SEARCH_SCREEN, Timeouts.SCREEN_CHANGE)) {
                backTo(T.ALERT_SCREEN, listOf(T.SEARCH_SCREEN))
                closeWithBack(T.ALERT_SCREEN)
                return@runCheck bad("Choosing a search result did not return to the create page.")
            }
            val name = "AUTOCHECK${System.currentTimeMillis() % 100000}"
            val failures = mutableListOf<String>()
            if (!engine.enterText(T.ALERT_NAME_INPUT, name).ok) failures += "the Alert Name field did not take a value"
            if (!engine.enterText(T.ALERT_VALUE_INPUT, "9999999").ok) failures += "the value field did not take a value"
            if (failures.isNotEmpty()) {
                closeWithBack(T.ALERT_SCREEN)
                return@runCheck bad(failures.joinToString("; ") + ".")
            }
            if (!engine.isTappable(T.ALERT_CREATE_BUTTON)) {
                closeWithBack(T.ALERT_SCREEN)
                return@runCheck bad("The CREATE button is missing or disabled.${dashTagHint()}")
            }
            testAlertName = name
            engine.tap(T.ALERT_CREATE_BUTTON)
            val left = engine.waitForGone(T.ALERT_SCREEN, Timeouts.SCREEN_CHANGE)
            if (!left) {
                val texts = engine.screenTexts().take(8).joinToString(" | ")
                closeWithBack(T.ALERT_SCREEN)
                testAlertName = null
                return@runCheck bad("CREATE was tapped but the page stayed open (the app refused it?). Texts on screen: $texts")
            }
            val listed = engine.waitUntil(Timeouts.TRADION_TAB_LOAD) {
                engine.isPresent(T.ALERTS_SCREEN) && engine.ownLabelsOf(T.ALERT_ROW_NAME).any { it.equals(name, ignoreCase = true) }
            }
            if (listed) ok("the fields took a name and a value, CREATE made the alert '$name' and it is in the Alerts list")
            else bad("CREATE closed the page but the alert '$name' did not appear in the Alerts list.")
        }?.let { return it }

        runCheck(ids[2]) {
            val name = testAlertName ?: return@runCheck bad("No test alert was created, so there is nothing to search for.")
            if (!engine.isPresent(T.ALERTS_SEARCH_INPUT)) return@runCheck bad("The Alerts search field is not on screen.${dashTagHint()}")
            val term = name.take(9)
            val typed = engine.enterText(T.ALERTS_SEARCH_INPUT, term)
            if (!typed.ok) return@runCheck bad("The Alerts search field did not take '$term': ${typed.detail}")
            val found = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
                engine.ownLabelsOf(T.ALERT_ROW_NAME).any { it.equals(name, ignoreCase = true) }
            }
            engine.enterText(T.ALERTS_SEARCH_INPUT, "")
            engine.sleep(Timeouts.SETTLE * 2)
            if (found) ok("searching '$term' found the test alert") else bad("Searching '$term' did not list the test alert '$name'.")
        }?.let { return it }

        runCheck(ids[3]) {
            val name = testAlertName ?: return@runCheck nothingToCheck("no test alert was created")
            var index = engine.ownLabelsOf(T.ALERT_ROW_NAME).indexOfFirst { it.equals(name, ignoreCase = true) }
            if (index < 0) return@runCheck bad("The test alert '$name' is not in the list to delete. Remove any 'AUTOCHECK' alert by hand.")
            val row = engine.tapAt(T.ALERT_ROW, index)
            if (!row.ok || !engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(T.ALERT_DELETE_BUTTON) }) {
                return@runCheck bad("The test alert's sheet did not open with a DELETE button. Remove the 'AUTOCHECK' alert by hand.")
            }
            engine.tap(T.ALERT_DELETE_BUTTON)
            val gone = engine.waitUntil(Timeouts.TRADION_TAB_LOAD) {
                engine.isPresent(T.ALERTS_SCREEN) && engine.ownLabelsOf(T.ALERT_ROW_NAME).none { it.equals(name, ignoreCase = true) }
            }
            if (gone) {
                testAlertName = null
                ok("the test alert '$name' was deleted")
            } else {
                bad("DELETE was tapped but '$name' is still listed (or its confirmation dialog is open). Remove it by hand.")
            }
        }?.let { return it }

        if (!closeWithBack(T.ALERTS_SCREEN) && !engine.cancelled && step(ids[3]).status == StepStatus.PASSED) {
            failAndContinue(step(ids[3]), "The Alerts page did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // IPO ------------------------------------------------------------------------------------------------

    private fun ipoSteps(): AutomationRunResult? {
        val ids = listOf("ipo_page_checked", "ipo_details_checked")
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening IPO")?.let { return it }
        openRoute(T.INVEST_TILE_IPO, T.IPO_SCREEN, "The IPO page", "The IPO tile is not on the Investment tab.")?.let { why ->
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1), "the IPO page did not open")
            scrollToTop()
            return null
        }
        runCheck(ids[0]) {
            val (tabs, failures) = tapEachTab(T.IPO_TAB_PREFIX, 6)
            if (tabs == 0) return@runCheck bad("No IPO tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.${dashTagHint()}")
            if (failures.isNotEmpty()) return@runCheck bad(failures.joinToString("; ") + ".")
            // The first tab is Ongoing; its search box is only built when there is an IPO to search.
            if (!engine.isPresent(T.IPO_ROW)) return@runCheck ok("$tabs tab(s) tapped; no ongoing IPO is listed, so the search box was not exercised")
            val search = searchVerdict(T.IPO_SEARCH_INPUT, T.IPO_ROW, "IPO")
            if (search.failure != null) bad(search.failure) else ok("$tabs tab(s) tapped; ${search.note}")
        }?.let { return it }
        runCheck(ids[1]) {
            val rows = engine.countOf(T.IPO_ROW)
            if (rows == 0) return@runCheck nothingToCheck("no ongoing IPO is listed, so there are no details to open")
            var opened = false
            for (i in 0 until minOf(rows, 3)) {
                engine.tapAt(T.IPO_ROW, i)
                if (engine.waitForAny(listOf(T.IPO_DETAILS_SCREEN), Timeouts.SCREEN_CHANGE) is WaitOutcome.Found) {
                    opened = true
                    break
                }
            }
            if (!opened) return@runCheck bad("None of the first IPOs opened a details page (an applied IPO does not).${dashTagHint()}")
            engine.sleep(Timeouts.SETTLE * 2)
            val chips = engine.countOf(T.IPO_TOGGLE_CHIP)
            val failures = mutableListOf<String>()
            for (i in 0 until chips) {
                val tap = engine.tapAt(T.IPO_TOGGLE_CHIP, i)
                if (!tap.ok) failures += "button ${i + 1} could not be tapped (${tap.detail})"
                if (!engine.sleep(Timeouts.SETTLE)) break
            }
            val apply = engine.isTappable(T.IPO_APPLY_BUTTON)
            if (!closeWithBack(T.IPO_DETAILS_SCREEN) && !engine.cancelled) failures += "the details page did not close on Back"
            when {
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok("the details page opened; $chips button(s) tapped; the Apply button was ${if (apply) "available and not tapped" else "not available"}")
            }
        }?.let { return it }
        if (!closeWithBack(T.IPO_SCREEN) && !engine.cancelled && step(ids[1]).status != StepStatus.FAILED) {
            failAndContinue(step(ids[1]), "The IPO page did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // Vendor pages (Reports, Pledge, Back Office, Other request) -----------------------------------------

    private fun vendorTileStep(stepId: String, tile: String, what: String): AutomationRunResult? = runCheck(stepId) {
        scrollToTop()
        if (!revealDown(tile)) return@runCheck bad("The $what tile is not on the Investment tab.${dashTagHint()}")
        val tap = engine.tap(tile)
        if (!tap.ok) return@runCheck bad("The $what tile could not be tapped: ${tap.detail}")
        // The vendor sign-in is a network call, so give the page longer than a plain screen change.
        val opened = engine.waitUntil(30_000L) { engine.isPresent(T.WEB_VIEW_SCREEN) }
        if (!opened) {
            return@runCheck bad(
                "Tapping $what did not open its page within 30 seconds. Identifiers on screen: " +
                    "${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
            )
        }
        engine.sleep(Timeouts.SETTLE * 2)
        val closed = closeWithBack(T.WEB_VIEW_SCREEN)
        val home = engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(T.DASHBOARD_SCREEN) }
        when {
            !closed && !engine.cancelled -> bad("The $what page opened but did not close on Back.")
            !home -> bad("After leaving the $what page the dashboard did not come back.")
            else -> ok("the $what page opened (its content is a web page the tool cannot read) and Back returned to the dashboard")
        }
    }

    // ETF -------------------------------------------------------------------------------------------------

    private fun etfSteps(): AutomationRunResult? {
        val ids = listOf("etf_page_opened", "etf_tabs_options", "etf_tabs_buy_sell", "etf_search_settings")
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening ETF")?.let { return it }
        scrollToTop()
        val why = if (!revealDown(T.INVEST_TILE_ETF)) "The ETF tile is not on the Investment tab.${dashTagHint()}" else {
            val open = engine.tapAndConfirm(T.INVEST_TILE_ETF, listOf(T.ETF_SCREEN), 20_000L, retryAfterMs = 20_000L)
            if (open.ok) null else "The ETF page did not open: ${open.detail}"
        }
        if (why != null) {
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1), "the ETF page did not open")
            scrollToTop()
            return null
        }
        runCheck(ids[0]) {
            if (engine.waitUntil(Timeouts.TRADION_TAB_LOAD) { engine.isPresent(T.ETF_TAB_PREFIX + "0") }) ok("the ETF page opened with its tabs")
            else bad("The ETF page opened but showed no tabs.${dashTagHint()}")
        }?.let { return it }

        runCheck(ids[1]) {
            val targets = listOf(sheetChart, sheetAlert, sheetBasket, sheetMore)
            val (tabs, problems) = forEachTab(T.ETF_TAB_PREFIX, 10, T.ETF_ROW) {
                optionsFromFirstRow(T.ETF_ROW, T.ETF_SCREEN, targets).third
            }
            if (tabs == 0) bad("No ETF tabs were found.${dashTagHint()}")
            else if (problems.isNotEmpty()) bad(problems.joinToString("; ") + ".")
            else ok("$tabs tab(s): each opened one instrument's Chart, Alert, Basket and More Details (a box the sheet does not offer is skipped)")
        }?.let { return it }

        runCheck(ids[2]) {
            val (tabs, problems) = forEachTab(T.ETF_TAB_PREFIX, 10, T.ETF_ROW) {
                buySellFromFirstRow(T.ETF_ROW, T.ETF_SCREEN, orderPadOverlays).second
            }
            if (tabs == 0) bad("No ETF tabs were found.${dashTagHint()}")
            else if (problems.isNotEmpty()) bad(problems.joinToString("; ") + ".")
            else ok("$tabs tab(s): Buy and Sell each opened the order pad and it was closed with Back; nothing was placed")
        }?.let { return it }

        runCheck(ids[3]) {
            val tabIds = (0 until 10).map { "${T.ETF_TAB_PREFIX}$it" }
            if (engine.revealInList(tabIds[0], tabIds)) {
                engine.tap(tabIds[0])
                engine.sleep(Timeouts.SETTLE * 2)
                checkPageLoads(Timeouts.TRADION_TAB_LOAD, 1)
            }
            val search = searchVerdict(T.ETF_SEARCH_INPUT, T.ETF_ROW, "ETF")
            val (sorted, failures) = sortSheetCheck(T.ETF_FILTER_BUTTON, T.ETF_FILTER_SHEET, T.ETF_FILTER_CLEAR, "ETF", T.ETF_FILTER_EXCHANGE)
            val chips = lastChipsTapped
            val problems = failures.toMutableList()
            search.failure?.let { problems.add(0, it) }
            if (problems.isNotEmpty()) bad(problems.joinToString("; ") + ".")
            else ok("${search.note}; $chips exchange option(s) and $sorted sort option(s) tapped; Clear reset them and closed the sheet")
        }?.let { return it }

        if (!closeWithBack(T.ETF_SCREEN) && !engine.cancelled && step(ids[3]).status != StepStatus.FAILED) {
            failAndContinue(step(ids[3]), "The ETF page did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // Top Sectors and Customize ---------------------------------------------------------------------------

    private fun topSectorsStep(): AutomationRunResult? = runCheck("top_sectors_view_all_checked") {
        scrollToTop()
        if (!revealDown(T.DASHBOARD_TOP_SECTORS_VIEW_ALL)) {
            return@runCheck bad("The Top Sectors 'View All' is not on the Investment tab (the section may be switched off under Customize).${dashTagHint()}")
        }
        val open = engine.tapAndConfirm(T.DASHBOARD_TOP_SECTORS_VIEW_ALL, listOf(T.SECTORS_SCREEN), Timeouts.SCREEN_CHANGE)
        if (!open.ok) return@runCheck bad("Top Sectors 'View All' did not open: ${open.detail}")
        val (tabs, failures) = tapEachTab(T.SECTORS_TAB_PREFIX, 10)
        val closed = closeWithBack(T.SECTORS_SCREEN)
        scrollToTop()
        when {
            tabs == 0 -> bad("The sectors page showed no tabs.${dashTagHint()}")
            failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
            !closed && !engine.cancelled -> bad("The sectors page did not close on Back.")
            else -> ok("the sectors page opened and its $tabs tab(s) were each tapped")
        }
    }

    private fun investmentCustomizeStep(): AutomationRunResult? = runCheck("investment_customize_checked") {
        openRoute(
            T.INVEST_CUSTOMIZE_BUTTON, T.INVEST_CUSTOMIZE_SCREEN, "Customize",
            "The Customize button is not on the Investment tab.",
        )?.let { return@runCheck bad(it) }
        if (!engine.sleep(Timeouts.SETTLE * 2)) return@runCheck bad("cancelled")
        val closed = closeWithBack(T.INVEST_CUSTOMIZE_SCREEN)
        scrollToTop()
        if (closed) ok("the Customize screen opened and was left without a change")
        else bad("The Customize screen opened but did not close on Back (it may be asking to save changes; the tool never saves or discards).")
    }

    // Scanners: every tab of every segment ----------------------------------------------------------------

    private fun scannersDeepSteps(): AutomationRunResult? {
        val segments = listOf("equity" to "Equity", "futures" to "Futures", "options" to "Options", "commodity" to "Commodity")
        val budget = Timeouts.TRADION_TAB_LOAD
        fun selectSegment(i: Int): String? {
            val id = "${T.SCANNERS_SEGMENT_PREFIX}$i"
            if (!engine.isPresent(id)) return "the segment option is not on the Scanners screen"
            val tap = engine.tap(id)
            if (!tap.ok) return "the segment option could not be tapped (${tap.detail})"
            engine.sleep(Timeouts.SETTLE * 2)
            checkPageLoads(budget, 1)
            return null
        }
        for ((i, seg) in segments.withIndex()) {
            val (key, name) = seg
            val optionsId = "scanners_${key}_tab_options"
            val buySellId = "scanners_${key}_tab_buy_sell"
            val selectFailure = selectSegment(i)
            if (engine.cancelled) return stopped(step(optionsId))
            if (selectFailure != null) {
                runCheck(optionsId) { bad("$name: $selectFailure.") }?.let { return it }
                runCheck(buySellId) { bad("$name: $selectFailure.") }?.let { return it }
                if (key == "equity") runCheck("scanners_equity_search_settings") { bad("$name: $selectFailure.") }?.let { return it }
                continue
            }
            runCheck(optionsId) {
                val targets = listOf(sheetChart, sheetAlert, sheetOpChain, sheetMore)
                val notOffered = linkedSetOf<String>()
                val (tabs, problems) = forEachTab(T.SCANNERS_TAB_PREFIX, 10, T.SCANNER_ROW) {
                    val (_, missing, p) = optionsFromFirstRow(T.SCANNER_ROW, T.SCANNERS_SCREEN, targets)
                    notOffered += missing
                    p
                }
                when {
                    tabs == 0 -> bad("$name: no Scanners tabs were found.${dashTagHint()}")
                    problems.isNotEmpty() -> bad("$name: " + problems.joinToString("; ") + ".")
                    else -> ok(
                        "$name: $tabs tab(s), each opened one instrument's Chart, Alert, Op.Chain and More Details" +
                            if (notOffered.isEmpty()) "" else " (not offered on some: ${notOffered.joinToString(", ")})",
                    )
                }
            }?.let { return it }
            runCheck(buySellId) {
                val (tabs, problems) = forEachTab(T.SCANNERS_TAB_PREFIX, 10, T.SCANNER_ROW) {
                    buySellFromFirstRow(T.SCANNER_ROW, T.SCANNERS_SCREEN, orderPadOverlays).second
                }
                when {
                    tabs == 0 -> bad("$name: no Scanners tabs were found.${dashTagHint()}")
                    problems.isNotEmpty() -> bad("$name: " + problems.joinToString("; ") + ".")
                    else -> ok("$name: $tabs tab(s), Buy and Sell each opened the order pad and it was closed with Back; nothing was placed")
                }
            }?.let { return it }
            if (key == "equity") {
                runCheck("scanners_equity_search_settings") {
                    val tabIds = (0 until 10).map { "${T.SCANNERS_TAB_PREFIX}$it" }
                    if (engine.revealInList(tabIds[0], tabIds)) {
                        engine.tap(tabIds[0])
                        engine.sleep(Timeouts.SETTLE * 2)
                        checkPageLoads(budget, 1)
                    }
                    val search = searchVerdict(T.SCANNERS_SEARCH_INPUT, T.SCANNER_ROW, "Scanners")
                    val (sorted, failures) = sortSheetCheck(
                        T.SCANNERS_FILTER_BUTTON, T.SCANNERS_FILTER_SHEET, T.SCANNERS_FILTER_CLOSE, "Scanners", T.SCANNERS_FILTER_EXCHANGE,
                    )
                    val chips = lastChipsTapped
                    val problems = failures.toMutableList()
                    search.failure?.let { problems.add(0, it) }
                    if (problems.isNotEmpty()) bad(problems.joinToString("; ") + ".")
                    else ok("${search.note}; $chips exchange option(s) and $sorted sort option(s) tapped; Close reset them and closed the sheet")
                }?.let { return it }
            }
        }
        // Leave on Equity, the screen's own default.
        if (engine.isPresent("${T.SCANNERS_SEGMENT_PREFIX}0")) engine.tap("${T.SCANNERS_SEGMENT_PREFIX}0")
        return null
    }

    // ── Dashboard scenarios ───────────────────────────────────────────────────────────────────
    //
    // Each check below is one report step. None of them places, modifies or cancels anything:
    // Buy and Sell are only tapped to open the order pad, which is left with Back; Filter and sort
    // controls are tapped and then reset with the sheet's own Clear / Close; the Customize screen
    // is opened and left without a change.

    /** What one check found: [failure] null means it passed (with [note]); [skipNote] means nothing to check. */
    private class Verdict(val failure: String?, val note: String? = null, val skipNote: String? = null)

    private fun ok(note: String? = null) = Verdict(null, note)
    private fun bad(reason: String) = Verdict(reason)
    private fun nothingToCheck(why: String) = Verdict(null, skipNote = why)

    private fun dashTagHint(): String =
        " If no identifier for it is listed, the installed Tradion build predates the Dashboard " +
            "identifiers; build it with the current tradion_mobile sources."

    /** Runs [body] as the step [stepId] and records its [Verdict]; a session that ended mid-check ends the run. */
    private fun runCheck(stepId: String, body: () -> Verdict): AutomationRunResult? {
        val current = step(stepId)
        if (current.status == StepStatus.PENDING) begin(current)
        sessionEnded(current, "checking ${current.name}")?.let { return it }
        val verdict = body()
        if (engine.cancelled) return stopped(current)
        sessionEnded(current, "checking ${current.name}")?.let { return it }
        val failure = verdict.failure
        val skipNote = verdict.skipNote
        when {
            failure != null -> failAndContinue(current, failure)
            skipNote != null -> skip(current, skipNote)
            else -> pass(current, verdict.note)
        }
        return null
    }

    /** Scrolls to the top and opens the pushed route behind [openId]. Null on success, else why not. */
    private fun openRoute(openId: String, screenId: String, what: String, missing: String): String? {
        scrollToTop()
        if (!revealDown(openId)) return missing + dashTagHint()
        val open = engine.tapAndConfirm(openId, listOf(screenId), Timeouts.SCREEN_CHANGE)
        return if (open.ok) null else "$what did not open: ${open.detail}"
    }

    /**
     * Presses Back out of whichever of [overlays] is on screen until [home] shows again. Back is only
     * ever pressed for an overlay whose own marker was just seen (see [closeWithBack]).
     */
    private fun backTo(home: String, overlays: List<String>): Boolean {
        repeat(3) {
            if (engine.isPresent(home)) return true
            val overlay = engine.firstPresent(overlays)
                ?: return engine.waitUntil(Timeouts.SETTLE * 4) { engine.isPresent(home) }
            closeWithBack(overlay)
            if (engine.cancelled) return false
        }
        return engine.isPresent(home)
    }

    /**
     * Taps each tab `<prefix>0`, `<prefix>1`, ... that exists (up to [maxTabs]) and checks the screen does
     * not turn into the retry screen. Ends back on the first tab. Returns how many tabs were seen and the failures.
     */
    private fun tapEachTab(prefix: String, maxTabs: Int): Pair<Int, List<String>> {
        val all = (0 until maxTabs).map { "$prefix$it" }
        val failures = mutableListOf<String>()
        var seen = 0
        for ((i, id) in all.withIndex()) {
            if (!engine.revealInList(id, all)) break
            seen++
            val tap = engine.tap(id)
            if (!tap.ok) {
                failures += "tab ${i + 1} could not be tapped (${tap.detail})"
                continue
            }
            if (!engine.sleep(Timeouts.SETTLE * 2)) break
            if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0) failures += "tab ${i + 1} shows the 'Something went wrong / Retry' screen"
        }
        if (seen > 1 && engine.revealInList(all[0], all)) {
            engine.tap(all[0])
            engine.sleep(Timeouts.SETTLE * 2)
        }
        return seen to failures
    }

    /**
     * Opens a sort sheet through [buttonId], taps each of its sort rows, then taps [resetId] (Clear / Close,
     * which restores the default order and closes the sheet). Returns the number of rows tapped and the failures.
     */
    private var lastChipsTapped = 0

    private fun sortSheetCheck(
        buttonId: String, sheetId: String, resetId: String, what: String, chipId: String? = null,
    ): Pair<Int, List<String>> {
        lastChipsTapped = 0
        val open = engine.tapAndConfirm(buttonId, listOf(sheetId), Timeouts.SCREEN_CHANGE)
        if (!open.ok) return 0 to listOf("The $what sort icon did not open its sheet: ${open.detail}${dashTagHint()}")
        val failures = mutableListOf<String>()
        var tapped = 0
        if (chipId != null) {
            val chips = engine.countOf(chipId)
            if (chips == 0) failures += "the sheet offered no exchange (NSE / BSE) options${dashTagHint()}"
            for (i in 0 until chips) {
                val tap = engine.tapAt(chipId, i)
                if (tap.ok) lastChipsTapped++ else failures += "exchange option ${i + 1} could not be tapped (${tap.detail})"
                if (!engine.sleep(Timeouts.SETTLE)) break
            }
        }
        for (option in T.SORT_OPTIONS) {
            val name = option.removePrefix("sort_option_").replace('_', ' ')
            if (!engine.isPresent(sheetId)) {
                val again = engine.tapAndConfirm(buttonId, listOf(sheetId), Timeouts.SCREEN_CHANGE)
                if (!again.ok) {
                    failures += "the sheet closed after a sort row and could not be reopened"
                    break
                }
            }
            if (!engine.isPresent(option)) {
                failures += "the '$name' sort row is not on the sheet"
                continue
            }
            val tap = engine.tap(option)
            if (!tap.ok) failures += "the '$name' sort row could not be tapped (${tap.detail})" else tapped++
            if (!engine.sleep(Timeouts.SETTLE * 2)) break
        }
        if (!engine.isPresent(sheetId)) engine.tapAndConfirm(buttonId, listOf(sheetId), Timeouts.SCREEN_CHANGE)
        val reset = engine.tap(resetId)
        if (!reset.ok) {
            failures += "the sheet's reset control could not be tapped (${reset.detail})"
            closeWithBack(sheetId)
        } else if (!engine.waitForGone(sheetId, Timeouts.SCREEN_CHANGE)) {
            failures += "the sheet did not close after its reset control was tapped"
            closeWithBack(sheetId)
        }
        return tapped to failures
    }

    /**
     * For the first [rowId] row of the list on [home]: opens its sheet, taps Buy then Sell, expects the order pad
     * each time and leaves it with Back. Nothing on the pad is touched. Returns how many pads opened and the failures.
     */
    private fun buySellFromFirstRow(rowId: String, home: String, overlays: List<String>): Pair<Int, List<String>> {
        val failures = mutableListOf<String>()
        var opened = 0
        for ((button, label) in listOf(T.TRADE_BUY_BUTTON to "Buy", T.TRADE_SELL_BUTTON to "Sell")) {
            if (!backTo(home, overlays)) {
                failures += "could not get back to the list before $label"
                break
            }
            if (!engine.isPresent(rowId)) {
                failures += "$label: no scrip row is on screen"
                break
            }
            val row = engine.tap(rowId)
            if (!row.ok) {
                failures += "$label: the first scrip could not be tapped (${row.detail})"
                continue
            }
            if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(button) }) {
                failures += "$label: the scrip's sheet never showed '$button'.${dashTagHint()}"
                continue
            }
            // One tap only: the sheet closes itself, so a retry could only ever hit a second pad.
            val pad = engine.tapAndConfirm(
                button, listOf(T.ORDER_WINDOW_SCREEN), Timeouts.SCREEN_CHANGE, retryAfterMs = Timeouts.SCREEN_CHANGE,
            )
            if (!pad.ok) {
                failures += "$label did not open the order pad: ${pad.detail}"
                continue
            }
            opened++
            if (!engine.sleep(Timeouts.SETTLE)) break
            if (!closeWithBack(T.ORDER_WINDOW_SCREEN) && !engine.cancelled) failures += "the order pad did not close on Back after $label"
        }
        backTo(home, overlays)
        return opened to failures
    }

    private val orderPadOverlays = listOf(
        T.ORDER_WINDOW_SCREEN, T.CHART_SCREEN, T.MORE_INFO_SCREEN, T.OPTION_CHAIN_SCREEN,
        T.SCRIP_SHEET_CHART_BUTTON, T.SCRIP_SHEET_OPCHAIN_BUTTON, T.SCRIP_SHEET_MORE_BUTTON,
        T.TRADE_BUY_BUTTON, T.SCANNERS_FILTER_SHEET, T.FUTURES_FILTER_SHEET, T.ETF_FILTER_SHEET,
        T.ALERT_SCREEN, T.BASKET_SELECT_SCREEN, T.SCRIP_SHEET_ALERT_BUTTON, T.SCRIP_SHEET_BASKET_BUTTON,
    )

    private fun clip(text: String, max: Int = 40): String = if (text.length <= max) text else text.take(max) + "…"

    // 1 — the index dropdown -----------------------------------------------------------------------

    private fun indexDropdownStep(): AutomationRunResult? = runCheck("dashboard_index_dropdown_checked") {
        scrollToTop()
        val open = engine.tap(T.DASHBOARD_INDEX_DROPDOWN)
        if (!open.ok) return@runCheck bad("The index dropdown arrow could not be tapped: ${open.detail}${dashTagHint()}")
        val shown = engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.countOf(T.DASHBOARD_INDEX_ITEM) > 0 }
        if (!shown) {
            return@runCheck bad(
                "The arrow was tapped but no index was listed within ${Timeouts.SCREEN_CHANGE / 1000} seconds. " +
                    "Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.",
            )
        }
        val names = engine.groupedLabelsOf(T.DASHBOARD_INDEX_ITEM)
        val close = engine.tap(T.DASHBOARD_INDEX_CLOSE)
        if (!close.ok) return@runCheck bad("The index list opened but its close button could not be tapped: ${close.detail}")
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.countOf(T.DASHBOARD_INDEX_ITEM) == 0 }) {
            return@runCheck bad("The index list did not close after its close button was tapped.")
        }
        ok("${names.size} index(es) listed (${names.take(3).joinToString(" | ") { clip(it) }}), then closed")
    }

    // 2 — the Trading tab ---------------------------------------------------------------------------

    private fun tradingTabSelectedStep(): AutomationRunResult? = runCheck("dashboard_trading_tab_selected") {
        scrollToTop()
        // Move the selection away first, so "selected" has to be earned by tapping Trading.
        val toInvestments = engine.tap(T.DASHBOARD_TAB_INVESTMENTS)
        if (!toInvestments.ok) return@runCheck bad("The Investments tab could not be tapped to move the selection: ${toInvestments.detail}")
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(T.DASHBOARD_INVESTMENTS_VIEW) }) {
            return@runCheck bad("Tapping Investments did not open the Investments page.")
        }
        val tap = engine.tap(T.DASHBOARD_TAB_TRADING)
        if (!tap.ok) return@runCheck bad("The Trading tab could not be tapped: ${tap.detail}")
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isPresent(T.DASHBOARD_TRADING_VIEW) }) {
            return@runCheck bad("Tapping Trading did not open the Trading page.")
        }
        if (!engine.waitUntil(Timeouts.SCREEN_CHANGE) { engine.isSelected(T.DASHBOARD_TAB_TRADING) }) {
            return@runCheck bad("The Trading page is showing but its tab does not report itself as selected.")
        }
        ok("moved to Investments and back; the Trading page opened and its tab reports selected")
    }

    // 3–6 — Trading Ideas (the Research calls screen) -----------------------------------------------

    private fun tradingIdeasSteps(): AutomationRunResult? {
        val ids = listOf(
            "trading_ideas_tabs_clickable", "trading_ideas_subtabs_search",
            "trading_ideas_menu_opened", "trading_ideas_filter_checked",
        )
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening Trading Ideas")?.let { return it }
        openRoute(
            T.DASHBOARD_OPEN_RESEARCH_CALLS, T.RESEARCH_CALL_SCREEN, "Trading Ideas",
            "The Research tile is not on the Trading tab.",
        )?.let { why ->
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1), "Trading Ideas did not open")
            scrollToTop()
            return null
        }

        runCheck(ids[0]) {
            val (tabs, failures) = tapEachTab(T.RESEARCH_TAB_PREFIX, 12)
            when {
                tabs == 0 -> bad("No Trading Ideas tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok("$tabs tab(s) each tapped and showed no error")
            }
        }?.let { return it }

        runCheck(ids[1]) {
            val prefix = T.RESEARCH_SUBTAB_PREFIX
            val (subs, failures) = tapEachTab(prefix, 6)
            if (subs == 0) return@runCheck bad("No sub tabs (Intraday / Positional) were found on the first Trading Ideas tab.")
            if (failures.isNotEmpty()) return@runCheck bad(failures.joinToString("; ") + ".")
            val names = (0 until subs).mapNotNull { engine.labelsOf("$prefix$it").firstOrNull() }.joinToString(", ")
            // The search box is only built for a sub tab holding more than one call.
            var searchable = engine.isPresent(T.RESEARCH_SEARCH_INPUT)
            if (!searchable) {
                for (i in 0 until subs) {
                    val id = "$prefix$i"
                    if (!engine.revealInList(id, (0 until subs).map { "$prefix$it" })) continue
                    engine.tap(id)
                    engine.sleep(Timeouts.SETTLE * 2)
                    if (engine.isPresent(T.RESEARCH_SEARCH_INPUT)) {
                        searchable = true
                        break
                    }
                }
            }
            if (!searchable) {
                return@runCheck ok("$subs sub tab(s) tapped ($names); the search box is built only for a sub tab with more than one call and none has, so no scrip was searched")
            }
            val name = engine.ownLabelsOf(T.RESEARCH_CARD_NAME).firstOrNull()?.trim()
            if (name.isNullOrEmpty()) return@runCheck bad("The search box is shown but no call is listed to search for.${dashTagHint()}")
            val term = name.take(3)
            val typed = engine.enterText(T.RESEARCH_SEARCH_INPUT, term)
            if (!typed.ok) return@runCheck bad("The search box did not take '$term': ${typed.detail}")
            val narrowed = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
                val shown = engine.ownLabelsOf(T.RESEARCH_CARD_NAME)
                shown.isNotEmpty() && shown.all { it.startsWith(term, ignoreCase = true) }
            }
            val shownNow = engine.ownLabelsOf(T.RESEARCH_CARD_NAME).take(3).joinToString(" | ") { clip(it) }
            engine.enterText(T.RESEARCH_SEARCH_INPUT, "")
            engine.sleep(Timeouts.SETTLE * 2)
            if (!narrowed) return@runCheck bad("Searching '$term' did not narrow the calls to scrips starting with it (showing: ${shownNow.ifEmpty { "none" }}).")
            ok("$subs sub tab(s) tapped ($names); searching '$term' found: $shownNow")
        }?.let { return it }

        runCheck(ids[2]) {
            if (!engine.isPresent(T.RESEARCH_FILTER_BUTTON)) {
                return@runCheck bad("The top-right menu icon is not on the Trading Ideas page (it is hidden on the two basket tabs).${dashTagHint()}")
            }
            val open = engine.tapAndConfirm(T.RESEARCH_FILTER_BUTTON, listOf(T.RESEARCH_FILTER_SHEET), Timeouts.SCREEN_CHANGE)
            if (open.ok) ok("the menu opened the Filter By sheet") else bad("The menu icon did not open its sheet: ${open.detail}")
        }?.let { return it }

        runCheck(ids[3]) {
            if (!engine.isPresent(T.RESEARCH_FILTER_SHEET)) {
                val again = engine.tapAndConfirm(T.RESEARCH_FILTER_BUTTON, listOf(T.RESEARCH_FILTER_SHEET), Timeouts.SCREEN_CHANGE)
                if (!again.ok) return@runCheck bad("The Filter By sheet is not open: ${again.detail}")
            }
            val groups = listOf(
                "Status" to T.RESEARCH_FILTER_STATUS,
                "Exchange" to T.RESEARCH_FILTER_EXCHANGE,
                "Returns" to T.RESEARCH_FILTER_RETURNS,
            )
            val ids3 = groups.map { it.second } + T.RESEARCH_FILTER_CLEAR
            val tapped = mutableListOf<String>()
            val absent = mutableListOf<String>()
            val failures = mutableListOf<String>()
            for ((label, id) in groups) {
                if (!engine.revealInList(id, ids3)) {
                    absent += label
                    continue
                }
                val options = engine.countOf(id)
                val tap = engine.tap(id)
                if (tap.ok) tapped += "$label ($options option(s))" else failures += "a $label option could not be tapped (${tap.detail})"
                if (!engine.sleep(Timeouts.SETTLE * 2)) break
                if (!engine.isPresent(T.RESEARCH_FILTER_SHEET)) {
                    val again = engine.tapAndConfirm(T.RESEARCH_FILTER_BUTTON, listOf(T.RESEARCH_FILTER_SHEET), Timeouts.SCREEN_CHANGE)
                    if (!again.ok) {
                        failures += "the sheet closed after a $label option and could not be reopened"
                        break
                    }
                }
            }
            if (tapped.isEmpty() && failures.isEmpty()) {
                closeWithBack(T.RESEARCH_FILTER_SHEET)
                return@runCheck bad("The Filter By sheet offered no Status, Exchange or Returns options.${dashTagHint()}")
            }
            if (engine.isPresent(T.RESEARCH_FILTER_SHEET)) {
                engine.revealInList(T.RESEARCH_FILTER_CLEAR, ids3)
                val clear = engine.tap(T.RESEARCH_FILTER_CLEAR)
                if (!clear.ok) {
                    failures += "Clear could not be tapped (${clear.detail})"
                    closeWithBack(T.RESEARCH_FILTER_SHEET)
                } else if (!engine.waitForGone(T.RESEARCH_FILTER_SHEET, Timeouts.SCREEN_CHANGE)) {
                    failures += "the sheet did not close after Clear"
                    closeWithBack(T.RESEARCH_FILTER_SHEET)
                }
            }
            when {
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok(
                    "tapped the first option of ${tapped.joinToString(", ")}; Clear reset them and closed the sheet" +
                        if (absent.isEmpty()) "" else "; not offered: ${absent.joinToString(", ")}",
                )
            }
        }?.let { return it }

        if (engine.isPresent(T.RESEARCH_FILTER_SHEET)) closeWithBack(T.RESEARCH_FILTER_SHEET)
        if (!closeWithBack(T.RESEARCH_CALL_SCREEN) && !engine.cancelled && step(ids[3]).status == StepStatus.PASSED) {
            failAndContinue(step(ids[3]), "Trading Ideas opened but did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // 12–13 — Futures › View All -------------------------------------------------------------------

    private fun futuresSteps(): AutomationRunResult? {
        val ids = listOf("futures_view_all_checked", "futures_scrip_buy_sell_checked")
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening Futures View All")?.let { return it }
        openRoute(
            T.DASHBOARD_FUTURES_VIEW_ALL, T.FUTURES_SCREEN, "Futures View All",
            "The Futures card's View All is not on the Trading tab (the card appears only when futures data loads " +
                "and the section is switched on under Customize).",
        )?.let { why ->
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1), "Futures View All did not open")
            scrollToTop()
            return null
        }

        runCheck(ids[0]) {
            val (tabs, failures) = tapEachTab(T.FUTURES_TAB_PREFIX, 8)
            if (tabs == 0) return@runCheck bad("No Futures tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.${dashTagHint()}")
            val problems = failures.toMutableList()
            val (sorted, sortFailures) = sortSheetCheck(T.FUTURES_FILTER_BUTTON, T.FUTURES_FILTER_SHEET, T.FUTURES_FILTER_CLEAR, "Futures")
            problems += sortFailures
            if (problems.isNotEmpty()) bad(problems.joinToString("; ") + ".")
            else ok("$tabs tab(s) tapped; $sorted sort option(s) tapped; Clear reset the sort and closed the sheet")
        }?.let { return it }

        runCheck(ids[1]) {
            val (opened, failures) = buySellFromFirstRow(T.FUTURES_ROW, T.FUTURES_SCREEN, orderPadOverlays)
            when {
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok("Buy and Sell each opened the order pad ($opened of 2) and it was closed with Back; nothing was placed")
            }
        }?.let { return it }

        if (!closeWithBack(T.FUTURES_SCREEN) && !engine.cancelled && step(ids[1]).status == StepStatus.PASSED) {
            failAndContinue(step(ids[1]), "The Futures screen did not close on Back.")
        }
        scrollToTop()
        return null
    }

    // 14 — FII / DII ----------------------------------------------------------------------------------

    private fun fiiDiiStep(): AutomationRunResult? = runCheck("fii_dii_view_all_checked") {
        openRoute(
            T.DASHBOARD_FII_DII_VIEW_ALL, T.FII_DII_SCREEN, "FII / DII View All",
            "The FII / DII card's View All is not on the Trading tab (the card appears only when the section is " +
                "switched on under Customize).",
        )?.let { return@runCheck bad(it) }
        val rows = engine.waitUntil(Timeouts.TRADION_TAB_LOAD) { engine.countOf(T.FII_DII_ROW) > 0 }
        val count = engine.countOf(T.FII_DII_ROW)
        val closed = closeWithBack(T.FII_DII_SCREEN)
        scrollToTop()
        when {
            !rows -> bad("The FII / DII screen opened but showed no data within ${Timeouts.TRADION_TAB_LOAD / 1000} seconds.")
            !closed && !engine.cancelled -> bad("The FII / DII screen showed $count row(s) but did not close on Back.")
            else -> ok("the FII / DII screen opened with $count row(s) of data")
        }
    }

    // 15 — Customize ----------------------------------------------------------------------------------

    private fun customizeStep(): AutomationRunResult? = runCheck("dashboard_customize_checked") {
        openRoute(
            T.DASHBOARD_CUSTOMIZE_BUTTON, T.DASHBOARD_CUSTOMIZE_SCREEN, "Customize",
            "The Customize button is not on the Trading tab.",
        )?.let { return@runCheck bad(it) }
        if (!engine.sleep(Timeouts.SETTLE * 2)) return@runCheck bad("cancelled")
        // Nothing is changed, so Back leaves without the app's "Do you want to save changes?" dialog.
        val closed = closeWithBack(T.DASHBOARD_CUSTOMIZE_SCREEN)
        scrollToTop()
        if (closed) ok("the Customize screen opened and was left without a change")
        else bad("The Customize screen opened but did not close on Back (it may be asking to save changes; the tool never saves or discards).")
    }

    // 7–11 — Scanners (the "Screener" page) --------------------------------------------------------------

    private fun screenerSteps(): AutomationRunResult? {
        val ids = listOf(
            "screener_tabs_checked", "screener_dropdown_checked", "screener_search_checked",
            "screener_sort_checked", "screener_scrip_checked",
        )
        val budget = Timeouts.TRADION_TAB_LOAD
        val first = step(ids[0])
        begin(first)
        sessionEnded(first, "opening Scanners")?.let { return it }
        openRoute(
            T.DASHBOARD_SCANNERS_VIEW_ALL, T.SCANNERS_SCREEN, "Scanners",
            "The Scanners 'View All' link is not on the Investments tab.",
        )?.let { why ->
            if (engine.cancelled) return stopped(first)
            failAndContinue(first, why)
            skipSteps(ids.drop(1) + scannerDeepIds, "Scanners did not open")
            scrollToTop()
            return null
        }
        val tabIds = (0 until 8).map { "${T.SCANNERS_TAB_PREFIX}$it" }
        fun firstTab() {
            if (engine.revealInList(tabIds[0], tabIds)) {
                engine.tap(tabIds[0])
                engine.sleep(Timeouts.SETTLE * 2)
                checkPageLoads(budget, 1)
            }
        }

        runCheck(ids[0]) {
            val (tabs, failures) = walkTabs(T.SCANNERS_TAB_PREFIX, budget) { i ->
                if (engine.countOf(T.SCANNER_ROW) == 0) listOf("tab ${i + 1} shows no data${dashTagHint()}") else emptyList()
            }
            firstTab()
            when {
                tabs == 0 -> bad("No Scanners tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok("$tabs tab(s) each tapped, finished loading and listed scrips")
            }
        }?.let { return it }

        runCheck(ids[1]) {
            val names = listOf("Equity", "Futures", "Options", "Commodity")
            val failures = mutableListOf<String>()
            var seen = 0
            for ((i, name) in names.withIndex()) {
                val id = "${T.SCANNERS_SEGMENT_PREFIX}$i"
                if (!engine.isPresent(id)) {
                    failures += "the $name option is not on screen"
                    continue
                }
                seen++
                val tap = engine.tap(id)
                if (!tap.ok) {
                    failures += "the $name option could not be tapped (${tap.detail})"
                    continue
                }
                if (!engine.sleep(Timeouts.SETTLE * 2)) break
                loadFailure(checkPageLoads(budget, 1), budget, name)?.let { failures += it }
            }
            // Back to Equity, so the next checks run on the same list as the first.
            if (engine.isPresent("${T.SCANNERS_SEGMENT_PREFIX}0")) {
                engine.tap("${T.SCANNERS_SEGMENT_PREFIX}0")
                engine.sleep(Timeouts.SETTLE * 2)
                checkPageLoads(budget, 1)
            }
            when {
                seen == 0 -> bad("The segment dropdown is not on the Scanners screen.${dashTagHint()}")
                failures.isNotEmpty() -> bad(failures.joinToString("; ") + ".")
                else -> ok("$seen option(s) shown (${names.take(seen).joinToString(", ")}), each tapped and loaded")
            }
        }?.let { return it }

        runCheck(ids[2]) {
            firstTab()
            if (!engine.isPresent(T.SCANNERS_SEARCH_INPUT)) {
                return@runCheck bad("The search field is not on the Scanners screen (it is built once the tab has data).${dashTagHint()}")
            }
            val name = engine.ownLabelsOf(T.SCANNER_ROW_NAME).firstOrNull()?.trim()
            if (name.isNullOrEmpty()) return@runCheck bad("No scrip is listed to search for.${dashTagHint()}")
            val term = name.take(3)
            val typed = engine.enterText(T.SCANNERS_SEARCH_INPUT, term)
            if (!typed.ok) return@runCheck bad("The search field did not take '$term': ${typed.detail}")
            val narrowed = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
                val shown = engine.ownLabelsOf(T.SCANNER_ROW_NAME)
                shown.isNotEmpty() && shown.all { it.startsWith(term, ignoreCase = true) }
            }
            val shownNow = engine.ownLabelsOf(T.SCANNER_ROW_NAME).take(3).joinToString(" | ") { clip(it) }
            engine.enterText(T.SCANNERS_SEARCH_INPUT, "")
            engine.sleep(Timeouts.SETTLE * 2)
            if (narrowed) ok("searching '$term' listed: $shownNow")
            else bad("Searching '$term' did not narrow the list to scrips starting with it (showing: ${shownNow.ifEmpty { "none" }}).")
        }?.let { return it }

        runCheck(ids[3]) {
            val before = engine.ownLabelsOf(T.SCANNER_ROW_NAME).firstOrNull()
            val (tapped, failures) = sortSheetCheck(T.SCANNERS_FILTER_BUTTON, T.SCANNERS_FILTER_SHEET, T.SCANNERS_FILTER_CLOSE, "Scanners")
            engine.sleep(Timeouts.SETTLE * 2)
            val after = engine.ownLabelsOf(T.SCANNER_ROW_NAME).firstOrNull()
            if (failures.isNotEmpty()) return@runCheck bad(failures.joinToString("; ") + ".")
            val order = if (before == after) "the first scrip is back to ${before?.let { clip(it) }}"
            else "the first scrip is ${after?.let { clip(it) }} (was ${before?.let { clip(it) }}; live prices can reorder a list)"
            ok("$tapped sort option(s) tapped; the sheet's Close (its reset control) closed it and $order")
        }?.let { return it }

        runCheck(ids[4]) {
            val problems = mutableListOf<String>()
            val opened = mutableListOf<String>()
            val notOffered = mutableListOf<String>()
            val targets = listOf(
                Triple(T.SCRIP_SHEET_CHART_BUTTON, T.CHART_SCREEN, "Chart"),
                Triple(T.SCRIP_SHEET_OPCHAIN_BUTTON, T.OPTION_CHAIN_SCREEN, "Op.Chain"),
                Triple(T.SCRIP_SHEET_MORE_BUTTON, T.MORE_INFO_SCREEN, "More Details"),
            )
            val boxes = targets.map { it.first }
            for ((button, screen, label) in targets) {
                if (!backTo(T.SCANNERS_SCREEN, orderPadOverlays)) {
                    problems += "could not get back to the list before $label"
                    break
                }
                if (!engine.isPresent(T.SCANNER_ROW)) {
                    problems += "$label: no scrip row is on screen"
                    break
                }
                val row = engine.tap(T.SCANNER_ROW)
                if (!row.ok) {
                    problems += "$label: the first scrip could not be tapped (${row.detail})"
                    continue
                }
                if (engine.waitForAny(boxes, Timeouts.SCREEN_CHANGE) !is WaitOutcome.Found) {
                    problems += "$label: the scrip's sheet showed none of Chart / Op.Chain / More Details.${dashTagHint()}"
                    continue
                }
                if (!engine.isPresent(button)) {
                    notOffered += label
                    continue
                }
                val open = engine.tapAndConfirm(button, listOf(screen), Timeouts.SCREEN_CHANGE, retryAfterMs = Timeouts.SCREEN_CHANGE)
                if (!open.ok) {
                    problems += "$label did not open: ${open.detail}"
                    continue
                }
                opened += label
                if (!engine.sleep(Timeouts.SETTLE)) break
                if (!closeWithBack(screen) && !engine.cancelled) problems += "$label did not close on Back"
            }
            if (opened.isEmpty() && problems.isEmpty()) {
                problems += "none of Chart / Op.Chain / More Details is offered for the first scrip"
            }
            val (pads, padFailures) = buySellFromFirstRow(T.SCANNER_ROW, T.SCANNERS_SCREEN, orderPadOverlays)
            problems += padFailures
            when {
                problems.isNotEmpty() -> bad(problems.joinToString("; ") + ".")
                else -> ok(
                    "${opened.joinToString(", ")} opened and closed; Buy and Sell opened the order pad ($pads of 2) and " +
                        "it was closed with Back; nothing was placed" +
                        if (notOffered.isEmpty()) "" else "; not offered for this scrip: ${notOffered.joinToString(", ")}",
                )
            }
        }?.let { return it }

        backTo(T.SCANNERS_SCREEN, orderPadOverlays)
        scannersDeepSteps()?.let { return it }
        backTo(T.SCANNERS_SCREEN, orderPadOverlays)
        if (!closeWithBack(T.SCANNERS_SCREEN) && !engine.cancelled && step(ids[4]).status == StepStatus.PASSED) {
            failAndContinue(step(ids[4]), "The Scanners screen did not close on Back.")
        }
        scrollToTop()
        return null
    }

    /** Scrolls the page down until [id] is built, at most a few screenfuls; scrolls back up afterwards only if it moved. */
    private fun revealDown(id: String): Boolean {
        if (engine.isPresent(id)) return true
        var scrolls = 0
        while (scrolls < 8 && !engine.isPresent(id) && engine.scrollPage(forward = true)) {
            scrolls++
            if (!engine.sleep(Timeouts.SETTLE)) return false
        }
        return engine.isPresent(id)
    }

    /**
     * Taps each of [toggles] in order (e.g. Day P&L, then Net P&L to restore)
     * and checks the screen is still healthy after every tap. Skipped when the
     * switch is not shown, which it is not for an empty list.
     */
    private fun switchStep(
        stepId: String,
        viewId: String,
        toggles: List<String>,
        label: String,
        skipReason: String,
    ): AutomationRunResult? {
        val current = step(stepId)
        begin(current)
        if (toggles.none(engine::isPresent)) {
            skip(current, skipReason)
            return null
        }
        for (toggle in toggles) {
            val tap = engine.tap(toggle)
            if (!tap.ok) {
                if (engine.cancelled) return stopped(current)
                failAndContinue(current, "Could not tap '$toggle' ($label): ${tap.detail}")
                return null
            }
            if (!engine.sleep(Timeouts.SETTLE * 2)) return stopped(current)
            if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0 || !engine.isPresent(viewId)) {
                failAndContinue(current, "After tapping '$toggle' the screen shows an error or lost '$viewId'.")
                return null
            }
        }
        pass(current, "$label switched both ways and the screen stayed healthy")
        return null
    }

    /**
     * Flips the Stock / MTF switch and back. It is one toggle, so the second
     * tap is what restores the account's own setting; a failure of the first
     * half still attempts the restore.
     */
    private fun holdingsSwitchStep(budgetMs: Long): AutomationRunResult? {
        val current = step("holdings_switch_toggled")
        begin(current)
        if (!engine.isPresent(T.HOLDINGS_SWITCH_TOGGLE)) {
            skip(current, "the Stock / MTF switch is not shown for this account")
            return null
        }
        val first = engine.tap(T.HOLDINGS_SWITCH_TOGGLE)
        if (!first.ok) {
            if (engine.cancelled) return stopped(current)
            failAndContinue(current, "Could not tap the Stock / MTF switch: ${first.detail}")
            return null
        }
        if (!engine.sleep(Timeouts.SETTLE * 2)) return stopped(current)
        val check = checkPageLoads(budgetMs, 1)
        if (engine.cancelled) return stopped(current)
        val problem = loadFailure(check, budgetMs, "the other holdings view")
        // Restore the account's own setting whatever the other view did.
        val back = engine.tap(T.HOLDINGS_SWITCH_TOGGLE)
        if (!engine.sleep(Timeouts.SETTLE * 2)) return stopped(current)
        when {
            !back.ok -> return abort(current, "Switched Stock / MTF but could not switch back: ${back.detail}")
            problem != null -> failAndContinue(current, problem)
            else -> pass(current, "switched to the other holdings view, it loaded, and switched back")
        }
        return null
    }

    /**
     * Opens a sort / filter sheet and closes it again with Back, applying
     * nothing. Back is only pressed once the sheet's own marker is on screen.
     */
    private fun sheetStep(
        stepId: String,
        buttonId: String,
        sheetId: String,
        viewId: String,
        skipReason: String,
    ): AutomationRunResult? {
        val current = step(stepId)
        begin(current)
        if (!engine.isPresent(buttonId)) {
            skip(current, skipReason)
            return null
        }
        val open = engine.tapAndConfirm(buttonId, listOf(sheetId), Timeouts.SCREEN_CHANGE)
        if (!open.ok) {
            if (engine.cancelled) return stopped(current)
            failAndContinue(current, "The sheet did not open: ${open.detail}")
            return null
        }
        if (!closeWithBack(sheetId)) {
            if (engine.cancelled) return stopped(current)
            return abort(current, "The sheet opened but did not close on Back.")
        }
        if (!engine.isPresent(viewId)) {
            failAndContinue(current, "After closing the sheet the screen no longer shows '$viewId'.")
            return null
        }
        pass(current, "opened from its icon and closed with Back, nothing applied")
        return null
    }

    // ── Logout ──────────────────────────────────────────────────────────────────────────────────

    private fun logout(): AutomationRunResult? {
        val out = step("logged_out")
        begin(out)
        sessionEnded(out, "starting Logout")?.let { return it }
        // Logout sits at the foot of Profile > Account, below the fold.
        if (!engine.isPresent(T.PROFILE_SCREEN)) {
            val open = engine.tapAndConfirm(
                T.BOTTOMNAV_PROFILE_TAB,
                listOf(T.PROFILE_SCREEN),
                Timeouts.SCREEN_CHANGE,
                stopOn = loginScreenIds,
            )
            if (!open.ok) {
                if (engine.cancelled) return stopped(out)
                sessionEnded(out, "opening Profile to log out")?.let { return it }
                return abort(out, "Could not open Profile to log out: ${open.detail}")
            }
        }
        engine.tap(T.PROFILE_TAB_ACCOUNT)
        if (!engine.sleep(Timeouts.SETTLE)) return stopped(out)
        sessionEnded(out, "opening Profile Account to log out")?.let { return it }
        var scrolls = 0
        while (!engine.isPresent(T.PROFILE_LOGOUT_BUTTON) && scrolls < 10 && engine.scrollPage(forward = true)) {
            scrolls++
            if (!engine.sleep(Timeouts.SETTLE)) return stopped(out)
        }
        val ask = engine.tapAndConfirm(
            T.PROFILE_LOGOUT_BUTTON,
            listOf(T.LOGOUT_CONFIRM_BUTTON),
            Timeouts.SCREEN_CHANGE,
            stopOn = loginScreenIds,
        )
        if (!ask.ok) {
            if (engine.cancelled) return stopped(out)
            sessionEnded(out, "opening the logout confirmation")?.let { return it }
            return abort(out, "Could not open the logout confirmation: ${ask.detail}")
        }
        // No retry: the dialog may stay up for the whole logout request, and a
        // second Yes would send a second logout.
        val confirm = engine.tapAndConfirm(
            T.LOGOUT_CONFIRM_BUTTON,
            listOf(T.USER_ID_SCREEN, T.PASSWORD_SCREEN),
            Timeouts.LOGOUT,
            retryAfterMs = Timeouts.LOGOUT,
        )
        if (!confirm.ok) {
            if (engine.cancelled) return stopped(out)
            return abort(out, "Confirmed logout, but the login screen never appeared: ${confirm.detail}")
        }
        pass(out, "confirmed with Yes and landed back on the login screens")
        return null
    }
}

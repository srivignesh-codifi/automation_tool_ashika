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
 * modifies or cancels an order or moves funds. On Orders, Dashboard and
 * Portfolio the tool only selects tabs, flips view switches, opens a sort sheet
 * and scrolls — it never taps a row, a buy / sell / exit button, or a basket
 * action. It types only the three credential values plus the fixed search
 * terms baked into this flow.
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

    private companion object {
        /** Typed into side forms and never submitted. Obviously not a real number or PAN. */
        const val FAKE_MOBILE = "9999999999"
        const val FAKE_PAN = "ABCDE1234F"

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
            s("watchlist_tab_opened", "Watchlist tab opened"),
            s("watchlist_tabs_loaded", "Watchlist tabs found"),
            s("watchlist_predefined_no_add", "Predefined list refuses Search / add"),
            s("watchlist_basket_toggled", "Basket mode toggled and restored"),
            s("watchlist_filter_opened", "Filter screen opened and closed"),
            s("scrip_details_opened", "Scrip details opened and closed"),
            s("scrip_chart_loaded", "Scrip details › Chart loaded (screenshot)"),
            s("scrip_more_details_tabs", "Scrip details › More Details tabs load"),
            s("watchlist_edit_opened", "Edit watchlist: screen opened (long-press)"),
            s("watchlist_edit_rename", "Edit watchlist: renamed"),
            s("watchlist_edit_reorder", "Edit watchlist: scrip moved down"),
            s("watchlist_edit_delete", "Edit watchlist: scrip deleted"),
            s("watchlist_edit_saved", "Edit watchlist: saved, count drops by one"),
            s("watchlist_edit_verified", "Edit watchlist: rename and order persisted"),
            s("watchlist_edit_restored", "Edit watchlist: name restored and saved"),
            s("search_opened", "Search screen opened"),
            s("search_tabs_listed", "Search segments listed"),
            s("search_stocks_added", "Stocks segment: TCS added"),
            s("search_stocks_deleted", "Stocks segment: TCS deleted again"),
            s("search_fno_added", "F&O segment: NIFTY added"),
            s("search_fno_deleted", "F&O segment: NIFTY deleted again"),
            s("search_commodity_added", "Commodity segment: crude oil added"),
            s("search_commodity_deleted", "Commodity segment: crude oil deleted again"),
            s("search_all_added", "All segment: INFY added"),
            s("search_details_opened", "Search result details opened and closed"),
            s("search_closed", "Search screen closed"),
            s("watchlist_count_updated", "Watchlist count matches the adds and deletes"),
            s("orders_pending_loaded", "Orders › Pending loaded"),
            s("orders_order_book_loaded", "Orders › Order Book loaded"),
            s("orders_trade_book_loaded", "Orders › Trade Book loaded"),
            s("orders_basket_loaded", "Orders › Basket loaded"),
            s("orders_gtt_loaded", "Orders › GTT loaded"),
            s("orders_sip_loaded", "Orders › SIP loaded"),
            s("dashboard_trading_loaded", "Dashboard › Trading loaded"),
            s("option_chain_opened", "Trading › Option Chain opened"),
            s("option_chain_nfo_loaded", "Option Chain › NFO: first script loads in 7 s"),
            s("option_chain_mcx_loaded", "Option Chain › MCX: first script loads in 7 s"),
            s("option_chain_cds_loaded", "Option Chain › CDS: first script loads in 7 s"),
            s("option_chain_bfo_loaded", "Option Chain › BFO: first script loads in 7 s"),
            s("research_calls_loaded", "Trading › Research calls: every tab and sub tab loads"),
            s("dashboard_investments_loaded", "Dashboard › Investments loaded"),
            s("scanners_view_all_loaded", "Investments › Scanners › View All: every tab loads"),
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

    /**
     * Every possible header tab index of each strip. A tab scrolled out of view
     * is not in the accessibility tree at all, so "a tab of this strip is
     * showing" is the only reliable way to tell which strip is on screen.
     */
    private val watchlistTabIds = (0 until Timeouts.MAX_WATCHLIST_TABS).map { "${T.WATCHLIST_TAB_PREFIX}$it" }
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

        if (!moduleOn("login")) skipModule("login") else {
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

        if (moduleOn("login")) {
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

        if (moduleOn("login")) {
        // 13a — Enable 2FA --------------------------------------------------------------------------------------
        // Tradion's biometric screen is titled "Enable 2FA security". It appears
        // only on a device with biometrics, and the run cannot go past it (no
        // Skip), so the next step ends the run there after this one is recorded.
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
                pass(
                    enable2fa,
                    "Enable now was visible and clickable and was tapped (${tap.detail}); it starts Android's " +
                        "own authentication prompt, which this tool does not read or drive",
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
        if (afterOtpId == T.BIOMETRIC_SCREEN) {
            return abort(
                disclosure,
                "The app is offering to enable biometric login. Tradion's enrolment screen has no " +
                    "Skip, and this tool never drives fingerprint or face authentication, so the run " +
                    "cannot continue. Use a device or emulator with no biometrics enrolled.",
            )
        }
        var disclosureShown = afterOtpId == T.RISK_DISCLOSURE_DIALOG
        if (!disclosureShown) {
            when (engine.waitForAny(listOf(T.RISK_DISCLOSURE_DIALOG), Timeouts.DISCLOSURE_APPEARS)) {
                is WaitOutcome.Found -> disclosureShown = true
                is WaitOutcome.Cancelled -> return stopped(disclosure)
                else -> Unit
            }
        }
        if (!disclosureShown) {
            skip(disclosure, "the risk disclosure did not appear, so there was nothing to accept")
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
            pass(disclosure, "confirmed across $taps page(s)")
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

    /**
     * Opens the details sheet of the first watchlist row. Returns null on
     * success, a message starting "EMPTY" when there is no row, or a failure.
     */
    private fun openFirstRowSheet(): String? {
        if (!engine.isPresent(T.WATCHLIST_SCRIP_ROW)) return "EMPTY: no watchlist row"
        val sheets = listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)
        val open = engine.tapAndConfirm(T.WATCHLIST_SCRIP_ROW, sheets, Timeouts.SCREEN_CHANGE)
        if (!open.ok) return "Tapping the first watchlist row did not open its details: ${open.detail}"
        return if (engine.sleep(1_000L)) null else "cancelled"
    }

    /** Closes a details sheet that is still open, never pressing Back with nothing to close. */
    private fun closeSheetIfOpen() {
        engine.firstPresent(listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET))?.let { closeWithBack(it) }
    }

    /**
     * Long-presses the first watchlist row to open Edit. After deleting the last
     * row, the selected header tab is the only remaining route back into Edit,
     * so verification sessions fall back to long-pressing that exact tab.
     */
    private fun openEditScreen(): String? {
        val anchor = if (engine.isPresent(T.WATCHLIST_SCRIP_ROW)) {
            T.WATCHLIST_SCRIP_ROW
        } else {
            selectedEditableWatchlistTab
                ?: return "No scrip row or selected editable watchlist tab is available to open Edit."
        }
        val open = engine.tapAndConfirm(
            anchor, listOf(T.REORDER_SCREEN), Timeouts.WATCHLIST_EDIT, longPress = true,
        )
        return if (open.ok) null else "Long-pressing '$anchor' did not open the edit screen: ${open.detail}"
    }

    /**
     * Leaves an edit screen that did not close after Save. Back asks "save
     * changes?" when something is unsaved, so Discard is tapped if that shows.
     * Back is only pressed while the edit screen is confirmed on screen.
     */
    private fun escapeEditScreen(): Boolean {
        if (!engine.isPresent(T.REORDER_SCREEN)) return true
        // The first Back can merely dismiss the rename field's keyboard. Retry
        // only while Edit itself is still positively present.
        repeat(3) {
            if (!engine.isPresent(T.REORDER_SCREEN)) return true
            if (!engine.pressBack()) return false
            when (val found = engine.waitForAny(
                listOf(T.REORDER_DISCARD_BUTTON, T.WATCHLIST_SCREEN),
                Timeouts.CONFIRM_RETRY,
            )) {
                is WaitOutcome.Found -> {
                    if (found.identifier == T.REORDER_DISCARD_BUTTON) {
                        val discard = engine.tap(T.REORDER_DISCARD_BUTTON)
                        if (!discard.ok) return false
                    }
                    if (engine.waitForGone(T.REORDER_SCREEN, Timeouts.SCREEN_CHANGE)) return true
                }
                is WaitOutcome.Cancelled -> return false
                else -> Unit
            }
        }
        return engine.waitForGone(T.REORDER_SCREEN, Timeouts.SCREEN_CHANGE)
    }

    /**
     * What the edit screen is showing right now, for a failed Save: the app
     * explains a refusal in a snackbar ("Market watch name already exists",
     * "can't be empty", ...), which is otherwise lost.
     */
    private fun editScreenHint(): String {
        val texts = engine.screenTexts()
        val identifiers = engine.visibleIdentifiers()
        return buildString {
            if (texts.isNotEmpty()) append(" Texts on screen: ${texts.joinToString(" | ")}.")
            if (identifiers.isNotEmpty()) append(" Identifiers on screen: ${identifiers.joinToString(", ")}.")
        }
    }

    /** The rows of the open edit screen, top to bottom, as the app labels them. */
    private fun editRows(): List<String> =
        engine.groupedLabelsOf(T.REORDER_ROW).map { it.trim() }.filter { it.isNotEmpty() }

    private data class EditOutcome(
        val countAfter: Int?,
        val persistedDeletes: Int,
    )

    /**
     * One edit session on the open edit screen, ending saved: rename, move the
     * first scrip down, delete the (new) first scrip, Save, check the count; then
     * a second session confirms the name and order persisted and puts the name
     * back. Every step is soft; the one thing that must not happen is leaving the
     * run stuck on the edit screen, so each failure path leaves it.
     * Returns the watchlist count afterwards (null if unreadable), or null with
     * the run cancelled — callers distinguish via [AutomationEngine.cancelled].
     */
    private fun editSession(editIds: List<String>, countBefore: Int?, allowDelete: Boolean): EditOutcome? {
        val rename = step(editIds[1])
        val reorder = step(editIds[2])
        val delete = step(editIds[3])
        val saved = step(editIds[4])
        val verified = step(editIds[5])
        val restored = step(editIds[6])

        val rowsBefore = editRows()
        val origName = engine.watchlistName()?.trim().orEmpty()
        val replacement = if (origName.lastOrNull()?.uppercaseChar() == 'Q') 'R' else 'Q'
        val newName = if (origName.length >= 25) origName.dropLast(1) + replacement else "$origName$replacement"

        // Rename ---------------------------------------------------------------------------------------
        begin(rename)
        var renamed = false
        if (origName.isEmpty()) {
            failAndContinue(rename, "The watchlist's current name could not be read from the edit screen, so it was left alone.")
        } else {
            val typed = engine.enterText(T.REORDER_RENAME_INPUT, newName)
            if (!typed.ok) failAndContinue(rename, "Could not type the new name: ${typed.detail}")
            else { renamed = true; pass(rename, "renamed '$origName' to '$newName'") }
        }
        if (engine.cancelled) return null

        // Move the first scrip down ---------------------------------------------------------------------------
        begin(reorder)
        var reordered = false
        if (rowsBefore.isEmpty()) {
            failAndContinue(reorder, "No rows could be read from the edit screen ('${T.REORDER_ROW}' carried no text). Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
        } else if (rowsBefore.size < 2) {
            skip(reorder, "the watchlist holds fewer than two scrips, so there is nothing to rearrange")
        } else {
            val move = engine.customAction(T.REORDER_ROW, "Move down")
            if (!move.ok) {
                failAndContinue(reorder, "Could not move the first scrip down: ${move.detail}")
            } else {
                val landed = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
                    val after = editRows()
                    after.size >= 2 && after[0] == rowsBefore[1] && after[1] == rowsBefore[0]
                }
                if (engine.cancelled) return null
                val after = editRows()
                if (landed) {
                    reordered = true
                    pass(reorder, "the first scrip moved below the second")
                } else {
                    failAndContinue(reorder, "After 'Move down' the first two rows should have swapped, but the list now starts with ${after.take(2)} (was ${rowsBefore.take(2)}).")
                }
            }
        }
        if (engine.cancelled) return null

        // Delete the (new) first scrip -----------------------------------------------------------------------
        begin(delete)
        val rowsNow = editRows()
        var deleted = 0
        // A watchlist holding one scrip is never emptied. Deleting its only scrip
        // leaves an empty watchlist, and in a recorded run the rename that puts the
        // name back then never completed (Save left the screen open for the whole
        // 20 s), stranding the temporary name. The run's own INFY add puts one scrip
        // back each time, so such an account hit this every run. With two or more
        // scrips the list is never empty, so the delete is still exercised.
        val deleteSkipped = rowsNow.size == 1 || !allowDelete
        if (deleteSkipped) {
            skip(
                delete,
                if (!allowDelete) {
                    "Search and add is not selected, and its INFY add is what puts a deleted scrip back, " +
                        "so nothing is deleted"
                } else {
                    "the watchlist holds a single scrip; deleting it would leave the watchlist empty, and an " +
                        "empty watchlist's name could not be saved back in an earlier run, so nothing is deleted " +
                        "(add a second scrip to test deleting)"
                },
            )
        } else {
            val deleteTap = engine.tap(T.REORDER_DELETE_BUTTON)
            if (deleteTap.ok && rowsNow.isNotEmpty()) {
                val landed = engine.waitUntil(Timeouts.SCREEN_CHANGE) {
                    val after = editRows()
                    after.firstOrNull() != rowsNow.first()
                }
                if (landed) deleted = TRIM_COUNT
            }
            if (engine.cancelled) return null
            when {
                !deleteTap.ok -> failAndContinue(delete, "No delete icon could be tapped on the edit screen: ${deleteTap.detail}")
                rowsNow.isEmpty() -> failAndContinue(delete, "The delete control was tapped, but no row label was readable beforehand, so the affected scrip cannot be verified.")
                deleted == 0 -> failAndContinue(delete, "The delete control was tapped, but the first row did not disappear within ${Timeouts.SCREEN_CHANGE / 1000} seconds; Save was not credited with a deletion.")
                else -> pass(delete, "the first edit row disappeared after its delete control was tapped")
            }
        }
        val expectedRows = if (deleted > 0) rowsNow.drop(deleted) else rowsNow

        // Save ----------------------------------------------------------------------------------------------------
        begin(saved)
        var countAfter: Int? = countBefore
        var persistedDeletes = 0
        var saveClosed = false
        var escapedAfterSaveFailure = true
        if (!renamed && !reordered && deleted == 0) {
            failAndContinue(saved, "Nothing was changed on the edit screen (rename, reorder and delete all failed or were skipped), so Save had no valid change to persist.")
            escapedAfterSaveFailure = escapeEditScreen()
        } else {
            val save = engine.tap(T.REORDER_SAVE_BUTTON)
            if (!save.ok) {
                failAndContinue(saved, "Could not tap Save: ${save.detail}")
                escapedAfterSaveFailure = escapeEditScreen()
            } else if (!engine.waitForGone(T.REORDER_SCREEN, Timeouts.EDIT_SAVE)) {
                if (engine.cancelled) return null
                failAndContinue(saved, "Tapped Save but the edit screen did not close within ${Timeouts.EDIT_SAVE / 1000} seconds (a duplicate name or rejected request can leave it open); attempted to leave it with Discard.${editScreenHint()}")
                escapedAfterSaveFailure = escapeEditScreen()
            } else {
                saveClosed = true
                val back = backOnWatchlist()
                val expected = countBefore?.minus(deleted)
                countAfter = if (back == null) waitForScripCount(expected) else null
                if (engine.cancelled) return null
                if (deleted > 0 && expected != null && countAfter == expected) persistedDeletes = deleted
                when {
                    back != null -> failAndContinue(saved, "Saved, but then $back")
                    deleteSkipped && (expected == null || countAfter == expected) -> pass(
                        saved,
                        "saved the new name; the watchlist kept its ${countAfter ?: "single"} scrip(s) because the delete was skipped",
                    )
                    deleted == 0 && !deleteSkipped -> failAndContinue(saved, "The edit screen closed, but no scrip deletion had been confirmed, so the required count drop was not tested.")
                    expected != null && countAfter != expected -> failAndContinue(saved, "Saved after deleting $deleted of $countBefore scrip(s), so the count should be $expected, but it shows ${countAfter ?: "nothing readable"}." + if (countAfter == null) countDiagnostics() else "")
                    else -> pass(saved, "saved; the watchlist went from ${countBefore ?: "an unreadable count"} to ${countAfter ?: "an unreadable count"} scrip(s)")
                }
            }
        }

        if (!saveClosed) {
            if (engine.cancelled) return null
            skip(verified, "Save did not close the edit screen, so persistence could not be checked")
            if (escapedAfterSaveFailure) {
                skip(restored, "the unsaved rename was discarded during recovery")
            } else {
                begin(restored)
                failAndContinue(restored, "The failed Save left Edit open and the Discard recovery did not close it; the original name could not be confirmed.")
            }
            backOnWatchlist()
            return EditOutcome(readScripCount() ?: countAfter, persistedDeletes = 0)
        }

        // Reopen: did the name and the order persist? ---------------------------------------------------------------
        begin(verified)
        if (backOnWatchlist() != null || (!engine.isPresent(T.REORDER_SCREEN) && openEditScreen() != null)) {
            failAndContinue(verified, "The edit screen could not be reopened to check the saved changes.")
            begin(restored)
            if (renamed) {
                failAndContinue(restored, "The edit screen could not be reopened, so the temporary watchlist name could not be restored safely.")
            } else {
                skip(restored, "the edit screen could not be reopened, but the name was never changed")
            }
            return EditOutcome(countAfter, persistedDeletes)
        }
        if (engine.cancelled) return null
        engine.sleep(Timeouts.SETTLE)
        val nameNow = engine.watchlistName()?.trim().orEmpty()
        val rowsAfter = editRows()
        val problems = mutableListOf<String>()
        if (renamed && nameNow != newName) problems += "the name should be '$newName' but is '$nameNow'"
        val compare = minOf(3, expectedRows.size, rowsAfter.size)
        val rowsPersisted = when {
            expectedRows.isEmpty() -> rowsAfter.isEmpty()
            rowsAfter.isEmpty() -> false
            else -> compare > 0 && rowsAfter.take(compare) == expectedRows.take(compare)
        }
        if (!rowsPersisted) {
            problems += "the first rows should be ${expectedRows.take(3)} but are ${rowsAfter.take(3)}"
        }
        if (deleted > 0 && rowsPersisted) persistedDeletes = deleted
        if (problems.isEmpty()) pass(verified, "the temporary name and the order of the first ${maxOf(compare, 0)} scrip(s) persisted")
        else failAndContinue(verified, problems.joinToString("; ") + ".")

        // Put the name back ---------------------------------------------------------------------------------------------
        begin(restored)
        if (!renamed) {
            if (closeWithBack(T.REORDER_SCREEN)) {
                skip(restored, "the name was never changed")
            } else {
                failAndContinue(restored, "The name was never changed, but the verification edit screen did not close.")
            }
        } else if (nameNow == origName) {
            if (closeWithBack(T.REORDER_SCREEN)) {
                skip(restored, "the temporary rename did not persist; the original name is already present")
            } else {
                failAndContinue(restored, "The original name is present, but the verification edit screen did not close.")
            }
        } else {
            val back = engine.enterText(T.REORDER_RENAME_INPUT, origName)
            if (!back.ok) {
                failAndContinue(restored, "Could not type the original name back: ${back.detail}")
                escapeEditScreen()
            } else {
                // Let the app register the typed name before Save is pressed: Save only acts
                // once the app has marked the name as changed. If the screen is still open
                // after a first try, press Save once more; a rename is safe to repeat.
                if (!engine.sleep(Timeouts.SETTLE)) return null
                var save2 = engine.tap(T.REORDER_SAVE_BUTTON)
                var closed2 = save2.ok && engine.waitForGone(T.REORDER_SCREEN, Timeouts.EDIT_SAVE_FIRST_TRY)
                var retried = false
                if (!closed2 && !engine.cancelled && engine.isPresent(T.REORDER_SCREEN)) {
                    retried = true
                    save2 = engine.tap(T.REORDER_SAVE_BUTTON)
                    closed2 = save2.ok && engine.waitForGone(T.REORDER_SCREEN, Timeouts.EDIT_SAVE - Timeouts.EDIT_SAVE_FIRST_TRY)
                }
                if (!closed2) {
                    if (engine.cancelled) return null
                    failAndContinue(
                        restored,
                        "The original name '$origName' could not be saved back; the watchlist is still called '$newName'." +
                            (if (retried) " Save was pressed twice." else "") + editScreenHint(),
                    )
                    escapeEditScreen()
                } else {
                    val reopened = backOnWatchlist() == null && openEditScreen() == null
                    if (engine.cancelled) return null
                    if (reopened && !engine.sleep(Timeouts.SETTLE)) return null
                    val restoredName = if (reopened) engine.watchlistName()?.trim() else null
                    val verificationClosed = !reopened || closeWithBack(T.REORDER_SCREEN)
                    if (restoredName == origName && verificationClosed) {
                        pass(restored, "the original name was saved and verified after reopening Edit")
                    } else {
                        val closeProblem = if (reopened && !verificationClosed) " The verification edit screen also did not close." else ""
                        failAndContinue(restored, "The restore save closed, but reopening Edit showed ${restoredName?.let { "a different name" } ?: "no readable name"}.$closeProblem")
                    }
                }
            }
        }
        backOnWatchlist()
        countAfter = readScripCount() ?: countAfter
        return EditOutcome(countAfter, persistedDeletes)
    }

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

    private val searchStepIds = listOf(
        "search_tabs_listed", "search_stocks_added", "search_stocks_deleted", "search_fno_added",
        "search_fno_deleted", "search_commodity_added", "search_commodity_deleted", "search_all_added",
        "search_details_opened", "search_closed",
    )

    /** Fails [current] softly and returns false when the Watchlist tab cannot be shown. */
    private fun onWatchlistOrFail(current: AutomationStepResult): Boolean {
        val problem = backOnWatchlist() ?: return true
        failAndContinue(current, problem)
        return false
    }

    /** Number of header tabs on the user-watchlist strip, learnt while walking it. The last is "Recently Viewed". */
    private var watchlistTabCount = 0

    /** The exact editable tab selected for this run, retained for empty-list edit verification. */
    private var selectedEditableWatchlistTab: String? = null

    /**
     * Selects a watchlist tab that can be edited. Tradion remembers the tab last
     * selected, and after the Predefined check that is "Recently Viewed" — the
     * last tab, which cannot take scrips. Tab 0 is not reliable to reach: a tab
     * scrolled out of view is absent from the accessibility tree. So this takes
     * whichever editable tab is showing, and scrolls the strip only if none is.
     */
    private fun selectEditableWatchlistTab(): Boolean {
        // The final tab is Recently Viewed. With fewer than two discovered tabs
        // there is no positively identified editable target, so do not guess.
        if (watchlistTabCount < 2) return false
        val lastEditable = watchlistTabCount - 2
        val candidates = watchlistTabIds.take(lastEditable + 1)
        val remembered = selectedEditableWatchlistTab?.takeIf { it in candidates }
        val target = when {
            remembered != null && engine.revealInList(remembered, watchlistTabIds) -> remembered
            else -> engine.firstPresent(candidates)
                ?: candidates.firstOrNull { engine.revealInList(it, watchlistTabIds) }
                ?: return false
        }
        if (!engine.tap(target).ok) return false
        if (!engine.sleep(Timeouts.SETTLE)) return false
        selectedEditableWatchlistTab = target
        return true
    }

    private fun runWatchlist(): AutomationRunResult? {
        val viewOn = moduleOn("watchlist")
        val editOn = moduleOn("watchlist_edit")
        val searchOn = moduleOn("watchlist_search")
        if (!viewOn && !editOn && !searchOn) {
            // The Watchlist tab is only opened as a way into one of its modules.
            skipSteps(listOf("watchlist_tab_opened", "watchlist_tabs_loaded"), "no Watchlist module selected")
            skipModule("watchlist")
            skipModule("watchlist_edit")
            skipModule("watchlist_search")
            return null
        }
        // Watchlist tab opened ------------------------------------------------------------------
        val tabOpened = step("watchlist_tab_opened")
        begin(tabOpened)
        val open = engine.tapAndConfirm(T.BOTTOMNAV_WATCHLIST_TAB, listOf(T.WATCHLIST_SCREEN), Timeouts.WATCHLIST_NAV)
        if (!open.ok) {
            if (engine.cancelled) return stopped(tabOpened)
            failAndContinue(tabOpened, "Could not open the Watchlist tab: ${open.detail}")
            skipSteps(watchlistStepIds + searchStepIds + "watchlist_count_updated", "the Watchlist tab did not open")
            return null
        }
        pass(tabOpened, open.detail)

        // Watchlist tabs open -------------------------------------------------------------------
        // No load test: the tabs are only counted (scrolling the strip), which the edit and search steps need.
        val tabs = step("watchlist_tabs_loaded")
        begin(tabs)
        countWatchlistTabs(tabs)

        // Predefined list refuses Search / add -----------------------------------------------------
        val predefined = step("watchlist_predefined_no_add")
        if (!viewOn) skip(predefined, modules.skipNote("watchlist")) else run {
        begin(predefined)
        val toPredefined = engine.tapAndConfirm(T.WATCHLIST_MODE_PREDEFINED, predefinedTabIds, Timeouts.SCREEN_CHANGE)
        if (!toPredefined.ok) {
            if (engine.cancelled) return stopped(predefined)
            failAndContinue(predefined, "Could not switch to Predefined: ${toPredefined.detail}")
        } else {
            // Tapping the search row must NOT open Search here: Predefined lists take no scrips.
            val tap = engine.tap(T.WATCHLIST_SEARCH_BUTTON)
            if (!engine.sleep(2_000L)) return stopped(predefined)
            if (engine.isPresent(T.SEARCH_SCREEN)) {
                closeWithBack(T.SEARCH_SCREEN)
                failAndContinue(predefined, "Search opened on a Predefined list, which should not accept scrips.")
            } else if (!tap.ok) {
                failAndContinue(predefined, "Could not tap the search row on the Predefined list: ${tap.detail}")
            } else {
                pass(predefined, "tapping Search on a Predefined list stayed on the list (it takes no scrips)")
            }
        }
        // Back to the user's own watchlists, on an editable tab.
        val toWatchlist = engine.tapAndConfirm(T.WATCHLIST_MODE_WATCHLIST, watchlistTabIds, Timeouts.SCREEN_CHANGE)
        if (!toWatchlist.ok) {
            if (engine.cancelled) return stopped(predefined)
            backOnWatchlist()
            engine.tap(T.WATCHLIST_MODE_WATCHLIST)
        }
        }
        val editableReady = selectEditableWatchlistTab()
        if (engine.cancelled) return stopped(predefined)

        if (!viewOn) skipModule("watchlist") else run {
        // Basket mode toggled and restored -------------------------------------------------------------
        val basket = step("watchlist_basket_toggled")
        begin(basket)
        if (onWatchlistOrFail(basket)) {
            if (!engine.isPresent(T.WATCHLIST_MODE_WATCHLIST)) {
                failAndContinue(basket, "The Watchlist / Predefined switch is not on screen, so basket mode cannot be told apart from normal mode.")
            } else {
                val on = engine.tap(T.WATCHLIST_BASKET_BUTTON)
                if (!on.ok) {
                    failAndContinue(basket, "Could not tap the basket icon: ${on.detail}")
                } else {
                    val entered = engine.waitForGone(T.WATCHLIST_MODE_WATCHLIST, Timeouts.SCREEN_CHANGE)
                    if (engine.cancelled) return stopped(basket)
                    // Restore regardless, so the rest of the run is not left in basket mode.
                    val off = engine.tapAndConfirm(
                        T.WATCHLIST_BASKET_BUTTON, listOf(T.WATCHLIST_MODE_WATCHLIST), Timeouts.SCREEN_CHANGE,
                        tapAtLeastOnce = entered,
                    )
                    when {
                        engine.cancelled -> return stopped(basket)
                        !off.ok -> failAndContinue(basket, "Basket mode could not be switched off again: ${off.detail}")
                        entered -> pass(basket, "basket mode hid the Watchlist/Predefined switch and was switched off again")
                        else -> failAndContinue(basket, "Tapping the basket icon did not change the screen.")
                    }
                }
            }
        }

        // Filter screen opened and closed ----------------------------------------------------------------
        val filter = step("watchlist_filter_opened")
        begin(filter)
        if (onWatchlistOrFail(filter)) {
            val filterOpen = engine.tapAndConfirm(T.WATCHLIST_FILTER_BUTTON, listOf(T.WATCHLIST_FILTER_SCREEN), Timeouts.SCREEN_CHANGE)
            when {
                !filterOpen.ok -> {
                    if (engine.cancelled) return stopped(filter)
                    failAndContinue(filter, "The Filter screen did not open: ${filterOpen.detail}")
                }
                !closeWithBack(T.WATCHLIST_FILTER_SCREEN) -> {
                    if (engine.cancelled) return stopped(filter)
                    failAndContinue(filter, "The Filter screen opened but did not close on Back.")
                }
                else -> pass(filter, "opened from the gear icon and closed with Back, nothing applied")
            }
        }

        // Scrip details opened and closed ------------------------------------------------------------------
        val details = step("scrip_details_opened")
        begin(details)
        if (onWatchlistOrFail(details)) {
            val sheets = listOf(T.SCRIP_DETAILS_SHEET, T.INDEX_DETAILS_SHEET)
            if (!engine.isPresent(T.WATCHLIST_SCRIP_ROW)) {
                skip(details, "this watchlist is empty, so there is no row to open")
            } else {
                val detailsOpen = engine.tapAndConfirm(T.WATCHLIST_SCRIP_ROW, sheets, Timeouts.SCREEN_CHANGE)
                if (!detailsOpen.ok) {
                    if (engine.cancelled) return stopped(details)
                    failAndContinue(details, "Tapping the first watchlist row did not open its details: ${detailsOpen.detail}")
                } else {
                    val sheet = engine.firstPresent(sheets) ?: T.SCRIP_DETAILS_SHEET
                    if (!engine.sleep(1_500L)) return stopped(details)
                    if (!closeWithBack(sheet)) {
                        if (engine.cancelled) return stopped(details)
                        failAndContinue(details, "The details sheet opened but did not close on Back.")
                    } else {
                        pass(details, "opened '$sheet' from the first row and closed it with Back")
                    }
                }
            }
        }

        // Chart and More Details, both opened from the first row's details sheet -------------------------------
        // Back from either lands on the Watchlist, not on the sheet, so the sheet is reopened for each.
        val chart = step("scrip_chart_loaded")
        begin(chart)
        if (onWatchlistOrFail(chart)) {
            val sheetFailure = openFirstRowSheet()
            if (engine.cancelled) return stopped(chart)
            if (sheetFailure != null) {
                if (sheetFailure.startsWith("EMPTY")) skip(chart, "this watchlist is empty, so there is no row to open")
                else failAndContinue(chart, sheetFailure)
            } else {
                val toChart = engine.tapAndConfirm(T.SCRIP_SHEET_CHART_BUTTON, listOf(T.CHART_SCREEN), Timeouts.SCREEN_CHANGE)
                if (!toChart.ok) {
                    if (engine.cancelled) return stopped(chart)
                    failAndContinue(chart, "The Chart button did not open the chart: ${toChart.detail}")
                    closeSheetIfOpen()
                } else {
                    // The chart is a web page; the app only knows when the page has finished.
                    val ready = engine.waitForAny(listOf(T.CHART_READY), Timeouts.CHART_LOAD)
                    if (engine.cancelled) return stopped(chart)
                    engine.sleep(1_000L)
                    captureScreenshot("chart")
                    if (ready !is WaitOutcome.Found) {
                        failAndContinue(chart, "The chart page had not finished loading after ${Timeouts.CHART_LOAD / 1000} seconds (screenshot saved).")
                    } else {
                        pass(chart, "the chart page finished loading; screenshot saved")
                    }
                    if (!closeWithBack(T.CHART_SCREEN)) {
                        if (engine.cancelled) return stopped(chart)
                        if (chart.status == StepStatus.PASSED) failAndContinue(chart, "The chart opened but did not close on Back.")
                    }
                }
            }
        }

        val more = step("scrip_more_details_tabs")
        begin(more)
        if (onWatchlistOrFail(more)) {
            val sheetFailure = openFirstRowSheet()
            if (engine.cancelled) return stopped(more)
            if (sheetFailure != null) {
                if (sheetFailure.startsWith("EMPTY")) skip(more, "this watchlist is empty, so there is no row to open")
                else failAndContinue(more, sheetFailure)
            } else if (!engine.isPresent(T.SCRIP_SHEET_MORE_BUTTON)) {
                closeSheetIfOpen()
                skip(more, "this scrip's sheet offers no More Details (an index, for instance)")
            } else {
                val toMore = engine.tapAndConfirm(T.SCRIP_SHEET_MORE_BUTTON, listOf(T.MORE_INFO_SCREEN), Timeouts.SCREEN_CHANGE)
                if (!toMore.ok) {
                    if (engine.cancelled) return stopped(more)
                    failAndContinue(more, "The More Details button did not open the details screen: ${toMore.detail}")
                    closeSheetIfOpen()
                } else {
                    // Equity (NSE/BSE) has Market Depth, Technicals, Fundamentals and Info; other segments have no Fundamentals.
                    val failures = mutableListOf<String>()
                    val seen = mutableListOf<String>()
                    for (name in listOf("market_depth", "technicals", "fundamentals", "info")) {
                        val id = "${T.MORE_INFO_TAB_PREFIX}$name"
                        if (!engine.isPresent(id)) continue
                        val select = engine.tap(id)
                        if (!select.ok) { failures += "$name could not be selected (${select.detail})"; continue }
                        if (!engine.sleep(Timeouts.SETTLE)) return stopped(more)
                        val check = checkPageLoads(Timeouts.MORE_INFO_LOAD, scrollPages = 2)
                        if (engine.cancelled) return stopped(more)
                        loadFailure(check, Timeouts.MORE_INFO_LOAD, name)?.let { failures += it }
                        seen += name
                    }
                    when {
                        seen.isEmpty() -> failAndContinue(more, "None of the More Details tabs could be found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
                        failures.isNotEmpty() -> failAndContinue(more, failures.joinToString("; ") + ". Tabs seen: ${seen.joinToString()}.")
                        else -> pass(more, "tabs ${seen.joinToString()} each loaded within ${Timeouts.MORE_INFO_LOAD / 1000} s" + if ("fundamentals" !in seen) " (no Fundamentals tab: not an equity scrip)" else "")
                    }
                    if (!closeWithBack(T.MORE_INFO_SCREEN)) {
                        if (engine.cancelled) return stopped(more)
                        if (more.status == StepStatus.PASSED) failAndContinue(more, "The details screen opened but did not close on Back.")
                    }
                }
            }
        }

        }

        // Edit watchlist: rename, move a scrip, delete a scrip, save, verify, restore -------------------------------
        val editIds = listOf(
            "watchlist_edit_opened", "watchlist_edit_rename", "watchlist_edit_reorder", "watchlist_edit_delete",
            "watchlist_edit_saved", "watchlist_edit_verified", "watchlist_edit_restored",
        )
        var countAfterTrim: Int? = null
        var persistedEditDeletes = 0
        if (!editOn) {
            skipModule("watchlist_edit")
            if (searchOn) {
                // Search checks the count against where it started.
                backOnWatchlist()
                countAfterTrim = readScripCount()
            }
        } else run {
        val edit = step(editIds[0])
        begin(edit)
        if (!onWatchlistOrFail(edit)) {
            skipSteps(editIds.drop(1), "the Watchlist tab was not showing")
        } else if (!editableReady && !selectEditableWatchlistTab()) {
            failAndContinue(edit, "No editable watchlist tab could be selected (only Recently Viewed was reachable).")
            skipSteps(editIds.drop(1), "no editable watchlist tab")
        } else {
            if (!engine.sleep(Timeouts.SETTLE)) return stopped(edit)
            val countBeforeEdit = readScripCount()
            countAfterTrim = countBeforeEdit
            if (!engine.isPresent(T.WATCHLIST_SCRIP_ROW)) {
                skip(edit, "this watchlist is empty, so there is nothing to edit")
                skipSteps(editIds.drop(1), "this watchlist is empty")
            } else {
                val opened = openEditScreen()
                if (engine.cancelled) return stopped(edit)
                if (opened != null) {
                    failAndContinue(edit, opened)
                    skipSteps(editIds.drop(1), "the edit screen did not open")
                } else {
                    pass(edit, "long-pressed the first row and the edit screen appeared" + countNote(countBeforeEdit))
                    val outcome = editSession(editIds, countBeforeEdit, allowDelete = searchOn)
                    if (engine.cancelled) return stopped(edit)
                    countAfterTrim = outcome?.countAfter
                    persistedEditDeletes = outcome?.persistedDeletes ?: 0
                }
            }
        }

        }

        // Search ----------------------------------------------------------------------------------------------------
        if (!searchOn) {
            skipModule("watchlist_search")
            return null
        }
        val searchOpened = step("search_opened")
        begin(searchOpened)
        if (!onWatchlistOrFail(searchOpened)) {
            skipSteps(searchStepIds + "watchlist_count_updated", "the Watchlist tab was not showing")
            return null
        }
        if (!engine.isPresent(T.WATCHLIST_SEARCH_BUTTON) || !selectEditableWatchlistTab()) {
            failAndContinue(searchOpened, "No editable watchlist tab is selected, so Search would not take scrips.")
            skipSteps(searchStepIds + "watchlist_count_updated", "Search was not opened")
            return null
        }
        val searchOpen = engine.tapAndConfirm(T.WATCHLIST_SEARCH_BUTTON, listOf(T.SEARCH_SCREEN), Timeouts.SEARCH_SCREEN)
        val inputFound = searchOpen.ok &&
            engine.waitForAny(listOf(T.SEARCH_SCRIP_INPUT), Timeouts.SCREEN_CHANGE) is WaitOutcome.Found
        if (engine.cancelled) return stopped(searchOpened)
        if (!inputFound) {
            failAndContinue(searchOpened, "Could not open Search: ${searchOpen.detail}")
            if (engine.isPresent(T.SEARCH_SCREEN)) closeWithBack(T.SEARCH_SCREEN)
            skipSteps(searchStepIds + "watchlist_count_updated", "Search did not open")
            return null
        }
        pass(searchOpened, searchOpen.detail)

        val tabsListed = step("search_tabs_listed")
        begin(tabsListed)
        val missingTabs = T.SEARCH_TABS.filterNot { engine.revealInList(it, T.SEARCH_TABS) }
        if (engine.cancelled) return stopped(tabsListed)
        if (missingTabs.isNotEmpty()) {
            failAndContinue(tabsListed, "These search segments are missing: ${missingTabs.joinToString()}. Expected All, F&O, Commodity and Stocks.")
        } else {
            pass(tabsListed, "All, F&O, Commodity and Stocks are all offered")
        }

        // Each segment: add the first new result, then delete that exact result
        // again. All keeps its result only when it compensates a deletion that
        // the edit verification proved persisted.
        var net = 0
        for (seg in listOf(
            SearchSeg("stocks", T.SEARCH_TAB_STOCKS, "Stocks", "tcs", "TCS", deleteAgain = true),
            SearchSeg("fno", T.SEARCH_TAB_FNO, "F&O", "nifty", "NIFTY", deleteAgain = true),
            SearchSeg("commodity", T.SEARCH_TAB_COMMODITY, "Commodity", "crudeoil", "crude oil", deleteAgain = true),
            SearchSeg(
                "all", T.SEARCH_TAB_ALL, "All", "infy", "INFY",
                deleteAgain = persistedEditDeletes == 0,
            ),
        )) {
            val addStep = step("search_${seg.key}_added")
            val delStep = if (seg.key == "all") null else step("search_${seg.key}_deleted")
            begin(addStep)
            if (engine.isPresent(T.SEARCH_SCREEN).not()) {
                failAndContinue(addStep, "The Search screen is no longer showing.")
                delStep?.let { skip(it, "the Search screen is no longer showing") }
                continue
            }
            val outcome = searchAndAdd(seg)
            if (engine.cancelled) return stopped(addStep)
            if (outcome.failure != null) {
                failAndContinue(addStep, outcome.failure)
                delStep?.let { skip(it, "nothing was added") }
                continue
            }
            net++
            if (!seg.deleteAgain) {
                pass(addStep, "added and kept the first new ${seg.what} result under ${seg.tabName} to replace the edit deletion")
                continue
            }
            if (delStep != null) {
                pass(addStep, "added the first new ${seg.what} result under the ${seg.tabName} segment")
                begin(delStep)
            }
            val deleteFailure = deleteAddedSearchResult(outcome.rowIndex, seg.what)
            if (engine.cancelled) return stopped(delStep ?: addStep)
            if (deleteFailure == null) {
                net--
                if (delStep != null) {
                    pass(delStep, "removed the same ${seg.what} result that this run added")
                } else {
                    pass(addStep, "added INFY successfully, then removed that same result because no edit deletion needed replacing")
                }
            } else {
                failAndContinue(delStep ?: addStep, deleteFailure)
            }
        }

        // Search result details ------------------------------------------------------------------------------------
        val searchDetails = step("search_details_opened")
        begin(searchDetails)
        if (!engine.isPresent(T.SEARCH_SCREEN)) {
            failAndContinue(searchDetails, "The Search screen is no longer showing.")
        } else if (!engine.isPresent(T.SEARCH_RESULT_ROW)) {
            failAndContinue(searchDetails, "No search result row is on screen to open.")
        } else {
            val opened = engine.tapAndConfirm(T.SEARCH_RESULT_ROW, listOf(T.SEARCH_RESULT_SHEET), Timeouts.SCREEN_CHANGE)
            if (!opened.ok) {
                if (engine.cancelled) return stopped(searchDetails)
                failAndContinue(searchDetails, "Tapping a search result did not open its details: ${opened.detail}")
            } else {
                if (!engine.sleep(1_500L)) return stopped(searchDetails)
                if (!closeWithBack(T.SEARCH_RESULT_SHEET)) {
                    if (engine.cancelled) return stopped(searchDetails)
                    failAndContinue(searchDetails, "The result's details sheet opened but did not close on Back.")
                } else {
                    pass(searchDetails, "opened a result's details sheet and closed it with Back")
                }
            }
        }

        // Search closed ------------------------------------------------------------------------------------------------
        val searchClosed = step("search_closed")
        begin(searchClosed)
        val closeOutcome = engine.tapAndConfirm(
            T.SEARCH_BACK_BUTTON,
            listOf(T.WATCHLIST_SCREEN),
            Timeouts.SCREEN_CHANGE,
            tapAtLeastOnce = true,
        )
        val searchGone = engine.waitForGone(T.SEARCH_SCREEN, Timeouts.SCREEN_CHANGE)
        if (!closeOutcome.ok || !searchGone) {
            if (engine.cancelled) return stopped(searchClosed)
            failAndContinue(searchClosed, "Search did not close from its back arrow: ${closeOutcome.detail}")
            if (engine.isPresent(T.SEARCH_SCREEN)) closeWithBack(T.SEARCH_SCREEN)
        } else {
            pass(searchClosed, "closed from the search field's back arrow")
        }

        // Count ------------------------------------------------------------------------------------------------------------
        val countUpdated = step("watchlist_count_updated")
        begin(countUpdated)
        if (!onWatchlistOrFail(countUpdated)) return null
        val expectedCount = countAfterTrim?.plus(net)
        val countNow = waitForScripCount(expectedCount)
        if (engine.cancelled) return stopped(countUpdated)
        if (expectedCount == null || countNow != expectedCount) {
            failAndContinue(countUpdated, "After the adds and deletes (net ${if (net >= 0) "+" else ""}$net) on a watchlist of ${countAfterTrim ?: "unknown size"}, its count should show ${expectedCount ?: "a different number"}, but shows ${countNow ?: "nothing readable"}.${if (countNow == null) countDiagnostics() else ""}")
        } else {
            pass(countUpdated, "the watchlist went from $countAfterTrim to $countNow scrips (net ${if (net >= 0) "+" else ""}$net)")
        }
        return null
    }

    private val watchlistStepIds = listOf(
        "watchlist_tabs_loaded", "watchlist_predefined_no_add", "watchlist_basket_toggled",
        "watchlist_filter_opened", "scrip_details_opened", "scrip_chart_loaded", "scrip_more_details_tabs",
        "watchlist_edit_opened", "watchlist_edit_rename", "watchlist_edit_reorder", "watchlist_edit_delete",
        "watchlist_edit_saved", "watchlist_edit_verified", "watchlist_edit_restored", "search_opened",
    )

    private data class SearchSeg(
        val key: String,
        val tabId: String,
        val tabName: String,
        val query: String,
        val what: String,
        val deleteAgain: Boolean,
    )

    private class AddOutcome(val failure: String?, val rowIndex: Int = -1)

    /**
     * Selects [seg]'s segment, types its query and adds the first result not yet
     * in the watchlist. The add counts only once its "+" has turned into the
     * added icon.
     */
    private fun searchAndAdd(seg: SearchSeg): AddOutcome {
        searchOnly(seg.tabId, seg.tabName, seg.query)?.let { return AddOutcome(it) }
        val tap = engine.tapFirstDescendant(T.SEARCH_RESULT_ROW, T.SEARCH_SCRIP_ADD_BUTTON)
        if (!tap.ok) {
            return AddOutcome(
                "No ${seg.what} result offered '+' under ${seg.tabName}; every listed result may " +
                    "already be in the watchlist (${tap.detail}).",
            )
        }
        val rowIndex = tap.containerIndex ?: return AddOutcome("The added ${seg.what} result's row could not be tracked.")
        var requestInFlight = false
        var rejected = false
        val completed = engine.waitUntil(Timeouts.ADD_CONFIRM) {
            val added = engine.hasDescendantAt(T.SEARCH_RESULT_ROW, rowIndex, T.SEARCH_SCRIP_ADDED_ICON)
            val addAvailable = engine.hasDescendantAt(T.SEARCH_RESULT_ROW, rowIndex, T.SEARCH_SCRIP_ADD_BUTTON)
            when {
                added -> true
                !addAvailable -> {
                    requestInFlight = true
                    false
                }
                requestInFlight -> {
                    rejected = true
                    true
                }
                else -> false
            }
        }
        if (engine.cancelled) return AddOutcome("cancelled")
        if (completed && rejected) {
            return AddOutcome("The ${seg.what} add request completed but the same row returned to '+', so Tradion rejected the add.")
        }
        if (completed) return AddOutcome(null, rowIndex)
        return AddOutcome(
            "Tapped '+' on the first ${seg.what} result but it never turned into the added icon " +
                "within ${Timeouts.ADD_CONFIRM / 1000} seconds. The watchlist may be full (50 scrips) or " +
                "the add request failed.",
        )
    }

    /** Deletes the exact search-result row returned by [searchAndAdd]. Null on success. */
    private fun deleteAddedSearchResult(rowIndex: Int, what: String): String? {
        if (rowIndex < 0) return "The $what result's row was not tracked, so it was not safe to delete."
        val tap = engine.tapDescendantAt(T.SEARCH_RESULT_ROW, rowIndex, T.SEARCH_SCRIP_ADDED_ICON)
        if (!tap.ok) return "Could not delete the same $what result that was added: ${tap.detail}"
        var requestInFlight = false
        var rejected = false
        val completed = engine.waitUntil(Timeouts.ADD_CONFIRM) {
            val addAvailable = engine.hasDescendantAt(T.SEARCH_RESULT_ROW, rowIndex, T.SEARCH_SCRIP_ADD_BUTTON)
            val stillAdded = engine.hasDescendantAt(T.SEARCH_RESULT_ROW, rowIndex, T.SEARCH_SCRIP_ADDED_ICON)
            when {
                addAvailable -> true
                !stillAdded -> {
                    requestInFlight = true
                    false
                }
                requestInFlight -> {
                    rejected = true
                    true
                }
                else -> false
            }
        }
        if (engine.cancelled) return "cancelled"
        if (completed && rejected) {
            return "The $what delete request completed but the same row stayed added, so Tradion rejected the delete."
        }
        if (!completed) {
            return "Tapped the added icon, but the same $what result did not return to '+' within ${Timeouts.ADD_CONFIRM / 1000} seconds."
        }
        return null
    }

    /** Selects a search tab, types [query] and waits for its results. Null on success. */
    private fun searchOnly(tabId: String, tabName: String, query: String): String? {
        if (!engine.revealInList(tabId, T.SEARCH_TABS)) {
            return "The '$tabName' search tab ('$tabId') is not on the search screen."
        }
        // Put the new query in first. Tradion starts a fetch immediately when a
        // tab changes, using whatever text is already in the field; selecting
        // the tab while the previous query is still present can race that stale
        // request against the debounced new one.
        val entry = engine.enterText(T.SEARCH_SCRIP_INPUT, query)
        if (!entry.ok) return "Could not enter '$query' in the search field: ${entry.detail}"
        val select = engine.tap(tabId)
        if (!select.ok) return "Could not select the '$tabName' tab: ${select.detail}"
        if (!engine.sleep(Timeouts.SETTLE)) return "cancelled"
        // Changing the segment starts a request immediately. The app clears the
        // previous rows and exposes search_loading until the newest request is
        // complete, so no fixed debounce delay is needed here.
        if (!engine.waitForGone(T.SEARCH_LOADING, Timeouts.SEARCH_RESULTS)) {
            if (engine.cancelled) return "cancelled"
            return "Search for '$query' under $tabName was still loading after ${Timeouts.SEARCH_RESULTS / 1000} seconds."
        }
        if (engine.cancelled) return "cancelled"
        return when (
            val outcome = engine.waitForAny(
                listOf(T.SEARCH_RESULTS_LIST, T.SEARCH_NO_RESULT),
                Timeouts.SEARCH_RESULTS,
            )
        ) {
            is WaitOutcome.Found -> if (outcome.identifier == T.SEARCH_RESULTS_LIST) null
            else "Search returned no '$query' results under $tabName."
            else -> reasonFor(outcome, "Search results for '$query' under $tabName", Timeouts.SEARCH_RESULTS)
        }
    }

    /** Counts the user-watchlist header tabs by scrolling the strip; selects none and checks no loading. */
    private fun countWatchlistTabs(step: AutomationStepResult) {
        var counted = 0
        for (id in watchlistTabIds) {
            if (!engine.revealInList(id, watchlistTabIds)) break
            counted++
        }
        watchlistTabCount = counted
        if (counted == 0) {
            failAndContinue(step, "No watchlist tabs were found. Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}.")
        } else {
            pass(step, "$counted watchlist tab(s) found (the last is Recently Viewed); no load check")
        }
    }

    /**
     * Brings the Watchlist tab back if the app has drifted off it (an order
     * update or a notification can move Tradion to another tab on its own).
     * Returns null when the Watchlist is showing, else the reason it is not.
     */
    private fun backOnWatchlist(): String? {
        if (engine.isPresent(T.WATCHLIST_SCREEN)) return null
        val back = engine.tapAndConfirm(T.BOTTOMNAV_WATCHLIST_TAB, listOf(T.WATCHLIST_SCREEN), Timeouts.SCREEN_CHANGE)
        return if (back.ok) null else "The app left the Watchlist tab and it could not be reopened: ${back.detail}"
    }

    /** Selects the first user watchlist, scrolling the tab strip back to its start first. */
    private fun selectFirstWatchlistTab(): Boolean {
        val first = watchlistTabIds.first()
        if (!engine.revealInList(first, watchlistTabIds)) return false
        if (!engine.tap(first).ok) return false
        return engine.sleep(Timeouts.SETTLE)
    }

    /** The "N / 50" label's N, or null when it is absent or unreadable. */
    private fun readScripCount(): Int? =
        engine.labelsOf(T.WATCHLIST_SCRIP_COUNT).firstNotNullOfOrNull {
            Regex("""(\d+)\s*/\s*\d+""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

    /** Waits for the count label to read [expected]; returns the last value read. */
    private fun waitForScripCount(expected: Int?): Int? {
        if (expected == null) {
            engine.sleep(Timeouts.SETTLE)
            if (!engine.isPresent(T.WATCHLIST_SCREEN)) backOnWatchlist()
            return readScripCount()
        }
        var last: Int? = null
        var recoveredFromDrift = false
        engine.waitUntil(Timeouts.COUNT_UPDATE) {
            if (!engine.isPresent(T.WATCHLIST_SCREEN) && !recoveredFromDrift) {
                recoveredFromDrift = true
                backOnWatchlist()
            }
            last = readScripCount()
            last == expected
        }
        return last
    }

    /** What the count label looked like, for a failure message when it could not be read. */
    private fun countDiagnostics(): String =
        " The label '${T.WATCHLIST_SCRIP_COUNT}' was ${if (engine.isPresent(T.WATCHLIST_SCRIP_COUNT)) "on screen with text ${engine.labelsOf(T.WATCHLIST_SCRIP_COUNT)}" else "not on screen"}. " +
            "Identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}."

    private fun countNote(count: Int?): String =
        if (count == null) "; scrip count label not readable" else "; watchlist holds $count scrip(s)"

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
        val dashboardStepIds = dashboardTabs.map { it.stepId } + listOf(
            "option_chain_opened", "option_chain_nfo_loaded", "option_chain_mcx_loaded",
            "option_chain_cds_loaded", "option_chain_bfo_loaded", "research_calls_loaded",
            "scanners_view_all_loaded",
        )
        if (!moduleOn("dashboard")) skipModule("dashboard") else {
        withPage(Page("Dashboard", T.BOTTOMNAV_DASHBOARD_TAB, T.DASHBOARD_SCREEN), dashboardStepIds) {
            checkTab(dashboardTabs[0], budget)?.let { return@withPage it }
            optionChainSteps()?.let { return@withPage it }
            researchCallsStep()?.let { return@withPage it }
            checkTab(dashboardTabs[1], budget)?.let { return@withPage it }
            scannersViewAllStep()
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

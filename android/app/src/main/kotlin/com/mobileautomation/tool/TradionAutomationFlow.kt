package com.mobileautomation.tool

import com.mobileautomation.tool.AutomationConfig.Texts
import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T
import android.os.SystemClock
import com.mobileautomation.tool.AutomationEngine.WaitOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Regression pass for the Tradion app (`com.codifi.rmoney`) and every sibling
 * build that carries the same identifiers (iTrade, ...: [TargetApp.Flow.TRADION]).
 * [app] picks which. An app runs the modules [AppModules.of] gives it, and the
 * steps of the others are not in its checklist; what it lacks inside a module is
 * reported NOT APPLICABLE ([TargetApp.absent], [TargetApp.notApplicableCases]).
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
    private val app: TargetApp = TargetApp.TRADION,
) : FlowBase(engine, buildSteps(app)) {

    init {
        // Lets the report show Login and Watchlist (and the rest) as separate results.
        for (module in AppModules.of(app)) for (id in module.stepIds) step(id).module = module.id
    }

    internal companion object {
        /** Typed into side forms and never submitted. Obviously not a real number or PAN. */
        const val FAKE_MOBILE = "9999999999"
        const val FAKE_PAN = "ABCDE1234F"

        /**
         * The session may expire (HTTP 401) this many times in one run. Each earlier time the tool signs in again and
         * resumes from the interrupted step; this one ends the run (owner's rule).
         */
        const val MAX_SESSION_LOSSES = 3

        /** The Login module's detours: steps that leave the sign-in path and come back. Skipped by "direct login only". */
        val LOGIN_DETOUR_STEPS = listOf(
            "guest_user_checked", "unblock_account_checked", "join_now_checked",
            "forgot_password_checked", "switch_account_checked",
        )

        fun s(id: String, name: String) = AutomationStepResult(id, name)

        /** [app]'s checklist: Tradion's, less the steps of every Tradion module [app] does not have. */
        fun buildSteps(app: TargetApp = TargetApp.TRADION): List<AutomationStepResult> {
            val own = AppModules.of(app).map { it.id }.toSet()
            val absent = AppModules.TRADION.filter { it.id !in own }.flatMap { it.stepIds }.toSet()
            return allSteps().filter { it.id !in absent }
        }

        private fun allSteps(): List<AutomationStepResult> = listOf(
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
            s("biometric_confirmed", "Biometric enrolment confirmed (Enable now, Android prompt, Home)"),
            s("disclosure_accepted", "Risk disclosure accepted"),
            s("home_displayed", "Home displayed"),
        ) + WatchlistSuite.steps() + WatchlistSuite.orderSteps() + listOf(
            s("orders_pending_loaded", "Orders › Pending loaded"),
            s("orders_order_book_loaded", "Orders › Order Book loaded"),
            s("orders_trade_book_loaded", "Orders › Trade Book loaded"),
            s("orders_basket_loaded", "Orders › Basket loaded"),
            s("orders_gtt_loaded", "Orders › GTT loaded"),
            s("orders_sip_loaded", "Orders › SIP loaded"),
        ) + DashboardSuite.steps() + listOf(
            s("holdings_loaded", "Portfolio › Holdings loaded"),
            s("holdings_mode_toggled", "Holdings › Net P&L / Day P&L toggled"),
            s("holdings_switch_toggled", "Holdings › Stock / MTF switched and restored"),
            s("holdings_sort_opened", "Holdings › Sort sheet opened and closed"),
            s("positions_loaded", "Portfolio › Positions loaded"),
            s("positions_mode_toggled", "Positions › Net P&L / Day P&L toggled"),
            s("positions_sort_opened", "Positions › Sort sheet opened and closed"),
        ) + ProfileSuite.steps() + listOf(
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
    /** Tradion's biometric sign-in, shown instead of the password screen once biometrics were enabled for the user. */
    private val biometricLoginIds = listOf(T.BIOMETRIC_LOGIN_SCREEN, T.BIOMETRIC_LOGIN_BUTTON)
    private val loginScreenIds = userIdScreenIds + passwordScreenIds + otpScreenIds + biometricLoginIds

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
    /** The market-data feed, measured in a live-market run only. */
    private val feedMonitor by lazy { FeedMonitor(engine) }

    override fun afterStep(step: AutomationStepResult) {
        if (modules.liveMarket) feedMonitor.sample(steps)
    }

    override fun beforeComplete() {
        if (modules.liveMarket) feedMonitor.sample(steps)
        result.feed = feedMonitor.report(modules.liveMarket)
    }

    /** Kept only to sign in again after the session expires mid-run; never logged or reported. */
    private var userId = ""
    private var password = ""
    private var otp = ""

    fun run(userId: String, password: String, otp: String): AutomationRunResult {
        this.userId = userId
        this.password = password
        this.otp = otp
        Redactor.secrets = listOf(userId)
        val info = startRun(app.testName)
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
            userIdScreenIds + passwordScreenIds + otpScreenIds + biometricLoginIds + postLoginIds + bottomNavIds,
            Timeouts.USER_ID_SCREEN,
        )
        when (entry) {
            is WaitOutcome.Found -> when {
                biometricLoginIds.contains(entry.identifier) -> if (resetFromInside) {
                    val problem = leaveBiometricLogin()
                    if (engine.cancelled) return stopped(userIdScreen)
                    if (problem != null) return abort(userIdScreen, problem)
                    pass(userIdScreen, "the app opened on its biometric sign-in (biometrics were enabled earlier); Switch Account took it back to the User ID screen")
                } else {
                    return abort(
                        userIdScreen,
                        "The app opened on its biometric sign-in screen, so it still remembers a user with biometrics " +
                            "enabled even though its data was cleared. Use Switch account in the app (or clear its storage " +
                            "by hand) and start the run again.",
                    )
                }
                userIdScreenIds.contains(entry.identifier) -> pass(
                    userIdScreen,
                    "'${entry.identifier}' located" + if (engine.notificationPromptsAllowed > 0) {
                        "; the app still asked for the notification permission (answered with Allow). " +
                            "Install the current ${app.displayName} build: it skips that prompt when the tool launches it"
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
                    (if (engine.biometricPromptInFront()) " Android's biometric prompt is in front: the app opened on its " +
                        "biometric sign-in, which raises the prompt by itself. Cancel it, use Switch account, or clear the " +
                        "app's data." else "") +
                    " The splash screen checks the network and the app version first; an update " +
                    "prompt or a no-internet screen would stop it here.",
            )
        }

        if (!moduleOn("login")) skipModule("login")
        else if (modules.directLogin) skipSteps(LOGIN_DETOUR_STEPS, "direct login only")
        else {
        // 5a — Guest User, Unblock Account, Join Now ------------------------------
        val guest = step("guest_user_checked")
        if (app.lacks(TargetApp.Feature.GUEST_USER)) {
            skipAs(guest, "not_applicable", "${app.displayName} has no Guest User link on its User ID page")
        } else {
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
        }

        val unblock = step("unblock_account_checked")
        begin(unblock)
        if (app.lacks(TargetApp.Feature.UNBLOCK_WITHOUT_USER_ID)) {
            // This app opens the form only once the User ID field has text ("Please enter the User ID to unblock.").
            // The real User ID is typed; step 6 types it again over it.
            val typed = engine.enterText(T.USER_ID_INPUT, userId)
            if (!typed.ok) return abort(unblock, "Could not enter the User ID before Unblock Account: ${typed.detail}")
            if (!engine.sleep(Timeouts.SETTLE)) return stopped(unblock)
        }
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

        // 13b — Biometric enrolment confirmed -------------------------------------------------------------------
        // The enrolment screen is mandatory and has no Skip. The tool never answers the fingerprint / face / PIN
        // prompt itself: a person does, and this step watches what happens and says exactly what went wrong.
        val biometric = step("biometric_confirmed")
        begin(biometric)
        var arrivedId = afterOtpId
        if (afterOtpId != T.BIOMETRIC_SCREEN) {
            skip(biometric, "no Enable 2FA screen was offered; it appears only on a device with biometrics, for a user who has not enabled them")
        } else {
            val outcome = confirmBiometric(enableNowTapped)
            if (engine.cancelled) return stopped(biometric)
            if (outcome.problem != null) {
                biometric.cause = outcome.cause
                biometric.evidence = outcome.evidence
                biometric.recommendation = outcome.recommendation
                return abort(biometric, outcome.problem)
            }
            pass(biometric, outcome.note)
            arrivedId = engine.firstPresent(postLoginIds + bottomNavIds) ?: T.HOME_SCREEN
        }

        // 14 — Risk disclosure accepted -----------------------------------------------------------------------
        // Optional: it is raised over Home only when the server returns disclosure
        // text. Home is built first and the disclosure route is pushed a frame
        // later, so seeing Home is not proof that no dialog is coming.
        val disclosure = step("disclosure_accepted")
        begin(disclosure)
        var disclosureShown = arrivedId == T.RISK_DISCLOSURE_DIALOG
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

        // Watchlist, Order placement, then Orders, Dashboard, Portfolio, Profile, then Logout. When the session
        // expires part way through a module, the tool signs in again and runs the module again: every step that
        // already ended is passed over, so testing resumes at the step that was interrupted.
        // The operator may skip the running module (the ⏭ button): it unwinds like a stop, its unfinished steps are
        // marked NOT TESTED, and the same function runs again, passing over every step that has ended.
        engine.skipAllowed = true
        // The suites look their steps up by id, so a module the app does not have is not run at all.
        val has = AppModules.of(app).map { it.id }.toSet()
        val flowModules = listOfNotNull(
            ::runWatchlist.takeIf { "watchlist" in has },
            ::runOrderPlacement.takeIf { "order_placement" in has },
            ::runTabs,
            ::logout,
        )
        for (module in flowModules) {
            while (true) {
                val end = module()
                if (engine.skipping) {
                    skipCurrentModule()
                    // Orders sent before the skip are on a live account: those still pending are cancelled all the same.
                    if (module == ::runOrderPlacement && !engine.cancelled) orderSuite.cancelLeftoverOrders()
                    continue
                }
                if (end == null) break
                if (pendingBreak == null) return end
                resumeAfterSessionLoss()?.let { return it }
            }
        }
        engine.skipAllowed = false

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
        " If no identifier for it is listed, the installed ${app.displayName} build predates these login " +
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
                val now = engine.clock()
                if (goneSince == 0L) goneSince = now
                now - goneSince >= 1_500L
            }
        }
        if (engine.cancelled) return SideCheck("cancelled", stranded = true)
        if (!left) {
            return SideCheck(
                "Join Now was tapped but ${app.displayName} stayed in front for ${Timeouts.JOIN_NOW_LEAVES / 1000} " +
                    "seconds. The registration link opens in the browser; there may be no browser to " +
                    "open it, or the link failed to launch.",
            )
        }

        if (!engine.sleep(2_000L)) return SideCheck("cancelled", stranded = true)
        if (engine.targetInForeground()) {
            // An in-app registration page (iTrade opens Join Now in its own WebView): Back returns to the login page.
            val page = engine.firstPresent(listOf(T.WEB_VIEW_SCREEN))
            if (engine.isPresent(T.USER_ID_SCREEN)) return SideCheck(null, note = "Join Now returned to the login page by itself")
            engine.pressBack()
            val back = engine.waitForAny(userIdScreenIds, Timeouts.SCREEN_CHANGE)
            if (back !is WaitOutcome.Found) {
                return SideCheck(reasonFor(back, "The User ID screen after leaving Join Now", Timeouts.SCREEN_CHANGE), stranded = true)
            }
            return SideCheck(
                null,
                note = "${app.displayName} opened its registration page in the app" +
                    (if (page != null) " ('$page')" else "") + "; Back returned to the login page",
            )
        }
        engine.launcher.launch(engine.targetPackage)?.let {
            return SideCheck("${app.displayName} could not be brought back after Join Now: $it", stranded = true)
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
            note = "${app.displayName} handed over to the browser for registration (its page is outside what this " +
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

    /**
     * Ends the module the operator skipped: every step of it that has not ended is marked NOT TESTED, then the app is
     * brought back to its tabs (Back while a page or sheet hides them) so the next module starts from a known place.
     */
    private fun skipCurrentModule() {
        val id = lastStarted?.id?.let { modules.moduleOf(it) }
        engine.endSkip()
        if (id != null) {
            for (stepId in modules.stepIds(id)) {
                val s = step(stepId)
                if (s.status == StepStatus.PENDING || s.status == StepStatus.RUNNING) {
                    skipAs(s, "not_tested", "skipped by the tester: ${modules.label(id)} module skipped mid-run")
                }
            }
        }
        repeat(4) {
            if (engine.cancelled || engine.firstPresent(bottomNavIds) != null) return
            engine.pressBack()
            engine.sleep(700L)
        }
    }

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
    // One suite each for the whole run, so what a suite has learnt (its test instrument, the counts it compares
    // against) survives running it again after a session expiry.
    private val watchlistSuite by lazy {
        WatchlistSuite(
            flow = this,
            engine = engine,
            modules = modules,
            stopped = { stopped(it) },
            sessionLost = { sessionEnded(it, "checking ${it.name}") },
        )
    }
    private val orderSuite by lazy {
        WatchlistSuite(
            flow = this,
            engine = engine,
            modules = modules,
            stopped = { stopped(it) },
            sessionLost = { sessionEnded(it, "checking ${it.name}") },
        )
    }
    private val dashboardSuite by lazy {
        DashboardSuite(
            flow = this,
            engine = engine,
            stopped = { stopped(it) },
            sessionLost = { sessionEnded(it, "checking ${it.name}") },
        )
    }

    private val profileSuite by lazy {
        ProfileSuite(
            flow = this,
            engine = engine,
            stopped = { stopped(it) },
            sessionLost = { sessionEnded(it, "checking ${it.name}") },
            redact = { text -> if (userId.isEmpty()) text else text.replace(userId, "[user id]", ignoreCase = true) },
            off = modules.profileOff,
        )
    }

    private fun runWatchlist(): AutomationRunResult? = watchlistSuite.run()


    /** The Order placement module: the order pad per segment, orders where the settings say under the margin gate, then Pending and Order Book. */
    private fun runOrderPlacement(): AutomationRunResult? = orderSuite.runOrders()

    // ── Orders, Dashboard, Portfolio, Profile ─────────────────────────────────────────────────

    /**
     * One inner tab to check. [tabId] is tapped first (null: the page has no
     * strip); [strip] lists its siblings so a scrolled-off tab can be brought
     * into view. [chips] are in-page switches (scanner tabs) that are tapped in
     * turn when they are present; a missing chip is not an error because some
     * only exist when the server sends the section.
     */
    /**
     * Brings a tab of a sideways strip into the tree by selecting the tab nearest to it: a TabBar scrolls its selected tab
     * into view, while a scroll action can swipe the page under it instead (the Orders strip, run 39bdf01c: with Basket
     * selected, Pending and Order Book were not in the tree). True when [id] is present.
     */
    private fun revealByNeighbour(id: String, strip: List<String>): Boolean {
        val want = strip.indexOf(id)
        if (want < 0) return engine.isPresent(id)
        repeat(5) {
            if (engine.isPresent(id)) return true
            val present = strip.filter { engine.isPresent(it) }
            if (present.isEmpty()) return false
            val edge = if (want > strip.indexOf(present.last())) present.last() else present.first()
            engine.tap(edge)
            if (!engine.sleep(Timeouts.SETTLE * 3)) return false
        }
        return engine.isPresent(id)
    }

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
            .filter { tab ->
                val feature = when (tab.stepId) {
                    "orders_gtt_loaded" -> TargetApp.Feature.ORDERS_GTT_TAB
                    "orders_sip_loaded" -> TargetApp.Feature.ORDERS_SIP_TAB
                    else -> null
                }
                val absent = feature != null && app.lacks(feature)
                // Only once: the Orders page runs again after a session expiry or a skipped module.
                if (absent && moduleOn("orders") && step(tab.stepId).status == StepStatus.PENDING) {
                    skipAs(step(tab.stepId), "not_applicable", "${app.displayName}'s Orders has no such tab")
                }
                !absent
            }
        if (!moduleOn("orders")) skipModule("orders") else {
        withPage(Page("Orders", T.BOTTOMNAV_ORDER_TAB, T.ORDERBOOK_SCREEN), orderTabs.map { it.stepId }) {
            for (tab in orderTabs) checkTab(tab, budget)?.let { return@withPage it }
            null
        }?.let { return it }
        }

        // Dashboard -----------------------------------------------------------------------------------------
        // Independent test cases (DB-01 ... DB-I25): see DashboardSuite.
        if (!moduleOn("dashboard")) skipModule("dashboard") else {
            dashboardSuite.run()?.let { return it }
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

        // Profile: every row of the Account and Trade Settings tabs (see ProfileSuite) --------------------------
        if (!moduleOn("profile")) skipModule("profile") else profileSuite.run()?.let { return it }
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
        // Resumed after a session expiry: the page's steps that already ended are not run again.
        val first = stepIds.map(::step).firstOrNull { !ended(it) } ?: return null
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
            skipSteps(stepIds, "the ${page.name} tab did not open")
            return null
        }
        return body()
    }

    /** True for a step that already has its outcome, so a module run again after a session expiry passes it over. */
    private fun ended(step: AutomationStepResult): Boolean =
        step.status == StepStatus.PASSED || step.status == StepStatus.FAILED || step.status == StepStatus.SKIPPED

    // ── Biometrics ─────────────────────────────────────────────────────────────────────────────────

    /** How the biometric enrolment ended: [problem] null means confirmed, with [note]. */
    private class BiometricOutcome(
        val problem: String? = null,
        val note: String? = null,
        val cause: String? = null,
        val evidence: String? = null,
        val recommendation: String? = null,
    )

    /**
     * Waits on the Enable 2FA screen for a person to confirm Android's biometric prompt, and tells apart the ways it
     * goes wrong: the prompt never appears, the device has no fingerprint / face enrolled, the sensor is locked out
     * after too many attempts, the prompt is dismissed or fails, or nobody answers. Enable now is tapped again (at most
     * three times in all) when the screen is back with no prompt. While the prompt is up the app's window is not
     * readable, so this is a plain poll and not a wait that treats a lost window as the app having gone.
     */
    private fun confirmBiometric(alreadyTapped: Boolean): BiometricOutcome {
        val signedIn = postLoginIds + bottomNavIds
        val started = engine.clock()
        var taps = if (alreadyTapped) 1 else 0
        var prompts = 0
        var promptUp = false
        var onScreenSince = 0L
        var lockedOutUntil = 0L
        val messages = linkedSetOf<String>()
        fun tapEnable(): Boolean {
            taps++
            return engine.tap(T.BIOMETRIC_ENABLE_BUTTON, listOf("Enable now")).ok
        }
        if (!alreadyTapped && !tapEnable()) {
            return BiometricOutcome(
                "The Enable 2FA screen is showing but Enable now could not be tapped",
                cause = "automation", evidence = engine.visibleIdentifiers().joinToString(", "),
            )
        }
        engine.showNotice("${app.displayName} asks to enable biometrics: touch the fingerprint sensor (or use the phone's PIN) when Android asks.")
        try {
            val confirmed = engine.waitUntil(Timeouts.BIOMETRIC_CONFIRM) {
                if (engine.firstPresent(signedIn) != null) return@waitUntil true
                val now = engine.clock()
                if (engine.biometricPromptInFront()) {
                    if (!promptUp) prompts++
                    promptUp = true
                    onScreenSince = 0L
                    return@waitUntil false
                }
                promptUp = false
                if (!engine.isPresent(T.BIOMETRIC_SCREEN)) return@waitUntil false
                // Back on the screen with no prompt: read what the app said (it reports failures as a snackbar).
                for (line in engine.screenTexts(30)) {
                    if (line.contains("biometric", ignoreCase = true) || line.contains("locked out", ignoreCase = true) ||
                        line.contains("too many attempts", ignoreCase = true)
                    ) {
                        messages += line.trim()
                    }
                }
                if (messages.any { it.contains("No biometrics enrolled", ignoreCase = true) }) return@waitUntil true
                if (messages.any { it.contains("locked out", ignoreCase = true) } && lockedOutUntil == 0L) {
                    // Android refuses for 30 seconds after too many attempts; one more try after that.
                    lockedOutUntil = now + 31_000L
                }
                if (onScreenSince == 0L) onScreenSince = now
                val waitFor = if (lockedOutUntil > now) lockedOutUntil - now else 6_000L
                if (now - onScreenSince > waitFor && taps < 3) {
                    tapEnable()
                    onScreenSince = 0L
                }
                false
            }
            val seconds = (engine.clock() - started) / 1000
            val said = if (messages.isEmpty()) "" else " The app said: \"${messages.joinToString("\" / \"")}\"."
            if (confirmed && engine.firstPresent(signedIn) != null) {
                return BiometricOutcome(
                    note = "confirmed by hand after $seconds s; Android's prompt appeared $prompts time(s), Enable now tapped $taps time(s)" +
                        if (messages.isEmpty()) "" else "; the app showed: ${messages.joinToString(" / ")}",
                )
            }
            if (engine.cancelled) return BiometricOutcome("stopped")
            val evidence = "screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { if (engine.biometricPromptInFront()) "Android's biometric prompt" else "none" }}"
            captureScreenshot("biometric_confirmed_failure")
            return when {
                messages.any { it.contains("No biometrics enrolled", ignoreCase = true) } -> BiometricOutcome(
                    "Enable now failed because this device has biometric hardware but no fingerprint or face enrolled, " +
                        "and the Enable 2FA screen has no Skip.$said",
                    cause = "test_data", evidence = evidence,
                    recommendation = "Enrol a fingerprint (Settings > Security) on the test device, or use a device or emulator " +
                        "without biometric hardware. Developers: the screen offers no way on when enrolment is impossible.",
                )
                messages.any { it.contains("locked out", ignoreCase = true) || it.contains("too many attempts", ignoreCase = true) } -> BiometricOutcome(
                    "Android locked the biometric sensor after too many attempts and it was not confirmed within " +
                        "${Timeouts.BIOMETRIC_CONFIRM / 1000} s (Enable now tapped $taps time(s)).$said",
                    cause = "test_data", evidence = evidence,
                    recommendation = "Wait for the lock-out to end (30 s, longer after repeated lock-outs) and confirm with a " +
                        "registered finger.",
                )
                prompts == 0 -> BiometricOutcome(
                    "Enable now was tapped $taps time(s) but Android's biometric prompt never appeared.$said",
                    cause = "app_defect", evidence = evidence,
                    recommendation = "Developers: bioEnable() calls authenticate() only when getAvailableBiometrics() or " +
                        "isDeviceSupported() says so; check what they return on this device, and that a failed check " +
                        "tells the user instead of doing nothing.",
                )
                engine.biometricPromptInFront() -> BiometricOutcome(
                    "Android's biometric prompt was on screen and nobody confirmed it within ${Timeouts.BIOMETRIC_CONFIRM / 1000} s.$said",
                    cause = "test_data", evidence = evidence,
                    recommendation = "Touch the fingerprint sensor (or use the PIN) when the run reaches this screen.",
                )
                else -> BiometricOutcome(
                    "Android's biometric prompt appeared $prompts time(s) and was dismissed or not recognised each time; " +
                        "the app stayed on Enable 2FA.$said",
                    cause = "unconfirmed", evidence = evidence,
                    recommendation = "Confirm with a registered finger. If the prompt closes by itself, developers should check " +
                        "the authenticate() result handling in bioEnable().",
                )
            }
        } finally {
            engine.hideNotice()
        }
    }

    /**
     * Leaves the biometric sign-in screen for the User ID / password screens with its Switch Account link. The screen
     * raises Android's prompt by itself; while that prompt is up nothing in the app can be tapped, so it is cancelled
     * with Back first (Back goes to the prompt, not the app). Returns null on success, else what went wrong.
     */
    private fun leaveBiometricLogin(): String? {
        repeat(2) {
            if (engine.biometricPromptInFront()) {
                engine.pressBack()
                if (!engine.sleep(800L)) return "stopped"
            }
            val tap = engine.tap(T.BIOMETRIC_LOGIN_SWITCH_ACCOUNT)
            if (tap.ok && engine.waitForAny(userIdScreenIds + passwordScreenIds, Timeouts.SCREEN_CHANGE) is WaitOutcome.Found) {
                return null
            }
            if (engine.cancelled) return "stopped"
        }
        return "The app is on its biometric sign-in screen and its Switch Account link did not lead to the User ID " +
            "screen. Identifiers visible: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}."
    }

    // ── Session expiry (HTTP 401) ─────────────────────────────────────────────────────────────────

    /** A session loss waiting for [resumeAfterSessionLoss]: where it happened and which calls were refused. */
    private class SessionBreak(
        val step: AutomationStepResult,
        val context: String,
        val screen: String,
        val api: String,
        val atMs: Long,
    )

    private var sessionLosses = 0
    private var pendingBreak: SessionBreak? = null

    /** The newest refused call already put on a step, so each one the app reports is counted once. */
    private var lastUnauthorizedMs = 0L

    private class Unauthorized(val atMs: Long, val text: String)

    private fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

    /**
     * The calls the app has refused as unauthorised since the last look, read from its `api_unauthorized` marker
     * (lines of `<epoch ms>|<status>|<METHOD>|<path>`). Each is put on the step that was running when it happened,
     * which is where the report highlights it, whether or not the app then logged out.
     */
    private fun newUnauthorizedCalls(): List<Unauthorized> {
        if (!engine.isPresent(T.API_UNAUTHORIZED)) return emptyList()
        val fresh = engine.labelsOf(T.API_UNAUTHORIZED).flatMap { it.lines() }.mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 4) return@mapNotNull null
            val at = parts[0].toLongOrNull() ?: return@mapNotNull null
            if (at < result.startedAtMs || at <= lastUnauthorizedMs) return@mapNotNull null
            val status = if (parts[1] == "401") "HTTP 401" else parts[1]
            Unauthorized(at, "$status ${parts[2]} ${parts.drop(3).joinToString("|")} at ${clock(at)}")
        }.distinctBy { it.atMs to it.text }.sortedBy { it.atMs }
        fresh.lastOrNull()?.let { lastUnauthorizedMs = it.atMs }
        for (call in fresh) {
            val owner = steps.filter { (it.startedAtMs ?: Long.MAX_VALUE) <= call.atMs }.maxByOrNull { it.startedAtMs!! } ?: continue
            owner.unauthorized = listOfNotNull(owner.unauthorized, call.text).joinToString("; ")
        }
        return fresh
    }

    /**
     * Notices an unexpected return to a sign-in screen: the session expired (commonly an API answering 401). The
     * refused calls are highlighted on their steps. The first [MAX_SESSION_LOSSES] - 1 times the run unwinds to
     * [run], which signs in again and resumes from the interrupted step; the last time the run ends here.
     * Returns null while the app is still signed in.
     */
    private fun sessionEnded(
        current: AutomationStepResult,
        context: String,
    ): AutomationRunResult? {
        val calls = newUnauthorizedCalls()
        // The biometric sign-in raises Android's prompt by itself, which hides the app: a prompt in front is that screen.
        val identifier = engine.firstPresent(loginScreenIds)
            ?: (if (engine.biometricPromptInFront()) T.BIOMETRIC_LOGIN_SCREEN else null)
            ?: return null
        val screen = when (identifier) {
            in passwordScreenIds -> "Password"
            in otpScreenIds -> "OTP"
            in biometricLoginIds -> "biometric sign-in"
            else -> "User ID"
        }
        sessionLosses++
        val api = calls.joinToString("; ") { it.text }.ifEmpty {
            "not reported by the app (install a ${app.displayName} build that publishes '${T.API_UNAUTHORIZED}')"
        }
        if (current.unauthorized == null) current.unauthorized = "session ended while $context; API: $api"
        val brk = SessionBreak(current, context, screen, api, System.currentTimeMillis())
        if (sessionLosses < MAX_SESSION_LOSSES) {
            pendingBreak = brk
            return result
        }
        val reason = "SESSION EXPIRED (HTTP 401), $sessionLosses of $MAX_SESSION_LOSSES: ${app.displayName} returned to the " +
            "$screen screen while $context. API: $api. The run stops at the ${MAX_SESSION_LOSSES}rd session expiry. " +
            "This is a target-app / session failure, not a missing automation identifier: check why the server " +
            "refuses the token, and that the same account is not signed in elsewhere."
        recordBreak(brk, "run stopped: session expiry $sessionLosses of $MAX_SESSION_LOSSES")
        // A case that already ended as BLOCKED / NOT TESTED (the Back Office page in the browser) keeps that outcome: the
        // session ending afterwards is reported on the steps that could not run, not as a failure of the case.
        if (current.status != StepStatus.SKIPPED) failAndContinue(current, reason)
        result.failureMessage = reason
        skipSteps(
            steps.filter { it.status == StepStatus.PENDING }.map { it.id },
            "not run: ${app.displayName}'s session expired (HTTP 401) $MAX_SESSION_LOSSES times",
        )
        return finish()
    }

    private fun recordBreak(brk: SessionBreak, outcome: String) {
        result.sessionBreaks += mapOf(
            "number" to sessionLosses,
            "stepId" to brk.step.id,
            "stepName" to brk.step.name,
            "code" to brk.step.code,
            "context" to brk.context,
            "screen" to brk.screen,
            "api" to brk.api,
            "atMs" to brk.atMs,
            "outcome" to outcome,
        )
    }

    /**
     * Signs in again after [sessionEnded] noticed a session loss, then puts the interrupted step back in the queue
     * so the module resumes there. A step that had already ended keeps its outcome. Returns a result only when the
     * run has to end (signing in failed, or the run was stopped).
     */
    private fun resumeAfterSessionLoss(): AutomationRunResult? {
        val brk = pendingBreak ?: return null
        pendingBreak = null
        val step = brk.step
        val started = engine.clock()
        engine.showNotice("Session expired ($sessionLosses of $MAX_SESSION_LOSSES, HTTP 401). Signing in again…")
        val problem = try {
            signInAgain()
        } finally {
            engine.hideNotice()
        }
        if (engine.cancelled) return stopped(step)
        if (problem != null) {
            val reason = "SESSION EXPIRED (HTTP 401): ${app.displayName} returned to the ${brk.screen} screen while ${brk.context}. " +
                "API: ${brk.api}. Signing in again failed: $problem."
            recordBreak(brk, "signing in again failed: $problem")
            if (step.status != StepStatus.SKIPPED) failAndContinue(step, reason)
            result.failureMessage = reason
            skipSteps(
                steps.filter { it.status == StepStatus.PENDING }.map { it.id },
                "not run: ${app.displayName}'s session expired (HTTP 401) and signing in again failed",
            )
            return finish()
        }
        val seconds = (engine.clock() - started) / 1000
        val rerun = step.status == StepStatus.RUNNING || step.status == StepStatus.FAILED
        recordBreak(
            brk,
            "signed in again in $seconds s; " + if (rerun) "resumed from this step" else "this step had finished; resumed from the next one",
        )
        if (rerun) {
            if (result.failureMessage != null && result.failureMessage == step.failureReason) result.failureMessage = null
            step.resetForRerun()
        }
        return null
    }

    /**
     * Signs in again from whichever sign-in screen the app is on (biometric sign-in, User ID, password, authenticator
     * or OTP), entering the User ID, password and the run's OTP itself. Only when the app refuses that OTP (a
     * one-time OTP already spent) does a banner ask a person to type a new one. Returns null on success, else what
     * went wrong.
     */
    private fun signInAgain(): String? {
        val signedIn = postLoginIds + bottomNavIds
        if ((engine.firstPresent(biometricLoginIds) != null || engine.biometricPromptInFront()) && otp.isNotEmpty()) {
            // The run can sign in by itself with the password and OTP: no waiting for a fingerprint.
            leaveBiometricLogin()?.let { return it }
        } else if (engine.firstPresent(biometricLoginIds) != null || engine.biometricPromptInFront()) {
            // Biometrics were enabled for this user: a touch signs in again with no OTP. Otherwise fall back to the password.
            engine.showNotice(
                "Session expired ($sessionLosses of $MAX_SESSION_LOSSES, HTTP 401). Touch the fingerprint sensor (or use " +
                    "the PIN) to sign in to ${app.displayName}; in ${Timeouts.RELOGIN_BIOMETRIC / 1000} s the run switches to the password.",
            )
            val touched = engine.waitUntil(Timeouts.RELOGIN_BIOMETRIC) { engine.firstPresent(signedIn) != null }
            if (engine.cancelled) return "stopped"
            if (!touched) leaveBiometricLogin()?.let { return "the biometric sign-in was not confirmed, and $it" }
        }
        if (engine.firstPresent(userIdScreenIds) != null && engine.firstPresent(passwordScreenIds) == null) {
            val typed = engine.enterText(T.USER_ID_INPUT, userId)
            if (!typed.ok) return "the User ID could not be entered (${typed.detail})"
            if (!engine.sleep(Timeouts.SETTLE)) return "stopped"
            val login = engine.tap(T.USER_ID_LOGIN_BUTTON, Texts.LOGIN)
            if (!login.ok) return "Login could not be tapped (${login.detail})"
            if (engine.waitForAny(passwordScreenIds, Timeouts.PASSWORD_SCREEN) !is WaitOutcome.Found) {
                return "the password screen did not follow the User ID"
            }
        }
        if (engine.firstPresent(passwordScreenIds) != null) {
            val typed = engine.enterText(T.PASSWORD_INPUT, password)
            if (!typed.ok) return "the password could not be entered (${typed.detail})"
            val proceed = engine.tap(T.PASSWORD_SUBMIT_BUTTON, Texts.PROCEED)
            if (!proceed.ok) return "Proceed could not be tapped (${proceed.detail})"
            val next = engine.waitForAny(
                listOf(T.TOTP_SCREEN, T.BIOMETRIC_SCREEN) + otpScreenIds + signedIn,
                Timeouts.OTP_SCREEN,
            )
            if (next !is WaitOutcome.Found) return "the OTP screen did not follow the password (it may have been rejected)"
            if (next.identifier == T.TOTP_SCREEN) {
                val link = engine.tapAndConfirm(T.TOTP_LOGIN_WITH_OTP_LINK, otpScreenIds, Timeouts.SCREEN_CHANGE)
                if (!link.ok) return "Login with OTP could not be used (${link.detail})"
            }
        }
        if (engine.isPresent(T.TOTP_SCREEN)) {
            // The app came back on the authenticator screen: its Login with OTP link leads to the OTP screen.
            val link = engine.tapAndConfirm(T.TOTP_LOGIN_WITH_OTP_LINK, otpScreenIds, Timeouts.SCREEN_CHANGE)
            if (!link.ok) return "Login with OTP could not be used (${link.detail})"
        }
        if (engine.firstPresent(otpScreenIds) != null && otp.isNotEmpty()) {
            engine.showNotice("Session expired ($sessionLosses of $MAX_SESSION_LOSSES, HTTP 401). Entering the OTP…")
            val typed = engine.enterText(T.OTP_INPUT, otp)
            if (!typed.ok) return "the OTP could not be entered (${typed.detail})"
            // The field submits itself on the sixth digit; Confirm OTP is the fallback.
            val after = engine.waitForAny(signedIn + T.BIOMETRIC_SCREEN, Timeouts.OTP_SUBMIT / 4)
            if (after is WaitOutcome.TimedOut && engine.isTappable(T.OTP_SUBMIT_BUTTON)) {
                engine.tap(T.OTP_SUBMIT_BUTTON, Texts.CONFIRM_OTP)
                engine.waitForAny(signedIn + T.BIOMETRIC_SCREEN, Timeouts.OTP_SUBMIT)
            }
            if (engine.cancelled) return "stopped"
        }
        if (engine.firstPresent(otpScreenIds) != null) {
            // Still on the OTP screen: the app refused the run's OTP (or none was given).
            engine.showNotice(
                "Session expired ($sessionLosses of $MAX_SESSION_LOSSES, HTTP 401). The run's OTP was not accepted: type a NEW OTP into ${app.displayName} " +
                    "within ${Timeouts.RELOGIN_OTP / 60_000} min; the test resumes by itself.",
            )
            val entered = engine.waitUntil(Timeouts.RELOGIN_OTP) {
                engine.firstPresent(signedIn + T.BIOMETRIC_SCREEN) != null
            }
            if (!entered) {
                return if (engine.cancelled) "stopped" else "nobody entered the new OTP within ${Timeouts.RELOGIN_OTP / 1000} seconds"
            }
        }
        if (engine.isPresent(T.BIOMETRIC_SCREEN)) {
            engine.tap(T.BIOMETRIC_ENABLE_BUTTON, listOf("Enable now"))
            engine.showNotice("Confirm the biometric prompt in ${app.displayName}; the test resumes by itself.")
            if (!engine.waitUntil(Timeouts.BIOMETRIC_CONFIRM) { engine.firstPresent(signedIn) != null }) {
                return "the biometric enrolment was not confirmed within ${Timeouts.BIOMETRIC_CONFIRM / 1000} seconds"
            }
        }
        if (engine.waitForAny(listOf(T.RISK_DISCLOSURE_DIALOG), Timeouts.DISCLOSURE_APPEARS) is WaitOutcome.Found) {
            engine.tapUpTo(T.DISCLOSURE_UNDERSTAND_BUTTON, Texts.DISCLOSURE_UNDERSTAND, maxTimes = 6, settleMs = 700L)
            if (!engine.waitForGone(T.RISK_DISCLOSURE_DIALOG, Timeouts.SCREEN_CHANGE)) return "the risk disclosure did not close"
        }
        return when (engine.waitForAny(listOf(T.HOME_SCREEN, T.WATCHLIST_SCREEN) + bottomNavIds, Timeouts.HOME_SCREEN)) {
            is WaitOutcome.Found -> null
            else -> "Home did not appear after signing in again"
        }
    }

    /**
     * Selects [tab] (and each of its chips that is on screen) and waits for
     * every section to finish loading. A tab still loading at its deadline, or
     * showing the retry screen, fails its own step and the run moves on.
     */
    private fun checkTab(tab: TabStep, budgetMs: Long): AutomationRunResult? {
        val current = step(tab.stepId)
        if (ended(current)) return null
        if (current.status == StepStatus.PENDING) begin(current)
        sessionEnded(current, "checking ${current.name}")?.let { return it }
        if (tab.tabId != null) {
            if (!revealByNeighbour(tab.tabId, tab.strip) && !engine.revealInList(tab.tabId, tab.strip.ifEmpty { listOf(tab.tabId) })) {
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







    // ── Watchlist scenarios ───────────────────────────────────────────────────────────────────
    //
    // Nothing here keeps a change: the gear screen's radios are put back and it is left with Clear,
    // the Alert page is typed into and left with Back (Create is only checked, never tapped), and every
    // scrip a step adds is removed again.

    // Search extras: one scrip per exchange, then a FUT, CE and PE contract ------------------------------


    // ── Investment tab scenarios ──────────────────────────────────────────────────────────────
    //
    // As with the Trading tab: Buy and Sell only open the order pad (left with Back), the IPO's Apply and the
    // Alert page's Delete-less flows never touch anything else. The one thing that is created is a test alert
    // (Greater than 9999999, a value no scrip reaches), found again through the Alerts search and deleted.









    // Alerts ---------------------------------------------------------------------------------------------

    private var testAlertName: String? = null


    // IPO ------------------------------------------------------------------------------------------------


    // Vendor pages (Reports, Pledge, Back Office, Other request) -----------------------------------------


    // ETF -------------------------------------------------------------------------------------------------


    // Top Sectors and Customize ---------------------------------------------------------------------------



    // Scanners: every tab of every segment ----------------------------------------------------------------


    // ── Dashboard scenarios ───────────────────────────────────────────────────────────────────
    //
    // Each check below is one report step. None of them places, modifies or cancels anything:
    // Buy and Sell are only tapped to open the order pad, which is left with Back; Filter and sort
    // controls are tapped and then reset with the sheet's own Clear / Close; the Customize screen
    // is opened and left without a change.

    /** What one check found: [failure] null means it passed (with [note]); [skipNote] means nothing to check. */
    private class Verdict(val failure: String?, val note: String? = null, val skipNote: String? = null)

    private fun ok(note: String? = null) = Verdict(null, note)











    // 1 — the index dropdown -----------------------------------------------------------------------


    // 2 — the Trading tab ---------------------------------------------------------------------------


    // 3–6 — Trading Ideas (the Research calls screen) -----------------------------------------------


    // 12–13 — Futures › View All -------------------------------------------------------------------


    // 14 — FII / DII ----------------------------------------------------------------------------------


    // 15 — Customize ----------------------------------------------------------------------------------


    // 7–11 — Scanners (the "Screener" page) --------------------------------------------------------------


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
        if (ended(current)) return null
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
        if (ended(current)) return null
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
        if (ended(current)) return null
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
        if (out.status != StepStatus.PENDING) return null
        // PF-22 in the Profile settings: switched off, the run ends signed in.
        if ("logged_out" in modules.profileOff) {
            skipAs(out, "not_tested", "turned off in the Profile settings; the app was left signed in")
            return null
        }
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

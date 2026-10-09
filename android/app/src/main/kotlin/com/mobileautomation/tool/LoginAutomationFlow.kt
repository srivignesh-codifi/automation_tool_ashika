package com.mobileautomation.tool

import com.mobileautomation.tool.AutomationConfig.Ids
import com.mobileautomation.tool.AutomationEngine.WaitOutcome

/**
 * Phase 1: the successful login flow, extended with a post-login regression
 * pass over Watchlist and Search.
 *
 *   Client ID -> OTP -> MPIN -> Skip Biometric -> Home / Watchlist ->
 *   Disclosure accepted -> Watchlist -> Discover add-scrip -> Edit watchlist
 *   trim -> Search (TCS, NIFTY futures/options, crude oil)
 *
 * Then every bottom tab is visited — Home, Research, Portfolio, Orders — and
 * each of their tabs is checked for sections that never finish loading, and
 * the run ends by logging out from Profile.
 *
 * Deliberately absent: invalid credential cases, and anything that places,
 * modifies or cancels an order or moves funds. On Portfolio and Orders the
 * tool only selects tabs and scrolls; it never taps a row or an action button.
 * It types only the three credential values plus the fixed set of search
 * terms baked into this flow.
 *
 * Both pin screens in the target app submit automatically when their last digit
 * lands, so every submit step probes for the next screen *before* falling back
 * to tapping a submit button. Submitting an OTP twice would burn the operator's
 * one-time code.
 */
class LoginAutomationFlow(private val engine: AutomationEngine) {

    private val steps = listOf(
        AutomationStepResult("target_app_installed", "Target application installed"),
        AutomationStepResult("accessibility_enabled", "Accessibility permission enabled"),
        AutomationStepResult("target_app_data_cleared", "Target application data cleared"),
        AutomationStepResult("target_app_launched", "Target application launched"),
        AutomationStepResult("intro_login_tapped", "Intro screen Login tapped"),
        AutomationStepResult("client_id_screen_displayed", "Client ID screen displayed"),
        AutomationStepResult("client_id_input_found", "Client ID input found"),
        AutomationStepResult("client_id_entered", "Client ID entered"),
        AutomationStepResult("continue_tapped", "Continue button tapped"),
        AutomationStepResult("otp_screen_displayed", "OTP screen displayed"),
        AutomationStepResult("otp_input_found", "OTP input found"),
        AutomationStepResult("otp_entered", "OTP entered"),
        AutomationStepResult("otp_submitted", "OTP submitted"),
        AutomationStepResult("mpin_screen_displayed", "MPIN screen displayed"),
        AutomationStepResult("mpin_entered", "MPIN entered"),
        AutomationStepResult("mpin_submitted", "MPIN submitted"),
        AutomationStepResult("biometric_screen_checked", "Biometric screen checked"),
        AutomationStepResult("biometric_skipped", "Biometric skipped"),
        AutomationStepResult("home_displayed", "Home or Watchlist displayed"),
        AutomationStepResult("disclosure_accepted", "Disclosure dialog accepted"),
        AutomationStepResult("watchlist_tab_opened", "Watchlist tab opened"),
        AutomationStepResult("discover_scrip_added", "Discover scrip add dialog opened"),
        AutomationStepResult("watchlist_checkboxes_toggled", "Watchlist checkboxes toggled"),
        AutomationStepResult("watchlist_add_saved", "Add-to-watchlist saved"),
        AutomationStepResult("first_watchlist_tab_opened", "First watchlist tab opened"),
        AutomationStepResult("watchlist_edit_opened", "Watchlist edit screen opened"),
        AutomationStepResult("watchlist_scrips_trimmed", "Watchlist scrips trimmed"),
        AutomationStepResult("search_opened", "Search screen opened"),
        AutomationStepResult("search_filter_chips_listed", "Search filter chips listed"),
        AutomationStepResult("search_tcs_added", "TCS added (Stock filter)"),
        AutomationStepResult("search_nifty_futures_added", "NIFTY futures added (Futures filter)"),
        AutomationStepResult("search_nifty_options_added", "NIFTY options added (Options filter)"),
        AutomationStepResult("search_crudeoil_added", "Crude oil added (Commodity filter)"),
        AutomationStepResult("search_all_filter_results", "All filter lists results"),
        AutomationStepResult("search_mutual_fund_results", "Mutual fund results listed"),
        AutomationStepResult("search_closed", "Search screen closed"),
        AutomationStepResult("watchlist_count_updated", "Watchlist count reflects the adds"),
        AutomationStepResult("watchlist_view_toggled", "Heat map view toggled and restored"),
        AutomationStepResult("watchlist_filter_sheet_opened", "Filter & Sorting sheet opened and closed"),
        AutomationStepResult("scrip_details_opened", "Scrip details opened and closed"),
        AutomationStepResult("home_overview_loaded", "Home › Overview loaded"),
        AutomationStepResult("home_stocks_loaded", "Home › Stocks loaded"),
        AutomationStepResult("home_fno_loaded", "Home › F&O loaded"),
        AutomationStepResult("home_mutual_funds_loaded", "Home › Mutual Funds loaded"),
        AutomationStepResult("home_commodity_loaded", "Home › Commodity loaded"),
        AutomationStepResult("research_loaded", "Research loaded"),
        AutomationStepResult("holdings_overview_loaded", "Portfolio › Holdings › Overview loaded"),
        AutomationStepResult("holdings_equity_loaded", "Portfolio › Holdings › Equity loaded"),
        AutomationStepResult("holdings_thematic_loaded", "Portfolio › Holdings › Thematic Basket loaded"),
        AutomationStepResult("holdings_mutual_funds_loaded", "Portfolio › Holdings › Mutual Funds loaded"),
        AutomationStepResult("portfolio_my_wealth_loaded", "Portfolio › My Wealth loaded"),
        AutomationStepResult("orders_open_loaded", "Orders › Open loaded"),
        AutomationStepResult("orders_executed_loaded", "Orders › Executed loaded"),
        AutomationStepResult("orders_gtt_loaded", "Orders › GTT loaded"),
        AutomationStepResult("orders_sip_loaded", "Orders › SIP loaded"),
        AutomationStepResult("orders_basket_loaded", "Orders › Basket loaded"),
        AutomationStepResult("orders_alerts_loaded", "Orders › Alerts loaded"),
        AutomationStepResult("positions_loaded", "Orders › Positions loaded"),
        AutomationStepResult("profile_opened", "Profile opened"),
        AutomationStepResult("logged_out", "Logged out"),
    )

    private fun step(id: String): AutomationStepResult = steps.first { it.id == id }

    private lateinit var result: AutomationRunResult

    // Each screen is detected by its container marker OR by a control that only
    // exists on that screen. The redundancy matters: Flutter's `isImportant()`
    // returns false for a semantics node that has no label and no actions —
    // which is exactly the shape of a `*_screen` marker — so those markers reach
    // the service only because the config sets flagIncludeNotImportantViews.
    // Accepting a control identifier as well means screen detection does not
    // hinge on that single flag, nor on how Flutter chose to merge the wrapper.
    private val clientIdScreenIds = listOf(Ids.CLIENT_ID_SCREEN, Ids.CLIENT_ID_INPUT)
    private val otpScreenIds = listOf(Ids.OTP_SCREEN, Ids.OTP_INPUT)
    private val mpinScreenIds = listOf(Ids.MPIN_SCREEN, Ids.MPIN_INPUT)
    private val biometricScreenIds = listOf(Ids.BIOMETRIC_SCREEN, Ids.BIOMETRIC_SKIP_BUTTON)

    /** Identifiers that mean "login already finished". */
    private val postLoginIds = listOf(Ids.HOME_SCREEN, Ids.WATCHLIST_SCREEN, Ids.RISK_DISCLOSURE_DIALOG)

    fun run(clientId: String, otp: String, mpin: String): AutomationRunResult {
        val info = engine.launcher.inspect(engine.targetPackage)
        result = AutomationRunResult(
            runId = engine.runId,
            testName = AutomationConfig.TEST_NAME,
            targetPackage = engine.targetPackage,
            targetMainActivity = info.mainActivity,
            targetVersionName = info.versionName,
            deviceModel = engine.deviceModel(),
            deviceManufacturer = engine.deviceManufacturer(),
            androidVersion = engine.androidVersion(),
            sdkInt = engine.sdkInt(),
            startedAtMs = System.currentTimeMillis(),
            steps = steps,
        )

        engine.emit(
            "automation_started",
            mapOf(
                "testName" to AutomationConfig.TEST_NAME,
                "targetPackage" to engine.targetPackage,
                "totalSteps" to steps.size,
                "steps" to steps.map { mapOf("id" to it.id, "name" to it.name) },
            ),
        )

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
        if (!info.installed) {
            return abort(installed, "Target application is not installed")
        }
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
        // Every run starts from a fresh install state. This also resets the
        // notification permission, so the app asks again after launch; the
        // engine answers that prompt with "Allow" wherever it appears.
        val dataCleared = step("target_app_data_cleared")
        begin(dataCleared)
        val clearOutcome = engine.clearTargetAppData()
        if (!clearOutcome.ok) {
            if (engine.cancelled) return stopped(dataCleared)
            return abort(dataCleared, "Could not clear the target app's data: ${clearOutcome.detail}")
        }
        pass(dataCleared, clearOutcome.detail)

        // 4 — Target application launched -------------------------------------
        val launched = step("target_app_launched")
        begin(launched)
        engine.launcher.launch(engine.targetPackage)?.let { return abort(launched, it) }
        if (!engine.waitForTargetWindow(AutomationConfig.Timeouts.APP_LAUNCH)) {
            if (engine.cancelled) return stopped(launched)
            return abort(
                launched,
                "The target application's window did not become readable within " +
                    "${AutomationConfig.Timeouts.APP_LAUNCH / 1000} seconds of launching it.",
            )
        }
        pass(launched, "launcher activity started and its window became readable")

        // 5 — Intro screen Login tapped -----------------------------------------
        // The pre-login carousel ("Invest rightly, Switch timely" / Login
        // button) publishes no screen marker, so this step is optional by
        // design rather than by identifier: if the app is ever seen opening
        // straight onto a later screen, skip instead of failing — the same
        // tolerance biometric_skipped has for a phase the target app chose not
        // to show. Note for anyone chasing the notification-permission dialog
        // that can sit on top of this screen: ACTION_CLICK is dispatched to the
        // Login view directly rather than as a screen-coordinate gesture, so it
        // can still land even while that system dialog is drawn on top.
        val introLogin = step("intro_login_tapped")
        val alreadyPastIntro = (clientIdScreenIds + mpinScreenIds + postLoginIds).any(engine::isPresent)
        if (alreadyPastIntro) {
            introLogin.skip(
                "the app opened straight onto a later screen, so there was no intro screen to dismiss",
            )
            emitStep(introLogin)
        } else {
            begin(introLogin)
            val introOutcome = engine.waitAndTap(
                Ids.INTRO_LOGIN_BUTTON,
                AutomationConfig.Texts.LOGIN,
                AutomationConfig.Timeouts.INTRO_SCREEN,
            )
            if (!introOutcome.ok) {
                if (engine.cancelled) return stopped(introLogin)
                return abort(
                    introLogin,
                    "Could not find or tap the intro screen's Login button: ${introOutcome.detail}",
                )
            }
            pass(
                introLogin,
                introOutcome.detail + if (engine.notificationPromptsAllowed > 0) {
                    "; the notification permission prompt was answered with Allow first"
                } else {
                    ""
                },
            )
        }

        // 6 — Client ID screen displayed --------------------------------------
        val clientIdScreen = step("client_id_screen_displayed")
        begin(clientIdScreen)
        val entry = engine.waitForAny(
            clientIdScreenIds + mpinScreenIds + postLoginIds,
            AutomationConfig.Timeouts.CLIENT_ID_SCREEN,
        )
        when (entry) {
            is WaitOutcome.Found -> when {
                clientIdScreenIds.contains(entry.identifier) ->
                    pass(clientIdScreen, "'${entry.identifier}' located")
                mpinScreenIds.contains(entry.identifier) -> return abort(
                    clientIdScreen,
                    "The app opened straight onto the MPIN screen, so it still has a stored " +
                        "session for a previous login. Force stop the UAT app (or switch account) " +
                        "and start the run again.",
                )
                else -> return abort(
                    clientIdScreen,
                    "The app opened already logged in ('${entry.identifier}' is displayed). " +
                        "Log out or force stop the UAT app and start the run again.",
                )
            }
            is WaitOutcome.Cancelled -> return stopped(clientIdScreen)
            else -> return abort(clientIdScreen, reasonFor(entry, "The Client ID screen", AutomationConfig.Timeouts.CLIENT_ID_SCREEN))
        }

        // 7 — Client ID input found -------------------------------------------
        val clientIdInput = step("client_id_input_found")
        begin(clientIdInput)
        if (!engine.isPresent(Ids.CLIENT_ID_INPUT)) {
            return abort(
                clientIdInput,
                "The Client ID screen is displayed but no node carries the identifier " +
                    "'${Ids.CLIENT_ID_INPUT}'.",
            )
        }
        pass(clientIdInput, "'${Ids.CLIENT_ID_INPUT}' located")

        // 8 — Client ID entered ------------------------------------------------
        val clientIdEntered = step("client_id_entered")
        begin(clientIdEntered)
        val clientIdOutcome = engine.enterText(Ids.CLIENT_ID_INPUT, clientId)
        if (!clientIdOutcome.ok) {
            return abort(clientIdEntered, "Could not enter the Client ID: ${clientIdOutcome.detail}")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(clientIdEntered)
        val clientIdLengthOk = engine.verifyLength(Ids.CLIENT_ID_INPUT, clientId.length)
        if (clientIdLengthOk == false) {
            return abort(
                clientIdEntered,
                "The Client ID field did not accept the whole value (a length check after entry " +
                    "did not match). An input formatter on the field may be rejecting it.",
            )
        }
        pass(
            clientIdEntered,
            clientIdOutcome.detail + if (clientIdLengthOk == true) ", length verified" else "",
        )

        // 9 — Continue button tapped ------------------------------------------
        val continueTapped = step("continue_tapped")
        begin(continueTapped)
        val continueOutcome = engine.tap(Ids.CLIENT_ID_CONTINUE_BUTTON, AutomationConfig.Texts.CONTINUE)
        if (!continueOutcome.ok) {
            return abort(continueTapped, "Could not tap Continue: ${continueOutcome.detail}")
        }
        pass(continueTapped, continueOutcome.detail)

        // 10 — OTP screen displayed --------------------------------------------
        val otpScreen = step("otp_screen_displayed")
        begin(otpScreen)
        when (val outcome = engine.waitForAny(otpScreenIds, AutomationConfig.Timeouts.OTP_SCREEN)) {
            is WaitOutcome.Found -> pass(otpScreen, "'${outcome.identifier}' located")
            is WaitOutcome.Cancelled -> return stopped(otpScreen)
            else -> return abort(
                otpScreen,
                reasonFor(outcome, "The OTP screen", AutomationConfig.Timeouts.OTP_SCREEN) +
                    " The Client ID may have been rejected, or the OTP request may have failed.",
            )
        }

        // 11 — OTP input found --------------------------------------------------
        val otpInput = step("otp_input_found")
        begin(otpInput)
        if (!engine.isPresent(Ids.OTP_INPUT)) {
            return abort(
                otpInput,
                "The OTP screen is displayed but no node carries the identifier '${Ids.OTP_INPUT}'.",
            )
        }
        pass(otpInput, "'${Ids.OTP_INPUT}' located")

        // 12 — OTP entered -----------------------------------------------------
        val otpEntered = step("otp_entered")
        begin(otpEntered)
        val otpOutcome = engine.enterText(Ids.OTP_INPUT, otp)
        if (!otpOutcome.ok) {
            return abort(otpEntered, "Could not enter the OTP: ${otpOutcome.detail}")
        }
        pass(otpEntered, otpOutcome.detail)

        // 13 — OTP submitted ---------------------------------------------------
        // The pin field submits itself on the last digit. Probe for the MPIN
        // screen first so a manual tap can never double submit the OTP.
        val otpSubmitted = step("otp_submitted")
        begin(otpSubmitted)
        var sawMpinScreen = false
        val autoOtp = engine.waitForAny(mpinScreenIds, AutomationConfig.Timeouts.AUTO_SUBMIT_PROBE)
        when (autoOtp) {
            is WaitOutcome.Found -> {
                sawMpinScreen = true
                pass(otpSubmitted, "submitted automatically when the final digit was entered")
            }
            is WaitOutcome.Cancelled -> return stopped(otpSubmitted)
            is WaitOutcome.TargetWindowLost -> return abort(
                otpSubmitted,
                reasonFor(autoOtp, "the screen after the OTP", 0),
            )
            is WaitOutcome.TimedOut -> {
                if (!engine.isPresent(Ids.OTP_SUBMIT_BUTTON)) {
                    return abort(
                        otpSubmitted,
                        "The OTP did not auto submit and no '${Ids.OTP_SUBMIT_BUTTON}' is present " +
                            "to tap.",
                    )
                }
                val submitOutcome = engine.tap(Ids.OTP_SUBMIT_BUTTON, AutomationConfig.Texts.OTP_SUBMIT)
                if (!submitOutcome.ok) {
                    return abort(otpSubmitted, "Could not submit the OTP: ${submitOutcome.detail}")
                }
                pass(otpSubmitted, submitOutcome.detail)
            }
        }

        // 14 — MPIN screen displayed -------------------------------------------
        val mpinScreen = step("mpin_screen_displayed")
        begin(mpinScreen)
        if (sawMpinScreen) {
            pass(mpinScreen, "MPIN screen located")
        } else {
            when (val outcome = engine.waitForAny(mpinScreenIds, AutomationConfig.Timeouts.MPIN_SCREEN)) {
                is WaitOutcome.Found -> pass(mpinScreen, "'${outcome.identifier}' located")
                is WaitOutcome.Cancelled -> return stopped(mpinScreen)
                else -> return abort(
                    outcome = outcome,
                    step = mpinScreen,
                    what = "The MPIN screen",
                    timeoutMs = AutomationConfig.Timeouts.MPIN_SCREEN,
                    extra = " The OTP may have been rejected. If the test account has biometric " +
                        "login enabled, the system biometric prompt can also sit on top of this " +
                        "screen, which makes it unreadable to the tool.",
                )
            }
        }

        // 15 — MPIN entered ----------------------------------------------------
        // The tool supports both MPIN implementations and picks by inspection.
        val mpinEntered = step("mpin_entered")
        begin(mpinEntered)
        val mpinOutcome = when {
            engine.isPresent(Ids.MPIN_INPUT) -> {
                val o = engine.enterText(Ids.MPIN_INPUT, mpin)
                if (o.ok) {
                    AutomationEngine.ActionOutcome(true, "detected a text input; ${o.detail}")
                } else {
                    o
                }
            }
            engine.hasCustomMpinKeypad() -> {
                val o = engine.tapKeypadDigits(mpin)
                if (o.ok) {
                    AutomationEngine.ActionOutcome(true, "detected a custom numeric keypad; ${o.detail}")
                } else {
                    o
                }
            }
            else -> AutomationEngine.ActionOutcome(
                false,
                "the MPIN screen exposes neither '${Ids.MPIN_INPUT}' nor '${Ids.MPIN_KEY_PREFIX}<digit>' nodes",
            )
        }
        if (!mpinOutcome.ok) {
            return abort(mpinEntered, "Could not enter the MPIN: ${mpinOutcome.detail}")
        }
        pass(mpinEntered, mpinOutcome.detail)

        // 16 — MPIN submitted --------------------------------------------------
        val mpinSubmitted = step("mpin_submitted")
        begin(mpinSubmitted)
        var submitNote = "submitted automatically when the final digit was entered"
        if (engine.isPresent(Ids.MPIN_SUBMIT_BUTTON)) {
            val o = engine.tap(Ids.MPIN_SUBMIT_BUTTON)
            if (!o.ok) return abort(mpinSubmitted, "Could not submit the MPIN: ${o.detail}")
            submitNote = o.detail
        }
        val postMpin = engine.waitForAny(
            biometricScreenIds + postLoginIds,
            AutomationConfig.Timeouts.MPIN_SCREEN,
        )
        val postMpinId = when (postMpin) {
            is WaitOutcome.Found -> {
                pass(mpinSubmitted, submitNote)
                postMpin.identifier
            }
            is WaitOutcome.Cancelled -> return stopped(mpinSubmitted)
            else -> return abort(
                outcome = postMpin,
                step = mpinSubmitted,
                what = "the screen after the MPIN",
                timeoutMs = AutomationConfig.Timeouts.MPIN_SCREEN,
                extra = " The MPIN may have been rejected.",
            )
        }

        // 17 — Biometric screen checked ----------------------------------------
        val biometricChecked = step("biometric_screen_checked")
        val biometricSkipped = step("biometric_skipped")
        begin(biometricChecked)
        val biometricShown = biometricScreenIds.contains(postMpinId)
        if (biometricShown) {
            pass(biometricChecked, "'$postMpinId' displayed")
        } else {
            pass(
                biometricChecked,
                "not displayed — the app went straight to '$postMpinId', which is a valid " +
                    "outcome when biometric enrolment was already offered, already enabled, or " +
                    "is unsupported on this device",
            )
        }

        // 18 — Biometric skipped -----------------------------------------------
        if (!biometricShown) {
            biometricSkipped.skip("no biometric screen appeared, so there was nothing to skip")
            emitStep(biometricSkipped)
        } else {
            begin(biometricSkipped)
            val skipOutcome = engine.tap(Ids.BIOMETRIC_SKIP_BUTTON, AutomationConfig.Texts.BIOMETRIC_SKIP)
            if (!skipOutcome.ok) {
                return abort(
                    biometricSkipped,
                    "The biometric screen is displayed but Skip could not be tapped: " +
                        "${skipOutcome.detail}. This tool never automates fingerprint or face " +
                        "authentication, so the run cannot continue past this point.",
                )
            }
            pass(biometricSkipped, skipOutcome.detail)
        }

        // 19 — Home or Watchlist displayed -------------------------------------
        val home = step("home_displayed")
        begin(home)
        val finalOutcome = engine.waitForAny(postLoginIds, AutomationConfig.Timeouts.HOME_SCREEN)
        when (finalOutcome) {
            is WaitOutcome.Found -> if (finalOutcome.identifier == Ids.RISK_DISCLOSURE_DIALOG) {
                pass(
                    home,
                    "the risk disclosure dialog is covering Home. That dialog is only raised " +
                        "after a successful login, and its modal barrier hides the Home semantics " +
                        "from the accessibility tree, so this counts as Home reached.",
                )
            } else {
                pass(home, "'${finalOutcome.identifier}' located")
            }
            is WaitOutcome.Cancelled -> return stopped(home)
            else -> return abort(
                outcome = finalOutcome,
                step = home,
                what = "The Home or Watchlist screen",
                timeoutMs = AutomationConfig.Timeouts.HOME_SCREEN,
            )
        }

        // 20 — Disclosure dialog accepted --------------------------------------
        val disclosureAccepted = step("disclosure_accepted")
        if (!engine.isPresent(Ids.RISK_DISCLOSURE_DIALOG)) {
            disclosureAccepted.skip(
                "the risk disclosure dialog did not appear, so there was nothing to accept",
            )
            emitStep(disclosureAccepted)
        } else {
            begin(disclosureAccepted)
            val disclosureTaps = engine.tapUpTo(
                Ids.DISCLOSURE_UNDERSTAND_BUTTON,
                AutomationConfig.Texts.DISCLOSURE_UNDERSTAND,
                maxTimes = 5,
                settleMs = AutomationConfig.Timeouts.SETTLE,
            )
            if (engine.cancelled) return stopped(disclosureAccepted)
            if (disclosureTaps == 0) {
                return abort(
                    disclosureAccepted,
                    "The risk disclosure dialog is displayed but its confirm button " +
                        "('${Ids.DISCLOSURE_UNDERSTAND_BUTTON}') could not be tapped even once.",
                )
            }
            pass(disclosureAccepted, "confirmed across $disclosureTaps page(s)")
        }

        // 21 — Watchlist tab opened ---------------------------------------------
        val watchlistTabOpened = step("watchlist_tab_opened")
        begin(watchlistTabOpened)
        val watchlistTabOutcome = engine.waitAndTap(
            Ids.BOTTOMNAV_WATCHLIST_TAB,
            AutomationConfig.Texts.WATCHLIST_NAV,
            AutomationConfig.Timeouts.WATCHLIST_NAV,
        )
        if (!watchlistTabOutcome.ok) {
            if (engine.cancelled) return stopped(watchlistTabOpened)
            return abort(watchlistTabOpened, "Could not tap the Watchlist tab: ${watchlistTabOutcome.detail}")
        }
        pass(watchlistTabOpened, watchlistTabOutcome.detail)

        // 22 — Discover scrip add dialog opened ---------------------------------
        val discoverScripAdded = step("discover_scrip_added")
        begin(discoverScripAdded)
        val discoverAddOutcome = engine.waitAndTap(
            Ids.DISCOVER_SCRIP_ADD_BUTTON,
            emptyList(),
            AutomationConfig.Timeouts.DISCOVER_ADD,
        )
        if (!discoverAddOutcome.ok) {
            if (engine.cancelled) return stopped(discoverScripAdded)
            return abort(
                discoverScripAdded,
                "Could not tap Discover's add-scrip button: ${discoverAddOutcome.detail}",
            )
        }
        pass(discoverScripAdded, discoverAddOutcome.detail)

        // 23 — Watchlist checkboxes toggled --------------------------------------
        val checkboxesToggled = step("watchlist_checkboxes_toggled")
        begin(checkboxesToggled)
        val checkboxIds = (1..4).map { "${Ids.WATCHLIST_CHECKBOX_PREFIX}$it" }
        val checkboxTaps = engine.tapEach(checkboxIds, AutomationConfig.Timeouts.SETTLE)
        if (engine.cancelled) return stopped(checkboxesToggled)
        if (checkboxTaps == 0) {
            return abort(
                checkboxesToggled,
                "None of the watchlist checkboxes (${checkboxIds.joinToString()}) could be tapped.",
            )
        }
        pass(checkboxesToggled, "toggled $checkboxTaps of ${checkboxIds.size} rows")

        // 24 — Add-to-watchlist saved --------------------------------------------
        val addSaved = step("watchlist_add_saved")
        begin(addSaved)
        val addSaveOutcome = engine.waitAndTap(
            Ids.WATCHLIST_ADD_SAVE_BUTTON,
            emptyList(),
            AutomationConfig.Timeouts.WATCHLIST_ADD_SAVE,
        )
        if (!addSaveOutcome.ok) {
            if (engine.cancelled) return stopped(addSaved)
            return abort(addSaved, "Could not tap Save on the add-to-watchlist dialog: ${addSaveOutcome.detail}")
        }
        pass(addSaved, addSaveOutcome.detail)

        // 25 — First watchlist tab opened -----------------------------------------
        // Confirmed by the "N/50 Scrips" label: only a user watchlist tab shows
        // it, Discover does not.
        val firstTabOpened = step("first_watchlist_tab_opened")
        begin(firstTabOpened)
        val firstTabOutcome = engine.tapAndConfirm(
            Ids.WATCHLIST_FIRST_TAB,
            listOf(Ids.WATCHLIST_SCRIP_COUNT),
            AutomationConfig.Timeouts.WATCHLIST_FIRST_TAB,
            tapAtLeastOnce = true,
        )
        if (!firstTabOutcome.ok) {
            if (engine.cancelled) return stopped(firstTabOpened)
            return abort(firstTabOpened, "Could not open the first watchlist tab: ${firstTabOutcome.detail}")
        }
        pass(firstTabOpened, firstTabOutcome.detail)

        // 26 — Watchlist edit screen opened ---------------------------------------
        val editOpened = step("watchlist_edit_opened")
        begin(editOpened)
        // Let the tab's rows land before taking the baseline the trim is checked against.
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(editOpened)
        val countBeforeEdit = readScripCount()
        val editOpenOutcome = engine.tapAndConfirm(
            Ids.WATCHLIST_EDIT_BUTTON,
            listOf(Ids.EDIT_WATCHLIST_SCREEN),
            AutomationConfig.Timeouts.WATCHLIST_EDIT,
        )
        if (!editOpenOutcome.ok) {
            if (engine.cancelled) return stopped(editOpened)
            return abort(editOpened, "Could not open Edit Watchlist: ${editOpenOutcome.detail}")
        }
        pass(editOpened, editOpenOutcome.detail + countNote(countBeforeEdit))

        // 27 — Watchlist scrips trimmed --------------------------------------------
        // Row deletes are staged and only sent on Save. With nothing to delete,
        // Back closes the screen (it pops itself when nothing changed).
        val scripsTrimmed = step("watchlist_scrips_trimmed")
        begin(scripsTrimmed)
        val hasRows = engine.waitForAny(
            listOf(Ids.EDIT_WATCHLIST_DELETE_BUTTON),
            AutomationConfig.Timeouts.EDIT_ROWS,
        ) is WaitOutcome.Found
        if (engine.cancelled) return stopped(scripsTrimmed)
        val countAfterTrim: Int?
        if (!hasRows) {
            if (!closeWithBack(Ids.EDIT_WATCHLIST_SCREEN)) {
                if (engine.cancelled) return stopped(scripsTrimmed)
                return abort(scripsTrimmed, "The watchlist is empty, but Edit Watchlist did not close on Back.")
            }
            countAfterTrim = countBeforeEdit
            scripsTrimmed.skip("the watchlist is empty, so there was nothing to delete; closed Edit Watchlist")
            emitStep(scripsTrimmed)
        } else {
            val deletes = engine.tapUpTo(
                Ids.EDIT_WATCHLIST_DELETE_BUTTON,
                maxTimes = 6,
                settleMs = AutomationConfig.Timeouts.SETTLE,
            )
            if (engine.cancelled) return stopped(scripsTrimmed)
            val saveOutcome = engine.tap(Ids.EDIT_WATCHLIST_SAVE_BUTTON)
            if (!saveOutcome.ok) {
                return abort(scripsTrimmed, "Deleted $deletes scrip(s) but could not tap Save: ${saveOutcome.detail}")
            }
            if (!engine.waitForGone(Ids.EDIT_WATCHLIST_SCREEN, AutomationConfig.Timeouts.SCREEN_CHANGE)) {
                if (engine.cancelled) return stopped(scripsTrimmed)
                return abort(scripsTrimmed, "Tapped Save after deleting $deletes scrip(s), but Edit Watchlist did not close.")
            }
            val expected = countBeforeEdit?.minus(deletes)
            countAfterTrim = waitForScripCount(expected)
            if (engine.cancelled) return stopped(scripsTrimmed)
            if (expected != null && countAfterTrim != expected) {
                return abort(
                    scripsTrimmed,
                    "Deleted $deletes of $countBeforeEdit scrip(s) and saved, so the watchlist should hold " +
                        "$expected, but its count label shows ${countAfterTrim ?: "nothing readable"}.",
                )
            }
            pass(scripsTrimmed, "deleted $deletes scrip(s) and saved" + countNote(countAfterTrim))
        }

        // 28 — Search screen opened -----------------------------------------------
        val searchOpened = step("search_opened")
        begin(searchOpened)
        val searchOpenOutcome = engine.tapAndConfirm(
            Ids.WATCHLIST_SEARCH_BUTTON,
            listOf(Ids.SEARCH_SCREEN),
            AutomationConfig.Timeouts.SEARCH_SCREEN,
        )
        if (!searchOpenOutcome.ok) {
            if (engine.cancelled) return stopped(searchOpened)
            return abort(searchOpened, "Could not open Search: ${searchOpenOutcome.detail}")
        }
        when (val input = engine.waitForAny(listOf(Ids.SEARCH_SCRIP_INPUT), AutomationConfig.Timeouts.SCREEN_CHANGE)) {
            is WaitOutcome.Found -> Unit
            is WaitOutcome.Cancelled -> return stopped(searchOpened)
            else -> return abort(searchOpened, reasonFor(input, "The search field", AutomationConfig.Timeouts.SCREEN_CHANGE))
        }
        pass(searchOpened, searchOpenOutcome.detail)

        // 29 — Search filter chips listed --------------------------------------------
        val chipsListed = step("search_filter_chips_listed")
        begin(chipsListed)
        val missingChips = Ids.SEARCH_FILTER_CHIPS.filterNot { revealChip(it) }
        if (engine.cancelled) return stopped(chipsListed)
        if (missingChips.isNotEmpty()) {
            return abort(
                chipsListed,
                "These search filter chips are missing: ${missingChips.joinToString()}. Expected All, Stock, " +
                    "Futures, Options, Commodity and Mutual Funds.",
            )
        }
        pass(chipsListed, "All, Stock, Futures, Options, Commodity and Mutual Funds are all offered")

        // 30–33 — One add per trading filter --------------------------------------------
        // Each query is allowed to settle before the next one starts, so a slow
        // earlier response can never overwrite the results being acted on.
        var added = 0
        for (search in listOf(
            SearchAdd("search_tcs_added", Ids.SEARCH_FILTER_CHIP_STOCK, "Stock", "tcs", "TCS"),
            SearchAdd("search_nifty_futures_added", Ids.SEARCH_FILTER_CHIP_FUTURES, "Futures", "nifty", "NIFTY futures"),
            SearchAdd("search_nifty_options_added", Ids.SEARCH_FILTER_CHIP_OPTIONS, "Options", null, "NIFTY options"),
            SearchAdd("search_crudeoil_added", Ids.SEARCH_FILTER_CHIP_COMMODITY, "Commodity", "crudeoil", "crude oil"),
        )) {
            val searchStep = step(search.stepId)
            begin(searchStep)
            searchAndAdd(search)?.let { failure ->
                if (engine.cancelled) return stopped(searchStep)
                return abort(searchStep, failure)
            }
            added++
            pass(searchStep, "added the first ${search.what} result under the ${search.chipName} filter")
        }

        // 34 — All filter lists results -------------------------------------------------
        val allResults = step("search_all_filter_results")
        begin(allResults)
        tapChip(Ids.SEARCH_FILTER_CHIP_ALL, "All")?.let { return abort(allResults, it) }
        waitForResults(Ids.SEARCH_RESULTS_LIST, "'crudeoil' under All")?.let { failure ->
            if (engine.cancelled) return stopped(allResults)
            return abort(allResults, failure)
        }
        pass(allResults, "switching back to All re-ran the query and listed results")

        // 35 — Mutual fund results listed --------------------------------------------------
        // Typed first, while still on a scrip filter, so the switch to Mutual
        // Funds sends exactly one fund query.
        val mfResults = step("search_mutual_fund_results")
        begin(mfResults)
        val mfEntry = engine.enterText(Ids.SEARCH_SCRIP_INPUT, "axis")
        if (!mfEntry.ok) return abort(mfResults, "Could not enter 'axis' in the search field: ${mfEntry.detail}")
        if (!waitForSearchToSettle()) return stopped(mfResults)
        tapChip(Ids.SEARCH_FILTER_CHIP_MUTUAL_FUNDS, "Mutual Funds")?.let { return abort(mfResults, it) }
        waitForResults(Ids.MF_SEARCH_RESULTS, "'axis' under Mutual Funds")?.let { failure ->
            if (engine.cancelled) return stopped(mfResults)
            return abort(mfResults, failure)
        }
        pass(mfResults, "the Mutual Funds filter switched to fund search and listed funds for 'axis'")

        // 36 — Search screen closed ---------------------------------------------------
        val searchClosed = step("search_closed")
        begin(searchClosed)
        val closeOutcome = engine.tapAndConfirm(
            Ids.SEARCH_BACK_BUTTON,
            listOf(Ids.WATCHLIST_SCRIP_COUNT),
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!closeOutcome.ok || !engine.waitForGone(Ids.SEARCH_SCREEN, AutomationConfig.Timeouts.SCREEN_CHANGE)) {
            if (engine.cancelled) return stopped(searchClosed)
            return abort(searchClosed, "Search did not close from its back arrow: ${closeOutcome.detail}")
        }
        pass(searchClosed, "closed from the search field's back arrow")

        // 37 — Watchlist count reflects the adds ------------------------------------------
        val countUpdated = step("watchlist_count_updated")
        begin(countUpdated)
        val expectedCount = countAfterTrim?.plus(added)
        val countNow = waitForScripCount(expectedCount)
        if (engine.cancelled) return stopped(countUpdated)
        if (expectedCount == null || countNow != expectedCount) {
            return abort(
                countUpdated,
                "After adding $added scrip(s) to a watchlist of ${countAfterTrim ?: "unknown size"}, its count " +
                    "label should show ${expectedCount ?: "a larger number"}, but shows ${countNow ?: "nothing readable"}.",
            )
        }
        pass(countUpdated, "the watchlist went from $countAfterTrim to $countNow scrips")

        // 38 — Heat map view toggled and restored -------------------------------------------
        val viewToggled = step("watchlist_view_toggled")
        begin(viewToggled)
        val startLayout = engine.firstPresent(listOf(Ids.WATCHLIST_LIST_VIEW, Ids.WATCHLIST_GRID_VIEW))
        if (startLayout == null) {
            return abort(
                viewToggled,
                "Neither the list nor the heat map layout is on screen. Identifiers on screen: " +
                    engine.visibleIdentifiers().joinToString(", ") + ".",
            )
        }
        val otherLayout = if (startLayout == Ids.WATCHLIST_LIST_VIEW) Ids.WATCHLIST_GRID_VIEW else Ids.WATCHLIST_LIST_VIEW
        for (layout in listOf(otherLayout, startLayout)) {
            val toggle = engine.tapAndConfirm(
                Ids.WATCHLIST_VIEW_TOGGLE_BUTTON,
                listOf(layout),
                AutomationConfig.Timeouts.SCREEN_CHANGE,
            )
            if (!toggle.ok) {
                if (engine.cancelled) return stopped(viewToggled)
                return abort(viewToggled, "The layout toggle did not switch to '$layout': ${toggle.detail}")
            }
        }
        pass(viewToggled, "switched to ${layoutName(otherLayout)} and back to ${layoutName(startLayout)}")

        // 39 — Filter & Sorting sheet opened and closed ---------------------------------------
        val filterSheet = step("watchlist_filter_sheet_opened")
        begin(filterSheet)
        val sheetOutcome = engine.tapAndConfirm(
            Ids.WATCHLIST_FILTER_BUTTON,
            listOf(Ids.WATCHLIST_FILTER_SHEET),
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!sheetOutcome.ok) {
            if (engine.cancelled) return stopped(filterSheet)
            return abort(filterSheet, "The Filter & Sorting sheet did not open: ${sheetOutcome.detail}")
        }
        if (!closeWithBack(Ids.WATCHLIST_FILTER_SHEET)) {
            if (engine.cancelled) return stopped(filterSheet)
            return abort(filterSheet, "The Filter & Sorting sheet opened but did not close on Back.")
        }
        pass(filterSheet, "opened from the filter icon and closed with Back, nothing applied")

        // 40 — Scrip details opened and closed ------------------------------------------------
        val detailsOpened = step("scrip_details_opened")
        begin(detailsOpened)
        val detailScreens = listOf(Ids.SCRIP_DETAILS_SCREEN, Ids.INDEX_DETAILS_SCREEN)
        val detailsOutcome = engine.tapAndConfirm(
            Ids.WATCHLIST_SCRIP_ROW,
            detailScreens,
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!detailsOutcome.ok) {
            if (engine.cancelled) return stopped(detailsOpened)
            return abort(detailsOpened, "Tapping the first watchlist row did not open its details: ${detailsOutcome.detail}")
        }
        val openedScreen = engine.firstPresent(detailScreens) ?: Ids.SCRIP_DETAILS_SCREEN
        if (!closeWithBack(openedScreen) ||
            engine.waitForAny(listOf(Ids.WATCHLIST_SCRIP_COUNT), AutomationConfig.Timeouts.SCREEN_CHANGE) !is WaitOutcome.Found
        ) {
            if (engine.cancelled) return stopped(detailsOpened)
            return abort(detailsOpened, "The details screen opened but Back did not return to the watchlist.")
        }
        pass(detailsOpened, "opened '$openedScreen' from the first row and returned to the watchlist")

        // 41–58 — Every bottom tab and inner tab finishes loading ------------------------
        // Read-only: only bottom tabs, inner tab headers and scrolling are
        // touched — never a row, nor a buy, sell, exit or order button. A tab
        // still loading at its deadline, or showing the Reload screen, fails
        // its own step and the run moves on, so one slow tab cannot hide the
        // state of the others and the run still ends logged out.
        val tabLoad = AutomationConfig.Timeouts.TAB_LOAD
        val ordersLoad = AutomationConfig.Timeouts.ORDERS_TAB_LOAD
        val sections = listOf(
            Section(
                "Home", Ids.BOTTOMNAV_HOME_TAB, Ids.DASHBOARD_SCREEN,
                listOf(
                    TabCheck("home_overview_loaded", listOf("${Ids.HOME_TAB_PREFIX}overview"), tabLoad),
                    TabCheck("home_stocks_loaded", listOf("${Ids.HOME_TAB_PREFIX}stocks"), tabLoad),
                    TabCheck("home_fno_loaded", listOf("${Ids.HOME_TAB_PREFIX}f_o"), tabLoad),
                    TabCheck("home_mutual_funds_loaded", listOf("${Ids.HOME_TAB_PREFIX}mutual_funds"), tabLoad),
                    TabCheck("home_commodity_loaded", listOf("${Ids.HOME_TAB_PREFIX}commodity"), tabLoad),
                ),
            ),
            Section(
                "Research", Ids.BOTTOMNAV_RESEARCH_TAB, Ids.RESEARCH_SCREEN,
                listOf(TabCheck("research_loaded", emptyList(), tabLoad)),
            ),
            Section(
                "Portfolio", Ids.BOTTOMNAV_PORTFOLIO_TAB, Ids.PORTFOLIO_SCREEN,
                listOf(
                    holdingsTab("holdings_overview_loaded", "overview", tabLoad),
                    holdingsTab("holdings_equity_loaded", "equity", tabLoad),
                    holdingsTab("holdings_thematic_loaded", "thematic_basket", tabLoad),
                    holdingsTab("holdings_mutual_funds_loaded", "mutual_funds", tabLoad),
                    TabCheck("portfolio_my_wealth_loaded", listOf("${Ids.PORTFOLIO_TAB_PREFIX}my_wealth"), tabLoad),
                ),
            ),
            Section(
                "Orders", Ids.BOTTOMNAV_ORDER_TAB, Ids.ORDERBOOK_SCREEN,
                listOf("open", "executed", "gtt", "sip", "basket", "alerts").map { tab ->
                    TabCheck(
                        "orders_${tab}_loaded",
                        listOf("${Ids.ORDERBOOK_TAB_PREFIX}orders", "${Ids.ORDERS_TAB_PREFIX}$tab"),
                        ordersLoad,
                    )
                } + TabCheck("positions_loaded", listOf("${Ids.ORDERBOOK_TAB_PREFIX}positions"), ordersLoad),
            ),
        )
        for (section in sections) {
            checkSection(section)?.let { return it }
        }

        // 59 — Profile opened ---------------------------------------------------------------
        // Orders is on screen now, and its header carries the profile icon.
        val profileOpened = step("profile_opened")
        begin(profileOpened)
        val profileOutcome = engine.tapAndConfirm(
            Ids.PROFILE_BUTTON,
            listOf(Ids.PROFILE_SCREEN),
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!profileOutcome.ok) {
            if (engine.cancelled) return stopped(profileOpened)
            return abort(profileOpened, "Could not open Profile from the header icon: ${profileOutcome.detail}")
        }
        pass(profileOpened, profileOutcome.detail)

        // 60 — Logged out ---------------------------------------------------------------------
        // Logout sits at the bottom of Profile, below the fold.
        val loggedOut = step("logged_out")
        begin(loggedOut)
        var scrolls = 0
        while (!engine.isPresent(Ids.PROFILE_LOGOUT_BUTTON) && scrolls < 8 && engine.scrollPage(forward = true)) {
            scrolls++
            if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(loggedOut)
        }
        val logoutOutcome = engine.tapAndConfirm(
            Ids.PROFILE_LOGOUT_BUTTON,
            listOf(Ids.LOGOUT_CONFIRM_BUTTON),
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!logoutOutcome.ok) {
            if (engine.cancelled) return stopped(loggedOut)
            return abort(loggedOut, "Could not open the logout confirmation: ${logoutOutcome.detail}")
        }
        // No retry: the dialog stays up for the whole logout request, and a
        // second OK would send a second logout.
        val confirmOutcome = engine.tapAndConfirm(
            Ids.LOGOUT_CONFIRM_BUTTON,
            listOf(Ids.CLIENT_ID_SCREEN),
            AutomationConfig.Timeouts.LOGOUT,
            retryAfterMs = AutomationConfig.Timeouts.LOGOUT,
        )
        if (!confirmOutcome.ok) {
            if (engine.cancelled) return stopped(loggedOut)
            return abort(loggedOut, "Confirmed logout, but the Client ID screen never appeared: ${confirmOutcome.detail}")
        }
        pass(loggedOut, "confirmed with OK and landed on the Client ID screen")

        return finish()
    }

    // ── Watchlist and search helpers ────────────────────────────────────────

    /** One search-and-add step. [query] null keeps the text already typed. */
    private data class SearchAdd(
        val stepId: String,
        val chipId: String,
        val chipName: String,
        val query: String?,
        val what: String,
    )

    /** The "N/50 Scrips" label's N, or null when it is absent or unreadable. */
    private fun readScripCount(): Int? =
        engine.labelOf(Ids.WATCHLIST_SCRIP_COUNT)
            ?.let { Regex("""(\d+)\s*/\s*\d+""").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** Waits for the count label to read [expected]; returns the last value read. */
    private fun waitForScripCount(expected: Int?): Int? {
        if (expected == null) {
            engine.sleep(AutomationConfig.Timeouts.SETTLE)
            return readScripCount()
        }
        var last: Int? = null
        engine.waitUntil(AutomationConfig.Timeouts.COUNT_UPDATE) {
            last = readScripCount()
            last == expected
        }
        return last
    }

    private fun countNote(count: Int?): String =
        if (count == null) "; scrip count label not readable" else "; watchlist holds $count scrip(s)"

    private fun layoutName(identifier: String): String =
        if (identifier == Ids.WATCHLIST_GRID_VIEW) "the heat map" else "the list"

    /** Presses Back until [identifier] leaves the screen, at most twice. */
    private fun closeWithBack(identifier: String): Boolean {
        repeat(2) {
            if (!engine.pressBack()) return false
            if (engine.waitForGone(identifier, AutomationConfig.Timeouts.CONFIRM_RETRY)) return true
            if (engine.cancelled) return false
        }
        return false
    }

    /** Scrolls the chip row until [chipId] is built. */
    private fun revealChip(chipId: String): Boolean =
        engine.revealInList(chipId, Ids.SEARCH_FILTER_CHIPS)

    /** Taps a filter chip. Returns a failure reason, or null on success. */
    private fun tapChip(chipId: String, name: String): String? {
        if (!revealChip(chipId)) {
            return "The '$name' filter chip ('$chipId') is not on the search screen."
        }
        val outcome = engine.tap(chipId)
        return if (outcome.ok) null else "Could not tap the '$name' filter chip: ${outcome.detail}"
    }

    /**
     * Lets an in-flight scrip query finish. The chip or text change that
     * starts it is applied on Flutter's next frame, hence the settle first.
     */
    private fun waitForSearchToSettle(): Boolean {
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return false
        engine.waitForGone(Ids.SEARCH_LOADING, AutomationConfig.Timeouts.SEARCH_RESULTS)
        return !engine.cancelled
    }

    /** Waits for [resultsId] to list results. Returns a failure reason, or null. */
    private fun waitForResults(resultsId: String, what: String): String? {
        if (!waitForSearchToSettle()) return "cancelled"
        return when (val outcome = engine.waitForAny(listOf(resultsId), AutomationConfig.Timeouts.SEARCH_RESULTS)) {
            is WaitOutcome.Found -> null
            else -> reasonFor(outcome, "Search results for $what", AutomationConfig.Timeouts.SEARCH_RESULTS)
        }
    }

    /**
     * Selects [search]'s chip, types its query, and adds the first result not
     * yet in the watchlist. The add counts only once its "+" has turned into a
     * tick. Returns a failure reason, or null on success.
     */
    private fun searchAndAdd(search: SearchAdd): String? {
        tapChip(search.chipId, search.chipName)?.let { return it }
        if (search.query != null) {
            if (!waitForSearchToSettle()) return "cancelled"
            val entry = engine.enterText(Ids.SEARCH_SCRIP_INPUT, search.query)
            if (!entry.ok) return "Could not enter '${search.query}' in the search field: ${entry.detail}"
        }
        waitForResults(Ids.SEARCH_RESULTS_LIST, "${search.what} under ${search.chipName}")?.let { return it }

        val ticksBefore = engine.countOf(Ids.SEARCH_SCRIP_ADDED_ICON)
        val tap = engine.tap(Ids.SEARCH_SCRIP_ADD_BUTTON)
        if (!tap.ok) {
            return "No ${search.what} result offered '+' under ${search.chipName}; every listed result may " +
                "already be in the watchlist (${tap.detail})."
        }
        val landed = engine.waitUntil(AutomationConfig.Timeouts.ADD_CONFIRM) {
            engine.countOf(Ids.SEARCH_SCRIP_ADDED_ICON) > ticksBefore
        }
        if (landed) return null
        if (engine.cancelled) return "cancelled"
        if (engine.isPresent(Ids.WATCHLIST_ADD_SAVE_BUTTON)) {
            return "Tapping '+' opened the 'Add scrip to' sheet instead of adding straight to the open " +
                "watchlist, which means Search was not opened from a user watchlist tab."
        }
        return "Tapped '+' on the first ${search.what} result but it never turned into a tick within " +
            "${AutomationConfig.Timeouts.ADD_CONFIRM / 1000} seconds. The watchlist may be full (50 scrips) " +
            "or the add request failed."
    }

    // ── Bottom tab load checks ──────────────────────────────────────────────

    /** One tab to check: [taps] are tapped in order (parent tabs first), the last one is the tab itself. */
    private data class TabCheck(val stepId: String, val taps: List<String>, val budgetMs: Long)

    private data class Section(val name: String, val bottomTab: String, val screen: String, val tabs: List<TabCheck>)

    private fun holdingsTab(stepId: String, tab: String, budgetMs: Long) = TabCheck(
        stepId,
        listOf("${Ids.PORTFOLIO_TAB_PREFIX}holdings", "${Ids.HOLDINGS_TAB_PREFIX}$tab"),
        budgetMs,
    )

    /**
     * Opens [section]'s bottom tab and checks each of its tabs. Returns a
     * result only when the run has to end (cancelled).
     */
    private fun checkSection(section: Section): AutomationRunResult? {
        // In MF mode the third bottom tab is SIP, which has no Research screen.
        if (section.bottomTab == Ids.BOTTOMNAV_RESEARCH_TAB &&
            !engine.isPresent(Ids.BOTTOMNAV_RESEARCH_TAB) && engine.isPresent(Ids.BOTTOMNAV_SIP_TAB)
        ) {
            section.tabs.forEach { skipTab(it, "the app is in Mutual Fund mode, which shows SIP instead of Research") }
            return null
        }
        val open = engine.tapAndConfirm(
            section.bottomTab,
            listOf(section.screen),
            AutomationConfig.Timeouts.SCREEN_CHANGE,
        )
        if (!open.ok) {
            if (engine.cancelled) return stopped(step(section.tabs.first().stepId))
            section.tabs.forEach { tab ->
                begin(step(tab.stepId))
                failAndContinue(step(tab.stepId), "Could not open the ${section.name} tab: ${open.detail}")
            }
            return null
        }
        for (tab in section.tabs) {
            checkTab(tab)?.let { return it }
        }
        return null
    }

    private fun skipTab(tab: TabCheck, reason: String) {
        val step = step(tab.stepId)
        step.skip(reason)
        emitStep(step)
    }

    /** Selects [tab] and waits for every section on it to finish loading. */
    private fun checkTab(tab: TabCheck): AutomationRunResult? {
        val step = step(tab.stepId)
        begin(step)
        val target = tab.taps.lastOrNull()
        if (target != null) {
            // Parent tabs (Orders, Holdings) are tapped only when the inner tab
            // strip is not already showing — re-selecting them on every inner
            // tab is needless churn. An inner tab is judged missing only once
            // its own strip is on screen.
            if (!engine.isPresent(target)) {
                for (parent in tab.taps.dropLast(1)) {
                    when (selectTab(step, parent)) {
                        TabSelect.CANCELLED -> return stopped(step)
                        TabSelect.FAILED -> return null
                        TabSelect.OK -> Unit
                    }
                }
            }
            if (!engine.isPresent(target)) {
                // Thematic Basket and My Wealth are switched on per account.
                step.skip("this account is not offered the '${target.substringAfterLast("_tab_")}' tab")
                emitStep(step)
                return null
            }
            when (selectTab(step, target)) {
                TabSelect.CANCELLED -> return stopped(step)
                TabSelect.FAILED -> return null
                TabSelect.OK -> Unit
            }
        }

        val check = checkPageLoads(tab.budgetMs)
        if (engine.cancelled) return stopped(step)
        val seconds = "%.1f".format(check.elapsedMs / 1000.0)
        val where = if (check.pages > 1) " across ${check.pages} screenfuls" else ""
        val empties = if (check.empty > 0) "; ${check.empty} section(s) show no data" else ""
        when {
            check.errors > 0 -> failAndContinue(
                step,
                "Showed the 'Something went wrong / Reload' screen (${check.errors} on screen) instead of data.",
            )
            !check.settled -> failAndContinue(
                step,
                "${check.stillLoading} section(s) were still loading after ${tab.budgetMs / 1000} seconds$where$empties.",
            )
            else -> pass(step, "every section loaded in $seconds s$where$empties")
        }
        return null
    }

    private enum class TabSelect { OK, FAILED, CANCELLED }

    /** Taps the tab [id] and lets it settle. On FAILED, [step] has already been failed. */
    private fun selectTab(step: AutomationStepResult, id: String): TabSelect {
        val outcome = engine.tap(id)
        if (!outcome.ok) {
            if (engine.cancelled) return TabSelect.CANCELLED
            failAndContinue(step, "Could not select '$id': ${outcome.detail}")
            return TabSelect.FAILED
        }
        return if (engine.sleep(AutomationConfig.Timeouts.SETTLE)) TabSelect.OK else TabSelect.CANCELLED
    }

    private data class LoadCheck(
        val settled: Boolean,
        val elapsedMs: Long,
        val pages: Int,
        val empty: Int,
        val errors: Int,
        val stillLoading: Int,
    )

    /**
     * Waits, within [budgetMs] in total, until no `ui_loading` marker is on
     * screen, then scrolls a screenful and repeats, so sections below the fold
     * — which Flutter only builds once they are near the viewport — are
     * checked too. Scrolls back to the top afterwards so the tab strip is
     * reachable again.
     */
    private fun checkPageLoads(budgetMs: Long): LoadCheck {
        val start = android.os.SystemClock.elapsedRealtime()
        val deadline = start + budgetMs
        var pages = 1
        var empty = 0
        var errors = 0
        var settled = true
        var stillLoading = 0
        while (true) {
            val remaining = (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            val clear = engine.waitUntil(remaining) { engine.countOf(Ids.UI_LOADING) == 0 }
            empty = maxOf(empty, engine.countOf(Ids.UI_EMPTY))
            errors = maxOf(errors, engine.countOf(Ids.UI_ERROR))
            if (!clear) {
                settled = false
                stillLoading = engine.countOf(Ids.UI_LOADING)
                break
            }
            if (engine.cancelled || pages >= AutomationConfig.Timeouts.TAB_MAX_PAGES) break
            if (!engine.scrollPage(forward = true)) break
            pages++
            // Sections scrolled into view start their own fetch on the next frame.
            if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) break
        }
        val elapsed = android.os.SystemClock.elapsedRealtime() - start
        var back = 0
        while (back < AutomationConfig.Timeouts.TAB_MAX_PAGES + 2 && engine.scrollPage(forward = false)) back++
        engine.sleep(AutomationConfig.Timeouts.SETTLE)
        return LoadCheck(settled, elapsed, pages, empty, errors, stillLoading)
    }

    /**
     * Marks [step] failed but lets the run continue: used where one broken
     * tab should not stop the remaining tabs from being checked. The run's
     * verdict still ends FAILED.
     */
    private fun failAndContinue(step: AutomationStepResult, reason: String) {
        step.fail(reason)
        emitStep(step)
        if (result.failureMessage == null) result.failureMessage = reason
        captureScreenshot("failed_${step.id}")
    }

    // ── Step bookkeeping ────────────────────────────────────────────────────

    private fun begin(step: AutomationStepResult) {
        step.start()
        engine.emit(
            "step_started",
            mapOf(
                "stepId" to step.id,
                "stepIndex" to steps.indexOf(step),
                "name" to step.name,
            ),
        )
    }

    private fun pass(step: AutomationStepResult, note: String? = null) {
        step.pass(note)
        emitStep(step)
    }

    /**
     * Emits the terminal event for a step. Skipped steps ride on `step_passed`
     * with `status: "skipped"`, so the event vocabulary stays the six documented
     * names while the UI can still tell the two apart.
     */
    private fun emitStep(step: AutomationStepResult) {
        engine.emit(
            if (step.status == StepStatus.FAILED) "step_failed" else "step_passed",
            mapOf(
                "stepId" to step.id,
                "stepIndex" to steps.indexOf(step),
                "name" to step.name,
                "status" to step.status.wire,
                "durationMs" to step.durationMs,
                "note" to step.note,
                "reason" to step.failureReason,
            ),
        )
    }

    private fun reasonFor(outcome: WaitOutcome, what: String, timeoutMs: Long): String = when (outcome) {
        is WaitOutcome.TimedOut -> buildString {
            append(what).append(" was not detected within ").append(timeoutMs / 1000)
            append(" seconds.")
            if (!outcome.windowReadable) {
                append(
                    " The target application's window was not readable at the end of the wait — " +
                        "it may have been closed, or another application may be in the foreground.",
                )
            }
            if (outcome.visibleIdentifiers.isNotEmpty()) {
                append(" Identifiers visible when the wait expired: ")
                append(outcome.visibleIdentifiers.joinToString(", "))
                append('.')
            } else if (outcome.windowReadable) {
                append(
                    " No accessibility identifiers were visible at all, which usually means the " +
                        "installed build of the target app does not carry the Semantics " +
                        "identifiers this tool looks for.",
                )
            }
        }
        is WaitOutcome.TargetWindowLost -> "The target application stopped being the readable " +
            "foreground window while waiting for $what. The run was stopped because another " +
            "application appears to have taken over, or the target app closed."
        else -> "$what could not be confirmed."
    }

    private fun abort(step: AutomationStepResult, reason: String): AutomationRunResult {
        step.fail(reason)
        emitStep(step)
        result.failureMessage = reason
        result.passed = false
        captureScreenshot("failed_${step.id}")
        return complete()
    }

    private fun abort(
        outcome: WaitOutcome,
        step: AutomationStepResult,
        what: String,
        timeoutMs: Long,
        extra: String = "",
    ): AutomationRunResult = abort(step, reasonFor(outcome, what, timeoutMs) + extra)

    private fun stopped(step: AutomationStepResult): AutomationRunResult {
        val reason = if (engine.serviceGone) {
            "The accessibility service was turned off while the run was in progress."
        } else {
            "The run was stopped by the operator."
        }
        step.skip(reason)
        emitStep(step)
        result.stopped = true
        result.passed = false
        result.failureMessage = reason
        return complete()
    }

    private fun finish(): AutomationRunResult {
        result.passed = steps.none { it.status == StepStatus.FAILED } &&
            steps.none { it.status == StepStatus.PENDING || it.status == StepStatus.RUNNING }
        captureScreenshot("completed")
        return complete()
    }

    private fun complete(): AutomationRunResult {
        result.finishedAtMs = System.currentTimeMillis()
        return result
    }

    /**
     * Evidence capture is best effort by design: a secure screen in the target
     * app, or an Android 10 device, means no image. That never changes the
     * verdict of the run.
     */
    private fun captureScreenshot(label: String) {
        val path = engine.screenshot(label) { reason ->
            if (result.screenshotUnavailableReason == null) {
                result.screenshotUnavailableReason = reason
            }
        }
        if (path != null) result.screenshots[label] = path
    }
}

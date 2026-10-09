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
 * Deliberately absent: invalid credential cases, logout, and anything that
 * touches portfolios, orders or funds. This class can only read login and
 * watchlist/search screens and type the three credential values plus the
 * fixed set of search terms baked into this flow.
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
        AutomationStepResult("search_tcs_added", "TCS added from search"),
        AutomationStepResult("search_nifty_futures_added", "NIFTY futures added from search"),
        AutomationStepResult("search_nifty_options_added", "NIFTY options added from search"),
        AutomationStepResult("search_crudeoil_added", "Crude oil added from search"),
        AutomationStepResult("search_closed", "Search screen closed"),
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

        // 3 — Target application launched -------------------------------------
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

        // 4 — Intro screen Login tapped -----------------------------------------
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
            pass(introLogin, introOutcome.detail)
        }

        // 5 — Client ID screen displayed --------------------------------------
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

        // 6 — Client ID input found -------------------------------------------
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

        // 7 — Client ID entered ------------------------------------------------
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

        // 8 — Continue button tapped ------------------------------------------
        val continueTapped = step("continue_tapped")
        begin(continueTapped)
        val continueOutcome = engine.tap(Ids.CLIENT_ID_CONTINUE_BUTTON, AutomationConfig.Texts.CONTINUE)
        if (!continueOutcome.ok) {
            return abort(continueTapped, "Could not tap Continue: ${continueOutcome.detail}")
        }
        pass(continueTapped, continueOutcome.detail)

        // 9 — OTP screen displayed --------------------------------------------
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

        // 10 — OTP input found --------------------------------------------------
        val otpInput = step("otp_input_found")
        begin(otpInput)
        if (!engine.isPresent(Ids.OTP_INPUT)) {
            return abort(
                otpInput,
                "The OTP screen is displayed but no node carries the identifier '${Ids.OTP_INPUT}'.",
            )
        }
        pass(otpInput, "'${Ids.OTP_INPUT}' located")

        // 11 — OTP entered -----------------------------------------------------
        val otpEntered = step("otp_entered")
        begin(otpEntered)
        val otpOutcome = engine.enterText(Ids.OTP_INPUT, otp)
        if (!otpOutcome.ok) {
            return abort(otpEntered, "Could not enter the OTP: ${otpOutcome.detail}")
        }
        pass(otpEntered, otpOutcome.detail)

        // 12 — OTP submitted ---------------------------------------------------
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

        // 13 — MPIN screen displayed -------------------------------------------
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

        // 14 — MPIN entered ----------------------------------------------------
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

        // 15 — MPIN submitted --------------------------------------------------
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

        // 16 — Biometric screen checked ----------------------------------------
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

        // 17 — Biometric skipped -----------------------------------------------
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

        // 18 — Home or Watchlist displayed -------------------------------------
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

        // 19 — Disclosure dialog accepted --------------------------------------
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

        // 20 — Watchlist tab opened ---------------------------------------------
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

        // 21 — Discover scrip add dialog opened ---------------------------------
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

        // 22 — Watchlist checkboxes toggled --------------------------------------
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

        // 23 — Add-to-watchlist saved --------------------------------------------
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

        // 24 — First watchlist tab opened -----------------------------------------
        val firstTabOpened = step("first_watchlist_tab_opened")
        begin(firstTabOpened)
        val firstTabOutcome = engine.waitAndTap(
            Ids.WATCHLIST_FIRST_TAB,
            emptyList(),
            AutomationConfig.Timeouts.WATCHLIST_FIRST_TAB,
        )
        if (!firstTabOutcome.ok) {
            if (engine.cancelled) return stopped(firstTabOpened)
            return abort(firstTabOpened, "Could not tap the first watchlist tab: ${firstTabOutcome.detail}")
        }
        pass(firstTabOpened, firstTabOutcome.detail)

        // 25 — Watchlist edit screen opened ---------------------------------------
        val editOpened = step("watchlist_edit_opened")
        begin(editOpened)
        val editOpenOutcome = engine.waitAndTap(
            Ids.WATCHLIST_EDIT_BUTTON,
            emptyList(),
            AutomationConfig.Timeouts.WATCHLIST_EDIT,
        )
        if (!editOpenOutcome.ok) {
            if (engine.cancelled) return stopped(editOpened)
            return abort(editOpened, "Could not tap the watchlist settings/edit icon: ${editOpenOutcome.detail}")
        }
        pass(editOpened, editOpenOutcome.detail)

        // 26 — Watchlist scrips trimmed --------------------------------------------
        val scripsTrimmed = step("watchlist_scrips_trimmed")
        if (!engine.isPresent(Ids.EDIT_WATCHLIST_DELETE_BUTTON)) {
            engine.pressBack()
            scripsTrimmed.skip("the watchlist is already empty, so there was nothing to delete")
            emitStep(scripsTrimmed)
        } else {
            begin(scripsTrimmed)
            val deletes = engine.tapUpTo(
                Ids.EDIT_WATCHLIST_DELETE_BUTTON,
                maxTimes = 6,
                settleMs = AutomationConfig.Timeouts.SETTLE,
            )
            if (engine.cancelled) return stopped(scripsTrimmed)
            val saveOutcome = engine.tap(Ids.EDIT_WATCHLIST_SAVE_BUTTON)
            if (!saveOutcome.ok) {
                return abort(
                    scripsTrimmed,
                    "Deleted $deletes scrip(s) but could not tap Save: ${saveOutcome.detail}",
                )
            }
            pass(scripsTrimmed, "deleted $deletes scrip(s) and saved")
        }

        // 27 — Search screen opened -----------------------------------------------
        val searchOpened = step("search_opened")
        begin(searchOpened)
        val searchOpenOutcome = engine.waitAndTap(
            Ids.WATCHLIST_SEARCH_BUTTON,
            emptyList(),
            AutomationConfig.Timeouts.SEARCH_SCREEN,
        )
        if (!searchOpenOutcome.ok) {
            if (engine.cancelled) return stopped(searchOpened)
            return abort(searchOpened, "Could not tap the Watchlist search icon: ${searchOpenOutcome.detail}")
        }
        pass(searchOpened, searchOpenOutcome.detail)

        // 28 — TCS added from search -------------------------------------------------
        val tcsAdded = step("search_tcs_added")
        begin(tcsAdded)
        val tcsEntry = engine.enterText(Ids.SEARCH_SCRIP_INPUT, "tcs")
        if (!tcsEntry.ok) {
            return abort(tcsAdded, "Could not enter 'tcs' in the search field: ${tcsEntry.detail}")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(tcsAdded)
        val tcsTaps = engine.tapUpTo(
            Ids.SEARCH_SCRIP_ADD_BUTTON,
            maxTimes = 2,
            settleMs = AutomationConfig.Timeouts.SETTLE,
        )
        if (engine.cancelled) return stopped(tcsAdded)
        if (tcsTaps == 0) {
            return abort(tcsAdded, "No TCS search result's add button could be tapped.")
        }
        pass(tcsAdded, "added $tcsTaps result(s)")

        // 29 — NIFTY futures added from search ----------------------------------------
        val niftyFuturesAdded = step("search_nifty_futures_added")
        begin(niftyFuturesAdded)
        val niftyEntry = engine.enterText(Ids.SEARCH_SCRIP_INPUT, "nifty")
        if (!niftyEntry.ok) {
            return abort(niftyFuturesAdded, "Could not enter 'nifty' in the search field: ${niftyEntry.detail}")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(niftyFuturesAdded)
        val futuresFilterOutcome = engine.tap(Ids.SEARCH_FILTER_CHIP_FUTURES)
        if (!futuresFilterOutcome.ok) {
            return abort(
                niftyFuturesAdded,
                "Could not tap the Futures filter chip: ${futuresFilterOutcome.detail}",
            )
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(niftyFuturesAdded)
        val futuresTaps = engine.tapUpTo(
            Ids.SEARCH_SCRIP_ADD_BUTTON,
            maxTimes = 1,
            settleMs = AutomationConfig.Timeouts.SETTLE,
        )
        if (engine.cancelled) return stopped(niftyFuturesAdded)
        if (futuresTaps == 0) {
            return abort(niftyFuturesAdded, "No NIFTY futures search result's add button could be tapped.")
        }
        pass(niftyFuturesAdded, "added the first NIFTY futures result")

        // 30 — NIFTY options added from search ----------------------------------------
        val niftyOptionsAdded = step("search_nifty_options_added")
        begin(niftyOptionsAdded)
        val optionsFilterOutcome = engine.tap(Ids.SEARCH_FILTER_CHIP_OPTIONS)
        if (!optionsFilterOutcome.ok) {
            return abort(
                niftyOptionsAdded,
                "Could not tap the Options filter chip: ${optionsFilterOutcome.detail}",
            )
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(niftyOptionsAdded)
        val optionsTaps = engine.tapUpTo(
            Ids.SEARCH_SCRIP_ADD_BUTTON,
            maxTimes = 1,
            settleMs = AutomationConfig.Timeouts.SETTLE,
        )
        if (engine.cancelled) return stopped(niftyOptionsAdded)
        if (optionsTaps == 0) {
            return abort(niftyOptionsAdded, "No NIFTY options search result's add button could be tapped.")
        }
        pass(niftyOptionsAdded, "added the first NIFTY options result")

        // 31 — Crude oil added from search -----------------------------------------------
        val crudeoilAdded = step("search_crudeoil_added")
        begin(crudeoilAdded)
        val allFilterOutcome = engine.tap(Ids.SEARCH_FILTER_CHIP_ALL)
        if (!allFilterOutcome.ok) {
            return abort(crudeoilAdded, "Could not reset the filter to 'All': ${allFilterOutcome.detail}")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(crudeoilAdded)
        val crudeoilEntry = engine.enterText(Ids.SEARCH_SCRIP_INPUT, "crudeoil")
        if (!crudeoilEntry.ok) {
            return abort(crudeoilAdded, "Could not enter 'crudeoil' in the search field: ${crudeoilEntry.detail}")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(crudeoilAdded)
        val crudeoilTaps = engine.tapUpTo(
            Ids.SEARCH_SCRIP_ADD_BUTTON,
            maxTimes = 1,
            settleMs = AutomationConfig.Timeouts.SETTLE,
        )
        if (engine.cancelled) return stopped(crudeoilAdded)
        if (crudeoilTaps == 0) {
            return abort(crudeoilAdded, "No crude oil search result's add button could be tapped.")
        }
        pass(crudeoilAdded, "added the first crude oil result")

        // 32 — Search screen closed ---------------------------------------------------
        val searchClosed = step("search_closed")
        begin(searchClosed)
        if (!engine.pressBack()) {
            return abort(searchClosed, "The system back action could not be dispatched.")
        }
        if (!engine.sleep(AutomationConfig.Timeouts.SETTLE)) return stopped(searchClosed)
        pass(searchClosed, "dispatched the system back action")

        return finish()
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

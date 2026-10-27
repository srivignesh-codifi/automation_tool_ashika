package com.mobileautomation.tool

import android.os.SystemClock
import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T

/**
 * The Profile module of the Tradion regression pass: every row of the Account and Trade Settings tabs as an
 * independent test case (PF-01 ... PF-20).
 *
 * Rules it keeps:
 *
 * * **Every row is reached.** A row that opens a screen or sheet is tapped once, the screen must answer within [UI]
 *   and finish loading within [LOAD], and it is left with Back (pressed only while its own marker is on screen).
 * * **Settings are put back.** Each switch (Fixed header, Notification, Order preview, Dark theme) and the font size
 *   is changed once, the change is read back from the control's own state, and it is restored and read back again.
 * * **Nothing is submitted, paid, switched or frozen.** Switch Account, Add Funds, Rate Our App, Security Settings and
 *   Trade Settings are opened and left; Enable / Disable TOTP (sends an OTP or turns 2FA off), Freeze (opens the
 *   browser), Developer Console (an SSO hand-off) and the social links are checked for presence only.
 * * **No case depends on another.** Each starts from the Profile tab it needs and returns there.
 */
class ProfileSuite(
    private val flow: FlowBase,
    private val engine: AutomationEngine,
    private val stopped: (AutomationStepResult) -> AutomationRunResult,
    private val sessionLost: (AutomationStepResult) -> AutomationRunResult?,
    /** Removes the signed-in user id from text that goes into the report. */
    private val redact: (String) -> String,
    /** The checks switched off in the Profile settings on the tool's module page. */
    private val off: Set<String> = emptySet(),
) {

    class Case(val code: String, val id: String, val name: String, val expected: String, val severity: String) {
        fun toStep(): AutomationStepResult = AutomationStepResult(id, name).also {
            it.code = code
            it.expected = expected
            it.severity = severity
            it.module = "profile"
        }
    }

    companion object {
        private fun c(code: String, id: String, name: String, expected: String, severity: String = "Medium") =
            Case(code, id, name, expected, severity)

        /** Mean screen brightness above which a "dark" screen still looks light, and below which a "light" one looks dark. */
        private const val DARK_MAX = 0.40
        private const val LIGHT_MIN = 0.55

        /** A screen in the wrong theme is read once more after this long, in case it was still repainting. */
        private const val THEME_RECHECK = 1_500L

        /** PF-06 checks this many messages per exchange tab and skips the rest of a longer list. */
        private const val EXCHANGE_ROWS_CHECKED = 10

        /** The switches of the Trade Settings tab, by the suffix of their identifier. */
        val TOGGLES = listOf(
            "fixed_header" to "Fixed header",
            "notification" to "Order notification",
            "order_preview" to "Order preview",
            "theme" to "Dark theme",
        )

        /** In run order. The first ids are the module's old step ids, kept so earlier reports still line up. */
        val CASES: List<Case> = listOf(
            c("PF-01", "profile_account_loaded", "Profile › Account loaded",
                "The Account tab is selected, finishes loading with no error screen, and shows the profile card and the Switch Account, Funds, Exchange Messages, Markets Status, Customer Support, Rate Our App and Logout rows.", "Critical"),
            c("PF-02", "profile_details_opened", "Profile details",
                "The profile card opens the profile details, which finish loading: Email, Phone, PAN and Demat ID are filled (not '--'), every segment group shows ACTIVE / INACTIVE, and the bank account opens its details sheet, which closes on Back.", "High"),
            c("PF-03", "profile_switch_account_sheet", "Switch Account sheet",
                "Switch Account opens its sheet listing the signed-in account and the Add User option; Back closes it without switching."),
            c("PF-04", "profile_funds_opened", "Funds",
                "The Funds row opens Funds, which finishes loading with readable balances and its Add Funds and Withdraw buttons; Withdraw opens its screen and Back returns (nothing withdrawn).", "High"),
            c("PF-05", "profile_add_funds_opened", "Add Funds",
                "The row's Add Funds button opens Add Funds; Back returns; nothing is paid.", "High"),
            c("PF-06", "profile_exchange_messages", "Exchange Messages",
                "Exchange Messages opens. Load test: every exchange tab (NSE, NFO, BSE, BFO, NCOM, MCX, NSLB, NCX) selects, loads within the load time, and shows its first 10 messages the server sent (the rest of a longer list is skipped), or NO DATA FOUND when it sent none; no message row is blank. A rapid second pass through all tabs leaves every tab with the same data. Back returns.", "High"),
            c("PF-07", "profile_market_status", "Markets Status",
                "Markets Status opens and every exchange row shows a status; Back returns."),
            c("PF-08", "profile_customer_support", "Customer Support",
                "Customer Support opens its sheet with Call Us, Email Us and Company; Company opens the company page with its exchange / regulator links; Back returns to the sheet and closes it. The phone, mail and web links are not opened."),
            c("PF-09", "profile_rate_app", "Rate Our App",
                "Rate Our App opens with its rating stars and Submit; Back returns; nothing is submitted.", "Low"),
            c("PF-10", "profile_footer_info", "Company, regulatory and version details",
                "The foot of the Account tab shows the follow-us links, the SEBI / exchange registration lines and the app and build versions.", "Low"),
            c("PF-21", "profile_qr_scan", "QR login scanner (header icon)",
                "The scan icon in the Settings header asks for the camera (if not yet allowed) and opens the QR login scanner; Back returns. Nothing is scanned."),
            c("PF-11", "profile_trade_settings_loaded", "Profile › Trade Settings loaded",
                "The Trade Settings tab is selected, finishes loading, and shows the four switches, Font sizes, TOTP, Security Settings, Freeze & UnFreeze, Trade Settings, Accessibility Settings and Developer Console.", "Critical"),
            c("PF-12", "profile_toggle_fixed_header", "Fixed header switch",
                "Fixed header switches over and back; its state follows each tap and the setting ends as it was found."),
            c("PF-13", "profile_toggle_notification", "Order notification switch",
                "Order notification switches over and back (saved on the server each time); the setting ends as it was found."),
            c("PF-14", "profile_toggle_order_preview", "Order preview switch",
                "Order preview switches over and back (saved on the server each time); the setting ends as it was found."),
            c("PF-15", "profile_toggle_theme", "Dark theme switch",
                "Dark theme switches over and back. The app actually repaints at once: the Settings tab, the Profile tab and the Exchange Messages page are dark with the switch on and light with it off (read from screenshots), before, during and after; the setting ends as it was found."),
            c("PF-16", "profile_font_size", "Font sizes",
                "Font sizes offers Small, Medium and Large; another size is selected and shows as selected; the original size is restored."),
            c("PF-17", "profile_security_settings", "Security Settings",
                "Security Settings opens Change Password with Current, New and Confirm fields; submitting them empty shows the three 'cannot be empty' errors and sends nothing; Back returns.", "High"),
            c("PF-18", "profile_trade_preferences", "Trade Settings (order preferences)",
                "The order preferences load; every segment tab selects and shows its settings and Default Quantity; Save Settings is disabled with no change, enabled after quantity +, disabled again after quantity −; Back returns without a save prompt. Nothing is saved.", "High"),
            c("PF-19", "profile_accessibility_settings", "Accessibility Settings",
                "Accessibility Settings shows Font Size, Theme, High Contrast, Color Blind Mode, Motion, Touch Targets and Disability Profiles; Reduce motion, Disable shimmer, Reduce transitions and Larger touch targets each switch over and back; Back returns."),
            c("PF-20", "profile_guarded_rows", "TOTP, Freeze & UnFreeze, Developer Console",
                "The rows that would send an OTP, change 2FA, open the browser or hand over to SSO are shown with their current state (Enable / Disable TOTP, Freeze / UnFreeze); they are not tapped."),
        )

        fun steps(): List<AutomationStepResult> = CASES.map { it.toStep() }

        val ALL_IDS: List<String> = CASES.map { it.id }

        private val TAB_POSITION = Regex("""Tab \d+ of \d+""", RegexOption.IGNORE_CASE)
    }

    private val UI = Timeouts.UI_RESPONSE
    private val LOAD = Timeouts.DATA_LOAD

    /** Said once per case when the installed Tradion build predates the Profile identifiers. */
    private val STALE = "the installed Tradion build does not carry the Profile identifiers (it was built before they " +
        "were added); rebuild and reinstall Tradion from the current working tree"

    // ── Where things are: an identifier, and the visible label to fall back on ──────────────────

    /** A row or button: tapped by [id], or by its visible [label] on a build without the identifier. */
    private class Row(val id: String, val label: String)

    /**
     * A screen or sheet a row opens: known by [id], or (on a build without it) by the bottom bar being gone, which
     * happens for every page and sheet pushed over the tabs, together with all of [sig] being readable.
     */
    private class Screen(val id: String, val sig: List<String>)

    private object R {
        val HEADER = Row(T.PROFILE_HEADER_CARD, "Account Details")
        val SWITCH = Row(T.PROFILE_SWITCH_ACCOUNT_ROW, "Switch Between Multiple Trading Profiles")
        val FUNDS = Row(T.PROFILE_FUNDS_ROW, "Stocks, F&O and Commodities")
        val ADD_FUNDS = Row(T.PROFILE_ADD_FUNDS_BUTTON, "Add Funds")
        val EXCHANGE = Row(T.PROFILE_EXCHANGE_MESSAGES_ROW, "Exchange Messages")
        val MARKET = Row(T.PROFILE_MARKET_STATUS_ROW, "Markets Status")
        val SUPPORT = Row(T.PROFILE_CUSTOMER_SUPPORT_ROW, "Customer Support")
        val RATE = Row(T.PROFILE_RATE_APP_ROW, "Rate Our App")
        val LOGOUT = Row(T.PROFILE_LOGOUT_BUTTON, "Logout")
        val SECURITY = Row(T.PROFILE_SECURITY_ROW, "Security Settings")
        val PREFERENCES = Row(T.PROFILE_TRADE_PREFERENCES_ROW, "Preferences")
        val ACCESSIBILITY = Row(T.PROFILE_ACCESSIBILITY_ROW, "Accessibility Settings")
        val TOTP = Row(T.PROFILE_TOTP_ROW, "TOTP")
        val FREEZE = Row(T.PROFILE_FREEZE_ROW, "Freeze & UnFreeze Account")
        val DEVELOPER = Row(T.PROFILE_DEVELOPER_CONSOLE_ROW, "Developer Console")
    }

    private object S {
        val DETAILS = Screen(T.PROFILE_DETAILS_SCREEN, listOf("Personal Information"))
        val SWITCH = Screen(T.SWITCH_ACCOUNT_SHEET, listOf("Switch Account"))
        val FUNDS = Screen(T.FUNDS_SCREEN, listOf("Withdraw"))
        val ADD_FUNDS = Screen(T.ADD_FUNDS_SCREEN, listOf("Add Funds"))
        val EXCHANGE = Screen(T.EXCHANGE_MESSAGES_SCREEN, listOf("Exchange Messages"))
        val MARKET = Screen(T.MARKET_STATUS_SCREEN, listOf("Market Status"))
        val SUPPORT = Screen(T.CUSTOMER_SUPPORT_SHEET, listOf("Call Us"))
        val RATE = Screen(T.RATE_APP_SCREEN, listOf("Rate Our App"))
        val PASSWORD = Screen(T.CHANGE_PASSWORD_SCREEN, listOf("Current Password"))
        val PREFERENCES = Screen(T.TRADE_PREFERENCES_SCREEN, listOf("Order Preference"))
        val ACCESSIBILITY = Screen(T.ACCESSIBILITY_SETTINGS_SCREEN, listOf("Font Size", "Theme"))
        val COMPANY = Screen(T.COMPANY_SCREEN, listOf("SEBI"))
    }

    /** Whether the installed build carries the Profile identifiers; learnt the first time a tab is shown. */
    private var taggedBuild: Boolean? = null

    // ── Outcomes ───────────────────────────────────────────────────────────────────────────────

    private enum class Kind { FAIL, BLOCKED, NOT_TESTED }

    private object Cause {
        const val APP = "app_defect"
        const val UNCONFIRMED = "unconfirmed"
        const val AUTOMATION = "automation"
        const val ACCESSIBILITY = "accessibility"
        const val API = "api_data"
    }

    private class CaseEnd(
        val kind: Kind,
        message: String,
        val category: String? = null,
        val evidence: String? = null,
        val recommendation: String? = null,
    ) : RuntimeException(message, null, false, false)

    private class Stopped : RuntimeException(null, null, false, false)

    /** Thrown by an inside step that legitimately left the screen it was opened from. */
    private object ScreenLeft : RuntimeException(null, null, false, false)

    private val notes = mutableListOf<String>()
    private fun note(text: String) {
        notes += text
    }

    private var current: AutomationStepResult? = null
    private var currentCase: Case? = null
    private var cleanup: String? = null
    private var evidenceCount = 0

    private fun alive() {
        if (engine.cancelled) throw Stopped()
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val reached = engine.waitUntil(timeoutMs, 150L, condition)
        alive()
        return reached
    }

    private fun pause(ms: Long) {
        engine.sleep(ms)
        alive()
    }

    private fun has(part: String): Boolean = engine.hasTextContaining(part)

    // ── Evidence, with personal details removed ──────────────────────────────────────────────────

    /** A line that may be personal: an email, a masked or long number (PAN, Demat, phone, account), or a name in capitals. */
    private val personalLine = Regex("""@|\*|\d{5,}|^[A-Z][A-Z.']+(\s+[A-Z][A-Z.']+)+$""")

    /** Readable text for the report, never with personal details: Profile shows the client's name, PAN, Demat and bank. */
    private fun safeTexts(limit: Int): List<String> =
        engine.screenTexts(limit * 2).map { it.trim() }.filterNot { personalLine.containsMatchIn(it) }.take(limit)

    private fun snapshot(): String {
        val ids = engine.visibleIdentifiers()
        val texts = safeTexts(15)
        return redact(
            "identifiers: ${ids.take(30).joinToString(", ").ifEmpty { "none" }}; " +
                "readable text (personal details removed): ${if (texts.isEmpty()) "none" else texts.joinToString(" | ")}",
        )
    }

    private fun screenName(): String {
        val known = listOf(
            S.DETAILS to "profile_details", S.SWITCH to "switch_account", S.ADD_FUNDS to "add_funds", S.FUNDS to "funds",
            S.EXCHANGE to "exchange_messages", S.MARKET to "market_status", S.SUPPORT to "customer_support",
            S.RATE to "rate_app", S.PASSWORD to "change_password", S.PREFERENCES to "trade_preferences",
            S.ACCESSIBILITY to "accessibility_settings", S.COMPANY to "company",
        )
        known.firstOrNull { isOpen(it.first) }?.let { return it.second }
        return when {
            engine.isPresent(T.QR_SCAN_SCREEN) -> "qr_scanner"
            onView(account = false) -> "profile_trade_settings"
            onView(account = true) -> "profile_account"
            else -> "unknown_screen"
        }
    }

    /** Profile details and the bank sheet show the client's PAN, Demat, email, phone and bank account. */
    private fun personalOnScreen(): Boolean =
        isOpen(S.DETAILS) || engine.isPresent(T.BANK_DETAILS_SHEET) || (!tabsShowing() && has("Personal Information"))

    /** A screenshot of the case, never of a screen with personal details. */
    private fun shot(label: String): String? {
        if (personalOnScreen()) return null
        val case = currentCase ?: return null
        return flow.captureScreenshot("${case.code}_$label")
    }

    private fun fail(message: String, cause: String = Cause.UNCONFIRMED, recommend: String? = null): Nothing {
        var evidence: String? = null
        if (!engine.cancelled) {
            evidence = "screen: ${screenName()}; " + snapshot()
            if (!personalOnScreen()) currentCase?.let { flow.captureScreenshot("${it.code}_failure_${++evidenceCount}_${screenName()}") }
        }
        throw CaseEnd(Kind.FAIL, message, cause, evidence, recommend)
    }

    private fun blocked(message: String, cause: String, recommend: String? = null): Nothing =
        throw CaseEnd(Kind.BLOCKED, message, cause, if (engine.cancelled) null else "screen: ${screenName()}; " + snapshot(), recommend)

    /**
     * Ends the case BLOCKED when the check it is about to make needs an identifier the installed build lacks (a
     * switch's on / off state, a stepper, a tab strip): one clear "rebuild Tradion" finding, not a false failure.
     */
    private fun requireTags(what: String) {
        if (taggedBuild != false) return
        val done = if (notes.isEmpty()) "" else " What could be checked passed: ${notes.joinToString("; ")}."
        throw CaseEnd(
            Kind.BLOCKED, "$what needs the Profile identifiers, but $STALE.$done", Cause.AUTOMATION, null,
            "Rebuild Tradion (flutter build apk) from the working tree that has the Profile identifiers and install it, then run again.",
        )
    }

    private fun missingTag(id: String, what: String): Nothing = blocked(
        "$what could not be found: '$id' is not on screen" + if (taggedBuild == false) " ($STALE)" else "",
        Cause.AUTOMATION,
        "Install a Tradion build that carries the Profile identifiers (docs/AUTOMATION_SEMANTICS.md, Profile).",
    )

    /** Records how long the server took on the case's own step. */
    private fun timing(startedAtMs: Long, what: String) {
        val took = engine.clock() - startedAtMs
        val step = current ?: return
        step.apiKind = step.apiKind ?: StepApi.READ
        step.apiMs = maxOf(step.apiMs ?: 0L, took)
        step.apiChecks += 1
        step.apiStatus = if (took >= StepApi.SLOW_MS) StepApi.SLOW else StepApi.OK
        step.apiDetail = "$what in ${"%.1f".format(took / 1000.0)} s"
        note("$what in ${"%.1f".format(took / 1000.0)} s")
    }

    // ── Running a case ─────────────────────────────────────────────────────────────────────────

    private fun runCase(id: String, body: () -> Unit): AutomationRunResult? {
        val c = CASES.first { it.id == id }
        val step = flow.step(id)
        if (step.status != StepStatus.PENDING) return null
        current = step
        currentCase = c
        notes.clear()
        cleanup = null
        flow.begin(step)
        var end: CaseEnd? = null
        try {
            body()
        } catch (e: CaseEnd) {
            end = e
        } catch (_: Stopped) {
            return stopped(step)
        } catch (e: Exception) {
            val at = e.stackTrace.firstOrNull { it.className.startsWith("com.mobileautomation") }
                ?.let { " (at ${it.methodName}, line ${it.lineNumber})" }.orEmpty()
            end = CaseEnd(
                Kind.FAIL, "Automation error: ${e.javaClass.simpleName}: ${e.message ?: "no detail"}$at", Cause.AUTOMATION,
                if (engine.cancelled) null else "screen: ${screenName()}; " + snapshot(),
            )
        }
        if (engine.cancelled) return stopped(step)
        if (taggedBuild == false && end?.kind != Kind.BLOCKED) note("located by visible labels: $STALE")
        val detail = redact(notes.joinToString("; "))
        step.cleanup = cleanup
        if (end != null && (end.kind == Kind.FAIL || end.kind == Kind.BLOCKED)) {
            step.cause = end.category
            step.evidence = end.evidence
            step.recommendation = end.recommendation
        }
        val message = redact(end?.message.orEmpty())
        // The run's own failure screenshot is taken as the step is recorded: never on a page of personal details.
        if (personalOnScreen()) try {
            recover()
        } catch (_: Stopped) {
            return stopped(step)
        }
        when (end?.kind) {
            null -> flow.pass(step, detail.ifEmpty { null })
            Kind.FAIL -> flow.failAndContinue(step, message + if (detail.isEmpty()) "" else " (before that: $detail)")
            Kind.BLOCKED -> flow.skipAs(step, "blocked", message)
            Kind.NOT_TESTED -> flow.skipAs(step, "not_tested", message)
        }
        try {
            recover()
        } catch (_: Stopped) {
            return stopped(step)
        }
        return sessionLost(step)
    }

    // ── Screens, rows and getting back to Profile ──────────────────────────────────────────────

    private val loginIds = listOf(
        T.USER_ID_SCREEN, T.USER_ID_INPUT, T.PASSWORD_SCREEN, T.PASSWORD_INPUT, T.OTP_SCREEN, T.OTP_INPUT,
        T.BIOMETRIC_LOGIN_SCREEN,
    )

    private fun loggedOut(): Boolean = engine.firstPresent(loginIds) != null

    /** The bottom bar is only readable on the tabs themselves: any page or sheet pushed over them hides it. */
    private fun tabsShowing(): Boolean = engine.isPresent(T.BOTTOMNAV_PROFILE_TAB)

    private fun isOpen(screen: Screen): Boolean =
        engine.isPresent(screen.id) || (!tabsShowing() && screen.sig.all(::has))

    /** The Profile tab ([account]) or Settings tab is the one showing: by its view's identifier, or by its own rows. */
    private fun onView(account: Boolean): Boolean {
        if (engine.isPresent(if (account) T.PROFILE_ACCOUNT_VIEW else T.PROFILE_TRADE_SETTINGS_VIEW)) return true
        if (!engine.isPresent(T.PROFILE_SCREEN)) return false
        return if (account) has("Switch Between Multiple Trading Profiles") || has("Exchange Messages")
        else has("Fixed Headers") || has("Font sizes")
    }

    /** Leaves [screen] with Back: by its marker when it has one, else only while the tabs are hidden (Back cannot exit then). */
    private fun close(screen: Screen): Boolean {
        if (engine.isPresent(screen.id)) return flow.closeWithBack(screen.id)
        if (tabsShowing() || !isOpen(screen)) return !isOpen(screen)
        repeat(2) {
            engine.pressBack()
            if (waitUntil(UI) { !isOpen(screen) }) return true
        }
        return false
    }

    /** Taps [row]: by its identifier, or by its visible label, scrolled into view first. */
    private fun tapRow(row: Row, what: String) {
        if (engine.isPresent(row.id) || engine.scrollIdIntoView(row.id)) {
            alive()
            if (engine.isPresent(row.id)) {
                val tap = engine.tap(row.id)
                alive()
                if (!tap.ok) fail("$what could not be tapped: ${tap.detail}", Cause.AUTOMATION)
                return
            }
        }
        alive()
        if (!has(row.label)) engine.scrollTextIntoView(row.label)
        alive()
        if (!has(row.label)) fail("$what is not on the tab (no '${row.label}' found after scrolling)", Cause.APP)
        val tap = engine.tapText(row.label)
        alive()
        if (!tap.ok) fail("$what could not be tapped: ${tap.detail}", Cause.AUTOMATION)
    }

    /** Whether [row] is on the tab, scrolling to it if needed. */
    private fun rowPresent(row: Row): Boolean {
        if (engine.isPresent(row.id) || has(row.label)) return true
        engine.scrollIdIntoView(row.id)
        alive()
        if (engine.isPresent(row.id)) return true
        engine.scrollTextIntoView(row.label)
        alive()
        return engine.isPresent(row.id) || has(row.label)
    }

    /**
     * Closes what the last case opened and makes sure Profile is showing. Back is pressed for a marker that was just
     * seen, or while the bottom bar is hidden (a page or sheet over the tabs, so Back cannot leave the app). The
     * order preferences' "save changes?" prompt is always answered with Discard. True when Profile shows alone.
     */
    private fun recover(): Boolean {
        if (!engine.targetInForeground()) engine.returnToTarget()
        engine.hideKeyboard()
        repeat(6) {
            alive()
            if (loggedOut()) return false
            if (engine.isPresent(T.TRADE_PREF_DISCARD_BUTTON) || (!tabsShowing() && has("Do you want to save changes"))) {
                if (engine.isPresent(T.TRADE_PREF_DISCARD_BUTTON)) engine.tap(T.TRADE_PREF_DISCARD_BUTTON) else engine.tapText("Discard")
                waitUntil(UI) { !has("Do you want to save changes") }
                return@repeat
            }
            if (!tabsShowing()) {
                val marker = engine.firstPresent(OVERLAYS)
                if (marker != null) flow.closeWithBack(marker) else engine.pressBack()
                waitUntil(UI) { tabsShowing() }
                return@repeat
            }
            if (!engine.isPresent(T.PROFILE_SCREEN)) {
                val open = engine.tapAndConfirm(T.BOTTOMNAV_PROFILE_TAB, listOf(T.PROFILE_SCREEN), UI, retryAfterMs = UI, stopOn = loginIds)
                alive()
                if (!open.ok) return false
                return@repeat
            }
            return true
        }
        return tabsShowing() && engine.isPresent(T.PROFILE_SCREEN)
    }

    private val OVERLAYS = listOf(
        T.SWITCH_ACCOUNT_SHEET, T.CUSTOMER_SUPPORT_SHEET, T.BANK_DETAILS_SHEET, T.COMPANY_SCREEN, T.PROFILE_DETAILS_SCREEN,
        T.WITHDRAW_FUNDS_SCREEN, T.ADD_FUNDS_SCREEN, T.FUNDS_SCREEN, T.EXCHANGE_MESSAGES_SCREEN, T.MARKET_STATUS_SCREEN,
        T.RATE_APP_SCREEN, T.CHANGE_PASSWORD_SCREEN, T.TRADE_PREFERENCES_SCREEN, T.ACCESSIBILITY_SETTINGS_SCREEN,
        T.QR_SCAN_SCREEN,
    )

    /** Profile, on the Profile tab ([account]) or the Settings tab, scrolled to its top. */
    private fun onTab(account: Boolean) {
        if (loggedOut()) blocked("The app shows the sign-in screen: the session ended before this case", Cause.APP)
        if (!recover()) blocked(
            "Profile could not be opened: identifiers on screen: ${engine.visibleIdentifiers().joinToString(", ").ifEmpty { "none" }}",
            Cause.AUTOMATION,
        )
        val tab = if (account) T.PROFILE_TAB_ACCOUNT else T.PROFILE_TAB_TRADE_SETTINGS
        val name = if (account) "Profile" else "Settings"
        if (!onView(account)) {
            val tap = engine.tap(tab)
            alive()
            if (!tap.ok) fail("The $name tab could not be tapped: ${tap.detail}", Cause.AUTOMATION)
            if (!waitUntil(UI) { onView(account) }) fail("The $name tab did not show its content within ${UI / 1000} s of being tapped", Cause.APP)
        }
        if (taggedBuild == null) {
            taggedBuild = engine.isPresent(if (account) T.PROFILE_ACCOUNT_VIEW else T.PROFILE_TRADE_SETTINGS_VIEW)
        }
        engine.toTop()
        pause(150)
    }

    /** Waits until no section of the screen is loading; fails on the app's error screen. */
    private fun settle(what: String) {
        val started = engine.clock()
        val clear = waitUntil(LOAD) { engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 }
        timing(started, "$what loaded")
        if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0) {
            fail("$what shows the 'Something went wrong' screen instead of its data", Cause.API)
        }
        if (!clear) fail("$what was still loading after ${LOAD / 1000} s", Cause.API)
    }

    /** The readable lines under [container] (or the whole screen), without tab position labels. */
    private fun lines(container: String? = null): List<String> {
        val raw = if (container != null && engine.isPresent(container)) engine.labelsOf(container).flatMap { it.lines() }
        else engine.screenTexts(200)
        return raw.map { it.trim() }.filter { it.isNotEmpty() && !TAB_POSITION.matches(it) }.distinct()
    }

    private fun joined(all: List<String>): String = all.joinToString(" ").replace(Regex("\\s+"), " ")

    private fun missing(all: List<String>, wanted: List<String>): List<String> {
        val text = joined(all)
        return wanted.filterNot { text.contains(it, ignoreCase = true) }
    }

    /**
     * Opens [row] and waits for [screen]; checks it finishes loading and has readable content containing one of [anyOf]
     * (when given), runs [inside] on it, and closes it with Back. Nothing on the screen is tapped except by [inside].
     */
    private fun openAndReturn(
        row: Row,
        screen: Screen,
        what: String,
        account: Boolean,
        anyOf: List<String> = emptyList(),
        minLines: Int = 2,
        load: Boolean = true,
        inside: ((List<String>) -> Unit)? = null,
    ) {
        onTab(account)
        val started = engine.clock()
        tapRow(row, "The $what row")
        if (!waitUntil(UI) { isOpen(screen) || loggedOut() }) {
            fail("$what did not open within ${UI / 1000} s of tapping its row", Cause.APP)
        }
        if (loggedOut()) fail("Opening $what took the app to the sign-in screen", Cause.APP)
        timing(started, "$what opened")
        if (load) settle(what)
        var content = lines(screen.id)
        if (content.size < minLines) {
            waitUntil(UI) { lines(screen.id).size >= minLines }
            content = lines(screen.id)
        }
        if (content.isEmpty()) blocked(
            "$what opened but exposes no readable content, so it could not be verified",
            Cause.ACCESSIBILITY, "Give the page's labels Semantics so its content can be read.",
        )
        if (anyOf.isNotEmpty() && missing(content, anyOf).size == anyOf.size) {
            fail("$what is readable but shows none of: ${anyOf.joinToString(", ")}", Cause.UNCONFIRMED)
        }
        note("$what opened (${content.size} readable line(s))")
        shot(screenName())
        val leftAlready = try {
            inside?.invoke(content)
            false
        } catch (_: ScreenLeft) {
            true
        }
        if (!leftAlready) {
            if (!isOpen(screen)) fail("$what closed by itself while it was being checked", Cause.APP)
            if (!close(screen)) fail("$what did not close on Back", Cause.APP)
        } else if (isOpen(screen)) {
            close(screen)
        }
        if (!waitUntil(UI) { onView(account) }) {
            fail("Back from $what did not return to the ${if (account) "Profile" else "Settings"} tab", Cause.APP)
        }
    }

    // ═══ The run ═══════════════════════════════════════════════════════════════════════════════

    /** Runs every case. Returns a result only when the run has to end. */
    fun run(): AutomationRunResult? {
        for (case in CASES) {
            if (case.id in off) {
                val step = flow.step(case.id)
                if (step.status == StepStatus.PENDING) flow.skipAs(step, "not_tested", "turned off in the Profile settings")
                continue
            }
            runCase(case.id) { body(case.id) }?.let { return it }
        }
        return null
    }

    private fun body(id: String) {
        when (id) {
            "profile_account_loaded" -> accountLanding()
            "profile_details_opened" -> openAndReturn(
                R.HEADER, S.DETAILS, "Profile details", account = true, minLines = 3, inside = ::profileDetails,
            )
            "profile_switch_account_sheet" -> openAndReturn(
                R.SWITCH, S.SWITCH, "The Switch Account sheet", account = true, minLines = 1, load = false,
            ) { lines ->
                if (missing(lines, listOf("Switch Account")).isNotEmpty()) fail("The Switch Account sheet has no title", Cause.UNCONFIRMED)
                note("no account was chosen")
            }
            "profile_funds_opened" -> openAndReturn(R.FUNDS, S.FUNDS, "Funds", account = true, minLines = 3) { funds() }
            "profile_add_funds_opened" -> openAndReturn(
                R.ADD_FUNDS, S.ADD_FUNDS, "Add Funds", account = true, anyOf = listOf("Add Funds", "Amount", "Balance"),
            ) { note("nothing was paid") }
            "profile_exchange_messages" -> openAndReturn(
                R.EXCHANGE, S.EXCHANGE, "Exchange Messages", account = true, minLines = 1,
            ) { exchangeTabs() }
            "profile_market_status" -> openAndReturn(
                R.MARKET, S.MARKET, "Markets Status", account = true, anyOf = listOf("NSE", "BSE", "MCX", "NFO", "CDS", "BFO"),
            ) { marketStatus() }
            "profile_customer_support" -> openAndReturn(
                R.SUPPORT, S.SUPPORT, "The Customer Support sheet", account = true,
                anyOf = listOf("Call Us", "Email Us", "Company"), load = false,
            ) { customerSupport() }
            "profile_rate_app" -> openAndReturn(R.RATE, S.RATE, "Rate Our App", account = true, minLines = 1, load = false) {
                if (!engine.isPresent(T.RATE_SUBMIT_BUTTON) && !has("Submit")) fail("Rate Our App has no Submit button", Cause.APP)
                note("Submit present, not tapped; no rating was given")
            }
            "profile_footer_info" -> footer()
            "profile_qr_scan" -> qrScan()
            "profile_trade_settings_loaded" -> tradeSettingsLanding()
            "profile_toggle_fixed_header", "profile_toggle_notification", "profile_toggle_order_preview", "profile_toggle_theme" ->
                toggle(id.removePrefix("profile_toggle_"))
            "profile_font_size" -> fontSize()
            "profile_security_settings" -> openAndReturn(
                R.SECURITY, S.PASSWORD, "Security Settings (Change Password)", account = false,
                anyOf = listOf("Password"), load = false,
            ) { changePassword() }
            "profile_trade_preferences" -> openAndReturn(
                R.PREFERENCES, S.PREFERENCES, "Trade Settings (order preferences)", account = false, minLines = 3,
            ) { tradePreferences() }
            "profile_accessibility_settings" -> openAndReturn(
                R.ACCESSIBILITY, S.ACCESSIBILITY, "Accessibility Settings", account = false, load = false, minLines = 3,
            ) { accessibility() }
            "profile_guarded_rows" -> guardedRows()
        }
    }

    // ── PF-01 / PF-11: the two tabs ─────────────────────────────────────────────────────────────

    private fun accountLanding() {
        onTab(account = true)
        settle("The Profile tab")
        if (!has("Account Details") && lines(T.PROFILE_HEADER_CARD).isEmpty()) {
            fail("The profile card at the top of the Profile tab is not readable", Cause.UNCONFIRMED)
        }
        val rows = listOf(
            R.SWITCH to "Switch Account", R.FUNDS to "Funds", R.ADD_FUNDS to "Add Funds", R.EXCHANGE to "Exchange Messages",
            R.MARKET to "Markets Status", R.SUPPORT to "Customer Support", R.RATE to "Rate Our App", R.LOGOUT to "Logout",
        )
        val absent = rows.filterNot { rowPresent(it.first) }.map { it.second }
        engine.toTop()
        if (absent.isNotEmpty()) fail("The Profile tab has no ${absent.joinToString(", ")} row", Cause.APP)
        note("profile card and all ${rows.size} rows present")
        shot("profile_tab")
    }

    private fun tradeSettingsLanding() {
        onTab(account = false)
        settle("The Settings tab")
        val rows = listOf(
            Row("${T.PROFILE_TOGGLE_PREFIX}fixed_header", "Fixed Headers") to "Fixed Headers",
            Row("${T.PROFILE_TOGGLE_PREFIX}notification", "Notifications") to "Notifications",
            Row("${T.PROFILE_TOGGLE_PREFIX}order_preview", "orderPreview") to "Order preview",
            Row("${T.PROFILE_TOGGLE_PREFIX}theme", "Dark Theme") to "Dark Theme",
            Row("${T.PROFILE_FONT_SIZE_PREFIX}0", "Font sizes") to "Font sizes",
            R.TOTP to "TOTP", R.SECURITY to "Security Settings", R.FREEZE to "Freeze & UnFreeze",
            Row(T.PROFILE_TRADE_PREFERENCES_ROW, "Trade Settings") to "Trade Settings",
            R.ACCESSIBILITY to "Accessibility Settings", R.DEVELOPER to "Developer Console",
        )
        val absent = rows.filterNot { rowPresent(it.first) }.map { it.second }
        engine.toTop()
        if (absent.isNotEmpty()) fail("The Settings tab has no ${absent.joinToString(", ")}", Cause.APP)
        // The order preview row shows its translation keys instead of text on this build.
        if (has("orderPreviewDescription")) {
            note("the Order preview row shows the untranslated keys 'orderPreview' / 'orderPreviewDescription'")
            current?.let { it.recommendation = "Developers: add the 'orderPreview' and 'orderPreviewDescription' translations (the row shows the raw keys)." }
        }
        note("all ${rows.size} controls present")
        shot("settings_tab")
    }

    // ── PF-10: the foot of the Profile tab ──────────────────────────────────────────────────────

    private fun footer() {
        onTab(account = true)
        if (!rowPresent(R.LOGOUT)) fail("The Logout row is not on the Profile tab", Cause.APP)
        repeat(3) { if (engine.scrollPage(forward = true)) pause(200) }
        val all = lines()
        val wanted = listOf("SEBI Registration No", "NSE Member Code", "App Version", "Build Version")
        val gaps = missing(all, wanted)
        val links = (0 until 8).count { engine.isPresent("${T.PROFILE_SOCIAL_LINK_PREFIX}$it") }
        val follow = has("Follow us")
        shot("footer")
        engine.toTop()
        if (gaps.size == wanted.size && all.isEmpty()) blocked("The foot of the Profile tab exposes no readable text", Cause.ACCESSIBILITY)
        if (gaps.isNotEmpty()) fail("The foot of the Profile tab shows no ${gaps.joinToString(", ")}", Cause.UNCONFIRMED)
        all.firstOrNull { it.contains("App Version", ignoreCase = true) }?.let { note(it) }
        note(
            when {
                links > 0 -> "$links follow-us link(s) shown, not opened"
                follow -> "'Follow us on' shown, links not opened"
                else -> "no follow-us links shown"
            },
        )
    }

    // ── PF-12 ... PF-15: the switches ───────────────────────────────────────────────────────────

    private fun toggle(slug: String) {
        val id = "${T.PROFILE_TOGGLE_PREFIX}$slug"
        val label = TOGGLES.first { it.first == slug }.second
        onTab(account = false)
        if (slug == "theme" && taggedBuild == false) return themeByLabel(label)
        requireTags("Reading the $label's on / off state")
        if (!engine.isPresent(id)) engine.scrollIdIntoView(id)
        alive()
        if (!engine.isPresent(id)) missingTag(id, "The $label")
        val original = engine.switchStateOfId(id) ?: blocked(
            "The $label does not expose its on / off state, so a change could not be verified",
            Cause.ACCESSIBILITY, "The CupertinoSwitch should publish its toggled state (it does by default).",
        )
        cleanup = "nothing changed"
        if (slug == "theme") return themeRepaint(Control.ofId(engine, id), label, original)
        flip(id, label, to = !original)
        cleanup = "FAILED: $label left ${if (!original) "on" else "off"}; switch it back by hand"
        if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0) {
            flip(id, label, to = original)
            fail("Switching $label showed the 'Something went wrong' screen", Cause.API)
        }
        shot("${slug}_${if (!original) "on" else "off"}")
        flip(id, label, to = original)
        cleanup = "restored: $label is ${if (original) "on" else "off"} again"
        note("$label: ${if (original) "on" else "off"} → ${if (!original) "on" else "off"} → ${if (original) "on" else "off"}")
    }

    /** A switch: by its identifier, or (an older build) beside its row's label. */
    private class Control(val reveal: () -> Unit, val read: () -> Boolean?, val press: () -> AutomationEngine.ActionOutcome) {
        companion object {
            fun ofId(engine: AutomationEngine, id: String) = Control(
                { if (!engine.isTappable(id)) engine.scrollIdIntoView(id) },
                { engine.switchStateOfId(id) },
                { engine.tap(id) },
            )

            fun besideText(engine: AutomationEngine, label: String) = Control(
                { if (engine.switchStateBesideText(label) == null) engine.scrollTextIntoView(label) },
                { engine.switchStateBesideText(label) },
                { engine.tapSwitchBesideText(label) },
            )
        }
    }

    private fun flip(id: String, label: String, to: Boolean) = flip(Control.ofId(engine, id), label, to)

    /** Taps the switch once and waits until it reads [to]; fails when it does not. */
    private fun flip(control: Control, label: String, to: Boolean) {
        val started = engine.clock()
        control.reveal()
        alive()
        val tap = control.press()
        alive()
        if (!tap.ok) fail("The $label could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        // The theme repaints the whole app, and Notification / Order preview save on the server: give them the load time.
        if (!waitUntil(LOAD) { control.read() == to }) {
            fail("The $label did not turn ${if (to) "on" else "off"} within ${LOAD / 1000} s of being tapped", Cause.APP)
        }
        timing(started, "$label turned ${if (to) "on" else "off"}")
        pause(Timeouts.SETTLE)
    }

    /**
     * PF-15: the switch turning over proves only the switch. The screens have to repaint, so the Settings tab is read
     * from a screenshot before, the Settings tab, the Profile tab and a pushed page (Exchange Messages) in the other
     * theme, and the Settings and Profile tabs again once it is restored. The switch is put back before any failure.
     */
    private fun themeRepaint(control: Control, label: String, original: Boolean) {
        val problems = mutableListOf<String>()
        val seen = mutableListOf<String>()
        looks(original, "Settings tab (before)", problems, seen)
        flip(control, label, to = !original)
        cleanup = "FAILED: $label left ${if (!original) "on" else "off"}; switch it back by hand"
        if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0) problems += "switching it showed the 'Something went wrong' screen"
        looks(!original, "Settings tab", problems, seen)
        try {
            onTab(account = true)
            looks(!original, "Profile tab", problems, seen)
            tapRow(R.EXCHANGE, "The Exchange Messages row")
            if (waitUntil(UI) { isOpen(S.EXCHANGE) }) {
                waitUntil(LOAD) { engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 }
                looks(!original, "Exchange Messages", problems, seen)
                close(S.EXCHANGE)
            } else {
                seen += "Exchange Messages did not open, so it was not checked"
            }
        } catch (e: CaseEnd) {
            seen += "the other screens could not be checked: ${e.message}"
        }
        onTab(account = false)
        flip(control, label, to = original)
        cleanup = "restored: $label is ${if (original) "on" else "off"} again"
        looks(original, "Settings tab (restored)", problems, seen)
        try {
            onTab(account = true)
            looks(original, "Profile tab (restored)", problems, seen)
        } catch (e: CaseEnd) {
            seen += "the Profile tab could not be checked after restoring: ${e.message}"
        }
        note("$label: ${if (original) "on" else "off"} → ${if (!original) "on" else "off"} → ${if (original) "on" else "off"}")
        note("brightness: " + seen.joinToString(", "))
        if (problems.isNotEmpty()) fail(
            "The theme did not follow the switch: " + problems.joinToString("; "), Cause.APP,
            "Every screen should read the theme with ref.watch(themeProvider) (not ref.read) so it repaints when Dark theme changes.",
        )
        if (seen.none { it.contains(" %") }) note("the repaint could not be read: no screenshot was available")
    }

    /**
     * PF-15 on a build without the Profile identifiers: the Dark Theme switch is found beside its label and must
     * expose its on / off state; the repaint is then checked exactly as on a tagged build.
     */
    private fun themeByLabel(label: String) {
        val text = "Dark Theme"
        engine.scrollTextIntoView(text)
        alive()
        if (!has(text)) fail("The Settings tab shows no '$text' row", Cause.APP)
        val control = Control.besideText(engine, text)
        val original = control.read() ?: blocked(
            "No readable switch was found beside '$text', so it was not tapped",
            Cause.ACCESSIBILITY,
            "Install a Tradion build with the Profile identifiers (profile_toggle_theme).",
        )
        note("switch found beside its label (older Tradion build)")
        cleanup = "nothing changed"
        themeRepaint(control, label, original)
    }

    /**
     * Reads the screen's brightness from a screenshot right away and records a problem when it does not match [dark].
     * A screen in the wrong theme is read once more after [THEME_RECHECK], so a repaint still in progress is not a
     * failure; one that is still wrong then is.
     */
    private fun looks(dark: Boolean, where: String, problems: MutableList<String>, seen: MutableList<String>) {
        val name = "theme_${if (dark) "dark" else "light"}_" + where.lowercase().replace(Regex("[^a-z]+"), "_").trim('_')
        fun read(): Double? {
            pause(Timeouts.SETTLE)
            return shot(name)?.let { ScreenshotInspector.brightness(it) }
        }
        fun wrong(b: Double) = if (dark) b > DARK_MAX else b < LIGHT_MIN
        var b = read()
        if (b != null && wrong(b)) {
            pause(THEME_RECHECK)
            b = read()
        }
        if (b == null) {
            seen += "$where not read (no screenshot)"
            return
        }
        val pct = "%.0f".format(b * 100)
        seen += "$where $pct %"
        if (dark && b > DARK_MAX) problems += "$where still looks light with Dark theme on (brightness $pct %)"
        if (!dark && b < LIGHT_MIN) problems += "$where still looks dark with Dark theme off (brightness $pct %)"
    }

    // ── PF-16: font size ────────────────────────────────────────────────────────────────────────

    private fun fontSize() {
        onTab(account = false)
        requireTags("Reading which font size is selected")
        val segments = (0..2).map { "${T.PROFILE_FONT_SIZE_PREFIX}$it" }
        if (!engine.isPresent(segments[0])) engine.scrollIdIntoView(segments[0])
        alive()
        if (!engine.isPresent(segments[0])) missingTag(segments[0], "The Font sizes control")
        val names = listOf("Small", "Medium", "Large")
        val original = segments.indexOfFirst { engine.isSelected(it) }
        if (original < 0) blocked(
            "None of Small / Medium / Large reports itself as selected, so a change could not be verified",
            Cause.ACCESSIBILITY, "Publish the selected segment (AutomationTag(selected:)).",
        )
        val other = if (original == 2) 1 else 2
        cleanup = "nothing changed"
        select(segments[other], names[other])
        cleanup = "FAILED: the font size was left at ${names[other]}; set it back to ${names[original]} by hand"
        shot("font_${names[other].lowercase()}")
        select(segments[original], names[original])
        cleanup = "restored: font size ${names[original]}"
        note("font size ${names[original]} → ${names[other]} → ${names[original]}")
    }

    private fun select(id: String, name: String) {
        if (!engine.isTappable(id)) engine.scrollIdIntoView(id)
        alive()
        val tap = engine.tap(id)
        alive()
        if (!tap.ok) fail("Font size $name could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(UI) { engine.isSelected(id) }) fail("Font size $name does not show as selected after it was tapped", Cause.APP)
        pause(Timeouts.SETTLE)
    }

    // ── Inside the screens ──────────────────────────────────────────────────────────────────────

    /** PF-02: the personal fields are filled, every segment group has a status, and the bank details sheet opens. */
    private fun profileDetails(content: List<String>) {
        repeat(3) { if (engine.scrollPage(forward = true)) pause(200) }
        val all = (content + lines(T.PROFILE_DETAILS_SCREEN)).distinct()
        val fields = listOf("Email", "Phone", "PAN", "Demat")
        val absent = missing(all, fields)
        if (absent.size == fields.size) fail("Profile details shows none of ${fields.joinToString(", ")}", Cause.UNCONFIRMED)
        if (absent.isNotEmpty()) fail("Profile details has no ${absent.joinToString(", ")} field", Cause.APP)
        // A field the server did not fill shows "--"; the values themselves are personal and are never recorded.
        val empty = all.count { it == "--" }
        if (empty > 0) fail("$empty personal detail field(s) show '--' (not returned by the server)", Cause.API)
        val statuses = all.count { it.equals("ACTIVE", true) || it.equals("INACTIVE", true) }
        if (statuses == 0) fail("Profile details lists no segment with an ACTIVE / INACTIVE status", Cause.UNCONFIRMED)
        note("Email, Phone, PAN and Demat filled; $statuses segment status(es) shown; email / phone links not opened")
        engine.toTop()
        requireTags("Opening the bank details sheet")
        engine.scrollIdIntoView(T.PROFILE_BANK_DETAILS_ROW)
        alive()
        if (!engine.isPresent(T.PROFILE_BANK_DETAILS_ROW)) {
            note("no bank account row (none linked)")
            return
        }
        val open = engine.tapAndConfirm(T.PROFILE_BANK_DETAILS_ROW, listOf(T.BANK_DETAILS_SHEET), UI, retryAfterMs = UI)
        alive()
        if (!open.ok) fail("The bank account row did not open its details sheet: ${open.detail}", Cause.APP)
        if (lines(T.BANK_DETAILS_SHEET).isEmpty()) fail("The bank details sheet is empty", Cause.UNCONFIRMED)
        if (!flow.closeWithBack(T.BANK_DETAILS_SHEET)) fail("The bank details sheet did not close on Back", Cause.APP)
        engine.toTop()
        note("bank details sheet opened and closed")
    }

    /** PF-04: Funds carries Add Funds and Withdraw; Withdraw opens its screen and is left. */
    private fun funds() {
        if (!has("Add Funds") && !engine.isPresent(T.FUNDS_ADD_BUTTON)) fail("Funds has no Add Funds button", Cause.APP)
        if (!has("Withdraw") && !engine.isPresent(T.FUNDS_WITHDRAW_BUTTON)) fail("Funds has no Withdraw button", Cause.APP)
        note("Add Funds and Withdraw buttons shown")
        requireTags("Opening Withdraw")
        val started = engine.clock()
        val open = engine.tapAndConfirm(T.FUNDS_WITHDRAW_BUTTON, listOf(T.WITHDRAW_FUNDS_SCREEN), UI, retryAfterMs = UI)
        alive()
        if (!open.ok) fail("Withdraw did not open its screen: ${open.detail}", Cause.APP)
        timing(started, "Withdraw opened")
        settle("Withdraw")
        if (lines(T.WITHDRAW_FUNDS_SCREEN).isEmpty()) fail("The Withdraw screen shows nothing readable", Cause.UNCONFIRMED)
        shot("withdraw")
        if (!flow.closeWithBack(T.WITHDRAW_FUNDS_SCREEN)) fail("Withdraw did not close on Back", Cause.APP)
        if (!waitUntil(UI) { isOpen(S.FUNDS) }) fail("Back from Withdraw did not return to Funds", Cause.APP)
        note("Withdraw opened and left (nothing withdrawn)")
    }

    /**
     * PF-06, a load test of every exchange tab. Each tab is selected and its load timed; its list is then paged to the
     * end and the rows shown are counted against what the server sent (the `exchange_messages_count_<EX>` marker), so
     * a tab that drops messages fails even though it "shows data". A second, rapid pass switches through every tab
     * without waiting, then checks that every tab still shows what it showed the first time.
     */
    private fun exchangeTabs() {
        val names = listOf("NSE", "NFO", "BSE", "BFO", "NCOM", "MCX", "NSLB", "NCX")
        val results = mutableListOf<String>()
        val appProblems = mutableListOf<String>()
        val apiProblems = mutableListOf<String>()
        val sentFirst = mutableMapOf<String, Int>()
        // Tabs whose first row was shown in the first pass: only these are expected to show it again.
        val shownFirst = mutableSetOf<String>()
        var lastHidden = 0
        var slowest = 0L
        var slowestName = ""
        var counted = true
        for ((i, name) in names.withIndex()) {
            val tapped = selectExchange(i, name)
            if (tapped != null) {
                appProblems += tapped
                continue
            }
            pause(300)
            val started = engine.clock()
            val settled = waitUntil(LOAD) {
                engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 && sentOf(name) != "none"
            }
            val took = engine.clock() - started
            if (took > slowest) {
                slowest = took
                slowestName = name
            }
            val time = "%.1f s".format(took / 1000.0)
            val sentText = sentOf(name)
            val empty = has("NO DATA FOUND")
            if (engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0) {
                apiProblems += "$name shows the error screen"
                continue
            }
            if (sentText == null) {
                // A build without the count marker: only the load state can be read.
                counted = false
                when {
                    !settled -> apiProblems += "$name still loading after ${LOAD / 1000} s"
                    empty -> results += "$name: no data ($time)"
                    else -> results += "$name: messages ($time)"
                }
                continue
            }
            val sent = sentText.toIntOrNull()
            if (sent == null) {
                apiProblems += "$name: no response from the server within ${LOAD / 1000} s" +
                    if (empty) " (the tab shows NO DATA FOUND as if there were none)" else ""
                continue
            }
            sentFirst[name] = sent
            if (sent == 0) {
                if (!empty) appProblems += "$name: the server sent no messages but the tab does not say NO DATA FOUND"
                results += "$name: 0 sent, NO DATA FOUND ($time)"
                continue
            }
            if (empty) appProblems += "$name: the server sent $sent message(s) but the tab shows NO DATA FOUND"
            // Only the first rows are checked; the rest of a long list is skipped.
            val checked = minOf(sent, EXCHANGE_ROWS_CHECKED)
            val (seen, blank) = pageThroughMessages(checked)
            val shown = seen.filter { it < checked }
            val missingRows = (0 until checked).filterNot { it in shown }
            if (0 in shown) shownFirst += name
            results += if (checked < sent) "$name: ${shown.size} of the first $checked shown ($sent sent, the rest skipped) ($time)"
            else "$name: ${shown.size} of $sent shown ($time)"
            if (missingRows.isNotEmpty()) {
                if (checked == sent && missingRows == listOf(sent - 1)) {
                    lastHidden++
                    appProblems += "$name: shows ${shown.size} of $sent messages; the last one (#$sent) is never shown"
                } else {
                    appProblems += "$name: shows ${shown.size} of the first $checked messages; missing #" +
                        missingRows.take(8).joinToString(", #") { "${it + 1}" } + if (missingRows.size > 8) " …" else ""
                }
            }
            if (blank > 0) appProblems += "$name: $blank message row(s) are blank"
            if (took >= StepApi.SLOW_MS) note("$name was slow to load ($time)")
        }
        if (slowestName.isNotEmpty()) timing(engine.clock() - slowest, "slowest tab ($slowestName) loaded")
        note(results.joinToString("; "))

        // Rapid pass: every tab tapped without waiting, then each one read again.
        if (sentFirst.isNotEmpty()) {
            for ((i, name) in names.withIndex()) {
                selectExchange(i, name, confirm = false)
                pause(150)
            }
            val changed = mutableListOf<String>()
            for ((i, name) in names.withIndex()) {
                val before = sentFirst[name] ?: continue
                if (selectExchange(i, name) != null) continue
                pause(300)
                waitUntil(LOAD) { engine.countOf(AutomationConfig.Ids.UI_LOADING) == 0 && sentOf(name) != "none" }
                val now = sentOf(name)?.toIntOrNull()
                when {
                    engine.countOf(AutomationConfig.Ids.UI_ERROR) > 0 -> changed += "$name shows the error screen"
                    now == null -> changed += "$name lost its data"
                    now != before -> changed += "$name: $before → $now message(s)"
                    now > 0 && name in shownFirst && !waitUntil(UI) { engine.isPresent("${T.EXCHANGE_MESSAGE_ITEM_PREFIX}0") } ->
                        changed += "$name shows no rows (the server sent $now)"
                    now == 0 && !has("NO DATA FOUND") -> changed += "$name no longer says NO DATA FOUND"
                }
            }
            if (changed.isEmpty()) note("rapid pass through all ${names.size} tabs: every tab kept its data")
            else appProblems += "after rapid tab switching: " + changed.joinToString(", ")
        }

        if (appProblems.isNotEmpty()) fail(
            (appProblems + apiProblems).joinToString("; "), Cause.APP,
            if (lastHidden > 0) "exchange_message_screen.dart, NseListView: the itemBuilder returns Container() for " +
                "i == list.length - 1, so every tab hides its last message; build NseListCard for every index." else null,
        )
        if (apiProblems.isNotEmpty()) fail(apiProblems.joinToString("; "), Cause.API)
        if (!counted) throw CaseEnd(
            Kind.BLOCKED,
            "Every tab loaded, but the messages could not be counted against what the server sent: the installed " +
                "Tradion has no exchange_messages_count_ marker. What could be checked: ${notes.joinToString("; ")}.",
            Cause.AUTOMATION, null,
            "Rebuild Tradion (flutter build apk) from the working tree and install it, then run again.",
        )
    }

    /** Selects exchange tab [i]. Null when done; otherwise what went wrong. */
    private fun selectExchange(i: Int, name: String, confirm: Boolean = true): String? {
        val id = "${T.EXCHANGE_MESSAGES_TAB_PREFIX}$i"
        val tap = if (engine.isPresent(id) || (taggedBuild == true && engine.revealInList(id, listOf(id)))) {
            engine.tap(id)
        } else {
            // The tab strip scrolls sideways; its labels are the topmost texts of their name.
            engine.tapText(name, topmost = true)
        }
        alive()
        if (!tap.ok) return "$name could not be tapped"
        if (confirm && engine.isPresent(id) && !waitUntil(UI) { engine.isSelected(id) }) return "$name does not show as selected"
        return null
    }

    /** What the server sent for tab [name]: a count, "none" before a response, null on a build without the marker. */
    private fun sentOf(name: String): String? = engine.labelOf("${T.EXCHANGE_MESSAGES_COUNT_PREFIX}$name")?.trim()

    /**
     * Pages the selected tab's list until the first [sent] rows have been seen, or to its end. Returns the indexes of
     * the message rows seen and how many of them had no text. Message text is never recorded.
     */
    private fun pageThroughMessages(sent: Int): Pair<Set<Int>, Int> {
        val prefix = T.EXCHANGE_MESSAGE_ITEM_PREFIX
        val seen = sortedSetOf<Int>()
        var blank = 0
        var still = 0
        val maxPages = (sent / 3 + 4).coerceIn(4, 80)
        for (page in 0 until maxPages) {
            val fresh = engine.visibleIdentifiers(limit = 400)
                .filter { it.startsWith(prefix) }
                .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
                .filter { seen.add(it) }
            blank += fresh.filter { it < sent }.count { idx -> engine.labelsOf("$prefix$idx").all { it.isBlank() } }
            if ((0 until sent).all { it in seen }) break
            if (fresh.isEmpty()) {
                if (++still >= 2) break
            } else {
                still = 0
            }
            if (!engine.scrollPage(forward = true)) break
            pause(250)
        }
        engine.toTop()
        return seen to blank
    }

    /** PF-07: every exchange row carries a status. */
    private fun marketStatus() {
        if (has("No Data")) fail("Markets Status shows No Data", Cause.API)
        requireTags("Reading each exchange row's status")
        val rows = engine.groupedLabelsOf(T.MARKET_STATUS_ROW)
        if (rows.isEmpty()) missingTag(T.MARKET_STATUS_ROW, "The exchange rows")
        val bare = rows.filter { it.lines().map(String::trim).filter(String::isNotEmpty).size < 2 }
        if (bare.isNotEmpty()) fail("${bare.size} of ${rows.size} exchange row(s) show no status", Cause.UNCONFIRMED)
        note("${rows.size} exchange(s): " + rows.joinToString(", ") { it.lines().map(String::trim).filter(String::isNotEmpty).joinToString(" ") })
    }

    /** PF-08: Call Us / Email Us shown (not opened); Company opens the company page with its links. */
    private fun customerSupport() {
        val absent = listOf("Call Us", "Email Us", "Company").filterNot(::has)
        if (absent.isNotEmpty()) fail("The Customer Support sheet has no ${absent.joinToString(", ")}", Cause.APP)
        val tap = if (engine.isPresent(T.SUPPORT_COMPANY_ROW)) engine.tap(T.SUPPORT_COMPANY_ROW) else engine.tapText("Company")
        alive()
        if (!tap.ok) fail("Company could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        if (!waitUntil(UI) { engine.isPresent(T.COMPANY_SCREEN) || (has("SEBI") && !has("Call Us")) }) {
            fail("Company did not open the company page", Cause.APP)
        }
        repeat(4) { if (engine.scrollPage(forward = true)) pause(200) }
        val page = lines(T.COMPANY_SCREEN)
        engine.toTop()
        if (page.isEmpty()) blocked("The company page exposes no readable content", Cause.ACCESSIBILITY)
        val gaps = missing(page, listOf("SEBI", "NSE", "BSE", "MCX"))
        shot("company")
        if (engine.isPresent(T.COMPANY_SCREEN)) flow.closeWithBack(T.COMPANY_SCREEN) else engine.pressBack()
        if (!waitUntil(UI) { has("Call Us") || onView(account = true) }) {
            fail("Back from the company page did not return to Customer Support", Cause.APP)
        }
        if (gaps.isNotEmpty()) fail("The company page shows no ${gaps.joinToString(", ")} link", Cause.UNCONFIRMED)
        note("Call Us and Email Us shown (not opened: dialer / mail app); company page shows ${page.size} line(s) with the SEBI and exchange links (not opened: browser)")
        // Company pops the sheet on some builds: the caller then has nothing left to close.
        if (!has("Call Us")) throw ScreenLeft
    }

    /** PF-17: Change Password refuses an empty submission field by field and sends nothing. */
    private fun changePassword() {
        val absent = listOf("Current Password", "New Password", "Confirm Password").filterNot(::has)
        if (absent.isNotEmpty()) fail("Change Password has no ${absent.joinToString(", ")} field", Cause.APP)
        // The fields are empty on opening, and the button validates them before any request is made.
        val tap = if (engine.isPresent(T.CHANGE_PASSWORD_SUBMIT)) engine.tap(T.CHANGE_PASSWORD_SUBMIT)
        else engine.tapLowestExactText("Change Password")
        alive()
        if (!tap.ok) fail("Change Password could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        val wanted = listOf("Old Password cannot be empty", "New Password cannot be empty", "Confirm Password cannot be empty")
        if (!waitUntil(UI) { wanted.all(::has) }) {
            fail("Submitting empty fields did not show: ${wanted.filterNot(::has).joinToString(", ")}", Cause.APP)
        }
        engine.hideKeyboard()
        shot("validation")
        note("an empty submission shows all three 'cannot be empty' errors; nothing was sent")
    }

    /** PF-18: segment tabs, quantity stepper and the Save Settings state; nothing is saved. */
    private fun tradePreferences() {
        if (!has("Default Quantity")) fail("The order preferences show no Default Quantity", Cause.UNCONFIRMED)
        note("Default Quantity shown")
        requireTags("Switching segments and reading the Save Settings state")
        val tabs = (0 until 8).map { "${T.TRADE_PREF_TAB_PREFIX}$it" }.filter(engine::isPresent)
        if (tabs.isEmpty()) missingTag("${T.TRADE_PREF_TAB_PREFIX}0", "The segment tabs")
        val original = tabs.firstOrNull(engine::isSelected) ?: tabs.first()
        val seen = mutableListOf<String>()
        for (id in tabs) {
            val name = joined(lines(id))
            val tap = engine.tap(id)
            alive()
            if (!tap.ok) fail("Segment $name could not be tapped: ${tap.detail}", Cause.AUTOMATION)
            if (!waitUntil(UI) { engine.isSelected(id) }) fail("Segment $name does not show as selected after it was tapped", Cause.APP)
            if (!waitUntil(UI) { has("Default Quantity") }) fail("Segment $name shows no Default Quantity", Cause.UNCONFIRMED)
            if (engine.isPresent(T.TRADE_PREF_SAVE_BUTTON) && !engine.isDisabled(T.TRADE_PREF_SAVE_BUTTON)) {
                fail("Save Settings is enabled on $name although nothing was changed", Cause.APP)
            }
            seen += name
        }
        engine.tap(original)
        waitUntil(UI) { engine.isSelected(original) }
        note("segments ${seen.joinToString(", ")} each selected and showed their settings; Save Settings disabled with no change")
        if (!engine.isPresent(T.TRADE_PREF_QTY_PLUS)) engine.scrollIdIntoView(T.TRADE_PREF_QTY_PLUS)
        alive()
        if (!engine.isPresent(T.TRADE_PREF_QTY_PLUS) || !engine.isPresent(T.TRADE_PREF_QTY_MINUS)) {
            missingTag(T.TRADE_PREF_QTY_PLUS, "The Default Quantity + / − buttons")
        }
        cleanup = "nothing changed"
        if (!engine.tap(T.TRADE_PREF_QTY_PLUS).ok) fail("Quantity + could not be tapped", Cause.AUTOMATION)
        cleanup = "the quantity was raised by 1 and not saved; leaving discards it"
        alive()
        if (!waitUntil(UI) { !engine.isDisabled(T.TRADE_PREF_SAVE_BUTTON) }) {
            fail("Save Settings stayed disabled after the quantity was changed", Cause.APP)
        }
        if (!engine.tap(T.TRADE_PREF_QTY_MINUS).ok) fail("Quantity − could not be tapped", Cause.AUTOMATION)
        alive()
        if (!waitUntil(UI) { engine.isDisabled(T.TRADE_PREF_SAVE_BUTTON) }) {
            fail("Save Settings stayed enabled after the quantity was set back to its saved value", Cause.APP)
        }
        cleanup = "restored: quantity back to its saved value, nothing saved"
        engine.hideKeyboard()
        // Back must leave without "Do you want to save changes?": nothing differs from what is saved.
        engine.pressBack()
        if (waitUntil(UI) { engine.isPresent(T.TRADE_PREF_DISCARD_BUTTON) }) {
            engine.tap(T.TRADE_PREF_DISCARD_BUTTON)
            waitUntil(UI) { !engine.isPresent(T.TRADE_PREF_DISCARD_BUTTON) }
            cleanup = "restored: the save prompt was answered with Discard"
            fail("Back asked \"Do you want to save changes?\" although the quantity was back to its saved value", Cause.APP)
        }
        note("quantity + enabled Save Settings and − disabled it again; Back left without a save prompt")
        throw ScreenLeft
    }

    /** PF-19: every section is there and each Motion / Touch Targets switch goes over and back. */
    private fun accessibility() {
        repeat(6) { if (engine.scrollPage(forward = true)) pause(200) }
        val all = lines()
        engine.toTop()
        val sections = listOf("Font Size", "Theme", "High Contrast", "Color Blind Mode", "Motion", "Touch Targets", "Disability Profiles")
        val gaps = missing(all, sections)
        if (gaps.isNotEmpty()) fail("Accessibility Settings has no ${gaps.joinToString(", ")} section", Cause.UNCONFIRMED)
        note("all ${sections.size} sections shown")
        requireTags("Reading the Motion and Touch Targets switches")
        val done = mutableListOf<String>()
        for ((id, label) in T.A11Y_SWITCHES) {
            if (!engine.isPresent(id)) engine.scrollIdIntoView(id)
            alive()
            if (!engine.isPresent(id)) missingTag(id, "The $label switch")
            val original = engine.switchStateOfId(id)
                ?: blocked("The $label switch does not expose its on / off state", Cause.ACCESSIBILITY)
            cleanup = "FAILED: $label may be left ${if (!original) "on" else "off"}; check it by hand"
            flip(id, label, to = !original)
            flip(id, label, to = original)
            cleanup = "restored: every switch is as it was"
            done += label
        }
        engine.toTop()
        note("switched over and back: ${done.joinToString(", ")}")
    }

    /** PF-21: the header scan icon opens the QR login scanner (after the camera permission). */
    private fun qrScan() {
        onTab(account = true)
        requireTags("The scan icon (it has no label to find it by)")
        if (!engine.isPresent(T.HEADER_SCAN_BUTTON)) missingTag(T.HEADER_SCAN_BUTTON, "The scan icon")
        val tap = engine.tap(T.HEADER_SCAN_BUTTON)
        alive()
        if (!tap.ok) fail("The scan icon could not be tapped: ${tap.detail}", Cause.AUTOMATION)
        var prompt: String? = null
        waitUntil(UI) {
            prompt = engine.permissionPromptMessage()
            prompt != null || engine.isPresent(T.QR_SCAN_SCREEN)
        }
        if (prompt != null) {
            // The camera is the tester's decision: the tool never grants or refuses a permission other than notifications.
            engine.showNotice("Tradion asks for the camera (QR login). Allow it to let the scanner be checked; otherwise this case is skipped in 20 s.")
            try {
                waitUntil(20_000L) { engine.permissionPromptMessage() == null }
            } finally {
                engine.hideNotice()
            }
            if (engine.permissionPromptMessage() != null) {
                engine.pressBack()
                pause(800)
                if (!engine.targetInForeground()) engine.returnToTarget()
                throw CaseEnd(Kind.NOT_TESTED, "The camera permission prompt was not answered, so the scanner could not open (prompt: \"$prompt\")")
            }
            if (!engine.targetInForeground()) {
                // Refusing makes Tradion open its App info page in Settings.
                engine.returnToTarget()
                throw CaseEnd(Kind.NOT_TESTED, "The camera permission was refused; Tradion opened its App info to ask for it again. Allow the camera to test the scanner.")
            }
        }
        if (!waitUntil(UI) { engine.isPresent(T.QR_SCAN_SCREEN) }) {
            if (has("allow camera permission")) {
                throw CaseEnd(Kind.NOT_TESTED, "The camera permission is refused for Tradion (the app says 'Please allow camera permission')")
            }
            fail("Tapping the scan icon did not open the QR login scanner within ${UI / 1000} s", Cause.APP)
        }
        shot("qr_scanner")
        if (!flow.closeWithBack(T.QR_SCAN_SCREEN)) fail("The QR scanner did not close on Back", Cause.APP)
        note("QR login scanner opened and left; nothing scanned")
    }

    // ── PF-20: rows that are only looked at ─────────────────────────────────────────────────────

    private fun guardedRows() {
        onTab(account = false)
        val found = mutableListOf<String>()
        for ((row, states) in listOf(
            R.TOTP to listOf("Enable TOTP", "Disable TOTP"),
            R.FREEZE to listOf("Freeze"),
            R.DEVELOPER to listOf("Developer Console"),
        )) {
            if (!rowPresent(row)) fail("The ${row.label} row is not on the Settings tab", Cause.APP)
            val text = if (engine.isPresent(row.id)) joined(lines(row.id)) else joined(lines())
            val state = states.firstOrNull { text.contains(it, ignoreCase = true) }
                ?: fail("The ${row.label} row is readable but shows none of ${states.joinToString(" / ")}", Cause.UNCONFIRMED)
            if (engine.isPresent(row.id) && !engine.isTappable(row.id)) fail("The ${row.label} row is not an enabled, tappable control", Cause.APP)
            found += if (row === R.TOTP) "TOTP row reads '$state'" else "${row.label} row shown"
        }
        engine.toTop()
        note(found.joinToString("; ") + "; none tapped (they send an OTP, change 2FA, open the browser or hand over to SSO)")
    }
}

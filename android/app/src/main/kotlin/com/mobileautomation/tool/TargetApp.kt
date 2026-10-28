package com.mobileautomation.tool

/**
 * The applications this tool is permitted to drive, and everything that differs between them.
 *
 * This is the allowlist: a package that is not listed here is refused before it
 * is inspected or launched (see [AutomationConfig.ALLOWED_PACKAGES]). Each entry
 * also selects the test flow that runs against it ([flow]) and says what of that
 * flow the app does not have.
 *
 * Which of them the picker offers is [AutomationConfig.OFFERED_APPS]. A new app's package must also be named by the
 * manifest's `<queries>`, `android:packageNames` in res/xml/accessibility_service_config.xml, the Dart fallback
 * config, report_service.dart and scripts/provision_device.sh; TargetAppRegistryTest fails, naming the file, when one
 * is missed.
 */
enum class TargetApp(
    /** Stable id used on the platform channel and in the report. */
    val id: String,
    val displayName: String,
    val packageName: String,
    val testName: String,
    /** What the tester types after the one-time code. */
    val secret: Secret,
    /** The test flow that drives it. Every Tradion-family app uses [Flow.TRADION]. */
    val flow: Flow = Flow.TRADION,
    /** Modules of [flow] (ids from [AppModules]) the app has nothing for: they are left out of its checklist. */
    val absentModules: Set<String> = emptySet(),
    /**
     * Features of the shared flow this app does not have (see [Feature]). Their checks are reported as NOT
     * APPLICABLE instead of failing on a control that cannot exist.
     */
    val absent: Set<String> = emptySet(),
    /** The tabs of Profile › Exchange Messages, in order (each tab's tag is `exchange_messages_tab_<index>`). */
    val exchangeMessageTabs: List<String> = listOf("NSE", "NFO", "BSE", "BFO", "NCOM", "MCX", "NSLB", "NCX"),
    /** Text the foot of Profile › Account must show (registration lines and versions). */
    val profileFooterLines: List<String> = listOf("SEBI Registration No", "NSE Member Code", "App Version", "Build Version"),
    /** Watchlist / Dashboard cases (by step id) this app has nothing for, with the reason the report gives. */
    val notApplicableCases: Map<String, String> = emptyMap(),
    /** The index the Dashboard's ticker leads with (DB-01). */
    val tickerIndex: String = "NIFTY 50",
    /**
     * The section headings of the Dashboard's Investments tab (DB-I01), each matched as part of a line; a heading that
     * the tab's own name "Investments" starts with (iTrade's "Invest") is matched as a whole line.
     */
    val investmentsSections: List<String> = listOf("Indian Indices", "Quick Tools", "Funds", "Holdings", "Scanners", "Top Sectors", "Customi"),
    /** The Top Sectors cards print their 3M / 6M / 1Y figures with a % sign (without it, as bare numbers under the periods). */
    val sectorFiguresInPercent: Boolean = true,
    /** Top Sectors › View All lists NSE constituents by their series ("INFY-EQ"); without it, by symbol and exchange line. */
    val sectorRowsWithSeries: Boolean = true,
) {
    ASHIKA(
        id = "ashika",
        displayName = "Ashika",
        packageName = "com.codifi.dhanush",
        testName = "Phase 1 - Login Automation",
        secret = Secret.MPIN,
        flow = Flow.ASHIKA,
    ),
    TRADION(
        id = "tradion",
        displayName = "Tradion",
        packageName = "com.codifi.rmoney",
        testName = "Tradion - Regression Automation",
        secret = Secret.PASSWORD,
    ),
    /** A sibling build of Tradion: same login and identifiers, driven by [TradionAutomationFlow]. */
    ITRADE(
        id = "itrade",
        displayName = "iTrade",
        packageName = "com.codifi.itrade",
        testName = "iTrade - Regression Automation",
        secret = Secret.PASSWORD,
        absent = setOf(
            Feature.GUEST_USER,
            Feature.UNBLOCK_WITHOUT_USER_ID,
            Feature.ORDERS_GTT_TAB,
            Feature.ORDERS_SIP_TAB,
            Feature.BO_CO_TAB,
            Feature.MARKET_PROTECTION,
            Feature.GTT_DEFAULT_GREATER,
            Feature.SEARCH_COMMODITY_TAB,
            Feature.TECHNICALS_MOVING_AVERAGES,
            Feature.PROFILE_SWITCH_ACCOUNT,
            Feature.PROFILE_RATE_APP,
            Feature.PROFILE_NOTIFICATION_TOGGLE,
            Feature.PROFILE_ORDER_PREVIEW_TOGGLE,
            Feature.PROFILE_GUARDED_ROWS,
            Feature.ADD_FUNDS_IN_APP,
        ),
        exchangeMessageTabs = listOf("NSE", "BSE", "MCX"),
        notApplicableCases = mapOf(
            "wl_search_commodity" to "iTrade's search has no Commodity category (All, F&O, Currency, Stocks); MCX is searched under All",
            "ow_gtt_pad" to "iTrade's Orders has no GTT tab (switched off in the app), so no GTT order can be created",
            "ow_gtt_create" to "iTrade's Orders has no GTT tab (switched off in the app), so no GTT order can be created",
            "ow_gtt_modify" to "iTrade's Orders has no GTT tab (switched off in the app), so no GTT order can be created",
            "ow_gtt_cancel" to "iTrade's Orders has no GTT tab (switched off in the app), so no GTT order can be created",
            "wl_more_fundamentals" to "iTrade's More Details has no Fundamentals tab (Market Depth, Technicals, Info)",
            "db_research_calls" to "nothing on iTrade's Dashboard opens Research Calls / Trading Ideas",
            "db_tools_view_all" to "iTrade's Dashboard has no Scalper / Options Trading Tools",
            "db_trading_customize" to "iTrade's Customize button is switched off in the app",
            "db_trading_customize_edit" to "iTrade's Customize button is switched off in the app",
            "db_investments_customize" to "iTrade's Customize button is switched off in the app",
            "db_investments_customize_edit" to "iTrade's Customize button is switched off in the app",
            "db_alerts_list" to "iTrade has no Alerts tile on the Investments tab (Alerts is covered by WL-07 / WL-08)",
            "db_new_alert_form" to "iTrade has no Alerts tile on the Investments tab (the New Alert page is covered by WL-07 / WL-08)",
            "db_ipo_tabs" to "nothing on iTrade's Dashboard opens IPO",
            "db_ipo_details" to "nothing on iTrade's Dashboard opens IPO",
            "db_ipo_apply_state" to "nothing on iTrade's Dashboard opens IPO",
            "db_mutual_funds" to "iTrade's Quick Tools has no Mutual Funds tile (ETF only)",
            "db_reports" to "iTrade's Quick Tools has no Reports tile (ETF only)",
            "db_pledge" to "iTrade's Quick Tools has no Pledge tile (ETF only)",
            "db_other_request" to "iTrade's Quick Tools has no Other Request tile (ETF only)",
            "db_back_office" to "iTrade's Quick Tools has no Back Office tile (ETF only)",
            "db_funds_summary" to "iTrade's Funds card has no Available Margin / Opening Balance summary (Funds is covered by PF-04)",
            "db_add_funds" to "iTrade's Add Now opens the payment page in the browser; not tapped",
            "db_withdraw_funds" to "iTrade's Funds card has no Available Margin entry to Funds (Withdraw is covered by PF-04)",
        ),
        profileFooterLines = listOf("SEBI Registration", "App Version"),
        // Seen in run f00f716f: the ticker reads "NIFTY" / "SENSEX"; Quick Tools is headed "Invest" and Customize is
        // switched off; the sector cards show "-14.17" under "3M"; the sector page lists BSE rows ("INFY", "BSE").
        tickerIndex = "NIFTY",
        investmentsSections = listOf("Indian Indices", "Invest", "Funds", "Holdings", "Scanners", "Top Sectors"),
        sectorFiguresInPercent = false,
        sectorRowsWithSeries = false,
    );

    fun lacks(feature: String): Boolean = feature in absent

    /** Parts of the Tradion flow a sibling build may not have. */
    object Feature {
        /** The Guest User link and form on the User ID page. */
        const val GUEST_USER = "guest_user"
        /** Unblock Account opening its form while the User ID field is still empty. */
        const val UNBLOCK_WITHOUT_USER_ID = "unblock_without_user_id"
        /** The GTT tab of Orders. */
        const val ORDERS_GTT_TAB = "orders_gtt_tab"
        /** The SIP tab of Orders. */
        const val ORDERS_SIP_TAB = "orders_sip_tab"
        /** The order pad's BO / CO tab (cover and bracket orders). */
        const val BO_CO_TAB = "bo_co_tab"
        /** The order pad's Market Protection switch on Market orders. */
        const val MARKET_PROTECTION = "market_protection"
        /** A new GTT opening on "Greater than" (without it, the default is "Less than"). */
        const val GTT_DEFAULT_GREATER = "gtt_default_greater"
        /** Search's Commodity category (without it, MCX instruments are searched under All). */
        const val SEARCH_COMMODITY_TAB = "search_commodity_tab"
        /** The Technicals tab's EMA / SMA moving-average summary and toggle. */
        const val TECHNICALS_MOVING_AVERAGES = "technicals_moving_averages"
        /** Profile › Account's Switch Account row and its sheet. */
        const val PROFILE_SWITCH_ACCOUNT = "profile_switch_account"
        /** Profile › Account's Rate Our App row. */
        const val PROFILE_RATE_APP = "profile_rate_app"
        /** Profile › Trade Settings' Order notification switch. */
        const val PROFILE_NOTIFICATION_TOGGLE = "profile_notification_toggle"
        /** Profile › Trade Settings' Order preview switch. */
        const val PROFILE_ORDER_PREVIEW_TOGGLE = "profile_order_preview_toggle"
        /** Profile › Trade Settings' TOTP, Freeze & UnFreeze and Developer Console rows. */
        const val PROFILE_GUARDED_ROWS = "profile_guarded_rows"
        /** Add Funds opening a screen inside the app (without it, the button opens the browser and is not tapped). */
        const val ADD_FUNDS_IN_APP = "add_funds_in_app"
    }

    /** The credential the app asks for besides the user id and the OTP. */
    enum class Secret { MPIN, PASSWORD }

    /** Which test flow drives an app. */
    enum class Flow {
        /** [LoginAutomationFlow]: Ashika's login, watchlist and tab load checks. */
        ASHIKA,

        /** [TradionAutomationFlow]: the full regression pass of Tradion and its sibling builds. */
        TRADION,
    }

    companion object {
        fun forPackage(packageName: String): TargetApp? =
            values().firstOrNull { it.packageName == packageName }
    }
}

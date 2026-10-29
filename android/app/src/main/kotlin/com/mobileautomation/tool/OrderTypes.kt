package com.mobileautomation.tool

/**
 * The kinds of order an app's order pad offers, for the report's "Order types" section: what the project has, and what
 * the run did with each. Which of them an app lacks comes from [TargetApp.absent] (iTrade has no BO / CO tab and no
 * GTT), so the catalogue cannot drift from what the Order placement module itself skips.
 *
 * The run's attempts ([attempt]) go into [AutomationRunResult.orderAttempts]; a type's attempts are those whose kind or
 * product is its [Type.id]. GTT and Basket are not placed from the pad: their outcome is that of their cases.
 */
object OrderTypes {

    class Type(
        /** A kind of [OrderPlan.KINDS], a product, or `gtt` / `basket`. */
        val id: String,
        /** Order type, Variety, Product, Validity, Advanced or Orders screen. */
        val group: String,
        val label: String,
        val meaning: String,
        val exchanges: List<String> = OrderPlan.KEYS.map { it.uppercase() },
        /** The [TargetApp.Feature] without which the app does not offer it. */
        val feature: String? = null,
        /** The cases that test it, when it is not placed from the pad. */
        val stepIds: List<String> = emptyList(),
    )

    private val EQUITY = listOf("NSE", "BSE")

    val ALL: List<Type> = listOf(
        Type("market", "Order type", "Market", "Executes at the best price available"),
        Type("limit", "Order type", "Limit", "Executes at the price set, or better"),
        Type("sl", "Order type", "SL (stop-loss limit)", "A Limit order sent once the trigger price is reached"),
        Type("slm", "Order type", "SL-M (stop-loss market)", "A Market order sent once the trigger price is reached"),
        Type("amo", "Variety", "AMO (after market order)", "Placed outside market hours for the next session"),
        Type("co", "Variety", "CO (cover order)", "Intraday order with a compulsory stop-loss", feature = TargetApp.Feature.BO_CO_TAB),
        Type("bo", "Variety", "BO (bracket order)", "Intraday order with a target and a stop-loss", feature = TargetApp.Feature.BO_CO_TAB),
        Type("mis", "Product", "MIS (intraday)", "Squared off the same day; the product of every order type above"),
        Type("nrml", "Product", "NRML (normal)", "Carried forward; the only other product on NFO, BFO and MCX"),
        Type("cnc", "Product", "CNC (delivery)", "Equity bought for delivery", exchanges = EQUITY),
        Type("mtf", "Product", "MTF (margin trading)", "Equity bought with margin funding", exchanges = EQUITY),
        Type("ioc", "Validity", "IOC (immediate or cancel)", "Cancelled at once if not filled; DAY is the default validity"),
        Type("disclosed", "Advanced", "Disclosed quantity", "Only part of the quantity is shown to the market", exchanges = listOf("NSE", "BSE", "MCX")),
        Type(
            "gtt", "Orders screen", "GTT (good till triggered)", "Placed by the exchange when a trigger price is reached",
            exchanges = listOf("NSE"), feature = TargetApp.Feature.ORDERS_GTT_TAB, stepIds = WatchlistSuite.GTT_IDS,
        ),
        Type(
            "basket", "Orders screen", "Basket", "Several orders saved together and executed at once",
            exchanges = listOf("NSE"), stepIds = WatchlistSuite.BASKET_IDS,
        ),
    )

    /** Whether [app] offers [type]. */
    fun offers(app: TargetApp, type: Type): Boolean = type.feature == null || !app.lacks(type.feature)

    /** [app]'s catalogue for the report; null for a flow that places no order (Ashika). */
    fun catalog(app: TargetApp, moduleRun: Boolean): Map<String, Any?>? {
        if (app.flow != TargetApp.Flow.TRADION) return null
        return mapOf(
            "app" to app.displayName,
            "moduleRun" to moduleRun,
            "types" to ALL.map { t ->
                val offered = offers(app, t)
                mapOf(
                    "id" to t.id,
                    "group" to t.group,
                    "label" to t.label,
                    "meaning" to t.meaning,
                    "exchanges" to t.exchanges,
                    "offered" to offered,
                    "why" to if (offered) null else "${app.displayName} has no ${featureName(t.feature)}",
                    "stepIds" to t.stepIds,
                )
            },
        )
    }

    private fun featureName(feature: String?): String = when (feature) {
        TargetApp.Feature.BO_CO_TAB -> "BO / CO tab on its order pad"
        TargetApp.Feature.ORDERS_GTT_TAB -> "GTT tab under Orders"
        else -> feature.orEmpty()
    }

    /**
     * One order the module tried to place. [outcome]: `refused` (sent, and the exchange or the app refused it, which is
     * what the test wants), `accepted` (sent and NOT refused), `failed` (the pad could not be set up or stopped it),
     * `skipped` (not sent: the margin gate, the market hours, or earlier failures), `off` (switched off in the Order
     * settings), `not_applicable` (the app does not offer it) or `not_offered` (the app has it, but not for this
     * account, e.g. MTF without margin-trading activation).
     */
    fun attempt(exchange: String, side: String, kind: String, product: String, outcome: String, detail: String): Map<String, Any?> =
        mapOf(
            "exchange" to exchange,
            "side" to side,
            "kind" to kind,
            "product" to product,
            "outcome" to outcome,
            "detail" to Redactor.apply(detail),
        )
}

package com.mobileautomation.tool

import com.mobileautomation.tool.AutomationConfig.Timeouts
import com.mobileautomation.tool.AutomationConfig.TradionIds as T
import kotlin.math.abs

/**
 * Tradion's order pad, read and driven through the identifiers the app publishes on it (see
 * `docs/AUTOMATION_SEMANTICS.md` in tradion_mobile): a tag wraps each input box (label and field), each switch, chip and
 * footer control, and the three tabs. A validation message is a red snackbar that shows for three seconds after BUY /
 * SELL is tapped (the boxes themselves only get a red border).
 *
 * This class only reads and acts; it decides nothing. What may be tapped, and when an order may be sent at all, is
 * decided by the order cases in [WatchlistSuite] (see [Gate]).
 */
class OrderPad(private val engine: AutomationEngine) {

    companion object {
        val TABS = listOf("Regular", "BO / CO", "AMO")
        val EQUITY_PRODUCTS = listOf("mis", "nrml", "cnc", "mtf")
        val DERIVATIVE_PRODUCTS = listOf("mis", "nrml")

        private val NUMBER = Regex("""-?[\d,]*\.?\d+""")
        private val TICK = Regex("""Tick\s*Size\s*:\s*([\d.]+)""", RegexOption.IGNORE_CASE)
        private val LOT = Regex("""Lot\s*Size\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val MARKET = Regex("""\bMarket\b""", RegexOption.IGNORE_CASE)

        fun tabId(name: String) = when (name) {
            "Regular" -> T.ORDER_TAB_REGULAR
            "BO / CO" -> T.ORDER_TAB_BO_CO
            else -> T.ORDER_TAB_AMO
        }

        fun formId(name: String) = when (name) {
            "Regular" -> T.ORDER_FORM_REGULAR
            "BO / CO" -> T.ORDER_FORM_BO_CO
            else -> T.ORDER_FORM_AMO
        }
    }

    /**
     * Whether an order may be sent from the pad as it stands. The pad itself says so: the funds strip reads
     * `Avail. Margin 0.00` and `Margin req.` is above it, so the exchange side rejects the order for margin. This is
     * the whole safety of placing an order on a live account: with anything else on the strip no order is sent.
     */
    class Gate(val ok: Boolean, val why: String, val available: Double?, val required: Double?)

    // ── Reading ────────────────────────────────────────────────────────────────────────────────

    fun isOpen(): Boolean = engine.isPresent(T.ORDER_WINDOW_SCREEN)

    /** Everything printed on the pad right now, on one line (for the few values that carry no tag: lot and tick size). */
    fun text(): String = engine.onScreenLines().joinToString(" ")

    private fun number(id: String): Double? =
        engine.labelsOf(id).firstNotNullOfOrNull { l -> NUMBER.find(l)?.value?.replace(",", "")?.toDoubleOrNull() }

    fun available(): Double? = number(T.ORDER_MARGIN_AVAILABLE)

    fun required(): Double? = number(T.ORDER_MARGIN_REQUIRED)

    fun shortfallShown(): Boolean = engine.isPresent(T.ORDER_FUNDS_BANNER)

    /** The last price shown for [exchange] in the header. */
    fun ltp(exchange: String): Double? = number("${T.ORDER_LTP_PREFIX}${exchange.lowercase()}")

    fun tickSize(): Double? = TICK.find(text())?.groupValues?.get(1)?.toDoubleOrNull()

    fun lotSize(): Int? = LOT.find(text())?.groupValues?.get(1)?.toIntOrNull()

    /** Waits until the funds strip holds numbers (it shows `--` while the margin loads and again after each change). */
    fun waitForFunds(timeoutMs: Long): Boolean =
        engine.waitUntil(timeoutMs) { available() != null && required() != null }

    fun gate(): Gate {
        val avail = available()
        val req = required()
        return when {
            avail == null || req == null ->
                Gate(false, "the funds strip is not readable (Avail. Margin ${avail ?: "--"}, Margin req. ${req ?: "--"})", avail, req)
            abs(avail) >= 0.005 ->
                Gate(false, "Avail. Margin is ${"%.2f".format(avail)}, not 0.00", avail, req)
            req <= 0.0 ->
                Gate(false, "Avail. Margin is 0.00 but the pad needs no margin for this order (Margin req. ${"%.2f".format(req)}), so it would not be refused", avail, req)
            else -> Gate(true, "Avail. Margin 0.00, Margin req. ${"%.2f".format(req)}", avail, req)
        }
    }

    /**
     * [gate] once the margin has been worked out for the pad's current state. After a change (the product, the order
     * type) the strip keeps the old numbers, greyed, for a moment: a SELL read straight after MIS was chosen still said
     * `Margin req. 0.00` from the CNC state. So a gate that is not met is read again for up to [timeoutMs]; the answer is
     * the same test, only given time.
     */
    fun settledGate(timeoutMs: Long): Gate {
        var g = gate()
        val end = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (!g.ok && android.os.SystemClock.elapsedRealtime() < end) {
            if (!engine.sleep(600)) break
            g = gate()
        }
        return g
    }

    /** Whether a piece of printed text (not a tagged control) is on the pad. */
    fun hasLabel(part: String): Boolean = engine.hasTextContaining(part)

    fun has(id: String): Boolean = engine.isPresent(id)

    fun hasTab(name: String): Boolean = engine.isPresent(tabId(name))

    fun hasProduct(type: String): Boolean = engine.isPresent("${T.ORDER_PRODUCT_PREFIX}$type")

    fun productSelected(type: String): Boolean = engine.isSelected("${T.ORDER_PRODUCT_PREFIX}$type")

    fun hasField(id: String): Boolean = engine.hasFieldOf(id)

    fun value(id: String): String? = engine.fieldValueOf(id)

    /**
     * The Price box shows Market when it has no input field (a Market price is a disabled box, which publishes no editable
     * node) while Stoploss is off (with Stoploss on, the main Price box is disabled for the other reason).
     */
    fun priceIsMarket(): Boolean =
        engine.isPresent(T.ORDER_FIELD_PRICE) && !engine.hasFieldOf(T.ORDER_FIELD_PRICE) &&
            engine.switchStateOfId(T.ORDER_SWITCH_STOPLOSS) != true

    /** Whether the switch tagged [id] is on; null when it is not exposed. Scrolls to it first when it is below the fold. */
    fun switchState(id: String): Boolean? = engine.switchStateOfId(id) ?: run {
        reveal(id)
        engine.switchStateOfId(id)
    }

    /**
     * Whether [id] is on the pad. A control below the fold is not in the accessibility tree at all (Flutter drops what is
     * clipped away), so an absent one is looked for by paging down before it is called missing.
     */
    fun present(id: String): Boolean = engine.isPresent(id) || (reveal(id) && engine.isPresent(id))

    /** The text of the red validation snackbar, or null while none is showing. */
    fun message(): String? = engine.labelsOf(T.ERROR_SNACKBAR_MESSAGE).firstOrNull { it.isNotBlank() }?.trim()

    /** Whether the submit button reads BUY ORDER (or SELL ORDER). */
    fun submitIs(buy: Boolean): Boolean =
        engine.labelsOf(T.ORDER_SUBMIT_BUTTON).any { it.contains(if (buy) "BUY" else "SELL", ignoreCase = true) }

    // ── Driving ────────────────────────────────────────────────────────────────────────────────

    /**
     * Opens a tab and waits for its own form to be built and the other two to be gone. The pages slide: for a moment the
     * old form and the new one (and, from Regular to AMO, the BO / CO one between them) are all in the tree, and a box
     * read then can belong to the page that is leaving.
     */
    fun openTab(name: String): Boolean {
        if (!engine.tap(tabId(name)).ok) return false
        val target = formId(name)
        val others = TABS.filter { it != name }.map(::formId)
        engine.waitUntil(Timeouts.UI_RESPONSE) { engine.isPresent(target) && others.none { engine.isPresent(it) } }
        if (!engine.isPresent(target)) return false
        engine.sleep(300)
        return true
    }

    /** Brings the box tagged [id] into view (the form scrolls under the footer). */
    fun reveal(id: String): Boolean = engine.scrollIdIntoView(id, maxPages = 5)

    fun setField(id: String, value: String): AutomationEngine.ActionOutcome {
        reveal(id)
        return engine.enterText(id, value)
    }

    fun tap(id: String): Boolean {
        reveal(id)
        return engine.tap(id).ok
    }

    /** A quantity step by the + / - button. */
    fun stepQuantity(plus: Boolean): Boolean = engine.tap(if (plus) T.ORDER_QUANTITY_PLUS else T.ORDER_QUANTITY_MINUS).ok

    /** The Market / Limit switch inside the Price box. */
    fun toggleMarketLimit(): Boolean = engine.tap(T.ORDER_PRICE_TYPE_TOGGLE).ok

    /** Taps BUY ORDER / SELL ORDER. The only call in the tool that can send an order: see [Gate]. */
    fun tapOrderButton(): Boolean = engine.tap(T.ORDER_SUBMIT_BUTTON).ok
}

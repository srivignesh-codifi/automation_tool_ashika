package com.mobileautomation.tool

/**
 * The last guard before anything leaves the tool (events to the UI, the saved report): the run's User ID / Client ID
 * is replaced wherever it appears, and evidence text drops lines that look personal. Screens show the ID ("Not
 * <USER ID>?", a dashboard card) and the account holder's name ("Hi, <NAME>") in places no check can foresee.
 */
object Redactor {

    /** Values that must never appear in a report: set by the flow at the start of the run. */
    @Volatile
    var secrets: List<String> = emptyList()

    fun apply(text: String?): String? {
        if (text == null) return null
        var out: String = text
        for (s in secrets) if (s.length >= 3) out = out.replace(s, "[user id]", ignoreCase = true)
        return out
    }

    /** A screen line that may be personal: an email, a greeting with the name, a masked or long number, a PAN. */
    private val personal = Regex("""@|^Hi,|\*{2,}|\d{9,}|\b[A-Z]{5}\d{4}[A-Z]\b""")

    /** [lines] for evidence: personal lines dropped, the ID replaced. */
    fun evidence(lines: List<String>): List<String> =
        lines.map { it.trim() }.filterNot { personal.containsMatchIn(it) }.mapNotNull { apply(it) }
}

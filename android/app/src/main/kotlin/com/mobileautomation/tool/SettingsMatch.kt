package com.mobileautomation.tool

/**
 * Recognises the Settings controls that clear an app's data, by meaning rather
 * than by exact label. Phone makers word them differently ("Clear storage",
 * "Clear data", "Clear Data", "CLEAR ALL DATA", "Storage & cache", "Storage
 * usage"), and a fixed list of exact labels breaks on the first phone that
 * spells one another way.
 *
 * Pure text logic, so it is unit tested without a device.
 */
object SettingsMatch {

    private const val MAX_LABEL = 40

    /** Trimmed, lower case, every run of whitespace (including line breaks) as one space. */
    fun normalise(raw: CharSequence?): String =
        raw?.toString()?.replace(Regex("\\s+"), " ")?.trim()?.lowercase().orEmpty()

    /**
     * The button that wipes the app's data: "Clear data", "Clear storage",
     * "Clear all data". Not "Clear cache" and not "Clear defaults".
     */
    fun isClearData(raw: CharSequence?): Boolean {
        val t = normalise(raw)
        return t.isNotEmpty() && t.length <= MAX_LABEL &&
            t.contains("clear") && (t.contains("data") || t.contains("storage")) &&
            !t.contains("cache")
    }

    /**
     * The App info row that opens the storage page: "Storage", "Storage & cache",
     * "Storage usage". Not a Clear button, and not a long sentence that happens
     * to mention storage.
     */
    fun isStorageEntry(raw: CharSequence?): Boolean {
        val t = normalise(raw)
        return t.isNotEmpty() && t.length <= MAX_LABEL &&
            t.contains("storage") && !t.contains("clear")
    }

    /**
     * The overflow ("three dots") button of a screen, for phones that keep Clear
     * data in a menu. Its only label is usually a content description.
     */
    fun isMoreOptions(raw: CharSequence?): Boolean {
        val t = normalise(raw)
        return t in setOf("more", "more options", "more actions", "more menu", "overflow menu", "menu", "options") ||
            t.startsWith("more option")
    }

    private val CONFIRM = setOf(
        "delete", "delete all", "delete data", "delete all data", "ok", "clear", "clear data",
        "clear all data", "clear storage", "yes", "confirm",
    )

    /** The button that confirms the "delete all app data?" question. */
    fun isConfirm(raw: CharSequence?): Boolean = normalise(raw) in CONFIRM
}

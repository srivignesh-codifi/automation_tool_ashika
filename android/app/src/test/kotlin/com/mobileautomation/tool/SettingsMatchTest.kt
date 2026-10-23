package com.mobileautomation.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMatchTest {

    @Test
    fun clearButtonIsRecognisedWhateverItsSpellingOrCase() {
        listOf(
            "Clear storage", "Clear data", "Clear Data", "CLEAR DATA", "Clear all data",
            "Clear  data", "  clear storage ", "Clear\ndata", "Clear app data",
        ).forEach { assertTrue("'$it' is a clear button", SettingsMatch.isClearData(it)) }
    }

    @Test
    fun clearButtonIsNotConfusedWithOtherControls() {
        listOf(
            "Clear cache", "Clear defaults", "Clear Cache", "Storage", "Storage & cache",
            "Force stop", "Uninstall", "", "Open by default",
            "Clear the data of every app you have ever installed on this phone",
        ).forEach { assertFalse("'$it' is not a clear button", SettingsMatch.isClearData(it)) }
        assertFalse(SettingsMatch.isClearData(null))
    }

    @Test
    fun storageRowIsRecognisedButNotAClearButton() {
        listOf("Storage", "Storage & cache", "Storage and cache", "Storage usage", "STORAGE", "Manage storage")
            .forEach { assertTrue("'$it' is a storage row", SettingsMatch.isStorageEntry(it)) }
        listOf("Clear storage", "Clear data", "Battery", "", "Used storage: 24 MB of the 128 GB your phone has in total")
            .forEach { assertFalse("'$it' is not a storage row", SettingsMatch.isStorageEntry(it)) }
    }

    @Test
    fun theTwoNeverOverlap() {
        listOf("Clear storage", "Storage", "Clear Data", "Storage & cache", "Clear cache").forEach {
            assertFalse(
                "'$it' must be one or the other",
                SettingsMatch.isClearData(it) && SettingsMatch.isStorageEntry(it),
            )
        }
    }

    @Test
    fun confirmButtonsAreRecognisedAndCancelIsNot() {
        listOf("Delete", "DELETE", "OK", "Ok", "Clear all data", "Yes", "Confirm", "Clear data")
            .forEach { assertTrue("'$it' confirms", SettingsMatch.isConfirm(it)) }
        listOf("Cancel", "No", "CANCEL", "Clear cache", "", "Delete everything and lose it").forEach {
            assertFalse("'$it' does not confirm", SettingsMatch.isConfirm(it))
        }
    }

    @Test
    fun normaliseCollapsesWhitespaceAndCase() {
        assertEquals("clear data", SettingsMatch.normalise("  Clear\n\tDATA  "))
        assertEquals("", SettingsMatch.normalise(null))
    }

    @Test
    fun overflowButtonIsRecognised() {
        listOf("More options", "more options", "More", "Overflow menu", "Menu", "More actions")
            .forEach { assertTrue("'$it' is an overflow button", SettingsMatch.isMoreOptions(it)) }
        listOf("Clear data", "Storage", "", "Moreover the app is large")
            .forEach { assertFalse("'$it' is not an overflow button", SettingsMatch.isMoreOptions(it)) }
    }
}

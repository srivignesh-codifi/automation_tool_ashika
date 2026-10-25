package com.mobileautomation.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flows look steps up by id with `first { }`, and a missing id kills the whole run with
 * "NoSuchElementException: Collection contains no element matching the predicate". These checks catch a module or a
 * suite that names a step the checklist does not have, without needing a device.
 */
class ChecklistWiringTest {

    private val checklist = TradionAutomationFlow.buildSteps()

    @Test
    fun stepIdsAreUnique() {
        val ids = checklist.map { it.id }
        assertEquals("duplicate step ids: ${ids.groupBy { it }.filter { it.value.size > 1 }.keys}", ids.size, ids.toSet().size)
    }

    @Test
    fun everyModuleStepExistsInTheChecklist() {
        val ids = checklist.map { it.id }.toSet()
        for (module in AppModules.TRADION) {
            val missing = module.stepIds.filter { it !in ids }
            assertTrue("module '${module.id}' names steps the checklist lacks: $missing", missing.isEmpty())
        }
    }

    @Test
    fun everyWatchlistCaseBelongsToAWatchlistModuleAndIsInTheChecklist() {
        val ids = checklist.map { it.id }.toSet()
        val owned = AppModules.TRADION.flatMap { it.stepIds }.toSet()
        for (case in WatchlistSuite.CASES) {
            assertTrue("case ${case.code} (${case.id}) is not in the checklist", case.id in ids)
            assertTrue("case ${case.code} (${case.id}) is not owned by any module", case.id in owned)
        }
        assertEquals("case codes are unique", WatchlistSuite.CASES.size, WatchlistSuite.CASES.map { it.code }.toSet().size)
    }

    @Test
    fun watchlistModulesSkipEverythingButTheStateCheck() {
        val own = WatchlistSuite.CASES.filter { it.id != WatchlistSuite.STATE_ID }.map { it.id }.toSet()
        val skipped = listOf("watchlist", "watchlist_search", "watchlist_edit", "watchlist_settings")
            .flatMap { WatchlistSuite.idsOf(it) }.toSet()
        assertEquals(own, skipped)
    }

    @Test
    fun directLoginSkipsOnlyDetoursThatBelongToTheLoginModule() {
        val login = AppModules.TRADION.first { it.id == "login" }.stepIds.toSet()
        val detours = TradionAutomationFlow.LOGIN_DETOUR_STEPS
        assertTrue("every detour is a Login module step", login.containsAll(detours))
        assertTrue("the checks on the sign-in path stay", "otp_confirm_button_checked" !in detours && "enable_2fa_checked" !in detours)
        assertTrue(detours.all { id -> checklist.any { it.id == id } })
    }
}

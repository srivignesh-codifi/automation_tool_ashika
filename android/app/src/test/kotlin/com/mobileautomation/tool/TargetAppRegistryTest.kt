package com.mobileautomation.tool

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [TargetApp] is the one list of apps; these files must name every package in it too. Each failure says which file to
 * change, so adding an app needs no other guide.
 */
class TargetAppRegistryTest {

    /** Unit tests run from android/app. */
    private fun read(path: String): String = File(path).readText()

    private fun assertNamesEveryApp(path: String, what: String) {
        val text = read(path)
        val missing = TargetApp.values().map { it.packageName }.filter { it !in text }
        assertTrue("$path: add ${missing.joinToString()} to $what", missing.isEmpty())
    }

    @Test
    fun manifestQueriesEveryApp() =
        assertNamesEveryApp("src/main/AndroidManifest.xml", "the <queries> block (<package android:name=\"...\" />)")

    @Test
    fun accessibilityServiceIsScopedToEveryApp() =
        assertNamesEveryApp("src/main/res/xml/accessibility_service_config.xml", "android:packageNames")

    @Test
    fun dartFallbackListsEveryApp() =
        assertNamesEveryApp("../../lib/services/automation_service.dart", "AutomationConfigInfo.fallback() (allowedPackages and targets)")

    @Test
    fun provisioningCoversEveryApp() =
        assertNamesEveryApp("../../scripts/provision_device.sh", "TARGETS=(...)")

    @Test
    fun reportNamesEveryApp() =
        assertNamesEveryApp("../../lib/services/report_service.dart", "_appNames (the short name in report file names)")

    @Test
    fun theDefaultAppIsOneThatIsOffered() {
        assertTrue("OFFERED_APPS is empty", AutomationConfig.OFFERED_APPS.isNotEmpty())
        assertTrue(
            "DEFAULT_TARGET_PACKAGE is not offered",
            AutomationConfig.OFFERED_APPS.any { it.packageName == AutomationConfig.DEFAULT_TARGET_PACKAGE },
        )
    }

    @Test
    fun idsAndPackagesAreUnique() {
        val apps = TargetApp.values()
        assertTrue("duplicate app ids", apps.map { it.id }.toSet().size == apps.size)
        assertTrue("duplicate packages", apps.map { it.packageName }.toSet().size == apps.size)
    }

    @Test
    fun absentModulesAreModulesOfTheFlow() {
        for (app in TargetApp.values()) {
            val known = when (app.flow) {
                TargetApp.Flow.ASHIKA -> AppModules.ASHIKA
                TargetApp.Flow.TRADION -> AppModules.TRADION
            }.map { it.id }.toSet()
            val unknown = app.absentModules.filter { it !in known }
            assertTrue("${app.id}: absentModules names unknown modules $unknown (known: $known)", unknown.isEmpty())
        }
    }
}

package com.mobileautomation.tool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The report's Order types section: each app's catalogue follows what the app has, and every placed kind is in it. */
class OrderTypesTest {

    @Suppress("UNCHECKED_CAST")
    private fun types(app: TargetApp): Map<String, Map<String, Any?>> =
        (OrderTypes.catalog(app, moduleRun = true)!!["types"] as List<Map<String, Any?>>).associateBy { it["id"] as String }

    @Test
    fun tradionOffersEveryType() {
        val types = types(TargetApp.TRADION)
        assertEquals(OrderTypes.ALL.map { it.id }, types.keys.toList())
        assertTrue(types.values.all { it["offered"] == true && it["why"] == null })
    }

    @Test
    fun iTradeLacksCoverBracketAndGtt() {
        val types = types(TargetApp.ITRADE)
        for (id in listOf("co", "bo", "gtt")) {
            assertFalse("$id should not be offered on iTrade", types.getValue(id)["offered"] as Boolean)
            assertTrue((types.getValue(id)["why"] as String).startsWith("iTrade has no"))
        }
        assertTrue(types.getValue("market")["offered"] as Boolean)
        assertTrue(types.getValue("basket")["offered"] as Boolean)
    }

    @Test
    fun ashikaPlacesNoOrderSoHasNoCatalogue() {
        assertNull(OrderTypes.catalog(TargetApp.ASHIKA, moduleRun = true))
    }

    @Test
    fun everyKindTheModuleCanPlaceIsInTheCatalogue() {
        val ids = OrderTypes.ALL.map { it.id }.toSet()
        val missing = OrderPlan.KINDS.filter { it !in ids }
        assertTrue("kinds of order with no line in the report: $missing", missing.isEmpty())
        assertTrue("the product of every order type", "mis" in ids)
    }

    @Test
    fun gttAndBasketPointAtCasesInTheChecklist() {
        val checklist = TradionAutomationFlow.buildSteps().map { it.id }.toSet()
        for (type in OrderTypes.ALL.filter { it.stepIds.isNotEmpty() }) {
            val missing = type.stepIds.filter { it !in checklist }
            assertTrue("${type.id} names steps the checklist lacks: $missing", missing.isEmpty())
        }
    }
}

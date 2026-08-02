package com.vasilisneo.trackstar.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPlanTest {

    @Test
    fun `tiers are ordered free bronze silver gold`() {
        assertTrue(AppPlan.FREE.ordinal < AppPlan.BRONZE.ordinal)
        assertTrue(AppPlan.BRONZE.ordinal < AppPlan.SILVER.ordinal)
        assertTrue(AppPlan.SILVER.ordinal < AppPlan.GOLD.ordinal)
    }

    @Test
    fun `atLeast is true for same tier`() {
        assertTrue(AppPlan.SILVER.atLeast(AppPlan.SILVER))
    }

    @Test
    fun `atLeast is true for a higher tier`() {
        assertTrue(AppPlan.GOLD.atLeast(AppPlan.SILVER))
        assertTrue(AppPlan.SILVER.atLeast(AppPlan.BRONZE))
    }

    @Test
    fun `atLeast is false for a lower tier`() {
        assertFalse(AppPlan.BRONZE.atLeast(AppPlan.GOLD))
        assertFalse(AppPlan.FREE.atLeast(AppPlan.BRONZE))
    }

    @Test
    fun `fromEntitlements picks the highest active entitlement`() {
        // Gold wins even when lower tiers are also present.
        assertEquals(AppPlan.GOLD, AppPlan.fromEntitlements(setOf("bronze", "silver", "gold")))
        assertEquals(AppPlan.SILVER, AppPlan.fromEntitlements(setOf("bronze", "silver")))
        assertEquals(AppPlan.BRONZE, AppPlan.fromEntitlements(setOf("bronze")))
    }

    @Test
    fun `fromEntitlements is free when nothing is active`() {
        assertEquals(AppPlan.FREE, AppPlan.fromEntitlements(emptySet()))
    }

    @Test
    fun `fromEntitlements ignores unknown entitlements`() {
        assertEquals(AppPlan.FREE, AppPlan.fromEntitlements(setOf("platinum", "diamond")))
    }

    @Test
    fun `entitlement ids match the backend webhook mapping`() {
        assertEquals(null, AppPlan.FREE.entitlementId)
        assertEquals("bronze", AppPlan.BRONZE.entitlementId)
        assertEquals("silver", AppPlan.SILVER.entitlementId)
        assertEquals("gold", AppPlan.GOLD.entitlementId)
    }
}

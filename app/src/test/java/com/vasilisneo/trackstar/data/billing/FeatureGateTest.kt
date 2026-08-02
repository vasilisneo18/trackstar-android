package com.vasilisneo.trackstar.data.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGateTest {

    @Test
    fun `canUseAI is silver and gold only`() {
        assertFalse(FeatureGate.canUseAI(AppPlan.FREE))
        assertFalse(FeatureGate.canUseAI(AppPlan.BRONZE))
        assertTrue(FeatureGate.canUseAI(AppPlan.SILVER))
        assertTrue(FeatureGate.canUseAI(AppPlan.GOLD))
    }

    @Test
    fun `canCoach is gold only`() {
        assertFalse(FeatureGate.canCoach(AppPlan.FREE))
        assertFalse(FeatureGate.canCoach(AppPlan.BRONZE))
        assertFalse(FeatureGate.canCoach(AppPlan.SILVER))
        assertTrue(FeatureGate.canCoach(AppPlan.GOLD))
    }

    @Test
    fun `unlimited logging and full history require any paid tier`() {
        for (plan in listOf(AppPlan.BRONZE, AppPlan.SILVER, AppPlan.GOLD)) {
            assertTrue(FeatureGate.hasUnlimitedLogging(plan))
            assertTrue(FeatureGate.hasFullHistory(plan))
        }
        assertFalse(FeatureGate.hasUnlimitedLogging(AppPlan.FREE))
        assertFalse(FeatureGate.hasFullHistory(AppPlan.FREE))
    }

    @Test
    fun `template limit is 20 for gold and 0 otherwise`() {
        assertEquals(FeatureGate.GOLD_TEMPLATE_LIMIT, FeatureGate.templateLimit(AppPlan.GOLD))
        assertEquals(0, FeatureGate.templateLimit(AppPlan.SILVER))
        assertEquals(0, FeatureGate.templateLimit(AppPlan.BRONZE))
        assertEquals(0, FeatureGate.templateLimit(AppPlan.FREE))
    }

    @Test
    fun `free user can start sessions below the weekly cap`() {
        assertTrue(FeatureGate.canStartSession(AppPlan.FREE, sessionsThisWeek = 0))
        assertTrue(FeatureGate.canStartSession(AppPlan.FREE, sessionsThisWeek = FeatureGate.WEEKLY_SESSION_LIMIT - 1))
    }

    @Test
    fun `free user is blocked at and above the weekly cap`() {
        assertFalse(FeatureGate.canStartSession(AppPlan.FREE, sessionsThisWeek = FeatureGate.WEEKLY_SESSION_LIMIT))
        assertFalse(FeatureGate.canStartSession(AppPlan.FREE, sessionsThisWeek = FeatureGate.WEEKLY_SESSION_LIMIT + 5))
    }

    @Test
    fun `paid users are never blocked regardless of count`() {
        assertTrue(FeatureGate.canStartSession(AppPlan.BRONZE, sessionsThisWeek = 999))
        assertTrue(FeatureGate.canStartSession(AppPlan.GOLD, sessionsThisWeek = 999))
    }

    @Test
    fun `weekly sessions remaining counts down for free and is unbounded for paid`() {
        assertEquals(FeatureGate.WEEKLY_SESSION_LIMIT, FeatureGate.weeklySessionsRemaining(AppPlan.FREE, 0))
        assertEquals(1, FeatureGate.weeklySessionsRemaining(AppPlan.FREE, FeatureGate.WEEKLY_SESSION_LIMIT - 1))
        // Never negative once over the cap.
        assertEquals(0, FeatureGate.weeklySessionsRemaining(AppPlan.FREE, FeatureGate.WEEKLY_SESSION_LIMIT + 3))
        assertEquals(Int.MAX_VALUE, FeatureGate.weeklySessionsRemaining(AppPlan.GOLD, 10))
    }
}

package com.vasilisneo.trackstar.data.billing

// Mirrors iOS's AppPlan / BillingPeriod. The subscription tiers, in ascending order, plus the two
// billing cadences. `entitlementId` matches the RevenueCat dashboard entitlement (and the backend
// webhook's mapping) exactly — do not rename without updating both.
enum class AppPlan(val entitlementId: String?) {
    FREE(null),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold");

    companion object {
        // Highest active entitlement wins, matching the backend webhook (gold > silver > bronze).
        fun fromEntitlements(active: Set<String>): AppPlan = when {
            active.contains(GOLD.entitlementId) -> GOLD
            active.contains(SILVER.entitlementId) -> SILVER
            active.contains(BRONZE.entitlementId) -> BRONZE
            else -> FREE
        }
    }
}

enum class BillingPeriod { MONTHLY, ANNUAL }

// Live, localized prices for one plan, pulled from the RevenueCat offering (so the screen shows
// real store prices instead of the hardcoded EUR fallbacks). Any field may be null if the matching
// package isn't in the offering yet.
data class PlanPricing(
    val monthlyPrice: String? = null,
    val annualPrice: String? = null,
    val annualMonthlyEquivalent: String? = null,
)

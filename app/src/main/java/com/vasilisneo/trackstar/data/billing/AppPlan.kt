package com.vasilisneo.trackstar.data.billing

// Mirrors iOS's AppPlan / BillingPeriod. The subscription tiers, in ascending order, plus the two
// billing cadences. `entitlementId` matches the RevenueCat dashboard entitlement (and the backend
// webhook's mapping) exactly — do not rename without updating both.
//
// Monetization refactor (W8): the target model is FREE / GOLD (Athlete Gold, €4.99) / COACH (sold
// on the web; implies gold). COACH is a strict superset of GOLD, so the ordinal ladder still holds
// (COACH ≥ GOLD ≥ …). BRONZE/SILVER are the archived legacy metal tiers — kept here for now so the
// old SubscriptionScreen / themes / icons still compile; they'll be removed when that UI is reduced
// to Free vs Athlete Gold.
enum class AppPlan(val entitlementId: String?) {
    FREE(null),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold"),
    COACH("coach");

    // Tiers are declared in ascending order, so `ordinal` is the tier rank — a plan "is at least"
    // another when its rank is >=. Used for tier-threshold gates (e.g. premium theme unlocks).
    fun atLeast(other: AppPlan): Boolean = ordinal >= other.ordinal

    companion object {
        // Highest active entitlement wins, matching the backend webhook (coach > gold > silver > bronze).
        fun fromEntitlements(active: Set<String>): AppPlan = when {
            active.contains(COACH.entitlementId) -> COACH
            active.contains(GOLD.entitlementId) -> GOLD
            active.contains(SILVER.entitlementId) -> SILVER
            active.contains(BRONZE.entitlementId) -> BRONZE
            else -> FREE
        }
    }
}

enum class BillingPeriod { MONTHLY, ANNUAL }

// Live, localized prices for one plan, pulled from the RevenueCat offering (so the screen shows
// real store prices in the buyer's own currency instead of the hardcoded EUR fallbacks). Any field
// may be null if the matching package isn't in the offering yet. `annualMonthlyEquivalent` (the
// annual price ÷ 12) and `savings` ("Save NN%") are computed in BillingManager from the raw store
// amounts so they stay consistent with — and in the same currency as — whatever the store returns.
data class PlanPricing(
    val monthlyPrice: String? = null,
    val annualPrice: String? = null,
    val annualMonthlyEquivalent: String? = null,
    val savings: String? = null,
    // Free-trial length in days when the product offers one (else 0). Drives whether the UI
    // advertises a trial, so we never claim a trial the store won't actually grant.
    val trialDays: Int = 0,
)

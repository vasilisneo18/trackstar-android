package com.vasilisneo.trackstar.data.billing

// Mirrors iOS's AppPlan / BillingPeriod. The subscription tiers, in ascending order, plus the two
// billing cadences. `entitlementId` matches the RevenueCat dashboard entitlement (and the backend
// webhook's mapping) exactly — do not rename without updating both.
enum class AppPlan(val entitlementId: String?) {
    FREE(null),
    BRONZE("bronze"),
    SILVER("silver"),
    GOLD("gold");

    // Tiers are declared in ascending order, so `ordinal` is the tier rank — a plan "is at least"
    // another when its rank is >=. Used for tier-threshold gates (e.g. premium theme unlocks).
    fun atLeast(other: AppPlan): Boolean = ordinal >= other.ordinal

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

// Where the active subscription was actually purchased. RevenueCat unifies entitlements across
// platforms, so an Apple-bought plan still shows as active on Android — but it can only be *managed*
// in the store it was bought from. Drives the "Manage Subscription" routing (Play page vs. an
// instruction to manage on Apple vs. nothing for a comped grant).
enum class SubStore { PLAY, APP_STORE, PROMOTIONAL, OTHER }

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
)

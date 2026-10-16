package com.vasilisneo.trackstar.data.billing

// Central subscription feature gates, mirroring iOS's FeatureGate (Trackstar/Store/FeatureGate.swift).
//
// Functions take the plan explicitly so Compose call sites can drive them off a collected
// BillingManager.currentPlan (recomposes when the plan changes), while non-Compose callers
// (view models) pass BillingManager.currentPlan.value via the `currentPlan` convenience.
//
// What is deliberately NOT here: the AI *monthly usage* limit (6/mo per type). That is enforced
// by the backend (GET /api/ai/usage, surfaced through the AI planner view models' limitReached),
// so it is not duplicated client-side. FeatureGate only decides *tier access* — which plan may
// open the AI planners at all — plus the local template cap.
object FeatureGate {

    const val GOLD_TEMPLATE_LIMIT = 20

    // AI diet & workout planners: Silver and Gold only (iOS FeatureGate.canUseAI).
    fun canUseAI(plan: AppPlan): Boolean = plan == AppPlan.SILVER || plan == AppPlan.GOLD

    // Coaching (MyTeam roster, templates, apply-to-athlete): Gold only (iOS FeatureGate.canCoach).
    fun canCoach(plan: AppPlan): Boolean = plan == AppPlan.GOLD

    // Any paid tier removes the free weekly logging cap (iOS FeatureGate.hasUnlimitedLogging).
    fun hasUnlimitedLogging(plan: AppPlan): Boolean = plan != AppPlan.FREE

    // Any paid tier unlocks full stats/history analytics (iOS FeatureGate.hasFullHistory).
    fun hasFullHistory(plan: AppPlan): Boolean = plan != AppPlan.FREE

    // How many plan templates a coach may keep (iOS FeatureGate.templateLimit).
    fun templateLimit(plan: AppPlan): Int = if (plan == AppPlan.GOLD) GOLD_TEMPLATE_LIMIT else 0

    // Live current plan, for non-Compose callers (view models). Compose should collect the flow.
    val currentPlan: AppPlan get() = BillingManager.currentPlan.value
}

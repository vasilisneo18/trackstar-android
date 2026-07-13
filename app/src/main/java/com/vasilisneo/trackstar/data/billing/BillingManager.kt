package com.vasilisneo.trackstar.data.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitLogOut
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Android analogue of iOS's RevenueCatManager: the single owner of RevenueCat state. Configured
// once at launch, identified with the backend userId after auth (so purchases attribute to the
// right account and the backend's RC webhook updates the correct user's plan), and queried for the
// current plan + live prices.
//
// Convention (see the setup checklist): each paid tier is a RevenueCat *offering* whose identifier
// equals the entitlement id — "bronze" / "silver" / "gold" — containing a monthly and an annual
// package. currentPlan is derived from active *entitlements*, matching the backend.
//
// Until a RevenueCat Android key is set (BuildConfig.REVENUECAT_API_KEY blank), isConfigured stays
// false and every method is a graceful no-op: currentPlan = FREE, no prices, purchases fail
// cleanly. So the app runs fully before RevenueCat/Play are wired up.
object BillingManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentPlan = MutableStateFlow(AppPlan.FREE)
    val currentPlan: StateFlow<AppPlan> = _currentPlan.asStateFlow()

    private val _pricing = MutableStateFlow<Map<AppPlan, PlanPricing>>(emptyMap())
    val pricing: StateFlow<Map<AppPlan, PlanPricing>> = _pricing.asStateFlow()

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing.asStateFlow()

    var isConfigured: Boolean = false
        private set

    // Call once from Application.onCreate.
    fun configure(context: Context, apiKey: String) {
        if (isConfigured || apiKey.isBlank()) return
        Purchases.logLevel = LogLevel.ERROR
        Purchases.configure(PurchasesConfiguration.Builder(context.applicationContext, apiKey).build())
        isConfigured = true
        scope.launch {
            refresh()
            fetchOfferings()
        }
    }

    // Attribute the RevenueCat customer to the signed-in backend user. Safe to call repeatedly.
    fun logIn(userId: String) {
        if (!isConfigured || userId.isBlank()) return
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitLogIn(userId) }
            refresh()
            fetchOfferings()
        }
    }

    // On sign-out, detach so the next user starts anonymous.
    fun logOut() {
        if (!isConfigured) return
        scope.launch {
            runCatching { Purchases.sharedInstance.awaitLogOut() }
            _currentPlan.value = AppPlan.FREE
        }
    }

    private suspend fun refresh() {
        if (!isConfigured) return
        runCatching { Purchases.sharedInstance.awaitCustomerInfo() }
            .onSuccess { info -> _currentPlan.value = planFrom(info) }
    }

    private suspend fun fetchOfferings() {
        if (!isConfigured) return
        val offerings = runCatching { Purchases.sharedInstance.awaitOfferings() }.getOrNull() ?: return
        val map = mutableMapOf<AppPlan, PlanPricing>()
        for (plan in listOf(AppPlan.BRONZE, AppPlan.SILVER, AppPlan.GOLD)) {
            val offering = offerings.all[plan.entitlementId] ?: continue
            map[plan] = PlanPricing(
                monthlyPrice = offering.monthly?.product?.price?.formatted,
                annualPrice = offering.annual?.product?.price?.formatted,
            )
        }
        _pricing.value = map
    }

    // Launches the Play purchase sheet for the given tier/cadence. Returns:
    //   Result.success(true)  — purchased,
    //   Result.success(false) — user cancelled,
    //   Result.failure(...)   — not configured, package missing, or a store error.
    suspend fun purchase(activity: Activity, plan: AppPlan, billing: BillingPeriod): Result<Boolean> {
        if (!isConfigured) return Result.failure(IllegalStateException("Subscriptions aren't available yet."))
        val offering = offeringFor(plan) ?: return Result.failure(IllegalStateException("This plan isn't available right now."))
        val pkg: Package = (if (billing == BillingPeriod.ANNUAL) offering.annual else offering.monthly)
            ?: return Result.failure(IllegalStateException("This plan isn't available right now."))

        _isPurchasing.value = true
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            _currentPlan.value = planFrom(result.customerInfo)
            Result.success(true)
        } catch (e: PurchasesException) {
            if (e.code == PurchasesErrorCode.PurchaseCancelledError) Result.success(false)
            else Result.failure(e)
        } finally {
            _isPurchasing.value = false
        }
    }

    // Restore entitlements after a reinstall / new device.
    suspend fun restore(): Result<AppPlan> {
        if (!isConfigured) return Result.failure(IllegalStateException("Subscriptions aren't available yet."))
        _isPurchasing.value = true
        return try {
            val info = Purchases.sharedInstance.awaitRestore()
            val plan = planFrom(info)
            _currentPlan.value = plan
            Result.success(plan)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isPurchasing.value = false
        }
    }

    private suspend fun offeringFor(plan: AppPlan): Offering? {
        val offerings = runCatching { Purchases.sharedInstance.awaitOfferings() }.getOrNull() ?: return null
        return offerings.all[plan.entitlementId]
    }

    private fun planFrom(info: CustomerInfo): AppPlan = AppPlan.fromEntitlements(info.entitlements.active.keys)
}

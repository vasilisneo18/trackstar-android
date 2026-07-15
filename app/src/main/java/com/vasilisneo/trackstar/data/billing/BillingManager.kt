package com.vasilisneo.trackstar.data.billing

import android.app.Activity
import android.content.Context
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Offerings
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
// Offering layout (shared with iOS): a single offering (the "current" one) whose packages are named
// "{tier}_{monthly|annual}" — bronze_monthly, bronze_annual, silver_monthly, … gold_annual. Each
// package carries both the iOS and the Google Play product; RevenueCat serves the right one here.
// currentPlan is derived from active *entitlements* (bronze/silver/gold), matching the backend.
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
        val offering = currentOffering(offerings) ?: return
        val map = mutableMapOf<AppPlan, PlanPricing>()
        for (plan in listOf(AppPlan.BRONZE, AppPlan.SILVER, AppPlan.GOLD)) {
            val monthly = packageFor(offering, plan, BillingPeriod.MONTHLY)?.product?.price?.formatted
            val annual = packageFor(offering, plan, BillingPeriod.ANNUAL)?.product?.price?.formatted
            if (monthly != null || annual != null) {
                map[plan] = PlanPricing(monthlyPrice = monthly, annualPrice = annual)
            }
        }
        _pricing.value = map
    }

    // Launches the Play purchase sheet for the given tier/cadence. Returns:
    //   Result.success(true)  — purchased,
    //   Result.success(false) — user cancelled,
    //   Result.failure(...)   — not configured, package missing, or a store error.
    suspend fun purchase(activity: Activity, plan: AppPlan, billing: BillingPeriod): Result<Boolean> {
        if (!isConfigured) return Result.failure(IllegalStateException("Subscriptions aren't available yet."))
        val offerings = runCatching { Purchases.sharedInstance.awaitOfferings() }.getOrNull()
        val offering = offerings?.let(::currentOffering)
            ?: return Result.failure(IllegalStateException("This plan isn't available right now."))
        val pkg: Package = packageFor(offering, plan, billing)
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

    // The single shared offering — the "current" one, else the only one in the project.
    private fun currentOffering(offerings: Offerings): Offering? =
        offerings.current ?: offerings.all.values.firstOrNull()

    // Packages are named "{tier}_{monthly|annual}" (bronze_monthly … gold_annual), shared with iOS.
    private fun packageFor(offering: Offering, plan: AppPlan, billing: BillingPeriod): Package? {
        val period = if (billing == BillingPeriod.ANNUAL) "annual" else "monthly"
        val id = "${plan.name.lowercase()}_$period"
        return offering.availablePackages.firstOrNull { it.identifier == id }
    }

    private fun planFrom(info: CustomerInfo): AppPlan = AppPlan.fromEntitlements(info.entitlements.active.keys)
}

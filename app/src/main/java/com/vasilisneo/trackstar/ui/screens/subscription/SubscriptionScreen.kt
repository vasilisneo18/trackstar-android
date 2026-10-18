package com.vasilisneo.trackstar.ui.screens.subscription

// Visual replica of SubscriptionView (Trackstar/UI/View/Subscription/SubscriptionView.swift)
// on iOS: a swipeable pager of Bronze/Silver/Gold plan cards with a tier segmented control,
// and a billing bottom sheet (Annual/Monthly). Wired to RevenueCat via BillingManager: prices come
// live from the store offering (falling back to the hardcoded EUR strings until RevenueCat/Play are
// configured), "Start Free Trial" runs a real purchase, and Restore Purchases / current-plan states
// work. The hardcoded EUR values below are the fallbacks shown before live pricing loads.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.billing.AppPlan
import com.vasilisneo.trackstar.data.billing.BillingManager
import com.vasilisneo.trackstar.data.billing.BillingPeriod
import com.vasilisneo.trackstar.data.billing.PlanPricing
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import com.vasilisneo.trackstar.ui.theme.TrackstarSurface
import kotlinx.coroutines.launch

private data class Tier(
    val name: String,
    val plan: AppPlan,
    val accent: Color,
    val monthly: String,
    val annual: String,
    val annualMonthly: String,
    val savings: String,
    val features: List<String>,
)

// The single in-app paid tier: Athlete Gold (€4.99/mo · €39.99/yr). Coaching is sold on the web
// (Starter/Coach/Pro), never in-app — Apple's anti-steering rules make an in-app coach-purchase
// link a rejection risk — so no coach tier appears here. Prices are fallbacks shown until the
// live RevenueCat price loads.
private val AthleteGold = Tier(
    name = "Athlete Gold", plan = AppPlan.GOLD, accent = Color(0xFFFFC61A),
    monthly = "€4.99", annual = "€39.99", annualMonthly = "€3.33", savings = "Save 33%",
    features = listOf(
        "AI workout & diet planners",
        "Full history, stats & progress charts",
        "Premium app themes",
    ),
)

// Finds the hosting Activity from a Compose context — RevenueCat's purchase flow needs one to
// launch Google's billing sheet.
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// Opens RevenueCat's subscription-management URL (cancel / upgrade / downgrade / payment method) —
// the Android analogue of iOS's "Manage in App Store". For a Google Play URL it prefers the Play
// Store app, falling back to a browser if that isn't the handler.
private fun openManageUrl(context: android.content.Context, url: String) {
    val uri = android.net.Uri.parse(url)
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
    if (url.contains("play.google", ignoreCase = true)) intent.setPackage("com.android.vending")
    runCatching { context.startActivity(intent) }
        .onFailure { runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) } }
}

// Message for a comped-grant holder who taps to upgrade: they already hold the plan free, so there's
// nothing to buy until the grant ends.
private fun grantUpgradeMessage(planName: String, expiry: java.util.Date?): String {
    val until = expiry?.let {
        " until " + java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(it)
    } ?: ""
    return "You have $planName free$until. You can purchase a subscription once your grant expires."
}

private fun featureIcon(feature: String): ImageVector {
    val f = feature.lowercase()
    return when {
        f.contains("ai") -> Icons.Filled.AutoAwesome
        f.contains("no ads") -> Icons.Filled.NotificationsOff
        f.contains("history") -> Icons.Filled.Schedule
        f.contains("stats") -> Icons.Filled.BarChart
        f.contains("template") -> Icons.Filled.ContentCopy
        f.contains("coach") -> Icons.Filled.Groups
        f.contains("assign") -> Icons.Filled.SwapHoriz
        f.contains("unlimited") && f.contains("session") -> Icons.Filled.AllInclusive
        else -> Icons.Filled.CheckCircle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onDismiss: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showBilling by remember { mutableStateOf(false) }
    val tier = AthleteGold

    val currentPlan by BillingManager.currentPlan.collectAsState()
    val managementUrl by BillingManager.managementUrl.collectAsState()
    val currentPlanExpiry by BillingManager.currentPlanExpiry.collectAsState()
    val pricing by BillingManager.pricing.collectAsState()

    // Gold and Coach both already include the athlete features, so Athlete Gold is their plan.
    val isCurrentPlan = currentPlan.atLeast(AppPlan.GOLD)

    // Where the active plan is billed, derived from RevenueCat's management URL: Apple, or a comped
    // grant (a paid plan with no management URL). Google Play on Android needs no special handling.
    val isApplePlan = managementUrl?.contains("apple", ignoreCase = true) == true
    val isGrantPlan = currentPlan != AppPlan.FREE && managementUrl == null

    // Caption above the CTA — only when it adds information (cross-platform Apple, or a grant).
    val subscribedCaption = when {
        isApplePlan -> "Subscribed on Apple"
        isGrantPlan -> "Plan granted by Trackstar"
        else -> null
    }
    val currentTierName = if (currentPlan == AppPlan.COACH) "Coach" else "Athlete Gold"

    // Non-null message shown in a dialog (Apple cross-platform or comped grant).
    var manageInfoMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Theme-aware background (accent glow at the top over the base), consistent with the
            // rest of the app rather than a fixed blue.
            .trackstarBackground()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp)
                ) {
                    GlassCircleIconButton(onClick = onDismiss, icon = Icons.Filled.Close, contentDescription = "Close")
                    Text("Level Up", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.width(44.dp))
                }
            }

            // Single Athlete Gold card (no tier carousel — one in-app paid tier).
            Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PlanCard(tier, pricing[tier.plan], modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Bottom CTA — navigationBarsPadding so it clears the system nav bar on 3-button devices.
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp)) {
                subscribedCaption?.let { caption ->
                    Text(
                        caption,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                if (isCurrentPlan) {
                    PillButton(
                        text = "Current Plan",
                        foreground = Color.White.copy(alpha = 0.5f),
                        background = Color.White.copy(alpha = 0.08f),
                        enabled = false,
                        onClick = {}
                    )
                } else {
                    PillButton(
                        text = "Join Athlete Gold",
                        foreground = Color.Black,
                        background = Color.White,
                        onClick = {
                            val url = managementUrl
                            when {
                                // Free → straight to purchase.
                                currentPlan == AppPlan.FREE -> showBilling = true
                                // Comped grant (paid plan, no store URL) → nothing to buy yet.
                                url == null -> manageInfoMessage = grantUpgradeMessage(currentTierName, currentPlanExpiry)
                                // Bought on Apple → must cancel there before buying on Android.
                                url.contains("apple", ignoreCase = true) -> manageInfoMessage =
                                    "This subscription was purchased on Apple. To subscribe on Android, cancel it from your Apple subscriptions first, then come back and purchase here."
                                // Bought on Google Play (or other store) → change it there.
                                else -> openManageUrl(context, url)
                            }
                        }
                    )
                }
            }
        }
    }

    manageInfoMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { manageInfoMessage = null },
            confirmButton = { TextButton(onClick = { manageInfoMessage = null }) { Text("OK") } },
            title = { Text("Manage subscription") },
            text = { Text(message) },
        )
    }

    if (showBilling) {
        // Merge live store prices over the hardcoded fallbacks for Athlete Gold.
        val livePrice = pricing[tier.plan]
        BillingSheet(
            tier = tier,
            monthly = livePrice?.monthlyPrice ?: tier.monthly,
            annual = livePrice?.annualPrice ?: tier.annual,
            annualMonthly = livePrice?.annualMonthlyEquivalent ?: tier.annualMonthly,
            savings = livePrice?.savings ?: tier.savings,
            trialDays = livePrice?.trialDays ?: 0,
            onDismiss = { showBilling = false },
            onSubscribe = { billing ->
                val activity = context.findActivity()
                if (activity == null) {
                    showBilling = false
                } else {
                    scope.launch {
                        val result = BillingManager.purchase(activity, tier.plan, billing)
                        result.onSuccess { purchased ->
                            showBilling = false
                            if (purchased) onDismiss()
                        }.onFailure { e ->
                            showBilling = false
                            android.widget.Toast.makeText(
                                context, e.message ?: "Purchase failed. Please try again.", android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            onRestore = {
                scope.launch {
                    val result = BillingManager.restore()
                    val msg = result.fold(
                        onSuccess = { plan -> if (plan == AppPlan.FREE) "No purchases to restore." else "Restored your ${plan.name.lowercase()} plan." },
                        onFailure = { it.message ?: "Couldn't restore purchases." },
                    )
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    if (result.getOrNull()?.let { it != AppPlan.FREE } == true) { showBilling = false; onDismiss() }
                }
            },
        )
    }
}

@Composable
private fun PlanCard(tier: Tier, pricing: PlanPricing?, modifier: Modifier = Modifier) {
    // Live store price (in the buyer's currency) when loaded, else the hardcoded EUR fallback.
    val monthlyPrice = pricing?.monthlyPrice ?: tier.monthly
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(24.dp))
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(185.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(tier.accent.copy(alpha = 0.28f), Color.Transparent)))
            )
            Icon(
                Icons.Filled.MilitaryTech, contentDescription = null,
                tint = tier.accent.copy(alpha = 0.10f),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(100.dp)
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tier.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("$monthlyPrice/month", fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f))
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))

        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            tier.features.forEach { feature ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                        Icon(featureIcon(feature), contentDescription = null, tint = tier.accent.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
                    }
                    Text(feature, fontSize = 14.sp, color = Color.White.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillingSheet(
    tier: Tier,
    monthly: String,
    annual: String,
    annualMonthly: String,
    savings: String,
    trialDays: Int,
    onDismiss: () -> Unit,
    onSubscribe: (BillingPeriod) -> Unit,
    onRestore: () -> Unit,
) {
    var isAnnual by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()
    val isPurchasing by BillingManager.isPurchasing.collectAsState()
    val priceLabel = if (isAnnual) "$annual/yr" else "$monthly/mo"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = TrackstarSurface) {
        // navigationBarsPadding so the trial CTA / restore / terms clear the 3-button nav bar
        // instead of being clipped behind it.
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BillingRow(
                title = "Annual",
                subtitle = "$annual/year  ·  $annualMonthly/month",
                badge = savings,
                selected = isAnnual,
                onClick = { isAnnual = true }
            )
            BillingRow(
                title = "Monthly",
                subtitle = "$monthly/month",
                badge = null,
                selected = !isAnnual,
                onClick = { isAnnual = false }
            )

            Spacer(modifier = Modifier.height(12.dp))
            PillButton(
                text = if (trialDays > 0) "Start Free Trial · $priceLabel" else "Subscribe · $priceLabel",
                foreground = Color.Black,
                background = Color.White,
                enabled = !isPurchasing,
                loading = isPurchasing,
                onClick = { onSubscribe(if (isAnnual) BillingPeriod.ANNUAL else BillingPeriod.MONTHLY) }
            )
            if (trialDays > 0) {
                Text(
                    "$trialDays-day free trial, then $priceLabel",
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Text(
                "Restore Purchases",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable(enabled = !isPurchasing, onClick = onRestore)
            )
            Text(
                "Cancel anytime · By subscribing you agree to our Terms of Service.",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.22f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun BillingRow(title: String, subtitle: String, badge: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            if (badge != null) {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF40D999).copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(badge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF40D999))
                }
            }
        }
        if (selected) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    foreground: Color,
    background: Color,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            CircularProgressIndicator(color = foreground, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = foreground)
        }
    }
}

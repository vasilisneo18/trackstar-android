package com.vasilisneo.trackstar.ui.screens.subscription

// Visual replica of SubscriptionView (Trackstar/UI/View/Subscription/SubscriptionView.swift)
// on iOS: a swipeable pager of Bronze/Silver/Gold plan cards with a tier segmented control,
// and a billing bottom sheet (Annual/Monthly). Prices are the hardcoded EUR fallbacks from
// iOS's AppPlan — there's no RevenueCat on Android, so "Start Free Trial" is a no-op that
// just dismisses. Skipped vs iOS: real purchases, restore, "current plan" states.

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.TrackstarSurface
import kotlinx.coroutines.launch

private data class Tier(
    val name: String,
    val accent: Color,
    val monthly: String,
    val annual: String,
    val annualMonthly: String,
    val savings: String,
    val features: List<String>,
)

private val Tiers = listOf(
    Tier(
        name = "Bronze", accent = Color(0xFFCC8033),
        monthly = "€2.99", annual = "€19.99", annualMonthly = "€1.67", savings = "Save 44%",
        features = listOf("Create & edit workout plans", "Unlimited session logging", "Session history & stats", "No ads"),
    ),
    Tier(
        name = "Silver", accent = Color(0xFFB8BFD1),
        monthly = "€5.99", annual = "€39.99", annualMonthly = "€3.33", savings = "Save 44%",
        features = listOf("Create & edit workout plans", "Unlimited session logging", "Session history & stats", "No ads", "AI diet plans", "AI workout plans"),
    ),
    Tier(
        name = "Gold", accent = Color(0xFFFFC61A),
        monthly = "€9.99", annual = "€69.99", annualMonthly = "€5.83", savings = "Save 42%",
        features = listOf("Create & edit workout plans", "Unlimited session logging", "Session history & stats", "No ads", "AI diet & workout plans", "Coach athletes", "Assign & view athlete plans", "Up to 20 plan templates", "AI template generation"),
    ),
)

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
    initialTier: Int = 1, // default to Silver (the "popular" tier)
) {
    val pagerState = rememberPagerState(initialPage = initialTier.coerceIn(0, 2), pageCount = { Tiers.size })
    val scope = rememberCoroutineScope()
    var showBilling by remember { mutableStateOf(false) }
    val selectedTier = Tiers[pagerState.currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Opaque base first — then the blue tint on top. Without the opaque base the
            // gradient's low-alpha top would let the screen behind show through.
            .background(TrackstarBackground)
            .background(Brush.verticalGradient(listOf(Color(0xFF2E80FF).copy(alpha = 0.14f), Color.Transparent)))
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
                // Tier segmented buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Tiers.forEachIndexed { i, tier ->
                        val selected = pagerState.currentPage == i
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(tier.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.weight(1f)
            ) { page ->
                // Full-width page + the card's own horizontal margin, so adjacent cards
                // don't peek in at the edges (contentPadding would cause that).
                PlanCard(Tiers[page], modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Bottom CTA
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 24.dp)) {
                PillButton(
                    text = "Join ${selectedTier.name}",
                    foreground = Color.Black,
                    background = Color.White,
                    onClick = { showBilling = true }
                )
            }
        }
    }

    if (showBilling) {
        BillingSheet(tier = selectedTier, onDismiss = { showBilling = false }, onSubscribe = { showBilling = false; onDismiss() })
    }
}

@Composable
private fun PlanCard(tier: Tier, modifier: Modifier = Modifier) {
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
                Text("${tier.monthly}/month", fontSize = 16.sp, color = Color.White.copy(alpha = 0.55f))
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
private fun BillingSheet(tier: Tier, onDismiss: () -> Unit, onSubscribe: () -> Unit) {
    var annual by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = TrackstarSurface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BillingRow(
                title = "Annual",
                subtitle = "${tier.annual}/year  ·  ${tier.annualMonthly}/month",
                badge = tier.savings,
                selected = annual,
                onClick = { annual = true }
            )
            BillingRow(
                title = "Monthly",
                subtitle = "${tier.monthly}/month",
                badge = null,
                selected = !annual,
                onClick = { annual = false }
            )

            Spacer(modifier = Modifier.height(12.dp))
            PillButton(
                text = "Start Free Trial · ${if (annual) tier.annual + "/yr" else tier.monthly + "/mo"}",
                foreground = Color.Black,
                background = Color.White,
                onClick = onSubscribe
            )
            Text(
                "7-day free trial, then ${if (annual) tier.annual + "/yr" else tier.monthly + "/mo"}",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                "Restore Purchases",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
            Text(
                "Cancel anytime · Billed in EUR · By subscribing you agree to our Terms of Service.",
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
private fun PillButton(text: String, foreground: Color, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = foreground)
    }
}

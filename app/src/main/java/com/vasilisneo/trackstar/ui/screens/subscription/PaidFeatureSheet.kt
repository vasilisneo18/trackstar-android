package com.vasilisneo.trackstar.ui.screens.subscription

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.activity.TrailCatalog
import com.vasilisneo.trackstar.data.billing.AppPlan
import com.vasilisneo.trackstar.data.billing.BillingManager
import com.vasilisneo.trackstar.data.billing.FeatureGate
import com.vasilisneo.trackstar.ui.i18n.Text
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import com.vasilisneo.trackstar.ui.screens.main.activity.AppSheet
import com.vasilisneo.trackstar.ui.screens.main.activity.LocalBarInsets

/** A paid feature a free (or lower-tier) user just tried to use. Each has its own screen explaining
 *  what it gives before the plans (PaidFeatureSheet). iOS: PaidFeature. */
sealed class PaidFeature(val plan: AppPlan, val icon: ImageVector) {
    data object OutdoorMaps : PaidFeature(AppPlan.BRONZE, Icons.Filled.Map)
    data object AiPlanners : PaidFeature(AppPlan.SILVER, Icons.Filled.AutoAwesome)
    data object UnlimitedSessions : PaidFeature(AppPlan.BRONZE, Icons.Filled.AllInclusive)
    data object FullHistory : PaidFeature(AppPlan.BRONZE, Icons.Filled.BarChart)
    data class Themes(val needs: AppPlan) : PaidFeature(needs, Icons.Filled.Palette)

    val title: String get() = when (this) {
        OutdoorMaps -> tr("Slope & trail maps")
        AiPlanners -> tr("AI planners")
        UnlimitedSessions -> tr("Unlimited sessions")
        FullHistory -> tr("Full history & stats")
        is Themes -> tr("App themes")
    }

    val subtitle: String get() = when (this) {
        OutdoorMaps -> tr("See where you can ride and walk — and where you went.")
        AiPlanners -> tr("Let AI build your week of training and eating.")
        UnlimitedSessions -> trf("Free accounts can log %s sessions a week. Train as often as you like.", "${FeatureGate.WEEKLY_SESSION_LIMIT}")
        FullHistory -> tr("Every workout you've logged, and how you're progressing.")
        is Themes -> tr("Make Trackstar yours with a new look.")
    }

    val benefits: List<String> get() = when (this) {
        OutdoorMaps -> buildList {
            add(tr("Ski resorts worldwide with their piste maps"))
            add(tr("Your runs drawn over the pistes, coloured by difficulty"))
            if (TrailCatalog.enabled.value) {
                add(tr("Marked hiking trails, with a route to follow"))
                add(tr("Download trail maps for hikes with no signal"))
            }
        }
        AiPlanners -> listOf(
            tr("A weekly workout plan built around your goal and schedule"),
            tr("A diet plan with the foods you like"),
            trf("%s plans of each a month", "6"),
        )
        UnlimitedSessions -> listOf(
            tr("Plan and log as many sessions as you want"),
            tr("Quick Log and live sessions with no weekly cap"),
        )
        FullHistory -> listOf(
            tr("Your whole workout history, not just the last 14 days"),
            tr("Personal records and progress for every exercise"),
            tr("Weekly volume, streaks and completion rate"),
        )
        is Themes -> listOf(
            tr("Colour themes for the whole app"),
            tr("Matching app icons for your plan"),
        )
    }
}

private fun AppPlan.tint(): Color = when (this) {
    AppPlan.BRONZE -> Color(0xFFCC8033)
    AppPlan.SILVER -> Color(0xFFB8BFD1)
    AppPlan.GOLD -> Color(0xFFFFC72E)
    AppPlan.FREE -> Color.White.copy(alpha = 0.6f)
}

private fun AppPlan.displayName(): String = when (this) {
    AppPlan.BRONZE -> "Bronze"
    AppPlan.SILVER -> "Silver"
    AppPlan.GOLD -> "Gold"
    AppPlan.FREE -> "Free"
}

/** The screen for one paid feature: what it gives, which plan has it, and a button to the plans —
 *  "Start free trial" when Play still offers this account a trial, otherwise "Upgrade". */
@Composable
fun PaidFeatureSheet(feature: PaidFeature, onClose: () -> Unit, onOpenPlans: () -> Unit) {
    val pricing by BillingManager.pricing.collectAsState()
    // Play only returns a free-trial offer to accounts that can still use one.
    val trialDays = pricing[feature.plan]?.trialDays ?: 0
    val tint = feature.plan.tint()

    AppSheet(onDismissRequest = onClose) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.padding(start = 20.dp, top = 20.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Box(modifier = Modifier.size(96.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(feature.icon, contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.Text(feature.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    androidx.compose.material3.Text(feature.subtitle, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
                if (feature == PaidFeature.OutdoorMaps) PistePreviewMap()
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(18.dp),
                ) {
                    feature.benefits.forEach { benefit ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            androidx.compose.material3.Text(benefit, fontSize = 15.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                androidx.compose.material3.Text(
                    if (feature.plan == AppPlan.GOLD) trf("Included in %s", feature.plan.displayName())
                    else trf("Included in %s and above", feature.plan.displayName()),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = LocalBarInsets.current.bottom + 16.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onOpenPlans),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (trialDays > 0) "Start free trial" else "Upgrade", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                }
                if (trialDays > 0) {
                    androidx.compose.material3.Text(trf("%s days free, then cancel anytime", "$trialDays"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

/** A glance at what the piste maps look like: Val Thorens with its runs in their difficulty colours.
 *  Not interactive; until the runs load it's just the mountain. iOS: PistePreviewMap. */
@Composable
private fun PistePreviewMap() {
    // Val Thorens (OpenSkiMap extent, trimmed to the main bowl).
    val box = listOf(45.272, 6.555, 45.312, 6.605)
    var pistes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.vasilisneo.trackstar.data.activity.Piste>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { pistes = com.vasilisneo.trackstar.data.activity.PisteMapStore.previewPistes(box) }
    val bounds = com.google.android.gms.maps.model.LatLngBounds(
        com.google.android.gms.maps.model.LatLng(box[0], box[1]), com.google.android.gms.maps.model.LatLng(box[2], box[3]))
    val camera = com.google.maps.android.compose.rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(bounds.center, 12.6f)
    }
    Box(modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp))) {
        com.google.maps.android.compose.GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            googleMapOptionsFactory = { com.google.android.gms.maps.GoogleMapOptions().liteMode(true) },
            properties = com.google.maps.android.compose.MapProperties(mapType = com.google.maps.android.compose.MapType.HYBRID),
            uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false,
                compassEnabled = false, myLocationButtonEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false),
            onMapClick = {},
        ) {
            com.vasilisneo.trackstar.ui.screens.main.activity.PisteLines(pistes, width = 7f, convention = "europe")
        }
        // Takes the touches so the lite map never opens Google Maps.
        Box(Modifier.fillMaxSize().clickable(enabled = false) {})
        androidx.compose.material3.Text("Val Thorens", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 10.dp, vertical = 5.dp))
        com.vasilisneo.trackstar.ui.screens.main.activity.OsmAttribution(Modifier.align(Alignment.BottomEnd))
    }
}

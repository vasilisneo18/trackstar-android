package com.vasilisneo.trackstar.ui.screens.main

// Visual replica of ProfileView (Trackstar/UI/View/MainApp/Profile/ProfileView.swift) on
// iOS. Driven by placeholder data (PlaceholderProfile) since there's no auth/session,
// profile-networking, or subscription system on Android yet — same simulated posture as
// the login/register flow. Scope cuts vs iOS: the collapsing nav-bar name animation, the
// QR-connect sheet, the subscription sheet, and the Edit-Profile / Settings / Personal-Info
// detail screens (the rows are present but currently inert). The tap-to-flip weight→goal
// stat card is reproduced.

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.initialsFrom
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

private data class ProfileData(
    val fullName: String,
    val initials: String,
    val email: String,
    val country: String,
    val gender: String,
    val age: Int?,
    val heightCm: Int?,
    val weightKg: Double?,
    val targetWeightKg: Double?,
)

private val CardSurface = Color.White.copy(alpha = 0.06f)

@Composable
fun ProfileScreen(
    onBackClick: () -> Unit = {},
    onLogout: () -> Unit = {},
    onPersonalInfo: () -> Unit = {},
    onSettings: () -> Unit = {},
    onUpgrade: () -> Unit = {},
    onQrCode: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
) {
    // Real profile from GET /api/profile, falling back to the cached session name/email
    // (and "—" for body stats) while the fetch is in flight or if it fails offline.
    val remote = viewModel.profile
    val fullName = listOfNotNull(remote?.firstName?.ifBlank { null }, remote?.lastName?.ifBlank { null })
        .joinToString(" ").ifBlank { viewModel.cachedFullName }
    val profile = ProfileData(
        fullName = fullName,
        initials = initialsFrom(fullName),
        email = remote?.email ?: viewModel.cachedEmail,
        country = remote?.country ?: "",
        gender = remote?.gender?.replaceFirstChar { it.uppercase() } ?: "—",
        age = remote?.age,
        heightCm = remote?.height?.toInt(),
        weightKg = remote?.weight,
        targetWeightKg = remote?.targetWeight,
    )

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Nav bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassCircleIconButton(onClick = onBackClick, icon = Icons.Filled.Close, contentDescription = "Close")
                Spacer(modifier = Modifier.weight(1f))
                GlassCircleIconButton(onClick = onQrCode, icon = Icons.Filled.QrCode2, contentDescription = "My QR code")
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ProfileHeader(profile)

                PersonalSection(profile, onUpgrade = onUpgrade)

                AppSection(onPersonalInfo = onPersonalInfo, onSettings = onSettings)

                LogoutSection(onLogout = { viewModel.logout(); onLogout() })
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: ProfileData) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Soft glow behind the avatar — a radial gradient rather than Modifier.blur(),
            // which needs RenderEffect (API 31+) and no-ops below that; minSdk here is 26.
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TrackstarAccent.copy(alpha = 0.45f),
                                TrackstarAccent.copy(alpha = 0.15f),
                                TrackstarAccent.copy(alpha = 0f),
                            )
                        ),
                        shape = CircleShape,
                    )
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(TrackstarAccent.copy(alpha = 0.9f), TrackstarAccent.copy(alpha = 0.45f))
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(profile.initials, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Text(profile.fullName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(profile.email, fontSize = 14.sp, color = Color.White.copy(alpha = 0.45f))

        // Fixed-height slot so the async country fetch doesn't reflow the page when it fills in.
        Box(modifier = Modifier.height(18.dp), contentAlignment = Alignment.Center) {
            if (profile.country.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(13.dp))
                    Text(profile.country, fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun PersonalSection(profile: ProfileData, onUpgrade: () -> Unit) {
    val plan by com.vasilisneo.trackstar.data.billing.BillingManager.currentPlan.collectAsState()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        // Free members see the "Level Up" upsell; subscribers see their tier (matches iOS).
        if (plan == com.vasilisneo.trackstar.data.billing.AppPlan.FREE) {
            LevelUpCard(onClick = onUpgrade)
        } else {
            MembershipCard(plan = plan, onClick = onUpgrade)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(icon = Icons.Filled.Person, title = "Gender", value = profile.gender, unit = "", modifier = Modifier.weight(1f))
            StatCard(icon = Icons.Filled.Cake, title = "Age", value = profile.age?.toString() ?: "—", unit = if (profile.age != null) "yrs" else "", modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(icon = Icons.Filled.Straighten, title = "Height", value = profile.heightCm?.toString() ?: "—", unit = if (profile.heightCm != null) "cm" else "", modifier = Modifier.weight(1f))
            WeightStatCard(weight = profile.weightKg, targetWeight = profile.targetWeightKg, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun LevelUpCard(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFFCD7F32).copy(alpha = 0.35f), // bronze
                        Color(0xFFB8B8B8).copy(alpha = 0.25f), // silver
                        Color(0xFFE6B325).copy(alpha = 0.35f), // gold
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = Color(0xFFE6B325), modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Level Up", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Start your 7-day free trial", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
    }
}

// Subscriber's tier card (iOS's paid plan-card state): tinted icon + tier name + Manage/Upgrade.
@Composable
private fun MembershipCard(plan: com.vasilisneo.trackstar.data.billing.AppPlan, onClick: () -> Unit) {
    val accent = com.vasilisneo.trackstar.ui.components.tierAccentColor(plan)
    val name = plan.name.lowercase().replaceFirstChar { it.uppercase() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                if (plan == com.vasilisneo.trackstar.data.billing.AppPlan.GOLD) "Manage" else "Upgrade",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatCard(icon: ImageVector, title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(22.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (unit.isNotEmpty()) {
                Text(unit, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp))
            }
        }
        Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun WeightStatCard(weight: Double?, targetWeight: Double?, modifier: Modifier = Modifier) {
    var flipped by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "weightFlip"
    )
    Box(
        modifier = modifier
            .height(100.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
            .clickable { flipped = !flipped },
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            StatFace(icon = Icons.Filled.MonitorWeight, iconTint = Color.White.copy(alpha = 0.75f),
                value = weight?.let { "%.1f".format(it) } ?: "—", unit = if (weight != null) "kg" else "", title = "Weight")
        } else {
            // Counter-rotate so the back face isn't mirrored
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }, contentAlignment = Alignment.Center) {
                StatFace(icon = Icons.Filled.TrackChanges, iconTint = TrackstarAccent.copy(alpha = 0.85f),
                    value = targetWeight?.let { "%.1f".format(it) } ?: "—", unit = if (targetWeight != null) "kg" else "", title = "Goal")
            }
        }
    }
}

@Composable
private fun StatFace(icon: ImageVector, iconTint: Color, value: String, unit: String, title: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(unit, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun AppSection(onPersonalInfo: () -> Unit, onSettings: () -> Unit) {
    ProfileGroup {
        ProfileRow(icon = Icons.Outlined.Badge, label = "Personal Info", onClick = onPersonalInfo)
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(start = 62.dp))
        ProfileRow(icon = Icons.Filled.Settings, label = "Settings", onClick = onSettings)
    }
}

@Composable
private fun LogoutSection(onLogout: () -> Unit) {
    ProfileGroup {
        ProfileRow(icon = Icons.AutoMirrored.Filled.Logout, label = "Log Out", showChevron = false, onClick = onLogout)
    }
}

@Composable
private fun ProfileGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardSurface)
    ) {
        content()
    }
}

@Composable
private fun ProfileRow(icon: ImageVector, label: String, showChevron: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Text(label, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (showChevron) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
        }
    }
}

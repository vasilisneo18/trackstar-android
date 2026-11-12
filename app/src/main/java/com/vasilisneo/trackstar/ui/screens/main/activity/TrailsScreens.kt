package com.vasilisneo.trackstar.ui.screens.main.activity

// Hiking on Android: the Home card, the trail browser (search, Near me, countries), a trail's page
// (route on the terrain map, length, network, Hike this) and offline trail packs (download /
// delete). Mirrors iOS's TrailsViews.swift.

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline as MapPolyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.vasilisneo.trackstar.data.activity.ActivityStore
import com.vasilisneo.trackstar.data.activity.ActivityType
import com.vasilisneo.trackstar.data.activity.Trail
import com.vasilisneo.trackstar.data.activity.TrailCatalog
import com.vasilisneo.trackstar.data.activity.TrailInfo
import com.vasilisneo.trackstar.data.activity.activityType
import com.vasilisneo.trackstar.ui.i18n.Text
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CardFill = Color.White.copy(alpha = 0.06f)

// MARK: - Home card

/** Full-width card: tap to record a hike; the map button opens the trail browser. */
@Composable
fun HikingHomeCard(date: LocalDate, onRecord: () -> Unit, onTrails: () -> Unit, modifier: Modifier = Modifier) {
    val all by ActivityStore.activities.collectAsState()
    val hikes = all.filter {
        it.activityType == ActivityType.Hike && Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).year == date.year
    }
    val km = hikes.sumOf { it.distanceMeters } / 1000
    Box(
        modifier = modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(20.dp)).background(CardFill)
            .clickable(onClick = onRecord).padding(10.dp)
    ) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF143329), Color(0xFF407352))))) {
            val w = size.width; val h = size.height; val top = h - 70.dp.toPx()
            val path = Path().apply {
                moveTo(0f, h); lineTo(w * 0.30f, top + 0.15f * 70.dp.toPx()); lineTo(w * 0.48f, top + 0.55f * 70.dp.toPx())
                lineTo(w * 0.70f, top); lineTo(w, top + 0.45f * 70.dp.toPx()); lineTo(w, h); close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF6B9E66), Color(0xFF335C3D)), startY = top, endY = h))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(10.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Hiking, null, tint = TrackstarAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Hiking", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (hikes.isNotEmpty()) trf("%s hikes this year", "${hikes.size}") else tr("Trails & hikes"),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                if (km > 0) androidx.compose.material3.Text(String.format("%.1f km", km), fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        CornerButton(Icons.Filled.Map, tr("Trails"), size = 44.dp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), onClick = onTrails)
    }
}

// MARK: - Trail browser

@Composable
fun TrailsDialog(onClose: () -> Unit, onHike: (Trail?) -> Unit) {
    val context = LocalContext.current
    val countries by TrailCatalog.countries.collectAsState()
    val downloaded by TrailCatalog.downloaded.collectAsState()
    var text by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    /** null = near me (or the first country when location is unknown); otherwise an ISO code. */
    var country by remember { mutableStateOf<String?>(null) }
    var here by remember { mutableStateOf<LatLng?>(null) }
    var located by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TrailInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var opened by remember { mutableStateOf<TrailInfo?>(null) }
    var showOffline by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) runCatching {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { loc -> if (loc != null) here = LatLng(loc.latitude, loc.longitude); located = true }
                .addOnFailureListener { located = true }
        }.onFailure { located = true } else located = true
        TrailCatalog.loadCountries()
    }
    LaunchedEffect(text) { if (text.isNotEmpty()) delay(200); query = text }
    LaunchedEffect(query, country, here, located, countries) {
        if (!located) return@LaunchedEffect
        val h = here; val c = country
        val pool: List<TrailInfo> = when {
            c != null -> TrailCatalog.trails(c)
            query.isNotEmpty() -> countries.flatMap { TrailCatalog.trails(it.cc) }
            h != null -> TrailCatalog.countriesNear(h.latitude, h.longitude).flatMap { TrailCatalog.trails(it) }
                .sortedBy { it.distanceTo(h.latitude, h.longitude) }.take(60)
            countries.isNotEmpty() -> { country = countries.first().cc; return@LaunchedEffect }
            else -> emptyList()
        }
        var found = TrailCatalog.search(query, pool)
        if (h != null && c == null && query.isNotEmpty()) found = found.sortedBy { it.distanceTo(h.latitude, h.longitude) }
        results = found.take(200)
        loading = false
        listState.scrollToItem(0)
    }

    AppSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxSize().padding(top = LocalBarInsets.current.top)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                RoundButton(Icons.Filled.Close, tr("Close"), onClose)
                Spacer(Modifier.width(14.dp))
                Text("Trails", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                RoundButton(if (downloaded.isEmpty()) Icons.Filled.Download else Icons.Filled.DownloadDone, tr("Offline maps")) { showOffline = true }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Search trails, villages, areas", fontSize = 16.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                    BasicTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
                if (text.isNotEmpty()) Icon(Icons.Filled.Cancel, tr("Clear"), tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp).clickable { text = "" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (here != null) TrailChip(tr("Near me"), Icons.Filled.NearMe, country == null) { country = null }
                countries.forEach { c ->
                    TrailChip("${com.vasilisneo.trackstar.data.activity.SkiResort.flag(c.cc)} ${com.vasilisneo.trackstar.data.activity.SkiResortCatalog.countryName(c.cc)}",
                        if (c.cc in downloaded) Icons.Filled.DownloadDone else null, country == c.cc) { country = c.cc }
                }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBarInsets.current.bottom + 24.dp)) {
                items(results, key = { it.id }) { t -> TrailRow(t, here, Modifier.padding(bottom = 10.dp).clickable { opened = t }) }
                if (!loading && results.isEmpty()) item {
                    Text(if (countries.isEmpty()) "Trails need a connection the first time" else "No trails found", fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 50.dp))
                }
                item {
                    androidx.compose.material3.Text("© OpenStreetMap", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                }
            }
        }
        opened?.let { t -> TrailDetailDialog(t, onClose = { opened = null }, onHike = { route -> opened = null; onHike(route) }) }
        if (showOffline) OfflineTrailsDialog(onClose = { showOffline = false })
    }
}

@Composable
private fun TrailChip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) TrackstarAccent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp)) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
        androidx.compose.material3.Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun TrailRow(t: TrailInfo, here: LatLng?, modifier: Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(14.dp)) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(t.color.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Hiking, null, tint = t.color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(t.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
            androidx.compose.material3.Text(t.place, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            androidx.compose.material3.Text(String.format("%.1f km", t.km), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (here != null) {
                val d = t.distanceTo(here.latitude, here.longitude)
                androidx.compose.material3.Text(if (d < 10_000) trf("%s km away", String.format("%.1f", d / 1000)) else trf("%s km away", String.format("%.0f", d / 1000)),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
            }
        }
    }
}

// MARK: - Trail page

@Composable
fun TrailDetailDialog(info: TrailInfo, onClose: () -> Unit, onHike: (Trail?) -> Unit) {
    var route by remember { mutableStateOf<Trail?>(null) }
    var failed by remember { mutableStateOf(false) }
    val center = LatLng(info.lat, info.lon)
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, 13f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(info.id) {
        route = TrailCatalog.route(info)
        failed = route == null
    }
    fun fit() {
        val b = info.box
        if (b.size != 4) return
        scope.launch { runCatching { camera.move(CameraUpdateFactory.newLatLngBounds(LatLngBounds(LatLng(b[0], b[1]), LatLng(b[2], b[3])), 80)) } }
    }
    AppSheet(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize()) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(top = LocalBarInsets.current.top + 64.dp, bottom = LocalBarInsets.current.bottom + 110.dp).padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(24.dp))) {
                    GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = camera, properties = terrainMapProperties,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                        onMapLoaded = { fit() }) {
                        route?.lines?.forEach { line ->
                            MapPolyline(points = line.coordinates, color = Color.White, width = 16f, jointType = JointType.ROUND,
                                startCap = RoundCap(), endCap = RoundCap(), zIndex = 1f)
                            MapPolyline(points = line.coordinates, color = info.color, width = 10f, jointType = JointType.ROUND,
                                startCap = RoundCap(), endCap = RoundCap(), zIndex = 2f)
                        }
                    }
                    OsmAttribution(Modifier.align(Alignment.BottomEnd))
                    if (route == null) {
                        androidx.compose.material3.Text(if (failed) tr("Trail map not available offline") else tr("Loading trail…"),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                            modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.compose.material3.Text(info.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    androidx.compose.material3.Text("${info.flag} ${info.place}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
                    TrailStat(String.format("%.1f", info.km), "km", Modifier.weight(1f))
                    info.networkLabel?.let { TrailStat(it, tr("Network"), Modifier.weight(1f)) }
                    info.ref?.takeIf { it != info.n }?.let { TrailStat(it, tr("Marked"), Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 20.dp, vertical = 14.dp)) {
                RoundButton(Icons.Filled.ExpandMore, tr("Close"), onClose)
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalBarInsets.current.bottom).padding(16.dp)
                    .height(54.dp).clip(RoundedCornerShape(50)).background(TrackstarAccent).clickable { onHike(route) }) {
                Icon(Icons.Filled.Hiking, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Hike this", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

@Composable
private fun TrailStat(value: String, label: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        androidx.compose.material3.Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        androidx.compose.material3.Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
    }
}

// MARK: - Offline packs

/** Download a country's trails for hikes without signal, or delete them again. */
@Composable
fun OfflineTrailsDialog(onClose: () -> Unit) {
    val countries by TrailCatalog.countries.collectAsState()
    val downloaded by TrailCatalog.downloaded.collectAsState()
    val downloading by TrailCatalog.downloading.collectAsState()
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TrailCatalog.loadCountries() }
    AppSheet(onDismissRequest = onClose) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().padding(top = LocalBarInsets.current.top, bottom = LocalBarInsets.current.bottom).padding(20.dp)) {
            RoundButton(Icons.Filled.Close, tr("Close"), onClose)
            Text("Offline maps", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Download a country's trails to see them on the map and follow them without signal. The base map itself needs a connection.",
                fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
            countries.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(14.dp)) {
                    androidx.compose.material3.Text(com.vasilisneo.trackstar.data.activity.SkiResort.flag(c.cc), fontSize = 26.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        androidx.compose.material3.Text(com.vasilisneo.trackstar.data.activity.SkiResortCatalog.countryName(c.cc),
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        androidx.compose.material3.Text("${trf("%s trails", "${c.count}")} · ${android.text.format.Formatter.formatShortFileSize(LocalContext.current, c.packBytes ?: 0)}",
                            fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                    when {
                        c.cc in downloading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        c.cc in downloaded -> Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))
                                .clickable { TrailCatalog.deletePack(c.cc) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Filled.DeleteOutline, null, tint = Color(0xFFFF453A), modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Delete", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF453A))
                        }
                        else -> Row(verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(TrackstarAccent)
                                .clickable { failed = false; scope.launch { if (!TrailCatalog.download(c.cc)) failed = true } }
                                .padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Icon(Icons.Filled.Download, null, tint = Color.White, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Download", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
            if (failed) Text("Couldn't download. Check your connection and try again.", fontSize = 13.sp, color = Color(0xFFFF453A))
        }
    }
}

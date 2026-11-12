package com.vasilisneo.trackstar.ui.screens.main.activity

// Slopes on Android: the Home card, the ski resort browser (search, country chips, near me), a
// resort's page (piste map coloured by difficulty, lifts, legend, runs by colour — tap one to focus
// it) and the full-screen piste map. Mirrors iOS's ActivityViews.swift (SlopesCard) and
// ResortsView.swift.

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.Stairs
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline as MapPolyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.vasilisneo.trackstar.data.activity.ActivityStore
import com.vasilisneo.trackstar.data.activity.ActivityType
import com.vasilisneo.trackstar.data.activity.Piste
import com.vasilisneo.trackstar.data.activity.PisteMapStore
import com.vasilisneo.trackstar.data.activity.SkiResort
import com.vasilisneo.trackstar.data.activity.SkiResortCatalog
import com.vasilisneo.trackstar.data.activity.activityType
import com.vasilisneo.trackstar.data.activity.coordinates
import com.vasilisneo.trackstar.ui.i18n.Text
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val CardFill = Color.White.copy(alpha = 0.06f)

// MARK: - Map overlay

/** Pistes coloured by difficulty and lifts as thin dashed lines, inside a `GoogleMap { }`. When
 *  `highlighted` is set those segments are drawn on top with a white casing and the rest fade. */
@Composable
@GoogleMapComposable
fun PisteLines(
    pistes: List<Piste>, width: Float = 9f, convention: String? = null,
    highlighted: Set<Long>? = null, onClick: ((Piste) -> Unit)? = null,
) {
    fun faded(p: Piste) = highlighted != null && p.id !in highlighted
    for (p in pistes) {
        if (p.isLift) continue
        val pts = p.coordinates
        // Black pistes get a grey casing so they show on the dark map.
        if (p.difficulty == "advanced" || p.difficulty == "expert") {
            MapPolyline(points = pts, color = Color(0xFF8C8C8C).copy(alpha = if (faded(p)) 0.25f else 1f), width = width + 5f,
                jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
        }
        MapPolyline(points = pts, color = p.color(convention).copy(alpha = if (faded(p)) 0.25f else 0.9f), width = width,
            jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(),
            clickable = onClick != null, onClick = { onClick?.invoke(p) })
    }
    for (l in pistes) {
        if (!l.isLift) continue
        MapPolyline(points = l.coordinates, color = Color.White.copy(alpha = if (faded(l)) 0.2f else 0.7f), width = 4f,
            pattern = listOf(Dash(12f), Gap(9f)), clickable = onClick != null, onClick = { onClick?.invoke(l) })
    }
    if (highlighted != null) {
        for (p in pistes.filter { it.id in highlighted }) {
            MapPolyline(points = p.coordinates, color = Color.White, width = width + 12f, jointType = JointType.ROUND,
                startCap = RoundCap(), endCap = RoundCap())
            MapPolyline(points = p.coordinates, color = if (p.isLift) Color(0xFF333333) else p.color(convention), width = width + 5f,
                jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap(),
                pattern = if (p.isLift) listOf(Dash(18f), Gap(12f)) else null)
        }
    }
}

/** "© OpenStreetMap" — required wherever the piste data is shown. */
@Composable
fun OsmAttribution(modifier: Modifier = Modifier) {
    androidx.compose.material3.Text("© OpenStreetMap", fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.75f),
        modifier = modifier.padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 2.dp))
}

@Composable
fun DifficultyDot(difficulty: String, convention: String?, size: Dp = 10.dp) {
    val color = Piste.colorFor(difficulty.ifEmpty { null }, convention)
    Box(Modifier.size(size).clip(CircleShape).background(color)
        .then(if (color == Color.Black) Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape) else Modifier))
}

// MARK: - Home card

/** Full-width card: last resort's map (or a snowy mountain), "Slopes" + season totals; the Groups
 *  and Ski resorts buttons stacked on the right. Tap to record. */
@Composable
fun SlopesHomeCard(date: LocalDate, lastType: ActivityType, onRecord: () -> Unit, onResorts: () -> Unit,
                   onGroups: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val all by ActivityStore.activities.collectAsState()
    val (_, runs, vertical) = ActivityStore.slopeSeason(date, all)
    val center = all.firstOrNull { it.activityType.isSlope }?.coordinates?.firstOrNull()
    Box(
        modifier = modifier.fillMaxWidth().height(164.dp).clip(RoundedCornerShape(20.dp)).background(CardFill)
            .clickable(onClick = onRecord).padding(10.dp)
    ) {
        if (center != null) {
            StaticMap(center = center, zoom = 13f, onClick = onRecord, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.15f)))))
        } else {
            SnowyMountain(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
        }
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(10.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(lastType.icon(), null, tint = TrackstarAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Slopes", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (runs > 0) trf("%s runs this season", "$runs") else tr("Ski & snowboard"),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
                if (vertical > 0) Text(trf("%s m vertical", "${vertical.toInt()}"), fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) {
            if (onGroups != null) CornerButton(Icons.Filled.Groups, tr("Groups"), size = 44.dp, onClick = onGroups)
            CornerButton(Icons.Filled.Map, tr("Ski resorts"), size = 44.dp, onClick = onResorts)
        }
    }
}

/** A stylised snowy mountain on a dusk sky — the Slopes card before the first day out. */
@Composable
private fun SnowyMountain(modifier: Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFF1A2950), Color(0xFF52739E))))) {
        val w = size.width; val h = size.height; val top = h - 70.dp.toPx()
        val path = Path().apply {
            moveTo(0f, h); lineTo(0f, top + 40.dp.toPx()); lineTo(w * 0.22f, top + 12.dp.toPx())
            lineTo(w * 0.38f, top + 30.dp.toPx()); lineTo(w * 0.6f, top); lineTo(w * 0.8f, top + 26.dp.toPx())
            lineTo(w, top + 14.dp.toPx()); lineTo(w, h); close()
        }
        drawPath(path, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFC7D9F2)), startY = top, endY = h))
    }
}

// MARK: - Resort browser

/** Ski resorts around the world: search by name / region / country, filter by country or Near me. */
@Composable
fun ResortsDialog(onClose: () -> Unit, onRecord: () -> Unit) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(SkiResortCatalog.resorts.isNotEmpty()) }
    var query by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    /** null = near me (or everything when location is unknown); otherwise an ISO country code. */
    var country by remember { mutableStateOf<String?>(null) }
    var here by remember { mutableStateOf<LatLng?>(null) }
    var results by remember { mutableStateOf<List<SkiResort>>(emptyList()) }
    var opened by remember { mutableStateOf<SkiResort?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { SkiResortCatalog.load(context) }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) here = LatLng(loc.latitude, loc.longitude)
                }
            }
        } else {
            // Without a location, start on the phone's country (if it has resorts).
            val home = java.util.Locale.getDefault().country
            if (SkiResortCatalog.countries.any { it.first == home }) country = home
        }
        loaded = true
    }
    // The list follows the field once typing pauses.
    LaunchedEffect(text) { if (text.isNotEmpty()) delay(150); query = text }
    LaunchedEffect(loaded, query, country, here) {
        if (!loaded) return@LaunchedEffect
        results = withContext(Dispatchers.Default) {
            val c = country; val h = here
            val pool = when {
                c != null -> SkiResortCatalog.resorts.filter { it.countryCode == c }
                query.isEmpty() && h != null -> SkiResortCatalog.nearest(h.latitude, h.longitude)
                else -> SkiResortCatalog.resorts
            }
            SkiResortCatalog.search(query, pool).take(200)
        }
        listState.scrollToItem(0)
    }
    val chipLabels = remember(loaded) { SkiResortCatalog.countries.map { (code, _) -> code to "${SkiResort.flag(code)} ${SkiResortCatalog.countryName(code)}" } }

    FullScreenDialog(onDismissRequest = onClose) {
        Column(Modifier.fillMaxSize().trackstarBackground().padding(top = LocalBarInsets.current.top)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                RoundButton(Icons.Filled.Close, tr("Close"), onClose)
                Spacer(Modifier.width(14.dp))
                Text("Ski resorts", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            // Search field
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Icon(Icons.Filled.Search, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text("Search resorts, regions, countries", fontSize = 16.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                    BasicTextField(value = text, onValueChange = { text = it }, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth())
                }
                if (text.isNotEmpty()) Icon(Icons.Filled.Cancel, tr("Clear"), tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp).clickable { text = "" })
            }
            // Country chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Chip(if (here == null) tr("All") else tr("Near me"), if (here == null) Icons.Filled.Language else Icons.Filled.NearMe, country == null) { country = null }
                chipLabels.forEach { (code, label) -> Chip(label, null, country == code) { country = code } }
            }
            LazyColumn(state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalBarInsets.current.bottom + 24.dp)) {
                items(results, key = { it.id }) { r ->
                    ResortRow(r, here, Modifier.padding(bottom = 10.dp).clickable { opened = r })
                }
                if (loaded && results.isEmpty()) item {
                    Text("No resorts found", fontSize = 15.sp, color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(top = 50.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                item {
                    androidx.compose.material3.Text("OpenSkiMap · © OpenStreetMap", fontSize = 11.sp, color = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        opened?.let { r -> ResortDetailDialog(r, onClose = { opened = null }, onRecord = { opened = null; onRecord() }) }
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) TrackstarAccent else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp)) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
        androidx.compose.material3.Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.6f))
    }
}

/** One resort: name, place, runs by colour, km / lifts and elevation (or distance from you). */
@Composable
private fun ResortRow(r: SkiResort, here: LatLng?, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(14.dp)) {
        Row {
            androidx.compose.material3.Text("${r.flag} ${r.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                maxLines = 2, modifier = Modifier.weight(1f))
            if (here != null) {
                val m = r.distanceTo(here.latitude, here.longitude)
                androidx.compose.material3.Text(if (m < 10_000) String.format("%.1f km", m / 1000) else String.format("%.0f km", m / 1000),
                    fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (r.place.isNotEmpty()) androidx.compose.material3.Text(r.place, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            r.difficultySummary.forEach { (d, n) ->
                DifficultyDot(d, r.convention)
                androidx.compose.material3.Text("$n", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 4.dp, end = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            val parts = buildList {
                if (r.kilometers >= 0.5) add(String.format("%.0f km", r.kilometers))
                if (r.lifts > 0) add(if (r.lifts == 1) tr("1 lift") else trf("%s lifts", "${r.lifts}"))
                r.maxElevation?.let { add("↑ $it m") }
            }
            androidx.compose.material3.Text(parts.joinToString(" · "), fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), maxLines = 1)
        }
    }
}

// MARK: - Resort page

/** A run or lift as listed under the map: every OSM segment with the same name and colour. */
data class PisteGroup(val key: String, val name: String, val isLift: Boolean, val difficulty: String?, val liftType: String?, val segmentIds: Set<Long>) {
    companion object {
        fun groups(pistes: List<Piste>): List<PisteGroup> {
            val byKey = LinkedHashMap<String, Pair<Piste, MutableSet<Long>>>()
            for (p in pistes) {
                val name = p.name ?: continue
                val key = "${p.kind}|${p.difficulty ?: ""}|$name"
                byKey.getOrPut(key) { p to mutableSetOf() }.second.add(p.id)
            }
            return byKey.map { (k, v) -> PisteGroup(k, v.first.name ?: "", v.first.isLift, v.first.difficulty, v.first.liftType, v.second) }
        }
    }
}

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val pistes: List<Piste>) : LoadState
    data object Unavailable : LoadState
}

fun liftIcon(type: String?): ImageVector = when (type) {
    "chair_lift", "gondola", "cable_car", "mixed_lift" -> Icons.Filled.Stairs
    else -> Icons.Filled.Stairs
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResortDetailDialog(resort: SkiResort, onClose: () -> Unit, onRecord: () -> Unit) {
    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var selected by remember { mutableStateOf<PisteGroup?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(resort.coordinate, 13f) }
    val pistes = (state as? LoadState.Loaded)?.pistes.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(reload) {
        state = LoadState.Loading
        // Huge linked areas (Dolomiti Superski spans ~100 km) load around their centre instead.
        val box = resort.box
        val all = if (box != null && box.size == 4 && box[2] - box[0] < 0.7 && box[3] - box[1] < 0.7) PisteMapStore.pistesInBox(box)
                  else PisteMapStore.pistesAround(resort.coordinate, 6000.0)
        if (all == null) { state = LoadState.Unavailable; return@LaunchedEffect }
        // Keep this resort's own pistes / lifts (tiles include neighbours); untagged lines count when close.
        val mine = withContext(Dispatchers.Default) {
            all.filter { p ->
                p.areas?.contains(resort.id) ?: p.coordinates.any { resort.distanceTo(it.latitude, it.longitude) < 3000 }
            }
        }
        state = LoadState.Loaded(mine)
    }

    fun fit(group: PisteGroup?) {
        val pts = group?.let { g -> pistes.filter { it.id in g.segmentIds }.flatMap { it.coordinates } } ?: pistes.flatMap { it.coordinates }
        val b = boundsOf(pts) ?: return
        scope.launch { runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(b, if (group == null) 60 else 140), 600) } }
    }

    /** Focus the map on a run / lift (tap it again to show the whole resort). */
    fun select(group: PisteGroup?) {
        val next = if (group == selected) null else group
        selected = next
        fit(next)
    }

    FullScreenDialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().trackstarBackground()) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = LocalBarInsets.current.top + 72.dp, bottom = LocalBarInsets.current.bottom + 110.dp)) {
                item {
                    Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(24.dp))) {
                        ResortMap(resort, pistes, camera, selected, onMapLoaded = { fit(selected) }, onPisteClick = { p ->
                            PisteGroup.groups(pistes).firstOrNull { p.id in it.segmentIds }?.let { select(it) }
                        })
                        if (pistes.isNotEmpty()) CornerButton(Icons.Filled.OpenInFull, tr("Full screen map"), size = 38.dp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) { fullScreen = true }
                        selected?.let { g -> SelectedPisteChip(g, resort.convention, Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 56.dp, end = 56.dp)) { select(null) } }
                        LoadOverlay(state, Modifier.align(Alignment.Center)) { reload++ }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.Text(resort.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        androidx.compose.material3.Text("${resort.flag} ${resort.place}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
                        Row {
                            Stat("${resort.runCount}", tr("Runs"), Modifier.weight(1f))
                            Stat(String.format("%.0f", resort.kilometers), "km", Modifier.weight(1f))
                            Stat("${resort.lifts}", tr("Lifts"), Modifier.weight(1f))
                            val lo = resort.minElevation; val hi = resort.maxElevation
                            if (lo != null && hi != null) Stat("${hi - lo}", tr("m vertical"), Modifier.weight(1f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            resort.difficultySummary.forEach { (d, n) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    DifficultyDot(d, resort.convention, 11.dp)
                                    androidx.compose.material3.Text("${Piste.label(if (d == "other") null else d, resort.convention)} · $n", fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(start = 5.dp))
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(14.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.7f)))
                                Text("Lifts", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.padding(start = 5.dp))
                            }
                        }
                    }
                }
                if (pistes.isNotEmpty()) item {
                    RunsByColour(pistes, resort.convention, selected) { g ->
                        select(g)
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 20.dp, vertical = 14.dp)) {
                RoundButton(Icons.Filled.ExpandMore, tr("Close"), onClose)
            }
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalBarInsets.current.bottom).padding(16.dp)
                    .height(54.dp).clip(RoundedCornerShape(50)).background(TrackstarAccent).clickable(onClick = onRecord)) {
                Icon(Icons.Filled.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Record here", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
        if (fullScreen) {
            ResortFullMap(resort, pistes, camera, selected, onSelect = { select(it) }, onClose = { fullScreen = false })
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        androidx.compose.material3.Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        androidx.compose.material3.Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
    }
}

@Composable
private fun LoadOverlay(state: LoadState, modifier: Modifier, onRetry: () -> Unit) {
    when {
        state is LoadState.Loading -> Pill(tr("Loading piste map…"), modifier)
        state is LoadState.Unavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.padding(24.dp).clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp)) {
            Text("Piste map not available right now", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("OpenStreetMap is busy — try again in a few minutes.", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("Try again", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.Black,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onRetry).padding(horizontal = 16.dp, vertical = 8.dp))
        }
        state is LoadState.Loaded && state.pistes.isEmpty() -> Pill(tr("No pistes mapped here yet"), modifier)
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    androidx.compose.material3.Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** The resort's pistes and lifts on a dark map; the selected run / lift is highlighted, the rest fade. */
@Composable
private fun ResortMap(
    resort: SkiResort, pistes: List<Piste>, camera: CameraPositionState, selected: PisteGroup?,
    controlsBottom: Dp = 12.dp, onMapLoaded: () -> Unit, onPisteClick: (Piste) -> Unit,
) {
    val context = LocalContext.current
    val hasLocation = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
            properties = darkMapProperties.copy(isMyLocationEnabled = hasLocation),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            onMapLoaded = onMapLoaded,
        ) {
            PisteLines(pistes, width = 10f, convention = resort.convention, highlighted = selected?.segmentIds, onClick = onPisteClick)
        }
        OsmAttribution(Modifier.align(Alignment.BottomEnd))
        if (hasLocation) {
            CornerButton(Icons.Filled.MyLocation, tr("My location"), size = 38.dp,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = controlsBottom + 24.dp)) {
                runCatching {
                    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15f), 500) }
                    }
                }
            }
        }
    }
}

/** "● Jubilee Run  ✕" over the map while a run / lift is focused. */
@Composable
private fun SelectedPisteChip(g: PisteGroup, convention: String?, modifier: Modifier = Modifier, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.7f)).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)) {
        if (g.isLift) Icon(liftIcon(g.liftType), null, tint = Color.White, modifier = Modifier.size(12.dp)) else DifficultyDot(g.difficulty ?: "", convention)
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(g.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, tr("Show all"), tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

/** Named runs grouped by colour (easiest first), then the lifts — tap one to focus it on the map. */
@Composable
private fun RunsByColour(pistes: List<Piste>, convention: String?, selected: PisteGroup?, onTap: (PisteGroup) -> Unit) {
    val order = listOf("novice", "easy", "intermediate", "advanced", "expert", "freeride", "extreme")
    val all = remember(pistes) { PisteGroup.groups(pistes) }
    val runs = all.filter { !it.isLift }
    val sections = (order + "").mapNotNull { d ->
        val list = runs.filter { (it.difficulty ?: "") == d || (d == "" && (it.difficulty ?: "") !in order) }.sortedBy { it.name.lowercase() }
        if (list.isEmpty()) null else d to list
    }
    val lifts = all.filter { it.isLift }.sortedBy { it.name.lowercase() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
        sections.forEach { (d, list) ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    DifficultyDot(d, convention, 11.dp)
                    androidx.compose.material3.Text(Piste.label(d.ifEmpty { null }, convention), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(start = 6.dp))
                }
                list.forEach { RunRow(it, it == selected, onTap) }
            }
        }
        if (lifts.isNotEmpty()) Column {
            Text("Lifts", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
            lifts.forEach { RunRow(it, it == selected, onTap) }
        }
    }
}

@Composable
private fun RunRow(g: PisteGroup, isSelected: Boolean, onTap: (PisteGroup) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) TrackstarAccent.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onTap(g) }.padding(vertical = 9.dp, horizontal = 10.dp)) {
        if (g.isLift) { Icon(liftIcon(g.liftType), null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(10.dp)) }
        androidx.compose.material3.Text(g.name, fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White,
            modifier = Modifier.weight(1f))
        Icon(if (isSelected) Icons.Filled.GpsFixed else Icons.AutoMirrored.Filled.ArrowForwardIos, null,
            tint = if (isSelected) TrackstarAccent else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(if (isSelected) 16.dp else 12.dp))
    }
}

/** Full-screen piste map, sharing the selection with the resort page; tap a run in the bottom strip to focus it. */
@Composable
private fun ResortFullMap(resort: SkiResort, pistes: List<Piste>, camera: CameraPositionState, selected: PisteGroup?,
                          onSelect: (PisteGroup?) -> Unit, onClose: () -> Unit) {
    val groups = remember(pistes) {
        PisteGroup.groups(pistes).sortedWith(compareBy<PisteGroup> { it.isLift }.thenBy { it.name.lowercase() })
    }
    FullScreenDialog(onDismissRequest = onClose) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ResortMap(resort, pistes, camera, selected, controlsBottom = LocalBarInsets.current.bottom + 70.dp, onMapLoaded = {},
                onPisteClick = { p -> groups.firstOrNull { p.id in it.segmentIds }?.let(onSelect) })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 16.dp, vertical = 8.dp)) {
                RoundButton(Icons.Filled.CloseFullscreen, tr("Close"), onClose)
                if (selected != null) SelectedPisteChip(selected, resort.convention, Modifier.weight(1f, fill = false)) { onSelect(null) }
                else androidx.compose.material3.Text(resort.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalBarInsets.current.bottom + 24.dp)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                groups.forEach { g ->
                    val isSel = g == selected
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSel) Color.White else Color.Black.copy(alpha = 0.65f))
                            .clickable { onSelect(g) }.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        if (g.isLift) Icon(liftIcon(g.liftType), null, tint = if (isSel) Color.Black else Color.White, modifier = Modifier.size(11.dp))
                        else DifficultyDot(g.difficulty ?: "", resort.convention, 9.dp)
                        Spacer(Modifier.width(6.dp))
                        androidx.compose.material3.Text(g.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isSel) Color.Black else Color.White, maxLines = 1)
                    }
                }
            }
        }
    }
}

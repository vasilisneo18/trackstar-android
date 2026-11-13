package com.vasilisneo.trackstar.ui.screens.main.activity

// GPS activities on Android: the Home card, the full-screen recorder (live Google Map, stats,
// start / pause / finish), the summary / detail screen (route map, stats, splits or slope runs) and
// the day's activities carousel. Mirrors iOS's UI/View/MainApp/Activity/ActivityViews.swift.

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.GroupOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Snowboarding
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.health.connect.client.PermissionController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline as MapPolyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.vasilisneo.trackstar.data.activity.ActivityFormat
import com.vasilisneo.trackstar.data.activity.ActivityRecorder
import com.vasilisneo.trackstar.data.activity.ActivityStore
import com.vasilisneo.trackstar.data.activity.ActivityType
import com.vasilisneo.trackstar.data.activity.SlopeAnalyzer
import com.vasilisneo.trackstar.data.activity.activityType
import com.vasilisneo.trackstar.data.activity.coordinates
import com.vasilisneo.trackstar.data.activity.displayTitle
import com.vasilisneo.trackstar.data.activity.paceSecondsPerKm
import com.vasilisneo.trackstar.data.activity.speedKmh
import com.vasilisneo.trackstar.data.api.RecordedActivity
import com.vasilisneo.trackstar.data.health.HealthConnectManager
import com.vasilisneo.trackstar.ui.i18n.Text
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.i18n.trf
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CardFill = Color.White.copy(alpha = 0.06f)

/** System bar sizes measured in the host screen — a Dialog window doesn't receive them reliably. */
data class BarInsets(val top: androidx.compose.ui.unit.Dp, val bottom: androidx.compose.ui.unit.Dp)
val LocalBarInsets = androidx.compose.runtime.staticCompositionLocalOf { BarInsets(24.dp, 24.dp) }

/** Full-screen cover (the recorder, the full-screen piste map) — edge to edge, under the status and
 *  navigation bars, with content kept clear of them via LocalBarInsets. iOS: fullScreenCover. */
@Composable
fun FullScreenDialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val bars = WindowInsets.systemBars
    val density = androidx.compose.ui.platform.LocalDensity.current
    val insets = BarInsets(
        top = with(density) { bars.getTop(this).toDp() },
        bottom = with(density) { bars.getBottom(this).toDp() },
    )
    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Some Android versions still place a dialog window below the status bar; let it draw
        // under both bars so the cover really is full screen (no dark band at the top).
        val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            window?.setFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBarInsets provides insets) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** iOS-style page sheet (the same look as Home's Add widget gallery): slides up, leaves a sliver of
 *  the screen above it, rounded top corners, swipe down to dismiss. iOS: .sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottom = with(density) { WindowInsets.systemBars.getBottom(this).toDp() }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(0.93f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalBarInsets provides BarInsets(12.dp, bottom)) { content() }
            // Grabber, like iOS sheets.
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(36.dp).height(5.dp)
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)))
        }
    }
}

fun ActivityType.icon(): ImageVector = when (this) {
    ActivityType.Run -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityType.Walk -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.Hike -> Icons.Filled.Hiking
    ActivityType.Ride -> Icons.AutoMirrored.Filled.DirectionsBike
    ActivityType.Ski -> Icons.Filled.DownhillSkiing
    ActivityType.Snowboard -> Icons.Filled.Snowboarding
}

/** A muted dark style so the route / pistes stand out (Google Maps JSON style). */
const val DARK_MAP_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1d2533"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#8a93a6"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#1d2533"}]},
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2e3a4f"}]},
 {"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0f1724"}]},
 {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#212b3b"}]}
]"""

val darkMapProperties: MapProperties
    get() = MapProperties(mapStyleOptions = MapStyleOptions(DARK_MAP_STYLE))

/** Hikes: Google's terrain map (contour lines, shaded relief) instead of the flat dark style. */
val terrainMapProperties: MapProperties
    get() = MapProperties(mapType = com.google.maps.android.compose.MapType.TERRAIN)

/** Marked hiking trails as thin lines in their marking colour, inside a `GoogleMap { }`. */
@Composable
@com.google.maps.android.compose.GoogleMapComposable
fun TrailLines(trails: List<com.vasilisneo.trackstar.data.activity.Trail>, width: Float = 7f) {
    for (t in trails) for (line in t.lines) {
        if (line.isRoad) RoadLine(line.coordinates, width)
        else MapPolyline(points = line.coordinates, color = t.color.copy(alpha = 0.85f), width = width,
            jointType = com.google.android.gms.maps.model.JointType.ROUND,
            startCap = com.google.android.gms.maps.model.RoundCap(), endCap = com.google.android.gms.maps.model.RoundCap())
    }
}

/** A trail section along a main road with traffic: orange dashes, never the trail colour. */
@Composable
@com.google.maps.android.compose.GoogleMapComposable
fun RoadLine(points: List<LatLng>, width: Float, zIndex: Float = 0f) {
    MapPolyline(points = points, color = Color(0xFFFF9500), width = width, zIndex = zIndex,
        pattern = listOf(com.google.android.gms.maps.model.Dash(width * 2), com.google.android.gms.maps.model.Gap(width * 1.5f)))
}

fun boundsOf(points: List<LatLng>): LatLngBounds? {
    if (points.isEmpty()) return null
    val b = LatLngBounds.builder()
    points.forEach { b.include(it) }
    return b.build()
}

// MARK: - Home card

/** Full-width card: map backdrop, "Activity" + km this week; tap to record. Groups in the corner. */
@Composable
fun ActivityHomeCard(date: LocalDate, onRecord: () -> Unit, onGroups: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val all by ActivityStore.activities.collectAsState()
    LaunchedEffect(Unit) { ActivityStore.refresh() }
    val center = all.firstOrNull()?.coordinates?.firstOrNull() ?: LatLng(35.17, 33.36)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(164.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardFill)
            .clickable(onClick = onRecord)
            .padding(10.dp)
    ) {
        StaticMap(center = center, zoom = 13f, onClick = onRecord, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(
            Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Black.copy(alpha = 0.15f)))))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = TrackstarAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Activity", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(trf("%s km this week", ActivityFormat.distance(ActivityStore.weekDistance(date, all))),
                    fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
        if (onGroups != null) {
            CornerButton(Icons.Filled.Groups, tr("Groups"), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp), onClick = onGroups)
        }
    }
}

@Composable
fun CornerButton(icon: ImageVector, label: String, size: androidx.compose.ui.unit.Dp = 36.dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

/** A non-interactive lite-mode map (cheap bitmap), for card backdrops. Taps go to `onClick` — a lite
 *  map with no click listener opens the Google Maps app instead. */
@Composable
fun StaticMap(center: LatLng, zoom: Float, onClick: () -> Unit = {}, modifier: Modifier = Modifier) {
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, zoom) }
    LaunchedEffect(center) { camera.position = CameraPosition.fromLatLngZoom(center, zoom) }
    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        properties = darkMapProperties,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, compassEnabled = false,
            myLocationButtonEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false),
        onMapClick = { onClick() },
        onMapLongClick = { onClick() },
    )
    // A Compose layer on top takes the touches, so swipes scroll the page instead of being eaten by
    // the map view, and taps still open the recorder.
    Box(modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null, onClick = onClick))
}

/** The selected day's activities, swipeable (pager) with page dots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityCarouselCard(activities: List<RecordedActivity>, onOpen: (RecordedActivity) -> Unit, modifier: Modifier = Modifier) {
    if (activities.isEmpty()) return
    val pager = rememberPagerState { activities.size }
    Column(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardFill).padding(vertical = 14.dp)) {
        Text(if (activities.size == 1) "Activity" else "Activities", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.55f), modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pager) { page ->
            val a = activities[page]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(a) }.padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center) {
                    RouteSketch(a.coordinates, Modifier.fillMaxSize().padding(10.dp))
                    Box(modifier = Modifier.align(Alignment.BottomEnd).size(20.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center) {
                        Icon(a.activityType.icon(), contentDescription = null, tint = Color.Black, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.displayTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
                    Text("${ActivityFormat.distance(a.distanceMeters)} km · ${ActivityFormat.duration(a.movingSeconds)}",
                        fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Text(ActivityFormat.headline(a), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        if (activities.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                activities.indices.forEach { i ->
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (pager.currentPage == i) 0.6f else 0.2f)))
                }
            }
        }
    }
}

/** The route as a line on a tile (no map tiles — cheap for lists). */
@Composable
fun RouteSketch(points: List<LatLng>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val lats = points.map { it.latitude }; val lngs = points.map { it.longitude }
        val minLat = lats.min(); val maxLat = lats.max(); val minLng = lngs.min(); val maxLng = lngs.max()
        val w = (maxLng - minLng).coerceAtLeast(1e-6); val h = (maxLat - minLat).coerceAtLeast(1e-6)
        val scale = minOf(size.width / w, size.height / h).toFloat()
        val ox = (size.width - (w * scale).toFloat()) / 2; val oy = (size.height - (h * scale).toFloat()) / 2
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, p ->
            val x = ox + ((p.longitude - minLng) * scale).toFloat()
            val y = oy + ((maxLat - p.latitude) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, TrackstarAccent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

// MARK: - Recorder

/** Full-screen recorder. Asks for location (and notifications) first; the summary replaces it in place. */
@Composable
fun ActivityRecorderDialog(initialType: ActivityType, onClose: () -> Unit, guide: com.vasilisneo.trackstar.data.activity.Trail? = null) {
    val context = LocalContext.current
    val snapshot by ActivityRecorder.snapshot.collectAsState()
    var finished by remember { mutableStateOf<RecordedActivity?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("trackstar_prefs", android.content.Context.MODE_PRIVATE) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) ActivityRecorder.prepare(context, initialType)
        else permissionDenied = true
    }
    LaunchedEffect(Unit) {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (fineGranted) ActivityRecorder.prepare(context, initialType) else permissionLauncher.launch(needed.toTypedArray())
    }
    DisposableEffect(Unit) {
        onDispose { if (ActivityRecorder.snapshot.value.state == ActivityRecorder.State.Idle) ActivityRecorder.cancel() }
    }

    FullScreenDialog(onDismissRequest = {}) {
        val done = finished
        if (done != null) {
            val health = remember { HealthConnectManager(context) }
            val scope = rememberCoroutineScope()
            val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { _ ->
                scope.launch { health.saveWorkout(done) }
            }
            ActivityDetailContent(
                activity = done, isNew = true,
                onSave = {
                    ActivityStore.save(done)
                    if (health.isAvailable) scope.launch {
                        if (health.hasWorkoutPermission()) health.saveWorkout(done) else hcLauncher.launch(health.workoutPermissions)
                    }
                    ActivityRecorder.cancel()
                    onClose()
                },
                onDiscard = { ActivityRecorder.cancel(); onClose() },
            )
        } else {
            RecorderContent(
                snapshot = snapshot, initialType = initialType, permissionDenied = permissionDenied, guide = guide,
                onClose = {
                    if (snapshot.state == ActivityRecorder.State.Idle) { ActivityRecorder.cancel(); onClose() } else confirmDiscard = true
                },
                onStart = {
                    prefs.edit().putString(if (snapshot.type.isSlope) "slopesLastType" else "activityLastType", snapshot.type.raw).apply()
                    ActivityRecorder.start()
                },
                onFinish = { finished = ActivityRecorder.finish() ?: run { onClose(); null } },
            )
        }
        if (confirmDiscard) {
            AlertDialog(
                onDismissRequest = { confirmDiscard = false },
                containerColor = Color(0xFF1A1A26),
                title = { Text("Discard this activity?", color = Color.White) },
                text = { Text("Your route and stats will be lost.", color = Color.White.copy(alpha = 0.7f)) },
                confirmButton = { TextButton(onClick = { confirmDiscard = false; ActivityRecorder.cancel(); onClose() }) { Text("Discard", color = Color(0xFFFF453A)) } },
                dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep Recording", color = Color.White) } },
            )
        }
    }
}

@Composable
private fun RecorderContent(
    snapshot: ActivityRecorder.Snapshot, initialType: ActivityType, permissionDenied: Boolean,
    guide: com.vasilisneo.trackstar.data.activity.Trail? = null,
    onClose: () -> Unit, onStart: () -> Unit, onFinish: () -> Unit,
) {
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(35.17, 33.36), 15f) }
    var follow by remember { mutableStateOf(true) }
    // Live sharing with my groups (remembered across recordings, like iOS's liveShareEnabled).
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trackstar_prefs", android.content.Context.MODE_PRIVATE) }
    var shareLive by remember { mutableStateOf(prefs.getBoolean("liveShareEnabled", false)) }
    val groups by com.vasilisneo.trackstar.data.activity.GroupStore.groups.collectAsState()
    val liveMembers by com.vasilisneo.trackstar.data.activity.GroupStore.liveMembers.collectAsState()
    val memberNames by com.vasilisneo.trackstar.data.activity.GroupStore.memberNames.collectAsState()
    LaunchedEffect(Unit) { com.vasilisneo.trackstar.data.activity.GroupStore.refresh() }
    LiveShareLoop(shareLive && groups.isNotEmpty(), snapshot.state)
    var pistes by remember { mutableStateOf<List<com.vasilisneo.trackstar.data.activity.Piste>>(emptyList()) }
    val hasFix = snapshot.lastLocation != null
    var trails by remember { mutableStateOf<List<com.vasilisneo.trackstar.data.activity.Trail>>(emptyList()) }
    val isHike = snapshot.type == ActivityType.Hike
    LaunchedEffect(snapshot.type.isSlope, isHike, hasFix) {
        val here = snapshot.lastLocation
        if (snapshot.type.isSlope && here != null && pistes.isEmpty()) {
            pistes = com.vasilisneo.trackstar.data.activity.PisteMapStore.pistesAround(here) ?: emptyList()
        }
        if (isHike && here != null && trails.isEmpty()) {
            trails = com.vasilisneo.trackstar.data.activity.TrailMapStore.trailsAround(here)
        }
    }
    LaunchedEffect(snapshot.lastLocation, follow) {
        val here = snapshot.lastLocation ?: return@LaunchedEffect
        if (follow) camera.animate(CameraUpdateFactory.newLatLngZoom(here, maxOf(camera.position.zoom, 15f)), 600)
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = (if (isHike) terrainMapProperties else darkMapProperties)
                .copy(isMyLocationEnabled = snapshot.lastLocation != null && !permissionDenied),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapClick = { follow = false },
        ) {
            val showPistes = snapshot.type.isSlope && pistes.isNotEmpty()
            if (showPistes) PisteLines(pistes)
            if (isHike) TrailLines(trails)
            // The trail picked in the trail browser, on top with a white casing.
            guide?.lines?.forEach { line ->
                MapPolyline(points = line.coordinates, color = Color.White, width = 16f, zIndex = 1f)
                if (line.isRoad) RoadLine(line.coordinates, 10f, zIndex = 2f)
                else MapPolyline(points = line.coordinates, color = guide.color, width = 10f, zIndex = 2f)
            }
            if (shareLive) LiveMemberMarkers(liveMembers,
                initials = { id -> (memberNames[id] ?: "?").split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase() },
                name = { id -> memberNames[id] ?: "" })
            if (snapshot.route.size > 1) MapPolyline(points = snapshot.route,
                color = if (showPistes) Color.White.copy(alpha = 0.95f) else TrackstarAccent, width = if (showPistes) 7f else 14f)
        }
        if ((snapshot.type.isSlope && pistes.isNotEmpty()) || (isHike && trails.isNotEmpty())) OsmAttribution(Modifier.align(Alignment.CenterEnd))
        // Top bar: close, GPS badge, recenter.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 16.dp, vertical = 10.dp)) {
            RoundButton(Icons.Filled.Close, tr("Close"), onClose)
            Spacer(Modifier.weight(1f))
            val (dot, label) = when {
                permissionDenied -> Color(0xFFFF453A) to "Location off"
                snapshot.hasGoodSignal -> Color(0xFF30D158) to "GPS ready"
                else -> Color(0xFFFF9F0A) to "Finding GPS…"
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            if (groups.isNotEmpty()) {
                // Share live with my groups: green while on.
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable {
                    shareLive = !shareLive
                    prefs.edit().putBoolean("liveShareEnabled", shareLive).apply()
                }, contentAlignment = Alignment.Center) {
                    Icon(if (shareLive) Icons.Filled.Groups else Icons.Filled.GroupOff,
                        contentDescription = tr(if (shareLive) "Sharing live with your groups" else "Not sharing live"),
                        tint = if (shareLive) Color(0xFF30D158) else Color.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
            }
            RoundButton(Icons.Filled.MyLocation, tr("My location")) { follow = true }
        }
        // Bottom panel.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF12121C))
                .padding(bottom = LocalBarInsets.current.bottom)
                .padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 16.dp)
        ) {
            if (snapshot.state == ActivityRecorder.State.Idle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    (if (initialType.isSlope) ActivityType.slopes else ActivityType.everyday).forEach { t ->
                        val selected = snapshot.type == t
                        // Icon over label so four types (Run · Walk · Hike · Ride) fit, in Greek too.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .background(if (selected) TrackstarAccent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f))
                                .clickable { ActivityRecorder.setType(t) }.padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Icon(t.icon(), null, tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f), modifier = Modifier.size(17.dp))
                            Text(t.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                                maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (snapshot.type.isSlope) SlopeLiveStats(snapshot) else EverydayLiveStats(snapshot)
            Controls(snapshot, permissionDenied, onStart, onFinish)
        }
    }
}

@Composable
private fun EverydayLiveStats(s: ActivityRecorder.Snapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ActivityFormat.distance(s.distanceMeters), fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Kilometers", fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Metric(ActivityFormat.duration(s.movingSeconds), "Time", Modifier.weight(1f))
        if (s.type == ActivityType.Hike) {
            // On a hike the climb matters more than pace.
            Metric("${s.elevationGainMeters.toInt()} m", "Climbed", Modifier.weight(1f))
            Metric(s.currentAltitude?.let { "${it.toInt()} m" } ?: "–", "Altitude", Modifier.weight(1f))
        } else {
            if (s.type.usesPace) Metric(ActivityFormat.pace(s.currentPaceSecondsPerKm), "Pace /km", Modifier.weight(1f))
            else Metric(ActivityFormat.speed(s.currentSpeedKmh), "km/h", Modifier.weight(1f))
            Metric("${s.elevationGainMeters.toInt()} m", "Elevation", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SlopeLiveStats(s: ActivityRecorder.Snapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${if (s.state == ActivityRecorder.State.Recording) s.currentSpeedKmh.toInt() else 0}", fontSize = 60.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("km/h", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(bottom = 12.dp))
        }
        if (s.state != ActivityRecorder.State.Idle) {
            val (label, color) = when {
                s.state == ActivityRecorder.State.Paused -> "Paused" to Color.White.copy(alpha = 0.5f)
                s.slopePhase == SlopeAnalyzer.Phase.Skiing -> (if (s.type == ActivityType.Snowboard) "Riding" else "Skiing") to Color(0xFF30D158)
                s.slopePhase == SlopeAnalyzer.Phase.OnLift -> "On lift" to Color(0xFF64D2FF)
                else -> "Waiting" to Color(0xFFFF9F0A)
            }
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Metric(ActivityFormat.duration(s.movingSeconds), "Time", Modifier.weight(1f))
        Metric("${s.slopeRuns}", "Runs", Modifier.weight(1f))
        Metric("${s.slopeVerticalMeters.toInt()} m", "Vertical", Modifier.weight(1f))
        Metric("${s.slopeTopSpeedKmh.toInt()}", "Top km/h", Modifier.weight(1f))
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(value, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
    }
}

@Composable
private fun Controls(s: ActivityRecorder.Snapshot, permissionDenied: Boolean, onStart: () -> Unit, onFinish: () -> Unit) {
    when (s.state) {
        ActivityRecorder.State.Idle -> Box(
            modifier = Modifier.size(92.dp).clip(CircleShape)
                .background(if (permissionDenied) Color.White.copy(alpha = 0.15f) else TrackstarAccent)
                .clickable(enabled = !permissionDenied, onClick = onStart),
            contentAlignment = Alignment.Center
        ) { Text("Start", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        ActivityRecorder.State.Recording -> Box(
            modifier = Modifier.size(92.dp).clip(CircleShape).background(Color.White).clickable { ActivityRecorder.pause() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Pause, contentDescription = tr("Pause"), tint = Color.Black, modifier = Modifier.size(34.dp)) }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            CircleAction(Icons.Filled.PlayArrow, "Resume", TrackstarAccent) { ActivityRecorder.resume() }
            CircleAction(Icons.Filled.Stop, "Finish", Color(0xFFFF453A).copy(alpha = 0.85f), onFinish)
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(78.dp).clip(CircleShape).background(color).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = tr(label), tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

// MARK: - Detail / summary

/** A saved activity's detail, as a full-screen dialog. */
@Composable
fun ActivityDetailDialog(activity: RecordedActivity, onClose: () -> Unit) {
    AppSheet(onDismissRequest = onClose) {
        ActivityDetailContent(activity = activity, isNew = false, onDelete = { ActivityStore.delete(activity); onClose() }, onClose = onClose)
    }
}

@Composable
fun ActivityDetailContent(
    activity: RecordedActivity, isNew: Boolean,
    onSave: (() -> Unit)? = null, onDiscard: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null, onClose: (() -> Unit)? = null,
    mapOverlay: (@Composable (CameraPositionState) -> Unit)? = null,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val route = remember(activity.clientId) { activity.coordinates }
    var pistes by remember { mutableStateOf<List<com.vasilisneo.trackstar.data.activity.Piste>>(emptyList()) }
    var runPistes by remember { mutableStateOf<Map<Int, com.vasilisneo.trackstar.data.activity.Piste>>(emptyMap()) }
    var convention by remember { mutableStateOf<String?>(null) }
    var trails by remember { mutableStateOf<List<com.vasilisneo.trackstar.data.activity.Trail>>(emptyList()) }
    val isHike = activity.activityType == ActivityType.Hike
    val context = LocalContext.current
    LaunchedEffect(activity.clientId) {
        if (isHike) { trails = com.vasilisneo.trackstar.data.activity.TrailMapStore.trailsCovering(route); return@LaunchedEffect }
        val slopes = activity.slopes ?: return@LaunchedEffect
        if (route.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.vasilisneo.trackstar.data.activity.SkiResortCatalog.load(context)
            convention = com.vasilisneo.trackstar.data.activity.SkiResortCatalog.resortNear(route.first())?.convention
        }
        val loaded = com.vasilisneo.trackstar.data.activity.PisteMapStore.pistesCovering(route)
        runPistes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            buildMap {
                slopes.runs.forEachIndexed { i, run ->
                    val lo = maxOf(0, run.startIndex); val hi = minOf(route.size - 1, run.endIndex)
                    if (hi > lo) com.vasilisneo.trackstar.data.activity.PisteMatcher.piste(route.subList(lo, hi + 1), loaded)?.let { put(i, it) }
                }
            }
        }
        pistes = loaded
    }
    val camera = rememberCameraPositionState()
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 76.dp, bottom = if (isNew) 120.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.padding(top = LocalBarInsets.current.top)
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp))) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(), cameraPositionState = camera,
                        properties = if (isHike) terrainMapProperties else darkMapProperties,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                        onMapLoaded = {
                            boundsOf(route)?.let { camera.move(CameraUpdateFactory.newLatLngBounds(it, 80)) }
                        },
                    ) {
                        if (pistes.isNotEmpty()) PisteLines(pistes, width = 14f, convention = convention)
                        if (trails.isNotEmpty()) TrailLines(trails)
                        // White casing so a hike's route stands out over terrain and trails.
                        if (isHike && route.size > 1) MapPolyline(points = route, color = Color.White, width = 18f, zIndex = 1f)
                        if (route.size > 1) MapPolyline(points = route, color = if (pistes.isEmpty()) TrackstarAccent else Color.White.copy(alpha = 0.95f),
                            width = if (pistes.isEmpty()) 12f else 6f, zIndex = 2f)
                    }
                    if (pistes.isNotEmpty() || trails.isNotEmpty()) OsmAttribution(Modifier.align(Alignment.BottomEnd))
                    mapOverlay?.invoke(camera)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(activity.activityType.icon(), null, tint = TrackstarAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(activity.displayTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    val whenText = Instant.ofEpochMilli(activity.startedAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(com.vasilisneo.trackstar.ui.i18n.I18n.locale))
                    Text(whenText, fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
            val slopes = activity.slopes
            if (slopes != null) {
                item {
                    TileGrid(listOf(
                        Triple("${slopes.runCount}", null, "Runs"),
                        Triple("${slopes.verticalMeters.toInt()}", "m", "Vertical"),
                        Triple("${slopes.topSpeedKmh.toInt()}", "km/h", "Top speed"),
                        Triple(ActivityFormat.distance(activity.distanceMeters), "km", "Downhill distance"),
                        Triple(ActivityFormat.duration(activity.movingSeconds), null, "Time"),
                        Triple(ActivityFormat.duration(slopes.liftSeconds), null, "On lifts"),
                    ))
                }
                if (slopes.runs.isNotEmpty()) item { SlopeRunsSection(activity, runPistes, convention) }
            } else if (activity.activityType == ActivityType.Hike) {
                // Hikes lead with the climb: up, down and the highest point, then pace.
                item {
                    TileGrid(buildList {
                        add(Triple(ActivityFormat.distance(activity.distanceMeters), "km", "Distance"))
                        add(Triple(ActivityFormat.duration(activity.movingSeconds), null, "Moving time"))
                        add(Triple("${activity.elevationGainMeters.toInt()}", "m", "Ascent"))
                        activity.elevationLossMeters?.let { add(Triple("${it.toInt()}", "m", "Descent")) }
                        activity.elevationProfile?.maxOfOrNull { it.a }?.let { add(Triple("${it.toInt()}", "m", "Highest point")) }
                        add(Triple(ActivityFormat.pace(activity.paceSecondsPerKm), "/km", "Avg pace"))
                    })
                }
                activity.elevationProfile?.takeIf { it.size >= 2 }?.let { profile ->
                    item { ElevationProfileCard(profile, activity.elevationGainMeters, activity.elevationLossMeters) }
                }
                if (activity.splits.orEmpty().isNotEmpty()) item { SplitsSection(activity) }
            } else {
                item {
                    TileGrid(listOf(
                        Triple(ActivityFormat.distance(activity.distanceMeters), "km", "Distance"),
                        Triple(ActivityFormat.duration(activity.movingSeconds), null, "Moving time"),
                        if (activity.activityType.usesPace) Triple(ActivityFormat.pace(activity.paceSecondsPerKm), "/km", "Avg pace")
                        else Triple(ActivityFormat.speed(activity.speedKmh), "km/h", "Avg speed"),
                        Triple("${activity.elevationGainMeters.toInt()}", "m", "Elevation gain"),
                    ))
                }
                activity.elevationProfile?.takeIf { it.size >= 2 }?.let { profile ->
                    item { ElevationProfileCard(profile, activity.elevationGainMeters, activity.elevationLossMeters) }
                }
                val splits = activity.splits.orEmpty()
                if (splits.isNotEmpty()) item { SplitsSection(activity) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = LocalBarInsets.current.top).padding(horizontal = 20.dp, vertical = 14.dp)) {
            if (isNew) RoundButton(Icons.Filled.DeleteOutline, tr("Discard")) { onDiscard?.invoke() }
            else RoundButton(Icons.Filled.ExpandMore, tr("Close")) { onClose?.invoke() }
            Spacer(Modifier.weight(1f))
            if (!isNew && onDelete != null) RoundButton(Icons.Filled.DeleteOutline, tr("Delete")) { confirmDelete = true }
        }
        if (isNew) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = LocalBarInsets.current.bottom).padding(16.dp)
                    .height(54.dp).clip(RoundedCornerShape(50)).background(TrackstarAccent).clickable { onSave?.invoke() },
                contentAlignment = Alignment.Center
            ) { Text("Save activity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White) }
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = Color(0xFF1A1A26),
                title = { Text("Delete this activity?", color = Color.White) },
                text = { Text("This activity will be permanently removed.", color = Color.White.copy(alpha = 0.7f)) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text("Delete", color = Color(0xFFFF453A)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Color.White) } },
            )
        }
    }
}

@Composable
private fun TileGrid(tiles: List<Triple<String, String?, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (value, unit, label) ->
                    Column(modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
                        Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            if (unit != null) {
                                Spacer(Modifier.width(3.dp))
                                Text(unit, fontSize = 13.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SplitsSection(activity: RecordedActivity) {
    val splits = activity.splits.orEmpty()
    val paces = splits.map { it.seconds / maxOf(it.distanceMeters / 1000, 0.01) }
    val fastest = paces.minOrNull() ?: 1.0
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
        Text("Splits", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        splits.forEachIndexed { i, split ->
            val pace = paces[i]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (split.distanceMeters >= 999) "${i + 1}" else String.format("%.2f", split.distanceMeters / 1000),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(36.dp))
                Text(if (activity.activityType.usesPace) ActivityFormat.pace(pace) else ActivityFormat.speed(3600 / pace),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.width(56.dp))
                Box(Modifier.weight(1f).height(8.dp)) {
                    Box(Modifier.fillMaxWidth((fastest / pace).toFloat().coerceIn(0.05f, 1f)).height(8.dp).clip(RoundedCornerShape(50))
                        .background(TrackstarAccent.copy(alpha = if (pace == fastest) 1f else 0.55f)))
                }
            }
        }
    }
}

@Composable
private fun SlopeRunsSection(activity: RecordedActivity, runPistes: Map<Int, com.vasilisneo.trackstar.data.activity.Piste>, convention: String?) {
    val runs = activity.slopes?.runs.orEmpty()
    val best = runs.maxOfOrNull { it.topSpeedKmh } ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
        Text("Runs", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
        runs.forEachIndexed { i, run ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(if (run.topSpeedKmh == best) TrackstarAccent else Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    runPistes[i]?.let { piste ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DifficultyDot(piste.difficulty ?: "", convention, 9.dp)
                            Spacer(Modifier.width(6.dp))
                            androidx.compose.material3.Text(piste.name ?: piste.difficultyLabel(convention), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                        }
                    }
                    Text("${ActivityFormat.distance(run.distanceMeters)} km · ↓ ${run.verticalMeters.toInt()} m", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("${ActivityFormat.duration(run.seconds)} · ${tr("avg")} ${run.avgSpeedKmh.toInt()} km/h", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${run.topSpeedKmh.toInt()}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("km/h", fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f))
                }
            }
        }
    }
}

/** While recording with sharing on: send my position + stats every 10 s, and fetch the groups' live
 *  members every other round. Clears my live position when sharing or the recording ends. */
@Composable
private fun LiveShareLoop(enabled: Boolean, state: ActivityRecorder.State) {
    val active = enabled && (state == ActivityRecorder.State.Recording || state == ActivityRecorder.State.Paused)
    val shared = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active) {
        if (!active) {
            if (shared.value) { shared.value = false; com.vasilisneo.trackstar.data.activity.GroupStore.stopLive() }
            return@LaunchedEffect
        }
        var round = 0
        var topSpeedSeen = 0.0
        while (true) {
            val s = ActivityRecorder.snapshot.value
            val loc = s.lastLocation; val started = s.startedAtMs
            if (s.state == ActivityRecorder.State.Recording && loc != null && started != null) {
                topSpeedSeen = maxOf(topSpeedSeen, s.currentSpeedKmh)
                com.vasilisneo.trackstar.data.activity.GroupStore.sendLive(com.vasilisneo.trackstar.data.api.LiveUpdate(
                    latitude = loc.latitude, longitude = loc.longitude, speedKmh = s.currentSpeedKmh,
                    activityType = s.type.raw,
                    phase = if (s.type.isSlope) when (s.slopePhase) {
                        SlopeAnalyzer.Phase.Skiing -> "skiing"; SlopeAnalyzer.Phase.OnLift -> "onLift"; else -> "waiting"
                    } else null,
                    startedAt = started, distanceMeters = s.distanceMeters, movingSeconds = s.movingSeconds,
                    topSpeedKmh = if (s.type.isSlope) s.slopeTopSpeedKmh else topSpeedSeen,
                    verticalMeters = s.slopeVerticalMeters, runs = s.slopeRuns, elevationGainMeters = s.elevationGainMeters,
                ))
                shared.value = true
            }
            if (round % 2 == 0) com.vasilisneo.trackstar.data.activity.GroupStore.refreshLiveMembers()
            round++
            kotlinx.coroutines.delay(10_000)
        }
    }
    // Leaving the recorder (finish / discard) also clears the live position.
    DisposableEffect(Unit) {
        onDispose {
            if (shared.value) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.vasilisneo.trackstar.data.activity.GroupStore.stopLive()
            }
        }
    }
}

// MARK: - Elevation profile

/** Altitude along the route (distance on x), with ascent / descent in the header. */
@Composable
fun ElevationProfileCard(profile: List<com.vasilisneo.trackstar.data.api.ElevationSample>, gain: Double, loss: Double?) {
    val lo0 = profile.minOf { it.a }; val hi0 = profile.maxOf { it.a }
    // Keep flat walks from looking like cliffs: at least a 40 m tall window.
    val pad = maxOf(40 - (hi0 - lo0), 0.0) / 2 + 5
    val lo = lo0 - pad; val hi = hi0 + pad
    val maxD = profile.last().d.coerceAtLeast(1.0)
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardFill).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Elevation", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            androidx.compose.material3.Text("↑ ${gain.toInt()} m", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
            if (loss != null) {
                Spacer(Modifier.width(10.dp))
                androidx.compose.material3.Text("↓ ${loss.toInt()} m", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.75f))
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val axisW = 52.dp.toPx(); val axisH = 18.dp.toPx()
            val w = size.width - axisW; val h = size.height - axisH
            fun x(d: Double) = (d / maxD * w).toFloat()
            fun y(a: Double) = (h - (a - lo) / (hi - lo) * h).toFloat()
            // Grid + altitude labels (3 steps).
            val step = niceStep((hi - lo) / 3)
            var g = kotlin.math.ceil(lo / step) * step
            while (g <= hi) {
                drawLine(Color.White.copy(alpha = 0.08f), androidx.compose.ui.geometry.Offset(0f, y(g)), androidx.compose.ui.geometry.Offset(w, y(g)), 1.dp.toPx())
                val t = textMeasurer.measure("${g.toInt()} m", labelStyle)
                drawText(t, topLeft = androidx.compose.ui.geometry.Offset(w + 6.dp.toPx(), y(g) - t.size.height / 2))
                g += step
            }
            // Distance labels.
            val kmStep = niceStep(maxD / 1000 / 3).coerceAtLeast(0.5)
            var k = 0.0
            while (k * 1000 <= maxD) {
                val t = textMeasurer.measure(if (k % 1.0 == 0.0) "${k.toInt()} km" else "$k km", labelStyle)
                drawText(t, topLeft = androidx.compose.ui.geometry.Offset((x(k * 1000) - if (k == 0.0) 0f else t.size.width / 2f).coerceAtMost(w - t.size.width), h + 4.dp.toPx()))
                k += kmStep
            }
            val line = androidx.compose.ui.graphics.Path()
            profile.forEachIndexed { i, p -> if (i == 0) line.moveTo(x(p.d), y(p.a)) else line.lineTo(x(p.d), y(p.a)) }
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line); lineTo(x(profile.last().d), h); lineTo(x(profile.first().d), h); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(TrackstarAccent.copy(alpha = 0.45f), TrackstarAccent.copy(alpha = 0.05f)), startY = 0f, endY = h))
            drawPath(line, TrackstarAccent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** 1, 2, 5 × 10ⁿ — round axis steps. */
private fun niceStep(raw: Double): Double {
    if (raw <= 0) return 1.0
    val mag = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(raw)))
    val f = raw / mag
    return mag * when { f <= 1 -> 1.0; f <= 2 -> 2.0; f <= 5 -> 5.0; else -> 10.0 }
}

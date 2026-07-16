package com.vasilisneo.trackstar.ui.screens.main.diet

// Ports iOS's DietView: a collapsing-header weekly diet tab — pinned day selector, a nutrition
// card (calorie ring + Protein/Carbs/Fat macro bars for consumed-vs-planned), and a list of meal
// cards you can tick off as consumed and expand to see foods. Add/edit-meal, the plan editor, and
// the AI planner arrive in later phases (the ✎/✨ buttons are wired to callbacks).

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.DietMeal
import com.vasilisneo.trackstar.data.api.FoodItem
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.components.ProfileAvatarButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.currentAppTheme
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardFill = Color.White.copy(alpha = 0.08f)
private val HeaderTint = Color(0xFF3B3B46)
private val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DietScreen(
    onProfileClick: () -> Unit = {},
    onOpenAiPlanner: () -> Unit = {},
    viewModel: DietViewModel = viewModel(),
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // collapse = how far the large title (item 0) has scrolled off, as a 0..1 fraction of its own
    // height (measured from layoutInfo, so it's exact and works no matter how short the day's content
    // is — unlike a discrete index check, which never trips when there isn't a full title's worth of
    // scroll room). The frost engages only in the last stretch (near-docked), so it never appears
    // while the day bar is still floating up; the day bar's frost extends upward to keep it seamless.
    val collapse by remember {
        derivedStateOf {
            val title = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }
                ?: return@derivedStateOf 1f
            val h = title.size.toFloat()
            if (h <= 0f) 0f else ((-title.offset).toFloat() / h).coerceIn(0f, 1f)
        }
    }
    val pinned = collapse > 0.9f
    // frost 0→1 as the day bar docks. The header fill is OPAQUE (not a translucent tint): the meal/
    // nutrition content scrolling under the pinned day bar must be hidden, and with no cross-platform
    // blur we can't rely on iOS-style material. The colour is the exact frosted look — HeaderTint at
    // 0.82 composited over the theme's top gradient — so a docked header looks identical to the tint,
    // just solid. Theme-derived, so it re-tints with the Appearance setting.
    val frostProgress by animateFloatAsState(targetValue = if (pinned) 1f else 0f, animationSpec = tween(200), label = "frost")
    val headerFill = HeaderTint.copy(alpha = 0.82f).compositeOver(currentAppTheme.gradientTop.compositeOver(Color.Black))
    val titleCollapse = collapse
    val meals = viewModel.activeMeals
    val canConsume = viewModel.canConsumeToday

    // Long-press "Edit" opens the single-meal sheet; the ✎ opens the bulk day editor.
    var editingMeal by remember { mutableStateOf<DietMeal?>(null) }
    var showPlanSheet by remember { mutableStateOf(false) }

    // Re-fetch when the AI planner (a separate route) applies a new plan and bumps the signal, so
    // it shows immediately on return. Keyed on the version, so it only fires on an actual change.
    val refreshVersion = DietRefreshSignal.version
    androidx.compose.runtime.LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.fetch()
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Text(
            "Trackstar", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.05f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = com.vasilisneo.trackstar.ui.components.tabWatermarkBottomPadding())
        )

        // iOS structure: a fixed nav bar ABOVE the scroll view. The day bar is a pinned section header
        // inside the list, so it docks directly below the nav bar (flush, no overlap). Both carry the
        // same frost, which only fades in once the day bar is pinned — so they read as one material.
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().background(headerFill.copy(alpha = frostProgress)).statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                    ProfileAvatarButton(initials = viewModel.userInitials, onClick = onProfileClick)
                    Spacer(modifier = Modifier.size(12.dp))
                    Text("Diet Plan", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.alpha(frostProgress))
                    Spacer(modifier = Modifier.weight(1f))
                    GlassCircleIconButton(onClick = onOpenAiPlanner, contentDescription = "AI planner", icon = Icons.Filled.AutoAwesome)
                    Spacer(modifier = Modifier.size(10.dp))
                    GlassCircleIconButton(onClick = { showPlanSheet = true }, contentDescription = "Edit plan", icon = Icons.Filled.Edit)
                }
            }

            // Disable the Android overscroll stretch: at the ends it drags the whole content layer
            // (including the pinned day bar) up under the nav bar. iOS's rubber-band keeps pinned
            // headers put; nulling the config gives the same "day bar stays docked" behavior.
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = com.vasilisneo.trackstar.ui.components.tabBarContentBottomPadding()),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp).alpha(1f - titleCollapse)) {
                        Text(headerDate(viewModel.currentDay), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                        Text("Diet Plan", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                // Pinned day selector — docks flush below the nav bar; frosts (with it) once near-
                // docked. drawBehind paints the frost far above the item's own bounds; the LazyColumn
                // clips it at its top edge (= nav bar bottom), so the last sliver of title scrolling
                // beneath is always covered — no raw-gradient gap during the final approach.
                stickyHeader {
                    Column(
                        modifier = Modifier.fillMaxWidth().drawBehind {
                            if (frostProgress > 0f) {
                                drawRect(
                                    color = headerFill.copy(alpha = frostProgress),
                                    topLeft = Offset(0f, -3000f),
                                    size = Size(size.width, size.height + 3000f),
                                )
                            }
                        }
                    ) {
                        DaySelector(viewModel.currentDay, hasMeals = { viewModel.hasMeals(it) }, onSelect = { viewModel.currentDay = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.08f * frostProgress)))
                    }
                }
                if (meals.isNotEmpty()) {
                    item {
                        NutritionCard(meals, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 28.dp))
                    }
                }
                item {
                    Text("Meals", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                }
                if (meals.isEmpty()) {
                    item { EmptyMeals() }
                } else {
                    items(meals, key = { it.id }) { meal ->
                        MealCard(
                            meal = meal,
                            canConsume = canConsume,
                            onToggle = { viewModel.toggleConsumed(meal.id) },
                            onEdit = { editingMeal = meal },
                            onDelete = { viewModel.removeMeal(meal.id) },
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                        )
                    }
                }
            }
            }
        }
    }

    // Long-press → Edit a single meal (type locked).
    editingMeal?.let { target ->
        AddMealSheet(
            existing = target,
            lockedType = MealType.from(target.type),
            onSave = { viewModel.addMeal(it); editingMeal = null },
            onDismiss = { editingMeal = null },
        )
    }

    // ✎ → bulk day editor. Saving replaces the whole day's meal list (iOS setMeals).
    if (showPlanSheet) {
        PlanDietSheet(
            initialMeals = meals,
            onSave = { viewModel.setMeals(it); showPlanSheet = false },
            onDismiss = { showPlanSheet = false },
        )
    }
}

// MARK: - Day selector

// Mirrors the weekly plan's WeeklyPlanTabBar / DayTabPill exactly: the active pill animates its
// width open to show the full day name, the closing pill contracts to a single-letter chip, and
// both animate in lock-step so the freed space transfers across. Only the active fill color
// differs from the plan — Diet uses the theme accent, matching iOS's DietView day selector.
@Composable
internal fun DaySelector(current: String, hasMeals: (String) -> Boolean, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 6.dp
        val inactiveW = 34.dp
        // 7 pills + 6 gaps; the one active pill fills whatever's left.
        val activeW = (maxWidth - inactiveW * 6 - gap * 6).coerceAtLeast(72.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
            dayNames.forEach { day ->
                DayTabPill(
                    day = day,
                    isActive = day == current,
                    hasMeals = hasMeals(day),
                    activeWidth = activeW,
                    inactiveWidth = inactiveW,
                    onClick = { onSelect(day) },
                )
            }
        }
    }
}

@Composable
private fun DayTabPill(day: String, isActive: Boolean, hasMeals: Boolean, activeWidth: Dp, inactiveWidth: Dp, onClick: () -> Unit) {
    val accent = TrackstarAccent
    val spec = tween<Dp>(durationMillis = 280, easing = FastOutSlowInEasing)
    val width by animateDpAsState(targetValue = if (isActive) activeWidth else inactiveWidth, animationSpec = spec, label = "dayTabWidth")
    val bg by animateColorAsState(
        targetValue = if (isActive) accent else Color.White.copy(alpha = 0.1f),
        animationSpec = tween(280, easing = FastOutSlowInEasing), label = "dayTabBg"
    )
    Box {
        Box(
            modifier = Modifier.width(width).height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isActive) day else day.take(1),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                color = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
            )
        }
        if (hasMeals && !isActive) {
            Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp).size(6.dp).clip(CircleShape).background(dayColor(day)))
        }
    }
}

// MARK: - Nutrition card

@Composable
internal fun NutritionCard(meals: List<DietMeal>, modifier: Modifier = Modifier) {
    val consumed = meals.filter { it.isConsumed }
    val consumedCal = consumed.sumOf { it.totalCalories }
    val totalCal = meals.sumOf { it.totalCalories }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardFill).padding(20.dp)
    ) {
        CalorieRing(consumedCal, totalCal)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            MacroBar("Protein", consumed.sumOf { it.totalProtein }, meals.sumOf { it.totalProtein }, Color(0xFF0A84FF))
            MacroBar("Carbs", consumed.sumOf { it.totalCarbs }, meals.sumOf { it.totalCarbs }, Color(0xFF64D2FF))
            MacroBar("Fat", consumed.sumOf { it.totalFat }, meals.sumOf { it.totalFat }, Color(0xFFFF9500))
        }
    }
}

@Composable
private fun CalorieRing(consumedCal: Int, totalCal: Int) {
    val accent = TrackstarAccent
    val target = if (totalCal > 0) (consumedCal.toFloat() / totalCal).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(targetValue = target, animationSpec = tween(400), label = "calRing")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val stroke = 8.dp.toPx(); val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(Color.White.copy(alpha = 0.1f), 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(accent, -90f, animated * 360f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$consumedCal", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            Text("/ $totalCal kcal", fontSize = 9.sp, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun MacroBar(label: String, consumed: Double, total: Double, color: Color) {
    val progress = if (total > 0) (consumed / total).toFloat().coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(targetValue = progress, animationSpec = tween(400), label = "macro")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
            Text(if (total > 0) "${consumed.toInt()}/${total.toInt()}g" else "${consumed.toInt()}g", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
        }
        Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(modifier = Modifier.fillMaxWidth(animated).height(5.dp).clip(RoundedCornerShape(50)).background(color))
        }
    }
}

// MARK: - Meal card

// Ports iOS's DietMealCard: swipe right to consume / left to un-consume (only for today, revealing a
// colored background + icon and firing at an 80dp threshold), long-press for the Edit/Delete menu,
// tap to expand the food list. The 44dp circle toggles consume too (matching iOS's tap target).
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MealCard(
    meal: DietMeal,
    canConsume: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(meal.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val offsetX = remember(meal.id) { Animatable(0f) }
    val thresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val mt = MealType.from(meal.type)
    val nameColor = if (meal.isConsumed) Color.White.copy(alpha = 0.45f) else Color.White

    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
        SwipeBackground(offsetX.value, meal.isConsumed, canConsume, thresholdPx)

        Column(
            modifier = Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .then(
                    if (canConsume) Modifier.pointerInput(meal.id, meal.isConsumed) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(offsetX.value) >= thresholdPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggle()
                                }
                                // Critically damped (no overshoot): an underdamped spring springs
                                // PAST zero to the other side, briefly flashing the opposite swipe
                                // background as it settles.
                                scope.launch { offsetX.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
                            },
                            onHorizontalDrag = { _, delta ->
                                // Consumed meals only swipe left (to un-consume); planned meals only right.
                                val next = (offsetX.value + delta * 0.75f).let { if (meal.isConsumed) it.coerceAtMost(0f) else it.coerceAtLeast(0f) }
                                scope.launch { offsetX.snapTo(next) }
                            },
                        )
                    } else Modifier
                )
                .background(CardFill)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null,
                    onClick = { if (meal.foods.isNotEmpty()) expanded = !expanded },
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true },
                )
                .animateContentSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Consume toggle (type icon → green check).
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(if (meal.isConsumed) Color(0xFF34C759) else Color.White.copy(alpha = 0.12f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = canConsume, onClick = onToggle)
                ) {
                    if (meal.isConsumed) Icon(Icons.Filled.Check, contentDescription = "Consumed", tint = Color.White, modifier = Modifier.size(15.dp))
                    else Icon(mt.icon, contentDescription = null, tint = mt.color, modifier = Modifier.size(15.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(meal.type, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = nameColor)
                    val subtitle = if (meal.foods.isNotEmpty()) meal.foods.joinToString(" · ") { it.name } else meal.name
                    if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f), maxLines = 1)
                }
                if (meal.totalCalories > 0) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${meal.totalCalories}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (meal.isConsumed) Color.White.copy(alpha = 0.4f) else Color.White)
                        Text("kcal", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                    }
                }
                if (meal.foods.isNotEmpty()) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f))
                }
            }
            if (expanded && meal.foods.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    meal.foods.forEach { food -> FoodRow(food) }
                }
            }
        }

        MealContextMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, onEdit = onEdit, onDelete = onDelete)
    }
}

// Swipe reveal behind the card: green + check when consuming (right), neutral + undo when
// un-consuming (left). Background intensity tracks how far past the threshold you've dragged.
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SwipeBackground(offset: Float, isConsumed: Boolean, canConsume: Boolean, thresholdPx: Float) {
    val progress = (abs(offset) / thresholdPx).coerceIn(0f, 1f)
    val past = abs(offset) >= thresholdPx
    when {
        canConsume && offset > 0f -> Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.matchParentSize().background(Color(0xFF34C759).copy(alpha = 0.25f + progress * 0.55f)).padding(start = 20.dp)
        ) { Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (past) 25.dp else 22.dp)) }
        canConsume && offset < 0f -> Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.05f + progress * 0.1f)).padding(end = 20.dp)
        ) { Icon(Icons.Filled.Replay, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(if (past) 25.dp else 22.dp)) }
    }
}

@Composable
private fun MealContextMenu(expanded: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF1E1E2A),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        ContextMenuRow("Edit", Icons.Filled.Edit, Color.White) { onDismiss(); onEdit() }
        Box(modifier = Modifier.width(200.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        ContextMenuRow("Delete", Icons.Filled.Delete, Color(0xFFFF453A)) { onDismiss(); onDelete() }
    }
}

@Composable
private fun ContextMenuRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.width(200.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
        Spacer(modifier = Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun FoodRow(food: FoodItem) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(food.name, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
            if (food.amount.isNotBlank()) Text(food.amount, fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MacroBadge("P", food.protein, Color(0xFF0A84FF))
            MacroBadge("C", food.carbs, Color(0xFF64D2FF))
            MacroBadge("F", food.fat, Color(0xFFFF9500))
            if (food.calories > 0) Text("${food.calories} kcal", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun MacroBadge(label: String, value: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        Text(if (value % 1.0 == 0.0) "${value.toInt()}" else String.format(Locale.ENGLISH, "%.1f", value), fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
    }
}

@Composable
internal fun EmptyMeals() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 60.dp)) {
        Icon(Icons.Filled.Restaurant, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(40.dp))
        Text("No meals planned", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))
        Text("Tap + Add meal to get started", fontSize = 13.sp, color = Color.White.copy(alpha = 0.25f))
    }
}

// MARK: - Helpers

private fun dayColor(day: String): Color = when (day) {
    "Monday" -> Color(0xFF0A84FF); "Tuesday" -> Color(0xFFFF9F0A); "Wednesday" -> Color(0xFFFFD60A)
    "Thursday" -> Color(0xFF34C759); "Friday" -> Color(0xFF64D2FF); "Saturday" -> Color(0xFFAF52DE)
    else -> Color(0xFFFF375F)
}

private fun headerDate(dayName: String): String {
    // The date of the selected weekday in the current Mon–Sun week.
    val today = LocalDate.now()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val idx = dayNames.indexOf(dayName).coerceAtLeast(0)
    return monday.plusDays(idx.toLong()).format(DateTimeFormatter.ofPattern("EEEE · d MMMM", Locale.ENGLISH))
}

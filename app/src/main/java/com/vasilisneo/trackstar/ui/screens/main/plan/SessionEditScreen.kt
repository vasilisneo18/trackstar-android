package com.vasilisneo.trackstar.ui.screens.main.plan

// Ports iOS's SessionEditView.swift: the unified add/edit surface for one planned session. Title
// field, exercise list (unconfigured stubs render as a distinct tap-to-configure row; configured
// ones show a summary row), "Add Exercise" -> ExercisePickerSheet, X discards in-sheet edits
// (nothing was ever pushed to the backend — see SessionEditViewModel), checkmark commits via a
// single upsert. "Add Superset" is wired in Phase 4 once CompoundExercisePairSheet exists.
// Deviation: tap-to-edit + trailing delete icon per row, not iOS's swipe-to-reveal actions
// (Android has no equivalently clean swipe-actions primitive) — same capability, different gesture.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.api.ExerciseData
import com.vasilisneo.trackstar.data.api.ExerciseSet
import com.vasilisneo.trackstar.data.workout.ExerciseDisplayUnit
import com.vasilisneo.trackstar.data.workout.groupedForDisplay
import com.vasilisneo.trackstar.ui.components.DragReorderState
import com.vasilisneo.trackstar.ui.components.dragHandle
import com.vasilisneo.trackstar.ui.components.dragReorderItem
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground

@Composable
fun SessionEditScreen(
    weekIdentifier: String,
    day: String,
    sessionId: String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    val viewModel: SessionEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SessionEditViewModel(app, weekIdentifier, day, sessionId) }
        }
    )

    var showPicker by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<ExerciseData?>(null) }
    var editingPair by remember { mutableStateOf<Pair<ExerciseData, ExerciseData>?>(null) }
    var showNewPairSheet by remember { mutableStateOf(false) }
    var deletingExerciseId by remember { mutableStateOf<String?>(null) }
    var deletingPairIds by remember { mutableStateOf<Pair<String, String>?>(null) }
    var commentingExercise by remember { mutableStateOf<ExerciseData?>(null) }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                HeaderIcon(icon = Icons.Filled.Close, contentDescription = "Discard", onClick = onClose)
                Spacer(modifier = Modifier.weight(1f))
                Text(if (viewModel.isNew) "New Session" else "Edit Session", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                HeaderIcon(
                    icon = Icons.Filled.Check, contentDescription = "Save", tint = TrackstarAccent,
                    onClick = { viewModel.save { success -> if (success) onSaved() } }
                )
            }

            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TrackstarAccent)
                }
            } else {
                val units = viewModel.exercises.groupedForDisplay()
                val dragState = remember(units) { DragReorderState(units) { it.id } }
                fun commitReorder(newOrder: List<ExerciseDisplayUnit>) {
                    viewModel.reorderExercises(
                        newOrder.flatMap { u ->
                            when (u) {
                                is ExerciseDisplayUnit.Single -> listOf(u.exercise)
                                is ExerciseDisplayUnit.Pair -> listOf(u.a, u.b)
                            }
                        }
                    )
                }
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        SessionTitleTextField(value = viewModel.title, onValueChange = viewModel::updateTitle)
                    }

                    if (viewModel.exercises.isEmpty()) {
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 60.dp)
                            ) {
                                Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(36.dp))
                                Text("No exercises yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))
                            }
                        }
                    } else {
                        items(dragState.order, key = { it.id }) { unit ->
                            // Skip animateItem on the row being dragged — its position is driven by
                            // the drag offset, and a placement animation fights the instant per-swap
                            // offset compensation (the "bounce" where the held card springs back).
                            Box(modifier = Modifier.dragReorderItem(dragState, unit.id).then(if (dragState.draggingKey == unit.id) Modifier else Modifier.animateItem())) {
                                when (unit) {
                                    is ExerciseDisplayUnit.Single -> SessionExerciseRow(
                                        exercise = unit.exercise,
                                        onClick = { editingExercise = unit.exercise },
                                        onDelete = { deletingExerciseId = unit.exercise.id },
                                        comments = viewModel.exerciseComments[unit.exercise.id] ?: emptyList(),
                                        onCommentsTap = { commentingExercise = unit.exercise },
                                        dragHandleModifier = Modifier.dragHandle(dragState, unit.id, listState, ::commitReorder),
                                    )
                                    is ExerciseDisplayUnit.Pair -> SessionSupersetRow(
                                        a = unit.a, b = unit.b,
                                        onClick = { editingPair = unit.a to unit.b },
                                        onDelete = { deletingPairIds = (unit.a.id ?: "") to (unit.b.id ?: "") },
                                        commentsA = viewModel.exerciseComments[unit.a.id] ?: emptyList(),
                                        commentsB = viewModel.exerciseComments[unit.b.id] ?: emptyList(),
                                        onCommentsTapA = { commentingExercise = unit.a },
                                        onCommentsTapB = { commentingExercise = unit.b },
                                        dragHandleModifier = Modifier.dragHandle(dragState, unit.id, listState, ::commitReorder),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showPicker = true }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text("Add Exercise", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().clickable { showNewPairSheet = true }.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.padding(start = 6.dp))
                        Text("Add Superset", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showPicker) {
        ExercisePickerSheet(
            onAdd = { stubs -> viewModel.addExercises(stubs) },
            onDismiss = { showPicker = false },
        )
    }

    editingExercise?.let { exercise ->
        ExerciseEditorSheet(
            existing = exercise,
            onSave = { updated ->
                viewModel.updateExercise(updated)
                editingExercise = null
            },
            onDelete = { viewModel.deleteExercise(exercise.id ?: ""); editingExercise = null },
            onDismiss = { editingExercise = null },
        )
    }

    if (showNewPairSheet) {
        CompoundExercisePairSheet(
            initialExerciseA = null,
            initialExerciseB = null,
            onSave = { a, b -> viewModel.upsertExercisePair(a, b); showNewPairSheet = false },
            onDismiss = { showNewPairSheet = false },
        )
    }

    editingPair?.let { (a, b) ->
        CompoundExercisePairSheet(
            initialExerciseA = a,
            initialExerciseB = b,
            onSave = { updatedA, updatedB -> viewModel.upsertExercisePair(updatedA, updatedB); editingPair = null },
            onDismiss = { editingPair = null },
        )
    }

    deletingExerciseId?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingExerciseId = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Exercise?", color = Color.White) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.deleteExercise(id); deletingExerciseId = null }) {
                    Text("Delete", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingExerciseId = null }) { Text("Cancel", color = Color.White) }
            },
        )
    }

    deletingPairIds?.let { (aId, bId) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deletingPairIds = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete Superset?", color = Color.White) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.deleteExercisePair(aId, bId); deletingPairIds = null }) {
                    Text("Delete", color = Color(0xFFFF453A))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deletingPairIds = null }) { Text("Cancel", color = Color.White) }
            },
        )
    }

    commentingExercise?.let { exercise ->
        val exId = exercise.id ?: ""
        ExerciseCommentsSheet(
            exerciseName = exercise.name ?: "Exercise",
            exerciseId = exId,
            weekIdentifier = viewModel.weekId,
            authorName = viewModel.authorName,
            authorRole = viewModel.authorRole,
            initialComments = viewModel.exerciseComments[exId] ?: emptyList(),
            onCommentsUpdated = { viewModel.setCommentsFor(exId, it) },
            onDismiss = { commentingExercise = null },
        )
    }
}

@Composable
private fun HeaderIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, tint: Color = Color.White, onClick: () -> Unit) {
    // 44dp glass circle with a 16dp glyph — matches iOS's glassCircle(size: 44) and the superset /
    // exercise-picker header buttons.
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DragHandle(modifier: Modifier) {
    // Icons.Filled.Menu = three stacked lines, matching iOS's "line.3.horizontal"
    // (Material's DragHandle glyph only has two).
    Icon(
        Icons.Filled.Menu, contentDescription = "Drag to reorder", tint = Color.White.copy(alpha = 0.35f),
        modifier = modifier.size(18.dp)
    )
}

// Android-idiomatic stand-in for iOS's swipe-to-reveal Edit/Delete actions: long-press the card
// content to open a context menu. Styled like an iOS context menu (rounded, dark, hairline
// border, label left / glyph right, hairline divider between actions) rather than the stock
// Material dropdown.
@Composable
internal fun ExerciseContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    editLabel: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color(0xFF1E1E2A),
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
    ) {
        ContextMenuRow(label = editLabel, icon = Icons.Filled.Edit, tint = Color.White, onClick = { onDismiss(); onEdit() })
        Box(modifier = Modifier.width(200.dp).height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        ContextMenuRow(label = "Delete", icon = Icons.Filled.Delete, tint = Color(0xFFFF453A), onClick = { onDismiss(); onDelete() })
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

// Shared with WeeklyPlanScreen's single-session inline view (same package), so a session's
// exercises look identical whether edited inline or in the full-screen SessionEditScreen.
// Configured exercises render as the full iOS ExerciseView stat card; unconfigured stubs as the
// orange "Set up" card (UnconfiguredExerciseRow).
@Composable
internal fun SessionExerciseRow(
    exercise: ExerciseData,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    comments: List<ExerciseComment> = emptyList(),
    onCommentsTap: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    if (!exercise.isConfigured()) {
        UnconfiguredExerciseCard(exercise, onSetUp = onClick, onDelete = onDelete, dragHandleModifier = dragHandleModifier)
    } else {
        ConfiguredExerciseCard(exercise, onClick = onClick, onDelete = onDelete, comments = comments, onCommentsTap = onCommentsTap, dragHandleModifier = dragHandleModifier)
    }
}

// Ports iOS ExerciseView's note button: divider + "Add note" when empty, else a bubble+count
// badge, the latest comment text, and a chevron. Tapping opens the comments sheet.
@Composable
internal fun ExerciseNoteButton(comments: List<ExerciseComment>, onTap: () -> Unit, showDivider: Boolean = true, compact: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Full stat cards separate the note row with a divider; the compound (superset) card
        // doesn't — iOS's CompoundExerciseView places "Add note" directly under the exercise name.
        if (showDivider) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            // No ripple — iOS's note row has no press flash.
            modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
        ) {
            if (comments.isEmpty()) {
                // Compound (superset) card uses iOS's smaller 10/12; full stat cards use 11/13.
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(if (compact) 10.dp else 11.dp))
                Text("Add note", fontSize = if (compact) 12.sp else 13.sp, color = Color.White.copy(alpha = 0.3f))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                    Text("${comments.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text(comments.last().text, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 2, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val UnconfiguredOrange = Color(0xFFFF9F0A)

// Ports iOS's ExerciseView: drag handle + name header + one labeled stat-column row
// (Sets / type / Weight / Rest, plus a set-type badge) per group of identical sets.
// Tap opens the editor; long-press opens the Edit/Delete context menu (iOS uses swipe actions).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfiguredExerciseCard(
    exercise: ExerciseData,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    comments: List<ExerciseComment> = emptyList(),
    onCommentsTap: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp)
    ) {
        DragHandle(dragHandleModifier)
        // Tap/long-press live on the CONTENT column only, so they can't fire while
        // long-pressing the drag handle (which starts a reorder drag instead).
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                // No ripple — iOS's card has no press flash (tap edits, long-press opens the menu).
                .combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true },
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(exercise.name ?: "Exercise", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.groupedSets().forEachIndexed { index, group ->
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        SupersetStat("Sets", "${group.count}")
                        SupersetStat(group.typeLabel, group.repsRangeDisplay)
                        if (!group.weight.isNullOrBlank()) {
                            SupersetStat("Weight", "${group.weight}${(exercise.resistanceUnit?.weight ?: "KG").uppercase(java.util.Locale.ENGLISH)}")
                        } else if (!group.bandLevel.isNullOrBlank()) {
                            SupersetStat("Band", group.bandLevel)
                        }
                        if (group.restSeconds > 0) {
                            SupersetStat("Rest", formatRest(group.restSeconds))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        val badge = SetTypeOption.entries.firstOrNull { it.backendValue == group.setType && it.badgeColor != null }
                        if (badge != null) {
                            Text(
                                badge.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = badge.badgeColor!!,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(badge.badgeColor!!.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            ExerciseNoteButton(comments = comments, onTap = onCommentsTap)
        }
    }
    ExerciseContextMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, editLabel = "Edit", onEdit = onClick, onDelete = onDelete)
    }
}

// Ports iOS's UnconfiguredExerciseRow exactly: orange-bordered card, name + "No sets configured",
// and a "Set up" pill — no drag handle (iOS shows none here). Tap opens the editor; long-press
// opens the Set up / Delete context menu (iOS uses swipe actions).
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnconfiguredExerciseCard(exercise: ExerciseData, onSetUp: () -> Unit, onDelete: () -> Unit, dragHandleModifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, UnconfiguredOrange.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            // No ripple — tap sets up, long-press opens the menu (matching the configured card).
            .combinedClickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onSetUp,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name ?: "Exercise", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text("No sets configured", fontSize = 12.sp, color = UnconfiguredOrange.copy(alpha = 0.8f))
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(UnconfiguredOrange.copy(alpha = 0.4f)).clickable(onClick = onSetUp).padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
            Text("Set up", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
    ExerciseContextMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, editLabel = "Set up", onEdit = onSetUp, onDelete = onDelete)
    }
}

// Coalesces consecutive identical sets into display groups — mirrors ExerciseView.groupedSets on
// iOS (order-preserving, keyed on freq/reps-range/resistance/rest/set-type).
private data class PlanSetGroup(
    val count: Int, val reps: Int?, val repsMax: Int?, val duration: String?, val distance: String?,
    val weight: String?, val bandLevel: String?, val restSeconds: Int, val setType: String?,
) {
    val repsRangeDisplay: String
        get() = when {
            reps != null -> { val max = repsMax; if (max != null && max != reps) "${minOf(reps, max)}-${maxOf(reps, max)}" else "$reps" }
            duration != null -> duration
            distance != null -> distance
            else -> ""
        }
    val typeLabel: String
        get() = when {
            reps != null -> "Reps"
            duration != null -> "Duration"
            distance != null -> "Distance"
            else -> ""
        }
}

private fun ExerciseData.groupedSets(): List<PlanSetGroup> {
    val groups = mutableListOf<PlanSetGroup>()
    sets.orEmpty().forEach { set ->
        val freq = set.frequencyValue
        val res = set.resistanceValue
        val last = groups.lastOrNull()
        if (last != null &&
            last.reps == freq?.reps && last.duration == freq?.duration && last.distance == freq?.distance &&
            last.repsMax == set.repsMax && last.weight == res?.weight && last.bandLevel == res?.bandLevel &&
            last.restSeconds == (set.restSeconds ?: 0) && last.setType == set.setType
        ) {
            groups[groups.size - 1] = last.copy(count = last.count + 1)
        } else {
            groups.add(
                PlanSetGroup(
                    count = 1, reps = freq?.reps, repsMax = set.repsMax, duration = freq?.duration, distance = freq?.distance,
                    weight = res?.weight, bandLevel = res?.bandLevel, restSeconds = set.restSeconds ?: 0, setType = set.setType,
                )
            )
        }
    }
    return groups
}

// Ports iOS's CompoundExerciseView: a merged card showing both exercises (name + per-set label),
// an arrow-down divider between them, then Rounds / Rest stat columns — plus this app's
// drag-handle + trailing delete convention. The two halves share round count and rest, taken
// from exercise A (which is how supersets are configured).
private val SupersetCyan = Color(0xFF64D2FF)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SessionSupersetRow(
    a: ExerciseData,
    b: ExerciseData,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    commentsA: List<ExerciseComment> = emptyList(),
    commentsB: List<ExerciseComment> = emptyList(),
    onCommentsTapA: () -> Unit = {},
    onCommentsTapB: () -> Unit = {},
    dragHandleModifier: Modifier = Modifier,
) {
    val rounds = a.sets.orEmpty().size
    val restSeconds = a.sets.orEmpty().firstOrNull()?.restSeconds ?: 0
    val badge = a.sets.orEmpty().firstOrNull()?.setType?.let { raw ->
        SetTypeOption.entries.firstOrNull { it.backendValue == raw && it.badgeColor != null }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(12.dp),
    ) {
      DragHandle(dragHandleModifier)
      // Tap/long-press on the content only — the handle's long-press starts a drag.
      Column(
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier
              .weight(1f)
              // No ripple — iOS's card has no press flash when tapped to edit.
              .combinedClickable(
                  interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                  indication = null,
                  onClick = onClick,
                  onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuOpen = true },
              )
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Link, contentDescription = null, tint = SupersetCyan, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.padding(start = 6.dp))
            Text("Superset", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SupersetCyan)
            Spacer(modifier = Modifier.weight(1f))
            if (badge != null) {
                Text(
                    badge.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = badge.badgeColor!!,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badge.badgeColor!!.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        SupersetExerciseRow(a, commentsA, onCommentsTapA)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(10.dp))
            Box(modifier = Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.1f)))
        }

        SupersetExerciseRow(b, commentsB, onCommentsTapB)

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            SupersetStat(label = "Rounds", value = "$rounds")
            if (restSeconds > 0) SupersetStat(label = "Rest", value = formatRest(restSeconds))
        }
      }
    }
    ExerciseContextMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, editLabel = "Edit", onEdit = onClick, onDelete = onDelete)
    }
}

@Composable
private fun SupersetExerciseRow(exercise: ExerciseData, comments: List<ExerciseComment> = emptyList(), onCommentsTap: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(exercise.name ?: "Exercise", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            val label = exercise.sets.orEmpty().firstOrNull()?.let { setSessionLabel(it) }.orEmpty()
            if (label.isNotBlank()) {
                Text(label, fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        ExerciseNoteButton(comments = comments, onTap = onCommentsTap, showDivider = false, compact = true)
    }
}

@Composable
private fun SupersetStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

private fun formatRest(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return when {
        m == 0 -> "${s}s"
        s == 0 -> "${m}m"
        else -> "${m}m ${s}s"
    }
}

// Per-set label like iOS's ExerciseSet.sessionLabel: "8-12 reps @ 50 kg", "30 sec duration", …
private fun setSessionLabel(set: ExerciseSet): String {
    val freq = set.frequencyValue
    val valuePart = when {
        freq?.reps != null -> {
            val max = set.repsMax
            if (max != null && max != freq.reps) "${minOf(freq.reps, max)}-${maxOf(freq.reps, max)} reps" else "${freq.reps} reps"
        }
        freq?.duration != null -> "${freq.duration} duration"
        freq?.distance != null -> "${freq.distance} distance"
        else -> ""
    }
    val weight = set.resistanceValue?.weight
    val band = set.resistanceValue?.bandLevel
    val resistancePart = when {
        !weight.isNullOrBlank() -> "@ $weight kg"
        !band.isNullOrBlank() -> "· $band"
        else -> ""
    }
    return listOf(valuePart, resistancePart).filter { it.isNotBlank() }.joinToString(" ")
}

// Compact filled title field (50dp, white 15% fill, no border) — matches iOS's WLBTextfield used
// for the session title. Shared by SessionEditScreen and WeeklyPlanScreen's inline single-session
// view. Material3's OutlinedTextField is ~56dp with built-in padding and reads too tall here.
@Composable
internal fun SessionTitleTextField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(TrackstarAccent),
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.15f)),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text("Session Title", color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp)
                }
                inner()
            }
        },
    )
}


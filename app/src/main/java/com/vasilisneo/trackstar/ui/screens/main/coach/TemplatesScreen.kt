package com.vasilisneo.trackstar.ui.screens.main.coach

// Ports iOS's TemplatesView (the ⧉ button on the roster): the coach's saved weekly-plan templates.
// Create a named template, view its active days + exercise count, swipe to delete, tap to edit
// (editor arrives in a later phase). API-first via TemplatesViewModel.

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TemplatesScreen(onClose: () -> Unit, onOpenTemplate: (String, String) -> Unit, viewModel: TemplatesViewModel = viewModel()) {
    val templates = viewModel.templates
    var showCreate by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // At the Gold template cap (FeatureGate.GOLD_TEMPLATE_LIMIT), the + surfaces the limit instead
    // of opening the create dialog.
    fun requestCreate() {
        if (viewModel.canCreate) showCreate = true
        else android.widget.Toast.makeText(
            context, "You've reached the ${viewModel.templateLimit}-template limit.", android.widget.Toast.LENGTH_LONG
        ).show()
    }

    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp)) {
                GlassCircleIconButton(onClick = onClose, contentDescription = "Close", icon = Icons.Filled.Close)
                Spacer(modifier = Modifier.weight(1f))
                GlassCircleIconButton(onClick = { requestCreate() }, contentDescription = "New template", icon = Icons.Filled.Add)
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                item {
                    Text("Templates", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 16.dp))
                }
                if (viewModel.isLoading && templates.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f))
                        }
                    }
                } else if (templates.isEmpty()) {
                    item { EmptyState(onCreate = { requestCreate() }) }
                } else {
                    items(templates, key = { it.id }) { t ->
                        SwipeRevealTemplateRow(
                            template = t,
                            onOpen = { onOpenTemplate(t.id, t.name) },
                            onDelete = { viewModel.delete(t.id) },
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateTemplateDialog(
            onDismiss = { showCreate = false },
            onCreate = { name -> showCreate = false; viewModel.create(name) { id -> onOpenTemplate(id, name) } },
        )
    }
}

@Composable
private fun CreateTemplateDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A26),
        title = { Text("New Template", color = Color.White) },
        text = {
            Column {
                Text("Give this template a name.", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(modifier = Modifier.height(12.dp))
                BasicTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner -> if (name.isEmpty()) Text("Template name", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp); inner() },
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onCreate(name) }) { Text("Create", color = Color.White) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.6f)) } },
    )
}

@Composable
private fun SwipeRevealTemplateRow(template: TemplateSummary, onOpen: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val actionWidth = 80.dp
    val buttonSize = 68.dp
    val actionWidthPx = with(density) { actionWidth.toPx() }
    var offsetX by remember(template.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggableState = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(-actionWidthPx, 0f) }
    fun settle(target: Float) { scope.launch { animate(offsetX, target, animationSpec = spring(dampingRatio = 0.85f)) { v, _ -> offsetX = v } } }

    Box(modifier = modifier.fillMaxWidth()) {
        if (offsetX < 0f) {
            Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(end = 6.dp).size(buttonSize).clip(RoundedCornerShape(20.dp)).background(Color(0xFFEF4A40)).clickable { settle(0f); onDelete() },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
        Box(
            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }
                .draggable(draggableState, Orientation.Horizontal, onDragStopped = { settle(if (offsetX < -actionWidthPx * 0.4f) -actionWidthPx else 0f) })
        ) {
            TemplateCard(template, onClick = { if (offsetX != 0f) settle(0f) else onOpen() })
        }
    }
}

@Composable
private fun TemplateCard(template: TemplateSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f))) {
            Icon(Icons.Filled.Description, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(template.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            if (template.activeDays.isEmpty()) {
                Text("No exercises yet", fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f))
            } else {
                val days = template.activeDays.joinToString(" · ") { it.take(1) }
                val count = template.exerciseCount
                Text("$days · $count exercise${if (count == 1) "" else "s"}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp, start = 40.dp, end = 40.dp),
    ) {
        Icon(Icons.Filled.NoteAdd, contentDescription = null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(52.dp))
        Text("No Templates Yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Build weekly workout templates and assign them to athletes in seconds.",
            fontSize = 14.sp, color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onCreate).padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("Create Template", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

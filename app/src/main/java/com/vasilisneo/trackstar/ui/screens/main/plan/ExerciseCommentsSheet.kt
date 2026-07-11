package com.vasilisneo.trackstar.ui.screens.main.plan

// Ports iOS's ExerciseCommentsSheet: a chat-style sheet of an exercise's notes/comments — header
// (exercise name + "Comments"), a scrolling list (avatar initials + author + relative time +
// text), an input bar to post, and long-press-to-delete on your own comments. Fetches the
// exercise's comments on open and reports every change up via onCommentsUpdated so the card
// preview stays in sync.

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vasilisneo.trackstar.data.api.AddCommentRequest
import com.vasilisneo.trackstar.data.api.ExerciseComment
import com.vasilisneo.trackstar.data.auth.ApiResult
import com.vasilisneo.trackstar.data.workout.CommentRepository
import com.vasilisneo.trackstar.ui.theme.TrackstarBackground
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseCommentsSheet(
    exerciseName: String,
    exerciseId: String,
    weekIdentifier: String,
    authorName: String,
    authorRole: String,
    initialComments: List<ExerciseComment> = emptyList(),
    onCommentsUpdated: (List<ExerciseComment>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val repo = remember { CommentRepository() }

    var comments by remember { mutableStateOf(initialComments) }
    // Never flash a spinner: seed with whatever was preloaded and refresh silently in the
    // background (matching iOS, which seeds `initialComments` and updates in place).
    var isLoading by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<ExerciseComment?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(exerciseId) {
        when (val r = repo.getExerciseComments(exerciseId, weekIdentifier)) {
            is ApiResult.Success -> { comments = r.data; onCommentsUpdated(r.data) }
            is ApiResult.Error -> Unit
        }
        isLoading = false
    }

    // Keep the newest comment in view.
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) listState.animateScrollToItem(comments.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || isSending) return
        input = ""
        isSending = true
        scope.launch {
            val req = AddCommentRequest(weekIdentifier = weekIdentifier, userId = null, text = text, authorName = authorName, authorRole = authorRole)
            when (val r = repo.addComment(exerciseId, req)) {
                is ApiResult.Success -> { comments = comments + r.data; onCommentsUpdated(comments) }
                is ApiResult.Error -> Unit
            }
            isSending = false
        }
    }

    fun delete(comment: ExerciseComment) {
        val id = comment.id ?: return
        scope.launch {
            when (repo.deleteComment(id)) {
                is ApiResult.Success -> { comments = comments.filterNot { it.id == id }; onCommentsUpdated(comments) }
                is ApiResult.Error -> Unit
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // Transparent container + no drag handle: iOS's sheet has no grabber and paints its own
        // Midnight gradient edge-to-edge. The content Column below owns the rounded top and the
        // gradient so it fills the whole sheet (a Material grabber would otherwise float in a
        // see-through strip above the gradient).
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        // Medium-height sheet (~iOS .medium detent), not full-screen. Rounded top matches iOS's
        // presentationCornerRadius(30); imePadding lifts the input bar above the keyboard.
        Column(
            modifier = Modifier
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .trackstarBackground()
                // Lift the whole sheet a touch so it reads as an elevated surface (like iOS's
                // sheet) rather than the near-black page background.
                .background(Color.White.copy(alpha = 0.06f))
                .imePadding()
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).height(44.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(exerciseName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Comments", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(36.dp))
            }

            // Messages
            LazyColumn(
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else if (comments.isEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(44.dp))
                            Text("No comments yet", fontSize = 15.sp, color = Color.White.copy(alpha = 0.4f))
                            Text("Start the conversation", fontSize = 13.sp, color = Color.White.copy(alpha = 0.25f))
                        }
                    }
                }
                items(comments, key = { it.id ?: it.hashCode().toString() }) { comment ->
                    CommentRow(
                        comment = comment,
                        isOwn = comment.authorName == authorName,
                        onLongPress = { deleting = comment },
                    )
                }
            }

            // Input bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("Add a comment...", color = Color.White.copy(alpha = 0.4f), fontSize = 15.sp)
                        }
                        inner()
                    },
                )
                val canSend = input.trim().isNotEmpty() && !isSending
                if (isSending) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        Icons.Filled.ArrowCircleUp, contentDescription = "Send",
                        tint = if (canSend) Color.White else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(28.dp).clickable(enabled = canSend) { send() }
                    )
                }
            }
        }
    }

    deleting?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color(0xFF1A1A26),
            title = { Text("Delete comment?", color = Color.White) },
            text = { Text(comment.text, color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { delete(comment); deleting = null }) { Text("Delete", color = Color(0xFFFF453A)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Color.White) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CommentRow(comment: ExerciseComment, isOwn: Boolean, onLongPress: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { if (isOwn) onLongPress() })
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(comment.authorName), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.authorName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text(relativeTime(comment.timestamp), fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
            }
            Text(comment.text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")

private fun relativeTime(millis: Long?): String {
    if (millis == null) return ""
    val diff = System.currentTimeMillis() - millis
    if (diff < 5_000) return "now"
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}

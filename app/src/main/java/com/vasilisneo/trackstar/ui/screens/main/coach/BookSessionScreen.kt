package com.vasilisneo.trackstar.ui.screens.main.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vasilisneo.trackstar.data.api.SlotResponse
import com.vasilisneo.trackstar.ui.components.GlassCircleIconButton
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import com.vasilisneo.trackstar.ui.theme.trackstarBackground
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val CardFill = Color.White.copy(alpha = 0.06f)

@Composable
fun BookSessionScreen(
    onBack: () -> Unit = {},
    viewModel: BookSessionViewModel = viewModel(),
) {
    Box(modifier = Modifier.fillMaxSize().trackstarBackground()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                GlassCircleIconButton(onClick = onBack, icon = Icons.Filled.Close, contentDescription = "Close")
                Spacer(Modifier.weight(1f))
            }
            Text("Book a Session", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

            when {
                viewModel.isLoading && viewModel.available.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White.copy(alpha = 0.6f)) }
                viewModel.available.isEmpty() ->
                    Box(Modifier.fillMaxSize().padding(bottom = 80.dp, start = 32.dp, end = 32.dp), Alignment.Center) {
                        Text("No sessions available. Your coach hasn't added any upcoming slots yet.",
                            color = Color.White.copy(alpha = 0.5f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    viewModel.availableByDate.forEach { (date, slots) ->
                        item(key = "hdr-$date") {
                            Text(prettyDate(date), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                        }
                        items(slots, key = { it.id }) { slot ->
                            AthleteSlotCard(
                                slot = slot,
                                busy = viewModel.busySlotId == slot.id,
                                onBook = { viewModel.book(slot.id) },
                                onCancel = { viewModel.cancel(slot.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AthleteSlotCard(slot: SlotResponse, busy: Boolean, onBook: () -> Unit, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardFill).padding(16.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("${slot.startTime} – ${slot.endTime}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            slot.title?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)) }
            slot.coachName?.takeIf { it.isNotBlank() }?.let { Text("with $it", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f), modifier = Modifier.padding(top = 2.dp)) }
            if (slot.capacity > 1 && !slot.bookedByMe) {
                Text("${slot.remaining} spot${if (slot.remaining == 1) "" else "s"} left", fontSize = 12.sp, color = TrackstarAccent, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.size(12.dp))
        when {
            busy -> CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            slot.bookedByMe -> BookingChip(text = "Booked", booked = true, onClick = onCancel)
            slot.full -> Text("Full", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.SemiBold)
            else -> BookingChip(text = "Book", booked = false, onClick = onBook)
        }
    }
}

@Composable
private fun BookingChip(text: String, booked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (booked) Color.White.copy(alpha = 0.12f) else TrackstarAccent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        if (booked) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

private fun prettyDate(iso: String): String = runCatching {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}.getOrDefault(iso)

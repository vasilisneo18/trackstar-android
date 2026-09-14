package com.vasilisneo.trackstar.ui.screens.main.attendance

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vasilisneo.trackstar.data.api.VisitResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceExportSheet(
    reportTitle: String,
    subjectName: String?,
    visits: List<VisitResponse>,
    groupByAthlete: Boolean = false,
    onGenerated: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf(AttendancePeriod.ALL_TIME) }
    var fromMs by remember { mutableLongStateOf(System.currentTimeMillis() - 30L * 24 * 3600 * 1000) }
    var toMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var generating by remember { mutableStateOf(false) }

    val filtered = filterVisits(visits, period, fromMs, toMs)
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14141F)) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Export Report", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("PERIOD", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.4f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeriodChip(AttendancePeriod.ALL_TIME.label, period == AttendancePeriod.ALL_TIME, Modifier.weight(1f)) { period = AttendancePeriod.ALL_TIME }
                    PeriodChip(AttendancePeriod.LAST_7.label, period == AttendancePeriod.LAST_7, Modifier.weight(1f)) { period = AttendancePeriod.LAST_7 }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PeriodChip(AttendancePeriod.LAST_30.label, period == AttendancePeriod.LAST_30, Modifier.weight(1f)) { period = AttendancePeriod.LAST_30 }
                    PeriodChip(AttendancePeriod.THIS_MONTH.label, period == AttendancePeriod.THIS_MONTH, Modifier.weight(1f)) { period = AttendancePeriod.THIS_MONTH }
                }
            }
            PeriodChip(AttendancePeriod.CUSTOM.label, period == AttendancePeriod.CUSTOM, Modifier.fillMaxWidth()) { period = AttendancePeriod.CUSTOM }

            if (period == AttendancePeriod.CUSTOM) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.weight(1f))
                    DateButton(dateFmt.format(Date(fromMs))) { pickDate(context, fromMs) { fromMs = it; if (toMs < it) toMs = it } }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
                    DateButton(dateFmt.format(Date(toMs))) { pickDate(context, toMs) { if (it >= fromMs) toMs = it } }
                    Spacer(Modifier.weight(1f))
                }
            }

            Text(
                "${filtered.size} check-in${if (filtered.size == 1) "" else "s"} in this period",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
            )

            Box(
                modifier = Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                    .background(if (filtered.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White)
                    .clickable(enabled = filtered.isNotEmpty() && !generating) {
                        generating = true
                        val label = periodLabel(period, fromMs, toMs)
                        scope.launch {
                            val file = withContext(Dispatchers.Default) {
                                AttendancePdf.make(context, reportTitle, subjectName, label, filtered, groupByAthlete)
                            }
                            generating = false
                            onDismiss()
                            if (file != null) onGenerated(file)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (generating) CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else Text("Generate PDF", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun PeriodChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(42.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (selected) Color.Black else Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DateButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(132.dp).height(40.dp).clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 14.sp, color = Color.White)
    }
}

private fun pickDate(context: Context, current: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = current }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
            onPicked(c.timeInMillis)
        },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun filterVisits(visits: List<VisitResponse>, period: AttendancePeriod, fromMs: Long, toMs: Long): List<VisitResponse> {
    val (start, end) = if (period == AttendancePeriod.CUSTOM) {
        val cal = Calendar.getInstance().apply { timeInMillis = toMs; add(Calendar.DAY_OF_MONTH, 1) }
        fromMs to cal.timeInMillis
    } else period.bounds()
    return visits.filter { v ->
        val d = v.checkInAt?.toLong() ?: return@filter start == null
        (start == null || d >= start) && (end == null || d <= end)
    }
}

private fun periodLabel(period: AttendancePeriod, fromMs: Long, toMs: Long): String {
    if (period == AttendancePeriod.CUSTOM) {
        val f = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        return "${f.format(Date(fromMs))} – ${f.format(Date(toMs))}"
    }
    return period.label
}


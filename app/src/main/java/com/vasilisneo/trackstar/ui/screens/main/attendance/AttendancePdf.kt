package com.vasilisneo.trackstar.ui.screens.main.attendance

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.vasilisneo.trackstar.data.api.VisitResponse
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Renders a check-in report to a PDF (A4 @ 72dpi), grouped by month newest-first. Android port of
// iOS's AttendancePDF (athlete side). Returns the written file, or null on failure.
object AttendancePdf {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val ROW_H = 26f
    private val contentW = PAGE_W - MARGIN * 2

    private val ink = Color.rgb(26, 26, 26)
    private val sub = Color.rgb(115, 115, 115)
    private val accent = Color.rgb(46, 128, 255)
    private val cardBg = Color.rgb(244, 244, 244)
    private val separator = Color.rgb(224, 224, 224)

    fun make(
        context: Context,
        title: String,
        subjectName: String?,
        periodLabel: String,
        visits: List<VisitResponse>,
    ): File? {
        return try {
            val doc = PdfDocument()
            val rowFmt = SimpleDateFormat("EEE, MMM d, yyyy · HH:mm", Locale.ENGLISH)
            val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var canvas = page.canvas
            var y = MARGIN

            fun newPage() {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }

            fun paint(size: Float, color: Int, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    this.color = color
                    typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
                    textAlign = align
                }

            fun text(s: String, p: Paint, x: Float = MARGIN) {
                canvas.drawText(s, x, y + p.textSize, p)
            }

            // Header
            text("Trackstar Fitness", paint(22f, ink, bold = true))
            val savedY = y; y += 27f
            text("Attendance Report", paint(13f, sub))
            y = savedY + 46f
            // Accent rule
            canvas.drawRect(MARGIN, y, MARGIN + 56f, y + 3f, Paint().apply { color = accent })
            y += 16f

            if (!subjectName.isNullOrEmpty()) {
                text(subjectName, paint(17f, ink, bold = true)); y += 24f
                text(title, paint(12f, sub)); y += 18f
            } else {
                text(title, paint(14f, ink, bold = true)); y += 22f
            }
            text(periodLabel, paint(12f, sub)); y += 18f
            text(
                "Generated " + SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.ENGLISH).format(Date()),
                paint(10f, sub)
            ); y += 24f

            val totalMin = visits.mapNotNull { it.durationMin }.sum()
            text(
                "${visits.size} check-in${if (visits.size == 1) "" else "s"}  ·  ${formatMinutes(totalMin)} total",
                paint(13f, ink, bold = true)
            ); y += 26f

            fun drawRow(v: VisitResponse, ry: Float) {
                val ty = ry + (ROW_H - 14f) / 2f
                val whenStr = v.checkInAt?.let { rowFmt.format(Date(it.toLong())) } ?: "—"
                canvas.drawText(whenStr, MARGIN + 14f, ty + 11f, paint(11f, ink, bold = true))
                canvas.drawText(v.locationLabel, MARGIN + 214f, ty + 11f, paint(11f, sub))
                val dur = durationLabel(v)
                canvas.drawText(dur, PAGE_W - MARGIN - 14f, ty + 11f, paint(11f, ink, bold = true, align = Paint.Align.RIGHT))
            }

            fun drawSection(header: String, rows: List<VisitResponse>) {
                if (y + 24f + ROW_H > PAGE_H - MARGIN) newPage()
                text(header, paint(13f, ink, bold = true)); y += 20f

                val sorted = rows.sortedByDescending { it.checkInAt ?: 0.0 }
                var i = 0
                while (i < sorted.size) {
                    val avail = (PAGE_H - MARGIN) - y - 8f
                    val fit = maxOf(1, (avail / ROW_H).toInt())
                    val chunk = sorted.subList(i, minOf(i + fit, sorted.size))
                    val cardH = chunk.size * ROW_H + 8f

                    canvas.drawRoundRect(
                        RectF(MARGIN, y, MARGIN + contentW, y + cardH), 10f, 10f,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
                    )
                    var ry = y + 4f
                    chunk.forEachIndexed { j, v ->
                        drawRow(v, ry)
                        if (j < chunk.size - 1) {
                            canvas.drawRect(
                                MARGIN + 14f, ry + ROW_H, MARGIN + contentW - 14f, ry + ROW_H + 0.5f,
                                Paint().apply { color = separator }
                            )
                        }
                        ry += ROW_H
                    }
                    y += cardH + 16f
                    i += chunk.size
                    if (i < sorted.size) newPage()
                }
            }

            // Group by month, newest first.
            val cal = Calendar.getInstance()
            val groups = visits.groupBy { v ->
                val ms = v.checkInAt?.toLong() ?: 0L
                cal.timeInMillis = ms
                cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            }.toSortedMap(compareByDescending { it })

            for ((_, rows) in groups) {
                val first = rows.firstOrNull()?.checkInAt?.toLong() ?: 0L
                drawSection(monthFmt.format(Date(first)), rows)
            }

            doc.finishPage(page)
            val file = File(context.cacheDir, "Trackstar-Attendance-${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun durationLabel(v: VisitResponse): String {
        if (v.isOpen) return "—"
        val m = v.durationMin ?: return "—"
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    private fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

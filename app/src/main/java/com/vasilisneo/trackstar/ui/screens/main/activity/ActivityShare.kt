package com.vasilisneo.trackstar.ui.screens.main.activity

// Sharing an activity as an image — mirrors iOS's ActivityShare.swift: the route on the map, the
// Trackstar name with the plan-coloured logo on top, then the title, day and times and the three
// headline stats. 4:5 (1080 × 1350 px), sized for Instagram, WhatsApp and Messages.

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.google.android.gms.maps.GoogleMap
import com.vasilisneo.trackstar.R
import com.vasilisneo.trackstar.data.activity.ActivityFormat
import com.vasilisneo.trackstar.data.activity.ActivityType
import com.vasilisneo.trackstar.data.activity.activityType
import com.vasilisneo.trackstar.data.activity.coordinates
import com.vasilisneo.trackstar.data.activity.displayTitle
import com.vasilisneo.trackstar.data.activity.paceSecondsPerKm
import com.vasilisneo.trackstar.data.activity.speedKmh
import com.vasilisneo.trackstar.data.api.RecordedActivity
import com.vasilisneo.trackstar.data.billing.AppPlan
import com.vasilisneo.trackstar.ui.i18n.tr
import com.vasilisneo.trackstar.ui.theme.TrackstarAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

object ActivityShare {
    /** Layout in dp-like units (same numbers as iOS points), drawn at 3×. */
    private const val SCALE = 3f
    private const val CARD_W = 360f
    private const val CARD_H = 450f
    private const val MAP_H = 290f
    private const val BACKGROUND = 0xFF0D0D17.toInt()

    /** Debug harness only (see ActivityDemo): pretend Health Connect has these steps, and show the
     *  image here instead of opening the share sheet. */
    var debugSteps: Int? = null
    var debugImageSink: ((Bitmap) -> Unit)? = null

    /**
     * Snapshots the detail screen's map with the route's start (green) and finish (white) marked.
     * Null when there's no map or no route.
     */
    suspend fun mapSnapshot(map: GoogleMap?, activity: RecordedActivity): Bitmap? {
        map ?: return null
        val route = activity.coordinates
        if (route.size < 2) return null
        val ends = listOf(route.first(), route.last()).map { map.projection.toScreenLocation(it) }
        val snap = suspendCancellableCoroutine<Bitmap?> { cont -> map.snapshot { cont.resume(it) } } ?: return null
        val out = snap.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val r = 6f * out.width / 360f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r / 3; color = 0x99000000.toInt() }
        listOf(ends[0] to 0xFF34C759.toInt(), ends[1] to 0xFFFFFFFF.toInt()).forEach { (p, color) ->
            fill.color = color
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, fill)
            c.drawCircle(p.x.toFloat(), p.y.toFloat(), r, ring)
        }
        return out
    }

    /** Draws the share card. `icon` is the activity type's icon, drawn tinted in the accent colour. */
    suspend fun image(
        context: Context, activity: RecordedActivity, map: Bitmap?, icon: VectorPainter,
        steps: Int?, plan: AppPlan,
    ): Bitmap = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap((CARD_W * SCALE).toInt(), (CARD_H * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(SCALE, SCALE)
        c.drawColor(BACKGROUND)
        val accent = TrackstarAccent.toArgb()

        // Map, cropped to fill the top, fading into the background at the bottom.
        if (map != null) {
            val target = CARD_W / MAP_H
            val srcW: Int; val srcH: Int
            if (map.width.toFloat() / map.height > target) { srcH = map.height; srcW = (map.height * target).toInt() }
            else { srcW = map.width; srcH = (map.width / target).toInt() }
            // Keep the bottom-left corner, where Google's logo sits (it must stay visible).
            val left = 0; val top = map.height - srcH
            c.drawBitmap(map, Rect(left, top, left + srcW, top + srcH), RectF(0f, 0f, CARD_W, MAP_H), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            c.drawRect(0f, 0f, CARD_W, MAP_H, Paint().apply { color = 0x0DFFFFFF })
            drawIcon(c, icon, CARD_W / 2 - 32, MAP_H / 2 - 32, 64f, accent and 0x00FFFFFF or 0x99000000.toInt())
        }
        c.drawRect(0f, MAP_H - 50, CARD_W, MAP_H, Paint().apply {
            shader = LinearGradient(0f, MAP_H - 50, 0f, MAP_H, 0x000D0D17, BACKGROUND, Shader.TileMode.CLAMP)
        })

        // Top bar: logo + Trackstar, the website on the right, over a dark fade.
        c.drawRect(0f, 0f, CARD_W, 72f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, 72f, 0xD90D0D17.toInt(), 0x000D0D17, Shader.TileMode.CLAMP)
        })
        BitmapFactory.decodeResource(context.resources, logoFor(plan))?.let { logo ->
            val dst = RectF(20f, 16f, 46f, 42f)
            val save = c.save()
            c.clipPath(Path().apply { addRoundRect(dst, 7f, 7f, Path.Direction.CW) })
            c.drawBitmap(logo, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            c.restoreToCount(save)
        }
        val brand = text(16f, 0xFFFFFFFF.toInt(), bold = true)
        c.drawText("Trackstar", 54f, 29f + baselineOffset(brand), brand)
        val site = text(11f, 0x99FFFFFF.toInt(), medium = true)
        c.drawText("trackstar.fitness", CARD_W - 20 - site.measureText("trackstar.fitness"), 29f + baselineOffset(site), site)

        // Title, day and times, stats — centred in the space under the map.
        val titlePaint = text(19f, 0xFFFFFFFF.toInt(), bold = true)
        val datePaint = text(12f, 0x80FFFFFF.toInt())
        val labelPaint = text(11f, 0x80FFFFFF.toInt(), medium = true)
        val valuePaint = text(22f, 0xFFFFFFFF.toInt(), bold = true)
        val unitPaint = text(11f, 0x80FFFFFF.toInt(), medium = true)
        val blockH = 24f + 2f + 15f + 18f + 14f + 2f + 27f
        var y = MAP_H + (CARD_H - MAP_H - 20 - blockH) / 2

        drawIcon(c, icon, 20f, y + 4, 16f, accent)
        val title = ellipsize(activity.displayTitle, titlePaint, CARD_W - 40 - 23)
        c.drawText(title, 43f, y + 12 + baselineOffset(titlePaint), titlePaint)
        y += 26f
        c.drawText(ActivityFormat.dayAndTimes(activity), 20f, y + 7.5f + baselineOffset(datePaint), datePaint)
        y += 15f + 18f
        val colW = (CARD_W - 40) / 3
        stats(activity, steps).forEachIndexed { i, (label, value, unit) ->
            val x = 20 + i * colW
            c.drawText(label, x, y + 7 + baselineOffset(labelPaint), labelPaint)
            val baseline = y + 16 + 13.5f + baselineOffset(valuePaint)
            c.drawText(value, x, baseline, valuePaint)
            if (unit != null) c.drawText(unit, x + valuePaint.measureText(value) + 2, baseline, unitPaint)
        }
        bmp
    }

    /** Writes the image to the cache and opens the system share sheet. */
    suspend fun present(context: Context, image: Bitmap, title: String) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "activity.png").also { f -> FileOutputStream(f).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, tr("Share")))
    }

    /** The logo in the colour of the user's plan, matching their app icon. */
    private fun logoFor(plan: AppPlan) = when (plan) {
        AppPlan.FREE -> R.drawable.share_logo_free
        AppPlan.BRONZE -> R.drawable.share_logo_bronze
        AppPlan.SILVER -> R.drawable.share_logo_silver
        AppPlan.GOLD -> R.drawable.share_logo_gold
    }

    /** Three headline numbers, picked per activity type (slopes, hikes, walks, the rest). */
    private fun stats(a: RecordedActivity, steps: Int?): List<Triple<String, String, String?>> {
        a.slopes?.let { s ->
            return listOf(Triple(tr("Runs"), "${s.runCount}", null),
                Triple(tr("Vertical"), "${s.verticalMeters.toInt()}", "m"),
                Triple(tr("Top speed"), "${s.topSpeedKmh.toInt()}", "km/h"))
        }
        val distance = Triple(tr("Distance"), ActivityFormat.distance(a.distanceMeters), "km")
        val time = Triple(tr("Moving time"), ActivityFormat.duration(a.movingSeconds), null)
        if (a.activityType == ActivityType.Hike) return listOf(distance, Triple(tr("Ascent"), "${a.elevationGainMeters.toInt()}", "m"), time)
        // A walk is about the steps more than the pace.
        if (a.activityType == ActivityType.Walk && steps != null) return listOf(distance, Triple(tr("Steps"), ActivityFormat.steps(steps), null), time)
        val pace = if (a.activityType.usesPace) Triple(tr("Avg pace"), ActivityFormat.pace(a.paceSecondsPerKm), "/km")
        else Triple(tr("Avg speed"), ActivityFormat.speed(a.speedKmh), "km/h")
        return listOf(distance, time, pace)
    }

    private fun drawIcon(c: Canvas, icon: VectorPainter, x: Float, y: Float, size: Float, color: Int) {
        val px = (size * SCALE).toInt()
        val img = ImageBitmap(px, px)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(px.toFloat(), px.toFloat())) {
            with(icon) { draw(Size(px.toFloat(), px.toFloat()), colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color(color))) }
        }
        c.drawBitmap(img.asAndroidBitmap(), null, RectF(x, y, x + size, y + size), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun text(size: Float, color: Int, bold: Boolean = false, medium: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = when {
            bold -> Typeface.create("sans-serif", Typeface.BOLD)
            medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    /** Offset from a line's vertical centre to its baseline. */
    private fun baselineOffset(p: Paint): Float = -(p.ascent() + p.descent()) / 2

    private fun ellipsize(s: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(s) <= maxWidth) return s
        var end = s.length
        while (end > 0 && p.measureText(s.substring(0, end) + "…") > maxWidth) end--
        return s.substring(0, end) + "…"
    }
}

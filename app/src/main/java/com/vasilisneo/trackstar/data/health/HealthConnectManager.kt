package com.vasilisneo.trackstar.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Reads step counts from Health Connect — the Android equivalent of iOS's HealthManager
 * (HealthKit). Read-only. Safe to call when Health Connect is unavailable (returns zeros).
 */
class HealthConnectManager(private val context: Context) {

    val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null
    }

    /** True when the Health Connect provider is installed and usable on this device. */
    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasStepsPermission(): Boolean {
        val c = client ?: return false
        return runCatching { c.permissionController.getGrantedPermissions().containsAll(permissions) }
            .getOrDefault(false)
    }

    /** Total steps for a single calendar day. */
    suspend fun steps(day: LocalDate): Int {
        val c = client ?: return 0
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            val resp = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            resp[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
        }.getOrDefault(0)
    }

    /** Per-hour step totals for a day (index 0 = 00:00 … 23 = 23:00). */
    suspend fun hourlySteps(day: LocalDate): List<Int> {
        val c = client ?: return List(24) { 0 }
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return runCatching {
            val buckets: List<AggregationResultGroupedByDuration> = c.aggregateGroupByDuration(
                AggregateGroupByDurationRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    timeRangeSlicer = Duration.ofHours(1),
                )
            )
            val hours = IntArray(24)
            for (b in buckets) {
                val hr = b.startTime.atZone(zone).hour
                if (hr in 0..23) hours[hr] = b.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
            }
            hours.toList()
        }.getOrDefault(List(24) { 0 })
    }

    /** Per-day step totals for the 7 days starting at [weekStart] (index 0 = weekStart). */
    suspend fun weeklySteps(weekStart: LocalDate): List<Int> {
        val c = client ?: return List(7) { 0 }
        return runCatching {
            val buckets = c.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        weekStart.atStartOfDay(), weekStart.plusDays(7).atStartOfDay()
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                )
            )
            val days = IntArray(7)
            for (b in buckets) {
                val idx = ChronoUnit.DAYS.between(weekStart, b.startTime.toLocalDate()).toInt()
                if (idx in 0..6) days[idx] = b.result[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
            }
            days.toList()
        }.getOrDefault(List(7) { 0 })
    }
}

package com.vasilisneo.trackstar.ui.screens.main.attendance

import java.util.Calendar

// Period selection for a check-in PDF report (mirrors iOS's AttendancePeriod). Bounds are epoch ms;
// null = open-ended. `custom` returns nulls — the caller supplies dates.
enum class AttendancePeriod(val label: String) {
    ALL_TIME("All time"),
    LAST_7("Last 7 days"),
    LAST_30("Last 30 days"),
    THIS_MONTH("This month"),
    CUSTOM("Custom range");

    fun bounds(now: Long = System.currentTimeMillis()): Pair<Long?, Long?> {
        val cal = Calendar.getInstance()
        return when (this) {
            ALL_TIME -> null to null
            LAST_7 -> (now - 7L * 24 * 3600 * 1000) to now
            LAST_30 -> (now - 30L * 24 * 3600 * 1000) to now
            THIS_MONTH -> {
                cal.timeInMillis = now
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            CUSTOM -> null to null
        }
    }

    companion object {
        val presets = listOf(ALL_TIME, LAST_7, LAST_30, THIS_MONTH)
    }
}

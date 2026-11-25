package com.example.domain.attendance

import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.StaffRosterEntity
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class StoreGeofenceConfig(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 100.0,
    val storeLabel: String = "FILTR Coffee",
    val updatedAt: Long = 0L
) {
    val isConfigured: Boolean
        get() = latitude != 0.0 || longitude != 0.0
}

data class GeofenceCheckResult(
    val isInside: Boolean,
    val distanceMeters: Double,
    val allowedRadiusMeters: Double
)

enum class DayAttendanceStatus {
    PRESENT,
    ON_DUTY,
    ABSENT,
    WEEKLY_OFF,
    NOT_JOINED,
    UPCOMING
}

data class AttendanceCalendarDay(
    val dateKey: String,
    val dayOfMonth: Int,
    val status: DayAttendanceStatus,
    val log: AttendanceLogEntity?
)

data class MonthlyAttendanceSummary(
    val monthKey: String,
    val staffName: String,
    val days: List<AttendanceCalendarDay>,
    val presentDays: Int,
    val absentDays: Int,
    val completedShifts: Int,
    val totalWorkedMinutes: Int,
    val attendancePercent: Int
)

data class TeamRollCallRow(
    val member: StaffRosterEntity,
    val todayLog: AttendanceLogEntity?,
    val todayStatus: DayAttendanceStatus,
    val monthlySummary: MonthlyAttendanceSummary
)

fun formatDayOfWeekShort(dayOfWeek: Int): String = AttendanceCalculator.formatDayOfWeekShort(dayOfWeek)

object AttendanceCalculator {

    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

    fun normalizeStaffKey(staffName: String): String {
        val normalized = staffName
            .trim()
            .lowercase(Locale.US)
            .replace(NON_ALNUM_REGEX, "_")
            .trim('_')
        return normalized.ifBlank { "staff" }
    }

    fun buildLogId(dateKey: String, staffName: String): String {
        return "${dateKey}_${normalizeStaffKey(staffName)}"
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val radLat1 = Math.toRadians(lat1)
        val radLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2.0).pow(2) +
            cos(radLat1) * cos(radLat2) * sin(dLng / 2.0).pow(2)
        val clampedA = min(1.0, a.coerceAtLeast(0.0))
        val c = 2.0 * atan2(sqrt(clampedA), sqrt(1.0 - clampedA))
        return EARTH_RADIUS_METERS * c
    }

    fun evaluateGeofence(
        userLat: Double,
        userLng: Double,
        config: StoreGeofenceConfig
    ): GeofenceCheckResult {
        val distanceMeters = calculateDistanceMeters(
            lat1 = userLat,
            lng1 = userLng,
            lat2 = config.latitude,
            lng2 = config.longitude
        )
        val allowedRadiusMeters = config.radiusMeters.coerceAtLeast(0.0)
        val isInside = config.isConfigured && distanceMeters <= allowedRadiusMeters
        return GeofenceCheckResult(
            isInside = isInside,
            distanceMeters = distanceMeters,
            allowedRadiusMeters = allowedRadiusMeters
        )
    }

    fun calculateWorkedMinutes(checkInMs: Long, checkOutMs: Long): Int {
        if (checkInMs <= 0L || checkOutMs <= checkInMs) return 0
        return ((checkOutMs - checkInMs) / 60_000L).toInt()
    }

    fun formatWorkedDuration(minutes: Int): String {
        val safeMinutes = minutes.coerceAtLeast(0)
        val hours = safeMinutes / 60
        val mins = safeMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    fun formatDayOfWeekShort(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SUNDAY -> "Sun"
        Calendar.MONDAY -> "Mon"
        Calendar.TUESDAY -> "Tue"
        Calendar.WEDNESDAY -> "Wed"
        Calendar.THURSDAY -> "Thu"
        Calendar.FRIDAY -> "Fri"
        Calendar.SATURDAY -> "Sat"
        else -> "None"
    }

    fun buildMonthlySummary(
        monthKey: String,
        staffName: String,
        logs: List<AttendanceLogEntity>,
        trackingStartDateKey: String?,
        todayDateKey: String,
        weeklyOffDayOfWeek: Int = 0
    ): MonthlyAttendanceSummary {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        val month = parts.getOrNull(1)?.toIntOrNull()
        if (year == null || month == null || month !in 1..12) {
            return MonthlyAttendanceSummary(
                monthKey = monthKey,
                staffName = staffName,
                days = emptyList(),
                presentDays = 0,
                absentDays = 0,
                completedShifts = 0,
                totalWorkedMinutes = 0,
                attendancePercent = 0
            )
        }

        val calendar = GregorianCalendar(year, month - 1, 1)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

        val targetStaffKey = normalizeStaffKey(staffName)
        val logsByDate = logs
            .filter { log ->
                log.monthKey == monthKey &&
                    normalizeStaffKey(log.staffName) == targetStaffKey
            }
            .groupBy { it.dateKey }
            .mapValues { (_, dayLogs) ->
                dayLogs.maxByOrNull { it.updatedAt } ?: dayLogs.first()
            }

        val monthStartDateKey = String.format(Locale.US, "%04d-%02d-01", year, month)
        val todayMonthKey = if (todayDateKey.length >= 7) todayDateKey.substring(0, 7) else ""
        val effectiveStartDateKey = when {
            trackingStartDateKey.isNullOrBlank() -> monthStartDateKey
            monthKey < todayMonthKey -> monthStartDateKey
            else -> maxOf(monthStartDateKey, trackingStartDateKey)
        }

        var presentDays = 0
        var absentDays = 0
        var completedShifts = 0
        var totalWorkedMinutes = 0

        val calendarDays = (1..daysInMonth).map { day ->
            val dateKey = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
            val log = logsByDate[dateKey]
            val dayCal = GregorianCalendar(year, month - 1, day)

            val status = when {
                dateKey > todayDateKey -> DayAttendanceStatus.UPCOMING
                log != null && log.checkOutTimestamp > 0L -> {
                    presentDays++
                    completedShifts++
                    val worked = if (log.workedMinutes > 0) {
                        log.workedMinutes
                    } else {
                        calculateWorkedMinutes(log.checkInTimestamp, log.checkOutTimestamp)
                    }
                    totalWorkedMinutes += worked
                    DayAttendanceStatus.PRESENT
                }
                log != null && log.checkInTimestamp > 0L -> {
                    presentDays++
                    DayAttendanceStatus.ON_DUTY
                }
                dateKey < effectiveStartDateKey -> DayAttendanceStatus.NOT_JOINED
                weeklyOffDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY &&
                    dayCal.get(Calendar.DAY_OF_WEEK) == weeklyOffDayOfWeek -> DayAttendanceStatus.WEEKLY_OFF
                else -> {
                    absentDays++
                    DayAttendanceStatus.ABSENT
                }
            }

            AttendanceCalendarDay(
                dateKey = dateKey,
                dayOfMonth = day,
                status = status,
                log = log
            )
        }

        val totalEvaluated = presentDays + absentDays
        val attendancePercent = if (totalEvaluated == 0) {
            0
        } else {
            (presentDays * 100) / totalEvaluated
        }

        return MonthlyAttendanceSummary(
            monthKey = monthKey,
            staffName = staffName,
            days = calendarDays,
            presentDays = presentDays,
            absentDays = absentDays,
            completedShifts = completedShifts,
            totalWorkedMinutes = totalWorkedMinutes,
            attendancePercent = attendancePercent
        )
    }
}

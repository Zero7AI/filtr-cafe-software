package com.example.domain.attendance

import com.example.data.entity.AttendanceLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class AttendanceDomainTest {

    private val storeLat = 12.9716
    private val storeLng = 77.5946

    private fun offsetLat(baseLat: Double, northMeters: Double): Double {
        return baseLat + (northMeters / 111_320.0)
    }

    private fun offsetLng(baseLat: Double, baseLng: Double, eastMeters: Double): Double {
        return baseLng + (eastMeters / (111_320.0 * cos(Math.toRadians(baseLat))))
    }

    @Test
    fun `normalizeStaffKey and buildLogId normalize casing and punctuation`() {
        assertEquals("rohan_kumar", AttendanceCalculator.normalizeStaffKey("  Rohan   Kumar! "))
        assertEquals("staff", AttendanceCalculator.normalizeStaffKey("   "))
        assertEquals("2026-11-25_asha_nair", AttendanceCalculator.buildLogId("2026-11-25", " Asha Nair "))
    }

    @Test
    fun `haversine distance and geofence evaluate 50m and 100m accurately`() {
        val exactDistance = AttendanceCalculator.calculateDistanceMeters(storeLat, storeLng, storeLat, storeLng)
        assertEquals(0.0, exactDistance, 0.01)

        // ~50m north of store
        val lat50m = offsetLat(storeLat, 50.0)
        val dist50m = AttendanceCalculator.calculateDistanceMeters(storeLat, storeLng, lat50m, storeLng)
        assertEquals(50.0, dist50m, 1.0)

        val config50m = StoreGeofenceConfig(latitude = storeLat, longitude = storeLng, radiusMeters = 50.0)
        val inside45m = AttendanceCalculator.evaluateGeofence(offsetLat(storeLat, 45.0), storeLng, config50m)
        assertTrue(inside45m.isInside)
        assertTrue(inside45m.distanceMeters < 50.0)
        assertEquals(50.0, inside45m.allowedRadiusMeters, 0.001)

        val outside55mOn50m = AttendanceCalculator.evaluateGeofence(offsetLat(storeLat, 55.0), storeLng, config50m)
        assertFalse(outside55mOn50m.isInside)
        assertTrue(outside55mOn50m.distanceMeters > 50.0)

        // ~100m diagonal/east checks
        val config100m = StoreGeofenceConfig(latitude = storeLat, longitude = storeLng, radiusMeters = 100.0)
        val inside95m = AttendanceCalculator.evaluateGeofence(
            storeLat,
            offsetLng(storeLat, storeLng, 95.0),
            config100m
        )
        assertTrue(inside95m.isInside)
        assertEquals(95.0, inside95m.distanceMeters, 1.5)

        val outside110m = AttendanceCalculator.evaluateGeofence(
            storeLat,
            offsetLng(storeLat, storeLng, 110.0),
            config100m
        )
        assertFalse(outside110m.isInside)
        assertEquals(110.0, outside110m.distanceMeters, 1.5)
    }

    @Test
    fun `unconfigured store location rejects geofence even at origin`() {
        val unconfigured = StoreGeofenceConfig()
        assertFalse(unconfigured.isConfigured)
        val result = AttendanceCalculator.evaluateGeofence(0.0, 0.0, unconfigured)
        assertFalse(result.isInside)
    }

    @Test
    fun `worked duration calculates minutes and formats hours cleanly`() {
        val checkIn = 1_700_000_000_000L
        val checkOut9h15m = checkIn + ((9 * 60 + 15) * 60_000L)
        val checkOut8h = checkIn + (8 * 60 * 60_000L)
        val checkOut45m = checkIn + (45 * 60_000L)

        assertEquals(555, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkOut9h15m))
        assertEquals("9h 15m", AttendanceCalculator.formatWorkedDuration(555))

        assertEquals(480, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkOut8h))
        assertEquals("8h", AttendanceCalculator.formatWorkedDuration(480))

        assertEquals(45, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkOut45m))
        assertEquals("45m", AttendanceCalculator.formatWorkedDuration(45))

        assertEquals(0, AttendanceCalculator.calculateWorkedMinutes(0L, checkOut45m))
        assertEquals(0, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkIn))
        assertEquals(0, AttendanceCalculator.calculateWorkedMinutes(checkIn, checkIn - 60_000L))
        assertEquals("0m", AttendanceCalculator.formatWorkedDuration(-10))
    }

    @Test
    fun `monthly summary computes GREEN PRESENT ON_DUTY RED ABSENT and UPCOMING days`() {
        val logs = listOf(
            // Day 10: completed shift (8h = 480m) -> PRESENT (GREEN)
            AttendanceLogEntity(
                id = "2026-11-10_rohan",
                dateKey = "2026-11-10",
                monthKey = "2026-11",
                staffName = "Rohan",
                checkInTimestamp = 1_000_000L,
                checkOutTimestamp = 1_000_000L + 480 * 60_000L,
                workedMinutes = 480,
                updatedAt = 2_000_000L
            ),
            // Day 12: checked in only (today) -> ON_DUTY (counts as present)
            AttendanceLogEntity(
                id = "2026-11-12_rohan",
                dateKey = "2026-11-12",
                monthKey = "2026-11",
                staffName = "  rohan ",
                checkInTimestamp = 3_000_000L,
                checkOutTimestamp = 0L,
                workedMinutes = 0,
                updatedAt = 3_000_000L
            ),
            // Different staff member on day 11 should NOT count for Rohan
            AttendanceLogEntity(
                id = "2026-11-11_asha",
                dateKey = "2026-11-11",
                monthKey = "2026-11",
                staffName = "Asha",
                checkInTimestamp = 1_000_000L,
                checkOutTimestamp = 1_000_000L + 240 * 60_000L,
                workedMinutes = 240,
                updatedAt = 2_000_000L
            )
        )

        // Tracking started on 2026-11-10, today is 2026-11-12:
        // Days 1..9 -> UPCOMING, Day 10 -> PRESENT, Day 11 -> ABSENT (RED), Day 12 -> ON_DUTY, Days 13..30 -> UPCOMING
        val summary = AttendanceCalculator.buildMonthlySummary(
            monthKey = "2026-11",
            staffName = "Rohan",
            logs = logs,
            trackingStartDateKey = "2026-11-10",
            todayDateKey = "2026-11-12"
        )

        assertEquals(30, summary.days.size)
        assertEquals(DayAttendanceStatus.UPCOMING, summary.days[8].status) // Nov 9
        assertEquals(DayAttendanceStatus.PRESENT, summary.days[9].status)  // Nov 10
        assertEquals(DayAttendanceStatus.ABSENT, summary.days[10].status)  // Nov 11
        assertEquals(DayAttendanceStatus.ON_DUTY, summary.days[11].status) // Nov 12
        assertEquals(DayAttendanceStatus.UPCOMING, summary.days[12].status) // Nov 13

        assertEquals(2, summary.presentDays)
        assertEquals(1, summary.absentDays)
        assertEquals(1, summary.completedShifts)
        assertEquals(480, summary.totalWorkedMinutes)
        assertEquals(66, summary.attendancePercent) // (2 * 100) / 3
    }

    @Test
    fun `monthly summary for past month evaluates from day 1 and handles invalid monthKey`() {
        val pastSummary = AttendanceCalculator.buildMonthlySummary(
            monthKey = "2026-10",
            staffName = "Asha",
            logs = emptyList(),
            trackingStartDateKey = "2026-11-10",
            todayDateKey = "2026-11-12"
        )
        assertEquals(31, pastSummary.days.size)
        assertEquals(0, pastSummary.presentDays)
        assertEquals(31, pastSummary.absentDays)
        assertEquals(0, pastSummary.attendancePercent)
        assertTrue(pastSummary.days.all { it.status == DayAttendanceStatus.ABSENT })

        val invalidSummary = AttendanceCalculator.buildMonthlySummary(
            monthKey = "invalid",
            staffName = "Asha",
            logs = emptyList(),
            trackingStartDateKey = null,
            todayDateKey = "2026-11-12"
        )
        assertTrue(invalidSummary.days.isEmpty())
        assertEquals(0, invalidSummary.attendancePercent)
    }
}

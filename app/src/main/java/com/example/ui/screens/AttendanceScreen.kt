package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import com.example.domain.attendance.AttendanceCalendarDay
import com.example.domain.attendance.DayAttendanceStatus
import com.example.domain.attendance.GeofenceCheckResult
import com.example.domain.attendance.MonthlyAttendanceSummary
import com.example.domain.attendance.StoreGeofenceConfig
import com.example.domain.attendance.TeamRollCallRow
import com.example.domain.attendance.formatDayOfWeekShort
import com.example.domain.kds.KdsBaristaColorPalette
import com.example.util.LocationSample
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val AttendanceBg = Color(0xFFF8FAFC)
private val AttendanceInk = Color(0xFF09090B)
private val AttendanceMuted = Color(0xFF64748B)
private val AttendanceBorder = Color(0xFFE2E8F0)
private val AttendanceGreen = Color(0xFF16A34A)
private val AttendanceGreenBg = Color(0xFFDCFCE7)
private val AttendanceRed = Color(0xFFDC2626)
private val AttendanceRedBg = Color(0xFFFEE2E2)
private val AttendanceAmber = Color(0xFFD97706)
private val AttendanceAmberBg = Color(0xFFFEF3C7)
private val AttendanceOff = Color(0xFF0284C7)
private val AttendanceOffBg = Color(0xFFE0F2FE)
private val AttendanceNeutralBg = Color(0xFFF1F5F9)

private enum class ManagerAttendanceTab(val label: String) {
    ROLL_CALL("Roll Call"),
    CALENDAR("Calendar"),
    ROSTER_GPS("Roster & GPS")
}

/**
 * Unified mobile-first Staff & Manager attendance screen backed by geofenced GPS verification.
 */
@Composable
fun AttendanceScreen(
    isManager: Boolean,
    currentStaffName: String,
    storeConfig: StoreGeofenceConfig,
    hasLocationPermission: Boolean,
    isFetchingLocation: Boolean,
    currentLocation: LocationSample?,
    geofenceResult: GeofenceCheckResult?,
    locationError: String?,
    selectedMonthKey: String,
    availableStaffNames: List<String>,
    selectedManagerStaffName: String,
    personalSummary: MonthlyAttendanceSummary,
    managerSummary: MonthlyAttendanceSummary,
    todayPersonalLog: AttendanceLogEntity?,
    onRequestLocationPermission: () -> Unit,
    onRefreshLocationAndSync: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onSelectManagerStaff: (String) -> Unit,
    onSetStoreLocationHere: () -> Unit,
    onChangeRadiusMeters: (Double) -> Unit,
    staffRoster: List<StaffRosterEntity> = emptyList(),
    teamRollCall: List<TeamRollCallRow> = emptyList(),
    onDutyStaffKeys: Set<String> = emptySet(),
    selectedCheckInStaffName: String = currentStaffName,
    onSelectCheckInStaff: (String) -> Unit = {},
    onSaveStaffRosterMember: (
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onSaveStaffRosterMemberWithColor: ((
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean,
        colorHex: String
    ) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isManagerView by remember { mutableStateOf(false) }
    val effectiveManagerView = isManager && isManagerView

    var selectedManagerTab by remember { mutableStateOf(ManagerAttendanceTab.ROLL_CALL) }
    var selectedStaffDateKey by remember(selectedMonthKey) { mutableStateOf<String?>(null) }
    var selectedManagerDateKey by remember(selectedMonthKey, selectedManagerStaffName) {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AttendanceBg)
    ) {
        AttendanceHeaderCard(
            isManager = isManager,
            isManagerView = effectiveManagerView,
            currentStaffName = currentStaffName,
            isFetchingLocation = isFetchingLocation,
            onToggleView = { isManagerView = it },
            onRefreshLocationAndSync = onRefreshLocationAndSync
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!effectiveManagerView) {
                StaffAttendanceView(
                    storeConfig = storeConfig,
                    hasLocationPermission = hasLocationPermission,
                    isFetchingLocation = isFetchingLocation,
                    currentLocation = currentLocation,
                    geofenceResult = geofenceResult,
                    locationError = locationError,
                    selectedMonthKey = selectedMonthKey,
                    availableStaffNames = availableStaffNames,
                    onDutyStaffKeys = onDutyStaffKeys,
                    staffRosterEmpty = staffRoster.none { it.isActive },
                    selectedCheckInStaffName = selectedCheckInStaffName,
                    personalSummary = personalSummary,
                    todayPersonalLog = todayPersonalLog,
                    selectedDateKey = selectedStaffDateKey,
                    onSelectDateKey = { selectedStaffDateKey = it },
                    onSelectCheckInStaff = onSelectCheckInStaff,
                    onRequestLocationPermission = onRequestLocationPermission,
                    onRefreshLocationAndSync = onRefreshLocationAndSync,
                    onCheckIn = onCheckIn,
                    onCheckOut = onCheckOut,
                    onSelectMonth = onSelectMonth
                )
            } else {
                ManagerAttendanceView(
                    selectedTab = selectedManagerTab,
                    onSelectTab = { selectedManagerTab = it },
                    storeConfig = storeConfig,
                    hasLocationPermission = hasLocationPermission,
                    isFetchingLocation = isFetchingLocation,
                    locationError = locationError,
                    selectedMonthKey = selectedMonthKey,
                    availableStaffNames = availableStaffNames,
                    selectedManagerStaffName = selectedManagerStaffName,
                    managerSummary = managerSummary,
                    staffRoster = staffRoster,
                    teamRollCall = teamRollCall,
                    selectedDateKey = selectedManagerDateKey,
                    onSelectDateKey = { selectedManagerDateKey = it },
                    onRequestLocationPermission = onRequestLocationPermission,
                    onSelectMonth = onSelectMonth,
                    onSelectManagerStaff = onSelectManagerStaff,
                    onSetStoreLocationHere = onSetStoreLocationHere,
                    onChangeRadiusMeters = onChangeRadiusMeters,
                    onSaveStaffRosterMember = { existingId, name, roleTitle, shiftLabel, weeklyOffDayOfWeek, joinedDateKey, isActive, colorHex ->
                        if (onSaveStaffRosterMemberWithColor != null) {
                            onSaveStaffRosterMemberWithColor(
                                existingId,
                                name,
                                roleTitle,
                                shiftLabel,
                                weeklyOffDayOfWeek,
                                joinedDateKey,
                                isActive,
                                colorHex
                            )
                        } else {
                            onSaveStaffRosterMember(
                                existingId,
                                name,
                                roleTitle,
                                shiftLabel,
                                weeklyOffDayOfWeek,
                                joinedDateKey,
                                isActive
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttendanceHeaderCard(
    isManager: Boolean,
    isManagerView: Boolean,
    currentStaffName: String,
    isFetchingLocation: Boolean,
    onToggleView: (Boolean) -> Unit,
    onRefreshLocationAndSync: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FILTR COFFEE • ATTENDANCE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AttendanceMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentStaffName.ifBlank { "Staff Member" },
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AttendanceInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onRefreshLocationAndSync,
                    enabled = !isFetchingLocation,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AttendanceBorder),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    if (isFetchingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AttendanceInk
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh GPS and sync",
                            tint = AttendanceInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFetchingLocation) "Locating" else "Refresh",
                        color = AttendanceInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (isManager) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isManagerView,
                        onClick = { onToggleView(false) },
                        label = {
                            Text(
                                text = "My Attendance",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AttendanceInk,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = AttendanceNeutralBg,
                            labelColor = AttendanceInk,
                            iconColor = AttendanceMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = !isManagerView,
                            borderColor = AttendanceBorder,
                            selectedBorderColor = AttendanceInk
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )

                    FilterChip(
                        selected = isManagerView,
                        onClick = { onToggleView(true) },
                        label = {
                            Text(
                                text = "Manager View",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AttendanceInk,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = AttendanceNeutralBg,
                            labelColor = AttendanceInk,
                            iconColor = AttendanceMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isManagerView,
                            borderColor = AttendanceBorder,
                            selectedBorderColor = AttendanceInk
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StaffAttendanceView(
    storeConfig: StoreGeofenceConfig,
    hasLocationPermission: Boolean,
    isFetchingLocation: Boolean,
    currentLocation: LocationSample?,
    geofenceResult: GeofenceCheckResult?,
    locationError: String?,
    selectedMonthKey: String,
    availableStaffNames: List<String>,
    onDutyStaffKeys: Set<String> = emptySet(),
    staffRosterEmpty: Boolean = false,
    selectedCheckInStaffName: String,
    personalSummary: MonthlyAttendanceSummary,
    todayPersonalLog: AttendanceLogEntity?,
    selectedDateKey: String?,
    onSelectDateKey: (String) -> Unit,
    onSelectCheckInStaff: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRefreshLocationAndSync: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onSelectMonth: (String) -> Unit
) {
    GeofenceStatusCard(
        storeConfig = storeConfig,
        hasLocationPermission = hasLocationPermission,
        isFetchingLocation = isFetchingLocation,
        currentLocation = currentLocation,
        geofenceResult = geofenceResult,
        locationError = locationError,
        onRequestLocationPermission = onRequestLocationPermission,
        onRefreshLocationAndSync = onRefreshLocationAndSync
    )

    CheckInOutCard(
        geofenceResult = geofenceResult,
        todayPersonalLog = todayPersonalLog,
        availableStaffNames = availableStaffNames,
        onDutyStaffKeys = onDutyStaffKeys,
        staffRosterEmpty = staffRosterEmpty,
        selectedCheckInStaffName = selectedCheckInStaffName,
        onSelectCheckInStaff = onSelectCheckInStaff,
        onCheckIn = onCheckIn,
        onCheckOut = onCheckOut
    )

    MonthlyKpiStrip(summary = personalSummary)

    MonthlyCalendarAndDetailSection(
        monthKey = selectedMonthKey,
        summary = personalSummary,
        selectedDateKey = selectedDateKey,
        onSelectMonth = onSelectMonth,
        onSelectDateKey = onSelectDateKey
    )
}

@Composable
private fun GeofenceStatusCard(
    storeConfig: StoreGeofenceConfig,
    hasLocationPermission: Boolean,
    isFetchingLocation: Boolean,
    currentLocation: LocationSample?,
    geofenceResult: GeofenceCheckResult?,
    locationError: String?,
    onRequestLocationPermission: () -> Unit,
    onRefreshLocationAndSync: () -> Unit
) {
    val cardBg: Color
    val cardBorder: Color
    when {
        !hasLocationPermission -> {
            cardBg = Color.White
            cardBorder = AttendanceBorder
        }
        !storeConfig.isConfigured -> {
            cardBg = AttendanceAmberBg.copy(alpha = 0.45f)
            cardBorder = AttendanceAmber
        }
        geofenceResult?.isInside == true -> {
            cardBg = Color.White
            cardBorder = AttendanceGreen
        }
        geofenceResult != null -> {
            cardBg = Color.White
            cardBorder = AttendanceRed
        }
        else -> {
            cardBg = Color.White
            cardBorder = AttendanceBorder
        }
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (hasLocationPermission) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                    contentDescription = null,
                    tint = when {
                        !hasLocationPermission -> AttendanceRed
                        !storeConfig.isConfigured -> AttendanceAmber
                        geofenceResult?.isInside == true -> AttendanceGreen
                        geofenceResult != null -> AttendanceRed
                        else -> AttendanceMuted
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Store Geofence Status",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AttendanceInk
                )
            }

            when {
                !hasLocationPermission -> {
                    Text(
                        text = "Location permission is required to verify you are at ${storeConfig.storeLabel} before checking in or out.",
                        fontSize = 13.sp,
                        color = AttendanceMuted
                    )
                    Button(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AttendanceInk,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Allow Location Access",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                !storeConfig.isConfigured -> {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = AttendanceAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Store GPS coordinates are not set yet. Please ask an Admin / Manager to set the FILTR store location in Manager View.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AttendanceInk
                        )
                    }
                }

                geofenceResult != null -> {
                    val distanceMeters = geofenceResult.distanceMeters.roundToInt()
                    val limitMeters = geofenceResult.allowedRadiusMeters.roundToInt()
                    val isInside = geofenceResult.isInside
                    val pillText = if (isInside) {
                        "Inside FILTR Zone (${distanceMeters}m away • limit ${limitMeters}m)"
                    } else {
                        "Outside FILTR Zone (${distanceMeters}m away — need within ${limitMeters}m)"
                    }

                    Surface(
                        color = if (isInside) AttendanceGreenBg else AttendanceRedBg,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, if (isInside) AttendanceGreen else AttendanceRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isInside) AttendanceGreen else AttendanceRed)
                            )
                            Text(
                                text = pillText,
                                color = if (isInside) AttendanceGreen else AttendanceRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (currentLocation != null) {
                        Text(
                            text = String.format(
                                Locale.US,
                                "GPS accuracy ±%dm • Updated %s",
                                currentLocation.accuracyMeters.roundToInt(),
                                formatTime(currentLocation.capturedAtMs)
                            ),
                            fontSize = 12.sp,
                            color = AttendanceMuted
                        )
                    }
                }

                isFetchingLocation -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AttendanceInk
                        )
                        Text(
                            text = "Fetching current GPS coordinates…",
                            fontSize = 13.sp,
                            color = AttendanceMuted
                        )
                    }
                }

                else -> {
                    Text(
                        text = "GPS fix not acquired yet. Tap Refresh to verify your distance from ${storeConfig.storeLabel}.",
                        fontSize = 13.sp,
                        color = AttendanceMuted
                    )
                    OutlinedButton(
                        onClick = onRefreshLocationAndSync,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Check GPS Now",
                            color = AttendanceInk,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!locationError.isNullOrBlank()) {
                Surface(
                    color = AttendanceRedBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = locationError,
                        color = AttendanceRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckInOutCard(
    geofenceResult: GeofenceCheckResult?,
    todayPersonalLog: AttendanceLogEntity?,
    availableStaffNames: List<String>,
    onDutyStaffKeys: Set<String> = emptySet(),
    staffRosterEmpty: Boolean = false,
    selectedCheckInStaffName: String,
    onSelectCheckInStaff: (String) -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    val isInsideZone = geofenceResult?.isInside == true
    val canCheckIn = isInsideZone && todayPersonalLog == null
    val canCheckOut = isInsideZone &&
        (todayPersonalLog?.checkInTimestamp ?: 0L) > 0L &&
        todayPersonalLog?.checkOutTimestamp == 0L

    val shiftStatusLabel: String
    val shiftStatusColor: Color
    val shiftStatusBg: Color
    when {
        todayPersonalLog == null || todayPersonalLog.checkInTimestamp <= 0L -> {
            shiftStatusLabel = "Not Checked In"
            shiftStatusColor = AttendanceMuted
            shiftStatusBg = AttendanceNeutralBg
        }
        todayPersonalLog.checkOutTimestamp == 0L -> {
            shiftStatusLabel = "On Duty"
            shiftStatusColor = AttendanceGreen
            shiftStatusBg = AttendanceGreenBg
        }
        else -> {
            shiftStatusLabel = "Shift Completed"
            shiftStatusColor = AttendanceGreen
            shiftStatusBg = AttendanceGreenBg
        }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = AttendanceInk,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Today's Shift",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceInk
                    )
                }

                Surface(
                    color = shiftStatusBg,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = shiftStatusLabel,
                        color = shiftStatusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (todayPersonalLog != null) {
                val workedMinutes = resolveWorkedMinutes(todayPersonalLog)
                val workedText = if (todayPersonalLog.checkOutTimestamp > 0L) {
                    AttendanceCalculator.formatWorkedDuration(workedMinutes)
                } else {
                    "In progress"
                }

                Surface(
                    color = AttendanceBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AttendanceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ShiftMetricColumn(
                            label = "Check-In",
                            value = formatTime(todayPersonalLog.checkInTimestamp),
                            modifier = Modifier.weight(1f)
                        )
                        ShiftMetricColumn(
                            label = "Check-Out",
                            value = formatTime(todayPersonalLog.checkOutTimestamp),
                            modifier = Modifier.weight(1f)
                        )
                        ShiftMetricColumn(
                            label = "Worked",
                            value = workedText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Text(
                    text = "No shift punch recorded for today yet.",
                    fontSize = 13.sp,
                    color = AttendanceMuted
                )
            }

            if (availableStaffNames.size > 1 || staffRosterEmpty) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (staffRosterEmpty) {
                            "Checking in as (add full team in Manager View → Roster & GPS):"
                        } else {
                            "Checking in as:"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AttendanceMuted
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableStaffNames) { staffName ->
                            val isSelected = staffName.equals(selectedCheckInStaffName, ignoreCase = true)
                            val isOnDuty = AttendanceCalculator.normalizeStaffKey(staffName) in onDutyStaffKeys
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectCheckInStaff(staffName) },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        if (isOnDuty) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color(0xFF86EFAC) else AttendanceGreen)
                                            )
                                        }
                                        Text(
                                            text = staffName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1
                                        )
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AttendanceInk,
                                    selectedLabelColor = Color.White,
                                    containerColor = AttendanceNeutralBg,
                                    labelColor = AttendanceInk
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = AttendanceBorder,
                                    selectedBorderColor = AttendanceInk
                                ),
                                modifier = Modifier.height(36.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCheckIn,
                    enabled = canCheckIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AttendanceGreen,
                        contentColor = Color.White,
                        disabledContainerColor = AttendanceNeutralBg,
                        disabledContentColor = AttendanceMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHECK IN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                Button(
                    onClick = onCheckOut,
                    enabled = canCheckOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AttendanceRed,
                        contentColor = Color.White,
                        disabledContainerColor = AttendanceNeutralBg,
                        disabledContentColor = AttendanceMuted
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHECK OUT (END SHIFT)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.4.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiftMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AttendanceMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AttendanceInk
        )
    }
}

@Composable
private fun MonthlyKpiStrip(summary: MonthlyAttendanceSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                label = "Present Days",
                value = summary.presentDays.toString(),
                accentColor = AttendanceGreen,
                fillColor = AttendanceGreenBg.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Absent Days",
                value = summary.absentDays.toString(),
                accentColor = AttendanceRed,
                fillColor = AttendanceRedBg.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiCard(
                label = "Attendance %",
                value = "${summary.attendancePercent}%",
                accentColor = AttendanceInk,
                fillColor = Color.White,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "Total Hours",
                value = AttendanceCalculator.formatWorkedDuration(summary.totalWorkedMinutes),
                accentColor = AttendanceInk,
                fillColor = Color.White,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    accentColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = fillColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (fillColor == Color.White) AttendanceBorder else accentColor.copy(alpha = 0.45f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AttendanceMuted
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor
            )
        }
    }
}

@Composable
private fun MonthlyCalendarAndDetailSection(
    monthKey: String,
    summary: MonthlyAttendanceSummary,
    selectedDateKey: String?,
    onSelectMonth: (String) -> Unit,
    onSelectDateKey: (String) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month < yyyy-MM > switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint = AttendanceInk,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Monthly Calendar",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceInk
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onSelectMonth(shiftMonthKey(monthKey, -1)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = AttendanceInk
                        )
                    }
                    Surface(
                        color = AttendanceNeutralBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = monthKey,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AttendanceInk,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSelectMonth(shiftMonthKey(monthKey, 1)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = AttendanceInk
                        )
                    }
                }
            }

            // Legend
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalendarLegendItem(label = "Present / On Duty", color = AttendanceGreen)
                CalendarLegendItem(label = "Absent", color = AttendanceRed)
                CalendarLegendItem(label = "Off", color = AttendanceOff)
                CalendarLegendItem(label = "Upcoming", color = AttendanceMuted)
            }

            // Weekday headers
            val weekdays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                weekdays.forEach { dayName ->
                    Text(
                        text = dayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 7-column calendar grid using GregorianCalendar offset
            val firstDayOffset = remember(monthKey) { calculateFirstWeekdayOffset(monthKey) }
            val gridCells = remember(summary.days, firstDayOffset) {
                buildList<AttendanceCalendarDay?> {
                    repeat(firstDayOffset) { add(null) }
                    addAll(summary.days)
                    val remainder = size % 7
                    if (remainder != 0) {
                        repeat(7 - remainder) { add(null) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                gridCells.chunked(7).forEach { weekRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weekRow.forEach { day ->
                            if (day == null) {
                                Spacer(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                )
                            } else {
                                CalendarDayCell(
                                    day = day,
                                    isSelected = day.dateKey == selectedDateKey,
                                    onClick = { onSelectDateKey(day.dateKey) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            val selectedDay = summary.days.firstOrNull { it.dateKey == selectedDateKey }
            if (selectedDay != null) {
                HorizontalDivider(color = AttendanceBorder)
                AttendanceDayDetailCard(day = selectedDay)
            } else {
                Text(
                    text = "Tap any day above to view check-in, check-out, and hours worked.",
                    fontSize = 12.sp,
                    color = AttendanceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AttendanceMuted
        )
    }
}

private data class StatusVisuals(
    val label: String,
    val color: Color,
    val bgColor: Color,
    val borderColor: Color
)

private fun resolveDayStatusVisuals(status: DayAttendanceStatus): StatusVisuals = when (status) {
    DayAttendanceStatus.PRESENT -> StatusVisuals(
        label = "Present",
        color = AttendanceGreen,
        bgColor = AttendanceGreenBg,
        borderColor = AttendanceGreen
    )
    DayAttendanceStatus.ON_DUTY -> StatusVisuals(
        label = "On Duty",
        color = AttendanceGreen,
        bgColor = AttendanceGreenBg,
        borderColor = AttendanceGreen
    )
    DayAttendanceStatus.ABSENT -> StatusVisuals(
        label = "Absent",
        color = AttendanceRed,
        bgColor = AttendanceRedBg,
        borderColor = AttendanceRed
    )
    DayAttendanceStatus.WEEKLY_OFF -> StatusVisuals(
        label = "Weekly Off",
        color = AttendanceOff,
        bgColor = AttendanceOffBg,
        borderColor = AttendanceOff
    )
    DayAttendanceStatus.NOT_JOINED -> StatusVisuals(
        label = "Before Start",
        color = AttendanceMuted,
        bgColor = AttendanceNeutralBg,
        borderColor = AttendanceBorder
    )
    DayAttendanceStatus.UPCOMING -> StatusVisuals(
        label = "Upcoming",
        color = AttendanceMuted,
        bgColor = AttendanceNeutralBg,
        borderColor = AttendanceBorder
    )
}

@Composable
private fun CalendarDayCell(
    day: AttendanceCalendarDay,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = resolveDayStatusVisuals(day.status)
    val isSubtleNeutral = day.status == DayAttendanceStatus.UPCOMING ||
        day.status == DayAttendanceStatus.NOT_JOINED
    val textColor = if (day.status == DayAttendanceStatus.NOT_JOINED) {
        AttendanceMuted.copy(alpha = 0.72f)
    } else {
        visuals.color
    }

    val finalBorderColor = if (isSelected) AttendanceInk else visuals.borderColor
    val finalBorderWidth = when {
        isSelected -> 2.5.dp
        isSubtleNeutral -> 1.dp
        else -> 1.5.dp
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(visuals.bgColor)
            .border(finalBorderWidth, finalBorderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSubtleNeutral) FontWeight.Medium else FontWeight.ExtraBold,
                color = textColor
            )
            if (day.status == DayAttendanceStatus.ON_DUTY) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AttendanceGreen)
                )
            }
        }
    }
}

@Composable
private fun AttendanceDayDetailCard(day: AttendanceCalendarDay) {
    val visuals = resolveDayStatusVisuals(day.status)

    Surface(
        color = AttendanceBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.dateKey,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AttendanceInk
                )
                Surface(
                    color = visuals.bgColor,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = visuals.label,
                        color = visuals.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            val log = day.log
            when (day.status) {
                DayAttendanceStatus.PRESENT, DayAttendanceStatus.ON_DUTY -> {
                    if (log != null) {
                        val checkInMeters = "${log.checkInDistanceMeters.roundToInt()}m"
                        val checkOutText = if (log.checkOutTimestamp > 0L) {
                            "${formatTime(log.checkOutTimestamp)} (${log.checkOutDistanceMeters.roundToInt()}m)"
                        } else {
                            "Active shift"
                        }
                        val workedText = if (log.checkOutTimestamp > 0L) {
                            AttendanceCalculator.formatWorkedDuration(resolveWorkedMinutes(log))
                        } else {
                            "In progress"
                        }

                        DetailRow(label = "Check-In", value = "${formatTime(log.checkInTimestamp)} ($checkInMeters)")
                        DetailRow(label = "Check-Out", value = checkOutText)
                        DetailRow(label = "Hours Worked", value = workedText)
                    } else {
                        Text(
                            text = "${visuals.label} — Shift recorded.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = visuals.color
                        )
                    }
                }

                DayAttendanceStatus.ABSENT -> {
                    Text(
                        text = "Absent — No check-in was recorded on this date.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AttendanceRed
                    )
                }

                DayAttendanceStatus.WEEKLY_OFF -> {
                    Text(
                        text = "Weekly Off — Scheduled day off, not counted as absent.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = AttendanceOff
                    )
                }

                DayAttendanceStatus.NOT_JOINED -> {
                    Text(
                        text = "Before Start — Attendance tracking started after this date.",
                        fontSize = 13.sp,
                        color = AttendanceMuted
                    )
                }

                DayAttendanceStatus.UPCOMING -> {
                    Text(
                        text = "Upcoming date — attendance not evaluated yet.",
                        fontSize = 13.sp,
                        color = AttendanceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = AttendanceMuted
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AttendanceInk
        )
    }
}

@Composable
private fun ManagerAttendanceView(
    selectedTab: ManagerAttendanceTab,
    onSelectTab: (ManagerAttendanceTab) -> Unit,
    storeConfig: StoreGeofenceConfig,
    hasLocationPermission: Boolean,
    isFetchingLocation: Boolean,
    locationError: String?,
    selectedMonthKey: String,
    availableStaffNames: List<String>,
    selectedManagerStaffName: String,
    managerSummary: MonthlyAttendanceSummary,
    staffRoster: List<StaffRosterEntity>,
    teamRollCall: List<TeamRollCallRow>,
    selectedDateKey: String?,
    onSelectDateKey: (String) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onSelectMonth: (String) -> Unit,
    onSelectManagerStaff: (String) -> Unit,
    onSetStoreLocationHere: () -> Unit,
    onChangeRadiusMeters: (Double) -> Unit,
    onSaveStaffRosterMember: (
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean,
        colorHex: String
    ) -> Unit
) {
    ManagerSubTabBar(
        selectedTab = selectedTab,
        onSelectTab = onSelectTab
    )

    when (selectedTab) {
        ManagerAttendanceTab.ROLL_CALL -> {
            ManagerRollCallSection(
                teamRollCall = teamRollCall,
                onSelectStaffForCalendar = { staffName ->
                    onSelectManagerStaff(staffName)
                    onSelectTab(ManagerAttendanceTab.CALENDAR)
                },
                onNavigateToRoster = { onSelectTab(ManagerAttendanceTab.ROSTER_GPS) }
            )
        }

        ManagerAttendanceTab.CALENDAR -> {
            StaffSelectorRow(
                availableStaffNames = availableStaffNames,
                selectedManagerStaffName = selectedManagerStaffName,
                onSelectManagerStaff = onSelectManagerStaff
            )

            MonthlyKpiStrip(summary = managerSummary)

            MonthlyCalendarAndDetailSection(
                monthKey = selectedMonthKey,
                summary = managerSummary,
                selectedDateKey = selectedDateKey,
                onSelectMonth = onSelectMonth,
                onSelectDateKey = onSelectDateKey
            )

            DailyLogsCard(
                staffName = selectedManagerStaffName.ifBlank { managerSummary.staffName },
                days = managerSummary.days
            )
        }

        ManagerAttendanceTab.ROSTER_GPS -> {
            StaffRosterManagementCard(
                staffRoster = staffRoster,
                onSaveStaffRosterMember = onSaveStaffRosterMember
            )

            StoreGeofenceSettingsCard(
                storeConfig = storeConfig,
                hasLocationPermission = hasLocationPermission,
                isFetchingLocation = isFetchingLocation,
                locationError = locationError,
                onRequestLocationPermission = onRequestLocationPermission,
                onSetStoreLocationHere = onSetStoreLocationHere,
                onChangeRadiusMeters = onChangeRadiusMeters
            )
        }
    }
}

@Composable
private fun ManagerSubTabBar(
    selectedTab: ManagerAttendanceTab,
    onSelectTab: (ManagerAttendanceTab) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ManagerAttendanceTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                Surface(
                    color = if (isSelected) AttendanceInk else Color.Transparent,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else AttendanceMuted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerRollCallSection(
    teamRollCall: List<TeamRollCallRow>,
    onSelectStaffForCalendar: (String) -> Unit,
    onNavigateToRoster: () -> Unit
) {
    if (teamRollCall.isEmpty()) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AttendanceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = null,
                    tint = AttendanceMuted,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "No staff added to Roster yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AttendanceInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Add your team members in Roster & GPS to track today's live check-ins, weekly offs, and monthly attendance.",
                    fontSize = 13.sp,
                    color = AttendanceMuted,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onNavigateToRoster,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AttendanceInk,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Go to Roster & GPS",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        return
    }

    val presentCount = teamRollCall.count {
        it.todayStatus == DayAttendanceStatus.PRESENT || it.todayStatus == DayAttendanceStatus.ON_DUTY
    }
    val offCount = teamRollCall.count { it.todayStatus == DayAttendanceStatus.WEEKLY_OFF }
    val absentCount = teamRollCall.count { it.todayStatus == DayAttendanceStatus.ABSENT }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Roll Call (${teamRollCall.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AttendanceInk
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RollCallCountPill(label = "$presentCount In", color = AttendanceGreen, bgColor = AttendanceGreenBg)
                if (offCount > 0) {
                    RollCallCountPill(label = "$offCount Off", color = AttendanceOff, bgColor = AttendanceOffBg)
                }
                RollCallCountPill(label = "$absentCount Abs", color = AttendanceRed, bgColor = AttendanceRedBg)
            }
        }
    }

    teamRollCall.forEach { row ->
        RollCallEmployeeCard(
            row = row,
            onClick = { onSelectStaffForCalendar(row.member.name) }
        )
    }
}

@Composable
private fun RollCallCountPill(
    label: String,
    color: Color,
    bgColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RollCallEmployeeCard(
    row: TeamRollCallRow,
    onClick: () -> Unit
) {
    val visuals = resolveDayStatusVisuals(row.todayStatus)
    val todayLog = row.todayLog
    val arrivalText = if (todayLog != null && todayLog.checkInTimestamp > 0L) {
        formatTime(todayLog.checkInTimestamp)
    } else {
        "—"
    }
    val departureText = if (todayLog != null && todayLog.checkOutTimestamp > 0L) {
        formatTime(todayLog.checkOutTimestamp)
    } else {
        "—"
    }
    val workedText = when {
        todayLog == null || todayLog.checkInTimestamp <= 0L -> "—"
        todayLog.checkOutTimestamp > 0L -> AttendanceCalculator.formatWorkedDuration(resolveWorkedMinutes(todayLog))
        else -> "On Duty"
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.member.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val offSuffix = if (row.member.weeklyOffDayOfWeek != 0) {
                        " • Off: ${formatDayOfWeekShort(row.member.weeklyOffDayOfWeek)}"
                    } else {
                        ""
                    }
                    Text(
                        text = "${row.member.roleTitle} • ${row.member.shiftLabel}$offSuffix",
                        fontSize = 12.sp,
                        color = AttendanceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = visuals.bgColor,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, visuals.borderColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = visuals.label,
                        color = visuals.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Surface(
                color = AttendanceBg,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AttendanceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ShiftMetricColumn(
                        label = "Arrival",
                        value = arrivalText,
                        modifier = Modifier.weight(1f)
                    )
                    ShiftMetricColumn(
                        label = "Departure",
                        value = departureText,
                        modifier = Modifier.weight(1f)
                    )
                    ShiftMetricColumn(
                        label = "Worked",
                        value = workedText,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Present: ${row.monthlySummary.presentDays}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceGreen
                    )
                    Text(
                        text = "Absent: ${row.monthlySummary.absentDays}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceRed
                    )
                    Text(
                        text = "${row.monthlySummary.attendancePercent}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AttendanceInk
                    )
                }
                Text(
                    text = "Calendar ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AttendanceMuted
                )
            }
        }
    }
}

@Composable
private fun StaffRosterManagementCard(
    staffRoster: List<StaffRosterEntity>,
    onSaveStaffRosterMember: (
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean,
        colorHex: String
    ) -> Unit
) {
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<StaffRosterEntity?>(null) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Staff Roster",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AttendanceInk
                    )
                    Text(
                        text = "${staffRoster.size} ${if (staffRoster.size == 1) "member" else "members"} configured",
                        fontSize = 12.sp,
                        color = AttendanceMuted
                    )
                }

                Button(
                    onClick = {
                        editingMember = null
                        showEditorDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AttendanceInk,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Staff",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (staffRoster.isEmpty()) {
                Surface(
                    color = AttendanceBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AttendanceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No roster members yet. Tap + Add Staff to add baristas, kitchen staff, or helpers.",
                        fontSize = 13.sp,
                        color = AttendanceMuted,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    staffRoster.forEachIndexed { index, member ->
                        if (index > 0) {
                            HorizontalDivider(color = AttendanceBorder)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    editingMember = member
                                    showEditorDialog = true
                                }
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val dotColor = Color(
                                        KdsBaristaColorPalette.parseComposeColor(
                                            KdsBaristaColorPalette.resolveColorHex(member.name, member.colorHex)
                                        )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                    Text(
                                        text = member.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AttendanceInk,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        color = if (member.isActive) AttendanceGreenBg else AttendanceNeutralBg,
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = if (member.isActive) "Active" else "Inactive",
                                            color = if (member.isActive) AttendanceGreen else AttendanceMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${member.roleTitle} • ${member.shiftLabel} • Off: ${formatDayOfWeekShort(member.weeklyOffDayOfWeek)}",
                                    fontSize = 12.sp,
                                    color = AttendanceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Joined: ${member.joinedDateKey.ifBlank { "—" }}",
                                    fontSize = 11.sp,
                                    color = AttendanceMuted
                                )
                            }

                            IconButton(
                                onClick = {
                                    editingMember = member
                                    showEditorDialog = true
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit ${member.name}",
                                    tint = AttendanceInk,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditorDialog) {
        StaffRosterEditorDialog(
            existingMember = editingMember,
            onDismiss = { showEditorDialog = false },
            onSave = { existingId, name, roleTitle, shiftLabel, weeklyOffDayOfWeek, joinedDateKey, isActive, colorHex ->
                onSaveStaffRosterMember(
                    existingId,
                    name,
                    roleTitle,
                    shiftLabel,
                    weeklyOffDayOfWeek,
                    joinedDateKey,
                    isActive,
                    colorHex
                )
                showEditorDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffRosterEditorDialog(
    existingMember: StaffRosterEntity?,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean,
        colorHex: String
    ) -> Unit
) {
    val todayDateKey = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var name by remember(existingMember) { mutableStateOf(existingMember?.name.orEmpty()) }
    var roleTitle by remember(existingMember) {
        mutableStateOf(existingMember?.roleTitle?.ifBlank { "Barista" } ?: "Barista")
    }
    var shiftLabel by remember(existingMember) {
        mutableStateOf(existingMember?.shiftLabel?.ifBlank { "Full Day" } ?: "Full Day")
    }
    var weeklyOffDayOfWeek by remember(existingMember) {
        mutableStateOf(existingMember?.weeklyOffDayOfWeek ?: 0)
    }
    var joinedDateKey by remember(existingMember) {
        mutableStateOf(existingMember?.joinedDateKey?.ifBlank { todayDateKey } ?: todayDateKey)
    }
    var isActive by remember(existingMember) { mutableStateOf(existingMember?.isActive ?: true) }
    var selectedColorHex by remember(existingMember) {
        mutableStateOf(
            existingMember?.colorHex?.takeIf { it.isNotBlank() }
                ?: KdsBaristaColorPalette.resolveColorHex(existingMember?.name.orEmpty())
        )
    }

    val roleOptions = remember(roleTitle) {
        val base = listOf("Barista", "Kitchen", "Manager", "Helper")
        if (roleTitle.isNotBlank() && roleTitle !in base) base + roleTitle else base
    }
    val shiftOptions = remember(shiftLabel) {
        val base = listOf("Morning", "Evening", "Full Day")
        if (shiftLabel.isNotBlank() && shiftLabel !in base) base + shiftLabel else base
    }
    val weeklyOffOptions = remember {
        listOf(
            0 to "None",
            Calendar.SUNDAY to "Sun",
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
    }

    val trimmedJoined = joinedDateKey.trim()
    val isJoinDateValid = trimmedJoined.isEmpty() || trimmedJoined.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    val canSave = name.trim().isNotEmpty() && isJoinDateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = if (existingMember == null) "Add / Edit Staff Member" else "Edit ${existingMember.name}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AttendanceInk
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Staff Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Role",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AttendanceMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        roleOptions.forEach { option ->
                            RosterDialogChip(
                                label = option,
                                selected = roleTitle == option,
                                onClick = { roleTitle = option }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Shift",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AttendanceMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        shiftOptions.forEach { option ->
                            RosterDialogChip(
                                label = option,
                                selected = shiftLabel == option,
                                onClick = { shiftLabel = option }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Weekly Off",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AttendanceMuted
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        weeklyOffOptions.forEach { (dayCode, label) ->
                            RosterDialogChip(
                                label = label,
                                selected = weeklyOffDayOfWeek == dayCode,
                                onClick = { weeklyOffDayOfWeek = dayCode }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = joinedDateKey,
                    onValueChange = { joinedDateKey = it },
                    label = { Text("Join Date (yyyy-MM-dd)") },
                    isError = !isJoinDateValid,
                    supportingText = if (!isJoinDateValid) {
                        { Text("Use yyyy-MM-dd format (e.g. $todayDateKey)") }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "KDS & Attendance Badge Color",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AttendanceMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KdsBaristaColorPalette.COLORS.forEach { hex ->
                            val swatchColor = Color(KdsBaristaColorPalette.parseComposeColor(hex))
                            val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 30.dp else 26.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) AttendanceInk else Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorHex = hex }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Active Staff Member",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AttendanceInk
                        )
                        Text(
                            text = "Syncs across Roll Call, Check-In & KDS",
                            fontSize = 12.sp,
                            color = AttendanceMuted
                        )
                    }
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        existingMember?.id,
                        name.trim(),
                        roleTitle,
                        shiftLabel,
                        weeklyOffDayOfWeek,
                        trimmedJoined.ifBlank { todayDateKey },
                        isActive,
                        selectedColorHex
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AttendanceInk,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel", color = AttendanceMuted, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun RosterDialogChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AttendanceInk,
            selectedLabelColor = Color.White,
            containerColor = AttendanceNeutralBg,
            labelColor = AttendanceInk
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = AttendanceBorder,
            selectedBorderColor = AttendanceInk
        ),
        modifier = Modifier.height(34.dp)
    )
}

@Composable
private fun StoreGeofenceSettingsCard(
    storeConfig: StoreGeofenceConfig,
    hasLocationPermission: Boolean,
    isFetchingLocation: Boolean,
    locationError: String?,
    onRequestLocationPermission: () -> Unit,
    onSetStoreLocationHere: () -> Unit,
    onChangeRadiusMeters: (Double) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = AttendanceInk,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Store Geofence Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AttendanceInk
                )
            }

            Surface(
                color = AttendanceBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AttendanceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (storeConfig.isConfigured) {
                            String.format(
                                Locale.US,
                                "Store GPS: %.6f, %.6f",
                                storeConfig.latitude,
                                storeConfig.longitude
                            )
                        } else {
                            "Store GPS: Not configured"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (storeConfig.isConfigured) AttendanceInk else AttendanceAmber
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "Allowed Radius: %d m",
                            storeConfig.radiusMeters.roundToInt()
                        ),
                        fontSize = 12.sp,
                        color = AttendanceMuted
                    )
                }
            }

            Text(
                text = "Geofence Radius Preset",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AttendanceMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadiusPresetButton(
                    label = "50 m",
                    isSelected = abs(storeConfig.radiusMeters - 50.0) < 0.5,
                    onClick = { onChangeRadiusMeters(50.0) },
                    modifier = Modifier.weight(1f)
                )
                RadiusPresetButton(
                    label = "100 m",
                    isSelected = abs(storeConfig.radiusMeters - 100.0) < 0.5,
                    onClick = { onChangeRadiusMeters(100.0) },
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onSetStoreLocationHere,
                enabled = hasLocationPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AttendanceInk,
                    contentColor = Color.White,
                    disabledContainerColor = AttendanceNeutralBg,
                    disabledContentColor = AttendanceMuted
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isFetchingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "Set Current GPS as FILTR Store",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!hasLocationPermission) {
                OutlinedButton(
                    onClick = onRequestLocationPermission,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Allow Location Access First",
                        color = AttendanceInk,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!locationError.isNullOrBlank()) {
                Text(
                    text = locationError,
                    fontSize = 12.sp,
                    color = AttendanceRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RadiusPresetButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = AttendanceInk,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.height(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, AttendanceBorder),
            modifier = modifier.height(48.dp)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AttendanceInk
            )
        }
    }
}

@Composable
private fun StaffSelectorRow(
    availableStaffNames: List<String>,
    selectedManagerStaffName: String,
    onSelectManagerStaff: (String) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Staff Member",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AttendanceInk,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (availableStaffNames.isEmpty()) {
                Text(
                    text = "No staff attendance records available yet.",
                    fontSize = 13.sp,
                    color = AttendanceMuted,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableStaffNames) { staffName ->
                        val isSelected = staffName == selectedManagerStaffName
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectManagerStaff(staffName) },
                            label = {
                                Text(
                                    text = staffName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AttendanceInk,
                                selectedLabelColor = Color.White,
                                containerColor = AttendanceNeutralBg,
                                labelColor = AttendanceInk
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = AttendanceBorder,
                                selectedBorderColor = AttendanceInk
                            ),
                            modifier = Modifier.height(42.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyLogsCard(
    staffName: String,
    days: List<AttendanceCalendarDay>
) {
    val recordedDays = remember(days) {
        days.filter { it.log != null }.sortedByDescending { it.dateKey }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AttendanceBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (staffName.isNotBlank()) "Daily Logs • $staffName" else "Daily Logs",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AttendanceInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${recordedDays.size} logs",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AttendanceMuted
                )
            }

            if (recordedDays.isEmpty()) {
                Text(
                    text = "No punch logs found for this month.",
                    fontSize = 13.sp,
                    color = AttendanceMuted
                )
            } else {
                recordedDays.forEachIndexed { index, day ->
                    val log = day.log ?: return@forEachIndexed
                    if (index > 0) {
                        HorizontalDivider(color = AttendanceBorder)
                    }
                    DailyLogItemRow(day = day, log = log)
                }
            }
        }
    }
}

@Composable
private fun DailyLogItemRow(
    day: AttendanceCalendarDay,
    log: AttendanceLogEntity
) {
    val isCompleted = log.checkOutTimestamp > 0L
    val workedText = if (isCompleted) {
        AttendanceCalculator.formatWorkedDuration(resolveWorkedMinutes(log))
    } else {
        "On Duty"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = day.dateKey,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = AttendanceInk
            )
            Surface(
                color = AttendanceGreenBg,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = workedText,
                    color = AttendanceGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "In: ${formatTime(log.checkInTimestamp)} (${log.checkInDistanceMeters.roundToInt()}m)",
                fontSize = 12.sp,
                color = AttendanceMuted
            )
            Text(
                text = if (isCompleted) {
                    "Out: ${formatTime(log.checkOutTimestamp)} (${log.checkOutDistanceMeters.roundToInt()}m)"
                } else {
                    "Out: —"
                },
                fontSize = 12.sp,
                color = AttendanceMuted
            )
        }
    }
}

private fun resolveWorkedMinutes(log: AttendanceLogEntity): Int {
    return if (log.workedMinutes > 0) {
        log.workedMinutes
    } else {
        AttendanceCalculator.calculateWorkedMinutes(log.checkInTimestamp, log.checkOutTimestamp)
    }
}

private fun formatTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "—"
    return SimpleDateFormat("hh:mm a", Locale.US).format(Date(timestampMs))
}

private fun calculateFirstWeekdayOffset(monthKey: String): Int {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return 0
    val month = parts.getOrNull(1)?.toIntOrNull() ?: return 0
    if (month !in 1..12) return 0
    val calendar = GregorianCalendar(year, month - 1, 1)
    return (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY).coerceIn(0, 6)
}

private fun shiftMonthKey(monthKey: String, deltaMonths: Int): String {
    val parts = monthKey.split("-")
    val year = parts.getOrNull(0)?.toIntOrNull()
    val month = parts.getOrNull(1)?.toIntOrNull()
    if (year == null || month == null || month !in 1..12) return monthKey
    val calendar = GregorianCalendar(year, month - 1, 1)
    calendar.add(Calendar.MONTH, deltaMonths)
    return String.format(
        Locale.US,
        "%04d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1
    )
}

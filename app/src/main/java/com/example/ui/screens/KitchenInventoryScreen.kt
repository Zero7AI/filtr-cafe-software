package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import com.example.domain.kitchen.KitchenCountingMode
import com.example.domain.kitchen.KitchenInventoryCalculator
import com.example.domain.kitchen.KitchenSection
import com.example.domain.kitchen.KitchenShiftType
import com.example.domain.kitchen.KitchenStockStatus
import com.example.ui.KitchenInventoryDashboardSummary
import com.example.ui.KitchenInventoryViewRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val KitchenBg = Color(0xFFF8FAFC)
internal val KitchenInk = Color(0xFF09090B)
internal val KitchenMuted = Color(0xFF64748B)
internal val KitchenBorder = Color(0xFFE2E8F0)
internal val KitchenGreen = Color(0xFF16A34A)
internal val KitchenOrange = Color(0xFFD97706)
internal val KitchenRed = Color(0xFFDC2626)
internal val KitchenSky = Color(0xFF0284C7)

@Composable
fun KitchenInventoryScreen(
    isManager: Boolean,
    summary: KitchenInventoryDashboardSummary,
    route: KitchenInventoryViewRoute,
    activeItems: List<KitchenInventoryItemEntity>,
    allItems: List<KitchenInventoryItemEntity>,
    entriesToday: List<KitchenInventoryEntryEntity>,
    allShifts: List<KitchenInventoryShiftEntity>,
    allEntries: List<KitchenInventoryEntryEntity>,
    previousClosingShift: KitchenInventoryShiftEntity?,
    previousClosingEntries: List<KitchenInventoryEntryEntity>,
    onNavigate: (KitchenInventoryViewRoute) -> Unit,
    onStartOrResumeShift: (KitchenShiftType) -> Unit,
    onOpenFirstUnchecked: (KitchenShiftType, String?) -> Unit,
    onSaveItemAndAdvance: (
        shiftType: KitchenShiftType,
        item: KitchenInventoryItemEntity,
        sealedQty: Int,
        openedQty: Int,
        openedRemainingQty: Double,
        wastageQty: Double,
        wastageReason: String,
        nextItemId: String?,
        nextSectionId: String?
    ) -> Unit,
    onSubmitShift: (KitchenShiftType, String) -> Unit,
    onSaveItemConfig: (
        existingId: String?,
        name: String,
        emoji: String,
        sectionId: String,
        countingMode: String,
        unitLabel: String,
        containerLabel: String,
        packSize: Double,
        minThreshold: Double,
        targetLevel: Double,
        isActive: Boolean
    ) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KitchenBg)
    ) {
        // Persistent FILTR COFFEE Branded Header Bar
        Surface(
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (route != KitchenInventoryViewRoute.Dashboard) {
                            IconButton(
                                onClick = {
                                    when (route) {
                                        is KitchenInventoryViewRoute.SectionCounting ->
                                            onNavigate(KitchenInventoryViewRoute.ShiftOverview(route.shiftType))
                                        else -> onNavigate(KitchenInventoryViewRoute.Dashboard)
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = KitchenInk
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Surface(
                                color = KitchenInk,
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column {
                            Text(
                                text = "FILTR COFFEE • KITCHEN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenSky,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = when (route) {
                                    KitchenInventoryViewRoute.Dashboard -> "Daily Kitchen Inventory"
                                    is KitchenInventoryViewRoute.ShiftOverview -> route.shiftType.label
                                    is KitchenInventoryViewRoute.SectionCounting -> route.shiftType.label
                                    is KitchenInventoryViewRoute.ShiftSubmittedConfirmation -> "${route.shiftType.label} Saved"
                                    KitchenInventoryViewRoute.ManagerHub -> "Manager Inventory Hub"
                                },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = KitchenInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Inventory",
                                tint = KitchenInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isManager) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val inManager = route is KitchenInventoryViewRoute.ManagerHub
                            Surface(
                                color = if (inManager) KitchenInk else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (inManager) KitchenInk else Color(0xFFF59E0B)),
                                modifier = Modifier.clickable {
                                    onNavigate(
                                        if (inManager) KitchenInventoryViewRoute.Dashboard
                                        else KitchenInventoryViewRoute.ManagerHub
                                    )
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = if (inManager) Color.White else Color(0xFFB45309),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (inManager) "Staff View" else "Manager",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inManager) Color.White else Color(0xFFB45309)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (route) {
            KitchenInventoryViewRoute.Dashboard -> {
                KitchenEmployeeDashboard(
                    summary = summary,
                    activeItems = activeItems,
                    entriesToday = entriesToday,
                    onStartOrResumeShift = onStartOrResumeShift,
                    onOpenSection = { shiftType, secId ->
                        onOpenFirstUnchecked(shiftType, secId)
                    }
                )
            }

            is KitchenInventoryViewRoute.ShiftOverview -> {
                KitchenShiftOverviewView(
                    shiftType = route.shiftType,
                    summary = summary,
                    activeItems = activeItems,
                    entriesToday = entriesToday,
                    onStartSection = { secId -> onOpenFirstUnchecked(route.shiftType, secId) },
                    onResumeNextUnchecked = { onOpenFirstUnchecked(route.shiftType, null) },
                    onSubmitShift = { notes -> onSubmitShift(route.shiftType, notes) }
                )
            }

            is KitchenInventoryViewRoute.SectionCounting -> {
                KitchenGuidedCountingView(
                    shiftType = route.shiftType,
                    initialSectionId = route.sectionId,
                    selectedItemId = route.selectedItemId,
                    activeItems = activeItems,
                    entriesToday = entriesToday,
                    previousClosingShift = previousClosingShift,
                    previousClosingEntries = previousClosingEntries,
                    onSelectSectionOrItem = { secId, itemId ->
                        onNavigate(
                            KitchenInventoryViewRoute.SectionCounting(
                                shiftType = route.shiftType,
                                sectionId = secId,
                                selectedItemId = itemId
                            )
                        )
                    },
                    onSaveItemAndAdvance = onSaveItemAndAdvance,
                    onFinishReview = {
                        onNavigate(KitchenInventoryViewRoute.ShiftOverview(route.shiftType))
                    }
                )
            }

            is KitchenInventoryViewRoute.ShiftSubmittedConfirmation -> {
                val shift = if (route.shiftType == KitchenShiftType.OPENING) summary.openingShift else summary.closingShift
                val entries = entriesToday.filter { it.shiftType == route.shiftType.id && it.isChecked }
                KitchenShiftSubmittedView(
                    shiftType = route.shiftType,
                    shift = shift,
                    entries = entries,
                    totalActiveItems = activeItems.size,
                    onBackToDashboard = { onNavigate(KitchenInventoryViewRoute.Dashboard) }
                )
            }

            KitchenInventoryViewRoute.ManagerHub -> {
                KitchenInventoryManagerView(
                    summary = summary,
                    allItems = allItems,
                    allShifts = allShifts,
                    allEntries = allEntries,
                    onSaveItemConfig = onSaveItemConfig
                )
            }
        }
    }
}

@Composable
private fun KitchenEmployeeDashboard(
    summary: KitchenInventoryDashboardSummary,
    activeItems: List<KitchenInventoryItemEntity>,
    entriesToday: List<KitchenInventoryEntryEntity>,
    onStartOrResumeShift: (KitchenShiftType) -> Unit,
    onOpenSection: (KitchenShiftType, String) -> Unit
) {
    val activeShiftType = remember(summary.openingShift, summary.closingShift) {
        when {
            summary.openingShift?.status == "SUBMITTED" && summary.closingShift?.status != "SUBMITTED" ->
                KitchenShiftType.CLOSING
            else -> KitchenShiftType.OPENING
        }
    }
    val activeChecked = if (activeShiftType == KitchenShiftType.OPENING) {
        summary.openingCheckedCount
    } else {
        summary.closingCheckedCount
    }
    val pendingSections = if (activeShiftType == KitchenShiftType.OPENING) {
        summary.openingPendingSections
    } else {
        summary.closingPendingSections
    }
    val progressFloat = if (summary.totalActiveItems > 0) {
        (activeChecked.toFloat() / summary.totalActiveItems.toFloat()).coerceIn(0f, 1f)
    } else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Automatic Date + Staff Info + Progress Card
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = summary.formattedDate,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenInk
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Staff: ${summary.staffName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KitchenMuted
                            )
                        }
                        Surface(
                            color = KitchenSky.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "$activeChecked / ${summary.totalActiveItems} Checked",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenSky,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = KitchenGreen,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (pendingSections.isEmpty() && activeChecked > 0) {
                            "All active sections checked for ${activeShiftType.label}"
                        } else {
                            "${activeShiftType.label} • Pending sections: ${pendingSections.size}"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = KitchenMuted
                    )
                }
            }
        }

        // 4 Quick Summary Pills
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KitchenStatBox(
                    label = "Checked",
                    value = "$activeChecked/${summary.totalActiveItems}",
                    accent = KitchenSky,
                    modifier = Modifier.weight(1f)
                )
                KitchenStatBox(
                    label = "Low Stock",
                    value = "${summary.lowStockCount}",
                    accent = KitchenOrange,
                    modifier = Modifier.weight(1f)
                )
                KitchenStatBox(
                    label = "Out of Stock",
                    value = "${summary.outOfStockCount}",
                    accent = KitchenRed,
                    modifier = Modifier.weight(1f)
                )
                KitchenStatBox(
                    label = "Pending Zones",
                    value = "${pendingSections.size}",
                    accent = KitchenInk,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Giant Touch-Friendly Shift Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ShiftActionButtonCard(
                    title = "Opening Inventory",
                    subtitle = when {
                        summary.openingShift?.status == "SUBMITTED" ->
                            "Submitted by ${summary.openingShift.staffName} at ${formatKitchenTime(summary.openingShift.submittedAt)} • Tap to view"
                        summary.openingShift != null ->
                            "In progress: ${summary.openingCheckedCount}/${summary.totalActiveItems} items • Tap to resume"
                        else -> "Morning check before service (with last closing reference)"
                    },
                    isSubmitted = summary.openingShift?.status == "SUBMITTED",
                    isInProgress = summary.openingShift?.status == "IN_PROGRESS",
                    accentColor = KitchenSky,
                    icon = Icons.Default.WbSunny,
                    onClick = { onStartOrResumeShift(KitchenShiftType.OPENING) }
                )

                ShiftActionButtonCard(
                    title = "Closing Inventory",
                    subtitle = when {
                        summary.closingShift?.status == "SUBMITTED" ->
                            "Submitted by ${summary.closingShift.staffName} at ${formatKitchenTime(summary.closingShift.submittedAt)} • Tap to view"
                        summary.closingShift != null ->
                            "In progress: ${summary.closingCheckedCount}/${summary.totalActiveItems} items • Tap to resume"
                        else -> "End-of-day stock count, variance & wastage report"
                    },
                    isSubmitted = summary.closingShift?.status == "SUBMITTED",
                    isInProgress = summary.closingShift?.status == "IN_PROGRESS",
                    accentColor = Color(0xFF7C3AED),
                    icon = Icons.Default.NightsStay,
                    onClick = { onStartOrResumeShift(KitchenShiftType.CLOSING) }
                )
            }
        }

        // 4 Storage Sections Preview
        item {
            Text(
                text = "Storage Sections (${activeShiftType.label})",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = KitchenInk,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val shiftEntriesMap = entriesToday
            .filter { it.shiftType == activeShiftType.id && it.isChecked }
            .associateBy { it.itemId }

        items(KitchenSection.entries) { section ->
            val secItems = activeItems.filter { it.sectionId == section.id }
            val checkedCount = secItems.count { it.id in shiftEntriesMap }
            val isSubmitted = (if (activeShiftType == KitchenShiftType.OPENING) summary.openingShift else summary.closingShift)?.status == "SUBMITTED"

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = secItems.isNotEmpty() && !isSubmitted) {
                        onStartOrResumeShift(activeShiftType)
                        onOpenSection(activeShiftType, section.id)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(section.iconEmoji, fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = section.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenInk
                            )
                            Text(
                                text = if (secItems.isEmpty()) {
                                    "0 items configured (Configurable by Manager)"
                                } else {
                                    "$checkedCount of ${secItems.size} items checked"
                                },
                                fontSize = 12.sp,
                                color = KitchenMuted
                            )
                        }
                    }

                    if (secItems.isNotEmpty()) {
                        val done = checkedCount >= secItems.size
                        Surface(
                            color = if (done) KitchenGreen.copy(alpha = 0.14f) else KitchenOrange.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (done) "DONE" else "PENDING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (done) KitchenGreen else KitchenOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "EMPTY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KitchenStatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, KitchenBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = accent
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = KitchenMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShiftActionButtonCard(
    title: String,
    subtitle: String,
    isSubmitted: Boolean,
    isInProgress: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSubmitted -> Color(0xFFF0FDF4)
        isInProgress -> Color(0xFFFFFBEB)
        else -> Color.White
    }
    val borderColor = when {
        isSubmitted -> KitchenGreen
        isInProgress -> KitchenOrange
        else -> accentColor
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    color = borderColor.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isSubmitted) Icons.Default.CheckCircle else icon,
                            contentDescription = null,
                            tint = borderColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = KitchenInk
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = KitchenMuted
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = when {
                        isSubmitted -> "VIEW"
                        isInProgress -> "RESUME"
                        else -> "START"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun KitchenShiftOverviewView(
    shiftType: KitchenShiftType,
    summary: KitchenInventoryDashboardSummary,
    activeItems: List<KitchenInventoryItemEntity>,
    entriesToday: List<KitchenInventoryEntryEntity>,
    onStartSection: (String) -> Unit,
    onResumeNextUnchecked: () -> Unit,
    onSubmitShift: (String) -> Unit
) {
    val shift = if (shiftType == KitchenShiftType.OPENING) summary.openingShift else summary.closingShift
    val entriesMap = entriesToday
        .filter { it.shiftType == shiftType.id && it.isChecked }
        .associateBy { it.itemId }
    val totalCount = activeItems.size
    val checkedCount = activeItems.count { it.id in entriesMap }
    val pendingSections = KitchenSection.entries.filter { sec ->
        val secItems = activeItems.filter { it.sectionId == sec.id }
        secItems.isNotEmpty() && secItems.any { it.id !in entriesMap }
    }
    var shiftNotes by remember(shift?.id) { mutableStateOf(shift?.notes ?: "") }
    var showIncompleteConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${shiftType.label} • ${summary.formattedDate}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = KitchenInk
                    )
                    Text(
                        text = "Checked by ${summary.staffName} • $checkedCount of $totalCount items done",
                        fontSize = 13.sp,
                        color = KitchenMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) checkedCount.toFloat() / totalCount else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = KitchenGreen,
                        trackColor = Color(0xFFE2E8F0)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onResumeNextUnchecked,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenInk),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (checkedCount == 0) "Start Counting (Item 1)" else if (checkedCount < totalCount) "Resume Next Unchecked Item" else "Review Counted Items",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        items(KitchenSection.entries) { section ->
            val secItems = activeItems.filter { it.sectionId == section.id }
            val secChecked = secItems.count { it.id in entriesMap }
            val isSecDone = secItems.isNotEmpty() && secChecked == secItems.size
            Surface(
                color = if (secItems.isEmpty()) Color(0xFFF1F5F9) else Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = if (secItems.isNotEmpty() && !isSecDone) 1.5.dp else 1.dp,
                    color = if (isSecDone) KitchenGreen else if (secItems.isNotEmpty()) KitchenOrange else KitchenBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = secItems.isNotEmpty()) { onStartSection(section.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(section.iconEmoji, fontSize = 26.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = section.title,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = KitchenInk
                            )
                            Text(
                                text = if (secItems.isEmpty()) {
                                    "No items configured (Empty section)"
                                } else {
                                    "$secChecked / ${secItems.size} checked • Tap to count"
                                },
                                fontSize = 12.sp,
                                color = KitchenMuted
                            )
                        }
                    }
                    if (secItems.isNotEmpty()) {
                        Surface(
                            color = if (isSecDone) KitchenGreen else KitchenOrange,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isSecDone) "DONE" else "OPEN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = shiftNotes,
                        onValueChange = { shiftNotes = it },
                        label = { Text("Shift Notes (optional)") },
                        placeholder = { Text("Any delivery or kitchen notes...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (pendingSections.isNotEmpty()) {
                                showIncompleteConfirmDialog = true
                            } else {
                                onSubmitShift(shiftNotes)
                            }
                        },
                        enabled = checkedCount > 0,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pendingSections.isEmpty() && checkedCount > 0) KitchenGreen else KitchenInk
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (pendingSections.isEmpty() && checkedCount > 0) {
                                "Submit ${shiftType.label}"
                            } else {
                                "Submit ${shiftType.label} ($checkedCount/$totalCount)"
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }

    if (showIncompleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showIncompleteConfirmDialog = false },
            title = { Text("Unfinished Sections", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "You have ${totalCount - checkedCount} unchecked items in:\n" +
                        pendingSections.joinToString("\n") { "• ${it.title}" } +
                        "\n\nOnce submitted, this shift cannot be re-submitted. Continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showIncompleteConfirmDialog = false
                        onSubmitShift(shiftNotes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenOrange)
                ) {
                    Text("Submit Anyway", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIncompleteConfirmDialog = false }) {
                    Text("Keep Counting")
                }
            }
        )
    }
}

@Composable
private fun KitchenGuidedCountingView(
    shiftType: KitchenShiftType,
    initialSectionId: String?,
    selectedItemId: String?,
    activeItems: List<KitchenInventoryItemEntity>,
    entriesToday: List<KitchenInventoryEntryEntity>,
    previousClosingShift: KitchenInventoryShiftEntity?,
    previousClosingEntries: List<KitchenInventoryEntryEntity>,
    onSelectSectionOrItem: (sectionId: String, itemId: String) -> Unit,
    onSaveItemAndAdvance: (
        shiftType: KitchenShiftType,
        item: KitchenInventoryItemEntity,
        sealedQty: Int,
        openedQty: Int,
        openedRemainingQty: Double,
        wastageQty: Double,
        wastageReason: String,
        nextItemId: String?,
        nextSectionId: String?
    ) -> Unit,
    onFinishReview: () -> Unit
) {
    val orderedAllItems = remember(activeItems) {
        activeItems.sortedWith(
            compareBy<KitchenInventoryItemEntity> { KitchenSection.fromId(it.sectionId).orderIndex }
                .thenBy { it.orderIndex }
                .thenBy { it.name }
        )
    }
    if (orderedAllItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No inventory items configured yet.", color = KitchenMuted)
        }
        return
    }

    val currentItem = remember(orderedAllItems, selectedItemId, initialSectionId) {
        orderedAllItems.firstOrNull { it.id == selectedItemId }
            ?: orderedAllItems.firstOrNull { it.sectionId == initialSectionId }
            ?: orderedAllItems.first()
    }
    val currentSectionId = currentItem.sectionId
    val sectionItems = remember(orderedAllItems, currentSectionId) {
        orderedAllItems.filter { it.sectionId == currentSectionId }
    }
    val currentGlobalIdx = orderedAllItems.indexOfFirst { it.id == currentItem.id }.coerceAtLeast(0)
    val nextGlobalItem = orderedAllItems.getOrNull(currentGlobalIdx + 1)
    val prevGlobalItem = if (currentGlobalIdx > 0) orderedAllItems[currentGlobalIdx - 1] else null

    val entriesMap = remember(entriesToday, shiftType) {
        entriesToday.filter { it.shiftType == shiftType.id }.associateBy { it.itemId }
    }
    val existingEntry = entriesMap[currentItem.id]
    val prevClosingEntry = remember(previousClosingEntries, currentItem.id) {
        previousClosingEntries.firstOrNull { it.itemId == currentItem.id }
    }
    val todayOpeningEntry = remember(entriesToday, currentItem.id) {
        entriesToday.firstOrNull { it.shiftType == KitchenShiftType.OPENING.id && it.itemId == currentItem.id }
    }

    val mode = KitchenCountingMode.fromId(currentItem.countingMode)

    // Pre-fill existing entry OR previous reference so staff don't re-type stable stock unnecessarily
    var sealedQty by remember(currentItem.id, existingEntry?.checkedAt) {
        mutableIntStateOf(
            existingEntry?.sealedQty
                ?: if (shiftType == KitchenShiftType.OPENING) (prevClosingEntry?.sealedQty ?: 0) else (todayOpeningEntry?.sealedQty ?: 0)
        )
    }
    var openedQty by remember(currentItem.id, existingEntry?.checkedAt) {
        mutableIntStateOf(
            existingEntry?.openedQty
                ?: if (shiftType == KitchenShiftType.OPENING) (prevClosingEntry?.openedQty ?: 0) else (todayOpeningEntry?.openedQty ?: 0)
        )
    }
    var openedRemainingText by remember(currentItem.id, existingEntry?.checkedAt) {
        val seedVal = existingEntry?.openedRemainingQty
            ?: if (shiftType == KitchenShiftType.OPENING) (prevClosingEntry?.openedRemainingQty ?: 0.0) else (todayOpeningEntry?.openedRemainingQty ?: 0.0)
        mutableStateOf(
            if (seedVal == 0.0) "" else if (seedVal % 1.0 == 0.0) seedVal.toLong().toString() else seedVal.toString()
        )
    }
    var selectedPct by remember(currentItem.id, existingEntry?.checkedAt) {
        val seedPct = existingEntry?.openedRemainingQty
            ?: if (shiftType == KitchenShiftType.OPENING) (prevClosingEntry?.openedRemainingQty ?: 0.0) else (todayOpeningEntry?.openedRemainingQty ?: 0.0)
        mutableDoubleStateOf(seedPct)
    }
    var wastageText by remember(currentItem.id, existingEntry?.checkedAt) {
        val w = existingEntry?.wastageQty ?: 0.0
        mutableStateOf(if (w == 0.0) "" else if (w % 1.0 == 0.0) w.toLong().toString() else w.toString())
    }
    var wastageReason by remember(currentItem.id, existingEntry?.checkedAt) {
        mutableStateOf(existingEntry?.wastageReason ?: "")
    }

    val parsedRemaining = if (mode == KitchenCountingMode.SEALED_AND_PERCENTAGE) {
        selectedPct
    } else {
        openedRemainingText.toDoubleOrNull() ?: 0.0
    }
    val calculatedTotal = KitchenInventoryCalculator.calculateTotalUsableQty(
        countingMode = currentItem.countingMode,
        packSize = currentItem.packSize,
        sealedQty = sealedQty,
        openedQty = openedQty,
        openedRemainingQty = parsedRemaining
    )
    val liveStatus = KitchenInventoryCalculator.evaluateStockStatus(calculatedTotal, currentItem.minStockThreshold)
    val statusColor = when (liveStatus) {
        KitchenStockStatus.GREEN -> KitchenGreen
        KitchenStockStatus.ORANGE -> KitchenOrange
        KitchenStockStatus.RED -> KitchenRed
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Section Jump Strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
        ) {
            items(KitchenSection.entries) { section ->
                val secList = orderedAllItems.filter { it.sectionId == section.id }
                if (secList.isNotEmpty()) {
                    val isSel = section.id == currentSectionId
                    val doneCount = secList.count { entriesMap[it.id]?.isChecked == true }
                    Surface(
                        color = if (isSel) KitchenInk else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable {
                            val firstUnchecked = secList.firstOrNull { entriesMap[it.id]?.isChecked != true } ?: secList.first()
                            onSelectSectionOrItem(section.id, firstUnchecked.id)
                        }
                    ) {
                        Text(
                            text = "${section.iconEmoji} ${section.shortTitle} ($doneCount/${secList.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else KitchenInk,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // Compact Item Selector Strip within Section
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9))
        ) {
            items(sectionItems) { secItem ->
                val isCurrent = secItem.id == currentItem.id
                val isDone = entriesMap[secItem.id]?.isChecked == true
                Surface(
                    color = when {
                        isCurrent -> KitchenSky
                        isDone -> Color(0xFFDCFCE7)
                        else -> Color.White
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isCurrent) KitchenSky else KitchenBorder),
                    modifier = Modifier.clickable { onSelectSectionOrItem(currentSectionId, secItem.id) }
                ) {
                    Text(
                        text = "${if (isDone && !isCurrent) "✓ " else ""}${secItem.iconEmoji} ${secItem.name}",
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color.White else if (isDone) KitchenGreen else KitchenInk,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Scrollable Focused Item Card
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Item ${currentGlobalIdx + 1} of ${orderedAllItems.size}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenMuted
                        )
                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = liveStatus.label.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Item Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, KitchenBorder),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(currentItem.iconEmoji, fontSize = 30.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentItem.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = KitchenInk
                            )
                            Text(
                                text = "${KitchenSection.fromId(currentItem.sectionId).title} • Unit: ${currentItem.unitLabel}" +
                                    if (mode.usesSealedOpened && currentItem.packSize > 1.0) {
                                        " (1 ${currentItem.containerLabel.ifBlank { "pack" }} = ${KitchenInventoryCalculator.formatQty(currentItem.packSize, currentItem.unitLabel)})"
                                    } else "",
                                fontSize = 12.sp,
                                color = KitchenMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Opening Reference OR Closing Live Variance Banner
                    if (shiftType == KitchenShiftType.OPENING) {
                        val refText = if (prevClosingEntry != null && previousClosingShift != null) {
                            "Last Closing (${previousClosingShift.dateKey}): ${KitchenInventoryCalculator.formatQty(prevClosingEntry.totalUsableQty, currentItem.unitLabel)}"
                        } else {
                            "Min Required Stock: ${KitchenInventoryCalculator.formatQty(currentItem.minStockThreshold, currentItem.unitLabel)}"
                        }
                        Surface(
                            color = if (liveStatus != KitchenStockStatus.GREEN) Color(0xFFFFF7ED) else Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                if (liveStatus != KitchenStockStatus.GREEN) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = statusColor, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = refText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (liveStatus != KitchenStockStatus.GREEN) statusColor else KitchenSky
                                )
                            }
                        }
                    } else if (todayOpeningEntry != null) {
                        val diff = KitchenInventoryCalculator.roundTo2Decimals(todayOpeningEntry.totalUsableQty - calculatedTotal)
                        Surface(
                            color = Color(0xFFF5F3FF),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Today's Opening: ${KitchenInventoryCalculator.formatQty(todayOpeningEntry.totalUsableQty, currentItem.unitLabel)}  •  Used/Diff: ${KitchenInventoryCalculator.formatQty(diff, currentItem.unitLabel)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF5B21B6),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = KitchenBorder)
                    Spacer(modifier = Modifier.height(14.dp))

                    // CONDITIONAL COUNTING CONTROLS (No irrelevant fields!)
                    if (mode.usesSealedOpened) {
                        val containerName = currentItem.containerLabel.ifBlank { "packets" }
                        LargeTouchStepperRow(
                            label = "Sealed $containerName",
                            value = sealedQty,
                            onChange = { sealedQty = it.coerceAtLeast(0) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LargeTouchStepperRow(
                            label = "Opened $containerName",
                            value = openedQty,
                            onChange = { nextOpened ->
                                openedQty = nextOpened.coerceAtLeast(0)
                                if (openedQty == 0) {
                                    selectedPct = 0.0
                                    openedRemainingText = ""
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        if (mode == KitchenCountingMode.SEALED_AND_PERCENTAGE) {
                            Text(
                                text = "Remaining in Opened Container (%)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenInk
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(25.0, 50.0, 75.0, 100.0).forEach { pct ->
                                    val selected = selectedPct == pct && openedQty > 0
                                    Surface(
                                        color = if (selected) KitchenInk else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.5.dp, if (selected) KitchenInk else KitchenBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable {
                                                if (openedQty == 0) openedQty = 1
                                                selectedPct = pct
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${pct.toInt()}%",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (selected) Color.White else KitchenInk
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { selectedPct = 0.0 },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Clear opened % (0%)", fontSize = 12.sp, color = KitchenMuted)
                            }
                        } else {
                            val unitPrompt = when (mode) {
                                KitchenCountingMode.SEALED_AND_GRAMS -> "Remaining in Opened Container (grams)"
                                KitchenCountingMode.SEALED_AND_ML -> "Remaining in Opened Container (ml)"
                                else -> "Remaining in Opened Pack (${currentItem.unitLabel})"
                            }
                            OutlinedTextField(
                                value = openedRemainingText,
                                onValueChange = { raw ->
                                    openedRemainingText = raw.filter { it.isDigit() || it == '.' }
                                    val parsed = openedRemainingText.toDoubleOrNull() ?: 0.0
                                    if (parsed > 0.0 && openedQty == 0) openedQty = 1
                                },
                                label = { Text(unitPrompt) },
                                placeholder = { Text("0 ${currentItem.unitLabel}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val quickValues = when (mode) {
                                KitchenCountingMode.SEALED_AND_GRAMS -> listOf(100, 250, 500, 750)
                                KitchenCountingMode.SEALED_AND_ML -> listOf(100, 250, 500, 750)
                                else -> listOf(5, 10, 25, 50)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickValues.forEach { qv ->
                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, KitchenBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (openedQty == 0) openedQty = 1
                                                openedRemainingText = qv.toString()
                                            }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = "$qv ${currentItem.unitLabel}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = KitchenInk
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Direct Single-Field Items (Paneer, Vegetables, Cups, Straws, Spoons, Ice, etc.)
                        val fieldTitle = when (mode) {
                            KitchenCountingMode.DIRECT_GRAMS -> "Total Remaining Grams (g)"
                            KitchenCountingMode.DIRECT_ML -> "Total Remaining Volume (ml)"
                            else -> "Total Pieces (${currentItem.unitLabel})"
                        }
                        OutlinedTextField(
                            value = openedRemainingText,
                            onValueChange = { raw ->
                                openedRemainingText = raw.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text(fieldTitle) },
                            placeholder = { Text("0 ${currentItem.unitLabel}") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        val quickDeltas = when (mode) {
                            KitchenCountingMode.DIRECT_GRAMS -> listOf(100, 250, 500, 1000)
                            KitchenCountingMode.DIRECT_ML -> listOf(100, 250, 500, 1000)
                            else -> listOf(5, 10, 25, 50)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            quickDeltas.forEach { delta ->
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, KitchenBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            val cur = openedRemainingText.toDoubleOrNull() ?: 0.0
                                            val updated = cur + delta
                                            openedRemainingText = if (updated % 1.0 == 0.0) updated.toLong().toString() else updated.toString()
                                        }
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 11.dp)
                                    ) {
                                        Text(
                                            text = "+$delta",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = KitchenInk
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto-calculated Total Usable Quantity Banner
                    Surface(
                        color = statusColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, statusColor),
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
                                text = "Total Usable Stock",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenInk
                            )
                            Text(
                                text = KitchenInventoryCalculator.formatQty(calculatedTotal, currentItem.unitLabel),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = statusColor
                            )
                        }
                    }

                    // Wastage / Damaged Stock row (Closing Shift Only)
                    if (shiftType == KitchenShiftType.CLOSING) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Wastage / Damaged Stock (optional)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenInk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = wastageText,
                                onValueChange = { raw -> wastageText = raw.filter { it.isDigit() || it == '.' } },
                                label = { Text("Qty (${currentItem.unitLabel})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.42f)
                            )
                            OutlinedTextField(
                                value = wastageReason,
                                onValueChange = { wastageReason = it },
                                label = { Text("Reason") },
                                placeholder = { Text("Spill / Expired") },
                                singleLine = true,
                                modifier = Modifier.weight(0.58f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Spillage", "Expired", "Damaged").forEach { reasonTag ->
                                Surface(
                                    color = if (wastageReason == reasonTag) KitchenOrange.copy(alpha = 0.16f) else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable { wastageReason = reasonTag }
                                ) {
                                    Text(
                                        text = reasonTag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (wastageReason == reasonTag) KitchenOrange else KitchenMuted,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fixed Bottom Navigation Bar inside Guided Card Workflow
        Surface(
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (prevGlobalItem != null) {
                            onSelectSectionOrItem(prevGlobalItem.sectionId, prevGlobalItem.id)
                        } else {
                            onFinishReview()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (prevGlobalItem != null) "Prev" else "Sections", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        onSaveItemAndAdvance(
                            shiftType,
                            currentItem,
                            sealedQty,
                            openedQty,
                            parsedRemaining,
                            wastageText.toDoubleOrNull() ?: 0.0,
                            wastageReason,
                            nextGlobalItem?.id,
                            nextGlobalItem?.sectionId
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenGreen),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text(
                        text = if (nextGlobalItem != null) "Save & Next" else "Save & Review Shift",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun LargeTouchStepperRow(
    label: String,
    value: Int,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = KitchenInk,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier
                    .size(46.dp)
                    .clickable { onChange((value - 1).coerceAtLeast(0)) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("-", fontSize = 22.sp, fontWeight = FontWeight.Black, color = KitchenInk)
                }
            }

            Text(
                text = value.toString(),
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = KitchenInk,
                modifier = Modifier.width(34.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Surface(
                color = KitchenInk,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .size(46.dp)
                    .clickable { onChange(value + 1) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun KitchenShiftSubmittedView(
    shiftType: KitchenShiftType,
    shift: KitchenInventoryShiftEntity?,
    entries: List<KitchenInventoryEntryEntity>,
    totalActiveItems: Int,
    onBackToDashboard: () -> Unit
) {
    val lowCount = entries.count { it.stockStatus == KitchenStockStatus.ORANGE.id }
    val outCount = entries.count { it.stockStatus == KitchenStockStatus.RED.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = Color(0xFFF0FDF4),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(2.dp, KitchenGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = KitchenGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${shiftType.label} Submitted",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = KitchenInk
                    )
                    Text(
                        text = "Completed by ${shift?.staffName ?: "Staff"} at ${formatKitchenTime(shift?.submittedAt ?: 0L)}",
                        fontSize = 13.sp,
                        color = KitchenMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, KitchenGreen.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = KitchenGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Locked against duplicate submission",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenGreen
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitchenStatBox("Checked", "${entries.size}/$totalActiveItems", KitchenGreen, Modifier.weight(1f))
                        KitchenStatBox("Low Stock", "$lowCount", KitchenOrange, Modifier.weight(1f))
                        KitchenStatBox("Out of Stock", "$outCount", KitchenRed, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onBackToDashboard,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = KitchenInk),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Back to Kitchen Dashboard", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (!shift?.notes.isNullOrBlank()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KitchenBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Shift Notes: ${shift?.notes}",
                        fontSize = 13.sp,
                        color = KitchenInk,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        items(entries.sortedBy { it.itemNameSnapshot }) { entry ->
            val st = KitchenStockStatus.fromId(entry.stockStatus)
            val color = when (st) {
                KitchenStockStatus.GREEN -> KitchenGreen
                KitchenStockStatus.ORANGE -> KitchenOrange
                KitchenStockStatus.RED -> KitchenRed
            }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.itemNameSnapshot, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KitchenInk)
                        Text(KitchenSection.fromId(entry.sectionIdSnapshot).shortTitle, fontSize = 11.sp, color = KitchenMuted)
                        if (entry.wastageQty > 0.0) {
                            Text(
                                text = "Wastage: ${KitchenInventoryCalculator.formatQty(entry.wastageQty, entry.unitLabelSnapshot)} (${entry.wastageReason.ifBlank { "Reported" }})",
                                fontSize = 11.sp,
                                color = KitchenRed
                            )
                        }
                    }
                    Surface(
                        color = color.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = KitchenInventoryCalculator.formatQty(entry.totalUsableQty, entry.unitLabelSnapshot),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun formatKitchenTime(ms: Long): String {
    if (ms <= 0L) return "--:--"
    return try {
        SimpleDateFormat("hh:mm a", Locale.US).format(Date(ms))
    } catch (_: Exception) {
        "--:--"
    }
}

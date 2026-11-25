package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.domain.kitchen.KitchenStockStatus
import com.example.ui.KitchenInventoryDashboardSummary

private enum class ManagerKitchenTab(val title: String) {
    COMPARISON("Comparison"),
    REORDER("Reorder"),
    HISTORY("History"),
    CONFIGURE("Configure")
}

@Composable
fun KitchenInventoryManagerView(
    summary: KitchenInventoryDashboardSummary,
    allItems: List<KitchenInventoryItemEntity>,
    allShifts: List<KitchenInventoryShiftEntity>,
    allEntries: List<KitchenInventoryEntryEntity>,
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
    ) -> Unit
) {
    var selectedTab by remember { mutableStateOf(ManagerKitchenTab.COMPARISON) }
    var searchQuery by remember { mutableStateOf("") }
    var sectionFilter by remember { mutableStateOf("ALL") }
    var editingItem by remember { mutableStateOf<KitchenInventoryItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inspectingShift by remember { mutableStateOf<KitchenInventoryShiftEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Manager Sub-Tab Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ManagerKitchenTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val badge = if (tab == ManagerKitchenTab.REORDER && summary.reorderList.isNotEmpty()) {
                    " (${summary.reorderList.size})"
                } else ""
                Surface(
                    color = if (selected) KitchenInk else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = tab }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${tab.title}$badge",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else KitchenInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = KitchenBorder)

        when (selectedTab) {
            ManagerKitchenTab.COMPARISON -> {
                ManagerComparisonContent(
                    summary = summary,
                    searchQuery = searchQuery,
                    onSearchChanged = { searchQuery = it },
                    sectionFilter = sectionFilter,
                    onSectionFilterChanged = { sectionFilter = it }
                )
            }

            ManagerKitchenTab.REORDER -> {
                ManagerReorderContent(summary = summary)
            }

            ManagerKitchenTab.HISTORY -> {
                ManagerHistoryContent(
                    allShifts = allShifts,
                    allEntries = allEntries,
                    onSelectShift = { inspectingShift = it }
                )
            }

            ManagerKitchenTab.CONFIGURE -> {
                ManagerConfigureContent(
                    allItems = allItems,
                    sectionFilter = sectionFilter,
                    onSectionFilterChanged = { sectionFilter = it },
                    onEditItem = { editingItem = it },
                    onOpenAddItem = { showAddDialog = true }
                )
            }
        }
    }

    if (showAddDialog || editingItem != null) {
        KitchenItemConfigDialog(
            existing = editingItem,
            defaultSectionId = if (sectionFilter == "ALL") KitchenSection.OUTSIDE_FREEZER.id else sectionFilter,
            onDismiss = {
                showAddDialog = false
                editingItem = null
            },
            onSave = { name, emoji, secId, modeId, unitLabel, containerLabel, packSize, minThresh, targetLvl, isActive ->
                onSaveItemConfig(
                    editingItem?.id,
                    name,
                    emoji,
                    secId,
                    modeId,
                    unitLabel,
                    containerLabel,
                    packSize,
                    minThresh,
                    targetLvl,
                    isActive
                )
                showAddDialog = false
                editingItem = null
            }
        )
    }

    inspectingShift?.let { shift ->
        val shiftEntries = allEntries.filter { it.shiftId == shift.id }
        AlertDialog(
            onDismissRequest = { inspectingShift = null },
            title = {
                Text(
                    text = "${shift.dateKey} • ${if (shift.shiftType == "OPENING") "Opening" else "Closing"}",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Staff: ${shift.staffName} • Status: ${shift.status} • Submitted: ${formatKitchenTime(shift.submittedAt)}",
                        fontSize = 12.sp,
                        color = KitchenMuted
                    )
                    if (shift.notes.isNotBlank()) {
                        Text("Notes: ${shift.notes}", fontSize = 12.sp, color = KitchenInk, fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = KitchenBorder)
                    shiftEntries.sortedBy { it.itemNameSnapshot }.forEach { e ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(e.itemNameSnapshot, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = KitchenInk)
                                if (e.wastageQty > 0.0) {
                                    Text(
                                        text = "Waste: ${KitchenInventoryCalculator.formatQty(e.wastageQty, e.unitLabelSnapshot)} ${e.wastageReason}",
                                        fontSize = 11.sp,
                                        color = KitchenRed
                                    )
                                }
                            }
                            Text(
                                text = KitchenInventoryCalculator.formatQty(e.totalUsableQty, e.unitLabelSnapshot),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = KitchenInk
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingShift = null }) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ManagerComparisonContent(
    summary: KitchenInventoryDashboardSummary,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    sectionFilter: String,
    onSectionFilterChanged: (String) -> Unit
) {
    val filteredRows = remember(summary.varianceRows, searchQuery, sectionFilter) {
        summary.varianceRows.filter { row ->
            (sectionFilter == "ALL" || row.item.sectionId == sectionFilter) &&
                (searchQuery.isBlank() || row.item.name.contains(searchQuery, ignoreCase = true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ManagerShiftMiniCard(
                    title = "Today Opening",
                    shift = summary.openingShift,
                    checkedCount = summary.openingCheckedCount,
                    totalItems = summary.totalActiveItems,
                    modifier = Modifier.weight(1f)
                )
                ManagerShiftMiniCard(
                    title = "Today Closing",
                    shift = summary.closingShift,
                    checkedCount = summary.closingCheckedCount,
                    totalItems = summary.totalActiveItems,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChanged,
                label = { Text("Search items") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            SectionFilterChipsRow(sectionFilter = sectionFilter, onSelect = onSectionFilterChanged)
        }

        items(filteredRows) { row ->
            val badgeColor = when (row.latestStatus) {
                KitchenStockStatus.GREEN -> KitchenGreen
                KitchenStockStatus.ORANGE -> KitchenOrange
                KitchenStockStatus.RED -> KitchenRed
            }
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, KitchenBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${row.item.iconEmoji} ${row.item.name}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenInk
                        )
                        Surface(
                            color = badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = row.latestStatus.label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Open: ${row.openingQty?.let { KitchenInventoryCalculator.formatQty(it, row.item.unitLabel) } ?: "—"}",
                            fontSize = 12.sp,
                            color = KitchenMuted
                        )
                        Text(
                            text = "Close: ${row.closingQty?.let { KitchenInventoryCalculator.formatQty(it, row.item.unitLabel) } ?: "—"}",
                            fontSize = 12.sp,
                            color = KitchenInk,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Used: ${row.consumedQty?.let { KitchenInventoryCalculator.formatQty(it, row.item.unitLabel) } ?: "—"}",
                            fontSize = 12.sp,
                            color = KitchenSky,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (row.wastageQty > 0.0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Wastage: ${KitchenInventoryCalculator.formatQty(row.wastageQty, row.item.unitLabel)} (${row.wastageReason.ifBlank { "Damaged" }})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KitchenRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerShiftMiniCard(
    title: String,
    shift: KitchenInventoryShiftEntity?,
    checkedCount: Int,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, KitchenBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = KitchenInk)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = when {
                    shift?.status == "SUBMITTED" -> "Submitted ($checkedCount/$totalItems)"
                    shift != null -> "In Progress ($checkedCount/$totalItems)"
                    else -> "Not Started"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (shift?.status == "SUBMITTED") KitchenGreen else KitchenOrange
            )
            Text(
                text = if (shift != null) "${shift.staffName} • ${formatKitchenTime(if (shift.submittedAt > 0L) shift.submittedAt else shift.lastUpdatedAt)}" else "—",
                fontSize = 11.sp,
                color = KitchenMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ManagerReorderContent(summary: KitchenInventoryDashboardSummary) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Automatic Reorder List",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = KitchenInk
                    )
                    Text(
                        text = "${summary.reorderList.size} items below minimum stock",
                        fontSize = 12.sp,
                        color = KitchenMuted
                    )
                }
                if (summary.reorderList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val shareText = buildString {
                                appendLine("FILTR COFFEE — KITCHEN REORDER LIST (${summary.dateKey})")
                                appendLine()
                                summary.reorderList.forEachIndexed { idx, r ->
                                    val packNote = if (r.suggestedPacksToOrder > 0) {
                                        " (~${r.suggestedPacksToOrder} ${r.item.containerLabel.ifBlank { "packs" }})"
                                    } else ""
                                    appendLine(
                                        "${idx + 1}. ${r.item.name} [${r.orderStatusLabel}] — " +
                                            "Now: ${KitchenInventoryCalculator.formatQty(r.currentStock, r.item.unitLabel)}, " +
                                            "Min: ${KitchenInventoryCalculator.formatQty(r.minimumRequiredStock, r.item.unitLabel)}, " +
                                            "Buy: ${KitchenInventoryCalculator.formatQty(r.suggestedPurchaseQty, r.item.unitLabel)}$packNote"
                                    )
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Reorder List"))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (summary.reorderList.isEmpty()) {
            item {
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KitchenGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "All checked items are at sufficient stock levels (GREEN).",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = KitchenGreen,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(summary.reorderList) { rec ->
                val color = if (rec.status == KitchenStockStatus.RED) KitchenRed else KitchenOrange
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, color),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rec.item.iconEmoji} ${rec.item.name}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = KitchenInk
                            )
                            Surface(
                                color = color.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = rec.orderStatusLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = color,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Current: ${KitchenInventoryCalculator.formatQty(rec.currentStock, rec.item.unitLabel)}  •  Min Required: ${KitchenInventoryCalculator.formatQty(rec.minimumRequiredStock, rec.item.unitLabel)}",
                            fontSize = 12.sp,
                            color = KitchenMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val packLabel = if (rec.suggestedPacksToOrder > 0) {
                            " (${rec.suggestedPacksToOrder} ${rec.item.containerLabel.ifBlank { "packs" }})"
                        } else ""
                        Text(
                            text = "Suggested Order: ${KitchenInventoryCalculator.formatQty(rec.suggestedPurchaseQty, rec.item.unitLabel)}$packLabel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = KitchenSky
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerHistoryContent(
    allShifts: List<KitchenInventoryShiftEntity>,
    allEntries: List<KitchenInventoryEntryEntity>,
    onSelectShift: (KitchenInventoryShiftEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (allShifts.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KitchenBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No historical kitchen inventory shifts recorded yet.",
                        fontSize = 14.sp,
                        color = KitchenMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(allShifts) { shift ->
                val shiftEntries = allEntries.filter { it.shiftId == shift.id && it.isChecked }
                val lowCount = shiftEntries.count { it.stockStatus == KitchenStockStatus.ORANGE.id }
                val outCount = shiftEntries.count { it.stockStatus == KitchenStockStatus.RED.id }
                val isSub = shift.status == "SUBMITTED"
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KitchenBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectShift(shift) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${shift.dateKey} • ${if (shift.shiftType == "OPENING") "Opening Check" else "Closing Check"}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = KitchenInk
                            )
                            Text(
                                text = "By ${shift.staffName} at ${formatKitchenTime(if (shift.submittedAt > 0L) shift.submittedAt else shift.lastUpdatedAt)}",
                                fontSize = 12.sp,
                                color = KitchenMuted
                            )
                            Text(
                                text = "${shiftEntries.size} items • Low: $lowCount • Out: $outCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (outCount > 0) KitchenRed else KitchenInk
                            )
                        }
                        Surface(
                            color = if (isSub) KitchenGreen.copy(alpha = 0.14f) else KitchenOrange.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isSub) "SUBMITTED" else "IN PROGRESS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isSub) KitchenGreen else KitchenOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerConfigureContent(
    allItems: List<KitchenInventoryItemEntity>,
    sectionFilter: String,
    onSectionFilterChanged: (String) -> Unit,
    onEditItem: (KitchenInventoryItemEntity) -> Unit,
    onOpenAddItem: () -> Unit
) {
    val visibleItems = remember(allItems, sectionFilter) {
        allItems.filter { sectionFilter == "ALL" || it.sectionId == sectionFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Configure Items & Thresholds",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = KitchenInk
                    )
                    Text(
                        text = "Tap any item to adjust min levels, units, pack size, or section.",
                        fontSize = 12.sp,
                        color = KitchenMuted
                    )
                }
                Button(
                    onClick = onOpenAddItem,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KitchenInk)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Item", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            SectionFilterChipsRow(sectionFilter = sectionFilter, onSelect = onSectionFilterChanged)
        }

        if (visibleItems.isEmpty()) {
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, KitchenBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (sectionFilter == KitchenSection.OUTSIDE_FREEZER.id) {
                                "Outside Freezer has 0 items configured right now."
                            } else {
                                "No items in this category."
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = KitchenInk
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Item' above whenever you are ready to configure items here.",
                            fontSize = 12.sp,
                            color = KitchenMuted
                        )
                    }
                }
            }
        } else {
            items(visibleItems) { item ->
                Surface(
                    color = if (item.isActive) Color.White else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KitchenBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEditItem(item) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.iconEmoji} ${item.name}" + if (!item.isActive) " (Inactive)" else "",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isActive) KitchenInk else KitchenMuted
                            )
                            Text(
                                text = "${KitchenSection.fromId(item.sectionId).shortTitle} • ${KitchenCountingMode.fromId(item.countingMode).displayLabel}",
                                fontSize = 11.sp,
                                color = KitchenMuted
                            )
                            Text(
                                text = "Min Alert: ${KitchenInventoryCalculator.formatQty(item.minStockThreshold, item.unitLabel)}  •  Target: ${KitchenInventoryCalculator.formatQty(item.targetStockLevel, item.unitLabel)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = KitchenSky
                            )
                        }
                        Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = KitchenMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionFilterChipsRow(
    sectionFilter: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            val sel = sectionFilter == "ALL"
            Surface(
                color = if (sel) KitchenInk else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onSelect("ALL") }
            ) {
                Text(
                    text = "All Zones",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sel) Color.White else KitchenInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        items(KitchenSection.entries) { sec ->
            val sel = sectionFilter == sec.id
            Surface(
                color = if (sel) KitchenInk else Color(0xFFF1F5F9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onSelect(sec.id) }
            ) {
                Text(
                    text = "${sec.iconEmoji} ${sec.shortTitle}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sel) Color.White else KitchenInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun KitchenItemConfigDialog(
    existing: KitchenInventoryItemEntity?,
    defaultSectionId: String,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var emoji by remember(existing?.id) { mutableStateOf(existing?.iconEmoji ?: "📦") }
    var sectionId by remember(existing?.id) { mutableStateOf(existing?.sectionId ?: defaultSectionId) }
    var countingMode by remember(existing?.id) { mutableStateOf(existing?.countingMode ?: KitchenCountingMode.DIRECT_PIECES.id) }
    var unitLabel by remember(existing?.id) { mutableStateOf(existing?.unitLabel ?: "pcs") }
    var containerLabel by remember(existing?.id) { mutableStateOf(existing?.containerLabel ?: "packets") }
    var packSizeText by remember(existing?.id) { mutableStateOf((existing?.packSize ?: 1.0).toString()) }
    var minThreshText by remember(existing?.id) { mutableStateOf((existing?.minStockThreshold ?: 1.0).toString()) }
    var targetText by remember(existing?.id) { mutableStateOf((existing?.targetStockLevel ?: 2.0).toString()) }
    var isActive by remember(existing?.id) { mutableStateOf(existing?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existing == null) "Add Kitchen Item" else "Configure ${existing.name}",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (it.length <= 4) emoji = it },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(72.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Item Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Storage Section", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KitchenInk)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(KitchenSection.entries) { sec ->
                        val sel = sectionId == sec.id
                        Surface(
                            color = if (sel) KitchenInk else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { sectionId = sec.id }
                        ) {
                            Text(
                                text = "${sec.iconEmoji} ${sec.shortTitle}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else KitchenInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Text("Counting Mode", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = KitchenInk)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(KitchenCountingMode.entries) { mode ->
                        val sel = countingMode == mode.id
                        Surface(
                            color = if (sel) KitchenSky else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable {
                                countingMode = mode.id
                                unitLabel = when (mode) {
                                    KitchenCountingMode.SEALED_AND_PERCENTAGE -> "tubs"
                                    KitchenCountingMode.SEALED_AND_GRAMS,
                                    KitchenCountingMode.DIRECT_GRAMS -> "g"
                                    KitchenCountingMode.SEALED_AND_ML,
                                    KitchenCountingMode.DIRECT_ML -> "ml"
                                    KitchenCountingMode.SEALED_AND_PIECES,
                                    KitchenCountingMode.DIRECT_PIECES -> "pcs"
                                }
                            }
                        ) {
                            Text(
                                text = mode.displayLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) Color.White else KitchenInk,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = { unitLabel = it },
                        label = { Text("Unit (g/ml/pcs)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = containerLabel,
                        onValueChange = { containerLabel = it },
                        label = { Text("Container") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = packSizeText,
                    onValueChange = { packSizeText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Sealed Pack Size in $unitLabel") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minThreshText,
                        onValueChange = { minThreshText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Min Alert ($unitLabel)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Par Level ($unitLabel)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (existing != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active in Daily Checklists", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        emoji,
                        sectionId,
                        countingMode,
                        unitLabel,
                        containerLabel,
                        packSizeText.toDoubleOrNull() ?: 1.0,
                        minThreshText.toDoubleOrNull() ?: 1.0,
                        targetText.toDoubleOrNull() ?: 2.0,
                        isActive
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = KitchenGreen)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

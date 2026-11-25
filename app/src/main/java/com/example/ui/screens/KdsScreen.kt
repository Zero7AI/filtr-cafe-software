package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import com.example.domain.kds.KdsBaristaColorPalette
import com.example.domain.kds.KdsQueueUnitItem
import com.example.domain.kds.KdsUnitStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KdsScreen(
    queueItems: List<KdsQueueUnitItem>,
    availableBaristas: List<String>,
    staffRoster: List<StaffRosterEntity>,
    activeBaristaName: String,
    paidOnlyFilter: Boolean,
    todayOnlyFilter: Boolean = true,
    onDutyStaffKeys: Set<String> = emptySet(),
    isStaffMode: Boolean,
    onSelectBarista: (String) -> Unit,
    onTogglePaidOnlyFilter: (Boolean) -> Unit,
    onToggleTodayOnlyFilter: (Boolean) -> Unit = {},
    onTapQueueItem: (KdsQueueUnitItem) -> Unit,
    onUnclaimQueueItem: (KdsQueueUnitItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val rosterByNormalized = staffRoster.associateBy { AttendanceCalculator.normalizeStaffKey(it.name) }
    val baristaChoices = availableBaristas
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .ifEmpty { listOf(activeBaristaName.ifBlank { "Barista" }) }

    val makingCount = queueItems.count { it.stage == KdsUnitStage.MAKING }
    val activeBaristaColor = Color(
        KdsBaristaColorPalette.parseComposeColor(
            KdsBaristaColorPalette.resolveColorHex(
                activeBaristaName,
                rosterByNormalized[AttendanceCalculator.normalizeStaffKey(activeBaristaName)]?.colorHex.orEmpty()
            )
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("kds_screen")
    ) {
        // Compact Top Summary Card (matches OrdersScreen banner aesthetic)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = activeBaristaColor,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.LocalCafe,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "LIVE KDS QUEUE (FIFO)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "1st tap Make • 2nd tap Done • Hold Unclaim",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KdsMetricPill(
                        label = "${queueItems.size} Left",
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        tag = "kds_pending_count"
                    )
                    if (makingCount > 0) {
                        KdsMetricPill(
                            label = "$makingCount Making",
                            containerColor = activeBaristaColor,
                            contentColor = Color.White,
                            tag = "kds_making_count"
                        )
                    }
                }
            }
        }

        // Barista Selector + Today/Backlog & Paid Filters Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(items = baristaChoices, key = { it.lowercase(Locale.US) }) { barista ->
                    val normalizedKey = AttendanceCalculator.normalizeStaffKey(barista)
                    val isSelected = barista.equals(activeBaristaName, ignoreCase = true)
                    val isOnDuty = normalizedKey in onDutyStaffKeys
                    val rosterEntry = rosterByNormalized[normalizedKey]
                    val colorHex = KdsBaristaColorPalette.resolveColorHex(
                        barista,
                        rosterEntry?.colorHex.orEmpty()
                    )
                    val dotColor = Color(KdsBaristaColorPalette.parseComposeColor(colorHex))

                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectBarista(barista) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color.White else dotColor)
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = barista,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (isOnDuty) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0xFF86EFAC) else Color(0xFF16A34A))
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = dotColor,
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF4F4F5),
                            labelColor = Color(0xFF27272A)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("kds_barista_chip_${barista.lowercase(Locale.US).replace(" ", "_")}")
                    )
                }
            }

            FilterChip(
                selected = todayOnlyFilter,
                onClick = { onToggleTodayOnlyFilter(!todayOnlyFilter) },
                label = {
                    Text(
                        text = if (todayOnlyFilter) "Today ✓" else "All Days",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF09090B),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFFF4F4F5),
                    labelColor = Color(0xFF52525B)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("kds_today_filter_chip")
            )

            FilterChip(
                selected = paidOnlyFilter,
                onClick = { onTogglePaidOnlyFilter(!paidOnlyFilter) },
                label = {
                    Text(
                        text = if (paidOnlyFilter) "Paid ✓" else "All",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF16A34A),
                    selectedLabelColor = Color.White,
                    containerColor = Color(0xFFF4F4F5),
                    labelColor = Color(0xFF52525B)
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("kds_paid_filter_chip")
            )
        }

        if (queueItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = when {
                            paidOnlyFilter && todayOnlyFilter -> "No pending Paid orders for today"
                            todayOnlyFilter -> "All orders are prepared!"
                            paidOnlyFilter -> "No pending Paid orders in queue"
                            else -> "All orders are prepared!"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF18181B),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (todayOnlyFilter) {
                            "Today's new orders will appear here live in FIFO order. Tap 'Today ✓' to inspect older backlog."
                        } else {
                            "New orders will appear here automatically in FIFO order."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF71717A),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("kds_queue_list"),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = queueItems,
                    key = { it.claimKey }
                ) { unit ->
                    KdsQueueCard(
                        unit = unit,
                        activeBaristaName = activeBaristaName,
                        isStaffMode = isStaffMode,
                        onTap = { onTapQueueItem(unit) },
                        onLongPress = { onUnclaimQueueItem(unit) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KdsQueueCard(
    unit: KdsQueueUnitItem,
    activeBaristaName: String,
    isStaffMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val isMaking = unit.stage == KdsUnitStage.MAKING
    val claimColor = if (isMaking) {
        Color(KdsBaristaColorPalette.parseComposeColor(unit.claimedColorHex))
    } else {
        Color(0xFF09090B)
    }
    val cardBackground = if (isMaking) claimColor.copy(alpha = 0.12f) else Color.White
    val borderColor = if (isMaking) claimColor else Color(0xFFE4E4E7)
    val borderWidth = if (isMaking) 2.dp else 1.dp

    val timeFormatted = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(unit.orderTimestamp))
    val isClaimedByMe = isMaking && unit.claimedByStaff.equals(activeBaristaName.trim(), ignoreCase = true)

    Surface(
        color = cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = if (isMaking) 2.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("kds_card_${unit.claimKey}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Queue Rank Badge
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isMaking) claimColor else Color(0xFF18181B)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${unit.queuePosition}",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Center Details: Drink Name, Unit Info, Order #, Time, Notes
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = unit.itemName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF09090B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (unit.totalUnits > 1) {
                        Surface(
                            color = Color(0xFF09090B).copy(alpha = 0.08f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Cup ${unit.unitIndex}/${unit.totalUnits}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF27272A),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = Color(0xFFF4F4F5),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFD4D4D8))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = Color(0xFF52525B),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "#${unit.shortOrderNumber}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF18181B),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF71717A),
                        maxLines = 1,
                        softWrap = false
                    )

                    if (unit.customerName.isNotBlank() && !unit.customerName.equals("Walk-in", ignoreCase = true)) {
                        Text(
                            text = "• ${unit.customerName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF3F3F46),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (unit.orderNotes.isNotBlank()) {
                    Text(
                        text = "Note: ${unit.orderNotes}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB45309),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Action Status Pill + Paid Pill
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Surface(
                    color = if (unit.isPaid) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (unit.isPaid) "PAID" else "UNPAID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (unit.isPaid) Color(0xFF15803D) else Color(0xFFB91C1C),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (isMaking) {
                    Surface(
                        color = claimColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = when {
                                    isClaimedByMe -> "${unit.claimedByStaff} • Tap Done"
                                    !isStaffMode -> "${unit.claimedByStaff} • Tap Done"
                                    else -> "Making: ${unit.claimedByStaff}"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF09090B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Coffee,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Tap to Make",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KdsMetricPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    tag: String
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag(tag)
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

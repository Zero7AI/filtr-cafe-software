package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.OrderWithItems
import com.example.domain.inventory.InventoryUsageCalculator
import com.example.ui.BillingUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun InventoryUsageScreen(
    state: BillingUiState,
    inventoryItems: List<InventoryItemEntity>,
    usages: List<InventoryUsageEntity>,
    ordersList: List<OrderWithItems>,
    onOpenAddStockDialog: ((InventoryItemEntity) -> Unit)? = null
) {
    var usageDateFilter by remember { mutableStateOf("TODAY") }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }

    var dayBoundaryTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            val nowCal = Calendar.getInstance()
            val nextMidnight = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 1)
                set(Calendar.MILLISECOND, 0)
            }
            val delayMs = (nextMidnight.timeInMillis - nowCal.timeInMillis).coerceAtLeast(1_000L)
            delay(delayMs)
            dayBoundaryTick++
        }
    }

    val todayStart = remember(dayBoundaryTick) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val yesterdayStart = remember(todayStart) {
        Calendar.getInstance().apply {
            timeInMillis = todayStart
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
    }

    val filteredOrders = remember(ordersList, usageDateFilter, customStartMs, customEndMs, todayStart, yesterdayStart) {
        ordersList.filter { orderWithItems ->
            val orderTime = orderWithItems.order.timestamp
            when (usageDateFilter) {
                "TODAY" -> orderTime >= todayStart
                "YESTERDAY" -> orderTime in yesterdayStart until todayStart
                "LAST_7_DAYS" -> {
                    val weekAgo = Calendar.getInstance().apply {
                        timeInMillis = todayStart
                        add(Calendar.DAY_OF_YEAR, -6)
                    }.timeInMillis
                    orderTime >= weekAgo
                }
                "THIS_MONTH" -> {
                    val thirtyDaysAgo = Calendar.getInstance().apply {
                        timeInMillis = todayStart
                        add(Calendar.DAY_OF_YEAR, -29)
                    }.timeInMillis
                    orderTime >= thirtyDaysAgo
                }
                "CUSTOM" -> {
                    val start = customStartMs ?: 0L
                    val end = customEndMs ?: Long.MAX_VALUE
                    orderTime in start..end
                }
                else -> true
            }
        }
    }

    val report = remember(filteredOrders, inventoryItems, usages) {
        InventoryUsageCalculator.computeIngredientUsage(
            orders = filteredOrders,
            inventoryItems = inventoryItems,
            usages = usages
        )
    }

    val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val customLabel = remember(usageDateFilter, customStartMs, customEndMs) {
        if (usageDateFilter == "CUSTOM" && customStartMs != null && customEndMs != null) {
            "${sdf.format(Date(customStartMs!!))} - ${sdf.format(Date(customEndMs!!))}"
        } else {
            "Pick Date"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("inventory_usage_screen")
    ) {
        // Minimal Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inventory Usage",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "Calculated from Recipe Master × Orders Made",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = AmberAccent.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "${report.ordersCount} Orders",
                    color = AmberAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Clean Date Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                "TODAY" to "Today",
                "YESTERDAY" to "Yesterday",
                "LAST_7_DAYS" to "7 Days",
                "THIS_MONTH" to "30 Days"
            )
            items(filters, key = { it.first }) { (key, label) ->
                val isSelected = usageDateFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { usageDateFilter = key },
                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberAccent,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceColor,
                        labelColor = TextGray
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
            item {
                val isCustom = usageDateFilter == "CUSTOM"
                FilterChip(
                    selected = isCustom,
                    onClick = { showCustomDateDialog = true },
                    leadingIcon = {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    label = { Text(customLabel, fontSize = 12.sp, fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmberAccent,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceColor,
                        labelColor = TextGray
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (report.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = SurfaceVariantColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (report.ordersCount == 0) "No orders recorded for this period"
                        else "No Recipe Master mappings matched these orders",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configure ingredients in Recipe Master to see exact usage",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Clean 2-column table header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceVariantColor.copy(alpha = 0.45f))
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INGREDIENT (${report.items.size})",
                            color = TextGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "EXACT USAGE",
                            color = TextGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                    }

                    HorizontalDivider(color = SurfaceVariantColor)

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(report.items, key = { _, item -> item.inventoryItemId }) { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.itemName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                                )
                                Text(
                                    text = row.exactUsageLabel,
                                    color = AmberAccent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            if (idx < report.items.lastIndex) {
                                HorizontalDivider(
                                    color = SurfaceVariantColor.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDateDialog) {
        CustomDateRangePickerDialog(
            onDismiss = { showCustomDateDialog = false },
            onDateRangeApplied = { startMs, endMs ->
                customStartMs = startMs
                customEndMs = endMs
                usageDateFilter = "CUSTOM"
                showCustomDateDialog = false
            }
        )
    }
}

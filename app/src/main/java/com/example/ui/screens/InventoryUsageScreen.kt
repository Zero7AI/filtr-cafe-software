package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.calculatePacksAndCost
import com.example.ui.BillingUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val EmeraldSuccess = Color(0xFF10B981)
private val RedDanger = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
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

    // Date range filtering
    val now = Calendar.getInstance()
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val yesterdayStart = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val filteredOrders = remember(ordersList, usageDateFilter, customStartMs, customEndMs) {
        ordersList.filter { orderWithItems ->
            val orderTime = orderWithItems.order.timestamp
            when (usageDateFilter) {
                "TODAY" -> orderTime >= todayStart
                "YESTERDAY" -> orderTime in yesterdayStart until todayStart
                "LAST_7_DAYS" -> {
                    val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                    orderTime >= weekAgo.timeInMillis
                }
                "THIS_MONTH" -> {
                    val monthAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                    orderTime >= monthAgo.timeInMillis
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

    // Calculate aggregated inventory consumption for the selected period
    val consumptionBreakdown = remember(filteredOrders, usages, inventoryItems) {
        val rawUsageMap = mutableMapOf<Int, Pair<Double, String>>()

        filteredOrders.forEach { orderWithItems ->
            orderWithItems.items.forEach { orderItem ->
                val recipeUsages = usages.filter { it.menuItemId == orderItem.menuItemId }
                if (recipeUsages.isNotEmpty()) {
                    recipeUsages.forEach { usage ->
                        val current = rawUsageMap[usage.inventoryItemId]?.first ?: 0.0
                        val consumed = usage.quantityUsed * orderItem.quantity
                        rawUsageMap[usage.inventoryItemId] = Pair(current + consumed, usage.unit)
                    }
                } else {
                    // Direct item fallback
                    val directItem = inventoryItems.find {
                        it.name.equals(orderItem.itemName, ignoreCase = true) ||
                                orderItem.itemName.contains(it.name, ignoreCase = true) ||
                                it.name.contains(orderItem.itemName, ignoreCase = true)
                    }
                    if (directItem != null) {
                        val current = rawUsageMap[directItem.id]?.first ?: 0.0
                        val consumed = orderItem.quantity.toDouble()
                        rawUsageMap[directItem.id] = Pair(current + consumed, directItem.unit)
                    }
                }
            }
        }

        val items = rawUsageMap.mapNotNull { (invId, pair) ->
            val (qty, usageUnit) = pair
            val invItem = inventoryItems.find { it.id == invId } ?: return@mapNotNull null
            val (packs, cost) = calculatePacksAndCost(qty, usageUnit, invItem)
            ConsumedItemRecord(
                item = invItem,
                quantityUsed = qty,
                unit = usageUnit,
                packsUsed = packs,
                cost = cost
            )
        }.sortedByDescending { it.cost }

        val totalCost = items.sumOf { it.cost }
        Pair(items, totalCost)
    }

    val (consumedItems, totalConsumedCost) = consumptionBreakdown

    // Low stock alert items (estimated <= 7 orders remaining)
    val lowStockAlerts = remember(inventoryItems, usages) {
        inventoryItems.filter { it.isActive }.mapNotNull { item ->
            val itemUsages = usages.filter { it.inventoryItemId == item.id }
            val ordersLeft: Int = if (itemUsages.isNotEmpty()) {
                val maxPacksPerOrder = itemUsages.maxOfOrNull { usage ->
                    val (packs, _) = calculatePacksAndCost(usage.quantityUsed, usage.unit, item)
                    packs
                } ?: 0.0

                if (maxPacksPerOrder > 0.0) {
                    (item.currentStock / maxPacksPerOrder).toInt()
                } else {
                    (item.currentStock * item.standardQuantity).toInt()
                }
            } else {
                (item.currentStock * (if (item.standardQuantity > 0) item.standardQuantity else 1.0)).toInt()
            }

            if (ordersLeft <= 7) {
                LowStockAlertItem(item, ordersLeft.coerceAtLeast(0))
            } else null
        }.sortedBy { it.ordersRemaining }
    }

    val filterTitle = when (usageDateFilter) {
        "TODAY" -> "Today's Usage"
        "YESTERDAY" -> "Yesterday's Usage"
        "LAST_7_DAYS" -> "Last 7 Days Usage"
        "THIS_MONTH" -> "Last 30 Days Usage"
        "CUSTOM" -> "Custom Date Usage"
        else -> "All-Time Usage"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INVENTORY USAGE",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "Daily Consumption & Ingredient Cost Breakdown",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Date Filter Chips
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

            items(filters) { (key, label) ->
                val isSelected = usageDateFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { usageDateFilter = key },
                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
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
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Custom Date", fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium) },
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

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // LOW STOCK / REORDER ALERT BANNER (≤ 7 Orders Left)
            if (lowStockAlerts.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = RedDanger.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, RedDanger.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Alert",
                                        tint = RedDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CRITICAL REORDER ALERTS (≤ 7 Orders Left)",
                                        color = RedDanger,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Surface(
                                    color = RedDanger.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "${lowStockAlerts.size} Items",
                                        color = RedDanger,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Alert item chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                lowStockAlerts.take(5).forEach { alert ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Circle,
                                                contentDescription = null,
                                                tint = RedDanger,
                                                modifier = Modifier.size(6.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = alert.item.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${formatDouble(alert.item.currentStock)} ${alert.item.unit} stock",
                                                color = TextGray,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = RedDanger.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Only ~${alert.ordersRemaining} orders left",
                                                    color = Color(0xFFFCA5A5),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CONSUMPTION SUMMARY HERO CARD
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, SurfaceVariantColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = filterTitle.uppercase(),
                                color = TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = EmeraldSuccess.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${filteredOrders.size} Orders Served",
                                    color = EmeraldSuccess,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = formatCurrency(totalConsumedCost),
                            color = AmberAccent,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Total procurement cost of ingredients consumed",
                            color = TextGray,
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = SurfaceVariantColor.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Distinct Items Used", color = TextGray, fontSize = 11.sp)
                                Text(
                                    "${consumedItems.size} Ingredients",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Avg Cost / Order", color = TextGray, fontSize = 11.sp)
                                val avg = if (filteredOrders.isNotEmpty()) totalConsumedCost / filteredOrders.size else 0.0
                                Text(
                                    formatCurrency(avg),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // ITEMIZED CONSUMPTION LIST HEADER
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ITEMIZED INVENTORY CONSUMED",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${consumedItems.size} items",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }
            }

            if (consumedItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = TextGray.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No inventory consumed in this period",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(consumedItems) { record ->
                    val sharePercent = if (totalConsumedCost > 0) (record.cost / totalConsumedCost) * 100 else 0.0

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.item.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${record.item.category} • Pack: ${record.item.standardQuantity} ${record.item.unit}",
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = formatCurrency(record.cost),
                                        color = AmberAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", sharePercent)}% of total",
                                        color = TextGray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quantity details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = SurfaceVariantColor.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "Used: ${formatDouble(record.quantityUsed)} ${record.unit}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = "≈ ${formatDouble(record.packsUsed)} packs",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Custom Date Range Picker Dialog
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

private data class ConsumedItemRecord(
    val item: InventoryItemEntity,
    val quantityUsed: Double,
    val unit: String,
    val packsUsed: Double,
    val cost: Double
)

private data class LowStockAlertItem(
    val item: InventoryItemEntity,
    val ordersRemaining: Int
)

private fun formatDouble(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

private fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 2
    return format.format(value)
}

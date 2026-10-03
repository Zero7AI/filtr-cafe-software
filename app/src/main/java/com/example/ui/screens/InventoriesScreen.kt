package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.calculatePacksAndCost
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.OrderWithItems
import com.example.ui.BillingUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

data class ItemConsumptionRecord(
    val inventoryItem: InventoryItemEntity,
    val totalQuantityUsed: Double,
    val usageUnit: String,
    val totalPacksUsed: Double,
    val totalCost: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoriesScreen(
    state: BillingUiState,
    inventoryItems: List<InventoryItemEntity>,
    usages: List<InventoryUsageEntity>,
    ordersList: List<OrderWithItems>,
    inventoryOrders: List<InventoryOrderWithItems> = emptyList(),
    onUpdateStock: (itemId: Int, newStock: Double) -> Unit,
    onStartEditingStock: (Int) -> Unit,
    onCancelEditingStock: () -> Unit,
    onOpenAddStockDialog: (InventoryItemEntity) -> Unit,
    onDismissAddStockDialog: () -> Unit,
    onAddStockPacks: (itemId: Int, packsToAdd: Double) -> Unit,
    onOpenInventoryMaster: () -> Unit,
    onSaveInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    onEditInventoryItem: (InventoryItemEntity?) -> Unit,
    onDeleteInventoryItem: (InventoryItemEntity) -> Unit,
    onDismissInventoryMaster: () -> Unit,
    onDismissAddInventoryItem: () -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    // Date filter state (mirrors Analytics Dashboard)
    var dateFilter by remember { mutableStateOf("TODAY") } // TODAY, YESTERDAY, LAST_7_DAYS, THIS_MONTH, ALL_TIME, CUSTOM
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var isBreakdownExpanded by remember { mutableStateOf(false) }
    var isSupplierBreakdownExpanded by remember { mutableStateOf(false) }
    var stockFilter by remember { mutableStateOf("ALL") } // ALL, USED, LOW_STOCK

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Filter orders based on the selected date or date range
    val filteredOrders = remember(ordersList, dateFilter, customStartMs, customEndMs) {
        ordersList.filter { orderWithItems ->
            val orderTime = orderWithItems.order.timestamp
            when (dateFilter) {
                "TODAY" -> orderTime >= todayStart
                "YESTERDAY" -> {
                    val yesterdayStart = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    orderTime in yesterdayStart until todayStart
                }
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
                else -> true // ALL_TIME
            }
        }
    }

    // Calculate number of active days in period for accurate burn-rate calculation
    val periodDays = remember(dateFilter, customStartMs, customEndMs, ordersList) {
        when (dateFilter) {
            "TODAY", "YESTERDAY" -> 1
            "LAST_7_DAYS" -> 7
            "THIS_MONTH" -> 30
            "CUSTOM" -> {
                val start = customStartMs ?: 0L
                val end = customEndMs ?: System.currentTimeMillis()
                val diffDays = ((end - start) / (1000 * 60 * 60 * 24)).toInt()
                max(1, diffDays + 1)
            }
            else -> {
                if (ordersList.isNotEmpty()) {
                    val minTime = ordersList.minOf { it.order.timestamp }
                    val diffDays = ((System.currentTimeMillis() - minTime) / (1000 * 60 * 60 * 24)).toInt()
                    max(1, diffDays + 1)
                } else 1
            }
        }
    }

    val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
    val dateRangeLabel = remember(dateFilter, customStartMs, customEndMs) {
        when (dateFilter) {
            "TODAY" -> "Today"
            "YESTERDAY" -> "Yesterday"
            "LAST_7_DAYS" -> "Last 7 Days"
            "THIS_MONTH" -> "Last 30 Days"
            "ALL_TIME" -> "All Time"
            "CUSTOM" -> {
                val start = customStartMs ?: 0L
                val end = customEndMs ?: System.currentTimeMillis()
                val cal1 = Calendar.getInstance().apply { timeInMillis = start }
                val cal2 = Calendar.getInstance().apply { timeInMillis = end }
                val isSame = cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
                if (isSame) {
                    sdf.format(Date(start))
                } else {
                    "${sdf.format(Date(start))} - ${sdf.format(Date(end))}"
                }
            }
            else -> "Custom Range"
        }
    }

    // Calculate itemized consumption and total costs across the selected period
    val consumptionData = remember(filteredOrders, usages, inventoryItems) {
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
                    // Fallback for direct items (e.g. Fries, Peri peri, Ice cream, Water bottles)
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

        val breakdown = rawUsageMap.mapNotNull { (invId, pair) ->
            val (qty, unit) = pair
            val invItem = inventoryItems.find { it.id == invId } ?: return@mapNotNull null
            val (packs, cost) = calculatePacksAndCost(qty, unit, invItem)
            ItemConsumptionRecord(
                inventoryItem = invItem,
                totalQuantityUsed = qty,
                usageUnit = unit,
                totalPacksUsed = packs,
                totalCost = cost
            )
        }.sortedByDescending { it.totalCost }

        val totalCost = breakdown.sumOf { it.totalCost }
        val itemUsageMap = breakdown.associateBy { it.inventoryItem.id }

        Triple(breakdown, totalCost, itemUsageMap)
    }

    val (consumedBreakdown, totalConsumedCost, itemConsumptionMap) = consumptionData

    // Filter inventory purchase orders based on date filter
    val filteredInventoryOrders = remember(inventoryOrders, dateFilter, customStartMs, customEndMs) {
        inventoryOrders.filter { orderWithItems ->
            val orderTime = orderWithItems.order.timestamp
            when (dateFilter) {
                "TODAY" -> orderTime >= todayStart
                "YESTERDAY" -> {
                    val yesterdayStart = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    orderTime in yesterdayStart until todayStart
                }
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
                else -> true // ALL_TIME
            }
        }
    }

    val totalPeriodSpend = remember(filteredInventoryOrders) {
        filteredInventoryOrders.sumOf { it.order.totalAmount }
    }

    val supplierBreakdown = remember(filteredInventoryOrders) {
        filteredInventoryOrders
            .groupBy { it.order.supplierName.ifBlank { "WhatsApp Bill" } }
            .map { (supplier, orders) ->
                val amount = orders.sumOf { it.order.totalAmount }
                Triple(supplier, amount, orders.size)
            }
            .sortedByDescending { it.second }
    }

    // Filter stock items based on stockFilter and searchQuery
    val displayInventoryItems = remember(inventoryItems, stockFilter, itemConsumptionMap, state.inventorySearchQuery) {
        var list = inventoryItems
        val q = state.inventorySearchQuery.trim().lowercase()
        if (q.isNotBlank()) {
            list = list.filter { it.name.lowercase().contains(q) || it.category.lowercase().contains(q) }
        }
        when (stockFilter) {
            "USED" -> list.filter { itemConsumptionMap.containsKey(it.id) }
            "LOW_STOCK" -> list.filter { it.currentStock < 1.0 }
            else -> list
        }
    }

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
                Pair(item, ordersLeft.coerceAtLeast(0))
            } else null
        }.sortedBy { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "INVENTORIES",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Stock & Consumption Overview",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onOpenInventoryMaster,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantColor, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Inv Master", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Date Filter Chips Row (Mirroring Dashboard & Analytics)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            item {
                val isCustom = dateFilter == "CUSTOM"
                FilterChip(
                    selected = isCustom,
                    onClick = { showCustomDateDialog = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date Range",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            if (isCustom) dateRangeLabel else "📅 Custom Date",
                            fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceColor,
                        labelColor = TextGray
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }

            val presetFilters = listOf(
                "TODAY" to "Today",
                "YESTERDAY" to "Yesterday",
                "LAST_7_DAYS" to "7 Days",
                "THIS_MONTH" to "30 Days",
                "ALL_TIME" to "All Time"
            )

            items(presetFilters) { (filter, label) ->
                val isSelected = dateFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { dateFilter = filter },
                    label = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Color.Black,
                        containerColor = SurfaceColor,
                        labelColor = TextGray
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // LOW STOCK / REORDER ALERT SECTION (<= 7 Orders Left)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lowStockAlerts.isNotEmpty()) Color(0xFF7F1D1D).copy(alpha = 0.25f) else SurfaceColor
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (lowStockAlerts.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.6f) else SurfaceVariantColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (lowStockAlerts.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (lowStockAlerts.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (lowStockAlerts.isNotEmpty()) "CRITICAL REORDER ALERTS (≤ 7 Orders Left)" else "STOCK HEALTH STATUS",
                                    color = if (lowStockAlerts.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Surface(
                                color = if (lowStockAlerts.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (lowStockAlerts.isNotEmpty()) "${lowStockAlerts.size} Urgent Items" else "All Healthy (>7 Orders)",
                                    color = if (lowStockAlerts.isNotEmpty()) Color(0xFFEF4444) else Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (lowStockAlerts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                lowStockAlerts.forEach { (item, ordersLeft) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SurfaceColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Stock: ${formatDouble(item.currentStock)} ${item.unit} (${item.category})",
                                                color = TextGray,
                                                fontSize = 11.sp
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Only ~$ordersLeft orders left",
                                                    color = Color(0xFFFCA5A5),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { onOpenAddStockDialog(item) },
                                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Text("+ Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // HERO SECTION: INVENTORY SPEND & USAGE
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, SurfaceVariantColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "INVENTORY SPEND & USAGE",
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            Surface(
                                color = SurfaceVariantColor,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = dateRangeLabel,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Dual Metric Summary Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // TOTAL SPEND (Procured)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkBackground,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "TOTAL SPEND",
                                            color = TextGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ShoppingCart,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981), // Emerald
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = formatCurrency(totalPeriodSpend),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${filteredInventoryOrders.size} Purchase Bills",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (periodDays > 1 && totalPeriodSpend > 0) {
                                        Text(
                                            text = "~${formatCurrency(totalPeriodSpend / periodDays)}/day",
                                            color = TextGray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }

                            // TOTAL CONSUMED (Sales)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkBackground,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "TOTAL CONSUMED",
                                            color = TextGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.TrendingDown,
                                            contentDescription = null,
                                            tint = AmberAccent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = formatCurrency(totalConsumedCost),
                                        color = AmberAccent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${consumedBreakdown.size} Recipe Items Used",
                                        color = TextGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (periodDays > 1 && totalConsumedCost > 0) {
                                        Text(
                                            text = "~${formatCurrency(totalConsumedCost / periodDays)}/day",
                                            color = TextGray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = SurfaceVariantColor)

                        // 1. Spend by Supplier Expandable Section
                        if (supplierBreakdown.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isSupplierBreakdownExpanded = !isSupplierBreakdownExpanded }
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSupplierBreakdownExpanded) "Hide Spend by Supplier" else "View Spend by Supplier (${supplierBreakdown.size})",
                                    color = Color(0xFF10B981),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (isSupplierBreakdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = isSupplierBreakdownExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.padding(bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    supplierBreakdown.forEach { (supplier, amount, orderCount) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = DarkBackground,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = supplier,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "$orderCount bill${if (orderCount > 1) "s" else ""}",
                                                            color = TextGray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = formatCurrency(amount),
                                                        color = Color(0xFF10B981),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                if (totalPeriodSpend > 0) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    val progress = (amount / totalPeriodSpend).toFloat().coerceIn(0f, 1f)
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp),
                                                        color = Color(0xFF10B981),
                                                        trackColor = SurfaceVariantColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = SurfaceVariantColor)
                        }

                        // 2. Consumption Breakdown Expandable Section
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isBreakdownExpanded = !isBreakdownExpanded }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBreakdownExpanded) "Hide Consumption Breakdown" else "View Itemized Consumption (${consumedBreakdown.size})",
                                color = AmberAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (isBreakdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = AmberAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Expandable Itemized Breakdown List
                        AnimatedVisibility(
                            visible = isBreakdownExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (consumedBreakdown.isEmpty()) {
                                    Text(
                                        text = "No inventory consumption recorded for this period.",
                                        color = TextGray,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else {
                                    consumedBreakdown.forEach { record ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = DarkBackground,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = record.inventoryItem.name,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "${formatDouble(record.totalQuantityUsed)} ${record.usageUnit} (~${formatDouble(record.totalPacksUsed)} packs)",
                                                            color = TextGray,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                    Text(
                                                        text = formatCurrency(record.totalCost),
                                                        color = AmberAccent,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                if (totalConsumedCost > 0) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    val progress = (record.totalCost / totalConsumedCost).toFloat().coerceIn(0f, 1f)
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(4.dp),
                                                        color = AmberAccent,
                                                        trackColor = SurfaceVariantColor
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
            }

            // Quick Stock Filter Chips
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = stockFilter == "ALL",
                        onClick = { stockFilter = "ALL" },
                        label = { Text("All Stock (${inventoryItems.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceColor,
                            labelColor = TextGray
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    FilterChip(
                        selected = stockFilter == "USED",
                        onClick = { stockFilter = "USED" },
                        label = { Text("Used in Period (${consumedBreakdown.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceColor,
                            labelColor = TextGray
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    val lowCount = inventoryItems.count { it.currentStock < 1.0 }
                    FilterChip(
                        selected = stockFilter == "LOW_STOCK",
                        onClick = { stockFilter = "LOW_STOCK" },
                        label = { Text("Low Stock ($lowCount)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF4444),
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceColor,
                            labelColor = if (lowCount > 0) Color(0xFFEF4444) else TextGray
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Empty state check
            if (displayInventoryItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Warehouse,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = SurfaceVariantColor
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (stockFilter == "USED") "No items were consumed in $dateRangeLabel"
                                       else if (stockFilter == "LOW_STOCK") "All inventory stock is currently healthy!"
                                       else "No inventory items found.",
                                color = TextGray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                // Stock Items List
                items(displayInventoryItems) { item ->
                    val consumptionRecord = itemConsumptionMap[item.id]
                    val packsConsumed = consumptionRecord?.totalPacksUsed ?: 0.0
                    val dailyBurnPacks = if (periodDays > 0) packsConsumed / periodDays else 0.0
                    val daysLeft = if (dailyBurnPacks > 0) (item.currentStock / dailyBurnPacks).toInt() else null

                    InventoryItemCard(
                        item = item,
                        daysLeft = daysLeft,
                        dateRangeLabel = dateRangeLabel,
                        consumptionRecord = consumptionRecord,
                        isEditing = state.editingStockItemId == item.id,
                        onUpdateStock = { onUpdateStock(item.id, it) },
                        onStartEditingStock = { onStartEditingStock(item.id) },
                        onCancelEditingStock = onCancelEditingStock,
                        onAddStock = { onOpenAddStockDialog(item) }
                    )
                }
            }
        }
    }

    // Custom Date Range Picker Dialog
    if (showCustomDateDialog) {
        CustomDateRangePickerDialog(
            onDismiss = { showCustomDateDialog = false },
            onDateRangeApplied = { start, end ->
                customStartMs = start
                customEndMs = end
                dateFilter = "CUSTOM"
                showCustomDateDialog = false
            }
        )
    }

    // Add Stock Dialog
    if (state.showAddStockDialog) {
        state.addStockItem?.let { item ->
            AddStockDialog(
                item = item,
                onDismiss = onDismissAddStockDialog,
                onAddStock = { qty -> onAddStockPacks(item.id, qty) }
            )
        }
    }

    // Inventory Master Dialog
    if (state.showInventoryMasterDialog) {
        InventoryMasterDialog(
            state = state,
            inventoryItems = inventoryItems.let { items ->
                val q = state.inventorySearchQuery.lowercase()
                if (q.isBlank()) items else items.filter { it.name.lowercase().contains(q) }
            },
            onSearchQueryChanged = onSearchQueryChanged,
            onSaveInventoryItem = onSaveInventoryItem,
            onEditInventoryItem = onEditInventoryItem,
            onDeleteInventoryItem = onDeleteInventoryItem,
            onDismiss = onDismissInventoryMaster
        )
    }
}

@Composable
fun InventoryItemCard(
    item: InventoryItemEntity,
    daysLeft: Int?,
    dateRangeLabel: String = "Today",
    consumptionRecord: ItemConsumptionRecord? = null,
    isEditing: Boolean,
    onUpdateStock: (Double) -> Unit,
    onStartEditingStock: () -> Unit,
    onCancelEditingStock: () -> Unit,
    onAddStock: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.currentStock < 1.0) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "LOW STOCK",
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    val indicator = when {
                        item.currentStock > 3.0 -> "🟢"
                        item.currentStock >= 1.0 -> "🟡"
                        else -> "🔴"
                    }
                    Text(text = indicator, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val stdQtyStr = formatDouble(item.standardQuantity)
                    val currentStockStr = formatDouble(item.currentStock)
                    val totalQtyStr = formatDouble(item.standardQuantity * item.currentStock)
                    val costStr = formatCurrency(item.currentStock * item.defaultPrice)

                    Text(
                        text = "$stdQtyStr ${item.unit} × $currentStockStr packs = $totalQtyStr ${item.unit}    $costStr",
                        color = Color.White,
                        fontSize = 13.sp
                    )

                    // Consumption Pill for the selected period
                    if (consumptionRecord != null && consumptionRecord.totalQuantityUsed > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = AmberAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Used in $dateRangeLabel: ${formatDouble(consumptionRecord.totalQuantityUsed)} ${consumptionRecord.usageUnit} (${formatCurrency(consumptionRecord.totalCost)})",
                                    color = AmberAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (daysLeft != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "~$daysLeft days left based on usage ($dateRangeLabel)",
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row {
                    if (isEditing) {
                        var stockText by remember { mutableStateOf(formatDouble(item.currentStock)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = stockText,
                                onValueChange = { stockText = it },
                                modifier = Modifier
                                    .width(76.dp)
                                    .height(46.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AmberAccent,
                                    unfocusedBorderColor = SurfaceVariantColor,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = {
                                val newStock = (stockText.toDoubleOrNull() ?: item.currentStock).coerceAtLeast(0.0)
                                onUpdateStock(newStock)
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "Save", tint = AmberAccent)
                            }
                            IconButton(onClick = onCancelEditingStock, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextGray)
                            }
                        }
                    } else {
                        IconButton(onClick = onStartEditingStock, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Stock", tint = TextGray)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onAddStock, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.AddBox, contentDescription = "Add Stock", tint = AmberAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddStockDialog(
    item: InventoryItemEntity,
    onDismiss: () -> Unit,
    onAddStock: (Double) -> Unit
) {
    var qtyText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceColor,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Add Stock",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = item.name,
                    color = AmberAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatDouble(item.standardQuantity)} ${item.unit} per pack × ${formatCurrency(item.defaultPrice)}/pack",
                    color = TextGray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("How many packs?", color = TextGray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = SurfaceVariantColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AmberAccent
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                val qty = qtyText.toDoubleOrNull() ?: 0.0
                val totalQty = qty * item.standardQuantity
                val totalCost = qty * item.defaultPrice

                Text(
                    text = "Total: ${formatDouble(totalQty)} ${item.unit}, ${formatCurrency(totalCost)}",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (qty > 0) {
                                onAddStock(qty)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = DarkBackground),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Stock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}


private fun formatDouble(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}

private fun formatCurrency(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    return format.format(value)
}

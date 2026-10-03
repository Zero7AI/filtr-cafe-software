package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.ui.BillingUiState
import java.text.SimpleDateFormat
import java.util.*

val DarkBackground = Color(0xFF09090B)
val SurfaceColor = Color(0xFF18181B)
val SurfaceVariantColor = Color(0xFF27272A)
val AmberAccent = Color(0xFFF59E0B)
val TextGray = Color(0xFFA1A1AA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryOrderScreen(
    state: BillingUiState,
    inventoryOrders: List<InventoryOrderWithItems>,
    inventoryItems: List<InventoryItemEntity>,
    onOpenAddOrder: () -> Unit,
    onOpenInventoryMaster: () -> Unit,
    onDeleteOrder: (String) -> Unit,
    onConfirmDeleteOrder: (String) -> Unit,
    onDismissDeleteOrder: () -> Unit,
    onSaveOrder: (supplierName: String, notes: String, items: List<Triple<String, Double, Double>>) -> Unit,
    onDismissAddOrder: () -> Unit,
    onSaveInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    onEditInventoryItem: (InventoryItemEntity?) -> Unit,
    onDeleteInventoryItem: (InventoryItemEntity) -> Unit,
    onDismissInventoryMaster: () -> Unit,
    onDismissAddInventoryItem: () -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    // Date filter state
    var dateFilter by remember { mutableStateOf("THIS_MONTH") } // TODAY, YESTERDAY, LAST_7_DAYS, THIS_MONTH, ALL_TIME, CUSTOM
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var isSupplierBreakdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Filter orders based on the selected date or date range
    val dateFilteredOrders = remember(inventoryOrders, dateFilter, customStartMs, customEndMs) {
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

    // Date range human-readable label
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

    // Total spend & metrics for selected period
    val totalSpendPeriod = remember(dateFilteredOrders) {
        dateFilteredOrders.sumOf { it.order.totalAmount }
    }
    val orderCountPeriod = dateFilteredOrders.size
    val avgSpendPerOrder = if (orderCountPeriod > 0) totalSpendPeriod / orderCountPeriod else 0.0

    // Supplier spend breakdown
    val supplierBreakdown = remember(dateFilteredOrders) {
        dateFilteredOrders
            .groupBy { it.order.supplierName.ifBlank { "WhatsApp Order" } }
            .map { (supplier, orders) ->
                val amount = orders.sumOf { it.order.totalAmount }
                Triple(supplier, amount, orders.size)
            }
            .sortedByDescending { it.second }
    }

    // Filter by search query (supplier name, order ID, or item names)
    val displayOrders = remember(dateFilteredOrders, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isBlank()) {
            dateFilteredOrders
        } else {
            dateFilteredOrders.filter { orderWithItems ->
                orderWithItems.order.orderId.lowercase().contains(q) ||
                orderWithItems.order.supplierName.lowercase().contains(q) ||
                orderWithItems.order.notes.lowercase().contains(q) ||
                orderWithItems.items.any { it.itemName.lowercase().contains(q) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Card: TOTAL INVENTORY PROCUREMENT SPEND with Balanced Metrics & Supplier Breakdown
        item(key = "spend_card") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    // Header line: Title & Tag on left; Item Master shortcut on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "TOTAL PROCUREMENT SPEND",
                                color = TextGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Surface(
                                color = AmberAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = dateRangeLabel.uppercase(),
                                    color = AmberAccent,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenInventoryMaster,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, SurfaceVariantColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Item Master", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "₹${String.format(Locale.US, "%,.2f", totalSpendPeriod)}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stats row: 3 balanced tiles (Bills, Avg Spend, Suppliers)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Tile 1: Bills
                        Surface(
                            color = SurfaceVariantColor.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("BILLS", color = TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("$orderCountPeriod Orders", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tile 2: Avg per Bill
                        Surface(
                            color = SurfaceVariantColor.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("AVG / BILL", color = TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("₹${String.format(Locale.US, "%.0f", avgSpendPerOrder)}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tile 3: Suppliers
                        Surface(
                            color = SurfaceVariantColor.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text("VENDORS", color = TextGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(1.dp))
                                Text("${supplierBreakdown.size} Vendors", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Vendor Spend Breakdown Toggle
                    if (supplierBreakdown.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = if (isSupplierBreakdownExpanded) SurfaceVariantColor.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSupplierBreakdownExpanded = !isSupplierBreakdownExpanded }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PieChart,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Vendor Spend Breakdown (${supplierBreakdown.size})",
                                        color = AmberAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    imageVector = if (isSupplierBreakdownExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = AmberAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Expandable Supplier Breakdown
                    AnimatedVisibility(
                        visible = isSupplierBreakdownExpanded && supplierBreakdown.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            HorizontalDivider(color = SurfaceVariantColor, modifier = Modifier.padding(bottom = 8.dp))

                            supplierBreakdown.forEach { (supplier, amount, count) ->
                                val percentage = if (totalSpendPeriod > 0) (amount / totalSpendPeriod * 100.0) else 0.0
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$supplier ($count ${if (count == 1) "bill" else "bills"})",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "₹${String.format(Locale.US, "%,.2f", amount)}  (${String.format(Locale.US, "%.1f", percentage)}%)",
                                            color = AmberAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { (percentage / 100.0).toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = AmberAccent,
                                        trackColor = SurfaceVariantColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Date Filter Chips Row
        item(key = "date_filters") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
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
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceColor,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = SurfaceVariantColor,
                            selectedBorderColor = AmberAccent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                item {
                    val isCustom = dateFilter == "CUSTOM"
                    FilterChip(
                        selected = isCustom,
                        onClick = { showCustomDateDialog = true },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Select Custom Date",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isCustom) dateRangeLabel else "Custom Date",
                                fontSize = 12.sp,
                                fontWeight = if (isCustom) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceColor,
                            labelColor = TextGray
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isCustom,
                            borderColor = SurfaceVariantColor,
                            selectedBorderColor = AmberAccent
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        // Search Bar & Add Bill Button - Pixel-perfect aligned at 50dp
        item(key = "search_and_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search supplier, item, or bill ID...", color = TextGray, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextGray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextGray)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AmberAccent,
                        unfocusedBorderColor = SurfaceVariantColor,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = AmberAccent,
                        focusedContainerColor = SurfaceColor,
                        unfocusedContainerColor = SurfaceColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Button(
                    onClick = onOpenAddOrder,
                    modifier = Modifier.height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Bill", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Orders List
        if (displayOrders.isEmpty()) {
            item(key = "empty_state") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = SurfaceVariantColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No orders match \"$searchQuery\""
                               else "No inventory orders found for $dateRangeLabel",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching for a different supplier or item"
                               else "Add a bill using the '+ Add Bill' button above",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                    if (dateFilter != "ALL_TIME") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { dateFilter = "ALL_TIME" },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("View All Time Orders", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            items(displayOrders, key = { it.order.orderId }) { orderWithItems ->
                InventoryOrderCard(
                    orderWithItems = orderWithItems,
                    onDelete = { onDeleteOrder(orderWithItems.order.orderId) }
                )
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

    // Dialogs triggered by ViewModel state
    if (state.showAddInventoryOrderDialog) {
        AddInventoryOrderDialog(
            inventoryItems = inventoryItems,
            onDismiss = onDismissAddOrder,
            onSave = onSaveOrder
        )
    }

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

    // Delete confirmation
    state.inventoryOrderToDelete?.let { orderId ->
        AlertDialog(
            onDismissRequest = onDismissDeleteOrder,
            containerColor = Color(0xFF18181B),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Delete Inventory Order?") },
            text = { Text("Are you sure you want to delete order $orderId?") },
            confirmButton = {
                TextButton(onClick = { onConfirmDeleteOrder(orderId) }) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteOrder) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun InventoryOrderCard(
    orderWithItems: InventoryOrderWithItems,
    onDelete: () -> Unit
) {
    val order = orderWithItems.order
    val items = orderWithItems.items
    val sdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val dateStr = remember(order.timestamp) { sdf.format(Date(order.timestamp)) }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.8f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Order ID badge, Supplier, Date & Time on left; Amount & Expand on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = AmberAccent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = order.orderId,
                                color = AmberAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (order.supplierName.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = TextGray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = order.supplierName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateStr,
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Right side: Total Amount & Expand Arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "₹${String.format(Locale.US, "%,.2f", order.totalAmount)}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "${items.size} ${if (items.size == 1) "item" else "items"}",
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = AmberAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Optional Notes row
            if (order.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SurfaceVariantColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = order.notes,
                            color = TextGray,
                            fontSize = 11.sp,
                            maxLines = if (isExpanded) 10 else 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Items Content: Preview when collapsed, Detailed Table when expanded
            if (!isExpanded) {
                // Collapsed: preview items
                Surface(
                    color = SurfaceVariantColor.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        val previewItems = items.take(2)
                        previewItems.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "• ${item.itemName} (${item.quantity.let { if (it % 1 == 0.0) it.toInt() else it }} ${item.unit})",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "₹${String.format(Locale.US, "%,.2f", item.subtotal)}",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (items.size > 2) {
                            Text(
                                text = "+ ${items.size - 2} more items (tap to view all)",
                                color = AmberAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                // Expanded: Full itemized table
                Surface(
                    color = SurfaceVariantColor.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ITEM", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                            Text("QTY", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("RATE", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("TOTAL", color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        }

                        HorizontalDivider(color = SurfaceVariantColor, modifier = Modifier.padding(bottom = 6.dp))

                        // Table Rows
                        items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.itemName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(2f),
                                    maxLines = 2
                                )
                                Text(
                                    text = "${item.quantity.let { if (it % 1 == 0.0) it.toInt() else it }} ${item.unit}",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", item.unitPrice)}",
                                    color = TextGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "₹${String.format(Locale.US, "%.2f", item.subtotal)}",
                                    color = AmberAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }
                        }

                        HorizontalDivider(color = SurfaceVariantColor, modifier = Modifier.padding(vertical = 8.dp))

                        // Bottom action inside expanded card: Delete button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total: ₹${String.format(Locale.US, "%,.2f", order.totalAmount)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedButton(
                                onClick = onDelete,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete Bill", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryOrderDialog(
    inventoryItems: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onSave: (supplierName: String, notes: String, items: List<Triple<String, Double, Double>>) -> Unit
) {
    var supplierName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    data class OrderRow(var name: String = "", var qty: String = "", var unit: String = "kg", var rate: String = "")
    var rows by remember { mutableStateOf(listOf(OrderRow())) }

    val unitOptions = listOf("kg", "L", "g", "ml", "pcs", "pack")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Inventory Order",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                HorizontalDivider(color = SurfaceVariantColor)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = supplierName,
                        onValueChange = { supplierName = it },
                        label = { Text("Supplier Name (Optional)", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AmberAccent
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Items", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    rows.forEachIndexed { index, row ->
                        val rowQty = row.qty.toDoubleOrNull() ?: 0.0
                        val rowRate = row.rate.toDoubleOrNull() ?: 0.0
                        val rowSubtotal = rowQty * rowRate

                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Item ${index + 1}", color = TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        if (rowSubtotal > 0.0) {
                                            Surface(
                                                color = AmberAccent.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "= ₹${String.format(Locale.US, "%,.2f", rowSubtotal)}",
                                                    color = AmberAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (rows.size > 1) {
                                        IconButton(
                                            onClick = { rows = rows.filterIndexed { i, _ -> i != index } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                var itemNameExpanded by remember { mutableStateOf(false) }
                                ExposedDropdownMenuBox(
                                    expanded = itemNameExpanded,
                                    onExpandedChange = { itemNameExpanded = !itemNameExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = row.name,
                                        onValueChange = { newName ->
                                            val updatedRows = rows.toMutableList()
                                            updatedRows[index] = row.copy(name = newName)
                                            rows = updatedRows
                                            itemNameExpanded = true
                                        },
                                        label = { Text("Item Name", color = TextGray) },
                                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AmberAccent,
                                            unfocusedBorderColor = SurfaceVariantColor,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = AmberAccent
                                        )
                                    )
                                    
                                    val filteredItems = if (row.name.isBlank()) {
                                        inventoryItems
                                    } else {
                                        inventoryItems.filter { it.name.contains(row.name, ignoreCase = true) }
                                    }.sortedBy { it.category }
                                    
                                    if (filteredItems.isNotEmpty() && itemNameExpanded) {
                                        ExposedDropdownMenu(
                                            expanded = itemNameExpanded,
                                            onDismissRequest = { itemNameExpanded = false },
                                            modifier = Modifier.background(SurfaceVariantColor)
                                        ) {
                                            var currentCategory = ""
                                            filteredItems.forEach { item ->
                                                if (item.category != currentCategory) {
                                                    currentCategory = item.category
                                                    DropdownMenuItem(
                                                        text = { Text(currentCategory.ifBlank { "Uncategorized" }, color = AmberAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                                        onClick = { },
                                                        enabled = false
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text(item.name, color = Color.White) },
                                                    onClick = {
                                                        val updatedRows = rows.toMutableList()
                                                        updatedRows[index] = row.copy(
                                                            name = item.name,
                                                            unit = item.unit,
                                                            rate = if (item.defaultPrice > 0) item.defaultPrice.toString() else row.rate
                                                        )
                                                        rows = updatedRows
                                                        itemNameExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = row.qty,
                                        onValueChange = { newVal ->
                                            val updatedRows = rows.toMutableList()
                                            updatedRows[index] = row.copy(qty = newVal)
                                            rows = updatedRows
                                        },
                                        label = { Text("Qty", color = TextGray) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AmberAccent,
                                            unfocusedBorderColor = SurfaceVariantColor,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = AmberAccent
                                        )
                                    )
                                    
                                    var unitExpanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(
                                        expanded = unitExpanded,
                                        onExpandedChange = { unitExpanded = !unitExpanded },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        OutlinedTextField(
                                            value = row.unit,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Unit", color = TextGray) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                                            modifier = Modifier.menuAnchor(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AmberAccent,
                                                unfocusedBorderColor = SurfaceVariantColor,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                cursorColor = AmberAccent
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = unitExpanded,
                                            onDismissRequest = { unitExpanded = false },
                                            modifier = Modifier.background(SurfaceVariantColor)
                                        ) {
                                            unitOptions.forEach { opt ->
                                                DropdownMenuItem(
                                                    text = { Text(opt, color = Color.White) },
                                                    onClick = {
                                                        val updatedRows = rows.toMutableList()
                                                        updatedRows[index] = row.copy(unit = opt)
                                                        rows = updatedRows
                                                        unitExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = row.rate,
                                        onValueChange = { newVal ->
                                            val updatedRows = rows.toMutableList()
                                            updatedRows[index] = row.copy(rate = newVal)
                                            rows = updatedRows
                                        },
                                        label = { Text("Price", color = TextGray) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = AmberAccent,
                                            unfocusedBorderColor = SurfaceVariantColor,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            cursorColor = AmberAccent
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { rows = rows + OrderRow() },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, SurfaceVariantColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Item", fontSize = 13.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = AmberAccent
                        )
                    )
                }
                
                // Bottom bar
                Surface(
                    color = SurfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val total = rows.sumOf { (it.qty.toDoubleOrNull() ?: 0.0) * (it.rate.toDoubleOrNull() ?: 0.0) }
                        
                        Column {
                            Text("Total Amount", color = TextGray, fontSize = 12.sp)
                            Text("₹${String.format(Locale.US, "%.2f", total)}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val mappedItems = rows.mapNotNull { row ->
                                        if (row.name.isNotBlank()) {
                                            val q = row.qty.toDoubleOrNull() ?: 0.0
                                            val r = row.rate.toDoubleOrNull() ?: 0.0
                                            Triple(row.name.trim(), q, r)
                                        } else null
                                    }
                                    if (mappedItems.isNotEmpty()) {
                                        onSave(supplierName.trim(), notes.trim(), mappedItems)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = DarkBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Order", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryMasterDialog(
    state: BillingUiState,
    inventoryItems: List<InventoryItemEntity>,
    onSearchQueryChanged: (String) -> Unit,
    onSaveInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    onEditInventoryItem: (InventoryItemEntity?) -> Unit,
    onDeleteInventoryItem: (InventoryItemEntity) -> Unit,
    onDismiss: () -> Unit
) {
    // We are simulating an inner dialog if edit item is active
    var itemToEdit by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    val allCategories = inventoryItems.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val unitOptions = listOf("kg", "L", "g", "ml", "pcs", "pack")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Inventory Master",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                
                HorizontalDivider(color = SurfaceVariantColor)
                
                // Content
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.inventorySearchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = { Text("Search items...", color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberAccent,
                                unfocusedBorderColor = SurfaceVariantColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        
                        Button(
                            onClick = { 
                                itemToEdit = null
                                showEditDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = DarkBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(inventoryItems) { item ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Category: ${item.category.ifBlank { "N/A" }} • Unit: ${item.unit} • Stock: ${item.currentStock}",
                                            color = TextGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Default Price: ₹${item.defaultPrice}",
                                            color = AmberAccent,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    
                                    Row {
                                        IconButton(onClick = { 
                                            itemToEdit = item
                                            showEditDialog = true 
                                        }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                                        }
                                        IconButton(onClick = { onDeleteInventoryItem(item) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
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
    
    // Inline Add/Edit Dialog
    if (showEditDialog) {
        var name by remember { mutableStateOf(itemToEdit?.name ?: "") }
        var quantity by remember { mutableStateOf(itemToEdit?.standardQuantity?.toString() ?: "") }
        var unit by remember { mutableStateOf(itemToEdit?.unit ?: "kg") }
        var defaultPrice by remember { mutableStateOf(itemToEdit?.defaultPrice?.toString() ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = SurfaceColor,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(if (itemToEdit == null) "Add Inventory Item" else "Edit Inventory Item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name", color = TextGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Qty per pack", color = TextGray) },
                        placeholder = { Text("e.g. 5, 250", color = TextGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var unitExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = unitExpanded,
                        onExpandedChange = { unitExpanded = !unitExpanded }
                    ) {
                        OutlinedTextField(
                            value = unit,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit", color = TextGray) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AmberAccent,
                                unfocusedBorderColor = SurfaceVariantColor,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = unitExpanded,
                            onDismissRequest = { unitExpanded = false },
                            modifier = Modifier.background(SurfaceVariantColor)
                        ) {
                            unitOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = Color.White) },
                                    onClick = {
                                        unit = opt
                                        unitExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultPrice,
                        onValueChange = { defaultPrice = it },
                        label = { Text("Price per pack", color = TextGray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveInventoryItem(
                            name, 
                            itemToEdit?.category ?: "", 
                            unit, 
                            defaultPrice.toDoubleOrNull() ?: 0.0,
                            (quantity.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.001)
                        )
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = DarkBackground)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}

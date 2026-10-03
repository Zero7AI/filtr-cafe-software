package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.OrderWithItems
import com.example.ui.BillingUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: BillingUiState,
    ordersList: List<OrderWithItems>,
    onDateFilterSelected: (String) -> Unit,
    onOpenCalendarClick: () -> Unit = {},
    onDateRangeSelected: (Long, Long) -> Unit = { _, _ -> },
    onDismissCalendarDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val dateFilters = listOf("TODAY", "YESTERDAY", "THIS_WEEK", "THIS_MONTH", "ALL_TIME")

    // Filter orders based on date filter
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val filteredOrders = ordersList.filter { orderWithItems ->
        val orderTime = orderWithItems.order.timestamp
        val now = Calendar.getInstance()

        when (state.dashboardDateFilter) {
            "TODAY" -> orderTime >= todayStart
            "YESTERDAY" -> {
                val yesterdayStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                orderTime >= yesterdayStart && orderTime < todayStart
            }
            "THIS_WEEK" -> {
                val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                orderTime >= weekAgo.timeInMillis
            }
            "THIS_MONTH" -> {
                val monthAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -30) }
                orderTime >= monthAgo.timeInMillis
            }
            "CUSTOM" -> {
                val start = state.customStartDateMs ?: 0L
                val end = state.customEndDateMs ?: Long.MAX_VALUE
                orderTime in start..end
            }
            else -> true // ALL_TIME
        }
    }

    // Key metrics calculations
    val totalRevenue = filteredOrders.sumOf { it.order.totalAmount }
    val totalOrdersCount = filteredOrders.size
    val paidOrders = filteredOrders.filter { it.order.isPaid }
    val paidRevenue = paidOrders.sumOf { it.order.totalAmount }
    val pendingRevenue = totalRevenue - paidRevenue
    val avgOrderValue = if (totalOrdersCount > 0) totalRevenue / totalOrdersCount else 0.0

    // Top SKUs calculations
    val itemSalesMap = mutableMapOf<String, Pair<Int, Double>>() // Name -> (Quantity, Revenue)
    filteredOrders.forEach { orderWithItems ->
        orderWithItems.items.forEach { item ->
            val current = itemSalesMap[item.itemName] ?: Pair(0, 0.0)
            itemSalesMap[item.itemName] = Pair(current.first + item.quantity, current.second + (item.itemPrice * item.quantity))
        }
    }
    val topSellingItems = itemSalesMap.entries
        .sortedByDescending { it.value.second } // Sort by revenue

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(16.dp)
            .testTag("dashboard_screen")
    ) {
        // Date Filter Row
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = Color(0xFF09090B),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ANALYTICS & METRICS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF09090B)
                )
            }
        }

        // Date Filter Chips with Calendar Icon on leftmost part
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Calendar Icon (Leftmost part, just left to Today)
            item {
                val isCustomActive = state.dashboardDateFilter == "CUSTOM"
                val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
                val customRangeLabel = if (isCustomActive && state.customStartDateMs != null && state.customEndDateMs != null) {
                    "${sdf.format(Date(state.customStartDateMs))} - ${sdf.format(Date(state.customEndDateMs))}"
                } else "Custom Date"

                FilterChip(
                    selected = isCustomActive,
                    onClick = { onOpenCalendarClick() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date Range",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text(customRangeLabel, fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF09090B),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF4F4F5),
                        labelColor = Color(0xFF52525B)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("calendar_date_picker_button")
                )
            }

            items(dateFilters) { filter ->
                val isSelected = state.dashboardDateFilter == filter
                val label = when (filter) {
                    "TODAY" -> "Today"
                    "YESTERDAY" -> "Yesterday"
                    "THIS_WEEK" -> "This Week"
                    "THIS_MONTH" -> "This Month"
                    else -> "All Time"
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onDateFilterSelected(filter) },
                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF09090B),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF4F4F5),
                        labelColor = Color(0xFF52525B)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero Total Revenue Card (Dark Neumorphic Style as per attached UI)
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF09090B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NET REVENUE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${filteredOrders.size} Orders",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "₹${String.format(Locale.US, "%.2f", totalRevenue)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Paid Collected", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("₹${String.format(Locale.US, "%.0f", paidRevenue)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                            }
                            Column {
                                Text("Pending / Unpaid", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("₹${String.format(Locale.US, "%.0f", pendingRevenue)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                            }
                            Column {
                                Text("Avg Order Value", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                Text("₹${String.format(Locale.US, "%.0f", avgOrderValue)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Top Performing Items / SKUs Section
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Coffee, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TOP SELLING SKUs", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF09090B))
                            }
                            Text("${topSellingItems.size} Items", fontSize = 12.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (topSellingItems.isEmpty()) {
                            Text("No items sold in selected period", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 12.dp))
                        } else {
                            topSellingItems.take(6).forEachIndexed { index, entry ->
                                val itemName = entry.key
                                val (qty, revenue) = entry.value

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = if (index == 0) Color(0xFF09090B) else Color(0xFFF4F4F5),
                                            shape = CircleShape,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (index == 0) Color.White else Color(0xFF09090B)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(text = itemName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF09090B))
                                            Text(text = "$qty units sold", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }

                                    Text(
                                        text = "₹${String.format(Locale.US, "%.0f", revenue)}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF09090B)
                                    )
                                }

                                if (index < topSellingItems.take(6).size - 1) {
                                    HorizontalDivider(color = Color(0xFFF4F4F5))
                                }
                            }
                        }
                    }
                }
            }

            // Kitchen Preparation Time Analytics Section
            item {
                val completedOrdersWithPrepTime = filteredOrders.filter { it.order.isMade && it.order.completedTimestamp > it.order.timestamp }
                val avgOrderPrepSec = if (completedOrdersWithPrepTime.isNotEmpty()) {
                    completedOrdersWithPrepTime.map { (it.order.completedTimestamp - it.order.timestamp) / 1000 }.average()
                } else 0.0

                val avgOrderPrepMin = (avgOrderPrepSec / 60).toInt()
                val avgOrderPrepSecRem = (avgOrderPrepSec % 60).toInt()

                // SKU level prep time map
                val skuPrepTimesMap = mutableMapOf<String, MutableList<Long>>()
                filteredOrders.forEach { orderWithItems ->
                    orderWithItems.items.forEach { item ->
                        if (item.completedQuantity > 0 && item.completedTimestamp > orderWithItems.order.timestamp) {
                            val prepSec = (item.completedTimestamp - orderWithItems.order.timestamp) / 1000
                            skuPrepTimesMap.getOrPut(item.itemName) { mutableListOf() }.add(prepSec)
                        }
                    }
                }

                val avgSkuPrepList = skuPrepTimesMap.map { (itemName, times) ->
                    val avgSec = times.average()
                    Pair(itemName, avgSec)
                }.sortedBy { it.second } // Fastest first

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = CircleShape,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("⏱️", fontSize = 14.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PREPARATION TIME ANALYTICS", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF09090B))
                            }
                            Surface(color = Color(0xFFF4F4F5), shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = "${completedOrdersWithPrepTime.size} Orders Measured",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF52525B),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Average Order Prep Banner
                        Surface(
                            color = Color(0xFFF8F9FA),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("AVERAGE ORDER PREP TIME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF71717A))
                                    Text(
                                        text = if (avgOrderPrepSec > 0) "${avgOrderPrepMin}m ${avgOrderPrepSecRem}s" else "No completed orders yet",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (avgOrderPrepSec > 0) Color(0xFF16A34A) else Color.Gray
                                    )
                                }
                                if (avgSkuPrepList.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("⚡ Fastest SKU", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                                        val fastest = avgSkuPrepList.first()
                                        val fastMin = (fastest.second / 60).toInt()
                                        val fastSec = (fastest.second % 60).toInt()
                                        Text(
                                            text = "${fastest.first} (${if (fastMin > 0) "${fastMin}m " else ""}${fastSec}s)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF09090B)
                                        )
                                    }
                                }
                            }
                        }

                        if (avgSkuPrepList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("SKU PREPARATION TIME BREAKDOWN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF71717A))
                            Spacer(modifier = Modifier.height(8.dp))

                            avgSkuPrepList.take(5).forEachIndexed { index, (itemName, avgSec) ->
                                val min = (avgSec / 60).toInt()
                                val sec = (avgSec % 60).toInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = itemName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF09090B))
                                    Surface(color = Color(0xFFF0FDF4), shape = RoundedCornerShape(6.dp)) {
                                        Text(
                                            text = "⏱️ ${if (min > 0) "${min}m " else ""}${sec}s avg",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF166534),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (index < avgSkuPrepList.take(5).size - 1) {
                                    HorizontalDivider(color = Color(0xFFF4F4F5))
                                }
                            }
                        }
                    }
                }
            }

            // Quick Order Status Counts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val madeCount = filteredOrders.count { it.order.isMade }
                    val deliveredCount = filteredOrders.count { it.order.isDelivered }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF09090B)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Ready Coffee", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$madeCount / $totalOrdersCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF09090B))
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF09090B)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Delivered Orders", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$deliveredCount / $totalOrdersCount", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFF09090B))
                        }
                    }
                }
            }
        }
    }

    if (state.showCustomDatePickerDialog) {
        CustomDateRangePickerDialog(
            onDismiss = onDismissCalendarDialog,
            onDateRangeApplied = onDateRangeSelected
        )
    }
}

@Composable
fun CustomDateRangePickerDialog(
    onDismiss: () -> Unit,
    onDateRangeApplied: (Long, Long) -> Unit
) {
    val context = LocalContext.current
    var isSingleDayMode by remember { mutableStateOf(false) }

    val startCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val endCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    var startMsState by remember { mutableStateOf(startCal.timeInMillis) }
    var endMsState by remember { mutableStateOf(endCal.timeInMillis) }

    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    val showStartDatePicker = {
        val c = Calendar.getInstance().apply { timeInMillis = startMsState }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startMsState = newCal.timeInMillis
                if (isSingleDayMode) {
                    val newEndCal = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    endMsState = newEndCal.timeInMillis
                } else if (endMsState < startMsState) {
                    val newEndCal = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 23, 59, 59)
                        set(Calendar.MILLISECOND, 999)
                    }
                    endMsState = newEndCal.timeInMillis
                }
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showEndDatePicker = {
        val c = Calendar.getInstance().apply { timeInMillis = endMsState }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newEndCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                endMsState = newEndCal.timeInMillis
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = Color(0xFF09090B),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Select Date or Range",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF09090B)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().testTag("custom_date_range_dialog")) {
                Text(
                    text = "Filter orders, inventory, and financial analytics for a specific date or date range:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Mode toggle: Single Day vs Date Range
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF4F4F5), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.Button(
                        onClick = {
                            isSingleDayMode = true
                            val todayStart = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val todayEnd = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                                set(Calendar.MILLISECOND, 999)
                            }
                            startMsState = todayStart.timeInMillis
                            endMsState = todayEnd.timeInMillis
                        },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (isSingleDayMode) Color(0xFF09090B) else Color.Transparent,
                            contentColor = if (isSingleDayMode) Color.White else Color(0xFF52525B)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Single Day", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    androidx.compose.material3.Button(
                        onClick = { isSingleDayMode = false },
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = if (!isSingleDayMode) Color(0xFF09090B) else Color.Transparent,
                            contentColor = if (!isSingleDayMode) Color.White else Color(0xFF52525B)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Date Range", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isSingleDayMode) {
                    // Single Day Selector
                    Text("Selected Day:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = { showStartDatePicker() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF09090B))
                    ) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(sdf.format(Date(startMsState)), fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                    }
                } else {
                    // Date Range Selectors
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Date:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showStartDatePicker() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF09090B)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(sdf.format(Date(startMsState)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("End Date:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            androidx.compose.material3.OutlinedButton(
                                onClick = { showEndDatePicker() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF09090B)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(sdf.format(Date(endMsState)), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Presets
                Text("Quick Shortcuts:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                val presets = listOf(
                    1 to "Today",
                    2 to "Yesterday",
                    7 to "Last 7 Days",
                    30 to "Last 30 Days"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (days, label) ->
                        androidx.compose.material3.SuggestionChip(
                            onClick = {
                                if (days == 1) {
                                    val calS = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                                    val calE = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
                                    startMsState = calS.timeInMillis
                                    endMsState = calE.timeInMillis
                                } else if (days == 2) {
                                    val calS = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                                    val calE = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1); set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
                                    startMsState = calS.timeInMillis
                                    endMsState = calE.timeInMillis
                                } else {
                                    val calS = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                                    val calE = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }
                                    startMsState = calS.timeInMillis
                                    endMsState = calE.timeInMillis
                                }
                            },
                            label = { Text(label, fontSize = 10.sp) },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = {
                    onDateRangeApplied(startMsState, endMsState)
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun isSameDay(time1: Long, time2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

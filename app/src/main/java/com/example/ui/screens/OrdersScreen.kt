package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.ui.BillingUiState
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeCardBorder
import com.example.ui.theme.CoffeeOnPrimary
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.StatusGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(
    state: BillingUiState,
    ordersList: List<OrderWithItems>,
    menuItems: List<MenuItemEntity> = emptyList(),
    onFilterChanged: (String) -> Unit,
    onToggleMade: (OrderWithItems) -> Unit,
    onTogglePaid: (OrderWithItems) -> Unit,
    onSelectPaymentMode: (OrderWithItems, String) -> Unit = { _, _ -> },
    onToggleDelivered: (OrderWithItems) -> Unit,
    onToggleItemCompleted: (orderId: String, itemId: Int, currentCompletedQty: Int, targetUnitIndex: Int, totalQty: Int) -> Unit = { _, _, _, _, _ -> },
    onUpdateOrderItems: (OrderWithItems, List<OrderItemEntity>) -> Unit = { _, _ -> },
    onUpdateOrderWithCustomerDetails: (OrderWithItems, List<OrderItemEntity>, String, String) -> Unit = { order, items, _, _ -> onUpdateOrderItems(order, items) },
    onSyncOrder: (OrderWithItems) -> Unit,
    onDeleteOrder: (String) -> Unit,
    onViewReceipt: (OrderWithItems) -> Unit,
    onSyncAllOrders: () -> Unit = {},
    onSyncPendingOrders: () -> Unit = {},
    onOrdersDateFilterChanged: (String) -> Unit = {},
    onOpenOrdersDatePicker: () -> Unit = {},
    onDismissOrdersDatePicker: () -> Unit = {},
    onOrdersDateRangeSelected: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val filterOptions = listOf("PENDING", "ALL", "MADE", "UNPAID", "DELIVERED")
    var selectedOrderForPaymentDialog by remember { mutableStateOf<OrderWithItems?>(null) }
    var selectedOrderForEditDialog by remember { mutableStateOf<OrderWithItems?>(null) }
    val unsyncedCount = ordersList.count { it.order.syncStatus != "SYNCED" }

    // Step 1: Filter by date first
    val now = java.util.Calendar.getInstance()
    val dateFilteredOrders = when (state.ordersDateFilter) {
        "TODAY" -> {
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            ordersList.filter { it.order.timestamp >= todayStart }
        }
        "CUSTOM" -> {
            val start = state.ordersCustomStartMs ?: 0L
            val end = state.ordersCustomEndMs ?: Long.MAX_VALUE
            ordersList.filter { it.order.timestamp in start..end }
        }
        else -> ordersList // ALL
    }

    // Step 2: Then filter by status
    val statusFilteredOrders = dateFilteredOrders.filter { orderWithItems ->
        when (state.statusFilter) {
            "PENDING" -> !orderWithItems.order.isDelivered || !orderWithItems.order.isPaid
            "MADE" -> orderWithItems.order.isMade
            "UNPAID" -> !orderWithItems.order.isPaid
            "DELIVERED" -> orderWithItems.order.isDelivered
            else -> true
        }
    }

    // Step 3: Prioritize UNPAID orders at the top / front, then sort by newest timestamp
    val filteredOrders = statusFilteredOrders.sortedWith(
        compareBy<OrderWithItems> { it.order.isPaid } // false (UNPAID) comes first!
            .thenByDescending { it.order.timestamp }
    )

    // Total revenue for the date range (independent of status filter) — always shows full day's total
    val totalDayRevenue = dateFilteredOrders.sumOf { it.order.totalAmount }
    // Revenue for just the currently filtered status subset (used for display context)
    val totalFilteredRevenue = filteredOrders.sumOf { it.order.totalAmount }

    // Date label for the header
    val dateLabel = when (state.ordersDateFilter) {
        "TODAY" -> "TODAY"
        "CUSTOM" -> {
            val sdf = SimpleDateFormat("dd MMM", Locale.getDefault())
            val start = state.ordersCustomStartMs
            val end = state.ordersCustomEndMs
            if (start != null && end != null) "${sdf.format(Date(start))} - ${sdf.format(Date(end))}" else "CUSTOM"
        }
        else -> "ALL"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(top = 8.dp)
            .padding(12.dp)
            .testTag("orders_screen")
    ) {
        // Top Total Revenue & Total Orders Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090B)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Bill Amount & Total Orders Processed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Bill Amount
                        Column {
                            Text(
                                text = "TOTAL BILL ($dateLabel)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%.0f", totalDayRevenue)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Divider between Bill Amount and Total Orders
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.2f))
                        )

                        // Total Orders Count
                        Column {
                            Text(
                                text = "ORDERS ($dateLabel)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${dateFilteredOrders.size}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (filteredOrders.size != dateFilteredOrders.size) {
                                    Text(
                                        text = " (${filteredOrders.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                } else {
                                    Text(
                                        text = " orders",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 2.dp),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // Right side: Sync buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (unsyncedCount > 0) {
                            Button(
                                onClick = onSyncPendingOrders,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("sync_pending_orders_button")
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Sync ($unsyncedCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                            }
                        }

                        Button(
                            onClick = onSyncAllOrders,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("sync_all_orders_button")
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Sync", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                        }
                    }
                }

                // Sub-row: Payment split & Processed orders breakdown
                val upiTotal = dateFilteredOrders.filter { it.order.isPaid && it.order.paymentMode == "UPI" }.sumOf { it.order.totalAmount }
                val cashTotal = dateFilteredOrders.filter { it.order.isPaid && it.order.paymentMode == "CASH" }.sumOf { it.order.totalAmount }
                val processedCount = dateFilteredOrders.count { it.order.isDelivered || it.order.isMade }
                val deliveredCount = dateFilteredOrders.count { it.order.isDelivered }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // UPI / Cash breakdown
                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "UPI",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = " ₹${String.format(Locale.US, "%.0f", upiTotal)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = " • ",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.4f)
                            )
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "Cash",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = " ₹${String.format(Locale.US, "%.0f", cashTotal)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Processed Orders Detail Badge
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Done: $processedCount/${dateFilteredOrders.size} (Del: $deliveredCount)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Status Filters Header: PENDING | ALL | MADE | UNPAID | DELIVERED | 📅
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(filterOptions) { filter ->
                val isSelected = state.statusFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onFilterChanged(filter)
                        // When ALL is clicked, reset date filter to TODAY
                        if (filter == "ALL") onOrdersDateFilterChanged("TODAY")
                    },
                    label = { Text(filter, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF09090B),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFFF4F4F5),
                        labelColor = Color(0xFF52525B)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("order_filter_$filter")
                )
            }

            // Calendar icon for date selection (rightmost)
            item {
                Surface(
                    color = if (state.ordersDateFilter == "CUSTOM") Color(0xFF09090B) else Color(0xFFF4F4F5),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("orders_calendar_button")
                        .padding(0.dp)
                ) {
                    IconButton(
                        onClick = onOpenOrdersDatePicker,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Date Range",
                            tint = if (state.ordersDateFilter == "CUSTOM") Color.White else Color(0xFF52525B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(64.dp), tint = CoffeeCardBorder)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No orders found in this status", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredOrders) { orderWithItems ->
                    OrderCard(
                        orderWithItems = orderWithItems,
                        onToggleMade = { onToggleMade(orderWithItems) },
                        onTogglePaid = { onTogglePaid(orderWithItems) },
                        onOpenPaymentDialog = { selectedOrderForPaymentDialog = orderWithItems },
                        onToggleDelivered = { onToggleDelivered(orderWithItems) },
                        onToggleItemCompleted = onToggleItemCompleted,
                        onEditOrder = { selectedOrderForEditDialog = orderWithItems },
                        onSyncOrder = { onSyncOrder(orderWithItems) },
                        onDeleteOrder = { onDeleteOrder(orderWithItems.order.orderId) },
                        onViewReceipt = { onViewReceipt(orderWithItems) }
                    )
                }
            }
        }

        // Edit Order Items Dialog
        selectedOrderForEditDialog?.let { targetOrder ->
            EditOrderDialog(
                orderWithItems = targetOrder,
                menuItems = menuItems,
                onDismiss = { selectedOrderForEditDialog = null },
                onSave = { updatedItems ->
                    onUpdateOrderItems(targetOrder, updatedItems)
                    selectedOrderForEditDialog = null
                },
                onSaveWithCustomerDetails = { updatedItems, name, contact ->
                    onUpdateOrderWithCustomerDetails(targetOrder, updatedItems, name, contact)
                    selectedOrderForEditDialog = null
                }
            )
        }

        // Payment Mode Selection Dialog
        selectedOrderForPaymentDialog?.let { targetOrder ->
            AlertDialog(
                onDismissRequest = { selectedOrderForPaymentDialog = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        tint = Color(0xFF09090B),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text(
                        text = "Select Payment Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Order #${targetOrder.order.orderId} • ${targetOrder.order.customerName}",
                            fontSize = 13.sp,
                            color = Color(0xFF71717A)
                        )
                        Text(
                            text = "Total Amount: ₹${String.format(Locale.US, "%.2f", targetOrder.order.totalAmount)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF09090B),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // CASH Option
                        Button(
                            onClick = {
                                onSelectPaymentMode(targetOrder, "CASH")
                                selectedOrderForPaymentDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetOrder.order.isPaid && targetOrder.order.paymentMode == "CASH") Color(0xFF16A34A) else Color(0xFF09090B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("payment_cash_button")
                        ) {
                            Icon(imageVector = Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (targetOrder.order.isPaid && targetOrder.order.paymentMode == "CASH") "💵 CASH PAYMENT ✓" else "💵 CASH PAYMENT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // UPI Option
                        Button(
                            onClick = {
                                onSelectPaymentMode(targetOrder, "UPI")
                                selectedOrderForPaymentDialog = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (targetOrder.order.isPaid && targetOrder.order.paymentMode == "UPI") Color(0xFF2563EB) else Color(0xFF09090B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("payment_upi_button")
                        ) {
                            Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = if (targetOrder.order.isPaid && targetOrder.order.paymentMode == "UPI") "📱 UPI PAYMENT ✓" else "📱 UPI PAYMENT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (targetOrder.order.isPaid) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    onSelectPaymentMode(targetOrder, "UNPAID")
                                    selectedOrderForPaymentDialog = null
                                }
                            ) {
                                Text(text = "Unmark Paid (Set to Unpaid)", color = Color(0xFFDC2626), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { selectedOrderForPaymentDialog = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            )
        }
    }

    // Orders Date Range Picker Dialog (reuses CustomDateRangePickerDialog from DashboardScreen)
    if (state.showOrdersDatePickerDialog) {
        CustomDateRangePickerDialog(
            onDismiss = onDismissOrdersDatePicker,
            onDateRangeApplied = onOrdersDateRangeSelected
        )
    }
}

@Composable
fun OrderCard(
    orderWithItems: OrderWithItems,
    onToggleMade: () -> Unit,
    onTogglePaid: () -> Unit,
    onOpenPaymentDialog: () -> Unit = {},
    onToggleDelivered: () -> Unit,
    onToggleItemCompleted: (orderId: String, itemId: Int, currentCompletedQty: Int, targetUnitIndex: Int, totalQty: Int) -> Unit,
    onEditOrder: () -> Unit = {},
    onSyncOrder: () -> Unit,
    onDeleteOrder: () -> Unit,
    onViewReceipt: () -> Unit
) {
    val order = orderWithItems.order
    val dateFormatted = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color(0xFF09090B)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (!order.isPaid) 1.5.dp else 1.dp,
                color = if (!order.isPaid) Color(0xFFEF4444) else Color(0xFFE4E4E7),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("order_card_${order.orderId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Order ID, Timestamp & Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = order.orderId,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF71717A)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!order.isPaid) {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "UNPAID",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", order.totalAmount)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = if (!order.isPaid) Color(0xFFDC2626) else Color(0xFF09090B)
                    )

                    IconButton(
                        onClick = onEditOrder,
                        modifier = Modifier.testTag("edit_order_button_${order.orderId}")
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Order", tint = Color(0xFF09090B))
                    }

                    IconButton(
                        onClick = onDeleteOrder,
                        modifier = Modifier.testTag("delete_order_button_${order.orderId}")
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF71717A))
                    }
                }
            }

            // Customer Details
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Customer: ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF09090B)
                )
                Text(
                    text = order.customerName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF09090B)
                )

                if (order.contactNumber.isNotBlank()) {
                    Text(
                        text = " (${order.contactNumber})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF71717A)
                    )
                }
            }

            // Kitchen SKU Line-by-Line Breakdown & Completion Controls
            Surface(
                color = Color(0xFFF8F9FA),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "KITCHEN ITEMS & SKUs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF71717A),
                            letterSpacing = 0.5.sp
                        )

                        val totalUnits = orderWithItems.items.sumOf { it.quantity }
                        val completedUnits = orderWithItems.items.sumOf { it.completedQuantity.coerceAtMost(it.quantity) }
                        val allDone = totalUnits > 0 && completedUnits == totalUnits

                        Surface(
                            color = if (allDone) Color(0xFFDCFCE7) else Color(0xFFFEF9C3),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (allDone) "ALL $totalUnits SKUs READY ✓" else "$completedUnits/$totalUnits SKUs Prepared",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (allDone) Color(0xFF166534) else Color(0xFF854D0E),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    orderWithItems.items.forEach { item ->
                        val qty = item.quantity
                        val completedQty = item.completedQuantity

                        for (unitIndex in 1..qty) {
                            val isUnitMade = unitIndex <= completedQty

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        if (isUnitMade) Color(0xFFF0FDF4) else Color.White,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isUnitMade) Color(0xFFBBF7D0) else Color(0xFFE4E4E7),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isUnitMade) Icons.Default.CheckCircle else Icons.Default.Coffee,
                                        contentDescription = null,
                                        tint = if (isUnitMade) Color(0xFF16A34A) else Color(0xFF71717A),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (qty > 1) "${item.itemName} (Unit $unitIndex/$qty)" else item.itemName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isUnitMade) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isUnitMade) Color(0xFF15803D) else Color(0xFF09090B)
                                    )
                                }

                                Button(
                                    onClick = {
                                        onToggleItemCompleted(order.orderId, item.id, completedQty, unitIndex, qty)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isUnitMade) Color(0xFF16A34A) else Color(0xFF09090B),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (isUnitMade) "Completed ✓" else "Mark Made",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Status Badges Row (Directly Clickable Cards)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = if (order.isMade) "Coffee Ready" else "Making",
                    isPositive = order.isMade,
                    icon = Icons.Default.Coffee,
                    onClick = onToggleMade,
                    modifier = Modifier.weight(1f).testTag("badge_made_${order.orderId}")
                )
                StatusBadge(
                    text = if (order.isPaid) "Paid (${order.paymentMode.ifBlank { "CASH" }})" else "Unpaid",
                    isPositive = order.isPaid,
                    icon = Icons.Default.Payment,
                    onClick = onOpenPaymentDialog,
                    modifier = Modifier.weight(1f).testTag("badge_paid_${order.orderId}")
                )
                StatusBadge(
                    text = if (order.isDelivered) "Delivered" else "Preparing",
                    isPositive = order.isDelivered,
                    icon = Icons.Default.DoneAll,
                    onClick = onToggleDelivered,
                    modifier = Modifier.weight(1f).testTag("badge_delivered_${order.orderId}")
                )
                StatusBadge(
                    text = when (order.syncStatus) {
                        "SYNCED" -> "Sheet Synced"
                        "FAILED" -> "Sync Failed"
                        else -> "Pending Sync"
                    },
                    isPositive = order.syncStatus == "SYNCED",
                    icon = Icons.Default.CloudUpload,
                    onClick = onSyncOrder,
                    modifier = Modifier.weight(1f).testTag("badge_sync_${order.orderId}")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bill Receipt Button (Kept as is)
            OutlinedButton(
                onClick = onViewReceipt,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("order_bill_receipt_button_${order.orderId}")
            ) {
                Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bill Receipt", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    isPositive: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = if (isPositive) StatusGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isPositive) StatusGreen.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPositive) StatusGreen else Color.Gray,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) StatusGreen else Color.DarkGray
            )
        }
    }
}

@Composable
fun EditOrderDialog(
    orderWithItems: OrderWithItems,
    menuItems: List<MenuItemEntity>,
    onDismiss: () -> Unit,
    onSave: (List<OrderItemEntity>) -> Unit,
    onSaveWithCustomerDetails: (List<OrderItemEntity>, String, String) -> Unit = { items, _, _ -> onSave(items) }
) {
    var editableItems by remember(orderWithItems) {
        mutableStateOf(orderWithItems.items.map { it.copy() })
    }
    var editCustomerName by remember(orderWithItems) {
        mutableStateOf(orderWithItems.order.customerName)
    }
    var editContactNumber by remember(orderWithItems) {
        mutableStateOf(orderWithItems.order.contactNumber)
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }

    val categories = remember(menuItems) {
        listOf("ALL") + menuItems.map { it.category }.distinct().sorted()
    }

    val availableMenuItems = remember(menuItems, searchQuery, selectedCategory) {
        menuItems.filter { item ->
            item.isAvailable &&
            (selectedCategory == "ALL" || item.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true))
        }
    }

    val currentTotal = editableItems.sumOf { it.subtotal }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Edit Order #${orderWithItems.order.orderId}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
            ) {
                // Customer Details (Editable)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = editCustomerName,
                        onValueChange = { editCustomerName = it },
                        label = { Text("Customer Name", fontSize = 10.sp) },
                        placeholder = { Text("Walk-in", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                    OutlinedTextField(
                        value = editContactNumber,
                        onValueChange = { editContactNumber = it },
                        label = { Text("Contact", fontSize = 10.sp) },
                        placeholder = { Text("Optional", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    )
                }

                Text(
                    text = "CURRENT ITEMS IN ORDER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF71717A),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (editableItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No items in order! Add items below.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(editableItems) { orderItem ->
                            Surface(
                                color = Color(0xFFF8F9FA),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = orderItem.itemName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF09090B)
                                        )
                                        Text(
                                            text = "₹${orderItem.itemPrice} x ${orderItem.quantity} = ₹${String.format(Locale.US, "%.2f", orderItem.subtotal)}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF71717A)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Minus Button
                                        IconButton(
                                            onClick = {
                                                val newQty = orderItem.quantity - 1
                                                if (newQty <= 0) {
                                                    editableItems = editableItems.filter { it !== orderItem && (it.id == 0 || it.id != orderItem.id) }
                                                } else {
                                                    editableItems = editableItems.map { item ->
                                                        if (item === orderItem || (item.id != 0 && item.id == orderItem.id) || (item.itemName == orderItem.itemName && item.itemPrice == orderItem.itemPrice)) {
                                                            item.copy(
                                                                quantity = newQty,
                                                                subtotal = item.itemPrice * newQty,
                                                                completedQuantity = item.completedQuantity.coerceAtMost(newQty)
                                                            )
                                                        } else item
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                                        }

                                        Text(
                                            text = "${orderItem.quantity}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp)
                                        )

                                        // Plus Button
                                        IconButton(
                                            onClick = {
                                                val newQty = orderItem.quantity + 1
                                                editableItems = editableItems.map { item ->
                                                    if (item === orderItem || (item.id != 0 && item.id == orderItem.id) || (item.itemName == orderItem.itemName && item.itemPrice == orderItem.itemPrice)) {
                                                        item.copy(
                                                            quantity = newQty,
                                                            subtotal = item.itemPrice * newQty
                                                        )
                                                    } else item
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                        }

                                        // Delete/Remove Item Button
                                        IconButton(
                                            onClick = {
                                                editableItems = editableItems.filter { it !== orderItem && (it.id == 0 || it.id != orderItem.id) }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove Item", tint = Color(0xFF71717A), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Section 2: Add Items from Menu Master
                Text(
                    text = "ADD ITEMS FROM MENU",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF71717A),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search menu to add...", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(availableMenuItems) { menuItem ->
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = menuItem.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = Color(0xFF09090B)
                                    )
                                    Text(
                                        text = "₹${menuItem.price} • ${menuItem.category}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF71717A)
                                    )
                                }

                                Button(
                                    onClick = {
                                        val existingIndex = editableItems.indexOfFirst {
                                            it.menuItemId == menuItem.id || it.itemName.equals(menuItem.name, ignoreCase = true)
                                        }
                                        if (existingIndex >= 0) {
                                            val existing = editableItems[existingIndex]
                                            val newQty = existing.quantity + 1
                                            editableItems = editableItems.toMutableList().apply {
                                                this[existingIndex] = existing.copy(
                                                    quantity = newQty,
                                                    subtotal = existing.itemPrice * newQty
                                                )
                                            }
                                        } else {
                                            val newItem = OrderItemEntity(
                                                id = 0,
                                                orderId = orderWithItems.order.orderId,
                                                menuItemId = menuItem.id,
                                                itemName = menuItem.name,
                                                itemPrice = menuItem.price,
                                                quantity = 1,
                                                subtotal = menuItem.price,
                                                completedQuantity = 0
                                            )
                                            editableItems = editableItems + newItem
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Updated Total:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("₹${String.format(Locale.US, "%.2f", currentTotal)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveWithCustomerDetails(editableItems, editCustomerName, editContactNumber)
                },
                enabled = editableItems.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

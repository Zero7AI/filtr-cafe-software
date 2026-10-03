package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.OrderWithItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CustomersScreen(
    customers: List<CustomerEntity>,
    ordersList: List<OrderWithItems>,
    onAddCustomer: (name: String, contact: String) -> Unit,
    onUpdateCustomer: (CustomerEntity) -> Unit,
    onDeleteCustomer: (CustomerEntity) -> Unit,
    onMarkOrderPaid: (OrderWithItems, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var deletingCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.contactNumber.contains(searchQuery, ignoreCase = true)
        }
    }

    // Show detail view if a customer is selected
    if (selectedCustomer != null) {
        val customer = selectedCustomer!!
        val customerOrders = remember(ordersList, customer.name) {
            ordersList.filter { it.order.customerName.equals(customer.name, ignoreCase = true) }
                .sortedByDescending { it.order.timestamp }
        }
        CustomerDetailView(
            customer = customer,
            customerOrders = customerOrders,
            onBack = { selectedCustomer = null },
            onMarkOrderPaid = onMarkOrderPaid,
            modifier = modifier
        )
        return
    }

    // Main customer list view
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(top = 8.dp)
            .padding(12.dp)
            .testTag("customers_screen")
    ) {
        // Header Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF09090B)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CUSTOMERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${customers.size} Customers",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("add_customer_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search customers...", color = Color(0xFF71717A)) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF71717A)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF09090B),
                unfocusedBorderColor = Color(0xFFE4E4E7),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("customer_search_input")
        )

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFFE4E4E7)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No customers matching \"$searchQuery\""
                               else "No customers yet. Add your first customer!",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCustomers, key = { it.id }) { customer ->
                    val pendingAmount = remember(ordersList, customer.name) {
                        ordersList
                            .filter { it.order.customerName.equals(customer.name, ignoreCase = true) && !it.order.isPaid }
                            .sumOf { it.order.totalAmount }
                    }
                    CustomerCard(
                        customer = customer,
                        pendingAmount = pendingAmount,
                        onClick = { selectedCustomer = customer },
                        onEdit = { editingCustomer = customer },
                        onDelete = { deletingCustomer = customer }
                    )
                }
            }
        }
    }

    // Add Customer Dialog
    if (showAddDialog) {
        AddEditCustomerDialog(
            existingCustomer = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, contact ->
                onAddCustomer(name, contact)
                showAddDialog = false
            }
        )
    }

    // Edit Customer Dialog
    editingCustomer?.let { customer ->
        AddEditCustomerDialog(
            existingCustomer = customer,
            onDismiss = { editingCustomer = null },
            onSave = { name, contact ->
                onUpdateCustomer(customer.copy(name = name, contactNumber = contact))
                editingCustomer = null
            }
        )
    }

    // Delete Confirmation Dialog
    deletingCustomer?.let { customer ->
        AlertDialog(
            onDismissRequest = { deletingCustomer = null },
            title = { Text("Delete Customer?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${customer.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomer(customer)
                        deletingCustomer = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCustomer = null }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

// ==================== CUSTOMER DETAIL VIEW ====================

@Composable
private fun CustomerDetailView(
    customer: CustomerEntity,
    customerOrders: List<OrderWithItems>,
    onBack: () -> Unit,
    onMarkOrderPaid: (OrderWithItems, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalAmount = customerOrders.sumOf { it.order.totalAmount }
    val paidAmount = customerOrders.filter { it.order.isPaid }.sumOf { it.order.totalAmount }
    val pendingAmount = totalAmount - paidAmount
    val paidCount = customerOrders.count { it.order.isPaid }
    val unpaidCount = customerOrders.count { !it.order.isPaid }

    var showSettleAllDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf<OrderWithItems?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(top = 8.dp)
            .padding(12.dp)
    ) {
        // Header with back button
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
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = customer.name, fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White)
                        if (customer.contactNumber.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = customer.contactNumber, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }
                    if (pendingAmount > 0) {
                        Button(
                            onClick = { showSettleAllDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFCA5A5).copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(text = "Settle All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "TOTAL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f), letterSpacing = 0.5.sp)
                            Text(text = "₹${String.format(Locale.US, "%.0f", totalAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                    Surface(color = Color(0xFF16A34A).copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "PAID ($paidCount)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80), letterSpacing = 0.5.sp)
                            Text(text = "₹${String.format(Locale.US, "%.0f", paidAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF4ADE80))
                        }
                    }
                    Surface(
                        color = if (pendingAmount > 0) Color(0xFFDC2626).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(6.dp), modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "DUE ($unpaidCount)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (pendingAmount > 0) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.4f), letterSpacing = 0.5.sp)
                            Text(text = "₹${String.format(Locale.US, "%.0f", pendingAmount)}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (pendingAmount > 0) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // Orders list
        if (customerOrders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFFE4E4E7))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No orders found for ${customer.name}", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            var orderFilter by remember { mutableStateOf("ALL") }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All (${customerOrders.size})", "UNPAID" to "⏳ Unpaid ($unpaidCount)", "PAID" to "✅ Paid ($paidCount)").forEach { (key, label) ->
                    Surface(
                        color = if (orderFilter == key) Color(0xFF09090B) else Color.White,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { orderFilter = key }
                            .border(1.dp, if (orderFilter == key) Color(0xFF09090B) else Color(0xFFE4E4E7), RoundedCornerShape(8.dp))
                    ) {
                        Text(text = label, fontSize = 12.sp, fontWeight = if (orderFilter == key) FontWeight.Bold else FontWeight.Medium, color = if (orderFilter == key) Color.White else Color(0xFF52525B), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }

            val displayedOrders = remember(customerOrders, orderFilter) {
                when (orderFilter) {
                    "PAID" -> customerOrders.filter { it.order.isPaid }
                    "UNPAID" -> customerOrders.filter { !it.order.isPaid }
                    else -> customerOrders
                }
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                items(displayedOrders, key = { it.order.orderId }) { orderWithItems ->
                    CustomerOrderCard(orderWithItems = orderWithItems, onPaymentAction = { showPaymentDialog = orderWithItems })
                }
            }
        }
    }

    // ==================== PAYMENT MODE DIALOG (per order) ====================
    showPaymentDialog?.let { orderWithItems ->
        val isPaid = orderWithItems.order.isPaid
        AlertDialog(
            onDismissRequest = { showPaymentDialog = null },
            title = { Text(text = if (isPaid) "Change Payment" else "Mark as Paid", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(text = "Order ${orderWithItems.order.orderId}", fontSize = 13.sp, color = Color(0xFF71717A))
                    Text(text = "₹${String.format(Locale.US, "%.2f", orderWithItems.order.totalAmount)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF09090B))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Select payment method:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF52525B))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onMarkOrderPaid(orderWithItems, "CASH"); showPaymentDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("💵 Cash", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp) }
                        Button(
                            onClick = { onMarkOrderPaid(orderWithItems, "UPI"); showPaymentDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("📱 UPI", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp) }
                    }
                    if (isPaid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onMarkOrderPaid(orderWithItems, "UNPAID"); showPaymentDialog = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) { Text("Mark as Unpaid", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPaymentDialog = null }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    // ==================== SETTLE ALL DUES DIALOG ====================
    if (showSettleAllDialog) {
        val unpaidOrders = remember(customerOrders) { customerOrders.filter { !it.order.isPaid } }
        val totalDue = unpaidOrders.sumOf { it.order.totalAmount }
        AlertDialog(
            onDismissRequest = { showSettleAllDialog = false },
            title = { Text(text = "Settle All Dues", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column {
                    Text(text = customer.name, fontSize = 13.sp, color = Color(0xFF71717A))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "₹${String.format(Locale.US, "%.0f", totalDue)}", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "across ${unpaidOrders.size} orders", fontSize = 13.sp, color = Color(0xFF71717A))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Mark all unpaid orders as paid via:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF52525B))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { unpaidOrders.forEach { onMarkOrderPaid(it, "CASH") }; showSettleAllDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("💵 Cash", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp) }
                        Button(
                            onClick = { unpaidOrders.forEach { onMarkOrderPaid(it, "UPI") }; showSettleAllDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("📱 UPI", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSettleAllDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }
}

// ==================== ORDER CARD (inside customer detail) ====================

@Composable
private fun CustomerOrderCard(orderWithItems: OrderWithItems, onPaymentAction: () -> Unit) {
    val order = orderWithItems.order
    val dateFormatted = SimpleDateFormat("dd MMM, yyyy  •  hh:mm a", Locale.getDefault()).format(Date(order.timestamp))
    val itemsSummary = orderWithItems.items.take(3).joinToString(", ") { "${it.itemName} ×${it.quantity}" }
    val remainingItems = orderWithItems.items.size - 3

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF09090B)),
        modifier = Modifier.fillMaxWidth().border(1.dp, if (!order.isPaid) Color(0xFFFCA5A5).copy(alpha = 0.5f) else Color(0xFFE4E4E7), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = order.orderId, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                Surface(color = if (order.isPaid) Color(0xFF16A34A).copy(alpha = 0.1f) else Color(0xFFDC2626).copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Icon(imageVector = if (order.isPaid) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null, tint = if (order.isPaid) Color(0xFF16A34A) else Color(0xFFDC2626), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (order.isPaid) "PAID" + (if (order.paymentMode != "UNPAID") " (${order.paymentMode})" else "") else "UNPAID", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (order.isPaid) Color(0xFF16A34A) else Color(0xFFDC2626))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = dateFormatted, fontSize = 11.sp, color = Color(0xFF71717A))
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "₹${String.format(Locale.US, "%.2f", order.totalAmount)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF09090B))
                if (!order.isPaid) {
                    Button(onClick = onPaymentAction, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), modifier = Modifier.height(30.dp)) {
                        Text("Mark Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    OutlinedButton(onClick = onPaymentAction, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp), modifier = Modifier.height(28.dp)) {
                        Text("Change", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color(0xFF71717A))
                    }
                }
            }
            if (itemsSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = itemsSummary + if (remainingItems > 0) " +$remainingItems more" else "", fontSize = 11.sp, color = Color(0xFF71717A), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (order.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "📝 ${order.notes}", fontSize = 10.sp, color = Color(0xFFA1A1AA), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ==================== CUSTOMER CARD ====================

@Composable
private fun CustomerCard(customer: CustomerEntity, pendingAmount: Double, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val dateFormatted = if (customer.lastOrderTimestamp > 0) SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(customer.lastOrderTimestamp)) else "No orders"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White, contentColor = Color(0xFF09090B)),
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, if (pendingAmount > 0) Color(0xFFFCA5A5).copy(alpha = 0.4f) else Color(0xFFE4E4E7), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("customer_card_${customer.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF09090B))
                        if (customer.contactNumber.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF71717A), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = customer.contactNumber, fontSize = 13.sp, color = Color(0xFF71717A))
                            }
                        }
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) { Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF09090B), modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFF71717A), modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = Color(0xFFF4F4F5), shape = RoundedCornerShape(8.dp)) { Text(text = "📋 ${customer.totalOrders}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF52525B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                Surface(color = Color(0xFFF4F4F5), shape = RoundedCornerShape(8.dp)) { Text(text = "₹${String.format(Locale.US, "%.0f", customer.totalSpent)}", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF52525B), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                if (pendingAmount > 0) {
                    Surface(color = Color(0xFFDC2626).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) { Text(text = "⏳ ₹${String.format(Locale.US, "%.0f", pendingAmount)} due", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                } else {
                    Surface(color = Color(0xFF16A34A).copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) { Text(text = "✅ Clear", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF16A34A), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                }
                Surface(color = Color(0xFFF4F4F5), shape = RoundedCornerShape(8.dp)) { Text(text = dateFormatted, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF71717A), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
            }
        }
    }
}

// ==================== ADD/EDIT DIALOG ====================

@Composable
private fun AddEditCustomerDialog(existingCustomer: CustomerEntity?, onDismiss: () -> Unit, onSave: (name: String, contact: String) -> Unit) {
    var name by remember { mutableStateOf(existingCustomer?.name ?: "") }
    var contact by remember { mutableStateOf(existingCustomer?.contactNumber ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = if (existingCustomer == null) Icons.Default.Add else Icons.Default.Edit, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(28.dp)) },
        title = { Text(text = if (existingCustomer == null) "Add Customer" else "Edit Customer", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it; nameError = null }, label = { Text("Customer Name *") }, leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null) }, singleLine = true, isError = nameError != null, supportingText = nameError?.let { { Text(it, color = Color(0xFFDC2626)) } }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().testTag("dialog_customer_name_input"))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact Number (Optional)") }, leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().testTag("dialog_customer_contact_input"))
            }
        },
        confirmButton = {
            Button(onClick = { val trimmedName = name.trim(); if (trimmedName.isBlank()) { nameError = "Name is required" } else { onSave(trimmedName, contact.trim()) } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)), shape = RoundedCornerShape(8.dp)) {
                Text(if (existingCustomer == null) "Add" else "Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) } }
    )
}

package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.domain.inventory.IngredientPriceSummary
import com.example.domain.inventory.RawMaterialPriceTracker
import com.example.domain.inventory.SkuPriceHistoryMatrix
import com.example.ui.BillingUiState
import com.example.ui.InventoryOrderItemInput
import java.util.Locale
import java.util.UUID

val DarkBackground = Color(0xFF09090B)
val SurfaceColor = Color(0xFF18181B)
val SurfaceVariantColor = Color(0xFF27272A)
val AmberAccent = Color(0xFFF59E0B)
val TextGray = Color(0xFFA1A1AA)

private const val DEFAULT_SUPPLIER_SHEET_URL =
    "https://docs.google.com/spreadsheets/d/1UsUM0SLdDf4uHXhJuao96Twkd-Eq5Yxl4_VHM_sFw90/edit?gid=0#gid=0"

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
    onSaveOrder: (supplierName: String, notes: String, items: List<InventoryOrderItemInput>) -> Unit,
    onDismissAddOrder: () -> Unit,
    onSaveInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    onEditInventoryItem: (InventoryItemEntity?) -> Unit,
    onDeleteInventoryItem: (InventoryItemEntity) -> Unit,
    onDismissInventoryMaster: () -> Unit,
    onDismissAddInventoryItem: () -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onUpdateInventoryItem: (InventoryItemEntity) -> Unit = {},
    onImportReceiptClick: (() -> Unit)? = null,
    pricingSummaries: List<IngredientPriceSummary> = emptyList(),
    skuPriceHistoryMatrix: SkuPriceHistoryMatrix? = null,
    googleSheetsUrl: String = "",
    onOpenGoogleSheet: ((String) -> Unit)? = null,
    onRetryQueuedReceipt: (String) -> Unit = {},
    onDismissQueuedReceipt: (String) -> Unit = {},
    onSyncPricingToSheets: ((Boolean) -> Unit)? = null,
    onExportPricingXlsx: ((Boolean) -> Unit)? = null,
    onExportPricingCsv: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var includeDateInCells by remember { mutableStateOf(true) }

    val resolvedMatrix = remember(skuPriceHistoryMatrix, inventoryItems, inventoryOrders) {
        skuPriceHistoryMatrix ?: RawMaterialPriceTracker.buildSkuPriceHistoryMatrix(
            inventoryItems = inventoryItems,
            inventoryOrders = inventoryOrders
        )
    }

    val filteredRows = remember(resolvedMatrix, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.US)
        if (q.isBlank()) resolvedMatrix.rows
        else resolvedMatrix.rows.filter { it.skuName.lowercase(Locale.US).contains(q) }
    }

    val liveSheetUrl = googleSheetsUrl.ifBlank { DEFAULT_SUPPLIER_SHEET_URL }
    val hikeCount = resolvedMatrix.rows.count { it.isPriceHike }
    val anomalyCount = resolvedMatrix.rows.count { it.hasUnitAnomaly }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("inventory_orders_list"),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "purchase_tracking_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Purchase Tracking",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "SKU Price History • ${resolvedMatrix.rows.size} SKUs tracked",
                        color = TextGray,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (onImportReceiptClick != null) {
                        OutlinedButton(
                            onClick = onImportReceiptClick,
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("import_receipt_pdf_button")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = AmberAccent, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bill PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = onOpenAddOrder,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Bill", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        item(key = "live_sheet_card") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LIVE SUPPLIER PRICING SHEET",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Jump straight to all past-to-date supplier prices in Excel / Google Sheets",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                        }
                        if (hikeCount > 0) {
                            Surface(
                                color = Color(0xFFEF4444).copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$hikeCount Hike${if (hikeCount == 1) "" else "s"}",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { onOpenGoogleSheet?.invoke(liveSheetUrl) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("open_live_pricing_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open Live Supplier Pricing Sheet",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (onExportPricingXlsx != null) {
                                    onExportPricingXlsx(includeDateInCells)
                                } else {
                                    onExportPricingCsv?.invoke()
                                }
                            },
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export .xlsx", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        if (onSyncPricingToSheets != null) {
                            OutlinedButton(
                                onClick = { onSyncPricingToSheets(includeDateInCells) },
                                border = BorderStroke(1.dp, SurfaceVariantColor),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (state.pendingReceiptQueue.isNotEmpty()) {
            item(key = "pending_receipt_queue") {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${state.pendingReceiptQueue.size} Queued Receipt(s) Waiting for Retry",
                            color = AmberAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        state.pendingReceiptQueue.forEach { intake ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = intake.sourceLabel,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { onRetryQueuedReceipt(intake.id) }) {
                                        Text("Retry", color = AmberAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(onClick = { onDismissQueuedReceipt(intake.id) }) {
                                        Text("Clear", color = TextGray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "matrix_controls") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search SKU name...", color = TextGray, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(18.dp)) },
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
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    FilterChip(
                        selected = includeDateInCells,
                        onClick = { includeDateInCells = !includeDateInCells },
                        label = {
                            Text(
                                text = if (includeDateInCells) "Dates On" else "Numbers Only",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmberAccent,
                            selectedLabelColor = Color.Black,
                            containerColor = SurfaceColor,
                            labelColor = TextGray
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                if (anomalyCount > 0) {
                    Text(
                        text = "$anomalyCount SKU(s) have a >3x/<0.3x unit jump flagged CHECK UNIT (excluded from % change).",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item(key = "sku_price_history_table") {
            if (filteredRows.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, SurfaceVariantColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No SKUs matching \"$searchQuery\"" else "No supplier bills recorded yet",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Upload bills on WhatsApp or tap + Add Bill to track SKU prices.",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                val horizontalScroll = rememberScrollState()
                val billColsCount = resolvedMatrix.maxBillColumns.coerceAtLeast(1)

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    border = BorderStroke(1.dp, SurfaceVariantColor.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                        Row(
                            modifier = Modifier
                                .background(SurfaceVariantColor.copy(alpha = 0.65f))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SKU Name", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(170.dp))
                            Text("Unit / Weight", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(105.dp))
                            Text("Latest (₹)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(85.dp))
                            Text("vs First %", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(90.dp))
                            for (b in 1..billColsCount) {
                                Text("Bill $b", color = AmberAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(105.dp))
                            }
                        }

                        HorizontalDivider(color = SurfaceVariantColor)

                        filteredRows.forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = row.skuName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(170.dp).padding(end = 6.dp)
                                )
                                Text(
                                    text = row.unitWeightLabel,
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.width(105.dp)
                                )
                                Text(
                                    text = "₹${formatCompactRate(row.latestPrice)}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.width(85.dp)
                                )
                                val pct = row.priceChangeVsFirstPct
                                val pctText = if (pct != null) {
                                    "${if (pct > 0) "+" else ""}${String.format(Locale.US, "%.1f", pct)}%"
                                } else {
                                    "0.0%"
                                }
                                val pctColor = when {
                                    pct != null && pct > 0.0 -> Color(0xFFFCA5A5)
                                    pct != null && pct < 0.0 -> Color(0xFF34D399)
                                    else -> TextGray
                                }
                                Box(modifier = Modifier.width(90.dp)) {
                                    Surface(
                                        color = when {
                                            pct != null && pct > 0.0 -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                            pct != null && pct < 0.0 -> Color(0xFF10B981).copy(alpha = 0.16f)
                                            else -> Color.Transparent
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = pctText,
                                            color = pctColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                for (bIdx in 0 until billColsCount) {
                                    val bill = row.bills.getOrNull(bIdx)
                                    val cellText = bill?.formatCell(includeDateInCells) ?: "—"
                                    val cellColor = when {
                                        bill == null -> TextGray.copy(alpha = 0.4f)
                                        bill.isUnitAnomaly -> Color(0xFFFBBF24)
                                        else -> Color.White.copy(alpha = 0.9f)
                                    }
                                    Text(
                                        text = cellText,
                                        color = cellColor,
                                        fontSize = 11.sp,
                                        fontWeight = if (bill?.isUnitAnomaly == true) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.width(105.dp).padding(end = 6.dp)
                                    )
                                }
                            }
                            if (idx < filteredRows.lastIndex) {
                                HorizontalDivider(color = SurfaceVariantColor.copy(alpha = 0.45f))
                            }
                        }
                    }
                }
            }
        }
    }

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
            onUpdateInventoryItem = onUpdateInventoryItem,
            onEditInventoryItem = onEditInventoryItem,
            onDeleteInventoryItem = onDeleteInventoryItem,
            onDismiss = onDismissInventoryMaster
        )
    }

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

private fun formatCompactRate(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (kotlin.math.abs(rounded - Math.round(rounded)) < 0.005) {
        Math.round(rounded).toString()
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryOrderDialog(
    inventoryItems: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onSave: (supplierName: String, notes: String, items: List<InventoryOrderItemInput>) -> Unit,
    initialSupplierName: String = "",
    initialNotes: String = "",
    initialItems: List<InventoryOrderItemInput> = emptyList(),
    sharedBannerText: String? = null,
    sharedErrorText: String? = null,
    onRetryAiScan: (() -> Unit)? = null,
    requiresAdminUnlock: Boolean = false,
    onRequestAdminUnlock: () -> Unit = {}
) {
    var supplierName by remember(initialSupplierName) { mutableStateOf(initialSupplierName) }
    var notes by remember(initialNotes) { mutableStateOf(initialNotes) }

    data class OrderRow(
        val rowId: String = UUID.randomUUID().toString(),
        var name: String = "",
        var qty: String = "",
        var unit: String = "kg",
        var rate: String = ""
    )
    var rows by remember(initialItems) {
        mutableStateOf(
            if (initialItems.isNotEmpty()) {
                initialItems.map { item ->
                    val qtyText = if (item.quantity % 1.0 == 0.0) {
                        item.quantity.toInt().toString()
                    } else {
                        item.quantity.toString()
                    }
                    val rateText = if (item.unitPrice > 0.0) {
                        if (item.unitPrice % 1.0 == 0.0) item.unitPrice.toInt().toString() else item.unitPrice.toString()
                    } else {
                        ""
                    }
                    OrderRow(
                        name = item.itemName,
                        qty = qtyText,
                        unit = item.unit.ifBlank { "kg" },
                        rate = rateText
                    )
                }
            } else {
                listOf(OrderRow())
            }
        )
    }

    val unitOptions = listOf("kg", "L", "g", "ml", "pcs", "pack")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().imePadding(),
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
                        text = if (sharedBannerText != null) "Review Shared Receipt" else "Add Inventory Order",
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
                    if (!sharedBannerText.isNullOrBlank()) {
                        Surface(
                            color = AmberAccent.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("shared_receipt_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = sharedBannerText,
                                    color = AmberAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!sharedErrorText.isNullOrBlank()) {
                                    Text(
                                        text = "Could not auto-read everything ($sharedErrorText) â€” verify or retry AI scan below.",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 11.sp
                                    )
                                    if (onRetryAiScan != null) {
                                        OutlinedButton(
                                            onClick = onRetryAiScan,
                                            border = BorderStroke(1.dp, AmberAccent),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.padding(top = 4.dp).height(32.dp)
                                        ) {
                                            Text("Retry AI Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                        key(row.rowId) {
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
                                                    text = "= â‚¹${String.format(Locale.US, "%,.2f", rowSubtotal)}",
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
                                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
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
                                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                            Text("â‚¹${String.format(Locale.US, "%.2f", total)}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = onDismiss) {
                                Text("Cancel", color = Color.White)
                            }
                            val hasValidRow = rows.any {
                                it.name.isNotBlank() && (it.qty.toDoubleOrNull() ?: 0.0) > 0.0
                            }
                            Button(
                                onClick = {
                                    if (requiresAdminUnlock) {
                                        onRequestAdminUnlock()
                                    } else {
                                        val mappedItems = rows.mapNotNull { row ->
                                            val q = row.qty.toDoubleOrNull() ?: 0.0
                                            val r = (row.rate.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                                            if (row.name.isNotBlank() && q > 0.0) {
                                                InventoryOrderItemInput(
                                                    itemName = row.name.trim(),
                                                    quantity = q,
                                                    unit = row.unit,
                                                    unitPrice = r
                                                )
                                            } else null
                                        }
                                        if (mappedItems.isNotEmpty()) {
                                            onSave(supplierName.trim(), notes.trim(), mappedItems)
                                        }
                                    }
                                },
                                enabled = requiresAdminUnlock || hasValidRow,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = DarkBackground),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (requiresAdminUnlock) "Unlock Admin & Save" else "Save Order",
                                    fontWeight = FontWeight.Bold
                                )
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
    onDismiss: () -> Unit,
    onUpdateInventoryItem: (InventoryItemEntity) -> Unit = {}
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
                        items(inventoryItems, key = { it.id }) { item ->
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
                                            text = "Category: ${item.category.ifBlank { "N/A" }} â€¢ Unit: ${item.unit} â€¢ Stock: ${item.currentStock}",
                                            color = TextGray,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Default Price: â‚¹${item.defaultPrice}",
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
        var name by remember(itemToEdit?.id, showEditDialog) { mutableStateOf(itemToEdit?.name ?: "") }
        var category by remember(itemToEdit?.id, showEditDialog) { mutableStateOf(itemToEdit?.category ?: "") }
        var quantity by remember(itemToEdit?.id, showEditDialog) { mutableStateOf(itemToEdit?.standardQuantity?.toString() ?: "") }
        var unit by remember(itemToEdit?.id, showEditDialog) { mutableStateOf(itemToEdit?.unit ?: "kg") }
        var defaultPrice by remember(itemToEdit?.id, showEditDialog) { mutableStateOf(itemToEdit?.defaultPrice?.toString() ?: "") }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = SurfaceColor,
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text(if (itemToEdit == null) "Add Inventory Item" else "Edit Inventory Item") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name", color = TextGray) },
                        singleLine = true,
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
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category", color = TextGray) },
                        placeholder = { Text("e.g. Dairy, Coffee, Cups", color = TextGray) },
                        singleLine = true,
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
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                        val cleanName = name.trim()
                        if (cleanName.isBlank()) return@Button
                        val parsedPrice = (defaultPrice.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                        val parsedStdQty = (quantity.toDoubleOrNull() ?: 1.0).coerceAtLeast(0.001)
                        val existing = itemToEdit
                        if (existing != null) {
                            onUpdateInventoryItem(
                                existing.copy(
                                    name = cleanName,
                                    category = category.trim(),
                                    unit = unit,
                                    defaultPrice = parsedPrice,
                                    standardQuantity = parsedStdQty
                                )
                            )
                        } else {
                            onSaveInventoryItem(
                                cleanName,
                                category.trim(),
                                unit,
                                parsedPrice,
                                parsedStdQty
                            )
                        }
                        showEditDialog = false
                    },
                    enabled = name.isNotBlank(),
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

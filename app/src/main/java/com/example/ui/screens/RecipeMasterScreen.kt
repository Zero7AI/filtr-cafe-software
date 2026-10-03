package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.calculatePacksAndCost
import com.example.ui.BillingUiState
import java.text.NumberFormat
import java.util.Locale

private val EmeraldSuccess = Color(0xFF10B981)
private val RedDanger = Color(0xFFEF4444)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeMasterScreen(
    state: BillingUiState,
    menuItems: List<MenuItemEntity>,
    inventoryItems: List<InventoryItemEntity>,
    usages: List<InventoryUsageEntity>,
    onOpenUsageDialog: (menuItemId: Int, menuItemName: String) -> Unit,
    onDismissUsageDialog: () -> Unit,
    onSaveUsage: (menuItemId: Int, menuItemName: String, inventoryItemId: Int, inventoryItemName: String, quantityUsed: Double, unit: String) -> Unit,
    onDeleteUsage: (InventoryUsageEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var ingredientToDelete by remember { mutableStateOf<InventoryUsageEntity?>(null) }
    var ingredientToEdit by remember { mutableStateOf<InventoryUsageEntity?>(null) }

    // Deduplicate menu items by normalized SKU name so each SKU from Item Master appears exactly once
    val uniqueMenuItems = remember(menuItems, usages) {
        menuItems.groupBy { it.name.trim().lowercase() }.map { (_, group) ->
            // Canonical item: prefer the one that has configured recipe usages, then smallest ID
            group.find { item -> usages.any { it.menuItemId == item.id || it.menuItemName.equals(item.name, ignoreCase = true) } }
                ?: group.minByOrNull { it.id }
                ?: group.first()
        }
    }

    val categories = remember(uniqueMenuItems) {
        listOf("ALL") + uniqueMenuItems.map { it.category.uppercase() }.distinct().sorted()
    }

    val filteredMenuItems = remember(uniqueMenuItems, searchQuery, selectedCategory) {
        uniqueMenuItems.filter { item ->
            val matchQuery = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            val matchCat = selectedCategory == "ALL" || item.category.equals(selectedCategory, ignoreCase = true)
            matchQuery && matchCat
        }.sortedBy { it.name }
    }

    val groupedItems = remember(filteredMenuItems) {
        filteredMenuItems.groupBy { it.category }
    }

    val totalSkus = uniqueMenuItems.size
    val configuredSkus = remember(uniqueMenuItems, usages) {
        uniqueMenuItems.count { menuItem ->
            usages.any { it.menuItemId == menuItem.id || it.menuItemName.equals(menuItem.name, ignoreCase = true) }
        }
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
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = AmberAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "RECIPE MASTER",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = "SKU Formulations, COGS & Margins",
                    color = TextGray,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = AmberAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "$configuredSkus / $totalSkus Configured",
                    color = AmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search recipes or ingredients...", color = TextGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextGray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceColor,
                unfocusedContainerColor = SurfaceColor,
                focusedBorderColor = AmberAccent,
                unfocusedBorderColor = SurfaceVariantColor,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
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

        // Recipe List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            groupedItems.forEach { (category, items) ->
                item {
                    Text(
                        text = category.uppercase(),
                        color = AmberAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }

                items(items, key = { it.name.trim().lowercase() }) { menuItem ->
                    val itemUsages = usages.filter {
                        it.menuItemId == menuItem.id || it.menuItemName.equals(menuItem.name, ignoreCase = true)
                    }.distinctBy { it.inventoryItemId }

                    // Compute live COGS for this menuItem
                    var totalCogs = 0.0
                    val ingredientCosts = itemUsages.map { usage ->
                        val invItem = inventoryItems.find { it.id == usage.inventoryItemId }
                        val cost = if (invItem != null) {
                            val (_, c) = calculatePacksAndCost(usage.quantityUsed, usage.unit, invItem)
                            c
                        } else 0.0
                        totalCogs += cost
                        Pair(usage, cost)
                    }

                    val profitMargin = menuItem.price - totalCogs
                    val marginPercent = if (menuItem.price > 0) (profitMargin / menuItem.price) * 100 else 0.0

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                        border = BorderStroke(1.dp, if (itemUsages.isEmpty()) RedDanger.copy(alpha = 0.3f) else SurfaceVariantColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // SKU Title & Quick Metrics
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = menuItem.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(
                                            text = "Price: ₹${menuItem.price.toInt()}",
                                            color = TextGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (itemUsages.isNotEmpty()) {
                                            Text(
                                                text = "•",
                                                color = TextGray.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "COGS: ₹${String.format(Locale.US, "%.2f", totalCogs)}",
                                                color = AmberAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (itemUsages.isNotEmpty()) {
                                    Surface(
                                        color = if (marginPercent >= 60.0) EmeraldSuccess.copy(alpha = 0.15f) else AmberAccent.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, if (marginPercent >= 60.0) EmeraldSuccess.copy(alpha = 0.4f) else AmberAccent.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", marginPercent)}% Margin",
                                            color = if (marginPercent >= 60.0) EmeraldSuccess else AmberAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onOpenUsageDialog(menuItem.id, menuItem.name) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Add Ingredient", tint = AmberAccent)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = SurfaceVariantColor.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(10.dp))

                            if (itemUsages.isEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "⚠️ No recipe defined yet",
                                        color = TextGray,
                                        fontSize = 13.sp
                                    )
                                    TextButton(
                                        onClick = { onOpenUsageDialog(menuItem.id, menuItem.name) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+ Configure Recipe", color = AmberAccent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ingredientCosts.forEach { (usage, cost) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Circle,
                                                    contentDescription = null,
                                                    tint = AmberAccent.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(5.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = usage.inventoryItemName,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    fontSize = 13.sp
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${formatQty(usage.quantityUsed)} ${usage.unit}",
                                                    color = TextGray,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "₹${String.format(Locale.US, "%.2f", cost)}",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                IconButton(
                                                    onClick = { ingredientToEdit = usage },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Edit Ingredient",
                                                        tint = AmberAccent.copy(alpha = 0.9f),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(2.dp))
                                                IconButton(
                                                    onClick = { ingredientToDelete = usage },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = RedDanger.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(15.dp)
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
        }
    }

    // Delete Confirmation Dialog
    if (ingredientToDelete != null) {
        val usage = ingredientToDelete!!
        AlertDialog(
            onDismissRequest = { ingredientToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = RedDanger,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Remove Ingredient?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${usage.inventoryItemName}\" from the recipe for \"${usage.menuItemName}\"?",
                    color = TextGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteUsage(usage)
                        ingredientToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ingredientToDelete = null }) {
                    Text("Cancel", color = TextGray)
                }
            },
            containerColor = SurfaceColor,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal Dialog for editing existing recipe component
    if (ingredientToEdit != null) {
        val usage = ingredientToEdit!!
        AddEditUsageDialog(
            menuItemId = usage.menuItemId,
            menuItemName = usage.menuItemName,
            initialUsage = usage,
            inventoryItems = inventoryItems,
            onDismiss = { ingredientToEdit = null },
            onSave = { menuId, menuName, invId, invName, qty, unit ->
                onSaveUsage(menuId, menuName, invId, invName, qty, unit)
                ingredientToEdit = null
            }
        )
    }

    // Modal Dialog for adding recipe component
    if (state.showUsagePerSkuDialog) {
        AddEditUsageDialog(
            menuItemId = state.usageEditingMenuItemId ?: 0,
            menuItemName = state.usageEditingMenuItemName,
            initialUsage = null,
            inventoryItems = inventoryItems,
            onDismiss = onDismissUsageDialog,
            onSave = onSaveUsage
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditUsageDialog(
    menuItemId: Int,
    menuItemName: String,
    initialUsage: InventoryUsageEntity? = null,
    inventoryItems: List<InventoryItemEntity>,
    onDismiss: () -> Unit,
    onSave: (menuItemId: Int, menuItemName: String, inventoryItemId: Int, inventoryItemName: String, quantityUsed: Double, unit: String) -> Unit
) {
    var selectedInventoryItem by remember(initialUsage) {
        mutableStateOf<InventoryItemEntity?>(
            if (initialUsage != null) inventoryItems.find { it.id == initialUsage.inventoryItemId } else null
        )
    }
    var quantityText by remember(initialUsage) {
        mutableStateOf(initialUsage?.quantityUsed?.let { formatQty(it) } ?: "")
    }
    var unitText by remember(initialUsage) {
        mutableStateOf(initialUsage?.unit ?: "")
    }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceColor,
            border = BorderStroke(1.dp, SurfaceVariantColor),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (initialUsage != null) "Edit Recipe Component" else "Add Recipe Component",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "For SKU: $menuItemName",
                    color = AmberAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Select Master Inventory Item Dropdown
                Text("Select Master Ingredient", color = TextGray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = !isDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedInventoryItem?.name ?: "Tap to choose ingredient...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = if (selectedInventoryItem != null) Color.White else TextGray,
                            focusedBorderColor = AmberAccent,
                            unfocusedBorderColor = SurfaceVariantColor
                        ),
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        inventoryItems.filter { it.isActive }.sortedBy { it.name }.forEach { invItem ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(invItem.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${invItem.category} • Pack: ${invItem.standardQuantity} ${invItem.unit} @ ₹${invItem.defaultPrice.toInt()}",
                                            fontSize = 11.sp,
                                            color = TextGray
                                        )
                                    }
                                },
                                onClick = {
                                    selectedInventoryItem = invItem
                                    unitText = invItem.unit
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quantity & Unit Inputs
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text("Quantity Used", color = TextGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { quantityText = it },
                            placeholder = { Text("e.g. 50", color = TextGray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberAccent,
                                unfocusedBorderColor = SurfaceVariantColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Unit", color = TextGray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = unitText,
                            onValueChange = { unitText = it },
                            placeholder = { Text("g, ml, pcs", color = TextGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AmberAccent,
                                unfocusedBorderColor = SurfaceVariantColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Cost Preview
                val qty = quantityText.toDoubleOrNull() ?: 0.0
                if (selectedInventoryItem != null && qty > 0.0 && unitText.isNotBlank()) {
                    val (_, cost) = calculatePacksAndCost(qty, unitText, selectedInventoryItem!!)
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        color = AmberAccent.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Component Cost:", color = TextGray, fontSize = 12.sp)
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", cost)}",
                                color = AmberAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val inv = selectedInventoryItem
                            if (inv != null && qty > 0.0 && unitText.isNotBlank()) {
                                onSave(menuItemId, menuItemName, inv.id, inv.name, qty, unitText.trim())
                                onDismiss()
                            }
                        },
                        enabled = selectedInventoryItem != null && qty > 0.0 && unitText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberAccent, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (initialUsage != null) "Update Ingredient" else "Save Ingredient", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}

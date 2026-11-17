package com.example.ui.screens

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.InventoryItemEntity
import com.example.ui.BillingUiState
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeOnPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInventoryScreen(
    state: BillingUiState,
    inventoryItems: List<InventoryItemEntity>,
    onSaveClosingInventory: (stockMap: Map<Int, Double>, staffNotes: String) -> Unit,
    onAddNewInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var staffClosingNotes by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Map of itemId to counted stock
    val stockCounts = remember { mutableStateMapOf<Int, Double>() }

    // Initialize or update stock counts from inventoryItems if not already set by user
    LaunchedEffect(inventoryItems) {
        inventoryItems.forEach { item ->
            if (!stockCounts.containsKey(item.id)) {
                stockCounts[item.id] = item.currentStock.coerceAtLeast(0.0)
            }
        }
    }

    // Number edit dialog state for precise decimal input
    var itemForCustomInput by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var customInputValue by remember { mutableStateOf("") }

    val config = LocalConfiguration.current
    val isCompactScreen = config.screenHeightDp < 780 || config.screenWidthDp < 380

    val dynamicCategories = remember(inventoryItems) {
        inventoryItems.map { it.category.trim().uppercase() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val categories = remember(dynamicCategories) {
        listOf("ALL") + dynamicCategories
    }

    val filteredItems = remember(inventoryItems, searchQuery, selectedCategory) {
        inventoryItems.filter { item ->
            item.isActive &&
            (selectedCategory == "ALL" || item.category.equals(selectedCategory, ignoreCase = true)) &&
            (searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true) || item.category.contains(searchQuery, ignoreCase = true))
        }
    }

    val currentCategoryIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (isCompactScreen) 4.dp else 8.dp)
                .padding(bottom = if (isCompactScreen) 130.dp else 145.dp)
                .pointerInput(categories, selectedCategory) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX < -80f) {
                                if (currentCategoryIndex < categories.lastIndex) {
                                    selectedCategory = categories[currentCategoryIndex + 1]
                                }
                            } else if (totalDragX > 80f) {
                                if (currentCategoryIndex > 0) {
                                    selectedCategory = categories[currentCategoryIndex - 1]
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            // Header with Title and Add Inventory Button in Top Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompactScreen) 10.dp else 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📦 Inventories Manual",
                        fontWeight = FontWeight.Black,
                        fontSize = if (isCompactScreen) 17.sp else 19.sp,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "Count closing stock & log notes before closing",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF71717A)
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_inventory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Inventory",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Inventory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar (identical styling to New Order)
            Column(modifier = Modifier.padding(horizontal = if (isCompactScreen) 10.dp else 12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search inventory (milk, beans, syrups, cups...)") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CoffeeBrownPrimary) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF09090B),
                        unfocusedTextColor = Color(0xFF09090B),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFFAFAFA),
                        focusedBorderColor = Color(0xFF09090B),
                        unfocusedBorderColor = Color(0xFFD4D4D8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_inventory_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips (identical styling to New Order)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategory.equals(category, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = category },
                            label = { Text(text = category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoffeeBrownPrimary,
                                selectedLabelColor = CoffeeOnPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = CoffeeBrownPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("inventory_category_chip_$category")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inventory Items Grid
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color(0xFFD4D4D8)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No inventory items found",
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (config.screenWidthDp >= 600) 3 else 2),
                    contentPadding = PaddingValues(
                        start = if (isCompactScreen) 10.dp else 12.dp,
                        end = if (isCompactScreen) 10.dp else 12.dp,
                        top = 4.dp,
                        bottom = 12.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("manual_inventory_grid")
                ) {
                    items(filteredItems, key = { it.id }) { item ->
                        val currentCount = stockCounts[item.id] ?: item.currentStock.coerceAtLeast(0.0)

                        ManualInventoryItemCard(
                            item = item,
                            count = currentCount,
                            onIncrement = {
                                val step = if (item.unit.equals("kg", ignoreCase = true) || item.unit.equals("l", ignoreCase = true)) 0.5 else 1.0
                                val newCount = (currentCount + step).coerceAtLeast(0.0)
                                stockCounts[item.id] = Math.round(newCount * 100.0) / 100.0
                            },
                            onDecrement = {
                                val step = if (item.unit.equals("kg", ignoreCase = true) || item.unit.equals("l", ignoreCase = true)) 0.5 else 1.0
                                val newCount = (currentCount - step).coerceAtLeast(0.0)
                                stockCounts[item.id] = Math.round(newCount * 100.0) / 100.0
                            },
                            onTapCount = {
                                itemForCustomInput = item
                                customInputValue = if (currentCount % 1.0 == 0.0) currentCount.toInt().toString() else currentCount.toString()
                            }
                        )
                    }
                }
            }
        }

        // Sticky Bottom Closing Notes & Submit Bar (matching New Order sticky bar style)
        Surface(
            color = Color(0xFF09090B),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(if (isCompactScreen) 8.dp else 12.dp)
                .testTag("manual_inventory_bottom_bar")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompactScreen) 10.dp else 14.dp)
            ) {
                // Staff Closing Notes Input Field
                OutlinedTextField(
                    value = staffClosingNotes,
                    onValueChange = { staffClosingNotes = it },
                    placeholder = {
                        Text(
                            "📝 Staff Remarks / Store Closing Notes (e.g. Milk low, 2 syrups expiring soon)",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF18181B),
                        unfocusedContainerColor = Color(0xFF18181B),
                        focusedBorderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(bottom = 8.dp)
                        .testTag("staff_closing_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${inventoryItems.size}",
                                    color = Color(0xFF09090B),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Total Inventory Items",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = "Daily Closing Audit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    Button(
                        onClick = {
                            isSaving = true
                            onSaveClosingInventory(stockCounts.toMap(), staffClosingNotes)
                            isSaving = false
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF09090B),
                            disabledContainerColor = Color.White.copy(alpha = 0.6f),
                            disabledContentColor = Color(0xFF09090B).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_closing_inventory_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF09090B),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Saving...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = null,
                                tint = Color(0xFF09090B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Save Closing Stock", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Custom Numeric Count Dialog
        itemForCustomInput?.let { item ->
            AlertDialog(
                onDismissRequest = { itemForCustomInput = null },
                title = { Text("Set Stock for ${item.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column {
                        Text(
                            text = "Unit: ${item.unit} • Category: ${item.category}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = customInputValue,
                            onValueChange = { customInputValue = it },
                            label = { Text("Physical Count / Stock (${item.unit})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = customInputValue.toDoubleOrNull() ?: 0.0
                            stockCounts[item.id] = parsed.coerceAtLeast(0.0)
                            itemForCustomInput = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B))
                    ) {
                        Text("Update Stock", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemForCustomInput = null }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Add New Inventory Item Dialog
        if (showAddDialog) {
            AddInventoryDialog(
                categories = dynamicCategories,
                onDismiss = { showAddDialog = false },
                onSave = { name, category, unit, defaultPrice, packSize, initialStock ->
                    onAddNewInventoryItem(name, category, unit, defaultPrice, packSize)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun ManualInventoryItemCard(
    item: InventoryItemEntity,
    count: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onTapCount: () -> Unit
) {
    val formattedCount = if (count % 1.0 == 0.0) count.toInt().toString() else String.format(Locale.US, "%.1f", count)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (count > 0.0) Color(0xFFF4F4F5) else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (count > 0.0) 2.dp else 1.dp,
                color = if (count > 0.0) Color(0xFF09090B) else Color(0xFFE4E4E7),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("inventory_card_${item.name}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.category.uppercase().ifBlank { "INVENTORY" },
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF71717A)
                    )
                    Surface(
                        color = Color(0xFFE4E4E7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.unit.ifBlank { "unit" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF18181B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF09090B),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stepper in -, + format (matching MenuItemCard in New Order)
            if (count > 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF09090B)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDecrement,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "$formattedCount ${item.unit}",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clickable { onTapCount() }
                            .padding(horizontal = 4.dp)
                    )

                    IconButton(
                        onClick = onIncrement,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onIncrement,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("add_count_button_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "COUNT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddInventoryDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, unit: String, defaultPrice: Double, packSize: Double, initialStock: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "General") }
    var unit by remember { mutableStateOf("kg") }
    var packSize by remember { mutableStateOf("1.0") }
    var initialStock by remember { mutableStateOf("0.0") }

    val commonUnits = listOf("kg", "L", "pcs", "pack", "g", "ml", "box")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Add New Inventory Item", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name *") },
                    placeholder = { Text("e.g. Vanilla Syrup 700ml") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    placeholder = { Text("e.g. Dairy, Syrups, Packaging") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit (kg, L, pcs)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = packSize,
                        onValueChange = { packSize = it },
                        label = { Text("Pack Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = initialStock,
                    onValueChange = { initialStock = it },
                    label = { Text("Current / Initial Stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val pack = packSize.toDoubleOrNull() ?: 1.0
                        val stock = initialStock.toDoubleOrNull() ?: 0.0
                        onSave(name.trim(), category.trim(), unit.trim().lowercase(), 0.0, pack, stock)
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add Item", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

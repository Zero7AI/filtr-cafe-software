package com.example.ui.screens

import java.util.Locale
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.InventoryItemEntity
import com.example.ui.BillingUiState
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeOnPrimary
import com.example.util.InventoryReportGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualInventoryScreen(
    state: BillingUiState,
    inventoryItems: List<InventoryItemEntity>,
    onSaveClosingInventory: (stockMap: Map<Int, Double>, staffNotes: String) -> Unit,
    onAddNewInventoryItem: (name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var staffClosingNotes by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSavedListDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Map of itemId to counted stock
    val stockCounts = remember { mutableStateMapOf<Int, Double>() }

    // Initialize stock counts from inventoryItems
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
                .padding(bottom = if (isCompactScreen) 160.dp else 175.dp)
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
            // Header: Left (Saved List) | Center (Title) | Right (Add Item)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isCompactScreen) 8.dp else 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT BUTTON: View Saved List & Share
                OutlinedButton(
                    onClick = { showSavedListDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF09090B)
                    ),
                    border = BorderStroke(1.5.dp, Color(0xFF09090B)),
                    contentPadding = PaddingValues(horizontal = if (isCompactScreen) 8.dp else 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("view_saved_inventory_list_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                        contentDescription = "Saved Inventories",
                        tint = Color(0xFF09090B),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCompactScreen) "Saved" else "Saved List",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF09090B)
                    )
                }

                // CENTER: Screen Title (responsive with weight so buttons never get squished)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "📦 Inventories Manual",
                        fontWeight = FontWeight.Black,
                        fontSize = if (isCompactScreen) 14.sp else 16.sp,
                        color = Color(0xFF09090B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Physical Stock & Closing Remarks",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = Color(0xFF71717A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // RIGHT BUTTON: Add Inventory Item
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = if (isCompactScreen) 8.dp else 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("add_inventory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Inventory",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isCompactScreen) "Add" else "Add Item",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search Bar
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

                // Category Chips
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

            Spacer(modifier = Modifier.height(6.dp))

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

        // Sticky Bottom Closing Notes & Submit Bar (Properly formatted and fully visible on all devices)
        Surface(
            color = Color(0xFF09090B),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(if (isCompactScreen) 8.dp else 12.dp)
                .imePadding()
                .testTag("manual_inventory_bottom_bar")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompactScreen) 10.dp else 12.dp)
            ) {
                // Responsive Multi-line Staff Remarks Card (Never clips text on any device density)
                Surface(
                    color = Color(0xFF18181B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Staff Closing Remarks / Notes",
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicTextField(
                            value = staffClosingNotes,
                            onValueChange = { staffClosingNotes = it },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(Color.White),
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 36.dp, max = 80.dp)
                                .testTag("staff_closing_notes_input"),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (staffClosingNotes.isEmpty()) {
                                        Text(
                                            text = "Type closing remarks (e.g. 4 milk packets left, need more for morning; vanilla syrup low)...",
                                            color = Color(0xFFA1A1AA),
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                // Bottom Action Buttons & Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { showSavedListDialog = true }
                            .padding(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.White,
                                shape = CircleShape
                            ) {
                                Text(
                                    text = "${inventoryItems.size}",
                                    color = Color(0xFF09090B),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Items Counted",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = "Daily Closing Audit 📋",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFF59E0B)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Quick WhatsApp Share Button
                        Button(
                            onClick = {
                                try {
                                    val pdfFile = InventoryReportGenerator.generateInventoryPdf(
                                        context = context,
                                        items = inventoryItems,
                                        stockCounts = stockCounts.toMap(),
                                        staffNotes = staffClosingNotes,
                                        operatorName = state.operatorName.ifBlank { "Staff" }
                                    )
                                    val caption = InventoryReportGenerator.generateInventoryText(
                                        items = inventoryItems,
                                        stockCounts = stockCounts.toMap(),
                                        staffNotes = staffClosingNotes,
                                        operatorName = state.operatorName.ifBlank { "Staff" }
                                    )
                                    InventoryReportGenerator.shareViaWhatsApp(
                                        context = context,
                                        file = pdfFile,
                                        mimeType = "application/pdf",
                                        caption = caption
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366), // WhatsApp Green
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("whatsapp_share_quick_button")
                        ) {
                            Text("💬", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Save Closing Stock Button
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
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("save_closing_inventory_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF09090B),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Saving...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    tint = Color(0xFF09090B),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Save Stock", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
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

        // Saved Inventories List Dialog with WhatsApp PDF and Image sharing
        if (showSavedListDialog) {
            SavedInventoriesListDialog(
                inventoryItems = inventoryItems,
                stockCounts = stockCounts.toMap(),
                staffNotes = staffClosingNotes,
                operatorName = state.operatorName.ifBlank { "Staff" },
                onDismiss = { showSavedListDialog = false }
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

            // Stepper in -, + format
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

/**
 * Clean List Format Dialog showing currently saved inventories, with WhatsApp PDF and Image sharing options.
 */
@Composable
fun SavedInventoriesListDialog(
    inventoryItems: List<InventoryItemEntity>,
    stockCounts: Map<Int, Double>,
    staffNotes: String,
    operatorName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var listSearchQuery by remember { mutableStateOf("") }

    val activeItems = remember(inventoryItems) { inventoryItems.filter { it.isActive } }
    val filteredList = remember(activeItems, listSearchQuery) {
        if (listSearchQuery.isBlank()) activeItems
        else activeItems.filter {
            it.name.contains(listSearchQuery, ignoreCase = true) ||
            it.category.contains(listSearchQuery, ignoreCase = true)
        }
    }

    val grouped = remember(filteredList) {
        filteredList.groupBy { it.category.trim().uppercase().ifBlank { "GENERAL" } }.toSortedMap()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "📋 Saved Inventories List",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "${activeItems.size} items registered in system",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF71717A)
                    )
                }

                Surface(
                    color = Color(0xFF09090B),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${activeItems.size} Total",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
            ) {
                // Search filter within the list
                OutlinedTextField(
                    value = listSearchQuery,
                    onValueChange = { listSearchQuery = it },
                    placeholder = { Text("Filter items in list...", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFFAFAFA),
                        unfocusedContainerColor = Color(0xFFFAFAFA)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Staff closing note preview if present
                if (staffNotes.isNotBlank()) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📝", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Closing Notes: $staffNotes",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Grouped List of Items
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (category, itemsInCat) ->
                        item {
                            Surface(
                                color = Color(0xFFF4F4F5),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = category,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        items(itemsInCat, key = { it.id }) { item ->
                            val count = stockCounts[item.id] ?: item.currentStock.coerceAtLeast(0.0)
                            val countStr = if (count % 1.0 == 0.0) "${count.toInt()} ${item.unit}" else "${String.format(Locale.US, "%.1f", count)} ${item.unit}"

                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF09090B)
                                        )
                                        Text(
                                            text = if (item.standardQuantity > 0) "Pack size: ${item.standardQuantity} ${item.unit}" else item.unit,
                                            fontSize = 10.sp,
                                            color = Color(0xFF71717A)
                                        )
                                    }

                                    Surface(
                                        color = if (count > 0.0) Color(0xFF09090B) else Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = countStr,
                                            color = if (count > 0.0) Color.White else Color(0xFFDC2626),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share PDF via WhatsApp
                Button(
                    onClick = {
                        try {
                            val pdfFile = InventoryReportGenerator.generateInventoryPdf(
                                context = context,
                                items = activeItems,
                                stockCounts = stockCounts,
                                staffNotes = staffNotes,
                                operatorName = operatorName
                            )
                            val caption = InventoryReportGenerator.generateInventoryText(
                                items = activeItems,
                                stockCounts = stockCounts,
                                staffNotes = staffNotes,
                                operatorName = operatorName
                            )
                            InventoryReportGenerator.shareViaWhatsApp(
                                context = context,
                                file = pdfFile,
                                mimeType = "application/pdf",
                                caption = caption
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "PDF share error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("📄", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Share PDF", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                // Share Image via WhatsApp
                Button(
                    onClick = {
                        try {
                            val imageFile = InventoryReportGenerator.generateInventoryImage(
                                context = context,
                                items = activeItems,
                                stockCounts = stockCounts,
                                staffNotes = staffNotes,
                                operatorName = operatorName
                            )
                            val caption = InventoryReportGenerator.generateInventoryText(
                                items = activeItems,
                                stockCounts = stockCounts,
                                staffNotes = staffNotes,
                                operatorName = operatorName
                            )
                            InventoryReportGenerator.shareViaWhatsApp(
                                context = context,
                                file = imageFile,
                                mimeType = "image/png",
                                caption = caption
                            )
                        } catch (e: Exception) {
                            Toast.makeText(context, "Image share error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("💬", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Share Image", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
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

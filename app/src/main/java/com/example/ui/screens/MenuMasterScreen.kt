package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MenuItemEntity
import com.example.ui.BillingUiState
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeCardBorder
import com.example.ui.theme.CoffeeOnPrimary

@Composable
fun MenuMasterScreen(
    state: BillingUiState,
    menuItems: List<MenuItemEntity>,
    allMenuItems: List<MenuItemEntity> = menuItems,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onOpenAddEditDialog: (MenuItemEntity?) -> Unit,
    onToggleAvailability: (MenuItemEntity) -> Unit,
    onDeleteItem: (MenuItemEntity) -> Unit,
    onMoveItemUp: (MenuItemEntity, List<MenuItemEntity>) -> Unit = { _, _ -> },
    onMoveItemDown: (MenuItemEntity, List<MenuItemEntity>) -> Unit = { _, _ -> },
    onSetItemPosition: (MenuItemEntity, Int, List<MenuItemEntity>) -> Unit = { _, _, _ -> },
    onOpenAddCategoryDialog: () -> Unit = {},
    onRestoreDefaultMenu: () -> Unit = {},
    onSyncItemMasterToSheets: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uniqueMenuItems = menuItems.distinctBy { it.name.trim().lowercase() }
    val uniqueAllMenuItems = allMenuItems.distinctBy { it.name.trim().lowercase() }

    val defaultCategories = listOf("All", "ICED COFFEE", "FRAPPE", "FRIES", "SANDWICH")
    val dynamicCategories = uniqueAllMenuItems.map { it.category.uppercase() }.distinct().filter { it.isNotBlank() }
    val customCats = state.customCategories.map { it.uppercase() }
    val categories = (listOf("All") + defaultCategories + dynamicCategories + customCats).distinct()

    var itemToSetPosition by remember { mutableStateOf<Pair<MenuItemEntity, Int>?>(null) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .padding(12.dp)
                .testTag("menu_master_screen")
        ) {
            // Header Row with Title, Sync Item Master and Reload Default Menu
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ITEM MASTER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoffeeBrownPrimary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onSyncItemMasterToSheets,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("sync_item_master_button")
                    ) {
                        Text("Sync Item Master", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onRestoreDefaultMenu,
                        colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Reload Default", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Search & Category Chips
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Search item master...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CoffeeBrownPrimary) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoffeeBrownPrimary,
                    unfocusedBorderColor = CoffeeCardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_master_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Categories horizontal bar with + Add Category button
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+ Category" button
                item {
                    Surface(
                        onClick = onOpenAddCategoryDialog,
                        shape = RoundedCornerShape(20.dp),
                        color = AmberAccentSecondary.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, AmberAccentSecondary),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = CoffeeBrownPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Category", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoffeeBrownPrimary)
                        }
                    }
                }

                items(categories) { category ->
                    val isSelected = state.selectedCategory.equals(category, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CoffeeBrownPrimary,
                            selectedLabelColor = CoffeeOnPrimary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uniqueMenuItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Coffee, contentDescription = null, modifier = Modifier.size(64.dp), tint = CoffeeCardBorder)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No menu items found in ${state.selectedCategory}", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(uniqueMenuItems, key = { _, item -> item.id }) { index, menuItem ->
                        ItemMasterRow(
                            index = index,
                            totalCount = uniqueMenuItems.size,
                            menuItem = menuItem,
                            onEdit = { onOpenAddEditDialog(menuItem) },
                            onToggleAvailability = { onToggleAvailability(menuItem) },
                            onDelete = { onDeleteItem(menuItem) },
                            onMoveUp = { onMoveItemUp(menuItem, uniqueMenuItems) },
                            onMoveDown = { onMoveItemDown(menuItem, uniqueMenuItems) },
                            onOpenSetPosition = { itemToSetPosition = Pair(menuItem, index + 1) }
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add New Item
        FloatingActionButton(
            onClick = { onOpenAddEditDialog(null) },
            containerColor = CoffeeBrownPrimary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .testTag("add_new_item_fab")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item")
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Add Item", fontWeight = FontWeight.Bold)
            }
        }

        // Set Position Dialog
        itemToSetPosition?.let { (item, pos) ->
            SetPositionDialog(
                itemName = item.name,
                currentPosition = pos,
                totalCount = uniqueMenuItems.size,
                onDismiss = { itemToSetPosition = null },
                onConfirm = { target0Indexed ->
                    onSetItemPosition(item, target0Indexed, uniqueMenuItems)
                    itemToSetPosition = null
                }
            )
        }
    }
}

@Composable
fun ItemMasterRow(
    index: Int,
    totalCount: Int,
    menuItem: MenuItemEntity,
    onEdit: () -> Unit,
    onToggleAvailability: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpenSetPosition: () -> Unit
) {
    var accumulatedDragY by remember { mutableFloatStateOf(0f) }

    ElevatedCard(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("item_master_row_${menuItem.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sequence Number Badge & Drag Reorder Handle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                // Drag Handle with vertical gesture support
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(menuItem.id, index) {
                            detectVerticalDragGestures(
                                onDragStart = { accumulatedDragY = 0f },
                                onDragEnd = {
                                    if (accumulatedDragY < -40f && index > 0) {
                                        onMoveUp()
                                    } else if (accumulatedDragY > 40f && index < totalCount - 1) {
                                        onMoveDown()
                                    }
                                    accumulatedDragY = 0f
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDragY += dragAmount
                                    if (accumulatedDragY < -55f && index > 0) {
                                        onMoveUp()
                                        accumulatedDragY = 0f
                                    } else if (accumulatedDragY > 55f && index < totalCount - 1) {
                                        onMoveDown()
                                        accumulatedDragY = 0f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = Color(0xFFA1A1AA),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Position Number Badge (Tap to jump to position)
                Surface(
                    onClick = onOpenSetPosition,
                    color = CoffeeBrownPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "#${index + 1}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CoffeeBrownPrimary
                        )
                    }
                }

                // Quick Up/Down buttons
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) CoffeeBrownPrimary else Color(0xFFD4D4D8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < totalCount - 1) CoffeeBrownPrimary else Color(0xFFD4D4D8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Item Details
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    text = menuItem.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberAccentSecondary
                )
                Text(
                    text = menuItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (menuItem.description.isNotBlank()) {
                    Text(
                        text = menuItem.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Price: ₹${menuItem.price}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = CoffeeBrownPrimary
                )
            }

            // Availability & Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (menuItem.isAvailable) "In Stock" else "Out of Stock",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (menuItem.isAvailable) MaterialTheme.colorScheme.primary else Color.Red
                    )
                    Switch(
                        checked = menuItem.isAvailable,
                        onCheckedChange = { onToggleAvailability() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CoffeeBrownPrimary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFFE4E4E7),
                            uncheckedBorderColor = Color(0xFFA1A1AA)
                        )
                    )
                }

                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = CoffeeBrownPrimary, modifier = Modifier.size(18.dp))
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Category", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter a name for the new category (e.g. DESSERTS, SHAKES, SNACKS):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF71717A)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. DESSERTS") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        onConfirm(categoryName.trim().uppercase())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                enabled = categoryName.isNotBlank()
            ) {
                Text("Create Category", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetPositionDialog(
    itemName: String,
    currentPosition: Int,
    totalCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var positionText by remember { mutableStateOf(currentPosition.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Order Position", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Move '$itemName' to position (1 to $totalCount):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF71717A)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = positionText,
                    onValueChange = { positionText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Position Number") },
                    placeholder = { Text("1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pos = positionText.toIntOrNull()
                    if (pos != null && pos in 1..totalCount) {
                        onConfirm(pos - 1)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                enabled = positionText.toIntOrNull()?.let { it in 1..totalCount } == true
            ) {
                Text("Set Position", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

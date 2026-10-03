package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.MenuItemEntity
import com.example.ui.BillingUiState
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeCardBorder
import com.example.ui.theme.CoffeeOnPrimary
import com.example.ui.theme.CreamBackground

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
    onRestoreDefaultMenu: () -> Unit = {},
    onSyncItemMasterToSheets: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uniqueMenuItems = menuItems.distinctBy { it.name.trim().lowercase() }
    val uniqueAllMenuItems = allMenuItems.distinctBy { it.name.trim().lowercase() }

    val defaultCategories = listOf("All", "ICED COFFEE", "FRAPPE", "FRIES", "SANDWICH")
    val dynamicCategories = uniqueAllMenuItems.map { it.category }.distinct().filter { it.isNotBlank() }
    val categories = (listOf("All") + defaultCategories + dynamicCategories).distinct()

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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(categories) { category ->
                    val isSelected = state.selectedCategory == category
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
                        Text(text = "No menu items found", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uniqueMenuItems) { menuItem ->
                        ItemMasterRow(
                            menuItem = menuItem,
                            onEdit = { onOpenAddEditDialog(menuItem) },
                            onToggleAvailability = { onToggleAvailability(menuItem) },
                            onDelete = { onDeleteItem(menuItem) }
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
    }
}

@Composable
fun ItemMasterRow(
    menuItem: MenuItemEntity,
    onEdit: () -> Unit,
    onToggleAvailability: () -> Unit,
    onDelete: () -> Unit
) {
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
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                    fontWeight = FontWeight.Bold
                )
                if (menuItem.description.isNotBlank()) {
                    Text(
                        text = menuItem.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Price: ₹${menuItem.price}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoffeeBrownPrimary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (menuItem.isAvailable) "In Stock" else "Out of Stock",
                        fontSize = 10.sp,
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

                IconButton(onClick = onEdit) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = CoffeeBrownPrimary)
                }

                IconButton(onClick = onDelete) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
            }
        }
    }
}

package com.example.ui.screens

import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.MenuItemEntity
import com.example.ui.BillingUiState
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeCardBorder
import com.example.ui.theme.CoffeeOnPrimary
import com.example.ui.theme.CoffeePrimaryContainer
import com.example.ui.theme.CreamBackground

@Composable
fun BillingScreen(
    state: BillingUiState,
    menuItems: List<MenuItemEntity>,
    allMenuItems: List<MenuItemEntity> = menuItems,
    onCategorySelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCustomerNameChanged: (String) -> Unit,
    onContactNumberChanged: (String) -> Unit,
    onOrderNotesChanged: (String) -> Unit = {},
    onAddToCart: (MenuItemEntity) -> Unit,
    onRemoveFromCart: (MenuItemEntity) -> Unit,
    onClearCart: () -> Unit,
    onSubmitOrder: () -> Unit,
    onSelectCustomerSuggestion: (CustomerEntity) -> Unit = {},
    onDismissCustomerSuggestions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val defaultCategories = listOf("All", "ICED COFFEE", "FRAPPE", "FRIES", "SANDWICH")
    val dynamicCategories = allMenuItems.map { it.category.uppercase() }.distinct().filter { it.isNotBlank() }
    val customCats = state.customCategories.map { it.uppercase() }
    val categories = (listOf("All") + defaultCategories + dynamicCategories + customCats).distinct()
    val totalCartItemsCount = state.cartItems.values.sum()
    val totalAmount = state.cartItems.entries.sumOf { (item, qty) -> item.price * qty }

    val currentCategoryIndex = categories.indexOf(state.selectedCategory).coerceAtLeast(0)
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFFAFAFA))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .padding(bottom = if (totalCartItemsCount > 0) 160.dp else 16.dp)
                .pointerInput(categories, state.selectedCategory) {
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX < -80f) {
                                if (currentCategoryIndex < categories.lastIndex) {
                                    onCategorySelected(categories[currentCategoryIndex + 1])
                                }
                            } else if (totalDragX > 80f) {
                                if (currentCategoryIndex > 0) {
                                    onCategorySelected(categories[currentCategoryIndex - 1])
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragX += dragAmount
                        }
                    )
                }
        ) {
            // Top Customer Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .border(1.dp, Color(0xFFE4E4E7), RoundedCornerShape(16.dp))
                    .testTag("customer_info_card")
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        text = "CUSTOMER & ORDER DETAILS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = state.customerName,
                                onValueChange = onCustomerNameChanged,
                                label = { Text("Customer Name") },
                                placeholder = { Text("Walk-in Customer") },
                                leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Color(0xFF09090B)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF09090B),
                                    unfocusedTextColor = Color(0xFF09090B),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color(0xFFFAFAFA),
                                    focusedLabelColor = Color(0xFF09090B),
                                    unfocusedLabelColor = Color(0xFF71717A),
                                    focusedBorderColor = Color(0xFF09090B),
                                    unfocusedBorderColor = Color(0xFFD4D4D8)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("customer_name_input")
                            )
                            // Auto-suggest dropdown
                            androidx.compose.material3.DropdownMenu(
                                expanded = state.showCustomerSuggestions && state.customerSuggestions.isNotEmpty(),
                                onDismissRequest = { onDismissCustomerSuggestions() },
                                modifier = Modifier.fillMaxWidth(0.45f)
                            ) {
                                state.customerSuggestions.forEach { customer ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = customer.name,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF09090B)
                                                )
                                                if (customer.contactNumber.isNotBlank()) {
                                                    Text(
                                                        text = customer.contactNumber,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF71717A)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = { onSelectCustomerSuggestion(customer) },
                                        trailingIcon = {
                                            Surface(
                                                color = Color(0xFFF4F4F5),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${customer.totalOrders}",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF52525B),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.contactNumber,
                            onValueChange = onContactNumberChanged,
                            label = { Text("Contact (Optional)") },
                            placeholder = { Text("Phone Number") },
                            leadingIcon = { Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF09090B)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF09090B),
                                unfocusedTextColor = Color(0xFF09090B),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color(0xFFFAFAFA),
                                focusedLabelColor = Color(0xFF09090B),
                                unfocusedLabelColor = Color(0xFF71717A),
                                focusedBorderColor = Color(0xFF09090B),
                                unfocusedBorderColor = Color(0xFFD4D4D8)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("customer_contact_input")
                        )
                    }
                }
            }

            // Search and Category Selector
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    placeholder = { Text("Search coffee, drinks, bakery...") },
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
                        focusedTextColor = Color(0xFF09090B),
                        unfocusedTextColor = Color(0xFF09090B),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFFAFAFA),
                        focusedBorderColor = Color(0xFF09090B),
                        unfocusedBorderColor = Color(0xFFD4D4D8)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_menu_input"),
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
                            label = { Text(text = category, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CoffeeBrownPrimary,
                                selectedLabelColor = CoffeeOnPrimary,
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = CoffeeBrownPrimary
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("category_chip_$category")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Menu Items Grid
            if (menuItems.isEmpty()) {
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("menu_grid")
                ) {
                    items(menuItems) { menuItem ->
                        val qty = state.cartItems[menuItem] ?: 0
                        MenuItemCard(
                            menuItem = menuItem,
                            quantityInCart = qty,
                            onAdd = { onAddToCart(menuItem) },
                            onRemove = { onRemoveFromCart(menuItem) }
                        )
                    }
                }
            }
        }

        // Bottom Cart Summary Bar
        AnimatedVisibility(
            visible = totalCartItemsCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Surface(
                color = Color(0xFF09090B),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sticky_cart_bar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = state.orderNotes,
                        onValueChange = onOrderNotesChanged,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("order_notes_input"),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF18181B), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Order Notes",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (state.orderNotes.isEmpty()) {
                                        Text(
                                            text = "Notes / Special Requests (e.g. Less sugar, extra hot)",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        }
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
                                        text = "$totalCartItemsCount",
                                        color = Color(0xFF09090B),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Items In Order",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = "₹${String.format(Locale.US, "%.2f", totalAmount)}",
                                fontWeight = FontWeight.Black,
                                fontSize = 22.sp,
                                color = Color.White
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClearCart) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear Cart", tint = Color.White.copy(alpha = 0.8f))
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            Button(
                                onClick = onSubmitOrder,
                                enabled = !state.isPlacingOrder,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF09090B),
                                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                                    disabledContentColor = Color(0xFF09090B).copy(alpha = 0.6f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("submit_order_button")
                            ) {
                                if (state.isPlacingOrder) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF09090B),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Placing...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                } else {
                                    Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = null, tint = Color(0xFF09090B), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Place Order", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItemCard(
    menuItem: MenuItemEntity,
    quantityInCart: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (quantityInCart > 0) Color(0xFFF4F4F5) else Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (quantityInCart > 0) 2.dp else 1.dp,
                color = if (quantityInCart > 0) Color(0xFF09090B) else Color(0xFFE4E4E7),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("menu_card_${menuItem.name}")
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
                        text = menuItem.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF71717A)
                    )
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", menuItem.price)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = Color(0xFF09090B)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = menuItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF09090B),
                    maxLines = 2
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (quantityInCart > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF09090B)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                    }

                    Text(
                        text = "$quantityInCart",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )

                    IconButton(
                        onClick = onAdd,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("add_item_button_${menuItem.id}")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "ADD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SurfaceColor(): Color = MaterialTheme.colorScheme.surface

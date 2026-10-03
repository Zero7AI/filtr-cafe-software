package com.example.kitchen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.ui.theme.*

@Composable
fun OrderScreen(
    menuItems: List<MenuItemEntity>,
    cartItems: Map<MenuItemEntity, Int>,
    customerName: String,
    contactNumber: String,
    orderNotes: String,
    paymentMode: String,
    isPlacingOrder: Boolean,
    onAddToCart: (MenuItemEntity) -> Unit,
    onRemoveFromCart: (MenuItemEntity) -> Unit,
    onClearCart: () -> Unit,
    onCustomerNameChange: (String) -> Unit,
    onContactNumberChange: (String) -> Unit,
    onOrderNotesChange: (String) -> Unit,
    onPaymentModeChange: (String) -> Unit,
    onSubmitOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = remember(menuItems) {
        listOf("All") + menuItems.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val filteredItems = remember(menuItems, selectedCategory) {
        if (selectedCategory == "All") menuItems
        else menuItems.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val totalAmount = remember(cartItems) {
        cartItems.entries.sumOf { (item, qty) -> item.price * qty }
    }
    val totalItemCount = remember(cartItems) {
        cartItems.values.sum()
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // ==================== LEFT PANE: MENU (65%) ====================
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) ShadcnBlack else ShadcnWhite,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) ShadcnBlack else ShadcnBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = category }
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) ShadcnWhite else ShadcnBlack,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Menu Items Grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val currentQty = cartItems[item] ?: 0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAddToCart(item) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = ShadcnWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = ShadcnBlack,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "₹${item.price.toInt()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = StatusGreen
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (currentQty > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledIconButton(
                                        onClick = { onRemoveFromCart(item) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = ShadcnMutedBg,
                                            contentColor = ShadcnBlack
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, "Remove", modifier = Modifier.size(16.dp))
                                    }

                                    Text(
                                        text = currentQty.toString(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = ShadcnBlack
                                    )

                                    FilledIconButton(
                                        onClick = { onAddToCart(item) },
                                        modifier = Modifier.size(32.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = ShadcnBlack,
                                            contentColor = ShadcnWhite
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, "Add", modifier = Modifier.size(16.dp))
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onAddToCart(item) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ShadcnBlack,
                                        contentColor = ShadcnWhite
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = "+ ADD",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vertical Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ShadcnBorder)
        )

        // ==================== RIGHT PANE: CART & CHECKOUT (35%) ====================
        Surface(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight(),
            color = ShadcnWhite,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cart Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Cart",
                            tint = ShadcnBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CART ($totalItemCount)",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ShadcnBlack
                        )
                    }

                    if (cartItems.isNotEmpty()) {
                        TextButton(
                            onClick = onClearCart,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Clear",
                                color = StatusRed,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = ShadcnBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Details & Items
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Customer Name Input
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = onCustomerNameChange,
                        label = { Text("Customer Name", fontSize = 12.sp) },
                        placeholder = { Text("Walk-in Customer", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShadcnBlack,
                            unfocusedBorderColor = ShadcnBorder
                        )
                    )

                    // Contact Phone Input
                    OutlinedTextField(
                        value = contactNumber,
                        onValueChange = onContactNumberChange,
                        label = { Text("Phone Number (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 9876543210", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShadcnBlack,
                            unfocusedBorderColor = ShadcnBorder
                        )
                    )

                    // Notes Input
                    OutlinedTextField(
                        value = orderNotes,
                        onValueChange = onOrderNotesChange,
                        label = { Text("Kitchen Notes", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Less sugar, extra crispy", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ShadcnBlack,
                            unfocusedBorderColor = ShadcnBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ORDER ITEMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = ShadcnMutedText
                    )

                    if (cartItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cart is empty.\nTap items on the left to add.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = ShadcnMutedText,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        cartItems.forEach { (item, qty) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = ShadcnBlack
                                    )
                                    Text(
                                        text = "$qty x ₹${item.price.toInt()} = ₹${(qty * item.price).toInt()}",
                                        fontSize = 12.sp,
                                        color = ShadcnMutedText
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FilledIconButton(
                                        onClick = { onRemoveFromCart(item) },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = ShadcnMutedBg,
                                            contentColor = ShadcnBlack
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, "Remove", modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = qty.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FilledIconButton(
                                        onClick = { onAddToCart(item) },
                                        modifier = Modifier.size(26.dp),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = ShadcnBlack,
                                            contentColor = ShadcnWhite
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, "Add", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = ShadcnBorder)
                Spacer(modifier = Modifier.height(10.dp))

                // Payment Mode Selector (CASH / UPI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCash = paymentMode == "CASH"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPaymentModeChange("CASH") },
                        color = if (isCash) ShadcnBlack else ShadcnMutedBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "💵 CASH",
                            color = if (isCash) ShadcnWhite else ShadcnBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    val isUpi = paymentMode == "UPI"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPaymentModeChange("UPI") },
                        color = if (isUpi) ShadcnBlack else ShadcnMutedBg,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📱 UPI",
                            color = if (isUpi) ShadcnWhite else ShadcnBlack,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 10.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Total & Place Order Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ShadcnMutedText)
                        Text(
                            text = "₹${totalAmount.toInt()}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = ShadcnBlack
                        )
                    }

                    Button(
                        onClick = onSubmitOrder,
                        enabled = cartItems.isNotEmpty() && !isPlacingOrder,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        if (isPlacingOrder) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "PLACE ORDER ✓",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.example.kitchen.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kitchen.data.entity.OrderWithItems
import com.example.kitchen.ui.components.KanbanOrderCard
import com.example.kitchen.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

enum class KdsSubTab {
    PENDING,
    COMPLETED
}

@Composable
fun KitchenKdsScreen(
    pendingOrders: List<OrderWithItems>,
    completedTodayOrders: List<OrderWithItems>,
    onMarkReady: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableStateOf(KdsSubTab.PENDING) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreamBackground)
    ) {
        // Sub-Header with Filter Switcher
        Surface(
            color = ShadcnWhite,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pending Tab Button
                    FilterChip(
                        selected = selectedSubTab == KdsSubTab.PENDING,
                        onClick = { selectedSubTab = KdsSubTab.PENDING },
                        label = {
                            Text(
                                text = "🔥 ACTIVE PENDING (${pendingOrders.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShadcnBlack,
                            selectedLabelColor = ShadcnWhite,
                            containerColor = ShadcnMutedBg,
                            labelColor = ShadcnBlack
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Completed Today Tab Button
                    FilterChip(
                        selected = selectedSubTab == KdsSubTab.COMPLETED,
                        onClick = { selectedSubTab = KdsSubTab.COMPLETED },
                        label = {
                            Text(
                                text = "✅ COMPLETED TODAY (${completedTodayOrders.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ShadcnBlack,
                            selectedLabelColor = ShadcnWhite,
                            containerColor = ShadcnMutedBg,
                            labelColor = ShadcnBlack
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Right: Helper instruction
                Text(
                    text = if (selectedSubTab == KdsSubTab.PENDING)
                        "👆 Touch any order card to mark it READY"
                    else
                        "Orders finished today in the kitchen",
                    color = ShadcnMutedText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Content Area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedSubTab) {
                KdsSubTab.PENDING -> {
                    if (pendingOrders.isEmpty()) {
                        // Empty State
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "No pending orders",
                                tint = StatusGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "All Caught Up! 🎉",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShadcnBlack
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No pending orders in the kitchen. New orders will appear here automatically.",
                                fontSize = 15.sp,
                                color = ShadcnMutedText
                            )
                        }
                    } else {
                        // Kanban Grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = pendingOrders,
                                key = { it.order.orderId }
                            ) { orderWithItems ->
                                KanbanOrderCard(
                                    orderWithItems = orderWithItems,
                                    onMarkReady = onMarkReady
                                )
                            }
                        }
                    }
                }

                KdsSubTab.COMPLETED -> {
                    if (completedTodayOrders.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = "No completed orders",
                                tint = ShadcnMutedText,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Completed Orders Today Yet",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ShadcnMutedText
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = completedTodayOrders,
                                key = { it.order.orderId }
                            ) { orderWithItems ->
                                CompletedOrderCard(orderWithItems = orderWithItems)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedOrderCard(
    orderWithItems: OrderWithItems,
    modifier: Modifier = Modifier
) {
    val order = orderWithItems.order
    val items = orderWithItems.items
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val completedTimeStr = if (order.completedTimestamp > 0) sdf.format(Date(order.completedTimestamp)) else ""

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ShadcnWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displaySeq = if (order.orderId.contains("-")) {
                    "#" + order.orderId.substringAfterLast("-")
                } else {
                    "#" + order.orderId
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displaySeq,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ShadcnBlack
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = order.customerName,
                        fontSize = 14.sp,
                        color = ShadcnMutedText
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = if (completedTimeStr.isNotBlank()) "Ready at $completedTimeStr ✓" else "Ready ✓",
                        color = StatusGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = ShadcnBorder, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { item ->
                Text(
                    text = "${item.quantity}x ${item.itemName}",
                    fontSize = 14.sp,
                    color = Color(0xFF3F3F46)
                )
            }
        }
    }
}

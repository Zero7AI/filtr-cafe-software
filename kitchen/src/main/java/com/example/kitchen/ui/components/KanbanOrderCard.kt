package com.example.kitchen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
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
import com.example.kitchen.data.entity.OrderWithItems
import com.example.kitchen.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun KanbanOrderCard(
    orderWithItems: OrderWithItems,
    onMarkReady: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val order = orderWithItems.order
    val items = orderWithItems.items

    // Calculate wait time dynamically
    var elapsedSeconds by remember(order.timestamp) {
        mutableStateOf(((System.currentTimeMillis() - order.timestamp) / 1000).coerceAtLeast(0))
    }

    LaunchedEffect(order.timestamp) {
        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - order.timestamp) / 1000).coerceAtLeast(0)
            delay(1000L)
        }
    }

    val elapsedMinutes = elapsedSeconds / 60
    val elapsedSecRemainder = elapsedSeconds % 60
    val timeFormatted = if (elapsedMinutes > 0) "${elapsedMinutes}m ${elapsedSecRemainder}s" else "${elapsedSecRemainder}s"

    // Urgency coloring based on wait time
    val (cardBg, cardBorder, timerColor) = when {
        elapsedMinutes >= 6 -> Triple(UrgencyUrgent, UrgencyBorderUrgent, StatusRed)
        elapsedMinutes >= 3 -> Triple(UrgencyWarning, UrgencyBorderWarning, StatusAmber)
        else -> Triple(ShadcnWhite, ShadcnBorder, StatusGreen)
    }

    var isRemoving by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isRemoving,
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    isRemoving = true
                    onMarkReady(order.orderId)
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = BorderStroke(1.5.dp, cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top Row: Order ID + Wait Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Order Sequence Badge (e.g. #1005)
                    val displaySeq = if (order.orderId.contains("-")) {
                        "#" + order.orderId.substringAfterLast("-")
                    } else {
                        "#" + order.orderId
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ShadcnBlack
                        ) {
                            Text(
                                text = displaySeq,
                                color = ShadcnWhite,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (order.customerName.isNotBlank() && order.customerName != "Walk-in Customer") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = order.customerName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = ShadcnBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Elapsed Timer Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = timerColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, timerColor.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Wait time",
                                tint = timerColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timeFormatted,
                                color = timerColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ShadcnBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Items List (Large & Clear for Kitchen)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quantity Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF27272A),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "${item.quantity}x",
                                    color = ShadcnWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }

                            // Item Name
                            Text(
                                text = item.itemName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                color = ShadcnBlack,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Customer Notes (if any)
                if (order.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: ${order.notes}",
                            color = Color(0xFF92400E),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Big One-Tap Ready Action Button
                Button(
                    onClick = {
                        isRemoving = true
                        onMarkReady(order.orderId)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusGreen,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Mark Ready",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TOUCH TO MARK READY ✓",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

package com.example.kitchen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kitchen.ui.KitchenTab
import com.example.kitchen.ui.theme.*
import com.example.kitchen.util.CloudConnectionStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun KitchenTopBar(
    currentTab: KitchenTab,
    onTabSelected: (KitchenTab) -> Unit,
    connectionStatus: CloudConnectionStatus,
    pendingOrderCount: Int,
    onOpenSettings: () -> Unit
) {
    var currentTimeStr by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        while (true) {
            currentTimeStr = sdf.format(Date())
            delay(1000L)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Prominent Alert Banner when NOT connected
        AnimatedVisibility(
            visible = connectionStatus != CloudConnectionStatus.CONNECTED,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StatusRed)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (connectionStatus == CloudConnectionStatus.DISCONNECTED)
                        "⚠️ NOT CONNECTED TO INTERNET — Orders saved locally and will sync once connected"
                    else
                        "⚠️ NOT CONNECTED TO CLOUD — Supabase unreachable. Retrying connection...",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Main Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ShadcnBlack,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Branding & Connection Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FILTR",
                        color = ShadcnWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KITCHEN",
                        color = Color(0xFFA1A1AA),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(20.dp))

                    // Live Connection Indicator Pill
                    val (badgeBg, badgeText, badgeIcon) = when (connectionStatus) {
                        CloudConnectionStatus.CONNECTED -> Triple(
                            Color(0xFF14532D),
                            "Cloud Connected",
                            Icons.Default.CloudDone
                        )
                        CloudConnectionStatus.CLOUD_OFFLINE -> Triple(
                            Color(0xFF7F1D1D),
                            "Cloud Offline",
                            Icons.Default.CloudOff
                        )
                        CloudConnectionStatus.DISCONNECTED -> Triple(
                            Color(0xFF7F1D1D),
                            "No Network",
                            Icons.Default.CloudOff
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(badgeBg)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionStatus == CloudConnectionStatus.CONNECTED)
                                        Color(0xFF4ADE80)
                                    else Color(0xFFF87171)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Center: Big Navigation Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF27272A))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Kitchen Tab
                    val isKitchenActive = currentTab == KitchenTab.KITCHEN
                    Button(
                        onClick = { onTabSelected(KitchenTab.KITCHEN) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isKitchenActive) ShadcnWhite else Color.Transparent,
                            contentColor = if (isKitchenActive) ShadcnBlack else Color(0xFFA1A1AA)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OutdoorGrill,
                            contentDescription = "Kitchen",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "KITCHEN KDS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (pendingOrderCount > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isKitchenActive) StatusAmber else Color(0xFFD97706))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = pendingOrderCount.toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // Order Taking Tab
                    val isOrderActive = currentTab == KitchenTab.ORDER
                    Button(
                        onClick = { onTabSelected(KitchenTab.ORDER) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isOrderActive) ShadcnWhite else Color.Transparent,
                            contentColor = if (isOrderActive) ShadcnBlack else Color(0xFFA1A1AA)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PointOfSale,
                            contentDescription = "Order",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NEW ORDER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Right: Clock & Settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentTimeStr,
                        color = ShadcnWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFFA1A1AA)
                        )
                    }
                }
            }
        }
    }
}

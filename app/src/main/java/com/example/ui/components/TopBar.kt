package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeOnPrimary
import com.example.ui.theme.CreamBackground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import com.example.ui.FiltrTab

@Composable
fun FiltrTopBar(
    currentTab: FiltrTab = FiltrTab.BILLING,
    hasSheetsWebhook: Boolean,
    isFirestoreConnected: Boolean = false,
    isSupabaseConnected: Boolean = false,
    isNetworkAvailable: Boolean = true,
    isStaffMode: Boolean = true,
    onSelectTab: (FiltrTab) -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    onOpenDrawer: (() -> Unit)? = null
) {
    Surface(
        color = Color(0xFF09090B), // Pure dark header
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("filtr_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (onOpenDrawer != null && !isStaffMode) Modifier.clickable { onOpenDrawer() } else Modifier
            ) {
                if (onOpenDrawer != null && !isStaffMode) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Drawer Navigation",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Coffee,
                        contentDescription = "FILTR Logo",
                        tint = Color(0xFF09090B),
                        modifier = Modifier
                            .padding(6.dp)
                            .size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "FILTR",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                    Text(
                        text = "POS",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                // Upper Navigation Bar Action Items: Item Master & Dashboard (Hidden in Staff Mode)
                if (!isStaffMode) {
                    IconButton(
                        onClick = { onSelectTab(FiltrTab.MENU) },
                        modifier = Modifier.testTag("top_nav_item_master")
                    ) {
                        Surface(
                            color = if (currentTab == FiltrTab.MENU) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Item Master",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onSelectTab(FiltrTab.DASHBOARD) },
                        modifier = Modifier.testTag("top_nav_dashboard")
                    ) {
                        Surface(
                            color = if (currentTab == FiltrTab.DASHBOARD) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = "Dashboard",
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                    }
                }

                // Cloud DB Status Indicator
                val cloudConnected = isSupabaseConnected || isFirestoreConnected
                val cloudLabel = if (isSupabaseConnected) "Supabase" else if (isFirestoreConnected) "Firebase" else "Local"
                val cloudColor = if (isSupabaseConnected) Color(0xFF10B981) else if (isFirestoreConnected) Color(0xFF1565C0) else Color(0xFF9E9E9E)
                Surface(
                    color = cloudColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .then(
                            if (onOpenSettings != null && !isStaffMode) Modifier.clickable { onOpenSettings() } else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cloudConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "Cloud Status",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = cloudLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Google Sheets Status Indicator
                Surface(
                    color = if (hasSheetsWebhook) Color(0xFF15803D) else Color(0xFFB91C1C),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .then(
                            if (onOpenSettings != null && !isStaffMode) Modifier.clickable { onOpenSettings() } else Modifier
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasSheetsWebhook) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = "Sheets Status",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (hasSheetsWebhook) "Sheets" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Network connectivity indicator
                if (!isNetworkAvailable) {
                    Surface(
                        color = Color(0xFFDC2626),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "No Internet",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "No Internet",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!isStaffMode && onOpenSettings != null) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

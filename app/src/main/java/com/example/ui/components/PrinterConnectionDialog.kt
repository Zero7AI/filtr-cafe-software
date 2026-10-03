package com.example.ui.components

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ThermalPrinterService
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary

@Composable
fun PrinterConnectionDialog(
    printerService: ThermalPrinterService,
    printers: List<UsbDevice>,
    isConnected: Boolean,
    connectedPrinterName: String?,
    isPermissionPending: Boolean,
    onConnectDevice: (UsbDevice) -> Unit,
    onDisconnect: () -> Unit,
    onTestPrint: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        printerService.refreshPrinters()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🖨️", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thermal Printer (USB OTG)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = CoffeeBrownPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { printerService.refreshPrinters() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Printers",
                        tint = CoffeeBrownPrimary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Currently connected banner
                if (isConnected) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Ready to Print",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF16A34A)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = connectedPrinterName ?: "Connected Printer",
                                fontSize = 12.sp,
                                color = Color(0xFF15803D),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onTestPrint,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Test Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onDisconnect,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Disconnect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Permission Pending Indicator
                if (isPermissionPending) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberAccentSecondary.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = CoffeeBrownPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Requesting USB Permission...",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CoffeeBrownPrimary
                                )
                                Text(
                                    text = "Tap 'OK' on the system dialog to allow access.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF71717A)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (printers.isEmpty()) {
                    // Empty state with clear OTG troubleshooting steps
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("🔌", fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No USB Printers Detected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Please follow these 3 steps:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CoffeeBrownPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "1. Plug printer into phone with OTG adapter.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "2. Turn printer power switch ON.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Text(
                                    text = "3. Turn ON 'OTG Connection' in phone Settings (OnePlus, Oppo, Realme, Vivo, Xiaomi turn OTG off by default).",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { printerService.refreshPrinters() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Scan Again", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Open Settings", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Discovered Devices (${printers.size}):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    printers.forEach { device ->
                        val displayName = remember(device) { printerService.getDeviceDisplayName(device) }
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onConnectDevice(device) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🖨️", fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "VID: ${device.vendorId}  PID: ${device.productId}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Button(
                                    onClick = { onConnectDevice(device) },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    enabled = !isPermissionPending
                                ) {
                                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF09090B), fontWeight = FontWeight.Bold)
            }
        }
    )
}

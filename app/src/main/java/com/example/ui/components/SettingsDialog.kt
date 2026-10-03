package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import com.example.BuildConfig
import com.example.data.remote.GoogleSheetsSyncService
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary

@Composable
fun SettingsDialog(
    currentWebhookUrl: String,
    isFirestoreConnected: Boolean = false,
    isSupabaseConnected: Boolean = false,
    isPrinterConnected: Boolean = false,
    printerName: String? = null,
    appVersionName: String = BuildConfig.VERSION_NAME,
    appVersionCode: Int = BuildConfig.VERSION_CODE,
    isCheckingForUpdate: Boolean = false,
    onCheckForUpdates: () -> Unit = {},
    onManagePrinter: (() -> Unit)? = null,
    onNavigateToReceiptDesign: (() -> Unit)? = null,
    onSaveWebhookUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var webhookUrlText by remember { mutableStateOf(currentWebhookUrl) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = CoffeeBrownPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Sync & Database Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CoffeeBrownPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("settings_dialog")
            ) {
                // Cloud Database Status Card
                val cloudConnected = isSupabaseConnected || isFirestoreConnected
                val primaryLabel = if (isSupabaseConnected) "Supabase (Primary)" else "Firebase Firestore"
                val primaryColor = if (isSupabaseConnected) Color(0xFF10B981) else if (isFirestoreConnected) Color(0xFF1565C0) else Color(0xFF9E9E9E)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = primaryColor.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (cloudConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = primaryColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = primaryLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                text = if (isSupabaseConnected)
                                    "Connected — Real-time multi-device sync active (PostgreSQL)"
                                else if (isFirestoreConnected)
                                    "Connected — Firestore fallback active"
                                else
                                    "Not connected — Running in offline mode",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Thermal Printer Status Card
                val printerColor = if (isPrinterConnected) Color(0xFF10B981) else Color(0xFF71717A)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = printerColor.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🖨️", fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Thermal Printer (USB OTG)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = printerColor
                            )
                            Text(
                                text = if (isPrinterConnected)
                                    "Connected: ${printerName ?: "Ready"}"
                                else
                                    "Disconnected — Tap to connect via USB OTG",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onManagePrinter != null) {
                            OutlinedButton(
                                onClick = onManagePrinter,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(if (isPrinterConnected) "Manage" else "Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Bill & Receipt Design Shortcut Card
                if (onNavigateToReceiptDesign != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F6F0)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2D9CF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToReceiptDesign.invoke() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎨", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Bill & Receipt Design",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = CoffeeBrownPrimary
                                    )
                                    Text(
                                        "Customize header, story photos, tagline & footers",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Text(
                                "Open →",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AmberAccentSecondary
                            )
                        }
                    }
                }

                // App Version & In-App OTA Updates Card
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = CoffeeBrownPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "FILTR Billing v$appVersionName",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CoffeeBrownPrimary
                                )
                                Text(
                                    text = "Build $appVersionCode • Automatic Cloud Updates",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onCheckForUpdates,
                            enabled = !isCheckingForUpdate,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            if (isCheckingForUpdate) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = CoffeeBrownPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking...", fontSize = 11.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Updates", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Sheets Section (Analytics Export)
                Text(
                    text = "Google Sheets Analytics Export",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CoffeeBrownPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Connect Google Sheets as your analytics & reporting database using a Webhook URL:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = webhookUrlText,
                    onValueChange = { webhookUrlText = it },
                    label = { Text("Google Apps Script Webhook URL") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("webhook_url_input"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberAccentSecondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "How to setup Google Sheets (1 minute):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "1. Open Google Sheets -> Extensions -> Apps Script\n2. Copy & paste the script below\n3. Click Deploy -> Web app -> Access: Anyone",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AppsScript", GoogleSheetsSyncService.GOOGLE_APPS_SCRIPT_TEMPLATE)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Google Apps Script code copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Google Apps Script Code", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveWebhookUrl(webhookUrlText) },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Webhook")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoffeeBrownPrimary)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

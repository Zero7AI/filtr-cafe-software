package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.SupabaseService
import com.example.ui.theme.ShadcnBlack
import com.example.ui.theme.ShadcnBorder
import com.example.ui.theme.ShadcnMutedText
import com.example.ui.theme.ShadcnWhite
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceAccessGateDialog(
    isStaffApp: Boolean,
    accessStatus: String, // "UNVERIFIED", "PENDING", "REVOKED"
    savedOperatorName: String,
    deviceModel: String,
    errorMessage: String,
    isVerifying: Boolean,
    onVerify: (operatorName: String, passcode: String) -> Unit,
    onRefreshStatus: () -> Unit,
    onExitApp: () -> Unit
) {
    var operatorName by remember(savedOperatorName) { mutableStateOf(savedOperatorName) }
    var passcode by remember { mutableStateOf("") }
    var showPasscode by remember { mutableStateOf(false) }

    val isRevoked = accessStatus == "REVOKED"
    val isPending = accessStatus == "PENDING"

    AlertDialog(
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        onDismissRequest = { /* Mandatory gate cannot be dismissed */ },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isRevoked -> Color(0xFFFEE2E2)
                            isPending -> Color(0xFFFEF3C7)
                            else -> Color(0xFFF4F4F5)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isRevoked -> Icons.Default.Block
                        isPending -> Icons.Default.Schedule
                        else -> Icons.Default.Lock
                    },
                    contentDescription = null,
                    tint = when {
                        isRevoked -> Color(0xFFDC2626)
                        isPending -> StatusAmber
                        else -> ShadcnBlack
                    },
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = when {
                    isRevoked -> "Access Revoked ⚠️"
                    isPending -> "Approval Pending"
                    isStaffApp -> "FILTR Staff Activation"
                    else -> "FILTR Admin Verification"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isRevoked) Color(0xFFDC2626) else ShadcnBlack
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFFF4F4F5),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Device: $deviceModel (${if (isStaffApp) "Staff App" else "Admin Billing"})",
                        fontSize = 11.sp,
                        color = ShadcnMutedText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                when {
                    isRevoked -> {
                        Text(
                            text = "This device's access has been revoked by the Store Owner. Please contact the owner or switch to the authorized Staff app.",
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                    isPending -> {
                        Text(
                            text = "Device registered as \"${operatorName.ifBlank { deviceModel }}\". Ask the Store Owner to approve this device under Active Devices.",
                            fontSize = 13.sp,
                            color = ShadcnBlack
                        )
                    }
                    else -> {
                        Text(
                            text = if (isStaffApp)
                                "Enter your name and store staff passcode once to link this phone."
                            else
                                "Enter your name and Admin passcode to register and unlock this device.",
                            fontSize = 12.sp,
                            color = ShadcnMutedText
                        )

                        OutlinedTextField(
                            value = operatorName,
                            onValueChange = { operatorName = it },
                            label = { Text("Your Name / Counter Label") },
                            placeholder = { Text(if (isStaffApp) "e.g. Counter 1" else "e.g. Somesh") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = passcode,
                            onValueChange = { passcode = it },
                            label = { Text(if (isStaffApp) "Store Staff Passcode" else "Admin Passcode") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showPasscode = !showPasscode }) {
                                    Icon(
                                        imageVector = if (showPasscode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle passcode visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPasscode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        },
        confirmButton = {
            if (isRevoked || isPending) {
                Button(
                    onClick = onRefreshStatus,
                    enabled = !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ShadcnWhite, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Check Status")
                }
            } else {
                Button(
                    onClick = { onVerify(operatorName.trim(), passcode.trim()) },
                    enabled = operatorName.isNotBlank() && passcode.isNotBlank() && !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ShadcnWhite, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Verify & Unlock", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onExitApp) {
                Text("Exit App", color = Color.Gray)
            }
        },
        containerColor = ShadcnWhite,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun ActiveDevicesDialog(
    devices: List<SupabaseService.DeviceRegistration>,
    currentDeviceId: String,
    accessPolicy: SupabaseService.AccessPolicyConfig,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onUpdateDeviceStatus: (registryKey: String, newStatus: String) -> Unit,
    onSavePolicy: (adminPasscode: String, staffPasscode: String, requireApproval: Boolean, forceReverifyAll: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var showPolicyEditor by remember { mutableStateOf(false) }
    var adminCode by remember(accessPolicy) { mutableStateOf(accessPolicy.adminPasscode) }
    var staffCode by remember(accessPolicy) { mutableStateOf(accessPolicy.staffPasscode) }
    var requireApproval by remember(accessPolicy) { mutableStateOf(accessPolicy.requireOwnerApprovalForAdmin) }
    var forceReverify by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Devices, contentDescription = null, tint = ShadcnBlack, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Devices & Access (${devices.size})", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = ShadcnBlack)
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ShadcnBlack)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ShadcnBlack, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showPolicyEditor) "Passcode & Approval Policy" else "All tracked Admin & Staff mobiles",
                        fontSize = 12.sp,
                        color = ShadcnMutedText
                    )
                    TextButton(
                        onClick = { showPolicyEditor = !showPolicyEditor },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (showPolicyEditor) "View Devices" else "Passcodes", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (showPolicyEditor) {
                    OutlinedTextField(
                        value = adminCode,
                        onValueChange = { adminCode = it },
                        label = { Text("Admin Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = staffCode,
                        onValueChange = { staffCode = it },
                        label = { Text("Staff Store Passcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Require Owner approval for new Admin phones", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = requireApproval, onCheckedChange = { requireApproval = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Force all devices to re-enter new passcode", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(checked = forceReverify, onCheckedChange = { forceReverify = it })
                    }
                    Button(
                        onClick = {
                            onSavePolicy(adminCode.trim(), staffCode.trim(), requireApproval, forceReverify)
                            showPolicyEditor = false
                        },
                        enabled = adminCode.isNotBlank() && staffCode.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Access Policy", fontWeight = FontWeight.Bold)
                    }
                } else {
                    if (devices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No registered devices found yet.", fontSize = 13.sp, color = ShadcnMutedText)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(devices, key = { it.registryKey }) { dev ->
                                val isCurrent = dev.deviceId == currentDeviceId && dev.appRole == "ADMIN"
                                DeviceRowCard(
                                    device = dev,
                                    isCurrentDevice = isCurrent,
                                    onRevoke = { onUpdateDeviceStatus(dev.registryKey, "REVOKED") },
                                    onApprove = { onUpdateDeviceStatus(dev.registryKey, "ACTIVE") }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Close", color = ShadcnBlack)
            }
        },
        containerColor = ShadcnWhite,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun DeviceRowCard(
    device: SupabaseService.DeviceRegistration,
    isCurrentDevice: Boolean,
    onRevoke: () -> Unit,
    onApprove: () -> Unit
) {
    val isAdmin = device.appRole.equals("ADMIN", ignoreCase = true)
    val lastSeenStr = remember(device.lastSeenMs) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(device.lastSeenMs))
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        border = BorderStroke(1.dp, ShadcnBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isAdmin) ShadcnBlack else StatusGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = device.operatorName + if (isCurrentDevice) " (This Phone)" else "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = ShadcnBlack
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isAdmin) ShadcnBlack else Color(0xFFE0F2FE)
                ) {
                    Text(
                        text = if (isAdmin) "ADMIN" else "STAFF",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAdmin) ShadcnWhite else Color(0xFF0369A1),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${device.deviceModel} • v${device.appVersionName} (code ${device.appVersionCode})",
                fontSize = 11.sp,
                color = ShadcnMutedText
            )
            Text(
                text = "Last active: $lastSeenStr",
                fontSize = 11.sp,
                color = ShadcnMutedText
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ShadcnBorder)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (badgeBg, badgeFg, badgeLabel) = when (device.status) {
                    "REVOKED" -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "REVOKED")
                    "PENDING" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "PENDING")
                    else -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "ACTIVE")
                }

                Surface(shape = RoundedCornerShape(12.dp), color = badgeBg) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (device.status == "ACTIVE") Icons.Default.CheckCircle else Icons.Default.Block,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(badgeLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = badgeFg)
                    }
                }

                if (!isCurrentDevice) {
                    if (device.status == "ACTIVE") {
                        OutlinedButton(
                            onClick = onRevoke,
                            border = BorderStroke(1.dp, Color(0xFFEF4444)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Revoke Access", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                        }
                    } else {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (device.status == "PENDING") "Approve" else "Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

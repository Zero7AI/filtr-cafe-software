package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
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
                    else -> "FILTR Device Access"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isRevoked) Color(0xFFDC2626) else ShadcnBlack
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = Color(0xFFF4F4F5),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Device: $deviceModel",
                        fontSize = 11.sp,
                        color = ShadcnMutedText,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                when {
                    isRevoked -> {
                        Text(
                            text = "Full app access (Staff & Admin) has been revoked by the Store Owner. Ask the Owner to tap Restore under Devices & Access Control, then tap Check Status.",
                            fontSize = 13.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                    isPending -> {
                        Text(
                            text = "Device registered as \"${operatorName.ifBlank { deviceModel }}\". Ask the Store Owner to approve this device under Devices & Access Control.",
                            fontSize = 13.sp,
                            color = ShadcnBlack
                        )
                    }
                    else -> {
                        Text(
                            text = "Enter your Operator Name and Passcode (Staff PIN or Admin Passcode) to activate this device.",
                            fontSize = 12.sp,
                            color = ShadcnMutedText
                        )

                        OutlinedTextField(
                            value = operatorName,
                            onValueChange = { operatorName = it },
                            label = { Text("Operator Name / Counter Label") },
                            placeholder = { Text("e.g. Counter 1 or Somesh") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = passcode,
                            onValueChange = { passcode = it },
                            label = { Text("Passcode (Staff PIN or Admin Code)") },
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
fun AdminUnlockDialog(
    onDismiss: () -> Unit,
    onUnlock: (adminPasscode: String, onResult: (success: Boolean, message: String) -> Unit) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var showPasscode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF4F4F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = ShadcnBlack,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Switch to Admin Mode",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = ShadcnBlack
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enter the Admin Passcode to unlock administrative features (Full Drawer, Billing Settings, Menu & Stock Controls, Device Approvals).",
                    fontSize = 12.sp,
                    color = ShadcnMutedText
                )

                OutlinedTextField(
                    value = passcode,
                    onValueChange = {
                        passcode = it
                        errorMessage = ""
                    },
                    enabled = !isChecking,
                    label = { Text("Admin Passcode") },
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
            Button(
                onClick = {
                    isChecking = true
                    errorMessage = ""
                    onUnlock(passcode.trim()) { success, message ->
                        isChecking = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = message.ifBlank { "Incorrect Admin Passcode" }
                        }
                    }
                },
                enabled = !isChecking && passcode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ShadcnWhite
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isChecking) "Verifying..." else "Unlock Admin", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isChecking) {
                Text("Cancel", color = Color.Gray)
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
    onUpdateDeviceRole: (registryKey: String, newRole: String) -> Unit,
    onSavePolicy: (adminPasscode: String, staffPasscode: String, requireApproval: Boolean, forceReverifyAll: Boolean) -> Unit,
    onDismiss: () -> Unit,
    staffRoster: List<StaffRosterEntity> = emptyList(),
    onLinkDeviceToMember: (registryKey: String, member: StaffRosterEntity) -> Unit = { _, _ -> },
    onCreateAndLinkMember: (registryKey: String, name: String) -> Unit = { _, _ -> }
) {
    var activeSection by remember { mutableStateOf("BY_TEAM") } // BY_TEAM, ALL_DEVICES, PASSCODES
    var devicePendingRevoke by remember { mutableStateOf<SupabaseService.DeviceRegistration?>(null) }
    var devicePendingMemberLink by remember { mutableStateOf<SupabaseService.DeviceRegistration?>(null) }
    var adminCode by remember { mutableStateOf(accessPolicy.adminPasscode) }
    var staffCode by remember { mutableStateOf(accessPolicy.staffPasscode) }
    var requireApproval by remember { mutableStateOf(accessPolicy.requireOwnerApprovalForAdmin) }
    var forceReverify by remember { mutableStateOf(false) }
    var showAdminCode by remember { mutableStateOf(false) }
    var showStaffCode by remember { mutableStateOf(false) }

    LaunchedEffect(accessPolicy, activeSection) {
        if (activeSection != "PASSCODES") {
            adminCode = accessPolicy.adminPasscode
            staffCode = accessPolicy.staffPasscode
            requireApproval = accessPolicy.requireOwnerApprovalForAdmin
        }
    }

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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "BY_TEAM" to "By Team",
                        "ALL_DEVICES" to "All (${devices.size})",
                        "PASSCODES" to "Passcodes"
                    ).forEach { (key, label) ->
                        val selected = activeSection == key
                        Surface(
                            color = if (selected) ShadcnBlack else Color(0xFFF4F4F5),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeSection = key }
                        ) {
                            Text(
                                text = label,
                                color = if (selected) ShadcnWhite else ShadcnBlack,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp)
                            )
                        }
                    }
                }

                if (activeSection == "PASSCODES") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .imePadding(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = adminCode,
                            onValueChange = { adminCode = it },
                            label = { Text("Admin Passcode") },
                            trailingIcon = {
                                IconButton(onClick = { showAdminCode = !showAdminCode }) {
                                    Icon(
                                        imageVector = if (showAdminCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle admin passcode visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showAdminCode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = staffCode,
                            onValueChange = { staffCode = it },
                            label = { Text("Staff Store Passcode") },
                            trailingIcon = {
                                IconButton(onClick = { showStaffCode = !showStaffCode }) {
                                    Icon(
                                        imageVector = if (showStaffCode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle staff passcode visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showStaffCode) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                                activeSection = "ALL_DEVICES"
                            },
                            enabled = adminCode.isNotBlank() && staffCode.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Access Policy", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (activeSection == "BY_TEAM") {
                    val staffOnlyRoster = remember(staffRoster) {
                        staffRoster.filter { it.isActive && !it.orgRole.equals("ADMIN", ignoreCase = true) }
                    }
                    val adminDevices = remember(devices) {
                        devices.filter { it.appRole.equals("ADMIN", ignoreCase = true) }
                    }
                    val staffDevices = remember(devices) {
                        devices.filter { !it.appRole.equals("ADMIN", ignoreCase = true) }
                    }
                    Text(
                        text = "Staff Roster phones auto-track attendance & stay locked to Staff Mode.",
                        fontSize = 11.sp,
                        color = ShadcnMutedText
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "hdr_admins") {
                            Text(
                                text = "ADMIN LOGINS (${adminDevices.size} devices)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = ShadcnBlack
                            )
                        }
                        adminDevices.groupBy {
                            it.linkedMemberName.ifBlank { it.operatorName.ifBlank { "Admin" } }
                                .lowercase()
                        }.forEach { (key, devs) ->
                            item(key = "adm_$key") {
                                val displayName = devs.first().linkedMemberName.ifBlank { devs.first().operatorName }
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
                                    border = BorderStroke(1.dp, ShadcnBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("$displayName (${devs.size} login${if (devs.size > 1) "s" else ""})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShadcnBlack)
                                            Surface(color = ShadcnBlack, shape = RoundedCornerShape(6.dp)) {
                                                Text("ADMIN", color = ShadcnWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                            }
                                        }
                                        devs.forEach { d ->
                                            Text(
                                                text = "• ${d.deviceModel}${if (d.deviceId == currentDeviceId) " (This Phone)" else ""} • ${d.status}",
                                                fontSize = 11.sp,
                                                color = ShadcnMutedText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "hdr_staff") {
                            Text(
                                text = "STAFF ROSTER & DEVICES (${staffOnlyRoster.size} roster • ${staffDevices.size} phones)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0369A1),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(staffOnlyRoster, key = { "roster_${it.id}" }) { member ->
                            val memberKey = AttendanceCalculator.normalizeStaffKey(member.name)
                            val linkedDevs = devices.filter { d ->
                                d.linkedMemberId == member.id ||
                                    (d.linkedMemberId.isBlank() && AttendanceCalculator.normalizeStaffKey(d.operatorName) == memberKey)
                            }
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F9FF)),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${member.name} • ${member.roleTitle}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShadcnBlack)
                                        Text("${linkedDevs.size} device${if (linkedDevs.size != 1) "s" else ""}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0369A1))
                                    }
                                    if (linkedDevs.isEmpty()) {
                                        Text("No device linked yet", fontSize = 11.sp, color = ShadcnMutedText)
                                    } else {
                                        linkedDevs.forEach { d ->
                                            Text(
                                                text = "• ${d.deviceModel} (${d.appRole}) • ${d.status}",
                                                fontSize = 11.sp,
                                                color = ShadcnBlack
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val unassignedStaffDevs = staffDevices.filter { d ->
                            val norm = AttendanceCalculator.normalizeStaffKey(d.operatorName)
                            d.linkedMemberId.isBlank() && staffOnlyRoster.none {
                                AttendanceCalculator.normalizeStaffKey(it.name) == norm
                            }
                        }
                        if (unassignedStaffDevs.isNotEmpty()) {
                            item(key = "hdr_unlinked_staff") {
                                Text(
                                    text = "UNLINKED STAFF MOBILES (${unassignedStaffDevs.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = StatusAmber,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            items(unassignedStaffDevs, key = { "unlinked_${it.registryKey}" }) { dev ->
                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(dev.operatorName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShadcnBlack)
                                            Text(dev.deviceModel, fontSize = 11.sp, color = ShadcnMutedText)
                                        }
                                        TextButton(onClick = { onCreateAndLinkMember(dev.registryKey, dev.operatorName) }) {
                                            Text("Add to Roster", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Make Staff / Admin changes role only • Assign links to Staff Roster • Revoke locks out full app.",
                        fontSize = 11.sp,
                        color = ShadcnMutedText
                    )
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
                                val isCurrent = dev.deviceId == currentDeviceId
                                val nextRole = if (dev.appRole.equals("ADMIN", ignoreCase = true)) "STAFF" else "ADMIN"
                                DeviceRowCard(
                                    device = dev,
                                    isCurrentDevice = isCurrent,
                                    onRevoke = { devicePendingRevoke = dev },
                                    onApprove = { onUpdateDeviceStatus(dev.registryKey, "ACTIVE") },
                                    onToggleRole = { onUpdateDeviceRole(dev.registryKey, nextRole) },
                                    onAssignMember = { devicePendingMemberLink = dev }
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

    devicePendingMemberLink?.let { target ->
        val staffChoices = remember(staffRoster) {
            staffRoster.filter { it.isActive && !it.orgRole.equals("ADMIN", ignoreCase = true) }
        }
        AlertDialog(
            onDismissRequest = { devicePendingMemberLink = null },
            title = {
                Text(
                    text = "Assign Staff Member — ${target.operatorName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = ShadcnBlack
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${target.deviceModel} will sync attendance and stay locked to Staff Mode when linked to a Staff Roster member.",
                        fontSize = 12.sp,
                        color = ShadcnMutedText
                    )
                    staffChoices.forEach { member ->
                        OutlinedButton(
                            onClick = {
                                onLinkDeviceToMember(target.registryKey, member)
                                devicePendingMemberLink = null
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${member.name} (${member.roleTitle})", color = ShadcnBlack, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = {
                            onCreateAndLinkMember(target.registryKey, target.operatorName)
                            devicePendingMemberLink = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack, contentColor = ShadcnWhite),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add \"${target.operatorName}\" to Staff Roster & Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { devicePendingMemberLink = null }) {
                    Text("Cancel", color = ShadcnMutedText)
                }
            },
            containerColor = ShadcnWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }

    devicePendingRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = { devicePendingRevoke = null },
            title = {
                Text(
                    text = "Revoke Full App Access?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFFDC2626)
                )
            },
            text = {
                Text(
                    text = "This locks \"${target.operatorName}\" (${target.deviceModel}) out of BOTH Staff and Admin views until restored. To remove only Admin rights, cancel and tap \"Make Staff\" instead.",
                    fontSize = 13.sp,
                    color = ShadcnBlack
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateDeviceStatus(target.registryKey, "REVOKED")
                        devicePendingRevoke = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Revoke Access", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { devicePendingRevoke = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancel", color = ShadcnBlack)
                }
            },
            containerColor = ShadcnWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun DeviceRowCard(
    device: SupabaseService.DeviceRegistration,
    isCurrentDevice: Boolean,
    onRevoke: () -> Unit,
    onApprove: () -> Unit,
    onToggleRole: () -> Unit,
    onAssignMember: () -> Unit
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
            if (device.linkedMemberName.isNotBlank()) {
                Text(
                    text = "Linked Member: ${device.linkedMemberName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0369A1)
                )
            }

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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onAssignMember,
                            border = BorderStroke(1.dp, Color(0xFF0369A1).copy(alpha = 0.45f)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (device.linkedMemberId.isNotBlank()) "Reassign" else "Assign",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0369A1)
                            )
                        }
                        OutlinedButton(
                            onClick = onToggleRole,
                            border = BorderStroke(1.dp, ShadcnBorder),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isAdmin) "Make Staff" else "Make Admin",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ShadcnBlack
                            )
                        }
                        if (device.status == "ACTIVE") {
                            OutlinedButton(
                                onClick = onRevoke,
                                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Revoke", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        } else {
                            Button(
                                onClick = onApprove,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
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
}

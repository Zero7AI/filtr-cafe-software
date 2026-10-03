package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.UpiAccountEntity

@Composable
fun AccountsUpiDialog(
    upiAccounts: List<UpiAccountEntity>,
    onDismiss: () -> Unit,
    onAddUpiAccount: (vpa: String, payeeName: String, isDefault: Boolean) -> Unit,
    onDeleteUpiAccount: (UpiAccountEntity) -> Unit,
    onSetDefaultAccount: (accountId: Int) -> Unit
) {
    var newVpa by remember { mutableStateOf("") }
    var newPayeeName by remember { mutableStateOf("") }
    var makeDefaultCheck by remember { mutableStateOf(upiAccounts.isEmpty()) }
    var vpaError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = Color(0xFF09090B),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Accounts / UPI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF09090B)
                        )
                        Text(
                            text = "Dynamic QR Payment Accounts",
                            fontSize = 11.sp,
                            color = Color(0xFF71717A)
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF09090B))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
            ) {
                Text(
                    text = "CONFIGURED UPI ACCOUNTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF71717A),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (upiAccounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No UPI ID added yet. Add a UPI ID below to generate dynamic payment QR codes on receipts.",
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(upiAccounts) { account ->
                            Surface(
                                color = if (account.isDefault) Color(0xFFF4F4F5) else Color.White,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (account.isDefault) Color(0xFF09090B) else Color(0xFFE4E4E7)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("upi_account_item_${account.id}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = account.vpa,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF09090B)
                                            )
                                            if (account.isDefault) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFF16A34A),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "DEFAULT",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "Payee: ${account.payeeName}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF71717A)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!account.isDefault) {
                                            IconButton(
                                                onClick = { onSetDefaultAccount(account.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.StarOutline,
                                                    contentDescription = "Set Default",
                                                    tint = Color(0xFF71717A)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "Default Account",
                                                tint = Color(0xFFEAB308),
                                                modifier = Modifier.size(24.dp).padding(4.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onDeleteUpiAccount(account) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete UPI",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE4E4E7))

                // Section: Add New UPI Account
                Text(
                    text = "ADD NEW UPI ID",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF71717A),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                OutlinedTextField(
                    value = newVpa,
                    onValueChange = {
                        newVpa = it
                        vpaError = false
                    },
                    label = { Text("UPI VPA / ID (e.g. 9907839311@ybl)", fontSize = 12.sp) },
                    singleLine = true,
                    isError = vpaError,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newPayeeName,
                    onValueChange = { newPayeeName = it },
                    label = { Text("Merchant / Payee Name (e.g. FILTR Coffee)", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = makeDefaultCheck,
                        onCheckedChange = { makeDefaultCheck = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF09090B))
                    )
                    Text("Set as Default UPI ID", fontSize = 12.sp, color = Color(0xFF09090B))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        if (newVpa.isBlank() || !newVpa.contains("@")) {
                            vpaError = true
                            return@Button
                        }
                        onAddUpiAccount(newVpa.trim(), newPayeeName.trim(), makeDefaultCheck)
                        newVpa = ""
                        newPayeeName = ""
                        makeDefaultCheck = false
                        vpaError = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add UPI Account", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

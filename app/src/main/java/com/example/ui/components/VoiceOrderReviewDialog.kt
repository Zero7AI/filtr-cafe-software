package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ParsedVoiceOrder
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeePrimaryContainer

@Composable
fun VoiceOrderReviewDialog(
    parsedVoiceOrder: ParsedVoiceOrder,
    onConfirmOrder: (ParsedVoiceOrder) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Parsed Order",
                tint = AmberAccentSecondary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Review AI Voice Order",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = CoffeeBrownPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_order_review_dialog")
            ) {
                Text(
                    text = "Gemini AI extracted the following details from your voice recording:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Customer Info Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoffeePrimaryContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = CoffeeBrownPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Customer: ${parsedVoiceOrder.customerName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (parsedVoiceOrder.contactNumber.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp), tint = CoffeeBrownPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Contact: ${parsedVoiceOrder.contactNumber}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (parsedVoiceOrder.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📝 Notes: ${parsedVoiceOrder.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CoffeeBrownPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = AmberAccentSecondary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Extracted Order Items:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    items(parsedVoiceOrder.items) { item ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.menuItemName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Surface(
                                    color = AmberAccentSecondary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "x${item.quantity}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmOrder(parsedVoiceOrder) },
                colors = ButtonDefaults.buttonColors(containerColor = CoffeeBrownPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("apply_voice_order_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apply to Bill")
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

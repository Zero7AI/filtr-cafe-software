package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.OrderWithItems
import com.example.data.entity.UpiAccountEntity
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun generateUpiQrBitmap(
    vpa: String,
    payeeName: String,
    amount: Double,
    orderId: String,
    sizePx: Int = 400
): Bitmap? {
    if (vpa.isBlank()) return null
    return try {
        val encodedName = URLEncoder.encode(payeeName, "UTF-8")
        val encodedNote = URLEncoder.encode("Order $orderId", "UTF-8")
        val formattedAmount = String.format(Locale.US, "%.2f", amount)

        val upiUrl = "upi://pay?pa=$vpa&pn=$encodedName&am=$formattedAmount&cu=INR&tn=$encodedNote"

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(upiUrl, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(
                    x,
                    y,
                    if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bmp
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun OrderReceiptDialog(
    orderWithItems: OrderWithItems,
    upiAccounts: List<UpiAccountEntity> = emptyList(),
    isPrinterConnected: Boolean = false,
    printerName: String? = null,
    onPrintReceipt: (() -> Unit)? = null,
    onConnectPrinter: (() -> Unit)? = null,
    onCustomizeDesign: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val order = orderWithItems.order
    val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(order.timestamp))

    var selectedUpiAccount by remember(upiAccounts) {
        mutableStateOf(upiAccounts.firstOrNull { it.isDefault } ?: upiAccounts.firstOrNull())
    }

    val activeVpa = selectedUpiAccount?.vpa ?: ""
    val activePayeeName = selectedUpiAccount?.payeeName ?: "FILTR Coffee"

    val qrBitmap = remember(activeVpa, activePayeeName, order.totalAmount, order.orderId) {
        generateUpiQrBitmap(
            vpa = activeVpa,
            payeeName = activePayeeName,
            amount = order.totalAmount,
            orderId = order.orderId,
            sizePx = 600
        )
    }

    val upiPaymentUrl = "upi://pay?pa=${activeVpa}&pn=${URLEncoder.encode(activePayeeName, "UTF-8")}&am=${String.format(Locale.US, "%.2f", order.totalAmount)}&cu=INR&tn=${URLEncoder.encode("Order ${order.orderId}", "UTF-8")}"

    val receiptText = buildString {
        appendLine("===============================")
        appendLine("          FILTR COFFEE         ")
        appendLine("      Authentic Brew & Bites   ")
        appendLine("===============================")
        appendLine("Order ID  : ${order.orderId}")
        appendLine("Date      : $dateStr")
        appendLine("Customer  : ${order.customerName}")
        if (order.contactNumber.isNotBlank()) appendLine("Phone     : ${order.contactNumber}")
        appendLine("-------------------------------")
        // Group items by name to consolidate duplicates
        val groupedItems = orderWithItems.items.groupBy { it.itemName }.map { (name, items) ->
            Triple(name, items.sumOf { it.quantity }, items.sumOf { it.subtotal })
        }
        groupedItems.forEach { (name, qty, subtotal) ->
            appendLine("${qty}x ${name.take(18).padEnd(18)} ₹${subtotal}")
        }
        appendLine("-------------------------------")
        appendLine("TOTAL AMOUNT : ₹${order.totalAmount}")
        appendLine("Payment      : ${if (order.isPaid) "PAID" else "PENDING"}")
        appendLine("UPI Payment  : $upiPaymentUrl")
        appendLine("Status       : ${if (order.isDelivered) "DELIVERED" else "IN PROGRESS"}")
        appendLine("===============================")
        appendLine("    Thank you for visiting!    ")
        appendLine("===============================")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFF09090B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "FILTR Bill Receipt",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF09090B)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onCustomizeDesign != null) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { onCustomizeDesign.invoke() }
                                .padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🎨", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Design Bill",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF09090B))
                    }
                }
            }
        },
        text = {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F4F5)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receipt_card")
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "FILTR COFFEE",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        letterSpacing = 2.sp,
                        color = Color(0xFF09090B)
                    )
                    Text(
                        text = "Order #${order.orderId}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF18181B)
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF71717A)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE4E4E7))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Customer:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                        Text(text = order.customerName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF09090B))
                    }

                    if (order.contactNumber.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Contact:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF09090B))
                            Text(text = order.contactNumber, style = MaterialTheme.typography.bodySmall, color = Color(0xFF09090B))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE4E4E7))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Group items by name to consolidate duplicates
                        val groupedItems = orderWithItems.items.groupBy { it.itemName }.map { (name, items) ->
                            Triple(name, items.sumOf { it.quantity }, items.sumOf { it.subtotal })
                        }
                        groupedItems.forEach { (name, qty, subtotal) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${qty}x ${name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF09090B),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "₹${subtotal}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF09090B)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE4E4E7))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF09090B)
                        )
                        Text(
                            text = "₹${order.totalAmount}",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = Color(0xFF09090B)
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFE4E4E7))

                    // DYNAMIC UPI QR CODE SECTION
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE4E4E7)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("dynamic_upi_qr_section")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = Color(0xFF09090B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SCAN TO PAY (UPI)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFF09090B)
                                )
                            }

                            if (qrBitmap != null) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(2.dp, Color(0xFF09090B)),
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .size(240.dp)
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "Dynamic UPI QR Code",
                                        modifier = Modifier
                                            .padding(8.dp)
                                            .size(224.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Amount: ₹${String.format(Locale.US, "%.2f", order.totalAmount)}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF09090B)
                            )

                            Text(
                                text = "UPI ID: $activeVpa",
                                fontSize = 11.sp,
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )

                            // If multiple UPI accounts exist, allow switching between them
                            if (upiAccounts.size > 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    upiAccounts.forEach { acc ->
                                        val isSel = acc.id == selectedUpiAccount?.id
                                        Surface(
                                            color = if (isSel) Color(0xFF09090B) else Color(0xFFF4F4F5),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { selectedUpiAccount = acc }
                                        ) {
                                            Text(
                                                text = acc.vpa.split("@").firstOrNull() ?: acc.vpa,
                                                color = if (isSel) Color.White else Color(0xFF09090B),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Print button
                if (onPrintReceipt != null || onConnectPrinter != null) {
                    if (isPrinterConnected) {
                        Button(
                            onClick = { onPrintReceipt?.invoke() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🖨️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Print", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onConnectPrinter?.invoke() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🔌", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Printer", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF71717A))
                        }
                    }
                }
                // Share button
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "FILTR Coffee Bill - #${order.orderId}")
                            putExtra(Intent.EXTRA_TEXT, receiptText)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF09090B),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF09090B), fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

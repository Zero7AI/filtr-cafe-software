package com.example.voice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.entity.MenuItemEntity
import com.example.voice.engine.CatalogItem
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceOrderState
import com.example.voice.viewmodel.VoiceOrderViewModel
import java.util.Locale

// FILTR Shadcn Theme Palette
private val ColorBg = Color(0xFFFAFAFA)
private val ColorSurface = Color(0xFFFFFFFF)
private val ColorCard = Color(0xFFF4F4F5)
private val ColorBorder = Color(0xFFE4E4E7)
private val ColorBorderDark = Color(0xFFD4D4D8)
private val ColorAmber = Color(0xFFF59E0B)
private val ColorAmberDark = Color(0xFFD97706)
private val ColorGreen = Color(0xFF10B981)
private val ColorTextPrimary = Color(0xFF09090B)
private val ColorTextSecondary = Color(0xFF71717A)

@Composable
fun VoiceOrderScreen(
    viewModel: VoiceOrderViewModel,
    availableMenuItems: List<MenuItemEntity> = emptyList(),
    onTransferToBilling: (List<VoiceOrderLineItem>, String?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val orderState by viewModel.orderState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeechActive by viewModel.isSpeechActive.collectAsState()
    val transcript by viewModel.currentTranscript.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsState()
    val catalogItems by MenuCatalog.itemsFlow.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.bindService(context)
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    // Bind only when RECORD_AUDIO is granted; unbind & stop capture cleanly on stop or tab switch
    DisposableEffect(lifecycleOwner) {
        val hasMicPermission = {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (hasMicPermission()) {
                        viewModel.bindService(context)
                    } else if (!permissionRequestedOnce) {
                        permissionRequestedOnce = true
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.onMicPermissionDenied()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.unbindService(context)
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            if (hasMicPermission()) {
                viewModel.bindService(context)
            } else if (!permissionRequestedOnce) {
                permissionRequestedOnce = true
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                viewModel.onMicPermissionDenied()
            }
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.unbindService(context)
        }
    }

    // Keep menu catalog synced with Room items
    LaunchedEffect(availableMenuItems) {
        if (availableMenuItems.isNotEmpty()) {
            viewModel.syncMenuItemsWithRoom(availableMenuItems)
        }
    }

    if (showConfirmDialog && orderState.items.isNotEmpty()) {
        VoiceOrderConfirmDialog(
            orderState = orderState,
            onConfirmTransfer = {
                viewModel.confirmAndTransferToBilling(onTransferToBilling)
            },
            onDismiss = { viewModel.dismissConfirmDialog() }
        )
    }

    Scaffold(
        containerColor = ColorBg,
        topBar = {
            VoiceOrderTopBar(
                isListening = isListening,
                isSpeechActive = isSpeechActive,
                audioLevel = audioLevel,
                transcript = transcript,
                statusMessage = orderState.statusMessage,
                onToggleListening = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!isListening && !granted) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.toggleListening()
                    }
                }
            )
        },
        bottomBar = {
            VoiceOrderBottomBar(
                hasItems = orderState.items.isNotEmpty(),
                onReset = { viewModel.resetOrder() },
                onConfirm = { viewModel.requestConfirm() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            // Dynamic Search & Chip Filtering based on inputText (Compact Bar)
            val trimmedInput = inputText.trim()
            val qtyMatch = Regex("^(\\d+)\\s*(.*)$").find(trimmedInput)
            val (extractedQty, searchQuery) = if (qtyMatch != null) {
                val q = (qtyMatch.groupValues[1].toIntOrNull() ?: 1).coerceAtLeast(1)
                val rest = qtyMatch.groupValues[2].trim()
                Pair(q, rest)
            } else {
                Pair(1, trimmedInput)
            }
            val queryLower = searchQuery.lowercase()

            val displayChips: List<CatalogItem> = remember(queryLower, catalogItems) {
                if (queryLower.isBlank()) {
                    val defaultNames = listOf(
                        "Peri Peri Fries",
                        "Caramel Frappe",
                        "Bombay",
                        "Paneer Sandwich",
                        "Vietnamese Iced Coffee",
                        "Americano",
                        "Salted Fries",
                        "Hazelnut Frappe"
                    )
                    val matchedDefaults = defaultNames.mapNotNull { name ->
                        catalogItems.find { it.name.equals(name, ignoreCase = true) }
                    }
                    matchedDefaults.ifEmpty { catalogItems.take(8) }
                } else {
                    catalogItems.filter { item ->
                        val nameLower = item.name.lowercase()
                        val catLower = item.category.lowercase()
                        nameLower.contains(queryLower) ||
                            item.aliases.any { it.contains(queryLower) } ||
                            catLower.contains(queryLower)
                    }.sortedWith(
                        compareByDescending<CatalogItem> { it.name.lowercase().startsWith(queryLower) }
                            .thenByDescending { it.name.lowercase().split(" ").any { w -> w.startsWith(queryLower) } }
                            .thenByDescending { it.aliases.any { a -> a.startsWith(queryLower) } }
                            .thenBy { it.name.length }
                    )
                }
            }

            // Search & Manual Punch Input (unclipped height)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            "Search or type order (e.g. haz)...",
                            fontSize = 12.sp,
                            color = ColorTextSecondary
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (inputText.isNotBlank()) {
                                val topMatch = displayChips.firstOrNull()
                                if (topMatch != null && queryLower.isNotBlank()) {
                                    viewModel.addItemDirect(topMatch.name, topMatch.price, quantity = extractedQty)
                                } else {
                                    viewModel.submitUtterance(inputText)
                                }
                                inputText = ""
                            }
                        }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ColorTextPrimary,
                        unfocusedTextColor = ColorTextPrimary,
                        focusedBorderColor = ColorAmberDark,
                        unfocusedBorderColor = ColorBorder,
                        focusedContainerColor = ColorSurface,
                        unfocusedContainerColor = ColorSurface
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 46.dp)
                )
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            val topMatch = displayChips.firstOrNull()
                            if (topMatch != null && queryLower.isNotBlank()) {
                                viewModel.addItemDirect(topMatch.name, topMatch.price, quantity = extractedQty)
                            } else {
                                viewModel.submitUtterance(inputText)
                            }
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .background(ColorTextPrimary, RoundedCornerShape(8.dp))
                        .size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Dynamic Quick Item One-Touch Chips (Compact single row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (displayChips.isEmpty() && queryLower.isNotBlank()) {
                    Surface(
                        color = ColorCard,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder)
                    ) {
                        Text(
                            text = "No item found matching \"$searchQuery\"",
                            color = ColorTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    displayChips.forEach { item ->
                        val chipLabel = if (extractedQty > 1) {
                            "+$extractedQty ${item.name}"
                        } else {
                            "+ ${item.name}"
                        }
                        QuickItemChip(
                            label = chipLabel,
                            price = item.price,
                            isHighlighted = queryLower.isNotBlank()
                        ) {
                            viewModel.addItemDirect(item.name, item.price, quantity = extractedQty)
                            inputText = ""
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Order items list
            Box(modifier = Modifier.weight(1f)) {
                if (orderState.items.isEmpty()) {
                    VoiceOrderEmptyState()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(orderState.items, key = { it.lineId }) { item ->
                            VoiceLineItemCard(
                                item = item,
                                onIncrement = { viewModel.onItemQuantityChange(item.lineId, 1) },
                                onDecrement = { viewModel.onItemQuantityChange(item.lineId, -1) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            orderState.orderNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                OrderNotesCard(orderNotes = notes)
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Financial Summary Card
            VoiceFinancialSummaryCard(orderState = orderState)
        }
    }
}

@Composable
private fun VoiceOrderConfirmDialog(
    orderState: VoiceOrderState,
    onConfirmTransfer: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ColorSurface,
        title = {
            Text(
                text = "Confirm Voice Order",
                color = ColorTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 17.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                orderState.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${item.quantity}× ${item.name}",
                                color = ColorTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            if (item.modifiers.isNotEmpty()) {
                                Text(
                                    text = item.modifiers.joinToString(", ") { "+ ${it.name}" },
                                    color = ColorAmberDark,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Text(
                            text = "₹${String.format(Locale.US, "%.0f", item.itemTotal)}",
                            color = ColorTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                orderState.orderNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                    HorizontalDivider(color = ColorBorder, modifier = Modifier.padding(vertical = 2.dp))
                    Text(
                        text = "Notes: $notes",
                        color = ColorTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = ColorBorder, modifier = Modifier.padding(vertical = 2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total",
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "₹${String.format(Locale.US, "%.2f", orderState.total)}",
                        color = ColorAmberDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmTransfer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAmberDark,
                    contentColor = Color.White
                )
            ) {
                Text("TRANSFER TO BILLING", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = ColorTextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun VoiceOrderTopBar(
    isListening: Boolean,
    isSpeechActive: Boolean,
    audioLevel: Double,
    transcript: String,
    statusMessage: String?,
    onToggleListening: () -> Unit
) {
    Surface(
        color = ColorSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Row 1: Header + Mode Badge + Live Mic Indicator + Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "VOICE ORDER",
                        color = ColorTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        letterSpacing = 0.5.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(ColorAmber.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "OFFLINE ASR",
                            color = ColorAmberDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                    if (isListening && audioLevel > 50) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(if (isSpeechActive) ColorGreen else ColorAmberDark, CircleShape)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isListening) (if (isSpeechActive) "Speaking..." else "Listening") else "Muted",
                        color = if (isListening) (if (isSpeechActive) ColorGreen else ColorAmberDark) else ColorTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = isListening,
                        onCheckedChange = { onToggleListening() },
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ColorAmberDark,
                            uncheckedThumbColor = ColorTextSecondary,
                            uncheckedTrackColor = ColorCard
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Row 2: Slim 1-Line Status / Live Transcript Ticker
            val tickerText = when {
                transcript.isNotBlank() -> "🎙️ Heard: \"$transcript\""
                !statusMessage.isNullOrBlank() -> "⚡ $statusMessage"
                else -> "🎙️ Ready: speak order or search menu below"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ColorCard, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tickerText,
                    color = if (transcript.isNotBlank()) ColorTextPrimary else ColorTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (transcript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickItemChip(
    label: String,
    price: Double? = null,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isHighlighted) ColorAmber.copy(alpha = 0.12f) else ColorSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isHighlighted) ColorAmberDark else ColorBorder
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = label,
                color = if (isHighlighted) ColorAmberDark else ColorTextPrimary,
                fontSize = 11.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
            )
            if (price != null) {
                Text(
                    text = "₹${price.toInt()}",
                    color = if (isHighlighted) ColorAmberDark.copy(alpha = 0.85f) else ColorTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun VoiceLineItemCard(
    item: VoiceOrderLineItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Surface(
        color = ColorSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                if (item.modifiers.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 1.dp)
                    ) {
                        item.modifiers.forEach { mod ->
                            val modLabel = if (mod.priceDelta > 0) {
                                "+ ${mod.name} (+₹${mod.priceDelta.toInt()})"
                            } else {
                                "+ ${mod.name}"
                            }
                            Text(
                                text = modLabel,
                                color = ColorAmberDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = "@ ₹${String.format(Locale.US, "%.0f", item.unitPrice)} each",
                    color = ColorTextSecondary,
                    fontSize = 11.sp
                )
            }

            // Steppers [-] Qty [+] & Line Total
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onDecrement,
                    modifier = Modifier
                        .size(30.dp)
                        .background(ColorCard, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrement",
                        tint = ColorTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = "${item.quantity}",
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.widthIn(min = 20.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onIncrement,
                    modifier = Modifier
                        .size(30.dp)
                        .background(ColorCard, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increment",
                        tint = ColorTextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "₹${String.format(Locale.US, "%.0f", item.itemTotal)}",
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun VoiceOrderEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "🎙️", fontSize = 34.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Voice Ordering Ready",
            color = ColorTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Speak an order naturally in Hinglish or English.\ne.g. '2 peri peri fries aur 1 caramel frappe'",
            color = ColorTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
private fun VoiceFinancialSummaryCard(orderState: VoiceOrderState) {
    Surface(
        color = ColorSurface,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalQty = orderState.items.sumOf { it.quantity }
            Column {
                Text(
                    text = "TOTAL ITEMS",
                    color = ColorTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$totalQty item${if (totalQty != 1) "s" else ""} (${orderState.items.size} unique)",
                    color = ColorTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "TOTAL",
                    color = ColorAmberDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "₹${String.format(Locale.US, "%.2f", orderState.total)}",
                    color = ColorTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun VoiceOrderBottomBar(
    hasItems: Boolean,
    onReset: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        color = ColorSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: RESET (Clears order to zero)
            OutlinedButton(
                onClick = onReset,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorTextSecondary),
                border = androidx.compose.foundation.BorderStroke(1.dp, ColorBorderDark),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
            ) {
                Text(text = "RESET", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            // Button 2: CONFIRM (Opens confirmation dialog before transferring to billing)
            Button(
                onClick = onConfirm,
                enabled = hasItems,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAmberDark,
                    contentColor = Color.White,
                    disabledContainerColor = ColorCard,
                    disabledContentColor = ColorTextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1.5f)
                    .height(44.dp)
            ) {
                Text(
                    text = "CONFIRM",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun OrderNotesCard(orderNotes: String) {
    Surface(
        color = Color(0xFF1E293B), // Dark Slate 800
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF0284C7).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NOTES",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = orderNotes,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

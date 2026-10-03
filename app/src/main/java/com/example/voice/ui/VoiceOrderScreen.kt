package com.example.voice.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.data.entity.MenuItemEntity
import com.example.voice.engine.CatalogItem
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceStateMachineEnum
import com.example.voice.viewmodel.VoiceOrderViewModel

// FILTR Shadcn Theme Palette
private val ColorBg = Color(0xFFFAFAFA)
private val ColorSurface = Color(0xFFFFFFFF)
private val ColorCard = Color(0xFFF4F4F5)
private val ColorBorder = Color(0xFFE4E4E7)
private val ColorBorderDark = Color(0xFFD4D4D8)
private val ColorAmber = Color(0xFFF59E0B)
private val ColorAmberDark = Color(0xFFD97706)
private val ColorGreen = Color(0xFF10B981)
private val ColorRed = Color(0xFFEF4444)
private val ColorTextPrimary = Color(0xFF09090B)
private val ColorTextSecondary = Color(0xFF71717A)

@Composable
fun VoiceOrderScreen(
    viewModel: VoiceOrderViewModel,
    availableMenuItems: List<MenuItemEntity> = emptyList(),
    onTransferToBilling: (List<VoiceOrderLineItem>, String?) -> Unit
) {
    val context = LocalContext.current
    val orderState by viewModel.orderState.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isSpeechActive by viewModel.isSpeechActive.collectAsState()
    val transcript by viewModel.currentTranscript.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()

    var inputText by remember { mutableStateOf("") }

    // Start background capture & bind to service when screen opens
    LaunchedEffect(Unit) {
        viewModel.bindService(context)
    }

    // Keep menu catalog synced with Room items
    LaunchedEffect(availableMenuItems) {
        if (availableMenuItems.isNotEmpty()) {
            viewModel.syncMenuItemsWithRoom(availableMenuItems)
        }
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
                onToggleListening = { viewModel.toggleListening() }
            )
        },
        bottomBar = {
            VoiceOrderBottomBar(
                hasItems = orderState.items.isNotEmpty(),
                onReset = { viewModel.resetOrder() },
                onConfirm = {
                    viewModel.confirmAndTransferToBilling(onTransferToBilling)
                }
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
                val q = qtyMatch.groupValues[1].toIntOrNull() ?: 1
                val rest = qtyMatch.groupValues[2].trim()
                Pair(q, rest)
            } else {
                Pair(1, trimmedInput)
            }
            val queryLower = searchQuery.lowercase()

            val displayChips: List<CatalogItem> = remember(queryLower, availableMenuItems) {
                val activeItems = MenuCatalog.items
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
                        activeItems.find { it.name.equals(name, ignoreCase = true) }
                    }
                    if (matchedDefaults.isNotEmpty()) {
                        matchedDefaults
                    } else {
                        activeItems.take(8)
                    }
                } else {
                    activeItems.filter { item ->
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

            // Compact Search & Manual Punch Input (Height 40dp)
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
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(
                                onClick = { inputText = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = ColorTextSecondary,
                                    modifier = Modifier.size(14.dp)
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
                                    inputText = ""
                                } else {
                                    viewModel.submitUtterance(inputText)
                                    inputText = ""
                                }
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
                        .height(42.dp)
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
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
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
                            Text(
                                text = "+ ${mod.name}",
                                color = ColorAmberDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    text = "@ ₹${String.format("%.0f", item.unitPrice)} each",
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
                    text = "₹${String.format("%.0f", item.itemTotal)}",
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
                    text = "₹${String.format("%.2f", orderState.total)}",
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

            // Button 2: CONFIRM (Opens pop-up to directly bill and go to billing section)
            Button(
                onClick = onConfirm,
                enabled = hasItems,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAmberDark,
                    disabledContainerColor = ColorCard
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
                    letterSpacing = 0.5.sp,
                    color = Color.White
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
                color = ColorTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}


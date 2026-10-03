package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.FixedCostEntity
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.StaffMember
import com.example.data.entity.calculatePacksAndCost
import com.example.ui.BillingUiState
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Theme Colors
private val DarkCardBg = Color(0xFF18181B)
private val DarkCardBorder = Color(0xFF27272A)
private val MutedTextColor = Color(0xFFA1A1AA)
private val StatusGreen = Color(0xFF10B981)
private val StatusRed = Color(0xFFEF4444)
private val StatusAmber = Color(0xFFF59E0B)
private val StatusBlue = Color(0xFF3B82F6)
private val StatusPurple = Color(0xFF8B5CF6)

data class ProratedFixedCostBreakdown(
    val total: Double,
    val rent: Double,
    val electricity: Double,
    val staffSalaries: Double,
    val staffCount: Int,
    val maidSalary: Double,
    val miscellaneous: Double,
    val daysCount: Int,
    val isConfiguredInDb: Boolean,
    val monthKeyDescription: String,
    val dailyBurden: Double,
    val staffMembers: List<StaffMember> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfitLossScreen(
    state: BillingUiState,
    ordersList: List<OrderWithItems>,
    inventoryItems: List<InventoryItemEntity>,
    usages: List<InventoryUsageEntity>,
    fixedCosts: List<FixedCostEntity>,
    onOpenFixedCostDialog: (String) -> Unit,
    onDismissFixedCostDialog: () -> Unit,
    onSaveFixedCost: (FixedCostEntity) -> Unit,
    onBackfillOrders: (Long, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var dateFilter by remember { mutableStateOf("TODAY") } // TODAY, YESTERDAY, LAST_7_DAYS, THIS_MONTH, MONTH, CUSTOM
    var showCustomDateDialog by remember { mutableStateOf(false) }
    var customStartMs by remember { mutableStateOf<Long?>(null) }
    var customEndMs by remember { mutableStateOf<Long?>(null) }

    // Active Month navigation (defaults to current month, e.g. "2026-09")
    val currentMonthKey = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }
    var selectedMonthKey by remember { mutableStateOf(currentMonthKey) }

    // Cloud Backfill for selected date filter / month / custom range
    LaunchedEffect(dateFilter, selectedMonthKey, customStartMs, customEndMs) {
        val nowMs = System.currentTimeMillis()
        when (dateFilter) {
            "TODAY" -> {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onBackfillOrders(todayStart, nowMs)
            }
            "YESTERDAY" -> {
                val yCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                val yStart = (yCal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val yEnd = (yCal.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                onBackfillOrders(yStart, yEnd)
            }
            "LAST_7_DAYS" -> {
                val weekAgo = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onBackfillOrders(weekAgo, nowMs)
            }
            "THIS_MONTH" -> {
                val monthStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onBackfillOrders(monthStart, nowMs)
            }
            "MONTH" -> {
                val parts = selectedMonthKey.split("-")
                val now = Calendar.getInstance()
                val y = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
                val m = (parts.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)) - 1
                val calStart = Calendar.getInstance().apply {
                    set(y, m, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    set(y, m, calStart.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                onBackfillOrders(calStart.timeInMillis, calEnd.timeInMillis)
            }
            "CUSTOM" -> {
                if (customStartMs != null && customEndMs != null) {
                    onBackfillOrders(customStartMs!!, customEndMs!!)
                }
            }
        }
    }

    // Multi-month Day-by-Day Proration & Filtered Orders
    val (proratedBreakdown, filteredOrders) = remember(
        dateFilter,
        selectedMonthKey,
        ordersList,
        fixedCosts,
        customStartMs,
        customEndMs
    ) {
        computePeriodProration(
            dateFilter = dateFilter,
            selectedMonthKey = selectedMonthKey,
            ordersList = ordersList,
            fixedCostsList = fixedCosts,
            customStartMs = customStartMs,
            customEndMs = customEndMs
        )
    }

    // 1. Gross Cafe Sales & 5% GST
    val grossSales = remember(filteredOrders) {
        filteredOrders.sumOf { it.order.totalAmount }
    }
    // 5% inclusive GST formula: GST = Gross * (5.0 / 105.0)
    val gstAmount = grossSales * (5.0 / 105.0)
    val netSales = grossSales - gstAmount

    // 2. Recipe Ingredient COGS & Breakdown
    val (recipeCogs, topIngredients) = remember(filteredOrders, usages, inventoryItems) {
        val rawUsageMap = mutableMapOf<Int, Pair<Double, String>>()
        val rawCostMap = mutableMapOf<Int, Double>()
        var totalCogsCalc = 0.0

        filteredOrders.forEach { orderWithItems ->
            orderWithItems.items.forEach { orderItem ->
                // Match recipe by menuItemId OR by itemName
                val recipeUsages = usages.filter {
                    it.menuItemId == orderItem.menuItemId ||
                    it.menuItemName.equals(orderItem.itemName, ignoreCase = true)
                }

                if (recipeUsages.isNotEmpty()) {
                    recipeUsages.forEach { usage ->
                        val consumed = usage.quantityUsed * orderItem.quantity
                        val invItem = inventoryItems.find { it.id == usage.inventoryItemId }
                        if (invItem != null) {
                            val (_, cost) = calculatePacksAndCost(consumed, usage.unit, invItem)
                            totalCogsCalc += cost
                            rawCostMap[usage.inventoryItemId] = (rawCostMap[usage.inventoryItemId] ?: 0.0) + cost
                            val prev = rawUsageMap[usage.inventoryItemId]
                            rawUsageMap[usage.inventoryItemId] = Pair((prev?.first ?: 0.0) + consumed, usage.unit)
                        }
                    }
                } else {
                    // Fallback to matching direct inventory item
                    val directItem = inventoryItems.find {
                        it.name.equals(orderItem.itemName, ignoreCase = true) ||
                        orderItem.itemName.contains(it.name, ignoreCase = true) ||
                        it.name.contains(orderItem.itemName, ignoreCase = true)
                    }
                    if (directItem != null) {
                        val consumed = orderItem.quantity.toDouble()
                        val (_, cost) = calculatePacksAndCost(consumed, directItem.unit, directItem)
                        totalCogsCalc += cost
                        rawCostMap[directItem.id] = (rawCostMap[directItem.id] ?: 0.0) + cost
                        val prev = rawUsageMap[directItem.id]
                        rawUsageMap[directItem.id] = Pair((prev?.first ?: 0.0) + consumed, directItem.unit)
                    } else {
                        // Unconfigured recipe: fallback estimate 35% COGS
                        totalCogsCalc += (orderItem.itemPrice * orderItem.quantity) * 0.35
                    }
                }
            }
        }

        val ingredientList = rawUsageMap.mapNotNull { (invId, pair) ->
            val (qty, usageUnit) = pair
            val invItem = inventoryItems.find { it.id == invId } ?: return@mapNotNull null
            val cost = rawCostMap[invId] ?: 0.0
            val (packs, _) = calculatePacksAndCost(qty, usageUnit, invItem)

            val u1 = usageUnit.trim().lowercase()
            val u2 = invItem.unit.trim().lowercase()
            val usageDetail = when {
                u1 == u2 -> "${formatQty(qty)} $usageUnit"
                u1 == "g" && (u2 == "kg" || u2 == "kilogram" || u2 == "kgs") -> {
                    if (qty >= 1000.0) "${formatDecimal(qty / 1000.0)} kg (${formatQty(qty)} g)"
                    else "${formatQty(qty)} g (${formatDecimal(qty / 1000.0)} kg)"
                }
                u1 == "ml" && (u2 == "l" || u2 == "liter" || u2 == "liters" || u2 == "litre" || u2 == "litres") -> {
                    if (qty >= 1000.0) "${formatDecimal(qty / 1000.0)} L (${formatQty(qty)} ml)"
                    else "${formatQty(qty)} ml (${formatDecimal(qty / 1000.0)} L)"
                }
                else -> "${formatQty(qty)} $usageUnit (${formatDecimal(packs)} ${invItem.unit})"
            }

            Triple(invItem.name, cost, usageDetail)
        }.sortedByDescending { it.second }.take(5)

        Pair(totalCogsCalc, ingredientList)
    }

    // 3. Gross Profit
    val grossProfit = netSales - recipeCogs
    val grossMarginPct = if (netSales > 0) (grossProfit / netSales) * 100.0 else 0.0

    // 4. Net Operating Profit & Margin
    val netOperatingProfit = grossProfit - proratedBreakdown.total
    val netMarginPct = if (netSales > 0) (netOperatingProfit / netSales) * 100.0 else 0.0

    // 5. Break-Even Run Rate
    val dailyFixedCost = proratedBreakdown.dailyBurden
    val grossProfitRatio = if (grossSales > 0) grossProfit / grossSales else 0.65
    val breakEvenDailySales = if (grossProfitRatio > 0.05) dailyFixedCost / grossProfitRatio else (dailyFixedCost / 0.65)
    val currentDailySalesRunRate = if (proratedBreakdown.daysCount > 0) grossSales / proratedBreakdown.daysCount else 0.0
    val breakEvenProgress = if (breakEvenDailySales > 0) {
        (currentDailySalesRunRate / breakEvenDailySales).toFloat().coerceIn(0f, 2f)
    } else 0f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF09090B)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ========== 1. HEADER & SETTINGS BUTTON ==========
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FINANCIAL P&L STATEMENT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Month-Wise Fixed Costs, 5% GST & Recipe COGS",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedTextColor
                    )
                }

                FilledTonalButton(
                    onClick = { onOpenFixedCostDialog(selectedMonthKey) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StatusAmber.copy(alpha = 0.15f),
                        contentColor = StatusAmber
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Set Fixed Costs",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Set Fixed Costs", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // ========== 2. PERIOD SELECTOR CHIPS ==========
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterOptions = listOf(
                        "TODAY" to "Today",
                        "YESTERDAY" to "Yesterday",
                        "LAST_7_DAYS" to "Last 7 Days",
                        "THIS_MONTH" to "This Month (MTD)",
                        "MONTH" to "Select Month",
                        "CUSTOM" to "Custom Range"
                    )

                    items(filterOptions) { (key, label) ->
                        val isSelected = dateFilter == key
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (key == "CUSTOM") {
                                    showCustomDateDialog = true
                                } else {
                                    dateFilter = key
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color.White,
                                selectedLabelColor = Color(0xFF09090B),
                                containerColor = DarkCardBg,
                                labelColor = MutedTextColor
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else DarkCardBorder
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Dedicated Month Selector Bar (Active when "MONTH" is selected)
                if (dateFilter == "MONTH") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Month Navigation Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { selectedMonthKey = getPreviousMonthKey(selectedMonthKey) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Previous Month",
                                        tint = StatusAmber
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatMonthDisplay(selectedMonthKey).uppercase(Locale.US),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${getDaysInMonthKey(selectedMonthKey)} Days • ${proratedBreakdown.monthKeyDescription}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedTextColor
                                    )
                                }

                                IconButton(
                                    onClick = { selectedMonthKey = getNextMonthKey(selectedMonthKey) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = "Next Month",
                                        tint = StatusAmber
                                    )
                                }
                            }

                            // Quick Month Chips
                            val recentMonthKeys = remember(fixedCosts) {
                                val cal = Calendar.getInstance()
                                val list = mutableListOf<String>()
                                for (i in 0..3) {
                                    val c = cal.clone() as Calendar
                                    c.add(Calendar.MONTH, -i)
                                    list.add(SimpleDateFormat("yyyy-MM", Locale.US).format(c.time))
                                }
                                (list + fixedCosts.map { it.monthKey }).distinct().sortedDescending().take(4)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                recentMonthKeys.forEach { mKey ->
                                    val isCurrent = mKey == selectedMonthKey
                                    val hasConfig = fixedCosts.any { it.monthKey == mKey }
                                    Surface(
                                        color = if (isCurrent) StatusAmber.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isCurrent) StatusAmber else Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedMonthKey = mKey }
                                    ) {
                                        Text(
                                            text = formatMonthShort(mKey) + if (hasConfig) " *" else "",
                                            fontSize = 11.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) StatusAmber else Color.White,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Proration & Configuration Summary Banner
                Surface(
                    color = DarkCardBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (proratedBreakdown.isConfiguredInDb) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (proratedBreakdown.isConfiguredInDb) StatusGreen else StatusAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Period: ${proratedBreakdown.daysCount} ${if (proratedBreakdown.daysCount == 1) "Day" else "Days"} (${proratedBreakdown.monthKeyDescription})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Overhead: ${formatInr(proratedBreakdown.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = StatusAmber
                        )
                    }
                }
            }
        }

        // ========== 3. TOP 4-CARD HERO SUMMARY ==========
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Card 1: Net Operating Profit (Full Width Hero)
                val isProfitable = netOperatingProfit >= 0
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProfitable) StatusGreen.copy(alpha = 0.1f) else StatusRed.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.5.dp,
                        if (isProfitable) StatusGreen.copy(alpha = 0.4f) else StatusRed.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = "NET OPERATING PROFIT",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (isProfitable) StatusGreen else StatusRed,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Surface(
                                    color = (if (isProfitable) StatusGreen else StatusRed).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "EBITDA",
                                        color = if (isProfitable) StatusGreen else StatusRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                color = if (isProfitable) StatusGreen else StatusRed,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${if (netMarginPct >= 0) "+" else ""}${formatDecimal(netMarginPct)}% NET",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = "${if (netOperatingProfit < 0) "-" else ""}${formatInr(Math.abs(netOperatingProfit))}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = if (isProfitable) StatusGreen else StatusRed,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 28.sp
                        )

                        // Responsive Formula Breakdown Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FormulaPill(
                                label = "Net Rev",
                                value = formatInr(netSales),
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Text("-", color = MutedTextColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            FormulaPill(
                                label = "COGS",
                                value = formatInr(recipeCogs),
                                color = StatusAmber,
                                modifier = Modifier.weight(1f)
                            )
                            Text("-", color = MutedTextColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            FormulaPill(
                                label = "Fixed",
                                value = formatInr(proratedBreakdown.total),
                                color = StatusPurple,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Cards 2, 3, 4, 5 (Responsive 2x2 Grid)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Net Revenue
                    FinancialMiniCard(
                        title = "NET REVENUE",
                        value = formatInr(netSales),
                        subtitle = "Gross: ${formatInr(grossSales)} (-5% GST)",
                        accentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    // Recipe COGS
                    FinancialMiniCard(
                        title = "RECIPE COGS",
                        value = formatInr(recipeCogs),
                        subtitle = "Ingredients & Packaging",
                        accentColor = StatusAmber,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Fixed Overheads
                    FinancialMiniCard(
                        title = "OVERHEADS",
                        value = formatInr(proratedBreakdown.total),
                        subtitle = "${formatInr(dailyFixedCost)}/day • ${proratedBreakdown.daysCount}d",
                        accentColor = StatusPurple,
                        modifier = Modifier.weight(1f)
                    )

                    // Gross Profit
                    FinancialMiniCard(
                        title = "GROSS MARGIN",
                        value = formatInr(grossProfit),
                        subtitle = "${formatDecimal(grossMarginPct)}% Gross Margin",
                        accentColor = if (grossProfit >= 0) StatusGreen else StatusRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ========== 4. FINANCIAL WATERFALL TABLE ==========
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "P&L WATERFALL STATEMENT",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "${filteredOrders.size} Orders Processed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedTextColor
                        )
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    // 1. Gross Billings
                    WaterfallRow(
                        prefix = "[+]",
                        title = "Gross Customer Billings",
                        detail = "Total orders collected",
                        amount = formatInr(grossSales),
                        percentage = "100.0%",
                        amountColor = Color.White
                    )

                    // 2. 5% Inclusive GST
                    WaterfallRow(
                        prefix = "[-]",
                        title = "5% GST Tax Remittance",
                        detail = "Inclusive (Gross × 5 / 105)",
                        amount = "-${formatInr(gstAmount)}",
                        percentage = "-${formatDecimal(if (grossSales > 0) (gstAmount / grossSales) * 100 else 4.76)}%",
                        amountColor = StatusRed
                    )

                    // 3. Net Operating Revenue (Subtotal)
                    WaterfallSubtotalRow(
                        title = "Net Cafe Operating Revenue",
                        amount = formatInr(netSales),
                        percentage = "${formatDecimal(if (grossSales > 0) (netSales / grossSales) * 100 else 95.24)}%",
                        amountColor = Color.White
                    )

                    // 4. Recipe Ingredients COGS
                    WaterfallRow(
                        prefix = "[-]",
                        title = "Recipe Ingredients (COGS)",
                        detail = "Itemized milk, beans, paneer & sauces",
                        amount = "-${formatInr(recipeCogs)}",
                        percentage = "-${formatDecimal(if (netSales > 0) (recipeCogs / netSales) * 100 else 0.0)}%",
                        amountColor = StatusAmber
                    )

                    // 5. Gross Operating Margin (Subtotal)
                    WaterfallSubtotalRow(
                        title = "Gross Cafe Margin",
                        amount = formatInr(grossProfit),
                        percentage = "${formatDecimal(grossMarginPct)}%",
                        amountColor = if (grossProfit >= 0) StatusGreen else StatusRed
                    )

                    // 6. Prorated Fixed Operating Overheads
                    WaterfallRow(
                        prefix = "[-]",
                        title = "Prorated Fixed Overheads",
                        detail = "Rent, electricity, salaries, maid & misc (${proratedBreakdown.daysCount} d)",
                        amount = "-${formatInr(proratedBreakdown.total)}",
                        percentage = "-${formatDecimal(if (netSales > 0) (proratedBreakdown.total / netSales) * 100 else 0.0)}%",
                        amountColor = StatusPurple
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    // 7. Net Operating Profit (Final Bottom Line)
                    WaterfallFinalRow(
                        title = "NET OPERATING PROFIT",
                        subtitle = "EBITDA",
                        amount = "${if (netOperatingProfit < 0) "-" else ""}${formatInr(Math.abs(netOperatingProfit))}",
                        percentage = "${if (netMarginPct >= 0) "+" else ""}${formatDecimal(netMarginPct)}%",
                        amountColor = if (netOperatingProfit >= 0) StatusGreen else StatusRed
                    )
                }
            }
        }

        // ========== 5. ITEMIZED FIXED OVERHEAD BREAKDOWN ==========
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, DarkCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FIXED OVERHEADS BREAKDOWN",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${proratedBreakdown.monthKeyDescription} • Prorated for ${proratedBreakdown.daysCount} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedTextColor
                            )
                        }

                        IconButton(
                            onClick = { onOpenFixedCostDialog(selectedMonthKey) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Month Fixed Costs",
                                tint = StatusAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = DarkCardBorder)

                    OverheadItemRow(
                        icon = Icons.Default.Storefront,
                        name = "Shop Rent",
                        monthlyAmount = (proratedBreakdown.rent / proratedBreakdown.daysCount) * 30.0,
                        proratedAmount = proratedBreakdown.rent,
                        dailyRate = proratedBreakdown.rent / proratedBreakdown.daysCount
                    )

                    OverheadItemRow(
                        icon = Icons.Default.ElectricBolt,
                        name = "Electricity Bill",
                        monthlyAmount = (proratedBreakdown.electricity / proratedBreakdown.daysCount) * 30.0,
                        proratedAmount = proratedBreakdown.electricity,
                        dailyRate = proratedBreakdown.electricity / proratedBreakdown.daysCount
                    )

                    OverheadItemRow(
                        icon = Icons.Default.People,
                        name = "Staff Salaries (${proratedBreakdown.staffCount} staff)",
                        monthlyAmount = (proratedBreakdown.staffSalaries / proratedBreakdown.daysCount) * 30.0,
                        proratedAmount = proratedBreakdown.staffSalaries,
                        dailyRate = proratedBreakdown.staffSalaries / proratedBreakdown.daysCount,
                        subDetail = if (proratedBreakdown.staffMembers.isNotEmpty()) {
                            proratedBreakdown.staffMembers.joinToString(" • ") { "${it.name}: ${formatInr(it.salary)}" }
                        } else null
                    )

                    OverheadItemRow(
                        icon = Icons.Default.CleaningServices,
                        name = "Maid / Daily Cleaning",
                        monthlyAmount = (proratedBreakdown.maidSalary / proratedBreakdown.daysCount) * 30.0,
                        proratedAmount = proratedBreakdown.maidSalary,
                        dailyRate = proratedBreakdown.maidSalary / proratedBreakdown.daysCount
                    )

                    OverheadItemRow(
                        icon = Icons.Default.Build,
                        name = "Misc & Maintenance",
                        monthlyAmount = (proratedBreakdown.miscellaneous / proratedBreakdown.daysCount) * 30.0,
                        proratedAmount = proratedBreakdown.miscellaneous,
                        dailyRate = proratedBreakdown.miscellaneous / proratedBreakdown.daysCount
                    )

                    HorizontalDivider(color = DarkCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Operating Overhead",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatInr(proratedBreakdown.total),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusPurple
                            )
                            Text(
                                text = "${formatInr(dailyFixedCost)}/day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedTextColor
                            )
                        }
                    }
                }
            }
        }

        // ========== 6. BREAK-EVEN & DAILY RUN RATE ==========
        item {
            val isAboveBreakEven = currentDailySalesRunRate >= breakEvenDailySales
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (isAboveBreakEven) StatusGreen.copy(alpha = 0.3f) else StatusAmber.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BREAK-EVEN RUN RATE",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Surface(
                            color = if (isAboveBreakEven) StatusGreen.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isAboveBreakEven) StatusGreen else StatusAmber)
                        ) {
                            Text(
                                text = if (isAboveBreakEven) "PROFITABLE RUN RATE 🚀" else "₹${formatDecimal(Math.max(0.0, breakEvenDailySales - currentDailySalesRunRate))}/day TO BREAK-EVEN",
                                color = if (isAboveBreakEven) StatusGreen else StatusAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Daily Sales", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                            Text(
                                text = "${formatInr(currentDailySalesRunRate)}/day",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target to Cover Overheads", style = MaterialTheme.typography.bodySmall, color = MutedTextColor)
                            Text(
                                text = "${formatInr(breakEvenDailySales)}/day",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusAmber
                            )
                        }
                    }

                    // Progress Bar
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { breakEvenProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (isAboveBreakEven) StatusGreen else StatusAmber,
                            trackColor = DarkCardBorder
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0%", fontSize = 10.sp, color = MutedTextColor)
                            Text(
                                text = "${(breakEvenProgress * 100).toInt()}% of Break-even target",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAboveBreakEven) StatusGreen else StatusAmber
                            )
                            Text("100% Target", fontSize = 10.sp, color = MutedTextColor)
                        }
                    }
                }
            }
        }

        // ========== 7. TOP RECIPE COGS INGREDIENTS ==========
        if (topIngredients.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCardBg),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DarkCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "TOP INGREDIENTS CONSUMED (COGS)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        HorizontalDivider(color = DarkCardBorder)

                        topIngredients.forEach { (name, cost, usageDetail) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = usageDetail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MutedTextColor
                                    )
                                }

                                Text(
                                    text = formatInr(cost),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== SET FIXED COSTS MODAL DIALOG ==========
    if (state.showFixedCostDialog) {
        val editingMonth = if (state.editingFixedCostMonthKey.isNotBlank()) state.editingFixedCostMonthKey else selectedMonthKey

        SetFixedCostsDialog(
            targetMonthKey = editingMonth,
            allConfiguredCosts = fixedCosts,
            onDismiss = onDismissFixedCostDialog,
            onSave = { savedEntity ->
                onSaveFixedCost(savedEntity)
                selectedMonthKey = savedEntity.monthKey
            }
        )
    }

    // ========== CUSTOM DATE PICKER DIALOG ==========
    if (showCustomDateDialog) {
        CustomDateRangePickerDialog(
            onDismiss = { showCustomDateDialog = false },
            onDateRangeApplied = { startMs, endMs ->
                customStartMs = startMs
                customEndMs = endMs
                dateFilter = "CUSTOM"
                showCustomDateDialog = false
                onBackfillOrders(startMs, endMs)
            }
        )
    }
}

// ==================== CALCULATION ENGINE ====================

private fun computePeriodProration(
    dateFilter: String,
    selectedMonthKey: String,
    ordersList: List<OrderWithItems>,
    fixedCostsList: List<FixedCostEntity>,
    customStartMs: Long?,
    customEndMs: Long?
): Pair<ProratedFixedCostBreakdown, List<OrderWithItems>> {
    val now = Calendar.getInstance()

    when (dateFilter) {
        "TODAY" -> {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val orders = ordersList.filter { it.order.timestamp >= todayStart }
            val todayMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val daysInMonth = getDaysInMonthKey(todayMonthKey)
            val monthCost = getFixedCostForMonthKey(todayMonthKey, fixedCostsList)
            val isConfigured = fixedCostsList.any { it.monthKey == todayMonthKey }

            val breakdown = ProratedFixedCostBreakdown(
                total = monthCost.rent / daysInMonth + monthCost.electricity / daysInMonth + monthCost.staffSalaries / daysInMonth + monthCost.maidSalary / daysInMonth + monthCost.miscellaneous / daysInMonth,
                rent = monthCost.rent / daysInMonth,
                electricity = monthCost.electricity / daysInMonth,
                staffSalaries = monthCost.staffSalaries / daysInMonth,
                staffCount = monthCost.staffCount,
                maidSalary = monthCost.maidSalary / daysInMonth,
                miscellaneous = monthCost.miscellaneous / daysInMonth,
                daysCount = 1,
                isConfiguredInDb = isConfigured,
                monthKeyDescription = todayMonthKey,
                dailyBurden = monthCost.dailyCost(daysInMonth),
                staffMembers = monthCost.parseStaffMembers()
            )
            return Pair(breakdown, orders)
        }

        "YESTERDAY" -> {
            val yesterdayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            val yesterdayStart = (yesterdayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val orders = ordersList.filter { it.order.timestamp in yesterdayStart until todayStart }
            val yesterdayMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(yesterdayCal.time)
            val daysInMonth = getDaysInMonthKey(yesterdayMonthKey)
            val monthCost = getFixedCostForMonthKey(yesterdayMonthKey, fixedCostsList)
            val isConfigured = fixedCostsList.any { it.monthKey == yesterdayMonthKey }

            val breakdown = ProratedFixedCostBreakdown(
                total = monthCost.rent / daysInMonth + monthCost.electricity / daysInMonth + monthCost.staffSalaries / daysInMonth + monthCost.maidSalary / daysInMonth + monthCost.miscellaneous / daysInMonth,
                rent = monthCost.rent / daysInMonth,
                electricity = monthCost.electricity / daysInMonth,
                staffSalaries = monthCost.staffSalaries / daysInMonth,
                staffCount = monthCost.staffCount,
                maidSalary = monthCost.maidSalary / daysInMonth,
                miscellaneous = monthCost.miscellaneous / daysInMonth,
                daysCount = 1,
                isConfiguredInDb = isConfigured,
                monthKeyDescription = yesterdayMonthKey,
                dailyBurden = monthCost.dailyCost(daysInMonth),
                staffMembers = monthCost.parseStaffMembers()
            )
            return Pair(breakdown, orders)
        }

        "LAST_7_DAYS" -> {
            val weekAgoCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val orders = ordersList.filter { it.order.timestamp >= weekAgoCal.timeInMillis }

            // Day-by-day accumulation across 7 days (handles month boundaries perfectly)
            var totalRent = 0.0
            var totalElec = 0.0
            var totalStaff = 0.0
            var totalMaid = 0.0
            var totalMisc = 0.0
            var staffCountLatest = 2
            var staffMembersLatest = emptyList<StaffMember>()
            val monthsSeen = mutableSetOf<String>()

            val cur = weekAgoCal.clone() as Calendar
            for (i in 0 until 7) {
                val mKey = SimpleDateFormat("yyyy-MM", Locale.US).format(cur.time)
                monthsSeen.add(mKey)
                val dim = getDaysInMonthKey(mKey)
                val mCost = getFixedCostForMonthKey(mKey, fixedCostsList)
                totalRent += mCost.rent / dim
                totalElec += mCost.electricity / dim
                totalStaff += mCost.staffSalaries / dim
                totalMaid += mCost.maidSalary / dim
                totalMisc += mCost.miscellaneous / dim
                staffCountLatest = mCost.staffCount
                staffMembersLatest = mCost.parseStaffMembers()
                cur.add(Calendar.DAY_OF_YEAR, 1)
            }

            val totalAll = totalRent + totalElec + totalStaff + totalMaid + totalMisc
            val desc = monthsSeen.joinToString(" & ")

            val breakdown = ProratedFixedCostBreakdown(
                total = totalAll,
                rent = totalRent,
                electricity = totalElec,
                staffSalaries = totalStaff,
                staffCount = staffCountLatest,
                maidSalary = totalMaid,
                miscellaneous = totalMisc,
                daysCount = 7,
                isConfiguredInDb = monthsSeen.any { m -> fixedCostsList.any { it.monthKey == m } },
                monthKeyDescription = desc,
                dailyBurden = totalAll / 7.0,
                staffMembers = staffMembersLatest
            )
            return Pair(breakdown, orders)
        }

        "THIS_MONTH" -> {
            val cal = Calendar.getInstance()
            val currentDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
            val thisMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)

            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val orders = ordersList.filter { it.order.timestamp >= monthStart }
            val daysInMonth = getDaysInMonthKey(thisMonthKey)
            val monthCost = getFixedCostForMonthKey(thisMonthKey, fixedCostsList)
            val isConfigured = fixedCostsList.any { it.monthKey == thisMonthKey }
            val ratio = currentDayOfMonth.toDouble() / daysInMonth

            val breakdown = ProratedFixedCostBreakdown(
                total = monthCost.totalFixedCost * ratio,
                rent = monthCost.rent * ratio,
                electricity = monthCost.electricity * ratio,
                staffSalaries = monthCost.staffSalaries * ratio,
                staffCount = monthCost.staffCount,
                maidSalary = monthCost.maidSalary * ratio,
                miscellaneous = monthCost.miscellaneous * ratio,
                daysCount = currentDayOfMonth,
                isConfiguredInDb = isConfigured,
                monthKeyDescription = "$thisMonthKey (MTD)",
                dailyBurden = monthCost.dailyCost(daysInMonth),
                staffMembers = monthCost.parseStaffMembers()
            )
            return Pair(breakdown, orders)
        }

        "MONTH" -> {
            // Full specific month (e.g. "2026-08", "2026-09", etc.)
            val parts = selectedMonthKey.split("-")
            val y = parts.getOrNull(0)?.toIntOrNull() ?: now.get(Calendar.YEAR)
            val m = (parts.getOrNull(1)?.toIntOrNull() ?: (now.get(Calendar.MONTH) + 1)) - 1
            val calStart = Calendar.getInstance().apply {
                set(y, m, 1, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val calEnd = Calendar.getInstance().apply {
                set(y, m, calStart.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val orders = ordersList.filter { it.order.timestamp in calStart.timeInMillis..calEnd.timeInMillis }
            val daysInMonth = calStart.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthCost = getFixedCostForMonthKey(selectedMonthKey, fixedCostsList)
            val isConfigured = fixedCostsList.any { it.monthKey == selectedMonthKey }

            val breakdown = ProratedFixedCostBreakdown(
                total = monthCost.totalFixedCost,
                rent = monthCost.rent,
                electricity = monthCost.electricity,
                staffSalaries = monthCost.staffSalaries,
                staffCount = monthCost.staffCount,
                maidSalary = monthCost.maidSalary,
                miscellaneous = monthCost.miscellaneous,
                daysCount = daysInMonth,
                isConfiguredInDb = isConfigured,
                monthKeyDescription = formatMonthDisplay(selectedMonthKey),
                dailyBurden = monthCost.dailyCost(daysInMonth),
                staffMembers = monthCost.parseStaffMembers()
            )
            return Pair(breakdown, orders)
        }

        "CUSTOM" -> {
            val s = customStartMs ?: 0L
            val e = customEndMs ?: Long.MAX_VALUE
            val orders = ordersList.filter { it.order.timestamp in s..e }

            if (customStartMs != null && customEndMs != null) {
                var totalRent = 0.0
                var totalElec = 0.0
                var totalStaff = 0.0
                var totalMaid = 0.0
                var totalMisc = 0.0
                var staffCountLatest = 2
                var staffMembersLatest = emptyList<StaffMember>()
                val monthsSeen = mutableSetOf<String>()

                val cur = Calendar.getInstance().apply { timeInMillis = customStartMs }
                val endCal = Calendar.getInstance().apply { timeInMillis = customEndMs }
                val todayEndCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }

                // If range extends into future days (e.g. user selected full month up to 30th when today is 21st),
                // calculate operational overhead up to today so operating burden matches elapsed sales period.
                val isFutureSpanning = endCal.timeInMillis > todayEndCal.timeInMillis && cur.timeInMillis <= todayEndCal.timeInMillis
                val calculationEndMs = if (isFutureSpanning) todayEndCal.timeInMillis else endCal.timeInMillis
                val calculationEndCal = Calendar.getInstance().apply { timeInMillis = calculationEndMs }

                var dayCount = 0

                while (cur.timeInMillis <= calculationEndCal.timeInMillis) {
                    val mKey = SimpleDateFormat("yyyy-MM", Locale.US).format(cur.time)
                    monthsSeen.add(mKey)
                    val dim = getDaysInMonthKey(mKey)
                    val mCost = getFixedCostForMonthKey(mKey, fixedCostsList)
                    totalRent += mCost.rent / dim
                    totalElec += mCost.electricity / dim
                    totalStaff += mCost.staffSalaries / dim
                    totalMaid += mCost.maidSalary / dim
                    totalMisc += mCost.miscellaneous / dim
                    staffCountLatest = mCost.staffCount
                    staffMembersLatest = mCost.parseStaffMembers()
                    dayCount++
                    cur.add(Calendar.DAY_OF_YEAR, 1)
                }

                val safeDayCount = maxOf(1, dayCount)
                val totalAll = totalRent + totalElec + totalStaff + totalMaid + totalMisc
                val desc = if (isFutureSpanning) {
                    "${monthsSeen.joinToString(" & ")} (MTD ${safeDayCount}d)"
                } else {
                    monthsSeen.joinToString(" & ")
                }

                val breakdown = ProratedFixedCostBreakdown(
                    total = totalAll,
                    rent = totalRent,
                    electricity = totalElec,
                    staffSalaries = totalStaff,
                    staffCount = staffCountLatest,
                    maidSalary = totalMaid,
                    miscellaneous = totalMisc,
                    daysCount = safeDayCount,
                    isConfiguredInDb = monthsSeen.any { m -> fixedCostsList.any { it.monthKey == m } },
                    monthKeyDescription = desc,
                    dailyBurden = totalAll / safeDayCount,
                    staffMembers = staffMembersLatest
                )
                return Pair(breakdown, orders)
            } else {
                val todayMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
                val mCost = getFixedCostForMonthKey(todayMonthKey, fixedCostsList)
                return Pair(
                    ProratedFixedCostBreakdown(
                        total = mCost.totalFixedCost,
                        rent = mCost.rent,
                        electricity = mCost.electricity,
                        staffSalaries = mCost.staffSalaries,
                        staffCount = mCost.staffCount,
                        maidSalary = mCost.maidSalary,
                        miscellaneous = mCost.miscellaneous,
                        daysCount = 1,
                        isConfiguredInDb = false,
                        monthKeyDescription = todayMonthKey,
                        dailyBurden = mCost.dailyCost(30),
                        staffMembers = mCost.parseStaffMembers()
                    ),
                    orders
                )
            }
        }

        else -> {
            val todayMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
            val mCost = getFixedCostForMonthKey(todayMonthKey, fixedCostsList)
            return Pair(
                ProratedFixedCostBreakdown(
                    total = mCost.totalFixedCost,
                    rent = mCost.rent,
                    electricity = mCost.electricity,
                    staffSalaries = mCost.staffSalaries,
                    staffCount = mCost.staffCount,
                    maidSalary = mCost.maidSalary,
                    miscellaneous = mCost.miscellaneous,
                    daysCount = 1,
                    isConfiguredInDb = false,
                    monthKeyDescription = todayMonthKey,
                    dailyBurden = mCost.dailyCost(30),
                    staffMembers = mCost.parseStaffMembers()
                ),
                ordersList
            )
        }
    }
}

// ==================== SUB-COMPONENTS ====================

@Composable
private fun FormulaPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, DarkCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = MutedTextColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FinancialMiniCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, DarkCardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MutedTextColor,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MutedTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WaterfallRow(
    prefix: String,
    title: String,
    detail: String,
    amount: String,
    percentage: String,
    amountColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = prefix,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = amountColor,
                modifier = Modifier.width(22.dp)
            )
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MutedTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = percentage,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = MutedTextColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WaterfallSubtotalRow(
    title: String,
    amount: String,
    percentage: String,
    amountColor: Color
) {
    Surface(
        color = Color.White.copy(alpha = 0.05f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[=]",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = amountColor,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = percentage,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MutedTextColor,
                    modifier = Modifier.padding(end = 6.dp),
                    maxLines = 1
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = amountColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun WaterfallFinalRow(
    title: String,
    subtitle: String = "EBITDA",
    amount: String,
    percentage: String,
    amountColor: Color
) {
    Surface(
        color = amountColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, amountColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[=]",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = amountColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            color = amountColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = subtitle,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = amountColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "Bottom Line",
                            fontSize = 9.sp,
                            color = MutedTextColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = amountColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    maxLines = 1
                )
                Surface(
                    color = amountColor,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = percentage,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverheadItemRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    monthlyAmount: Double,
    proratedAmount: Double,
    dailyRate: Double,
    subDetail: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = StatusAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatInr(monthlyAmount)}/mo (₹${formatDecimal(dailyRate)}/day)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MutedTextColor,
                    maxLines = 1
                )
                if (!subDetail.isNullOrBlank()) {
                    Text(
                        text = subDetail,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        color = StatusAmber.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = formatInr(proratedAmount),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

// ==================== SET FIXED COSTS DIALOG ====================

@Composable
fun SetFixedCostsDialog(
    targetMonthKey: String,
    allConfiguredCosts: List<FixedCostEntity>,
    onDismiss: () -> Unit,
    onSave: (FixedCostEntity) -> Unit
) {
    var activeMonthKey by remember { mutableStateOf(targetMonthKey) }

    // When activeMonthKey changes, reload entity
    val resolvedEntity = remember(activeMonthKey, allConfiguredCosts) {
        getFixedCostForMonthKey(activeMonthKey, allConfiguredCosts)
    }

    var rentStr by remember(resolvedEntity) { mutableStateOf(formatDecimal(resolvedEntity.rent)) }
    var electricityStr by remember(resolvedEntity) { mutableStateOf(formatDecimal(resolvedEntity.electricity)) }
    var maidSalaryStr by remember(resolvedEntity) { mutableStateOf(formatDecimal(resolvedEntity.maidSalary)) }
    var miscStr by remember(resolvedEntity) { mutableStateOf(formatDecimal(resolvedEntity.miscellaneous)) }
    var notes by remember(resolvedEntity) { mutableStateOf(resolvedEntity.notes) }

    // Individual staff members state (name and salary for each person)
    var staffList by remember(resolvedEntity) {
        mutableStateOf(resolvedEntity.parseStaffMembers())
    }

    fun updateStaffMember(index: Int, name: String, salary: Double) {
        val updated = staffList.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(name = name, salary = salary)
            staffList = updated
        }
    }

    fun addStaffMember() {
        staffList = staffList + StaffMember(
            name = "Staff ${staffList.size + 1}",
            salary = 15000.0
        )
    }

    fun removeStaffMember(index: Int) {
        if (staffList.size > 1 && index in staffList.indices) {
            val updated = staffList.toMutableList()
            updated.removeAt(index)
            staffList = updated
        }
    }

    val rent = rentStr.toDoubleOrNull() ?: 0.0
    val electricity = electricityStr.toDoubleOrNull() ?: 0.0
    val staffSalaries = staffList.sumOf { it.salary }
    val staffCount = staffList.size
    val maidSalary = maidSalaryStr.toDoubleOrNull() ?: 0.0
    val misc = miscStr.toDoubleOrNull() ?: 0.0

    val totalMonthly = rent + electricity + staffSalaries + maidSalary + misc
    val daysInThisMonth = getDaysInMonthKey(activeMonthKey)
    val dailyBurden = if (daysInThisMonth > 0) totalMonthly / daysInThisMonth else 0.0
    val isSavedInDb = allConfiguredCosts.any { it.monthKey == activeMonthKey }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DarkCardBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Set Fixed Overheads",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Month-Wise Cost & Staff Salaries Setup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedTextColor
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = MutedTextColor)
                        }
                    }
                }

                // Month Switcher with Prev / Next Buttons
                item {
                    Surface(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isSavedInDb) StatusGreen.copy(alpha = 0.5f) else StatusAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { activeMonthKey = getPreviousMonthKey(activeMonthKey) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Prev", tint = StatusAmber)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = formatMonthDisplay(activeMonthKey),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (isSavedInDb) StatusGreen else StatusAmber,
                                            shape = CircleShape,
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isSavedInDb) "Saved in Database" else "Unconfigured (Default)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSavedInDb) StatusGreen else StatusAmber
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { activeMonthKey = getNextMonthKey(activeMonthKey) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Next", tint = StatusAmber)
                                }
                            }
                        }
                    }
                }

                // 5% GST Accounting Policy Banner
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusBlue.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, StatusBlue.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = StatusBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "5% Inclusive GST & Net Revenue Accounting",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusBlue,
                                    fontSize = 12.sp
                                )
                            }

                            Text(
                                text = "• 5% GST inclusive is automatically deducted at the backend from gross customer orders: Gross × (5.0 / 105.0).\n" +
                                       "• Net Cafe Revenue = Gross Billings − 5% GST Remittance.\n" +
                                       "• Operating Profit (EBITDA) = Net Revenue − Recipe COGS − Fixed Overheads.\n" +
                                       "• Policy Example: On ₹10,000 gross billing, ₹476.19 is 5% GST remittance and ₹9,523.81 is Net Operating Revenue.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Live Summary Banner
                item {
                    Surface(
                        color = StatusAmber.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Monthly Budget", style = MaterialTheme.typography.labelSmall, color = MutedTextColor)
                                Text(
                                    text = formatInr(totalMonthly),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = StatusAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Daily Burden ($daysInThisMonth days)", style = MaterialTheme.typography.labelSmall, color = MutedTextColor)
                                Text(
                                    text = "${formatInr(dailyBurden)}/day",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                // DEDICATED STAFF MEMBERS & SALARIES SECTION
                item {
                    Surface(
                        color = Color.White.copy(alpha = 0.03f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = null,
                                        tint = StatusAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Staff Members & Salaries",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Individual names & monthly salaries",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = MutedTextColor
                                        )
                                    }
                                }

                                Surface(
                                    color = StatusAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "${staffList.size} Staff • ${formatInr(staffSalaries)}/mo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = DarkCardBorder)

                            // List of individual staff members
                            staffList.forEachIndexed { index, member ->
                                Surface(
                                    color = DarkCardBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, DarkCardBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f, fill = false)
                                            ) {
                                                Surface(
                                                    color = StatusAmber.copy(alpha = 0.2f),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "${index + 1}",
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = StatusAmber
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (member.name.isNotBlank()) member.name else "Staff Member ${index + 1}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (staffList.size > 1) {
                                                IconButton(
                                                    onClick = { removeStaffMember(index) },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Remove Staff",
                                                        tint = StatusRed.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = member.name,
                                                onValueChange = { newName ->
                                                    updateStaffMember(index, newName, member.salary)
                                                },
                                                label = { Text("Staff Name", fontSize = 11.sp) },
                                                placeholder = { Text("e.g. Ramesh", fontSize = 11.sp) },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = StatusAmber,
                                                    unfocusedBorderColor = DarkCardBorder,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                ),
                                                modifier = Modifier.weight(0.55f)
                                            )

                                            OutlinedTextField(
                                                value = if (member.salary > 0) formatDecimal(member.salary) else "",
                                                onValueChange = { newSalaryStr ->
                                                    val newSalary = newSalaryStr.toDoubleOrNull() ?: 0.0
                                                    updateStaffMember(index, member.name, newSalary)
                                                },
                                                label = { Text("Salary (₹/mo)", fontSize = 11.sp) },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = StatusAmber,
                                                    unfocusedBorderColor = DarkCardBorder,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                ),
                                                modifier = Modifier.weight(0.45f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Staff Member Button
                            OutlinedButton(
                                onClick = { addStaffMember() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Staff Member",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Staff Member", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Other Fixed Costs Inputs
                item {
                    OutlinedTextField(
                        value = rentStr,
                        onValueChange = { rentStr = it },
                        label = { Text("Shop Rent (₹/month)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = electricityStr,
                        onValueChange = { electricityStr = it },
                        label = { Text("Electricity Bill (₹/month)") },
                        supportingText = {
                            Text("Covers fryer, griller & cafe appliances (excluded from COGS)", color = MutedTextColor, fontSize = 10.sp)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = maidSalaryStr,
                        onValueChange = { maidSalaryStr = it },
                        label = { Text("Maid / Cleaning (₹/month)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = miscStr,
                        onValueChange = { miscStr = it },
                        label = { Text("Misc & Maintenance (₹/month)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Month Notes") },
                        placeholder = { Text("e.g. Hired 1 helper, revised salary for this month") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StatusAmber,
                            unfocusedBorderColor = DarkCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                rentStr = "9000"
                                electricityStr = "6000"
                                maidSalaryStr = "2000"
                                miscStr = "5000"
                                staffList = listOf(
                                    StaffMember(name = "Barista 1", salary = 15000.0),
                                    StaffMember(name = "Barista 2", salary = 15000.0)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkCardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Defaults (₹52k)", color = MutedTextColor, fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                if (activeMonthKey.isNotBlank()) {
                                    val totalSalaries = staffList.sumOf { it.salary }
                                    val entity = FixedCostEntity(
                                        monthKey = activeMonthKey,
                                        rent = rent,
                                        electricity = electricity,
                                        staffCount = staffList.size,
                                        staffSalaries = totalSalaries,
                                        maidSalary = maidSalary,
                                        miscellaneous = misc,
                                        notes = notes,
                                        staffMembersJson = FixedCostEntity.staffListToJson(staffList),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    onSave(entity)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusAmber,
                                contentColor = Color(0xFF09090B)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save for $activeMonthKey", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Configured Historical Months in DB
                if (allConfiguredCosts.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = DarkCardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Configured Months in Database",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MutedTextColor
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            allConfiguredCosts.forEach { costRecord ->
                                val isSelected = costRecord.monthKey == activeMonthKey
                                val staffDetails = costRecord.parseStaffMembers()
                                Surface(
                                    color = if (isSelected) StatusAmber.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSelected) StatusAmber else DarkCardBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeMonthKey = costRecord.monthKey }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f, fill = false)) {
                                            Text(
                                                text = formatMonthDisplay(costRecord.monthKey),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "${costRecord.staffCount} staff (${staffDetails.joinToString { it.name }}) • Rent: ${formatInr(costRecord.rent)}",
                                                fontSize = 10.sp,
                                                color = MutedTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = formatInr(costRecord.totalFixedCost),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Black,
                                            color = StatusAmber,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== HELPER FUNCTIONS ====================

fun getDaysInMonthKey(monthKey: String): Int {
    return try {
        val parts = monthKey.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    } catch (e: Exception) {
        30
    }
}

fun formatMonthDisplay(monthKey: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM", Locale.US)
        val sdfOut = SimpleDateFormat("MMMM yyyy", Locale.US)
        val date = sdfIn.parse(monthKey)
        if (date != null) sdfOut.format(date) else monthKey
    } catch (e: Exception) {
        monthKey
    }
}

fun formatMonthShort(monthKey: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM", Locale.US)
        val sdfOut = SimpleDateFormat("MMM yy", Locale.US)
        val date = sdfIn.parse(monthKey)
        if (date != null) sdfOut.format(date) else monthKey
    } catch (e: Exception) {
        monthKey
    }
}

fun getPreviousMonthKey(monthKey: String): String {
    return try {
        val parts = monthKey.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        cal.add(Calendar.MONTH, -1)
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    } catch (e: Exception) {
        monthKey
    }
}

fun getNextMonthKey(monthKey: String): String {
    return try {
        val parts = monthKey.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt() - 1
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        cal.add(Calendar.MONTH, 1)
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    } catch (e: Exception) {
        monthKey
    }
}

fun getFixedCostForMonthKey(
    monthKey: String,
    fixedCostsList: List<FixedCostEntity>
): FixedCostEntity {
    // 1. Direct match in database
    val match = fixedCostsList.find { it.monthKey == monthKey }
    if (match != null) return match

    // 2. If not found, look for closest earlier month in DB to carry forward changes
    val earlier = fixedCostsList.filter { it.monthKey < monthKey }.maxByOrNull { it.monthKey }
    if (earlier != null) {
        return earlier.copy(
            monthKey = monthKey,
            notes = "Carried forward from ${earlier.monthKey}"
        )
    }

    // 3. Fallback to default FILTR overheads
    return FixedCostEntity(monthKey = monthKey)
}

private fun formatInr(value: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.maximumFractionDigits = 0
    return format.format(value)
}

private fun formatDecimal(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun formatQty(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
}


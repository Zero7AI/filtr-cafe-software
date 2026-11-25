package com.example.domain.kds

import com.example.data.entity.KdsItemClaimEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

enum class KdsUnitStage {
    PENDING,
    MAKING,
    MADE
}

object KdsBaristaColorPalette {

    val COLORS: List<String> = listOf(
        "#16A34A", // Green
        "#DC2626", // Red
        "#2563EB", // Blue
        "#D97706", // Amber
        "#7C3AED", // Violet
        "#0D9488", // Teal
        "#E11D48"  // Rose
    )

    private val VALID_HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

    fun resolveColorHex(staffName: String, customColorHex: String = ""): String {
        val trimmedCustom = customColorHex.trim()
        if (VALID_HEX_REGEX.matches(trimmedCustom)) {
            return trimmedCustom.uppercase(Locale.US)
        }
        val clean = staffName.trim().lowercase(Locale.US)
        if (clean.isEmpty()) return COLORS[0]
        val hash = abs(clean.hashCode().toLong())
        val index = (hash % COLORS.size).toInt()
        return COLORS[index]
    }

    fun parseComposeColor(hex: String, fallback: Long = 0xFF16A34AL): Long {
        val cleaned = hex.trim().removePrefix("#")
        return when (cleaned.length) {
            6 -> {
                val rgb = cleaned.toLongOrNull(16) ?: return fallback
                0xFF000000L or rgb
            }
            8 -> cleaned.toLongOrNull(16) ?: fallback
            else -> fallback
        }
    }
}

data class KdsQueueUnitItem(
    val claimKey: String,
    val queuePosition: Int,
    val orderId: String,
    val shortOrderNumber: String,
    val orderTimestamp: Long,
    val customerName: String,
    val orderNotes: String,
    val isPaid: Boolean,
    val paymentMode: String,
    val itemId: Int,
    val itemName: String,
    val unitIndex: Int,
    val totalUnits: Int,
    val stage: KdsUnitStage,
    val claimedByStaff: String,
    val claimedColorHex: String,
    val preparedByStaff: String
)

object KdsQueueBuilder {

    fun buildClaimKey(orderId: String, itemId: Int, unitIndex: Int): String =
        "${orderId}_${itemId}_${unitIndex}"

    fun extractShortOrderNumber(orderId: String): String {
        val idx = orderId.lastIndexOf('-')
        return if (idx >= 0 && idx < orderId.length - 1) {
            orderId.substring(idx + 1)
        } else {
            orderId
        }
    }

    fun parseDateKeyStartMillis(dateKey: String): Long? {
        val trimmed = dateKey.trim()
        if (trimmed.length != 10) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                isLenient = false
            }
            sdf.parse(trimmed)?.time
        } catch (_: Exception) {
            null
        }
    }

    fun buildFifoPendingQueue(
        orders: List<OrderWithItems>,
        claimsByKey: Map<String, KdsItemClaimEntity>,
        rosterByNormalizedName: Map<String, StaffRosterEntity>,
        paidOnlyFilter: Boolean = false,
        minTimestampMs: Long? = null
    ): List<KdsQueueUnitItem> {
        val sortedActiveOrders = orders
            .asSequence()
            .filter { !it.order.isMade && !it.order.isDelivered }
            .filter { minTimestampMs == null || it.order.timestamp >= minTimestampMs }
            .filter { !paidOnlyFilter || it.order.isPaid }
            .sortedWith(compareBy<OrderWithItems> { it.order.timestamp }.thenBy { it.order.orderId })
            .toList()

        val rawUnits = mutableListOf<KdsQueueUnitItem>()
        for (orderWithItems in sortedActiveOrders) {
            val order = orderWithItems.order
            val shortNumber = extractShortOrderNumber(order.orderId)

            for (item in orderWithItems.items) {
                for (unitIndex in 1..item.quantity) {
                    val claimKey = buildClaimKey(order.orderId, item.id, unitIndex)
                    val claim = claimsByKey[claimKey]
                    val isDone = unitIndex <= item.completedQuantity || claim?.status == "MADE"
                    if (isDone) continue

                    val isMaking = claim?.status == "MAKING"
                    val stage = if (isMaking) KdsUnitStage.MAKING else KdsUnitStage.PENDING
                    val claimedBy = if (isMaking) claim?.staffName.orEmpty() else ""
                    val claimedColor = if (isMaking) {
                        val rosterMember = findRosterMember(claimedBy, rosterByNormalizedName)
                        val customOrRosterColor = claim?.colorHex.orEmpty().ifBlank {
                            rosterMember?.colorHex.orEmpty()
                        }
                        KdsBaristaColorPalette.resolveColorHex(claimedBy, customOrRosterColor)
                    } else {
                        ""
                    }

                    rawUnits.add(
                        KdsQueueUnitItem(
                            claimKey = claimKey,
                            queuePosition = 0,
                            orderId = order.orderId,
                            shortOrderNumber = shortNumber,
                            orderTimestamp = order.timestamp,
                            customerName = order.customerName,
                            orderNotes = order.notes,
                            isPaid = order.isPaid,
                            paymentMode = order.paymentMode,
                            itemId = item.id,
                            itemName = item.itemName,
                            unitIndex = unitIndex,
                            totalUnits = item.quantity,
                            stage = stage,
                            claimedByStaff = claimedBy,
                            claimedColorHex = claimedColor,
                            preparedByStaff = ""
                        )
                    )
                }
            }
        }

        return rawUnits.mapIndexed { index, unit ->
            unit.copy(queuePosition = index + 1)
        }
    }

    fun resolveUnitDisplayState(
        order: OrderEntity,
        item: OrderItemEntity,
        unitIndex: Int,
        claim: KdsItemClaimEntity?
    ): Pair<KdsUnitStage, String> {
        if (order.isMade || order.isDelivered || unitIndex <= item.completedQuantity || claim?.status == "MADE") {
            return KdsUnitStage.MADE to claim?.staffName.orEmpty()
        }
        if (claim?.status == "MAKING") {
            return KdsUnitStage.MAKING to claim.staffName
        }
        return KdsUnitStage.PENDING to ""
    }

    private fun findRosterMember(
        staffName: String,
        rosterByNormalizedName: Map<String, StaffRosterEntity>
    ): StaffRosterEntity? {
        if (staffName.isBlank() || rosterByNormalizedName.isEmpty()) return null
        val normalizedKey = AttendanceCalculator.normalizeStaffKey(staffName)
        return rosterByNormalizedName[normalizedKey]
            ?: rosterByNormalizedName[staffName.trim().lowercase(Locale.US)]
    }
}

package com.example.domain.kds

import com.example.data.entity.KdsItemClaimEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import com.example.ui.FiltrTab
import com.example.ui.STAFF_ALLOWED_TABS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KdsDomainTest {

    @Test
    fun `staff allowed tabs include KDS and exclude removed Manual Inventory`() {
        assertTrue(FiltrTab.BILLING in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.ORDERS in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.KDS in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.CHECKLIST in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.KITCHEN_INVENTORY in STAFF_ALLOWED_TABS)
        assertTrue(FiltrTab.ATTENDANCE in STAFF_ALLOWED_TABS)
        assertFalse(FiltrTab.values().any { it.name == "MANUAL_INVENTORY" })
    }

    @Test
    fun `fifo queue orders oldest pending units first with sequential queue positions`() {
        val olderOrder = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261126-1001",
                customerName = "Aarav",
                contactNumber = "",
                totalAmount = 200.0,
                timestamp = 1_000L,
                isMade = false,
                isPaid = false,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = "Less sugar"
            ),
            items = listOf(
                OrderItemEntity(
                    id = 10,
                    orderId = "FLT-20261126-1001",
                    menuItemId = 1,
                    itemName = "Vietnamese Iced Coffee",
                    itemPrice = 100.0,
                    quantity = 2,
                    subtotal = 200.0,
                    completedQuantity = 1
                )
            )
        )
        val newerPaidOrder = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261126-1002",
                customerName = "Diya",
                contactNumber = "",
                totalAmount = 120.0,
                timestamp = 2_000L,
                isMade = false,
                isPaid = true,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = "",
                paymentMode = "UPI"
            ),
            items = listOf(
                OrderItemEntity(
                    id = 20,
                    orderId = "FLT-20261126-1002",
                    menuItemId = 2,
                    itemName = "Classic Cold Coffee",
                    itemPrice = 120.0,
                    quantity = 1,
                    subtotal = 120.0,
                    completedQuantity = 0
                )
            )
        )
        val alreadyMadeOrder = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261126-1000",
                customerName = "Old",
                contactNumber = "",
                totalAmount = 90.0,
                timestamp = 500L,
                isMade = true,
                isPaid = true,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = ""
            ),
            items = listOf(
                OrderItemEntity(
                    id = 5,
                    orderId = "FLT-20261126-1000",
                    menuItemId = 1,
                    itemName = "Espresso",
                    itemPrice = 90.0,
                    quantity = 1,
                    subtotal = 90.0,
                    completedQuantity = 1
                )
            )
        )

        val claimKey = KdsQueueBuilder.buildClaimKey("FLT-20261126-1001", 10, 2)
        val claims = mapOf(
            claimKey to KdsItemClaimEntity(
                key = claimKey,
                orderId = "FLT-20261126-1001",
                itemId = 10,
                itemName = "Vietnamese Iced Coffee",
                unitIndex = 2,
                status = "MAKING",
                staffName = "Somesh",
                colorHex = "#16A34A",
                updatedAt = 1_500L
            )
        )

        val queue = KdsQueueBuilder.buildFifoPendingQueue(
            orders = listOf(newerPaidOrder, alreadyMadeOrder, olderOrder),
            claimsByKey = claims,
            rosterByNormalizedName = emptyMap(),
            paidOnlyFilter = false
        )

        // Older order's unit 1 was completedQuantity=1 so skipped; unit 2 (#1) then newer order (#2)
        assertEquals(2, queue.size)
        assertEquals(1, queue[0].queuePosition)
        assertEquals("1001", queue[0].shortOrderNumber)
        assertEquals(2, queue[0].unitIndex)
        assertEquals(KdsUnitStage.MAKING, queue[0].stage)
        assertEquals("Somesh", queue[0].claimedByStaff)
        assertEquals("#16A34A", queue[0].claimedColorHex)
        assertEquals("Less sugar", queue[0].orderNotes)

        assertEquals(2, queue[1].queuePosition)
        assertEquals("1002", queue[1].shortOrderNumber)
        assertEquals(KdsUnitStage.PENDING, queue[1].stage)
        assertTrue(queue[1].isPaid)

        // Paid-only filter keeps only paid order #1002
        val paidOnlyQueue = KdsQueueBuilder.buildFifoPendingQueue(
            orders = listOf(newerPaidOrder, olderOrder),
            claimsByKey = claims,
            rosterByNormalizedName = emptyMap(),
            paidOnlyFilter = true
        )
        assertEquals(1, paidOnlyQueue.size)
        assertEquals("1002", paidOnlyQueue[0].shortOrderNumber)
        assertEquals(1, paidOnlyQueue[0].queuePosition)

        // Today filter excludes older orders before midnight minTimestampMs
        val todayFilteredQueue = KdsQueueBuilder.buildFifoPendingQueue(
            orders = listOf(newerPaidOrder, olderOrder),
            claimsByKey = claims,
            rosterByNormalizedName = emptyMap(),
            paidOnlyFilter = false,
            minTimestampMs = 1_500L
        )
        assertEquals(1, todayFilteredQueue.size)
        assertEquals("1002", todayFilteredQueue[0].shortOrderNumber)
        assertTrue(KdsQueueBuilder.parseDateKeyStartMillis("2026-11-26") != null)
    }

    @Test
    fun `barista color palette resolves roster custom color and deterministic fallback`() {
        val roster = mapOf(
            AttendanceCalculator.normalizeStaffKey("Kanha") to StaffRosterEntity(
                id = "kanha",
                name = "Kanha",
                colorHex = "#DC2626"
            )
        )
        assertEquals("#DC2626", KdsBaristaColorPalette.resolveColorHex("Kanha", roster["kanha"]?.colorHex.orEmpty()))
        val someshFirst = KdsBaristaColorPalette.resolveColorHex("Somesh")
        val someshSecond = KdsBaristaColorPalette.resolveColorHex("somesh")
        assertEquals(someshFirst, someshSecond)
        assertTrue(someshFirst.startsWith("#"))
        assertEquals(0xFFDC2626L, KdsBaristaColorPalette.parseComposeColor("#DC2626"))
    }
}

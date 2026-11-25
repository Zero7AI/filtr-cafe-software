package com.example.kitchen.repository

import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import com.example.kitchen.data.remote.GoogleSheetsSyncService
import com.example.kitchen.data.remote.SupabaseService
import com.example.kitchen.testutil.FakeCustomerDao
import com.example.kitchen.testutil.FakeMenuDao
import com.example.kitchen.testutil.FakeOrderDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitchenRepositoryTest {

    private data class RecordedRequest(val method: String, val url: String, val body: String)

    private fun createSupabase(
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        handler: (RecordedRequest) -> Pair<Int, String>
    ): Pair<SupabaseService, MutableList<RecordedRequest>> {
        val requests = mutableListOf<RecordedRequest>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                val buf = Buffer()
                req.body?.writeTo(buf)
                val recorded = RecordedRequest(req.method, req.url.toString(), buf.readUtf8())
                synchronized(requests) { requests.add(recorded) }
                val (code, body) = handler(recorded)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Mock")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        return SupabaseService(
            baseUrl = "https://test.supabase.co",
            clientOverride = client,
            ioDispatcher = ioDispatcher
        ) to requests
    }

    private fun todayDatePart(): String = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())

    @Test
    fun createOrder_returnsImmediatelyWithoutWaitingForBackgroundNetwork() = runTest {
        val networkStarted = CountDownLatch(1)
        val allowNetworkFinish = CountDownLatch(1)
        val seqCounter = AtomicInteger(1000)

        val (supabase, _) = createSupabase { req ->
            when {
                req.url.contains("get_next_order_sequence") -> 200 to seqCounter.incrementAndGet().toString()
                req.url.endsWith("/rest/v1/orders") -> {
                    networkStarted.countDown()
                    allowNetworkFinish.await(5, TimeUnit.SECONDS)
                    201 to ""
                }
                else -> 201 to ""
            }
        }

        val orderDao = FakeOrderDao()
        val bgDispatcher = StandardTestDispatcher(testScheduler)
        val bgScope = TestScope(bgDispatcher)
        val repo = KitchenRepository(
            orderDao = orderDao,
            menuDao = FakeMenuDao(),
            customerDao = FakeCustomerDao(),
            supabaseService = supabase,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            externalSyncScope = bgScope
        )

        val item = MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 79.0)
        // Advance only the createOrder coroutine; backgroundScope uses a separate scheduler so createOrder must return first
        val result = repo.createOrder(
            customerName = "  Priya  ",
            contactNumber = " 9876543210 ",
            cartItems = mapOf(item to 2),
            notes = " Less ice ",
            paymentMode = "CASH",
            isPaid = true
        )

        assertTrue(result.isSuccess)
        val created = result.getOrThrow()
        assertEquals("Priya", created.order.customerName)
        assertEquals("9876543210", created.order.contactNumber)
        assertEquals("Less ice", created.order.notes)
        assertEquals(158.0, created.order.totalAmount, 0.001)
        // Persisted locally immediately even before background network finishes
        assertNotNull(orderDao.getOrderById(created.order.orderId))
        allowNetworkFinish.countDown()
        bgScope.advanceUntilIdle()
    }

    @Test
    fun createOrder_repeatCustomerDoesNotDoubleCountStatsInSupabase() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val customerPayloads = mutableListOf<JSONObject>()
        val seqCounter = AtomicInteger(1000)
        val (supabase, _) = createSupabase(ioDispatcher = dispatcher) { req ->
            when {
                req.url.contains("get_next_order_sequence") -> 200 to seqCounter.incrementAndGet().toString()
                req.url.contains("/rest/v1/customers") -> {
                    synchronized(customerPayloads) { customerPayloads.add(JSONObject(req.body)) }
                    201 to ""
                }
                else -> 201 to ""
            }
        }

        val customerDao = FakeCustomerDao()
        customerDao.insertCustomer(
            CustomerEntity(
                id = 1,
                name = "Kiran",
                contactNumber = "9000011111",
                totalOrders = 1,
                totalSpent = 100.0,
                lastOrderTimestamp = 500L,
                createdAt = 500L
            )
        )

        val repo = KitchenRepository(
            orderDao = FakeOrderDao(),
            menuDao = FakeMenuDao(),
            customerDao = customerDao,
            supabaseService = supabase,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = dispatcher,
            externalSyncScope = this
        )

        val item = MenuItemEntity(id = 1, name = "OG Vietnamese", category = "ICED COFFEE", price = 109.0)
        val res = repo.createOrder(
            customerName = " Kiran ",
            contactNumber = " 9000011111 ",
            cartItems = mapOf(item to 2),
            notes = "",
            paymentMode = "UPI",
            isPaid = false
        )
        assertTrue(res.isSuccess)
        advanceUntilIdle()

        val localCustomer = customerDao.getCustomerByName("Kiran")!!
        assertEquals(2, localCustomer.totalOrders)
        assertEquals(318.0, localCustomer.totalSpent, 0.001)

        assertEquals(1, customerPayloads.size)
        assertEquals(2, customerPayloads[0].getInt("total_orders"))
        assertEquals(318.0, customerPayloads[0].getDouble("total_spent"), 0.001)
    }

    @Test
    fun generateOrderId_serializesConcurrentCallsAndRetriesRpcCollision() = runTest {
        val datePart = todayDatePart()
        val orderDao = FakeOrderDao()
        orderDao.insertOrder(
            OrderEntity(
                orderId = "FLT-$datePart-1001",
                customerName = "Existing",
                totalAmount = 79.0,
                timestamp = System.currentTimeMillis()
            )
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val rpcSeq = AtomicInteger(1000)
        val (supabase, _) = createSupabase(ioDispatcher = dispatcher) { req ->
            when {
                req.url.contains("get_next_order_sequence") -> 200 to rpcSeq.incrementAndGet().toString()
                else -> 200 to "[]"
            }
        }

        val repo = KitchenRepository(
            orderDao = orderDao,
            menuDao = FakeMenuDao(),
            customerDao = FakeCustomerDao(),
            supabaseService = supabase,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = dispatcher,
            externalSyncScope = this
        )

        val ids = (1..5).map {
            async { repo.generateOrderId() }
        }.awaitAll()

        assertEquals(5, ids.toSet().size)
        assertFalse(ids.contains("FLT-$datePart-1001"))
    }

    @Test
    fun markOrderReady_pushesOrderAndItemsAndRetainsPendingOnCloudFailure() = runTest {
        val orderId = "FLT-${todayDatePart()}-1005"
        val orderDao = FakeOrderDao()
        orderDao.insertOrderWithItems(
            OrderEntity(
                orderId = orderId,
                customerName = "Sia",
                totalAmount = 218.0,
                timestamp = System.currentTimeMillis(),
                syncStatus = "SYNCED"
            ),
            listOf(
                OrderItemEntity(
                    orderId = orderId,
                    menuItemId = 1,
                    itemName = "OG Vietnamese",
                    itemPrice = 109.0,
                    quantity = 2,
                    subtotal = 218.0
                )
            )
        )

        val dispatcher = StandardTestDispatcher(testScheduler)
        val (failingSupa, _) = createSupabase(ioDispatcher = dispatcher) { 500 to "offline" }
        val failingRepo = KitchenRepository(
            orderDao = orderDao,
            menuDao = FakeMenuDao(),
            customerDao = FakeCustomerDao(),
            supabaseService = failingSupa,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = dispatcher,
            externalSyncScope = this
        )

        val offlineResult = failingRepo.markOrderReady(orderId)
        assertTrue(offlineResult.isSuccess)
        val afterOffline = orderDao.getOrderById(orderId)!!
        assertTrue(afterOffline.order.isMade)
        assertEquals(2, afterOffline.items[0].completedQuantity)
        assertEquals("PENDING", afterOffline.order.syncStatus)

        val (okSupa, okRequests) = createSupabase(ioDispatcher = dispatcher) { 204 to "" }
        val okRepo = KitchenRepository(
            orderDao = orderDao,
            menuDao = FakeMenuDao(),
            customerDao = FakeCustomerDao(),
            supabaseService = okSupa,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = dispatcher,
            externalSyncScope = this
        )

        val syncedCount = okRepo.syncUnsyncedOrders()
        assertEquals(1, syncedCount)
        assertEquals("SYNCED", orderDao.getOrderById(orderId)!!.order.syncStatus)

        // Also test direct online markOrderReady patches both orders and order_items
        val orderId2 = "FLT-${todayDatePart()}-1006"
        orderDao.insertOrderWithItems(
            OrderEntity(orderId = orderId2, customerName = "Dev", totalAmount = 99.0, timestamp = System.currentTimeMillis()),
            listOf(OrderItemEntity(orderId = orderId2, menuItemId = 2, itemName = "Salted Fries", itemPrice = 89.0, quantity = 1, subtotal = 89.0))
        )
        okRequests.clear()
        val onlineResult = okRepo.markOrderReady(orderId2)
        assertTrue(onlineResult.isSuccess)
        assertEquals("SYNCED", orderDao.getOrderById(orderId2)!!.order.syncStatus)
        assertTrue(okRequests.any { it.method == "PATCH" && it.url.contains("/rest/v1/orders?order_id=eq.$orderId2") })
        assertTrue(okRequests.any { it.method == "PATCH" && it.url.contains("/rest/v1/order_items?order_id=eq.$orderId2") })
    }

    @Test
    fun reconcileRemoteOrders_guardsZeroItemsSyncsFieldsAndPrunesDeletedOrdersAndMenu() = runTest {
        val now = System.currentTimeMillis()
        val orderDao = FakeOrderDao()
        val menuDao = FakeMenuDao(
            listOf(
                MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 79.0),
                MenuItemEntity(id = 99, name = "Obsolete Item", category = "OLD", price = 50.0)
            )
        )

        // Existing SYNCED today order that was deleted remotely
        orderDao.insertOrderWithItems(
            OrderEntity(orderId = "FLT-DEL", customerName = "Deleted", totalAmount = 79.0, timestamp = now, syncStatus = "SYNCED"),
            listOf(OrderItemEntity(orderId = "FLT-DEL", menuItemId = 1, itemName = "Americano", itemPrice = 79.0, quantity = 1, subtotal = 79.0))
        )
        // Existing PENDING offline order that must NOT be pruned
        orderDao.insertOrderWithItems(
            OrderEntity(orderId = "FLT-OFFLINE", customerName = "Offline", totalAmount = 79.0, timestamp = now, syncStatus = "PENDING"),
            listOf(OrderItemEntity(orderId = "FLT-OFFLINE", menuItemId = 1, itemName = "Americano", itemPrice = 79.0, quantity = 1, subtotal = 79.0))
        )
        // Existing SYNCED order whose paymentMode/notes/customerName changed remotely
        orderDao.insertOrderWithItems(
            OrderEntity(
                orderId = "FLT-EDIT",
                customerName = "Old Name",
                totalAmount = 79.0,
                timestamp = now,
                syncStatus = "SYNCED",
                paymentMode = "UNPAID",
                notes = ""
            ),
            listOf(OrderItemEntity(orderId = "FLT-EDIT", menuItemId = 1, itemName = "Americano", itemPrice = 79.0, quantity = 1, subtotal = 79.0))
        )

        val (supabase, _) = createSupabase { 200 to "[]" }
        val repo = KitchenRepository(
            orderDao = orderDao,
            menuDao = menuDao,
            customerDao = FakeCustomerDao(),
            supabaseService = supabase,
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
            externalSyncScope = this
        )

        val zeroItemRemote = OrderWithItems(
            order = OrderEntity(orderId = "FLT-PARTIAL", customerName = "Partial", totalAmount = 99.0, timestamp = now),
            items = emptyList()
        )
        val editedRemote = OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-EDIT",
                customerName = "Updated Name",
                contactNumber = "9999988888",
                totalAmount = 79.0,
                timestamp = now,
                isPaid = true,
                paymentMode = "UPI",
                notes = "Extra shot"
            ),
            items = listOf(OrderItemEntity(orderId = "FLT-EDIT", menuItemId = 1, itemName = "Americano", itemPrice = 79.0, quantity = 1, subtotal = 79.0))
        )

        repo.reconcileRemoteOrders(listOf(zeroItemRemote, editedRemote), startOfTodayMs = now - 60_000L)

        // Zero-item remote order is ignored
        assertNull(orderDao.getOrderById("FLT-PARTIAL"))
        // Deleted synced order is pruned
        assertNull(orderDao.getOrderById("FLT-DEL"))
        // Pending offline order is preserved
        assertNotNull(orderDao.getOrderById("FLT-OFFLINE"))
        // Updated status fields are reconciled
        val reconciled = orderDao.getOrderById("FLT-EDIT")!!
        assertEquals("Updated Name", reconciled.order.customerName)
        assertEquals("UPI", reconciled.order.paymentMode)
        assertEquals("Extra shot", reconciled.order.notes)
        assertTrue(reconciled.order.isPaid)

        // Menu pruning
        repo.reconcileRemoteMenuItems(
            listOf(MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 89.0))
        )
        val localMenu = menuDao.getAllMenuItemsList()
        assertEquals(1, localMenu.size)
        assertEquals(89.0, localMenu[0].price, 0.001)
    }
}

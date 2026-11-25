package com.example.kitchen.data.remote

import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupabaseServiceTest {

    private data class RecordedCall(
        val method: String,
        val url: String,
        val body: String
    )

    private fun createService(
        handler: (RecordedCall) -> Pair<Int, String>
    ): Pair<SupabaseService, MutableList<RecordedCall>> {
        val calls = mutableListOf<RecordedCall>()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req: Request = chain.request()
                val bodyBuffer = Buffer()
                req.body?.writeTo(bodyBuffer)
                val recorded = RecordedCall(
                    method = req.method,
                    url = req.url.toString(),
                    body = bodyBuffer.readUtf8()
                )
                calls.add(recorded)
                val (code, respBody) = handler(recorded)
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (code in 200..299) "OK" else "Error")
                    .body(respBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val service = SupabaseService(
            baseUrl = "https://test.supabase.co",
            clientOverride = client
        )
        return service to calls
    }

    private fun sampleOrder(): OrderWithItems {
        val order = OrderEntity(
            orderId = "FLT-20261125-1001",
            customerName = "Rahul",
            contactNumber = "9876543210",
            totalAmount = 208.0,
            timestamp = 1700000000000L,
            isMade = false,
            isPaid = true,
            isDelivered = false,
            syncStatus = "PENDING",
            notes = "Less sugar",
            paymentMode = "CASH"
        )
        val items = listOf(
            OrderItemEntity(
                id = 1,
                orderId = order.orderId,
                menuItemId = 10,
                itemName = "Hazelnut Iced Coffee",
                itemPrice = 99.0,
                quantity = 1,
                subtotal = 99.0
            ),
            OrderItemEntity(
                id = 2,
                orderId = order.orderId,
                menuItemId = 11,
                itemName = "OG Vietnamese",
                itemPrice = 109.0,
                quantity = 1,
                subtotal = 109.0
            )
        )
        return OrderWithItems(order, items)
    }

    @Test
    fun checkReachability_returnsTrueOn200AndFalseOn500() = runTest {
        val (okService, _) = createService { 200 to "[]" }
        assertTrue(okService.checkReachability())

        val (errService, _) = createService { 500 to """{"message":"db down"}""" }
        assertFalse(errService.checkReachability())
    }

    @Test
    fun saveOrder_returnsFailureWhenAnyHttpStepFails() = runTest {
        val (failUpsert, _) = createService { call ->
            if (call.url.endsWith("/rest/v1/orders")) 500 to "error" else 201 to ""
        }
        assertTrue(failUpsert.saveOrder(sampleOrder()).isFailure)

        val (failDeleteItems, _) = createService { call ->
            if (call.method == "DELETE") 503 to "error" else 201 to ""
        }
        assertTrue(failDeleteItems.saveOrder(sampleOrder()).isFailure)

        val (failInsertItems, _) = createService { call ->
            if (call.url.endsWith("/rest/v1/order_items") && call.method == "POST") 400 to "bad" else 200 to ""
        }
        assertTrue(failInsertItems.saveOrder(sampleOrder()).isFailure)

        val (allOk, calls) = createService { 201 to "" }
        assertTrue(allOk.saveOrder(sampleOrder()).isSuccess)
        assertEquals(3, calls.size)
        val upsertJson = JSONObject(calls[0].body)
        assertEquals("SYNCED", upsertJson.getString("sync_status"))
    }

    @Test
    fun updateOrderFields_andUpdateOrderItemCompletion_checkHttpStatus() = runTest {
        val (errService, _) = createService { 500 to "err" }
        assertTrue(errService.markOrderMade("FLT-20261125-1001", 1234L).isFailure)
        assertTrue(errService.updateOrderItemCompletion("FLT-20261125-1001", "OG Vietnamese", 2, 1234L).isFailure)

        val (okService, calls) = createService { 204 to "" }
        assertTrue(okService.markOrderMade("FLT-20261125-1001", 1234L).isSuccess)
        assertTrue(okService.updateOrderItemCompletion("FLT-20261125-1001", "OG Vietnamese", 2, 1234L).isSuccess)

        val orderPatch = JSONObject(calls[0].body)
        assertTrue(orderPatch.getBoolean("is_made"))
        assertEquals(1234L, orderPatch.getLong("completed_timestamp"))
        assertEquals("SYNCED", orderPatch.getString("sync_status"))

        assertTrue(calls[1].url.contains("item_name=eq.OG%20Vietnamese"))
        val itemPatch = JSONObject(calls[1].body)
        assertEquals(2, itemPatch.getInt("completed_quantity"))
        assertEquals(1234L, itemPatch.getLong("completed_timestamp"))
    }

    @Test
    fun getNextOrderSequence_returnsFailureOnErrorOrNonNumericBody() = runTest {
        val (errService, _) = createService { 500 to "error" }
        assertTrue(errService.getNextOrderSequence("20261125").isFailure)

        val (jsonErrService, _) = createService { 200 to """{"message":"bad rpc"}""" }
        assertTrue(jsonErrService.getNextOrderSequence("20261125").isFailure)

        val (okService, _) = createService { 200 to "1042" }
        assertEquals(1042, okService.getNextOrderSequence("20261125").getOrNull())
    }

    @Test
    fun getTodayMaxOrderSequence_picksNumericMaxAbove9999() = runTest {
        val rows = JSONArray().apply {
            put(JSONObject().put("order_id", "FLT-20261125-9999"))
            put(JSONObject().put("order_id", "FLT-20261125-10000"))
            put(JSONObject().put("order_id", "FLT-20261125-10002"))
        }.toString()
        val (service, _) = createService { 200 to rows }
        assertEquals(10002, service.getTodayMaxOrderSequence("20261125"))
    }

    @Test
    fun saveCustomer_usesOnConflictNameOmitsIdAndChecksHttpStatus() = runTest {
        val customer = CustomerEntity(
            id = 99,
            name = "  Aarav  ",
            contactNumber = " 9988776655 ",
            totalOrders = 2,
            totalSpent = 250.0,
            lastOrderTimestamp = 1000L,
            createdAt = 500L
        )
        val (failService, _) = createService { 409 to "conflict" }
        assertTrue(failService.saveCustomer(customer).isFailure)

        val (okService, calls) = createService { 201 to "" }
        assertTrue(okService.saveCustomer(customer).isSuccess)
        assertTrue(calls[0].url.endsWith("/rest/v1/customers?on_conflict=name"))
        val sent = JSONObject(calls[0].body)
        assertFalse(sent.has("id"))
        assertEquals("Aarav", sent.getString("name"))
        assertEquals("9988776655", sent.getString("contact_number"))
    }

    @Test
    fun jsonConversion_sanitizesSqlNullValues() = runTest {
        val ordersJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("order_id", "FLT-20261125-1010")
                    put("customer_name", JSONObject.NULL)
                    put("contact_number", "null")
                    put("total_amount", 99.0)
                    put("timestamp", 1000L)
                    put("notes", JSONObject.NULL)
                    put("payment_mode", JSONObject.NULL)
                    put(
                        "order_items",
                        JSONArray().put(
                            JSONObject().apply {
                                put("id", 5)
                                put("menu_item_id", 2)
                                put("item_name", "Vanila Iced Coffee")
                                put("item_price", 99.0)
                                put("quantity", 1)
                                put("subtotal", 99.0)
                            }
                        )
                    )
                }
            )
        }.toString()

        val menuJson = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("id", 2)
                    put("name", "Vanila Iced Coffee")
                    put("category", "ICED COFFEE")
                    put("price", 99.0)
                    put("description", JSONObject.NULL)
                    put("is_available", true)
                }
            )
        }.toString()

        val (service, _) = createService { call ->
            when {
                call.url.contains("/rest/v1/orders") -> 200 to ordersJson
                call.url.contains("/rest/v1/menu_items") -> 200 to menuJson
                else -> 404 to ""
            }
        }

        val orders = service.fetchOrdersSince(0L)!!
        assertEquals(1, orders.size)
        assertEquals("Walk-in Customer", orders[0].order.customerName)
        assertEquals("", orders[0].order.contactNumber)
        assertEquals("", orders[0].order.notes)
        assertEquals("UNPAID", orders[0].order.paymentMode)

        val menu = service.fetchMenuItems()
        assertEquals(1, menu.size)
        assertEquals("", menu[0].description)
    }

    @Test
    fun fetchOrdersSince_paginatesAcross200ItemPages() = runTest {
        fun makePage(startIdx: Int, count: Int): String {
            val arr = JSONArray()
            for (i in 0 until count) {
                val n = startIdx + i
                arr.put(
                    JSONObject().apply {
                        put("order_id", "FLT-20261125-${1000 + n}")
                        put("customer_name", "C$n")
                        put("total_amount", 100.0)
                        put("timestamp", 1000L + n)
                        put("order_items", JSONArray())
                    }
                )
            }
            return arr.toString()
        }

        val (service, calls) = createService { call ->
            if (call.url.contains("offset=0")) 200 to makePage(0, 200)
            else 200 to makePage(200, 15)
        }

        val all = service.listenToOrders(intervalMs = 10L).first()
        assertEquals(215, all.size)
        assertEquals(2, calls.size)
        assertTrue(calls[0].url.contains("limit=200&offset=0"))
        assertTrue(calls[1].url.contains("limit=200&offset=200"))
    }
}

package com.example.kitchen.data.remote

import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleSheetsSyncServiceTest {

    private fun sampleOrder(): OrderWithItems {
        val ts = 1700000000000L
        val completedTs = ts + 90_000L
        return OrderWithItems(
            order = OrderEntity(
                orderId = "FLT-20261125-1005",
                customerName = "Riya",
                contactNumber = "",
                totalAmount = 198.0,
                timestamp = ts,
                isMade = true,
                isPaid = true,
                isDelivered = false,
                syncStatus = "PENDING",
                notes = "Extra ice",
                paymentMode = "UPI",
                completedTimestamp = completedTs
            ),
            items = listOf(
                OrderItemEntity(
                    id = 1,
                    orderId = "FLT-20261125-1005",
                    menuItemId = 2,
                    itemName = "Caramel Iced Coffee",
                    itemPrice = 99.0,
                    quantity = 2,
                    subtotal = 198.0,
                    completedQuantity = 2,
                    completedTimestamp = completedTs
                )
            )
        )
    }

    @Test
    fun syncSingleOrder_retriesOn5xxAndAccepts302() = runTest {
        val attempts = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .followRedirects(false)
            .addInterceptor(Interceptor { chain ->
                val attempt = attempts.incrementAndGet()
                val code = if (attempt == 1) 503 else 302
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("Status $code")
                    .body("""{"result":"success"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = GoogleSheetsSyncService(client)
        val result = service.syncSingleOrder("https://script.google.com/macros/s/test/exec", sampleOrder())

        assertTrue(result.isSuccess)
        assertEquals(2, attempts.get())
    }

    @Test
    fun syncSingleOrder_failsWhenWebhookReturnsJsonError() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val buf = Buffer()
                chain.request().body?.writeTo(buf)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"result":"error","message":"sheet missing"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val service = GoogleSheetsSyncService(client)
        val result = service.syncSingleOrder("https://script.google.com/macros/s/test/exec", sampleOrder())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("sheet missing") == true)
    }

    @Test
    fun buildOrderJsonObject_populatesV5FieldsAndTurnaround() {
        val service = GoogleSheetsSyncService()
        val json = service.buildOrderJsonObject(sampleOrder())

        assertEquals("syncOrder", json.getString("action"))
        assertEquals("N/A", json.getString("contactNumber"))
        assertEquals("2x Caramel Iced Coffee", json.getString("itemsSummary"))
        assertEquals("YES", json.getString("isMade"))
        assertTrue(json.getString("completedTimestamp").isNotBlank())

        val item = json.getJSONArray("items").getJSONObject(0)
        assertEquals("General", item.getString("category"))
        assertEquals("READY", item.getString("itemStatus"))
        assertEquals("1m 30s", item.getString("turnaroundTime"))
        assertEquals(2, item.getInt("qty"))
        assertEquals(99.0, item.getDouble("price"), 0.001)
    }
}

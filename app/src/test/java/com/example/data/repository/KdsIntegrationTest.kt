package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.FiltrDatabase
import com.example.data.entity.KdsItemClaimEntity
import com.example.data.entity.OrderEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.remote.SupabaseService
import com.example.domain.kds.KdsQueueBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KdsIntegrationTest {

    private lateinit var db: FiltrDatabase
    private lateinit var repository: FiltrRepository
    private lateinit var repoScope: CoroutineScope
    private lateinit var fakeMetadataStore: ConcurrentHashMap<String, String>
    private lateinit var fakeSupabaseService: SupabaseService

    private fun createIsolatedSupabaseService(store: ConcurrentHashMap<String, String>): SupabaseService {
        val constructor = SupabaseService::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        val service = constructor.newInstance()

        val jsonType = "application/json; charset=utf-8".toMediaType()
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            if (request.method == "GET") {
                val rawQuery = request.url.encodedQuery.orEmpty()
                val keyParam = rawQuery.split("&")
                    .firstOrNull { it.startsWith("key=eq.") }
                    ?.removePrefix("key=eq.")
                    ?.let { URLDecoder.decode(it, "UTF-8") }
                    .orEmpty()
                val stored = store[keyParam]
                val bodyJson = if (stored != null) {
                    JSONArray().put(
                        JSONObject().apply {
                            put("key", keyParam)
                            put("value", stored)
                        }
                    ).toString()
                } else {
                    "[]"
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyJson.toResponseBody(jsonType))
                    .build()
            } else {
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                val bodyText = buffer.readUtf8()
                if (bodyText.startsWith("{")) {
                    val bodyObj = JSONObject(bodyText)
                    if (bodyObj.has("key") && bodyObj.has("value")) {
                        store[bodyObj.getString("key")] = bodyObj.getString("value")
                    }
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody(jsonType))
                    .build()
            }
        }

        val okClient = OkHttpClient.Builder().addInterceptor(interceptor).build()
        SupabaseService::class.java.getDeclaredField("client").apply {
            isAccessible = true
            set(service, okClient)
        }
        SupabaseService::class.java.getDeclaredField("_isInitialized").apply {
            isAccessible = true
            setBoolean(service, true)
        }
        return service
    }

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FiltrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fakeMetadataStore = ConcurrentHashMap()
        fakeSupabaseService = createIsolatedSupabaseService(fakeMetadataStore)
        repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = FiltrRepository(
            menuDao = db.menuDao(),
            orderDao = db.orderDao(),
            upiAccountDao = db.upiAccountDao(),
            inventoryDao = db.inventoryDao(),
            customerDao = db.customerDao(),
            fixedCostDao = db.fixedCostDao(),
            checklistDao = db.checklistDao(),
            kitchenInventoryDao = db.kitchenInventoryDao(),
            attendanceDao = db.attendanceDao(),
            kdsClaimDao = db.kdsClaimDao(),
            supabaseService = fakeSupabaseService,
            externalScope = repoScope
        )
    }

    @After
    fun tearDown() {
        repoScope.cancel()
        db.close()
    }

    private suspend fun awaitManagedSyncJobs() {
        val parentJob = repoScope.coroutineContext[Job] ?: return
        val children = parentJob.children.toList()
        children.forEach { it.join() }
    }

    @Test
    fun `two tap multi barista workflow claims guards completes out of order and auto marks order made`() = runTest {
        db.orderDao().insertOrderWithItems(
            OrderEntity(
                orderId = "FLT-20261126-1040",
                customerName = "Riya",
                contactNumber = "",
                totalAmount = 240.0,
                timestamp = 1_700_000_000_000L,
                isMade = false,
                isPaid = true,
                isDelivered = false,
                syncStatus = "SYNCED",
                notes = "Oat milk"
            ),
            listOf(
                OrderItemEntity(
                    id = 101,
                    orderId = "FLT-20261126-1040",
                    menuItemId = 1,
                    itemName = "Iced Latte",
                    itemPrice = 120.0,
                    quantity = 2,
                    subtotal = 240.0,
                    completedQuantity = 0
                )
            )
        )

        // Barista 1 (Somesh) claims Unit 1; Barista 2 (Kanha) claims Unit 2
        val tap1Unit1 = repository.handleKdsItemTap(
            orderId = "FLT-20261126-1040",
            itemId = 101,
            unitIndex = 1,
            baristaName = "Somesh",
            baristaColorHex = "#16A34A",
            isAdminOverride = false
        )
        assertTrue(tap1Unit1 is FiltrRepository.KdsTapOutcome.Claimed)

        val tap1Unit2 = repository.handleKdsItemTap(
            orderId = "FLT-20261126-1040",
            itemId = 101,
            unitIndex = 2,
            baristaName = "Kanha",
            baristaColorHex = "#DC2626",
            isAdminOverride = false
        )
        assertTrue(tap1Unit2 is FiltrRepository.KdsTapOutcome.Claimed)

        // Somesh accidentally taps Unit 2 (owned by Kanha) without admin override -> blocked
        val blockedTap = repository.handleKdsItemTap(
            orderId = "FLT-20261126-1040",
            itemId = 101,
            unitIndex = 2,
            baristaName = "Somesh",
            isAdminOverride = false
        )
        assertTrue(blockedTap is FiltrRepository.KdsTapOutcome.BlockedByOtherBarista)
        assertEquals("Kanha", (blockedTap as FiltrRepository.KdsTapOutcome.BlockedByOtherBarista).claimedByStaff)

        // Kanha finishes Unit 2 out of order before Unit 1!
        val completeUnit2 = repository.handleKdsItemTap(
            orderId = "FLT-20261126-1040",
            itemId = 101,
            unitIndex = 2,
            baristaName = "Kanha",
            isAdminOverride = false
        )
        assertTrue(completeUnit2 is FiltrRepository.KdsTapOutcome.Completed)
        val midOrder = db.orderDao().getOrderById("FLT-20261126-1040")!!
        // Unit 1 is still MAKING so completedQuantity stays 0 & order stays pending in FIFO queue!
        assertFalse(midOrder.order.isMade)
        assertEquals(0, midOrder.items.first().completedQuantity)

        val claimsMid = db.kdsClaimDao().getAllClaims().first().associateBy { it.key }
        val midQueue = KdsQueueBuilder.buildFifoPendingQueue(
            orders = listOf(midOrder),
            claimsByKey = claimsMid,
            rosterByNormalizedName = emptyMap()
        )
        assertEquals(1, midQueue.size)
        assertEquals(1, midQueue[0].unitIndex)
        assertEquals("Somesh", midQueue[0].claimedByStaff)

        // Somesh finishes Unit 1 -> whole order flips isMade = true & completedQuantity = 2!
        val completeUnit1 = repository.handleKdsItemTap(
            orderId = "FLT-20261126-1040",
            itemId = 101,
            unitIndex = 1,
            baristaName = "Somesh",
            isAdminOverride = false
        )
        assertTrue(completeUnit1 is FiltrRepository.KdsTapOutcome.Completed)
        val doneOrder = db.orderDao().getOrderById("FLT-20261126-1040")!!
        assertTrue(doneOrder.order.isMade)
        assertEquals(2, doneOrder.items.first().completedQuantity)

        // Cloud metadata received active claims payload
        awaitManagedSyncJobs()
        assertTrue(fakeMetadataStore.containsKey("kds_claims_active"))
    }

    @Test
    fun `unclaim resets MAKING item back to queue and cloud reconcile merges newer claim`() = runTest {
        val now = System.currentTimeMillis()
        val key = KdsQueueBuilder.buildClaimKey("FLT-20261126-1041", 201, 1)
        db.kdsClaimDao().upsertClaim(
            KdsItemClaimEntity(
                key = key,
                orderId = "FLT-20261126-1041",
                itemId = 201,
                itemName = "Americano",
                unitIndex = 1,
                status = "MAKING",
                staffName = "Somesh",
                colorHex = "#16A34A",
                updatedAt = now
            )
        )

        val released = repository.unclaimKdsItem("FLT-20261126-1041", 201, 1)
        assertTrue(released)
        assertEquals("PENDING", db.kdsClaimDao().getClaim(key)?.status)
        awaitManagedSyncJobs()

        // Another phone claims the item later in cloud
        val cloudArr = JSONArray().put(
            JSONObject().apply {
                put("key", key)
                put("orderId", "FLT-20261126-1041")
                put("itemId", 201)
                put("itemName", "Americano")
                put("unitIndex", 1)
                put("status", "MAKING")
                put("staffName", "Leeladhar")
                put("colorHex", "#2563EB")
                put("updatedAt", now + 60_000L)
            }
        )
        fakeMetadataStore["kds_claims_active"] = cloudArr.toString()

        repository.reconcileKdsClaimsFromCloud()
        val synced = db.kdsClaimDao().getClaim(key)!!
        assertEquals("MAKING", synced.status)
        assertEquals("Leeladhar", synced.staffName)
        assertEquals("#2563EB", synced.colorHex)
    }
}

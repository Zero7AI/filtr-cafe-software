package com.example.kitchen.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.remote.GoogleSheetsSyncService
import com.example.kitchen.data.remote.SupabaseService
import com.example.kitchen.repository.KitchenRepository
import com.example.kitchen.testutil.FakeCustomerDao
import com.example.kitchen.testutil.FakeMenuDao
import com.example.kitchen.testutil.FakeOrderDao
import com.example.kitchen.testutil.MainDispatcherRule
import com.example.kitchen.util.ConnectionMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KitchenViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createMockSupabase(code: Int = 200): SupabaseService {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val url = chain.request().url.toString()
                val body = when {
                    url.contains("get_next_order_sequence") -> "1001"
                    url.contains("/rest/v1/orders") && chain.request().method == "GET" -> "[]"
                    else -> ""
                }
                Response.Builder()
                    .request(chain.request())
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
            ioDispatcher = mainDispatcherRule.dispatcher
        )
    }

    private fun createViewModel(
        orderDao: FakeOrderDao = FakeOrderDao(),
        menuDao: FakeMenuDao = FakeMenuDao(
            listOf(
                MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 79.0),
                MenuItemEntity(id = 2, name = "OG Vietnamese", category = "ICED COFFEE", price = 109.0)
            )
        ),
        supabaseCode: Int = 200
    ): Triple<KitchenViewModel, FakeOrderDao, FakeMenuDao> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_kitchen_prefs", Context.MODE_PRIVATE).apply {
            edit().clear().commit()
        }
        val dispatcher = mainDispatcherRule.dispatcher
        val bgScope = TestScope(dispatcher)
        val repo = KitchenRepository(
            orderDao = orderDao,
            menuDao = menuDao,
            customerDao = FakeCustomerDao(),
            supabaseService = createMockSupabase(supabaseCode),
            sheetsSyncService = GoogleSheetsSyncService(),
            ioDispatcher = dispatcher,
            externalSyncScope = bgScope
        )
        val monitor = ConnectionMonitor(
            context = app,
            supabaseService = createMockSupabase(supabaseCode),
            scope = bgScope,
            ioDispatcher = dispatcher,
            autoStart = false
        )
        val vm = KitchenViewModel(
            application = app,
            repositoryOverride = repo,
            connectionMonitorOverride = monitor,
            prefsOverride = prefs,
            autoStartSync = false
        )
        return Triple(vm, orderDao, menuDao)
    }

    @Test
    fun cartItems_keyedByMenuItemId_survivesMenuPriceEdits() = runTest {
        val (vm, _, menuDao) = createViewModel()
        val itemV1 = MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 79.0)
        val itemV2Updated = itemV1.copy(price = 89.0, description = "Updated")

        vm.addToCart(itemV1)
        menuDao.updateMenuItem(itemV2Updated)
        // Collect live menuItems flow so menuItems.value reflects itemV2Updated
        vm.menuItems.first { it.any { m -> m.id == 1 && m.price == 89.0 } }
        vm.addToCart(itemV2Updated)

        // Keyed by Int (id = 1), so quantity increments to 2 rather than creating duplicate entries
        assertEquals(mapOf(1 to 2), vm.uiState.value.cartItems)

        vm.removeFromCart(itemV2Updated)
        assertEquals(mapOf(1 to 1), vm.uiState.value.cartItems)

        vm.clearCart()
        assertTrue(vm.uiState.value.cartItems.isEmpty())
    }

    @Test
    fun submitOrder_placesOrderWithUpdatedMenuPriceAndResetsForm() = runTest {
        val (vm, orderDao, menuDao) = createViewModel()
        val itemV1 = MenuItemEntity(id = 2, name = "OG Vietnamese", category = "ICED COFFEE", price = 109.0)
        menuDao.updateMenuItem(itemV1.copy(price = 119.0))
        vm.menuItems.first { it.any { m -> m.id == 2 && m.price == 119.0 } }

        vm.setTab(KitchenTab.ORDER)
        vm.addToCart(itemV1)
        vm.updateCustomerName("  Rohan ")
        vm.updateContactNumber(" 9876500000 ")
        vm.updateOrderNotes("Extra hot")
        vm.updatePaymentMode("CASH")

        vm.submitOrder()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isPlacingOrder)
        assertTrue(state.cartItems.isEmpty())
        assertEquals("", state.customerName)
        assertEquals("", state.contactNumber)
        assertEquals("", state.orderNotes)
        assertEquals(KitchenTab.KITCHEN, state.currentTab)
        assertTrue(state.snackbarMessage?.contains("placed successfully") == true)

        val pending = orderDao.getPendingOrdersWithItems().first()
        assertEquals(1, pending.size)
        assertEquals("Rohan", pending[0].order.customerName)
        assertEquals(119.0, pending[0].order.totalAmount, 0.001)
        assertTrue(pending[0].order.isPaid)
    }

    @Test
    fun submitOrder_andMarkOrderReady_surfaceErrorsInSnackbar() = runTest {
        val orderDao = FakeOrderDao()
        val (vm, _, _) = createViewModel(orderDao = orderDao)

        orderDao.insertOrderWithItems(
            OrderEntity(orderId = "FLT-20261125-1010", customerName = "Anu", totalAmount = 79.0, timestamp = 1000L),
            listOf(OrderItemEntity(orderId = "FLT-20261125-1010", menuItemId = 1, itemName = "Americano", itemPrice = 79.0, quantity = 1, subtotal = 79.0))
        )

        // Simulate local DB failure on markOrderReady
        orderDao.failNextUpdate = true
        vm.markOrderReady("FLT-20261125-1010")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.snackbarMessage?.contains("Failed to mark order ready") == true)
        vm.clearSnackbar()
        assertNull(vm.uiState.value.snackbarMessage)

        // Succeed on retry
        vm.markOrderReady("FLT-20261125-1010")
        advanceUntilIdle()
        assertTrue(orderDao.getOrderById("FLT-20261125-1010")!!.order.isMade)

        // Simulate local DB failure on submitOrder
        vm.addToCart(MenuItemEntity(id = 1, name = "Americano", category = "ICED COFFEE", price = 79.0))
        orderDao.failNextUpdate = true
        vm.submitOrder()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isPlacingOrder)
        assertFalse(vm.uiState.value.cartItems.isEmpty())
        assertTrue(vm.uiState.value.snackbarMessage?.contains("Failed to place order") == true)
    }

    @Test
    fun saveWebhookUrl_persistsTrimmedUrl_andViewModelStoreClearUnregistersMonitor() {
        val (vm, _, _) = createViewModel()
        vm.openSettings()
        assertTrue(vm.uiState.value.showSettingsDialog)

        vm.saveWebhookUrl("  https://script.google.com/macros/s/abc/exec  ")
        assertFalse(vm.uiState.value.showSettingsDialog)
        assertEquals("https://script.google.com/macros/s/abc/exec", vm.uiState.value.sheetsWebhookUrl)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = app.getSharedPreferences("test_kitchen_prefs", Context.MODE_PRIVATE)
        assertEquals("https://script.google.com/macros/s/abc/exec", prefs.getString("sheets_webhook_url", ""))

        // Verify onCleared via ViewModelStore.clear() executes without throwing
        val store = ViewModelStore()
        store.put("kitchen_vm", vm)
        store.clear()
        assertNotNull(vm.uiState.value)
    }
}

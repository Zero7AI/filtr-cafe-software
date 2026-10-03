package com.example.kitchen.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchen.data.database.KitchenDatabase
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import com.example.kitchen.data.remote.GoogleSheetsSyncService
import com.example.kitchen.data.remote.SupabaseService
import com.example.kitchen.repository.KitchenRepository
import com.example.kitchen.util.CloudConnectionStatus
import com.example.kitchen.util.ConnectionMonitor
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class KitchenTab {
    KITCHEN, // Kanban view
    ORDER    // Tablet POS
}

data class KitchenUiState(
    val currentTab: KitchenTab = KitchenTab.KITCHEN,
    val customerName: String = "",
    val contactNumber: String = "",
    val orderNotes: String = "",
    val paymentMode: String = "CASH",
    val cartItems: Map<MenuItemEntity, Int> = emptyMap(),
    val isPlacingOrder: Boolean = false,
    val sheetsWebhookUrl: String = "",
    val showSettingsDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class KitchenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: KitchenRepository
    private val connectionMonitor: ConnectionMonitor
    private val prefs = application.getSharedPreferences("filtr_kitchen_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        KitchenUiState(
            sheetsWebhookUrl = prefs.getString("sheets_webhook_url", "") ?: ""
        )
    )
    val uiState: StateFlow<KitchenUiState> = _uiState.asStateFlow()

    val pendingOrders: StateFlow<List<OrderWithItems>>
    val completedTodayOrders: StateFlow<List<OrderWithItems>>
    val menuItems: StateFlow<List<MenuItemEntity>>
    val connectionStatus: StateFlow<CloudConnectionStatus>
    val pendingOrderCount: StateFlow<Int>

    init {
        val database = KitchenDatabase.getDatabase(application, viewModelScope)
        val supabaseService = SupabaseService.getInstance()
        val sheetsService = GoogleSheetsSyncService()

        repository = KitchenRepository(
            orderDao = database.orderDao(),
            menuDao = database.menuDao(),
            customerDao = database.customerDao(),
            supabaseService = supabaseService,
            sheetsSyncService = sheetsService
        )

        connectionMonitor = ConnectionMonitor(
            context = application,
            supabaseService = supabaseService,
            scope = viewModelScope
        )

        connectionStatus = connectionMonitor.status

        pendingOrders = repository.pendingOrders.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        completedTodayOrders = repository.getCompletedTodayOrders().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        menuItems = repository.allMenuItems.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        pendingOrderCount = repository.pendingOrderCount.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

        // Start background real-time cloud sync with Supabase
        repository.startCloudSync(viewModelScope)
    }

    // ==================== TAB NAVIGATION ====================

    fun setTab(tab: KitchenTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    // ==================== KDS ACTIONS ====================

    fun markOrderReady(orderId: String) {
        viewModelScope.launch {
            repository.markOrderReady(orderId, _uiState.value.sheetsWebhookUrl)
        }
    }

    // ==================== ORDER TAKING ACTIONS ====================

    fun addToCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val updated = state.cartItems.toMutableMap()
            updated[item] = (updated[item] ?: 0) + 1
            state.copy(cartItems = updated)
        }
    }

    fun removeFromCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val updated = state.cartItems.toMutableMap()
            val current = updated[item] ?: 0
            if (current <= 1) {
                updated.remove(item)
            } else {
                updated[item] = current - 1
            }
            state.copy(cartItems = updated)
        }
    }

    fun clearCart() {
        _uiState.update { it.copy(cartItems = emptyMap()) }
    }

    fun updateCustomerName(name: String) {
        _uiState.update { it.copy(customerName = name) }
    }

    fun updateContactNumber(contact: String) {
        _uiState.update { it.copy(contactNumber = contact) }
    }

    fun updateOrderNotes(notes: String) {
        _uiState.update { it.copy(orderNotes = notes) }
    }

    fun updatePaymentMode(mode: String) {
        _uiState.update { it.copy(paymentMode = mode) }
    }

    fun submitOrder() {
        val state = _uiState.value
        if (state.isPlacingOrder || state.cartItems.isEmpty()) return

        _uiState.update { it.copy(isPlacingOrder = true) }

        viewModelScope.launch {
            val result = repository.createOrder(
                customerName = state.customerName,
                contactNumber = state.contactNumber,
                cartItems = state.cartItems,
                notes = state.orderNotes,
                paymentMode = state.paymentMode,
                isPaid = state.paymentMode == "CASH", // Mark cash as paid, UPI as unpaid
                webhookUrl = state.sheetsWebhookUrl
            )

            if (result.isSuccess) {
                val order = result.getOrNull()
                val orderId = order?.order?.orderId ?: ""
                _uiState.update {
                    it.copy(
                        isPlacingOrder = false,
                        cartItems = emptyMap(),
                        customerName = "",
                        contactNumber = "",
                        orderNotes = "",
                        currentTab = KitchenTab.KITCHEN, // Switch back to KDS to see the new card!
                        snackbarMessage = "Order $orderId placed successfully!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isPlacingOrder = false,
                        snackbarMessage = "Failed to place order: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    // ==================== SETTINGS ====================

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveWebhookUrl(url: String) {
        prefs.edit().putString("sheets_webhook_url", url).apply()
        _uiState.update { it.copy(sheetsWebhookUrl = url, showSettingsDialog = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

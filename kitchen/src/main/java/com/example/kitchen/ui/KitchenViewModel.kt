package com.example.kitchen.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val cartItems: Map<Int, Int> = emptyMap(),
    val isPlacingOrder: Boolean = false,
    val sheetsWebhookUrl: String = "",
    val showSettingsDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class KitchenViewModel internal constructor(
    application: Application,
    repositoryOverride: KitchenRepository? = null,
    connectionMonitorOverride: ConnectionMonitor? = null,
    prefsOverride: SharedPreferences? = null,
    autoStartSync: Boolean = true
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, null, null, null, true)

    private val prefs: SharedPreferences =
        prefsOverride ?: application.getSharedPreferences("filtr_kitchen_prefs", Context.MODE_PRIVATE)
    private val repository: KitchenRepository
    private val connectionMonitor: ConnectionMonitor
    private val cartItemSnapshot = mutableMapOf<Int, MenuItemEntity>()

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
        val supabaseService = SupabaseService.getInstance()
        repository = repositoryOverride ?: run {
            val database = KitchenDatabase.getDatabase(application, viewModelScope)
            KitchenRepository(
                orderDao = database.orderDao(),
                menuDao = database.menuDao(),
                customerDao = database.customerDao(),
                supabaseService = supabaseService,
                sheetsSyncService = GoogleSheetsSyncService(),
                externalSyncScope = viewModelScope
            )
        }

        connectionMonitor = connectionMonitorOverride ?: ConnectionMonitor(
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

        if (autoStartSync) {
            repository.startCloudSync(viewModelScope)
            viewModelScope.launch {
                var previousStatus: CloudConnectionStatus? = null
                connectionStatus.collect { status ->
                    if (status == CloudConnectionStatus.CONNECTED &&
                        previousStatus != null &&
                        previousStatus != CloudConnectionStatus.CONNECTED
                    ) {
                        repository.syncUnsyncedOrders()
                    }
                    previousStatus = status
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionMonitor.unregister()
    }

    // ==================== TAB NAVIGATION ====================

    fun setTab(tab: KitchenTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    // ==================== KDS ACTIONS ====================

    fun markOrderReady(orderId: String) {
        viewModelScope.launch {
            val result = repository.markOrderReady(orderId, _uiState.value.sheetsWebhookUrl)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        snackbarMessage = "Failed to mark order ready: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    // ==================== ORDER TAKING ACTIONS ====================

    fun addToCart(item: MenuItemEntity) {
        cartItemSnapshot[item.id] = item
        _uiState.update { state ->
            val updated = state.cartItems.toMutableMap()
            updated[item.id] = (updated[item.id] ?: 0) + 1
            state.copy(cartItems = updated)
        }
    }

    fun removeFromCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val updated = state.cartItems.toMutableMap()
            val current = updated[item.id] ?: 0
            if (current <= 1) {
                updated.remove(item.id)
                cartItemSnapshot.remove(item.id)
            } else {
                updated[item.id] = current - 1
            }
            state.copy(cartItems = updated)
        }
    }

    fun clearCart() {
        cartItemSnapshot.clear()
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

        val liveMenuById = menuItems.value.associateBy { it.id }
        val resolvedCart = state.cartItems.mapNotNull { (menuItemId, qty) ->
            if (qty <= 0) return@mapNotNull null
            val item = liveMenuById[menuItemId] ?: cartItemSnapshot[menuItemId] ?: return@mapNotNull null
            item to qty
        }.toMap()
        if (resolvedCart.isEmpty()) return

        _uiState.update { it.copy(isPlacingOrder = true) }

        viewModelScope.launch {
            val result = repository.createOrder(
                customerName = state.customerName,
                contactNumber = state.contactNumber,
                cartItems = resolvedCart,
                notes = state.orderNotes,
                paymentMode = state.paymentMode,
                isPaid = state.paymentMode == "CASH",
                webhookUrl = state.sheetsWebhookUrl
            )

            if (result.isSuccess) {
                cartItemSnapshot.clear()
                val order = result.getOrNull()
                val orderId = order?.order?.orderId ?: ""
                _uiState.update {
                    it.copy(
                        isPlacingOrder = false,
                        cartItems = emptyMap(),
                        customerName = "",
                        contactNumber = "",
                        orderNotes = "",
                        currentTab = KitchenTab.KITCHEN,
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
        val trimmed = url.trim()
        prefs.edit().putString("sheets_webhook_url", trimmed).apply()
        _uiState.update { it.copy(sheetsWebhookUrl = trimmed, showSettingsDialog = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}

package com.example.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.FiltrDatabase
import com.example.data.entity.InventoryItemEntity
import com.example.data.entity.InventoryOrderEntity
import com.example.data.entity.InventoryOrderItemEntity
import com.example.data.entity.InventoryOrderWithItems
import com.example.data.entity.InventoryUsageEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.FixedCostEntity
import com.example.data.entity.MenuItemEntity
import com.example.data.entity.OrderItemEntity
import com.example.data.entity.OrderWithItems
import com.example.BuildConfig
import com.example.data.entity.StoryPhotoMetadata
import com.example.data.entity.StoryPhotoPrintData
import com.example.data.remote.AppUpdateInfo
import com.example.data.remote.AppUpdateStatus
import com.example.data.remote.FirestoreService
import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.SupabaseService
import com.example.data.remote.ParsedVoiceOrder
import com.example.data.repository.FiltrRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class FiltrTab {
    BILLING,
    ORDERS,
    MENU,
    DASHBOARD,
    INVENTORY_ORDER,
    INVENTORIES,
    INVENTORY_USAGE,
    RECIPE_MASTER,
    CUSTOMERS,
    RECEIPT_DESIGN,
    PROFIT_LOSS,
    VOICE_ORDER
}

data class BillingUiState(
    val currentTab: FiltrTab = FiltrTab.BILLING,
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val customerName: String = "",
    val contactNumber: String = "",
    val orderNotes: String = "",
    val cartItems: Map<MenuItemEntity, Int> = emptyMap(),
    val googleSheetsWebhookUrl: String = "",
    val isPlacingOrder: Boolean = false,
    val isListeningVoice: Boolean = false,
    val isParsingVoice: Boolean = false,
    val voiceTranscript: String = "",
    val showVoiceOrderReviewDialog: Boolean = false,
    val parsedVoiceOrder: ParsedVoiceOrder? = null,
    val showSettingsDialog: Boolean = false,
    val showAddEditItemDialog: Boolean = false,
    val editingMenuItem: MenuItemEntity? = null,
    val selectedOrderForReceipt: OrderWithItems? = null,
    val statusFilter: String = "PENDING", // PENDING (default), ALL, MADE, UNPAID, DELIVERED
    val ordersDateFilter: String = "TODAY", // TODAY, CUSTOM, ALL
    val ordersCustomStartMs: Long? = null,
    val ordersCustomEndMs: Long? = null,
    val showOrdersDatePickerDialog: Boolean = false,
    val dashboardDateFilter: String = "TODAY", // TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, ALL_TIME, CUSTOM
    val customStartDateMs: Long? = null,
    val customEndDateMs: Long? = null,
    val showCustomDatePickerDialog: Boolean = false,
    val itemToDeleteConfirmation: MenuItemEntity? = null,
    val itemDeleteWarningMessage: MenuItemEntity? = null,
    val orderToDeleteConfirmation: String? = null,
    val inventoryItemToDeleteConfirmation: InventoryItemEntity? = null,
    val isSyncingSheets: Boolean = false,
    val lastSyncTime: String = "",
    val isDeletingOrder: Boolean = false,
    val snackbarMessage: String? = null,
    val isFirestoreConnected: Boolean = false,
    val isSupabaseConnected: Boolean = false,
    val isNetworkAvailable: Boolean = true,
    // Inventory
    val showInventoryMasterDialog: Boolean = false,
    val showAddInventoryOrderDialog: Boolean = false,
    val editingInventoryItem: InventoryItemEntity? = null,
    val showAddInventoryItemDialog: Boolean = false,
    val inventorySearchQuery: String = "",
    val inventoryOrderToDelete: String? = null,
    // Inventory Usage (Recipes)
    val showUsagePerSkuDialog: Boolean = false,
    val usageEditingMenuItemId: Int? = null,
    val usageEditingMenuItemName: String = "",
    // Inventories (Stock)
    val editingStockItemId: Int? = null,
    val showAddStockDialog: Boolean = false,
    val addStockItem: InventoryItemEntity? = null,
    // Customers
    val customerSuggestions: List<CustomerEntity> = emptyList(),
    val showCustomerSuggestions: Boolean = false,
    // Accounts & UPI
    val showAccountsUpiDialog: Boolean = false,
    // Thermal Receipt Story & Photo
    val showReceiptStoryDialog: Boolean = false,
    val receiptStoryTitle: String = "Testing of Hazelnut Iced Coffee",
    val receiptStoryDate: String = "",
    val receiptStoryCaption: String = "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.",
    val receiptStoryImagePath: String = "",
    val isReceiptStoryEnabled: Boolean = true,
    val isGeneratingStoryNote: Boolean = false,
    // Multi-photo from Supabase Storage & per-photo metadata
    val storyPhotoNames: List<String> = emptyList(),
    val isLoadingStoryPhotos: Boolean = false,
    val isUploadingStoryPhoto: Boolean = false,
    val selectedStoryPhotoName: String? = null,
    val storyPhotoMetadataMap: Map<String, StoryPhotoMetadata> = emptyMap(),
    // Receipt Text & Design Customization
    val receiptTagline: String = "We are working for you <3",
    val receiptHeaderSubtitle: String = "where coffee meets vibe",
    val receiptFooterLine1: String = "Thank you for hanging out with us!",
    val receiptFooterLine2: String = "Tag your cup on Instagram @filtr.coffeee",
    val receiptFooterLine3: String = "Made fresh - Crafted with obsession",
    // Fixed Costs & P&L
    val showFixedCostDialog: Boolean = false,
    val editingFixedCostMonthKey: String = "",
    // In-App OTA App Updates
    val appUpdateStatus: AppUpdateStatus = AppUpdateStatus.Idle,
    val showAppUpdateDialog: Boolean = false,
    val isCheckingForUpdate: Boolean = false,
    val githubRepoOwner: String = GitHubUpdateService.DEFAULT_REPO_OWNER,
    val githubRepoName: String = GitHubUpdateService.DEFAULT_REPO_NAME,
    val githubToken: String = "",
    val isForceUpdate: Boolean = false,
    val forceUpdateMessage: String = "",
    // Item Master Custom Ordering & Categories
    val customItemOrder: List<String> = emptyList(),
    val customCategories: List<String> = emptyList(),
    val showAddCategoryDialog: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FiltrDatabase.getDatabase(application, viewModelScope)
    private val repository = FiltrRepository(
        database.menuDao(), database.orderDao(), database.upiAccountDao(),
        database.inventoryDao(), database.customerDao(), database.fixedCostDao()
    )

    private val prefs = application.getSharedPreferences("filtr_prefs", Context.MODE_PRIVATE)

    private fun loadCustomItemOrder(): List<String> {
        val jsonStr = prefs.getString("menu_custom_item_order", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getString(i).trim()
                if (s.isNotBlank()) list.add(s)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadCustomCategories(): List<String> {
        val jsonStr = prefs.getString("menu_custom_categories", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.getString(i).trim().uppercase()
                if (s.isNotBlank()) list.add(s)
            }
            list.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadStoryPhotoMetadataMap(): Map<String, StoryPhotoMetadata> {
        val jsonStr = prefs.getString("story_photos_metadata", null) ?: return emptyMap()
        return try {
            val json = JSONObject(jsonStr)
            val map = mutableMapOf<String, StoryPhotoMetadata>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = json.getJSONObject(key)
                map[key] = StoryPhotoMetadata(
                    fileName = obj.optString("fileName", key),
                    title = obj.optString("title", ""),
                    date = obj.optString("date", ""),
                    caption = obj.optString("caption", "")
                )
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveStoryPhotoMetadataMap(map: Map<String, StoryPhotoMetadata>) {
        try {
            val json = JSONObject()
            for ((key, meta) in map) {
                val obj = JSONObject().apply {
                    put("fileName", meta.fileName)
                    put("title", meta.title)
                    put("date", meta.date)
                    put("caption", meta.caption)
                }
                json.put(key, obj)
            }
            prefs.edit().putString("story_photos_metadata", json.toString()).apply()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error saving story photo metadata: ${e.message}")
        }
    }

    private val _uiState = MutableStateFlow(
        BillingUiState(
            googleSheetsWebhookUrl = prefs.getString("sheets_webhook_url", "") ?: "",
            receiptStoryTitle = prefs.getString("receipt_story_title", "Testing of Hazelnut Iced Coffee") ?: "Testing of Hazelnut Iced Coffee",
            receiptStoryDate = prefs.getString("receipt_story_date", "") ?: "",
            receiptStoryCaption = prefs.getString("receipt_story_caption", "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.") ?: "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.",
            receiptStoryImagePath = prefs.getString("receipt_story_image_path", "") ?: "",
            isReceiptStoryEnabled = prefs.getBoolean("receipt_story_enabled", true),
            storyPhotoMetadataMap = loadStoryPhotoMetadataMap(),
            receiptTagline = prefs.getString("receipt_tagline", "We are working for you <3") ?: "We are working for you <3",
            receiptHeaderSubtitle = prefs.getString("receipt_header_subtitle", "where coffee meets vibe") ?: "where coffee meets vibe",
            receiptFooterLine1 = prefs.getString("receipt_footer_line1", "Thank you for hanging out with us!") ?: "Thank you for hanging out with us!",
            receiptFooterLine2 = prefs.getString("receipt_footer_line2", "Tag your cup on Instagram @filtr.coffeee") ?: "Tag your cup on Instagram @filtr.coffeee",
            receiptFooterLine3 = prefs.getString("receipt_footer_line3", "Made fresh - Crafted with obsession") ?: "Made fresh - Crafted with obsession",
            githubRepoOwner = prefs.getString("github_repo_owner", GitHubUpdateService.DEFAULT_REPO_OWNER) ?: GitHubUpdateService.DEFAULT_REPO_OWNER,
            githubRepoName = prefs.getString("github_repo_name", GitHubUpdateService.DEFAULT_REPO_NAME) ?: GitHubUpdateService.DEFAULT_REPO_NAME,
            githubToken = prefs.getString("github_token", "") ?: "",
            customItemOrder = loadCustomItemOrder(),
            customCategories = loadCustomCategories()
        )
    )
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    val menuItems: StateFlow<List<MenuItemEntity>> = repository.allMenuItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ordersList: StateFlow<List<OrderWithItems>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upiAccounts: StateFlow<List<com.example.data.entity.UpiAccountEntity>> = repository.allUpiAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryItems: StateFlow<List<InventoryItemEntity>> = repository.allInventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryOrders: StateFlow<List<InventoryOrderWithItems>> = repository.allInventoryOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryUsages: StateFlow<List<InventoryUsageEntity>> = repository.allInventoryUsages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fixedCosts: StateFlow<List<FixedCostEntity>> = repository.allFixedCosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var customerSearchJob: Job? = null

    init {
        // Monitor network connectivity in real-time
        val connectivityManager = application.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                _uiState.update { it.copy(isNetworkAvailable = true) }
                // Trigger immediate background sync of any pending orders to Supabase & Sheets when connection restored
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.syncPendingOrdersToSupabase()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Supabase sync on network reconnect failed: ${e.message}")
                    }
                    val url = _uiState.value.googleSheetsWebhookUrl
                    if (url.isNotBlank()) {
                        try {
                            repository.syncPendingOrdersToGoogleSheets(url)
                        } catch (e: Exception) {
                            Log.w("MainViewModel", "Sheets sync on network reconnect failed: ${e.message}")
                        }
                    }
                }
            }
            override fun onLost(network: android.net.Network) {
                _uiState.update { it.copy(isNetworkAvailable = false) }
            }
        }
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        // Set initial network state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(isNetworkAvailable = isConnected) }

        // Proactively deduplicate local menu items to clean up any legacy duplicates
        viewModelScope.launch(Dispatchers.IO) {
            repository.deduplicateMenuItems()
        }

        // Global continuous sync between Room Menu Items and Voice Engine MenuCatalog
        viewModelScope.launch {
            menuItems.collect { items ->
                if (items.isNotEmpty()) {
                    com.example.voice.engine.MenuCatalog.syncWithRoomMenuItems(items)
                }
            }
        }

        // Initialize Cloud Services: Supabase is primary, Firestore is fallback
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val supabaseService = SupabaseService.getInstance()
                val initialized = supabaseService.initialize()
                _uiState.update { it.copy(isSupabaseConnected = initialized) }
                if (initialized) {
                    Log.d("MainViewModel", "Supabase connected successfully — starting Supabase listeners")
                    repository.startSupabaseListeners(viewModelScope)
                } else {
                    Log.w("MainViewModel", "Supabase init failed — falling back to Firestore")
                    initFirestoreFallback()
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Supabase init failed: ${e.message}")
                _uiState.update { it.copy(isSupabaseConnected = false) }
                initFirestoreFallback()
            }
        }

        viewModelScope.launch {
            repository.deduplicateMenuItems()
            // One-time check only at startup (not reactive to prevent infinite loop)
            val items = menuItems.first()
            val hasLegacyItems = items.any { it.name.contains("South Indian", ignoreCase = true) || it.name.contains("Masala Chai", ignoreCase = true) }
            if (items.isEmpty() || hasLegacyItems) {
                repository.resetMenuToFiltrDefault()
            }
        }

        // Automatic periodic Cloud & Google Sheets Sync Loop (Attempts sync of any pending unsynced orders every 2 minutes)
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(120 * 1000L) // 2 minute background safety sync
                try {
                    repository.syncPendingOrdersToSupabase()
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Background Supabase sync failed, will retry: ${e.message}")
                }
                try {
                    val url = _uiState.value.googleSheetsWebhookUrl
                    if (url.isNotBlank()) {
                        repository.syncPendingOrdersToGoogleSheets(url)
                    }
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Background Sheets sync failed, will retry: ${e.message}")
                }
            }
        }

        // Load story photos from Supabase Storage
        loadStoryPhotos()

        // Startup backfill: fetch current month's orders from cloud to handle cases where
        // the real-time listener was frozen by battery optimization (e.g. OnePlus OplusHansManager)
        // or app was installed on a new device.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val monthStartMs = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val backfilled = repository.backfillOrdersForDateRange(monthStartMs, System.currentTimeMillis())
                if (backfilled > 0) {
                    Log.d("MainViewModel", "Startup backfill: $backfilled orders synced for current month")
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Startup backfill failed: ${e.message}")
            }
        }

        // Fetch remote custom item order and custom categories from Supabase
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remoteOrderJson = SupabaseService.getInstance().fetchSyncMetadata("menu_item_order")
                if (!remoteOrderJson.isNullOrBlank() && remoteOrderJson.startsWith("[")) {
                    val arr = JSONArray(remoteOrderJson)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i).trim()
                        if (s.isNotBlank()) list.add(s)
                    }
                    if (list.isNotEmpty()) {
                        prefs.edit().putString("menu_custom_item_order", remoteOrderJson).apply()
                        _uiState.update { it.copy(customItemOrder = list) }
                    }
                }
                val remoteCatJson = SupabaseService.getInstance().fetchSyncMetadata("custom_categories")
                if (!remoteCatJson.isNullOrBlank() && remoteCatJson.startsWith("[")) {
                    val arr = JSONArray(remoteCatJson)
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) {
                        val s = arr.getString(i).trim().uppercase()
                        if (s.isNotBlank()) list.add(s)
                    }
                    if (list.isNotEmpty()) {
                        val distinctList = list.distinct()
                        prefs.edit().putString("menu_custom_categories", remoteCatJson).apply()
                        _uiState.update { it.copy(customCategories = distinctList) }
                    }
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Remote item order/category sync: ${e.message}")
            }
        }

        // Check for app updates via GitHub Releases on startup
        viewModelScope.launch {
            delay(3000) // Small delay to prioritize initial POS UI load
            checkForAppUpdates(isManualCheck = false)
        }
    }

    private fun initFirestoreFallback() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestoreService = FirestoreService.getInstance()
                val initialized = firestoreService.initialize()
                _uiState.update { it.copy(isFirestoreConnected = initialized) }
                if (initialized) {
                    Log.d("MainViewModel", "Firebase Firestore connected as fallback")
                    repository.startFirestoreListeners(viewModelScope)
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Firebase init failed: ${e.message}")
                _uiState.update { it.copy(isFirestoreConnected = false) }
            }
        }
    }

    fun restoreDefaultMenu() {
        viewModelScope.launch {
            repository.resetMenuToFiltrDefault()
            _uiState.update { it.copy(snackbarMessage = "FILTR Menu restored to defaults!") }
        }
    }

    val filteredMenuItems: StateFlow<List<MenuItemEntity>> = combine(
        menuItems,
        _uiState
    ) { items, state ->
        val filtered = items.distinctBy { it.name.trim().lowercase() }.filter { item ->
            val matchesCategory = state.selectedCategory == "All" || item.category.equals(state.selectedCategory, ignoreCase = true)
            val matchesSearch = state.searchQuery.isBlank() ||
                    item.name.lowercase().contains(state.searchQuery.lowercase()) ||
                    item.category.lowercase().contains(state.searchQuery.lowercase())
            matchesCategory && matchesSearch
        }

        if (state.customItemOrder.isEmpty()) {
            filtered
        } else {
            val orderMap = state.customItemOrder.withIndex().associate { it.value.lowercase().trim() to it.index }
            filtered.sortedWith(
                compareBy<MenuItemEntity> {
                    orderMap[it.name.lowercase().trim()] ?: (10000 + it.id)
                }.thenBy { it.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCustomItemOrder(newOrder: List<String>) {
        val distinctOrder = newOrder.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        prefs.edit().putString("menu_custom_item_order", JSONArray(distinctOrder).toString()).apply()
        _uiState.update { it.copy(customItemOrder = distinctOrder) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseService.getInstance().saveSyncMetadata("menu_item_order", JSONArray(distinctOrder).toString())
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to sync menu_item_order: ${e.message}")
            }
        }
    }

    fun moveMenuItem(item: MenuItemEntity, targetPosition: Int, categoryItems: List<MenuItemEntity>) {
        val currentCategoryItems = categoryItems.toMutableList()
        val currentIndex = currentCategoryItems.indexOfFirst { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
        if (currentIndex == -1 || targetPosition !in currentCategoryItems.indices || currentIndex == targetPosition) return

        val moved = currentCategoryItems.removeAt(currentIndex)
        currentCategoryItems.add(targetPosition, moved)

        val currentGlobalOrder = _uiState.value.customItemOrder.toMutableList()
        val categoryItemNames = currentCategoryItems.map { it.name.trim() }

        val nonCategoryItems = currentGlobalOrder.filterNot { globalName ->
            categoryItemNames.any { it.equals(globalName, ignoreCase = true) }
        }
        val newGlobalOrder = nonCategoryItems + categoryItemNames
        saveCustomItemOrder(newGlobalOrder)
    }

    fun moveMenuItemUp(item: MenuItemEntity, categoryItems: List<MenuItemEntity>) {
        val index = categoryItems.indexOfFirst { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
        if (index > 0) {
            moveMenuItem(item, index - 1, categoryItems)
        }
    }

    fun moveMenuItemDown(item: MenuItemEntity, categoryItems: List<MenuItemEntity>) {
        val index = categoryItems.indexOfFirst { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
        if (index != -1 && index < categoryItems.size - 1) {
            moveMenuItem(item, index + 1, categoryItems)
        }
    }

    fun addCustomCategory(categoryName: String) {
        val clean = categoryName.trim().uppercase()
        if (clean.isBlank()) return
        val current = _uiState.value.customCategories
        if (!current.contains(clean)) {
            val updated = current + clean
            prefs.edit().putString("menu_custom_categories", JSONArray(updated).toString()).apply()
            _uiState.update {
                it.copy(
                    customCategories = updated,
                    selectedCategory = clean,
                    showAddCategoryDialog = false,
                    snackbarMessage = "Category '$clean' created!"
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    SupabaseService.getInstance().saveSyncMetadata("custom_categories", JSONArray(updated).toString())
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Failed to sync custom_categories: ${e.message}")
                }
            }
        } else {
            _uiState.update { it.copy(selectedCategory = clean, showAddCategoryDialog = false) }
        }
    }

    fun openAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = true) }
    }

    fun dismissAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = false) }
    }

    // Navigation Tab
    fun selectTab(tab: FiltrTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setSelectedCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateCustomerName(name: String) {
        _uiState.update { it.copy(customerName = name) }
        // Debounced auto-suggest
        customerSearchJob?.cancel()
        if (name.length >= 2) {
            customerSearchJob = viewModelScope.launch {
                delay(300)
                val suggestions = repository.searchCustomers(name)
                _uiState.update { it.copy(
                    customerSuggestions = suggestions,
                    showCustomerSuggestions = suggestions.isNotEmpty()
                ) }
            }
        } else {
            _uiState.update { it.copy(customerSuggestions = emptyList(), showCustomerSuggestions = false) }
        }
    }

    fun selectCustomerSuggestion(customer: CustomerEntity) {
        _uiState.update { it.copy(
            customerName = customer.name,
            contactNumber = customer.contactNumber,
            customerSuggestions = emptyList(),
            showCustomerSuggestions = false
        ) }
    }

    fun dismissCustomerSuggestions() {
        _uiState.update { it.copy(showCustomerSuggestions = false) }
    }

    fun updateContactNumber(contact: String) {
        _uiState.update { it.copy(contactNumber = contact) }
    }

    fun updateOrderNotes(notes: String) {
        _uiState.update { it.copy(orderNotes = notes) }
    }

    // Cart Operations
    fun addToCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val existingKey = state.cartItems.keys.find { it.id == item.id }
            val targetItem = existingKey ?: item
            val currentQty = state.cartItems[targetItem] ?: 0
            val updatedMap = state.cartItems.toMutableMap().apply {
                put(targetItem, currentQty + 1)
            }
            state.copy(cartItems = updatedMap)
        }
    }

    fun removeFromCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val existingKey = state.cartItems.keys.find { it.id == item.id } ?: item
            val currentQty = state.cartItems[existingKey] ?: 0
            val updatedMap = state.cartItems.toMutableMap()
            if (currentQty > 1) {
                updatedMap[existingKey] = currentQty - 1
            } else {
                updatedMap.remove(existingKey)
            }
            state.copy(cartItems = updatedMap)
        }
    }

    fun clearCart() {
        _uiState.update {
            it.copy(
                cartItems = emptyMap(),
                customerName = "",
                contactNumber = "",
                orderNotes = ""
            )
        }
    }

    fun applyVoiceOrderToCart(voiceItems: List<com.example.voice.model.VoiceOrderLineItem>) {
        val parsedItems = voiceItems.map { vItem ->
            com.example.data.remote.ParsedOrderItem(
                menuItemName = vItem.name,
                quantity = vItem.quantity
            )
        }
        val parsedOrder = com.example.data.remote.ParsedVoiceOrder(
            customerName = "Walk-in Customer",
            contactNumber = "",
            items = parsedItems,
            notes = ""
        )
        _uiState.update { state ->
            state.copy(
                currentTab = FiltrTab.BILLING,
                parsedVoiceOrder = parsedOrder,
                showVoiceOrderReviewDialog = true,
                snackbarMessage = "Reviewing ${voiceItems.size} items from Voice Order"
            )
        }
    }

    /**
     * Transfers confirmed voice order items directly into the Billing cart (FiltrTab.BILLING).
     * Matches line items to the live Room MenuItemEntity catalog, transfers order notes,
     * and switches the view to the Billing screen so the cashier can collect payment safely.
     */
    fun transferVoiceOrderToCart(
        voiceItems: List<com.example.voice.model.VoiceOrderLineItem>,
        notes: String? = null
    ) {
        val currentMenu = menuItems.value
        val newCart = _uiState.value.cartItems.toMutableMap()

        voiceItems.forEach { vItem ->
            val entity = currentMenu.find { it.name.trim().equals(vItem.name.trim(), ignoreCase = true) }
                ?: currentMenu.find { it.name.contains(vItem.name.trim(), ignoreCase = true) }
                ?: currentMenu.find { vItem.name.trim().contains(it.name, ignoreCase = true) }
                ?: MenuItemEntity(
                    id = 0,
                    name = vItem.name,
                    category = "Voice Order",
                    price = vItem.unitPrice,
                    isAvailable = true
                )
            newCart[entity] = (newCart[entity] ?: 0) + vItem.quantity
        }

        _uiState.update {
            it.copy(
                cartItems = newCart,
                orderNotes = if (!notes.isNullOrBlank()) notes else it.orderNotes,
                currentTab = FiltrTab.BILLING,
                snackbarMessage = "Transferred ${voiceItems.sumOf { v -> v.quantity }} item(s) to Cart!"
            )
        }
    }

    /**
     * Directly punches a voice order ticket into Room DB and navigates to the Orders (KDS) screen,
     * immediately opening the receipt dialog and syncing to Google Sheets without redundant confirmation popups.
     */
    fun punchVoiceOrderDirectly(
        voiceItems: List<com.example.voice.model.VoiceOrderLineItem>,
        notes: String? = null
    ) {
        viewModelScope.launch {
            if (_uiState.value.isPlacingOrder || voiceItems.isEmpty()) return@launch

            _uiState.update { it.copy(isPlacingOrder = true) }

            try {
                val currentMenu = menuItems.value
                val cartMap = mutableMapOf<MenuItemEntity, Int>()

                voiceItems.forEach { vItem ->
                    val entity = currentMenu.find { it.name.equals(vItem.name, ignoreCase = true) }
                        ?: MenuItemEntity(
                            id = 0,
                            name = vItem.name,
                            category = "Voice Order",
                            price = vItem.unitPrice,
                            isAvailable = true
                        )
                    cartMap[entity] = (cartMap[entity] ?: 0) + vItem.quantity
                }

                if (cartMap.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isPlacingOrder = false,
                            snackbarMessage = "No valid items in voice order"
                        )
                    }
                    return@launch
                }

                val webhookUrl = _uiState.value.googleSheetsWebhookUrl
                val orderNotes = notes ?: ""

                val createdOrder = repository.createOrder(
                    customerName = "Walk-in Customer",
                    contactNumber = "",
                    cartItems = cartMap,
                    notes = orderNotes,
                    webhookUrl = webhookUrl
                )

                // Redirect directly to Orders / KDS screen with receipt dialog ready
                _uiState.update {
                    it.copy(
                        currentTab = FiltrTab.ORDERS,
                        selectedOrderForReceipt = createdOrder,
                        isPlacingOrder = false,
                        snackbarMessage = "Order #${createdOrder.order.orderId} placed directly from Voice Order!"
                    )
                }

                // Asynchronously sync to Google Sheets in background
                if (webhookUrl.isNotBlank()) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.syncOrderToGoogleSheets(webhookUrl, createdOrder)
                    }
                }

                // Auto-deduct inventory stock based on recipes
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        repository.deductStockForOrder(createdOrder.items)
                    } catch (e: Exception) {
                        Log.w(TAG, "Stock deduction failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPlacingOrder = false,
                        snackbarMessage = "Failed to punch voice order: ${e.message}"
                    )
                }
            }
        }
    }

    // Submit Order
    fun submitOrder() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.isPlacingOrder || state.cartItems.isEmpty()) {
                if (state.cartItems.isEmpty()) {
                    _uiState.update { it.copy(snackbarMessage = "Cart is empty!") }
                }
                return@launch
            }

            // Lock order placement UI immediately to prevent duplicate taps
            _uiState.update { it.copy(isPlacingOrder = true) }

            try {
                val webhookUrl = state.googleSheetsWebhookUrl

                // 1. Create order with multi-device sequence check
                val createdOrder = repository.createOrder(
                    customerName = state.customerName,
                    contactNumber = state.contactNumber,
                    cartItems = state.cartItems,
                    notes = state.orderNotes,
                    webhookUrl = webhookUrl
                )

                // 2. Clear cart & redirect directly to Orders tab immediately with receipt dialog
                clearCart()
                _uiState.update {
                    it.copy(
                        currentTab = FiltrTab.ORDERS,
                        selectedOrderForReceipt = createdOrder,
                        isPlacingOrder = false,
                        snackbarMessage = "Order #${createdOrder.order.orderId} placed successfully!"
                    )
                }

                // 3. Asynchronously sync to Google Sheets in background without blocking UI
                if (webhookUrl.isNotBlank()) {
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        repository.syncOrderToGoogleSheets(webhookUrl, createdOrder)
                    }
                }

                // 4. Auto-deduct inventory stock based on recipes
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        repository.deductStockForOrder(createdOrder.items)
                    } catch (e: Exception) {
                        Log.w(TAG, "Stock deduction failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPlacingOrder = false,
                        snackbarMessage = "Failed to place order: ${e.message}"
                    )
                }
            }
        }
    }

    // Order Actions
    fun toggleOrderItemCompletedStatus(orderId: String, itemId: Int, currentCompletedQty: Int, targetUnitIndex: Int, totalQty: Int) {
        viewModelScope.launch {
            val newCompletedQty = if (targetUnitIndex <= currentCompletedQty) {
                targetUnitIndex - 1
            } else {
                targetUnitIndex
            }
            val safeQty = newCompletedQty.coerceIn(0, totalQty)
            val updatedOrder = repository.updateOrderItemCompletedQuantity(orderId, itemId, safeQty)

            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isNotBlank() && updatedOrder != null) {
                repository.syncOrderToGoogleSheets(url, updatedOrder)
            }
        }
    }

    fun toggleOrderMadeStatus(orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            val newStatus = !orderWithItems.order.isMade
            repository.updateOrderMadeStatus(orderWithItems.order.orderId, newStatus)
            _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} marked as ${if (newStatus) "Made" else "In Progress"}") }

            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isNotBlank()) {
                val updatedOrder = orderWithItems.copy(
                    order = orderWithItems.order.copy(isMade = newStatus)
                )
                repository.syncOrderToGoogleSheets(url, updatedOrder)
            }
        }
    }

    fun setOrderPaymentMode(orderWithItems: OrderWithItems, paymentMode: String) {
        viewModelScope.launch {
            val isPaid = paymentMode == "CASH" || paymentMode == "UPI"
            val safeMode = if (isPaid) paymentMode else "UNPAID"
            repository.updateOrderPaidStatus(orderWithItems.order.orderId, isPaid, safeMode)
            
            val msg = if (isPaid) {
                "Order #${orderWithItems.order.orderId} marked as Paid via $safeMode"
            } else {
                "Order #${orderWithItems.order.orderId} marked as Unpaid"
            }
            _uiState.update { it.copy(snackbarMessage = msg) }

            // Auto sync to Google Sheets if Webhook URL present
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isNotBlank()) {
                val updatedOrder = orderWithItems.copy(
                    order = orderWithItems.order.copy(isPaid = isPaid, paymentMode = safeMode)
                )
                repository.syncOrderToGoogleSheets(url, updatedOrder)
            }
        }
    }

    fun toggleOrderPaidStatus(orderWithItems: OrderWithItems) {
        if (orderWithItems.order.isPaid) {
            setOrderPaymentMode(orderWithItems, "UNPAID")
        } else {
            setOrderPaymentMode(orderWithItems, "CASH")
        }
    }

    fun toggleOrderDeliveredStatus(orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            val newStatus = !orderWithItems.order.isDelivered
            repository.updateOrderDeliveredStatus(orderWithItems.order.orderId, newStatus)
            _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} marked as ${if (newStatus) "Delivered" else "Pending Delivery"}") }

            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isNotBlank()) {
                val updatedOrder = orderWithItems.copy(
                    order = orderWithItems.order.copy(isDelivered = newStatus)
                )
                repository.syncOrderToGoogleSheets(url, updatedOrder)
            }
        }
    }

    fun updateOrderItems(orderWithItems: OrderWithItems, updatedItems: List<OrderItemEntity>) {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            repository.updateOrderWithItems(orderWithItems, updatedItems, url)
            _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} updated successfully!") }
        }
    }

    fun updateOrderWithCustomerDetails(
        orderWithItems: OrderWithItems,
        updatedItems: List<OrderItemEntity>,
        customerName: String,
        contactNumber: String
    ) {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            val updatedOrderEntity = orderWithItems.order.copy(
                customerName = customerName.trim().ifBlank { "Walk-in Customer" },
                contactNumber = contactNumber.trim()
            )
            val updatedOrder = OrderWithItems(updatedOrderEntity, orderWithItems.items)
            repository.updateOrderWithItems(updatedOrder, updatedItems, url)
            _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} updated successfully!") }
        }
    }

    fun syncOrderToSheets(orderWithItems: OrderWithItems) {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isBlank()) {
                _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                return@launch
            }

            _uiState.update { it.copy(snackbarMessage = "Syncing order #${orderWithItems.order.orderId} to Google Sheets...") }
            val result = repository.syncOrderToGoogleSheets(url, orderWithItems)
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = "Synced order #${orderWithItems.order.orderId} to Google Sheets!") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun syncPendingOrdersToSheets() {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isBlank()) {
                _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                return@launch
            }

            _uiState.update { it.copy(snackbarMessage = "Syncing unsynced / offline orders to Google Sheets...") }
            val result = repository.syncPendingOrdersToGoogleSheets(url)
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = result.getOrNull() ?: "Pending orders synced successfully!") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = time1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = time2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun syncAllOrdersToSheets() {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isBlank()) {
                _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                return@launch
            }

            val todayMs = System.currentTimeMillis()
            val dayOrders = ordersList.value.filter { isSameDay(it.order.timestamp, todayMs) }

            if (dayOrders.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No orders found for today to sync.") }
                return@launch
            }

            _uiState.update { it.copy(snackbarMessage = "Syncing today's ${dayOrders.size} order(s) to Google Sheets...") }
            val result = repository.syncAllOrdersToGoogleSheets(url, dayOrders)
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = "Today's ${dayOrders.size} order(s) synced successfully to Google Sheets!") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun syncItemMasterToSheets() {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isBlank()) {
                _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                return@launch
            }

            _uiState.update { it.copy(snackbarMessage = "Syncing Item Master to Google Sheets...") }
            val result = repository.syncItemMasterToGoogleSheets(url, menuItems.value)
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = "Item Master synced successfully to Google Sheets!") }
            } else {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    fun deleteOrder(orderId: String) {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            repository.deleteOrder(orderId, url)
            _uiState.update { it.copy(snackbarMessage = "Order #$orderId deleted") }
        }
    }

    // AI Voice Order Handling
    fun startListeningVoice() {
        _uiState.update {
            it.copy(
                isListeningVoice = true,
                isParsingVoice = false,
                voiceTranscript = "",
                parsedVoiceOrder = null,
                showVoiceOrderReviewDialog = false
            )
        }
    }

    fun processVoiceAudio(audioBytes: ByteArray, mimeType: String = "audio/m4a", durationMs: Long = 0L) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isListeningVoice = false,
                    isParsingVoice = true,
                    parsedVoiceOrder = null,
                    showVoiceOrderReviewDialog = false,
                    snackbarMessage = "✨ Gemini 3.5 Flash Lite analyzing voice recording..."
                )
            }

            val items = menuItems.value
            val result = repository.parseVoiceAudio(audioBytes, mimeType, items)

            // Asynchronously dispatch voice telemetry (audio file + diagnostic JSON) to Supabase Storage voice-orders bucket
            val timestamp = System.currentTimeMillis()
            val timeString = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date(timestamp))
            val baseFileName = "voice_order_${timeString}_${timestamp % 1000}"

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val supabase = SupabaseService.getInstance()
                    val audioFileName = "$baseFileName.m4a"
                    val audioUploadResult = supabase.uploadVoiceOrderAudio(audioFileName, audioBytes)

                    val isSuccess = result.isSuccess && result.getOrNull() != null
                    val parsedOrder = result.getOrNull()
                    val diagJson = org.json.JSONObject().apply {
                        put("timestamp", timestamp)
                        put("formattedTime", timeString)
                        put("durationMs", durationMs)
                        put("fileSizeBytes", audioBytes.size)
                        put("mimeType", mimeType)
                        put("isSuccess", isSuccess)
                        put("audioUploaded", audioUploadResult.isSuccess)
                        put("audioUrl", audioUploadResult.getOrNull() ?: "")
                        put("rawTranscript", parsedOrder?.rawTranscript ?: "")
                        put("parsedCustomerName", parsedOrder?.customerName ?: "")
                        put("parsedContactNumber", parsedOrder?.contactNumber ?: "")
                        put("parsedNotes", parsedOrder?.notes ?: "")
                        val itemsArray = org.json.JSONArray()
                        parsedOrder?.items?.forEach { item ->
                            val itemObj = org.json.JSONObject().apply {
                                put("name", item.menuItemName)
                                put("quantity", item.quantity)
                            }
                            itemsArray.put(itemObj)
                        }
                        put("items", itemsArray)
                        if (result.isFailure) {
                            put("errorMessage", result.exceptionOrNull()?.message ?: "Unknown error")
                        }
                    }.toString(2)

                    val diagFileName = "$baseFileName.json"
                    supabase.uploadVoiceOrderDiagnostics(diagFileName, diagJson)
                    Log.d("MainViewModel", "Voice telemetry uploaded successfully: $baseFileName")
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Failed to upload voice telemetry: ${e.message}")
                }
            }

            if (result.isSuccess && result.getOrNull() != null) {
                val parsedOrder = result.getOrNull() ?: return@launch
                val allMenu = menuItems.value
                val newCart = mutableMapOf<MenuItemEntity, Int>()

                parsedOrder.items.forEach { parsedItem ->
                    val menuObj = allMenu.find { it.name.trim().equals(parsedItem.menuItemName.trim(), ignoreCase = true) }
                        ?: allMenu.find { it.name.lowercase().contains(parsedItem.menuItemName.lowercase()) || parsedItem.menuItemName.lowercase().contains(it.name.lowercase()) }

                    if (menuObj != null) {
                        val currentQty = newCart[menuObj] ?: 0
                        newCart[menuObj] = currentQty + parsedItem.quantity
                    }
                }

                val custName = if (parsedOrder.customerName != "Walk-in Customer" && parsedOrder.customerName.isNotBlank()) parsedOrder.customerName else "Walk-in Customer"
                val contact = parsedOrder.contactNumber

                _uiState.update { state ->
                    state.copy(
                        isParsingVoice = false,
                        parsedVoiceOrder = parsedOrder,
                        showVoiceOrderReviewDialog = true,
                        customerName = if (custName.isNotBlank()) custName else state.customerName,
                        contactNumber = if (contact.isNotBlank()) contact else state.contactNumber,
                        orderNotes = if (parsedOrder.notes.isNotBlank()) parsedOrder.notes else state.orderNotes,
                        cartItems = if (newCart.isNotEmpty()) newCart else state.cartItems,
                        snackbarMessage = "✨ Voice Order Parsed! ${newCart.values.sum()} items added for $custName"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isParsingVoice = false,
                        snackbarMessage = "Could not parse voice recording. Please try speaking clearly."
                    )
                }
            }
        }
    }

    fun processVoiceTranscript(transcript: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isListeningVoice = false,
                    isParsingVoice = true,
                    voiceTranscript = transcript,
                    parsedVoiceOrder = null,
                    showVoiceOrderReviewDialog = false
                )
            }

            val items = menuItems.value
            val result = repository.parseVoiceOrder(transcript, items)

            if (result.isSuccess) {
                val parsedOrder = result.getOrNull()
                _uiState.update {
                    it.copy(
                        isParsingVoice = false,
                        parsedVoiceOrder = parsedOrder,
                        showVoiceOrderReviewDialog = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isParsingVoice = false,
                        snackbarMessage = "Could not parse voice order. Please try speaking again."
                    )
                }
            }
        }
    }

    fun cancelVoiceListening() {
        _uiState.update {
            it.copy(
                isListeningVoice = false,
                isParsingVoice = false,
                parsedVoiceOrder = null,
                showVoiceOrderReviewDialog = false
            )
        }
    }

    fun applyVoiceOrderToCart(parsedOrder: ParsedVoiceOrder) {
        val allMenu = menuItems.value
        val newCart = _uiState.value.cartItems.toMutableMap()

        parsedOrder.items.forEach { parsedItem ->
            val menuObj = allMenu.find { it.name.trim().equals(parsedItem.menuItemName.trim(), ignoreCase = true) }
                ?: allMenu.find { it.name.contains(parsedItem.menuItemName.trim(), ignoreCase = true) }
                ?: allMenu.find { parsedItem.menuItemName.trim().contains(it.name, ignoreCase = true) }
            if (menuObj != null) {
                val currentQty = newCart[menuObj] ?: 0
                newCart[menuObj] = currentQty + parsedItem.quantity
            } else {
                val fallbackItem = MenuItemEntity(
                    id = 0,
                    name = parsedItem.menuItemName,
                    category = "VOICE",
                    price = 0.0,
                    description = ""
                )
                val currentQty = newCart[fallbackItem] ?: 0
                newCart[fallbackItem] = currentQty + parsedItem.quantity
            }
        }

        _uiState.update {
            it.copy(
                customerName = if (parsedOrder.customerName != "Walk-in Customer" && parsedOrder.customerName.isNotBlank()) parsedOrder.customerName else it.customerName,
                contactNumber = if (parsedOrder.contactNumber.isNotBlank()) parsedOrder.contactNumber else it.contactNumber,
                orderNotes = if (parsedOrder.notes.isNotBlank()) parsedOrder.notes else it.orderNotes,
                cartItems = newCart,
                showVoiceOrderReviewDialog = false,
                parsedVoiceOrder = null,
                currentTab = FiltrTab.BILLING,
                snackbarMessage = "Voice order applied to bill!"
            )
        }
    }

    fun dismissVoiceOrderDialog() {
        _uiState.update {
            it.copy(
                showVoiceOrderReviewDialog = false,
                parsedVoiceOrder = null
            )
        }
    }

    // Item Master (Menu) Actions
    fun openAddEditItemDialog(item: MenuItemEntity? = null) {
        _uiState.update {
            it.copy(
                showAddEditItemDialog = true,
                editingMenuItem = item
            )
        }
    }

    fun dismissAddEditItemDialog() {
        _uiState.update {
            it.copy(
                showAddEditItemDialog = false,
                editingMenuItem = null
            )
        }
    }

    fun saveMenuItem(name: String, category: String, price: Double, description: String, isAvailable: Boolean) {
        viewModelScope.launch {
            val editing = _uiState.value.editingMenuItem
            val existingMatchingItem = if (editing == null) {
                menuItems.value.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
            } else null

            val targetItem = editing ?: existingMatchingItem
            val cleanName = name.trim()
            val cleanCat = category.trim().uppercase()

            if (targetItem != null) {
                val oldName = targetItem.name.trim()
                repository.updateMenuItem(
                    targetItem.copy(
                        name = cleanName,
                        category = cleanCat,
                        price = price,
                        description = description.trim(),
                        isAvailable = isAvailable
                    )
                )
                if (!oldName.equals(cleanName, ignoreCase = true)) {
                    val currentOrder = _uiState.value.customItemOrder.toMutableList()
                    val idx = currentOrder.indexOfFirst { it.equals(oldName, ignoreCase = true) }
                    if (idx != -1) {
                        currentOrder[idx] = cleanName
                        saveCustomItemOrder(currentOrder)
                    }
                }
                _uiState.update { it.copy(snackbarMessage = "Updated $cleanName") }
            } else {
                repository.addMenuItem(
                    MenuItemEntity(
                        name = cleanName,
                        category = cleanCat,
                        price = price,
                        description = description.trim(),
                        isAvailable = isAvailable
                    )
                )
                val currentOrder = _uiState.value.customItemOrder
                if (!currentOrder.any { it.equals(cleanName, ignoreCase = true) }) {
                    saveCustomItemOrder(currentOrder + cleanName)
                }
                _uiState.update { it.copy(snackbarMessage = "Added $cleanName to Menu") }
            }

            // If category is not in default or custom list, auto-add to customCategories
            val defaultCats = listOf("ALL", "ICED COFFEE", "FRAPPE", "FRIES", "SANDWICH")
            if (cleanCat.isNotBlank() && cleanCat !in defaultCats && cleanCat !in _uiState.value.customCategories) {
                addCustomCategory(cleanCat)
            }

            dismissAddEditItemDialog()
        }
    }

    fun setDashboardDateFilter(filter: String) {
        _uiState.update { it.copy(dashboardDateFilter = filter) }

        val startMs: Long
        val endMs: Long

        when (filter) {
            "TODAY" -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startMs = calStart.timeInMillis
                endMs = calEnd.timeInMillis
            }
            "YESTERDAY" -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                startMs = calStart.timeInMillis
                endMs = calEnd.timeInMillis
            }
            "THIS_WEEK" -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -7)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startMs = calStart.timeInMillis
                endMs = System.currentTimeMillis()
            }
            "THIS_MONTH" -> {
                val calStart = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                startMs = calStart.timeInMillis
                endMs = System.currentTimeMillis()
            }
            "CUSTOM" -> {
                startMs = _uiState.value.customStartDateMs ?: 0L
                endMs = _uiState.value.customEndDateMs ?: Long.MAX_VALUE
            }
            else -> { // ALL_TIME
                startMs = 0L
                endMs = Long.MAX_VALUE
            }
        }

        // Backfill from Firestore/Supabase for all date ranges including TODAY
        // The real-time listener may be suspended by battery optimization (e.g. OnePlus OplusHansManager),
        // so we always backfill to ensure multi-device orders are synced
        if (filter != "CUSTOM" || (startMs > 0L && endMs < Long.MAX_VALUE)) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val fetchStart = if (startMs == 0L) {
                        // ALL_TIME: fetch last 365 days (reasonable limit)
                        System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
                    } else startMs
                    val fetchEnd = if (endMs == Long.MAX_VALUE) System.currentTimeMillis() else endMs

                    val backfilled = repository.backfillOrdersForDateRange(fetchStart, fetchEnd)
                    if (backfilled > 0) {
                        _uiState.update {
                            it.copy(snackbarMessage = "$backfilled orders synced from Firebase")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Backfill for $filter failed: ${e.message}")
                }
            }
        }

        syncDateRangeAnalyticsToSheets(startMs, endMs)
    }

    // Accounts / UPI Dialog Management
    fun openAccountsUpiDialog() {
        _uiState.update { it.copy(showAccountsUpiDialog = true) }
    }

    fun closeAccountsUpiDialog() {
        _uiState.update { it.copy(showAccountsUpiDialog = false) }
    }

    fun addUpiAccount(vpa: String, payeeName: String, isDefault: Boolean) {
        viewModelScope.launch {
            repository.addUpiAccount(vpa, payeeName, isDefault)
            _uiState.update { it.copy(snackbarMessage = "UPI ID added successfully!") }
        }
    }

    fun deleteUpiAccount(account: com.example.data.entity.UpiAccountEntity) {
        viewModelScope.launch {
            repository.deleteUpiAccount(account)
            _uiState.update { it.copy(snackbarMessage = "UPI ID removed.") }
        }
    }

    fun setDefaultUpiAccount(accountId: Int) {
        viewModelScope.launch {
            repository.setDefaultUpiAccount(accountId)
            _uiState.update { it.copy(snackbarMessage = "Default UPI ID updated.") }
        }
    }

    // Delete Menu Item
    fun requestDeleteMenuItem(item: MenuItemEntity) {
        _uiState.update {
            it.copy(
                itemToDeleteConfirmation = item,
                itemDeleteWarningMessage = null
            )
        }
    }

    fun dismissDeleteDialogs() {
        if (_uiState.value.isDeletingOrder) return
        _uiState.update {
            it.copy(
                itemToDeleteConfirmation = null,
                itemDeleteWarningMessage = null,
                orderToDeleteConfirmation = null,
                isDeletingOrder = false
            )
        }
    }

    fun confirmDeleteMenuItem(item: MenuItemEntity) {
        viewModelScope.launch {
            repository.deleteMenuItem(item)
            val currentOrder = _uiState.value.customItemOrder
            val newOrder = currentOrder.filterNot { it.equals(item.name.trim(), ignoreCase = true) }
            saveCustomItemOrder(newOrder)
            _uiState.update {
                it.copy(
                    itemToDeleteConfirmation = null,
                    itemDeleteWarningMessage = null,
                    snackbarMessage = "Removed '${item.name}' from Item Master"
                )
            }
        }
    }

    fun markItemUnavailable(item: MenuItemEntity) {
        viewModelScope.launch {
            repository.updateMenuItem(item.copy(isAvailable = false))
            _uiState.update {
                it.copy(
                    itemDeleteWarningMessage = null,
                    snackbarMessage = "'${item.name}' marked as Unavailable to preserve order records"
                )
            }
        }
    }

    fun requestDeleteOrder(orderId: String) {
        _uiState.update { it.copy(orderToDeleteConfirmation = orderId) }
    }

    fun confirmDeleteOrder(orderId: String) {
        if (_uiState.value.isDeletingOrder) return
        _uiState.update { it.copy(isDeletingOrder = true) }
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                repository.deleteOrder(orderId, url)
                _uiState.update {
                    it.copy(
                        orderToDeleteConfirmation = null,
                        isDeletingOrder = false,
                        snackbarMessage = "Order #$orderId deleted. Syncing to Sheets..."
                    )
                }
                // Re-sync today's orders to Sheets — Firebase/Room is source of truth
                // This reads from Room (in-memory), NOT Firebase — zero extra Firebase reads
                // The syncAll handler removes orders from sheet that aren't in the list
                // datePrefix ensures only that day's rows are cleaned (preserves historical data)
                if (url.isNotBlank()) {
                    val todayMs = System.currentTimeMillis()
                    val todayOrders = ordersList.value.filter { isSameDay(it.order.timestamp, todayMs) }
                    // Extract date prefix from orderId (e.g., "20260806" from "FLT-20260806-1001")
                    val datePrefix = orderId.split("-").getOrNull(1) ?: SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    val result = repository.syncAllOrdersToGoogleSheets(url, todayOrders, datePrefix)
                    if (result.isSuccess) {
                        _uiState.update { it.copy(snackbarMessage = "Order #$orderId deleted & Sheets synced ✓") }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        orderToDeleteConfirmation = null,
                        isDeletingOrder = false,
                        snackbarMessage = "Failed to delete order: ${e.message}"
                    )
                }
            }
        }
    }

    fun openCustomDatePicker() {
        _uiState.update { it.copy(showCustomDatePickerDialog = true) }
    }

    fun dismissCustomDatePicker() {
        _uiState.update { it.copy(showCustomDatePickerDialog = false) }
    }

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        _uiState.update {
            it.copy(
                dashboardDateFilter = "CUSTOM",
                customStartDateMs = startMs,
                customEndDateMs = endMs,
                showCustomDatePickerDialog = false,
                snackbarMessage = "Fetching orders for selected range..."
            )
        }

        // Backfill any missing orders from Firestore for this date range
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val backfilled = repository.backfillOrdersForDateRange(startMs, endMs)
                if (backfilled > 0) {
                    _uiState.update {
                        it.copy(snackbarMessage = "$backfilled new orders synced from Firebase for selected range")
                    }
                } else {
                    _uiState.update {
                        it.copy(snackbarMessage = "Custom date range applied — all orders up to date")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Date range backfill failed: ${e.message}")
                _uiState.update {
                    it.copy(snackbarMessage = "Custom date range applied (offline mode)")
                }
            }
        }

        syncDateRangeAnalyticsToSheets(startMs, endMs)
    }

    fun syncDateRangeAnalyticsToSheets(startMs: Long, endMs: Long) {
        viewModelScope.launch {
            val url = _uiState.value.googleSheetsWebhookUrl
            if (url.isBlank()) return@launch

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val startStr = sdf.format(Date(startMs))
            val endStr = sdf.format(Date(endMs))

            val filteredOrders = ordersList.value.filter { orderWithItems ->
                orderWithItems.order.timestamp in startMs..endMs
            }

            val result = repository.syncDateRangeAnalyticsToGoogleSheets(url, startStr, endStr, filteredOrders)
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = "Range analytics & calculations synced to Google Sheets!") }
            }
        }
    }

    fun deleteMenuItem(item: MenuItemEntity) {
        requestDeleteMenuItem(item)
    }

    // Settings & Webhook
    fun openSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveGoogleSheetsWebhookUrl(url: String) {
        val trimmedUrl = url.trim()
        prefs.edit().putString("sheets_webhook_url", trimmedUrl).apply()
        _uiState.update {
            it.copy(
                googleSheetsWebhookUrl = trimmedUrl,
                showSettingsDialog = false,
                snackbarMessage = "Google Sheets Webhook URL saved! Syncing pending orders..."
            )
        }
        if (trimmedUrl.isNotBlank()) {
            syncPendingOrdersToSheets()
        }
    }

    // Receipt View Modal
    fun viewOrderReceipt(orderWithItems: OrderWithItems?) {
        _uiState.update { it.copy(selectedOrderForReceipt = orderWithItems) }
    }

    fun setStatusFilter(filter: String) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    fun setOrdersDateFilter(filter: String) {
        _uiState.update { it.copy(ordersDateFilter = filter) }
    }

    fun openOrdersDatePicker() {
        _uiState.update { it.copy(showOrdersDatePickerDialog = true) }
    }

    fun dismissOrdersDatePicker() {
        _uiState.update { it.copy(showOrdersDatePickerDialog = false) }
    }

    fun setOrdersCustomDateRange(startMs: Long, endMs: Long) {
        _uiState.update {
            it.copy(
                ordersCustomStartMs = startMs,
                ordersCustomEndMs = endMs,
                ordersDateFilter = "CUSTOM",
                showOrdersDatePickerDialog = false
            )
        }
        backfillOrdersForDateRange(startMs, endMs)
    }

    fun backfillOrdersForDateRange(startMs: Long, endMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backfilled = repository.backfillOrdersForDateRange(startMs, endMs)
                if (backfilled > 0) {
                    _uiState.update {
                        it.copy(snackbarMessage = "$backfilled orders synced from cloud")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Backfill for range failed: ${e.message}")
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    // ==================== RECEIPT STORY & PHOTO ====================

    fun openReceiptStoryDialog() {
        _uiState.update { it.copy(showReceiptStoryDialog = true) }
    }

    fun closeReceiptStoryDialog() {
        _uiState.update { it.copy(showReceiptStoryDialog = false) }
    }

    fun updateReceiptStory(
        title: String,
        date: String,
        caption: String,
        imagePath: String,
        isEnabled: Boolean
    ) {
        prefs.edit()
            .putString("receipt_story_title", title)
            .putString("receipt_story_date", date)
            .putString("receipt_story_caption", caption)
            .putString("receipt_story_image_path", imagePath)
            .putBoolean("receipt_story_enabled", isEnabled)
            .apply()

        _uiState.update {
            it.copy(
                receiptStoryTitle = title,
                receiptStoryDate = date,
                receiptStoryCaption = caption,
                receiptStoryImagePath = imagePath,
                isReceiptStoryEnabled = isEnabled,
                showReceiptStoryDialog = false,
                snackbarMessage = "Receipt Story updated successfully!"
            )
        }
    }

    fun generateReceiptStoryNote(title: String, date: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingStoryNote = true) }
            val result = repository.generateStoryNote(title, date)
            val note = result.getOrDefault("Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.")
            _uiState.update {
                it.copy(
                    receiptStoryCaption = note,
                    isGeneratingStoryNote = false,
                    snackbarMessage = "AI note generated!"
                )
            }
        }
    }

    // ==================== STORY PHOTO STORAGE (Supabase) ====================

    fun loadStoryPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingStoryPhotos = true) }
            val result = repository.listStoryPhotos()
            val photos = result.getOrDefault(emptyList())

            // Also sync receipt design config and metadata from Supabase
            try {
                val remoteConfig = repository.fetchReceiptDesignConfig()
                if (!remoteConfig.isNullOrBlank()) {
                    val json = org.json.JSONObject(remoteConfig)
                    val tagline = json.optString("tagline", _uiState.value.receiptTagline)
                    val subtitle = json.optString("headerSubtitle", _uiState.value.receiptHeaderSubtitle)
                    val f1 = json.optString("footerLine1", _uiState.value.receiptFooterLine1)
                    val f2 = json.optString("footerLine2", _uiState.value.receiptFooterLine2)
                    val f3 = json.optString("footerLine3", _uiState.value.receiptFooterLine3)
                    val enabled = json.optBoolean("isStoryEnabled", _uiState.value.isReceiptStoryEnabled)
                    val metaObj = json.optJSONObject("metadataMap")
                    val currentMap = _uiState.value.storyPhotoMetadataMap.toMutableMap()
                    if (metaObj != null) {
                        val keys = metaObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val item = metaObj.getJSONObject(k)
                            currentMap[k] = StoryPhotoMetadata(
                                fileName = item.optString("fileName", k),
                                title = item.optString("title", ""),
                                date = item.optString("date", ""),
                                caption = item.optString("caption", "")
                            )
                        }
                        saveStoryPhotoMetadataMap(currentMap)
                    }
                    _uiState.update {
                        it.copy(
                            receiptTagline = tagline,
                            receiptHeaderSubtitle = subtitle,
                            receiptFooterLine1 = f1,
                            receiptFooterLine2 = f2,
                            receiptFooterLine3 = f3,
                            isReceiptStoryEnabled = enabled,
                            storyPhotoMetadataMap = currentMap
                        )
                    }
                }
            } catch (e: Exception) {
                // Keep local cache if remote fetch fails
            }

            _uiState.update {
                it.copy(
                    storyPhotoNames = photos,
                    isLoadingStoryPhotos = false
                )
            }
        }
    }

    fun selectStoryPhoto(fileName: String?) {
        _uiState.update { it.copy(selectedStoryPhotoName = fileName) }
    }

    private fun pushReceiptConfigToSupabase() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val config = org.json.JSONObject().apply {
                    put("tagline", state.receiptTagline)
                    put("headerSubtitle", state.receiptHeaderSubtitle)
                    put("footerLine1", state.receiptFooterLine1)
                    put("footerLine2", state.receiptFooterLine2)
                    put("footerLine3", state.receiptFooterLine3)
                    put("isStoryEnabled", state.isReceiptStoryEnabled)
                    val metaObj = org.json.JSONObject()
                    for ((k, v) in state.storyPhotoMetadataMap) {
                        metaObj.put(k, org.json.JSONObject().apply {
                            put("fileName", v.fileName)
                            put("title", v.title)
                            put("date", v.date)
                            put("caption", v.caption)
                        })
                    }
                    put("metadataMap", metaObj)
                }
                repository.saveReceiptDesignConfig(config.toString())
            } catch (e: Exception) {
                // Ignore network failure, local cache is already saved
            }
        }
    }

    fun updateStoryPhotoMetadata(fileName: String, title: String, date: String, caption: String) {
        val currentMap = _uiState.value.storyPhotoMetadataMap.toMutableMap()
        currentMap[fileName] = StoryPhotoMetadata(
            fileName = fileName,
            title = title,
            date = date,
            caption = caption
        )
        saveStoryPhotoMetadataMap(currentMap)
        _uiState.update {
            it.copy(
                storyPhotoMetadataMap = currentMap,
                snackbarMessage = "Photo details saved!"
            )
        }
        pushReceiptConfigToSupabase()
    }

    fun updateReceiptDesignText(
        tagline: String,
        headerSubtitle: String,
        footerLine1: String,
        footerLine2: String,
        footerLine3: String,
        isStoryEnabled: Boolean
    ) {
        prefs.edit()
            .putString("receipt_tagline", tagline)
            .putString("receipt_header_subtitle", headerSubtitle)
            .putString("receipt_footer_line1", footerLine1)
            .putString("receipt_footer_line2", footerLine2)
            .putString("receipt_footer_line3", footerLine3)
            .putBoolean("receipt_story_enabled", isStoryEnabled)
            .apply()

        _uiState.update {
            it.copy(
                receiptTagline = tagline,
                receiptHeaderSubtitle = headerSubtitle,
                receiptFooterLine1 = footerLine1,
                receiptFooterLine2 = footerLine2,
                receiptFooterLine3 = footerLine3,
                isReceiptStoryEnabled = isStoryEnabled,
                snackbarMessage = "Receipt design updated & synced!"
            )
        }
        pushReceiptConfigToSupabase()
    }

    suspend fun generateStoryNoteAsync(title: String, date: String): String {
        return try {
            val result = repository.generateStoryNote(title, date)
            result.getOrDefault("Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you.")
        } catch (e: Exception) {
            "Late nights, endless trials, and pure passion. We spent months perfecting this exact brew ratio for you."
        }
    }

    fun uploadStoryPhoto(imageBytes: ByteArray, title: String = "", date: String = "", caption: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingStoryPhoto = true) }
            val fileName = "story_${System.currentTimeMillis()}.jpg"
            val result = repository.uploadStoryPhoto(fileName, imageBytes)
            if (result.isSuccess) {
                val currentMap = _uiState.value.storyPhotoMetadataMap.toMutableMap()
                currentMap[fileName] = StoryPhotoMetadata(
                    fileName = fileName,
                    title = title.ifBlank { "FILTR Moment" },
                    date = date,
                    caption = caption
                )
                saveStoryPhotoMetadataMap(currentMap)
                val listResult = repository.listStoryPhotos()
                val photos = listResult.getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        storyPhotoNames = photos,
                        storyPhotoMetadataMap = currentMap,
                        selectedStoryPhotoName = fileName,
                        isUploadingStoryPhoto = false,
                        snackbarMessage = "Photo uploaded and saved!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isUploadingStoryPhoto = false,
                        snackbarMessage = "Upload failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun deleteStoryPhotoByName(fileName: String) {
        viewModelScope.launch {
            val result = repository.deleteStoryPhoto(fileName)
            if (result.isSuccess) {
                val currentMap = _uiState.value.storyPhotoMetadataMap.toMutableMap()
                currentMap.remove(fileName)
                saveStoryPhotoMetadataMap(currentMap)
                _uiState.update {
                    it.copy(
                        storyPhotoNames = it.storyPhotoNames.filter { name -> name != fileName },
                        storyPhotoMetadataMap = currentMap,
                        selectedStoryPhotoName = if (it.selectedStoryPhotoName == fileName) null else it.selectedStoryPhotoName,
                        snackbarMessage = "Photo deleted"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(snackbarMessage = "Delete failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Get a random story photo bitmap and metadata for printing.
     * Downloads a random photo from the Supabase Storage bucket.
     */
    suspend fun getRandomStoryPhotoForPrint(): StoryPhotoPrintData? {
        val photos = _uiState.value.storyPhotoNames
        if (photos.isEmpty()) {
            val localPath = _uiState.value.receiptStoryImagePath
            if (localPath.isNotBlank()) {
                val bitmap = try {
                    android.graphics.BitmapFactory.decodeFile(localPath)
                } catch (e: Exception) {
                    null
                }
                return if (bitmap != null) {
                    StoryPhotoPrintData(
                        bitmap = bitmap,
                        title = _uiState.value.receiptStoryTitle,
                        date = _uiState.value.receiptStoryDate,
                        caption = _uiState.value.receiptStoryCaption
                    )
                } else null
            }
            return null
        }

        val randomFileName = photos.random()
        val result = repository.downloadStoryPhoto(randomFileName)
        val bitmap = if (result.isSuccess) {
            val bytes = result.getOrNull()
            if (bytes != null) {
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else null
        } else null

        if (bitmap == null) return null

        val meta = _uiState.value.storyPhotoMetadataMap[randomFileName]
        val title = meta?.title?.ifBlank { null } ?: _uiState.value.receiptStoryTitle
        val date = meta?.date?.ifBlank { null } ?: _uiState.value.receiptStoryDate
        val caption = meta?.caption?.ifBlank { null } ?: _uiState.value.receiptStoryCaption

        return StoryPhotoPrintData(
            bitmap = bitmap,
            title = title,
            date = date,
            caption = caption
        )
    }

    fun getStoryPhotoUrl(fileName: String): String {
        return repository.getStoryPhotoUrl(fileName)
    }

    // ==================== INVENTORY ====================

    fun openInventoryMasterDialog() {
        _uiState.update { it.copy(showInventoryMasterDialog = true) }
    }

    fun dismissInventoryMasterDialog() {
        _uiState.update { it.copy(showInventoryMasterDialog = false, editingInventoryItem = null, showAddInventoryItemDialog = false) }
    }

    fun openAddInventoryItemDialog(item: InventoryItemEntity? = null) {
        _uiState.update { it.copy(showAddInventoryItemDialog = true, editingInventoryItem = item) }
    }

    fun dismissAddInventoryItemDialog() {
        _uiState.update { it.copy(showAddInventoryItemDialog = false, editingInventoryItem = null) }
    }

    fun saveInventoryItem(name: String, category: String, unit: String, defaultPrice: Double, standardQuantity: Double = 1.0) {
        viewModelScope.launch {
            val editing = _uiState.value.editingInventoryItem
            if (editing != null) {
                repository.updateInventoryItem(editing.copy(name = name, category = category, unit = unit, defaultPrice = defaultPrice, standardQuantity = standardQuantity))
                _uiState.update { it.copy(snackbarMessage = "Inventory item updated", showAddInventoryItemDialog = false, editingInventoryItem = null) }
            } else {
                repository.addInventoryItem(InventoryItemEntity(name = name, category = category, unit = unit, defaultPrice = defaultPrice, standardQuantity = standardQuantity))
                _uiState.update { it.copy(snackbarMessage = "Inventory item added", showAddInventoryItemDialog = false) }
            }
        }
    }

    fun deleteInventoryItem(item: InventoryItemEntity) {
        viewModelScope.launch {
            repository.deleteInventoryItem(item)
            _uiState.update { it.copy(snackbarMessage = "\"${item.name}\" removed from inventory master") }
        }
    }

    fun updateInventorySearchQuery(query: String) {
        _uiState.update { it.copy(inventorySearchQuery = query) }
    }

    fun openAddInventoryOrderDialog() {
        _uiState.update { it.copy(showAddInventoryOrderDialog = true) }
    }

    fun dismissAddInventoryOrderDialog() {
        _uiState.update { it.copy(showAddInventoryOrderDialog = false) }
    }

    fun saveInventoryOrder(
        supplierName: String,
        notes: String,
        items: List<Triple<String, Double, Double>> // (itemName, quantity, unitPrice)
    ) {
        viewModelScope.launch {
            val orderId = repository.getNextInventoryOrderId()
            val totalAmount = items.sumOf { it.second * it.third }

            val orderEntity = InventoryOrderEntity(
                orderId = orderId,
                timestamp = System.currentTimeMillis(),
                supplierName = supplierName,
                totalAmount = totalAmount,
                notes = notes
            )

            // Filter out blank items
            val validItems = items.filter { it.first.isNotBlank() }
            if (validItems.isEmpty()) {
                _uiState.update { it.copy(snackbarMessage = "No valid items to save") }
                return@launch
            }

            val orderItems = validItems.map { (name, qty, price) ->
                InventoryOrderItemEntity(
                    orderId = orderId,
                    itemName = name,
                    quantity = qty,
                    unitPrice = price,
                    subtotal = qty * price
                )
            }

            // Auto-add new items to inventory master BEFORE creating the order
            // so that addInventoryOrder can find them and add stock correctly
            for ((name, _, price) in validItems) {
                val existing = repository.getInventoryItemByName(name)
                if (existing == null) {
                    repository.addInventoryItem(InventoryItemEntity(name = name, defaultPrice = price))
                }
            }

            repository.addInventoryOrder(orderEntity, orderItems)

            _uiState.update {
                it.copy(
                    showAddInventoryOrderDialog = false,
                    snackbarMessage = "Inventory order $orderId saved (${validItems.size} items, ₹${"%.0f".format(totalAmount)})"
                )
            }
        }
    }

    fun requestDeleteInventoryOrder(orderId: String) {
        _uiState.update { it.copy(inventoryOrderToDelete = orderId) }
    }

    fun dismissDeleteInventoryOrder() {
        _uiState.update { it.copy(inventoryOrderToDelete = null) }
    }

    fun confirmDeleteInventoryOrder(orderId: String) {
        viewModelScope.launch {
            repository.deleteInventoryOrder(orderId)
            _uiState.update { it.copy(inventoryOrderToDelete = null, snackbarMessage = "Inventory order $orderId deleted") }
        }
    }

    // ==================== INVENTORIES (STOCK) ====================

    fun updateStockLevel(itemId: Int, newStock: Double) {
        viewModelScope.launch {
            repository.updateStock(itemId, newStock)
            _uiState.update { it.copy(editingStockItemId = null, snackbarMessage = "Stock updated") }
        }
    }

    fun startEditingStock(itemId: Int) {
        _uiState.update { it.copy(editingStockItemId = itemId) }
    }

    fun cancelEditingStock() {
        _uiState.update { it.copy(editingStockItemId = null) }
    }

    fun openAddStockDialog(item: InventoryItemEntity) {
        _uiState.update { it.copy(showAddStockDialog = true, addStockItem = item) }
    }

    fun dismissAddStockDialog() {
        _uiState.update { it.copy(showAddStockDialog = false, addStockItem = null) }
    }

    fun addStockPacks(itemId: Int, packsToAdd: Double) {
        viewModelScope.launch {
            repository.addStock(itemId, packsToAdd)
            _uiState.update { it.copy(showAddStockDialog = false, addStockItem = null, snackbarMessage = "Stock added: +$packsToAdd packs") }
        }
    }

    // ==================== INVENTORY USAGE (RECIPES) ====================

    fun openUsagePerSkuDialog(menuItemId: Int, menuItemName: String) {
        _uiState.update { it.copy(showUsagePerSkuDialog = true, usageEditingMenuItemId = menuItemId, usageEditingMenuItemName = menuItemName) }
    }

    fun dismissUsagePerSkuDialog() {
        _uiState.update { it.copy(showUsagePerSkuDialog = false, usageEditingMenuItemId = null, usageEditingMenuItemName = "") }
    }

    fun saveUsage(menuItemId: Int, menuItemName: String, inventoryItemId: Int, inventoryItemName: String, quantityUsed: Double, unit: String) {
        viewModelScope.launch {
            // Check for existing usage to avoid duplicates
            val existing = repository.getUsageForMenuAndInventoryItem(menuItemId, inventoryItemId)
            if (existing != null) {
                // Update existing record instead of creating duplicate
                repository.updateUsage(existing.copy(quantityUsed = quantityUsed, unit = unit, inventoryItemName = inventoryItemName, menuItemName = menuItemName))
                _uiState.update { it.copy(snackbarMessage = "$inventoryItemName usage updated for $menuItemName") }
            } else {
                // Create new record
                repository.saveUsage(
                    InventoryUsageEntity(
                        menuItemId = menuItemId,
                        menuItemName = menuItemName,
                        inventoryItemId = inventoryItemId,
                        inventoryItemName = inventoryItemName,
                        quantityUsed = quantityUsed,
                        unit = unit
                    )
                )
                _uiState.update { it.copy(snackbarMessage = "$inventoryItemName usage saved for $menuItemName") }
            }
        }
    }

    fun deleteUsage(usage: InventoryUsageEntity) {
        viewModelScope.launch {
            repository.deleteUsage(usage)
            _uiState.update { it.copy(snackbarMessage = "${usage.inventoryItemName} removed from ${usage.menuItemName} recipe") }
        }
    }

    // ==================== CUSTOMER MANAGEMENT ====================

    fun addCustomer(name: String, contact: String) {
        viewModelScope.launch {
            val result = repository.saveCustomer(name.trim(), contact.trim())
            if (result.isSuccess) {
                _uiState.update { it.copy(snackbarMessage = "Customer '${name.trim()}' added") }
            } else {
                _uiState.update { it.copy(snackbarMessage = result.exceptionOrNull()?.message ?: "Failed to add customer") }
            }
        }
    }

    fun updateExistingCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            _uiState.update { it.copy(snackbarMessage = "Customer '${customer.name}' updated") }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            _uiState.update { it.copy(snackbarMessage = "Customer '${customer.name}' deleted") }
        }
    }

    // ==================== FIXED COSTS & P&L ====================

    fun openFixedCostDialog(monthKey: String = "") {
        val targetMonth = if (monthKey.isNotBlank()) monthKey else {
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        }
        _uiState.update {
            it.copy(
                showFixedCostDialog = true,
                editingFixedCostMonthKey = targetMonth
            )
        }
    }

    fun dismissFixedCostDialog() {
        _uiState.update { it.copy(showFixedCostDialog = false) }
    }

    fun saveFixedCost(cost: FixedCostEntity) {
        viewModelScope.launch {
            repository.saveFixedCost(cost)
            _uiState.update {
                it.copy(
                    showFixedCostDialog = false,
                    snackbarMessage = "Fixed costs for ${cost.monthKey} saved (₹${"%,.0f".format(cost.totalFixedCost)}/mo)"
                )
            }
        }
    }

    fun deleteFixedCost(monthKey: String) {
        viewModelScope.launch {
            repository.deleteFixedCost(monthKey)
            _uiState.update { it.copy(snackbarMessage = "Fixed costs for $monthKey deleted") }
        }
    }

    // ==================== IN-APP OTA APP UPDATES (GitHub Releases) ====================

    fun checkForAppUpdates(isManualCheck: Boolean = false) {
        val owner = _uiState.value.githubRepoOwner
        val repo = _uiState.value.githubRepoName
        val token = _uiState.value.githubToken.takeIf { it.isNotBlank() }
        val currentVersion = BuildConfig.VERSION_NAME
        val currentVersionCode = BuildConfig.VERSION_CODE

        _uiState.update { it.copy(isCheckingForUpdate = true) }

        viewModelScope.launch {
            val result = GitHubUpdateService.getInstance().checkForUpdate(
                owner = owner,
                repo = repo,
                currentVersionName = currentVersion,
                currentVersionCode = currentVersionCode,
                token = token
            )

            _uiState.update { it.copy(isCheckingForUpdate = false) }

            result.onSuccess { updateInfo ->
                if (updateInfo.isUpdateAvailable) {
                    _uiState.update {
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.Available(updateInfo),
                            showAppUpdateDialog = true,
                            isForceUpdate = updateInfo.isForceUpdate,
                            forceUpdateMessage = updateInfo.forceUpdateMessage
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.Idle,
                            isForceUpdate = false,
                            forceUpdateMessage = ""
                        ) 
                    }
                    if (isManualCheck) {
                        _uiState.update {
                            it.copy(snackbarMessage = "FILTR Billing is up to date (v$currentVersion)")
                        }
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Check for update failed: ${error.message}")
                if (isManualCheck) {
                    _uiState.update {
                        it.copy(
                            appUpdateStatus = AppUpdateStatus.Error(error.message ?: "Failed to check for updates"),
                            showAppUpdateDialog = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(appUpdateStatus = AppUpdateStatus.Idle) }
                }
            }
        }
    }

    fun startDownloadingUpdate(updateInfo: AppUpdateInfo) {
        val token = updateInfo.token ?: _uiState.value.githubToken.takeIf { it.isNotBlank() }
        _uiState.update {
            it.copy(
                appUpdateStatus = AppUpdateStatus.Downloading(
                    progressPercent = 0,
                    bytesDownloaded = 0L,
                    totalBytes = updateInfo.apkSizeBytes
                )
            )
        }

        viewModelScope.launch {
            val result = GitHubUpdateService.getInstance().downloadApk(
                context = getApplication(),
                downloadUrl = updateInfo.apkDownloadUrl,
                token = token
            ) { percent, bytesRead, total ->
                _uiState.update {
                    it.copy(
                        appUpdateStatus = AppUpdateStatus.Downloading(
                            progressPercent = percent,
                            bytesDownloaded = bytesRead,
                            totalBytes = total
                        )
                    )
                }
            }

            result.onSuccess { apkFile ->
                _uiState.update {
                    it.copy(appUpdateStatus = AppUpdateStatus.ReadyToInstall(apkFile, updateInfo))
                }
                // Automatically prompt package installer immediately upon download completion
                installDownloadedApk(apkFile)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        appUpdateStatus = AppUpdateStatus.Error("Download failed: ${error.message ?: "Unknown error"}")
                    )
                }
            }
        }
    }

    fun installDownloadedApk(apkFile: File) {
        val result = GitHubUpdateService.getInstance().installApk(getApplication(), apkFile)
        result.onFailure { error ->
            _uiState.update { it.copy(snackbarMessage = error.message) }
        }
    }

    fun dismissAppUpdateDialog() {
        if (_uiState.value.isForceUpdate) {
            // Cannot dismiss dialog during mandatory/force update
            return
        }
        _uiState.update { it.copy(showAppUpdateDialog = false) }
    }

    fun saveGitHubUpdateSettings(owner: String, repo: String, token: String) {
        val cleanOwner = owner.trim().ifBlank { GitHubUpdateService.DEFAULT_REPO_OWNER }
        val cleanRepo = repo.trim().ifBlank { GitHubUpdateService.DEFAULT_REPO_NAME }
        val cleanToken = token.trim()

        prefs.edit()
            .putString("github_repo_owner", cleanOwner)
            .putString("github_repo_name", cleanRepo)
            .putString("github_token", cleanToken)
            .apply()

        _uiState.update {
            it.copy(
                githubRepoOwner = cleanOwner,
                githubRepoName = cleanRepo,
                githubToken = cleanToken,
                snackbarMessage = "GitHub Update settings saved!"
            )
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

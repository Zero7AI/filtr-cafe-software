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
import com.example.data.entity.KdsItemClaimEntity
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
import com.example.data.entity.AttendanceLogEntity
import com.example.data.entity.ChecklistCompletionEntity
import com.example.data.entity.ChecklistTaskEntity
import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import com.example.data.entity.StaffRosterEntity
import com.example.domain.attendance.AttendanceCalculator
import com.example.domain.attendance.DayAttendanceStatus
import com.example.domain.inventory.IngredientPriceMatrix
import com.example.domain.inventory.InventoryAlertRow
import com.example.domain.attendance.GeofenceCheckResult
import com.example.domain.attendance.MonthlyAttendanceSummary
import com.example.domain.attendance.StoreGeofenceConfig
import com.example.domain.attendance.TeamRollCallRow
import com.example.domain.inventory.IngredientPriceSummary
import com.example.domain.inventory.ParsedRawMaterialReceipt
import com.example.domain.inventory.PendingReceiptIntake
import com.example.domain.inventory.PendingReceiptQueueStore
import com.example.domain.inventory.RawMaterialPriceTracker
import com.example.domain.kds.KdsBaristaColorPalette
import com.example.domain.kds.KdsQueueBuilder
import com.example.domain.kds.KdsQueueUnitItem
import com.example.domain.kitchen.KitchenCountingMode
import com.example.domain.kitchen.KitchenInventoryCalculator
import com.example.domain.kitchen.KitchenSection
import com.example.domain.kitchen.KitchenShiftType
import com.example.domain.kitchen.KitchenStockStatus
import com.example.util.KitchenLocationHelper
import com.example.util.LocationSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val STAFF_ALLOWED_TABS: Set<FiltrTab> = setOf(
    FiltrTab.BILLING,
    FiltrTab.ORDERS,
    FiltrTab.KDS,
    FiltrTab.CHECKLIST,
    FiltrTab.KITCHEN_INVENTORY,
    FiltrTab.ATTENDANCE
)

data class InventoryOrderItemInput(
    val itemName: String,
    val quantity: Double,
    val unit: String = "kg",
    val unitPrice: Double
)

enum class FiltrTab {
    BILLING,
    ORDERS,
    KDS,
    MENU,
    DASHBOARD,
    INVENTORY_ORDER,
    INVENTORIES,
    INVENTORY_USAGE,
    RECIPE_MASTER,
    CUSTOMERS,
    RECEIPT_DESIGN,
    PROFIT_LOSS,
    VOICE_ORDER,
    CHECKLIST,
    KITCHEN_INVENTORY,
    ATTENDANCE
}

data class ChecklistItemUiModel(
    val task: ChecklistTaskEntity,
    val completion: ChecklistCompletionEntity?
) {
    val isCompleted: Boolean get() = completion?.isCompleted == true
    val isOutOfStock: Boolean get() = completion?.isOutOfStock == true
    val completedTimestamp: Long get() = completion?.completedTimestamp ?: 0L
    val completedBy: String get() = completion?.completedBy ?: "Staff"
}

data class ChecklistSectionUiModel(
    val sectionId: String,
    val sectionName: String,
    val items: List<ChecklistItemUiModel>,
    val isExpanded: Boolean = true
) {
    val totalCount: Int get() = items.size
    val completedCount: Int get() = items.count { it.isCompleted }
    val pendingCount: Int get() = totalCount - completedCount
    val isAllCompleted: Boolean get() = totalCount > 0 && completedCount == totalCount
    val completionPercentage: Int get() = if (totalCount == 0) 0 else (completedCount * 100) / totalCount
}

data class ChecklistOverallStats(
    val dateKey: String,
    val formattedDate: String,
    val totalCount: Int,
    val completedCount: Int,
    val pendingCount: Int,
    val percentage: Int
)

data class BillingUiState(
    val currentTab: FiltrTab = FiltrTab.BILLING,
    val selectedCategory: String = "All",
    val searchQuery: String = "",
    val customerName: String = "",
    val contactNumber: String = "",
    val orderNotes: String = "",
    val cartItems: Map<MenuItemEntity, Int> = emptyMap(),
    val googleSheetsWebhookUrl: String = "",
    val googleSheetsUrl: String = "",
    val priceAlertThresholdPct: Double = 5.0,
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
    val isDeletingOrder: Boolean = false,
    val manualInventoryDraftCounts: Map<Int, Double> = emptyMap(),
    val manualInventoryDraftNotes: String = "",
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
    val showAddCategoryDialog: Boolean = false,
    // Daily Check List
    val showAddChecklistTaskDialog: Boolean = false,
    val collapsedChecklistSectionIds: Set<String> = emptySet(),
    // Device Access Control & Registry
    val deviceAccessStatus: String = "ACTIVE", // "ACTIVE", "UNVERIFIED", "PENDING", "REVOKED"
    val operatorName: String = "",
    val currentRole: String = "STAFF", // "ADMIN" or "STAFF"
    val linkedMemberId: String = "",
    val linkedMemberName: String = "",
    val accessGateError: String = "",
    val isVerifyingAccess: Boolean = false,
    val showActiveDevicesDialog: Boolean = false,
    val registeredDevices: List<SupabaseService.DeviceRegistration> = emptyList(),
    val isLoadingDevices: Boolean = false,
    val accessPolicyConfig: SupabaseService.AccessPolicyConfig = SupabaseService.AccessPolicyConfig(),
    // Shared Raw Material Receipt Intake (WhatsApp / PDF / Photo)
    val isParsingSharedReceipt: Boolean = false,
    val sharedReceiptSourceLabel: String = "",
    val parsedSharedReceipt: ParsedRawMaterialReceipt? = null,
    val pendingReceiptQueue: List<PendingReceiptIntake> = emptyList(),
    val activeRetryIntakeId: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FiltrDatabase.getDatabase(application, viewModelScope)
    private val repository = FiltrRepository(
        database.menuDao(), database.orderDao(), database.upiAccountDao(),
        database.inventoryDao(), database.customerDao(), database.fixedCostDao(),
        database.checklistDao(), database.kitchenInventoryDao(), database.attendanceDao(),
        database.kdsClaimDao()
    )

    private val prefs = application.getSharedPreferences("filtr_prefs", Context.MODE_PRIVATE)
    val currentDeviceId: String = try {
        android.provider.Settings.Secure.getString(application.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() } ?: "dev_${android.os.Build.MODEL.hashCode()}"
    } catch (_: Exception) {
        "dev_${android.os.Build.MODEL.hashCode()}"
    }
    val currentDeviceModel: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()

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

    private val initialRole = prefs.getString("authenticated_role", "STAFF") ?: "STAFF"
    private val _uiState = MutableStateFlow(
        BillingUiState(
            googleSheetsWebhookUrl = prefs.getString("sheets_webhook_url", "") ?: "",
            googleSheetsUrl = prefs.getString("google_sheets_url", "") ?: "",
            priceAlertThresholdPct = (prefs.getString("price_alert_threshold_pct", "5.0") ?: "5.0").toDoubleOrNull() ?: 5.0,
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
            customCategories = loadCustomCategories(),
            deviceAccessStatus = prefs.getString("device_access_status", "UNVERIFIED") ?: "UNVERIFIED",
            operatorName = prefs.getString("device_operator_name", "") ?: "",
            currentRole = initialRole,
            linkedMemberId = prefs.getString("linked_member_id", "") ?: "",
            linkedMemberName = prefs.getString("linked_member_name", "") ?: "",
            accessPolicyConfig = sanitizedPolicyForRole(SupabaseService.AccessPolicyConfig(), initialRole)
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

    val ingredientPricingSummaries: StateFlow<List<IngredientPriceSummary>> = combine(
        inventoryItems,
        inventoryOrders
    ) { items, orders ->
        RawMaterialPriceTracker.buildIngredientPricingSummaries(items, orders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryUsages: StateFlow<List<InventoryUsageEntity>> = repository.allInventoryUsages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inventoryAlerts: StateFlow<List<InventoryAlertRow>> = combine(
        inventoryItems,
        inventoryOrders,
        inventoryUsages,
        _uiState.map { it.priceAlertThresholdPct }.distinctUntilChanged()
    ) { items, orders, usages, threshold ->
        RawMaterialPriceTracker.evaluateInventoryAlerts(
            inventoryItems = items,
            inventoryOrders = orders,
            usages = usages,
            priceAlertThresholdPct = threshold
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val ingredientPriceMatrix: StateFlow<IngredientPriceMatrix> = combine(
        inventoryItems,
        inventoryOrders,
        inventoryUsages,
        _uiState.map { it.priceAlertThresholdPct }.distinctUntilChanged()
    ) { items, orders, usages, threshold ->
        RawMaterialPriceTracker.buildIngredientPriceMatrix(
            inventoryItems = items,
            inventoryOrders = orders,
            usages = usages,
            priceAlertThresholdPct = threshold
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IngredientPriceMatrix(emptyList(), emptyList()))

    val customers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fixedCosts: StateFlow<List<FixedCostEntity>> = repository.allFixedCosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun currentFormattedDateKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private fun currentFormattedDisplayDate(): String =
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())

    private val _activeDateKey = MutableStateFlow(currentFormattedDateKey())

    val todayDateKey: String
        get() = currentFormattedDateKey()

    private fun refreshDateKeyIfNeeded() {
        val current = currentFormattedDateKey()
        if (_activeDateKey.value != current) {
            _activeDateKey.value = current
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val checklistSections: StateFlow<List<ChecklistSectionUiModel>> = _activeDateKey
        .flatMapLatest { dateKey ->
            combine(
                repository.allChecklistTasks,
                repository.getChecklistCompletionsForDate(dateKey),
                _uiState.map { it.collapsedChecklistSectionIds }.distinctUntilChanged()
            ) { tasks: List<ChecklistTaskEntity>, completions: List<ChecklistCompletionEntity>, collapsedSectionIds: Set<String> ->
                val completionMap = completions.associateBy { it.taskId }
                val sectionOrder = listOf("01_OPENING", "02_CLEANING", "03_ROUTINE", "04_CLOSING", "05_GENERAL")
                val sectionNames = mapOf(
                    "01_OPENING" to "01. Opening & Daily Preparation",
                    "02_CLEANING" to "02. Cleaning & Store Hygiene",
                    "03_ROUTINE" to "03. Routine Store Checks",
                    "04_CLOSING" to "04. Closing Checklist",
                    "05_GENERAL" to "05. General Tasks"
                )

                val grouped = tasks.groupBy { it.sectionId }
                val allSections = (sectionOrder + grouped.keys).distinct()

                allSections.mapNotNull { secId ->
                    val sectionTasks = grouped[secId] ?: emptyList()
                    if (sectionTasks.isEmpty() && secId !in listOf("01_OPENING", "02_CLEANING", "03_ROUTINE", "04_CLOSING")) {
                        null
                    } else {
                        val items = sectionTasks.sortedBy { it.orderIndex }.map { task ->
                            ChecklistItemUiModel(
                                task = task,
                                completion = completionMap[task.id]
                            )
                        }
                        val secName = sectionTasks.firstOrNull()?.sectionName ?: (sectionNames[secId] ?: secId)
                        ChecklistSectionUiModel(
                            sectionId = secId,
                            sectionName = secName,
                            items = items,
                            isExpanded = secId !in collapsedSectionIds
                        )
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checklistStats: StateFlow<ChecklistOverallStats> = combine(
        checklistSections,
        _activeDateKey
    ) { sections, activeDateKey ->
        val total = sections.sumOf { it.totalCount }
        val completed = sections.sumOf { it.completedCount }
        val pending = total - completed
        val pct = if (total == 0) 0 else (completed * 100) / total
        val formatted = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(activeDateKey)
            if (parsed != null) SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(parsed) else currentFormattedDisplayDate()
        } catch (_: Exception) {
            currentFormattedDisplayDate()
        }
        ChecklistOverallStats(
            dateKey = activeDateKey,
            formattedDate = formatted,
            totalCount = total,
            completedCount = completed,
            pendingCount = pending,
            percentage = pct
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChecklistOverallStats(
        dateKey = currentFormattedDateKey(),
        formattedDate = currentFormattedDisplayDate(),
        totalCount = 0,
        completedCount = 0,
        pendingCount = 0,
        percentage = 0
    ))

    // ==================== SMART DAILY KITCHEN INVENTORY STATE ====================

    private val _kitchenRoute = MutableStateFlow<KitchenInventoryViewRoute>(KitchenInventoryViewRoute.Dashboard)
    val kitchenRoute: StateFlow<KitchenInventoryViewRoute> = _kitchenRoute.asStateFlow()

    private val _previousClosingShift = MutableStateFlow<KitchenInventoryShiftEntity?>(null)
    val previousClosingShift: StateFlow<KitchenInventoryShiftEntity?> = _previousClosingShift.asStateFlow()

    private val _previousClosingEntries = MutableStateFlow<List<KitchenInventoryEntryEntity>>(emptyList())
    val previousClosingEntries: StateFlow<List<KitchenInventoryEntryEntity>> = _previousClosingEntries.asStateFlow()

    val kitchenAllItems: StateFlow<List<KitchenInventoryItemEntity>> =
        repository.allKitchenItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kitchenActiveItems: StateFlow<List<KitchenInventoryItemEntity>> =
        repository.activeKitchenItems.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kitchenAllShifts: StateFlow<List<KitchenInventoryShiftEntity>> =
        repository.allKitchenShifts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kitchenAllEntries: StateFlow<List<KitchenInventoryEntryEntity>> =
        repository.allKitchenEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val kitchenShiftsToday: StateFlow<List<KitchenInventoryShiftEntity>> = _activeDateKey
        .flatMapLatest { dateKey -> repository.getKitchenShiftsForDate(dateKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val kitchenEntriesToday: StateFlow<List<KitchenInventoryEntryEntity>> = _activeDateKey
        .flatMapLatest { dateKey -> repository.getKitchenEntriesForDate(dateKey) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kitchenDashboardSummary: StateFlow<KitchenInventoryDashboardSummary> = combine(
        _activeDateKey,
        kitchenActiveItems,
        kitchenShiftsToday,
        kitchenEntriesToday,
        _uiState.map { it.operatorName.ifBlank { it.currentRole } }.distinctUntilChanged()
    ) { dateKey, activeItems, shiftsToday, entriesToday, staffName ->
        val openingShift = shiftsToday.firstOrNull { it.shiftType == KitchenShiftType.OPENING.id }
        val closingShift = shiftsToday.firstOrNull { it.shiftType == KitchenShiftType.CLOSING.id }
        val openingEntries = entriesToday.filter { it.shiftType == KitchenShiftType.OPENING.id && it.isChecked }
        val closingEntries = entriesToday.filter { it.shiftType == KitchenShiftType.CLOSING.id && it.isChecked }

        val activeIds = activeItems.map { it.id }.toSet()
        val openingByItem = openingEntries.filter { it.itemId in activeIds }.associateBy { it.itemId }
        val closingByItem = closingEntries.filter { it.itemId in activeIds }.associateBy { it.itemId }

        val latestByItem = activeItems.mapNotNull { item ->
            closingByItem[item.id] ?: openingByItem[item.id]
        }.associateBy { it.itemId }

        var lowCount = 0
        var outCount = 0
        val reorderList = activeItems.mapNotNull { item ->
            val latest = latestByItem[item.id] ?: return@mapNotNull null
            val rec = KitchenInventoryCalculator.computeReorderRecommendation(item, latest.totalUsableQty)
            if (rec != null) {
                if (rec.status == KitchenStockStatus.RED) outCount++ else if (rec.status == KitchenStockStatus.ORANGE) lowCount++
            }
            rec
        }.sortedWith(compareBy<com.example.domain.kitchen.KitchenReorderRecommendation> {
            if (it.status == KitchenStockStatus.RED) 0 else 1
        }.thenBy { it.item.sectionId }.thenBy { it.item.orderIndex })

        val openingPending = KitchenSection.entries.filter { sec ->
            val secItems = activeItems.filter { it.sectionId == sec.id }
            secItems.isNotEmpty() && secItems.any { it.id !in openingByItem }
        }
        val closingPending = KitchenSection.entries.filter { sec ->
            val secItems = activeItems.filter { it.sectionId == sec.id }
            secItems.isNotEmpty() && secItems.any { it.id !in closingByItem }
        }

        val varianceRows = KitchenInventoryCalculator.computeVarianceRows(
            items = activeItems,
            openingEntries = openingEntries,
            closingEntries = closingEntries
        )

        val formatted = try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateKey)
            if (parsed != null) SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(parsed) else currentFormattedDisplayDate()
        } catch (_: Exception) {
            currentFormattedDisplayDate()
        }

        KitchenInventoryDashboardSummary(
            dateKey = dateKey,
            formattedDate = formatted,
            staffName = staffName,
            totalActiveItems = activeItems.size,
            openingShift = openingShift,
            closingShift = closingShift,
            openingCheckedCount = openingByItem.size,
            closingCheckedCount = closingByItem.size,
            lowStockCount = lowCount,
            outOfStockCount = outCount,
            openingPendingSections = openingPending,
            closingPendingSections = closingPending,
            reorderList = reorderList,
            varianceRows = varianceRows
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        KitchenInventoryDashboardSummary(
            dateKey = currentFormattedDateKey(),
            formattedDate = currentFormattedDisplayDate(),
            staffName = initialRole,
            totalActiveItems = 0,
            openingShift = null,
            closingShift = null,
            openingCheckedCount = 0,
            closingCheckedCount = 0,
            lowStockCount = 0,
            outOfStockCount = 0,
            openingPendingSections = emptyList(),
            closingPendingSections = emptyList(),
            reorderList = emptyList(),
            varianceRows = emptyList()
        )
    )

    // ==================== STAFF ATTENDANCE STATE ====================

    private fun loadStoreGeofenceConfigFromPrefs(): StoreGeofenceConfig {
        val lat = prefs.getString("attendance_store_lat", null)?.toDoubleOrNull() ?: 0.0
        val lng = prefs.getString("attendance_store_lng", null)?.toDoubleOrNull() ?: 0.0
        val radius = prefs.getString("attendance_store_radius_m", null)?.toDoubleOrNull() ?: 100.0
        val label = prefs.getString("attendance_store_label", "FILTR Coffee") ?: "FILTR Coffee"
        val updatedAt = prefs.getLong("attendance_store_updated_at", 0L)
        return StoreGeofenceConfig(
            latitude = lat,
            longitude = lng,
            radiusMeters = if (radius > 0.0) radius else 100.0,
            storeLabel = label.ifBlank { "FILTR Coffee" },
            updatedAt = updatedAt
        )
    }

    private fun persistStoreGeofenceConfig(config: StoreGeofenceConfig) {
        prefs.edit()
            .putString("attendance_store_lat", config.latitude.toString())
            .putString("attendance_store_lng", config.longitude.toString())
            .putString("attendance_store_radius_m", config.radiusMeters.toString())
            .putString("attendance_store_label", config.storeLabel.trim().ifBlank { "FILTR Coffee" })
            .putLong("attendance_store_updated_at", config.updatedAt)
            .apply()
    }

    private val _storeGeofenceConfig = MutableStateFlow(loadStoreGeofenceConfigFromPrefs())
    val storeGeofenceConfig: StateFlow<StoreGeofenceConfig> = _storeGeofenceConfig.asStateFlow()

    private val _hasLocationPermission = MutableStateFlow(
        KitchenLocationHelper.hasLocationPermission(application)
    )
    val hasLocationPermission: StateFlow<Boolean> = _hasLocationPermission.asStateFlow()

    private val _isFetchingLocation = MutableStateFlow(false)
    val isFetchingLocation: StateFlow<Boolean> = _isFetchingLocation.asStateFlow()

    private val _currentLocationSample = MutableStateFlow<LocationSample?>(null)
    val currentLocationSample: StateFlow<LocationSample?> = _currentLocationSample.asStateFlow()

    private val _attendanceLocationError = MutableStateFlow<String?>(null)
    val attendanceLocationError: StateFlow<String?> = _attendanceLocationError.asStateFlow()

    val geofenceCheckResult: StateFlow<GeofenceCheckResult?> = combine(
        _currentLocationSample,
        _storeGeofenceConfig
    ) { sample, config ->
        if (sample != null && config.isConfigured) {
            AttendanceCalculator.evaluateGeofence(sample.latitude, sample.longitude, config)
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedAttendanceMonthKey = MutableStateFlow(currentFormattedDateKey().substring(0, 7))
    val selectedAttendanceMonthKey: StateFlow<String> = _selectedAttendanceMonthKey.asStateFlow()

    val staffRoster: StateFlow<List<StaffRosterEntity>> = repository.allStaffRoster
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onDutyStaffKeysToday: StateFlow<Set<String>> = combine(
        _activeDateKey,
        repository.allAttendanceLogs
    ) { dateKey, allLogs ->
        allLogs
            .asSequence()
            .filter {
                it.dateKey == dateKey &&
                    it.checkInTimestamp > 0L &&
                    it.checkOutTimestamp == 0L
            }
            .map { AttendanceCalculator.normalizeStaffKey(it.staffName) }
            .filter { it.isNotBlank() }
            .toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val availableAttendanceStaffNames: StateFlow<List<String>> = combine(
        _uiState.map { it.operatorName to it.registeredDevices }.distinctUntilChanged(),
        repository.allAttendanceLogs,
        staffRoster,
        onDutyStaffKeysToday
    ) { (operatorName, registeredDevices), attendanceLogs, roster, onDutyKeys ->
        val staffOnlyRoster = roster.filter { !it.orgRole.equals("ADMIN", ignoreCase = true) }
        val fromDevices = registeredDevices
            .filter { it.appRole == "STAFF" }
            .map { it.linkedMemberName.ifBlank { it.operatorName }.trim() }
        val fromLogs = attendanceLogs.map { it.staffName.trim() }

        val resolved = AttendanceCalculator.resolveSelectableStaffNames(
            roster = staffOnlyRoster,
            onDutyNormalizedKeys = onDutyKeys,
            fallbackDeviceNames = fromDevices,
            fallbackLogNames = fromLogs,
            loggedInOperatorName = operatorName
        )
        // Also merge verified STAFF device operators not yet added to Staff Roster
        val extraStaff = fromDevices
            .filter { AttendanceCalculator.isRealStaffName(it) }
            .filter { devName -> resolved.none { it.equals(devName, ignoreCase = true) } }
        (resolved + extraStaff).distinctBy { AttendanceCalculator.normalizeStaffKey(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedCheckInStaffName = MutableStateFlow("")
    val selectedCheckInStaffName: StateFlow<String> = combine(
        _selectedCheckInStaffName,
        _uiState.map { it.operatorName.trim().ifBlank { "Staff" } }.distinctUntilChanged(),
        availableAttendanceStaffNames
    ) { selected, operatorName, available ->
        val cleanSelected = selected.trim()
        if (cleanSelected.isNotBlank()) {
            available.firstOrNull { it.equals(cleanSelected, ignoreCase = true) } ?: cleanSelected
        } else {
            available.firstOrNull { it.equals(operatorName, ignoreCase = true) }
                ?: available.firstOrNull()
                ?: operatorName
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        _uiState.value.operatorName.trim().ifBlank { "Staff" }
    )

    private val _selectedKdsBaristaName = MutableStateFlow("")
    val selectedKdsBaristaName: StateFlow<String> = combine(
        _selectedKdsBaristaName,
        _uiState.map { it.operatorName.trim().ifBlank { "Barista" } }.distinctUntilChanged(),
        availableAttendanceStaffNames
    ) { selected, operatorName, available ->
        val cleanSelected = selected.trim()
        if (cleanSelected.isNotBlank()) {
            available.firstOrNull { it.equals(cleanSelected, ignoreCase = true) } ?: cleanSelected
        } else {
            available.firstOrNull { it.equals(operatorName, ignoreCase = true) }
                ?: available.firstOrNull()
                ?: operatorName
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        _uiState.value.operatorName.trim().ifBlank { "Barista" }
    )

    private val _kdsPaidOnlyFilter = MutableStateFlow(false)
    val kdsPaidOnlyFilter: StateFlow<Boolean> = _kdsPaidOnlyFilter.asStateFlow()

    private val _kdsTodayOnlyFilter = MutableStateFlow(true)
    val kdsTodayOnlyFilter: StateFlow<Boolean> = _kdsTodayOnlyFilter.asStateFlow()

    val kdsClaimsByKey: StateFlow<Map<String, KdsItemClaimEntity>> = repository.allKdsClaims
        .map { claims -> claims.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val kdsFifoQueue: StateFlow<List<KdsQueueUnitItem>> = combine(
        repository.allOrders,
        kdsClaimsByKey,
        staffRoster,
        _kdsPaidOnlyFilter,
        _kdsTodayOnlyFilter,
        _activeDateKey
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val orders = values[0] as List<OrderWithItems>
        @Suppress("UNCHECKED_CAST")
        val claimsMap = values[1] as Map<String, KdsItemClaimEntity>
        @Suppress("UNCHECKED_CAST")
        val roster = values[2] as List<StaffRosterEntity>
        val paidOnly = values[3] as Boolean
        val todayOnly = values[4] as Boolean
        val activeDateKey = values[5] as String

        val rosterMap = roster.associateBy { AttendanceCalculator.normalizeStaffKey(it.name) }
        val minTs = if (todayOnly) KdsQueueBuilder.parseDateKeyStartMillis(activeDateKey) else null
        KdsQueueBuilder.buildFifoPendingQueue(
            orders = orders,
            claimsByKey = claimsMap,
            rosterByNormalizedName = rosterMap,
            paidOnlyFilter = paidOnly,
            minTimestampMs = minTs
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedManagerAttendanceStaff = MutableStateFlow("")
    val selectedManagerAttendanceStaff: StateFlow<String> = combine(
        _selectedManagerAttendanceStaff,
        availableAttendanceStaffNames
    ) { selected, available ->
        val cleanSelected = selected.trim()
        if (cleanSelected.isNotBlank()) {
            available.firstOrNull { it.equals(cleanSelected, ignoreCase = true) } ?: cleanSelected
        } else {
            available.firstOrNull().orEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayPersonalAttendanceLog: StateFlow<AttendanceLogEntity?> = combine(
        _activeDateKey,
        selectedCheckInStaffName
    ) { activeDateKey, staffName ->
        activeDateKey to staffName
    }.flatMapLatest { (activeDateKey, staffName) ->
        val todayMonthKey = if (activeDateKey.length >= 7) activeDateKey.substring(0, 7) else currentFormattedDateKey().substring(0, 7)
        val expectedId = AttendanceCalculator.buildLogId(activeDateKey, staffName)
        repository.getAttendanceLogsForMonth(todayMonthKey).map { logs ->
            logs.firstOrNull { it.id == expectedId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun buildAttendanceSummaryForStaff(
        monthKey: String,
        staffName: String,
        allLogs: List<AttendanceLogEntity>,
        activeDateKey: String,
        rosterMember: StaffRosterEntity?
    ): MonthlyAttendanceSummary {
        val effectiveStaffName = staffName.trim().ifBlank { "Staff" }
        val targetStaffKey = AttendanceCalculator.normalizeStaffKey(effectiveStaffName)
        val logsForStaff = allLogs.filter {
            AttendanceCalculator.normalizeStaffKey(it.staffName) == targetStaffKey
        }
        val trackingStartDateKey = rosterMember?.joinedDateKey?.takeIf { it.isNotBlank() }
            ?: logsForStaff
                .mapNotNull { it.dateKey.takeIf { key -> key.isNotBlank() } }
                .minOrNull()
            ?: "$monthKey-01"
        return AttendanceCalculator.buildMonthlySummary(
            monthKey = monthKey,
            staffName = effectiveStaffName,
            logs = allLogs,
            trackingStartDateKey = trackingStartDateKey,
            todayDateKey = activeDateKey,
            weeklyOffDayOfWeek = rosterMember?.weeklyOffDayOfWeek ?: 0
        )
    }

    val personalAttendanceSummary: StateFlow<MonthlyAttendanceSummary> = combine(
        _selectedAttendanceMonthKey,
        selectedCheckInStaffName,
        repository.allAttendanceLogs,
        _activeDateKey,
        staffRoster
    ) { monthKey, staffName, allLogs, activeDateKey, roster ->
        val targetStaffKey = AttendanceCalculator.normalizeStaffKey(staffName.trim().ifBlank { "Staff" })
        val rosterMember = roster.firstOrNull {
            AttendanceCalculator.normalizeStaffKey(it.name) == targetStaffKey
        }
        buildAttendanceSummaryForStaff(
            monthKey = monthKey,
            staffName = staffName,
            allLogs = allLogs,
            activeDateKey = activeDateKey,
            rosterMember = rosterMember
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AttendanceCalculator.buildMonthlySummary(
            monthKey = currentFormattedDateKey().substring(0, 7),
            staffName = "Staff",
            logs = emptyList(),
            trackingStartDateKey = "${currentFormattedDateKey().substring(0, 7)}-01",
            todayDateKey = currentFormattedDateKey()
        )
    )

    val managerAttendanceSummary: StateFlow<MonthlyAttendanceSummary> = combine(
        _selectedAttendanceMonthKey,
        selectedManagerAttendanceStaff,
        repository.allAttendanceLogs,
        _activeDateKey,
        staffRoster
    ) { monthKey, staffName, allLogs, activeDateKey, roster ->
        val effectiveStaffName = staffName.trim().ifBlank { "Staff" }
        val targetStaffKey = AttendanceCalculator.normalizeStaffKey(effectiveStaffName)
        val rosterMember = roster.firstOrNull {
            AttendanceCalculator.normalizeStaffKey(it.name) == targetStaffKey
        }
        buildAttendanceSummaryForStaff(
            monthKey = monthKey,
            staffName = effectiveStaffName,
            allLogs = allLogs,
            activeDateKey = activeDateKey,
            rosterMember = rosterMember
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AttendanceCalculator.buildMonthlySummary(
            monthKey = currentFormattedDateKey().substring(0, 7),
            staffName = "Staff",
            logs = emptyList(),
            trackingStartDateKey = "${currentFormattedDateKey().substring(0, 7)}-01",
            todayDateKey = currentFormattedDateKey()
        )
    )

    val teamRollCall: StateFlow<List<TeamRollCallRow>> = combine(
        staffRoster,
        _selectedAttendanceMonthKey,
        repository.allAttendanceLogs,
        _activeDateKey
    ) { roster, monthKey, allLogs, activeDateKey ->
        val todayMonthKey = if (activeDateKey.length >= 7) activeDateKey.substring(0, 7) else monthKey
        roster
            .filter { it.isActive && !it.orgRole.equals("ADMIN", ignoreCase = true) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { member ->
                val targetStaffKey = AttendanceCalculator.normalizeStaffKey(member.name)
                val summary = buildAttendanceSummaryForStaff(
                    monthKey = monthKey,
                    staffName = member.name,
                    allLogs = allLogs,
                    activeDateKey = activeDateKey,
                    rosterMember = member
                )
                val todayLog = allLogs
                    .filter {
                        it.dateKey == activeDateKey &&
                            AttendanceCalculator.normalizeStaffKey(it.staffName) == targetStaffKey
                    }
                    .maxByOrNull { it.updatedAt }
                val todayStatus = if (monthKey == todayMonthKey) {
                    summary.days.firstOrNull { it.dateKey == activeDateKey }?.status ?: DayAttendanceStatus.ABSENT
                } else {
                    buildAttendanceSummaryForStaff(
                        monthKey = todayMonthKey,
                        staffName = member.name,
                        allLogs = allLogs,
                        activeDateKey = activeDateKey,
                        rosterMember = member
                    ).days.firstOrNull { it.dateKey == activeDateKey }?.status ?: DayAttendanceStatus.ABSENT
                }
                TeamRollCallRow(
                    member = member,
                    todayLog = todayLog,
                    todayStatus = todayStatus,
                    monthlySummary = summary
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var customerSearchJob: Job? = null
    private val connectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    init {
        refreshPendingReceiptQueue()
        // Monitor network connectivity in real-time
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                _uiState.update { it.copy(isNetworkAvailable = true) }
                // Trigger immediate background sync of any pending orders to Supabase & Sheets when connection restored
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.syncPendingOrdersToSupabase()
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Supabase sync on network reconnect failed: ${e.message}")
                    }
                    if (_uiState.value.pendingReceiptQueue.isNotEmpty() && !_uiState.value.isParsingSharedReceipt) {
                        val firstPending = _uiState.value.pendingReceiptQueue.firstOrNull()
                        if (firstPending != null) {
                            val att = PendingReceiptQueueStore.loadAttachmentBytes(getApplication(), firstPending)
                            if (att.isNotEmpty() || firstPending.sharedText.isNotBlank()) {
                                try {
                                    val res = repository.parseSharedRawMaterialReceipt(
                                        att,
                                        firstPending.sharedText,
                                        firstPending.sourceLabel
                                    )
                                    if (res.isSuccess) {
                                        PendingReceiptQueueStore.remove(getApplication(), firstPending.id)
                                        val refreshed = PendingReceiptQueueStore.loadAll(getApplication())
                                        _uiState.update {
                                            it.copy(
                                                pendingReceiptQueue = refreshed,
                                                snackbarMessage = "Queued receipt '${firstPending.sourceLabel}' ready — check Inventory Orders!"
                                            )
                                        }
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }
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
                // Check for app updates on network reconnection
                viewModelScope.launch {
                    checkForAppUpdates(isManualCheck = false)
                }
            }
            override fun onLost(network: android.net.Network) {
                _uiState.update { it.copy(isNetworkAvailable = false) }
            }
        }
        networkCallback = callback
        val networkRequest = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, callback)

        // Set initial network state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _uiState.update { it.copy(isNetworkAvailable = isConnected) }

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
                    repository.reconcileChecklistFromCloud(todayDateKey)
                    repository.reconcileKitchenInventoryFromCloud(todayDateKey)
                    refreshPreviousClosingReferenceInternal(todayDateKey)
                    reconcileAttendanceStoreConfigFromCloud()
                    repository.reconcileAttendanceFromCloud(todayDateKey.substring(0, 7))
                    repository.reconcileStaffRosterFromCloud()
                    repository.reconcileKdsClaimsFromCloud()
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deduplicateMenuItems()
                // One-time check only at startup (not reactive to prevent infinite loop)
                val items = menuItems.first()
                val hasLegacyItems = items.any { it.name.contains("South Indian", ignoreCase = true) || it.name.contains("Masala Chai", ignoreCase = true) }
                if (items.isEmpty() || hasLegacyItems) {
                    repository.resetMenuToFiltrDefault()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Menu startup initialization failed: ${e.message}")
            }
        }

        // Automatic periodic Cloud & Google Sheets Sync Loop (Runs every 60 seconds)
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(60 * 1000L) // 1 minute background check across all devices
                refreshDateKeyIfNeeded()
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
                try {
                    repository.reconcileChecklistFromCloud(_activeDateKey.value)
                    repository.reconcileKitchenInventoryFromCloud(_activeDateKey.value)
                    reconcileAttendanceStoreConfigFromCloud()
                    repository.reconcileAttendanceFromCloud(_activeDateKey.value.substring(0, 7))
                    repository.reconcileStaffRosterFromCloud()
                    repository.reconcileKdsClaimsFromCloud()
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Periodic checklist reconcile failed: ${e.message}")
                }
                // Check for updates and sync heartbeat every minute on every installed device
                syncDeviceAccessHeartbeat()
                withContext(Dispatchers.Main) {
                    checkForAppUpdates(isManualCheck = false)
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

        // Check for app updates and verify device access on startup + every 60s
        viewModelScope.launch(Dispatchers.IO) {
            syncDeviceAccessHeartbeat()
            while (true) {
                delay(60_000L)
                if (_uiState.value.isNetworkAvailable) {
                    syncDeviceAccessHeartbeat()
                }
            }
        }
        viewModelScope.launch {
            delay(1000) // Fast initial check immediately upon launch
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

    override fun onCleared() {
        super.onCleared()
        networkCallback?.let { cb ->
            try {
                connectivityManager.unregisterNetworkCallback(cb)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    fun restoreDefaultMenu() {
        if (!requireAdmin("Restoring default menu")) return
        viewModelScope.launch {
            try {
                repository.resetMenuToFiltrDefault()
                _uiState.update { it.copy(snackbarMessage = "FILTR Menu restored to defaults!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to restore menu: ${e.message}") }
            }
        }
    }

    val filteredMenuItems: StateFlow<List<MenuItemEntity>> = combine(
        menuItems,
        _uiState.map { Triple(it.selectedCategory, it.searchQuery, it.customItemOrder) }.distinctUntilChanged()
    ) { items, (selectedCategory, searchQuery, customItemOrder) ->
        val filtered = items.distinctBy { it.name.trim().lowercase() }.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.lowercase().contains(searchQuery.lowercase()) ||
                    item.category.lowercase().contains(searchQuery.lowercase())
            matchesCategory && matchesSearch
        }

        if (customItemOrder.isEmpty()) {
            filtered
        } else {
            val orderMap = customItemOrder.withIndex().associate { it.value.lowercase().trim() to it.index }
            filtered.sortedWith(
                compareBy<MenuItemEntity> {
                    orderMap[it.name.lowercase().trim()] ?: (10000 + it.id)
                }.thenBy { it.name }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCustomItemOrder(newOrder: List<String>) {
        if (!requireAdmin("Reordering menu items")) return
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
        if (!requireAdmin("Reordering menu items")) return
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
        if (!requireAdmin("Reordering menu items")) return
        val index = categoryItems.indexOfFirst { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
        if (index > 0) {
            moveMenuItem(item, index - 1, categoryItems)
        }
    }

    fun moveMenuItemDown(item: MenuItemEntity, categoryItems: List<MenuItemEntity>) {
        if (!requireAdmin("Reordering menu items")) return
        val index = categoryItems.indexOfFirst { it.name.trim().equals(item.name.trim(), ignoreCase = true) }
        if (index != -1 && index < categoryItems.size - 1) {
            moveMenuItem(item, index + 1, categoryItems)
        }
    }

    fun addCustomCategory(categoryName: String) {
        if (!requireAdmin("Adding menu categories")) return
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
        if (!requireAdmin("Adding menu categories")) return
        _uiState.update { it.copy(showAddCategoryDialog = true) }
    }

    fun dismissAddCategoryDialog() {
        _uiState.update { it.copy(showAddCategoryDialog = false) }
    }

    private fun ensureAppAllowed(): Boolean {
        val state = _uiState.value
        if (state.isForceUpdate) {
            _uiState.update {
                it.copy(
                    showAppUpdateDialog = true,
                    snackbarMessage = "Mandatory Update Required. Please update the application to continue."
                )
            }
            return false
        }
        if (state.deviceAccessStatus != "ACTIVE") {
            _uiState.update {
                it.copy(snackbarMessage = "Device verification required before continuing.")
            }
            return false
        }
        return true
    }

    private fun requireAdmin(actionLabel: String = "This action"): Boolean {
        if (!ensureAppAllowed()) return false
        if (_uiState.value.currentRole != "ADMIN") {
            _uiState.update {
                it.copy(snackbarMessage = "$actionLabel requires Admin access")
            }
            return false
        }
        return true
    }

    private fun sanitizedPolicyForRole(
        policy: SupabaseService.AccessPolicyConfig,
        role: String
    ): SupabaseService.AccessPolicyConfig {
        return if (role == "ADMIN") {
            policy
        } else {
            policy.copy(adminPasscode = "", staffPasscode = "")
        }
    }

    private fun BillingUiState.resetRestrictedStateForRole(role: String): BillingUiState {
        if (role == "ADMIN" && deviceAccessStatus == "ACTIVE") return this
        val safeTab = if (currentTab in STAFF_ALLOWED_TABS) currentTab else FiltrTab.BILLING
        return copy(
            currentTab = safeTab,
            showSettingsDialog = false,
            showAddEditItemDialog = false,
            editingMenuItem = null,
            itemToDeleteConfirmation = null,
            itemDeleteWarningMessage = null,
            inventoryItemToDeleteConfirmation = null,
            showInventoryMasterDialog = false,
            showAddInventoryOrderDialog = false,
            editingInventoryItem = null,
            showAddInventoryItemDialog = false,
            inventoryOrderToDelete = null,
            showUsagePerSkuDialog = false,
            usageEditingMenuItemId = null,
            usageEditingMenuItemName = "",
            editingStockItemId = null,
            showAddStockDialog = false,
            addStockItem = null,
            showAccountsUpiDialog = false,
            showReceiptStoryDialog = false,
            showFixedCostDialog = false,
            editingFixedCostMonthKey = "",
            showCustomDatePickerDialog = false,
            showAddCategoryDialog = false,
            showActiveDevicesDialog = false,
            registeredDevices = emptyList(),
            accessPolicyConfig = sanitizedPolicyForRole(accessPolicyConfig, role)
        )
    }

    // Navigation Tab
    fun selectTab(tab: FiltrTab) {
        if (!ensureAppAllowed()) return
        if (_uiState.value.currentRole != "ADMIN" && tab !in STAFF_ALLOWED_TABS) {
            _uiState.update {
                it.copy(
                    currentTab = if (it.currentTab in STAFF_ALLOWED_TABS) it.currentTab else FiltrTab.BILLING,
                    snackbarMessage = "Admin access required for this section"
                )
            }
            return
        }
        refreshDateKeyIfNeeded()
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

    private fun cartKeyMatches(a: MenuItemEntity, b: MenuItemEntity): Boolean {
        return if (a.id != 0 && b.id != 0) {
            a.id == b.id
        } else {
            a.id == b.id && a.name.trim().equals(b.name.trim(), ignoreCase = true)
        }
    }

    private fun mergeIntoCart(cart: MutableMap<MenuItemEntity, Int>, item: MenuItemEntity, qtyToAdd: Int) {
        val existingKey = cart.keys.find { cartKeyMatches(it, item) }
        val targetItem = existingKey ?: item
        cart[targetItem] = (cart[targetItem] ?: 0) + qtyToAdd
    }

    // Cart Operations
    fun addToCart(item: MenuItemEntity) {
        if (!ensureAppAllowed()) return
        _uiState.update { state ->
            val updatedMap = state.cartItems.toMutableMap()
            mergeIntoCart(updatedMap, item, 1)
            state.copy(cartItems = updatedMap)
        }
    }

    fun removeFromCart(item: MenuItemEntity) {
        _uiState.update { state ->
            val existingKey = state.cartItems.keys.find { cartKeyMatches(it, item) } ?: item
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
        val availableMenu = menuItems.value.filter { it.isAvailable }
        val newCart = _uiState.value.cartItems.toMutableMap()

        voiceItems.forEach { vItem ->
            val cleanName = vItem.name.trim()
            val entity = availableMenu.find { it.name.trim().equals(cleanName, ignoreCase = true) }
                ?: availableMenu.find { it.name.contains(cleanName, ignoreCase = true) }
                ?: availableMenu.find { cleanName.contains(it.name.trim(), ignoreCase = true) }
                ?: MenuItemEntity(
                    id = 0,
                    name = cleanName.ifBlank { vItem.name },
                    category = "Voice Order",
                    price = vItem.unitPrice,
                    isAvailable = true
                )
            mergeIntoCart(newCart, entity, vItem.quantity)
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
            if (!ensureAppAllowed()) return@launch
            if (_uiState.value.isPlacingOrder || voiceItems.isEmpty()) return@launch

            _uiState.update { it.copy(isPlacingOrder = true) }

            try {
                val availableMenu = menuItems.value.filter { it.isAvailable }
                val cartMap = mutableMapOf<MenuItemEntity, Int>()

                voiceItems.forEach { vItem ->
                    val cleanName = vItem.name.trim()
                    val entity = availableMenu.find { it.name.trim().equals(cleanName, ignoreCase = true) }
                        ?: MenuItemEntity(
                            id = 0,
                            name = cleanName.ifBlank { vItem.name },
                            category = "Voice Order",
                            price = vItem.unitPrice,
                            isAvailable = true
                        )
                    mergeIntoCart(cartMap, entity, vItem.quantity)
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
            if (!ensureAppAllowed()) return@launch
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
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val newCompletedQty = if (targetUnitIndex <= currentCompletedQty) {
                    targetUnitIndex - 1
                } else {
                    targetUnitIndex
                }
                val safeQty = newCompletedQty.coerceIn(0, totalQty)
                val updatedOrder = repository.updateOrderItemCompletedQuantity(
                    orderId = orderId,
                    itemId = itemId,
                    completedQty = safeQty,
                    performedByStaff = selectedKdsBaristaName.value
                )

                val url = _uiState.value.googleSheetsWebhookUrl
                if (url.isNotBlank() && updatedOrder != null) {
                    repository.syncOrderToGoogleSheets(url, updatedOrder)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update item status: ${e.message}") }
            }
        }
    }

    fun toggleOrderMadeStatus(orderWithItems: OrderWithItems) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update order status: ${e.message}") }
            }
        }
    }

    fun setOrderPaymentMode(orderWithItems: OrderWithItems, paymentMode: String) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update payment: ${e.message}") }
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
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update delivery status: ${e.message}") }
            }
        }
    }

    fun updateOrderItems(orderWithItems: OrderWithItems, updatedItems: List<OrderItemEntity>) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                repository.updateOrderWithItems(orderWithItems, updatedItems, url)
                _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} updated successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update order: ${e.message}") }
            }
        }
    }

    fun updateOrderWithCustomerDetails(
        orderWithItems: OrderWithItems,
        updatedItems: List<OrderItemEntity>,
        customerName: String,
        contactNumber: String,
        notes: String = orderWithItems.order.notes
    ) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                val updatedOrderEntity = orderWithItems.order.copy(
                    customerName = customerName.trim().ifBlank { "Walk-in Customer" },
                    contactNumber = contactNumber.trim(),
                    notes = notes.trim()
                )
                val updatedOrder = OrderWithItems(updatedOrderEntity, orderWithItems.items)
                repository.updateOrderWithItems(updatedOrder, updatedItems, url)
                _uiState.update { it.copy(snackbarMessage = "Order #${orderWithItems.order.orderId} updated successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update order: ${e.message}") }
            }
        }
    }

    fun syncOrderToSheets(orderWithItems: OrderWithItems) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                if (url.isBlank()) {
                    if (_uiState.value.currentRole == "ADMIN") {
                        _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "Google Sheets sync URL not configured yet.") }
                    }
                    return@launch
                }

                _uiState.update { it.copy(snackbarMessage = "Syncing order #${orderWithItems.order.orderId} to Google Sheets...") }
                val result = repository.syncOrderToGoogleSheets(url, orderWithItems)
                if (result.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = "Synced order #${orderWithItems.order.orderId} to Google Sheets!") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    fun syncPendingOrdersToSheets() {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                if (url.isBlank()) {
                    if (_uiState.value.currentRole == "ADMIN") {
                        _uiState.update { it.copy(showSettingsDialog = true, snackbarMessage = "Please configure Google Sheets Webhook URL first.") }
                    } else {
                        _uiState.update { it.copy(snackbarMessage = "Google Sheets sync URL not configured yet.") }
                    }
                    return@launch
                }

                _uiState.update { it.copy(snackbarMessage = "Syncing unsynced / offline orders to Google Sheets...") }
                val result = repository.syncPendingOrdersToGoogleSheets(url)
                if (result.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = result.getOrNull() ?: "Pending orders synced successfully!") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Sync failed: ${result.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") }
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
        if (!requireAdmin("Syncing all orders to Google Sheets")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    fun syncItemMasterToSheets() {
        if (!requireAdmin("Syncing Item Master")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Sync failed: ${e.message}") }
            }
        }
    }

    fun deleteOrder(orderId: String) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                repository.deleteOrder(orderId, url)
                _uiState.update { it.copy(snackbarMessage = "Order #$orderId deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete order: ${e.message}") }
            }
        }
    }

    // AI Voice Order Handling
    fun startListeningVoice() {
        if (!ensureAppAllowed()) return
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
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isListeningVoice = false,
                    isParsingVoice = true,
                    parsedVoiceOrder = null,
                    showVoiceOrderReviewDialog = false,
                    snackbarMessage = "🎙️ Deepgram Nova-3 analyzing voice recording..."
                )
            }

            try {
                val items = menuItems.value
                val result = repository.parseVoiceAudio(audioBytes, mimeType, items)

                // Asynchronously dispatch voice telemetry (audio file + diagnostic JSON) to Supabase Storage voice-orders bucket
                val timestamp = System.currentTimeMillis()
                val timeString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
                val baseFileName = "voice_order_${timeString}_${timestamp % 1000}"

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val supabase = SupabaseService.getInstance()
                        val audioFileName = "$baseFileName.m4a"
                        val audioUploadResult = supabase.uploadVoiceOrderAudio(audioFileName, audioBytes)

                        val isSuccess = result.isSuccess && result.getOrNull() != null
                        val parsedOrder = result.getOrNull()
                        val diagJson = JSONObject().apply {
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
                            val itemsArray = JSONArray()
                            parsedOrder?.items?.forEach { item ->
                                val itemObj = JSONObject().apply {
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
                    val availableMenu = menuItems.value.filter { it.isAvailable }
                    val newCart = mutableMapOf<MenuItemEntity, Int>()

                    parsedOrder.items.forEach { parsedItem ->
                        val cleanName = parsedItem.menuItemName.trim()
                        val menuObj = availableMenu.find { it.name.trim().equals(cleanName, ignoreCase = true) }
                            ?: availableMenu.find {
                                it.name.lowercase().contains(cleanName.lowercase()) ||
                                    cleanName.lowercase().contains(it.name.lowercase())
                            }

                        if (menuObj != null) {
                            mergeIntoCart(newCart, menuObj, parsedItem.quantity)
                        }
                    }

                    val hasSpokenCustomer = parsedOrder.customerName.isNotBlank() &&
                        !parsedOrder.customerName.trim().equals("Walk-in Customer", ignoreCase = true)
                    val contact = parsedOrder.contactNumber

                    _uiState.update { state ->
                        val updatedCustomer = if (hasSpokenCustomer && newCart.isNotEmpty()) {
                            parsedOrder.customerName.trim()
                        } else {
                            state.customerName
                        }
                        state.copy(
                            isParsingVoice = false,
                            parsedVoiceOrder = parsedOrder,
                            showVoiceOrderReviewDialog = true,
                            customerName = updatedCustomer,
                            contactNumber = if (contact.isNotBlank() && newCart.isNotEmpty()) contact else state.contactNumber,
                            orderNotes = if (parsedOrder.notes.isNotBlank() && newCart.isNotEmpty()) parsedOrder.notes else state.orderNotes,
                            cartItems = if (newCart.isNotEmpty()) newCart else state.cartItems,
                            snackbarMessage = "✨ Voice Order Parsed! ${newCart.values.sum()} items matched" +
                                if (hasSpokenCustomer) " for ${parsedOrder.customerName.trim()}" else ""
                        )
                    }
                } else {
                    val errMsg = result.exceptionOrNull()?.message ?: "Could not parse voice recording. Please try speaking clearly."
                    _uiState.update {
                        it.copy(
                            isParsingVoice = false,
                            snackbarMessage = errMsg
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isParsingVoice = false,
                        snackbarMessage = "Voice parsing failed: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun processVoiceTranscript(transcript: String) {
        if (!ensureAppAllowed()) return
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

            try {
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isParsingVoice = false,
                        snackbarMessage = "Voice parsing failed: ${e.message ?: "Unknown error"}"
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
        val availableMenu = menuItems.value.filter { it.isAvailable }
        val newCart = _uiState.value.cartItems.toMutableMap()

        parsedOrder.items.forEach { parsedItem ->
            val cleanName = parsedItem.menuItemName.trim()
            val menuObj = availableMenu.find { it.name.trim().equals(cleanName, ignoreCase = true) }
                ?: availableMenu.find { it.name.contains(cleanName, ignoreCase = true) }
                ?: availableMenu.find { cleanName.contains(it.name.trim(), ignoreCase = true) }
            if (menuObj != null) {
                mergeIntoCart(newCart, menuObj, parsedItem.quantity)
            } else {
                val fallbackItem = MenuItemEntity(
                    id = 0,
                    name = cleanName.ifBlank { parsedItem.menuItemName },
                    category = "VOICE",
                    price = 0.0,
                    description = ""
                )
                mergeIntoCart(newCart, fallbackItem, parsedItem.quantity)
            }
        }

        val hasSpokenCustomer = parsedOrder.customerName.isNotBlank() &&
            !parsedOrder.customerName.trim().equals("Walk-in Customer", ignoreCase = true)

        _uiState.update {
            it.copy(
                customerName = if (hasSpokenCustomer) parsedOrder.customerName.trim() else it.customerName,
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
        if (!requireAdmin("Editing menu items")) return
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
        if (!requireAdmin("Saving menu items")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save menu item: ${e.message}") }
            }
        }
    }

    fun setDashboardDateFilter(filter: String) {
        if (!requireAdmin("Dashboard analytics")) return
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
                    add(Calendar.DAY_OF_YEAR, -6)
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
        if (!requireAdmin("UPI account management")) return
        _uiState.update { it.copy(showAccountsUpiDialog = true) }
    }

    fun closeAccountsUpiDialog() {
        _uiState.update { it.copy(showAccountsUpiDialog = false) }
    }

    fun addUpiAccount(vpa: String, payeeName: String, isDefault: Boolean) {
        if (!requireAdmin("Adding UPI account")) return
        viewModelScope.launch {
            try {
                repository.addUpiAccount(vpa, payeeName, isDefault)
                _uiState.update { it.copy(snackbarMessage = "UPI ID added successfully!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to add UPI ID: ${e.message}") }
            }
        }
    }

    fun deleteUpiAccount(account: com.example.data.entity.UpiAccountEntity) {
        if (!requireAdmin("Deleting UPI account")) return
        viewModelScope.launch {
            try {
                repository.deleteUpiAccount(account)
                _uiState.update { it.copy(snackbarMessage = "UPI ID removed.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to remove UPI ID: ${e.message}") }
            }
        }
    }

    fun setDefaultUpiAccount(accountId: Int) {
        if (!requireAdmin("Setting default UPI account")) return
        viewModelScope.launch {
            try {
                repository.setDefaultUpiAccount(accountId)
                _uiState.update { it.copy(snackbarMessage = "Default UPI ID updated.") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update default UPI ID: ${e.message}") }
            }
        }
    }

    // Delete Menu Item
    fun requestDeleteMenuItem(item: MenuItemEntity) {
        if (!requireAdmin("Deleting menu items")) return
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
        if (!requireAdmin("Deleting menu items")) return
        viewModelScope.launch {
            try {
                val hasOrderHistory = withContext(Dispatchers.IO) {
                    repository.hasOrderHistoryForMenuItem(item)
                }
                if (hasOrderHistory) {
                    _uiState.update {
                        it.copy(
                            itemToDeleteConfirmation = null,
                            itemDeleteWarningMessage = item
                        )
                    }
                    return@launch
                }
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        itemToDeleteConfirmation = null,
                        snackbarMessage = "Failed to delete item: ${e.message}"
                    )
                }
            }
        }
    }

    fun markItemUnavailable(item: MenuItemEntity) {
        if (!requireAdmin("Updating item availability")) return
        viewModelScope.launch {
            try {
                repository.updateMenuItem(item.copy(isAvailable = false))
                _uiState.update {
                    it.copy(
                        itemDeleteWarningMessage = null,
                        snackbarMessage = "'${item.name}' marked as Unavailable to preserve order records"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        itemDeleteWarningMessage = null,
                        snackbarMessage = "Failed to update item: ${e.message}"
                    )
                }
            }
        }
    }

    fun requestDeleteOrder(orderId: String) {
        if (!ensureAppAllowed()) return
        _uiState.update { it.copy(orderToDeleteConfirmation = orderId) }
    }

    fun confirmDeleteOrder(orderId: String) {
        if (!ensureAppAllowed()) return
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
                // Re-sync today's orders to Sheets, explicitly excluding the deleted orderId
                // in case Room's stateIn emission hasn't flushed yet.
                if (url.isNotBlank()) {
                    val todayMs = System.currentTimeMillis()
                    val todayOrders = ordersList.value.filter {
                        it.order.orderId != orderId && isSameDay(it.order.timestamp, todayMs)
                    }
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
        if (!requireAdmin("Analytics date selection")) return
        _uiState.update { it.copy(showCustomDatePickerDialog = true) }
    }

    fun dismissCustomDatePicker() {
        _uiState.update { it.copy(showCustomDatePickerDialog = false) }
    }

    fun setCustomDateRange(startMs: Long, endMs: Long) {
        if (!requireAdmin("Analytics date selection")) return
        val safeStart = minOf(startMs, endMs)
        val safeEnd = maxOf(startMs, endMs)
        _uiState.update {
            it.copy(
                dashboardDateFilter = "CUSTOM",
                customStartDateMs = safeStart,
                customEndDateMs = safeEnd,
                showCustomDatePickerDialog = false,
                snackbarMessage = "Fetching orders for selected range..."
            )
        }

        // Backfill any missing orders from Firestore for this date range
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backfilled = repository.backfillOrdersForDateRange(safeStart, safeEnd)
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

        syncDateRangeAnalyticsToSheets(safeStart, safeEnd)
    }

    fun syncDateRangeAnalyticsToSheets(startMs: Long, endMs: Long) {
        if (!requireAdmin("Syncing analytics")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                Log.w(TAG, "Sheets range analytics sync failed: ${e.message}")
            }
        }
    }

    fun deleteMenuItem(item: MenuItemEntity) {
        requestDeleteMenuItem(item)
    }

    // Settings & Webhook
    fun openSettingsDialog() {
        if (!requireAdmin("Settings")) return
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun saveGoogleSheetsWebhookUrl(url: String) {
        if (_uiState.value.currentRole != "ADMIN") return
        val trimmedUrl = url.trim()
        prefs.edit().putString("sheets_webhook_url", trimmedUrl).apply()
        _uiState.update {
            it.copy(
                googleSheetsWebhookUrl = trimmedUrl,
                showSettingsDialog = false,
                snackbarMessage = "Google Sheets Webhook URL saved & synced across devices! Syncing pending orders..."
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            SupabaseService.getInstance().saveGoogleSheetsWebhookUrl(trimmedUrl)
        }
        if (trimmedUrl.isNotBlank()) {
            syncPendingOrdersToSheets()
        }
    }

    fun updatePriceAlertThreshold(thresholdPct: Double) {
        if (!requireAdmin("Updating price alert threshold")) return
        val safe = thresholdPct.coerceIn(0.5, 50.0)
        prefs.edit().putString("price_alert_threshold_pct", safe.toString()).apply()
        _uiState.update {
            it.copy(
                priceAlertThresholdPct = safe,
                snackbarMessage = "Price hike alert threshold set to ±${safe.toInt()}%"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            SupabaseService.getInstance().savePriceAlertThresholdPct(safe)
        }
    }

    fun updateWebhookUrl(url: String) = saveGoogleSheetsWebhookUrl(url)

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
        if (!requireAdmin("Receipt Story customization")) return
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
        if (!requireAdmin("Receipt Story customization")) return
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

    /** Fetches a receipt story note and surfaces errors instead of masking failures with a fallback. */
    private suspend fun fetchStoryNote(
        title: String,
        date: String,
        updateReceiptStoryState: Boolean
    ): String? {
        return try {
            var generatedNote: String? = null
            repository.generateStoryNote(title, date)
                .onSuccess { note ->
                    generatedNote = note
                    _uiState.update { state ->
                        state.copy(
                            receiptStoryCaption = if (updateReceiptStoryState) note else state.receiptStoryCaption,
                            isGeneratingStoryNote = if (updateReceiptStoryState) false else state.isGeneratingStoryNote,
                            snackbarMessage = "AI note generated!"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { state ->
                        state.copy(
                            isGeneratingStoryNote = if (updateReceiptStoryState) false else state.isGeneratingStoryNote,
                            snackbarMessage = "AI note generation failed: ${err.message ?: "Unknown error"}"
                        )
                    }
                }
            generatedNote
        } catch (e: CancellationException) {
            if (updateReceiptStoryState) {
                _uiState.update { it.copy(isGeneratingStoryNote = false) }
            }
            throw e
        } catch (e: Exception) {
            _uiState.update { state ->
                state.copy(
                    isGeneratingStoryNote = if (updateReceiptStoryState) false else state.isGeneratingStoryNote,
                    snackbarMessage = "AI note generation failed: ${e.message ?: "Unknown error"}"
                )
            }
            null
        }
    }

    fun generateReceiptStoryNote(title: String, date: String) {
        if (!requireAdmin("Generating receipt story note")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingStoryNote = true) }
            fetchStoryNote(title, date, updateReceiptStoryState = true)
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
        if (!requireAdmin("Receipt Story customization")) return
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
        if (!requireAdmin("Receipt design customization")) return
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
        if (!requireAdmin("Generating receipt story note")) return ""
        return fetchStoryNote(title, date, updateReceiptStoryState = false) ?: ""
    }

    fun uploadStoryPhoto(imageBytes: ByteArray, title: String = "", date: String = "", caption: String = "") {
        if (!requireAdmin("Uploading story photo")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingStoryPhoto = true) }
            try {
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isUploadingStoryPhoto = false,
                        snackbarMessage = "Upload failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteStoryPhotoByName(fileName: String) {
        if (!requireAdmin("Deleting story photo")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Delete failed: ${e.message}")
                }
            }
        }
    }

    private fun computeInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        while (width / inSampleSize > maxDim || height / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun decodeSampledBitmapFromFile(path: String, maxDim: Int = 512): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            android.graphics.BitmapFactory.decodeFile(path, opts)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, maxDim: Int = 512): android.graphics.Bitmap? {
        return try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim)
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Get a random story photo bitmap and metadata for printing.
     * Downloads a random photo from the Supabase Storage bucket on IO thread with downsampling.
     */
    suspend fun getRandomStoryPhotoForPrint(): StoryPhotoPrintData? = withContext(Dispatchers.IO) {
        val state = _uiState.value
        val photos = state.storyPhotoNames
        if (photos.isEmpty()) {
            val localPath = state.receiptStoryImagePath
            if (localPath.isNotBlank()) {
                val bitmap = decodeSampledBitmapFromFile(localPath)
                return@withContext if (bitmap != null) {
                    StoryPhotoPrintData(
                        bitmap = bitmap,
                        title = state.receiptStoryTitle,
                        date = state.receiptStoryDate,
                        caption = state.receiptStoryCaption
                    )
                } else null
            }
            return@withContext null
        }

        val randomFileName = photos.random()
        val result = try {
            repository.downloadStoryPhoto(randomFileName)
        } catch (_: Exception) {
            Result.failure(Exception("Download failed"))
        }
        val bitmap = if (result.isSuccess) {
            val bytes = result.getOrNull()
            if (bytes != null) decodeSampledBitmapFromBytes(bytes) else null
        } else null

        if (bitmap == null) return@withContext null

        val meta = state.storyPhotoMetadataMap[randomFileName]
        val title = meta?.title?.ifBlank { null } ?: state.receiptStoryTitle
        val date = meta?.date?.ifBlank { null } ?: state.receiptStoryDate
        val caption = meta?.caption?.ifBlank { null } ?: state.receiptStoryCaption

        StoryPhotoPrintData(
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
        if (!requireAdmin("Inventory Master")) return
        _uiState.update { it.copy(showInventoryMasterDialog = true) }
    }

    fun dismissInventoryMasterDialog() {
        _uiState.update { it.copy(showInventoryMasterDialog = false, editingInventoryItem = null, showAddInventoryItemDialog = false) }
    }

    fun openAddInventoryItemDialog(item: InventoryItemEntity? = null) {
        if (!requireAdmin("Inventory Master")) return
        _uiState.update { it.copy(showAddInventoryItemDialog = true, editingInventoryItem = item) }
    }

    fun dismissAddInventoryItemDialog() {
        _uiState.update { it.copy(showAddInventoryItemDialog = false, editingInventoryItem = null) }
    }

    fun saveInventoryItem(
        name: String,
        category: String,
        unit: String,
        defaultPrice: Double,
        standardQuantity: Double = 1.0,
        initialStock: Double = 0.0
    ) {
        if (!ensureAppAllowed()) return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Item name cannot be empty") }
            return
        }
        viewModelScope.launch {
            try {
                val editing = _uiState.value.editingInventoryItem
                if (editing != null) {
                    repository.updateInventoryItem(
                        editing.copy(
                            name = cleanName,
                            category = category.trim(),
                            unit = unit.trim().ifBlank { "kg" },
                            defaultPrice = defaultPrice,
                            standardQuantity = standardQuantity.coerceAtLeast(1.0)
                        )
                    )
                    _uiState.update { it.copy(snackbarMessage = "Inventory item updated", showAddInventoryItemDialog = false, editingInventoryItem = null) }
                } else {
                    repository.addInventoryItem(
                        InventoryItemEntity(
                            name = cleanName,
                            category = category.trim(),
                            unit = unit.trim().ifBlank { "kg" },
                            defaultPrice = defaultPrice,
                            standardQuantity = standardQuantity.coerceAtLeast(1.0),
                            currentStock = initialStock.coerceAtLeast(0.0)
                        )
                    )
                    _uiState.update { it.copy(snackbarMessage = "Inventory item added", showAddInventoryItemDialog = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save inventory item: ${e.message}") }
            }
        }
    }

    fun updateInventoryItem(item: InventoryItemEntity) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                repository.updateInventoryItem(item)
                _uiState.update { it.copy(snackbarMessage = "\"${item.name}\" updated successfully") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update item: ${e.message}") }
            }
        }
    }

    fun updateInventoryItemWithStock(item: InventoryItemEntity, newCount: Double) {
        if (!ensureAppAllowed()) return
        val rounded = Math.round(newCount.coerceAtLeast(0.0) * 100.0) / 100.0
        viewModelScope.launch {
            try {
                val updated = item.copy(currentStock = rounded)
                repository.updateInventoryItem(updated)
                repository.updateStock(updated.id, rounded)
                _uiState.update { state ->
                    state.copy(
                        manualInventoryDraftCounts = state.manualInventoryDraftCounts - updated.id,
                        snackbarMessage = "\"${updated.name}\" updated (${updated.unit})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update item: ${e.message}") }
            }
        }
    }

    fun updateManualInventoryDraftCount(itemId: Int, newCount: Double) {
        val rounded = Math.round(newCount.coerceAtLeast(0.0) * 100.0) / 100.0
        _uiState.update { state ->
            state.copy(manualInventoryDraftCounts = state.manualInventoryDraftCounts + (itemId to rounded))
        }
    }

    fun updateManualInventoryDraftNotes(notes: String) {
        _uiState.update { it.copy(manualInventoryDraftNotes = notes) }
    }

    fun deleteInventoryItem(item: InventoryItemEntity) {
        if (!requireAdmin("Deleting inventory items")) return
        viewModelScope.launch {
            try {
                repository.deleteInventoryItem(item)
                _uiState.update { state ->
                    state.copy(
                        manualInventoryDraftCounts = state.manualInventoryDraftCounts - item.id,
                        snackbarMessage = "\"${item.name}\" removed from inventory master"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete inventory item: ${e.message}") }
            }
        }
    }

    fun saveClosingInventoryAudit(
        stockMap: Map<Int, Double>,
        staffNotes: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (!ensureAppAllowed()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            try {
                val currentState = _uiState.value
                val allItems = inventoryItems.value
                val activeIds = allItems.filter { it.isActive }.map { it.id }.toSet()
                val validStockMap = stockMap.filterKeys { it in activeIds }

                withContext(Dispatchers.IO) {
                    // Update Room DB current stock for active items
                    for ((itemId, stock) in validStockMap) {
                        repository.updateStock(itemId, stock.coerceAtLeast(0.0))
                    }

                    // Format a daily closing log
                    val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                    val timeSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    val todayStr = sdf.format(Date())
                    val nowStr = timeSdf.format(Date())
                    val deviceName = currentState.operatorName.ifBlank { currentDeviceModel.ifBlank { "Staff Terminal" } }

                    val auditObj = JSONObject().apply {
                        put("date", todayStr)
                        put("timestamp", nowStr)
                        put("device", deviceName)
                        put("role", currentState.currentRole)
                        put("notes", staffNotes.trim())
                        val itemsArr = JSONArray()
                        for ((itemId, stock) in validStockMap) {
                            val itm = allItems.find { it.id == itemId }
                            if (itm != null) {
                                itemsArr.put(JSONObject().apply {
                                    put("id", itemId)
                                    put("name", itm.name)
                                    put("category", itm.category)
                                    put("unit", itm.unit)
                                    put("closingStock", stock.coerceAtLeast(0.0))
                                })
                            }
                        }
                        put("items", itemsArr)
                    }

                    val auditJson = auditObj.toString()

                    // Save locally to SharedPreferences for quick recall
                    prefs.edit()
                        .putString("last_closing_inventory_notes", staffNotes.trim())
                        .putString("last_closing_inventory_date", todayStr)
                        .putString("closing_inventory_$todayStr", auditJson)
                        .apply()

                    // Sync to Supabase sync_metadata so admin can see staff closing notes from anywhere
                    try {
                        SupabaseService.getInstance().saveSyncMetadata(
                            "closing_inventory_$todayStr",
                            auditJson
                        )
                    } catch (e: Exception) {
                        Log.w("MainViewModel", "Remote closing audit sync failed: ${e.message}")
                    }
                }

                _uiState.update {
                    it.copy(
                        manualInventoryDraftCounts = emptyMap(),
                        snackbarMessage = "Closing inventory & staff notes saved successfully!"
                    )
                }
                onComplete(true)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Save closing inventory failed", e)
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to save closing inventory: ${e.message}")
                }
                onComplete(false)
            }
        }
    }

    fun updateInventorySearchQuery(query: String) {
        _uiState.update { it.copy(inventorySearchQuery = query) }
    }

    fun openAddInventoryOrderDialog() {
        if (!requireAdmin("Creating purchase orders")) return
        _uiState.update { it.copy(showAddInventoryOrderDialog = true) }
    }

    fun dismissAddInventoryOrderDialog() {
        _uiState.update { it.copy(showAddInventoryOrderDialog = false) }
    }

    fun saveInventoryOrder(
        supplierName: String,
        notes: String,
        items: List<InventoryOrderItemInput>
    ) {
        if (!requireAdmin("Saving purchase orders")) return
        viewModelScope.launch {
            try {
                // Filter out blank names and non-positive quantities
                val validItems = items.filter { it.itemName.isNotBlank() && it.quantity > 0.0 }
                if (validItems.isEmpty()) {
                    _uiState.update { it.copy(snackbarMessage = "Add at least one item with valid quantity") }
                    return@launch
                }

                val orderId = repository.getNextInventoryOrderId()
                val totalAmount = validItems.sumOf { it.quantity * it.unitPrice }

                val orderEntity = InventoryOrderEntity(
                    orderId = orderId,
                    timestamp = System.currentTimeMillis(),
                    supplierName = supplierName.trim(),
                    totalAmount = totalAmount,
                    notes = notes.trim()
                )

                val orderItems = validItems.map { item ->
                    val cleanName = item.itemName.trim()
                    val cleanUnit = item.unit.trim().ifBlank { "kg" }
                    InventoryOrderItemEntity(
                        orderId = orderId,
                        itemName = cleanName,
                        quantity = item.quantity,
                        unit = cleanUnit,
                        unitPrice = item.unitPrice,
                        subtotal = item.quantity * item.unitPrice
                    )
                }

                // Auto-add new items to inventory master BEFORE creating the order
                // so that addInventoryOrder can find them and add stock correctly
                for (item in validItems) {
                    val cleanName = item.itemName.trim()
                    val existing = repository.getInventoryItemByName(cleanName)
                    if (existing == null) {
                        repository.addInventoryItem(
                            InventoryItemEntity(
                                name = cleanName,
                                unit = item.unit.trim().ifBlank { "kg" },
                                defaultPrice = item.unitPrice
                            )
                        )
                    }
                }

                repository.addInventoryOrder(orderEntity, orderItems)

                val url = _uiState.value.googleSheetsWebhookUrl
                if (url.isNotBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.syncInventoryPurchaseToSheets(
                            webhookUrl = url,
                            order = orderEntity,
                            items = orderItems,
                            priceAlertThresholdPct = _uiState.value.priceAlertThresholdPct
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        showAddInventoryOrderDialog = false,
                        snackbarMessage = "Inventory order $orderId saved & prices updated (${validItems.size} items, ₹${"%.0f".format(totalAmount)})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save inventory order: ${e.message}") }
            }
        }
    }

    fun requestDeleteInventoryOrder(orderId: String) {
        if (!requireAdmin("Deleting purchase orders")) return
        _uiState.update { it.copy(inventoryOrderToDelete = orderId) }
    }

    fun dismissDeleteInventoryOrder() {
        _uiState.update { it.copy(inventoryOrderToDelete = null) }
    }

    fun confirmDeleteInventoryOrder(orderId: String) {
        if (!requireAdmin("Deleting purchase orders")) return
        viewModelScope.launch {
            try {
                repository.deleteInventoryOrder(orderId)
                _uiState.update { it.copy(inventoryOrderToDelete = null, snackbarMessage = "Inventory order $orderId deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(inventoryOrderToDelete = null, snackbarMessage = "Failed to delete order: ${e.message}") }
            }
        }
    }

    // ==================== INVENTORIES (STOCK) ====================

    fun updateStockLevel(itemId: Int, newStock: Double) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                repository.updateStock(itemId, newStock.coerceAtLeast(0.0))
                _uiState.update { state ->
                    state.copy(
                        editingStockItemId = null,
                        manualInventoryDraftCounts = state.manualInventoryDraftCounts - itemId,
                        snackbarMessage = "Stock updated"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update stock: ${e.message}") }
            }
        }
    }

    fun startEditingStock(itemId: Int) {
        if (!requireAdmin("Editing inventory stock")) return
        _uiState.update { it.copy(editingStockItemId = itemId) }
    }

    fun cancelEditingStock() {
        _uiState.update { it.copy(editingStockItemId = null) }
    }

    fun openAddStockDialog(item: InventoryItemEntity) {
        if (!requireAdmin("Adding stock packs")) return
        _uiState.update { it.copy(showAddStockDialog = true, addStockItem = item) }
    }

    fun dismissAddStockDialog() {
        _uiState.update { it.copy(showAddStockDialog = false, addStockItem = null) }
    }

    fun addStockPacks(itemId: Int, packsToAdd: Double) {
        if (!requireAdmin("Adding stock packs")) return
        if (packsToAdd <= 0.0) return
        viewModelScope.launch {
            try {
                repository.addStock(itemId, packsToAdd)
                _uiState.update { it.copy(showAddStockDialog = false, addStockItem = null, snackbarMessage = "Stock added: +$packsToAdd packs") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to add stock: ${e.message}") }
            }
        }
    }

    // ==================== INVENTORY USAGE (RECIPES) ====================

    fun openUsagePerSkuDialog(menuItemId: Int, menuItemName: String) {
        if (!requireAdmin("Editing recipes")) return
        _uiState.update { it.copy(showUsagePerSkuDialog = true, usageEditingMenuItemId = menuItemId, usageEditingMenuItemName = menuItemName) }
    }

    fun dismissUsagePerSkuDialog() {
        _uiState.update { it.copy(showUsagePerSkuDialog = false, usageEditingMenuItemId = null, usageEditingMenuItemName = "") }
    }

    fun saveUsage(menuItemId: Int, menuItemName: String, inventoryItemId: Int, inventoryItemName: String, quantityUsed: Double, unit: String) {
        if (!requireAdmin("Saving recipes")) return
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save recipe usage: ${e.message}") }
            }
        }
    }

    fun deleteUsage(usage: InventoryUsageEntity) {
        if (!requireAdmin("Deleting recipe usage")) return
        viewModelScope.launch {
            try {
                repository.deleteUsage(usage)
                _uiState.update { it.copy(snackbarMessage = "${usage.inventoryItemName} removed from ${usage.menuItemName} recipe") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete recipe usage: ${e.message}") }
            }
        }
    }

    // ==================== CUSTOMER MANAGEMENT ====================

    fun addCustomer(name: String, contact: String) {
        if (!requireAdmin("Adding customers")) return
        viewModelScope.launch {
            try {
                val result = repository.saveCustomer(name.trim(), contact.trim())
                if (result.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = "Customer '${name.trim()}' added") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = result.exceptionOrNull()?.message ?: "Failed to add customer") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to add customer: ${e.message}") }
            }
        }
    }

    fun updateExistingCustomer(customer: CustomerEntity) {
        if (!requireAdmin("Updating customers")) return
        viewModelScope.launch {
            try {
                repository.updateCustomer(customer)
                _uiState.update { it.copy(snackbarMessage = "Customer '${customer.name}' updated") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update customer: ${e.message}") }
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        if (!requireAdmin("Deleting customers")) return
        viewModelScope.launch {
            try {
                repository.deleteCustomer(customer)
                _uiState.update { it.copy(snackbarMessage = "Customer '${customer.name}' deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete customer: ${e.message}") }
            }
        }
    }

    fun settleAllCustomerOrders(
        customerName: String,
        pendingOrders: List<OrderWithItems>,
        paymentMode: String
    ) {
        if (!requireAdmin("Settling customer dues")) return
        if (pendingOrders.isEmpty()) return
        val safeMode = if (paymentMode == "CASH" || paymentMode == "UPI") paymentMode else "CASH"
        viewModelScope.launch {
            var settledCount = 0
            try {
                val url = _uiState.value.googleSheetsWebhookUrl
                for (orderWithItems in pendingOrders) {
                    repository.updateOrderPaidStatus(orderWithItems.order.orderId, true, safeMode)
                    settledCount++
                    if (url.isNotBlank()) {
                        val updatedOrder = orderWithItems.copy(
                            order = orderWithItems.order.copy(isPaid = true, paymentMode = safeMode)
                        )
                        try {
                            repository.syncOrderToGoogleSheets(url, updatedOrder)
                        } catch (syncEx: Exception) {
                            Log.w(TAG, "Sheets sync skipped for ${orderWithItems.order.orderId}: ${syncEx.message}")
                        }
                    }
                }
                _uiState.update {
                    it.copy(snackbarMessage = "Settled $settledCount due order(s) for $customerName via $safeMode")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(snackbarMessage = "Settled $settledCount order(s); failed on remaining: ${e.message}")
                }
            }
        }
    }

    // ==================== FIXED COSTS & P&L ====================

    fun openFixedCostDialog(monthKey: String = "") {
        if (!requireAdmin("Managing fixed costs")) return
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
        if (!requireAdmin("Saving fixed costs")) return
        viewModelScope.launch {
            try {
                repository.saveFixedCost(cost)
                _uiState.update {
                    it.copy(
                        showFixedCostDialog = false,
                        snackbarMessage = "Fixed costs for ${cost.monthKey} saved (₹${"%,.0f".format(cost.totalFixedCost)}/mo)"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save fixed costs: ${e.message}") }
            }
        }
    }

    fun deleteFixedCost(monthKey: String) {
        if (!requireAdmin("Deleting fixed costs")) return
        viewModelScope.launch {
            try {
                repository.deleteFixedCost(monthKey)
                _uiState.update { it.copy(snackbarMessage = "Fixed costs for $monthKey deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete fixed costs: ${e.message}") }
            }
        }
    }

    // ==================== IN-APP OTA APP UPDATES (GitHub Releases) ====================

    private var lastUpdateCheckMs = 0L

    fun onActivityResumed() {
        refreshDateKeyIfNeeded()
        if (_uiState.value.isForceUpdate) {
            _uiState.update { it.copy(showAppUpdateDialog = true) }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckMs > 60 * 1000L) {
            lastUpdateCheckMs = now
            viewModelScope.launch(Dispatchers.IO) {
                syncDeviceAccessHeartbeat()
            }
            checkForAppUpdates(isManualCheck = false)
        }
    }

    fun showMandatoryUpdateDialog() {
        _uiState.update { it.copy(showAppUpdateDialog = true) }
    }

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
                token = token,
                isStaffApp = (_uiState.value.currentRole == "STAFF")
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
                    try {
                        com.example.util.AppUpdateNotificationHelper.showUpdateNotification(getApplication(), updateInfo)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to post system update notification: ${e.message}")
                    }
                } else {
                    try {
                        com.example.util.AppUpdateNotificationHelper.cancelUpdateNotification(getApplication())
                    } catch (_: Exception) {}
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
        if (!requireAdmin("GitHub update settings")) return
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

    // ==========================================
    // DAILY CHECK LIST ACTIONS
    // ==========================================

    fun toggleChecklistTask(taskId: String) {
        if (!ensureAppAllowed()) return
        val activeKey = _activeDateKey.value
        val operator = _uiState.value.operatorName.ifBlank { _uiState.value.currentRole }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.toggleChecklistTask(taskId, activeKey, operator)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to update task: ${e.message}") }
            }
        }
    }

    fun toggleChecklistSectionExpanded(sectionId: String) {
        _uiState.update { current ->
            val collapsed = current.collapsedChecklistSectionIds.toMutableSet()
            if (collapsed.contains(sectionId)) {
                collapsed.remove(sectionId)
            } else {
                collapsed.add(sectionId)
            }
            current.copy(collapsedChecklistSectionIds = collapsed)
        }
    }

    fun openAddChecklistTaskDialog() {
        if (!ensureAppAllowed()) return
        _uiState.update { it.copy(showAddChecklistTaskDialog = true) }
    }

    fun dismissAddChecklistTaskDialog() {
        _uiState.update { it.copy(showAddChecklistTaskDialog = false) }
    }

    fun addChecklistTask(title: String, section: String, notes: String = "") {
        if (!ensureAppAllowed()) return
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sectionMap = mapOf(
                    "OPENING" to Pair("01_OPENING", "01. Opening & Daily Preparation"),
                    "01_OPENING" to Pair("01_OPENING", "01. Opening & Daily Preparation"),
                    "CLEANING" to Pair("02_CLEANING", "02. Cleaning & Store Hygiene"),
                    "HYGIENE" to Pair("02_CLEANING", "02. Cleaning & Store Hygiene"),
                    "02_CLEANING" to Pair("02_CLEANING", "02. Cleaning & Store Hygiene"),
                    "ROUTINE" to Pair("03_ROUTINE", "03. Routine Store Checks"),
                    "03_ROUTINE" to Pair("03_ROUTINE", "03. Routine Store Checks"),
                    "CLOSING" to Pair("04_CLOSING", "04. Closing Checklist"),
                    "04_CLOSING" to Pair("04_CLOSING", "04. Closing Checklist"),
                    "GENERAL" to Pair("05_GENERAL", "05. General Tasks"),
                    "05_GENERAL" to Pair("05_GENERAL", "05. General Tasks")
                )
                val (secId, secName) = sectionMap[section] ?: Pair(section, section)
                repository.addChecklistTask(
                    sectionId = secId,
                    sectionName = secName,
                    title = title.trim(),
                    description = notes.trim()
                )
                withContext(Dispatchers.Main) {
                    dismissAddChecklistTaskDialog()
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to add task: ${e.message}") }
            }
        }
    }

    fun deleteChecklistTask(task: ChecklistTaskEntity) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteChecklistTask(task)
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to delete task: ${e.message}") }
            }
        }
    }

    fun refreshChecklist() {
        refreshDateKeyIfNeeded()
        val activeKey = _activeDateKey.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.reconcileChecklistFromCloud(activeKey)
            } catch (e: Exception) {
                Log.w(TAG, "Checklist refresh failed: ${e.message}")
            }
        }
    }

    // ==========================================
    // DEVICE TRACKING, PASSCODE & ACCESS CONTROL
    // ==========================================

    suspend fun syncDeviceAccessHeartbeat() {
        try {
            val supabase = SupabaseService.getInstance()
            val policy = supabase.fetchAccessPolicyConfig()
            val currentRole = prefs.getString("authenticated_role", "STAFF") ?: "STAFF"
            val appRole = currentRole
            val localStatus = prefs.getString("device_access_status", "UNVERIFIED") ?: "UNVERIFIED"
            val localOperator = prefs.getString("device_operator_name", "") ?: ""
            val localEpoch = prefs.getInt("device_passcode_epoch", 1)
            val localRoleUpdatedAtMs = prefs.getLong("device_role_updated_at_ms", 0L)
            val localMemberId = prefs.getString("linked_member_id", "") ?: ""
            val localMemberName = prefs.getString("linked_member_name", "") ?: ""
            val localMemberLinkTs = prefs.getLong("device_member_link_updated_at_ms", 0L)

            val remoteWebhook = supabase.fetchGoogleSheetsWebhookUrl()
            val remoteSheetUrl = supabase.fetchGoogleSheetsSpreadsheetUrl()
            val remoteThresholdPct = supabase.fetchPriceAlertThresholdPct()

            val remoteReg = supabase.syncDeviceHeartbeat(
                deviceId = currentDeviceId,
                appRole = appRole,
                operatorName = localOperator,
                deviceModel = currentDeviceModel,
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                appVersionName = BuildConfig.VERSION_NAME,
                appVersionCode = BuildConfig.VERSION_CODE,
                localStatus = localStatus,
                passcodeEpoch = localEpoch,
                localRoleUpdatedAtMs = localRoleUpdatedAtMs,
                localLinkedMemberId = localMemberId,
                localLinkedMemberName = localMemberName,
                localMemberLinkUpdatedAtMs = localMemberLinkTs
            )

            val statusAfterRemote = if (remoteReg != null) {
                SupabaseService.resolveDeviceAccessStatus(remoteReg.status, localStatus)
            } else {
                localStatus
            }.let { status ->
                if (status == "ACTIVE" && localEpoch < policy.passcodeEpoch) "UNVERIFIED" else status
            }

            val (resolvedRole, resolvedRoleUpdatedAtMs) = if (remoteReg != null) {
                SupabaseService.resolveDeviceRole(
                    remoteRole = remoteReg.appRole,
                    remoteRoleUpdatedAtMs = remoteReg.roleUpdatedAtMs,
                    localRole = currentRole,
                    localRoleUpdatedAtMs = localRoleUpdatedAtMs
                )
            } else {
                currentRole to localRoleUpdatedAtMs
            }

            val (resolvedMemberId, resolvedMemberName, resolvedMemberLinkTs) = if (remoteReg != null) {
                SupabaseService.resolveDeviceMemberLink(
                    remoteMemberId = remoteReg.linkedMemberId,
                    remoteMemberName = remoteReg.linkedMemberName,
                    remoteUpdatedAtMs = remoteReg.memberLinkUpdatedAtMs,
                    localMemberId = localMemberId,
                    localMemberName = localMemberName,
                    localUpdatedAtMs = localMemberLinkTs
                )
            } else {
                Triple(localMemberId, localMemberName, localMemberLinkTs)
            }

            val rosterSnapshot = staffRoster.value
            val linkedMember = resolvedMemberId.takeIf { it.isNotBlank() }?.let { mid ->
                rosterSnapshot.firstOrNull { it.id == mid }
            }
            val (effectiveRole, effectiveStatus) = SupabaseService.enforceMemberRosterPolicy(
                currentRole = resolvedRole,
                currentStatus = statusAfterRemote,
                linkedMember = linkedMember
            )

            if (!remoteWebhook.isNullOrBlank() && remoteWebhook != _uiState.value.googleSheetsWebhookUrl) {
                prefs.edit().putString("sheets_webhook_url", remoteWebhook).apply()
            }
            if (!remoteSheetUrl.isNullOrBlank() && remoteSheetUrl != _uiState.value.googleSheetsUrl) {
                prefs.edit().putString("google_sheets_url", remoteSheetUrl).apply()
            }
            if (remoteThresholdPct != null && kotlin.math.abs(remoteThresholdPct - _uiState.value.priceAlertThresholdPct) > 0.01) {
                prefs.edit().putString("price_alert_threshold_pct", remoteThresholdPct.toString()).apply()
            }

            if (effectiveStatus != localStatus || effectiveRole != currentRole ||
                resolvedRoleUpdatedAtMs != localRoleUpdatedAtMs ||
                resolvedMemberId != localMemberId || resolvedMemberName != localMemberName ||
                resolvedMemberLinkTs != localMemberLinkTs
            ) {
                prefs.edit()
                    .putString("device_access_status", effectiveStatus)
                    .putString("authenticated_role", effectiveRole)
                    .putLong("device_role_updated_at_ms", resolvedRoleUpdatedAtMs)
                    .putString("linked_member_id", resolvedMemberId)
                    .putString("linked_member_name", resolvedMemberName)
                    .putLong("device_member_link_updated_at_ms", resolvedMemberLinkTs)
                    .apply()
            }

            val roleNotice = when {
                effectiveRole != currentRole && effectiveRole == "STAFF" ->
                    "Role changed to Staff Mode by Store Owner"
                effectiveRole != currentRole && effectiveRole == "ADMIN" ->
                    "Role updated to Admin Mode by Store Owner"
                else -> null
            }

            _uiState.update {
                val updated = it.copy(
                    deviceAccessStatus = effectiveStatus,
                    accessPolicyConfig = sanitizedPolicyForRole(policy, effectiveRole),
                    currentRole = effectiveRole,
                    linkedMemberId = resolvedMemberId,
                    linkedMemberName = resolvedMemberName,
                    googleSheetsWebhookUrl = remoteWebhook ?: it.googleSheetsWebhookUrl,
                    googleSheetsUrl = remoteSheetUrl ?: it.googleSheetsUrl,
                    priceAlertThresholdPct = remoteThresholdPct ?: it.priceAlertThresholdPct,
                    snackbarMessage = roleNotice ?: it.snackbarMessage
                )
                if (effectiveStatus != "ACTIVE" || effectiveRole != "ADMIN") {
                    updated.resetRestrictedStateForRole(effectiveRole)
                } else {
                    updated
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncDeviceAccessHeartbeat skipped (offline?): ${e.message}")
        }
    }

    fun verifyDeviceAccess(operatorName: String, passcode: String) {
        if (operatorName.isBlank() || passcode.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingAccess = true, accessGateError = "") }
            try {
                val supabase = SupabaseService.getInstance()
                val policy = withContext(Dispatchers.IO) { supabase.fetchAccessPolicyConfig() }
                val cleanCode = passcode.trim()

                val (authenticatedRole, targetStatus) = when {
                    cleanCode.equals(policy.adminPasscode.trim(), ignoreCase = false) -> {
                        val status = if (policy.requireOwnerApprovalForAdmin) "PENDING" else "ACTIVE"
                        Pair("ADMIN", status)
                    }
                    cleanCode.equals(policy.staffPasscode.trim(), ignoreCase = false) -> {
                        Pair("STAFF", "ACTIVE")
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                isVerifyingAccess = false,
                                accessGateError = "Incorrect passcode. Please check with the Store Owner."
                            )
                        }
                        return@launch
                    }
                }

                val cleanOperator = operatorName.trim()
                val matchedStaffMember = if (authenticatedRole == "STAFF") {
                    val normKey = AttendanceCalculator.normalizeStaffKey(cleanOperator)
                    staffRoster.value.firstOrNull {
                        it.isActive && !it.orgRole.equals("ADMIN", ignoreCase = true) &&
                            (it.id == normKey || AttendanceCalculator.normalizeStaffKey(it.name) == normKey)
                    }
                } else {
                    null
                }
                val linkMemberId = matchedStaffMember?.id ?: _uiState.value.linkedMemberId
                val linkMemberName = matchedStaffMember?.name ?: _uiState.value.linkedMemberName
                val roleTs = System.currentTimeMillis()
                val remoteReg = withContext(Dispatchers.IO) {
                    supabase.syncDeviceHeartbeat(
                        deviceId = currentDeviceId,
                        appRole = authenticatedRole,
                        operatorName = cleanOperator,
                        deviceModel = currentDeviceModel,
                        androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        appVersionName = BuildConfig.VERSION_NAME,
                        appVersionCode = BuildConfig.VERSION_CODE,
                        localStatus = targetStatus,
                        passcodeEpoch = policy.passcodeEpoch,
                        localRoleUpdatedAtMs = roleTs,
                        localLinkedMemberId = linkMemberId,
                        localLinkedMemberName = linkMemberName,
                        localMemberLinkUpdatedAtMs = if (matchedStaffMember != null) roleTs else 0L
                    )
                }

                val finalStatus = when (remoteReg?.status) {
                    "REVOKED" -> "REVOKED"
                    "PENDING" -> "PENDING"
                    else -> targetStatus
                }
                val resolvedLinkedId = remoteReg?.linkedMemberId?.takeIf { it.isNotBlank() } ?: linkMemberId
                val resolvedLinkedName = remoteReg?.linkedMemberName?.takeIf { it.isNotBlank() } ?: linkMemberName
                prefs.edit()
                    .putString("device_access_status", finalStatus)
                    .putString("device_operator_name", cleanOperator)
                    .putString("authenticated_role", authenticatedRole)
                    .putInt("device_passcode_epoch", policy.passcodeEpoch)
                    .putLong("device_role_updated_at_ms", roleTs)
                    .putString("linked_member_id", resolvedLinkedId)
                    .putString("linked_member_name", resolvedLinkedName)
                    .apply()

                _uiState.update {
                    val base = it.copy(
                        isVerifyingAccess = false,
                        deviceAccessStatus = finalStatus,
                        operatorName = cleanOperator,
                        currentRole = authenticatedRole,
                        linkedMemberId = resolvedLinkedId,
                        linkedMemberName = resolvedLinkedName,
                        accessGateError = "",
                        accessPolicyConfig = sanitizedPolicyForRole(policy, authenticatedRole)
                    )
                    base.resetRestrictedStateForRole(authenticatedRole)
                }

                // Auto-record attendance check-in when a Staff mobile logs in
                if (authenticatedRole == "STAFF" && finalStatus == "ACTIVE") {
                    val attendanceName = resolvedLinkedName.ifBlank { cleanOperator }
                    if (AttendanceCalculator.isRealStaffName(attendanceName)) {
                        withContext(Dispatchers.IO) {
                            refreshDateKeyIfNeeded()
                            val dateKey = _activeDateKey.value
                            repository.recordStaffAutoLoginAttendance(
                                dateKey = dateKey,
                                monthKey = dateKey.take(7),
                                staffName = attendanceName,
                                deviceId = currentDeviceId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isVerifyingAccess = false,
                        accessGateError = "Verification failed: ${e.message ?: "Check internet connection"}"
                    )
                }
            }
        }
    }

    fun elevateToAdmin(
        passcode: String,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val cleanCode = passcode.trim()
        if (cleanCode.isBlank()) {
            onResult(false, "Please enter the Admin Passcode")
            return
        }
        viewModelScope.launch {
            try {
                val supabase = SupabaseService.getInstance()
                val policy = withContext(Dispatchers.IO) { supabase.fetchAccessPolicyConfig() }

                if (!cleanCode.equals(policy.adminPasscode.trim(), ignoreCase = false)) {
                    onResult(false, "Incorrect Admin Passcode")
                    return@launch
                }

                val linkedId = _uiState.value.linkedMemberId.ifBlank { prefs.getString("linked_member_id", "").orEmpty() }
                val linkedStaff = linkedId.takeIf { it.isNotBlank() }?.let { id ->
                    staffRoster.value.firstOrNull { it.id == id && !it.orgRole.equals("ADMIN", ignoreCase = true) }
                }
                if (linkedStaff != null) {
                    onResult(false, "This mobile is assigned to Staff (${linkedStaff.name}). Ask the Store Owner for Admin access.")
                    return@launch
                }

                val targetStatus = if (policy.requireOwnerApprovalForAdmin) "PENDING" else "ACTIVE"
                val roleTs = System.currentTimeMillis()
                val remoteReg = withContext(Dispatchers.IO) {
                    supabase.syncDeviceHeartbeat(
                        deviceId = currentDeviceId,
                        appRole = "ADMIN",
                        operatorName = _uiState.value.operatorName,
                        deviceModel = currentDeviceModel,
                        androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        appVersionName = BuildConfig.VERSION_NAME,
                        appVersionCode = BuildConfig.VERSION_CODE,
                        localStatus = targetStatus,
                        passcodeEpoch = policy.passcodeEpoch,
                        localRoleUpdatedAtMs = roleTs
                    )
                }

                val finalStatus = when (remoteReg?.status) {
                    "REVOKED" -> "REVOKED"
                    "PENDING" -> "PENDING"
                    else -> targetStatus
                }

                if (finalStatus == "REVOKED") {
                    prefs.edit()
                        .putString("device_access_status", "REVOKED")
                        .putString("authenticated_role", "STAFF")
                        .putLong("device_role_updated_at_ms", roleTs)
                        .apply()
                    _uiState.update {
                        it.copy(
                            deviceAccessStatus = "REVOKED",
                            currentRole = "STAFF"
                        ).resetRestrictedStateForRole("STAFF")
                    }
                    onResult(false, "Device access has been revoked by Owner")
                    return@launch
                }

                prefs.edit()
                    .putString("authenticated_role", "ADMIN")
                    .putString("device_access_status", finalStatus)
                    .putInt("device_passcode_epoch", policy.passcodeEpoch)
                    .putLong("device_role_updated_at_ms", roleTs)
                    .apply()

                _uiState.update {
                    val next = it.copy(
                        currentRole = "ADMIN",
                        deviceAccessStatus = finalStatus,
                        accessPolicyConfig = sanitizedPolicyForRole(policy, "ADMIN"),
                        snackbarMessage = if (finalStatus == "ACTIVE") {
                            "Elevated to Admin Mode"
                        } else {
                            "Admin access requires Owner approval — pending confirmation"
                        }
                    )
                    if (finalStatus != "ACTIVE") next.resetRestrictedStateForRole("STAFF") else next
                }
                onResult(true, if (finalStatus == "ACTIVE") "Elevated to Admin Mode" else "Pending Owner approval")
            } catch (e: Exception) {
                Log.w(TAG, "Admin elevation failed: ${e.message}")
                onResult(false, "Internet connection required to verify Admin passcode")
            }
        }
    }

    fun lockToStaffRole() {
        val roleTs = System.currentTimeMillis()
        prefs.edit()
            .putString("authenticated_role", "STAFF")
            .putLong("device_role_updated_at_ms", roleTs)
            .apply()
        _uiState.update {
            it.copy(
                currentRole = "STAFF",
                snackbarMessage = "Locked to Staff Mode"
            ).resetRestrictedStateForRole("STAFF")
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                SupabaseService.getInstance().syncDeviceHeartbeat(
                    deviceId = currentDeviceId,
                    appRole = "STAFF",
                    operatorName = _uiState.value.operatorName,
                    deviceModel = currentDeviceModel,
                    androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    localStatus = _uiState.value.deviceAccessStatus,
                    passcodeEpoch = _uiState.value.accessPolicyConfig.passcodeEpoch,
                    localRoleUpdatedAtMs = roleTs
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync staff lock heartbeat: ${e.message}")
            }
        }
    }

    fun logOutDevice() {
        val prevRole = _uiState.value.currentRole
        val prevStaffName = _uiState.value.linkedMemberName.ifBlank { _uiState.value.operatorName }.trim()
        val roleTs = System.currentTimeMillis()
        prefs.edit()
            .putString("device_access_status", "UNVERIFIED")
            .putString("authenticated_role", "STAFF")
            .putLong("device_role_updated_at_ms", roleTs)
            .apply()
        _uiState.update {
            it.copy(
                deviceAccessStatus = "UNVERIFIED",
                currentRole = "STAFF",
                manualInventoryDraftCounts = emptyMap(),
                manualInventoryDraftNotes = "",
                snackbarMessage = "Device signed out"
            ).resetRestrictedStateForRole("STAFF")
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (prevRole == "STAFF" && AttendanceCalculator.isRealStaffName(prevStaffName)) {
                    refreshDateKeyIfNeeded()
                    repository.recordStaffAutoLogoutAttendance(
                        dateKey = _activeDateKey.value,
                        staffName = prevStaffName
                    )
                }
                SupabaseService.getInstance().syncDeviceHeartbeat(
                    deviceId = currentDeviceId,
                    appRole = "STAFF",
                    operatorName = _uiState.value.operatorName,
                    deviceModel = currentDeviceModel,
                    androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    localStatus = "UNVERIFIED",
                    passcodeEpoch = _uiState.value.accessPolicyConfig.passcodeEpoch,
                    localRoleUpdatedAtMs = roleTs
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync logout heartbeat: ${e.message}")
            }
        }
    }

    fun refreshDeviceAccessStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVerifyingAccess = true, accessGateError = "") }
            withContext(Dispatchers.IO) {
                syncDeviceAccessHeartbeat()
            }
            _uiState.update { it.copy(isVerifyingAccess = false) }
        }
    }

    fun openActiveDevicesDialog() {
        if (!requireAdmin("Device access management")) return
        _uiState.update { it.copy(showActiveDevicesDialog = true) }
        refreshRegisteredDevices()
    }

    fun dismissActiveDevicesDialog() {
        _uiState.update { it.copy(showActiveDevicesDialog = false) }
    }

    fun refreshRegisteredDevices() {
        if (!requireAdmin("Device access management")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            try {
                val supabase = SupabaseService.getInstance()
                val devices = withContext(Dispatchers.IO) { supabase.fetchAllRegisteredDevices() }
                val policy = withContext(Dispatchers.IO) { supabase.fetchAccessPolicyConfig() }
                _uiState.update {
                    it.copy(
                        isLoadingDevices = false,
                        registeredDevices = devices,
                        accessPolicyConfig = policy
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingDevices = false,
                        snackbarMessage = "Failed to load devices: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateRemoteDeviceStatus(registryKey: String, newStatus: String) {
        if (!requireAdmin("Updating device status")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            try {
                val res = withContext(Dispatchers.IO) {
                    SupabaseService.getInstance().setDeviceAccessStatus(registryKey, newStatus)
                }
                if (res.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = "Device access updated to $newStatus") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Failed: ${res.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed: ${e.message}") }
            }
            refreshRegisteredDevices()
        }
    }

    fun updateRemoteDeviceRole(registryKey: String, newRole: String) {
        if (!requireAdmin("Updating device role")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            try {
                val res = withContext(Dispatchers.IO) {
                    SupabaseService.getInstance().setDeviceRole(registryKey, newRole)
                }
                if (res.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = "Device role updated to $newRole") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Failed: ${res.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed: ${e.message}") }
            }
            refreshRegisteredDevices()
        }
    }

    fun linkDeviceToMember(registryKey: String, member: StaffRosterEntity) {
        if (!requireAdmin("Linking device to team member")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            try {
                val targetRole = if (member.orgRole.equals("ADMIN", ignoreCase = true)) "ADMIN" else "STAFF"
                val res = withContext(Dispatchers.IO) {
                    SupabaseService.getInstance().setDeviceMemberLink(
                        registryKey = registryKey,
                        memberId = member.id,
                        memberName = member.name,
                        memberOrgRole = targetRole
                    )
                }
                if (res.isSuccess) {
                    _uiState.update { it.copy(snackbarMessage = "Device linked to ${member.name} ($targetRole)") }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Failed to link device: ${res.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to link device: ${e.message}") }
            }
            refreshRegisteredDevices()
        }
    }

    fun createAndLinkRosterMemberForDevice(registryKey: String, name: String) {
        if (!requireAdmin("Adding staff member & linking device")) return
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingDevices = true) }
            try {
                val memberId = AttendanceCalculator.normalizeStaffKey(cleanName)
                val member = StaffRosterEntity(
                    id = memberId,
                    name = cleanName,
                    roleTitle = "Barista",
                    shiftLabel = "Full Day",
                    joinedDateKey = _activeDateKey.value,
                    isActive = true,
                    updatedAt = System.currentTimeMillis(),
                    colorHex = KdsBaristaColorPalette.resolveColorHex(cleanName),
                    orgRole = "STAFF"
                )
                withContext(Dispatchers.IO) {
                    repository.saveStaffRosterMember(member)
                    SupabaseService.getInstance().setDeviceMemberLink(
                        registryKey = registryKey,
                        memberId = member.id,
                        memberName = member.name,
                        memberOrgRole = "STAFF"
                    )
                }
                _uiState.update { it.copy(snackbarMessage = "Added ${member.name} to Staff Roster & linked device") }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed: ${e.message}") }
            }
            refreshRegisteredDevices()
        }
    }

    fun saveAccessPolicy(
        adminPasscode: String,
        staffPasscode: String,
        requireApproval: Boolean,
        forceReverifyAll: Boolean
    ) {
        if (!requireAdmin("Saving access policy")) return
        viewModelScope.launch {
            try {
                val current = _uiState.value.accessPolicyConfig
                val nextEpoch = if (forceReverifyAll) current.passcodeEpoch + 1 else current.passcodeEpoch
                val updated = SupabaseService.AccessPolicyConfig(
                    adminPasscode = adminPasscode,
                    staffPasscode = staffPasscode,
                    passcodeEpoch = nextEpoch,
                    requireOwnerApprovalForAdmin = requireApproval
                )
                val res = withContext(Dispatchers.IO) {
                    SupabaseService.getInstance().saveAccessPolicyConfig(updated)
                }
                if (res.isSuccess) {
                    // Keep this Admin phone authenticated on the new epoch automatically
                    prefs.edit().putInt("device_passcode_epoch", nextEpoch).apply()
                    _uiState.update {
                        it.copy(
                            accessPolicyConfig = updated,
                            snackbarMessage = if (forceReverifyAll)
                                "Passcodes saved! All other devices must re-verify."
                            else
                                "Access policy saved!"
                        )
                    }
                } else {
                    _uiState.update { it.copy(snackbarMessage = "Failed to save policy: ${res.exceptionOrNull()?.message}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to save policy: ${e.message}") }
            }
        }
    }

    // ==========================================
    // SMART DAILY KITCHEN INVENTORY ACTIONS
    // ==========================================

    private suspend fun refreshPreviousClosingReferenceInternal(dateKey: String) {
        try {
            val (prevShift, prevEntries) = repository.getPreviousClosingReference(dateKey)
            _previousClosingShift.value = prevShift
            _previousClosingEntries.value = prevEntries
        } catch (e: Exception) {
            Log.w(TAG, "refreshPreviousClosingReference failed: ${e.message}")
        }
    }

    fun navigateKitchenRoute(route: KitchenInventoryViewRoute) {
        if (route is KitchenInventoryViewRoute.ManagerHub && !requireAdmin("Manager Kitchen Inventory")) {
            return
        }
        _kitchenRoute.value = route
        if (route is KitchenInventoryViewRoute.ShiftOverview || route is KitchenInventoryViewRoute.SectionCounting) {
            viewModelScope.launch(Dispatchers.IO) {
                refreshPreviousClosingReferenceInternal(_activeDateKey.value)
            }
        }
    }

    fun startOrResumeKitchenShift(shiftType: KitchenShiftType) {
        if (!ensureAppAllowed()) return
        refreshDateKeyIfNeeded()
        val dateKey = _activeDateKey.value
        val operator = _uiState.value.operatorName.ifBlank { _uiState.value.currentRole }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val shift = repository.ensureKitchenShiftStarted(dateKey, shiftType, operator)
                refreshPreviousClosingReferenceInternal(dateKey)
                _kitchenRoute.value = if (shift.status == "SUBMITTED") {
                    KitchenInventoryViewRoute.ShiftSubmittedConfirmation(shiftType)
                } else {
                    KitchenInventoryViewRoute.ShiftOverview(shiftType)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Could not open ${shiftType.label}: ${e.message}") }
            }
        }
    }

    fun openFirstUncheckedKitchenItem(shiftType: KitchenShiftType, sectionIdFilter: String? = null) {
        if (!ensureAppAllowed()) return
        val items = kitchenActiveItems.value
            .filter { sectionIdFilter == null || it.sectionId == sectionIdFilter }
            .sortedWith(compareBy<KitchenInventoryItemEntity> {
                KitchenSection.fromId(it.sectionId).orderIndex
            }.thenBy { it.orderIndex })
        if (items.isEmpty()) {
            _uiState.update { it.copy(snackbarMessage = "No items configured in this section yet") }
            return
        }
        val checkedIds = kitchenEntriesToday.value
            .filter { it.shiftType == shiftType.id && it.isChecked }
            .map { it.itemId }
            .toSet()
        val targetItem = items.firstOrNull { it.id !in checkedIds } ?: items.first()
        navigateKitchenRoute(
            KitchenInventoryViewRoute.SectionCounting(
                shiftType = shiftType,
                sectionId = sectionIdFilter ?: targetItem.sectionId,
                selectedItemId = targetItem.id
            )
        )
    }

    fun saveKitchenItemAndAdvance(
        shiftType: KitchenShiftType,
        item: KitchenInventoryItemEntity,
        sealedQty: Int,
        openedQty: Int,
        openedRemainingQty: Double,
        wastageQty: Double,
        wastageReason: String,
        nextItemId: String?,
        nextSectionId: String?
    ) {
        if (!ensureAppAllowed()) return
        val dateKey = _activeDateKey.value
        val operator = _uiState.value.operatorName.ifBlank { _uiState.value.currentRole }
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.saveKitchenItemEntry(
                dateKey = dateKey,
                shiftType = shiftType,
                item = item,
                sealedQty = sealedQty,
                openedQty = openedQty,
                openedRemainingQty = openedRemainingQty,
                wastageQty = wastageQty,
                wastageReason = wastageReason,
                checkedBy = operator
            )
            res.onSuccess {
                if (nextItemId != null) {
                    _kitchenRoute.value = KitchenInventoryViewRoute.SectionCounting(
                        shiftType = shiftType,
                        sectionId = nextSectionId ?: item.sectionId,
                        selectedItemId = nextItemId
                    )
                } else {
                    _kitchenRoute.value = KitchenInventoryViewRoute.ShiftOverview(shiftType)
                    _uiState.update { it.copy(snackbarMessage = "${item.name} saved! Ready to submit ${shiftType.label}.") }
                }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Failed to save item") }
            }
        }
    }

    fun submitKitchenShift(shiftType: KitchenShiftType, notes: String = "") {
        if (!ensureAppAllowed()) return
        val dateKey = _activeDateKey.value
        val operator = _uiState.value.operatorName.ifBlank { _uiState.value.currentRole }
        viewModelScope.launch(Dispatchers.IO) {
            val res = repository.submitKitchenShift(dateKey, shiftType, operator, notes)
            res.onSuccess {
                _kitchenRoute.value = KitchenInventoryViewRoute.ShiftSubmittedConfirmation(shiftType)
                _uiState.update { it.copy(snackbarMessage = "${shiftType.label} submitted by $operator") }
            }.onFailure { err ->
                _uiState.update { it.copy(snackbarMessage = err.message ?: "Already submitted") }
            }
        }
    }

    fun saveKitchenCatalogItemConfig(
        existingId: String?,
        name: String,
        iconEmoji: String,
        sectionId: String,
        countingMode: String,
        unitLabel: String,
        containerLabel: String,
        packSize: Double,
        minStockThreshold: Double,
        targetStockLevel: Double,
        isActive: Boolean
    ) {
        if (!requireAdmin("Configuring kitchen inventory items")) return
        if (name.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Item name cannot be empty") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val existing = existingId?.let { id -> kitchenAllItems.value.firstOrNull { it.id == id } }
            val nextOrder = existing?.orderIndex ?: ((kitchenAllItems.value.filter { it.sectionId == sectionId }.maxOfOrNull { it.orderIndex } ?: 0) + 1)
            val entity = KitchenInventoryItemEntity(
                id = existing?.id ?: "custom_k_${System.currentTimeMillis()}",
                name = name.trim(),
                iconEmoji = iconEmoji.trim().ifBlank { "📦" },
                sectionId = sectionId,
                countingMode = countingMode,
                unitLabel = unitLabel.trim().ifBlank { "pcs" },
                containerLabel = containerLabel.trim(),
                packSize = if (packSize > 0.0) packSize else 1.0,
                minStockThreshold = minStockThreshold.coerceAtLeast(0.0),
                targetStockLevel = targetStockLevel.coerceAtLeast(minStockThreshold.coerceAtLeast(0.0)),
                orderIndex = nextOrder,
                isDefault = existing?.isDefault ?: false,
                isActive = isActive
            )
            repository.saveKitchenCatalogItem(entity)
                .onSuccess {
                    _uiState.update { it.copy(snackbarMessage = "${entity.name} configuration saved") }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(snackbarMessage = "Failed to save item: ${err.message}") }
                }
        }
    }

    fun refreshKitchenInventory() {
        refreshDateKeyIfNeeded()
        val key = _activeDateKey.value
        viewModelScope.launch(Dispatchers.IO) {
            repository.reconcileKitchenInventoryFromCloud(key)
            refreshPreviousClosingReferenceInternal(key)
        }
    }

    // ==========================================
    // STAFF ATTENDANCE ACTIONS
    // ==========================================

    private suspend fun reconcileAttendanceStoreConfigFromCloud() {
        try {
            val cloud = repository.fetchAttendanceStoreConfig()
            val local = _storeGeofenceConfig.value
            if (cloud != null) {
                if (cloud.updatedAt > local.updatedAt || (!local.isConfigured && cloud.isConfigured)) {
                    persistStoreGeofenceConfig(cloud)
                    _storeGeofenceConfig.value = cloud
                } else if (local.isConfigured && local.updatedAt > cloud.updatedAt) {
                    repository.saveAttendanceStoreConfig(local)
                }
            } else if (local.isConfigured) {
                repository.saveAttendanceStoreConfig(local)
            }
        } catch (e: Exception) {
            Log.w(TAG, "reconcileAttendanceStoreConfigFromCloud failed: ${e.message}")
        }
    }

    fun refreshAttendanceLocationAndSync() {
        refreshDateKeyIfNeeded()
        val permitted = KitchenLocationHelper.hasLocationPermission(getApplication())
        _hasLocationPermission.value = permitted

        viewModelScope.launch {
            if (permitted) {
                _isFetchingLocation.value = true
                _attendanceLocationError.value = null
                val locResult = KitchenLocationHelper.fetchCurrentLocation(getApplication())
                _isFetchingLocation.value = false
                locResult.onSuccess { sample ->
                    _currentLocationSample.value = sample
                    _attendanceLocationError.value = null
                }.onFailure { err ->
                    _attendanceLocationError.value = err.message ?: "Unable to fetch GPS location"
                }
            } else {
                _isFetchingLocation.value = false
                _attendanceLocationError.value = null
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            reconcileAttendanceStoreConfigFromCloud()
            val selectedMonth = _selectedAttendanceMonthKey.value
            val todayMonth = _activeDateKey.value.substring(0, 7)
            repository.reconcileAttendanceFromCloud(selectedMonth)
            if (todayMonth != selectedMonth) {
                repository.reconcileAttendanceFromCloud(todayMonth)
            }
            repository.reconcileStaffRosterFromCloud()
        }
    }

    fun onLocationPermissionResult() {
        _hasLocationPermission.value = KitchenLocationHelper.hasLocationPermission(getApplication())
        refreshAttendanceLocationAndSync()
    }

    fun setStoreLocationFromCurrentGps() {
        if (!requireAdmin("Setting store GPS location")) return
        val permitted = KitchenLocationHelper.hasLocationPermission(getApplication())
        _hasLocationPermission.value = permitted
        if (!permitted) {
            _uiState.update { it.copy(snackbarMessage = "Location permission is required to set store GPS location.") }
            return
        }

        viewModelScope.launch {
            _isFetchingLocation.value = true
            _attendanceLocationError.value = null
            val locResult = KitchenLocationHelper.fetchCurrentLocation(getApplication())
            _isFetchingLocation.value = false
            locResult.onSuccess { sample ->
                _currentLocationSample.value = sample
                val now = System.currentTimeMillis()
                val current = _storeGeofenceConfig.value
                val updated = StoreGeofenceConfig(
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    radiusMeters = current.radiusMeters,
                    storeLabel = "FILTR Coffee",
                    updatedAt = now
                )
                persistStoreGeofenceConfig(updated)
                _storeGeofenceConfig.value = updated

                val saveRes = withContext(Dispatchers.IO) {
                    repository.saveAttendanceStoreConfig(updated)
                }
                val coords = String.format(Locale.US, "%.5f, %.5f", sample.latitude, sample.longitude)
                val msg = if (saveRes.isSuccess) {
                    "Store GPS location saved ($coords)"
                } else {
                    "Store GPS saved locally ($coords); cloud sync pending"
                }
                _uiState.update { it.copy(snackbarMessage = msg) }
            }.onFailure { err ->
                val msg = err.message ?: "Failed to obtain current GPS fix"
                _attendanceLocationError.value = msg
                _uiState.update { it.copy(snackbarMessage = msg) }
            }
        }
    }

    fun updateStoreRadiusMeters(radiusMeters: Double) {
        if (!requireAdmin("Updating geofence radius")) return
        val safeRadius = radiusMeters.coerceAtLeast(10.0)
        val now = System.currentTimeMillis()
        val updated = _storeGeofenceConfig.value.copy(
            radiusMeters = safeRadius,
            updatedAt = now
        )
        persistStoreGeofenceConfig(updated)
        _storeGeofenceConfig.value = updated

        viewModelScope.launch(Dispatchers.IO) {
            val saveRes = repository.saveAttendanceStoreConfig(updated)
            val msg = if (saveRes.isSuccess) {
                "Geofence radius updated to ${safeRadius.toInt()}m"
            } else {
                "Radius updated locally to ${safeRadius.toInt()}m; cloud sync pending"
            }
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun performStaffCheckIn() {
        if (!ensureAppAllowed()) return
        refreshDateKeyIfNeeded()
        val permitted = KitchenLocationHelper.hasLocationPermission(getApplication())
        _hasLocationPermission.value = permitted
        if (!permitted) {
            _uiState.update { it.copy(snackbarMessage = "Location permission is required for check-in.") }
            return
        }

        viewModelScope.launch {
            _isFetchingLocation.value = true
            _attendanceLocationError.value = null
            val locResult = KitchenLocationHelper.fetchCurrentLocation(getApplication())
            _isFetchingLocation.value = false
            val sample = locResult.getOrElse { err ->
                val msg = err.message ?: "Unable to fetch GPS location for check-in"
                _attendanceLocationError.value = msg
                _uiState.update { it.copy(snackbarMessage = msg) }
                return@launch
            }
            _currentLocationSample.value = sample

            val dateKey = _activeDateKey.value
            val monthKey = dateKey.substring(0, 7)
            val staffName = resolveActiveCheckInStaffName()
            val config = _storeGeofenceConfig.value

            val res = withContext(Dispatchers.IO) {
                repository.recordStaffCheckIn(
                    dateKey = dateKey,
                    monthKey = monthKey,
                    staffName = staffName,
                    deviceId = currentDeviceId,
                    userLat = sample.latitude,
                    userLng = sample.longitude,
                    config = config
                )
            }
            res.onSuccess { log ->
                _uiState.update {
                    it.copy(snackbarMessage = "${log.staffName} checked in! (${log.checkInDistanceMeters.toInt()}m from store)")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.message ?: "Check-in failed")
                }
            }
        }
    }

    fun performStaffCheckOut() {
        if (!ensureAppAllowed()) return
        refreshDateKeyIfNeeded()
        val permitted = KitchenLocationHelper.hasLocationPermission(getApplication())
        _hasLocationPermission.value = permitted
        if (!permitted) {
            _uiState.update { it.copy(snackbarMessage = "Location permission is required for check-out.") }
            return
        }

        viewModelScope.launch {
            _isFetchingLocation.value = true
            _attendanceLocationError.value = null
            val locResult = KitchenLocationHelper.fetchCurrentLocation(getApplication())
            _isFetchingLocation.value = false
            val sample = locResult.getOrElse { err ->
                val msg = err.message ?: "Unable to fetch GPS location for check-out"
                _attendanceLocationError.value = msg
                _uiState.update { it.copy(snackbarMessage = msg) }
                return@launch
            }
            _currentLocationSample.value = sample

            val dateKey = _activeDateKey.value
            val staffName = resolveActiveCheckInStaffName()
            val config = _storeGeofenceConfig.value

            val res = withContext(Dispatchers.IO) {
                repository.recordStaffCheckOut(
                    dateKey = dateKey,
                    staffName = staffName,
                    userLat = sample.latitude,
                    userLng = sample.longitude,
                    config = config
                )
            }
            res.onSuccess { log ->
                val duration = AttendanceCalculator.formatWorkedDuration(log.workedMinutes)
                _uiState.update {
                    it.copy(snackbarMessage = "${log.staffName} checked out! Worked $duration today")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.message ?: "Check-out failed")
                }
            }
        }
    }

    private fun resolveActiveCheckInStaffName(): String {
        val explicit = _selectedCheckInStaffName.value.trim()
        val available = availableAttendanceStaffNames.value
        if (explicit.isNotBlank()) {
            return available.firstOrNull { it.equals(explicit, ignoreCase = true) } ?: explicit
        }
        val operator = _uiState.value.operatorName.trim().ifBlank { "Staff" }
        return available.firstOrNull { it.equals(operator, ignoreCase = true) } ?: operator
    }

    fun selectCheckInStaff(staffName: String) {
        _selectedCheckInStaffName.value = staffName.trim()
    }

    fun saveAdminManualAttendance(
        dateKey: String,
        staffName: String,
        checkInTimestamp: Long,
        checkOutTimestamp: Long,
        notes: String
    ) {
        if (!requireAdmin("Editing staff attendance")) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveAdminManualAttendance(
                dateKey = dateKey,
                staffName = staffName,
                checkInTimestamp = checkInTimestamp,
                checkOutTimestamp = checkOutTimestamp,
                notes = notes
            ).onSuccess {
                _uiState.update {
                    it.copy(snackbarMessage = "Updated $staffName attendance for $dateKey")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.message ?: "Failed to update attendance")
                }
            }
        }
    }

    fun saveStaffRosterMember(
        existingId: String?,
        name: String,
        roleTitle: String,
        shiftLabel: String,
        weeklyOffDayOfWeek: Int,
        joinedDateKey: String,
        isActive: Boolean,
        colorHex: String = ""
    ) {
        if (!requireAdmin("Managing staff roster")) return
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Staff name cannot be blank.") }
            return
        }
        val cleanExistingId = existingId?.trim()?.takeIf { it.isNotBlank() }
        val effectiveJoinedDateKey = joinedDateKey.trim().ifBlank { _activeDateKey.value }
        val safeWeeklyOff = if (weeklyOffDayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY) {
            weeklyOffDayOfWeek
        } else {
            0
        }

        viewModelScope.launch(Dispatchers.IO) {
            val currentRoster = staffRoster.value.ifEmpty { repository.allStaffRoster.first() }
            val resolvedId = if (cleanExistingId != null) {
                cleanExistingId
            } else {
                val baseId = AttendanceCalculator.normalizeStaffKey(cleanName)
                if (currentRoster.any { it.id == baseId }) {
                    "${baseId}_${System.currentTimeMillis()}"
                } else {
                    baseId
                }
            }
            val existingColor = cleanExistingId?.let { id ->
                currentRoster.firstOrNull { it.id == id }?.colorHex.orEmpty()
            }.orEmpty()
            val resolvedColor = KdsBaristaColorPalette.resolveColorHex(
                cleanName,
                colorHex.trim().ifBlank { existingColor }
            )
            val entity = StaffRosterEntity(
                id = resolvedId,
                name = cleanName,
                roleTitle = roleTitle.trim().ifBlank { "Barista" },
                shiftLabel = shiftLabel.trim().ifBlank { "Full Day" },
                weeklyOffDayOfWeek = safeWeeklyOff,
                joinedDateKey = effectiveJoinedDateKey,
                isActive = isActive,
                updatedAt = System.currentTimeMillis(),
                colorHex = resolvedColor
            )
            val result = repository.saveStaffRosterMember(entity)
            if (result.isSuccess) {
                SupabaseService.getInstance().cascadeMemberPolicyToLinkedDevices(entity)
            }
            result.onSuccess {
                val verb = if (cleanExistingId != null) "updated" else "added"
                _uiState.update {
                    it.copy(snackbarMessage = "${entity.name} $verb in staff roster.")
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(snackbarMessage = err.message ?: "Failed to save staff roster member.")
                }
            }
        }
    }

    fun selectAttendanceMonth(monthKey: String) {
        refreshDateKeyIfNeeded()
        _selectedAttendanceMonthKey.value = monthKey
        viewModelScope.launch(Dispatchers.IO) {
            repository.reconcileAttendanceFromCloud(monthKey)
        }
    }

    fun selectManagerAttendanceStaff(staffName: String) {
        _selectedManagerAttendanceStaff.value = staffName
    }

    // ==================== KDS QUEUE ACTIONS ====================

    fun selectKdsBarista(baristaName: String) {
        val clean = baristaName.trim()
        if (clean.isNotBlank()) {
            _selectedKdsBaristaName.value = clean
        }
    }

    fun setKdsPaidOnlyFilter(paidOnly: Boolean) {
        _kdsPaidOnlyFilter.value = paidOnly
    }

    fun setKdsTodayOnlyFilter(todayOnly: Boolean) {
        refreshDateKeyIfNeeded()
        _kdsTodayOnlyFilter.value = todayOnly
    }

    fun onKdsItemTap(unit: KdsQueueUnitItem) {
        if (!ensureAppAllowed()) return
        val activeBarista = selectedKdsBaristaName.value.trim().ifBlank {
            _uiState.value.operatorName.trim().ifBlank { "Barista" }
        }
        val rosterColor = staffRoster.value
            .firstOrNull { AttendanceCalculator.normalizeStaffKey(it.name) == AttendanceCalculator.normalizeStaffKey(activeBarista) }
            ?.colorHex.orEmpty()
        val baristaColor = KdsBaristaColorPalette.resolveColorHex(activeBarista, rosterColor)
        val isAdminOverride = _uiState.value.currentRole == "ADMIN"

        viewModelScope.launch {
            try {
                when (
                    val outcome = repository.handleKdsItemTap(
                        orderId = unit.orderId,
                        itemId = unit.itemId,
                        unitIndex = unit.unitIndex,
                        baristaName = activeBarista,
                        baristaColorHex = baristaColor,
                        isAdminOverride = isAdminOverride
                    )
                ) {
                    is FiltrRepository.KdsTapOutcome.Claimed -> {
                        // Immediate visual color highlight in KdsScreen; no noisy toast needed
                    }
                    is FiltrRepository.KdsTapOutcome.Completed -> {
                        val url = _uiState.value.googleSheetsWebhookUrl
                        if (url.isNotBlank() && outcome.updatedOrder != null) {
                            repository.syncOrderToGoogleSheets(url, outcome.updatedOrder)
                        }
                    }
                    is FiltrRepository.KdsTapOutcome.BlockedByOtherBarista -> {
                        _uiState.update {
                            it.copy(
                                snackbarMessage = "Assigned to ${outcome.claimedByStaff} (Long-press card to unclaim)"
                            )
                        }
                    }
                    FiltrRepository.KdsTapOutcome.Ignored -> Unit
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "KDS update failed: ${e.message}") }
            }
        }
    }

    fun onKdsItemUnclaim(unit: KdsQueueUnitItem) {
        if (!ensureAppAllowed()) return
        viewModelScope.launch {
            try {
                val unclaimed = repository.unclaimKdsItem(unit.orderId, unit.itemId, unit.unitIndex)
                if (unclaimed) {
                    _uiState.update {
                        it.copy(snackbarMessage = "Released ${unit.itemName} (#${unit.shortOrderNumber}) back to queue")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(snackbarMessage = "Failed to release item: ${e.message}") }
            }
        }
    }

    // ==================== RAW MATERIAL SHARE RECEIPT INTAKE ====================

    fun importSharedRawMaterialReceipt(
        attachments: List<Pair<ByteArray, String>>,
        sharedText: String,
        sourceLabel: String,
        existingQueueId: String? = null
    ) {
        val label = sourceLabel.trim().ifBlank { "Shared Receipt" }
        _uiState.update {
            it.copy(
                isParsingSharedReceipt = true,
                sharedReceiptSourceLabel = label
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.parseSharedRawMaterialReceipt(
                attachments = attachments,
                sharedText = sharedText,
                sourceLabel = label
            )
            result.onSuccess { parsed ->
                if (!existingQueueId.isNullOrBlank()) {
                    PendingReceiptQueueStore.remove(getApplication(), existingQueueId)
                }
                val refreshedQueue = PendingReceiptQueueStore.loadAll(getApplication())
                _uiState.update {
                    it.copy(
                        isParsingSharedReceipt = false,
                        parsedSharedReceipt = parsed,
                        pendingReceiptQueue = refreshedQueue,
                        activeRetryIntakeId = null
                    )
                }
            }.onFailure { err ->
                val errorMsg = err.message ?: "Could not auto-extract line items"
                val enqueued = if (attachments.isNotEmpty() || sharedText.isNotBlank()) {
                    PendingReceiptQueueStore.enqueueOrUpdate(
                        context = getApplication(),
                        attachments = attachments,
                        sharedText = sharedText,
                        sourceLabel = label,
                        lastError = errorMsg,
                        existingId = existingQueueId
                    )
                } else null
                val refreshedQueue = PendingReceiptQueueStore.loadAll(getApplication())
                _uiState.update {
                    it.copy(
                        isParsingSharedReceipt = false,
                        pendingReceiptQueue = refreshedQueue,
                        activeRetryIntakeId = enqueued?.id ?: existingQueueId,
                        parsedSharedReceipt = ParsedRawMaterialReceipt(
                            supplierName = "",
                            notes = buildString {
                                append("Imported from $label")
                                val txt = sharedText.trim()
                                if (txt.isNotBlank()) {
                                    append(" • ")
                                    append(txt.take(120))
                                }
                            },
                            items = emptyList(),
                            sourceLabel = label,
                            parseError = errorMsg
                        )
                    )
                }
            }
        }
    }

    fun retryQueuedReceipt(intakeId: String) {
        val queue = PendingReceiptQueueStore.loadAll(getApplication())
        val intake = queue.firstOrNull { it.id == intakeId } ?: return
        val attachments = PendingReceiptQueueStore.loadAttachmentBytes(getApplication(), intake)
        importSharedRawMaterialReceipt(
            attachments = attachments,
            sharedText = intake.sharedText,
            sourceLabel = intake.sourceLabel,
            existingQueueId = intake.id
        )
    }

    fun retryActiveSharedReceipt() {
        val activeId = _uiState.value.activeRetryIntakeId ?: return
        retryQueuedReceipt(activeId)
    }

    fun dismissQueuedReceipt(intakeId: String) {
        PendingReceiptQueueStore.remove(getApplication(), intakeId)
        val refreshed = PendingReceiptQueueStore.loadAll(getApplication())
        _uiState.update { it.copy(pendingReceiptQueue = refreshed) }
    }

    fun syncIngredientPricingToSheets() {
        if (!requireAdmin("Syncing pricing to Google Sheets")) return
        val url = _uiState.value.googleSheetsWebhookUrl.trim()
        if (url.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Configure Google Sheets Webhook URL in Settings first, or use Excel CSV Export.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val summaries = ingredientPricingSummaries.value
            val orders = inventoryOrders.value
            val matrix = ingredientPriceMatrix.value
            val res = repository.syncIngredientPricingToSheets(url, summaries, orders, matrix)
            val msg = res.getOrElse { "Sheets sync failed: ${it.message}" }
            _uiState.update { it.copy(snackbarMessage = msg) }
        }
    }

    fun buildIngredientPricingCsvFile(context: Context): File {
        val summaries = ingredientPricingSummaries.value
        val orders = inventoryOrders.value
        val csvContent = RawMaterialPriceTracker.buildIngredientPricingCsv(
            summaries = summaries,
            inventoryOrders = orders,
            inventoryItems = inventoryItems.value,
            usages = inventoryUsages.value,
            priceAlertThresholdPct = _uiState.value.priceAlertThresholdPct
        )
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val exportDir = File(context.cacheDir, "inventory_reports").apply { mkdirs() }
        val outFile = File(exportDir, "FILTR_Ingredient_Pricing_$dateStr.csv")
        outFile.writeText(csvContent, Charsets.UTF_8)
        return outFile
    }

    fun refreshPendingReceiptQueue() {
        val items = PendingReceiptQueueStore.loadAll(getApplication())
        _uiState.update { it.copy(pendingReceiptQueue = items) }
    }

    fun dismissSharedReceiptDialog() {
        _uiState.update {
            it.copy(
                isParsingSharedReceipt = false,
                sharedReceiptSourceLabel = "",
                parsedSharedReceipt = null,
                activeRetryIntakeId = null
            )
        }
    }

    fun saveSharedReceiptOrder(
        supplierName: String,
        notes: String,
        items: List<InventoryOrderItemInput>
    ) {
        if (!requireAdmin("Saving raw material receipt")) return
        val retryId = _uiState.value.activeRetryIntakeId
        if (!retryId.isNullOrBlank()) {
            PendingReceiptQueueStore.remove(getApplication(), retryId)
        }
        val refreshedQueue = PendingReceiptQueueStore.loadAll(getApplication())
        _uiState.update {
            it.copy(
                parsedSharedReceipt = null,
                sharedReceiptSourceLabel = "",
                activeRetryIntakeId = null,
                pendingReceiptQueue = refreshedQueue,
                currentTab = FiltrTab.INVENTORY_ORDER
            )
        }
        saveInventoryOrder(supplierName = supplierName, notes = notes, items = items)
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

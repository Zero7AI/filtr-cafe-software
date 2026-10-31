package com.example

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import com.example.voice.ui.VoiceOrderScreen
import com.example.voice.viewmodel.VoiceOrderViewModel
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.FiltrTab
import com.example.ui.MainViewModel
import com.example.ui.components.AccountsUpiDialog
import com.example.ui.components.AddEditMenuItemDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.FiltrTopBar
import com.example.ui.components.OrderReceiptDialog
import com.example.ui.components.PrinterConnectionDialog
import com.example.ui.components.ReceiptStoryDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VoiceOrderMicButton
import com.example.ui.components.VoiceOrderReviewDialog
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.ChecklistScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoriesScreen
import com.example.ui.screens.InventoryOrderScreen
import com.example.ui.screens.InventoryUsageScreen
import com.example.ui.screens.MenuMasterScreen
import com.example.ui.screens.OrdersScreen
import com.example.ui.screens.ProfitLossScreen
import com.example.ui.screens.ReceiptDesignScreen
import com.example.ui.screens.RecipeMasterScreen
import com.example.util.InventoryNotificationHelper
import com.example.ui.theme.AmberAccentSecondary
import com.example.ui.theme.CoffeeBrownPrimary
import com.example.ui.theme.CoffeeOnPrimary
import com.example.ui.theme.FiltrTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var printerService: com.example.data.remote.ThermalPrinterService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on for POS counter use — prevents device from sleeping during service
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize thermal printer service
        printerService = com.example.data.remote.ThermalPrinterService(this)
        printerService.register()

        // Initialize inventory alert notification channel
        InventoryNotificationHelper.initChannel(this)

        setContent {
            FiltrTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val menuItems by viewModel.filteredMenuItems.collectAsStateWithLifecycle()
                val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
                val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
                val upiAccounts by viewModel.upiAccounts.collectAsStateWithLifecycle()
                val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
                val inventoryUsages by viewModel.inventoryUsages.collectAsStateWithLifecycle()

                // Thermal Printer state
                val isPrinterConnected by printerService.isConnected.collectAsStateWithLifecycle()
                val connectedPrinterName by printerService.connectedPrinterName.collectAsStateWithLifecycle()
                val isPrinterPermissionPending by printerService.permissionPending.collectAsStateWithLifecycle()
                val availablePrinters by printerService.availablePrinters.collectAsStateWithLifecycle()
                var showPrinterDialog by remember { mutableStateOf(false) }

                // Check and request notification permission on Android 13+ (API 33+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ -> }
                    LaunchedEffect(Unit) {
                        val permission = Manifest.permission.POST_NOTIFICATIONS
                        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                            permissionLauncher.launch(permission)
                        }
                    }
                }

                // Check and request microphone permission for offline voice ordering
                val micPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ -> }
                LaunchedEffect(Unit) {
                    val permission = Manifest.permission.RECORD_AUDIO
                    if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                        micPermissionLauncher.launch(permission)
                    }
                }

                // Check low stock and dispatch debounced notification
                LaunchedEffect(inventoryItems, inventoryUsages) {
                    if (inventoryItems.isNotEmpty()) {
                        InventoryNotificationHelper.checkAndNotifyLowStock(context, inventoryItems, inventoryUsages)
                    }
                }

                val isStaffMode = context.packageName == "filtr.coffee.staff"
                val snackbarHostState = remember { SnackbarHostState() }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        viewModel.clearSnackbar()
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        if (isStaffMode) {
                            ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF09090B),
                                drawerContentColor = Color.White,
                                modifier = Modifier
                                    .width(300.dp)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            if (dragAmount < -20f) {
                                                scope.launch { drawerState.close() }
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        Surface(
                                            color = Color.White,
                                            shape = CircleShape,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Coffee,
                                                contentDescription = "FILTR Logo",
                                                tint = Color(0xFF09090B),
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("FILTR COFFEE", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                            Text("Staff Operations Hub", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                    NavigationDrawerItem(
                                        label = { Text("New Order Billing", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.PointOfSale, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.BILLING,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.BILLING)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Daily Check List", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.CHECKLIST,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.CHECKLIST)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                            selectedIconColor = Color(0xFF10B981),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Kitchen Orders / KDS", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.ListAlt, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.ORDERS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.ORDERS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Voice Ordering", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.VOICE_ORDER,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.VOICE_ORDER)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        } else {
                            ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF09090B),
                                drawerContentColor = Color.White,
                                modifier = Modifier
                                    .width(300.dp)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures { change, dragAmount ->
                                            if (dragAmount < -20f) {
                                                scope.launch { drawerState.close() }
                                            }
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(20.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    ) {
                                        Surface(
                                            color = Color.White,
                                            shape = CircleShape,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Coffee,
                                                contentDescription = "FILTR Logo",
                                                tint = Color(0xFF09090B),
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("FILTR COFFEE", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                            Text("POS & Operations Hub", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                        }
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                    NavigationDrawerItem(
                                        label = { Text("New Order Billing", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.PointOfSale, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.BILLING,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.BILLING)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Voice Ordering", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.VOICE_ORDER,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.VOICE_ORDER)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Order Records & Status", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.ListAlt, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.ORDERS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.ORDERS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Daily Check List", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.CHECKLIST,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.CHECKLIST)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF10B981).copy(alpha = 0.25f),
                                            selectedIconColor = Color(0xFF10B981),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Item Master (Menu)", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.MenuBook, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.MENU,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.MENU)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Analytics Dashboard", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Analytics, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.DASHBOARD,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.DASHBOARD)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color.White.copy(alpha = 0.2f),
                                            selectedIconColor = Color.White,
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Accounts / UPI", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.QrCode, contentDescription = null) },
                                        selected = false,
                                        onClick = {
                                            viewModel.openAccountsUpiDialog()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Customers", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.People, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.CUSTOMERS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.CUSTOMERS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Inventory Order", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Inventory, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.INVENTORY_ORDER,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.INVENTORY_ORDER)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Inventories", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Warehouse, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.INVENTORIES,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.INVENTORIES)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Inventory Usage", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Assessment, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.INVENTORY_USAGE,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.INVENTORY_USAGE)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Recipe Master", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.RECIPE_MASTER,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.RECIPE_MASTER)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Profit & Loss (P&L)", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Analytics, contentDescription = null) },
                                        selected = uiState.currentTab == FiltrTab.PROFIT_LOSS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.PROFIT_LOSS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                    NavigationDrawerItem(
                                        label = { Text("🎨 Bill & Receipt Design", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        selected = uiState.currentTab == FiltrTab.RECEIPT_DESIGN,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.RECEIPT_DESIGN)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Sheets Sync & Settings", fontWeight = FontWeight.Medium) },
                                        icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = null) },
                                        selected = false,
                                        onClick = {
                                            viewModel.openSettingsDialog()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            FiltrTopBar(
                                currentTab = uiState.currentTab,
                                hasSheetsWebhook = uiState.googleSheetsWebhookUrl.isNotBlank(),
                                isFirestoreConnected = uiState.isFirestoreConnected,
                                isSupabaseConnected = uiState.isSupabaseConnected,
                                isNetworkAvailable = uiState.isNetworkAvailable,
                                isStaffMode = isStaffMode,
                                onSelectTab = { viewModel.selectTab(it) },
                                onOpenSettings = { viewModel.openSettingsDialog() },
                                onOpenDrawer = { scope.launch { drawerState.open() } }
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color(0xFF09090B),
                                contentColor = Color.White,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("bottom_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = uiState.currentTab == FiltrTab.BILLING,
                                    onClick = { viewModel.selectTab(FiltrTab.BILLING) },
                                    icon = { Icon(imageVector = Icons.Default.PointOfSale, contentDescription = "New Order") },
                                    label = { Text("New Order", fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF09090B),
                                        selectedTextColor = Color.White,
                                        indicatorColor = Color.White,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("tab_billing")
                                )

                                NavigationBarItem(
                                    selected = uiState.currentTab == FiltrTab.VOICE_ORDER,
                                    onClick = { viewModel.selectTab(FiltrTab.VOICE_ORDER) },
                                    icon = { Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Order") },
                                    label = { Text("Voice Order", fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF09090B),
                                        selectedTextColor = Color.White,
                                        indicatorColor = Color(0xFFF59E0B),
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("tab_voice_order")
                                )

                                NavigationBarItem(
                                    selected = uiState.currentTab == FiltrTab.ORDERS,
                                    onClick = { viewModel.selectTab(FiltrTab.ORDERS) },
                                    icon = { Icon(imageVector = Icons.Default.ListAlt, contentDescription = "Orders") },
                                    label = { Text("Orders", fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFF09090B),
                                        selectedTextColor = Color.White,
                                        indicatorColor = Color.White,
                                        unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                        unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier.testTag("tab_orders")
                                )
                            }
                        },
                        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (uiState.currentTab) {
                                FiltrTab.BILLING -> {
                                    BillingScreen(
                                        state = uiState,
                                        menuItems = menuItems,
                                        allMenuItems = allMenuItems,
                                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                        onCustomerNameChanged = { viewModel.updateCustomerName(it) },
                                        onContactNumberChanged = { viewModel.updateContactNumber(it) },
                                        onOrderNotesChanged = { viewModel.updateOrderNotes(it) },
                                        onAddToCart = { viewModel.addToCart(it) },
                                        onRemoveFromCart = { viewModel.removeFromCart(it) },
                                        onClearCart = { viewModel.clearCart() },
                                        onSubmitOrder = { viewModel.submitOrder() },
                                        onSelectCustomerSuggestion = { viewModel.selectCustomerSuggestion(it) },
                                        onDismissCustomerSuggestions = { viewModel.dismissCustomerSuggestions() }
                                    )
                                }
                                FiltrTab.VOICE_ORDER -> {
                                    val voiceViewModel: VoiceOrderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                    VoiceOrderScreen(
                                        viewModel = voiceViewModel,
                                        availableMenuItems = allMenuItems,
                                        onTransferToBilling = { voiceItems, notes ->
                                            viewModel.transferVoiceOrderToCart(voiceItems, notes)
                                        }
                                    )
                                }
                                FiltrTab.ORDERS -> {
                                    OrdersScreen(
                                        state = uiState,
                                        ordersList = ordersList,
                                        menuItems = allMenuItems,
                                        onFilterChanged = { viewModel.setStatusFilter(it) },
                                        onToggleMade = { viewModel.toggleOrderMadeStatus(it) },
                                        onTogglePaid = { viewModel.toggleOrderPaidStatus(it) },
                                        onSelectPaymentMode = { order, mode -> viewModel.setOrderPaymentMode(order, mode) },
                                        onToggleDelivered = { viewModel.toggleOrderDeliveredStatus(it) },
                                        onToggleItemCompleted = { orderId, itemId, currentCompletedQty, targetUnitIndex, totalQty ->
                                            viewModel.toggleOrderItemCompletedStatus(orderId, itemId, currentCompletedQty, targetUnitIndex, totalQty)
                                        },
                                        onUpdateOrderItems = { order, items -> viewModel.updateOrderItems(order, items) },
                                        onUpdateOrderWithCustomerDetails = { order, items, name, contact ->
                                            viewModel.updateOrderWithCustomerDetails(order, items, name, contact)
                                        },
                                        onSyncOrder = { viewModel.syncOrderToSheets(it) },
                                        onDeleteOrder = { viewModel.requestDeleteOrder(it) },
                                        onViewReceipt = { viewModel.viewOrderReceipt(it) },
                                        onSyncAllOrders = { viewModel.syncAllOrdersToSheets() },
                                        onSyncPendingOrders = { viewModel.syncPendingOrdersToSheets() },
                                        onOrdersDateFilterChanged = { viewModel.setOrdersDateFilter(it) },
                                        onOpenOrdersDatePicker = { viewModel.openOrdersDatePicker() },
                                        onDismissOrdersDatePicker = { viewModel.dismissOrdersDatePicker() },
                                        onOrdersDateRangeSelected = { start, end -> viewModel.setOrdersCustomDateRange(start, end) }
                                    )
                                }
                                FiltrTab.MENU -> {
                                    MenuMasterScreen(
                                        state = uiState,
                                        menuItems = menuItems,
                                        allMenuItems = allMenuItems,
                                        onCategorySelected = { viewModel.setSelectedCategory(it) },
                                        onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                        onOpenAddEditDialog = { viewModel.openAddEditItemDialog(it) },
                                        onToggleAvailability = { viewModel.saveMenuItem(it.name, it.category, it.price, it.description, !it.isAvailable) },
                                        onDeleteItem = { viewModel.requestDeleteMenuItem(it) },
                                        onMoveItemUp = { item, list -> viewModel.moveMenuItemUp(item, list) },
                                        onMoveItemDown = { item, list -> viewModel.moveMenuItemDown(item, list) },
                                        onSetItemPosition = { item, pos, list -> viewModel.moveMenuItem(item, pos, list) },
                                        onOpenAddCategoryDialog = { viewModel.openAddCategoryDialog() },
                                        onRestoreDefaultMenu = { viewModel.restoreDefaultMenu() },
                                        onSyncItemMasterToSheets = { viewModel.syncItemMasterToSheets() }
                                    )
                                }
                                FiltrTab.DASHBOARD -> {
                                    DashboardScreen(
                                        state = uiState,
                                        ordersList = ordersList,
                                        onDateFilterSelected = { viewModel.setDashboardDateFilter(it) },
                                        onOpenCalendarClick = { viewModel.openCustomDatePicker() },
                                        onDateRangeSelected = { startMs, endMs -> viewModel.setCustomDateRange(startMs, endMs) },
                                        onDismissCalendarDialog = { viewModel.dismissCustomDatePicker() }
                                    )
                                }
                                FiltrTab.INVENTORY_ORDER -> {
                                    val inventoryOrdersList by viewModel.inventoryOrders.collectAsStateWithLifecycle()
                                    val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                    InventoryOrderScreen(
                                        state = uiState,
                                        inventoryOrders = inventoryOrdersList,
                                        inventoryItems = inventoryItemsList,
                                        onOpenAddOrder = { viewModel.openAddInventoryOrderDialog() },
                                        onOpenInventoryMaster = { viewModel.openInventoryMasterDialog() },
                                        onDeleteOrder = { viewModel.requestDeleteInventoryOrder(it) },
                                        onConfirmDeleteOrder = { viewModel.confirmDeleteInventoryOrder(it) },
                                        onDismissDeleteOrder = { viewModel.dismissDeleteInventoryOrder() },
                                        onSaveOrder = { supplier, notes, items -> viewModel.saveInventoryOrder(supplier, notes, items) },
                                        onDismissAddOrder = { viewModel.dismissAddInventoryOrderDialog() },
                                        onSaveInventoryItem = { name, cat, unit, price, stdQty -> viewModel.saveInventoryItem(name, cat, unit, price, stdQty) },
                                        onEditInventoryItem = { viewModel.openAddInventoryItemDialog(it) },
                                        onDeleteInventoryItem = { viewModel.deleteInventoryItem(it) },
                                        onDismissInventoryMaster = { viewModel.dismissInventoryMasterDialog() },
                                        onDismissAddInventoryItem = { viewModel.dismissAddInventoryItemDialog() },
                                        onSearchQueryChanged = { viewModel.updateInventorySearchQuery(it) }
                                    )
                                }
                                FiltrTab.INVENTORIES -> {
                                    val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                    val usagesList by viewModel.inventoryUsages.collectAsStateWithLifecycle()
                                    val inventoryOrdersList by viewModel.inventoryOrders.collectAsStateWithLifecycle()
                                    InventoriesScreen(
                                        state = uiState,
                                        inventoryItems = inventoryItemsList,
                                        usages = usagesList,
                                        ordersList = ordersList,
                                        inventoryOrders = inventoryOrdersList,
                                        onUpdateStock = { id, stock -> viewModel.updateStockLevel(id, stock) },
                                        onStartEditingStock = { viewModel.startEditingStock(it) },
                                        onCancelEditingStock = { viewModel.cancelEditingStock() },
                                        onOpenAddStockDialog = { viewModel.openAddStockDialog(it) },
                                        onDismissAddStockDialog = { viewModel.dismissAddStockDialog() },
                                        onAddStockPacks = { id, packs -> viewModel.addStockPacks(id, packs) },
                                        onOpenInventoryMaster = { viewModel.openInventoryMasterDialog() },
                                        onSaveInventoryItem = { name, cat, unit, price, stdQty -> viewModel.saveInventoryItem(name, cat, unit, price, stdQty) },
                                        onEditInventoryItem = { viewModel.openAddInventoryItemDialog(it) },
                                        onDeleteInventoryItem = { viewModel.deleteInventoryItem(it) },
                                        onDismissInventoryMaster = { viewModel.dismissInventoryMasterDialog() },
                                        onDismissAddInventoryItem = { viewModel.dismissAddInventoryItemDialog() },
                                        onSearchQueryChanged = { viewModel.updateInventorySearchQuery(it) }
                                    )
                                }
                                FiltrTab.INVENTORY_USAGE -> {
                                    val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                    val usagesList by viewModel.inventoryUsages.collectAsStateWithLifecycle()
                                    InventoryUsageScreen(
                                        state = uiState,
                                        inventoryItems = inventoryItemsList,
                                        usages = usagesList,
                                        ordersList = ordersList,
                                        onOpenAddStockDialog = { viewModel.openAddStockDialog(it) }
                                    )
                                }
                                FiltrTab.RECIPE_MASTER -> {
                                    val menuItemsList by viewModel.menuItems.collectAsStateWithLifecycle()
                                    val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                    val usagesList by viewModel.inventoryUsages.collectAsStateWithLifecycle()
                                    RecipeMasterScreen(
                                        state = uiState,
                                        menuItems = menuItemsList,
                                        inventoryItems = inventoryItemsList,
                                        usages = usagesList,
                                        onOpenUsageDialog = { id, name -> viewModel.openUsagePerSkuDialog(id, name) },
                                        onDismissUsageDialog = { viewModel.dismissUsagePerSkuDialog() },
                                        onSaveUsage = { menuId, menuName, invId, invName, qty, unit ->
                                            viewModel.saveUsage(menuId, menuName, invId, invName, qty, unit)
                                        },
                                        onDeleteUsage = { viewModel.deleteUsage(it) }
                                    )
                                }
                                FiltrTab.CUSTOMERS -> {
                                    val customersList by viewModel.customers.collectAsStateWithLifecycle()
                                    CustomersScreen(
                                        customers = customersList,
                                        ordersList = ordersList,
                                        onAddCustomer = { name, contact -> viewModel.addCustomer(name, contact) },
                                        onUpdateCustomer = { viewModel.updateExistingCustomer(it) },
                                        onDeleteCustomer = { viewModel.deleteCustomer(it) },
                                        onMarkOrderPaid = { order, mode -> viewModel.setOrderPaymentMode(order, mode) }
                                    )
                                }
                                FiltrTab.RECEIPT_DESIGN -> {
                                    ReceiptDesignScreen(
                                        state = uiState,
                                        onBack = { viewModel.selectTab(FiltrTab.BILLING) },
                                        onUpdateText = { tagline, subtitle, f1, f2, f3, isStoryEnabled ->
                                            viewModel.updateReceiptDesignText(tagline, subtitle, f1, f2, f3, isStoryEnabled)
                                        },
                                        onSelectPhoto = { fileName ->
                                            viewModel.selectStoryPhoto(fileName)
                                        },
                                        onSavePhotoMetadata = { fileName, title, date, caption ->
                                            viewModel.updateStoryPhotoMetadata(fileName, title, date, caption)
                                        },
                                        onUploadPhoto = { bytes, title, date, caption ->
                                            viewModel.uploadStoryPhoto(bytes, title, date, caption)
                                        },
                                        onDeletePhoto = { fileName ->
                                            viewModel.deleteStoryPhotoByName(fileName)
                                        },
                                        onRefreshPhotos = {
                                            viewModel.loadStoryPhotos()
                                        },
                                        onGenerateNote = { title, date, onResult ->
                                            scope.launch {
                                                val note = viewModel.generateStoryNoteAsync(title, date)
                                                onResult(note)
                                            }
                                        },
                                        getPhotoUrl = { fileName ->
                                            viewModel.getStoryPhotoUrl(fileName)
                                        }
                                    )
                                }
                                FiltrTab.PROFIT_LOSS -> {
                                    val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                    val usagesList by viewModel.inventoryUsages.collectAsStateWithLifecycle()
                                    val fixedCostsList by viewModel.fixedCosts.collectAsStateWithLifecycle()
                                    ProfitLossScreen(
                                        state = uiState,
                                        ordersList = ordersList,
                                        inventoryItems = inventoryItemsList,
                                        usages = usagesList,
                                        fixedCosts = fixedCostsList,
                                        onOpenFixedCostDialog = { viewModel.openFixedCostDialog(it) },
                                        onDismissFixedCostDialog = { viewModel.dismissFixedCostDialog() },
                                        onSaveFixedCost = { viewModel.saveFixedCost(it) },
                                        onBackfillOrders = { startMs, endMs -> viewModel.backfillOrdersForDateRange(startMs, endMs) }
                                    )
                                }
                                FiltrTab.CHECKLIST -> {
                                    val checklistSections by viewModel.checklistSections.collectAsStateWithLifecycle()
                                    val checklistStats by viewModel.checklistStats.collectAsStateWithLifecycle()
                                    ChecklistScreen(
                                        sections = checklistSections,
                                        stats = checklistStats,
                                        onToggleTask = { viewModel.toggleChecklistTask(it) },
                                        onToggleSection = { viewModel.toggleChecklistSectionExpanded(it) },
                                        onOpenAddTask = { viewModel.openAddChecklistTaskDialog() },
                                        onRefresh = { viewModel.refreshChecklist() },
                                        showAddDialog = uiState.showAddChecklistTaskDialog,
                                        onDismissAddDialog = { viewModel.dismissAddChecklistTaskDialog() },
                                        onAddTask = { title, section, notes -> viewModel.addChecklistTask(title, section, notes) },
                                        onDeleteTask = { viewModel.deleteChecklistTask(it) }
                                    )
                                }
                            }

                            // Prominent AI Voice Order Mic Button at Bottom Right (Only on Billing screen to avoid obstructing management screens)
                            if (uiState.currentTab == FiltrTab.BILLING) {
                                VoiceOrderMicButton(
                                    isListening = uiState.isListeningVoice,
                                    isParsing = uiState.isParsingVoice,
                                    onStartListening = { viewModel.startListeningVoice() },
                                    onProcessTranscript = { viewModel.processVoiceTranscript(it) },
                                    onProcessAudio = { bytes, mimeType, durationMs -> viewModel.processVoiceAudio(bytes, mimeType, durationMs) },
                                    onCancel = { viewModel.cancelVoiceListening() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 16.dp, bottom = 16.dp)
                                )
                            }

                            // Confirmation Dialogs
                            uiState.itemToDeleteConfirmation?.let { item ->
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissDeleteDialogs() },
                                    title = { Text("Delete From Item Master?", fontWeight = FontWeight.Bold) },
                                    text = { Text("Are you sure you want to delete '${item.name}' from the menu master? (Past sales and order billing history will be preserved).") },
                                    confirmButton = {
                                        TextButton(onClick = { viewModel.confirmDeleteMenuItem(item) }) {
                                            Text("Yes, Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { viewModel.dismissDeleteDialogs() }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                            uiState.itemDeleteWarningMessage?.let { item ->
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissDeleteDialogs() },
                                    title = { Text("Cannot Delete Ordered Item") },
                                    text = { Text("'${item.name}' exists in previous order records. To safeguard financial and billing logs, it cannot be permanently deleted. Would you like to mark it as 'Unavailable' instead?") },
                                    confirmButton = {
                                        TextButton(onClick = { viewModel.markItemUnavailable(item) }) {
                                            Text("Mark Unavailable", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { viewModel.dismissDeleteDialogs() }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                            uiState.orderToDeleteConfirmation?.let { orderId ->
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissDeleteDialogs() },
                                    title = { Text("Delete Order Record?") },
                                    text = {
                                        if (uiState.isDeletingOrder) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.5.dp
                                                )
                                                Text("Deleting Order #$orderId from DB & Sheets...")
                                            }
                                        } else {
                                            Text("Are you sure you want to delete Order #$orderId? This will permanently remove it from both local database and Google Sheets.")
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = { viewModel.confirmDeleteOrder(orderId) },
                                            enabled = !uiState.isDeletingOrder
                                        ) {
                                            Text(
                                                if (uiState.isDeletingOrder) "Deleting..." else "Yes, Delete Order",
                                                color = if (uiState.isDeletingOrder) Color.Gray else Color.Red,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        if (!uiState.isDeletingOrder) {
                                            TextButton(onClick = { viewModel.dismissDeleteDialogs() }) {
                                                Text("Cancel")
                                            }
                                        }
                                    }
                                )
                            }

                            // Modals & Dialogs
                            if (uiState.showVoiceOrderReviewDialog && uiState.parsedVoiceOrder != null) {
                                VoiceOrderReviewDialog(
                                    parsedVoiceOrder = uiState.parsedVoiceOrder!!,
                                    onConfirmOrder = { viewModel.applyVoiceOrderToCart(it) },
                                    onDismiss = { viewModel.dismissVoiceOrderDialog() }
                                )
                            }

                            if (uiState.showSettingsDialog) {
                                SettingsDialog(
                                    currentWebhookUrl = uiState.googleSheetsWebhookUrl,
                                    isFirestoreConnected = uiState.isFirestoreConnected,
                                    isSupabaseConnected = uiState.isSupabaseConnected,
                                    isPrinterConnected = isPrinterConnected,
                                    printerName = connectedPrinterName,
                                    appVersionName = BuildConfig.VERSION_NAME,
                                    appVersionCode = BuildConfig.VERSION_CODE,
                                    isCheckingForUpdate = uiState.isCheckingForUpdate,
                                    onCheckForUpdates = { viewModel.checkForAppUpdates(isManualCheck = true) },
                                    onManagePrinter = { showPrinterDialog = true },
                                    onNavigateToReceiptDesign = {
                                        viewModel.dismissSettingsDialog()
                                        viewModel.selectTab(FiltrTab.RECEIPT_DESIGN)
                                    },
                                    onSaveWebhookUrl = { viewModel.saveGoogleSheetsWebhookUrl(it) },
                                    onDismiss = { viewModel.dismissSettingsDialog() }
                                )
                            }

                            if (uiState.showAppUpdateDialog) {
                                AppUpdateDialog(
                                    status = uiState.appUpdateStatus,
                                    isForceUpdate = uiState.isForceUpdate,
                                    forceUpdateMessage = uiState.forceUpdateMessage,
                                    onStartDownload = { updateInfo ->
                                        viewModel.startDownloadingUpdate(updateInfo)
                                    },
                                    onInstallApk = { apkFile ->
                                        viewModel.installDownloadedApk(apkFile)
                                    },
                                    onRetryCheck = {
                                        viewModel.checkForAppUpdates(isManualCheck = true)
                                    },
                                    onDismiss = {
                                        viewModel.dismissAppUpdateDialog()
                                    },
                                    onExitApp = {
                                        (context as? android.app.Activity)?.finishAffinity()
                                    }
                                )
                            }

                            if (uiState.showAccountsUpiDialog) {
                                AccountsUpiDialog(
                                    upiAccounts = upiAccounts,
                                    onDismiss = { viewModel.closeAccountsUpiDialog() },
                                    onAddUpiAccount = { vpa, payeeName, isDefault ->
                                        viewModel.addUpiAccount(vpa, payeeName, isDefault)
                                    },
                                    onDeleteUpiAccount = { account ->
                                        viewModel.deleteUpiAccount(account)
                                    },
                                    onSetDefaultAccount = { id ->
                                        viewModel.setDefaultUpiAccount(id)
                                    }
                                )
                            }

                            if (uiState.showAddEditItemDialog) {
                                val existingCategories = (allMenuItems.map { it.category } + uiState.customCategories).distinct().filter { it.isNotBlank() }
                                AddEditMenuItemDialog(
                                    itemToEdit = uiState.editingMenuItem,
                                    existingCategories = existingCategories,
                                    onSave = { name, cat, price, desc, available ->
                                        viewModel.saveMenuItem(name, cat, price, desc, available)
                                    },
                                    onDismiss = { viewModel.dismissAddEditItemDialog() }
                                )
                            }

                            if (uiState.showAddCategoryDialog) {
                                com.example.ui.screens.AddCategoryDialog(
                                    onDismiss = { viewModel.dismissAddCategoryDialog() },
                                    onConfirm = { newCategory ->
                                        viewModel.addCustomCategory(newCategory)
                                    }
                                )
                            }

                            uiState.selectedOrderForReceipt?.let { receiptOrder ->
                                OrderReceiptDialog(
                                    orderWithItems = receiptOrder,
                                    upiAccounts = upiAccounts,
                                    isPrinterConnected = isPrinterConnected,
                                    printerName = connectedPrinterName,
                                    onPrintReceipt = {
                                        scope.launch {
                                            val storyPrintData = if (uiState.isReceiptStoryEnabled) {
                                                try {
                                                    viewModel.getRandomStoryPhotoForPrint()
                                                } catch (e: Exception) { null }
                                            } else null

                                            val result = printerService.printReceipt(
                                                receiptOrder,
                                                storyPhoto = storyPrintData?.bitmap,
                                                storyTitle = if (uiState.isReceiptStoryEnabled) (storyPrintData?.title ?: uiState.receiptStoryTitle) else null,
                                                storyCaption = if (uiState.isReceiptStoryEnabled) (storyPrintData?.caption ?: uiState.receiptStoryCaption) else null,
                                                storyDate = if (uiState.isReceiptStoryEnabled) (storyPrintData?.date ?: uiState.receiptStoryDate) else null,
                                                headerSubtitle = uiState.receiptHeaderSubtitle,
                                                tagline = uiState.receiptTagline,
                                                footerLine1 = uiState.receiptFooterLine1,
                                                footerLine2 = uiState.receiptFooterLine2,
                                                footerLine3 = uiState.receiptFooterLine3
                                            )
                                            result.onSuccess {
                                                viewModel.showSnackbar("Token + Receipt printed!")
                                            }.onFailure {
                                                viewModel.showSnackbar("Print failed: ${it.message}")
                                            }
                                        }
                                    },
                                    onConnectPrinter = { showPrinterDialog = true },
                                    onCustomizeDesign = {
                                        viewModel.viewOrderReceipt(null)
                                        viewModel.selectTab(FiltrTab.RECEIPT_DESIGN)
                                    },
                                    onDismiss = { viewModel.viewOrderReceipt(null) }
                                )
                            }

                            // Dedicated Thermal Printer connection dialog
                            if (showPrinterDialog) {
                                PrinterConnectionDialog(
                                    printerService = printerService,
                                    printers = availablePrinters,
                                    isConnected = isPrinterConnected,
                                    connectedPrinterName = connectedPrinterName,
                                    isPermissionPending = isPrinterPermissionPending,
                                    onConnectDevice = { device ->
                                        try {
                                            printerService.connectToPrinter(device) { success ->
                                                if (success) {
                                                    viewModel.showSnackbar("Printer connected successfully!")
                                                    showPrinterDialog = false
                                                } else {
                                                    viewModel.showSnackbar("Failed to connect. Please grant USB permission.")
                                                }
                                            }
                                        } catch (e: Exception) {
                                            viewModel.showSnackbar("Error connecting: ${e.message}")
                                        }
                                    },
                                    onDisconnect = {
                                        printerService.disconnect()
                                        viewModel.showSnackbar("Printer disconnected")
                                    },
                                    onTestPrint = {
                                        scope.launch {
                                            val result = printerService.printTestPage()
                                            result.onSuccess {
                                                viewModel.showSnackbar("Test page printed successfully!")
                                            }.onFailure {
                                                viewModel.showSnackbar("Test print failed: ${it.message}")
                                            }
                                        }
                                    },
                                    onDismiss = { showPrinterDialog = false }
                                )
                            }

                            if (uiState.showReceiptStoryDialog) {
                                // Load photos from Supabase Storage when dialog opens
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    viewModel.loadStoryPhotos()
                                }
                                ReceiptStoryDialog(
                                    initialTitle = uiState.receiptStoryTitle,
                                    initialDate = uiState.receiptStoryDate,
                                    initialCaption = uiState.receiptStoryCaption,
                                    initialImagePath = uiState.receiptStoryImagePath,
                                    isEnabled = uiState.isReceiptStoryEnabled,
                                    isGeneratingAiNote = uiState.isGeneratingStoryNote,
                                    storyPhotoNames = uiState.storyPhotoNames,
                                    isLoadingPhotos = uiState.isLoadingStoryPhotos,
                                    isUploadingPhoto = uiState.isUploadingStoryPhoto,
                                    onUploadPhoto = { imageBytes ->
                                        viewModel.uploadStoryPhoto(imageBytes)
                                    },
                                    onDeletePhoto = { fileName ->
                                        viewModel.deleteStoryPhotoByName(fileName)
                                    },
                                    getPhotoUrl = { fileName ->
                                        viewModel.getStoryPhotoUrl(fileName)
                                    },
                                    onSave = { title, date, caption, imgPath, isEnabled ->
                                        viewModel.updateReceiptStory(title, date, caption, imgPath, isEnabled)
                                    },
                                    onGenerateAiNote = { title, date ->
                                        viewModel.generateReceiptStoryNote(title, date)
                                    },
                                    onDismiss = { viewModel.closeReceiptStoryDialog() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerService.disconnect()
        printerService.unregister()
    }
}

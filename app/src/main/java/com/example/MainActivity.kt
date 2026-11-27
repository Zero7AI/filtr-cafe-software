package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCode
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
import com.example.ui.KitchenInventoryViewRoute
import com.example.ui.MainViewModel
import com.example.ui.STAFF_ALLOWED_TABS
import com.example.ui.components.AccountsUpiDialog
import com.example.ui.components.ActiveDevicesDialog
import com.example.ui.components.AddEditMenuItemDialog
import com.example.ui.components.AdminUnlockDialog
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.DeviceAccessGateDialog
import com.example.ui.components.FiltrTopBar
import com.example.ui.components.OrderReceiptDialog
import com.example.ui.components.PrinterConnectionDialog
import com.example.ui.components.ReceiptStoryDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.VoiceOrderMicButton
import com.example.ui.components.VoiceOrderReviewDialog
import com.example.ui.screens.AddInventoryOrderDialog
import com.example.ui.screens.AddStockDialog
import com.example.ui.screens.AttendanceScreen
import com.example.ui.screens.BillingScreen
import com.example.ui.screens.ChecklistScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoriesScreen
import com.example.ui.screens.KdsScreen
import com.example.ui.screens.KitchenInventoryScreen
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        // Initialize notification channels
        InventoryNotificationHelper.initChannel(this)
        com.example.util.AppUpdateNotificationHelper.initChannel(this)

        if (intent?.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false) == true) {
            viewModel.showMandatoryUpdateDialog()
        }
        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }

        setContent {
            FiltrTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Thermal Printer state
                val isPrinterConnected by printerService.isConnected.collectAsStateWithLifecycle()
                val connectedPrinterName by printerService.connectedPrinterName.collectAsStateWithLifecycle()
                val isPrinterPermissionPending by printerService.permissionPending.collectAsStateWithLifecycle()
                val availablePrinters by printerService.availablePrinters.collectAsStateWithLifecycle()
                var showPrinterDialog by remember { mutableStateOf(false) }

                // Request required runtime permissions together only after device activation
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ -> }
                val locationPermissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { viewModel.onLocationPermissionResult() }
                val receiptDocumentPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenMultipleDocuments()
                ) { uris ->
                    if (uris.isNotEmpty()) {
                        processSharedReceiptUris(uris, sharedText = "")
                    }
                }
                LaunchedEffect(uiState.deviceAccessStatus) {
                    if (uiState.deviceAccessStatus == "ACTIVE") {
                        val neededPermissions = buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                            ) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        if (neededPermissions.isNotEmpty()) {
                            permissionsLauncher.launch(neededPermissions.toTypedArray())
                        }
                    }
                }

                // Inventory alerts removed per user request (keeping Kitchen Inventory & Usage clean)

                val isStaffMode = uiState.currentRole == "STAFF"
                var showAdminUnlockDialog by remember { mutableStateOf(false) }
                val snackbarHostState = remember { SnackbarHostState() }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()

                LaunchedEffect(isStaffMode, uiState.currentTab) {
                    if (isStaffMode && uiState.currentTab !in STAFF_ALLOWED_TABS) {
                        viewModel.selectTab(FiltrTab.BILLING)
                    }
                }

                LaunchedEffect(uiState.currentTab) {
                    if (uiState.currentTab == FiltrTab.ATTENDANCE) {
                        viewModel.refreshAttendanceLocationAndSync()
                    }
                }

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { message ->
                        snackbarHostState.showSnackbar(message)
                        viewModel.clearSnackbar()
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = uiState.deviceAccessStatus == "ACTIVE" && !uiState.isForceUpdate,
                    drawerContent = {
                        if (isStaffMode) {
                            ModalDrawerSheet(
                                drawerContainerColor = Color(0xFF09090B),
                                drawerContentColor = Color.White,
                                modifier = Modifier.width(300.dp)
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
                                        label = { Text("New Order", fontWeight = FontWeight.Bold) },
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
                                        label = { Text("Orders", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
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
                                        label = { Text("KDS Queue (FIFO)", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.LocalCafe, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        selected = uiState.currentTab == FiltrTab.KDS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.KDS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                            selectedIconColor = Color(0xFFF59E0B),
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
                                        label = { Text("Kitchen Inventory", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Kitchen, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                        selected = uiState.currentTab == FiltrTab.KITCHEN_INVENTORY,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.KITCHEN_INVENTORY)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFF38BDF8),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Staff Attendance", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF34D399)) },
                                        selected = uiState.currentTab == FiltrTab.ATTENDANCE,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.ATTENDANCE)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF34D399).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFF34D399),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Switch to Admin Mode", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        selected = false,
                                        onClick = {
                                            showAdminUnlockDialog = true
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color(0xFFF59E0B),
                                            unselectedTextColor = Color(0xFFF59E0B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Log Out / Switch Operator", fontWeight = FontWeight.Medium) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFEF4444)) },
                                        selected = false,
                                        onClick = {
                                            viewModel.logOutDevice()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color(0xFFEF4444),
                                            unselectedTextColor = Color(0xFFEF4444)
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
                                modifier = Modifier.width(300.dp)
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

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 10.dp))

                                    Text("LIVE OPERATIONS", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))

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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Order Records & Status", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) },
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("KDS Queue (FIFO)", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.LocalCafe, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        selected = uiState.currentTab == FiltrTab.KDS,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.KDS)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                            selectedIconColor = Color(0xFFF59E0B),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                                    Text("INVENTORY & COSTING", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Purchase Tracking", fontWeight = FontWeight.Bold) },
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Kitchen Inventory", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Kitchen, contentDescription = null, tint = Color(0xFF38BDF8)) },
                                        selected = uiState.currentTab == FiltrTab.KITCHEN_INVENTORY,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.KITCHEN_INVENTORY)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFF38BDF8),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                                    Text("TEAM & SECURITY", color = Color(0xFF34D399), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Staff Attendance", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF34D399)) },
                                        selected = uiState.currentTab == FiltrTab.ATTENDANCE,
                                        onClick = {
                                            viewModel.selectTab(FiltrTab.ATTENDANCE)
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            selectedContainerColor = Color(0xFF34D399).copy(alpha = 0.2f),
                                            selectedIconColor = Color(0xFF34D399),
                                            selectedTextColor = Color.White,
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Devices & Access Control", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Devices, contentDescription = null, tint = Color(0xFF34D399)) },
                                        selected = false,
                                        onClick = {
                                            viewModel.openActiveDevicesDialog()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.9f)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                                    Text("CATALOG & SETTINGS", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Item Master (Menu)", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) },
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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
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
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("🎨 Bill & Receipt Design", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = Color(0xFFF59E0B)) },
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

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 12.dp))

                                    NavigationDrawerItem(
                                        label = { Text("Lock to Staff Mode", fontWeight = FontWeight.Bold) },
                                        icon = { Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                        selected = false,
                                        onClick = {
                                            viewModel.lockToStaffRole()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color(0xFFF59E0B),
                                            unselectedTextColor = Color(0xFFF59E0B)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    NavigationDrawerItem(
                                        label = { Text("Log Out / Switch Operator", fontWeight = FontWeight.Medium) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFEF4444)) },
                                        selected = false,
                                        onClick = {
                                            viewModel.logOutDevice()
                                            scope.launch { drawerState.close() }
                                        },
                                        colors = NavigationDrawerItemDefaults.colors(
                                            unselectedIconColor = Color(0xFFEF4444),
                                            unselectedTextColor = Color(0xFFEF4444)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
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

                                if (isStaffMode) {
                                    NavigationBarItem(
                                        selected = uiState.currentTab == FiltrTab.ORDERS,
                                        onClick = { viewModel.selectTab(FiltrTab.ORDERS) },
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Orders") },
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

                                    NavigationBarItem(
                                        selected = uiState.currentTab == FiltrTab.KDS,
                                        onClick = { viewModel.selectTab(FiltrTab.KDS) },
                                        icon = { Icon(imageVector = Icons.Default.LocalCafe, contentDescription = "KDS") },
                                        label = { Text("KDS", fontWeight = FontWeight.Bold, maxLines = 1) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Color(0xFF09090B),
                                            selectedTextColor = Color.White,
                                            indicatorColor = Color(0xFFF59E0B),
                                            unselectedIconColor = Color.White.copy(alpha = 0.6f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier.testTag("tab_kds")
                                    )
                                } else {
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
                                        icon = { Icon(imageVector = Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Orders") },
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
                                    val menuItems by viewModel.filteredMenuItems.collectAsStateWithLifecycle()
                                    val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
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
                                    if (!isStaffMode) {
                                        val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
                                        val voiceViewModel: VoiceOrderViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                        VoiceOrderScreen(
                                            viewModel = voiceViewModel,
                                            availableMenuItems = allMenuItems,
                                            onTransferToBilling = { voiceItems, notes ->
                                                viewModel.transferVoiceOrderToCart(voiceItems, notes)
                                            }
                                        )
                                    }
                                }
                                FiltrTab.ORDERS -> {
                                    val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
                                    val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
                                    val kdsClaimsByKey by viewModel.kdsClaimsByKey.collectAsStateWithLifecycle()
                                    OrdersScreen(
                                        state = uiState,
                                        ordersList = ordersList,
                                        menuItems = allMenuItems,
                                        kdsClaimsByKey = kdsClaimsByKey,
                                        isStaffMode = isStaffMode,
                                        onFilterChanged = { viewModel.setStatusFilter(it) },
                                        onToggleMade = { viewModel.toggleOrderMadeStatus(it) },
                                        onTogglePaid = { viewModel.toggleOrderPaidStatus(it) },
                                        onSelectPaymentMode = { order, mode -> viewModel.setOrderPaymentMode(order, mode) },
                                        onToggleDelivered = { viewModel.toggleOrderDeliveredStatus(it) },
                                        onToggleItemCompleted = { orderId, itemId, currentCompletedQty, targetUnitIndex, totalQty ->
                                            viewModel.toggleOrderItemCompletedStatus(orderId, itemId, currentCompletedQty, targetUnitIndex, totalQty)
                                        },
                                        onUpdateOrderItems = { order, items -> viewModel.updateOrderItems(order, items) },
                                        onUpdateOrderWithCustomerDetails = { order, items, name, contact, notes ->
                                            viewModel.updateOrderWithCustomerDetails(order, items, name, contact, notes)
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
                                    if (!isStaffMode) {
                                        val menuItems by viewModel.filteredMenuItems.collectAsStateWithLifecycle()
                                        val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
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
                                }
                                FiltrTab.DASHBOARD -> {
                                    if (!isStaffMode) {
                                        val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
                                        DashboardScreen(
                                            state = uiState,
                                            ordersList = ordersList,
                                            onDateFilterSelected = { viewModel.setDashboardDateFilter(it) },
                                            onOpenCalendarClick = { viewModel.openCustomDatePicker() },
                                            onDateRangeSelected = { startMs, endMs -> viewModel.setCustomDateRange(startMs, endMs) },
                                            onDismissCalendarDialog = { viewModel.dismissCustomDatePicker() }
                                        )
                                    }
                                }
                                FiltrTab.INVENTORY_ORDER, FiltrTab.INVENTORIES -> {
                                    if (!isStaffMode) {
                                        val inventoryOrdersList by viewModel.inventoryOrders.collectAsStateWithLifecycle()
                                        val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                        val pricingSummaries by viewModel.ingredientPricingSummaries.collectAsStateWithLifecycle()
                                        val skuMatrix by viewModel.skuPriceHistoryMatrix.collectAsStateWithLifecycle()
                                        InventoryOrderScreen(
                                            state = uiState,
                                            inventoryOrders = inventoryOrdersList,
                                            inventoryItems = inventoryItemsList,
                                            pricingSummaries = pricingSummaries,
                                            skuPriceHistoryMatrix = skuMatrix,
                                            googleSheetsUrl = uiState.googleSheetsUrl,
                                            onOpenGoogleSheet = { sheetUrl -> openLiveGoogleSheet(sheetUrl) },
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
                                            onSearchQueryChanged = { viewModel.updateInventorySearchQuery(it) },
                                            onUpdateInventoryItem = { viewModel.updateInventoryItem(it) },
                                            onImportReceiptClick = {
                                                receiptDocumentPickerLauncher.launch(
                                                    arrayOf("application/pdf", "image/*")
                                                )
                                            },
                                            onRetryQueuedReceipt = { viewModel.retryQueuedReceipt(it) },
                                            onDismissQueuedReceipt = { viewModel.dismissQueuedReceipt(it) },
                                            onSyncPricingToSheets = { includeDates -> viewModel.syncIngredientPricingToSheets(includeDates) },
                                            onExportPricingXlsx = { includeDates ->
                                                val xlsxFile = viewModel.buildSkuPriceHistoryXlsxFile(context, includeDates)
                                                shareSkuPriceHistoryXlsx(xlsxFile)
                                            },
                                            onExportPricingCsv = {
                                                val csvFile = viewModel.buildIngredientPricingCsvFile(context)
                                                shareIngredientPricingCsv(csvFile)
                                            }
                                        )
                                    }
                                }
                                FiltrTab.INVENTORY_USAGE -> {
                                    if (!isStaffMode) {
                                        val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
                                        val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                        val usagesList by viewModel.inventoryUsages.collectAsStateWithLifecycle()
                                        InventoryUsageScreen(
                                            state = uiState,
                                            inventoryItems = inventoryItemsList,
                                            usages = usagesList,
                                            ordersList = ordersList,
                                            onOpenAddStockDialog = { viewModel.openAddStockDialog(it) }
                                        )
                                        if (uiState.showAddStockDialog) {
                                            uiState.addStockItem?.let { item ->
                                                AddStockDialog(
                                                    item = item,
                                                    onDismiss = { viewModel.dismissAddStockDialog() },
                                                    onAddStock = { qty -> viewModel.addStockPacks(item.id, qty) }
                                                )
                                            }
                                        }
                                    }
                                }
                                FiltrTab.RECIPE_MASTER -> {
                                    if (!isStaffMode) {
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
                                }
                                FiltrTab.CUSTOMERS -> {
                                    if (!isStaffMode) {
                                        val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
                                        val customersList by viewModel.customers.collectAsStateWithLifecycle()
                                        CustomersScreen(
                                            customers = customersList,
                                            ordersList = ordersList,
                                            onAddCustomer = { name, contact -> viewModel.addCustomer(name, contact) },
                                            onUpdateCustomer = { viewModel.updateExistingCustomer(it) },
                                            onDeleteCustomer = { viewModel.deleteCustomer(it) },
                                            onMarkOrderPaid = { order, mode -> viewModel.setOrderPaymentMode(order, mode) },
                                            onSettleAllOrders = { custName, pending, mode ->
                                                viewModel.settleAllCustomerOrders(custName, pending, mode)
                                            }
                                        )
                                    }
                                }
                                FiltrTab.RECEIPT_DESIGN -> {
                                    if (!isStaffMode) {
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
                                }
                                FiltrTab.PROFIT_LOSS -> {
                                    if (!isStaffMode) {
                                        val ordersList by viewModel.ordersList.collectAsStateWithLifecycle()
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
                                FiltrTab.KITCHEN_INVENTORY -> {
                                    val summary by viewModel.kitchenDashboardSummary.collectAsStateWithLifecycle()
                                    val route by viewModel.kitchenRoute.collectAsStateWithLifecycle()
                                    val activeItems by viewModel.kitchenActiveItems.collectAsStateWithLifecycle()
                                    val allItems by viewModel.kitchenAllItems.collectAsStateWithLifecycle()
                                    val entriesToday by viewModel.kitchenEntriesToday.collectAsStateWithLifecycle()
                                    val allShifts by viewModel.kitchenAllShifts.collectAsStateWithLifecycle()
                                    val allEntries by viewModel.kitchenAllEntries.collectAsStateWithLifecycle()
                                    val prevClosingShift by viewModel.previousClosingShift.collectAsStateWithLifecycle()
                                    val prevClosingEntries by viewModel.previousClosingEntries.collectAsStateWithLifecycle()

                                    KitchenInventoryScreen(
                                        isManager = !isStaffMode,
                                        summary = summary,
                                        route = route,
                                        activeItems = activeItems,
                                        allItems = allItems,
                                        entriesToday = entriesToday,
                                        allShifts = allShifts,
                                        allEntries = allEntries,
                                        previousClosingShift = prevClosingShift,
                                        previousClosingEntries = prevClosingEntries,
                                        onNavigate = { viewModel.navigateKitchenRoute(it) },
                                        onStartOrResumeShift = { viewModel.startOrResumeKitchenShift(it) },
                                        onOpenFirstUnchecked = { shiftType, secId ->
                                            viewModel.openFirstUncheckedKitchenItem(shiftType, secId)
                                        },
                                        onSaveItemAndAdvance = { shiftType, item, sealedQty, openedQty, openedRemainingQty, wastageQty, wastageReason, nextItemId, nextSectionId ->
                                            viewModel.saveKitchenItemAndAdvance(
                                                shiftType = shiftType,
                                                item = item,
                                                sealedQty = sealedQty,
                                                openedQty = openedQty,
                                                openedRemainingQty = openedRemainingQty,
                                                wastageQty = wastageQty,
                                                wastageReason = wastageReason,
                                                nextItemId = nextItemId,
                                                nextSectionId = nextSectionId
                                            )
                                        },
                                        onSubmitShift = { shiftType, notes ->
                                            viewModel.submitKitchenShift(shiftType, notes)
                                        },
                                        onSaveItemConfig = { existingId, name, emoji, sectionId, countingMode, unitLabel, containerLabel, packSize, minThreshold, targetLevel, isActive ->
                                            viewModel.saveKitchenCatalogItemConfig(
                                                existingId = existingId,
                                                name = name,
                                                iconEmoji = emoji,
                                                sectionId = sectionId,
                                                countingMode = countingMode,
                                                unitLabel = unitLabel,
                                                containerLabel = containerLabel,
                                                packSize = packSize,
                                                minStockThreshold = minThreshold,
                                                targetStockLevel = targetLevel,
                                                isActive = isActive
                                            )
                                        },
                                        onRefresh = { viewModel.refreshKitchenInventory() }
                                    )
                                }
                                FiltrTab.ATTENDANCE -> {
                                    val storeConfig by viewModel.storeGeofenceConfig.collectAsStateWithLifecycle()
                                    val hasLocationPermission by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
                                    val isFetchingLocation by viewModel.isFetchingLocation.collectAsStateWithLifecycle()
                                    val currentLocation by viewModel.currentLocationSample.collectAsStateWithLifecycle()
                                    val geofenceResult by viewModel.geofenceCheckResult.collectAsStateWithLifecycle()
                                    val locationError by viewModel.attendanceLocationError.collectAsStateWithLifecycle()
                                    val selectedMonthKey by viewModel.selectedAttendanceMonthKey.collectAsStateWithLifecycle()
                                    val availableStaffNames by viewModel.availableAttendanceStaffNames.collectAsStateWithLifecycle()
                                    val selectedManagerStaffName by viewModel.selectedManagerAttendanceStaff.collectAsStateWithLifecycle()
                                    val personalSummary by viewModel.personalAttendanceSummary.collectAsStateWithLifecycle()
                                    val managerSummary by viewModel.managerAttendanceSummary.collectAsStateWithLifecycle()
                                    val todayPersonalLog by viewModel.todayPersonalAttendanceLog.collectAsStateWithLifecycle()
                                    val staffRoster by viewModel.staffRoster.collectAsStateWithLifecycle()
                                    val teamRollCall by viewModel.teamRollCall.collectAsStateWithLifecycle()
                                    val selectedCheckInStaffName by viewModel.selectedCheckInStaffName.collectAsStateWithLifecycle()
                                    val onDutyStaffKeys by viewModel.onDutyStaffKeysToday.collectAsStateWithLifecycle()

                                    AttendanceScreen(
                                        isManager = !isStaffMode,
                                        currentStaffName = uiState.operatorName.ifBlank { uiState.currentRole },
                                        storeConfig = storeConfig,
                                        hasLocationPermission = hasLocationPermission,
                                        isFetchingLocation = isFetchingLocation,
                                        currentLocation = currentLocation,
                                        geofenceResult = geofenceResult,
                                        locationError = locationError,
                                        selectedMonthKey = selectedMonthKey,
                                        availableStaffNames = availableStaffNames,
                                        selectedManagerStaffName = selectedManagerStaffName,
                                        personalSummary = personalSummary,
                                        managerSummary = managerSummary,
                                        todayPersonalLog = todayPersonalLog,
                                        onRequestLocationPermission = {
                                            locationPermissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        },
                                        onRefreshLocationAndSync = { viewModel.refreshAttendanceLocationAndSync() },
                                        onCheckIn = { viewModel.performStaffCheckIn() },
                                        onCheckOut = { viewModel.performStaffCheckOut() },
                                        onSelectMonth = { viewModel.selectAttendanceMonth(it) },
                                        onSelectManagerStaff = { viewModel.selectManagerAttendanceStaff(it) },
                                        onSetStoreLocationHere = { viewModel.setStoreLocationFromCurrentGps() },
                                        onChangeRadiusMeters = { viewModel.updateStoreRadiusMeters(it) },
                                        staffRoster = staffRoster,
                                        teamRollCall = teamRollCall,
                                        onDutyStaffKeys = onDutyStaffKeys,
                                        selectedCheckInStaffName = selectedCheckInStaffName,
                                        onSelectCheckInStaff = { viewModel.selectCheckInStaff(it) },
                                        onSaveStaffRosterMember = { id, name, role, shift, offDay, joinDate, active ->
                                            viewModel.saveStaffRosterMember(id, name, role, shift, offDay, joinDate, active)
                                        },
                                        onSaveStaffRosterMemberWithColor = { id, name, role, shift, offDay, joinDate, active, colorHex ->
                                            viewModel.saveStaffRosterMember(id, name, role, shift, offDay, joinDate, active, colorHex)
                                        },
                                        onAdminSaveManualAttendance = { dateKey, staffName, checkInMs, checkOutMs, notes ->
                                            viewModel.saveAdminManualAttendance(dateKey, staffName, checkInMs, checkOutMs, notes)
                                        }
                                    )
                                }
                                FiltrTab.KDS -> {
                                    val queueItems by viewModel.kdsFifoQueue.collectAsStateWithLifecycle()
                                    val availableBaristas by viewModel.availableAttendanceStaffNames.collectAsStateWithLifecycle()
                                    val staffRoster by viewModel.staffRoster.collectAsStateWithLifecycle()
                                    val activeBaristaName by viewModel.selectedKdsBaristaName.collectAsStateWithLifecycle()
                                    val paidOnlyFilter by viewModel.kdsPaidOnlyFilter.collectAsStateWithLifecycle()
                                    val todayOnlyFilter by viewModel.kdsTodayOnlyFilter.collectAsStateWithLifecycle()
                                    val onDutyStaffKeys by viewModel.onDutyStaffKeysToday.collectAsStateWithLifecycle()
                                    KdsScreen(
                                        queueItems = queueItems,
                                        availableBaristas = availableBaristas,
                                        staffRoster = staffRoster,
                                        activeBaristaName = activeBaristaName,
                                        paidOnlyFilter = paidOnlyFilter,
                                        todayOnlyFilter = todayOnlyFilter,
                                        onDutyStaffKeys = onDutyStaffKeys,
                                        isStaffMode = isStaffMode,
                                        onSelectBarista = { viewModel.selectKdsBarista(it) },
                                        onTogglePaidOnlyFilter = { viewModel.setKdsPaidOnlyFilter(it) },
                                        onToggleTodayOnlyFilter = { viewModel.setKdsTodayOnlyFilter(it) },
                                        onTapQueueItem = { viewModel.onKdsItemTap(it) },
                                        onUnclaimQueueItem = { viewModel.onKdsItemUnclaim(it) }
                                    )
                                }
                            }

                            // Prominent AI Voice Order Mic Button at Bottom Right (positioned above sticky cart bar when cart is active)
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
                                        .padding(
                                            end = 16.dp,
                                            bottom = if (uiState.cartItems.isNotEmpty()) 148.dp else 16.dp
                                        )
                                )
                            }

                            // Admin Confirmation Dialogs
                            uiState.itemToDeleteConfirmation?.takeIf { !isStaffMode }?.let { item ->
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

                            uiState.itemDeleteWarningMessage?.takeIf { !isStaffMode }?.let { item ->
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

                            if (uiState.showSettingsDialog && !isStaffMode) {
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

                            // Persistent lockout: Ensure update dialog cannot be suppressed while force update is active
                            if (uiState.isForceUpdate && !uiState.showAppUpdateDialog) {
                                LaunchedEffect(uiState.isForceUpdate) {
                                    viewModel.showMandatoryUpdateDialog()
                                }
                            }

                            if (!uiState.isForceUpdate && uiState.deviceAccessStatus != "ACTIVE") {
                                DeviceAccessGateDialog(
                                    accessStatus = uiState.deviceAccessStatus,
                                    savedOperatorName = uiState.operatorName,
                                    deviceModel = viewModel.currentDeviceModel,
                                    errorMessage = uiState.accessGateError,
                                    isVerifying = uiState.isVerifyingAccess,
                                    onVerify = { name, passcode -> viewModel.verifyDeviceAccess(name, passcode) },
                                    onRefreshStatus = { viewModel.refreshDeviceAccessStatus() },
                                    onExitApp = { (context as? android.app.Activity)?.finishAffinity() }
                                )
                            }

                            if (showAdminUnlockDialog && isStaffMode) {
                                AdminUnlockDialog(
                                    onDismiss = { showAdminUnlockDialog = false },
                                    onUnlock = { passcode, onResult ->
                                        viewModel.elevateToAdmin(passcode) { success, message ->
                                            if (success) {
                                                showAdminUnlockDialog = false
                                            }
                                            onResult(success, message)
                                        }
                                    }
                                )
                            }

                            if (uiState.showActiveDevicesDialog && !isStaffMode) {
                                val staffRosterList by viewModel.staffRoster.collectAsStateWithLifecycle()
                                ActiveDevicesDialog(
                                    devices = uiState.registeredDevices,
                                    currentDeviceId = viewModel.currentDeviceId,
                                    accessPolicy = uiState.accessPolicyConfig,
                                    isLoading = uiState.isLoadingDevices,
                                    onRefresh = { viewModel.refreshRegisteredDevices() },
                                    onUpdateDeviceStatus = { key, status -> viewModel.updateRemoteDeviceStatus(key, status) },
                                    onUpdateDeviceRole = { key, role -> viewModel.updateRemoteDeviceRole(key, role) },
                                    onSavePolicy = { adminCode, staffCode, reqApproval, forceReverify ->
                                        viewModel.saveAccessPolicy(adminCode, staffCode, reqApproval, forceReverify)
                                    },
                                    onDismiss = { viewModel.dismissActiveDevicesDialog() },
                                    staffRoster = staffRosterList,
                                    onLinkDeviceToMember = { key, member -> viewModel.linkDeviceToMember(key, member) },
                                    onCreateAndLinkMember = { key, name -> viewModel.createAndLinkRosterMemberForDevice(key, name) }
                                )
                            }

                            if (uiState.showAccountsUpiDialog && !isStaffMode) {
                                val upiAccounts by viewModel.upiAccounts.collectAsStateWithLifecycle()
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

                            if (uiState.showAddEditItemDialog && !isStaffMode) {
                                val allMenuItems by viewModel.menuItems.collectAsStateWithLifecycle()
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

                            if (uiState.showAddCategoryDialog && !isStaffMode) {
                                com.example.ui.screens.AddCategoryDialog(
                                    onDismiss = { viewModel.dismissAddCategoryDialog() },
                                    onConfirm = { newCategory ->
                                        viewModel.addCustomCategory(newCategory)
                                    }
                                )
                            }

                            uiState.selectedOrderForReceipt?.let { receiptOrder ->
                                val upiAccounts by viewModel.upiAccounts.collectAsStateWithLifecycle()
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
                                    onCustomizeDesign = if (!isStaffMode) {
                                        {
                                            viewModel.viewOrderReceipt(null)
                                            viewModel.selectTab(FiltrTab.RECEIPT_DESIGN)
                                        }
                                    } else null,
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

                            if (uiState.showReceiptStoryDialog && !isStaffMode) {
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

                            if (uiState.isParsingSharedReceipt) {
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissSharedReceiptDialog() },
                                    title = { Text("Reading Receipt...", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                            Text(
                                                text = "Extracting raw-material items from ${uiState.sharedReceiptSourceLabel.ifBlank { "shared file" }} via Gemini AI..."
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { viewModel.dismissSharedReceiptDialog() }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                            uiState.parsedSharedReceipt?.let { sharedReceipt ->
                                val inventoryItemsList by viewModel.inventoryItems.collectAsStateWithLifecycle()
                                AddInventoryOrderDialog(
                                    inventoryItems = inventoryItemsList,
                                    initialSupplierName = sharedReceipt.supplierName,
                                    initialNotes = sharedReceipt.notes,
                                    initialItems = sharedReceipt.items,
                                    sharedBannerText = sharedReceipt.extractionSummary.ifBlank {
                                        "Imported from ${sharedReceipt.sourceLabel.ifBlank { "Shared Receipt" }}"
                                    },
                                    sharedErrorText = sharedReceipt.parseError,
                                    onRetryAiScan = if (uiState.activeRetryIntakeId != null) {
                                        { viewModel.retryActiveSharedReceipt() }
                                    } else {
                                        null
                                    },
                                    requiresAdminUnlock = isStaffMode,
                                    onRequestAdminUnlock = { showAdminUnlockDialog = true },
                                    onDismiss = { viewModel.dismissSharedReceiptDialog() },
                                    onSave = { supplier, notes, items ->
                                        viewModel.saveSharedReceiptOrder(supplier, notes, items)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false)) {
            viewModel.showMandatoryUpdateDialog()
        }
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incomingIntent: Intent?) {
        val intent = incomingIntent ?: return
        val action = intent.action ?: return
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE && action != Intent.ACTION_VIEW) {
            return
        }

        val uris = LinkedHashSet<Uri>()
        when (action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { uris.add(it) }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.forEach {
                    uris.add(it)
                }
                intent.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.let { uris.add(it) }
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
        if (uris.isEmpty() && sharedText.isBlank()) {
            return
        }

        // Prevent re-importing on configuration changes
        intent.action = Intent.ACTION_MAIN
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.data = null

        processSharedReceiptUris(uris.toList(), sharedText, intent.type)
    }

    private fun processSharedReceiptUris(
        uris: List<Uri>,
        sharedText: String,
        fallbackIntentMimeType: String? = null
    ) {
        lifecycleScope.launch {
            val (attachments, sourceNames) = withContext(Dispatchers.IO) {
                val collected = mutableListOf<Pair<ByteArray, String>>()
                val names = mutableListOf<String>()
                val maxBytesPerFile = 15 * 1024 * 1024

                for (uri in uris.take(3)) {
                    try {
                        val displayName = resolveUriDisplayName(uri)
                        val detectedMime = resolveUriMimeType(uri, displayName, fallbackIntentMimeType)
                        if (detectedMime.startsWith("image/") || detectedMime == "application/pdf") {
                            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                                val buffer = java.io.ByteArrayOutputStream()
                                val chunk = ByteArray(16 * 1024)
                                var total = 0
                                while (total < maxBytesPerFile) {
                                    val read = input.read(chunk)
                                    if (read <= 0) break
                                    val allowed = minOf(read, maxBytesPerFile - total)
                                    buffer.write(chunk, 0, allowed)
                                    total += allowed
                                }
                                buffer.toByteArray()
                            } ?: ByteArray(0)

                            if (bytes.isNotEmpty()) {
                                collected.add(bytes to detectedMime)
                                names.add(displayName.ifBlank { if (detectedMime == "application/pdf") "receipt.pdf" else "receipt.jpg" })
                            }
                        }
                    } catch (_: Exception) {
                        // Continue trying other attachments or shared text
                    }
                }
                collected to names
            }

            if (attachments.isEmpty() && sharedText.isBlank()) {
                viewModel.showSnackbar("Unsupported shared file — please share a PDF bill, image receipt, or text.")
                return@launch
            }

            val sourceLabel = when {
                sourceNames.size == 1 -> sourceNames.first()
                sourceNames.size > 1 -> "${sourceNames.first()} (+${sourceNames.size - 1} more)"
                else -> "Shared WhatsApp Text"
            }
            viewModel.importSharedRawMaterialReceipt(
                attachments = attachments,
                sharedText = sharedText,
                sourceLabel = sourceLabel
            )
        }
    }

    private fun resolveUriDisplayName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)?.trim().orEmpty()
                        if (name.isNotBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.trim().orEmpty()
    }

    private fun resolveUriMimeType(uri: Uri, displayName: String, fallbackType: String?): String {
        val resolved = contentResolver.getType(uri)?.trim().orEmpty()
        if (resolved.isNotBlank() && resolved != "application/octet-stream" && resolved != "*/*") {
            return resolved
        }
        val lowerName = displayName.lowercase()
        return when {
            lowerName.endsWith(".pdf") -> "application/pdf"
            lowerName.endsWith(".png") -> "image/png"
            lowerName.endsWith(".webp") -> "image/webp"
            lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") -> "image/jpeg"
            !fallbackType.isNullOrBlank() && fallbackType != "*/*" -> fallbackType
            else -> "application/pdf"
        }
    }

    private fun openLiveGoogleSheet(url: String) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
        } catch (e: Exception) {
            viewModel.showSnackbar("Unable to open sheet link: ${e.message}")
        }
    }

    private fun shareSkuPriceHistoryXlsx(xlsxFile: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                xlsxFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FILTR Coffee SKU Price History (.xlsx)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Open / Share SKU Price History (.xlsx)"))
        } catch (e: Exception) {
            viewModel.showSnackbar("Failed to export Excel (.xlsx): ${e.message}")
        }
    }

    private fun shareIngredientPricingCsv(csvFile: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                csvFile
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FILTR Coffee Ingredient Pricing & Hike Sheet")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(sendIntent, "Open / Share Ingredient Pricing Excel CSV"))
        } catch (e: Exception) {
            viewModel.showSnackbar("Failed to export Excel CSV: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        printerService.disconnect()
        printerService.unregister()
    }
}

@Composable
private fun LowStockNotificationEffect(viewModel: MainViewModel) {
    val context = LocalContext.current
    val inventoryItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val inventoryUsages by viewModel.inventoryUsages.collectAsStateWithLifecycle()

    LaunchedEffect(inventoryItems, inventoryUsages) {
        if (inventoryItems.isNotEmpty()) {
            InventoryNotificationHelper.checkAndNotifyLowStock(context, inventoryItems, inventoryUsages)
        }
    }
}

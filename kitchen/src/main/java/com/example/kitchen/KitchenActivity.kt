package com.example.kitchen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kitchen.ui.KitchenTab
import com.example.kitchen.ui.KitchenViewModel
import com.example.kitchen.ui.components.KitchenTopBar
import com.example.kitchen.ui.screens.KitchenKdsScreen
import com.example.kitchen.ui.screens.OrderScreen
import com.example.kitchen.ui.theme.CreamBackground
import com.example.kitchen.ui.theme.FiltrKitchenTheme
import com.example.kitchen.ui.theme.ShadcnBlack
import com.example.kitchen.ui.theme.ShadcnBorder

class KitchenActivity : ComponentActivity() {

    private val viewModel: KitchenViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FiltrKitchenTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val pendingOrders by viewModel.pendingOrders.collectAsStateWithLifecycle()
                val completedOrders by viewModel.completedTodayOrders.collectAsStateWithLifecycle()
                val menuItems by viewModel.menuItems.collectAsStateWithLifecycle()
                val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
                val pendingCount by viewModel.pendingOrderCount.collectAsStateWithLifecycle()

                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(uiState.snackbarMessage) {
                    uiState.snackbarMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.clearSnackbar()
                    }
                }

                Scaffold(
                    topBar = {
                        KitchenTopBar(
                            currentTab = uiState.currentTab,
                            onTabSelected = { viewModel.setTab(it) },
                            connectionStatus = connectionStatus,
                            pendingOrderCount = pendingCount,
                            onOpenSettings = { viewModel.openSettings() }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(CreamBackground)
                    ) {
                        when (uiState.currentTab) {
                            KitchenTab.KITCHEN -> {
                                KitchenKdsScreen(
                                    pendingOrders = pendingOrders,
                                    completedTodayOrders = completedOrders,
                                    onMarkReady = { orderId -> viewModel.markOrderReady(orderId) }
                                )
                            }
                            KitchenTab.ORDER -> {
                                OrderScreen(
                                    menuItems = menuItems,
                                    cartItems = uiState.cartItems,
                                    customerName = uiState.customerName,
                                    contactNumber = uiState.contactNumber,
                                    orderNotes = uiState.orderNotes,
                                    paymentMode = uiState.paymentMode,
                                    isPlacingOrder = uiState.isPlacingOrder,
                                    onAddToCart = { viewModel.addToCart(it) },
                                    onRemoveFromCart = { viewModel.removeFromCart(it) },
                                    onClearCart = { viewModel.clearCart() },
                                    onCustomerNameChange = { viewModel.updateCustomerName(it) },
                                    onContactNumberChange = { viewModel.updateContactNumber(it) },
                                    onOrderNotesChange = { viewModel.updateOrderNotes(it) },
                                    onPaymentModeChange = { viewModel.updatePaymentMode(it) },
                                    onSubmitOrder = { viewModel.submitOrder() }
                                )
                            }
                        }

                        // Settings Dialog (Google Sheets Webhook Configuration)
                        if (uiState.showSettingsDialog) {
                            var tempUrl by remember { mutableStateOf(uiState.sheetsWebhookUrl) }

                            AlertDialog(
                                onDismissRequest = { viewModel.closeSettings() },
                                title = {
                                    Text(
                                        text = "Kitchen Tablet Settings",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(
                                            text = "Supabase cloud backend is configured and syncing automatically.",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )

                                        OutlinedTextField(
                                            value = tempUrl,
                                            onValueChange = { tempUrl = it },
                                            label = { Text("Google Sheets Webhook URL (Optional)") },
                                            placeholder = { Text("https://script.google.com/macros/s/...") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { viewModel.saveWebhookUrl(tempUrl.trim()) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ShadcnBlack),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { viewModel.closeSettings() }) {
                                        Text("Cancel", color = Color.Gray)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

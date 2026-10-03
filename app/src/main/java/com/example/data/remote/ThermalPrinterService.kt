package com.example.data.remote

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.data.entity.OrderWithItems
import com.example.util.EscPosBuilder
import com.example.util.ThermalImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Manages USB OTG thermal printer connections and printing.
 * Uses Android USB Host API — no external dependencies needed.
 */
class ThermalPrinterService(private val context: Context) {

    companion object {
        private const val TAG = "ThermalPrinterService"
        const val ACTION_USB_PERMISSION = "filtr.coffee.staff.USB_PERMISSION"
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private var connectedDevice: UsbDevice? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectedPrinterName = MutableStateFlow<String?>(null)
    val connectedPrinterName: StateFlow<String?> = _connectedPrinterName.asStateFlow()

    private val _permissionPending = MutableStateFlow(false)
    val permissionPending: StateFlow<Boolean> = _permissionPending.asStateFlow()

    private val _availablePrinters = MutableStateFlow<List<UsbDevice>>(emptyList())
    val availablePrinters: StateFlow<List<UsbDevice>> = _availablePrinters.asStateFlow()

    // Pending permission callback and target device
    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private var pendingDevice: UsbDevice? = null

    // USB permission receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val targetDevice = device ?: pendingDevice
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    _permissionPending.value = false

                    if (granted && targetDevice != null) {
                        Log.d(TAG, "USB permission granted for ${targetDevice.deviceName}")
                        val success = openConnection(targetDevice)
                        pendingPermissionCallback?.invoke(success)
                    } else {
                        Log.w(TAG, "USB permission denied or target device null (granted=$granted)")
                        pendingPermissionCallback?.invoke(false)
                    }
                    pendingPermissionCallback = null
                    pendingDevice = null
                    refreshPrinters()
                }
            }
        }
    }

    // USB device attached/detached receiver — auto-update and auto-disconnect
    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached")
                    refreshPrinters()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (device != null && device.deviceName == connectedDevice?.deviceName) {
                        Log.d(TAG, "Printer disconnected: ${device.deviceName}")
                        disconnect()
                    }
                    refreshPrinters()
                }
            }
        }
    }

    /**
     * Register broadcast receivers. Call this in Activity onCreate.
     */
    fun register() {
        try {
            val permFilter = IntentFilter(ACTION_USB_PERMISSION)
            val deviceFilter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbPermissionReceiver, permFilter, Context.RECEIVER_NOT_EXPORTED)
                context.registerReceiver(usbDeviceReceiver, deviceFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbPermissionReceiver, permFilter)
                context.registerReceiver(usbDeviceReceiver, deviceFilter)
            }
            refreshPrinters()
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receivers: ${e.message}", e)
        }
    }

    /**
     * Unregister broadcast receivers. Call this in Activity onDestroy.
     */
    fun unregister() {
        try {
            context.unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering usbPermissionReceiver: ${e.message}")
        }
        try {
            context.unregisterReceiver(usbDeviceReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering usbDeviceReceiver: ${e.message}")
        }
    }

    /**
     * Safe display name for USB device (never throws SecurityException).
     */
    fun getDeviceDisplayName(device: UsbDevice): String {
        return try {
            device.productName?.takeIf { it.isNotBlank() }
                ?: device.manufacturerName?.let { "$it Printer" }
                ?: "USB Thermal Printer (${device.deviceId})"
        } catch (e: SecurityException) {
            "USB Thermal Printer (${device.deviceId})"
        } catch (e: Exception) {
            "USB Printer (${device.deviceId})"
        }
    }

    /**
     * Refresh the list of discovered USB printers.
     */
    fun refreshPrinters() {
        try {
            _availablePrinters.value = discoverPrinters()
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing printers: ${e.message}")
        }
    }

    /**
     * Discover available USB printers.
     */
    fun discoverPrinters(): List<UsbDevice> {
        val list = try {
            usbManager.deviceList.values.toList()
        } catch (e: Exception) {
            Log.e(TAG, "Error accessing deviceList: ${e.message}")
            emptyList()
        }

        // Filter for printer class (7) or common POS printer interfaces
        val printerDevices = list.filter { device ->
            try {
                (0 until device.interfaceCount).any { i ->
                    val iface = device.getInterface(i)
                    iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER ||
                    iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                    iface.interfaceClass == 0x02 // CDC
                }
            } catch (e: Exception) {
                false
            }
        }

        return printerDevices.ifEmpty { list }
    }

    /**
     * Request permission and connect to a USB printer.
     */
    fun connectToPrinter(device: UsbDevice, callback: ((Boolean) -> Unit)? = null) {
        try {
            if (usbManager.hasPermission(device)) {
                val opened = openConnection(device)
                callback?.invoke(opened)
            } else {
                pendingPermissionCallback = callback
                pendingDevice = device
                _permissionPending.value = true

                // On Android 14+ (Target U+), PendingIntent with FLAG_MUTABLE MUST have an explicit intent (setPackage)
                val intent = Intent(ACTION_USB_PERMISSION).apply {
                    setPackage(context.packageName)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
                usbManager.requestPermission(device, permissionIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in connectToPrinter: ${e.message}", e)
            _permissionPending.value = false
            pendingPermissionCallback = null
            pendingDevice = null
            callback?.invoke(false)
        }
    }

    /**
     * Open USB connection to the device.
     */
    fun openConnection(device: UsbDevice): Boolean {
        try {
            disconnect()

            // Find a suitable interface with a bulk OUT endpoint
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        ep.direction == UsbConstants.USB_DIR_OUT) {

                        val connection = usbManager.openDevice(device)
                        if (connection != null && connection.claimInterface(iface, true)) {
                            usbConnection = connection
                            usbInterface = iface
                            usbEndpoint = ep
                            connectedDevice = device
                            _isConnected.value = true
                            val displayName = getDeviceDisplayName(device)
                            _connectedPrinterName.value = displayName
                            Log.d(TAG, "Connected to printer: $displayName")
                            return true
                        }
                    }
                }
            }
            Log.w(TAG, "No suitable bulk OUT endpoint found on device")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect: ${e.message}", e)
            return false
        }
    }

    /**
     * Disconnect from the printer.
     */
    fun disconnect() {
        try {
            usbInterface?.let { usbConnection?.releaseInterface(it) }
            usbConnection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting: ${e.message}")
        }
        usbConnection = null
        usbInterface = null
        usbEndpoint = null
        connectedDevice = null
        _isConnected.value = false
        _connectedPrinterName.value = null
    }

    /**
     * Send raw bytes to the printer.
     */
    private fun sendData(data: ByteArray): Boolean {
        val connection = usbConnection ?: return false
        val endpoint = usbEndpoint ?: return false

        var offset = 0
        val chunkSize = endpoint.maxPacketSize.coerceAtLeast(64)
        while (offset < data.size) {
            val length = minOf(chunkSize, data.size - offset)
            val chunk = data.copyOfRange(offset, offset + length)
            val transferred = connection.bulkTransfer(endpoint, chunk, chunk.size, 5000)
            if (transferred < 0) {
                Log.e(TAG, "Bulk transfer failed at offset $offset")
                return false
            }
            offset += length
        }
        return true
    }

    /**
     * Print a complete receipt for an order.
     * Prints TWO slips: kitchen token (compact, for kitchen) + customer receipt (full).
     */
    suspend fun printReceipt(
        orderWithItems: OrderWithItems,
        storyPhoto: Bitmap? = null,
        storyTitle: String? = null,
        storyCaption: String? = null,
        storyDate: String? = null,
        headerSubtitle: String = "where coffee meets vibe",
        tagline: String = "We are working for you <3",
        footerLine1: String = "Thank you for hanging out with us!",
        footerLine2: String = "Tag your cup on Instagram @filtr.coffeee",
        footerLine3: String = "Made fresh - Crafted with obsession"
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) {
            return@withContext Result.failure(Exception("Printer not connected"))
        }

        try {
            // === STEP 1: Print Kitchen Token (compact, no pricing) ===
            val tokenData = EscPosBuilder.buildKitchenToken(orderWithItems)
            val tokenSent = sendData(tokenData)
            if (!tokenSent) {
                return@withContext Result.failure(Exception("Failed to print kitchen token"))
            }
            Log.d(TAG, "Kitchen token printed for ${orderWithItems.order.orderId}")

            // Small delay to let printer process and cut
            Thread.sleep(500)

            // === STEP 2: Print Customer Receipt (full with story photo) ===
            var photoBytes: ByteArray? = null
            var photoWidthBytes = 0
            var photoHeight = 0

            if (storyPhoto != null) {
                // Full-width 384-dot edge-to-edge printing with 7:4 aspect ratio (compact height)
                val processed = ThermalImageProcessor.processForThermalAspectRatio(storyPhoto, targetWidth = 384, aspectNumerator = 7, aspectDenominator = 4)
                photoBytes = processed.data
                photoWidthBytes = processed.widthBytes
                photoHeight = processed.heightDots
            }

            // Build ESC/POS customer receipt
            val receiptData = EscPosBuilder.buildReceipt(
                orderWithItems = orderWithItems,
                storyPhotoBytes = photoBytes,
                storyPhotoWidthBytes = photoWidthBytes,
                storyPhotoHeight = photoHeight,
                storyTitle = storyTitle,
                storyCaption = storyCaption,
                storyDate = storyDate,
                headerSubtitle = headerSubtitle,
                tagline = tagline,
                footerLine1 = footerLine1,
                footerLine2 = footerLine2,
                footerLine3 = footerLine3
            )

            // Send to printer
            val success = sendData(receiptData)
            if (success) {
                Log.d(TAG, "Customer receipt printed for ${orderWithItems.order.orderId}")
                Result.success("Token + Receipt printed successfully")
            } else {
                Result.failure(Exception("Failed to send receipt data to printer"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Print error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Print a test page to verify printer connection.
     */
    suspend fun printTestPage(): Result<String> = withContext(Dispatchers.IO) {
        if (!_isConnected.value) {
            return@withContext Result.failure(Exception("Printer not connected"))
        }

        try {
            val out = java.io.ByteArrayOutputStream()
            // Init
            out.write(byteArrayOf(0x1B, 0x40))
            // Center
            out.write(byteArrayOf(0x1B, 0x61, 0x01))
            // Double size
            out.write(byteArrayOf(0x1D, 0x21, 0x11))
            out.write("FILTR COFFEE\n".toByteArray())
            // Normal size
            out.write(byteArrayOf(0x1D, 0x21, 0x00))
            out.write("where coffee meets vibe\n\n".toByteArray())
            out.write("================================\n".toByteArray())
            out.write("Printer Test Successful!\n".toByteArray())
            out.write("================================\n\n\n".toByteArray())
            // Feed + cut
            out.write(byteArrayOf(0x1B, 0x64, 0x04))
            out.write(byteArrayOf(0x1D, 0x56, 0x01))

            val success = sendData(out.toByteArray())
            if (success) Result.success("Test page printed")
            else Result.failure(Exception("Failed to send test data"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

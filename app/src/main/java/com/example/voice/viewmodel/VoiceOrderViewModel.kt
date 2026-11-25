package com.example.voice.viewmodel

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.MenuItemEntity
import com.example.voice.audio.AudioCaptureService
import com.example.voice.audio.VoiceModalityProfile
import com.example.voice.engine.LocalOrderEngine
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceStateMachineEnum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class VoiceOrderViewModel : ViewModel() {

    private val orderEngine = LocalOrderEngine()
    private val engineMutex = Mutex()

    private val _orderState = MutableStateFlow(
        VoiceOrderState(
            sessionId = UUID.randomUUID().toString(),
            restaurantId = "FILTR Coffee",
            counterId = "Counter POS 1",
            deviceId = Build.MODEL,
            state = VoiceStateMachineEnum.IDLE,
            statusMessage = "Initializing Voice Engine..."
        )
    )
    val orderState: StateFlow<VoiceOrderState> = _orderState.asStateFlow()

    private val _isListening = MutableStateFlow(true)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeechActive = MutableStateFlow(false)
    val isSpeechActive: StateFlow<Boolean> = _isSpeechActive.asStateFlow()

    private val _audioLevel = MutableStateFlow(0.0)
    val audioLevel: StateFlow<Double> = _audioLevel.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _showConfirmDialog = MutableStateFlow(false)
    val showConfirmDialog: StateFlow<Boolean> = _showConfirmDialog.asStateFlow()

    private var captureService: AudioCaptureService? = null
    private var boundAppContext: Context? = null
    private var isBound = false
    private var isBindingInProgress = false
    private var autoResetJob: Job? = null

    @Volatile
    private var latestMenuItems: List<MenuItemEntity> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AudioCaptureService.LocalBinder ?: return
            val service = localBinder.getService()
            captureService = service
            isBound = true
            isBindingInProgress = false

            if (latestMenuItems.isNotEmpty()) {
                service.turnRecognizer.updateMenuContext(latestMenuItems)
            }

            service.audioLevelCallback = { rms ->
                _audioLevel.value = rms
            }
            service.speechStateCallback = { active ->
                _isSpeechActive.value = active
            }
            service.transcriptCallback = { text, isFinal, modality ->
                onInProcessTranscript(text, isFinal, modality)
            }
            service.statusCallback = { ready, msg ->
                _orderState.update { current ->
                    val nextState = when {
                        !ready -> if (current.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.IDLE
                        current.items.isNotEmpty() -> VoiceStateMachineEnum.ORDER_BUILDING
                        else -> VoiceStateMachineEnum.LISTENING
                    }
                    current.copy(
                        state = nextState,
                        statusMessage = msg
                    )
                }
            }

            if (_isListening.value) {
                service.resumeListening()
            } else {
                service.pauseListening()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            clearServiceCallbacks(captureService)
            captureService = null
            isBound = false
            isBindingInProgress = false
        }
    }

    fun bindService(context: Context) {
        val appContext = context.applicationContext
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            onMicPermissionDenied()
            return
        }

        if (isBound || isBindingInProgress) return

        boundAppContext = appContext
        isBindingInProgress = true

        val intent = Intent(appContext, AudioCaptureService::class.java).apply {
            action = if (_isListening.value) {
                AudioCaptureService.ACTION_START_LISTENING
            } else {
                AudioCaptureService.ACTION_PAUSE_LISTENING
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            isBindingInProgress = false
            _isListening.value = false
            _orderState.update {
                it.copy(
                    state = if (it.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.IDLE,
                    statusMessage = "Voice service start blocked: ${e.message ?: "permission/background limit"}"
                )
            }
            return
        }

        val bound = runCatching {
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)

        if (!bound) {
            isBindingInProgress = false
            _orderState.update {
                it.copy(
                    state = if (it.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.IDLE,
                    statusMessage = "Could not bind audio service"
                )
            }
        }
    }

    fun onMicPermissionDenied() {
        _isListening.value = false
        _isSpeechActive.value = false
        _audioLevel.value = 0.0
        _orderState.update {
            it.copy(
                state = if (it.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.IDLE,
                statusMessage = "Microphone permission required for voice ordering"
            )
        }
    }

    fun syncMenuItemsWithRoom(menuItems: List<MenuItemEntity>) {
        latestMenuItems = menuItems.toList()
        MenuCatalog.syncWithRoomMenuItems(latestMenuItems)
        captureService?.turnRecognizer?.updateMenuContext(latestMenuItems)
    }

    fun toggleListening() {
        val next = !_isListening.value
        if (next) {
            val appCtx = boundAppContext
            if (appCtx != null &&
                ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                onMicPermissionDenied()
                return
            }
            _isListening.value = true
            captureService?.resumeListening()
            _orderState.update {
                it.copy(
                    state = if (it.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.LISTENING,
                    statusMessage = "Deepgram Voice AI Active"
                )
            }
        } else {
            _isListening.value = false
            _isSpeechActive.value = false
            _audioLevel.value = 0.0
            captureService?.pauseListening()
            _orderState.update {
                it.copy(
                    state = if (it.items.isNotEmpty()) VoiceStateMachineEnum.ORDER_BUILDING else VoiceStateMachineEnum.IDLE,
                    statusMessage = "Voice Listening Paused"
                )
            }
        }
    }

    private fun onInProcessTranscript(
        text: String,
        isFinal: Boolean,
        modality: VoiceModalityProfile? = null
    ) {
        _currentTranscript.value = text
        if (isFinal && text.isNotBlank() && _isListening.value) {
            submitUtterance(text, modality)
        }
    }

    fun submitUtterance(
        rawText: String,
        modality: VoiceModalityProfile? = null
    ) {
        viewModelScope.launch {
            _currentTranscript.value = rawText
            val newState = engineMutex.withLock {
                val snapshot = _orderState.value
                val processed = withContext(Dispatchers.Default) {
                    orderEngine.processUtterance(rawText, snapshot, modality)
                }
                _orderState.update { processed }
                processed
            }
            restartInactivityTimer(newState)

            // If voice command triggered confirmation, show confirm dialog
            if (newState.state == VoiceStateMachineEnum.CONFIRMED && newState.items.isNotEmpty()) {
                _showConfirmDialog.value = true
            }
        }
    }

    fun quickAdd(itemName: String, quantity: Int = 1) {
        if (quantity <= 0) return
        val matched = MenuCatalog.items.find { it.name.equals(itemName, ignoreCase = true) }
            ?: MenuCatalog.findBestMatch(itemName)
        if (matched != null) {
            addItemDirect(matched.name, matched.price, quantity)
        } else {
            submitUtterance("add $quantity $itemName")
        }
    }

    fun addItemDirect(name: String, price: Double, quantity: Int = 1) {
        if (quantity <= 0) return
        var latestState: VoiceOrderState? = null
        _orderState.update { current ->
            val currentItems = current.items.toMutableList()
            val index = currentItems.indexOfFirst {
                it.name.equals(name, ignoreCase = true) && it.modifiers.isEmpty()
            }
            if (index >= 0) {
                val item = currentItems[index]
                val newQty = item.quantity + quantity
                val newTotal = newQty * item.unitPrice
                currentItems[index] = item.copy(quantity = newQty, itemTotal = newTotal)
            } else {
                val lineTotal = quantity * price
                currentItems.add(
                    VoiceOrderLineItem(
                        lineId = UUID.randomUUID().toString(),
                        sku = "SKU_${name.replace(" ", "_").uppercase()}",
                        name = name,
                        quantity = quantity,
                        unitPrice = price,
                        itemTotal = lineTotal
                    )
                )
            }
            val subtotal = currentItems.sumOf { it.itemTotal }
            val updated = current.copy(
                state = VoiceStateMachineEnum.ORDER_BUILDING,
                items = currentItems,
                subtotal = subtotal,
                tax = 0.0,
                total = subtotal,
                statusMessage = "Added ${quantity}× $name"
            )
            latestState = updated
            updated
        }
        latestState?.let(::restartInactivityTimer)
    }

    fun onItemQuantityChange(lineId: String, delta: Int) {
        var latestState: VoiceOrderState? = null
        _orderState.update { current ->
            val currentItems = current.items.toMutableList()
            val index = currentItems.indexOfFirst { it.lineId == lineId }
            if (index < 0) return@update current

            val item = currentItems[index]
            val newQty = item.quantity + delta
            if (newQty <= 0) {
                currentItems.removeAt(index)
            } else {
                val newTotal = newQty * item.unitPrice
                currentItems[index] = item.copy(quantity = newQty, itemTotal = newTotal)
            }
            val subtotal = currentItems.sumOf { it.itemTotal }
            val nextState = if (currentItems.isEmpty()) {
                if (_isListening.value) VoiceStateMachineEnum.LISTENING else VoiceStateMachineEnum.IDLE
            } else {
                VoiceStateMachineEnum.ORDER_BUILDING
            }
            val updated = current.copy(
                state = nextState,
                items = currentItems,
                subtotal = subtotal,
                tax = 0.0,
                total = subtotal,
                statusMessage = "Updated cart items"
            )
            latestState = updated
            updated
        }
        latestState?.let(::restartInactivityTimer)
    }

    /**
     * Resets order ticket to zero (empty state).
     */
    fun resetOrder(reasonMsg: String = "Order cleared. Ready for next order.") {
        autoResetJob?.cancel()
        orderEngine.resetSession()
        _orderState.update {
            VoiceOrderState(
                sessionId = UUID.randomUUID().toString(),
                restaurantId = "FILTR Coffee",
                counterId = "Counter POS 1",
                deviceId = Build.MODEL,
                state = if (_isListening.value) VoiceStateMachineEnum.LISTENING else VoiceStateMachineEnum.IDLE,
                items = emptyList(),
                subtotal = 0.0,
                tax = 0.0,
                total = 0.0,
                lastTranscript = "",
                statusMessage = reasonMsg
            )
        }
        _currentTranscript.value = ""
        _showConfirmDialog.value = false
    }

    fun requestConfirm() {
        if (_orderState.value.items.isNotEmpty()) {
            _showConfirmDialog.value = true
        }
    }

    fun dismissConfirmDialog() {
        _showConfirmDialog.value = false
        _orderState.update { current ->
            if (current.state == VoiceStateMachineEnum.CONFIRMED && current.items.isNotEmpty()) {
                current.copy(
                    state = VoiceStateMachineEnum.ORDER_BUILDING,
                    statusMessage = "Continue editing voice order"
                )
            } else {
                current
            }
        }
    }

    /**
     * Confirms the current voice order, transfers it to the POS Orders / Billing section,
     * and clears the voice ticket ready for the next customer.
     */
    fun confirmAndTransferToBilling(onTransfer: (List<VoiceOrderLineItem>, String?) -> Unit) {
        val current = _orderState.value
        val itemsToTransfer = current.items.toList()
        val notesToTransfer = current.orderNotes
        _showConfirmDialog.value = false
        if (itemsToTransfer.isNotEmpty()) {
            onTransfer(itemsToTransfer, notesToTransfer)
            resetOrder("Order transferred to billing cart.")
        }
    }

    private fun restartInactivityTimer(@Suppress("UNUSED_PARAMETER") state: VoiceOrderState) {
        // In FILTR billing, orders with punched items must NEVER be auto-deleted by silence.
        // Explicit [ RESET ] button is used by the cashier to clear the order to zero.
        autoResetJob?.cancel()
    }

    private fun clearServiceCallbacks(service: AudioCaptureService?) {
        service?.audioLevelCallback = null
        service?.speechStateCallback = null
        service?.transcriptCallback = null
        service?.statusCallback = null
    }

    fun unbindService(context: Context) {
        val appCtx = boundAppContext ?: context.applicationContext
        val service = captureService
        clearServiceCallbacks(service)
        service?.stopCaptureAndService()

        if (isBound || isBindingInProgress) {
            runCatching {
                appCtx.unbindService(serviceConnection)
            }
        }
        runCatching {
            appCtx.stopService(Intent(appCtx, AudioCaptureService::class.java))
        }

        captureService = null
        boundAppContext = null
        isBound = false
        isBindingInProgress = false
        _isSpeechActive.value = false
        _audioLevel.value = 0.0
    }

    override fun onCleared() {
        autoResetJob?.cancel()
        val service = captureService
        clearServiceCallbacks(service)
        boundAppContext?.let { appCtx ->
            unbindService(appCtx)
        } ?: run {
            service?.stopCaptureAndService()
            captureService = null
        }
        super.onCleared()
    }
}

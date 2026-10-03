package com.example.voice.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.MenuItemEntity
import com.example.voice.audio.AudioCaptureService
import com.example.voice.audio.VoiceModalityProfile
import com.example.voice.engine.LocalOrderEngine
import com.example.voice.engine.MenuCatalog
import com.example.voice.model.VoiceOrderState
import com.example.voice.model.VoiceOrderLineItem
import com.example.voice.model.VoiceStateMachineEnum
import com.example.voice.speech.InProcessVoskRecognizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceOrderViewModel : ViewModel() {

    private val orderEngine = LocalOrderEngine()

    private val _orderState = MutableStateFlow(
        VoiceOrderState(
            sessionId = UUID.randomUUID().toString(),
            restaurantId = "FILTR Coffee",
            counterId = "Counter POS 1",
            deviceId = android.os.Build.MODEL,
            state = VoiceStateMachineEnum.LISTENING,
            statusMessage = "Starting Offline Voice Engine & Flight Recorder..."
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
    private var isBound = false
    private var autoResetJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as AudioCaptureService.LocalBinder).getService()
            captureService = service
            isBound = true

            // Set up automatic live grammar updates whenever MenuCatalog changes
            MenuCatalog.onMenuUpdated = { phrases ->
                captureService?.voskRecognizer?.updateGrammar(phrases)
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
                _orderState.value = _orderState.value.copy(
                    state = if (ready) VoiceStateMachineEnum.LISTENING else VoiceStateMachineEnum.IDLE,
                    statusMessage = msg
                )
                if (ready) {
                    val activePhrases = MenuCatalog.getActiveGrammarPhrases()
                    service.voskRecognizer.updateGrammar(activePhrases)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            captureService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        if (!isBound) {
            val intent = Intent(context, AudioCaptureService::class.java)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Fallback to startService
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun syncMenuItemsWithRoom(menuItems: List<MenuItemEntity>) {
        MenuCatalog.syncWithRoomMenuItems(menuItems)
        val activePhrases = MenuCatalog.getActiveGrammarPhrases()
        captureService?.voskRecognizer?.updateGrammar(activePhrases)
    }

    fun toggleListening() {
        val next = !_isListening.value
        _isListening.value = next
        captureService?.voskRecognizer?.isEnabled?.set(next)
        _orderState.value = _orderState.value.copy(
            statusMessage = if (next) {
                "Offline Voice STT Active"
            } else {
                "Voice Listening Paused"
            }
        )
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
            val newState = orderEngine.processUtterance(rawText, _orderState.value, modality)
            _orderState.value = newState
            restartInactivityTimer(newState)

            // If voice command triggered confirmation, show confirm dialog
            if (newState.state == VoiceStateMachineEnum.CONFIRMED && newState.items.isNotEmpty()) {
                _showConfirmDialog.value = true
            }
        }
    }

    fun quickAdd(itemName: String, quantity: Int = 1) {
        val matched = MenuCatalog.items.find { it.name.equals(itemName, ignoreCase = true) }
            ?: MenuCatalog.findBestMatch(itemName)
        if (matched != null) {
            addItemDirect(matched.name, matched.price, quantity)
        } else {
            submitUtterance("add $quantity $itemName")
        }
    }

    fun addItemDirect(name: String, price: Double, quantity: Int = 1) {
        val currentItems = _orderState.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.name.equals(name, ignoreCase = true) }
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
        val updated = _orderState.value.copy(
            state = VoiceStateMachineEnum.ORDER_BUILDING,
            items = currentItems,
            subtotal = subtotal,
            tax = 0.0,
            total = subtotal,
            statusMessage = "Added ${quantity}× $name"
        )
        _orderState.value = updated
        restartInactivityTimer(updated)
    }

    fun onItemQuantityChange(lineId: String, delta: Int) {
        val currentItems = _orderState.value.items.toMutableList()
        val index = currentItems.indexOfFirst { it.lineId == lineId }
        if (index >= 0) {
            val item = currentItems[index]
            val newQty = item.quantity + delta
            if (newQty <= 0) {
                currentItems.removeAt(index)
            } else {
                val newTotal = newQty * item.unitPrice
                currentItems[index] = item.copy(quantity = newQty, itemTotal = newTotal)
            }
            val subtotal = currentItems.sumOf { it.itemTotal }
            val updated = _orderState.value.copy(
                items = currentItems,
                subtotal = subtotal,
                tax = 0.0,
                total = subtotal,
                statusMessage = "Updated cart items"
            )
            _orderState.value = updated
            restartInactivityTimer(updated)
        }
    }

    /**
     * Resets order ticket to zero (empty state).
     */
    fun resetOrder(reasonMsg: String = "Order cleared. Ready for next order.") {
        autoResetJob?.cancel()
        orderEngine.resetSession()
        val emptyState = VoiceOrderState(
            sessionId = UUID.randomUUID().toString(),
            restaurantId = "FILTR Coffee",
            counterId = "Counter POS 1",
            deviceId = android.os.Build.MODEL,
            state = VoiceStateMachineEnum.IDLE,
            items = emptyList(),
            subtotal = 0.0,
            tax = 0.0,
            total = 0.0,
            lastTranscript = "",
            statusMessage = reasonMsg
        )
        _orderState.value = emptyState
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
    }

    /**
     * Confirms the current voice order, transfers it to the POS Orders / Billing section,
     * and clears the voice ticket ready for the next customer.
     */
    fun confirmAndTransferToBilling(onTransfer: (List<VoiceOrderLineItem>, String?) -> Unit) {
        val itemsToTransfer = _orderState.value.items.toList()
        val notesToTransfer = _orderState.value.orderNotes
        _showConfirmDialog.value = false
        if (itemsToTransfer.isNotEmpty()) {
            onTransfer(itemsToTransfer, notesToTransfer)
            resetOrder("Order transferred to billing cart.")
        }
    }

    private fun restartInactivityTimer(state: VoiceOrderState) {
        // In FILTR billing, orders with punched items must NEVER be auto-deleted by silence.
        // Explicit [ RESET ] button is used by the cashier to clear the order to zero.
        autoResetJob?.cancel()
    }

    fun unbindService(context: Context) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore
            }
            isBound = false
        }
    }

    override fun onCleared() {
        MenuCatalog.onMenuUpdated = null
        autoResetJob?.cancel()
        super.onCleared()
    }
}

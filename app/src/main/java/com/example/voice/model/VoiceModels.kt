package com.example.voice.model

enum class VoiceStateMachineEnum {
    IDLE,
    LISTENING,
    TRANSCRIBING,
    ORDER_BUILDING,
    ORDER_READY,
    EDITING,
    CONFIRMED,
    SENT,
    COMPLETE
}

data class VoiceSelectedModifier(
    val groupId: String = "",
    val optionId: String = "",
    val name: String = "",
    val code: String = "",
    val priceDelta: Double = 0.0
)

data class VoiceOrderLineItem(
    val lineId: String = "",
    val sku: String = "",
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val variantName: String? = null,
    val modifiers: List<VoiceSelectedModifier> = emptyList(),
    val itemTotal: Double = 0.0,
    val isAmbiguous: Boolean = false,
    val ambiguityCandidates: List<String> = emptyList(),
    val rawUtterance: String? = null
)

data class VoiceOrderState(
    val sessionId: String = "",
    val restaurantId: String = "FILTR Coffee",
    val counterId: String = "Counter POS 1",
    val deviceId: String = "FILTR Billing Device",
    val state: VoiceStateMachineEnum = VoiceStateMachineEnum.IDLE,
    val items: List<VoiceOrderLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val currency: String = "₹",
    val lastTranscript: String? = "",
    val statusMessage: String? = "Ready for order",
    val orderNotes: String? = null
)

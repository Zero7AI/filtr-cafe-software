package com.example

import com.example.data.entity.MenuItemEntity
import com.example.data.remote.DeepgramVoiceService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepgramVoiceServiceTest {

    private val sampleMenu = listOf(
        MenuItemEntity(id = 1, name = "OG VIETNAMESE", category = "ICED COFFEE", price = 109.0),
        MenuItemEntity(id = 2, name = "PERI PERI FRIES", category = "FRIES", price = 99.0),
        MenuItemEntity(id = 3, name = "BOMBAY SANDWICH", category = "SANDWICH", price = 119.0),
        MenuItemEntity(id = 4, name = "HAZELNUT FRAPPE", category = "FRAPPE", price = 129.0)
    )

    @Test
    fun parseDeepgramResponseJson_extractsTranscriptAndConfidence() {
        val sampleJson = """
            {
              "results": {
                "channels": [
                  {
                    "detected_language": "en-IN",
                    "alternatives": [
                      {
                        "transcript": "Do OG Vietnamese aur ek Peri Peri Fries for Rahul less sugar",
                        "confidence": 0.96
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = DeepgramVoiceService.getInstance().parseDeepgramResponseJson(sampleJson)
        assertEquals("Do OG Vietnamese aur ek Peri Peri Fries for Rahul less sugar", parsed.transcript)
        assertEquals(0.96, parsed.confidence, 0.001)
        assertEquals("en-IN", parsed.detectedLanguage)
    }

    @Test
    fun parseTranscriptToOrder_extractsHinglishItemsCustomerPhoneAndNotes() {
        val transcript = "Do OG Vietnamese aur ek Peri Peri Fries for Rahul 9876543210 less sugar takeaway"
        val result = DeepgramVoiceService.getInstance().parseTranscriptToOrder(transcript, sampleMenu)

        assertTrue(result.isSuccess)
        val order = result.getOrThrow()
        assertEquals("Rahul", order.customerName)
        assertEquals("9876543210", order.contactNumber)
        assertTrue(order.notes.contains("Less sugar"))
        assertTrue(order.notes.contains("Takeaway"))
        assertEquals(2, order.items.find { it.menuItemName == "OG VIETNAMESE" }?.quantity)
        assertEquals(1, order.items.find { it.menuItemName == "PERI PERI FRIES" }?.quantity)
    }

    @Test
    fun parseTranscriptToOrder_rejectsBlankTranscript() {
        val result = DeepgramVoiceService.getInstance().parseTranscriptToOrder("   ", sampleMenu)
        assertTrue(result.isFailure)
    }

    @Test
    fun parseTranscriptToOrder_skipsTableStopWordAndExtractsPlus91Phone() {
        val transcript = "One OG Vietnamese for table 3 +91 9876543210"
        val result = DeepgramVoiceService.getInstance().parseTranscriptToOrder(transcript, sampleMenu)

        assertTrue(result.isSuccess)
        val order = result.getOrThrow()
        assertEquals("Walk-in Customer", order.customerName)
        assertEquals("9876543210", order.contactNumber)
        assertEquals(1, order.items.find { it.menuItemName == "OG VIETNAMESE" }?.quantity)
    }

    @Test
    fun parseTranscriptToOrder_extractsHinglishCustomerNameAndFormattedPhone() {
        val transcript = "Ek Hazelnut Frappe Priya ke naam pe +91 98765-43210"
        val result = DeepgramVoiceService.getInstance().parseTranscriptToOrder(transcript, sampleMenu)

        assertTrue(result.isSuccess)
        val order = result.getOrThrow()
        assertEquals("Priya", order.customerName)
        assertEquals("9876543210", order.contactNumber)
        assertEquals(1, order.items.find { it.menuItemName == "HAZELNUT FRAPPE" }?.quantity)
    }

    @Test
    fun parseTranscriptToOrder_ignoresMenuWordAsCustomerNameAndUnavailableItems() {
        val mixedMenu = sampleMenu.map { item ->
            if (item.name == "PERI PERI FRIES") item.copy(isAvailable = false) else item
        }
        val transcript = "One OG Vietnamese and one peri peri fries for bombay"
        val result = DeepgramVoiceService.getInstance().parseTranscriptToOrder(transcript, mixedMenu)

        assertTrue(result.isSuccess)
        val order = result.getOrThrow()
        assertEquals("Walk-in Customer", order.customerName)
        assertEquals("", order.contactNumber)
        assertTrue(order.items.none { it.menuItemName == "PERI PERI FRIES" })

        val unavailableOnly = sampleMenu.map { it.copy(isAvailable = false) }
        val failureResult = DeepgramVoiceService.getInstance().parseTranscriptToOrder("One OG Vietnamese", unavailableOnly)
        assertTrue(failureResult.isFailure)
    }
}

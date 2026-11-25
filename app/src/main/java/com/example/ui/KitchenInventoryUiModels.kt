package com.example.ui

import com.example.data.entity.KitchenInventoryEntryEntity
import com.example.data.entity.KitchenInventoryItemEntity
import com.example.data.entity.KitchenInventoryShiftEntity
import com.example.domain.kitchen.KitchenReorderRecommendation
import com.example.domain.kitchen.KitchenSection
import com.example.domain.kitchen.KitchenShiftType
import com.example.domain.kitchen.KitchenStockStatus
import com.example.domain.kitchen.KitchenVarianceRow

data class KitchenSectionProgress(
    val section: KitchenSection,
    val totalItems: Int,
    val checkedItems: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int
) {
    val isPending: Boolean get() = totalItems > 0 && checkedItems < totalItems
    val isCompleted: Boolean get() = totalItems > 0 && checkedItems >= totalItems
}

data class KitchenInventoryDashboardSummary(
    val dateKey: String,
    val formattedDate: String,
    val staffName: String,
    val totalActiveItems: Int,
    val openingShift: KitchenInventoryShiftEntity?,
    val closingShift: KitchenInventoryShiftEntity?,
    val openingCheckedCount: Int,
    val closingCheckedCount: Int,
    val lowStockCount: Int,
    val outOfStockCount: Int,
    val openingPendingSections: List<KitchenSection>,
    val closingPendingSections: List<KitchenSection>,
    val reorderList: List<KitchenReorderRecommendation>,
    val varianceRows: List<KitchenVarianceRow>
) {
    val activeShiftProgressPercent: Int
        get() {
            if (totalActiveItems <= 0) return 0
            val checked = maxOf(openingCheckedCount, closingCheckedCount)
            return ((checked * 100) / totalActiveItems).coerceIn(0, 100)
        }
}

sealed class KitchenInventoryViewRoute {
    data object Dashboard : KitchenInventoryViewRoute()
    data class ShiftOverview(val shiftType: KitchenShiftType) : KitchenInventoryViewRoute()
    data class SectionCounting(
        val shiftType: KitchenShiftType,
        val sectionId: String?,
        val selectedItemId: String? = null
    ) : KitchenInventoryViewRoute()
    data class ShiftSubmittedConfirmation(val shiftType: KitchenShiftType) : KitchenInventoryViewRoute()
    data object ManagerHub : KitchenInventoryViewRoute()
}

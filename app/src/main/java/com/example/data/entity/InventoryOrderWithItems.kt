package com.example.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class InventoryOrderWithItems(
    @Embedded val order: InventoryOrderEntity,
    @Relation(
        parentColumn = "orderId",
        entityColumn = "orderId"
    )
    val items: List<InventoryOrderItemEntity>
)

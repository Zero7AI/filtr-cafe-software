package com.example.kitchen.testutil

import com.example.kitchen.data.dao.CustomerDao
import com.example.kitchen.data.dao.MenuDao
import com.example.kitchen.data.dao.OrderDao
import com.example.kitchen.data.entity.CustomerEntity
import com.example.kitchen.data.entity.MenuItemEntity
import com.example.kitchen.data.entity.OrderEntity
import com.example.kitchen.data.entity.OrderItemEntity
import com.example.kitchen.data.entity.OrderWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FakeOrderDao : OrderDao {
    private val ordersMap = linkedMapOf<String, OrderEntity>()
    private val itemsList = mutableListOf<OrderItemEntity>()
    private val nextItemId = AtomicInteger(1)
    private val snapshot = MutableStateFlow<List<OrderWithItems>>(emptyList())

    var failNextUpdate = false

    private fun refresh() {
        snapshot.value = ordersMap.values
            .sortedByDescending { it.timestamp }
            .map { order ->
                OrderWithItems(
                    order = order,
                    items = itemsList.filter { it.orderId == order.orderId }
                )
            }
    }

    override fun getAllOrdersWithItems(): Flow<List<OrderWithItems>> = snapshot

    override fun getPendingOrdersWithItems(): Flow<List<OrderWithItems>> =
        snapshot.map { list -> list.filter { !it.order.isMade }.sortedBy { it.order.timestamp } }

    override fun getCompletedTodayOrdersWithItems(startOfDayTimestamp: Long): Flow<List<OrderWithItems>> =
        snapshot.map { list ->
            list.filter { it.order.isMade && it.order.timestamp >= startOfDayTimestamp }
                .sortedByDescending { it.order.completedTimestamp }
        }

    override suspend fun getOrderById(orderId: String): OrderWithItems? =
        ordersMap[orderId]?.let { order ->
            OrderWithItems(order, itemsList.filter { it.orderId == orderId })
        }

    override suspend fun insertOrder(order: OrderEntity) {
        if (failNextUpdate) {
            failNextUpdate = false
            throw IllegalStateException("Simulated Room write error")
        }
        ordersMap[order.orderId] = order
        refresh()
    }

    override suspend fun insertOrderItems(items: List<OrderItemEntity>) {
        for (item in items) {
            val resolvedId = if (item.id == 0) nextItemId.getAndIncrement() else item.id
            itemsList.add(item.copy(id = resolvedId))
        }
        refresh()
    }

    override suspend fun deleteOrderItemsByOrderId(orderId: String) {
        itemsList.removeAll { it.orderId == orderId }
        refresh()
    }

    override suspend fun updateOrder(order: OrderEntity) {
        if (ordersMap.containsKey(order.orderId)) {
            ordersMap[order.orderId] = order
            refresh()
        }
    }

    override suspend fun updateOrderItemCompletedQuantity(itemId: Int, completedQty: Int, completedTimestamp: Long) {
        val idx = itemsList.indexOfFirst { it.id == itemId }
        if (idx >= 0) {
            itemsList[idx] = itemsList[idx].copy(
                completedQuantity = completedQty,
                completedTimestamp = completedTimestamp
            )
            refresh()
        }
    }

    override suspend fun markAllOrderItemsCompleted(orderId: String, completedTimestamp: Long) {
        for (i in itemsList.indices) {
            if (itemsList[i].orderId == orderId) {
                itemsList[i] = itemsList[i].copy(
                    completedQuantity = itemsList[i].quantity,
                    completedTimestamp = completedTimestamp
                )
            }
        }
        refresh()
    }

    override suspend fun resetAllOrderItemsCompleted(orderId: String) {
        for (i in itemsList.indices) {
            if (itemsList[i].orderId == orderId) {
                itemsList[i] = itemsList[i].copy(
                    completedQuantity = 0,
                    completedTimestamp = 0L
                )
            }
        }
        refresh()
    }

    override suspend fun updateMadeStatus(orderId: String, isMade: Boolean, completedTimestamp: Long) {
        if (failNextUpdate) {
            failNextUpdate = false
            throw IllegalStateException("Simulated Room write error")
        }
        ordersMap[orderId]?.let {
            ordersMap[orderId] = it.copy(
                isMade = isMade,
                completedTimestamp = completedTimestamp,
                syncStatus = "PENDING"
            )
            refresh()
        }
    }

    override suspend fun updatePaidStatus(orderId: String, isPaid: Boolean, paymentMode: String) {
        ordersMap[orderId]?.let {
            ordersMap[orderId] = it.copy(isPaid = isPaid, paymentMode = paymentMode, syncStatus = "PENDING")
            refresh()
        }
    }

    override suspend fun updateDeliveredStatus(orderId: String, isDelivered: Boolean) {
        ordersMap[orderId]?.let {
            ordersMap[orderId] = it.copy(isDelivered = isDelivered, syncStatus = "PENDING")
            refresh()
        }
    }

    override suspend fun updateSyncStatus(orderId: String, status: String) {
        ordersMap[orderId]?.let {
            ordersMap[orderId] = it.copy(syncStatus = status)
            refresh()
        }
    }

    override suspend fun updateSyncStatusForOrders(orderIds: List<String>, status: String) {
        for (id in orderIds) {
            ordersMap[id]?.let { ordersMap[id] = it.copy(syncStatus = status) }
        }
        refresh()
    }

    override suspend fun getUnsyncedOrders(): List<OrderWithItems> =
        snapshot.value.filter { it.order.syncStatus != "SYNCED" }

    override suspend fun deleteOrder(orderId: String) {
        ordersMap.remove(orderId)
        itemsList.removeAll { it.orderId == orderId }
        refresh()
    }

    override fun getPendingOrderCount(): Flow<Int> =
        snapshot.map { list -> list.count { !it.order.isMade } }

    override suspend fun getTodayOrderCount(startOfDayTimestamp: Long): Int =
        ordersMap.values.count { it.timestamp >= startOfDayTimestamp }

    override suspend fun getTodayOrderIds(startOfDayTimestamp: Long): List<String> =
        ordersMap.values.filter { it.timestamp >= startOfDayTimestamp }.map { it.orderId }

    override suspend fun getOrdersSince(startTimestamp: Long): List<OrderEntity> =
        ordersMap.values.filter { it.timestamp >= startTimestamp }
}

class FakeMenuDao(initialItems: List<MenuItemEntity> = emptyList()) : MenuDao {
    private val items = MutableStateFlow(initialItems)
    private val nextId = AtomicInteger((initialItems.maxOfOrNull { it.id } ?: 0) + 1)

    override fun getAllMenuItems(): Flow<List<MenuItemEntity>> =
        items.map { list -> list.sortedWith(compareBy({ it.category }, { it.name })) }

    override suspend fun getAllMenuItemsList(): List<MenuItemEntity> =
        items.value.sortedBy { it.id }

    override fun getAvailableMenuItems(): Flow<List<MenuItemEntity>> =
        items.map { list -> list.filter { it.isAvailable }.sortedWith(compareBy({ it.category }, { it.name })) }

    override suspend fun getMenuItemById(id: Int): MenuItemEntity? =
        items.value.find { it.id == id }

    override suspend fun insertMenuItem(item: MenuItemEntity): Long {
        val id = if (item.id == 0) nextId.getAndIncrement() else item.id
        val current = items.value.filterNot { it.id == id }.toMutableList()
        current.add(item.copy(id = id))
        items.value = current
        return id.toLong()
    }

    override suspend fun insertAll(items: List<MenuItemEntity>) {
        val map = this.items.value.associateBy { it.id }.toMutableMap()
        for (item in items) {
            val id = if (item.id == 0) nextId.getAndIncrement() else item.id
            map[id] = item.copy(id = id)
        }
        this.items.value = map.values.toList()
    }

    override suspend fun updateMenuItem(item: MenuItemEntity) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }

    override suspend fun deleteMenuItem(item: MenuItemEntity) {
        items.value = items.value.filterNot { it.id == item.id }
    }

    override suspend fun getMenuItemCount(): Int = items.value.size

    override suspend fun deleteAllMenuItems() {
        items.value = emptyList()
    }
}

class FakeCustomerDao : CustomerDao {
    private val customers = MutableStateFlow<List<CustomerEntity>>(emptyList())
    private val nextId = AtomicInteger(1)

    override fun getAllCustomers(): Flow<List<CustomerEntity>> =
        customers.map { it.sortedBy { c -> c.name } }

    override suspend fun searchCustomers(query: String): List<CustomerEntity> =
        customers.value.filter { it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.lastOrderTimestamp }
            .take(10)

    override suspend fun getCustomerByName(name: String): CustomerEntity? =
        customers.value.find { it.name.equals(name, ignoreCase = true) }

    override suspend fun getCustomerById(id: Int): CustomerEntity? =
        customers.value.find { it.id == id }

    override suspend fun insertCustomer(customer: CustomerEntity): Long {
        val existingByName = customers.value.find { it.name.equals(customer.name, ignoreCase = true) }
        val resolvedId = when {
            customer.id != 0 -> customer.id
            existingByName != null -> existingByName.id
            else -> nextId.getAndIncrement()
        }
        val mutable = customers.value.filterNot { it.id == resolvedId || it.name.equals(customer.name, ignoreCase = true) }.toMutableList()
        mutable.add(customer.copy(id = resolvedId))
        customers.value = mutable
        return resolvedId.toLong()
    }

    override suspend fun updateCustomer(customer: CustomerEntity) {
        customers.value = customers.value.map { if (it.id == customer.id) customer else it }
    }

    override suspend fun deleteCustomer(customer: CustomerEntity) {
        customers.value = customers.value.filterNot { it.id == customer.id }
    }

    override suspend fun incrementOrderStats(name: String, amount: Double, timestamp: Long, contact: String) {
        customers.value = customers.value.map { c ->
            if (c.name.equals(name, ignoreCase = true)) {
                c.copy(
                    totalOrders = c.totalOrders + 1,
                    totalSpent = c.totalSpent + amount,
                    lastOrderTimestamp = timestamp,
                    contactNumber = if (contact.isNotBlank()) contact else c.contactNumber
                )
            } else c
        }
    }
}

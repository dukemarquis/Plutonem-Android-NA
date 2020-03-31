package com.plutonem.ui.products

import com.plutonem.android.fluxc.model.OrderImmutableModel
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.android.fluxc.model.order.OrderStatus
import com.plutonem.android.fluxc.model.order.OrderStatus.PAYING
import com.plutonem.android.fluxc.model.order.OrderStatus.fromOrder
import com.plutonem.android.fluxc.store.OrderStore
import com.plutonem.utilities.LocaleManagerWrapper
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.util.DateTimeUtils
import java.util.*
import javax.inject.Inject

class EditOrderRepository
@Inject constructor(
        private val localeManagerWrapper: LocaleManagerWrapper,
        private val orderStore: OrderStore,
        private val orderUtils: OrderUtilsWrapper
) {
    private var order: OrderModel? = null
    private var orderSnapshotWhenEditorOpened: OrderModel? = null
    val id: Int
        get() = order!!.id
    val localBuyerId: Int
        get() = order!!.localBuyerId
    val remoteOrderId: Long
        get() = order!!.remoteOrderId
    val orderName: String
        get() = order!!.orderName
    val orderPhoneNumber: String
        get() = order!!.orderPhoneNumber
    val orderAddress: String
        get() = order!!.orderAddress
    val shopTitle: String
        get() = order!!.shopTitle
    val productName: String
        get() = order!!.productDetail
    val itemSalesPrice: String
        get() = order!!.itemSalesPrice
    val itemDistributionMode: String
        get() = order!!.itemDistributionMode
    val status: OrderStatus
        get() = fromOrder(getOrder())
    val number: Long
        get() = order!!.orderNumber

    private var locked = false

    fun update(action: (OrderModel) -> Boolean): Boolean {
        reportTransactionState(true)
        val result = action(order!!)
        reportTransactionState(false)
        return result
    }

    fun set(action: () -> OrderModel) {
        reportTransactionState(true)
        this.order = action()
        reportTransactionState(false)
    }

    @Synchronized
    private fun reportTransactionState(lock: Boolean) {
        if (lock && locked) {
            val message = "EditOrderRepository: Transaction is writing on a locked thread ${Arrays.toString(
                    Thread.currentThread().stackTrace
            )}"
            AppLog.e(T.EDITOR, message)
        }
        locked = lock
    }

    fun hasOrder() = order != null
    fun getOrder(): OrderImmutableModel? = order
    fun getEditableOrder() = order

    fun updateConfirmDateIfShouldBeConfirmedImmediately(order: OrderModel) {
        if (orderUtils.shouldConfirmImmediately(fromOrder(order), order.dateCreated)) {
            order.setDateCreated(DateTimeUtils.iso8601FromDate(localeManagerWrapper.getCurrentCalendar().time))
        }
    }

    fun isOrderConfirmable() = order?.let { orderUtils.isConfirmable(it) } ?: false

    fun saveSnapshot() {
        orderSnapshotWhenEditorOpened = order?.clone()
    }

    fun hasSnapshot() = orderSnapshotWhenEditorOpened != null

    fun updateStatusFromSnapshot(order: OrderModel) {
        // the user has just tapped on "BUY NOW" on an half-baked order, make sure to set the status back to the
        // original order's status as we could not proceed with the action
        reportTransactionState(true)
        order.setStatus(orderSnapshotWhenEditorOpened?.status ?: PAYING.toString())
        reportTransactionState(false)
    }

    fun orderHasEdits() = orderUtils.orderHasEdits(orderSnapshotWhenEditorOpened, order!!)

    fun loadOrderByLocalOrderId(orderId: Int) {
        reportTransactionState(true)
        order = orderStore.getOrderByLocalOrderId(orderId)
        reportTransactionState(false)
    }
}
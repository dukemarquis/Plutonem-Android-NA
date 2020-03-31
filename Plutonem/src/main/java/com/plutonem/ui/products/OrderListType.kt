package com.plutonem.ui.products

import com.plutonem.R
import com.plutonem.android.fluxc.model.order.OrderStatus

enum class OrderListType(val orderStatuses: List<OrderStatus>) {
    DELIVERING(listOf(OrderStatus.DELIVERING)),
    RECEIVING(listOf(OrderStatus.RECEIVING)),
    FINISHED(listOf(OrderStatus.FINISHED));

    val titleResId: Int
        get() = when (this) {
            DELIVERING -> R.string.order_list_delivering
            RECEIVING -> R.string.order_list_receiving
            FINISHED -> R.string.order_list_finished
        }

    companion object {
        fun fromOrderStatus(status: OrderStatus): OrderListType {
            return when (status) {
                OrderStatus.DELIVERING -> DELIVERING
                OrderStatus.RECEIVING -> RECEIVING
                OrderStatus.FINISHED, OrderStatus.UNKNOWN, OrderStatus.PAYING -> FINISHED
            }
        }
    }
}
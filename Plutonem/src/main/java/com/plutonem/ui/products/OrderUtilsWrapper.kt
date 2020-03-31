package com.plutonem.ui.products

import com.plutonem.android.fluxc.model.OrderImmutableModel
import com.plutonem.android.fluxc.model.order.OrderStatus
import dagger.Reusable
import javax.inject.Inject

/**
 * Injectable wrapper around OrderUtils.
 *
 * OrderUtils interface is consisted of static methods, which make the client code difficult to test/mock. Main purpose
 * of this wrapper is to make testing easier.
 *
 */
@Reusable
class OrderUtilsWrapper @Inject constructor() {
    fun isConfirmable(order: OrderImmutableModel) = OrderUtils.isConfirmable(order)

    fun shouldConfirmImmediately(orderStatus: OrderStatus, dateCreated: String) =
            OrderUtils.shouldConfirmImmediately(orderStatus, dateCreated)

    fun orderHasEdits(oldOrder: OrderImmutableModel?, newOrder: OrderImmutableModel) =
            OrderUtils.orderHasEdits(oldOrder, newOrder)
}
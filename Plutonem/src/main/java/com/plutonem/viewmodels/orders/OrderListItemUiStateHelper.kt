package com.plutonem.viewmodels.orders

import com.plutonem.R
import com.plutonem.android.fluxc.model.LocalOrRemoteId.LocalId
import com.plutonem.android.fluxc.model.LocalOrRemoteId.RemoteId
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.ui.prefs.AppPrefsWrapper
import com.plutonem.ui.utils.UiString
import com.plutonem.ui.utils.UiString.UiStringRes
import com.plutonem.ui.utils.UiString.UiStringText
import com.plutonem.viewmodels.orders.OrderListItemIdentifier.LocalOrderId
import com.plutonem.viewmodels.orders.OrderListItemIdentifier.RemoteOrderId
import com.plutonem.viewmodels.orders.OrderListItemType.OrderListItemUiState
import org.apache.commons.text.StringEscapeUtils
import javax.inject.Inject

/**
 * Helper class which encapsulates logic for creating UiStates for items in the OrdersList.
 */
class OrderListItemUiStateHelper @Inject constructor(private val appPrefsWrapper: AppPrefsWrapper) {
    fun createOrderListItemUiState(
            order: OrderModel
    ): OrderListItemUiState {
        val remoteOrderId = RemoteOrderId(RemoteId(order.remoteOrderId))
        val localOrderId = LocalOrderId(LocalId(order.id))
        val shopTitle = getShopTitle(order = order)
        val orderDetail = getOrderDetail(order = order)
        val itemUiData = OrderListItemUiStateData(
                remoteOrderId = remoteOrderId,
                localOrderId = localOrderId,
                shopTitle = shopTitle,
                productName = getProductName(order = order),
                orderDetail = orderDetail,
                disableRippleEffect = false
        )

        return OrderListItemUiState(
                data = itemUiData
        )
    }

    private fun getShopTitle(order: OrderModel): UiString {
        return if (order.shopTitle.isNotBlank()) {
            UiStringText(StringEscapeUtils.unescapeHtml4(order.shopTitle))
        } else UiStringRes(R.string.untitled_in_parentheses)
    }

    private fun getProductName(order: OrderModel): UiString {
        return if (order.productDetail.isNotBlank()) {
            UiStringText(StringEscapeUtils.unescapeHtml4(order.productDetail))
        } else UiStringRes(R.string.undetailed_in_parentheses)
    }

    private fun getOrderDetail(order: OrderModel): UiString {
        return if (order.orderDetail.isNotBlank()) {
            UiStringText(StringEscapeUtils.unescapeHtml4(order.orderDetail))
        } else UiStringRes(R.string.undetailed_in_parentheses)
    }
}
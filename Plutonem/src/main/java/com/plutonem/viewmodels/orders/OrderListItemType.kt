package com.plutonem.viewmodels.orders

import com.plutonem.android.fluxc.model.LocalOrRemoteId
import com.plutonem.ui.utils.UiString
import com.plutonem.viewmodels.orders.OrderListItemIdentifier.LocalOrderId
import com.plutonem.viewmodels.orders.OrderListItemIdentifier.RemoteOrderId
import com.plutonem.widgets.OrderListButtonType

sealed class OrderListItemType {
    class OrderListItemUiState(
            val data: OrderListItemUiStateData
    ) : OrderListItemType()

    class LoadingItem(val localOrRemoteId: LocalOrRemoteId, val options: LoadingItemOptions) : OrderListItemType()
    object EndListIndicatorItem : OrderListItemType()
}

sealed class LoadingItemOptions

object LoadingItemDefaultPost : LoadingItemOptions()

data class OrderListItemUiStateData(
        val remoteOrderId: RemoteOrderId,
        val localOrderId: LocalOrderId,
        val shopTitle: UiString?,
        val productName: UiString?,
        val orderDetail: UiString?,
        val disableRippleEffect: Boolean
)

sealed class OrderListItemProgressBar(val visibility: Boolean) {
    object Hidden : OrderListItemProgressBar(visibility = false)
    object Indeterminate : OrderListItemProgressBar(visibility = true)
    data class Determinate(val progress: Int) : OrderListItemProgressBar(visibility = true)
}

sealed class OrderListItemAction(val buttonType: OrderListButtonType, val onButtonClicked: (OrderListButtonType) -> Unit) {
    class SingleItem(buttonType: OrderListButtonType, onButtonClicked: (OrderListButtonType) -> Unit) :
            OrderListItemAction(buttonType, onButtonClicked)
}
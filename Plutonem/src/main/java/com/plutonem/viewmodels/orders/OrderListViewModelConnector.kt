package com.plutonem.viewmodels.orders

import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.ui.products.OrderListType

class OrderListViewModelConnector(
        val buyer: BuyerModel,
        val orderListType: OrderListType,
        val orderFetcher: OrderFetcher
)
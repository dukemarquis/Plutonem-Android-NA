package com.plutonem.ui.products

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.plutonem.R
import com.plutonem.ui.utils.UiHelpers
import com.plutonem.utilities.getDrawableFromAttribute
import com.plutonem.viewmodels.orders.OrderListItemType.OrderListItemUiState
import com.plutonem.viewmodels.orders.OrderListItemUiStateData
import com.plutonem.widgets.PNTextView

sealed class OrderListItemViewHolder(
        @LayoutRes layout: Int,
        parent: ViewGroup,
        private val uiHelpers: UiHelpers
) : RecyclerView.ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false)) {
    private val shopTitleTextView: PNTextView = itemView.findViewById(R.id.shop_title)
    private val orderDetailTextView: PNTextView = itemView.findViewById(R.id.order_detail)
    private val container: ConstraintLayout = itemView.findViewById(R.id.container)
    private val selectableBackground: Drawable? = parent.context.getDrawableFromAttribute(
            android.R.attr.selectableItemBackground
    )

    abstract fun onBind(item: OrderListItemUiState)

    class Standard(
            parent: ViewGroup,
            private val uiHelpers: UiHelpers
    ) : OrderListItemViewHolder(R.layout.order_list_item, parent, uiHelpers) {
        private val productNameTextView: PNTextView = itemView.findViewById(R.id.product_name)

        override fun onBind(item: OrderListItemUiState) {
            setBasicValues(item.data)

            uiHelpers.setTextOrHide(productNameTextView, item.data.productName)
        }
    }

    protected fun setBasicValues(data: OrderListItemUiStateData) {
        uiHelpers.setTextOrHide(shopTitleTextView, data.shopTitle)
        uiHelpers.setTextOrHide(orderDetailTextView, data.orderDetail)
        if (data.disableRippleEffect) {
            container.background = null
        } else {
            container.background = selectableBackground
        }
    }
}
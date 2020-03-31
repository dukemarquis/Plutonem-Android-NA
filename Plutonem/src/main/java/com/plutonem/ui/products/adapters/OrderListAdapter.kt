package com.plutonem.ui.products.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.plutonem.R
import com.plutonem.android.fluxc.model.LocalOrRemoteId.LocalId
import com.plutonem.android.fluxc.model.LocalOrRemoteId.RemoteId
import com.plutonem.ui.products.OrderListItemViewHolder
import com.plutonem.ui.products.OrderListViewLayoutType
import com.plutonem.ui.products.OrderListViewLayoutType.STANDARD
import com.plutonem.ui.utils.UiHelpers
import com.plutonem.viewmodels.orders.OrderListItemType
import com.plutonem.viewmodels.orders.OrderListItemType.*

private const val VIEW_TYPE_ORDER = 0
private const val VIEW_TYPE_ENDLIST_INDICATOR = 1
private const val VIEW_TYPE_LOADING = 2

class OrderListAdapter(
        context: Context,
        private val uiHelpers: UiHelpers
) : PagedListAdapter<OrderListItemType, ViewHolder>(OrderListDiffItemCallback) {
    private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
    private var itemLayoutType: OrderListViewLayoutType = OrderListViewLayoutType.defaultValue

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is EndListIndicatorItem -> VIEW_TYPE_ENDLIST_INDICATOR
            is OrderListItemUiState -> {
                when (itemLayoutType) {
                    STANDARD -> VIEW_TYPE_ORDER
                }
            }
            is LoadingItem, null -> {
                when (itemLayoutType) {
                    STANDARD -> VIEW_TYPE_LOADING
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ENDLIST_INDICATOR -> {
                val view = layoutInflater.inflate(R.layout.list_with_fab_endlist_indicator, parent, false)
                EndListViewHolder(view)
            }
            VIEW_TYPE_LOADING -> {
                val view = layoutInflater.inflate(R.layout.order_list_item_skeleton, parent, false)
                LoadingViewHolder(view)
            }
            VIEW_TYPE_ORDER -> {
                OrderListItemViewHolder.Standard(parent, uiHelpers)
            }
            else -> {
                // Fail fast if a new view type is added so the we can handle it
                throw IllegalStateException("The view type '$viewType' needs to be handled")
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // The only holders that require special setup are OrderListItemViewHolder and subclasses
        if (holder is OrderListItemViewHolder) {
            val item = getItem(position)
            assert(item is OrderListItemUiState) {
                "If we are presenting OrderViewHolder, the item has to be of type OrderListItemUiState " +
                        "for position: $position"
            }
            holder.onBind((item as OrderListItemUiState))
        }
    }

    fun updateItemLayoutType(updatedItemLayoutType: OrderListViewLayoutType): Boolean {
        if (updatedItemLayoutType == itemLayoutType) {
            return false
        }
        itemLayoutType = updatedItemLayoutType
        notifyDataSetChanged()
        return true
    }

    private class LoadingViewHolder(view: View) : ViewHolder(view)

    private class EndListViewHolder(view: View) : ViewHolder(view)
}

private val OrderListDiffItemCallback = object : DiffUtil.ItemCallback<OrderListItemType>() {
    override fun areItemsTheSame(oldItem: OrderListItemType, newItem: OrderListItemType): Boolean {
        if (oldItem is EndListIndicatorItem && newItem is EndListIndicatorItem) {
            return true
        }
        if (oldItem is LoadingItem && newItem is LoadingItem) {
            return oldItem.localOrRemoteId == newItem.localOrRemoteId
        }
        if (oldItem is OrderListItemUiState && newItem is OrderListItemUiState) {
            return oldItem.data.localOrderId == newItem.data.localOrderId
        }
        if (oldItem is LoadingItem && newItem is OrderListItemUiState) {
            return when (oldItem.localOrRemoteId) {
                is LocalId -> oldItem.localOrRemoteId == newItem.data.localOrderId.id
                is RemoteId -> oldItem.localOrRemoteId == newItem.data.remoteOrderId.id
            }
        }
        return false
    }

    override fun areContentsTheSame(oldItem: OrderListItemType, newItem: OrderListItemType): Boolean {
        if (oldItem is EndListIndicatorItem && newItem is EndListIndicatorItem) {
            return true
        }
        if (oldItem is LoadingItem && newItem is LoadingItem) {
            return when (oldItem.localOrRemoteId) {
                is LocalId -> oldItem.localOrRemoteId.value == (newItem.localOrRemoteId as? LocalId)?.value
                is RemoteId -> oldItem.localOrRemoteId.value == (newItem.localOrRemoteId as? RemoteId)?.value
            }
        }
        if (oldItem is OrderListItemUiState && newItem is OrderListItemUiState) {
            return oldItem.data == newItem.data
        }
        return false
    }
}
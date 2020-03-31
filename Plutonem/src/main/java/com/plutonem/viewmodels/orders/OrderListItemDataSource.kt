package com.plutonem.viewmodels.orders

import com.plutonem.android.fluxc.Dispatcher
import com.plutonem.android.fluxc.generated.OrderActionBuilder
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.android.fluxc.model.LocalOrRemoteId
import com.plutonem.android.fluxc.model.LocalOrRemoteId.LocalId
import com.plutonem.android.fluxc.model.LocalOrRemoteId.RemoteId
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.android.fluxc.model.list.OrderListDescriptor
import com.plutonem.android.fluxc.model.list.datasource.ListItemDataSourceInterface
import com.plutonem.android.fluxc.store.OrderStore
import com.plutonem.android.fluxc.store.OrderStore.FetchOrderListPayload
import com.plutonem.viewmodels.orders.OrderListItemIdentifier.*
import com.plutonem.viewmodels.orders.OrderListItemType.*

sealed class OrderListItemIdentifier {
    data class LocalOrderId(val id: LocalId) : OrderListItemIdentifier()
    data class RemoteOrderId(val id: RemoteId) : OrderListItemIdentifier()
    object EndListIndicatorIdentifier : OrderListItemIdentifier()
}

class OrderListItemDataSource(
        private val dispatcher: Dispatcher,
        private val orderStore: OrderStore,
        private val orderFetcher: OrderFetcher,
        private val transform: (OrderModel) -> OrderListItemUiState
) : ListItemDataSourceInterface<OrderListDescriptor, OrderListItemIdentifier, OrderListItemType> {
    override fun fetchList(listDescriptor: OrderListDescriptor, offset: Long) {
        val fetchOrderListPayload = FetchOrderListPayload(listDescriptor, offset)
        dispatcher.dispatch(OrderActionBuilder.newFetchOrderListAction(fetchOrderListPayload))
    }

    override fun getItemIdentifiers(
            listDescriptor: OrderListDescriptor,
            remoteItemIds: List<RemoteId>,
            isListFullyFetched: Boolean
    ): List<OrderListItemIdentifier> {
        val localItems = orderStore.getLocalOrderIdsForDescriptor(listDescriptor)
                .map { LocalOrderId(id = it) }
        val remoteItems = remoteItemIds.map { RemoteOrderId(id = it) }
        val actualItems: List<OrderListItemIdentifier>

        actualItems = localItems + remoteItems

        // We only want to show the end list indicator if the list is fully fetched and it's not empty
        return if (isListFullyFetched && actualItems.isNotEmpty()) {
            (actualItems + listOf(EndListIndicatorIdentifier))
        } else actualItems
    }

    override fun getItemsAndFetchIfNecessary(
            listDescriptor: OrderListDescriptor,
            itemIdentifiers: List<OrderListItemIdentifier>
    ): List<OrderListItemType> {
        val localOrRemoteIds = localOrRemoteIdsFromOrderListItemIds(itemIdentifiers)
        val orderList = orderStore.getOrdersByLocalOrRemoteOrderIds(localOrRemoteIds, listDescriptor.buyer)

        // Convert the order list into 2 maps with local and remote ids as keys
        val localOrderMap = orderList.associateBy { LocalId(it.id) }
        val remoteOrderMap = orderList.filter { it.remoteOrderId != 0L }.associateBy { RemoteId(it.remoteOrderId) }

        fetchMissingRemoteOrders(listDescriptor.buyer, localOrRemoteIds, remoteOrderMap)

        return itemIdentifiers.map { identifier ->
            when (identifier) {
                is LocalOrderId -> transformToOrderListItemType(identifier.id, localOrderMap[identifier.id])
                is RemoteOrderId -> transformToOrderListItemType(identifier.id, remoteOrderMap[identifier.id])
                EndListIndicatorIdentifier -> EndListIndicatorItem
            }
        }
    }

    private fun localOrRemoteIdsFromOrderListItemIds(
            itemIdentifiers: List<OrderListItemIdentifier>
    ): List<LocalOrRemoteId> {
        val localOrRemoteIds = itemIdentifiers.mapNotNull {
            when (it) {
                is LocalOrderId -> it.id
                is RemoteOrderId -> it.id
                // We are creating a list of local and remote ids, so other type of identifiers don't matter
                EndListIndicatorIdentifier -> null
            }
        }
        return localOrRemoteIds
    }

    private fun fetchMissingRemoteOrders(
            buyer: BuyerModel,
            localOrRemoteIds: List<LocalOrRemoteId>,
            remoteOrderMap: Map<RemoteId, OrderModel>
    ) {
        val remoteIdsToFetch: List<RemoteId> = localOrRemoteIds.mapNotNull { it as? RemoteId }
                .filter { !remoteOrderMap.containsKey(it) }
        orderFetcher.fetchOrders(buyer, remoteIdsToFetch)
    }

    private fun transformToOrderListItemType(localOrRemoteId: LocalOrRemoteId, post: OrderModel?): OrderListItemType =
            if (post == null) {
                // If the post is not in cache, that means we'll be loading it
                val options = LoadingItemDefaultPost
                LoadingItem(localOrRemoteId, options)
            } else {
                transform(post)
            }
}
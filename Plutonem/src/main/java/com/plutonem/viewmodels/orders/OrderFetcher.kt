package com.plutonem.viewmodels.orders

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.plutonem.android.fluxc.Dispatcher
import com.plutonem.android.fluxc.generated.OrderActionBuilder
import com.plutonem.android.fluxc.model.BuyerModel
import com.plutonem.android.fluxc.model.CauseOfOnOrderChanged.UpdateOrder
import com.plutonem.android.fluxc.model.LocalOrRemoteId.RemoteId
import com.plutonem.android.fluxc.model.OrderModel
import com.plutonem.android.fluxc.store.OrderStore.OnOrderChanged
import com.plutonem.android.fluxc.store.OrderStore.RemoteOrderPayload
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Class which takes care of dispatching fetch order events while ignoring duplicate requests.
 */
class OrderFetcher constructor(
        private val lifecycle: Lifecycle,
        private val dispatcher: Dispatcher
) : LifecycleObserver {
    private val ongoingRequests = HashSet<RemoteId>()

    init {
        dispatcher.register(this)
        lifecycle.addObserver(this)
    }

    /**
     * Handles the [Lifecycle.Event.ON_DESTROY] event to cleanup the registration for dispatcher and removing the
     * observer for lifecycle.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private fun onDestroy() {
        lifecycle.removeObserver(this)
        dispatcher.unregister(this)
    }

    // TODO: We should implement batch fetching when it's available in the API
    fun fetchOrders(buyer: BuyerModel, remoteItemIds: List<RemoteId>) {
        remoteItemIds
                .filter {
                    // ignore duplicate requests
                    !ongoingRequests.contains(it)
                }
                .forEach { remoteId ->
                    ongoingRequests.add(remoteId)

                    val orderToFetch = OrderModel()
                    orderToFetch.setRemoteOrderId(remoteId.value)
                    dispatcher.dispatch(OrderActionBuilder.newFetchOrderAction(RemoteOrderPayload(orderToFetch, buyer)))
                }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onOrderChanged(event: OnOrderChanged) {
        (event.causeOfChange as? UpdateOrder)?.let { updateOrderCauseOfChange ->
            ongoingRequests.remove(RemoteId(updateOrderCauseOfChange.remoteOrderId))
        }
    }
}
package com.plutonem.ui.submits;

import android.os.AsyncTask;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.plutonem.Plutonem;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.generated.OrderActionBuilder;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.fluxc.model.OrderModel;
import com.plutonem.android.fluxc.model.order.OrderStatus;
import com.plutonem.android.fluxc.store.BuyerStore;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderChanged;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderSubmitted;
import com.plutonem.android.fluxc.store.OrderStore.RemoteOrderPayload;
import com.plutonem.ui.submits.OrderEvents.OrderSubmitStarted;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.util.ArrayList;

import javax.inject.Inject;

public class OrderSubmitHandler implements SubmitHandler<OrderModel> {
    private static ArrayList<OrderModel> sQueuedOrdersList = new ArrayList<>();
    private static OrderModel sCurrentSubmittingOrder = null;

    private SubmitOrderTask mCurrentTask = null;

    @Inject
    Dispatcher mDispatcher;
    @Inject
    BuyerStore mBuyerStore;
    @Inject
    SubmitActionUseCase mSubmitActionUseCase;

    OrderSubmitHandler() {
        ((Plutonem) Plutonem.getContext().getApplicationContext()).component().inject(this);
        AppLog.i(T.PRODUCTS, "OrderSubmitHandler > Created");
        mDispatcher.register(this);
    }

    void unregister() {
        mDispatcher.unregister(this);
    }

    @Override
    public boolean hasInProgressSubmits() {
        return mCurrentTask != null || !sQueuedOrdersList.isEmpty();
    }

    @Override
    public void cancelInProgressSubmits() {
        if (mCurrentTask != null) {
            AppLog.i(T.PRODUCTS, "OrderSubmitHandler > Cancelling current submit task");
            mCurrentTask.cancel(true);
        }
    }

    @Override
    public void submit(@NonNull OrderModel order) {
        synchronized (sQueuedOrdersList) {
            // first check whether there was an old version of this Order still enqueued waiting
            // for being submitted
            for (OrderModel queuedOrder : sQueuedOrdersList) {
                if (queuedOrder.getId() == order.getId()) {
                    // we found an older version, so let's remove it and replace it with the newest copy
                    sQueuedOrdersList.remove(queuedOrder);
                    break;
                }
            }
            sQueuedOrdersList.add(order);
        }
        submitNextOrder();
    }

    private void submitNextOrder() {
        synchronized (sQueuedOrdersList) {
            if (mCurrentTask == null) { // make sure nothing is running
                sCurrentSubmittingOrder = null;
                if (sQueuedOrdersList.size() > 0) {
                    sCurrentSubmittingOrder = sQueuedOrdersList.remove(0);
                    mCurrentTask = new SubmitOrderTask();
                    mCurrentTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, sCurrentSubmittingOrder);
                } else {
                    AppLog.i(T.PRODUCTS, "OrderSubmitHandler > Completed");
                }
            }
        }
    }

    private void finishSubmit() {
        synchronized (sQueuedOrdersList) {
            mCurrentTask = null;
            sCurrentSubmittingOrder = null;
        }
        submitNextOrder();
    }

    private enum SubmitOrderTaskResult {
        PUSH_ORDER_DISPATCHED, ERROR, NOTHING_TO_SUBMIT
    }

    private class SubmitOrderTask extends AsyncTask<OrderModel, Boolean, SubmitOrderTaskResult> {
        private OrderModel mOrder;
        private BuyerModel mBuyer;

        @Override
        protected void onPostExecute(SubmitOrderTaskResult result) {
            switch (result) {
                case ERROR:
                case NOTHING_TO_SUBMIT:
                    finishSubmit();
                    break;
                case PUSH_ORDER_DISPATCHED:
                    // will be handled in OnOrderChanged
                    break;
            }
        }

        @Override
        protected SubmitOrderTaskResult doInBackground(OrderModel... orders) {
            mOrder = orders[0];

            mBuyer = mBuyerStore.getBuyerByLocalId(mOrder.getLocalBuyerId());
            if (mBuyer == null) {
                return SubmitOrderTaskResult.ERROR;
            }

            if (TextUtils.isEmpty(mOrder.getStatus())) {
                mOrder.setStatus(OrderStatus.DELIVERING.toString());
            }

            EventBus.getDefault().post(new OrderSubmitStarted(mOrder));

            RemoteOrderPayload payload = new RemoteOrderPayload(mOrder, mBuyer);

            switch (mSubmitActionUseCase.getSubmitAction(mOrder)) {
                case SUBMIT:
                    AppLog.d(T.PRODUCTS, "OrderSubmitHandler - SUBMIT. Order: " + mOrder.getShopTitle() + " : " + mOrder.getProductDetail());
                    mDispatcher.dispatch(OrderActionBuilder.newSignInfoAction(payload));
                    break;
                case SUBMIT_AS_PAYING:
                    mOrder.setStatus(OrderStatus.PAYING.toString());
                    AppLog.d(T.PRODUCTS, "OrderSubmitHandler - SUBMIT_AS_PAYING. Order: " + mOrder.getShopTitle() + " : " + mOrder.getProductDetail());
                    return SubmitOrderTaskResult.ERROR;
                case DO_NOTHING:
                    AppLog.d(T.PRODUCTS, "OrderSubmitHandler - DO_NOTHING. Order: " + mOrder.getShopTitle() + " : " + mOrder.getProductDetail());
                    // A single order might be enqueued twice for submit. It might cause some side-effects when the
                    // order is a local draft.
                    // The first submit request pushes the order to the server and sets `isLocalDraft` to `false`.
                    // The second request would have invoked nothing to compare with the `when` condition in SubmitActionUseCase.
                    // This branch takes care of this situations and simply ignores the second request.
                    return SubmitOrderTaskResult.NOTHING_TO_SUBMIT;
            }
            return SubmitOrderTaskResult.PUSH_ORDER_DISPATCHED;
        }
    }

    /**
     * Has priority 9 on OnOrderSubmitted events, which ensures that OrderSubmitHandler is the first to receive
     * and process OnOrderSubmitted events, before they trickle down to other subscribers.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, priority = 9)
    public void onOrderSubmitted(OnOrderSubmitted event) {
        if (event.isError()) {
            AppLog.w(T.PRODUCTS, "OrderSubmitHandler > Order submit failed. " + event.error.type + ": "
                    + event.error.message);
        } else {
            synchronized (sQueuedOrdersList) {
                for (OrderModel order : sQueuedOrdersList) {
                    if (order.getId() == event.order.getId()) {
                        // Check if a new version of the order we've just uploaded is in the queue and update its state
                        order.setRemoteOrderId(event.order.getRemoteOrderId());
                        order.setIsLocalDraft(false);
                    }
                }
            }
        }

        finishSubmit();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, priority = 9)
    public void onOrderChanged(OnOrderChanged event) {
    }
}

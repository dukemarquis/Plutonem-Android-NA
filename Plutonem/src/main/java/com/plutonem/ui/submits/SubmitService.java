package com.plutonem.ui.submits;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plutonem.Plutonem;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.model.OrderModel;
import com.plutonem.android.fluxc.store.OrderStore;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderChanged;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderSubmitted;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import javax.inject.Inject;

public class SubmitService extends Service {
    private static final String KEY_LOCAL_ORDER_ID = "localOrderId";

    private static @Nullable
    SubmitService sInstance;

    private OrderSubmitHandler mOrderUploadHandler;

    @Inject
    Dispatcher mDispatcher;
    @Inject
    OrderStore mOrderStore;

    @Override
    public void onCreate() {
        super.onCreate();
        ((Plutonem) getApplication()).component().inject(this);
        AppLog.i(T.MAIN, "SubmitService > Created");
        mDispatcher.register(this);
        sInstance = this;

        if (mOrderUploadHandler == null) {
            mOrderUploadHandler = new OrderSubmitHandler();
        }
    }

    @Override
    public void onDestroy() {
        if (mOrderUploadHandler != null) {
            mOrderUploadHandler.cancelInProgressSubmits();
            mOrderUploadHandler.unregister();
        }

        mDispatcher.unregister(this);
        sInstance = null;
        AppLog.i(T.MAIN, "SubmitService > Destroyed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Skip this request if no items to submit were given
        if (intent == null || (!intent.hasExtra(KEY_LOCAL_ORDER_ID))) {
            AppLog.e(T.MAIN, "SubmitService > Killed and restarted with an empty intent");
            stopServiceIfSubmitsComplete();
            return START_NOT_STICKY;
        }

        if (intent.hasExtra(KEY_LOCAL_ORDER_ID)) {
            unpackOrderIntent(intent);
        }

        return START_REDELIVER_INTENT;
    }

    private void unpackOrderIntent(@NonNull Intent intent) {
        OrderModel order = mOrderStore.getOrderByLocalOrderId(intent.getIntExtra(KEY_LOCAL_ORDER_ID, 0));
        if (order != null) {
            mOrderUploadHandler.submit(order);
        }
    }

    /**
     * Adds a order to the queue.
     *
     * @param orderId
     */
    public static void submitOrder(Context context, int orderId) {
        Intent intent = new Intent(context, SubmitService.class);
        intent.putExtra(KEY_LOCAL_ORDER_ID, orderId);
        context.startService(intent);
    }

    private synchronized void stopServiceIfSubmitsComplete() {
        stopServiceIfSubmitsComplete(null, null);
    }

    private synchronized void stopServiceIfSubmitsComplete(Boolean isError, OrderModel order) {
        if (mOrderUploadHandler != null && mOrderUploadHandler.hasInProgressSubmits()) {
            return;
        }

        AppLog.i(T.MAIN, "SubmitService > Completed");
        stopSelf();
    }

    /**
     * Has lower priority than the OrderSubmitHandler, which ensures that the handler has already received and
     * processed this OnOrderSubmitted event. This means we can safely rely on its internal state being up to date.
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, priority = 7)
    public void onOrderSubmitted(OnOrderSubmitted event) {
        stopServiceIfSubmitsComplete(event.isError(), event.order);
    }

    /**
     * Has lower priority than the OrderSubmitHandler, which ensures that the handler has already received and
     * processed this OnOrderChanged event. This means we can safely rely on its internal state being up to date.
     */
    @Subscribe(threadMode = ThreadMode.MAIN, priority = 7)
    public void onOrderChanged(OnOrderChanged event) {
    }
}

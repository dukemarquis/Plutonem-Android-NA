package com.plutonem.ui.nemur.services.order;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.plutonem.models.NemurTag;
import com.plutonem.ui.nemur.NemurEvents;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.greenrobot.eventbus.EventBus;
import org.wordpress.android.util.AppLog;

import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_ACTION;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction;

/**
 * service which updates orders with specific tags or in specific buyers - relies on
 * EventBus to alert of update status
 */
public class NemurOrderService extends Service implements ServiceCompletionListener {
    private NemurOrderLogic mNemurOrderLogic;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurOrderLogic = new NemurOrderLogic(this);
        AppLog.i(AppLog.T.NEMUR, "nemur order service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur order service > destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        UpdateAction action;
        if (intent.hasExtra(ARG_ACTION)) {
            action = (UpdateAction) intent.getSerializableExtra(ARG_ACTION);
        } else {
            action = UpdateAction.REQUEST_NEWER;
        }

        EventBus.getDefault().post(new NemurEvents.UpdateOrdersStarted(action));

        if (intent.hasExtra(ARG_TAG)) {
            NemurTag tag = (NemurTag) intent.getSerializableExtra(ARG_TAG);
            mNemurOrderLogic.performTask(null, action, tag);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onCompleted(Object companion) {
        stopSelf();
    }
}

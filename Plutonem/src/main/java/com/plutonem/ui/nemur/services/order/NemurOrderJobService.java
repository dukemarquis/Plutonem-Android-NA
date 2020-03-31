package com.plutonem.ui.nemur.services.order;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.RequiresApi;

import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagType;
import com.plutonem.ui.nemur.NemurEvents;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.greenrobot.eventbus.EventBus;
import org.wordpress.android.util.AppLog;

import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_ACTION;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG_PARAM_DISPLAY_NAME;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG_PARAM_ENDPOINT;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG_PARAM_SLUG;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG_PARAM_TAGTYPE;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.ARG_TAG_PARAM_TITLE;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction;

/**
 * service which updates orders with specific tags or in specific buyers - relies on
 * EventBus to alert of update status
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NemurOrderJobService extends JobService implements ServiceCompletionListener {
    private NemurOrderLogic mNemurOrderLogic;

    @Override public boolean onStartJob(JobParameters params) {
        AppLog.i(AppLog.T.NEMUR, "nemur order job service > started");
        NemurOrderServiceStarter.UpdateAction action;
        if (params.getExtras() != null) {
            if (params.getExtras().containsKey(ARG_ACTION)) {
                action = UpdateAction.values()[(Integer) params.getExtras().get(ARG_ACTION)];
            } else {
                action = UpdateAction.REQUEST_NEWER;
            }

            EventBus.getDefault().post(new NemurEvents.UpdateOrdersStarted(action));

            if (params.getExtras().containsKey(ARG_TAG_PARAM_SLUG)) {
                NemurTag tag = getNemurTagFromBundleParams(params.getExtras());
                mNemurOrderLogic.performTask(params, action, tag);
            }
        }
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        AppLog.i(AppLog.T.NEMUR, "nemur order job service > stopped");
        jobFinished(params, false);
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurOrderLogic = new NemurOrderLogic(this);
        AppLog.i(AppLog.T.NEMUR, "nemur order job service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur order job service > destroyed");
        super.onDestroy();
    }

    @Override
    public void onCompleted(Object companion) {
        AppLog.i(AppLog.T.NEMUR, "nemur order job service > all tasks completed");
        jobFinished((JobParameters) companion, false);
    }

    private NemurTag getNemurTagFromBundleParams(PersistableBundle bundle) {
        String slug = bundle.getString(ARG_TAG_PARAM_SLUG);
        String displayName = bundle.getString(ARG_TAG_PARAM_DISPLAY_NAME);
        String title = bundle.getString(ARG_TAG_PARAM_TITLE);
        String endpoint = bundle.getString(ARG_TAG_PARAM_ENDPOINT);
        int tagType = bundle.getInt(ARG_TAG_PARAM_TAGTYPE);
        NemurTag tag = new NemurTag(slug, displayName, title, endpoint, NemurTagType.fromInt(tagType));
        return tag;
    }
}

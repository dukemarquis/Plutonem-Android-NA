package com.plutonem.ui.nemur.services.search;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.StringUtils;

/**
 * service which searches for nemur orders on plutonem
 */

public class NemurSearchService extends Service implements ServiceCompletionListener {
    private static final String ARG_QUERY = "query";
    private static final String ARG_OFFSET = "offset";

    private NemurSearchLogic mNemurSearchLogic;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurSearchLogic = new NemurSearchLogic(this);
        AppLog.i(AppLog.T.NEMUR, "nemur search service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur search service > destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String query = StringUtils.notNullStr(intent.getStringExtra(ARG_QUERY));
        int offset = intent.getIntExtra(ARG_OFFSET, 0);
        mNemurSearchLogic.startSearch(query, offset, null);

        return START_NOT_STICKY;
    }

    @Override
    public void onCompleted(Object companion) {
        AppLog.i(AppLog.T.NEMUR, "nemur search service > all tasks completed");
        stopSelf();
    }
}

package com.plutonem.ui.nemur.services.update;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.plutonem.Plutonem;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.wordpress.android.util.AppLog;

import java.util.EnumSet;

import static com.plutonem.ui.nemur.services.update.NemurUpdateServiceStarter.ARG_UPDATE_TASKS;

public class NemurUpdateService extends Service implements ServiceCompletionListener {
    /***
     * service which updates default tags and buyers for the Nemur, relies
     * on EventBus to notify of changes
     */

    private NemurUpdateLogic mNemurUpdateLogic;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurUpdateLogic = new NemurUpdateLogic(this, (Plutonem) getApplication(), this);
        AppLog.i(AppLog.T.NEMUR, "nemur service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur service > destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(ARG_UPDATE_TASKS)) {
            //noinspection unchecked
            EnumSet<NemurUpdateLogic.UpdateTask> tasks = (EnumSet<NemurUpdateLogic.UpdateTask>)
                    intent.getSerializableExtra(ARG_UPDATE_TASKS);
            mNemurUpdateLogic.performTasks(tasks, null);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onCompleted(Object companion) {
        AppLog.i(AppLog.T.NEMUR, "nemur service > all tasks completed");
        stopSelf();
    }
}


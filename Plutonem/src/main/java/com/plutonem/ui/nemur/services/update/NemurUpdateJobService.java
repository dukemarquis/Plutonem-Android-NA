package com.plutonem.ui.nemur.services.update;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.plutonem.Plutonem;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.wordpress.android.util.AppLog;

import java.util.EnumSet;

import static com.plutonem.ui.nemur.services.update.NemurUpdateServiceStarter.ARG_UPDATE_TASKS;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NemurUpdateJobService extends JobService implements ServiceCompletionListener {
    private NemurUpdateLogic mNemurUpdateLogic;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onStartJob(JobParameters params) {
        AppLog.i(AppLog.T.NEMUR, "nemur job service > started");
        if (params.getExtras() != null && params.getExtras().containsKey(ARG_UPDATE_TASKS)) {
            int[] tmp = (int[]) params.getExtras().get(ARG_UPDATE_TASKS);
            EnumSet<NemurUpdateLogic.UpdateTask> tasks = EnumSet.noneOf(NemurUpdateLogic.UpdateTask.class);
            for (int i : tmp) {
                tasks.add(NemurUpdateLogic.UpdateTask.values()[i]);
            }
            mNemurUpdateLogic.performTasks(tasks, params);
        }
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public boolean onStopJob(JobParameters params) {
        AppLog.i(AppLog.T.NEMUR, "nemur job service > stopped");
        jobFinished(params, false);
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurUpdateLogic = new NemurUpdateLogic(this, (Plutonem) getApplication(), this);
        AppLog.i(AppLog.T.NEMUR, "nemur job service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur job service > destroyed");
        super.onDestroy();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCompleted(Object companion) {
        AppLog.i(AppLog.T.NEMUR, "reader job service > all tasks completed");
        jobFinished((JobParameters) companion, false);
    }
}

package com.plutonem.ui.nemur.services.search;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.wordpress.android.util.AppLog;

import static com.plutonem.ui.nemur.services.search.NemurSearchServiceStarter.ARG_OFFSET;
import static com.plutonem.ui.nemur.services.search.NemurSearchServiceStarter.ARG_QUERY;

/**
 * service which searches for nemur orders on plutonem
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class NemurSearchJobService extends JobService implements ServiceCompletionListener {
    private NemurSearchLogic mNemurSearchLogic;

    @Override public boolean onStartJob(JobParameters params) {
        if (params.getExtras() != null && params.getExtras().containsKey(ARG_QUERY)) {
            String query = params.getExtras().getString(ARG_QUERY);
            int offset = params.getExtras().getInt(ARG_OFFSET, 0);
            mNemurSearchLogic.startSearch(query, offset, params);
        }

        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        jobFinished(params, false);
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNemurSearchLogic = new NemurSearchLogic(this);
        AppLog.i(AppLog.T.NEMUR, "nemur search job service > created");
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.NEMUR, "nemur search job service > destroyed");
        super.onDestroy();
    }

    @Override
    public void onCompleted(Object companion) {
        AppLog.i(AppLog.T.NEMUR, "nemur search job service > all tasks completed");
        jobFinished((JobParameters) companion, false);
    }
}

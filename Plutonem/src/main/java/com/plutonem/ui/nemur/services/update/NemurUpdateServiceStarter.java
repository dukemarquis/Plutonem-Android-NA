package com.plutonem.ui.nemur.services.update;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;

import org.wordpress.android.util.AppLog;

import java.util.EnumSet;

import static com.plutonem.JobServiceId.JOB_NEMUR_UPDATE_SERVICE_ID;

/*
 * this class provides a way to decide which kind of Service to start, depending on the platform we're running on
 */
public class NemurUpdateServiceStarter {
    public static final String ARG_UPDATE_TASKS = "update_tasks";

    public static void startService(Context context, EnumSet<NemurUpdateLogic.UpdateTask> tasks) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (context == null || tasks == null || tasks.size() == 0) {
                return;
            }
            Intent intent = new Intent(context, NemurUpdateService.class);
            intent.putExtra(ARG_UPDATE_TASKS, tasks);
            context.startService(intent);
        } else {
            // schedule the JobService here for API >= 26. The JobScheduler is available since API 21, but
            // it's preferable to use it only since enforcement in API 26 to not break any old behavior
            ComponentName componentName = new ComponentName(context, NemurUpdateJobService.class);

            PersistableBundle extras = new PersistableBundle();
            extras.putIntArray(ARG_UPDATE_TASKS, getIntArrayFromEnumSet(tasks));

            JobInfo jobInfo = new JobInfo.Builder(JOB_NEMUR_UPDATE_SERVICE_ID, componentName)
                    .setRequiresCharging(false)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(0) // if possible, try to run right away
                    .setExtras(extras)
                    .build();

            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            int resultCode = jobScheduler.schedule(jobInfo);
            if (resultCode == JobScheduler.RESULT_SUCCESS) {
                AppLog.i(AppLog.T.NEMUR, "nemur service > job scheduled");
            } else {
                AppLog.e(AppLog.T.NEMUR, "nemur service > job could not be scheduled");
            }
        }
    }

    private static int[] getIntArrayFromEnumSet(EnumSet<NemurUpdateLogic.UpdateTask> enumSet) {
        int[] ordinals2 = new int[enumSet.size()];
        int index = 0;
        for (NemurUpdateLogic.UpdateTask e : enumSet) {
            ordinals2[index++] = e.ordinal();
        }
        return ordinals2;
    }
}

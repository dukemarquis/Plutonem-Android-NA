package com.plutonem.ui.nemur.services.order;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;

import androidx.annotation.RequiresApi;

import com.plutonem.models.NemurTag;

import org.wordpress.android.util.AppLog;

public class NemurOrderServiceStarter {
    private static final int JOB_READER_POST_SERVICE_ID_TAG = 4001;
    public static final String ARG_TAG = "tag";
    public static final String ARG_ACTION = "action";

    public static final String ARG_TAG_PARAM_SLUG = "tag-slug";
    public static final String ARG_TAG_PARAM_DISPLAY_NAME = "tag-display-name";
    public static final String ARG_TAG_PARAM_TITLE = "tag-title";
    public static final String ARG_TAG_PARAM_ENDPOINT = "tag-endpoint";
    public static final String ARG_TAG_PARAM_TAGTYPE = "tag-type";

    public enum UpdateAction {
        REQUEST_NEWER, // request the newest orders for this tag/buyer
        REQUEST_REFRESH, // request fresh data and get rid of the rest
        REQUEST_OLDER, // request orders older than the oldest existing one for this tag/buyer
        REQUEST_OLDER_THAN_GAP // request orders older than the one with the gap marker for this tag
    }

    /*
     * update orders with the passed tag
     */
    public static void startServiceForTag(Context context, NemurTag tag, UpdateAction action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Intent intent = new Intent(context, NemurOrderService.class);
            intent.putExtra(ARG_TAG, tag);
            intent.putExtra(ARG_ACTION, action);
            context.startService(intent);
        } else {
            PersistableBundle extras = new PersistableBundle();
            extras.putInt(ARG_ACTION, action.ordinal());
            putNemurTagExtras(extras, tag);
            doScheduleJobWithBundle(context, extras, JOB_READER_POST_SERVICE_ID_TAG);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void doScheduleJobWithBundle(Context context, PersistableBundle extras, int jobId) {
        // schedule the JobService here for API >= 26. The JobScheduler is available since API 21, but
        // it's preferable to use it only since enforcement in API 26 to not break any old behavior
        ComponentName componentName = new ComponentName(context, NemurOrderJobService.class);

        JobInfo jobInfo = new JobInfo.Builder(jobId, componentName)
                .setRequiresCharging(false)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0) // if possible, try to run right away
                .setExtras(extras)
                .build();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        int resultCode = jobScheduler.schedule(jobInfo);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            AppLog.i(AppLog.T.NEMUR, "nemur order service > job scheduled");
        } else {
            AppLog.e(AppLog.T.NEMUR, "nemur order service > job could not be scheduled");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static void putNemurTagExtras(PersistableBundle extras, NemurTag tag) {
        extras.putString(ARG_TAG_PARAM_SLUG, tag.getTagSlug());
        extras.putString(ARG_TAG_PARAM_DISPLAY_NAME, tag.getTagDisplayName());
        extras.putString(ARG_TAG_PARAM_TITLE, tag.getTagTitle());
        extras.putString(ARG_TAG_PARAM_ENDPOINT, tag.getEndpoint());
        extras.putInt(ARG_TAG_PARAM_TAGTYPE, tag.tagType.toInt());
    }
}

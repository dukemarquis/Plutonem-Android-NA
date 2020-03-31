package com.plutonem.ui;

import com.plutonem.ui.prefs.AppPrefs;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

public enum ActivityId {
    UNKNOWN("Unknown"),
    ORDERS("Order List"),
    POST_EDITOR("Order Editor");

    private final String mStringValue;

    ActivityId(final String stringValue) {
        mStringValue = stringValue;
    }

    public static void trackLastActivity(ActivityId activityId) {
        AppLog.v(T.UTILS, "trackLastActivity, activityId: " + activityId);
        if (activityId != null) {
            AppPrefs.setLastActivityStr(activityId.name());
        }
    }
}

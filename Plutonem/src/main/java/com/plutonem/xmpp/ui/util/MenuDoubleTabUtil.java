package com.plutonem.xmpp.ui.util;

import android.os.SystemClock;
import android.util.Log;

import com.plutonem.Config;

public class MenuDoubleTabUtil {

    private static long lastMenuOpenedTimestamp = 0L;

    public static boolean shouldIgnoreTap() {
        boolean ignoreTab = lastMenuOpenedTimestamp + 250 > SystemClock.elapsedRealtime();
        if (ignoreTab) {
            Log.d(Config.LOGTAG,"ignoring tab");
        }
        return ignoreTab;
    }
}

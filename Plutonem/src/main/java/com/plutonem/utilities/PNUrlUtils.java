package com.plutonem.utilities;

import org.wordpress.android.util.UrlUtils;

public class PNUrlUtils {
    public static boolean safeToAddWordPressComAuthToken(String url) {
        return UrlUtils.isHttps(url) && isPlutonemCom(url);
    }

    public static boolean isPlutonemCom(String url) {
        return UrlUtils.getHost(url).endsWith(".plutonem.com") || UrlUtils.getHost(url).equals("plutonem.com");
    }
}

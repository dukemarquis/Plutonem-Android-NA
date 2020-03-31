package org.wordpress.android.util;

import android.net.Uri;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class UrlUtils {
    public static String urlEncode(final String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    /**
     * @param urlString url to get host from
     * @return host of uri if available. Empty string otherwise.
     */
    public static String getHost(final String urlString) {
        if (urlString != null) {
            Uri uri = Uri.parse(urlString);
            if (uri.getHost() != null) {
                return uri.getHost();
            }
        }
        return "";
    }

    /**
     * returns the passed url without the query parameters
     */
    public static String removeQuery(final String urlString) {
        if (urlString == null) {
            return null;
        }
        return Uri.parse(urlString).buildUpon().clearQuery().toString();
    }

    /**
     * returns true if passed url is https:
     */
    public static boolean isHttps(final String urlString) {
        return (urlString != null && urlString.startsWith("https:"));
    }

    /**
     * returns https: version of passed http: url
     */
    public static String makeHttps(final String urlString) {
        if (urlString == null || !urlString.startsWith("http:")) {
            return urlString;
        }
        return "https:" + urlString.substring(5, urlString.length());
    }
}

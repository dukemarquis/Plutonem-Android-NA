package org.wordpress.android.util;

import android.text.TextUtils;

import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONObject;

public class JSONUtils {
    private static final String JSON_NULL_STR = "null";

    /*
     * wrapper for JSONObject.optString() which handles "null" values
     */
    public static String getString(JSONObject json, String name) {
        String value = json.optString(name);
        // return empty string for "null"
        if (JSON_NULL_STR.equals(value)) {
            return "";
        }
        return value;
    }

    /*
     * use with strings that contain HTML entities
     */
    public static String getStringDecoded(JSONObject json, String name) {
        String value = getString(json, name);
        return StringEscapeUtils.unescapeHtml4(value);
    }

    /*
     * replacement for JSONObject.optBoolean() - optBoolean() only checks for "true" and "false",
     * but our API sometimes uses "0" to denote false
     */
    public static boolean getBool(JSONObject json, String name) {
        String value = getString(json, name);
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        if (value.equals("0")) {
            return false;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        if (value.equalsIgnoreCase("no")) {
            return false;
        }
        return true;
    }

    /*
     * returns the JSONObject child of the passed parent that matches the passed query
     * this is basically an "optJSONObject" that supports nested queries, for example:
     *
     * getJSONChild("meta/data/buyer")
     *
     * would find this:
     *
     *  "meta": {
     *       "data": {
     *           "buyer": {
     *                "ID": 1,
     *                "name": "Plutonem.com Things",
     *           }
     *       }
     *   }
     */
    public static JSONObject getJSONChild(final JSONObject jsonParent, final String query) {
        if (jsonParent == null || TextUtils.isEmpty(query)) {
            return null;
        }
        String[] names = query.split("/");
        JSONObject jsonChild = null;
        for (int i = 0; i < names.length; i++) {
            if (jsonChild == null) {
                jsonChild = jsonParent.optJSONObject(names[i]);
            } else {
                jsonChild = jsonChild.optJSONObject(names[i]);
            }
            if (jsonChild == null) {
                return null;
            }
        }
        return jsonChild;
    }
}

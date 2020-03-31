package com.plutonem.android.networking;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.RetryPolicy;
import com.plutonem.rest.RestClient;
import com.plutonem.rest.RestRequest;
import com.plutonem.rest.RestRequest.ErrorListener;
import com.plutonem.rest.RestRequest.Listener;

import org.json.JSONObject;
import org.wordpress.android.util.LanguageUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Interface to the Plutonem.com REST API.
 */

public class RestClientUtils {
    private static String sUserAgent = "Plutonem Networking Android";

    private RestClient mRestClient;
    private Authenticator mAuthenticator;
    private Context mContext;

    /**
     * Socket timeout in milliseconds for rest requests
     */
    private static final int REST_TIMEOUT_MS = 30000;

    /**
     * Default number of retries for GET rest requests
     */
    private static final int REST_MAX_RETRIES_GET = 3;

    /**
     * Default backoff multiplier for rest requests
     */
    private static final float REST_BACKOFF_MULT = 2f;

    public static void setUserAgent(String userAgent) {
        sUserAgent = userAgent;
    }

    public RestClientUtils(Context context, RequestQueue queue, Authenticator authenticator,
                           RestRequest.OnAuthFailedListener onAuthFailedListener) {
        this(context, queue, authenticator, onAuthFailedListener, RestClient.REST_CLIENT_VERSIONS.V1);
    }

    public RestClientUtils(Context context, RequestQueue queue, Authenticator authenticator,
                           RestRequest.OnAuthFailedListener onAuthFailedListener,
                           RestClient.REST_CLIENT_VERSIONS version) {
        // load an existing access token from prefs if we have one
        mContext = context;
        mAuthenticator = authenticator;
        mRestClient = RestClientFactory.instantiate(queue, version);
        if (onAuthFailedListener != null) {
            mRestClient.setOnAuthFailedListener(onAuthFailedListener);
        }
        mRestClient.setUserAgent(sUserAgent);
    }

    /**
     * Make GET request
     */
    public Request<JSONObject> get(String path, Listener listener, ErrorListener errorListener) {
        return get(path, null, null, listener, errorListener);
    }

    /**
     * Make GET request with params
     */
    public Request<JSONObject> get(String path, Map<String, String> params, RetryPolicy retryPolicy, Listener listener,
                                   ErrorListener errorListener) {
        // turn params into query string
        HashMap<String, String> paramsWithLocale = getRestLocaleParams(mContext);
        if (params != null) {
            paramsWithLocale.putAll(params);
        }

        String realPath = getSanitizedPath(path);
        if (TextUtils.isEmpty(realPath)) {
            realPath = path;
        }
        paramsWithLocale.putAll(getSanitizedParameters(path));

        RestRequest request = mRestClient.makeRequest(Request.Method.GET, mRestClient
                .getAbsoluteURL(realPath, paramsWithLocale), null, listener, errorListener);

        if (retryPolicy == null) {
            retryPolicy = new DefaultRetryPolicy(REST_TIMEOUT_MS, REST_MAX_RETRIES_GET, REST_BACKOFF_MULT);
        }
        request.setRetryPolicy(retryPolicy);
        AuthenticatorRequest authCheck = new AuthenticatorRequest(request, errorListener, mRestClient, mAuthenticator);
        authCheck.send();
        return request;
    }

    /**
     * Takes a URL and returns the path within, or an empty string (not null)
     */
    private static String getSanitizedPath(String unsanitizedPath) {
        if (unsanitizedPath != null) {
            int qmarkPos = unsanitizedPath.indexOf('?');
            if (qmarkPos > -1) { // strip any query string params off this to obtain the path
                return unsanitizedPath.substring(0, qmarkPos + 1);
            } else {
                // return the string as is, consider the whole string as the path
                return unsanitizedPath;
            }
        }
        return "";
    }

    /**
     * Takes a URL with query strings and returns a Map of query string values.
     */
    private static HashMap<String, String> getSanitizedParameters(String unsanitizedPath) {
        HashMap<String, String> queryParams = new HashMap<>();

        Uri uri = Uri.parse(unsanitizedPath);

        if (uri.getHost() == null) {
            uri = Uri.parse("://" + unsanitizedPath); // path may contain a ":" leading to Uri.parse to misinterpret
            // it as opaque so, try it with a empty scheme in front
        }

        if (uri.getQueryParameterNames() != null) {
            for (String paramName : uri.getQueryParameterNames()) {
                String value = uri.getQueryParameter(paramName);
                queryParams.put(paramName, value);
            }
        }

        return queryParams;
    }

    /**
     * Returns locale parameter used in REST calls which require the response to be localized
     */
    public static HashMap<String, String> getRestLocaleParams(Context context) {
        HashMap<String, String> params = new HashMap<>();
        String deviceLanguageCode = LanguageUtils.getCurrentDeviceLanguageCode(context);
        if (!TextUtils.isEmpty(deviceLanguageCode)) {
            // patch locale if it's any of the deprecated codes as can be read in Locale.java source code:
            deviceLanguageCode = LanguageUtils.patchDeviceLanguageCode(deviceLanguageCode);
            params.put("locale", deviceLanguageCode);
        }
        return params;
    }
}

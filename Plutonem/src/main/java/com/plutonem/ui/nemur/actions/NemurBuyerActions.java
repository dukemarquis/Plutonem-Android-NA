package com.plutonem.ui.nemur.actions;

import com.android.volley.VolleyError;
import com.plutonem.Plutonem;
import com.plutonem.datasets.NemurBuyerTable;
import com.plutonem.models.NemurBuyer;
import com.plutonem.rest.RestRequest;
import com.plutonem.ui.nemur.actions.NemurActions.UpdateBuyerInfoListener;
import com.plutonem.utilities.VolleyUtils;

import org.json.JSONObject;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

import java.net.HttpURLConnection;

public class NemurBuyerActions {
    /*
     * request info about a specific buyer
     */
    public static void updateBuyerInfo(long buyerId,
                                      final UpdateBuyerInfoListener infoListener) {
        // must pass a valid id
        final boolean hasBuyerId = (buyerId != 0);
        if (!hasBuyerId) {
            AppLog.w(T.NEMUR, "cannot get buyer info without id");
            if (infoListener != null) {
                infoListener.onResult(null);
            }
            return;
        }

        RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                handleUpdateBuyerInfoResponse(jsonObject, infoListener);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                // authentication error may indicate that API access has been disabled for this buyer
                int statusCode = VolleyUtils.statusCodeFromVolleyError(volleyError);
                boolean isAuthErr = (statusCode == HttpURLConnection.HTTP_FORBIDDEN);
                AppLog.e(T.NEMUR, volleyError);
                if (infoListener != null) {
                    infoListener.onResult(null);
                }
            }
        };

        Plutonem.getRestClientUtilsV1_1().get("nem/buyers/" + buyerId, listener, errorListener);
    }

    private static void handleUpdateBuyerInfoResponse(JSONObject jsonObject, UpdateBuyerInfoListener infoListener) {
        if (jsonObject == null) {
            if (infoListener != null) {
                infoListener.onResult(null);
            }
            return;
        }

        NemurBuyer buyerInfo = NemurBuyer.fromJson(jsonObject);
        NemurBuyerTable.addOrUpdateBuyer(buyerInfo);

        if (infoListener != null) {
            infoListener.onResult(buyerInfo);
        }
    }
}

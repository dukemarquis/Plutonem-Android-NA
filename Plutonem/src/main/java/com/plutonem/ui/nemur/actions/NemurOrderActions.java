package com.plutonem.ui.nemur.actions;

import android.os.Handler;

import com.android.volley.VolleyError;
import com.plutonem.Plutonem;
import com.plutonem.android.networking.RestClientUtils;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.models.NemurOrder;
import com.plutonem.rest.RestRequest;
import com.plutonem.ui.nemur.actions.NemurActions.UpdateResult;
import com.plutonem.ui.nemur.actions.NemurActions.UpdateResultListener;
import com.plutonem.utilities.VolleyUtils;

import org.json.JSONObject;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

public class NemurOrderActions {
    private NemurOrderActions() {
        throw new AssertionError();
    }

    /*
     * get the latest version of this order - note that the order is only considered changed if the
     * title/price value has changed, or if the image/video link has changed
     */
    public static void updateOrder(final NemurOrder localOrder,
                                   final UpdateResultListener resultListener) {
        String path = "nem/buyers/" + localOrder.buyerId + "/orders/" + localOrder.orderId + "/?meta=buyer";

        com.plutonem.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                handleUpdateOrderResponse(localOrder, jsonObject, resultListener);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(T.NEMUR, volleyError);
                if (resultListener != null) {
                    resultListener.onUpdateResult(UpdateResult.FAILED);
                }
            }
        };
        AppLog.d(T.NEMUR, "updating order");
        Plutonem.getRestClientUtilsV1_2().get(path, null, null, listener, errorListener);
    }

    private static void handleUpdateOrderResponse(final NemurOrder localOrder,
                                                 final JSONObject jsonObject,
                                                 final UpdateResultListener resultListener) {
        if (jsonObject == null) {
            if (resultListener != null) {
                resultListener.onUpdateResult(UpdateResult.FAILED);
            }
            return;
        }

        final Handler handler = new Handler();

        new Thread() {
            @Override
            public void run() {
                NemurOrder serverOrder = NemurOrder.fromJson(jsonObject);

                boolean hasChanges = !serverOrder.isSameOrder(localOrder);

                if (hasChanges) {
                    AppLog.d(T.NEMUR, "order updated");
                    // copy changes over to the local order - this is done instead of simply overwriting
                    // the local order with the server order because the server order was retrieved using
                    // the nem/buyers/$buyerId/orders/$orderId endpoint which is missing some information
                    localOrder.setTitle(serverOrder.getTitle());
                    localOrder.setPrice(serverOrder.getPrice());
                    localOrder.setItemDistributionMode(serverOrder.getItemDistributionMode());
                    localOrder.setFeaturedImage(serverOrder.getFeaturedImage());
                    localOrder.setFeaturedVideo(serverOrder.getFeaturedVideo());
                    NemurOrderTable.updateOrder(localOrder);
                }

                if (resultListener != null) {
                    final UpdateResult result = (hasChanges ? UpdateResult.CHANGED : UpdateResult.UNCHANGED);
                    handler.post(new Runnable() {
                        public void run() {
                            resultListener.onUpdateResult(result);
                        }
                    });
                }
            }
        }.start();
    }

    /**
     * similar to updateOrder, but used when order doesn't already exist in local db
     **/
    public static void requestBuyerOrder(final long buyerId,
                                       final long orderId,
                                       final NemurActions.OnRequestListener requestListener) {
        String path = "nem/buyers/" + buyerId + "/orders/" + orderId + "/?meta=site";
        requestOrder(Plutonem.getRestClientUtilsV1_1(), path, requestListener);
    }

    private static void requestOrder(RestClientUtils restClientUtils, String path, final NemurActions
            .OnRequestListener requestListener) {
        com.plutonem.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                NemurOrder order = NemurOrder.fromJson(jsonObject);
                NemurOrderTable.addOrder(order);
                if (requestListener != null) {
                    requestListener.onSuccess();
                }
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(T.NEMUR, volleyError);
                if (requestListener != null) {
                    int statusCode = 0;
                    // first try to get the error code from the JSON response, example:
                    // {"code":404,"headers":[{"name":"Content-Type","value":"application\/json"}],
                    // "body":{"error":"unfounded","message":"The order is no longer exist."}}
                    JSONObject jsonObject = VolleyUtils.volleyErrorToJSON(volleyError);
                    if (jsonObject != null && jsonObject.has("code")) {
                        statusCode = jsonObject.optInt("code");
                    }
                    if (statusCode == 0) {
                        statusCode = VolleyUtils.statusCodeFromVolleyError(volleyError);
                    }
                    requestListener.onFailure(statusCode);
                }
            }
        };

        AppLog.d(T.NEMUR, "requesting order");
        restClientUtils.get(path, null, null, listener, errorListener);
    }
}

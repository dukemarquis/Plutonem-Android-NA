package com.plutonem.ui.nemur.services.order;

import android.text.TextUtils;

import com.android.volley.VolleyError;
import com.plutonem.Plutonem;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.datasets.NemurTagTable;
import com.plutonem.models.NemurOrder;
import com.plutonem.models.NemurOrderList;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagType;
import com.plutonem.rest.RestRequest;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.NemurEvents;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;
import com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction;
import com.plutonem.ui.nemur.utils.NemurUtils;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.UrlUtils;

import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction.REQUEST_NEWER;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction.REQUEST_OLDER;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction.REQUEST_OLDER_THAN_GAP;
import static com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction.REQUEST_REFRESH;

public class NemurOrderLogic {
    private ServiceCompletionListener mCompletionListener;
    private Object mListenerCompanion;

    public NemurOrderLogic(ServiceCompletionListener listener) {
        mCompletionListener = listener;
    }

    public void performTask(Object companion, UpdateAction action,
                            NemurTag tag) {
        mListenerCompanion = companion;

        EventBus.getDefault().post(new NemurEvents.UpdateOrdersStarted(action));

        if (tag != null) {
            updateOrdersWithTag(tag, action);
        }
    }


    private void updateOrdersWithTag(final NemurTag tag, final UpdateAction action) {
        requestOrdersWithTag(
                tag,
                action,
                new NemurActions.UpdateResultListener() {
                    @Override
                    public void onUpdateResult(NemurActions.UpdateResult result) {
                        EventBus.getDefault().post(new NemurEvents.UpdateOrdersEnded(tag, result, action));
                        mCompletionListener.onCompleted(mListenerCompanion);
                    }
                });
    }

    private static void requestOrdersWithTag(final NemurTag tag,
                                            final UpdateAction updateAction,
                                            final NemurActions.UpdateResultListener resultListener) {
        String path = getRelativeEndpointForTag(tag);
        if (TextUtils.isEmpty(path)) {
            resultListener.onUpdateResult(NemurActions.UpdateResult.FAILED);
            return;
        }

        StringBuilder sb = new StringBuilder(path);

        // append #orders to retrieve
        sb.append("?number=").append(NemurConstants.NEMUR_MAX_ORDERS_TO_REQUEST);

        // return newest orders first (this is the default, but make it explicit since it's important)
        sb.append("&order=DESC");

        String beforeDate;
        switch (updateAction) {
            case REQUEST_OLDER:
                // request orders older than the oldest existing order with this tag
                beforeDate = NemurOrderTable.getOldestDateWithTag(tag);
                break;
            case REQUEST_OLDER_THAN_GAP:
                // request orders older than the order with the gap marker for this tag
                beforeDate = NemurOrderTable.getGapMarkerDateForTag(tag);
            case REQUEST_NEWER:
            case REQUEST_REFRESH:
            default:
                beforeDate = null;
                break;
        }
        if (!TextUtils.isEmpty(beforeDate)) {
            sb.append("&before=").append(UrlUtils.urlEncode(beforeDate));
        }

        sb.append("&meta=site");

        com.plutonem.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                // remember when this tag was updated if newer orders were requested
                if (updateAction == REQUEST_NEWER || updateAction == REQUEST_REFRESH) {
                    NemurTagTable.setTagLastUpdated(tag);
                }
                handleUpdateOrdersResponse(tag, jsonObject, updateAction, resultListener);
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(AppLog.T.NEMUR, volleyError);
                resultListener.onUpdateResult(NemurActions.UpdateResult.FAILED);
            }
        };

        Plutonem.getRestClientUtilsV1_2().get(sb.toString(), null, null, listener, errorListener);
    }

    /*
     * called after requesting orders with a specific tag or in a specific buyer
     */
    private static void handleUpdateOrdersResponse(final NemurTag tag,
                                                   final JSONObject jsonObject,
                                                   final UpdateAction updateAction,
                                                   final NemurActions.UpdateResultListener resultListener) {
        if (jsonObject == null) {
            resultListener.onUpdateResult(NemurActions.UpdateResult.FAILED);
            return;
        }

        new Thread() {
            @Override
            public void run() {
                NemurOrderList serverOrders = NemurOrderList.fromJson(jsonObject);
                NemurActions.UpdateResult updateResult = NemurOrderTable.compareOrders(serverOrders);
                if (updateResult.isNewOrChanged()) {
                    // gap detection - only applies to orders with a specific tag
                    NemurOrder orderWithGap = null;
                    if (tag != null) {
                        switch (updateAction) {
                            case REQUEST_NEWER:
                                // if there's no overlap between server and local (ie: all server
                                // orders are new), assume there's a gap between server and local
                                // provided that local orders exist
                                int numServerOrders = serverOrders.size();
                                if (numServerOrders >= 2
                                        && NemurOrderTable.getNumOrdersWithTag(tag) > 0
                                        && !NemurOrderTable.hasOverlap(serverOrders, tag)) {
                                    // treat the second to last server order as having a gap
                                    orderWithGap = serverOrders.get(numServerOrders - 2);
                                    // remove the last server order to deal with the edge case of
                                    // there actually not being a gap between local & server
                                    serverOrders.remove(numServerOrders - 1);
                                    NemurBuyerIdOrderId gapMarker = NemurOrderTable.getGapMarkerIdsForTag(tag);
                                    if (gapMarker != null) {
                                        // We mustn't have two gapMarkers at the same time. Therefor we need to
                                        // delete all orders before the current gapMarker and clear the gapMarker flag.
                                        NemurOrderTable.deleteOrdersBeforeGapMarkerForTag(tag);
                                        NemurOrderTable.removeGapMarkerForTag(tag);
                                    }
                                }
                                break;
                            case REQUEST_OLDER_THAN_GAP:
                                // if service was started as a request to fill a gap, delete existing orders
                                // before the one with the gap marker, then remove the existing gap marker
                                NemurOrderTable.deleteOrdersBeforeGapMarkerForTag(tag);
                                NemurOrderTable.removeGapMarkerForTag(tag);
                                break;
                            case REQUEST_REFRESH:
                                NemurOrderTable.deleteOrdersWithTag(tag);
                                break;
                            case REQUEST_OLDER:
                                // no-op
                                break;
                        }
                    }
                    NemurOrderTable.addOrUpdateOrders(tag, serverOrders);

                    // gap marker must be set after saving server orders
                    if (orderWithGap != null) {
                        NemurOrderTable.setGapMarkerForTag(orderWithGap.buyerId, orderWithGap.orderId, tag);
                        AppLog.d(AppLog.T.NEMUR, "added gap marker to tag " + tag.getTagNameForLog());
                    }
                } else if (updateResult == NemurActions.UpdateResult.UNCHANGED
                        && updateAction == UpdateAction.REQUEST_OLDER_THAN_GAP) {
                    // edge case - request to fill gap returned nothing new, so remove the gap marker
                    NemurOrderTable.removeGapMarkerForTag(tag);
                    AppLog.w(AppLog.T.NEMUR, "attempt to fill gap returned nothing new");
                }
                AppLog.d(AppLog.T.NEMUR, "requested orders response = " + updateResult.toString());
                resultListener.onUpdateResult(updateResult);
            }
        }.start();
    }

    /*
     * returns the endpoint to use when requesting orders with the passed tag
     */
    private static String getRelativeEndpointForTag(NemurTag tag) {
        if (tag == null) {
            return null;
        }

        // if passed tag has an assigned endpoint, return it and be done
        if (!TextUtils.isEmpty(tag.getEndpoint())) {
            return getRelativeEndpoint(tag.getEndpoint());
        }

        // check the db for the endpoint
        String endpoint = NemurTagTable.getEndpointForTag(tag);
        if (!TextUtils.isEmpty(endpoint)) {
            return getRelativeEndpoint(endpoint);
        }

        // never hand craft the endpoint for default tags, since these MUST be updated
        // using their stored endpoints
        if (tag.tagType == NemurTagType.DEFAULT) {
            return null;
        }

        return String.format("nem/tags/%s/orders", NemurUtils.sanitizeWithDashes(tag.getTagSlug()));
    }

    /*
     * returns the passed endpoint without the unnecessary path - this is
     * needed because as of one call returns the full path but we don't
     * want to use the full path since it may change between API
     * versions (as it did when we moved from v1 to v1.1)
     *
     * ex: https://public-api.wordpress.com/rest/v1/nemur/tags/xxx/orders
     * becomes just nemur/tags/xxx/orders
     */
    private static String getRelativeEndpoint(final String endpoint) {
        if (endpoint != null && endpoint.startsWith("http")) {
            int pos = endpoint.indexOf("/nem/");
            if (pos > -1) {
                return endpoint.substring(pos + 1, endpoint.length());
            }
            pos = endpoint.indexOf("/v1/");
            if (pos > -1) {
                return endpoint.substring(pos + 4, endpoint.length());
            }
        }
        return StringUtils.notNullStr(endpoint);
    }
}

package com.plutonem.ui.nemur.services.search;

import com.android.volley.VolleyError;
import com.plutonem.Plutonem;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.models.NemurOrderList;
import com.plutonem.rest.RestRequest;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.NemurEvents;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.UrlUtils;

import static com.plutonem.ui.nemur.utils.NemurUtils.getTagForSearchQuery;

public class NemurSearchLogic {
    private ServiceCompletionListener mCompletionListener;
    private Object mListenerCompanion;

    public NemurSearchLogic(ServiceCompletionListener listener) {
        mCompletionListener = listener;
    }

    public void startSearch(final String query, final int offset, Object companion) {
        mListenerCompanion = companion;
        String path = "nem/search?q="
                + UrlUtils.urlEncode(query)
                + "&number=" + NemurConstants.NEMUR_MAX_SEARCH_RESULTS_TO_REQUEST
                + "&offset=" + offset
                + "&meta=site,likes";

        RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                if (jsonObject != null) {
                    handleSearchResponse(query, offset, jsonObject);
                } else {
                    EventBus.getDefault().post(new NemurEvents.SearchOrdersEnded(query, offset, false));
                }
            }
        };
        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(AppLog.T.NEMUR, volleyError);
                EventBus.getDefault().post(new NemurEvents.SearchOrdersEnded(query, offset, false));
                mCompletionListener.onCompleted(mListenerCompanion);
            }
        };

        AppLog.d(AppLog.T.NEMUR, "nemur search service > starting search for " + query);
        EventBus.getDefault().post(new NemurEvents.SearchOrdersStarted(query, offset));
        Plutonem.getRestClientUtilsV1_2().get(path, null, null, listener, errorListener);
    }

    private void handleSearchResponse(final String query, final int offset, final JSONObject jsonObject) {
        new Thread() {
            @Override
            public void run() {
                NemurOrderList serverOrders = NemurOrderList.fromJson(jsonObject);
                NemurOrderTable.addOrUpdateOrders(getTagForSearchQuery(query), serverOrders);
                EventBus.getDefault().post(new NemurEvents.SearchOrdersEnded(query, offset, true));
                mCompletionListener.onCompleted(mListenerCompanion);
            }
        }.start();
    }
}

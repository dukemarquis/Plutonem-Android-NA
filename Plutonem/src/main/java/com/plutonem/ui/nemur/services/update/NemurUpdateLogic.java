package com.plutonem.ui.nemur.services.update;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.volley.VolleyError;
import com.plutonem.Plutonem;
import com.plutonem.datasets.NemurDatabase;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.datasets.NemurTagTable;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagList;
import com.plutonem.models.NemurTagType;
import com.plutonem.rest.RestRequest;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.NemurEvents;
import com.plutonem.ui.nemur.services.ServiceCompletionListener;
import com.plutonem.utilities.LocaleManager;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.JSONUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;

public class NemurUpdateLogic {
    /***
     * This class holds the business logic for Nemur Updates, serving both NemurUpdateService (<API26)
     * and NemurUpdateJobService (API26+).
     * Updates default tags and buyers for the Nemur, relies
     * on EventBus to notify of changes
     */

    public enum UpdateTask {
        TAGS
    }

    private EnumSet<UpdateTask> mCurrentTasks;
    private ServiceCompletionListener mCompletionListener;
    private Object mListenerCompanion;
    private String mLanguage;
    private Context mContext;

    public NemurUpdateLogic(Context context, Plutonem app, ServiceCompletionListener listener) {
        mCompletionListener = listener;
        app.component().inject(this);
        mLanguage = LocaleManager.getLanguage(app);
        mContext = context;
    }

    public void performTasks(EnumSet<UpdateTask> tasks, Object companion) {
        mCurrentTasks = EnumSet.copyOf(tasks);
        mListenerCompanion = companion;

        // perform in priority order - we want to update tags first since without them
        // the Nemur can't show anything
        if (tasks.contains(UpdateTask.TAGS)) {
            updateTags();
        }
    }

    private void taskCompleted(UpdateTask task) {
        mCurrentTasks.remove(task);
        if (mCurrentTasks.isEmpty()) {
            allTasksCompleted();
        }
    }

    private void allTasksCompleted() {
        AppLog.i(AppLog.T.NEMUR, "nemur service > all tasks completed");
        mCompletionListener.onCompleted(mListenerCompanion);
    }

    /***
     * update the tags that are default to the user
     */
    private void updateTags() {
        com.plutonem.rest.RestRequest.Listener listener = new RestRequest.Listener() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                handleUpdateTagsResponse(jsonObject);
            }
        };

        RestRequest.ErrorListener errorListener = new RestRequest.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                AppLog.e(AppLog.T.NEMUR, volleyError);
                taskCompleted(UpdateTask.TAGS);
            }
        };
        AppLog.d(AppLog.T.NEMUR, "nemur service > updating tags");
        HashMap<String, String> params = new HashMap<>();
        params.put("locale", mLanguage);
        Plutonem.getRestClientUtilsV1_2().get("nem/menu", params, null, listener, errorListener);
    }

    private void handleUpdateTagsResponse(final JSONObject jsonObject) {
        new Thread() {
            @Override
            public void run() {
                // get server categories, default
                NemurTagList serverCategories = new NemurTagList();
                serverCategories.addAll(parseTags(jsonObject, "default", NemurTagType.DEFAULT));

                // parse categories from the response, detect whether they're different from local
                NemurTagList localCategories = new NemurTagList();
                localCategories.addAll(NemurTagTable.getDefaultTags());

                if (
                        !localCategories.isSameList(serverCategories)
                ) {
                    AppLog.d(AppLog.T.NEMUR, "nemur service > default categories changed ");
                    // if any local categories have been removed from the server, make sure to delete
                    // them locally (including their orders)
                    deleteTags(localCategories.getDeletions(serverCategories));
                    // now replace local categories with the server categories
                    NemurTagTable.replaceTags(serverCategories);
                    // broadcast the fact that there are changes
                    EventBus.getDefault().post(new NemurEvents.DefaultTagsChanged());
                }

                taskCompleted(UpdateTask.TAGS);
            }
        }.start();
    }

    /*
     * parse a specific category section from the category response
     */
    private static NemurTagList parseTags(JSONObject jsonObject, String name, NemurTagType tagType) {
        NemurTagList categories = new NemurTagList();

        if (jsonObject == null) {
            return categories;
        }

        JSONObject jsonCategories = jsonObject.optJSONObject(name);
        if (jsonCategories == null) {
            return categories;
        }

        Iterator<String> it = jsonCategories.keys();
        while (it.hasNext()) {
            String internalName = it.next();
            JSONObject jsonCategory = jsonCategories.optJSONObject(internalName);
            if (jsonCategory != null) {
                String tagTitle = JSONUtils.getStringDecoded(jsonCategory, NemurConstants.JSON_TAG_TITLE);
                String tagDisplayName = JSONUtils.getStringDecoded(jsonCategory, NemurConstants.JSON_TAG_DISPLAY_NAME);
                String tagSlug = JSONUtils.getStringDecoded(jsonCategory, NemurConstants.JSON_TAG_SLUG);
                String endpoint = JSONUtils.getString(jsonCategory, NemurConstants.JSON_TAG_URL);

                categories.add(new NemurTag(tagSlug, tagDisplayName, tagTitle, endpoint, tagType));
            }
        }

        return categories;
    }

    private static void deleteTags(NemurTagList tagList) {
        if (tagList == null || tagList.size() == 0) {
            return;
        }

        SQLiteDatabase db = NemurDatabase.getWritableDb();
        db.beginTransaction();
        try {
            for (NemurTag tag : tagList) {
                NemurTagTable.deleteTag(tag);
                NemurOrderTable.deleteOrdersWithTag(tag);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}

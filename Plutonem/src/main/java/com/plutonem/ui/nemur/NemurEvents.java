package com.plutonem.ui.nemur;

import androidx.annotation.NonNull;

import com.plutonem.models.NemurTag;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter;

public class NemurEvents {
    private NemurEvents() {
        throw new AssertionError();
    }

    public static class DefaultTagsChanged {
    }

    public static class SingleOrderDownloaded {
    }

    public static class UpdateOrdersStarted {
        private final NemurOrderServiceStarter.UpdateAction mAction;

        public UpdateOrdersStarted(NemurOrderServiceStarter.UpdateAction action) {
            mAction = action;
        }

        public NemurOrderServiceStarter.UpdateAction getAction() {
            return mAction;
        }
    }

    public static class UpdateOrdersEnded {
        private final NemurTag mNemurTag;
        private final NemurActions.UpdateResult mResult;
        private final NemurOrderServiceStarter.UpdateAction mAction;

        public UpdateOrdersEnded(NemurActions.UpdateResult result,
                                 NemurOrderServiceStarter.UpdateAction action) {
            mResult = result;
            mAction = action;
            mNemurTag = null;
        }

        public UpdateOrdersEnded(NemurTag nemurTag,
                                 NemurActions.UpdateResult result,
                                 NemurOrderServiceStarter.UpdateAction action) {
            mNemurTag = nemurTag;
            mResult = result;
            mAction = action;
        }

        public NemurTag getNemurTag() {
            return mNemurTag;
        }

        public NemurActions.UpdateResult getResult() {
            return mResult;
        }

        public NemurOrderServiceStarter.UpdateAction getAction() {
            return mAction;
        }
    }

    public static class SearchOrdersStarted {
        private final String mQuery;
        private final int mOffset;

        public SearchOrdersStarted(@NonNull String query, int offset) {
            mQuery = query;
            mOffset = offset;
        }

        public String getQuery() {
            return mQuery;
        }

        public int getOffset() {
            return mOffset;
        }
    }

    public static class SearchOrdersEnded {
        private final String mQuery;
        private final boolean mDidSucceed;
        private final int mOffset;

        public SearchOrdersEnded(@NonNull String query, int offset, boolean didSucceed) {
            mQuery = query;
            mOffset = offset;
            mDidSucceed = didSucceed;
        }

        public boolean didSucceed() {
            return mDidSucceed;
        }

        public String getQuery() {
            return mQuery;
        }

        public int getOffset() {
            return mOffset;
        }
    }
}

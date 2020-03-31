package com.plutonem.ui.nemur.actions;

import com.plutonem.models.NemurBuyer;

/**
 * classes in this package serve as a middleman between local data and server data - used by
 * reader activities/fragments/adapters that wish to perform actions on posts, blogs & topics,
 * or wish to get the latest data from the server.
 * <p>
 * methods in this package which change state are generally optimistic
 * and work like this:
 * <p>
 * 1. caller asks method to send a network request which changes state
 * 2. method changes state in local data and returns to caller *before* network request completes
 * 3. caller can access local state change without waiting for the network request
 * 4. if the network request fails, the method restores the previous state of the local data
 * 5. if caller passes a listener, it can be alerted to the actual success/failure of the request
 * <p>
 * note that all methods MUST be called from the UI thread in order to guarantee that listeners
 * are alerted on the UI thread
 */
public class NemurActions {
    private NemurActions() {
        throw new AssertionError();
    }

    /*
     * listener when the failure status code is required
     */
    public interface OnRequestListener {
        void onSuccess();

        void onFailure(int statusCode);
    }

    /*
     * result when updating data (getting latest orders, etc.)
     */
    public enum UpdateResult {
        HAS_NEW, // new orders/etc. have been retrieved
        CHANGED, // no new orders, but existing ones have changed
        UNCHANGED, // no new or changed orders
        FAILED; // request failed

        public boolean isNewOrChanged() {
            return (this == HAS_NEW || this == CHANGED);
        }
    }

    public interface UpdateResultListener {
        void onUpdateResult(UpdateResult result);
    }

    /*
     * used by adapters to notify when more data should be loaded
     */
    public interface DataRequestedListener {
        void onRequestData();
    }

    /*
     * used by blog preview when requesting latest info about a blog
     */
    public interface UpdateBuyerInfoListener {
        void onResult(NemurBuyer buyerInfo);
    }
}

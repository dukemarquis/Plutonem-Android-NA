package com.plutonem.utilities;

import com.plutonem.R;

import org.wordpress.android.util.helpers.SwipeToRefreshHelper;
import org.wordpress.android.util.widgets.CustomSwipeRefreshLayout;

public class PNSwipeToRefreshHelper {
    /**
     * Builds a {@link org.wordpress.android.util.helpers.SwipeToRefreshHelper} and returns a new
     * instance with colors designated for the WordPress app.
     *
     * @param swipeRefreshLayout {@link CustomSwipeRefreshLayout} for refreshing the contents
     * of a view via a vertical swipe gesture.
     * @param listener {@link SwipeToRefreshHelper.RefreshListener} notified when a refresh is triggered
     * via the swipe gesture.
     */
    public static SwipeToRefreshHelper buildSwipeToRefreshHelper(CustomSwipeRefreshLayout swipeRefreshLayout,
                                                                 SwipeToRefreshHelper.RefreshListener listener) {
        return new SwipeToRefreshHelper(swipeRefreshLayout, listener, R.color.primary, R.color.accent);
    }
}

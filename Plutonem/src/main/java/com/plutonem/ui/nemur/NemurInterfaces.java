package com.plutonem.ui.nemur;

import com.plutonem.models.NemurOrder;

public class NemurInterfaces {
    private NemurInterfaces() {
        throw new AssertionError();
    }

    public interface OnOrderSelectedListener {
        void onOrderSelected(NemurOrder order);
    }

    /*
     * called from order detail fragment so toolbar can animate in/out when scrolling
     */
    public interface AutoHideToolbarListener {
        void onShowHideToolbar(boolean show);
    }

    /*
     * used by adapters to notify when data has been loaded
     */
    public interface DataLoadedListener {
        void onDataLoaded(boolean isEmpty);
    }

    /*
     * used to show Chat interface when users what to connect to the sellers
     */
    public interface ChatInterfaceListener {
        void onShowChat();
    }
}

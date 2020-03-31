package com.plutonem.ui.nemur;

public class NemurTypes {
    public static final NemurOrderListType DEFAULT_ORDER_LIST_TYPE = NemurOrderListType.TAG_DEFAULT;

    public enum NemurOrderListType {
        TAG_DEFAULT, // list orders in a default tag
        SEARCH_RESULTS; // list orders matching a specific search keyword or phrase

        public boolean isTagType() {
            return this.equals(TAG_DEFAULT);
        }
    }
}

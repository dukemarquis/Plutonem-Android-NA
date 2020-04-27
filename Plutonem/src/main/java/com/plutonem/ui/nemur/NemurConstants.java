package com.plutonem.ui.nemur;

public class NemurConstants {
    // max # orders to request when updating orders
    public static final int NEMUR_MAX_ORDERS_TO_REQUEST = 20;

    // max # results to request when searching orders
    public static final int NEMUR_MAX_SEARCH_RESULTS_TO_REQUEST = 20;

    // max # orders to display
    public static final int NEMUR_MAX_ORDERS_TO_DISPLAY = 200;

    public static final long NEMUR_AUTO_UPDATE_DELAY_MINUTES = 5; // 5 minute delay between automatic updates

    public static final long WOMEN_BUYER_ID = 10; // buyer id for women.plutonem.com

    // intent arguments / keys
    public static final String ARG_SHOP_NAME = "shop_name";
    public static final String ARG_PRODUCT_NAME = "product_name";
    public static final String ARG_ITEM_PRICE = "item_price";
    public static final String ARG_ITEM_DISTRIBUTION_MODE = "item_distribution_mode";

    // intent arguments / keys
    static final String ARG_TAG = "tag";
    static final String ARG_BUYER_ID = "buyer_id";
    static final String ARG_ORDER_ID = "order_id";
    static final String ARG_ORDER_LIST_TYPE = "order_list_type";
    static final String ARG_IS_SINGLE_ORDER = "is_single_order";
    static final String ARG_SEARCH_QUERY = "search_query";
    static final String ARG_IS_TOP_LEVEL = "is_top_level";

    static final String KEY_ALREADY_UPDATED = "already_updated";
    static final String KEY_ALREADY_REQUESTED = "already_requested";
    static final String KEY_RESTORE_POSITION = "restore_position";
    static final String KEY_WAS_PAUSED = "was_paused";
    static final String KEY_ERROR_MESSAGE = "error_message";
    static final String KEY_FIRST_LOAD = "first_load";
    static final String KEY_IS_REFRESHING = "is_refreshing";
    static final String KEY_ACTIVE_SEARCH_TAB = "active_search_tab";

    // JSON key names
    // tag endpoints
    public static final String JSON_TAG_TITLE = "title";
    public static final String JSON_TAG_DISPLAY_NAME = "tag_display_name";
    public static final String JSON_TAG_SLUG = "slug";
    public static final String JSON_TAG_URL = "URL";

    public static final String KEY_VARIOUS = "various";
    public static final String KEY_WOMEN = "women";
}

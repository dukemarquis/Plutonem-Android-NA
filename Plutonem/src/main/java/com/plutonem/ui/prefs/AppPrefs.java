package com.plutonem.ui.prefs;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.plutonem.Plutonem;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagType;
import com.plutonem.ui.nemur.utils.NemurUtils;
import com.plutonem.ui.products.AccountFilterSelection;
import com.plutonem.ui.products.OrderListViewLayoutType;

public class AppPrefs {
    public interface PrefKey {
        String name();

        String toString();
    }

    /**
     * Application related preferences. When the user disconnects, these preferences are erased.
     */
    public enum DeletablePrefKey implements PrefKey {
        // name of last shown activity
        LAST_ACTIVITY_STR,

        // last selected tag in the nemur
        NEMUR_TAG_NAME,
        NEMUR_TAG_TYPE,

        // index of the last active page in main activity
        MAIN_PAGE_INDEX,

        // selected buyer in the main activity
        SELECTED_BUYER_LOCAL_ID,

        // Store a version of the last dismissed News Card
        NEWS_CARD_DISMISSED_VERSION,
        // Store a version of the last shown News Card
        NEWS_CARD_SHOWN_VERSION,

        ORDER_LIST_ACCOUNT_FILTER,
        ORDER_LIST_VIEW_LAYOUT_TYPE
    }

    /**
     * These preferences won't be deleted when the user disconnects. They should be used for device specifics or user
     * independent prefs.
     */
    public enum UndeletablePrefKey implements PrefKey {
        // Same as above but for the nemur
        SWIPE_TO_NAVIGATE_NEMUR,
    }

    private static SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(Plutonem.getContext());
    }

    private static String getString(PrefKey key) {
        return getString(key, "");
    }

    private static String getString(PrefKey key, String defaultValue) {
        return prefs().getString(key.name(), defaultValue);
    }

    private static void setString(PrefKey key, String value) {
        SharedPreferences.Editor editor = prefs().edit();
        if (TextUtils.isEmpty(value)) {
            editor.remove(key.name());
        } else {
            editor.putString(key.name(), value);
        }
        editor.apply();
    }

    private static long getLong(PrefKey key, long defaultValue) {
        try {
            String value = getString(key);
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static void setLong(PrefKey key, long value) {
        setString(key, Long.toString(value));
    }

    private static int getInt(PrefKey key, int def) {
        try {
            String value = getString(key);
            if (value.isEmpty()) {
                return def;
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int getInt(PrefKey key) {
        return getInt(key, 0);
    }

    public static void setInt(PrefKey key, int value) {
        setString(key, Integer.toString(value));
    }

    public static boolean getBoolean(PrefKey key, boolean def) {
        String value = getString(key, Boolean.toString(def));
        return Boolean.parseBoolean(value);
    }

    public static void setBoolean(PrefKey key, boolean value) {
        setString(key, Boolean.toString(value));
    }

    public static NemurTag getNemurTag() {
        String tagName = getString(DeletablePrefKey.NEMUR_TAG_NAME);
        if (TextUtils.isEmpty(tagName)) {
            return null;
        }
        int tagType = getInt(DeletablePrefKey.NEMUR_TAG_TYPE);
        return NemurUtils.getTagFromTagName(tagName, NemurTagType.fromInt(tagType));
    }

    public static void setNemurTag(NemurTag tag) {
        if (tag != null && !TextUtils.isEmpty(tag.getTagSlug())) {
            setString(DeletablePrefKey.NEMUR_TAG_NAME, tag.getTagSlug());
            setInt(DeletablePrefKey.NEMUR_TAG_TYPE, tag.tagType.toInt());
        } else {
            prefs().edit()
                    .remove(DeletablePrefKey.NEMUR_TAG_NAME.name())
                    .remove(DeletablePrefKey.NEMUR_TAG_TYPE.name())
                    .apply();
        }
    }

    public static void setLastActivityStr(String value) {
        setString(DeletablePrefKey.LAST_ACTIVITY_STR, value);
    }

    public static int getMainPageIndex() {
        return getInt(DeletablePrefKey.MAIN_PAGE_INDEX);
    }

    public static void setMainPageIndex(int index) {
        setInt(DeletablePrefKey.MAIN_PAGE_INDEX, index);
    }

    public static int getSelectedBuyer() {
        return getInt(DeletablePrefKey.SELECTED_BUYER_LOCAL_ID, -1);
    }

    public static void setSelectedBuyer(int selectedBuyer) {
        setInt(DeletablePrefKey.SELECTED_BUYER_LOCAL_ID, selectedBuyer);
    }

    public static boolean isNemurSwipeToNavigateShown() {
        return getBoolean(UndeletablePrefKey.SWIPE_TO_NAVIGATE_NEMUR, false);
    }

    public static void setNemurSwipeToNavigateShown(boolean alreadyShown) {
        setBoolean(UndeletablePrefKey.SWIPE_TO_NAVIGATE_NEMUR, alreadyShown);
    }

    public static void setNewsCardDismissedVersion(int version) {
        setInt(DeletablePrefKey.NEWS_CARD_DISMISSED_VERSION, version);
    }

    public static int getNewsCardDismissedVersion() {
        return getInt(DeletablePrefKey.NEWS_CARD_DISMISSED_VERSION, -1);
    }

    public static void setNewsCardShownVersion(int version) {
        setInt(DeletablePrefKey.NEWS_CARD_SHOWN_VERSION, version);
    }

    public static int getNewsCardShownVersion() {
        return getInt(DeletablePrefKey.NEWS_CARD_SHOWN_VERSION, -1);
    }

    @NonNull public static AccountFilterSelection getAccountFilterSelection() {
        long id = getLong(DeletablePrefKey.ORDER_LIST_ACCOUNT_FILTER, AccountFilterSelection.getDefaultValue().getId());
        return AccountFilterSelection.fromId(id);
    }

    public static void setAccountFilterSelection(@NonNull AccountFilterSelection selection) {
        setLong(DeletablePrefKey.ORDER_LIST_ACCOUNT_FILTER, selection.getId());
    }

    @NonNull public static OrderListViewLayoutType getOrdersListViewLayoutType() {
        long id = getLong(DeletablePrefKey.ORDER_LIST_VIEW_LAYOUT_TYPE,
                OrderListViewLayoutType.getDefaultValue().getId());
        return OrderListViewLayoutType.fromId(id);
    }

    public static void setOrdersListViewLayoutType(@NonNull OrderListViewLayoutType type) {
        setLong(DeletablePrefKey.ORDER_LIST_VIEW_LAYOUT_TYPE, type.getId());
    }
}

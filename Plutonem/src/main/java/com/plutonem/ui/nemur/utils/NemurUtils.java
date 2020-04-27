package com.plutonem.ui.nemur.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.plutonem.R;
import com.plutonem.datasets.NemurTagTable;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagList;
import com.plutonem.models.NemurTagType;
import com.plutonem.ui.FilteredRecyclerView;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.services.update.TagUpdateClientUtilsProvider;

import org.apache.commons.text.StringEscapeUtils;
import org.wordpress.android.util.PhotonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NemurUtils {
    public static String getResizedImageUrl(final String imageUrl, int width, int height) {
        return getResizedImageUrl(imageUrl, width, height, PhotonUtils.Quality.MEDIUM);
    }

    public static String getResizedImageUrl(final String imageUrl,
                                            int width,
                                            int height,
                                            PhotonUtils.Quality quality) {
        final String unescapedUrl = StringEscapeUtils.unescapeHtml4(imageUrl);
        return PhotonUtils.getPhotonImageUrl(unescapedUrl, width, height, quality);
    }

    /*
     * returns the passed string formatted for use with our API - see sanitize_title_with_dashes
     */
    public static String sanitizeWithDashes(final String title) {
        if (title == null) {
            return "";
        }

        return title.trim()
                .replaceAll("&[^\\s]*;", "") // remove html entities
                .replaceAll("[\\.\\s]+", "-") // replace periods and whitespace with a dash
                .replaceAll("[^\\p{L}\\p{Nd}\\-]+",
                        "") // remove remaining non-alphanum/non-dash chars (Unicode aware)
                .replaceAll("--", "-"); // reduce double dashes potentially added above
    }

    /*
     * returns a tag object from the passed endpoint if tag is in database, otherwise null
     */
    public static NemurTag getTagFromEndpoint(String endpoint) {
        return NemurTagTable.getTagFromEndpoint(endpoint);
    }

    /*
     * returns a tag object from the passed tag name - first checks for it in the tag db
     * (so we can also get its title & endpoint), returns a new tag if that fails
     */
    public static NemurTag getTagFromTagName(String tagName, NemurTagType tagType) {
        return getTagFromTagName(tagName, tagType, false);
    }

    public static NemurTag getTagFromTagName(String tagName, NemurTagType tagType, boolean markDefaultIfInMemory) {
        NemurTag tag = NemurTagTable.getTag(tagName, tagType);
        if (tag != null) {
            return tag;
        } else {
            return createTagFromTagName(tagName, tagType, markDefaultIfInMemory);
        }
    }

    public static NemurTag createTagFromTagName(String tagName, NemurTagType tagType, boolean isDefaultInMemoryTag) {
        String tagSlug = sanitizeWithDashes(tagName).toLowerCase(Locale.ROOT);
        String tagDisplayName = tagType == NemurTagType.DEFAULT ? tagName : tagSlug;
//        String endpoint = "http://3.15.14.1/rest/v1.2/nem/buyers/10/orders";
        return new NemurTag(
                tagSlug,
                tagDisplayName,
                tagName,
                null,
                tagType,
                isDefaultInMemoryTag
        );
    }

    /*
     * returns the default tag, which is the one selected by default in the nemur when
     * the user hasn't already chosen one
     */
    public static NemurTag getDefaultTag() {
        NemurTag defaultTag = getTagFromEndpoint(NemurTag.TAG_ENDPOINT_DEFAULT);
        if (defaultTag == null) {
            defaultTag = getTagFromTagName(NemurTag.TAG_TITLE_DEFAULT, NemurTagType.DEFAULT, true);
        }
        return defaultTag;
    }

    public static @NonNull NemurTag getDefaultTagFromDbOrCreateInMemory(
            @NonNull Context context,
            TagUpdateClientUtilsProvider clientUtilsProvider
    ) {
        // getDefaultTag() tries to get the default tag from nemur db by tag endpoint or tag name.
        // In case it cannot get the default tag from db, it creates it in memory with createTagFromTagName
        NemurTag tag = getDefaultTag();

        if (tag.isDefaultInMemoryTag()) {
            // if the tag was created in memory from createTagFromTagName
            // we need to set some fields as below before to use it
            tag.setTagTitle(context.getString(R.string.nemur_varioud_display_name));
            tag.setTagDisplayName(context.getString(R.string.nemur_varioud_display_name));

            String baseUrl = clientUtilsProvider.getTagUpdateEndpointURL();

            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }

            tag.setEndpoint(baseUrl + NemurTag.VARIOUS_PATH);
        }

        return tag;
    }

    /*
     * used when storing search results in the reader post table
     */
    public static NemurTag getTagForSearchQuery(@NonNull String query) {
        String trimQuery = query != null ? query.trim() : "";
        String slug = NemurUtils.sanitizeWithDashes(trimQuery);
        return new NemurTag(slug, trimQuery, trimQuery, null, NemurTagType.SEARCH);
    }

    public static Map<String, TagInfo> getDefaultTagInfo() {
        // Note that the following is the desired order in the tabs
        // (see usage in prependDefaults)
        Map<String, TagInfo> defaultTagInfo = new LinkedHashMap<>();

        defaultTagInfo.put(NemurConstants.KEY_VARIOUS, new TagInfo(NemurTagType.DEFAULT, NemurTag.VARIOUS_PATH));
        defaultTagInfo.put(NemurConstants.KEY_WOMEN, new TagInfo(NemurTagType.DEFAULT, NemurTag.WOMEN_PATH));

        return defaultTagInfo;
    }

    private static boolean putIfAbsentDone(Map<String, NemurTag> defaultTags, String key, NemurTag tag) {
        boolean insertionDone = false;

        if (defaultTags.get(key) == null) {
            defaultTags.put(key, tag);
            insertionDone = true;
        }

        return insertionDone;
    }

    private static boolean defaultTagFoundAndAdded(
            Map<String, TagInfo> defaultTagInfos,
            NemurTag tag,
            Map<String, NemurTag> defaultTags
    ) {
        boolean foundAndAdded = false;

        for (String key : defaultTagInfos.keySet()) {
            if (defaultTagInfos.get(key).isDesiredTag(tag)) {
                if (putIfAbsentDone(defaultTags, key, tag)) {
                    foundAndAdded = true;
                }
                break;
            }
        }

        return foundAndAdded;
    }

    private static void prependDefaults(
            Map<String, NemurTag> defaultTags,
            NemurTagList orderedTagList,
            Map<String, TagInfo> defaultTagInfo
    ) {
        if (defaultTags.isEmpty()) return;

        List<String> reverseOrderedKeys = new ArrayList<>(defaultTagInfo.keySet());
        Collections.reverse(reverseOrderedKeys);

        for (String key : reverseOrderedKeys) {
            if (defaultTags.containsKey(key)) {
                NemurTag tag = defaultTags.get(key);

                orderedTagList.add(0, tag);
            }
        }
    }

    public static NemurTagList getOrderedTagsList(NemurTagList tagList, Map<String, TagInfo> defaultTagInfos) {
        NemurTagList orderedTagList = new NemurTagList();
        Map<String, NemurTag> defaultTags = new HashMap<>();

        for (NemurTag tag : tagList) {
            if (defaultTagFoundAndAdded(defaultTagInfos, tag, defaultTags)) continue;

            orderedTagList.add(tag);
        }
        prependDefaults(defaultTags, orderedTagList, defaultTagInfos);

        return orderedTagList;
    }

    public static boolean isTagManagedInVariousTab(
            NemurTag tag,
            boolean isTopLevelNemur,
            FilteredRecyclerView recyclerView
    ) {
        if (isTopLevelNemur) {
            if (NemurUtils.isDefaultInMemoryTag(tag)) {
                return true;
            }

            boolean isSpecialTag = tag != null
                                    &&
                                    (tag.isWomen());

            boolean tabsInitializingNow = recyclerView != null && recyclerView.getCurrentFilter() == null;

            boolean tagIsVariousProducts = tag != null
                                                        && (
                                                                tag.isVariousProducts()
                                                        );

            if (isSpecialTag) {
                return false;
            } else if (tabsInitializingNow) {
                return tagIsVariousProducts;
            } else if (recyclerView != null && recyclerView.getCurrentFilter() instanceof NemurTag) {
                if (recyclerView.isValidFilter(tag)) {
                    return tag.isVariousProducts();
                } else {
                    // If we reach here it means we are setting a followed tag or buyer in the Following tab
                    return true;
                }
            } else {
                return false;
            }
        } else {
            return tag != null && tag.isVariousProducts();
        }
    }

    public static @NonNull NemurTag getValidTagForSharedPrefs(
            @NonNull NemurTag tag,
            boolean isTopLevelNemur,
            FilteredRecyclerView recyclerView,
            @NonNull NemurTag defaultTag
    ) {
        if (!isTopLevelNemur) {
            return tag;
        }

        boolean isValidFilter = (recyclerView != null && recyclerView.isValidFilter(tag));
        boolean isSpecialTag = tag.isWomen();
        if (!isSpecialTag && !isValidFilter && isTagManagedInVariousTab(tag, isTopLevelNemur, recyclerView)) {
            return defaultTag;
        }

        return tag;
    }

    public static boolean isDefaultInMemoryTag(NemurTag tag) {
        return tag != null && tag.isDefaultInMemoryTag();
    }
}

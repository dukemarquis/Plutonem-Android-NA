package com.plutonem.ui.nemur.utils;

import androidx.annotation.NonNull;

import com.plutonem.datasets.NemurTagTable;
import com.plutonem.models.NemurTag;
import com.plutonem.models.NemurTagType;

import org.apache.commons.text.StringEscapeUtils;
import org.wordpress.android.util.PhotonUtils;

import java.util.Locale;

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

    public static NemurTag getTagFromTagName(String tagName, NemurTagType tagType, boolean isDefaultTag) {
        NemurTag tag = NemurTagTable.getTag(tagName, tagType);
        if (tag != null) {
            return tag;
        } else {
            return createTagFromTagName(tagName, tagType, isDefaultTag);
        }
    }

//    public static NemurTag createTagFromTagName(String tagName, NemurTagType tagType, boolean isDefaultTag) {
//        String tagSlug = sanitizeWithDashes(tagName).toLowerCase(Locale.ROOT);
//        String tagDisplayName = tagType == NemurTagType.DEFAULT ? tagName : tagSlug;
//        return new NemurTag(
//                tagSlug,
//                tagDisplayName,
//                tagName,
//                null,
//                tagType,
//                isDefaultTag
//        );
//    }

    public static NemurTag createTagFromTagName(String tagName, NemurTagType tagType, boolean isDefaultTag) {
        String tagSlug = sanitizeWithDashes(tagName).toLowerCase(Locale.ROOT);
        String tagDisplayName = tagType == NemurTagType.DEFAULT ? tagName : tagSlug;
        return new NemurTag(
                "Nemur",
                null,
                tagName,
                "http://39.99.148.207/rest/v1.2/nem/buyers/10/orders",
                tagType,
                isDefaultTag
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

    /*
     * used when storing search results in the reader post table
     */
    public static NemurTag getTagForSearchQuery(@NonNull String query) {
        String trimQuery = query != null ? query.trim() : "";
        String slug = NemurUtils.sanitizeWithDashes(trimQuery);
        return new NemurTag(slug, trimQuery, trimQuery, null, NemurTagType.SEARCH);
    }
}

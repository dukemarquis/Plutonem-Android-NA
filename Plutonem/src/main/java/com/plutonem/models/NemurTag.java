package com.plutonem.models;

import android.text.TextUtils;

import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.nemur.utils.NemurUtils;

import org.wordpress.android.util.StringUtils;

import java.io.Serializable;
import java.util.Locale;

public class NemurTag implements Serializable, FilterCriteria {
    public static final String VARIOUS_PATH = String.format(Locale.US, "nem/buyers/%d/orders",
            NemurConstants.VARIOUS_BUYER_ID);
    public static final String WOMEN_PATH = String.format(Locale.US, "nem/buyers/%d/orders",
            NemurConstants.WOMEN_BUYER_ID);
    public static final String COVID19_PATH = String.format(Locale.US, "nem/buyers/%d/orders",
            NemurConstants.COVID19_BUYER_ID);
    public static final String HEELS_PATH = String.format(Locale.US, "nem/buyers/%d/orders",
            NemurConstants.HEELS_BUYER_ID);

    public static final String TAG_TITLE_VARIOUS_PRODUCTS = "Various Products";
    public static final String TAG_TITLE_DEFAULT = TAG_TITLE_VARIOUS_PRODUCTS;
    public static final String TAG_ENDPOINT_DEFAULT = VARIOUS_PATH;

    private String mTagSlug; // tag for API calls
    private String mTagDisplayName; // tag for display, usually the same as the slug
    private String mTagTitle; // title, used for default tags
    private String mEndpoint; // endpoint for updating orders with this tag

    private boolean mIsDefaultInMemoryTag;

    public final NemurTagType tagType;

    public NemurTag(String slug,
                     String displayName,
                     String title,
                     String endpoint,
                     NemurTagType tagType) {
        this(slug, displayName, title, endpoint, tagType, false);
    }

    public NemurTag(String slug,
                     String displayName,
                     String title,
                     String endpoint,
                    NemurTagType tagType,
                     boolean isDefaultInMemoryTag) {
        // we need a slug since it's used to uniquely ID the tag (including setting it as the
        // primary key in the tag table)
        if (TextUtils.isEmpty(slug)) {
            if (!TextUtils.isEmpty(title)) {
                setTagSlug(NemurUtils.sanitizeWithDashes(title));
            } else {
                setTagSlug(getTagSlugFromEndpoint(endpoint));
            }
        } else {
            setTagSlug(slug);
        }

        setTagDisplayName(displayName);
        setTagTitle(title);
        setEndpoint(endpoint);
        this.tagType = tagType;
        mIsDefaultInMemoryTag = isDefaultInMemoryTag;
    }

    public String getEndpoint() {
        return StringUtils.notNullStr(mEndpoint);
    }

    public void setEndpoint(String endpoint) {
        this.mEndpoint = StringUtils.notNullStr(endpoint);
    }

    public String getTagTitle() {
        return StringUtils.notNullStr(mTagTitle);
    }

    public void setTagTitle(String title) {
        this.mTagTitle = StringUtils.notNullStr(title);
    }

    private boolean hasTagTitle() {
        return !TextUtils.isEmpty(mTagTitle);
    }

    public String getTagDisplayName() {
        return StringUtils.notNullStr(mTagDisplayName);
    }

    public void setTagDisplayName(String displayName) {
        this.mTagDisplayName = StringUtils.notNullStr(displayName);
    }

    public String getTagSlug() {
        return StringUtils.notNullStr(mTagSlug);
    }

    private void setTagSlug(String slug) {
        this.mTagSlug = StringUtils.notNullStr(slug);
    }

    /*
     * returns the tag name for use in the application log - if this is a default tag it returns
     * the full tag name
     */
    public String getTagNameForLog() {
        String tagSlug = getTagSlug();
        if (tagType == NemurTagType.DEFAULT) {
            return tagSlug;
        } else {
            return "...";
        }
    }

    /*
     * extracts the tag slug from a valid nemur/tags/[mTagSlug]/orders endpoint
     */
    private static String getTagSlugFromEndpoint(final String endpoint) {
        if (TextUtils.isEmpty(endpoint)) {
            return "";
        }

        // make sure passed endpoint is valid
        if (!endpoint.endsWith("/orders")) {
            return "";
        }
        int start = endpoint.indexOf("/nemur/tags/");
        if (start == -1) {
            return "";
        }

        // skip "/nemur/tags/" then find the next "/"
        start += 11;
        int end = endpoint.indexOf("/", start);
        if (end == -1) {
            return "";
        }

        return endpoint.substring(start, end);
    }

    public static boolean isSameTag(NemurTag tag1, NemurTag tag2) {
        return tag1 != null && tag2 != null && tag1.tagType == tag2.tagType && tag1.getTagSlug()
                                                                                    .equalsIgnoreCase(tag2.getTagSlug());
    }

    public boolean isHeels() {
        return tagType == NemurTagType.DEFAULT && getEndpoint().endsWith(HEELS_PATH);
    }

    public boolean isCovid19() {
        return tagType == NemurTagType.DEFAULT && getEndpoint().endsWith(COVID19_PATH);
    }

    public boolean isVariousProducts() {
        return tagType == NemurTagType.DEFAULT && getEndpoint().endsWith(VARIOUS_PATH);
    }

    public boolean isDefaultInMemoryTag() {
        return tagType == NemurTagType.DEFAULT && mIsDefaultInMemoryTag;
    }

    public boolean isWomen() {
        return tagType == NemurTagType.DEFAULT && getEndpoint().endsWith(WOMEN_PATH);
    }


    /*
     * the label is the text displayed in the dropdown filter
     */
    @Override
    public String getLabel() {
        if (isTagDisplayNameAlphaNumeric()) {
            return getTagDisplayName().toLowerCase(Locale.ROOT);
        } else if (hasTagTitle()) {
            return getTagTitle();
        } else {
            return getTagDisplayName();
        }
    }

    /*
     * returns true if the tag display name contains only alpha-numeric characters or hyphens
     */
    private boolean isTagDisplayNameAlphaNumeric() {
        if (TextUtils.isEmpty(mTagDisplayName)) {
            return false;
        }

        for (int i = 0; i < mTagDisplayName.length(); i++) {
            char c = mTagDisplayName.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-') {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof NemurTag) {
            NemurTag tag = (NemurTag) object;
            return (tag.tagType == this.tagType && tag.getLabel().equals(this.getLabel()));
        } else {
            return false;
        }
    }
}

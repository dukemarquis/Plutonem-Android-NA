package com.plutonem.models;

import android.text.TextUtils;

import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;
import com.plutonem.ui.nemur.utils.NemurUtils;

import org.json.JSONObject;
import org.wordpress.android.util.HtmlUtils;
import org.wordpress.android.util.JSONUtils;
import org.wordpress.android.util.StringUtils;

public class NemurOrder {
    private String mPseudoId;
    public long orderId;
    public long buyerId;
    public long accountId;

    private String mTitle;
    private String mPrice;
    private String mItemDistributionMode;
    private String mAccountName;
    private String mBuyerName;

    private String mDatePublished;

    private String mFeaturedImage;
    private String mFeaturedVideo;

    private NemurCardType mCardType = NemurCardType.DEFAULT;

    public static NemurOrder fromJson(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("null json order");
        }

        NemurOrder order = new NemurOrder();

        order.orderId = json.optLong("ID");
        order.buyerId = json.optLong("buyer_ID");

        if (json.has("pseudo_ID")) {
            order.mPseudoId = JSONUtils.getString(json, "pseudo_ID"); // nem/ endpoint
        } else {
            order.mPseudoId = JSONUtils.getString(json, "global_ID"); // buyers/ endpoint
        }

        order.mPrice = JSONUtils.getStringDecoded(json, "price");
        order.mTitle = JSONUtils.getStringDecoded(json, "title");
        order.mItemDistributionMode = JSONUtils.getStringDecoded(json, "item_distribution_mode");

        // parse the account section
        assignAccountFromJson(order, json.optJSONObject("account"));

        order.mFeaturedImage = JSONUtils.getString(json, "featured_image");
        order.mFeaturedVideo = JSONUtils.getString(json, "featured_video");
        order.mBuyerName = JSONUtils.getStringDecoded(json, "buyer_name");

        order.mDatePublished = JSONUtils.getString(json, "date");

        // remove html from title (rare, but does happen)
        if (order.hasTitle() && order.mTitle.contains("<") && order.mTitle.contains(">")) {
            order.mTitle = HtmlUtils.stripHtml(order.mTitle);
        }

        // buyer metadata - returned when ?meta=buyer was added to the request
        JSONObject jsonBuyer = JSONUtils.getJSONChild(json, "meta/data/buyer");
        if (jsonBuyer != null) {
            order.buyerId = jsonBuyer.optInt("ID");
            order.mBuyerName = JSONUtils.getString(jsonBuyer, "name");
        }

        // if there's no featured image, check if featured media has been set to an image
        if (!order.hasFeaturedImage() && json.has("featured_media")) {
            JSONObject jsonMedia = json.optJSONObject("featured_media");
            String type = JSONUtils.getString(jsonMedia, "type");
            if (type.equals("image")) {
                order.mFeaturedImage = JSONUtils.getString(jsonMedia, "uri");
            }
        }

        // set the card type last since it depends on information contained in the order - note
        // that this is stored in the order table rather than calculated on-the-fly
        order.setCardType(NemurCardType.fromNemurOrder(order));

        return order;
    }

    /*
     * assigns account-related info to the passed order from the passed JSON "account" object
     */
    private static void assignAccountFromJson(NemurOrder order, JSONObject jsonAccount) {
        if (jsonAccount == null) {
            return;
        }

        order.mAccountName = JSONUtils.getStringDecoded(jsonAccount, "name");
        order.accountId = jsonAccount.optLong("ID");
    }

    // --------------------------------------------------------------------------------------------

    public String getAccountName() {
        return StringUtils.notNullStr(mAccountName);
    }

    public void setAccountName(String name) {
        this.mAccountName = StringUtils.notNullStr(name);
    }

    public String getTitle() {
        return StringUtils.notNullStr(mTitle);
    }

    public void setTitle(String title) {
        this.mTitle = StringUtils.notNullStr(title);
    }

    public String getPrice() {
        return StringUtils.notNullStr(mPrice);
    }

    public void setPrice(String price) {
        this.mPrice = StringUtils.notNullStr(price);
    }

    public String getItemDistributionMode() {
        return StringUtils.notNullStr(mItemDistributionMode);
    }

    public void setItemDistributionMode(String itemDistributionMode) {
        this.mItemDistributionMode = StringUtils.notNullStr(itemDistributionMode);
    }

    public String getFeaturedImage() {
        return StringUtils.notNullStr(mFeaturedImage);
    }

    public void setFeaturedImage(String featuredImage) {
        this.mFeaturedImage = StringUtils.notNullStr(featuredImage);
    }

    public String getFeaturedVideo() {
        return StringUtils.notNullStr(mFeaturedVideo);
    }

    public void setFeaturedVideo(String featuredVideo) {
        this.mFeaturedVideo = StringUtils.notNullStr(featuredVideo);
    }

    public String getBuyerName() {
        return StringUtils.notNullStr(mBuyerName);
    }

    public void setBuyerName(String buyerName) {
        this.mBuyerName = StringUtils.notNullStr(buyerName);
    }

    public String getPseudoId() {
        return StringUtils.notNullStr(mPseudoId);
    }

    public void setPseudoId(String pseudoId) {
        this.mPseudoId = StringUtils.notNullStr(pseudoId);
    }

    public String getDatePublished() {
        return StringUtils.notNullStr(mDatePublished);
    }

    public void setDatePublished(String dateStr) {
        this.mDatePublished = StringUtils.notNullStr(dateStr);
    }

    public boolean hasPrice() {
        return !TextUtils.isEmpty(mPrice);
    }

    public boolean hasFeaturedImage() {
        return !TextUtils.isEmpty(mFeaturedImage);
    }

    public boolean hasFeaturedVideo() {
        return !TextUtils.isEmpty(mFeaturedVideo);
    }

    public boolean hasTitle() {
        return !TextUtils.isEmpty(mTitle);
    }

    /*
     * returns true if the passed order appears to be the same as this one - used when orders are
     * retrieved to determine which ones are new/changed/unchanged
     */
    public boolean isSameOrder(NemurOrder order) {
        return order != null
                && order.buyerId == this.buyerId
                && order.orderId == this.orderId
                && order.getTitle().equals(this.getTitle())
                && order.getPrice().equals(this.getPrice())
                && order.getItemDistributionMode().equals(this.getItemDistributionMode())
                && order.getFeaturedImage().equals(this.getFeaturedImage())
                && order.getFeaturedVideo().equals(this.getFeaturedVideo());
    }

    public boolean hasIds(NemurBuyerIdOrderId ids) {
        return ids != null
                && ids.getBuyerId() == this.buyerId
                && ids.getOrderId() == this.orderId;
    }

    public NemurCardType getCardType() {
        return mCardType != null ? mCardType : NemurCardType.DEFAULT;
    }

    public void setCardType(NemurCardType cardType) {
        this.mCardType = cardType;
    }

    /****
     * the following are transient variables - not stored in the db or returned in the json - whose
     * sole purpose is to cache commonly-used values for the order that speeds up using them inside
     * adapters
     ****/

    /*
     * returns the featured image url as a photon url set to the passed width/height
     */
    private transient String mFeaturedImageForDisplay;

    public String getFeaturedImageForDisplay(int width, int height) {
        if (mFeaturedImageForDisplay == null) {
            if (!hasFeaturedImage()) {
                mFeaturedImageForDisplay = "";
            } else {
                mFeaturedImageForDisplay = NemurUtils.getResizedImageUrl(mFeaturedImage, width, height);
            }
        }
        return mFeaturedImageForDisplay;
    }

    /*
     * used when a unique numeric id is required by an adapter (when hasStableIds() = true)
     */
    private transient long mStableId;

    public long getStableId() {
        if (mStableId == 0) {
            mStableId = (mPseudoId != null ? mPseudoId.hashCode() : 0);
        }
        return mStableId;
    }
}

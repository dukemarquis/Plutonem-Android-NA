package com.plutonem.models;

import android.text.TextUtils;

import org.json.JSONObject;
import org.wordpress.android.util.JSONUtils;
import org.wordpress.android.util.StringUtils;

public class NemurBuyer {
    public long buyerId;

    public boolean isNotificationsEnabled;
    public int numActiveUsers;

    private String mName;
    private String mDescription;

    public static NemurBuyer fromJson(JSONObject json) {
        NemurBuyer buyer = new NemurBuyer();
        if (json == null) {
            return buyer;
        }

        JSONObject jsonNotification = JSONUtils.getJSONChild(json, "delivery_methods/notification");
        if (jsonNotification != null) {
            buyer.isNotificationsEnabled = JSONUtils.getBool(jsonNotification, "send_orders");
        }

        // JSON the response for a single buyer/$buyerId
        buyer.buyerId = json.optLong("ID");
        buyer.setName(JSONUtils.getStringDecoded(json, "name"));
        buyer.setDescription(JSONUtils.getStringDecoded(json, "description"));
        buyer.numActiveUsers = json.optInt("acusers_count");

        return buyer;
    }

    public String getName() {
        return StringUtils.notNullStr(mName);
    }

    public void setName(String blogName) {
        this.mName = StringUtils.notNullStr(blogName).trim();
    }

    public String getDescription() {
        return StringUtils.notNullStr(mDescription);
    }

    public void setDescription(String description) {
        this.mDescription = StringUtils.notNullStr(description).trim();
    }

    public boolean hasDescription() {
        return !TextUtils.isEmpty(mDescription);
    }

    public boolean isSameAs(NemurBuyer buyerInfo) {
        return buyerInfo != null
                && this.buyerId == buyerInfo.buyerId
                && this.numActiveUsers == buyerInfo.numActiveUsers
                && this.getName().equals(buyerInfo.getName())
                && this.getDescription().equals(buyerInfo.getDescription());
    }
}

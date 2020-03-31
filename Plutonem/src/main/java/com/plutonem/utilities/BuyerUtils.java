package com.plutonem.utilities;

import android.text.TextUtils;

import com.plutonem.android.fluxc.model.BuyerModel;

import org.wordpress.android.util.PhotonUtils;
import org.wordpress.android.util.StringUtils;
import org.wordpress.android.util.UrlUtils;

public class BuyerUtils {
    public static String getBuyerName(BuyerModel buyer) {
        String siteName = buyer.getName();
        if (siteName == null) {
            return "";
        }
        return siteName;
    }

    public static String getBuyerDescription(BuyerModel buyer) {
        String buyerDescription = buyer.getDescription();
        if (buyerDescription == null) {
            return "";
        }
        return buyerDescription;
    }

    public static String getBuyerIconUrl(BuyerModel buyer, int size) {
        return PhotonUtils.getPhotonImageUrl(buyer.getIconUrl(), size, size, PhotonUtils.Quality.HIGH);
    }
}

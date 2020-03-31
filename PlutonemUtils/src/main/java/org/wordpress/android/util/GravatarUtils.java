package org.wordpress.android.util;

import android.text.TextUtils;

/**
 * see https://en.gravatar.com/site/implement/images/
 */
public class GravatarUtils {
    // by default tell gravatar to respond to non-existent images with a 404 - this means
    // it's up to the caller to catch the 404 and provide a suitable default image
    private static final DefaultImage DEFAULT_GRAVATAR = DefaultImage.MYSTERY_MAN;

    private enum DefaultImage {
        MYSTERY_MAN;

        @Override
        public String toString() {
            switch (this) {
                case MYSTERY_MAN:
                    return "mm";
                default:
                    return "blank";
            }
        }
    }

    /*
     * gravatars often contain the ?s= parameter which determines their size - detect this and
     * replace it with a new ?s= parameter which requests the avatar at the exact size needed
     */
    public static String fixGravatarUrl(final String imageUrl, int avatarSz) {
        return fixGravatarUrl(imageUrl, avatarSz, DEFAULT_GRAVATAR);
    }

    public static String fixGravatarUrl(final String imageUrl, int avatarSz, DefaultImage defaultImage) {
        if (TextUtils.isEmpty(imageUrl)) {
            return "";
        }

        // if this isn't a gravatar image, return as resized photon image url
        if (!imageUrl.contains("gravatar.com")) {
            return PhotonUtils.getPhotonImageUrl(imageUrl, avatarSz, avatarSz);
        }

        // remove all other params, then add query string for size and default image
        return UrlUtils.removeQuery(imageUrl) + "?s=" + avatarSz + "&d=" + defaultImage.toString();
    }
}

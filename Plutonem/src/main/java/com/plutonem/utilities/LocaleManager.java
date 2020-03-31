package com.plutonem.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.wordpress.android.util.LanguageUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Helper class for working with localized strings. Ensures updates to the users
 * selected language is properly saved and resources appropriately updated for the
 * android version.
 */
public class LocaleManager {
    /**
     * Key used for saving the language selection to shared preferences.
     */
    private static final String LANGUAGE_KEY = "language-pref";

    /**
     * Pattern to split a language string (to parse the language and region values).
     */
    private static Pattern languageSplitter = Pattern.compile("_");

    /**
     * If the user has selected a language other than the device default, return that
     * language code, else just return the device default language code.
     *
     * @return The 2-letter language code (example "en")
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(LANGUAGE_KEY, LanguageUtils.getCurrentDeviceLanguageCode());
    }

    /**
     * Method gets around a bug in the java.util.Formatter for API 7.x as detailed here
     * [https://bugs.openjdk.java.net/browse/JDK-8167567]. Any strings that contain
     * locale-specific grouping separators should use:
     * <code>
     * String.format(LocaleManager.getSafeLocale(context), baseString, val)
     * </code>
     * <p>
     * An example of a string that contains locale-specific grouping separators:
     * <code>
     * <string name="test">%,d likes</string>
     * </code>
     */
    public static Locale getSafeLocale(@Nullable Context context) {
        Locale baseLocale;
        if (context == null) {
            baseLocale = Locale.getDefault();
        } else {
            Configuration config = context.getResources().getConfiguration();
            baseLocale = Build.VERSION.SDK_INT >= 24 ? config.getLocales().get(0) : config.locale;
        }

        if (Build.VERSION.SDK_INT >= 24) {
            return languageLocale(baseLocale.getLanguage());
        } else {
            return baseLocale;
        }
    }

    /**
     * Gets a locale for the given language code.
     *
     * @param languageCode The language code (example "en" or "es-US"). If null or empty will return
     *                     the current default locale.
     */
    public static Locale languageLocale(@Nullable String languageCode) {
        if (TextUtils.isEmpty(languageCode)) {
            return Locale.getDefault();
        }
        // Attempt to parse language and region codes.
        String[] opts = languageSplitter.split(languageCode, 0);
        if (opts.length > 1) {
            return new Locale(opts[0], opts[1]);
        } else {
            return new Locale(opts[0]);
        }
    }
}

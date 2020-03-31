package com.plutonem;

import android.app.Service;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.net.http.HttpResponseCache;
import android.text.TextUtils;
import android.util.AndroidRuntimeException;
import android.webkit.WebSettings;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.multidex.MultiDexApplication;

import com.android.volley.RequestQueue;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.generated.ListActionBuilder;
import com.plutonem.android.fluxc.persistence.WellSqlConfig;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.android.fluxc.store.AccountStore.OnAuthenticationChanged;
import com.plutonem.android.fluxc.store.AccountStore.OnRegistrationChanged;
import com.plutonem.android.fluxc.store.ListStore.RemoveExpiredListsPayload;
import com.plutonem.android.fluxc.utils.ErrorUtils.OnUnexpectedError;
import com.plutonem.android.networking.RestClientUtils;
import com.plutonem.modules.AppComponent;
import com.plutonem.modules.DaggerAppComponent;
import com.plutonem.networking.OAuthAuthenticator;
import com.plutonem.rest.RestClient;
import com.plutonem.ui.nemur.services.update.NemurUpdateLogic;
import com.plutonem.ui.nemur.services.update.NemurUpdateServiceStarter;
import com.plutonem.utilities.BitmapLruCache;
import com.yarolegovich.wellsql.WellSql;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.PackageUtils;
import org.wordpress.android.util.ProfilingUtils;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

import javax.inject.Inject;
import javax.inject.Named;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasServiceInjector;
import dagger.android.support.HasSupportFragmentInjector;

public class Plutonem extends MultiDexApplication implements HasServiceInjector, HasSupportFragmentInjector {
    public static final String BUYER = "BUYER";
    public static String versionName;

    private static RestClientUtils sRestClientUtilsVersion1p1;
    private static RestClientUtils sRestClientUtilsVersion1p2;

    private static Context mContext;
    private static BitmapLruCache mBitmapCache;

    @Inject DispatchingAndroidInjector<Service> mServiceDispatchingAndroidInjector;
    @Inject DispatchingAndroidInjector<Fragment> mSupportFragmentInjector;

    @Inject Dispatcher mDispatcher;
    @Inject AccountStore mAccountStore;

    @Inject @Named("custom-ssl") RequestQueue mRequestQueue;
    public static RequestQueue sRequestQueue;
    @Inject OAuthAuthenticator mOAuthAuthenticator;
    public static OAuthAuthenticator sOAuthAuthenticator;

    protected AppComponent mAppComponent;

    public AppComponent component() {
        return mAppComponent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        initWellSql();

        // Init Dagger
        initDaggerComponent();
        component().inject(this);
        mDispatcher.register(this);

        // Init static fields from dagger injected singletons, for legacy Actions and Utilities
        sRequestQueue = mRequestQueue;
        sOAuthAuthenticator = mOAuthAuthenticator;

        ProfilingUtils.start("App Startup");
        // Enable log recording
        AppLog.enableRecording(true);
        AppLog.i(T.UTILS, "Plutonem.onCreate");

        versionName = PackageUtils.getVersionName(this);
        enableHttpResponseCache(mContext);

        // EventBus setup
        EventBus.TAG = "Plutonem-EVENT";
        EventBus.builder()
                .logNoSubscriberMessages(false)
                .sendNoSubscriberEvent(false)
                .throwSubscriberException(true)
                .installDefaultEventBus();

        RestClientUtils.setUserAgent(getUserAgent());

        MemoryAndConfigChangeMonitor memoryAndConfigChangeMonitor = new MemoryAndConfigChangeMonitor();
        registerComponentCallbacks(memoryAndConfigChangeMonitor);

        // Allows vector drawable from resources (in selectors for instance) on Android < 21 (can cause issues
        // with memory usage and the use of Configuration). More information: http://bit.ly/2H1KTQo
        // Note: if removed, this will cause crashes on Android < 21
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        // remove expired lists
        mDispatcher.dispatch(ListActionBuilder.newRemoveExpiredListsAction(new RemoveExpiredListsPayload()));

        startPostInitServices(mContext);
    }

    // note that this is overridden in PlutonemDebug
    protected void initWellSql() {
        WellSql.init(new WellSqlConfig(getApplicationContext()));
    }

    protected void initDaggerComponent() {
        mAppComponent = DaggerAppComponent.builder()
                .application(this)
                .build();
    }

    public static Context getContext() {
        return mContext;
    }

    public static RestClientUtils getRestClientUtilsV1_1() {
        if (sRestClientUtilsVersion1p1 == null) {
            sRestClientUtilsVersion1p1 = new RestClientUtils(mContext, sRequestQueue, sOAuthAuthenticator,
                    null, RestClient.REST_CLIENT_VERSIONS.V1_1);
        }
        return sRestClientUtilsVersion1p1;
    }

    public static RestClientUtils getRestClientUtilsV1_2() {
        if (sRestClientUtilsVersion1p2 == null) {
            sRestClientUtilsVersion1p2 = new RestClientUtils(mContext, sRequestQueue, sOAuthAuthenticator,
                    null, RestClient.REST_CLIENT_VERSIONS.V1_2);
        }
        return sRestClientUtilsVersion1p2;
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRegistrationChanged(OnRegistrationChanged event) {
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthenticationChanged(OnAuthenticationChanged event) {
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnexpectedError(OnUnexpectedError event) {
        AppLog.d(T.API, "Receiving OnUnexpectedError event, message: " + event.exception.getMessage());
    }

    private static String mDefaultUserAgent;

    /**
     * Device's default User-Agent string.
     * E.g.:
     * "Mozilla/5.0 (Linux; Android 6.0; Android SDK built for x86_64 Build/MASTER; wv)
     * AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/44.0.2403.119 Mobile
     * Safari/537.36"
     */
    public static String getDefaultUserAgent() {
        if (mDefaultUserAgent == null) {
            try {
                mDefaultUserAgent = WebSettings.getDefaultUserAgent(getContext());
            } catch (AndroidRuntimeException | NullPointerException | IllegalArgumentException e) {
                // Catch AndroidRuntimeException that could be raised by the WebView() constructor.
                // See https://github.com/wordpress-mobile/WordPress-Android/issues/3594
                // Catch NullPointerException that could be raised by WebSettings.getDefaultUserAgent()
                // See https://github.com/wordpress-mobile/WordPress-Android/issues/3838
                // Catch IllegalArgumentException that could be raised by WebSettings.getDefaultUserAgent()
                // See https://github.com/wordpress-mobile/WordPress-Android/issues/9015

                // initialize with the empty string, it's a rare issue
                mDefaultUserAgent = "";
            }
        }
        return mDefaultUserAgent;
    }

    public static final String USER_AGENT_APPNAME = "pn-android";
    private static String mUserAgent;

    /**
     * User-Agent string when making HTTP connections, for both API traffic and WebViews.
     * Appends "pn-android/version" to WebView's default User-Agent string for the webservers
     * to get the full feature list of the browser and serve content accordingly, e.g.:
     * "Mozilla/5.0 (Linux; Android 6.0; Android SDK built for x86_64 Build/MASTER; wv)
     * AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/44.0.2403.119 Mobile
     * Safari/537.36 wp-android/4.7"
     * Note that app versions prior to 2.7 simply used "pn-android" as the user agent
     **/
    public static String getUserAgent() {
        if (mUserAgent == null) {
            String defaultUserAgent = getDefaultUserAgent();
            if (TextUtils.isEmpty(defaultUserAgent)) {
                mUserAgent = USER_AGENT_APPNAME + "/" + PackageUtils.getVersionName(getContext());
            } else {
                mUserAgent = defaultUserAgent + " " + USER_AGENT_APPNAME + "/"
                        + PackageUtils.getVersionName(getContext());
            }
        }
        return mUserAgent;
    }

    /*
     * enable caching for HttpUrlConnection
     * http://developer.android.com/training/efficient-downloads/redundant_redundant.html
     */
    private static void enableHttpResponseCache(Context context) {
        try {
            long httpCacheSize = 5 * 1024 * 1024; // 5MB
            File httpCacheDir = new File(context.getCacheDir(), "http");
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (IOException e) {
            AppLog.w(T.UTILS, "Failed to enable http response cache");
        }
    }

    private static void startPostInitServices(Context context) {
        // Get nemur tags so they're available as soon as the Nemur is accessed
        NemurUpdateServiceStarter.startService(context, EnumSet.of(NemurUpdateLogic.UpdateTask.TAGS));
    }

    @Override
    public AndroidInjector<Service> serviceInjector() {
        return mServiceDispatchingAndroidInjector;
    }

    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return mSupportFragmentInjector;
    }

    /**
     * Uses ComponentCallbacks2 is used for memory-related event handling and configuration changes
     */
    private class MemoryAndConfigChangeMonitor implements ComponentCallbacks2 {
        @Override
        public void onConfigurationChanged(final Configuration newConfig) {
            // Reapply locale on configuration change
        }

        @Override
        public void onLowMemory() {
        }

        @Override
        public void onTrimMemory(final int level) {
            boolean evictBitmaps = false;
            switch (level) {
                case TRIM_MEMORY_COMPLETE:
                case TRIM_MEMORY_MODERATE:
                case TRIM_MEMORY_RUNNING_MODERATE:
                case TRIM_MEMORY_RUNNING_CRITICAL:
                case TRIM_MEMORY_RUNNING_LOW:
                    evictBitmaps = true;
                    break;
                case TRIM_MEMORY_BACKGROUND:
                case TRIM_MEMORY_UI_HIDDEN:
                default:
                    break;
            }

            if (evictBitmaps && mBitmapCache != null) {
                mBitmapCache.evictAll();
            }
        }
    }
}

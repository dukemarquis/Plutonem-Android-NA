package com.plutonem.modules;

import android.content.Context;

import com.plutonem.Plutonem;
import com.plutonem.android.fluxc.network.UserAgent;

import dagger.Module;
import dagger.Provides;

@Module
public class AppConfigModule {
    @Provides
    public UserAgent provideUserAgent(Context appContext) {
        return new UserAgent(appContext, Plutonem.USER_AGENT_APPNAME);
    }
}

package com.plutonem.modules;

import com.plutonem.android.fluxc.network.rest.plutonem.auth.AccessToken;
import com.plutonem.networking.OAuthAuthenticator;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class LegacyModule {
    @Singleton
    @Provides
    OAuthAuthenticator provideOAuthAuthenicator(AccessToken accessToken) {
        return new OAuthAuthenticator(accessToken);
    }
}

package com.plutonem.networking;

import com.plutonem.android.fluxc.network.rest.plutonem.auth.AccessToken;
import com.plutonem.android.networking.Authenticator;
import com.plutonem.android.networking.AuthenticatorRequest;

import org.wordpress.android.util.StringUtils;

// TODO: kill this when we don't need any other rest client than the one in FluxC
public class OAuthAuthenticator implements Authenticator {
    private AccessToken mAccessToken;

    public OAuthAuthenticator(AccessToken accessToken) {
        mAccessToken = accessToken;
    }

    @Override
    public void authenticate(final AuthenticatorRequest request) {
        request.sendWithAccessToken(StringUtils.notNullStr(mAccessToken.get()));
    }
}

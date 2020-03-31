package com.plutonem.android.networking;

import com.plutonem.rest.RestClient;
import com.plutonem.rest.RestRequest;
import com.plutonem.rest.RestRequest.ErrorListener;

/**
 * Encapsulates the behaviour for asking the Authenticator for an access token. This
 * allows the request maker to disregard the authentication state when making requests.
 */
public class AuthenticatorRequest {
    private RestRequest mRequest;
    private RestRequest.ErrorListener mListener;
    private RestClient mRestClient;
    private Authenticator mAuthenticator;

    protected AuthenticatorRequest(RestRequest request, ErrorListener listener, RestClient restClient,
                                   Authenticator authenticator) {
        mRequest = request;
        mListener = listener;
        mRestClient = restClient;
        mAuthenticator = authenticator;
    }

    /**
     * Attempt to send the request, checks to see if we have an access token and if not
     * asks the Authenticator to authenticate the request.
     *
     * If no Authenticator is provided the request is always sent.
     */
    protected void send() {
        if (mAuthenticator == null) {
            mRestClient.send(mRequest);
        } else {
            mAuthenticator.authenticate(this);
        }
    }

    public void sendWithAccessToken(String token) {
        mRequest.setAccessToken(token);
        mRestClient.send(mRequest);
    }
}

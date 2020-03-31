package com.plutonem.android.networking;

import com.android.volley.RequestQueue;
import com.plutonem.rest.RestClient;

public interface RestClientFactoryAbstract {
    RestClient make(RequestQueue queue);
    RestClient make(RequestQueue queue, RestClient.REST_CLIENT_VERSIONS version);
}

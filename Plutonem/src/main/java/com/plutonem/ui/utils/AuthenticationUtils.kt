package com.plutonem.ui.utils

import android.util.Base64
import com.plutonem.android.fluxc.network.HTTPAuthManager
import com.plutonem.android.fluxc.network.UserAgent
import com.plutonem.android.fluxc.network.rest.plutonem.auth.AccessToken
import com.plutonem.utilities.PNUrlUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthenticationUtils
@Inject constructor(
        private val accessToken: AccessToken,
        private val httpAuthManager: HTTPAuthManager,
        private val userAgent: UserAgent
) {
    fun getAuthHeaders(url: String): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = userAgent.userAgent
        if (PNUrlUtils.safeToAddWordPressComAuthToken(url)) {
            if (accessToken.exists()) {
                headers["Authorization"] = "Bearer " + accessToken.get()
            }
        } else {
            // Check if we had HTTP Auth credentials for the root url
            val httpAuthModel = httpAuthManager.getHTTPAuthModel(url)
            if (httpAuthModel != null) {
                val creds = String.format("%s:%s", httpAuthModel.username, httpAuthModel.password)
                val auth = "Basic " + Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
                headers["Authorization"] = auth
            }
        }
        return headers
    }
}

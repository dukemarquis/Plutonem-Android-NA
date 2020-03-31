package com.plutonem.networking

import com.android.volley.Request
import com.bumptech.glide.integration.volley.VolleyRequestFactory
import com.bumptech.glide.integration.volley.VolleyStreamFetcher
import com.bumptech.glide.load.data.DataFetcher
import com.plutonem.ui.utils.AuthenticationUtils
import com.plutonem.utilities.PNUrlUtils
import org.wordpress.android.util.UrlUtils
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RequestFactory which adds authorization headers to all Glide requests and makes sure requests to PN endpoints
 * use https.
 */
@Singleton
class GlideRequestFactory @Inject constructor(
        private val authenticationUtils: AuthenticationUtils
) : VolleyRequestFactory {
    override fun create(
            url: String,
            callback: DataFetcher.DataCallback<in InputStream>,
            priority: Request.Priority,
            headers: Map<String, String>
    ): Request<ByteArray>? {
        val httpsUrl: String = convertPNcomUrlToHttps(url)
        return VolleyStreamFetcher.GlideRequest(httpsUrl, callback, priority, addAuthHeaders(url, headers))
    }

    private fun convertPNcomUrlToHttps(url: String): String {
        return if (PNUrlUtils.isPlutonemCom(url) && !UrlUtils.isHttps(url)) UrlUtils.makeHttps(url) else url
    }

    private fun addAuthHeaders(url: String, currentHeaders: Map<String, String>): MutableMap<String, String> {
        val authenticationHeaders = authenticationUtils.getAuthHeaders(url)
        val headers = currentHeaders.toMutableMap()
        authenticationHeaders.entries.forEach { (key, value) ->
            headers[key] = value
        }
        return headers
    }
}

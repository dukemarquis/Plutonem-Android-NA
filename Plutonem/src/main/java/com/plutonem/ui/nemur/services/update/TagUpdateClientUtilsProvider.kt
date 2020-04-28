package com.plutonem.ui.nemur.services.update

import com.plutonem.Plutonem
import com.plutonem.android.networking.RestClientUtils
import javax.inject.Inject

class TagUpdateClientUtilsProvider @Inject constructor() {
    fun getRestClientForTagUpdate(): RestClientUtils {
        return Plutonem.getRestClientUtilsV1_2()
    }

    fun getTagUpdateEndpointURL(): String {
        return Plutonem.getRestClientUtilsV1_2().restClient.endpointURL
    }
}
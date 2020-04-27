package com.plutonem.ui.nemur.services.update

import com.plutonem.Plutonem
import javax.inject.Inject

class TagUpdateClientUtilsProvider @Inject constructor() {
    fun getTagUpdateEndpointURL(): String {
        return Plutonem.getRestClientUtilsV1_2().restClient.endpointURL
    }
}
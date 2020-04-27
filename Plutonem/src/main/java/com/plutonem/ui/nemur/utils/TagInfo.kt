package com.plutonem.ui.nemur.utils

import com.plutonem.models.NemurTag
import com.plutonem.models.NemurTagType

class TagInfo(
        val tagType: NemurTagType,
        private val endPoint: String
) {
    fun isDesiredTag(tag: NemurTag): Boolean {
        return tag.tagType == tagType && (endPoint.isEmpty() || tag.endpoint.endsWith(endPoint))
    }
}
package com.plutonem.utilities.image

import com.plutonem.R
import javax.inject.Inject

class ImagePlaceholderManager @Inject constructor() {
    fun getErrorResource(imgType: ImageType): Int? {
        return when (imgType) {
            ImageType.AVATAR -> R.drawable.bg_rectangle_neutral_10_user_32dp
            ImageType.BYAVATAR -> R.drawable.bg_rectangle_neutral_10_globe_32dp
            ImageType.PHOTO -> R.color.neutral_0
        }
    }

    fun getPlaceholderResource(imgType: ImageType): Int? {
        return when (imgType) {
            ImageType.AVATAR -> R.drawable.bg_oval_neutral_0
            ImageType.BYAVATAR -> R.color.neutral_0
            ImageType.PHOTO -> R.drawable.white_background
        }
    }
}
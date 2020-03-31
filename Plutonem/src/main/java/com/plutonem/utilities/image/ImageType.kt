package com.plutonem.utilities.image

enum class ImageType {
    @Deprecated(
            message = "Use AVATAR_WITH_BACKGROUND or AVATAR_WITHOUT_BACKGROUND instead.",
            replaceWith = ReplaceWith(
                    expression = "AVATAR_WITH_BACKGROUND",
                    imports = ["org.wordpress.android.util.image.ImageType"]))
    AVATAR,
    BYAVATAR,
    PHOTO
}
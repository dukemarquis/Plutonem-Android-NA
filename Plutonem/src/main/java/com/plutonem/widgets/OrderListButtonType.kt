package com.plutonem.widgets

import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.plutonem.R

enum class OrderListButtonType constructor(
        val value: Int,
        @StringRes val textResId: Int,
        @DrawableRes val iconResId: Int,
        @AttrRes val colorAttrId: Int
) {
    BUTTON_RECEIVE(1, R.string.button_receive, R.drawable.ic_receive_white_24dp, R.attr.wpColorTextSubtle);

    companion object {
        fun fromInt(value: Int): OrderListButtonType? = values().firstOrNull { it.value == value }
    }
}
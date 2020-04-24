package com.plutonem.utilities

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.content.res.AppCompatResources

@ColorRes
fun Context.getColorResIdFromAttribute(@AttrRes attribute: Int) =
        TypedValue().let {
            theme.resolveAttribute(attribute, it, true)
            it.resourceId
        }

@ColorInt
fun Context.getColorFromAttribute(@AttrRes attribute: Int) =
        TypedValue().let {
            theme.resolveAttribute(attribute, it, true)
            it.data
        }

fun Context.getColorStateListFromAttribute(@AttrRes attribute: Int): ColorStateList =
        getColorResIdFromAttribute(attribute).let {
            AppCompatResources.getColorStateList(this, it)
        }
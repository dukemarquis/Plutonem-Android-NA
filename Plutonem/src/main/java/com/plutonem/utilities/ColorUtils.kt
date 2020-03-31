package com.plutonem.utilities

import android.annotation.TargetApi
import android.content.res.ColorStateList
import android.os.Build
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

object ColorUtils {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun setImageResourceWithTint(imageView: ImageView, @DrawableRes drawableResId: Int, @ColorRes colorResId: Int) {
        imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(imageView.context, colorResId))
        imageView.setImageResource(drawableResId)
    }
}
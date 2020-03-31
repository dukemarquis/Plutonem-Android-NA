package com.plutonem.viewmodels.helpers

import android.content.Context
import androidx.annotation.StringRes
import org.wordpress.android.util.ToastUtils

class ToastMessageHolder(
        @StringRes val messageRes: Int,
        val duration: ToastUtils.Duration
) {
    fun show(context: Context) {
        ToastUtils.showToast(context, messageRes, duration)
    }
}
package com.plutonem.ui.nemur.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

fun ViewGroup.inflateView(layoutId: Int): View {
    return LayoutInflater.from(context)
            .inflate(layoutId, this, false)
}
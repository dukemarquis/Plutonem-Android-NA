package com.plutonem.ui.nemur.viewholders

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.plutonem.ui.nemur.NemurInterfaces
import com.plutonem.utilities.image.ImageManager

open class BaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    constructor(
            parent: ViewGroup,
            layoutId: Int
    ) : this(parent.inflateView(layoutId))

    open fun renderOrder(order: Any?, imageManager: ImageManager, photonWidth: Int, photonHeight: Int, marginLarge: Int, onOrderSelectedListener: NemurInterfaces.OnOrderSelectedListener) {}

    open fun onRecycled(success: Boolean) {}
}
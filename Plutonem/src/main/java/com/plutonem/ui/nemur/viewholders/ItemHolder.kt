package com.plutonem.ui.nemur.viewholders

import android.view.ViewGroup
import com.plutonem.R

internal abstract class ItemHolder(
    parent: ViewGroup
) : BaseViewHolder(parent, R.layout.nemur_cardview_order) {

    val frameVideo = itemView.findViewById(R.id.frame_video) as ViewGroup
}
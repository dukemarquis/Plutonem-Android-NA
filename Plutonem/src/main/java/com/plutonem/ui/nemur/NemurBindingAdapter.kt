package com.plutonem.ui.nemur

import androidx.core.view.ViewCompat
import androidx.databinding.BindingAdapter
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.plutonem.R
import com.plutonem.models.NemurOrder
import kohii.v1.core.Common
import kohii.v1.exoplayer.Kohii
import kohii.v1.media.MediaItem

/**
 * For DataBinding
 */

@BindingAdapter( "videoItem", "videoUrl", "provider" )
fun setVideo(
    view: PlayerView,
    videoItem: NemurOrder,
    videoUrl: String,
    kohii: Kohii
) {

    (view.findViewById(R.id.exo_content_frame) as? AspectRatioFrameLayout)
            ?.setAspectRatio( 1280.0F / 720.0F )

    val rebinder = kohii.setUp( MediaItem( videoUrl, "mp4" ) ) {
        tag        = "${videoItem.title}::${videoUrl}"
        preload    = true
        repeatMode = Common.REPEAT_MODE_ALL
    }
        .bind( view )

    view.setTag( R.id.nemur_detail_view_tag, rebinder )
    ViewCompat.setTransitionName( view, videoUrl )
}
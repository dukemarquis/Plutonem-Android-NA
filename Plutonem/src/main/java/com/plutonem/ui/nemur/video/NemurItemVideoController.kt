package com.plutonem.ui.nemur.video

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.view.isVisible
import kohii.v1.core.Manager
import kohii.v1.core.Playback

class NemurItemVideoController(
        private val playerManager: Manager,
        private val playerView: ViewGroup,
        private val playerControl: ImageButton
) : View.OnClickListener, Playback.Controller {

    override fun kohiiCanStart() = true

    override fun kohiiCanPause() = true

    override fun onClick(v: View?) {
        val playable = playerManager.findPlayableForContainer( playerView )
        val animatable = playerControl.drawable as android.graphics.drawable.Animatable

        if ( playable != null ) {
            if ( playable.isPlaying() ) {
                playerManager.pause( playable )
                playerControl.isVisible = true
                animatable.start()
            } else {
                playerManager.play( playable )
                playerControl.isVisible = false
            }
        }
    }

}
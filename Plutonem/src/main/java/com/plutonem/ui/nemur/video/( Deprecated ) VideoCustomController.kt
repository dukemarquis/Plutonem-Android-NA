package com.plutonem.ui.nemur.video

import android.view.View
import android.view.ViewGroup
import kohii.v1.core.Manager
import kohii.v1.core.Playback

class VideoCustomController (
        private val manager: Manager,
        val container: ViewGroup
) : View.OnClickListener, Playback.Controller {

    override fun kohiiCanStart() = true

    override fun kohiiCanPause() = true

    override fun onClick(v: View?) {
        val playable = manager.findPlayableForContainer(container)

        if (playable != null) {
            if (playable.isPlaying()) manager.pause(playable)
            else manager.play(playable)
        }
    }
}
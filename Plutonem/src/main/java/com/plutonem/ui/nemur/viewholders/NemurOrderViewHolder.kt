package com.plutonem.ui.nemur.viewholders

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.plutonem.R
import com.plutonem.models.NemurOrder
import com.plutonem.ui.nemur.NemurInterfaces
import com.plutonem.utilities.image.ImageManager
import com.plutonem.utilities.image.ImageType
import com.plutonem.widgets.PNTextView
import kohii.v1.core.Binder
import kohii.v1.core.Common
import kohii.v1.core.Playback
import kohii.v1.exoplayer.Kohii
import java.util.*

internal class NemurOrderViewHolder(
        parent: ViewGroup,
        val kohii: Kohii
) : ItemHolder(parent),
        Playback.StateListener,
        Playback.ArtworkHintListener {

    init {
        frameVideo.isVisible = true
    }

    internal val cardView = itemView.findViewById(R.id.card_view) as CardView
    internal val txtTitle = itemView.findViewById(R.id.text_title) as PNTextView
    internal val txtPrice = itemView.findViewById(R.id.text_price) as PNTextView
    internal val playerView = frameVideo.findViewById(R.id.player_view) as ViewGroup
    internal val imgFeatured = frameVideo.findViewById(R.id.image_featured) as ImageView
    internal val playAgain = frameVideo.findViewById(R.id.player_again) as Button

    internal var playback: Playback? = null

    private var videoImage: String? = null

//    var uniqueGlobalTag: UUID = UUID.randomUUID()

//    private val videoTag: String?
//        get() = "NO::$adapterPosition"

    private val params: Binder.Options.() -> Unit
        get() = {
//            tag = requireNotNull(videoTag)
            repeatMode = Common.REPEAT_MODE_ALL
            artworkHintListener = this@NemurOrderViewHolder
        }

    override fun renderOrder(order: Any?, imageManager: ImageManager, photonWidth: Int, photonHeight: Int, marginLarge: Int, onOrderSelectedListener: NemurInterfaces.OnOrderSelectedListener) {
        super.renderOrder(order, imageManager, photonWidth, photonHeight, marginLarge, onOrderSelectedListener)
        (order as? NemurOrder)?.also {
            imageManager.cancelRequestAndClearImageView(imgFeatured)
            txtTitle.visibility = View.VISIBLE
            txtTitle.text = it.title

            if (it.hasPrice()) {
                txtPrice.visibility = View.VISIBLE
                txtPrice.text = it.price
            } else {
                txtPrice.visibility = View.GONE
            }

            val titleMargin: Int
            if (it.hasFeaturedImage() && it.hasFeaturedVideo()) {
                videoImage = it.featuredImage
                imageManager.load(imgFeatured, ImageType.PHOTO,
                        it.getFeaturedImageForDisplay(photonWidth, photonHeight), ScaleType.CENTER_INSIDE)
                kohii.setUp(it.featuredVideo, params)
                        .bind(playerView) { pk ->
                            pk.addStateListener(this@NemurOrderViewHolder)
                            playback = pk
                        }
                frameVideo.visibility = View.VISIBLE
                titleMargin = marginLarge
            } else {
                frameVideo.visibility = View.GONE
                titleMargin = 0;
            }

            // set the top margin of the title based on whether there's a featured video with image
            val layoutParams: LinearLayout.LayoutParams = txtTitle.layoutParams as LinearLayout.LayoutParams
            layoutParams.topMargin = titleMargin;

            cardView.setOnClickListener {
                onOrderSelectedListener.onOrderSelected(order as NemurOrder?)
            }

            playAgain.setOnClickListener {
                if (playAgain.isVisible) playback?.rewind()
            }
        }
    }

    override fun onArtworkHint(
            shouldShow: Boolean,
            position: Long,
            state: Int
    ) {
        imgFeatured.isVisible = shouldShow
        playAgain.isVisible = shouldShow && state == Common.STATE_ENDED
    }

    override fun onRecycled(success: Boolean) {
        videoImage = null
        playback = null
    }
}
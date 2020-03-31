package com.plutonem.ui.nemur.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.plutonem.R;
import com.plutonem.models.NemurOrder;
import com.plutonem.utilities.image.ImageManager;
import com.plutonem.utilities.image.ImageType;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.PhotonUtils;

/**
 * topmost view in order detail - shows short video.
 */
public class NemurOrderDetailHeaderView extends LinearLayout {
    private NemurOrder mOrder;

    public NemurOrderDetailHeaderView(Context context) {
        super(context);
        initView(context);
    }

    public NemurOrderDetailHeaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public NemurOrderDetailHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        inflate(context, R.layout.nemur_order_detail_header_view, this);
    }

    public void setOrder(@NonNull NemurOrder order) {
        mOrder = order;

        showByavatarAndAvatar(mOrder.getFeaturedImage());
    }

    private void showByavatarAndAvatar(String imageUrl) {
        ImageManager imageManager = ImageManager.getInstance();
        boolean hasImage = !TextUtils.isEmpty(imageUrl);

        AppLog.w(AppLog.T.NEMUR, imageUrl);

        View imageFrame = findViewById(R.id.frame_image);
        ImageView image = findViewById(R.id.image);
        imageManager.cancelRequestAndClearImageView(image);

        /*
         * - if there's a byavatar and an avatar, show both of them overlaid using default sizing
         * - if there's only a byavatar, show it the full size of the parent frame and hide the avatar
         * - if there's only an avatar, show it the full size of the parent frame and hide the blavatar
         * - if there's neither a byavatar nor an avatar, hide them both
         */
        if (hasImage) {
            imageManager.load(image, ImageType.PHOTO,
                    PhotonUtils.getPhotonImageUrl(imageUrl, -1, -1), ScaleType.CENTER_CROP);
            image.setVisibility(View.VISIBLE);
        } else {
            image.setVisibility(View.GONE);
        }

        // hide the frame if there's neither a byavatar nor an avatar
        imageFrame.setVisibility(hasImage ? View.VISIBLE : View.GONE);
    }
}

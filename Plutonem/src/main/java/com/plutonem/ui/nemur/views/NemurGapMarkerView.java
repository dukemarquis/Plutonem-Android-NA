package com.plutonem.ui.nemur.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.plutonem.R;
import com.plutonem.models.NemurTag;
import com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter;
import com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter.UpdateAction;

import org.wordpress.android.util.NetworkUtils;

/**
 * marker view between orders indicating a gap in time between them that can be filled in - designed
 * for use inside NemurOrderAdapter
 */
public class NemurGapMarkerView extends RelativeLayout {
    private TextView mText;
    private ProgressBar mProgress;
    private NemurTag mCurrentTag;

    public NemurGapMarkerView(Context context) {
        super(context);
        initView(context);
    }

    public NemurGapMarkerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context);
    }

    public NemurGapMarkerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView(context);
    }

    private void initView(Context context) {
        View view = inflate(context, R.layout.nemur_gap_marker_view, this);
        mText = (TextView) view.findViewById(R.id.text_gap_marker);
        mProgress = (ProgressBar) view.findViewById(R.id.progress_gap_marker);

        mText.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                fillTheGap();
            }
        });
    }

    public void setCurrentTag(NemurTag tag) {
        mCurrentTag = tag;
        hideProgress();
    }

    private void fillTheGap() {
        if (mCurrentTag == null
                || !NetworkUtils.checkConnection(getContext())) {
            return;
        }

        // start service to fill the gap - EventBus will notify the owning fragment of new orders,
        // and will take care of hiding this view
        NemurOrderServiceStarter.startServiceForTag(getContext(), mCurrentTag, UpdateAction.REQUEST_OLDER_THAN_GAP);
        showProgress();
    }

    private void showProgress() {
        mText.setVisibility(View.INVISIBLE);
        mProgress.setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mText.setVisibility(View.VISIBLE);
        mProgress.setVisibility(View.GONE);
    }
}

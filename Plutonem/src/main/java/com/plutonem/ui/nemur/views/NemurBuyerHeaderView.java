package com.plutonem.ui.nemur.views;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.datasets.NemurBuyerTable;
import com.plutonem.models.NemurBuyer;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.actions.NemurBuyerActions;
import com.plutonem.utilities.LocaleManager;

import org.wordpress.android.util.ToastUtils;

/**
 * topmost view in order adapter when showing buyer preview - displays description, acUser
 * count
 */
public class NemurBuyerHeaderView extends LinearLayout {
    public interface OnBuyerInfoLoadedListener {
        void onBuyerInfoLoaded(NemurBuyer buyerInfo);
    }

    private long mBuyerId;
    private NemurBuyer mBuyerInfo;
    private OnBuyerInfoLoadedListener mBuyerInfoListener;

    public NemurBuyerHeaderView(Context context) {
        this(context, null);
    }

    public NemurBuyerHeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NemurBuyerHeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ((Plutonem) context.getApplicationContext()).component().inject(this);
        initView(context);
    }

    private void initView(Context context) {
        inflate(context, R.layout.nemur_buyer_header_view, this);
    }

    public void setOnBuyerInfoLoadedListener(OnBuyerInfoLoadedListener listener) {
        mBuyerInfoListener = listener;
    }

    public void loadBuyerInfo(long buyerId) {
        mBuyerId = buyerId;

        // first get info from local db
        final NemurBuyer localBuyerInfo;
        if (mBuyerId != 0) {
            localBuyerInfo = NemurBuyerTable.getBuyerInfo(mBuyerId);
        } else {
            ToastUtils.showToast(getContext(), R.string.nemur_toast_err_get_buyer_info);
            return;
        }
        if (localBuyerInfo != null) {
            showBuyerInfo(localBuyerInfo);
        }

        // then get from server if doesn't exist locally or is time to update it
        if (localBuyerInfo == null || NemurBuyerTable.isTimeToUpdateBuyerInfo(localBuyerInfo)) {
            NemurActions.UpdateBuyerInfoListener listener = new NemurActions.UpdateBuyerInfoListener() {
                @RequiresApi(api = Build.VERSION_CODES.KITKAT)
                @Override
                public void onResult(NemurBuyer serverBuyerInfo) {
                    if (isAttachedToWindow()) {
                        showBuyerInfo(serverBuyerInfo);
                    }
                }
            };
            NemurBuyerActions.updateBuyerInfo(mBuyerId, listener);
        }
    }

    private void showBuyerInfo(NemurBuyer buyerInfo) {
        // do nothing if unchanged
        if (buyerInfo == null || buyerInfo.isSameAs(mBuyerInfo)) {
            return;
        }

        mBuyerInfo = buyerInfo;

        ViewGroup layoutInfo = findViewById(R.id.layout_buyer_info);
        TextView txtDescription = layoutInfo.findViewById(R.id.text_buyer_description);
        TextView txtAcuserCount = layoutInfo.findViewById(R.id.text_buyer_acuser_count);

        if (buyerInfo.hasDescription()) {
            txtDescription.setText(buyerInfo.getDescription());
            txtDescription.setVisibility(View.VISIBLE);
        } else {
            txtDescription.setVisibility(View.GONE);
        }

        txtAcuserCount.setText(String.format(
                LocaleManager.getSafeLocale(getContext()),
                getContext().getString(R.string.namur_label_acuser_count),
                buyerInfo.numActiveUsers));

        if (layoutInfo.getVisibility() != View.VISIBLE) {
            layoutInfo.setVisibility(View.VISIBLE);
        }

        if (mBuyerInfoListener != null) {
            mBuyerInfoListener.onBuyerInfoLoaded(buyerInfo);
        }
    }
}

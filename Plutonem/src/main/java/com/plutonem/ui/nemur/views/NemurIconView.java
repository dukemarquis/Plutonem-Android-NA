package com.plutonem.ui.nemur.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.plutonem.R;

import java.util.Locale;

/*
 * used when showing buy
 */
public class NemurIconView extends LinearLayout {
    private ImageView mImageView;
    private TextView mTextBuy;
    private int mIconType;

    // these must match the same values in attrs.xml
    private static final int ICON_BUY = 0;

    public NemurIconView(Context context) {
        super(context);
        initView(context, null);
    }

    public NemurIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public NemurIconView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        inflate(context, R.layout.nemur_icon_view, this);

        mImageView = findViewById(R.id.image_buy);
        mTextBuy = findViewById(R.id.text_buy);

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.NemurIconBuyView,
                    0, 0);
            try {
                mIconType = a.getInteger(R.styleable.NemurIconBuyView_nemurIcon, ICON_BUY);
                switch (mIconType) {
                    case ICON_BUY:
                        mImageView.setImageDrawable(ContextCompat.getDrawable(context,
                                R.drawable.nemur_button_buy));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mImageView.setImageTintList(getResources().getColorStateList(
                                    R.color.neutral_accent_neutral_40_selector));
                        }
                        break;
                }
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mImageView.setEnabled(enabled);
        mTextBuy.setEnabled(enabled);
    }

    public void setView() {
        mTextBuy.setText(getContext().getString(R.string.nemur_btn_buy).toUpperCase(Locale.getDefault()));
    }
}

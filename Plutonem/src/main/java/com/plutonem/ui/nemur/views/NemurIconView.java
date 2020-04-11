package com.plutonem.ui.nemur.views;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.plutonem.R;

import java.util.Locale;

/*
 * used when showing buy
 */
public class NemurIconView extends LinearLayout {
    private ImageView mImageAction;
    private TextView mTextAction;
    private int mIconType;

    // these must match the same values in attrs.xml
    private static final int ICON_BUY = 0;
    private static final int ICON_CHAT = 1;

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

        mImageAction = findViewById(R.id.image_action);
        mTextAction = findViewById(R.id.text_action);

        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.NemurIconBuyView,
                    0, 0);
            try {
                mIconType = a.getInteger(R.styleable.NemurIconBuyView_nemurIcon, ICON_BUY);
                switch (mIconType) {
                    case ICON_BUY:
                        ColorStateList buyColor = AppCompatResources
                                .getColorStateList(context, R.color.on_surface_medium_secondary_selector);
                        mImageAction.setImageDrawable(ContextCompat.getDrawable(context,
                                R.drawable.nemur_button_buy));
                        ImageViewCompat.setImageTintList(mImageAction, buyColor);
                        mTextAction.setTextColor(buyColor);
                        break;
                    case ICON_CHAT:
                        ColorStateList chatColor = AppCompatResources
                                .getColorStateList(context, R.color.on_surface_medium_primary_selector);
                        mImageAction.setImageDrawable(ContextCompat.getDrawable(context,
                                R.drawable.ic_comment_white_24dp));
                        ImageViewCompat.setImageTintList(mImageAction, chatColor);
                        mTextAction.setTextColor(chatColor);
                }
            } finally {
                a.recycle();
            }

            // move the comment icon down a bit so it aligns with the text baseline
            if (mIconType == ICON_CHAT) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mImageAction.getLayoutParams();
                params.topMargin = context.getResources().getDimensionPixelSize(R.dimen.margin_extra_extra_small);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mImageAction.setEnabled(enabled);
        mTextAction.setEnabled(enabled);
    }

    public void setAction(int action) {
        mTextAction.setText(action == 0 ?
                getContext().getString(R.string.nemur_btn_buy).toUpperCase(Locale.getDefault()) :
                getContext().getString(R.string.nemur_chat).toUpperCase(Locale.getDefault()));
    }
}

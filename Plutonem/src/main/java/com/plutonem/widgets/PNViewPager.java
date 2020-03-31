package com.plutonem.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;

public class PNViewPager extends ViewPager {
    private boolean mPagingEnabled = true;

    public PNViewPager(Context context) {
        super(context);
    }

    public PNViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mPagingEnabled) {
            try {
                return super.onInterceptTouchEvent(ev);
            } catch (IllegalArgumentException e) {
                AppLog.e(T.UTILS, e);
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility") // we are not detecting tap events, so can ignore this one
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mPagingEnabled) {
            try {
                return super.onTouchEvent(ev);
            } catch (IllegalArgumentException e) {
                AppLog.e(AppLog.T.UTILS, e);
            }
        }
        return false;
    }

    public void setPagingEnabled(boolean pagingEnabled) {
        mPagingEnabled = pagingEnabled;
    }
}

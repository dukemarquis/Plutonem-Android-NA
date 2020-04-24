package com.plutonem.xmpp.ui.util;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;

public class StyledAttributes {

    public static @ColorInt int getColor(Context context, @AttrRes int attr) {
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{attr});
        int color = typedArray.getColor(0,0);
        typedArray.recycle();
        return color;
    }
}

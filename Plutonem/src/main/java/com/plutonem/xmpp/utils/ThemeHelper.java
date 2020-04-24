package com.plutonem.xmpp.utils;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.plutonem.R;

public class ThemeHelper {

    public static void fix(Snackbar snackbar) {
        final Context context = snackbar.getContext();
        TypedArray typedArray = context.obtainStyledAttributes(new int[]{R.attr.TextSizeBody1});
        final float size = typedArray.getDimension(0, 0f);
        typedArray.recycle();
        if (size != 0f) {
            final TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
            final TextView action = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_action);
            if (text != null && action != null) {
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
                action.setTextColor(ContextCompat.getColor(context, R.color.blue));
            }
        }
    }
}

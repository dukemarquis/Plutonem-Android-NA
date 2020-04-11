package com.plutonem.xmpp.ui.util;

import android.view.View;
import android.widget.ListView;

public class ListViewUtils {

    public static void setSelection(final ListView listView, int pos, boolean jumpToBottom) {
        if (jumpToBottom) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            if (lastChild != null) {
                listView.setSelectionFromTop(pos, -lastChild.getHeight());
                return;
            }
        }
        listView.setSelection(pos);
    }
}

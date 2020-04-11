package com.plutonem.xmpp.ui.widget;

import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class ListSelectionManager {
    private ActionMode selectionActionMode;
    private Object selectionIdentifier;
    private TextView selectionTextView;

    public void onCreate(TextView textView, ActionMode.Callback additionalCallback) {
        final CustomCallback callback = new CustomCallback(textView, additionalCallback);
        textView.setCustomSelectionActionModeCallback(callback);
    }

    private class CustomCallback implements ActionMode.Callback {

        private final TextView textView;
        private final ActionMode.Callback additionalCallback;
        Object identifier;

        CustomCallback(TextView textView, ActionMode.Callback additionalCallback) {
            this.textView = textView;
            this.additionalCallback = additionalCallback;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            selectionActionMode = mode;
            selectionIdentifier = identifier;
            selectionTextView = textView;
            if (additionalCallback != null) {
                additionalCallback.onCreateActionMode(mode, menu);
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (additionalCallback != null) {
                additionalCallback.onPrepareActionMode(mode, menu);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (additionalCallback != null && additionalCallback.onActionItemClicked(mode, item)) {
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (additionalCallback != null) {
                additionalCallback.onDestroyActionMode(mode);
            }
            if (selectionActionMode == mode) {
                selectionActionMode = null;
                selectionIdentifier = null;
                selectionTextView = null;
            }
        }
    }
}

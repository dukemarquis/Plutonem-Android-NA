package com.plutonem.xmpp.ui;

import android.app.Activity;
import android.app.Fragment;

import com.plutonem.xmpp.ui.interfaces.OnBackendConnected;

public abstract class XmppFragment extends Fragment implements OnBackendConnected {

    abstract void refresh();

    protected void runOnUiThread(Runnable runnable) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }
}

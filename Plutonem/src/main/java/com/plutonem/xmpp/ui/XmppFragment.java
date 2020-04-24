package com.plutonem.xmpp.ui;

import android.app.Activity;

import androidx.fragment.app.Fragment;

import com.plutonem.xmpp.ui.interfaces.OnBackendConnected;

public abstract class XmppFragment extends Fragment implements OnBackendConnected {

    public abstract void refresh();

    protected void runOnUiThread(Runnable runnable) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(runnable);
        }
    }
}

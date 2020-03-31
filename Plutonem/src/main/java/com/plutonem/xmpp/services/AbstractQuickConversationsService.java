package com.plutonem.xmpp.services;

import com.plutonem.android.login.BuildConfig;

public abstract class AbstractQuickConversationsService {

    protected final XmppConnectionService service;

    public AbstractQuickConversationsService(XmppConnectionService service) {
        this.service = service;
    }

    public abstract void considerSync();

    public static boolean isQuicksy() {
        // change a way to manifest it
        return false;
    }

    public static boolean isConversations() {
        // change a way to manifest it
        return true;
    }

    public abstract void signalAccountStateChange();

    public abstract boolean isSynchronizing();

    public abstract void considerSyncBackground(boolean force);
}

package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Contact;

public interface OnContactStatusChanged {
    public void onContactStatusChanged(final Contact contact, final boolean online);
}

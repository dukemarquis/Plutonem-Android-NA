package com.plutonem.xmpp.entities;

import rocks.xmpp.addr.Jid;

public interface Conversational {

    int MODE_MULTI = 1;
    int MODE_SINGLE = 0;

    Account getAccount();

    Contact getContact();

    Jid getJid();

    int getMode();

    String getUuid();
}

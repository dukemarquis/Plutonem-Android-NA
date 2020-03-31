package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Account;

public interface OnMessageAcknowledged {
    boolean onMessageAcknowledged(Account account, String id);
}
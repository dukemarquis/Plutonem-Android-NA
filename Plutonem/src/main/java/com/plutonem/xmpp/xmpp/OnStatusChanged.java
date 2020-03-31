package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Account;

public interface OnStatusChanged {
    public void onStatusChanged(Account account);
}

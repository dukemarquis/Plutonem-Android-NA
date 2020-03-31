package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.xmpp.stanzas.IqPacket;

public interface OnIqPacketReceived extends PacketReceived {
    void onIqPacketReceived(Account account, IqPacket packet);
}

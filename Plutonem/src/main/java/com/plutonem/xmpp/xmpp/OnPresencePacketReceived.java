package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.xmpp.stanzas.PresencePacket;

public interface OnPresencePacketReceived extends PacketReceived {
    public void onPresencePacketReceived(Account account, PresencePacket packet);
}

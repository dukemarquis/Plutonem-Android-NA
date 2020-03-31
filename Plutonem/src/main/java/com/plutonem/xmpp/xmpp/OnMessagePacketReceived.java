package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.xmpp.stanzas.MessagePacket;

public interface OnMessagePacketReceived extends PacketReceived {
    public void onMessagePacketReceived(Account account, MessagePacket packet);
}

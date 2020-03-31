package com.plutonem.xmpp.xmpp.jingle;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.xmpp.PacketReceived;
import com.plutonem.xmpp.xmpp.jingle.stanzas.JinglePacket;

public interface OnJinglePacketReceived extends PacketReceived {
    void onJinglePacketReceived(Account account, JinglePacket packet);
}

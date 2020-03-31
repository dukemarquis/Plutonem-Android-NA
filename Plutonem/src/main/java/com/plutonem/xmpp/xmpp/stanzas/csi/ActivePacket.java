package com.plutonem.xmpp.xmpp.stanzas.csi;

import com.plutonem.xmpp.xmpp.stanzas.AbstractStanza;

public class ActivePacket extends AbstractStanza {
    public ActivePacket() {
        super("active");
        setAttribute("xmlns", "urn:xmpp:csi:0");
    }
}

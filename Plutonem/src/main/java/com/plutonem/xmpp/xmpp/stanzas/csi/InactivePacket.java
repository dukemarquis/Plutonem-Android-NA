package com.plutonem.xmpp.xmpp.stanzas.csi;

import com.plutonem.xmpp.xmpp.stanzas.AbstractStanza;

public class InactivePacket extends AbstractStanza {
    public InactivePacket() {
        super("inactive");
        setAttribute("xmlns", "urn:xmpp:csi:0");
    }
}

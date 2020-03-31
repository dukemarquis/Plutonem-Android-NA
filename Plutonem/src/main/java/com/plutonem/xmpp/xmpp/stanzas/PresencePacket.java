package com.plutonem.xmpp.xmpp.stanzas;

public class PresencePacket extends AbstractAcknowledgeableStanza {

    public PresencePacket() {
        super("presence");
    }
}

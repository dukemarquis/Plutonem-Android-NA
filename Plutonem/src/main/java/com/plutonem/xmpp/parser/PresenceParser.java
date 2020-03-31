package com.plutonem.xmpp.parser;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xmpp.OnPresencePacketReceived;
import com.plutonem.xmpp.xmpp.stanzas.PresencePacket;

public class PresenceParser extends AbstractParser implements OnPresencePacketReceived {

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {

        // we will skip this part by now for future modification
    }
}

package com.plutonem.xmpp.generator;

import android.text.TextUtils;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.entities.Presence;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.stanzas.PresencePacket;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private PresencePacket subscription(String type, Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().asBareJid());
        return packet;
    }

    public PresencePacket requestPresenceUpdatesFrom(Contact contact) {
        PresencePacket packet = subscription("subscribe", contact);
        String displayName = contact.getAccount().getDisplayName();
        if (!TextUtils.isEmpty(displayName)) {
            packet.addChild("nick", Namespace.NICK).setContent(displayName);
        }
        return packet;
    }

    public PresencePacket sendPresenceUpdatesTo(Contact contact) {
        return subscription("subscribed", contact);
    }

    public PresencePacket selfPresence(Account account, Presence.Status status) {
        return selfPresence(account, status, true);
    }

    public PresencePacket selfPresence(final Account account, final Presence.Status status, final boolean personal) {

        // skip Pgp Encryption part.

        final PresencePacket packet = new PresencePacket();
        if (personal) {
            final String message = account.getPresenceStatusMessage();
            if (status.toShowString() != null) {
                packet.addChild("show").setContent(status.toShowString());
            }
            if (!TextUtils.isEmpty(message)) {
                packet.addChild(new Element("status").setContent(message));
            }
        }
        final String capHash = getCapHash(account);
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://3.15.14.1");
            cap.setAttribute("ver", capHash);
        }
        return packet;
    }
}

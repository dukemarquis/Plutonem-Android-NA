package com.plutonem.xmpp.parser;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Avatar;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.entities.Presence;
import com.plutonem.xmpp.generator.PresenceGenerator;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.OnPresencePacketReceived;
import com.plutonem.xmpp.xmpp.stanzas.PresencePacket;

import rocks.xmpp.addr.Jid;

public class PresenceParser extends AbstractParser implements OnPresencePacketReceived {

    public PresenceParser(XmppConnectionService service) {
        super(service);
    }

    private void parseContactPresence(final PresencePacket packet, final Account account) {

        // skip Multi User Chat part.
        // skip Pgp Encryption Presence part.

        final PresenceGenerator mPresenceGenerator = mXmppConnectionService.getPresenceGenerator();
        final Jid from = packet.getFrom();
        if (from == null || from.equals(account.getJid())) {
            return;
        }
        final String type = packet.getAttribute("type");
        final Contact contact = account.getRoster().getContact(from);
        if (type == null) {
            final String resource = from.isBareJid() ? "" : from.getResource();
            Avatar avatar = Avatar.parsePresence(packet.findChild("x", "vcard-temp:x:update"));
            if (avatar != null && (!contact.isSelf() || account.getAvatar() == null)) {
                avatar.owner = from.asBareJid();
                if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                    if (avatar.owner.equals(account.getJid().asBareJid())) {
                        account.setAvatar(avatar.getFilename());
                        mXmppConnectionService.databaseBackend.updateAccount(account);
                        mXmppConnectionService.getAvatarService().clear(account);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateAccountUi();
                    } else if (contact.setAvatar(avatar)) {
                        mXmppConnectionService.syncRoster(account);
                        mXmppConnectionService.getAvatarService().clear(contact);
                        mXmppConnectionService.updateConversationUi();
                        mXmppConnectionService.updateRosterUi();
                    }
                } else if (mXmppConnectionService.isDataSaverDisabled()) {
                    mXmppConnectionService.fetchAvatar(account, avatar);
                }
            }

            int sizeBefore = contact.getPresences().size();

            final String show = packet.findChildContent("show");
            final Element caps = packet.findChild("c", "http://jabber.org/protocol/caps");
            final String message = packet.findChildContent("status");
            final Presence presence = Presence.parse(show, caps, message);
            contact.updatePresence(resource, presence);
            if (presence.hasCaps()) {
                mXmppConnectionService.fetchCaps(account, from, presence);
            }

            final Element idle = packet.findChild("idle", Namespace.IDLE);
            if (idle != null) {
                try {
                    final String since = idle.getAttribute("since");
                    contact.setLastseen(AbstractParser.parseTimestamp(since));
                    contact.flagInactive();
                } catch (Throwable throwable) {
                    if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                        contact.flagActive();
                    }
                }
            } else {
                if (contact.setLastseen(AbstractParser.parseTimestamp(packet))) {
                    contact.flagActive();
                }
            }

            boolean online = sizeBefore < contact.getPresences().size();
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, online);
        } else if (type.equals("unavailable")) {
            if (contact.setLastseen(AbstractParser.parseTimestamp(packet, 0L, true))) {
                contact.flagInactive();
            }
            if (from.isBareJid()) {
                contact.clearPresences();
            } else {
                contact.removePresence(from.getResource());
            }
            if (contact.getShownStatus() == Presence.Status.OFFLINE) {
                contact.flagInactive();
            }
            mXmppConnectionService.onContactStatusChanged.onContactStatusChanged(contact, false);
        } else if (type.equals("subscribe")) {
            if (contact.setPresenceName(packet.findChildContent("nick", Namespace.NICK))) {
                mXmppConnectionService.getAvatarService().clear(contact);
            }
            if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
                mXmppConnectionService.sendPresencePacket(account,
                        mPresenceGenerator.sendPresenceUpdatesTo(contact));
            } else {
                contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
                final Conversation conversation = mXmppConnectionService.findOrCreateConversation(
                        account, contact.getJid().asBareJid(), false, false);
                final String statusMessage = packet.findChildContent("status");
                if (statusMessage != null
                        && !statusMessage.isEmpty()
                        && conversation.countMessages() == 0) {
                    conversation.add(new Message(
                            conversation,
                            statusMessage,
                            Message.ENCRYPTION_NONE,
                            Message.STATUS_RECEIVED
                    ));
                }
            }
        }
        mXmppConnectionService.updateRosterUi();
    }

    @Override
    public void onPresencePacketReceived(Account account, PresencePacket packet) {

        // we will skip this part by now for future modification.
        // now we need the Presence part!!

        // skip Multi User Chat part.

        this.parseContactPresence(packet, account);
    }
}

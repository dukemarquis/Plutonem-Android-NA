package com.plutonem.xmpp.generator;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.chatstate.ChatState;
import com.plutonem.xmpp.xmpp.stanzas.MessagePacket;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import rocks.xmpp.addr.Jid;

public class MessageGenerator extends AbstractGenerator {
    public MessageGenerator(XmppConnectionService service) {
        super(service);
    }

    private MessagePacket preparePacket(Message message) {
        Conversation conversation = (Conversation) message.getConversation();
        Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        final boolean isWithSelf = conversation.getContact().isSelf();
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            packet.setTo(message.getCounterpart());
            packet.setType(MessagePacket.TYPE_CHAT);
            if (!isWithSelf) {
                packet.addChild("request", "urn:xmpp:receipts");
            }
        } else if (message.isPrivateMessage()) {
            // skip Multi Mode Chat part.
        } else {
            // skip Multi Mode Chat part.
        }
        if (conversation.isSingleOrPrivateAndNonAnonymous() && !message.isPrivateMessage()) {
            packet.addChild("markable", "urn:xmpp:chat-markers:0");
        }
        packet.setFrom(account.getJid());
        packet.setId(message.getUuid());
        packet.addChild("origin-id", Namespace.STANZA_IDS).setAttribute("id", message.getUuid());
        if (message.edited()) {
            packet.addChild("replace", "urn:xmpp:message-correct:0").setAttribute("id", message.getEditedIdWireFormat());
        }
        return packet;
    }

    public void addDelay(MessagePacket packet, long timestamp) {
        final SimpleDateFormat mDateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        mDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Element delay = packet.addChild("delay", "urn:xmpp:delay");
        Date date = new Date(timestamp);
        delay.setAttribute("stamp", mDateFormat.format(date));
    }

    public MessagePacket generateChat(Message message) {
        MessagePacket packet = preparePacket(message);
        String content;
        if (message.hasFileOnRemoteHost()) {
            // omit by now
            content = null;
        } else {
            content = message.getBody();
        }
        packet.setBody(content);
        return packet;
    }

    public MessagePacket generateChatState(Conversation conversation) {
        final Account account = conversation.getAccount();
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(conversation.getJid().asBareJid());
        packet.setFrom(account.getJid());
        packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
        packet.addChild("no-store", "urn:xmpp:hints");
//        packet.addChild("no-storage", "urn:xmpp:hints"); //wrong! don't copy this. Its *store*
        return packet;
    }

    public MessagePacket confirm(final Account account, final Jid to, final String id, final Jid counterpart, final boolean groupChat) {
        MessagePacket packet = new MessagePacket();
        packet.setType(groupChat ? MessagePacket.TYPE_GROUPCHAT : MessagePacket.TYPE_CHAT);
        packet.setTo(groupChat ? to.asBareJid() : to);
        packet.setFrom(account.getJid());
        Element displayed = packet.addChild("displayed", "urn:xmpp:chat-markers:0");
        displayed.setAttribute("id", id);
        if (groupChat && counterpart != null) {
            displayed.setAttribute("sender", counterpart.toString());
        }
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }

    public MessagePacket received(Account account, MessagePacket originalMessage, ArrayList<String> namespaces, int type) {
        MessagePacket receivedPacket = new MessagePacket();
        receivedPacket.setType(type);
        receivedPacket.setTo(originalMessage.getFrom());
        receivedPacket.setFrom(account.getJid());
        for (String namespace : namespaces) {
            receivedPacket.addChild("received", namespace).setAttribute("id", originalMessage.getId());
        }
        receivedPacket.addChild("store", "urn:xmpp:hints");
        return receivedPacket;
    }


    public MessagePacket received(Account account, Jid to, String id) {
        MessagePacket packet = new MessagePacket();
        packet.setFrom(account.getJid());
        packet.setTo(to);
        packet.addChild("received", "urn:xmpp:receipts").setAttribute("id", id);
        packet.addChild("store", "urn:xmpp:hints");
        return packet;
    }
}

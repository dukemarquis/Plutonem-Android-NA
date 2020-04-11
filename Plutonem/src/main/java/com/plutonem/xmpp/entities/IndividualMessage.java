package com.plutonem.xmpp.entities;

import com.plutonem.xmpp.ui.adapter.MessageAdapter;

import java.util.Set;

import rocks.xmpp.addr.Jid;

public class IndividualMessage extends Message {

    private IndividualMessage(Conversational conversation) {
        super(conversation);
    }

    private IndividualMessage(Conversational conversation, String uuid, String conversationUUid, Jid counterpart, Jid trueCounterpart, String body, long timeSent, int encryption, int status, int type, boolean carbon, String remoteMsgId, String relativeFilePath, String serverMsgId, String fingerprint, boolean read, String edited, boolean oob, String errorMessage, Set<ReadByMarker> readByMarkers, boolean markable, boolean deleted, String bodyLanguage) {
        super(conversation, uuid, conversationUUid, counterpart, trueCounterpart, body, timeSent, encryption, status, type, carbon, remoteMsgId, relativeFilePath, serverMsgId, fingerprint, read, edited, oob, errorMessage, readByMarkers, markable, deleted, bodyLanguage);
    }

    @Override
    public Message next() {
        return null;
    }

    @Override
    public Message prev() {
        return null;
    }

//    @Override
//    public boolean isValidInSession() {
//        return true;
//    }

    public static Message createDateSeparator(Message message) {
        final Message separator = new IndividualMessage(message.getConversation());
        separator.setType(Message.TYPE_STATUS);
        separator.body = MessageAdapter.DATE_SEPARATOR_BODY;
        separator.setTime(message.getTimeSent());
        return separator;
    }
}

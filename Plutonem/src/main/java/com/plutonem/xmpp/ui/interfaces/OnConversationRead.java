package com.plutonem.xmpp.ui.interfaces;

import com.plutonem.xmpp.entities.Conversation;

public interface OnConversationRead {
    void onConversationRead(Conversation conversation, String upToUuid);
}

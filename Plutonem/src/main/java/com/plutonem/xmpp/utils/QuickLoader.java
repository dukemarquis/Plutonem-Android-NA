package com.plutonem.xmpp.utils;

import com.plutonem.xmpp.entities.Conversation;

import java.util.List;

public class QuickLoader {

    private static String CONVERSATION_UUID = null;
    private static Object LOCK = new Object();

    public static Conversation get(List<Conversation> haystack) {
        synchronized (LOCK) {
            if (CONVERSATION_UUID == null) {
                return null;
            }
            for (Conversation conversation : haystack) {
                if (conversation.getUuid().equals(CONVERSATION_UUID)) {
                    return conversation;
                }
            }
        }
        return null;
    }

}

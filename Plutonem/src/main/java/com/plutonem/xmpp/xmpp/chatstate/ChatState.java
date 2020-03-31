package com.plutonem.xmpp.xmpp.chatstate;

import com.plutonem.xmpp.xml.Element;

public enum ChatState {

    ACTIVE, INACTIVE, GONE, COMPOSING, PAUSED;

    public static Element toElement(ChatState state) {
        final String NAMESPACE = "http://jabber.org/protocol/chatstates";
        final Element element = new Element(state.toString().toLowerCase());
        element.setAttribute("xmlns",NAMESPACE);
        return element;
    }
}

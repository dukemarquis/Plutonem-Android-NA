package com.plutonem.xmpp.xmpp.stanzas;

import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xmpp.InvalidJid;

public abstract class AbstractAcknowledgeableStanza extends AbstractStanza {

    protected AbstractAcknowledgeableStanza(String name) {
        super(name);
    }


    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        setAttribute("id", id);
    }

    public Element getError() {
        Element error = findChild("error");
        if (error != null) {
            for(Element element : error.getChildren()) {
                if (!element.getName().equals("text")) {
                    return element;
                }
            }
        }
        return null;
    }

    public boolean valid() {
        return InvalidJid.isValid(getFrom()) && InvalidJid.isValid(getTo());
    }
}

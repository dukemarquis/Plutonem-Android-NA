package com.plutonem.xmpp.entities;

import rocks.xmpp.addr.Jid;

public class ReceiptRequest {

    private final Jid jid;
    private final String id;

    public ReceiptRequest(Jid jid, String id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (jid == null) {
            throw new IllegalArgumentException("jid must not be null");
        }
        this.jid = jid.asBareJid();
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReceiptRequest that = (ReceiptRequest) o;

        if (!jid.equals(that.jid)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        int result = jid.hashCode();
        result = 31 * result + id.hashCode();
        return result;
    }

    public String getId() {
        return id;
    }

    public Jid getJid() {
        return jid;
    }
}

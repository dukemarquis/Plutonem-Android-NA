package com.plutonem.xmpp.xmpp;

import androidx.annotation.NonNull;

import com.plutonem.xmpp.xmpp.stanzas.AbstractStanza;

import rocks.xmpp.addr.Jid;

public class InvalidJid implements Jid {

    private final String value;

    private InvalidJid(String jid) {
        this.value = jid;
    }

    public  static Jid of(String jid, boolean fallback) {
        final int pos = jid.indexOf('/');
        if (fallback && pos >= 0 && jid.length() >= pos + 1) {
            if (jid.substring(pos+1).trim().isEmpty()) {
                return Jid.ofEscaped(jid.substring(0,pos));
            }
        }
        return new InvalidJid(jid);
    }

    @Override
    @NonNull
    public String toString() {
        return value;
    }

    @Override
    public boolean isFullJid() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public boolean isBareJid() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public boolean isDomainJid() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Jid asBareJid() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Jid withLocal(CharSequence charSequence) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Jid withResource(CharSequence charSequence) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public Jid atSubdomain(CharSequence charSequence) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public String getLocal() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public String getEscapedLocal() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public String getDomain() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public String getResource() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public String toEscapedString() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return value.subSequence(start, end);
    }

    @Override
    public int compareTo(@NonNull Jid o) {
        throw new AssertionError("Not implemented");
    }

    public static Jid getNullForInvalid(Jid jid) {
        if (jid != null && jid instanceof InvalidJid) {
            return null;
        } else {
            return jid;
        }
    }

    public static boolean isValid(Jid jid) {
        return !(jid != null && jid instanceof InvalidJid);
    }

    public static boolean hasValidFrom(AbstractStanza stanza) {
        final String from = stanza.getAttribute("from");
        if (from == null) {
            return false;
        }
        try {
            Jid.ofEscaped(from);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

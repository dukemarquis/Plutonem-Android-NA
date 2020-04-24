package com.plutonem.xmpp.entities;

import com.plutonem.xmpp.xml.Element;

import rocks.xmpp.addr.Jid;

public class Avatar {

    public enum Origin { PEP, VCARD };

    public String sha1sum;
    public Jid owner;
    public Origin origin = Origin.PEP; //default to maintain compat

    public String getFilename() {
        return sha1sum;
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof Avatar) {
            Avatar other = (Avatar) object;
            return other.getFilename().equals(this.getFilename());
        } else {
            return false;
        }
    }

    public static Avatar parsePresence(Element x) {
        String hash = x == null ? null : x.findChildContent("photo");
        if (hash == null) {
            return null;
        }
        if (!isValidSHA1(hash)) {
            return null;
        }
        Avatar avatar = new Avatar();
        avatar.sha1sum = hash;
        avatar.origin = Origin.VCARD;
        return avatar;
    }

    private static boolean isValidSHA1(String s) {
        return s != null && s.matches("[a-fA-F0-9]{40}");
    }
}

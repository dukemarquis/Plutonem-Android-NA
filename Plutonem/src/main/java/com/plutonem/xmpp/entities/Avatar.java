package com.plutonem.xmpp.entities;

public class Avatar {

    public enum Origin { PEP, VCARD };

    public String sha1sum;
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
}

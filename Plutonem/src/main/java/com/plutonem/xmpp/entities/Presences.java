package com.plutonem.xmpp.entities;

import java.util.Hashtable;

public class Presences {

    private final Hashtable<String, Presence> presences = new Hashtable<>();

    public Presence.Status getShownStatus() {
        Presence.Status status = Presence.Status.OFFLINE;
        synchronized (this.presences) {
            for(Presence p : presences.values()) {
                if (p.getStatus() == Presence.Status.DND) {
                    return p.getStatus();
                } else if (p.getStatus().compareTo(status) < 0){
                    status = p.getStatus();
                }
            }
        }
        return status;
    }
}

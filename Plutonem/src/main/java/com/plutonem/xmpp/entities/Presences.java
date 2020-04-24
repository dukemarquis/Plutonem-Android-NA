package com.plutonem.xmpp.entities;

import java.util.Hashtable;

public class Presences {

    private final Hashtable<String, Presence> presences = new Hashtable<>();

    public void updatePresence(String resource, Presence presence) {
        synchronized (this.presences) {
            this.presences.put(resource, presence);
        }
    }

    public void removePresence(String resource) {
        synchronized (this.presences) {
            this.presences.remove(resource);
        }
    }

    public void clearPresences() {
        synchronized (this.presences) {
            this.presences.clear();
        }
    }

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

    public int size() {
        synchronized (this.presences) {
            return presences.size();
        }
    }
}

package com.plutonem.xmpp.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import rocks.xmpp.addr.Jid;

public class Roster {
    private final Account account;
    private final HashMap<Jid, Contact> contacts = new HashMap<>();
    private String version = null;

    public Roster(Account account) {
        this.account = account;
    }

    public Contact getContact(final Jid jid) {
        synchronized (this.contacts) {
            if (!contacts.containsKey(jid.asBareJid())) {
                Contact contact = new Contact(jid.asBareJid());
                contact.setAccount(account);
                contacts.put(contact.getJid().asBareJid(), contact);
                return contact;
            }
            return contacts.get(jid.asBareJid());
        }
    }

    public void clearPresences() {
        for (Contact contact : getContacts()) {
            contact.clearPresences();
        }
    }

    public void markAllAsNotInRoster() {
        for (Contact contact : getContacts()) {
            contact.resetOption(Contact.Options.IN_ROSTER);
        }
    }

    public List<Contact> getContacts() {
        synchronized (this.contacts) {
            return new ArrayList<>(this.contacts.values());
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return this.version;
    }

    public Account getAccount() {
        return this.account;
    }
}

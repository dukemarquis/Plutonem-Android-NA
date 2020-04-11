package com.plutonem.xmpp.ui.util;

public class PendingItem<T> {

    private T item = null;

    public synchronized void push(T item) {
        this.item = item;
    }

    public synchronized T pop() {
        final T item = this.item;
        this.item = null;
        return item;
    }

    public synchronized T peek() {
        return item;
    }

    public synchronized boolean clear() {
        boolean notNull = this.item != null;
        this.item = null;
        return notNull;
    }
}

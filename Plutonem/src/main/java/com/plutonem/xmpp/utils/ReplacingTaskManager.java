package com.plutonem.xmpp.utils;

import com.plutonem.xmpp.entities.Account;

import java.util.HashMap;

public class ReplacingTaskManager {

    private final HashMap<Account, ReplacingSerialSingleThreadExecutor> executors = new HashMap<>();

    public void execute(final Account account, Runnable runnable) {
        ReplacingSerialSingleThreadExecutor executor;
        synchronized (this.executors) {
            executor = this.executors.get(account);
            if (executor == null) {
                executor = new ReplacingSerialSingleThreadExecutor(ReplacingTaskManager.class.getSimpleName());
                this.executors.put(account, executor);
            }
            executor.execute(runnable);
        }
    }

    public void clear(Account account) {
        synchronized (this.executors) {
            this.executors.remove(account);
        }
    }
}

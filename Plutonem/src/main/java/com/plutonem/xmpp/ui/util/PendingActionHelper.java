package com.plutonem.xmpp.ui.util;

/**
 * Created by ch on 2020/4/17.
 */

public class PendingActionHelper {

    private PendingAction pendingAction;

    public void push(PendingAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public void execute() {
        if (pendingAction != null) {
            pendingAction.execute();
            pendingAction = null;
        }
    }

    public void undo() {
        pendingAction = null;
    }

    public interface PendingAction {
        void execute();
    }
}

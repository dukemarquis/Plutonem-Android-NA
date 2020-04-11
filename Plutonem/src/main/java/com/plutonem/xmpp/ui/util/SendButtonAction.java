package com.plutonem.xmpp.ui.util;

public enum SendButtonAction {
    TEXT;

    public static SendButtonAction valueOfOrDefault(final String setting) {
        if (setting == null) {
            return TEXT;
        }
        try {
            return valueOf(setting);
        } catch (IllegalArgumentException e) {
            return TEXT;
        }
    }
}

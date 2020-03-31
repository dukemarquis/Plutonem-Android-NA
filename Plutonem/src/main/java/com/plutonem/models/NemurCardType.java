package com.plutonem.models;

/**
 * Used by the nemur stream view to determine which type of "card" to use
 */
public enum NemurCardType {
    DEFAULT;

    public static NemurCardType fromNemurOrder(NemurOrder order) {
        if (order == null) {
            return DEFAULT;
        }

        return DEFAULT;
    }

    public static String toString(NemurCardType cardType) {
        if (cardType == null) {
            return "DEFAULT";
        }
        switch (cardType) {
            default:
                return "DEFAULT";
        }
    }

    public static NemurCardType fromString(String s) {
        return DEFAULT;
    }
}

package com.plutonem.models;

public enum NemurTagType {
    DEFAULT,
    SEARCH;

    private static final int INT_DEFAULT = 0;
    private static final int INT_SEARCH = 1;

    public static NemurTagType fromInt(int value) {
        if (value == INT_SEARCH) {
            return SEARCH;
        }
        return DEFAULT;
    }

    public int toInt() {
        switch (this) {
            case SEARCH:
                return INT_SEARCH;
            case DEFAULT:
            default:
                return INT_DEFAULT;
        }
    }
}

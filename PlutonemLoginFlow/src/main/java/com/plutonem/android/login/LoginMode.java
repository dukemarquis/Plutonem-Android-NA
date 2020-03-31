package com.plutonem.android.login;

import android.content.Intent;

public enum LoginMode {
    FULL,
    BUY_INTENT;

    private static final String ARG_LOGIN_MODE = "ARG_LOGIN_MODE";

    public static LoginMode fromIntent(Intent intent) {
        if (intent.hasExtra(ARG_LOGIN_MODE)) {
            return LoginMode.valueOf(intent.getStringExtra(ARG_LOGIN_MODE));
        } else {
            return FULL;
        }
    }

    public void putInto(Intent intent) {
        intent.putExtra(ARG_LOGIN_MODE, this.name());
    }
}

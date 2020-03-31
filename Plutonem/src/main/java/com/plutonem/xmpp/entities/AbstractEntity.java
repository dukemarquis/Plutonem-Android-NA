package com.plutonem.xmpp.entities;

import android.content.ContentValues;

public abstract class AbstractEntity {
    public static final String UUID = "uuid";

    protected String uuid;

    public String getUuid() {
        return this.uuid;
    }

    public abstract ContentValues getContentValues();
}

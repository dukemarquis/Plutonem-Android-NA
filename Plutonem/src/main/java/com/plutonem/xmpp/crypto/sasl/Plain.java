package com.plutonem.xmpp.crypto.sasl;

import android.util.Base64;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.xml.TagWriter;

import java.nio.charset.Charset;

public class Plain extends SaslMechanism {
    public Plain(final TagWriter tagWriter, final Account account) {
        super(tagWriter, account, null);
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getMechanism() {
        return "PLAIN";
    }

    @Override
    public String getClientFirstMessage() {
        return getMessage(account.getUsername(), account.getPassword());
    }

    public static String getMessage(String username, String password) {
        final String message = '\u0000' + username + '\u0000' + password;
        return Base64.encodeToString(message.getBytes(Charset.defaultCharset()), Base64.NO_WRAP);
    }
}
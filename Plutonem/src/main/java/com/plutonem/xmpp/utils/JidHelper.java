package com.plutonem.xmpp.utils;

import com.plutonem.xmpp.xmpp.InvalidJid;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import rocks.xmpp.addr.Jid;

public class JidHelper {

    private static List<String> LOCAL_PART_BLACKLIST = Arrays.asList("xmpp", "jabber", "me");

    public static String localPartOrFallback(Jid jid) {
        if (LOCAL_PART_BLACKLIST.contains(jid.getLocal().toLowerCase(Locale.ENGLISH))) {
            final String domain = jid.getDomain();
            final int index = domain.indexOf('.');
            return index > 1 ? domain.substring(0, index) : domain;
        } else {
            return jid.getLocal();
        }
    }

    public static Jid parseOrFallbackToInvalid(String jid) {
        try {
            return Jid.of(jid);
        } catch (IllegalArgumentException e) {
            return InvalidJid.of(jid, true);
        }
    }

}

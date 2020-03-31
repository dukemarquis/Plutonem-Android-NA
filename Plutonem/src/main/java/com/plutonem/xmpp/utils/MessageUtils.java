package com.plutonem.xmpp.utils;

import com.plutonem.xmpp.entities.Message;

import java.time.chrono.MinguoEra;
import java.util.regex.Pattern;

public class MessageUtils {

    private static final Pattern LTR_RTL = Pattern.compile("(\\u200E[^\\u200F]*\\u200F){3,}");

    private static final String EMPTY_STRING = "";

    public static String filterLtrRtl(String body) {
        return LTR_RTL.matcher(body).replaceFirst(EMPTY_STRING);
    }

    public static boolean unInitiatedButKnownSize(Message message) {
        return message.getType() == Message.TYPE_TEXT && message.getTransferable() == null && message.isOOb() && message.getFileParams().size > 0 && message.getFileParams().url != null;
    }
}

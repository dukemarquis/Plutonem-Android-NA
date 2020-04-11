package com.plutonem.xmpp.ui.util;

import com.plutonem.xmpp.entities.IndividualMessage;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.utils.UIHelper;

import java.util.List;

public class DateSeparator {

    public static void addAll(List<Message> messages) {
        for (int i = 0; i < messages.size(); ++i) {
            final Message current = messages.get(i);
            if (i == 0 || !UIHelper.sameDay(messages.get(i - 1).getTimeSent(), current.getTimeSent())) {
                messages.add(i, IndividualMessage.createDateSeparator(current));
                i++;
            }
        }
    }
}

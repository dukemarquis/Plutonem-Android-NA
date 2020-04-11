package com.plutonem.xmpp.utils;

import com.plutonem.Config;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.services.XmppConnectionService;

import java.util.ArrayList;
import java.util.List;

public class AccountUtils {

    public static List<String> getEnabledAccounts(final XmppConnectionService service) {
        ArrayList<String> accounts = new ArrayList<>();
        for (Account account : service.getAccounts()) {
            if (account.getStatus() != Account.State.DISABLED) {
                if (Config.DOMAIN_LOCK != null) {
                    accounts.add(account.getJid().getLocal());
                } else {
                    accounts.add(account.getJid().asBareJid().toString());
                }
            }
        }
        return accounts;
    }
}

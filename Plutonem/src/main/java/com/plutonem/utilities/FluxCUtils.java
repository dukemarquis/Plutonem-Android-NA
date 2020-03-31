package com.plutonem.utilities;

import com.plutonem.android.fluxc.store.AccountStore;

public class FluxCUtils {
    /**
     * This method doesn't do much, but insure we're doing the same check in all parts of the app.
     *
     * @return true if the user is signed in a Plutonem account.
     */
    public static boolean isSignedInPN(AccountStore accountStore) {
        return accountStore.hasAccessToken();
    }
}

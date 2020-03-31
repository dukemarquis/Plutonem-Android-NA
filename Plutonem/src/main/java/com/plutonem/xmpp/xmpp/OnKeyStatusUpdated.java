package com.plutonem.xmpp.xmpp;

import com.plutonem.xmpp.crypto.axolotl.AxolotlService;

public interface OnKeyStatusUpdated {
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report);
}

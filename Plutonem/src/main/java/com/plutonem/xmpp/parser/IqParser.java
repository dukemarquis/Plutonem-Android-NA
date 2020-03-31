package com.plutonem.xmpp.parser;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.plutonem.Config;
import com.plutonem.xmpp.crypto.axolotl.AxolotlService;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.InvalidJid;
import com.plutonem.xmpp.xmpp.OnIqPacketReceived;
import com.plutonem.xmpp.xmpp.stanzas.IqPacket;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import rocks.xmpp.addr.Jid;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

    public IqParser(final XmppConnectionService service) {
        super(service);
    }

    private void rosterItems(final Account account, final Element query) {

        // we are not sure if we would use Rostor in Plutonem, let's just keep it simple and to see if we implement later
    }

    public String avatarData(final IqPacket packet) {
        final Element pubsub = packet.findChild("pubsub", Namespace.PUBSUB);
        if (pubsub == null) {
            return null;
        }
        final Element items = pubsub.findChild("items");
        if (items == null) {
            return null;
        }
        return super.avatarData(items);
    }

    public Element getItem(final IqPacket packet) {
        final Element pubsub = packet.findChild("pubsub", Namespace.PUBSUB);
        if (pubsub == null) {
            return null;
        }
        final Element items = pubsub.findChild("items");
        if (items == null) {
            return null;
        }
        return items.findChild("item");
    }

    @NonNull
    public Set<Integer> deviceIds(final Element item) {
        Set<Integer> deviceIds = new HashSet<>();
        if (item != null) {
            final Element list = item.findChild("list");
            if (list != null) {
                for (Element device : list.getChildren()) {
                    if (!device.getName().equals("device")) {
                        continue;
                    }
                    try {
                        Integer id = Integer.valueOf(device.getAttribute("id"));
                        deviceIds.add(id);
                    } catch (NumberFormatException e) {
                        Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Encountered invalid <device> node in PEP (" + e.getMessage() + "):" + device.toString() + ", skipping...");
                        continue;
                    }
                }
            }
        }
        return deviceIds;
    }

    public Integer signedPreKeyId(final Element bundle) {
        final Element signedPreKeyPublic = bundle.findChild("signedPreKeyPublic");
        if (signedPreKeyPublic == null) {
            return null;
        }
        try {
            return Integer.valueOf(signedPreKeyPublic.getAttribute("signedPreKeyId"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public ECPublicKey signedPreKeyPublic(final Element bundle) {
        ECPublicKey publicKey = null;
        final Element signedPreKeyPublic = bundle.findChild("signedPreKeyPublic");
        if (signedPreKeyPublic == null) {
            return null;
        }
        try {
            publicKey = Curve.decodePoint(Base64.decode(signedPreKeyPublic.getContent(), Base64.DEFAULT), 0);
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Invalid signedPreKeyPublic in PEP: " + e.getMessage());
        }
        return publicKey;
    }

    public byte[] signedPreKeySignature(final Element bundle) {
        final Element signedPreKeySignature = bundle.findChild("signedPreKeySignature");
        if (signedPreKeySignature == null) {
            return null;
        }
        try {
            return Base64.decode(signedPreKeySignature.getContent(), Base64.DEFAULT);
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : Invalid base64 in signedPreKeySignature");
            return null;
        }
    }

    public IdentityKey identityKey(final Element bundle) {
        IdentityKey identityKey = null;
        final Element identityKeyElement = bundle.findChild("identityKey");
        if (identityKeyElement == null) {
            return null;
        }
        try {
            identityKey = new IdentityKey(Base64.decode(identityKeyElement.getContent(), Base64.DEFAULT), 0);
        } catch (Throwable e) {
            Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Invalid identityKey in PEP: " + e.getMessage());
        }
        return identityKey;
    }

    public Map<Integer, ECPublicKey> preKeyPublics(final IqPacket packet) {
        Map<Integer, ECPublicKey> preKeyRecords = new HashMap<>();
        Element item = getItem(packet);
        if (item == null) {
            Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Couldn't find <item> in bundle IQ packet: " + packet);
            return null;
        }
        final Element bundleElement = item.findChild("bundle");
        if (bundleElement == null) {
            return null;
        }
        final Element prekeysElement = bundleElement.findChild("prekeys");
        if (prekeysElement == null) {
            Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Couldn't find <prekeys> in bundle IQ packet: " + packet);
            return null;
        }
        for (Element preKeyPublicElement : prekeysElement.getChildren()) {
            if (!preKeyPublicElement.getName().equals("preKeyPublic")) {
                Log.d(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Encountered unexpected tag in prekeys list: " + preKeyPublicElement);
                continue;
            }
            Integer preKeyId = null;
            try {
                preKeyId = Integer.valueOf(preKeyPublicElement.getAttribute("preKeyId"));
                final ECPublicKey preKeyPublic = Curve.decodePoint(Base64.decode(preKeyPublicElement.getContent(), Base64.DEFAULT), 0);
                preKeyRecords.put(preKeyId, preKeyPublic);
            } catch (NumberFormatException e) {
                Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "could not parse preKeyId from preKey " + preKeyPublicElement.toString());
            } catch (Throwable e) {
                Log.e(Config.LOGTAG, AxolotlService.LOGPREFIX + " : " + "Invalid preKeyPublic (ID=" + preKeyId + ") in PEP: " + e.getMessage() + ", skipping...");
            }
        }
        return preKeyRecords;
    }

    public Pair<X509Certificate[], byte[]> verification(final IqPacket packet) {
        Element item = getItem(packet);
        Element verification = item != null ? item.findChild("verification", AxolotlService.PEP_PREFIX) : null;
        Element chain = verification != null ? verification.findChild("chain") : null;
        Element signature = verification != null ? verification.findChild("signature") : null;
        if (chain != null && signature != null) {
            List<Element> certElements = chain.getChildren();
            X509Certificate[] certificates = new X509Certificate[certElements.size()];
            try {
                CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
                int i = 0;
                for (Element cert : certElements) {
                    certificates[i] = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(Base64.decode(cert.getContent(), Base64.DEFAULT)));
                    ++i;
                }
                return new Pair<>(certificates, Base64.decode(signature.getContent(), Base64.DEFAULT));
            } catch (CertificateException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public PreKeyBundle bundle(final IqPacket bundle) {
        Element bundleItem = getItem(bundle);
        if (bundleItem == null) {
            return null;
        }
        final Element bundleElement = bundleItem.findChild("bundle");
        if (bundleElement == null) {
            return null;
        }
        ECPublicKey signedPreKeyPublic = signedPreKeyPublic(bundleElement);
        Integer signedPreKeyId = signedPreKeyId(bundleElement);
        byte[] signedPreKeySignature = signedPreKeySignature(bundleElement);
        IdentityKey identityKey = identityKey(bundleElement);
        if (signedPreKeyId == null || signedPreKeyPublic == null || identityKey == null) {
            return null;
        }

        return new PreKeyBundle(0, 0, 0, null,
                signedPreKeyId, signedPreKeyPublic, signedPreKeySignature, identityKey);
    }

    public List<PreKeyBundle> preKeys(final IqPacket preKeys) {
        List<PreKeyBundle> bundles = new ArrayList<>();
        Map<Integer, ECPublicKey> preKeyPublics = preKeyPublics(preKeys);
        if (preKeyPublics != null) {
            for (Integer preKeyId : preKeyPublics.keySet()) {
                ECPublicKey preKeyPublic = preKeyPublics.get(preKeyId);
                bundles.add(new PreKeyBundle(0, 0, preKeyId, preKeyPublic,
                        0, null, null, null));
            }
        }

        return bundles;
    }

    @Override
    public void onIqPacketReceived(final Account account, final IqPacket packet) {
        final boolean isGet = packet.getType() == IqPacket.TYPE.GET;
        if (packet.getType() == IqPacket.TYPE.ERROR || packet.getType() == IqPacket.TYPE.TIMEOUT) {
            return;
        }
        if (packet.hasChild("query", Namespace.ROSTER) && packet.fromServer(account)) {
            final Element query = packet.findChild("query");
            // If this is in response to a query for the whole roster:
            if (packet.getType() == IqPacket.TYPE.RESULT) {
                account.getRoster().markAllAsNotInRoster();
            }
            this.rosterItems(account, query);
        } else if ((packet.hasChild("block", Namespace.BLOCKING) || packet.hasChild("blocklist", Namespace.BLOCKING)) &&
                packet.fromServer(account)) {

            // we don't know if we need block function here so we keep this simple for now
        } else if (packet.hasChild("unblock", Namespace.BLOCKING) &&
                packet.fromServer(account) && packet.getType() == IqPacket.TYPE.SET) {

            // we don't know if we need unblock function here so we keep this simple for now
        } else if (packet.hasChild("open", "http://jabber.org/protocol/ibb")
                || packet.hasChild("data", "http://jabber.org/protocol/ibb")
                || packet.hasChild("close", "http://jabber.org/protocol/ibb")) {

            // we don't know if we need ibb of Jingle function here so keep this simple for now
        } else if (packet.hasChild("query", "http://jabber.org/protocol/disco#info")) {
            final IqPacket response = mXmppConnectionService.getIqGenerator().discoResponse(account, packet);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("query", "jabber:iq:version") && isGet) {
            final IqPacket response = mXmppConnectionService.getIqGenerator().versionResponse(packet);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("ping", "urn:xmpp:ping") && isGet) {
            final IqPacket response = packet.generateResponse(IqPacket.TYPE.RESULT);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("time", "urn:xmpp:time") && isGet) {
            final IqPacket response;
            if (mXmppConnectionService.useTorToConnect() || account.isOnion()) {
                response = packet.generateResponse(IqPacket.TYPE.ERROR);
                final Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("not-allowed", "urn:ietf:params:xml:ns:xmpp-stanzas");
            } else {
                response = mXmppConnectionService.getIqGenerator().entityTimeResponse(packet);
            }
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else if (packet.hasChild("pubsub", Namespace.PUBSUB) && packet.getType() == IqPacket.TYPE.SET) {

            // we don't know if we need pubsub function here so keep it simple for now
        } else {
            if (packet.getType() == IqPacket.TYPE.GET || packet.getType() == IqPacket.TYPE.SET) {
                final IqPacket response = packet.generateResponse(IqPacket.TYPE.ERROR);
                final Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented", "urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }


    public static List<Jid> items(IqPacket packet) {
        ArrayList<Jid> items = new ArrayList<>();
        final Element query = packet.findChild("query", Namespace.DISCO_ITEMS);
        if (query == null) {
            return items;
        }
        for(Element child : query.getChildren()) {
            if ("item".equals(child.getName())) {
                Jid jid = child.getAttributeAsJid("jid");
                if (jid != null) {
                    items.add(jid);
                }
            }
        }
        return items;
    }
}

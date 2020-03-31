package com.plutonem.xmpp.crypto.axolotl;

import android.os.Bundle;
import android.security.KeyChain;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.plutonem.Config;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.parser.IqParser;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.utils.CryptoHelper;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xmpp.OnAdvancedStreamFeaturesLoaded;
import com.plutonem.xmpp.xmpp.OnIqPacketReceived;
import com.plutonem.xmpp.xmpp.pep.PublishOptions;
import com.plutonem.xmpp.xmpp.stanzas.IqPacket;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyException;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SessionBuilder;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.KeyHelper;

import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import rocks.xmpp.addr.Jid;

public class AxolotlService implements OnAdvancedStreamFeaturesLoaded {

    public static final String PEP_PREFIX = "eu.siacs.conversations.axolotl";
    public static final String PEP_DEVICE_LIST = PEP_PREFIX + ".devicelist";
    public static final String PEP_DEVICE_LIST_NOTIFY = PEP_DEVICE_LIST + "+notify";
    public static final String PEP_BUNDLES = PEP_PREFIX + ".bundles";
    public static final String PEP_VERIFICATION = PEP_PREFIX + ".verification";
    public static final String PEP_OMEMO_WHITELISTED = PEP_PREFIX + ".whitelisted";

    public static final String LOGPREFIX = "AxolotlService";

    private static final int NUM_KEYS_TO_PUBLISH = 100;
    private static final int publishTriesThreshold = 3;

    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private final SQLiteAxolotlStore axolotlStore;
    private final SessionMap sessions;
    private final Map<Jid, Set<Integer>> deviceIds;
    private final FetchStatusMap fetchStatusMap;
    private final Map<Jid, Boolean> fetchDeviceListStatus = new HashMap<>();
    private final Set<SignalProtocolAddress> healingAttempts = new HashSet<>();
    private final HashSet<Integer> cleanedOwnDeviceIds = new HashSet<>();
    private final Set<Integer> PREVIOUSLY_REMOVED_FROM_ANNOUNCEMENT = new HashSet<>();
    private int numPublishTriesOnEmptyPep = 0;
    private boolean pepBroken = false;
    private int lastDeviceListNotificationHash = 0;
    private Set<XmppAxolotlSession> postponedSessions = new HashSet<>(); //sessions stored here will receive after mam catchup treatment
    private Set<SignalProtocolAddress> postponedHealing = new HashSet<>(); //addresses stored here will need a healing notification after mam catchup
    private AtomicBoolean changeAccessMode = new AtomicBoolean(false);

    public AxolotlService(Account account, XmppConnectionService connectionService) {
        if (account == null || connectionService == null) {
            throw new IllegalArgumentException("account and service cannot be null");
        }
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.mXmppConnectionService = connectionService;
        this.account = account;
        this.axolotlStore = new SQLiteAxolotlStore(this.account, this.mXmppConnectionService);
        this.deviceIds = new HashMap<>();
//        this.messageCache = new HashMap<>();
        this.sessions = new SessionMap(mXmppConnectionService, axolotlStore, account);
        this.fetchStatusMap = new FetchStatusMap();
//        this.executor = new SerialSingleThreadExecutor("Axolotl");
    }

    public static String getLogprefix(Account account) {
        return LOGPREFIX + " (" + account.getJid().asBareJid().toString() + "): ";
    }

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {

        // we will skip this part by now for future modification
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": skipping OMEMO initialization");
    }

    public boolean hasVerifiedKeys(String name) {
        for (XmppAxolotlSession session : this.sessions.getAll(name).values()) {
            if (session.getTrust().isVerified()) {
                return true;
            }
        }
        return false;
    }

    private SignalProtocolAddress getAddressForJid(Jid jid) {
        return new SignalProtocolAddress(jid.toString(), 0);
    }

    public Collection<XmppAxolotlSession> findOwnSessions() {
        SignalProtocolAddress ownAddress = getAddressForJid(account.getJid().asBareJid());
        ArrayList<XmppAxolotlSession> s = new ArrayList<>(this.sessions.getAll(ownAddress.getName()).values());
        Collections.sort(s);
        return s;
    }

    public void clearErrorsInFetchStatusMap(Jid jid) {
        fetchStatusMap.clearErrorFor(jid);
        fetchDeviceListStatus.remove(jid);
    }

    public void destroy() {
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": destroying old axolotl service. no longer in use");
        mXmppConnectionService.databaseBackend.wipeAxolotlDb(account);
    }

    public int getOwnDeviceId() {
        return axolotlStore.getLocalRegistrationId();
    }

    public SignalProtocolAddress getOwnAxolotlAddress() {
        return new SignalProtocolAddress(account.getJid().asBareJid().toString(), getOwnDeviceId());
    }

    public Set<Integer> getOwnDeviceIds() {
        return this.deviceIds.get(account.getJid().asBareJid());
    }

    public void registerDevices(final Jid jid, @NonNull final Set<Integer> deviceIds) {
        final int hash = deviceIds.hashCode();
        final boolean me = jid.asBareJid().equals(account.getJid().asBareJid());
        if (me) {
            if (hash != 0 && hash == this.lastDeviceListNotificationHash) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ignoring duplicate own device id list");
                return;
            }
            this.lastDeviceListNotificationHash = hash;
        }
        boolean needsPublishing = me && !deviceIds.contains(getOwnDeviceId());
        if (me) {
            deviceIds.remove(getOwnDeviceId());
        }
        Set<Integer> expiredDevices = new HashSet<>(axolotlStore.getSubDeviceSessions(jid.asBareJid().toString()));
        expiredDevices.removeAll(deviceIds);
        for (Integer deviceId : expiredDevices) {
            SignalProtocolAddress address = new SignalProtocolAddress(jid.asBareJid().toString(), deviceId);
            XmppAxolotlSession session = sessions.get(address);
            if (session != null && session.getFingerprint() != null) {
                if (session.getTrust().isActive()) {
                    session.setTrust(session.getTrust().toInactive());
                }
            }
        }
        Set<Integer> newDevices = new HashSet<>(deviceIds);
        for (Integer deviceId : newDevices) {
            SignalProtocolAddress address = new SignalProtocolAddress(jid.asBareJid().toString(), deviceId);
            XmppAxolotlSession session = sessions.get(address);
            if (session != null && session.getFingerprint() != null) {
                if (!session.getTrust().isActive()) {
                    Log.d(Config.LOGTAG, "reactivating device with fingerprint " + session.getFingerprint());
                    session.setTrust(session.getTrust().toActive());
                }
            }
        }
        if (me) {
            if (Config.OMEMO_AUTO_EXPIRY != 0) {
                needsPublishing |= deviceIds.removeAll(getExpiredDevices());
            }
            needsPublishing |= this.changeAccessMode.get();
            for (Integer deviceId : deviceIds) {
                SignalProtocolAddress ownDeviceAddress = new SignalProtocolAddress(jid.asBareJid().toString(), deviceId);
                if (sessions.get(ownDeviceAddress) == null) {
                    FetchStatus status = fetchStatusMap.get(ownDeviceAddress);
                    if (status == null || status == FetchStatus.TIMEOUT) {
                        fetchStatusMap.put(ownDeviceAddress, FetchStatus.PENDING);
                        this.buildSessionFromPEP(ownDeviceAddress);
                    }
                }
            }
            if (needsPublishing) {
                publishOwnDeviceId(deviceIds);
            }
        }
        final Set<Integer> oldSet = this.deviceIds.get(jid);
        final boolean changed = oldSet == null || oldSet.hashCode() != hash;
        this.deviceIds.put(jid, deviceIds);
        if (changed) {
            mXmppConnectionService.updateConversationUi(); //update the lock icon
            mXmppConnectionService.keyStatusUpdated(null);
            if (me) {
                mXmppConnectionService.updateAccountUi();
            }
        } else {
            Log.d(Config.LOGTAG, "skipped device list update because it hasn't changed");
        }
    }

    public void wipeOtherPepDevices() {
        if (pepBroken) {
            Log.d(Config.LOGTAG, getLogprefix(account) + "wipeOtherPepDevices called, but PEP is broken. Ignoring... ");
            return;
        }
        Set<Integer> deviceIds = new HashSet<>();
        deviceIds.add(getOwnDeviceId());
        publishDeviceIdsAndRefineAccessModel(deviceIds);
    }

    private void publishOwnDeviceIdIfNeeded() {
        if (pepBroken) {
            Log.d(Config.LOGTAG, getLogprefix(account) + "publishOwnDeviceIdIfNeeded called, but PEP is broken. Ignoring... ");
            return;
        }
        IqPacket packet = mXmppConnectionService.getIqGenerator().retrieveDeviceIds(account.getJid().asBareJid());
        mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
                    Log.d(Config.LOGTAG, getLogprefix(account) + "Timeout received while retrieving own Device Ids.");
                } else {
                    //TODO consider calling registerDevices only after item-not-found to account for broken PEPs
                    Element item = mXmppConnectionService.getIqParser().getItem(packet);
                    Set<Integer> deviceIds = mXmppConnectionService.getIqParser().deviceIds(item);
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": retrieved own device list: " + deviceIds);
                    registerDevices(account.getJid().asBareJid(), deviceIds);
                }
            }
        });
    }

    private Set<Integer> getExpiredDevices() {
        Set<Integer> devices = new HashSet<>();
        for (XmppAxolotlSession session : findOwnSessions()) {
            if (session.getTrust().isActive()) {
                long diff = System.currentTimeMillis() - session.getTrust().getLastActivation();
                if (diff > Config.OMEMO_AUTO_EXPIRY) {
                    long lastMessageDiff = System.currentTimeMillis() - mXmppConnectionService.databaseBackend.getLastTimeFingerprintUsed(account, session.getFingerprint());
                    long hours = Math.round(lastMessageDiff / (1000 * 60.0 * 60.0));
                    if (lastMessageDiff > Config.OMEMO_AUTO_EXPIRY) {
                        devices.add(session.getRemoteAddress().getDeviceId());
                        session.setTrust(session.getTrust().toInactive());
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": added own device " + session.getFingerprint() + " to list of expired devices. Last message received " + hours + " hours ago");
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": own device " + session.getFingerprint() + " was active " + hours + " hours ago");
                    }
                } //TODO print last activation diff
            }
        }
        return devices;
    }

    private void publishOwnDeviceId(Set<Integer> deviceIds) {
        Set<Integer> deviceIdsCopy = new HashSet<>(deviceIds);
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "publishing own device ids");
        if (deviceIdsCopy.isEmpty()) {
            if (numPublishTriesOnEmptyPep >= publishTriesThreshold) {
                Log.w(Config.LOGTAG, getLogprefix(account) + "Own device publish attempt threshold exceeded, aborting...");
                pepBroken = true;
                return;
            } else {
                numPublishTriesOnEmptyPep++;
                Log.w(Config.LOGTAG, getLogprefix(account) + "Own device list empty, attempting to publish (try " + numPublishTriesOnEmptyPep + ")");
            }
        } else {
            numPublishTriesOnEmptyPep = 0;
        }
        deviceIdsCopy.add(getOwnDeviceId());
        publishDeviceIdsAndRefineAccessModel(deviceIdsCopy);
    }

    private void publishDeviceIdsAndRefineAccessModel(Set<Integer> ids) {
        publishDeviceIdsAndRefineAccessModel(ids, true);
    }

    private void publishDeviceIdsAndRefineAccessModel(final Set<Integer> ids, final boolean firstAttempt) {
        final Bundle publishOptions = account.getXmppConnection().getFeatures().pepPublishOptions() ? PublishOptions.openAccess() : null;
        IqPacket publish = mXmppConnectionService.getIqGenerator().publishDeviceIds(ids, publishOptions);
        mXmppConnectionService.sendIqPacket(account, publish, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                final Element error = packet.getType() == IqPacket.TYPE.ERROR ? packet.findChild("error") : null;
                final boolean preConditionNotMet = PublishOptions.preconditionNotMet(packet);
                if (firstAttempt && preConditionNotMet) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": precondition wasn't met for device list. pushing node configuration");
                    mXmppConnectionService.pushNodeConfiguration(account, AxolotlService.PEP_DEVICE_LIST, publishOptions, new XmppConnectionService.OnConfigurationPushed() {
                        @Override
                        public void onPushSucceeded() {
                            publishDeviceIdsAndRefineAccessModel(ids, false);
                        }

                        @Override
                        public void onPushFailed() {
                            publishDeviceIdsAndRefineAccessModel(ids, false);
                        }
                    });
                } else {
                    if (AxolotlService.this.changeAccessMode.compareAndSet(true, false)) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": done changing access mode");
                        account.setOption(Account.OPTION_REQUIRES_ACCESS_MODE_CHANGE, false);
                        mXmppConnectionService.databaseBackend.updateAccount(account);
                    }
                    if (packet.getType() == IqPacket.TYPE.ERROR) {
                        if (preConditionNotMet) {
                            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": device list pre condition still not met on second attempt");
                        } else if (error != null) {
                            pepBroken = true;
                            Log.d(Config.LOGTAG, getLogprefix(account) + "Error received while publishing own device id" + packet.findChild("error"));
                        }

                    }
                }
            }
        });
    }

    public void publishDeviceVerificationAndBundle(final SignedPreKeyRecord signedPreKeyRecord,
                                                   final Set<PreKeyRecord> preKeyRecords,
                                                   final boolean announceAfter,
                                                   final boolean wipe) {
        try {
            IdentityKey axolotlPublicKey = axolotlStore.getIdentityKeyPair().getPublicKey();
            PrivateKey x509PrivateKey = KeyChain.getPrivateKey(mXmppConnectionService, account.getPrivateKeyAlias());
            X509Certificate[] chain = KeyChain.getCertificateChain(mXmppConnectionService, account.getPrivateKeyAlias());
            Signature verifier = Signature.getInstance("sha256WithRSA");
            verifier.initSign(x509PrivateKey, mXmppConnectionService.getRNG());
            verifier.update(axolotlPublicKey.serialize());
            byte[] signature = verifier.sign();
            IqPacket packet = mXmppConnectionService.getIqGenerator().publishVerification(signature, chain, getOwnDeviceId());
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ": publish verification for device " + getOwnDeviceId());
            mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(final Account account, IqPacket packet) {
                    String node = AxolotlService.PEP_VERIFICATION + ":" + getOwnDeviceId();
                    mXmppConnectionService.pushNodeConfiguration(account, node, PublishOptions.openAccess(), new XmppConnectionService.OnConfigurationPushed() {
                        @Override
                        public void onPushSucceeded() {
                            Log.d(Config.LOGTAG, getLogprefix(account) + "configured verification node to be world readable");
                            publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announceAfter, wipe);
                        }

                        @Override
                        public void onPushFailed() {
                            Log.d(Config.LOGTAG, getLogprefix(account) + "unable to set access model on verification node");
                            publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announceAfter, wipe);
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void publishBundlesIfNeeded(final boolean announce, final boolean wipe) {
        if (pepBroken) {
            Log.d(Config.LOGTAG, getLogprefix(account) + "publishBundlesIfNeeded called, but PEP is broken. Ignoring... ");
            return;
        }

        if (account.getXmppConnection().getFeatures().pepPublishOptions()) {
            this.changeAccessMode.set(account.isOptionSet(Account.OPTION_REQUIRES_ACCESS_MODE_CHANGE));
        } else {
            if (account.setOption(Account.OPTION_REQUIRES_ACCESS_MODE_CHANGE, true)) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": server doesn’t support publish-options. setting for later access mode change");
                mXmppConnectionService.databaseBackend.updateAccount(account);
            }
        }
        if (this.changeAccessMode.get()) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": server gained publish-options capabilities. changing access model");
        }
        IqPacket packet = mXmppConnectionService.getIqGenerator().retrieveBundlesForDevice(account.getJid().asBareJid(), getOwnDeviceId());
        mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {

                if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
                    return; //ignore timeout. do nothing
                }

                if (packet.getType() == IqPacket.TYPE.ERROR) {
                    Element error = packet.findChild("error");
                    if (error == null || !error.hasChild("item-not-found")) {
                        pepBroken = true;
                        Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "request for device bundles came back with something other than item-not-found" + packet);
                        return;
                    }
                }

                PreKeyBundle bundle = mXmppConnectionService.getIqParser().bundle(packet);
                Map<Integer, ECPublicKey> keys = mXmppConnectionService.getIqParser().preKeyPublics(packet);
                boolean flush = false;
                if (bundle == null) {
                    Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received invalid bundle:" + packet);
                    bundle = new PreKeyBundle(-1, -1, -1, null, -1, null, null, null);
                    flush = true;
                }
                if (keys == null) {
                    Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received invalid prekeys:" + packet);
                }
                try {
                    boolean changed = false;
                    // Validate IdentityKey
                    IdentityKeyPair identityKeyPair = axolotlStore.getIdentityKeyPair();
                    if (flush || !identityKeyPair.getPublicKey().equals(bundle.getIdentityKey())) {
                        Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Adding own IdentityKey " + identityKeyPair.getPublicKey() + " to PEP.");
                        changed = true;
                    }

                    // Validate signedPreKeyRecord + ID
                    SignedPreKeyRecord signedPreKeyRecord;
                    int numSignedPreKeys = axolotlStore.getSignedPreKeysCount();
                    try {
                        signedPreKeyRecord = axolotlStore.loadSignedPreKey(bundle.getSignedPreKeyId());
                        if (flush
                                || !bundle.getSignedPreKey().equals(signedPreKeyRecord.getKeyPair().getPublicKey())
                                || !Arrays.equals(bundle.getSignedPreKeySignature(), signedPreKeyRecord.getSignature())) {
                            Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Adding new signedPreKey with ID " + (numSignedPreKeys + 1) + " to PEP.");
                            signedPreKeyRecord = KeyHelper.generateSignedPreKey(identityKeyPair, numSignedPreKeys + 1);
                            axolotlStore.storeSignedPreKey(signedPreKeyRecord.getId(), signedPreKeyRecord);
                            changed = true;
                        }
                    } catch (InvalidKeyIdException e) {
                        Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Adding new signedPreKey with ID " + (numSignedPreKeys + 1) + " to PEP.");
                        signedPreKeyRecord = KeyHelper.generateSignedPreKey(identityKeyPair, numSignedPreKeys + 1);
                        axolotlStore.storeSignedPreKey(signedPreKeyRecord.getId(), signedPreKeyRecord);
                        changed = true;
                    }

                    // Validate PreKeys
                    Set<PreKeyRecord> preKeyRecords = new HashSet<>();
                    if (keys != null) {
                        for (Integer id : keys.keySet()) {
                            try {
                                PreKeyRecord preKeyRecord = axolotlStore.loadPreKey(id);
                                if (preKeyRecord.getKeyPair().getPublicKey().equals(keys.get(id))) {
                                    preKeyRecords.add(preKeyRecord);
                                }
                            } catch (InvalidKeyIdException ignored) {
                            }
                        }
                    }
                    int newKeys = NUM_KEYS_TO_PUBLISH - preKeyRecords.size();
                    if (newKeys > 0) {
                        List<PreKeyRecord> newRecords = KeyHelper.generatePreKeys(
                                axolotlStore.getCurrentPreKeyId() + 1, newKeys);
                        preKeyRecords.addAll(newRecords);
                        for (PreKeyRecord record : newRecords) {
                            axolotlStore.storePreKey(record.getId(), record);
                        }
                        changed = true;
                        Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Adding " + newKeys + " new preKeys to PEP.");
                    }


                    if (changed || changeAccessMode.get()) {
                        if (account.getPrivateKeyAlias() != null && Config.X509_VERIFICATION) {
                            mXmppConnectionService.publishDisplayName(account);
                            publishDeviceVerificationAndBundle(signedPreKeyRecord, preKeyRecords, announce, wipe);
                        } else {
                            publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announce, wipe);
                        }
                    } else {
                        Log.d(Config.LOGTAG, getLogprefix(account) + "Bundle " + getOwnDeviceId() + " in PEP was current");
                        if (wipe) {
                            wipeOtherPepDevices();
                        } else if (announce) {
                            Log.d(Config.LOGTAG, getLogprefix(account) + "Announcing device " + getOwnDeviceId());
                            publishOwnDeviceIdIfNeeded();
                        }
                    }
                } catch (InvalidKeyException e) {
                    Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Failed to publish bundle " + getOwnDeviceId() + ", reason: " + e.getMessage());
                }
            }
        });
    }

    private void publishDeviceBundle(SignedPreKeyRecord signedPreKeyRecord,
                                     Set<PreKeyRecord> preKeyRecords,
                                     final boolean announceAfter,
                                     final boolean wipe) {
        publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announceAfter, wipe, true);
    }

    private void publishDeviceBundle(final SignedPreKeyRecord signedPreKeyRecord,
                                     final Set<PreKeyRecord> preKeyRecords,
                                     final boolean announceAfter,
                                     final boolean wipe,
                                     final boolean firstAttempt) {
        final Bundle publishOptions = account.getXmppConnection().getFeatures().pepPublishOptions() ? PublishOptions.openAccess() : null;
        final IqPacket publish = mXmppConnectionService.getIqGenerator().publishBundles(
                signedPreKeyRecord, axolotlStore.getIdentityKeyPair().getPublicKey(),
                preKeyRecords, getOwnDeviceId(), publishOptions);
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + ": Bundle " + getOwnDeviceId() + " in PEP not current. Publishing...");
        mXmppConnectionService.sendIqPacket(account, publish, new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(final Account account, IqPacket packet) {
                final boolean preconditionNotMet = PublishOptions.preconditionNotMet(packet);
                if (firstAttempt && preconditionNotMet) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": precondition wasn't met for bundle. pushing node configuration");
                    final String node = AxolotlService.PEP_BUNDLES + ":" + getOwnDeviceId();
                    mXmppConnectionService.pushNodeConfiguration(account, node, publishOptions, new XmppConnectionService.OnConfigurationPushed() {
                        @Override
                        public void onPushSucceeded() {
                            publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announceAfter, wipe, false);
                        }

                        @Override
                        public void onPushFailed() {
                            publishDeviceBundle(signedPreKeyRecord, preKeyRecords, announceAfter, wipe, false);
                        }
                    });
                } else if (packet.getType() == IqPacket.TYPE.RESULT) {
                    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Successfully published bundle. ");
                    if (wipe) {
                        wipeOtherPepDevices();
                    } else if (announceAfter) {
                        Log.d(Config.LOGTAG, getLogprefix(account) + "Announcing device " + getOwnDeviceId());
                        publishOwnDeviceIdIfNeeded();
                    }
                } else if (packet.getType() == IqPacket.TYPE.ERROR) {
                    if (preconditionNotMet) {
                        Log.d(Config.LOGTAG, getLogprefix(account) + "bundle precondition still not met after second attempt");
                    } else {
                        Log.d(Config.LOGTAG, getLogprefix(account) + "Error received while publishing bundle: " + packet.toString());
                    }
                    pepBroken = true;
                }
            }
        });
    }

    public FingerprintStatus getFingerprintTrust(String fingerprint) {
        return axolotlStore.getFingerprintStatus(fingerprint);
    }

    public void setFingerprintTrust(String fingerprint, FingerprintStatus status) {
        axolotlStore.setFingerprintStatus(fingerprint, status);
    }

    private void verifySessionWithPEP(final XmppAxolotlSession session) {
        Log.d(Config.LOGTAG, "trying to verify fresh session (" + session.getRemoteAddress().getName() + ") with pep");
        final SignalProtocolAddress address = session.getRemoteAddress();
        final IdentityKey identityKey = session.getIdentityKey();
        try {
            IqPacket packet = mXmppConnectionService.getIqGenerator().retrieveVerificationForDevice(Jid.of(address.getName()), address.getDeviceId());
            mXmppConnectionService.sendIqPacket(account, packet, new OnIqPacketReceived() {
                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    Pair<X509Certificate[], byte[]> verification = mXmppConnectionService.getIqParser().verification(packet);
                    if (verification != null) {
                        try {
                            Signature verifier = Signature.getInstance("sha256WithRSA");
                            verifier.initVerify(verification.first[0]);
                            verifier.update(identityKey.serialize());
                            if (verifier.verify(verification.second)) {
                                try {
                                    mXmppConnectionService.getMemorizingTrustManager().getNonInteractive().checkClientTrusted(verification.first, "RSA");
                                    String fingerprint = session.getFingerprint();
                                    Log.d(Config.LOGTAG, "verified session with x.509 signature. fingerprint was: " + fingerprint);
                                    setFingerprintTrust(fingerprint, FingerprintStatus.createActiveVerified(true));
                                    axolotlStore.setFingerprintCertificate(fingerprint, verification.first[0]);
                                    fetchStatusMap.put(address, FetchStatus.SUCCESS_VERIFIED);
                                    Bundle information = CryptoHelper.extractCertificateInformation(verification.first[0]);
                                    try {
                                        final String cn = information.getString("subject_cn");
                                        final Jid jid = Jid.of(address.getName());
                                        Log.d(Config.LOGTAG, "setting common name for " + jid + " to " + cn);
                                        account.getRoster().getContact(jid).setCommonName(cn);
                                    } catch (final IllegalArgumentException ignored) {
                                        //ignored
                                    }
                                    finishBuildingSessionsFromPEP(address);
                                    return;
                                } catch (Exception e) {
                                    Log.d(Config.LOGTAG, "could not verify certificate");
                                }
                            }
                        } catch (Exception e) {
                            Log.d(Config.LOGTAG, "error during verification " + e.getMessage());
                        }
                    } else {
                        Log.d(Config.LOGTAG, "no verification found");
                    }
                    fetchStatusMap.put(address, FetchStatus.SUCCESS);
                    finishBuildingSessionsFromPEP(address);
                }
            });
        } catch (IllegalArgumentException e) {
            fetchStatusMap.put(address, FetchStatus.SUCCESS);
            finishBuildingSessionsFromPEP(address);
        }
    }

    private void finishBuildingSessionsFromPEP(final SignalProtocolAddress address) {
        SignalProtocolAddress ownAddress = new SignalProtocolAddress(account.getJid().asBareJid().toString(), 0);
        Map<Integer, FetchStatus> own = fetchStatusMap.getAll(ownAddress.getName());
        Map<Integer, FetchStatus> remote = fetchStatusMap.getAll(address.getName());
        if (!own.containsValue(FetchStatus.PENDING) && !remote.containsValue(FetchStatus.PENDING)) {
            FetchStatus report = null;
            if (own.containsValue(FetchStatus.SUCCESS) || remote.containsValue(FetchStatus.SUCCESS)) {
                report = FetchStatus.SUCCESS;
            } else if (own.containsValue(FetchStatus.SUCCESS_VERIFIED) || remote.containsValue(FetchStatus.SUCCESS_VERIFIED)) {
                report = FetchStatus.SUCCESS_VERIFIED;
            } else if (own.containsValue(FetchStatus.SUCCESS_TRUSTED) || remote.containsValue(FetchStatus.SUCCESS_TRUSTED)) {
                report = FetchStatus.SUCCESS_TRUSTED;
            } else if (own.containsValue(FetchStatus.ERROR) || remote.containsValue(FetchStatus.ERROR)) {
                report = FetchStatus.ERROR;
            }
            mXmppConnectionService.keyStatusUpdated(report);
        }
        if (Config.REMOVE_BROKEN_DEVICES) {
            Set<Integer> ownDeviceIds = new HashSet<>(getOwnDeviceIds());
            boolean publish = false;
            for (Map.Entry<Integer, FetchStatus> entry : own.entrySet()) {
                int id = entry.getKey();
                if (entry.getValue() == FetchStatus.ERROR && PREVIOUSLY_REMOVED_FROM_ANNOUNCEMENT.add(id) && ownDeviceIds.remove(id)) {
                    publish = true;
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error fetching own device with id " + id + ". removing from announcement");
                }
            }
            if (publish) {
                publishOwnDeviceId(ownDeviceIds);
            }
        }
    }

    private void buildSessionFromPEP(final SignalProtocolAddress address) {
        buildSessionFromPEP(address, null);
    }

    private void buildSessionFromPEP(final SignalProtocolAddress address, OnSessionBuildFromPep callback) {
        Log.i(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Building new session for " + address.toString());
        if (address.equals(getOwnAxolotlAddress())) {
            throw new AssertionError("We should NEVER build a session with ourselves. What happened here?!");
        }

        final Jid jid = Jid.of(address.getName());
        final boolean oneOfOurs = jid.asBareJid().equals(account.getJid().asBareJid());
        IqPacket bundlesPacket = mXmppConnectionService.getIqGenerator().retrieveBundlesForDevice(jid, address.getDeviceId());
        mXmppConnectionService.sendIqPacket(account, bundlesPacket, (account, packet) -> {
            if (packet.getType() == IqPacket.TYPE.TIMEOUT) {
                fetchStatusMap.put(address, FetchStatus.TIMEOUT);
            } else if (packet.getType() == IqPacket.TYPE.RESULT) {
                Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Received preKey IQ packet, processing...");
                final IqParser parser = mXmppConnectionService.getIqParser();
                final List<PreKeyBundle> preKeyBundleList = parser.preKeys(packet);
                final PreKeyBundle bundle = parser.bundle(packet);
                if (preKeyBundleList.isEmpty() || bundle == null) {
                    Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "preKey IQ packet invalid: " + packet);
                    fetchStatusMap.put(address, FetchStatus.ERROR);
                    finishBuildingSessionsFromPEP(address);
                    if (callback != null) {
                        callback.onSessionBuildFailed();
                    }
                    return;
                }
                Random random = new Random();
                final PreKeyBundle preKey = preKeyBundleList.get(random.nextInt(preKeyBundleList.size()));
                if (preKey == null) {
                    //should never happen
                    fetchStatusMap.put(address, FetchStatus.ERROR);
                    finishBuildingSessionsFromPEP(address);
                    if (callback != null) {
                        callback.onSessionBuildFailed();
                    }
                    return;
                }

                final PreKeyBundle preKeyBundle = new PreKeyBundle(0, address.getDeviceId(),
                        preKey.getPreKeyId(), preKey.getPreKey(),
                        bundle.getSignedPreKeyId(), bundle.getSignedPreKey(),
                        bundle.getSignedPreKeySignature(), bundle.getIdentityKey());

                try {
                    SessionBuilder builder = new SessionBuilder(axolotlStore, address);
                    builder.process(preKeyBundle);
                    XmppAxolotlSession session = new XmppAxolotlSession(account, axolotlStore, address, bundle.getIdentityKey());
                    sessions.put(address, session);
                    if (Config.X509_VERIFICATION) {
                        verifySessionWithPEP(session); //TODO; maybe inject callback in here too
                    } else {
                        FingerprintStatus status = getFingerprintTrust(CryptoHelper.bytesToHex(bundle.getIdentityKey().getPublicKey().serialize()));
                        FetchStatus fetchStatus;
                        if (status != null && status.isVerified()) {
                            fetchStatus = FetchStatus.SUCCESS_VERIFIED;
                        } else if (status != null && status.isTrusted()) {
                            fetchStatus = FetchStatus.SUCCESS_TRUSTED;
                        } else {
                            fetchStatus = FetchStatus.SUCCESS;
                        }
                        fetchStatusMap.put(address, fetchStatus);
                        finishBuildingSessionsFromPEP(address);
                        if (callback != null) {
                            callback.onSessionBuildSuccessful();
                        }
                    }
                } catch (UntrustedIdentityException | InvalidKeyException e) {
                    Log.e(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Error building session for " + address + ": "
                            + e.getClass().getName() + ", " + e.getMessage());
                    fetchStatusMap.put(address, FetchStatus.ERROR);
                    finishBuildingSessionsFromPEP(address);
                    if (oneOfOurs && cleanedOwnDeviceIds.add(address.getDeviceId())) {
                        removeFromDeviceAnnouncement(address.getDeviceId());
                    }
                    if (callback != null) {
                        callback.onSessionBuildFailed();
                    }
                }
            } else {
                fetchStatusMap.put(address, FetchStatus.ERROR);
                Element error = packet.findChild("error");
                boolean itemNotFound = error != null && error.hasChild("item-not-found");
                Log.d(Config.LOGTAG, getLogprefix(account) + "Error received while building session:" + packet.findChild("error"));
                finishBuildingSessionsFromPEP(address);
                if (oneOfOurs && itemNotFound && cleanedOwnDeviceIds.add(address.getDeviceId())) {
                    removeFromDeviceAnnouncement(address.getDeviceId());
                }
                if (callback != null) {
                    callback.onSessionBuildFailed();
                }
            }
        });
    }

    private void removeFromDeviceAnnouncement(Integer id) {
        HashSet<Integer> temp = new HashSet<>(getOwnDeviceIds());
        if (temp.remove(id)) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + " remove own device id " + id + " from announcement. devices left:" + temp);
            publishOwnDeviceId(temp);
        }
    }

    private XmppAxolotlSession recreateUncachedSession(SignalProtocolAddress address) {
        IdentityKey identityKey = axolotlStore.loadSession(address).getSessionState().getRemoteIdentityKey();
        return (identityKey != null)
                ? new XmppAxolotlSession(account, axolotlStore, address, identityKey)
                : null;
    }

    private XmppAxolotlSession getReceivingSession(SignalProtocolAddress senderAddress) {
        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            session = recreateUncachedSession(senderAddress);
            if (session == null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
        }
        return session;
    }

    private void notifyRequiresHealing(final SignalProtocolAddress signalProtocolAddress) {
        if (healingAttempts.add(signalProtocolAddress)) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": attempt to heal " + signalProtocolAddress);
            buildSessionFromPEP(signalProtocolAddress, new OnSessionBuildFromPep() {
                @Override
                public void onSessionBuildSuccessful() {
                    Log.d(Config.LOGTAG, "successfully build new session from pep after detecting broken session");
                    completeSession(getReceivingSession(signalProtocolAddress));
                }

                @Override
                public void onSessionBuildFailed() {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to build new session from pep after detecting broken session");
                }
            });
        } else {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": do not attempt to heal " + signalProtocolAddress + " again");
        }
    }

    public void processPostponed() {
        if (postponedSessions.size() > 0) {
            if (axolotlStore.flushPreKeys()) {
                publishBundlesIfNeeded(false, false);
            }
        }
        final Iterator<XmppAxolotlSession> iterator = postponedSessions.iterator();
        while (iterator.hasNext()) {
            final XmppAxolotlSession session = iterator.next();
            if (trustedOrPreviouslyResponded(session)) {
                completeSession(session);
            }
            iterator.remove();
        }
        final Iterator<SignalProtocolAddress> postponedHealingAttemptsIterator = postponedHealing.iterator();
        while (postponedHealingAttemptsIterator.hasNext()) {
            notifyRequiresHealing(postponedHealingAttemptsIterator.next());
            postponedHealingAttemptsIterator.remove();
        }
    }

    private boolean trustedOrPreviouslyResponded(XmppAxolotlSession session) {
        try {
            return trustedOrPreviouslyResponded(Jid.of(session.getRemoteAddress().getName()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean trustedOrPreviouslyResponded(Jid jid) {

        // for now we just set return to true to cross implemantation
        return true;
    }

    private void completeSession(XmppAxolotlSession session) {

        // we will add this implementation if we need it in the future
    }

    public enum FetchStatus {
        PENDING,
        SUCCESS,
        SUCCESS_VERIFIED,
        TIMEOUT,
        SUCCESS_TRUSTED,
        ERROR
    }

    interface OnSessionBuildFromPep {
        void onSessionBuildSuccessful();

        void onSessionBuildFailed();
    }

    private static class AxolotlAddressMap<T> {
        protected final Object MAP_LOCK = new Object();
        protected Map<String, Map<Integer, T>> map;

        public AxolotlAddressMap() {
            this.map = new HashMap<>();
        }

        public void put(SignalProtocolAddress address, T value) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                if (devices == null) {
                    devices = new HashMap<>();
                    map.put(address.getName(), devices);
                }
                devices.put(address.getDeviceId(), value);
            }
        }

        public T get(SignalProtocolAddress address) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                if (devices == null) {
                    return null;
                }
                return devices.get(address.getDeviceId());
            }
        }

        public Map<Integer, T> getAll(String name) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(name);
                if (devices == null) {
                    return new HashMap<>();
                }
                return devices;
            }
        }

        public boolean hasAny(SignalProtocolAddress address) {
            synchronized (MAP_LOCK) {
                Map<Integer, T> devices = map.get(address.getName());
                return devices != null && !devices.isEmpty();
            }
        }

        public void clear() {
            map.clear();
        }

    }

    private static class SessionMap extends AxolotlAddressMap<XmppAxolotlSession> {
        private final XmppConnectionService xmppConnectionService;
        private final Account account;

        public SessionMap(XmppConnectionService service, SQLiteAxolotlStore store, Account account) {
            super();
            this.xmppConnectionService = service;
            this.account = account;
            this.fillMap(store);
        }

        public Set<Jid> findCounterpartsForSourceId(Integer sid) {
            Set<Jid> candidates = new HashSet<>();
            synchronized (MAP_LOCK) {
                for (Map.Entry<String, Map<Integer, XmppAxolotlSession>> entry : map.entrySet()) {
                    String key = entry.getKey();
                    if (entry.getValue().containsKey(sid)) {
                        candidates.add(Jid.of(key));
                    }
                }
            }
            return candidates;
        }

        private void putDevicesForJid(String bareJid, List<Integer> deviceIds, SQLiteAxolotlStore store) {
            for (Integer deviceId : deviceIds) {
                SignalProtocolAddress axolotlAddress = new SignalProtocolAddress(bareJid, deviceId);
                IdentityKey identityKey = store.loadSession(axolotlAddress).getSessionState().getRemoteIdentityKey();
                if (Config.X509_VERIFICATION) {
                    X509Certificate certificate = store.getFingerprintCertificate(CryptoHelper.bytesToHex(identityKey.getPublicKey().serialize()));
                    if (certificate != null) {
                        Bundle information = CryptoHelper.extractCertificateInformation(certificate);
                        try {
                            final String cn = information.getString("subject_cn");
                            final Jid jid = Jid.of(bareJid);
                            Log.d(Config.LOGTAG, "setting common name for " + jid + " to " + cn);
                            account.getRoster().getContact(jid).setCommonName(cn);
                        } catch (final IllegalArgumentException ignored) {
                            //ignored
                        }
                    }
                }
                this.put(axolotlAddress, new XmppAxolotlSession(account, store, axolotlAddress, identityKey));
            }
        }

        private void fillMap(SQLiteAxolotlStore store) {
            List<Integer> deviceIds = store.getSubDeviceSessions(account.getJid().asBareJid().toString());
            putDevicesForJid(account.getJid().asBareJid().toString(), deviceIds, store);
            for (String address : store.getKnownAddresses()) {
                deviceIds = store.getSubDeviceSessions(address);
                putDevicesForJid(address, deviceIds, store);
            }
        }

        @Override
        public void put(SignalProtocolAddress address, XmppAxolotlSession value) {
            super.put(address, value);
            value.setNotFresh();
        }

        public void put(XmppAxolotlSession session) {
            this.put(session.getRemoteAddress(), session);
        }
    }

    private static class FetchStatusMap extends AxolotlAddressMap<FetchStatus> {

        public void clearErrorFor(Jid jid) {
            synchronized (MAP_LOCK) {
                Map<Integer, FetchStatus> devices = this.map.get(jid.asBareJid().toString());
                if (devices == null) {
                    return;
                }
                for (Map.Entry<Integer, FetchStatus> entry : devices.entrySet()) {
                    if (entry.getValue() == FetchStatus.ERROR) {
                        Log.d(Config.LOGTAG, "resetting error for " + jid.asBareJid() + "(" + entry.getKey() + ")");
                        entry.setValue(FetchStatus.TIMEOUT);
                    }
                }
            }
        }
    }
}

package com.plutonem.xmpp.services;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;

import androidx.annotation.BoolRes;
import androidx.annotation.IntegerRes;
import androidx.core.app.RemoteInput;

import com.google.android.exoplayer2.util.ParsableNalUnitBitArray;
import com.plutonem.Config;
import com.plutonem.android.login.R;
import com.plutonem.xmpp.crypto.axolotl.AxolotlService;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Avatar;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.entities.ServiceDiscoveryResult;
import com.plutonem.xmpp.generator.AbstractGenerator;
import com.plutonem.xmpp.generator.IqGenerator;
import com.plutonem.xmpp.generator.MessageGenerator;
import com.plutonem.xmpp.parser.IqParser;
import com.plutonem.xmpp.parser.MessageParser;
import com.plutonem.xmpp.parser.PresenceParser;
import com.plutonem.xmpp.persistance.DatabaseBackend;
import com.plutonem.xmpp.persistance.FileBackend;
import com.plutonem.xmpp.ui.SettingsActivity;
import com.plutonem.xmpp.ui.UiCallback;
import com.plutonem.xmpp.ui.XmppActivity;
import com.plutonem.xmpp.utils.Compatibility;
import com.plutonem.xmpp.utils.CryptoHelper;
import com.plutonem.xmpp.utils.ExceptionHelper;
import com.plutonem.xmpp.utils.PhoneHelper;
import com.plutonem.xmpp.utils.QuickLoader;
import com.plutonem.xmpp.utils.ReplacingTaskManager;
import com.plutonem.xmpp.utils.Resolver;
import com.plutonem.xmpp.utils.SerialSingleThreadExecutor;
import com.plutonem.xmpp.utils.WakeLockHelper;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.OnBindListener;
import com.plutonem.xmpp.xmpp.OnIqPacketReceived;
import com.plutonem.xmpp.xmpp.OnKeyStatusUpdated;
import com.plutonem.xmpp.xmpp.OnMessageAcknowledged;
import com.plutonem.xmpp.xmpp.OnMessagePacketReceived;
import com.plutonem.xmpp.xmpp.OnPresencePacketReceived;
import com.plutonem.xmpp.xmpp.OnStatusChanged;
import com.plutonem.xmpp.xmpp.OnUpdateBlocklist;
import com.plutonem.xmpp.xmpp.Patches;
import com.plutonem.xmpp.xmpp.XmppConnection;
import com.plutonem.xmpp.xmpp.chatstate.ChatState;
import com.plutonem.xmpp.xmpp.forms.Data;
import com.plutonem.xmpp.xmpp.jingle.OnJinglePacketReceived;
import com.plutonem.xmpp.xmpp.jingle.stanzas.JinglePacket;
import com.plutonem.xmpp.xmpp.mam.MamReference;
import com.plutonem.xmpp.xmpp.stanzas.IqPacket;
import com.plutonem.xmpp.xmpp.stanzas.MessagePacket;

import org.conscrypt.Conscrypt;

import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import me.leolin.shortcutbadger.ShortcutBadger;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends Service {

    public static final String ACTION_REPLY_TO_CONVERSATION = "reply_to_conversations";
    public static final String ACTION_MARK_AS_READ = "mark_as_read";
    public static final String ACTION_SNOOZE = "snooze";
    public static final String ACTION_CLEAR_NOTIFICATION = "clear_notification";
    public static final String ACTION_DISMISS_ERROR_NOTIFICATIONS = "dismiss_error";
    public static final String ACTION_TRY_AGAIN = "try_again";
    public static final String ACTION_IDLE_PING = "idle_ping";
    public static final String ACTION_FCM_TOKEN_REFRESH = "fcm_token_refresh";
    public static final String ACTION_FCM_MESSAGE_RECEIVED = "fcm_message_received";
    private static final String ACTION_POST_CONNECTIVITY_CHANGE = "eu.siacs.conversations.POST_CONNECTIVITY_CHANGE";

    private static final String SETTING_LAST_ACTIVITY_TS = "last_activity_timestamp";

    public final CountDownLatch restoredFromDatabaseLatch = new CountDownLatch(1);
    private final SerialSingleThreadExecutor mDatabaseWriterExecutor = new SerialSingleThreadExecutor("DatabaseWriter");
    private final SerialSingleThreadExecutor mDatabaseReaderExecutor = new SerialSingleThreadExecutor("DatabaseReader");
    private final SerialSingleThreadExecutor mNotificationExecutor = new SerialSingleThreadExecutor("NotificationExecutor");
    private final ReplacingTaskManager mRosterSyncTaskManager = new ReplacingTaskManager();
    private final IBinder mBinder = new XmppConnectionBinder();
    private final List<Conversation> conversations = new CopyOnWriteArrayList<>();
    private final IqGenerator mIqGenerator = new IqGenerator(this);
    private final HashSet<Jid> mLowPingTimeoutMode = new HashSet<>();
    public DatabaseBackend databaseBackend;
    private long mLastActivity = 0;
    private FileBackend fileBackend = new FileBackend(this);
    private MemorizingTrustManager mMemorizingTrustManager;
    private NotificationService mNotificationService = new NotificationService(this);
    private ShortcutService mShortcutService = new ShortcutService(this);
    private AtomicBoolean mForceForegroundService = new AtomicBoolean(false);
    private AtomicBoolean mForceDuringOnCreate = new AtomicBoolean(false);
    private OnMessagePacketReceived mMessageParser = new MessageParser(this);
    private OnPresencePacketReceived mPresenceParser = new PresenceParser(this);
    private IqParser mIqParser = new IqParser(this);
    private MessageGenerator mMessageGenerator = new MessageGenerator(this);
    private List<Account> accounts;
    private final OnJinglePacketReceived jingleListener = new OnJinglePacketReceived() {

        @Override
        public void onJinglePacketReceived(Account account, JinglePacket packet) {

            // we will skip this part by now for future modification
        }
    };
    private MessageArchiveService mMessageArchiveService = new MessageArchiveService(this);
    private AvatarService mAvatarService = new AvatarService(this);
    private QuickConversationsService mQuickConversationsService = new QuickConversationsService(this);
    private final OnMessageAcknowledged mOnMessageAcknowledgedListener = new OnMessageAcknowledged() {

        @Override
        public boolean onMessageAcknowledged(Account account, String uuid) {

            // we will skip this part by now for future modification
            // now we need it !!
            for (final Conversation conversation : getConversations()) {
                if (conversation.getAccount() == account) {
                    Message message = conversation.findUnsentMessageWithUuid(uuid);
                    if (message != null) {
                        message.setStatus(Message.STATUS_SEND);
                        message.setErrorMessage(null);
                        databaseBackend.updateMessage(message, false);
                        return true;
                    }
                }
            }
            return false;
        }
    };

    private boolean destroyed = false;

    private int unreadCount = -1;

    //Ui callback listeners
    private final Set<OnConversationUpdate> mOnConversationUpdates = Collections.newSetFromMap(new WeakHashMap<OnConversationUpdate, Boolean>());
    private final Set<OnShowErrorToast> mOnShowErrorToasts = Collections.newSetFromMap(new WeakHashMap<OnShowErrorToast, Boolean>());
    private final Set<OnAccountUpdate> mOnAccountUpdates = Collections.newSetFromMap(new WeakHashMap<OnAccountUpdate, Boolean>());
    private final Set<OnCaptchaRequested> mOnCaptchaRequested = Collections.newSetFromMap(new WeakHashMap<OnCaptchaRequested, Boolean>());
    private final Set<OnRosterUpdate> mOnRosterUpdates = Collections.newSetFromMap(new WeakHashMap<OnRosterUpdate, Boolean>());
    private final Set<OnUpdateBlocklist> mOnUpdateBlocklist = Collections.newSetFromMap(new WeakHashMap<OnUpdateBlocklist, Boolean>());
    private final Set<OnMucRosterUpdate> mOnMucRosterUpdate = Collections.newSetFromMap(new WeakHashMap<OnMucRosterUpdate, Boolean>());
    private final Set<OnKeyStatusUpdated> mOnKeyStatusUpdated = Collections.newSetFromMap(new WeakHashMap<OnKeyStatusUpdated, Boolean>());

    private final Object LISTENER_LOCK = new Object();

    private final OnBindListener mOnBindListener = new OnBindListener() {
        @Override
        public void onBind(Account account) {

            // we will skip this part by now for future modification
            // now we need it !!

            // skip Avatar Fetch Progress part.

            boolean loggedInSuccessfully = account.setOption(Account.OPTION_LOGGED_IN_SUCCESSFULLY, true);
            boolean gainedFeature = account.setOption(Account.OPTION_HTTP_UPLOAD_AVAILABLE, account.getXmppConnection().getFeatures().httpUpload(0));
            if (loggedInSuccessfully || gainedFeature) {
                databaseBackend.updateAccount(account);
            }

            // skip Roster Presence part.

            // skip Multi Mode Chat part.

            // skip Jingle File Transfer part.

            mQuickConversationsService.considerSyncBackground(false);

            final boolean flexible = account.getXmppConnection().getFeatures().flexibleOfflineMessageRetrieval();
            final boolean catchup = getMessageArchiveService().inCatchup(account);
            if (flexible && catchup && account.getXmppConnection().isMamPreferenceAlways()) {
                sendIqPacket(account, mIqGenerator.purgeOfflineMessages(), (acc, packet) -> {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        Log.d(Config.LOGTAG, acc.getJid().asBareJid() + ": successfully purged offline messages");
                    }
                });
            }

            // skip Presence part.

            // skip Multi Mode Chat part.

            // skip Contacts part.
        }
    };

    private AtomicLong mLastExpiryRun = new AtomicLong(0);
    private SecureRandom mRandom;
    private LruCache<Pair<String, String>, ServiceDiscoveryResult> discoCache = new LruCache<>(20);
    private OnStatusChanged statusListener = new OnStatusChanged() {
        @Override
        public void onStatusChanged(Account account) {
            XmppConnection connection = account.getXmppConnection();
            updateAccountUi();

            if (account.getStatus() == Account.State.ONLINE || account.getStatus().isError()) {
                mQuickConversationsService.signalAccountStateChange();
            }

            if (account.getStatus() == Account.State.ONLINE) {
                synchronized (mLowPingTimeoutMode) {
                    if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
                    }
                }
                if (account.setShowErrorNotification(true)) {
                    databaseBackend.updateAccount(account);
                }
                mMessageArchiveService.executePendingQueries(account);
                if (connection != null && connection.getFeatures().csi()) {
                    if (checkListeners()) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//inactive");
                        connection.sendInactive();
                    } else {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + " sending csi//active");
                        connection.sendActive();
                    }
                }
                List<Conversation> conversations = getConversations();
                for (Conversation conversation : conversations) {

                    // as we don't have 'JOIN' function for Multi user chat like conference or group, so we omit some value check process

                    if (conversation.getAccount() == account) {
                        sendUnsentMessages(conversation);
                    }
                }

                // simply omit Leaving Muc part for now

                // simply omit Joining Muc part for now

                scheduleWakeUpCall(Config.PING_MAX_INTERVAL, account.getUuid().hashCode());
            } else if (account.getStatus() == Account.State.OFFLINE || account.getStatus() == Account.State.DISABLED) {
                resetSendingToWaiting(account);
                if (account.isEnabled() && isInLowPingTimeoutMode(account)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": went into offline state during low ping mode. reconnecting now");
                    reconnectAccount(account, true, false);
                } else {
                    int timeToReconnect = mRandom.nextInt(10) + 2;
                    scheduleWakeUpCall(timeToReconnect, account.getUuid().hashCode());
                }
            } else if (account.getStatus() == Account.State.REGISTRATION_SUCCESSFUL) {
                databaseBackend.updateAccount(account);
                reconnectAccount(account, true, false);
            } else if (account.getStatus() != Account.State.CONNECTING && account.getStatus() != Account.State.NO_INTERNET) {
                resetSendingToWaiting(account);
                if (connection != null && account.getStatus().isAttemptReconnect()) {
                    final int next = connection.getTimeToNextAttempt();
                    final boolean lowPingTimeoutMode = isInLowPingTimeoutMode(account);
                    if (next <= 0) {
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. reconnecting now. lowPingTimeout=" + lowPingTimeoutMode);
                        reconnectAccount(account, true, false);
                    } else {
                        final int attempt = connection.getAttempt() + 1;
                        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": error connecting account. try again in " + next + "s for the " + attempt + " time. lowPingTimeout=" + lowPingTimeoutMode);
                        scheduleWakeUpCall(next, account.getUuid().hashCode());
                    }
                }
            }
            getNotificationService().updateErrorNotification();
        }
    };

    private WakeLock wakeLock;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;
    private BroadcastReceiver mInternalEventReceiver = new InternalEventReceiver();
    private BroadcastReceiver mInternalScreenEventReceiver = new InternalEventReceiver();

    private boolean isInLowPingTimeoutMode(Account account) {
        synchronized (mLowPingTimeoutMode) {
            return mLowPingTimeoutMode.contains(account.getJid().asBareJid());
        }
    }

    public boolean areMessagesInitialized() {
        return this.restoredFromDatabaseLatch.getCount() == 0;
    }

    public FileBackend getFileBackend() {
        return this.fileBackend;
    }

    public AvatarService getAvatarService() {
        return this.mAvatarService;
    }

    public Conversation find(final Account account, final Jid jid) {
        return find(getConversations(), account, jid);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent == null ? null : intent.getAction();
        final boolean needsForegroundService = intent != null && intent.getBooleanExtra(EventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE, false);
        if (needsForegroundService) {
            Log.d(Config.LOGTAG, "toggle forced foreground service after receiving event (action=" + action + ")");
            toggleForegroundService(true);
        }
        String pushedAccountHash = null;
        String pushedChannelHash = null;
        boolean interactive = false;
        if (action != null) {
            final String uuid = intent.getStringExtra("uuid");
            switch (action) {
                case ConnectivityManager.CONNECTIVITY_ACTION:
                    if (hasInternetConnection()) {
                        if (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0) {
                            schedulePostConnectivityChange();
                        }
                        if (Config.RESET_ATTEMPT_COUNT_ON_NETWORK_CHANGE) {
                            resetAllAttemptCounts(true, false);
                        }
                    }
                    break;
                case ACTION_REPLY_TO_CONVERSATION:
                    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
                    if (remoteInput == null) {
                        break;
                    }
                    final CharSequence body = remoteInput.getCharSequence("text_reply");
                    final boolean dismissNotification = intent.getBooleanExtra("dismiss_notification", false);
                    if (body == null || body.length() <= 0) {
                        break;
                    }
                    mNotificationExecutor.execute(() -> {
                        try {
                            restoredFromDatabaseLatch.await();
                            final Conversation c = findConversationByUuid(uuid);
                            if (c != null) {
                                directReply(c, body.toString(), dismissNotification);
                            }
                        } catch (InterruptedException e) {
                            Log.d(Config.LOGTAG, "unable to process direct reply");
                        }
                    });
                    break;
                case ACTION_MARK_AS_READ:
                    mNotificationExecutor.execute(() -> {
                        try {
                            final Conversation c = findConversationByUuid(uuid);
                            if (c != null) {
                                mNotificationService.clear(c);
                            } else {
                                mNotificationService.clear();
                            }
                            restoredFromDatabaseLatch.await();
                        } catch (InterruptedException e) {
                            Log.d(Config.LOGTAG, "unable to process clear notification");
                        }
                    });
                    break;
                case ACTION_SNOOZE:
                    mNotificationExecutor.execute(() -> {
                        final Conversation c = findConversationByUuid(uuid);
                        if (c == null) {
                            Log.d(Config.LOGTAG, "received snooze intent for unknown conversation (" + uuid + ")");
                            return;
                        }
                        c.setMutedTill(System.currentTimeMillis() + 30 * 60 * 1000);
                        mNotificationService.clear(c);
                        updateConversation(c);
                    });
                case NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED:
                    // skip Presence part.
                    break;
                case Intent.ACTION_SCREEN_ON:
                    deactivateGracePeriod();
                case Intent.ACTION_SCREEN_OFF:
                    // skip Presence part.
                    break;
                case ACTION_IDLE_PING:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        scheduleNextIdlePing();
                    }
                    break;
            }
        }
        synchronized (this) {
            WakeLockHelper.acquire(wakeLock);
            boolean pingNow = ConnectivityManager.CONNECTIVITY_ACTION.equals(action) || (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL > 0 && ACTION_POST_CONNECTIVITY_CHANGE.equals(action));
            final HashSet<Account> pingCandidates = new HashSet<>();
            final String androidId = PhoneHelper.getAndroidId(this);
            for (Account account : accounts) {
                final boolean pushWasMeantForThisAccount = CryptoHelper.getAccountFingerprint(account, androidId).equals(pushedAccountHash);
                pingNow |= processAccountState(account,
                        interactive,
                        "ui".equals(action),
                        pushWasMeantForThisAccount,
                        pingCandidates);
                if (pushWasMeantForThisAccount && pushedChannelHash != null) {
                    // skip Multi Mode Chat
                }
            }
            if (pingNow) {
                for (Account account : pingCandidates) {
                    final boolean lowTimeout = isInLowPingTimeoutMode(account);
                    account.getXmppConnection().sendPing();
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + " send ping (action=" + action + ",lowTimeout=" + Boolean.toString(lowTimeout) + ")");
                    scheduleWakeUpCall(lowTimeout ? Config.LOW_PING_TIMEOUT : Config.PING_TIMEOUT, account.getUuid().hashCode());
                }
            }
            WakeLockHelper.release(wakeLock);
        }
        if (SystemClock.elapsedRealtime() - mLastExpiryRun.get() >= Config.EXPIRY_INTERVAL) {
            expireOldMessages();
        }
        return START_STICKY;
    }

    private boolean processAccountState(Account account, boolean interactive, boolean isUiAction, boolean isAccountPushed, HashSet<Account> pingCandidates) {
        boolean pingNow = false;
        if (account.getStatus().isAttemptReconnect()) {
            if (!hasInternetConnection()) {
                account.setStatus(Account.State.NO_INTERNET);
                if (statusListener != null) {
                    statusListener.onStatusChanged(account);
                }
            } else {
                if (account.getStatus() == Account.State.NO_INTERNET) {
                    account.setStatus(Account.State.OFFLINE);
                    if (statusListener != null) {
                        statusListener.onStatusChanged(account);
                    }
                }
                if (account.getStatus() == Account.State.ONLINE) {
                    synchronized (mLowPingTimeoutMode) {
                        long lastReceived = account.getXmppConnection().getLastPacketReceived();
                        long lastSent = account.getXmppConnection().getLastPingSent();
                        long pingInterval = isUiAction ? Config.PING_MIN_INTERVAL * 1000 : Config.PING_MAX_INTERVAL * 1000;
                        long msToNextPing = (Math.max(lastReceived, lastSent) + pingInterval) - SystemClock.elapsedRealtime();
                        int pingTimeout = mLowPingTimeoutMode.contains(account.getJid().asBareJid()) ? Config.LOW_PING_TIMEOUT * 1000 : Config.PING_TIMEOUT * 1000;
                        long pingTimeoutIn = (lastSent + pingTimeout) - SystemClock.elapsedRealtime();
                        if (lastSent > lastReceived) {
                            if (pingTimeoutIn < 0) {
                                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": ping timeout");
                                this.reconnectAccount(account, true, interactive);
                            } else {
                                int secs = (int) (pingTimeoutIn / 1000);
                                this.scheduleWakeUpCall(secs, account.getUuid().hashCode());
                            }
                        } else {
                            pingCandidates.add(account);
                            if (isAccountPushed) {
                                pingNow = true;
                                if (mLowPingTimeoutMode.add(account.getJid().asBareJid())) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": entering low ping timeout mode");
                                }
                            } else if (msToNextPing <= 0) {
                                pingNow = true;
                            } else {
                                this.scheduleWakeUpCall((int) (msToNextPing / 1000), account.getUuid().hashCode());
                                if (mLowPingTimeoutMode.remove(account.getJid().asBareJid())) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": leaving low ping timeout mode");
                                }
                            }
                        }
                    }
                } else if (account.getStatus() == Account.State.OFFLINE) {
                    reconnectAccount(account, true, interactive);
                } else if (account.getStatus() == Account.State.CONNECTING) {
                    long secondsSinceLastConnect = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastConnect()) / 1000;
                    long secondsSinceLastDisco = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastDiscoStarted()) / 1000;
                    long discoTimeout = Config.CONNECT_DISCO_TIMEOUT - secondsSinceLastDisco;
                    long timeout = Config.CONNECT_TIMEOUT - secondsSinceLastConnect;
                    if (timeout < 0) {
                        Log.d(Config.LOGTAG, account.getJid() + ": time out during connect reconnecting (secondsSinceLast=" + secondsSinceLastConnect + ")");
                        account.getXmppConnection().resetAttemptCount(false);
                        reconnectAccount(account, true, interactive);
                    } else if (discoTimeout < 0) {
                        account.getXmppConnection().sendDiscoTimeout();
                        scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
                    } else {
                        scheduleWakeUpCall((int) Math.min(timeout, discoTimeout), account.getUuid().hashCode());
                    }
                } else {
                    if (account.getXmppConnection().getTimeToNextAttempt() <= 0) {
                        reconnectAccount(account, true, interactive);
                    }
                }
            }
        }
        return pingNow;
    }

    private void directReply(Conversation conversation, String body, final boolean dismissAfterReply) {
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            // skip Pgp Encryption Service part.
        } else {
            sendMessage(message);
            if (dismissAfterReply) {
                markRead(conversation, true);
            } else {
                mNotificationService.pushFromDirectReply(message);
            }
        }
    }

    private boolean manuallyChangePresence() {
        return getBooleanPreference(SettingsActivity.MANUALLY_CHANGE_PRESENCE, R.bool.manually_change_presence);
    }

    private boolean awayWhenScreenOff() {
        return getBooleanPreference(SettingsActivity.AWAY_WHEN_SCREEN_IS_OFF, R.bool.away_when_screen_off);
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    public boolean isInteractive() {
        try {
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            final boolean isScreenOn;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                isScreenOn = pm.isScreenOn();
            } else {
                isScreenOn = pm.isInteractive();
            }
            return isScreenOn;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void resetAllAttemptCounts(boolean reallyAll, boolean retryImmediately) {
        Log.d(Config.LOGTAG, "resetting all attempt counts");
        for (Account account : accounts) {
            if (account.hasErrorStatus() || reallyAll) {
                final XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    connection.resetAttemptCount(retryImmediately);
                }
            }
            if (account.setShowErrorNotification(true)) {
                mDatabaseWriterExecutor.execute(() -> databaseBackend.updateAccount(account));
            }
        }
        mNotificationService.updateErrorNotification();
    }

    private void expireOldMessages() {
        expireOldMessages(false);
    }

    public void expireOldMessages(final boolean resetHasMessagesLeftOnServer) {
        mLastExpiryRun.set(SystemClock.elapsedRealtime());
        mDatabaseWriterExecutor.execute(() -> {
            long timestamp = getAutomaticMessageDeletionDate();
            if (timestamp > 0) {
                databaseBackend.expireOldMessages(timestamp);
                synchronized (XmppConnectionService.this.conversations) {
                    for (Conversation conversation : XmppConnectionService.this.conversations) {
                        conversation.expireOldMessages(timestamp);
                        if (resetHasMessagesLeftOnServer) {
                            conversation.messagesLoaded.set(true);
                            conversation.setHasMessagesLeftOnServer(true);
                        }
                    }
                }
                updateConversationUi();
            }
        });
    }

    public boolean hasInternetConnection() {
        final ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try {
            final NetworkInfo activeNetwork = cm == null ? null : cm.getActiveNetworkInfo();
            return activeNetwork != null && (activeNetwork.isConnected() || activeNetwork.getType() == ConnectivityManager.TYPE_ETHERNET);
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to check for internet connection", e);
            return true; //if internet connection can not be checked it is probably best to just try
        }
    }

    @SuppressLint("TrulyRandom")
    @Override
    public void onCreate() {
        if (Compatibility.runsTwentySix()) {
            mNotificationService.initializeChannels();
        }

        // omit initiate Muclumbus service part for now

        mForceDuringOnCreate.set(Compatibility.runsAndTargetsTwentySix(this));
        toggleForegroundService();
        this.destroyed = false;

        // omit Load Omemo setting

        ExceptionHelper.init(getApplicationContext());
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1);
        } catch (Throwable throwable) {
            Log.e(Config.LOGTAG, "unable to initialize security provider", throwable);
        }
        Resolver.init(this);
        this.mRandom = new SecureRandom();
        updateMemorizingTrustmanager();
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        this.mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(final String key, final Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        if (mLastActivity == 0) {
            mLastActivity = getPreferences().getLong(SETTING_LAST_ACTIVITY_TS, System.currentTimeMillis());
        }

        Log.d(Config.LOGTAG, "initializing database...");
        this.databaseBackend = DatabaseBackend.getInstance(getApplicationContext());
        Log.d(Config.LOGTAG, "restoring accounts...");
        this.accounts = databaseBackend.getAccounts();
        final SharedPreferences.Editor editor = getPreferences().edit();
        if (this.accounts.size() == 0 && Arrays.asList("Sony", "Sony Ericsson").contains(Build.MANUFACTURER)) {
            editor.putBoolean(SettingsActivity.KEEP_FOREGROUND_SERVICE, true);
            Log.d(Config.LOGTAG, Build.MANUFACTURER + " is on blacklist. enabling foreground service");
        }
        final boolean hasEnabledAccounts = hasEnabledAccounts();
        editor.putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts).apply();
        editor.apply();

        // omit Toggle SetProfilePictureActivity part for now

        restoreFromDatabase();

        // omit initialization about contact watching as in Plutonem there is need to access the personal phone contacts

        // we don't need to circle around exchangeable file so perhaps we could omit file Watching for now

        // we don't want anything stay staggering with Pgp or Axolotl so cut the open connection part

        this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Plutonem Xmpp:Service");

        toggleForegroundService();
        updateUnreadCountBadge();
        toggleScreenEventReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            scheduleNextIdlePing();
            IntentFilter intentFilter = new IntentFilter();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            }
            intentFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            registerReceiver(this.mInternalEventReceiver, intentFilter);
        }
        mForceDuringOnCreate.set(false);
        toggleForegroundService();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_COMPLETE) {
            Log.d(Config.LOGTAG, "clear cache due to low memory");
            getBitmapCache().evictAll();
        }
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(this.mInternalEventReceiver);
        } catch (IllegalArgumentException e) {
            // ignored
        }
        destroyed = false;

        // omit the part to quit Watching file

        super.onDestroy();
    }

    public void toggleScreenEventReceiver() {
        if (awayWhenScreenOff() && !manuallyChangePresence()) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(this.mInternalScreenEventReceiver, filter);
        } else {
            try {
                unregisterReceiver(this.mInternalScreenEventReceiver);
            } catch (IllegalArgumentException e) {
                //ignored
            }
        }
    }

    public void toggleForegroundService() {
        toggleForegroundService(false);
    }

    private void toggleForegroundService(boolean force) {
        final boolean status;
        if (force || mForceDuringOnCreate.get() || mForceForegroundService.get() || (Compatibility.keepForegroundService(this) && hasEnabledAccounts())) {
            final Notification notification = this.mNotificationService.createForegroundNotification();
            startForeground(NotificationService.FOREGROUND_NOTIFICATION_ID, notification);
            if (!mForceForegroundService.get()) {
                mNotificationService.notify(NotificationService.FOREGROUND_NOTIFICATION_ID, notification);
            }
            status = true;
        } else {
            stopForeground(true);
            status = false;
        }
        if (!mForceForegroundService.get()) {
            mNotificationService.dismissForcedForegroundNotification(); //if the channel was changed the previous call might fail
        }
        Log.d(Config.LOGTAG, "ForegroundService: " + (status ? "on" : "off"));
    }

    public boolean foregroundNotificationNeedsUpdatingWhenErrorStateChanges() {
        return !mForceForegroundService.get() && Compatibility.keepForegroundService(this) && hasEnabledAccounts();
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if ((Compatibility.keepForegroundService(this) && hasEnabledAccounts()) || mForceForegroundService.get()) {
            Log.d(Config.LOGTAG, "ignoring onTaskRemoved because foreground service is activated");
        } else {
            this.logoutAndSave(false);
        }
    }

    private void logoutAndSave(boolean stop) {

        // in the case that we ignore all about Roster so this place we simply omit Database Roster Write part

        int activeAccounts = 0;
        for (final Account account : accounts) {
            if (account.getStatus() != Account.State.DISABLED) {
                activeAccounts++;
            }
            if (account.getXmppConnection() != null) {
                new Thread(() -> disconnect(account, false)).start();
            }
        }
        if (stop || activeAccounts == 0) {
            Log.d(Config.LOGTAG, "good bye");
            stopSelf();
        }
    }

    private void schedulePostConnectivityChange() {
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final long triggerAtMillis = SystemClock.elapsedRealtime() + (Config.POST_CONNECTIVITY_CHANGE_PING_INTERVAL * 1000);
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction(ACTION_POST_CONNECTIVITY_CHANGE);
        try {
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 1, intent, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for post connectivity change", e);
        }
    }

    public void scheduleWakeUpCall(int seconds, int requestCode) {
        final long timeToWake = SystemClock.elapsedRealtime() + (seconds < 0 ? 1 : seconds + 1) * 1000;
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction("ping");
        try {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, 0);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (RuntimeException e) {
            Log.e(Config.LOGTAG, "unable to schedule alarm for ping", e);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void scheduleNextIdlePing() {
        final long timeToWake = SystemClock.elapsedRealtime() + (Config.IDLE_PING_INTERVAL * 1000);
        final AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        final Intent intent = new Intent(this, EventReceiver.class);
        intent.setAction(ACTION_IDLE_PING);
        try {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeToWake, pendingIntent);
        } catch (RuntimeException e) {
            Log.d(Config.LOGTAG, "unable to schedule alarm for idle ping", e);
        }
    }

    public XmppConnection createConnection(final Account account) {
        final XmppConnection connection = new XmppConnection(account, this);
        connection.setOnMessagePacketReceivedListener(this.mMessageParser);
        connection.setOnStatusChangedListener(this.statusListener);
        connection.setOnPresencePacketReceivedListener(this.mPresenceParser);
        connection.setOnUnregisteredIqPacketReceivedListener(this.mIqParser);
        connection.setOnJinglePacketReceivedListener(this.jingleListener);
        connection.setOnBindListener(this.mOnBindListener);
        connection.setOnMessageAcknowledgeListener(this.mOnMessageAcknowledgedListener);
        connection.addOnAdvancedStreamFeaturesAvailableListener(this.mMessageArchiveService);
        connection.addOnAdvancedStreamFeaturesAvailableListener(this.mAvatarService);
        AxolotlService axolotlService = account.getAxolotlService();
        if (axolotlService != null) {
            connection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
        }
        return connection;
    }

    public void sendChatState(Conversation conversation) {
        if (sendChatStates()) {
            MessagePacket packet = mMessageGenerator.generateChatState(conversation);
            sendMessagePacket(conversation.getAccount(), packet);
        }
    }

    public void sendMessage(final Message message) {
        sendMessage(message, false, false);
    }

    private void sendMessage(final Message message, final boolean resend, final boolean delay) {
        final Account account = message.getConversation().getAccount();
        if (account.setShowErrorNotification(true)) {
            databaseBackend.updateAccount(account);
            mNotificationService.updateErrorNotification();
        }
        final Conversation conversation = (Conversation) message.getConversation();
        account.deactivateGracePeriod();

        if (QuickConversationsService.isQuicksy() && conversation.getMode() == Conversation.MODE_SINGLE) {
            // skip Quick Conversation Service part.
        }

        MessagePacket packet = null;
        final boolean addToConversation = (conversation.getMode() != Conversation.MODE_MULTI
                || !Patches.BAD_MUC_REFLECTION.contains(account.getServerIdentity()))
                && !message.edited();
        boolean saveInDb = addToConversation;
        message.setStatus(Message.STATUS_WAITING);

        // skip Multi Mode Chat part.

        final boolean inProgressJoin = false;

        if (account.isOnlineAndConnected() && !inProgressJoin) {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_NONE:
                    if (message.needsUploading()) {
                        // omit by now
                    } else {
                        packet = mMessageGenerator.generateChat(message);
                    }
                    break;
                case Message.ENCRYPTION_PGP:
                case Message.ENCRYPTION_DECRYPTED:
                    // omit by now
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    // omit by now
                    break;
            }
            if (packet != null) {
                if (account.getXmppConnection().getFeatures().sm()
                        || (conversation.getMode() == Conversation.MODE_MULTI && message.getCounterpart().isBareJid())) {
                    message.setStatus(Message.STATUS_UNSEND);
                } else {
                    message.setStatus(Message.STATUS_SEND);
                }
            }
        } else {
            switch (message.getEncryption()) {
                case Message.ENCRYPTION_DECRYPTED:
                    // omit by now
                    break;
                case Message.ENCRYPTION_AXOLOTL:
                    // omit by now
                    break;
            }
        }

        boolean mucMessage = conversation.getMode() == Conversation.MODE_MULTI && !message.isPrivateMessage();
        if (mucMessage) {
            // omit by now
        }

        if (resend) {
            if (packet != null && addToConversation) {
                if (account.getXmppConnection().getFeatures().sm() || mucMessage) {
                    markMessage(message, Message.STATUS_UNSEND);
                } else {
                    markMessage(message, Message.STATUS_SEND);
                }
            }
        } else {
            if (addToConversation) {
                conversation.add(message);
            }
            if (saveInDb) {
                databaseBackend.createMessage(message);
            } else if (message.edited()) {
                if (!databaseBackend.updateMessage(message, message.getEditedId())) {
                    Log.e(Config.LOGTAG, "error updated message in DB after edit");
                }
            }
            updateConversationUi();
        }
        if (packet != null) {
            if (delay) {
                mMessageGenerator.addDelay(packet, message.getTimeSent());
            }
            if (conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
                if (this.sendChatStates()) {
                    packet.addChild(ChatState.toElement(conversation.getOutgoingChatState()));
                }
            }
            sendMessagePacket(account, packet);
        }
    }

    private void sendUnsentMessages(final Conversation conversation) {
        conversation.findWaitingMessages(message -> resendMessage(message, true));
    }

    public void resendMessage(final Message message, final boolean delay) {
        sendMessage(message, true, delay);
    }

    private void restoreFromDatabase() {
        synchronized (this.conversations) {
            final Map<String, Account> accountLookupTable = new Hashtable<>();
            for (Account account : this.accounts) {
                accountLookupTable.put(account.getUuid(), account);
            }
            Log.d(Config.LOGTAG, "restoring conversations...");
            final long startTimeConversationsRestore = SystemClock.elapsedRealtime();
            this.conversations.addAll(databaseBackend.getConversations(Conversation.STATUS_AVAILABLE));
            for (Iterator<Conversation> iterator = conversations.listIterator(); iterator.hasNext(); ) {
                Conversation conversation = iterator.next();
                Account account = accountLookupTable.get(conversation.getAccountUuid());
                if (account != null) {
                    conversation.setAccount(account);
                } else {
                    Log.e(Config.LOGTAG, "unable to restore Conversations with " + conversation.getJid());
                    iterator.remove();
                }
            }
            long diffConversationsRestore = SystemClock.elapsedRealtime() - startTimeConversationsRestore;
            Log.d(Config.LOGTAG, "finished restoring conversations in " + diffConversationsRestore + "ms");
            Runnable runnable = () -> {
                long deletionDate = getAutomaticMessageDeletionDate();
                mLastExpiryRun.set(SystemClock.elapsedRealtime());
                if (deletionDate > 0) {
                    Log.d(Config.LOGTAG, "deleting messages that are older than " + AbstractGenerator.getTimestamp(deletionDate));
                    databaseBackend.expireOldMessages(deletionDate);
                }
                // omit Restoring Rostor part for now
                for (Account account : accounts) {
                    account.initAccountServices(XmppConnectionService.this);
                }
                getBitmapCache().evictAll();
                //omit loading phone contacts part for now
                Log.d(Config.LOGTAG, "restoring messages...");
                final long startMessageRestore = SystemClock.elapsedRealtime();
                final Conversation quickLoad = QuickLoader.get(this.conversations);
                if (quickLoad != null) {
                    restoreMessages(quickLoad);
                    updateConversationUi();
                    final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
                    Log.d(Config.LOGTAG, "quickly restored " + quickLoad.getName() + " after " + diffMessageRestore + "ms");
                }
                for (Conversation conversation : this.conversations) {
                    if (quickLoad != conversation) {
                        restoreMessages(conversation);
                    }
                }
                mNotificationService.finishBacklog(false);
                restoredFromDatabaseLatch.countDown();
                final long diffMessageRestore = SystemClock.elapsedRealtime() - startMessageRestore;
                Log.d(Config.LOGTAG, "finished restoring messages in " + diffMessageRestore + "ms");
                updateConversationUi();
            };
            mDatabaseReaderExecutor.execute(runnable); // will contain one write command (expiry) but that's fine
        }
    }

    private void restoreMessages(Conversation conversation) {
        conversation.addAll(0, databaseBackend.getMessages(conversation, Config.PAGE_SIZE));
        conversation.findUnsentTextMessages(message -> markMessage(message, Message.STATUS_WAITING));
        conversation.findUnreadMessages(message -> mNotificationService.pushFromBacklog(message));
    }

    public List<Conversation> getConversations() {
        return this.conversations;
    }

    public void loadMoreMessages(final Conversation conversation, final long timestamp, final OnMoreMessagesLoaded callback) {
        if (XmppConnectionService.this.getMessageArchiveService().queryInProgress(conversation, callback)) {
            return;
        } else if (timestamp == 0) {
            return;
        }
        Log.d(Config.LOGTAG, "load more messages for " + conversation.getName() + " prior to " + MessageGenerator.getTimestamp(timestamp));
        final Runnable runnable = () -> {
            final Account account = conversation.getAccount();
            List<Message> messages = databaseBackend.getMessages(conversation, 50, timestamp);
            if (messages.size() > 0) {
                conversation.addAll(0, messages);
                callback.onMoreMessagesLoaded(messages.size(), conversation);
            } else if (conversation.hasMessagesLeftOnServer()
                    && account.isOnlineAndConnected()
                    && conversation.getLastClearHistory().getTimestamp() == 0) {
                final boolean mamAvailable;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    mamAvailable = account.getXmppConnection().getFeatures().mam() && !conversation.getContact().isBlocked();
                } else {
                    // currently we ignore consideration about Multi User Chat sequence.
                    mamAvailable = false;
                }
                if (mamAvailable) {
                    MessageArchiveService.Query query = getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
                    if (query != null) {
                        query.setCallback(callback);
                        callback.informUser(R.string.fetching_history_from_server);
                    } else {
                        callback.informUser(R.string.not_fetching_history_retention_period);
                    }

                }
            }
        };
        mDatabaseReaderExecutor.execute(runnable);
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    public Conversation find(final Iterable<Conversation> haystack, final Account account, final Jid jid) {
        if (jid == null) {
            return null;
        }
        for (final Conversation conversation : haystack) {
            if ((account == null || conversation.getAccount() == account)
                    && (conversation.getJid().asBareJid().equals(jid.asBareJid()))) {
                return conversation;
            }
        }
        return null;
    }

    public boolean isConversationStillOpen(final Conversation conversation) {
        synchronized (this.conversations) {
            for (Conversation current : this.conversations) {
                if (current == conversation) {
                    return true;
                }
            }
        }
        return false;
    }

    public Conversation findOrCreateConversation(Account account, Jid jid, boolean muc, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, false, async);
    }

    public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final boolean async) {
        return this.findOrCreateConversation(account, jid, muc, joinAfterCreate, null, async);
    }

    public Conversation findOrCreateConversation(final Account account, final Jid jid, final boolean muc, final boolean joinAfterCreate, final MessageArchiveService.Query query, final boolean async) {
        synchronized (this.conversations) {
            Conversation conversation = find(account, jid);
            if (conversation != null) {
                return conversation;
            }
            conversation = databaseBackend.findConversation(account, jid);
            final boolean loadMessagesFromDb;
            if (conversation != null) {
                conversation.setStatus(Conversation.STATUS_AVAILABLE);
                conversation.setAccount(account);
                if (muc) {
                    // omit this part for now
                } else {
                    conversation.setMode(Conversation.MODE_SINGLE);
                    conversation.setContactJid(jid.asBareJid());
                }
                databaseBackend.updateConversation(conversation);
                loadMessagesFromDb = conversation.messagesLoaded.compareAndSet(true, false);
            } else {
                String conversationName;
                Contact contact = account.getRoster().getContact(jid);
                if (contact != null) {
                    conversationName = contact.getDisplayName();
                } else {
                    conversationName = jid.getLocal();
                }
                if (muc) {
                    // omit this part for now
                } else {
                    conversation = new Conversation(conversationName, account, jid.asBareJid(),
                            Conversation.MODE_SINGLE);
                }
                this.databaseBackend.createConversation(conversation);
                loadMessagesFromDb = false;
            }
            final Conversation c = conversation;
            final Runnable runnable = () -> {
                if (loadMessagesFromDb) {
                    c.addAll(0, databaseBackend.getMessages(c, Config.PAGE_SIZE));
                    updateConversationUi();
                    c.messagesLoaded.set(true);
                }
                if (account.getXmppConnection() != null
                        && !c.getContact().isBlocked()
                        && account.getXmppConnection().getFeatures().mam()
                        && !muc) {
                    if (query == null) {
                        mMessageArchiveService.query(c);
                    } else {
                        if (query.getConversation() == null) {
                            mMessageArchiveService.query(c, query.getStart(), query.isCatchup());
                        }
                    }
                }
                if (joinAfterCreate) {
                    // omit this part for now
                }
            };
            if (async) {
                mDatabaseReaderExecutor.execute(runnable);
            } else {
                runnable.run();
            }
            this.conversations.add(conversation);
            updateConversationUi();
            return conversation;
        }
    }

    public void createAccount(final Account account) {
        account.initAccountServices(this);
        databaseBackend.createAccount(account);
        this.accounts.add(account);
        this.reconnectAccountInBackground(account);
        updateAccountUi();
        syncEnabledAccountSetting();
        toggleForegroundService();
    }

    private void syncEnabledAccountSetting() {
        final boolean hasEnabledAccounts = hasEnabledAccounts();
        getPreferences().edit().putBoolean(EventReceiver.SETTING_ENABLED_ACCOUNTS, hasEnabledAccounts).apply();
        // leave toggle Profile Picture Activity blank here as we are now sure whether we need it.
    }

    public boolean updateAccount(final Account account) {
        if (databaseBackend.updateAccount(account)) {
            account.setShowErrorNotification(true);
            this.statusListener.onStatusChanged(account);
            databaseBackend.updateAccount(account);
            reconnectAccountInBackground(account);
            updateAccountUi();
            getNotificationService().updateErrorNotification();
            toggleForegroundService();
            syncEnabledAccountSetting();
            // omit clean cache for channel discovery service for now
            return true;
        } else {
            return false;
        }
    }

    public void deleteAccount(final Account account) {
        final boolean connected = account.getStatus() == Account.State.ONLINE;
        synchronized (this.conversations) {
            if (connected) {
                // currently we don't want be paired with Axolotl Service.
            }
            for (final Conversation conversation : conversations) {
                if (conversation.getAccount() == account) {
                    // currently we don't need to deal with the Multi Mode Conversation logic
                    conversations.remove(conversation);
                    mNotificationService.clear(conversation);
                }
            }
            if (account.getXmppConnection() != null) {
                new Thread(() -> disconnect(account, !connected)).start();
            }
            final Runnable runnable = () -> {
                if (!databaseBackend.deleteAccount(account)) {
                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": unable to delete account");
                }
            };
            mDatabaseWriterExecutor.execute(runnable);
            this.accounts.remove(account);
            // Skip the Roster Sync Cleaning part by now.
            updateAccountUi();
            mNotificationService.updateErrorNotification();
            syncEnabledAccountSetting();
            toggleForegroundService();
        }
    }

    public void setOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnConversationUpdates.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as ConversationListChangedListener");
            }
            this.mNotificationService.setIsInForeground(this.mOnConversationUpdates.size() > 0);
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnConversationListChangedListener(OnConversationUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnConversationUpdates.remove(listener);
            this.mNotificationService.setIsInForeground(this.mOnConversationUpdates.size() > 0);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnShowErrorToastListener(OnShowErrorToast listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnShowErrorToasts.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnShowErrorToastListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnShowErrorToastListener(OnShowErrorToast onShowErrorToast) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnShowErrorToasts.remove(onShowErrorToast);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public void setOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            remainingListeners = checkListeners();
            if (!this.mOnAccountUpdates.add(listener)) {
                Log.w(Config.LOGTAG, listener.getClass().getName() + " is already registered as OnAccountListChangedtListener");
            }
        }
        if (remainingListeners) {
            switchToForeground();
        }
    }

    public void removeOnAccountListChangedListener(OnAccountUpdate listener) {
        final boolean remainingListeners;
        synchronized (LISTENER_LOCK) {
            this.mOnAccountUpdates.remove(listener);
            remainingListeners = checkListeners();
        }
        if (remainingListeners) {
            switchToBackground();
        }
    }

    public boolean checkListeners() {
        return (this.mOnAccountUpdates.size() == 0
                && this.mOnConversationUpdates.size() == 0
                && this.mOnRosterUpdates.size() == 0
                && this.mOnCaptchaRequested.size() == 0
                && this.mOnMucRosterUpdate.size() == 0
                && this.mOnUpdateBlocklist.size() == 0
                && this.mOnShowErrorToasts.size() == 0
                && this.mOnKeyStatusUpdated.size() == 0);
    }

    private void switchToForeground() {
        final boolean broadcastLastActivity = broadcastLastActivity();
        for (Conversation conversation : getConversations()) {
            // ignore Multi part check logic
            conversation.setIncomingChatState(Config.DEFAULT_CHAT_STATE);
        }
        for (Account account : getAccounts()) {
            if (account.getStatus() == Account.State.ONLINE) {
                account.deactivateGracePeriod();
                final XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    if (connection.getFeatures().csi()) {
                        connection.sendActive();
                    }
                    if (broadcastLastActivity) {
                        sendPresence(account, false); //send new presence but don't include idle because we are not
                    }
                }
            }
        }
        Log.d(Config.LOGTAG, "app switched into foreground");
    }

    private void switchToBackground() {
        final boolean broadcastLastActivity = broadcastLastActivity();
        if (broadcastLastActivity) {
            mLastActivity = System.currentTimeMillis();
            final SharedPreferences.Editor editor = getPreferences().edit();
            editor.putLong(SETTING_LAST_ACTIVITY_TS, mLastActivity);
            editor.apply();
        }
        for (Account account : getAccounts()) {
            if (account.getStatus() == Account.State.ONLINE) {
                XmppConnection connection = account.getXmppConnection();
                if (connection != null) {
                    if (broadcastLastActivity) {
                        sendPresence(account, true);
                    }
                    if (connection.getFeatures().csi()) {
                        connection.sendInactive();
                    }
                }
            }
        }
        this.mNotificationService.setIsInForeground(false);
        Log.d(Config.LOGTAG, "app switched into background");
    }

    private boolean hasEnabledAccounts() {
        if (this.accounts == null) {
            return false;
        }
        for (Account account : this.accounts) {
            if (account.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    public void pushNodeConfiguration(Account account, final String node, final Bundle options, final OnConfigurationPushed callback) {
        pushNodeConfiguration(account, account.getJid().asBareJid(), node, options, callback);
    }

    public void pushNodeConfiguration(Account account, final Jid jid, final String node, final Bundle options, final OnConfigurationPushed callback) {
        Log.d(Config.LOGTAG, "pushing node configuration");
        sendIqPacket(account, mIqGenerator.requestPubsubConfiguration(jid, node), new OnIqPacketReceived() {
            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    Element pubsub = packet.findChild("pubsub", "http://jabber.org/protocol/pubsub#owner");
                    Element configuration = pubsub == null ? null : pubsub.findChild("configure");
                    Element x = configuration == null ? null : configuration.findChild("x", Namespace.DATA);
                    if (x != null) {
                        Data data = Data.parse(x);
                        data.submit(options);
                        sendIqPacket(account, mIqGenerator.publishPubsubConfiguration(jid, node, data), new OnIqPacketReceived() {
                            @Override
                            public void onIqPacketReceived(Account account, IqPacket packet) {
                                if (packet.getType() == IqPacket.TYPE.RESULT && callback != null) {
                                    Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": successfully changed node configuration for node " + node);
                                    callback.onPushSucceeded();
                                } else if (packet.getType() == IqPacket.TYPE.ERROR && callback != null) {
                                    callback.onPushFailed();
                                }
                            }
                        });
                    } else if (callback != null) {
                        callback.onPushFailed();
                    }
                } else if (packet.getType() == IqPacket.TYPE.ERROR && callback != null) {
                    callback.onPushFailed();
                }
            }
        });
    }

    private void disconnect(Account account, boolean force) {
        if ((account.getStatus() == Account.State.ONLINE)
                || (account.getStatus() == Account.State.DISABLED)) {
            final XmppConnection connection = account.getXmppConnection();
            if (!force) {
                List<Conversation> conversations = getConversations();
                for (Conversation conversation : conversations) {
                    if (conversation.getAccount() == account) {
                        if (conversation.getMode() == Conversation.MODE_MULTI) {
                            // omit by now
                        }
                    }
                }
                // omit Sending offline presence part for now
            }
            connection.disconnect(force);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void checkForAvatar(Account account, final UiCallback<Avatar> callback) {
        // skip Fetch Avatar part.
        callback.error(0, null);
    }

    public void updateConversation(final Conversation conversation) {
        mDatabaseWriterExecutor.execute(() -> databaseBackend.updateConversation(conversation));
    }

    private void reconnectAccount(final Account account, final boolean force, final boolean interactive) {
        synchronized (account) {
            XmppConnection connection = account.getXmppConnection();
            if (connection == null) {
                connection = createConnection(account);
                account.setXmppConnection(connection);
            }
            boolean hasInternet = hasInternetConnection();
            if (account.isEnabled() && hasInternet) {
                if (!force) {
                    disconnect(account, false);
                }
                Thread thread = new Thread(connection);
                connection.setInteractive(interactive);
                connection.prepareNewConnection();
                connection.interrupt();
                thread.start();
                scheduleWakeUpCall(Config.CONNECT_DISCO_TIMEOUT, account.getUuid().hashCode());
            } else {
                disconnect(account, force || account.getTrueStatus().isError() || !hasInternet);
                // omit Clear presences in Roster part for now
                connection.resetEverything();
                // omit Reset brokeness of axolotl service part for now
                if (!hasInternet) {
                    account.setStatus(Account.State.NO_INTERNET);
                }
            }
        }
    }

    public void reconnectAccountInBackground(final Account account) {
        new Thread(() -> reconnectAccount(account, false, true)).start();
    }

    public void resetSendingToWaiting(Account account) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account) {
                conversation.findUnsentTextMessages(message -> markMessage(message, Message.STATUS_WAITING));
            }
        }
    }

    public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status) {
        return markMessage(account, recipient, uuid, status, null);
    }

    public Message markMessage(final Account account, final Jid recipient, final String uuid, final int status, String errorMessage) {
        if (uuid == null) {
            return null;
        }
        for (Conversation conversation : getConversations()) {
            if (conversation.getJid().asBareJid().equals(recipient) && conversation.getAccount() == account) {
                final Message message = conversation.findSentMessageWithUuidOrRemoteId(uuid);
                if (message != null) {
                    markMessage(message, status, errorMessage);
                }
                return message;
            }
        }
        return null;
    }

    public boolean markMessage(Conversation conversation, String uuid, int status, String serverMessageId) {
        if (uuid == null) {
            return false;
        } else {
            Message message = conversation.findSentMessageWithUuid(uuid);
            if (message != null) {
                if (message.getServerMsgId() == null) {
                    message.setServerMsgId(serverMessageId);
                }
                markMessage(message, status);
                return true;
            } else {
                return false;
            }
        }
    }

    public void markMessage(Message message, int status) {
        markMessage(message, status, null);
    }

    public void markMessage(Message message, int status, String errorMessage) {
        final int oldStatus = message.getStatus();
        if (status == Message.STATUS_SEND_FAILED && (oldStatus == Message.STATUS_SEND_RECEIVED || oldStatus == Message.STATUS_SEND_DISPLAYED)) {
            return;
        }
        if (status == Message.STATUS_SEND_RECEIVED && oldStatus == Message.STATUS_SEND_DISPLAYED) {
            return;
        }
        message.setErrorMessage(errorMessage);
        message.setStatus(status);
        databaseBackend.updateMessage(message, false);
        updateConversationUi();
    }

    private SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public long getAutomaticMessageDeletionDate() {
        final long timeout = getLongPreference(SettingsActivity.AUTOMATIC_MESSAGE_DELETION, R.integer.automatic_message_deletion);
        return timeout == 0 ? timeout : (System.currentTimeMillis() - (timeout * 1000));
    }

    public long getLongPreference(String name, @IntegerRes int res) {
        long defaultValue = getResources().getInteger(res);
        try {
            return Long.parseLong(getPreferences().getString(name, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanPreference(String name, @BoolRes int res) {
        return getPreferences().getBoolean(name, getResources().getBoolean(res));
    }

    public boolean confirmMessages() {
        return getBooleanPreference("confirm_messages", R.bool.confirm_messages);
    }

    public boolean allowMessageCorrection() {
        return getBooleanPreference("allow_message_correction", R.bool.allow_message_correction);
    }

    public boolean broadcastLastActivity() {
        return getBooleanPreference(SettingsActivity.BROADCAST_LAST_ACTIVITY, R.bool.last_activity);
    }

    public boolean sendChatStates() {
        return getBooleanPreference("chat_states", R.bool.chat_states);
    }

    public boolean useTorToConnect() {
        return QuickConversationsService.isConversations() && getBooleanPreference("use_tor", R.bool.use_tor);
    }

    public boolean showExtendedConnectionOptions() {
        return QuickConversationsService.isConversations() && getBooleanPreference("show_connection_options", R.bool.show_connection_options);
    }

    public int unreadCount() {
        int count = 0;
        for (Conversation conversation : getConversations()) {
            count += conversation.unreadCount();
        }
        return count;
    }

    private <T> List<T> threadSafeList(Set<T> set) {
        synchronized (LISTENER_LOCK) {
            return set.size() == 0 ? Collections.emptyList() : new ArrayList<>(set);
        }
    }

    public void updateConversationUi() {
        for (OnConversationUpdate listener : threadSafeList(this.mOnConversationUpdates)) {
            listener.onConversationUpdate();
        }
    }

    public void updateAccountUi() {
        for (OnAccountUpdate listener : threadSafeList(this.mOnAccountUpdates)) {
            listener.onAccountUpdate();
        }
    }

    public void updateRosterUi() {
        for (OnRosterUpdate listener : threadSafeList(this.mOnRosterUpdates)) {
            listener.onRosterUpdate();
        }
    }

    public void keyStatusUpdated(AxolotlService.FetchStatus report) {
        for (OnKeyStatusUpdated listener : threadSafeList(this.mOnKeyStatusUpdated)) {
            listener.onKeyStatusUpdated(report);
        }
    }

    public Account findAccountByJid(final Jid accountJid) {
        for (Account account : this.accounts) {
            if (account.getJid().asBareJid().equals(accountJid.asBareJid())) {
                return account;
            }
        }
        return null;
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public boolean markRead(final Conversation conversation, boolean dismiss) {
        return markRead(conversation, null, dismiss).size() > 0;
    }

    public void markRead(final Conversation conversation) {
        markRead(conversation, null, true);
    }

    public List<Message> markRead(final Conversation conversation, String upToUuid, boolean dismiss) {
        if (dismiss) {
            mNotificationService.clear(conversation);
        }
        final List<Message> readMessages = conversation.markRead(upToUuid);
        if (readMessages.size() > 0) {
            Runnable runnable = () -> {
                for (Message message : readMessages) {
                    databaseBackend.updateMessage(message, false);
                }
            };
            mDatabaseWriterExecutor.execute(runnable);
            updateUnreadCountBadge();
            return readMessages;
        } else {
            return readMessages;
        }
    }

    public synchronized void updateUnreadCountBadge() {
        int count = unreadCount();
        if (unreadCount != count) {
            Log.d(Config.LOGTAG, "update unread count to " + count);
            if (count > 0) {
                ShortcutBadger.applyCount(getApplicationContext(), count);
            } else {
                ShortcutBadger.removeCount(getApplicationContext());
            }
            unreadCount = count;
        }
    }

    public void sendReadMarker(final Conversation conversation, String upToUuid) {
        // set Multi User Chat type to false by default.
        final boolean isPrivateAndNonAnonymousMuc = false;
        final List<Message> readMessages = this.markRead(conversation, upToUuid, true);
        if (readMessages.size() > 0) {
            updateConversationUi();
        }
        final Message markable = Conversation.getLatestMarkableMessage(readMessages, isPrivateAndNonAnonymousMuc);
        if (confirmMessages()
                && markable != null
                && (markable.trusted() || isPrivateAndNonAnonymousMuc)
                && markable.getRemoteMsgId() != null) {
            Log.d(Config.LOGTAG, conversation.getAccount().getJid().asBareJid() + ": sending read marker to " + markable.getCounterpart().toString());
            Account account = conversation.getAccount();
            final Jid to = markable.getCounterpart();
            // set Multi User Chat type to false by default.
            final boolean groupChat = false;
            MessagePacket packet = mMessageGenerator.confirm(account, to, markable.getRemoteMsgId(), markable.getCounterpart(), groupChat);
            this.sendMessagePacket(conversation.getAccount(), packet);
        }
    }

    public SecureRandom getRNG() {
        return this.mRandom;
    }

    public MemorizingTrustManager getMemorizingTrustManager() {
        return this.mMemorizingTrustManager;
    }

    public void setMemorizingTrustManager(MemorizingTrustManager trustManager) {
        this.mMemorizingTrustManager = trustManager;
    }

    public void updateMemorizingTrustmanager() {
        final MemorizingTrustManager tm;
        final boolean dontTrustSystemCAs = getBooleanPreference("dont_trust_system_cas", R.bool.dont_trust_system_cas);
        if (dontTrustSystemCAs) {
            tm = new MemorizingTrustManager(getApplicationContext(), null);
        } else {
            tm = new MemorizingTrustManager(getApplicationContext());
        }
        setMemorizingTrustManager(tm);
    }

    public LruCache<String, Bitmap> getBitmapCache() {
        return this.mBitmapCache;
    }

    public void sendMessagePacket(Account account, MessagePacket packet) {
        XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendMessagePacket(packet);
        }
    }

    public void sendIqPacket(final Account account, final IqPacket packet, final OnIqPacketReceived callback) {
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.sendIqPacket(packet, callback);
        } else if (callback != null) {
            callback.onIqPacketReceived(account, new IqPacket(IqPacket.TYPE.TIMEOUT));
        }
    }

    private void sendPresence(final Account account, final boolean includeIdleTimeStamp) {
        // we are not sure if we need to send Presence in Plutonem, so skip this part by now
    }

    private void deactivateGracePeriod() {
        for (Account account : getAccounts()) {
            account.deactivateGracePeriod();
        }
    }

    public MessageGenerator getMessageGenerator() {
        return this.mMessageGenerator;
    }

    public IqGenerator getIqGenerator() {
        return this.mIqGenerator;
    }

    public IqParser getIqParser() {
        return this.mIqParser;
    }

    public MessageArchiveService getMessageArchiveService() {
        return this.mMessageArchiveService;
    }

    public NotificationService getNotificationService() {
        return this.mNotificationService;
    }

    public void publishDisplayName(Account account) {
        String displayName = account.getDisplayName();
        final IqPacket request;
        if (TextUtils.isEmpty(displayName)) {
            request = mIqGenerator.deleteNode(Namespace.NICK);
        } else {
            request = mIqGenerator.publishNick(displayName);
        }
        mAvatarService.clear(account);
        sendIqPacket(account, request, (account1, packet) -> {
            if (packet.getType() == IqPacket.TYPE.ERROR) {
                Log.d(Config.LOGTAG, account1.getJid().asBareJid() + ": unable to modify nick name " + packet.toString());
            }
        });
    }

    public ServiceDiscoveryResult getCachedServiceDiscoveryResult(Pair<String, String> key) {
        ServiceDiscoveryResult result = discoCache.get(key);
        if (result != null) {
            return result;
        } else {
            result = databaseBackend.findDiscoveryResult(key.first, key.second);
            if (result != null) {
                discoCache.put(key, result);
            }
            return result;
        }
    }

    public boolean blindTrustBeforeVerification() {
        return getBooleanPreference(SettingsActivity.BLIND_TRUST_BEFORE_VERIFICATION, R.bool.btbv);
    }

    public ShortcutService getShortcutService() {
        return mShortcutService;
    }

    public interface OnMoreMessagesLoaded {
        void onMoreMessagesLoaded(int count, Conversation conversation);

        void informUser(int r);
    }

    public interface OnConversationUpdate {
        void onConversationUpdate();
    }

    public interface OnAccountUpdate {
        void onAccountUpdate();
    }

    public interface OnCaptchaRequested {
        void onCaptchaRequested(Account account, String id, Data data, Bitmap captcha);
    }

    public interface OnRosterUpdate {
        void onRosterUpdate();
    }

    public interface OnMucRosterUpdate {
        void onMucRosterUpdate();
    }

    public interface OnConfigurationPushed {
        void onPushSucceeded();

        void onPushFailed();
    }

    public interface OnShowErrorToast {
        void onShowErrorToast(int resId);
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    private class InternalEventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onStartCommand(intent, 0, 0);
        }
    }
}

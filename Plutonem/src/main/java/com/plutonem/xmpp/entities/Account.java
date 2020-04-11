package com.plutonem.xmpp.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Log;

import com.plutonem.Config;
import com.plutonem.android.login.R;
import com.plutonem.xmpp.crypto.axolotl.AxolotlService;
import com.plutonem.xmpp.services.AvatarService;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.utils.UIHelper;
import com.plutonem.xmpp.xmpp.XmppConnection;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import rocks.xmpp.addr.Jid;

public class Account extends AbstractEntity implements AvatarService.Avatarable {

    public static final String TABLENAME = "accounts";

    public static final String USERNAME = "username";
    public static final String SERVER = "server";
    public static final String PASSWORD = "password";
    public static final String OPTIONS = "options";
    public static final String ROSTERVERSION = "rosterversion";
    public static final String KEYS = "keys";
    public static final String AVATAR = "avatar";
    public static final String DISPLAY_NAME = "display_name";
    public static final String HOSTNAME = "hostname";
    public static final String PORT = "port";
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "status_message";
    public static final String RESOURCE = "resource";

    public static final String PINNED_MECHANISM_KEY = "pinned_mechanism";
    public static final String PRE_AUTH_REGISTRATION_TOKEN = "pre_auth_registration";

    public static final int OPTION_USETLS = 0;
    public static final int OPTION_DISABLED = 1;
    public static final int OPTION_REGISTER = 2;
    public static final int OPTION_USECOMPRESSION = 3;
    public static final int OPTION_MAGIC_CREATE = 4;
    public static final int OPTION_REQUIRES_ACCESS_MODE_CHANGE = 5;
    public static final int OPTION_LOGGED_IN_SUCCESSFULLY = 6;
    public static final int OPTION_HTTP_UPLOAD_AVAILABLE = 7;
    public static final int OPTION_UNVERIFIED = 8;
    public static final int OPTION_FIXED_USERNAME = 9;
    private static final String KEY_PGP_SIGNATURE = "pgp_signature";
    protected final JSONObject keys;
    private final Roster roster = new Roster(this);
    private final Collection<Jid> blocklist = new CopyOnWriteArraySet<>();
    public final Set<Conversation> pendingConferenceJoins = new HashSet<>();
    public final Set<Conversation> pendingConferenceLeaves = new HashSet<>();
    public final Set<Conversation> inProgressConferenceJoins = new HashSet<>();
    public final Set<Conversation> inProgressConferencePings = new HashSet<>();
    protected Jid jid;
    protected String password;
    protected int options = 0;
    protected State status = State.OFFLINE;
    private State lastErrorStatus = State.OFFLINE;
    protected String avatar;
    protected String hostname = null;
    protected int port = 5222;
    private String rosterVersion;
    private String displayName = null;
    private AxolotlService axolotlService = null;
    private XmppConnection xmppConnection = null;
    private long mEndGracePeriod = 0L;
    private Presence.Status presenceStatus = Presence.Status.ONLINE;
    private String presenceStatusMessage = null;

    public Account(final Jid jid, final String password) {
        this(java.util.UUID.randomUUID().toString(), jid,
                password, 0, null, "", null, null, null, 5222, Presence.Status.ONLINE, null);
    }

    public Account(
            final String uuid, final Jid jid,
            final String password, final int options, final String rosterVersion, final String keys,
            final String avatar, String displayName, String hostname, int port,
            final Presence.Status status, String statusMessage
    ) {
        this.uuid = uuid;
        this.jid = jid;
        this.password = password;
        this.options = options;
        this.rosterVersion = rosterVersion;
        JSONObject tmp;
        try {
            tmp = new JSONObject(keys);
        } catch (JSONException e) {
            tmp = new JSONObject();
        }
        this.keys = tmp;
        this.avatar = avatar;
        this.displayName = displayName;
        this.hostname = hostname;
        this.port = port;
        this.presenceStatus = status;
        this.presenceStatusMessage = statusMessage;
    }

    public static Account fromCursor(final Cursor cursor) {
        final Jid jid;
        try {
            String resource = cursor.getString(cursor.getColumnIndex(RESOURCE));
            jid = Jid.of(
                    cursor.getString(cursor.getColumnIndex(USERNAME)),
                    cursor.getString(cursor.getColumnIndex(SERVER)),
                    resource == null || resource.trim().isEmpty() ? null : resource);
        } catch (final IllegalArgumentException ignored) {
            Log.d(Config.LOGTAG, cursor.getString(cursor.getColumnIndex(USERNAME)) + "@" + cursor.getString(cursor.getColumnIndex(SERVER)));
            throw new AssertionError(ignored);
        }
        return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
                jid,
                cursor.getString(cursor.getColumnIndex(PASSWORD)),
                cursor.getInt(cursor.getColumnIndex(OPTIONS)),
                cursor.getString(cursor.getColumnIndex(ROSTERVERSION)),
                cursor.getString(cursor.getColumnIndex(KEYS)),
                cursor.getString(cursor.getColumnIndex(AVATAR)),
                cursor.getString(cursor.getColumnIndex(DISPLAY_NAME)),
                cursor.getString(cursor.getColumnIndex(HOSTNAME)),
                cursor.getInt(cursor.getColumnIndex(PORT)),
                Presence.Status.fromShowString(cursor.getString(cursor.getColumnIndex(STATUS))),
                cursor.getString(cursor.getColumnIndex(STATUS_MESSAGE)));
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public XmppConnection.Identity getServerIdentity() {
        if (xmppConnection == null) {
            return XmppConnection.Identity.UNKNOWN;
        } else {
            return xmppConnection.getServerIdentity();
        }
    }

    public boolean setShowErrorNotification(boolean newValue) {
        boolean oldValue = showErrorNotification();
        setKey("show_error", Boolean.toString(newValue));
        return newValue != oldValue;
    }

    public boolean showErrorNotification() {
        String key = getKey("show_error");
        return key == null || Boolean.parseBoolean(key);
    }

    public boolean isEnabled() {
        return !isOptionSet(Account.OPTION_DISABLED);
    }

    public boolean isOptionSet(final int option) {
        return ((options & (1 << option)) != 0);
    }

    public boolean setOption(final int option, final boolean value) {
        final int before = this.options;
        if (value) {
            this.options |= 1 << option;
        } else {
            this.options &= ~(1 << option);
        }
        return before != this.options;
    }

    public String getUsername() {
        return jid.getEscapedLocal();
    }

    public boolean setJid(final Jid next) {
        final Jid previousFull = this.jid;
        final Jid prev = this.jid != null ? this.jid.asBareJid() : null;
        final boolean changed = prev == null || (next != null && !prev.equals(next.asBareJid()));
        if (changed) {
            final AxolotlService oldAxolotlService = this.axolotlService;
            if (oldAxolotlService != null) {
                // apparently we don't need this logic since we are not going to support Axolotl Service right now
            }
        }
        this.jid = next;
        return next != null && !next.equals(previousFull);
    }

    public String getServer() {
        return jid.getDomain();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getHostname() {
        return this.hostname == null ? "" : this.hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public boolean isOnion() {
        final String server = getServer();
        return server != null && server.endsWith(".onion");
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public State getStatus() {
        if (isOptionSet(OPTION_DISABLED)) {
            return State.DISABLED;
        } else {
            return this.status;
        }
    }

    public void setStatus(final State status) {
        this.status = status;
        if (status.isError || status == State.ONLINE) {
            this.lastErrorStatus = status;
        }
    }

    public State getTrueStatus() {
        return this.status;
    }

    public State getLastErrorStatus() {
        return this.lastErrorStatus;
    }

    public boolean hasErrorStatus() {
        return getXmppConnection() != null
                && (getStatus().isError() || getStatus() == State.CONNECTING)
                && getXmppConnection().getAttempt() >= 3;
    }

    public String getResource() {
        return jid.getResource();
    }

    public void setResource(final String resource) {
        this.jid = this.jid.withResource(resource);
    }

    public Jid getJid() {
        return jid;
    }

    public String getKey(final String name) {
        synchronized (this.keys) {
            return this.keys.optString(name, null);
        }
    }

    public int getKeyAsInt(final String name, int defaultValue) {
        String key = getKey(name);
        try {
            return key == null ? defaultValue : Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean setKey(final String keyName, final String keyValue) {
        synchronized (this.keys) {
            try {
                this.keys.put(keyName, keyValue);
                return true;
            } catch (final JSONException e) {
                return false;
            }
        }
    }

    public boolean setPrivateKeyAlias(String alias) {
        return setKey("private_key_alias", alias);
    }

    public String getPrivateKeyAlias() {
        return getKey("private_key_alias");
    }

    @Override
    public ContentValues getContentValues() {
        final ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(USERNAME, jid.getLocal());
        values.put(SERVER, jid.getDomain());
        values.put(PASSWORD, password);
        values.put(OPTIONS, options);
        synchronized (this.keys) {
            values.put(KEYS, this.keys.toString());
        }
        values.put(ROSTERVERSION, rosterVersion);
        values.put(AVATAR, avatar);
        values.put(DISPLAY_NAME, displayName);
        values.put(HOSTNAME, hostname);
        values.put(PORT, port);
        values.put(STATUS, presenceStatus.toShowString());
        values.put(STATUS_MESSAGE, presenceStatusMessage);
        values.put(RESOURCE, jid.getResource());
        return values;
    }

    public AxolotlService getAxolotlService() {
        // simply we don't want Axolotl Encryption Service so we set this value default to null or if future plan changes it
        return axolotlService;
    }

    public void initAccountServices(final XmppConnectionService context) {
        // omit initialization of Axolotl Service and Pgp Decryption Service for now
        this.axolotlService = null;
    }

    public XmppConnection getXmppConnection() {
        return this.xmppConnection;
    }

    public void setXmppConnection(final XmppConnection connection) {
        this.xmppConnection = connection;
    }

    public boolean unsetPgpSignature() {
        synchronized (this.keys) {
            return keys.remove(KEY_PGP_SIGNATURE) != null;
        }
    }

    public Roster getRoster() {
        return this.roster;
    }

    public boolean setAvatar(final String filename) {
        if (this.avatar != null && this.avatar.equals(filename)) {
            return false;
        } else {
            this.avatar = filename;
            return true;
        }
    }

    public String getAvatar() {
        return this.avatar;
    }

    public void activateGracePeriod(final long duration) {
        if (duration > 0) {
            this.mEndGracePeriod = SystemClock.elapsedRealtime() + duration;
        }
    }

    public void deactivateGracePeriod() {
        this.mEndGracePeriod = 0L;
    }

    public boolean inGracePeriod() {
        return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
    }

    public boolean isBlocked(final ListItem contact) {
        final Jid jid = contact.getJid();
        return jid != null && (blocklist.contains(jid.asBareJid()) || blocklist.contains(Jid.ofDomain(jid.getDomain())));
    }

    public boolean isBlocked(final Jid jid) {
        return jid != null && blocklist.contains(jid.asBareJid());
    }


    public Collection<Jid> getBlocklist() {
        return this.blocklist;
    }

    public void clearBlocklist() {
        getBlocklist().clear();
    }

    public boolean isOnlineAndConnected() {
        return this.getStatus() == State.ONLINE && this.getXmppConnection() != null;
    }

    @Override
    public int getAvatarBackgroundColor() {
        return UIHelper.getColorForName(jid.asBareJid().toString());
    }

    public enum State {
        DISABLED(false, false),
        OFFLINE(false),
        CONNECTING(false),
        ONLINE(false),
        NO_INTERNET(false),
        UNAUTHORIZED,
        SERVER_NOT_FOUND,
        REGISTRATION_SUCCESSFUL(false),
        REGISTRATION_FAILED(true, false),
        REGISTRATION_WEB(true, false),
        REGISTRATION_CONFLICT(true, false),
        REGISTRATION_NOT_SUPPORTED(true, false),
        REGISTRATION_PLEASE_WAIT(true, false),
        REGISTRATION_INVALID_TOKEN(true,false),
        REGISTRATION_PASSWORD_TOO_WEAK(true, false),
        TLS_ERROR,
        INCOMPATIBLE_SERVER,
        TOR_NOT_AVAILABLE,
        DOWNGRADE_ATTACK,
        SESSION_FAILURE,
        BIND_FAILURE,
        HOST_UNKNOWN,
        STREAM_ERROR,
        STREAM_OPENING_ERROR,
        POLICY_VIOLATION,
        PAYMENT_REQUIRED,
        MISSING_INTERNET_PERMISSION(false);

        private final boolean isError;
        private final boolean attemptReconnect;

        State(final boolean isError) {
            this(isError, true);
        }

        State(final boolean isError, final boolean reconnect) {
            this.isError = isError;
            this.attemptReconnect = reconnect;
        }

        State() {
            this(true, true);
        }

        public boolean isError() {
            return this.isError;
        }

        public boolean isAttemptReconnect() {
            return this.attemptReconnect;
        }

        public int getReadableId() {
            switch (this) {
                case DISABLED:
                    return R.string.account_status_disabled;
                case ONLINE:
                    return R.string.account_status_online;
                case CONNECTING:
                    return R.string.account_status_connecting;
                case OFFLINE:
                    return R.string.account_status_offline;
                case UNAUTHORIZED:
                    return R.string.account_status_unauthorized;
                case SERVER_NOT_FOUND:
                    return R.string.account_status_not_found;
                case NO_INTERNET:
                    return R.string.account_status_no_internet;
                case REGISTRATION_FAILED:
                    return R.string.account_status_regis_fail;
                case REGISTRATION_WEB:
                    return R.string.account_status_regis_web;
                case REGISTRATION_CONFLICT:
                    return R.string.account_status_regis_conflict;
                case REGISTRATION_SUCCESSFUL:
                    return R.string.account_status_regis_success;
                case REGISTRATION_NOT_SUPPORTED:
                    return R.string.account_status_regis_not_sup;
                case REGISTRATION_INVALID_TOKEN:
                    return R.string.account_status_regis_invalid_token;
                case TLS_ERROR:
                    return R.string.account_status_tls_error;
                case INCOMPATIBLE_SERVER:
                    return R.string.account_status_incompatible_server;
                case TOR_NOT_AVAILABLE:
                    return R.string.account_status_tor_unavailable;
                case BIND_FAILURE:
                    return R.string.account_status_bind_failure;
                case SESSION_FAILURE:
                    return R.string.session_failure;
                case DOWNGRADE_ATTACK:
                    return R.string.sasl_downgrade;
                case HOST_UNKNOWN:
                    return R.string.account_status_host_unknown;
                case POLICY_VIOLATION:
                    return R.string.account_status_policy_violation;
                case REGISTRATION_PLEASE_WAIT:
                    return R.string.registration_please_wait;
                case REGISTRATION_PASSWORD_TOO_WEAK:
                    return R.string.registration_password_too_weak;
                case STREAM_ERROR:
                    return R.string.account_status_stream_error;
                case STREAM_OPENING_ERROR:
                    return R.string.account_status_stream_opening_error;
                case PAYMENT_REQUIRED:
                    return R.string.payment_required;
                case MISSING_INTERNET_PERMISSION:
                    return R.string.missing_internet_permission;
                default:
                    return R.string.account_status_unknown;
            }
        }
    }
}

package com.plutonem.xmpp.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;

import com.plutonem.Config;
import com.plutonem.xmpp.services.AvatarService;
import com.plutonem.xmpp.utils.CryptoHelper;
import com.plutonem.xmpp.utils.UIHelper;

import org.json.JSONException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rocks.xmpp.addr.Jid;

public class Message extends AbstractEntity implements AvatarService.Avatarable {

    public static final String TABLENAME = "messages";

    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_UNSEND = 1;
    public static final int STATUS_SEND = 2;
    public static final int STATUS_SEND_FAILED = 3;
    public static final int STATUS_WAITING = 5;
    public static final int STATUS_OFFERED = 6;
    public static final int STATUS_SEND_RECEIVED = 7;
    public static final int STATUS_SEND_DISPLAYED = 8;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_DECRYPTED = 3;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 4;
    public static final int ENCRYPTION_AXOLOTL = 5;
    public static final int ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE = 6;
    public static final int ENCRYPTION_AXOLOTL_FAILED = 7;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;
    public static final int TYPE_STATUS = 3;
    public static final int TYPE_PRIVATE = 4;
    public static final int TYPE_PRIVATE_FILE = 5;

    public static final String CONVERSATION = "conversationUuid";
    public static final String COUNTERPART = "counterpart";
    public static final String TRUE_COUNTERPART = "trueCounterpart";
    public static final String BODY = "body";
    public static final String BODY_LANGUAGE = "bodyLanguage";
    public static final String TIME_SENT = "timeSent";
    public static final String ENCRYPTION = "encryption";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String CARBON = "carbon";
    public static final String OOB = "oob";
    public static final String EDITED = "edited";
    public static final String REMOTE_MSG_ID = "remoteMsgId";
    public static final String SERVER_MSG_ID = "serverMsgId";
    public static final String RELATIVE_FILE_PATH = "relativeFilePath";
    public static final String FINGERPRINT = "axolotl_fingerprint";
    public static final String READ = "read";
    public static final String ERROR_MESSAGE = "errorMsg";
    public static final String READ_BY_MARKERS = "readByMarkers";
    public static final String MARKABLE = "markable";
    public static final String DELETED = "deleted";
    public static final String ME_COMMAND = "/me ";

    public boolean markable = false;
    protected String conversationUuid;
    protected Jid counterpart;
    protected Jid trueCounterpart;
    protected String body;
    protected String encryptedBody;
    protected long timeSent;
    protected int encryption;
    protected int status;
    protected int type;
    protected boolean deleted = false;
    protected boolean carbon = false;
    protected boolean oob = false;
    protected List<Edit> edits = new ArrayList<>();
    protected String relativeFilePath;
    protected boolean read = true;
    protected String remoteMsgId = null;
    private String bodyLanguage = null;
    protected String serverMsgId = null;
    private final Conversational conversation;
    protected Transferable transferable = null;
    private Message mNextMessage = null;
    private Message mPreviousMessage = null;
    private String axolotlFingerprint = null;
    private String errorMessage = null;
    private Set<ReadByMarker> readByMarkers = new HashSet<>();

    private Boolean isGeoUri = null;
    private Boolean isEmojisOnly = null;
    private Boolean treatAsDownloadable = null;
    private FileParams fileParams = null;

    protected Message(Conversational conversation) {
        this.conversation = conversation;
    }

    public Message(Conversational conversation, String body, int encryption) {
        this(conversation, body, encryption, STATUS_UNSEND);
    }

    public Message(Conversational conversation, String body, int encryption, int status) {
        this(conversation, java.util.UUID.randomUUID().toString(),
                conversation.getUuid(),
                conversation.getJid() == null ? null : conversation.getJid().asBareJid(),
                null,
                body,
                System.currentTimeMillis(),
                encryption,
                status,
                TYPE_TEXT,
                false,
                null,
                null,
                null,
                null,
                true,
                null,
                false,
                null,
                null,
                false,
                false,
                null);
    }

    protected Message(final Conversational conversation, final String uuid, final String conversationUUid, final Jid counterpart,
                      final Jid trueCounterpart, final String body, final long timeSent,
                      final int encryption, final int status, final int type, final boolean carbon,
                      final String remoteMsgId, final String relativeFilePath,
                      final String serverMsgId, final String fingerprint, final boolean read,
                      final String edited, final boolean oob, final String errorMessage, final Set<ReadByMarker> readByMarkers,
                      final boolean markable, final boolean deleted, final String bodyLanguage) {
        this.conversation = conversation;
        this.uuid = uuid;
        this.conversationUuid = conversationUUid;
        this.counterpart = counterpart;
        this.trueCounterpart = trueCounterpart;
        this.body = body == null ? "" : body;
        this.timeSent = timeSent;
        this.encryption = encryption;
        this.status = status;
        this.type = type;
        this.carbon = carbon;
        this.remoteMsgId = remoteMsgId;
        this.relativeFilePath = relativeFilePath;
        this.serverMsgId = serverMsgId;
        this.axolotlFingerprint = fingerprint;
        this.read = read;
        this.edits = Edit.fromJson(edited);
        this.oob = oob;
        this.errorMessage = errorMessage;
        this.readByMarkers = readByMarkers == null ? new HashSet<>() : readByMarkers;
        this.markable = markable;
        this.deleted = deleted;
        this.bodyLanguage = bodyLanguage;
    }

    public static Message fromCursor(Cursor cursor, Conversation conversation) {
        return new Message(conversation,
                cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(CONVERSATION)),
                fromString(cursor.getString(cursor.getColumnIndex(COUNTERPART))),
                fromString(cursor.getString(cursor.getColumnIndex(TRUE_COUNTERPART))),
                cursor.getString(cursor.getColumnIndex(BODY)),
                cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
                cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(TYPE)),
                cursor.getInt(cursor.getColumnIndex(CARBON)) > 0,
                cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)),
                cursor.getString(cursor.getColumnIndex(RELATIVE_FILE_PATH)),
                cursor.getString(cursor.getColumnIndex(SERVER_MSG_ID)),
                cursor.getString(cursor.getColumnIndex(FINGERPRINT)),
                cursor.getInt(cursor.getColumnIndex(READ)) > 0,
                cursor.getString(cursor.getColumnIndex(EDITED)),
                cursor.getInt(cursor.getColumnIndex(OOB)) > 0,
                cursor.getString(cursor.getColumnIndex(ERROR_MESSAGE)),
                ReadByMarker.fromJsonString(cursor.getString(cursor.getColumnIndex(READ_BY_MARKERS))),
                cursor.getInt(cursor.getColumnIndex(MARKABLE)) > 0,
                cursor.getInt(cursor.getColumnIndex(DELETED)) > 0,
                cursor.getString(cursor.getColumnIndex(BODY_LANGUAGE))
        );
    }

    private static Jid fromString(String value) {
        try {
            if (value != null) {
                return Jid.of(value);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(CONVERSATION, conversationUuid);
        if (counterpart == null) {
            values.putNull(COUNTERPART);
        } else {
            values.put(COUNTERPART, counterpart.toString());
        }
        if (trueCounterpart == null) {
            values.putNull(TRUE_COUNTERPART);
        } else {
            values.put(TRUE_COUNTERPART, trueCounterpart.toString());
        }
        values.put(BODY, body.length() > Config.MAX_STORAGE_MESSAGE_CHARS ? body.substring(0, Config.MAX_STORAGE_MESSAGE_CHARS) : body);
        values.put(TIME_SENT, timeSent);
        values.put(ENCRYPTION, encryption);
        values.put(STATUS, status);
        values.put(TYPE, type);
        values.put(CARBON, carbon ? 1 : 0);
        values.put(REMOTE_MSG_ID, remoteMsgId);
        values.put(RELATIVE_FILE_PATH, relativeFilePath);
        values.put(SERVER_MSG_ID, serverMsgId);
        values.put(FINGERPRINT, axolotlFingerprint);
        values.put(READ, read ? 1 : 0);
        try {
            values.put(EDITED, Edit.toJson(edits));
        } catch (JSONException e) {
            Log.e(Config.LOGTAG,"error persisting json for edits",e);
        }
        values.put(OOB, oob ? 1 : 0);
        values.put(ERROR_MESSAGE, errorMessage);
        values.put(READ_BY_MARKERS, ReadByMarker.toJson(readByMarkers).toString());
        values.put(MARKABLE, markable ? 1 : 0);
        values.put(DELETED, deleted ? 1 : 0);
        values.put(BODY_LANGUAGE, bodyLanguage);
        return values;
    }

    public String getConversationUuid() {
        return conversationUuid;
    }

    public Conversational getConversation() {
        return this.conversation;
    }

    public Jid getCounterpart() {
        return counterpart;
    }

    public void setCounterpart(final Jid counterpart) {
        this.counterpart = counterpart;
    }

    public Contact getContact() {
        if (this.conversation.getMode() == Conversation.MODE_SINGLE) {
            return this.conversation.getContact();
        } else {
            // we will save getting Contact in Multi Mode part later to fulfill
            return null;
        }
    }

    public String getBody() {
        return body;
    }

    public synchronized void setBody(String body) {
        if (body == null) {
            throw new Error("You should not set the message body to null");
        }
        this.body = body;
        this.isGeoUri = null;
        this.isEmojisOnly = null;
        this.treatAsDownloadable = null;
        this.fileParams = null;
    }

    public boolean setErrorMessage(String message) {
        boolean changed = (message != null && !message.equals(errorMessage))
                || (message == null && errorMessage != null);
        this.errorMessage = message;
        return changed;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public int getEncryption() {
        return encryption;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getRemoteMsgId() {
        return this.remoteMsgId;
    }

    public void setRemoteMsgId(String id) {
        this.remoteMsgId = id;
    }

    public String getServerMsgId() {
        return this.serverMsgId;
    }

    public void setServerMsgId(String id) {
        this.serverMsgId = id;
    }

    public boolean isRead() {
        return this.read;
    }

    public boolean isDeleted() {
        return this.deleted;
    }

    public void setTime(long time) {
        this.timeSent = time;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isCarbon() {
        return carbon;
    }

    public void setCarbon(boolean carbon) {
        this.carbon = carbon;
    }

    public String getBodyLanguage() {
        return this.bodyLanguage;
    }

    public void setBodyLanguage(String language) {
        this.bodyLanguage = language;
    }

    public boolean edited() {
        return this.edits.size() > 0;
    }

    public void setTrueCounterpart(Jid trueCounterpart) {
        this.trueCounterpart = trueCounterpart;
    }

    public Jid getTrueCounterpart() {
        return this.trueCounterpart;
    }

    public Transferable getTransferable() {

        // we don't allow the file transferring by now

        return null;
    }


    @Override
    public int getAvatarBackgroundColor() {

        // since we are not supporting Muc option for Group Chat right now, we only set one option here.

        return UIHelper.getColorForName(UIHelper.getMessageDisplayName(this));
    }

    public boolean isOOb() {

        // we don't allow file transferring right now

        return false;
    }

    boolean similar(Message message) {
        if (!isPrivateMessage() && this.serverMsgId != null && message.getServerMsgId() != null) {
            return this.serverMsgId.equals(message.getServerMsgId()) || Edit.wasPreviouslyEditedServerMsgId(edits, message.getServerMsgId());
        } else if (Edit.wasPreviouslyEditedServerMsgId(edits, message.getServerMsgId())) {
            return true;
        } else if (this.body == null || this.counterpart == null) {
            return false;
        } else {

            // we don't have file transfer in message right now so skip hasFileOnRemoteHost ask

            String body, otherBody;
            body = this.body;
            otherBody = message.body;
            final boolean matchingCounterpart = this.counterpart.equals(message.getCounterpart());
            if (message.getRemoteMsgId() != null) {
                final boolean hasUuid = CryptoHelper.UUID_PATTERN.matcher(message.getRemoteMsgId()).matches();
                if (hasUuid && matchingCounterpart && Edit.wasPreviouslyEditedRemoteMsgId(edits, message.getRemoteMsgId())) {
                    return true;
                }
                return (message.getRemoteMsgId().equals(this.remoteMsgId) || message.getRemoteMsgId().equals(this.uuid))
                        && matchingCounterpart
                        && (body.equals(otherBody) || (message.getEncryption() == Message.ENCRYPTION_PGP && hasUuid));
            } else {
                return this.remoteMsgId == null
                        && matchingCounterpart
                        && body.equals(otherBody)
                        && Math.abs(this.getTimeSent() - message.getTimeSent()) < Config.MESSAGE_MERGE_WINDOW * 1000;
            }
        }
    }

    public String getEditedId() {
        if (edits.size() > 0) {
            return edits.get(edits.size() - 1).getEditedId();
        } else {
            throw new IllegalStateException("Attempting to store unedited message");
        }
    }

    public String getEditedIdWireFormat() {
        if (edits.size() > 0) {
            return edits.get(Config.USE_LMC_VERSION_1_1 ? 0 : edits.size() - 1).getEditedId();
        } else {
            throw new IllegalStateException("Attempting to store unedited message");
        }
    }

    public synchronized boolean treatAsDownloadable() {
        //omit this part by now
        return false;
    }

    public synchronized boolean isGeoUri() {
        // omit this part by now
        return false;
    }

    public synchronized FileParams getFileParams() {
        if (fileParams == null) {
            fileParams = new FileParams();
            if (this.transferable != null) {
                fileParams.size = this.transferable.getFileSize();
            }
            final String[] parts = body == null ? new String[0] : body.split("\\|");
            switch (parts.length) {
                case 1:
                    try {
                        fileParams.size = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        fileParams.url = parseUrl(parts[0]);
                    }
                    break;
                case 5:
                    fileParams.runtime = parseInt(parts[4]);
                case 4:
                    fileParams.width = parseInt(parts[2]);
                    fileParams.height = parseInt(parts[3]);
                case 2:
                    fileParams.url = parseUrl(parts[0]);
                    fileParams.size = parseLong(parts[1]);
                    break;
                case 3:
                    fileParams.size = parseLong(parts[0]);
                    fileParams.width = parseInt(parts[1]);
                    fileParams.height = parseInt(parts[2]);
                    break;
            }
        }
        return fileParams;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static URL parseUrl(String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public void untie() {
        this.mNextMessage = null;
        this.mPreviousMessage = null;
    }

    public boolean isPrivateMessage() {
        return type == TYPE_PRIVATE || type == TYPE_PRIVATE_FILE;
    }

    public boolean isFileOrImage() {
        return type == TYPE_FILE || type == TYPE_IMAGE || type == TYPE_PRIVATE_FILE;
    }

    public boolean hasFileOnRemoteHost() {
        return isFileOrImage() && getFileParams().url != null;
    }

    public boolean needsUploading() {
        return isFileOrImage() && getFileParams().url == null;
    }

    public class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public int runtime = 0;
    }
}

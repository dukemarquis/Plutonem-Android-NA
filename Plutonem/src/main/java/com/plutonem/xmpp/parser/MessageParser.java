package com.plutonem.xmpp.parser;

import android.app.PendingIntent;
import android.util.Log;
import android.util.Pair;

import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.entities.ReceiptRequest;
import com.plutonem.xmpp.services.MessageArchiveService;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.xml.Element;
import com.plutonem.xmpp.xml.LocalizedContent;
import com.plutonem.xmpp.xml.Namespace;
import com.plutonem.xmpp.xmpp.InvalidJid;
import com.plutonem.xmpp.xmpp.OnMessagePacketReceived;
import com.plutonem.xmpp.xmpp.chatstate.ChatState;
import com.plutonem.xmpp.xmpp.stanzas.MessagePacket;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import rocks.xmpp.addr.Jid;

public class MessageParser extends AbstractParser implements OnMessagePacketReceived {

    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH);

    public MessageParser(XmppConnectionService service) {
        super(service);
    }

    private static String extractStanzaId(Element packet, boolean isTypeGroupChat, Conversation conversation) {
        final Jid by;
        final boolean safeToExtract;
        if (isTypeGroupChat) {
            // only need to omit it by now
            by = null;
            safeToExtract = false;
        } else {
            Account account = conversation.getAccount();
            by = account.getJid().asBareJid();
            safeToExtract = account.getXmppConnection().getFeatures().stanzaIds();
        }
        return safeToExtract ? extractStanzaId(packet, by) : null;
    }

    private static String extractStanzaId(Element packet, Jid by) {
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("stanza-id")
                    && Namespace.STANZA_IDS.equals(child.getNamespace())
                    && by.equals(InvalidJid.getNullForInvalid(child.getAttributeAsJid("by")))) {
                return child.getAttribute("id");
            }
        }
        return null;
    }

    private boolean extractChatState(Conversation c, final boolean isTypeGroupChat, final MessagePacket packet) {
        ChatState state = ChatState.parse(packet);
        if (state != null && c != null) {
            final Account account = c.getAccount();
            Jid from = packet.getFrom();
            if (from.asBareJid().equals(account.getJid().asBareJid())) {
                c.setOutgoingChatState(state);
                if (state == ChatState.ACTIVE || state == ChatState.COMPOSING) {
                    mXmppConnectionService.markRead(c);
                    activateGracePeriod(account);
                }
                return false;
            } else {
                if (isTypeGroupChat) {
                    // skip the logic about Multi User Chat
                } else {
                    return c.setIncomingChatState(state);
                }
            }
        }
        return false;
    }

    private boolean handleErrorMessage(Account account, MessagePacket packet) {
        if (packet.getType() == MessagePacket.TYPE_ERROR) {
            Jid from = packet.getFrom();
            if (from != null) {
                mXmppConnectionService.markMessage(
                        account,
                        from.asBareJid(),
                        packet.getId(),
                        Message.STATUS_SEND_FAILED,
                        extractErrorMessage(packet));

                // omit the part about Self ping and Rejoin a Muc party since we only need single chat mode right now

            }
            return true;
        }
        return false;
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket original) {
        if (handleErrorMessage(account, original)) {
            return;
        }
        final MessagePacket packet;
        Long timestamp = null;
        boolean isCarbon = false;
        String serverMsgId = null;
        final Element fin = original.findChild("fin", MessageArchiveService.Version.MAM_0.namespace);
        if (fin != null) {
            mXmppConnectionService.getMessageArchiveService().processFinLegacy(fin, original.getFrom());
            return;
        }
        final Element result = MessageArchiveService.Version.findResult(original);
        final MessageArchiveService.Query query = result == null ? null : mXmppConnectionService.getMessageArchiveService().findQuery(result.getAttribute("queryid"));
        if (query != null && query.validFrom(original.getFrom())) {
            Pair<MessagePacket, Long> f = original.getForwardedMessagePacket("result", query.version.namespace);
            if (f == null) {
                return;
            }
            timestamp = f.second;
            packet = f.first;
            serverMsgId = result.getAttribute("id");
            query.incrementMessageCount();
        } else if (query != null) {
            Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": received mam result from invalid sender");
            return;
        } else if (original.fromServer(account)) {
            Pair<MessagePacket, Long> f;
            f = original.getForwardedMessagePacket("received", "urn:xmpp:carbons:2");
            f = f == null ? original.getForwardedMessagePacket("sent", "urn:xmpp:carbons:2") : f;
            packet = f != null ? f.first : original;
            if (handleErrorMessage(account, packet)) {
                return;
            }
            timestamp = f != null ? f.second : null;
            isCarbon = f != null;
        } else {
            packet = original;
        }

        if (timestamp == null) {
            timestamp = AbstractParser.parseTimestamp(original, AbstractParser.parseTimestamp(packet));
        }

        // set several variable default to null cause all about Multi User Chat or Encryption Chat.
        final LocalizedContent body = packet.getBody();
        final Element mucUserElement = null;
        final String pgpEncrypted = null;
        final Element replaceElement = null;
        final Element oob = null;
        final Element xP1S3 = null;
        final URL xP1S3url = null;
        final String oobUrl = null;
        final String replacementId = null;
        final Element axolotlEncrypted = null;
        int status;
        final Jid counterpart;
        final Jid to = packet.getTo();
        final Jid from = packet.getFrom();
        final Element originId = packet.findChild("origin-id", Namespace.STANZA_IDS);
        final String remoteMsgId;
        if (originId != null && originId.getAttribute("id") != null) {
            remoteMsgId = originId.getAttribute("id");
        } else {
            remoteMsgId = packet.getId();
        }
        boolean notify = false;

        if (from == null || !InvalidJid.isValid(from) || !InvalidJid.isValid(to)) {
            Log.e(Config.LOGTAG, "encountered invalid message from='" + from + "' to='" + to + "'");
            return;
        }

        // set several variable default to false cause all about Multi User Chat or Encryption Chat.
        boolean isTypeGroupChat = false;
        if (query != null && !query.muc() && isTypeGroupChat) {
            Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": received groupchat (" + from + ") message on regular MAM request. skipping");
            return;
        }
        boolean isMucStatusMessage = false;
        boolean selfAddressed;

        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            selfAddressed = to == null || account.getJid().asBareJid().equals(to.asBareJid());
            if (selfAddressed) {
                counterpart = from;
            } else {
                counterpart = to != null ? to : account.getJid();
            }
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
            selfAddressed = false;
        }

        // omit the part of invitation from Muc or Conference since Plutonem not support any of these right now

        if ((body != null || pgpEncrypted != null || (axolotlEncrypted != null && axolotlEncrypted.hasChild("payload")) || oobUrl !=null || xP1S3 != null) && !isMucStatusMessage) {
            // set several variable default to false cause all about Multi User Chat or Encryption Chat.
            final boolean conversationIsProbablyMuc = false;
            final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.asBareJid(), conversationIsProbablyMuc, false, query, false);
            final boolean conversationMultiMode = false;

            if (serverMsgId == null) {
                serverMsgId = extractStanzaId(packet, false, conversation);
            }

            if (selfAddressed) {
                if (mXmppConnectionService.markMessage(conversation, remoteMsgId, Message.STATUS_SEND_RECEIVED, serverMsgId)) {
                    return;
                }
                status = Message.STATUS_RECEIVED;
                if (remoteMsgId != null && conversation.findMessageWithRemoteId(remoteMsgId, counterpart) != null) {
                    return;
                }
            }

            // omit the part about Group chat for now

            // omit the part about xP1S3url for now

            // omit the part about pgpEncrypted for now

            // omit the part about axolotl for now

            // omit the part about oob for now

            final Message message;
            message = new Message(conversation, body.content, Message.ENCRYPTION_NONE, status);
            if (body.count > 1) {
                message.setBodyLanguage(body.language);
            }

            message.setCounterpart(counterpart);
            message.setRemoteMsgId(remoteMsgId);
            message.setServerMsgId(serverMsgId);
            message.setCarbon(isCarbon);
            message.setTime(timestamp);

            // skip the part about oob file transfer circumstances since Plutonem is not supporting it right now

            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");

            // skip the part about Multi mode in MAM for now

            updateLastseen(account, from);

            // skip the part about message correction since we are not supporting it right now

            long deletionDate = mXmppConnectionService.getAutomaticMessageDeletionDate();
            if (deletionDate != 0 && message.getTimeSent() < deletionDate) {
                Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": skipping message from " + message.getCounterpart().toString() + " because it was sent prior to our deletion date");
                return;
            }

            boolean checkForDuplicates = (isTypeGroupChat && packet.hasChild("delay", "urn:xmpp:delay"))
                    || message.isPrivateMessage()
                    || message.getServerMsgId() != null
                    || (query == null && mXmppConnectionService.getMessageArchiveService().isCatchupInProgress(conversation));
            if (checkForDuplicates) {
                final Message duplicate = conversation.findDuplicateMessage(message);
                if (duplicate != null) {
                    final boolean serverMsgIdUpdated;
                    if (duplicate.getStatus() != Message.STATUS_RECEIVED
                            && duplicate.getUuid().equals(message.getRemoteMsgId())
                            && duplicate.getServerMsgId() == null
                            && message.getServerMsgId() != null) {
                        duplicate.setServerMsgId(message.getServerMsgId());
                        if (mXmppConnectionService.databaseBackend.updateMessage(duplicate, false)) {
                            serverMsgIdUpdated = true;
                        } else {
                            serverMsgIdUpdated = false;
                            Log.e(Config.LOGTAG, "failed to update message");
                        }
                    } else {
                        serverMsgIdUpdated = false;
                    }
                    Log.d(Config.LOGTAG, "skipping duplicate message with " + message.getCounterpart() + ". serverMsgIdUpdated=" + serverMsgIdUpdated);
                    return;
                }
            }

            if (query != null && query.getPagingOrder() == MessageArchiveService.PagingOrder.REVERSE) {
                conversation.prepend(query.getActualInThisQuery(), message);
            } else {
                conversation.add(message);
            }

            if (query != null) {
                query.incrementActualMessageCount();
            }

            if (query == null || query.isCatchup()) { // either no mam or catchup
                if (status == Message.STATUS_SEND || status == Message.STATUS_SEND_RECEIVED) {
                    mXmppConnectionService.markRead(conversation);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else {
                    message.markUnread();
                    notify = true;
                }
            }

            if (message.getEncryption() == Message.ENCRYPTION_PGP) {
                // skip the logic about ENCRYPTION Chat
            } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE || message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
                notify = false;
            }

            if (query == null) {
                extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet);
                mXmppConnectionService.updateConversationUi();
            }

            if (mXmppConnectionService.confirmMessages()
                    && message.getStatus() == Message.STATUS_RECEIVED
                    && (message.trusted() || message.isPrivateMessage())
                    && remoteMsgId != null
                    && !selfAddressed
                    && !isTypeGroupChat) {
                processMessageReceipts(account, packet, query);
            }

            mXmppConnectionService.databaseBackend.createMessage(message);

            // skip the logic about File Transfer Chat logic
            if (notify) {
                if (query != null && query.isCatchup()) {
                    mXmppConnectionService.getNotificationService().pushFromBacklog(message);
                } else {
                    mXmppConnectionService.getNotificationService().push(message);
                }
            }
        } else if (!packet.hasChild("body")) {
            final Conversation conversation = mXmppConnectionService.find(account, from.asBareJid());

            // skip Axolotl Encrypted Chat logic now

            if (query == null && extractChatState(mXmppConnectionService.find(account, counterpart.asBareJid()), isTypeGroupChat, packet)) {
                mXmppConnectionService.updateConversationUi();
            }

            // skip Group Chat logic now

            // skip Multi User Chat logic now

            Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
            if (received == null) {
                received = packet.findChild("received", "urn:xmpp:receipts");
            }
            if (received != null) {
                String id = received.getAttribute("id");
                if (packet.fromAccount(account)) {
                    if (query != null && id != null && packet.getTo() != null) {
                        query.removePendingReceiptRequest(new ReceiptRequest(packet.getTo(), id));
                    }
                } else {
                    mXmppConnectionService.markMessage(account, from.asBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
                }
            }
            Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
            if (displayed != null) {
                final String id = displayed.getAttribute("id");
                final Jid sender = InvalidJid.getNullForInvalid(displayed.getAttributeAsJid("sender"));
                if (packet.fromAccount(account) && !selfAddressed) {
                    dismissNotification(account, counterpart, query);
                    if (query == null) {
                        activateGracePeriod(account);
                    }
                } else if (isTypeGroupChat) {
                    // skip Group Chat logic consideration here
                } else {
                    final Message displayedMessage = mXmppConnectionService.markMessage(account, from.asBareJid(), id, Message.STATUS_SEND_DISPLAYED);
                    Message message = displayedMessage == null ? null : displayedMessage.prev();
                    while (message != null
                            && message.getStatus() == Message.STATUS_SEND_RECEIVED
                            && message.getTimeSent() < displayedMessage.getTimeSent()) {
                        mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                        message = message.prev();
                    }
                    if (displayedMessage != null && selfAddressed) {
                        dismissNotification(account, counterpart, query);
                    }
                }
            }
        }

        // save event of pubsub logic for later processing

        // save nick setting logic for later processing
    }

    private void dismissNotification(Account account, Jid counterpart, MessageArchiveService.Query query) {
        Conversation conversation = mXmppConnectionService.find(account, counterpart.asBareJid());
        if (conversation != null && (query == null || query.isCatchup())) {
            mXmppConnectionService.markRead(conversation); //TODO only mark messages read that are older than timestamp
        }
    }

    private void processMessageReceipts(Account account, MessagePacket packet, MessageArchiveService.Query query) {
        final boolean markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
        final boolean request = packet.hasChild("request", "urn:xmpp:receipts");
        if (query == null) {
            final ArrayList<String> receiptsNamespaces = new ArrayList<>();
            if (markable) {
                receiptsNamespaces.add("urn:xmpp:chat-markers:0");
            }
            if (request) {
                receiptsNamespaces.add("urn:xmpp:receipts");
            }
            if (receiptsNamespaces.size() > 0) {
                MessagePacket receipt = mXmppConnectionService.getMessageGenerator().received(account,
                        packet,
                        receiptsNamespaces,
                        packet.getType());
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        } else if (query.isCatchup()) {
            if (request) {
                query.addPendingReceiptRequest(new ReceiptRequest(packet.getFrom(), packet.getId()));
            }
        }
    }

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getLongPreference("grace_period_length", R.integer.grace_period) * 1000;
        Log.d(Config.LOGTAG, account.getJid().asBareJid() + ": activating grace period till " + TIME_FORMAT.format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }
}

package com.plutonem.xmpp.utils;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.Pair;

import androidx.annotation.ColorInt;

import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Conversational;
import com.plutonem.xmpp.entities.ListItem;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.entities.Presence;
import com.plutonem.xmpp.entities.Transferable;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import rocks.xmpp.addr.Jid;

public class UIHelper {

    private static int[] UNSAFE_COLORS = {
            0xFFF44336, //red 500
            0xFFE53935, //red 600
            0xFFD32F2F, //red 700
            0xFFC62828, //red 800

            0xFFEF6C00, //orange 800

            0xFFF4511E, //deep orange 600
            0xFFE64A19, //deep orange 700
            0xFFD84315, //deep orange 800,
    };

    private static int[] SAFE_COLORS = {
            0xFFE91E63, //pink 500
            0xFFD81B60, //pink 600
            0xFFC2185B, //pink 700
            0xFFAD1457, //pink 800

            0xFF9C27B0, //purple 500
            0xFF8E24AA, //purple 600
            0xFF7B1FA2, //purple 700
            0xFF6A1B9A, //purple 800

            0xFF673AB7, //deep purple 500,
            0xFF5E35B1, //deep purple 600
            0xFF512DA8, //deep purple 700
            0xFF4527A0, //deep purple 800,

            0xFF3F51B5, //indigo 500,
            0xFF3949AB,//indigo 600
            0xFF303F9F,//indigo 700
            0xFF283593, //indigo 800

            0xFF2196F3, //blue 500
            0xFF1E88E5, //blue 600
            0xFF1976D2, //blue 700
            0xFF1565C0, //blue 800

            0xFF03A9F4, //light blue 500
            0xFF039BE5, //light blue 600
            0xFF0288D1, //light blue 700
            0xFF0277BD, //light blue 800

            0xFF00BCD4, //cyan 500
            0xFF00ACC1, //cyan 600
            0xFF0097A7, //cyan 700
            0xFF00838F, //cyan 800

            0xFF009688, //teal 500,
            0xFF00897B, //teal 600
            0xFF00796B, //teal 700
            0xFF00695C, //teal 800,

            //0xFF558B2F, //light green 800

            //0xFFC0CA33, //lime 600
            0xFF9E9D24, //lime 800

            0xFF795548, //brown 500,
            //0xFF4E342E, //brown 800
            0xFF607D8B, //blue grey 500,
            //0xFF37474F //blue grey 800
    };

    private static final int[] COLORS;

    static {
        COLORS = Arrays.copyOf(SAFE_COLORS, SAFE_COLORS.length + UNSAFE_COLORS.length);
        System.arraycopy(UNSAFE_COLORS, 0, COLORS, SAFE_COLORS.length, UNSAFE_COLORS.length);
    }

    private static final List<Character> PUNCTIONATION = Arrays.asList('.', ',', '?', '!', ';', ':');

    public static int getColorForName(String name) {
        return getColorForName(name, false);
    }

    public static int getColorForName(String name, boolean safe) {
        if (Config.XEP_0392) {
            return XEP0392Helper.rgbFromNick(name);
        }
        if (name == null || name.isEmpty()) {
            return 0xFF202020;
        }
        if (safe) {
            return SAFE_COLORS[(int) (getLongForName(name) % SAFE_COLORS.length)];
        } else {
            return COLORS[(int) (getLongForName(name) % COLORS.length)];
        }
    }

    private static long getLongForName(String name) {
        try {
            final MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            return Math.abs(new BigInteger(messageDigest.digest(name.getBytes())).longValue());
        } catch (Exception e) {
            return 0;
        }
    }

    public static Pair<CharSequence, Boolean> getMessagePreview(final Context context, final Message message) {
        return getMessagePreview(context, message, 0);
    }

    public static Pair<CharSequence, Boolean> getMessagePreview(final Context context, final Message message, @ColorInt int textColor) {
        final Transferable d = message.getTransferable();
        if (d != null) {
            // omit this part by now
            return null;
        } else if (message.isFileOrImage() && message.isDeleted()) {
            // omit this part by now
            return null;
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            // omit this part by now
            return null;
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            // omit this part by now
            return null;
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            return new Pair<>(context.getString(R.string.not_encrypted_for_this_device), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            // omit this part by now
            return null;
        } else if (message.isFileOrImage()) {
            // omit this part by now
            return null;
        } else {
            final String body = MessageUtils.filterLtrRtl(message.getBody());
            if (body.startsWith(Message.ME_COMMAND)) {
                return new Pair<>(body.replaceAll("^" + Message.ME_COMMAND,
                        UIHelper.getMessageDisplayName(message) + " "), false);
            } else if (message.isGeoUri()) {
                // omit this part by now
                return null;
            } else if (message.treatAsDownloadable() || MessageUtils.unInitiatedButKnownSize(message)) {
                // omit this part by now
                return null;
            } else {
                SpannableStringBuilder styledBody = new SpannableStringBuilder(body);
                if (textColor != 0) {
                    StylingHelper.format(styledBody, 0, styledBody.length() - 1, textColor);
                }
                SpannableStringBuilder builder = new SpannableStringBuilder();
                for (CharSequence l : CharSequenceUtils.split(styledBody, '\n')) {
                    if (l.length() > 0) {
                        if (l.toString().equals("```")) {
                            continue;
                        }
                        char first = l.charAt(0);
                        if ((first != '>' || !isPositionFollowedByQuoteableCharacter(l, 0)) && first != '\u00bb') {
                            CharSequence line = CharSequenceUtils.trim(l);
                            if (line.length() == 0) {
                                continue;
                            }
                            char last = line.charAt(line.length() - 1);
                            if (builder.length() != 0) {
                                builder.append(' ');
                            }
                            builder.append(line);
                            if (!PUNCTIONATION.contains(last)) {
                                break;
                            }
                        }
                    }
                }
                if (builder.length() == 0) {
                    builder.append(body.trim());
                }
                return new Pair<>(builder, false);
            }
        }
    }

    public static boolean isPositionFollowedByQuoteableCharacter(CharSequence body, int pos) {
        return !isPositionFollowedByNumber(body, pos)
                && !isPositionFollowedByEmoticon(body, pos)
                && !isPositionFollowedByEquals(body, pos);
    }

    private static boolean isPositionFollowedByNumber(CharSequence body, int pos) {
        boolean previousWasNumber = false;
        for (int i = pos + 1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isDigit(body.charAt(i))) {
                previousWasNumber = true;
            } else if (previousWasNumber && (c == '.' || c == ',')) {
                previousWasNumber = false;
            } else {
                return (Character.isWhitespace(c) || c == '%' || c == '+') && previousWasNumber;
            }
        }
        return previousWasNumber;
    }

    private static boolean isPositionFollowedByEquals(CharSequence body, int pos) {
        return body.length() > pos + 1 && body.charAt(pos + 1) == '=';
    }

    private static boolean isPositionFollowedByEmoticon(CharSequence body, int pos) {
        if (body.length() <= pos + 1) {
            return false;
        } else {
            final char first = body.charAt(pos + 1);
            return first == ';'
                    || first == ':'
                    || closingBeforeWhitespace(body, pos + 1);
        }
    }

    private static boolean closingBeforeWhitespace(CharSequence body, int pos) {
        for (int i = pos; i < body.length(); ++i) {
            final char c = body.charAt(i);
            if (Character.isWhitespace(c)) {
                return false;
            } else if (c == '<' || c == '>') {
                return body.length() == i + 1 || Character.isWhitespace(body.charAt(i + 1));
            }
        }
        return false;
    }

    public static String getMessageDisplayName(final Message message) {
        final Conversational conversation = message.getConversation();
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            final Contact contact = message.getContact();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                // omit this part by now
                return null;
            } else {
                return contact != null ? contact.getDisplayName() : "";
            }
        } else {
            if (conversation instanceof Conversation && conversation.getMode() == Conversation.MODE_MULTI) {
                //omit this part by now
                return null;
            } else {
                final Jid jid = conversation.getAccount().getJid();
                return jid.getLocal() != null ? jid.getLocal() : Jid.ofDomain(jid.getDomain()).toString();
            }
        }
    }

    public static String getMessageHint(Context context, Conversation conversation) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                if (Config.multipleEncryptionChoices()) {
                    return context.getString(R.string.send_unencrypted_message);
                } else {
                    return context.getString(R.string.send_message_to_x, conversation.getName());
                }
            // we don't need the other formation, so omit the process now
            case Message.ENCRYPTION_AXOLOTL:
            case Message.ENCRYPTION_PGP:
                return "";
            default:
                return "";
        }
    }

    public static ListItem.Tag getTagForStatus(Context context, Presence.Status status) {
        switch (status) {
            case CHAT:
                return new ListItem.Tag(context.getString(R.string.presence_chat), 0xff259b24);
            case AWAY:
                return new ListItem.Tag(context.getString(R.string.presence_away), 0xffff9800);
            case XA:
                return new ListItem.Tag(context.getString(R.string.presence_xa), 0xfff44336);
            case DND:
                return new ListItem.Tag(context.getString(R.string.presence_dnd), 0xfff44336);
            default:
                return new ListItem.Tag(context.getString(R.string.presence_online), 0xff259b24);
        }
    }
}

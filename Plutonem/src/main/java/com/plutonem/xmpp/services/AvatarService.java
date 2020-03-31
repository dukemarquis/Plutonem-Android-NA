package com.plutonem.xmpp.services;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.ColorInt;

import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Contact;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Conversational;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.utils.UIHelper;
import com.plutonem.xmpp.xmpp.OnAdvancedStreamFeaturesLoaded;

import java.util.ArrayList;
import java.util.Locale;

import javax.annotation.Nullable;

import rocks.xmpp.addr.Jid;

public class AvatarService implements OnAdvancedStreamFeaturesLoaded {

    private static final int FG_COLOR = 0xFFFAFAFA;

    public static final int SYSTEM_UI_AVATAR_SIZE = 48;

    private static final String PREFIX_CONTACT = "contact";
    private static final String PREFIX_ACCOUNT = "account";
    private static final String PREFIX_GENERIC = "generic";

    private static final String CHANNEL_SYMBOL = "#";

    final private ArrayList<Integer> sizes = new ArrayList<>();

    protected XmppConnectionService mXmppConnectionService = null;

    AvatarService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static int getSystemUiAvatarSize(final Context context) {
        return (int) (SYSTEM_UI_AVATAR_SIZE * context.getResources().getDisplayMetrics().density);
    }

    private Bitmap get(final Contact contact, final int size, boolean cachedOnly) {
        if (contact.isSelf()) {
            return get(contact.getAccount(), size, cachedOnly);
        }
        final String KEY = key(contact, size);
        Bitmap avatar = this.mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null || cachedOnly) {
            return avatar;
        }
        if (contact.getAvatarFilename() != null && QuickConversationsService.isQuicksy()) {
            avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatarFilename(), size);
        }
        if (avatar == null && contact.getProfilePhoto() != null) {
            avatar = mXmppConnectionService.getFileBackend().cropCenterSquare(Uri.parse(contact.getProfilePhoto()), size);
        }
        if (avatar == null && contact.getAvatarFilename() != null) {
            avatar = mXmppConnectionService.getFileBackend().getAvatar(contact.getAvatarFilename(), size);
        }
        if (avatar == null) {
            avatar = get(contact.getDisplayName(), contact.getJid().asBareJid().toString(), size, false);
        }
        this.mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public void clear(Contact contact) {
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(
                        key(contact, size));
            }
        }

        // since we are not supporting contact in conference situation, ignoring the clear process for now
    }

    private String key(Contact contact, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_CONTACT +
                '\0' +
                contact.getAccount().getJid().asBareJid() +
                '\0' +
                emptyOrNull(contact.getJid()) +
                '\0' +
                size;
    }

    public Bitmap get(Conversation conversation, int size) {
        return get(conversation, size, false);
    }

    public Bitmap get(Conversation conversation, int size, boolean cachedOnly) {
        // omit the Muc option condition consideration for now, so we turn into Single mode session directly
        return get(conversation.getContact(), size, cachedOnly);
    }

    public Bitmap get(Account account, int size) {
        return get(account, size, false);
    }

    public Bitmap get(Account account, int size, boolean cachedOnly) {
        final String KEY = key(account, size);
        Bitmap avatar = mXmppConnectionService.getBitmapCache().get(KEY);
        if (avatar != null || cachedOnly) {
            return avatar;
        }
        avatar = mXmppConnectionService.getFileBackend().getAvatar(account.getAvatar(), size);
        if (avatar == null) {
            final String displayName = account.getDisplayName();
            final String jid = account.getJid().asBareJid().toEscapedString();
            if (QuickConversationsService.isQuicksy() && !TextUtils.isEmpty(displayName)) {
                avatar = get(displayName, jid, size, false);
            } else {
                avatar = get(jid, null, size, false);
            }
        }
        mXmppConnectionService.getBitmapCache().put(KEY, avatar);
        return avatar;
    }

    public Bitmap get(Message message, int size, boolean cachedOnly) {
        final Conversational conversation = message.getConversation();
        // omit the situation of Muc operation and go straight to the Next part
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            Contact c = message.getContact();
            if (c != null && (c.getProfilePhoto() != null || c.getAvatarFilename() != null)) {
                return get(c, size, cachedOnly);
            } else if (conversation instanceof Conversation && message.getConversation().getMode() == Conversation.MODE_MULTI) {
                // omit this part by now -- ignore the Muc option
            } else if (c != null) {
                return get(c, size, cachedOnly);
            }
            Jid tcp = message.getCounterpart();
            String seed = tcp != null ? tcp.asBareJid().toString() : null;
            return get(UIHelper.getMessageDisplayName(message), seed, size, cachedOnly);
        } else {
            return get(conversation.getAccount(), size, cachedOnly);
        }
    }

    public void clear(Account account) {
        synchronized (this.sizes) {
            for (Integer size : sizes) {
                this.mXmppConnectionService.getBitmapCache().remove(key(account, size));
            }
        }
    }

    private String key(Account account, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_ACCOUNT + "_" + account.getUuid() + "_"
                + String.valueOf(size);
    }

    public Bitmap get(final String name, String seed, final int size, boolean cachedOnly) {
        final String KEY = key(seed == null ? name : name+"\0"+seed, size);
        Bitmap bitmap = mXmppConnectionService.getBitmapCache().get(KEY);
        if (bitmap != null || cachedOnly) {
            return bitmap;
        }
        bitmap = getImpl(name, seed, size);
        mXmppConnectionService.getBitmapCache().put(KEY, bitmap);
        return bitmap;
    }

    private static Bitmap getImpl(final String name, final String seed, final int size) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        final String trimmedName = name == null ? "" : name.trim();
        drawTile(canvas, trimmedName, seed, 0, 0, size, size);
        return bitmap;
    }

    private String key(String name, int size) {
        synchronized (this.sizes) {
            if (!this.sizes.contains(size)) {
                this.sizes.add(size);
            }
        }
        return PREFIX_GENERIC + "_" + name + "_" + String.valueOf(size);
    }

    private static boolean drawTile(Canvas canvas, String letter, int tileColor, int left, int top, int right, int bottom) {
        letter = letter.toUpperCase(Locale.getDefault());
        Paint tilePaint = new Paint(), textPaint = new Paint();
        tilePaint.setColor(tileColor);
        textPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(FG_COLOR);
        textPaint.setTypeface(Typeface.create("sans-serif-light",
                Typeface.NORMAL));
        textPaint.setTextSize((float) ((right - left) * 0.8));
        Rect rect = new Rect();

        canvas.drawRect(new Rect(left, top, right, bottom), tilePaint);
        textPaint.getTextBounds(letter, 0, 1, rect);
        float width = textPaint.measureText(letter);
        canvas.drawText(letter, (right + left) / 2 - width / 2, (top + bottom)
                / 2 + rect.height() / 2, textPaint);
        return true;
    }

    private static boolean drawTile(Canvas canvas, String name, String seed, int left, int top, int right, int bottom) {
        if (name != null) {
            final String letter = name.equals(CHANNEL_SYMBOL) ? name : getFirstLetter(name);
            final int color = UIHelper.getColorForName(seed == null ? name : seed);
            drawTile(canvas, letter, color, left, top, right, bottom);
            return true;
        }
        return false;
    }

    private static String getFirstLetter(String name) {
        for (Character c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                return c.toString();
            }
        }
        return "X";
    }

    @Override
    public void onAdvancedStreamFeaturesAvailable(Account account) {

        // Leaving it bland code for future modification
    }

    private static String emptyOrNull(@Nullable Jid value) {
        return value == null ? "" : value.toString();
    }

    public interface Avatarable {
        @ColorInt int getAvatarBackgroundColor();
    }
}

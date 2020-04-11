package com.plutonem.xmpp.ui.adapter;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Conversational;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.services.MessageArchiveService;
import com.plutonem.xmpp.ui.XmppActivity;
import com.plutonem.xmpp.ui.text.DividerSpan;
import com.plutonem.xmpp.ui.text.QuoteSpan;
import com.plutonem.xmpp.ui.util.AvatarWorkerTask;
import com.plutonem.xmpp.ui.util.MyLinkify;
import com.plutonem.xmpp.ui.widget.ClickableMovementMethod;
import com.plutonem.xmpp.ui.widget.CopyTextView;
import com.plutonem.xmpp.utils.EmojiWrapper;
import com.plutonem.xmpp.utils.Emoticons;
import com.plutonem.xmpp.utils.StylingHelper;
import com.plutonem.xmpp.utils.UIHelper;
import com.plutonem.xmpp.xmpp.mam.MamReference;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

public class MessageAdapter extends ArrayAdapter<Message> implements CopyTextView.CopyHandler {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    private static final int SENT = 0;
    private static final int RECEIVED = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private final XmppActivity activity;
    private DisplayMetrics metrics;
    private boolean mUseGreenBackground = false;

    public MessageAdapter(XmppActivity activity, List<Message> messages) {
        super(activity, 0, messages);
        this.activity = activity;
        metrics = getContext().getResources().getDisplayMetrics();
        updatePreferences();
    }

    private static void resetClickListener(View... views) {
        for (View view : views) {
            view.setOnClickListener(null);
        }
    }

    @Override
    public int getViewTypeCount() {
        return 4;
    }

    private int getItemViewType(Message message) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getStatus() <= Message.STATUS_RECEIVED) {
            return RECEIVED;
        }

        return SENT;
    }

    @Override
    public int getItemViewType(int position) {
        return this.getItemViewType(getItem(position));
    }

    private int getMessageTextColor(boolean onDark, boolean primary) {
        if (onDark) {
            return ContextCompat.getColor(activity, primary ? R.color.white : R.color.white70);
        } else {
            return ContextCompat.getColor(activity, primary ? R.color.black87 : R.color.black54);
        }
    }

    private void displayStatus(ViewHolder viewHolder, Message message, int type, boolean darkBackground) {
        // skip File Transfer part.
        String info = null;
        boolean error = false;
        if (viewHolder.indicatorReceived != null) {
            viewHolder.indicatorReceived.setVisibility(View.GONE);
        }

        // skip Edit Indicator part.
        // skip File Transfer part.
        // skip Multi Mode part.
        switch (message.getMergedStatus()) {
            case Message.STATUS_WAITING:
                info = getContext().getString(R.string.waiting);
                break;
            case Message.STATUS_UNSEND:
                info = getContext().getString(R.string.sending);
                break;
            case Message.STATUS_SEND_RECEIVED:
            case Message.STATUS_SEND_DISPLAYED:
                viewHolder.indicatorReceived.setImageResource(darkBackground ? R.drawable.ic_done_white_18dp : R.drawable.ic_done_black_18dp);
                viewHolder.indicatorReceived.setAlpha(darkBackground ? 0.7f : 0.57f);
                viewHolder.indicatorReceived.setVisibility(View.VISIBLE);
                break;
            case Message.STATUS_SEND_FAILED:
                final String errorMessage = message.getErrorMessage();
                if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                    info = getContext().getString(R.string.cancelled);
                } else if (errorMessage != null) {
                    final String[] errorParts = errorMessage.split("\\u001f", 2);
                    if (errorParts.length == 2) {
                        info = getContext().getString(R.string.send_failed);
                    } else {
                        info = getContext().getString(R.string.send_failed);
                    }
                } else {
                    info = getContext().getString(R.string.send_failed);
                }
                error = true;
                break;
            default:
                break;
        }

        if (error && type == SENT) {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_Warning);
            }
        } else {
            if (darkBackground) {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption_OnDark);
            } else {
                viewHolder.time.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Caption);
            }
            viewHolder.time.setTextColor(this.getMessageTextColor(darkBackground, false));
        }

        // skip Encryption Service part

        final String formattedTime = UIHelper.readableTimeDifferenceFull(getContext(), message.getMergedTimeSent());
        final String bodyLanguage = message.getBodyLanguage();
        final String bodyLanguageInfo = bodyLanguage == null ? "" : String.format(" \u00B7 %s", bodyLanguage.toUpperCase(Locale.US));
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            if (info != null) {
                viewHolder.time.setText(formattedTime + " \u00B7 " + info + bodyLanguageInfo);
            } else {
                viewHolder.time.setText(formattedTime+bodyLanguageInfo);
            }
        } else {
            if (info != null) {
                if (error) {
                    viewHolder.time.setText(info + " \u00B7 " + formattedTime + bodyLanguageInfo);
                } else {
                    viewHolder.time.setText(info);
                }
            } else {
                viewHolder.time.setText(formattedTime+bodyLanguageInfo);
            }
        }
    }

    private void displayInfoMessage(ViewHolder viewHolder, CharSequence text, boolean darkBackground) {
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        viewHolder.messageBody.setText(text);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Secondary);
        }
        viewHolder.messageBody.setTextIsSelectable(false);
    }

    private void displayEmojiMessage(final ViewHolder viewHolder, final String body, final boolean darkBackground) {
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_Emoji);
        }
        Spannable span = new SpannableString(body);
        float size = Emoticons.isEmoji(body) ? 3.0f : 2.0f;
        span.setSpan(new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody.setText(EmojiWrapper.transform(span));
    }

    private void displayTextMessage(final ViewHolder viewHolder, final Message message, boolean darkBackground, int type) {
        viewHolder.messageBody.setVisibility(View.VISIBLE);
        if (darkBackground) {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1_OnDark);
        } else {
            viewHolder.messageBody.setTextAppearance(getContext(), R.style.TextAppearance_Conversations_Body1);
        }
        viewHolder.messageBody.setHighlightColor(ContextCompat.getColor(activity, darkBackground
                ? (type == SENT || !mUseGreenBackground ? R.color.black26 : R.color.grey800) : R.color.grey500));
        viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
        if (message.getBody() != null) {
            // skip Me Command part.
            SpannableStringBuilder body = message.getMergedBody();
            if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
                body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
                body.append("\u2026");
            }
            Message.MergeSeparator[] mergeSeparators = body.getSpans(0, body.length(), Message.MergeSeparator.class);
            for (Message.MergeSeparator mergeSeparator : mergeSeparators) {
                int start = body.getSpanStart(mergeSeparator);
                int end = body.getSpanEnd(mergeSeparator);
                body.setSpan(new DividerSpan(true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // skip Text Quotes part.
            // skip Private Message part.
            // skip Multi Mode part.
            Matcher matcher = Emoticons.getEmojiPattern(body).matcher(body);
            while (matcher.find()) {
                if (matcher.start() < matcher.end()) {
                    body.setSpan(new RelativeSizeSpan(1.2f), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            StylingHelper.format(body, viewHolder.messageBody.getCurrentTextColor());
            // skip Highlighted Text part.
            MyLinkify.addLinks(body,true);
            viewHolder.messageBody.setAutoLinkMask(0);
            viewHolder.messageBody.setText(EmojiWrapper.transform(body));
            viewHolder.messageBody.setTextIsSelectable(true);
            viewHolder.messageBody.setMovementMethod(ClickableMovementMethod.getInstance());
            // skip Text Quotes part.
        } else {
            viewHolder.messageBody.setText("");
            viewHolder.messageBody.setTextIsSelectable(false);
        }
    }

    private void loadMoreMessages(Conversation conversation) {
        conversation.setLastClearHistory(0, null);
        activity.xmppConnectionService.updateConversation(conversation);
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        MessageArchiveService.Query query = activity.xmppConnectionService.getMessageArchiveService().query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(activity, R.string.not_fetching_history_retention_period, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        final Message message = getItem(position);
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession = message.isValidInSession() && (!omemoEncryption || message.isTrusted());
        final Conversational conversation = message.getConversation();
        final Account account = conversation.getAccount();
        final int type = getItemViewType(position);
        ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            switch (type) {
                case DATE_SEPARATOR:
                    view = activity.getLayoutInflater().inflate(R.layout.message_date_bubble, parent, false);
                    viewHolder.status_message = view.findViewById(R.id.message_body);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    break;
                case SENT:
                    view = activity.getLayoutInflater().inflate(R.layout.message_sent, parent, false);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case RECEIVED:
                    view = activity.getLayoutInflater().inflate(R.layout.message_received, parent, false);
                    viewHolder.message_box = view.findViewById(R.id.message_box);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.messageBody = view.findViewById(R.id.message_body);
                    viewHolder.time = view.findViewById(R.id.message_time);
                    viewHolder.indicatorReceived = view.findViewById(R.id.indicator_received);
                    break;
                case STATUS:
                    view = activity.getLayoutInflater().inflate(R.layout.message_status, parent, false);
                    viewHolder.contact_picture = view.findViewById(R.id.message_photo);
                    viewHolder.status_message = view.findViewById(R.id.status_message);
                    viewHolder.load_more_messages = view.findViewById(R.id.load_more_messages);
                    break;
                default:
                    throw new AssertionError("Unknown view type");
            }

            if (viewHolder.messageBody != null) {
                viewHolder.messageBody.setCopyHandler(this);
            }
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
            if (viewHolder == null) {
                return view;
            }
        }

        boolean darkBackground = type == RECEIVED && (!isInValidSession || mUseGreenBackground) || activity.isDarkTheme();

        if (type == DATE_SEPARATOR) {
            if (UIHelper.today(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.today);
            } else if (UIHelper.yesterday(message.getTimeSent())) {
                viewHolder.status_message.setText(R.string.yesterday);
            } else {
                viewHolder.status_message.setText(DateUtils.formatDateTime(activity, message.getTimeSent(), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
            }
            viewHolder.message_box.setBackgroundResource(R.drawable.date_bubble_white);
            return view;
        } else if (type == STATUS) {
            if ("LOAD_MORE".equals(message.getBody())) {
                viewHolder.status_message.setVisibility(View.GONE);
                viewHolder.contact_picture.setVisibility(View.GONE);
                viewHolder.load_more_messages.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setOnClickListener(v -> loadMoreMessages((Conversation) message.getConversation()));
            } else {
                viewHolder.status_message.setVisibility(View.VISIBLE);
                viewHolder.load_more_messages.setVisibility(View.GONE);
                viewHolder.status_message.setText(message.getBody());

                // skip Multi User Chat part.
                boolean showAvatar;
                if (conversation.getMode() == Conversation.MODE_SINGLE) {
                    showAvatar = true;
                    AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar_on_status_message);
                } else {
                    showAvatar = false;
                }
                if (showAvatar) {
                    viewHolder.contact_picture.setAlpha(0.5f);
                    viewHolder.contact_picture.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.contact_picture.setVisibility(View.GONE);
                }
            }
            return view;
        } else {
            AvatarWorkerTask.loadAvatar(message, viewHolder.contact_picture, R.dimen.avatar);
        }

        resetClickListener(viewHolder.message_box, viewHolder.messageBody);

        // skip Contact Picture Click and Long Click part

        // skip File Message and oob Transfer part.
        // skip Axolotl or Pgp Encryption Service part.
        // skip Geo Location Message part.
        if (message.isDeleted()) {
            displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity, message).first, darkBackground);
        } else {
            if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                displayEmojiMessage(viewHolder, message.getBody().trim(), darkBackground);
            } else {
                displayTextMessage(viewHolder, message, darkBackground, type);
            }
        }

        if (type == RECEIVED) {
            if (isInValidSession) {
                int bubble;
                if (!mUseGreenBackground) {
                    bubble = activity.getThemeResource(R.attr.message_bubble_received_monochrome, R.drawable.message_bubble_received_white);
                } else {
                    bubble = activity.getThemeResource(R.attr.message_bubble_received_green, R.drawable.message_bubble_received);
                }
                viewHolder.message_box.setBackgroundResource(bubble);
            } else {
                viewHolder.message_box.setBackgroundResource(R.drawable.message_bubble_received_warning);
            }
        }

        displayStatus(viewHolder, message, type, darkBackground);

        return view;
    }

    @Override
    public void notifyDataSetChanged() {
        // skip Quote Text part.
        super.notifyDataSetChanged();
    }

    private String transformText(CharSequence text, int start, int end, boolean forCopy) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object copySpan = new Object();
        builder.setSpan(copySpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        DividerSpan[] dividerSpans = builder.getSpans(0, builder.length(), DividerSpan.class);
        for (DividerSpan dividerSpan : dividerSpans) {
            builder.replace(builder.getSpanStart(dividerSpan), builder.getSpanEnd(dividerSpan),
                    dividerSpan.isLarge() ? "\n\n" : "\n");
        }
        start = builder.getSpanStart(copySpan);
        end = builder.getSpanEnd(copySpan);
        if (start == -1 || end == -1) return "";
        builder = new SpannableStringBuilder(builder, start, end);
        if (forCopy) {
            QuoteSpan[] quoteSpans = builder.getSpans(0, builder.length(), QuoteSpan.class);
            for (QuoteSpan quoteSpan : quoteSpans) {
                builder.insert(builder.getSpanStart(quoteSpan), "> ");
            }
        }
        return builder.toString();
    }


    @Override
    public String transformTextForCopy(CharSequence text, int start, int end) {
        if (text instanceof Spanned) {
            return transformText(text, start, end, true);
        } else {
            return text.toString().substring(start, end);
        }
    }

    public void updatePreferences() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        this.mUseGreenBackground = p.getBoolean("use_green_background", activity.getResources().getBoolean(R.bool.use_green_background));
    }

    private static class ViewHolder {
        public Button load_more_messages;
        protected LinearLayout message_box;
        protected ImageView indicatorReceived;
        protected TextView time;
        protected CopyTextView messageBody;
        protected ImageView contact_picture;
        protected TextView status_message;
    }
}

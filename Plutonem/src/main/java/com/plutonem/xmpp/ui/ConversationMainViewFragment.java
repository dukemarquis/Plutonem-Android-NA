package com.plutonem.xmpp.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.databinding.FragmentConversationBinding;
import com.plutonem.databinding.FragmentConversationMainViewBinding;
import com.plutonem.ui.main.BottomNavController;
import com.plutonem.ui.main.PMainActivity;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.entities.Conversational;
import com.plutonem.xmpp.entities.Message;
import com.plutonem.xmpp.entities.Presence;
import com.plutonem.xmpp.services.MessageArchiveService;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.ui.adapter.MessageAdapter;
import com.plutonem.xmpp.ui.util.DateSeparator;
import com.plutonem.xmpp.ui.util.ListViewUtils;
import com.plutonem.xmpp.ui.util.PendingItem;
import com.plutonem.xmpp.ui.util.ScrollState;
import com.plutonem.xmpp.ui.util.SendButtonAction;
import com.plutonem.xmpp.ui.util.SendButtonTool;
import com.plutonem.xmpp.ui.widget.EditMessage;
import com.plutonem.xmpp.utils.EmojiWrapper;
import com.plutonem.xmpp.utils.QuickLoader;
import com.plutonem.xmpp.utils.StylingHelper;
import com.plutonem.xmpp.utils.UIHelper;
import com.plutonem.xmpp.xmpp.XmppConnection;
import com.plutonem.xmpp.xmpp.chatstate.ChatState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.plutonem.xmpp.ui.util.SoftKeyboardUtils.hideSoftKeyboard;

public class ConversationMainViewFragment extends XmppFragment implements EditMessage.KeyboardListener {

    public static final String RECENTLY_USED_QUICK_ACTION = "recently_used_quick_action";
    public static final String STATE_CONVERSATION_UUID = ConversationMainViewFragment.class.getName() + ".uuid";
    public static final String STATE_SCROLL_POSITION = ConversationMainViewFragment.class.getName() + ".scroll_position";
    private static final String STATE_LAST_MESSAGE_UUID = "state_last_message_uuid";

    private final List<Message> messageList = new ArrayList<>();
    private final PendingItem<String> pendingConversationsUuid = new PendingItem<>();
    private final PendingItem<Bundle> pendingExtras = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private final PendingItem<String> pendingLastMessageUuid = new PendingItem<>();
    protected MessageAdapter messageListAdapter;
    private String lastMessageUuid = null;
    private Conversation conversation;
    private FragmentConversationMainViewBinding binding;
    private Toast messageLoaderToast;
    private PMainActivity activity;
    private boolean reInitRequiredOnStart = true;

    private Toolbar mToolbar = null;
    private BottomNavController mBottomNavController;

    private AbsListView.OnScrollListener mOnScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState) {
                fireReadEvent();
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            toggleScrollDownButton(view);
            synchronized (ConversationMainViewFragment.this.messageList) {
                if (firstVisibleItem < 5 && conversation != null && conversation.messagesLoaded.compareAndSet(true, false) && messageList.size() > 0) {
                    long timestamp;
                    if (messageList.get(0).getType() == Message.TYPE_STATUS && messageList.size() >= 2) {
                        timestamp = messageList.get(1).getTimeSent();
                    } else {
                        timestamp = messageList.get(0).getTimeSent();
                    }

                    activity.xmppConnectionService.loadMoreMessages(conversation, timestamp, new XmppConnectionService.OnMoreMessagesLoaded() {
                        @Override
                        public void onMoreMessagesLoaded(final int c, final Conversation conversation) {
                            if (ConversationMainViewFragment.this.conversation != conversation) {
                                conversation.messagesLoaded.set(true);
                                return;
                            }
                            runOnUiThread(() -> {
                                synchronized (messageList) {
                                    final int oldPosition = binding.messagesView.getFirstVisiblePosition();
                                    Message message = null;
                                    int childPos;
                                    for (childPos = 0; childPos + oldPosition < messageList.size(); ++childPos) {
                                        message = messageList.get(oldPosition + childPos);
                                        if (message.getType() != Message.TYPE_STATUS) {
                                            break;
                                        }
                                    }
                                    final String uuid = message != null ? message.getUuid() : null;
                                    View v = binding.messagesView.getChildAt(childPos);
                                    final int pxOffset = (v == null) ? 0 : v.getTop();
                                    ConversationMainViewFragment.this.conversation.populateWithMessages(ConversationMainViewFragment.this.messageList);
                                    try {
                                        updateStatusMessages();
                                    } catch (IllegalStateException e) {
                                        Log.d(Config.LOGTAG, "caught illegal state exception while updating status messages");
                                    }
                                    messageListAdapter.notifyDataSetChanged();
                                    int pos = Math.max(getIndexOf(uuid, messageList), 0);
                                    binding.messagesView.setSelectionFromTop(pos, pxOffset);
                                    if (messageLoaderToast != null) {
                                        messageLoaderToast.cancel();
                                    }
                                    conversation.messagesLoaded.set(true);
                                }
                            });
                        }

                        @Override
                        public void informUser(final int resId) {

                            runOnUiThread(() -> {
                                if (messageLoaderToast != null) {
                                    messageLoaderToast.cancel();
                                }
                                if (ConversationMainViewFragment.this.conversation != conversation) {
                                    return;
                                }
                                messageLoaderToast = Toast.makeText(view.getContext(), resId, Toast.LENGTH_LONG);
                                messageLoaderToast.show();
                            });

                        }
                    });
                }
            }
        }
    };

    private TextView.OnEditorActionListener mEditorActionListener = (v, actionId, event) -> {
        if (actionId == EditorInfo.IME_ACTION_SEND) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && imm.isFullscreenMode()) {
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
            sendMessage();
            return true;
        } else {
            return false;
        }
    };

    private View.OnClickListener mScrollButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            stopScrolling();
            setSelection(binding.messagesView.getCount() - 1, true);
        }
    };

    private View.OnClickListener mSendButtonListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // since we only have text sending mission in this part now, we could ignore the other formation process
            // such as, TAKE_PHONE && RECORD_VIDEO && SEND_LOCATION && CHOOSE_PICTURE && CANCEL.
            Object tag = v.getTag();
            if (tag instanceof SendButtonAction) {
                sendMessage();
            } else {
                sendMessage();
            }
        }
    };

    private static boolean scrolledToBottom(AbsListView listView) {
        final int count = listView.getCount();
        if (count == 0) {
            return true;
        } else if (listView.getLastVisiblePosition() == count - 1) {
            final View lastChild = listView.getChildAt(listView.getChildCount() - 1);
            return lastChild != null && lastChild.getBottom() <= listView.getHeight();
        } else {
            return false;
        }
    }

    private void toggleScrollDownButton() {
        toggleScrollDownButton(binding.messagesView);
    }

    private void toggleScrollDownButton(AbsListView listView) {
        if (conversation == null) {
            return;
        }
        if (scrolledToBottom(listView)) {
            lastMessageUuid = null;
            hideUnreadMessagesCount();
        } else {
            binding.scrollToBottomButton.setEnabled(true);
            binding.scrollToBottomButton.show();
            if (lastMessageUuid == null) {
                lastMessageUuid = conversation.getLatestMessage().getUuid();
            }
            if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) > 0) {
                binding.unreadCountCustomView.setVisibility(View.VISIBLE);
            }
        }
    }

    private int getIndexOf(String uuid, List<Message> messages) {
        if (uuid == null) {
            return messages.size() - 1;
        }
        for (int i = 0; i < messages.size(); ++i) {
            if (uuid.equals(messages.get(i).getUuid())) {
                return i;
            } else {
                Message next = messages.get(i);
                while (next != null && next.wasMergedIntoPrevious()) {
                    if (uuid.equals(next.getUuid())) {
                        return i;
                    }
                    next = next.next();
                }

            }
        }
        return -1;
    }

    private ScrollState getScrollPosition() {
        final ListView listView = this.binding == null ? null : this.binding.messagesView;
        if (listView == null || listView.getCount() == 0 || listView.getLastVisiblePosition() == listView.getCount() - 1) {
            return null;
        } else {
            final int pos = listView.getFirstVisiblePosition();
            final View view = listView.getChildAt(0);
            if (view == null) {
                return null;
            } else {
                return new ScrollState(pos, view.getTop());
            }
        }
    }

    private void setScrollPosition(ScrollState scrollPosition, String lastMessageUuid) {
        if (scrollPosition != null) {
            this.lastMessageUuid = lastMessageUuid;
            if (lastMessageUuid != null) {
                binding.unreadCountCustomView.setUnreadCount(conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
            }
            //TODO maybe this needs a 'post'
            this.binding.messagesView.setSelectionFromTop(scrollPosition.position, scrollPosition.offset);
            toggleScrollDownButton();
        }
    }

    private void sendMessage() {
        final Editable text = this.binding.textinput.getText();
        final String body = text == null ? "" : text.toString();
        final Conversation conversation = this.conversation;
        if (body.length() == 0 || conversation == null) {
            return;
        }

        final Message message;
        message = new Message(conversation, body, conversation.getNextEncryption());

        sendMessage(message);
    }

    public void updateChatMsgHint() {
        this.binding.textInputHint.setVisibility(View.GONE);
        this.binding.textinput.setHint(UIHelper.getMessageHint(getActivity(), conversation));
        getActivity().invalidateOptionsMenu();
    }

    public void  setupIme() {
        this.binding.textinput.refreshIme();
    }

    public void toggleInputMethod() {
        binding.textinput.setVisibility(View.VISIBLE);
        updateSendButton();
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        Log.d(Config.LOGTAG, "ConversationMainViewFragment.onAttach()");

        if (context instanceof PMainActivity) {
            this.activity = (PMainActivity) context;
        } else {
            throw new IllegalStateException("Trying to attach fragment to activity that is not the PMainActivity");
        }

        // detect the bottom nav controller when this fragment is hosted in the main activity - this is used to
        // hide the bottom nav when the user clicks conversation item
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // skip TextInput Rich Content part.

        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversation_main_view, container, false);
        binding.getRoot().setOnClickListener(null);

        binding.textinput.addTextChangedListener(new StylingHelper.MessageEditorStyler(binding.textinput));
        binding.textinput.setOnEditorActionListener(mEditorActionListener);

        binding.textSendButton.setOnClickListener(this.mSendButtonListener);
        binding.scrollToBottomButton.setOnClickListener(this.mScrollButtonListener);
        binding.messagesView.setOnScrollListener(mOnScrollListener);
        binding.messagesView.setTranscriptMode(ListView.TRANSCRIPT_MODE_NORMAL);
        messageListAdapter = new MessageAdapter((XmppActivity) getActivity(), this.messageList);
        binding.messagesView.setAdapter(messageListAdapter);

        // this is not so concise as it should, probably we will change this to better method later.
        mToolbar = this.binding.toolbarMain;
        ((AppCompatActivity) getActivity()).setSupportActionBar((mToolbar));

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(EmojiWrapper.transform(getConversation().getName()));
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        return binding.getRoot();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.messagesView.post(this::fireReadEvent);
    }

    private void fireReadEvent() {
        if (activity != null && this.conversation != null) {
            String uuid = getLastVisibleMessageUuid();
            if (uuid != null) {
                activity.onConversationRead(this.conversation, uuid);
            }
        }
    }

    private String getLastVisibleMessageUuid() {
        if (binding == null) {
            return null;
        }
        synchronized (this.messageList) {
            int pos = binding.messagesView.getLastVisiblePosition();
            if (pos > 0) {
                Message message = null;
                for (int i = pos; i >= 0; --i) {
                    try {
                        message = (Message) binding.messagesView.getItemAtPosition(i);
                    } catch (IndexOutOfBoundsException e) {
                        // should not happen if we synchronize properly. however if that fails we just gonna try item -1
                        continue;
                    }
                    if (message.getType() != Message.TYPE_STATUS) {
                        break;
                    }
                }
                if (message != null) {
                    while (message.next() != null && message.next().wasMergedIntoPrevious()) {
                        message = message.next();
                    }
                    return message.getUuid();
                }
            }
        }
        return null;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        final Activity activity = getActivity();
        if (activity instanceof PMainActivity) {
            ((PMainActivity) activity).clearPendingViewIntent();
        }
        super.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (conversation != null) {
            outState.putString(STATE_CONVERSATION_UUID, conversation.getUuid());
            outState.putString(STATE_LAST_MESSAGE_UUID, lastMessageUuid);
            final ScrollState scrollState = getScrollPosition();
            if (scrollState != null) {
                outState.putParcelable(STATE_SCROLL_POSITION, scrollState);
            }
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        String uuid = savedInstanceState.getString(STATE_CONVERSATION_UUID);
        pendingLastMessageUuid.push(savedInstanceState.getString(STATE_LAST_MESSAGE_UUID, null));
        if (uuid != null) {
            QuickLoader.set(uuid);
            this.pendingConversationsUuid.push(uuid);
            pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.reInitRequiredOnStart && this.conversation != null) {
            final Bundle extras = pendingExtras.pop();
            reInit(this.conversation, extras != null);
            if (extras != null) {
                processExtras(extras);
            }
        } else if (conversation == null && activity != null && activity.xmppConnectionService != null) {
            final String uuid = pendingConversationsUuid.pop();
            Log.d(Config.LOGTAG, "ConversationMainViewFragment.onStart() - activity was bound but no conversation loaded. uuid=" + uuid);
            if (uuid != null) {
                findAndReInitByUuidOrArchive(uuid);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        final Activity activity = getActivity();
        if (activity == null || !activity.isChangingConfigurations()) {
            hideSoftKeyboard(activity);
        }
        if (this.conversation != null) {
            final String msg = this.binding.textinput.getText().toString();
            storeNextMessage(msg);
            updateChatState(this.conversation, msg);
            this.activity.xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        this.reInitRequiredOnStart = true;
    }

    private void updateChatState(final Conversation conversation, final String msg) {
        ChatState state = msg.length() == 0 ? Config.DEFAULT_CHAT_STATE : ChatState.PAUSED;
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(state)) {
            activity.xmppConnectionService.sendChatState(conversation);
        }
    }

    private void saveMessageDraft() {
        final Conversation previousConversation = this.conversation;
        if (this.activity == null || this.binding == null || previousConversation == null) {
            return;
        }
        Log.d(Config.LOGTAG, "ConversationMainViewFragment.saveMessageDraftStopAudioPlayer()");
        final String msg = this.binding.textinput.getText().toString();
        storeNextMessage(msg);
        updateChatState(this.conversation, msg);
        toggleInputMethod();
    }

    public void reInit(Conversation conversation, Bundle extras) {
        QuickLoader.set(conversation.getUuid());
        this.saveMessageDraft();
        this.clearPending();
        if (this.reInit(conversation, extras != null)) {
            if (extras != null) {
                processExtras(extras);
            }
            this.reInitRequiredOnStart = false;
        } else {
            this.reInitRequiredOnStart = true;
            pendingExtras.push(extras);
        }
        resetUnreadMessagesCount();
    }

    private void reInit(Conversation conversation) {
        reInit(conversation ,false);
    }

    private boolean reInit(final Conversation conversation, final boolean hasExtras) {
        if (conversation == null) {
            return false;
        }
        this.conversation = conversation;
        //once we set the conversation all is good and it will automatically do the right thing in onStart()
        if (this.activity == null || this.binding == null) {
            return false;
        }

        if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
            activity.onConversationArchived(this.conversation);
            return false;
        }

        stopScrolling();
        Log.d(Config.LOGTAG, "reInit(hasExtras=" + Boolean.toString(hasExtras) + ")");

        if (this.conversation.isRead() && hasExtras) {
            Log.d(Config.LOGTAG, "trimming conversation");
            this.conversation.trim();
        }

        setupIme();

        final boolean scrolledToBottomAndNoPending = this.scrolledToBottom() && pendingScrollState.peek() == null;

        this.binding.textSendButton.setContentDescription(activity.getString(R.string.send_message_to_x, conversation.getName()));
        this.binding.textinput.setKeyboardListener(null);
        this.binding.textinput.setText("");
        final boolean participating = conversation.getMode() == Conversational.MODE_SINGLE;
        if (participating) {
            this.binding.textinput.append(this.conversation.getNextMessage());
        }
        this.binding.textinput.setKeyboardListener(this);
        messageListAdapter.updatePreferences();
        refresh(false);
        this.conversation.messagesLoaded.set(true);
        Log.d(Config.LOGTAG, "scrolledToBottomAndNoPending=" + Boolean.toString(scrolledToBottomAndNoPending));

        if (hasExtras || scrolledToBottomAndNoPending) {
            resetUnreadMessagesCount();
            synchronized (this.messageList) {
                Log.d(Config.LOGTAG, "jump to first unread message");
                final Message first = conversation.getFirstUnreadMessage();
                final int bottom = Math.max(0, this.messageList.size() - 1);
                final int pos;
                final boolean jumpToBottom;
                if (first == null) {
                    pos = bottom;
                    jumpToBottom = true;
                } else {
                    int i = getIndexOf(first.getUuid(), this.messageList);
                    pos = i < 0 ? bottom : i;
                    jumpToBottom = false;
                }
                setSelection(pos, jumpToBottom);
            }
        }

        this.binding.messagesView.post(this::fireReadEvent);
        //TODO if we only do this when this fragment is running on main it won't *bing* in tablet layout which might be unnecessary since we can *see* it
        activity.xmppConnectionService.getNotificationService().setOpenConversation(this.conversation);
        return true;
    }

    private void resetUnreadMessagesCount() {
        lastMessageUuid = null;
        hideUnreadMessagesCount();
    }

    private void hideUnreadMessagesCount() {
        if (this.binding == null) {
            return;
        }
        this.binding.scrollToBottomButton.setEnabled(false);
        this.binding.scrollToBottomButton.hide();
        this.binding.unreadCountCustomView.setVisibility(View.GONE);
    }

    private void setSelection(int pos, boolean jumpToBottom) {
        ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom);
        this.binding.messagesView.post(() -> ListViewUtils.setSelection(this.binding.messagesView, pos, jumpToBottom));
        this.binding.messagesView.post(this::fireReadEvent);
    }

    private boolean scrolledToBottom() {
        return this.binding != null && scrolledToBottom(this.binding.messagesView);
    }

    private void processExtras(Bundle extras) {

    }

    private void updateSnackBar(final Conversation conversation) {
        hideSnackBar();
    }

    @Override
    public void refresh() {
        if (this.binding == null) {
            Log.d(Config.LOGTAG, "ConversationMainViewFragment.refresh() skipped updated because view binding was null");
            return;
        }
        if (this.conversation != null && this.activity != null && this.activity.xmppConnectionService != null) {
            if (!activity.xmppConnectionService.isConversationStillOpen(this.conversation)) {
                activity.onConversationArchived(this.conversation);
                return;
            }
        }
        this.refresh(true);
    }

    private void refresh(boolean notifyConversationRead) {
        synchronized (this.messageList) {
            if (this.conversation != null) {
                conversation.populateWithMessages(this.messageList);
                updateSnackBar(conversation);
                updateStatusMessages();
                if (conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid) != 0) {
                    binding.unreadCountCustomView.setVisibility(View.VISIBLE);
                    binding.unreadCountCustomView.setUnreadCount(conversation.getReceivedMessagesCountSinceUuid(lastMessageUuid));
                }
                this.messageListAdapter.notifyDataSetChanged();
                updateChatMsgHint();
                if (notifyConversationRead && activity != null) {
                    binding.messagesView.post(this::fireReadEvent);
                }
                updateSendButton();
                updateEditability();
                activity.invalidateOptionsMenu();
            }
        }
    }

    protected void messageSent() {
        this.binding.textinput.setText("");
        storeNextMessage();
        updateChatMsgHint();
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean prefScrollToBottom = p.getBoolean("scroll_to_bottom", activity.getResources().getBoolean(R.bool.scroll_to_bottom));
        if (prefScrollToBottom || scrolledToBottom()) {
            new Handler().post(() -> {
                int size = messageList.size();
                this.binding.messagesView.setSelection(size - 1);
            });
        }
    }

    private boolean storeNextMessage() {
        return storeNextMessage(this.binding.textinput.getText().toString());
    }

    private boolean storeNextMessage(String msg) {
        final boolean participating = conversation.getMode() == Conversational.MODE_SINGLE;
        if (this.conversation.getStatus() != Conversation.STATUS_ARCHIVED && participating && this.conversation.setNextMessage(msg)) {
            this.activity.xmppConnectionService.updateConversation(this.conversation);
            return true;
        }
        return false;
    }

    private void updateEditability() {
        boolean canWrite = this.conversation.getMode() == Conversation.MODE_SINGLE;
        this.binding.textinput.setFocusable(canWrite);
        this.binding.textinput.setFocusableInTouchMode(canWrite);
        this.binding.textSendButton.setEnabled(canWrite);
        this.binding.textinput.setCursorVisible(canWrite);
        this.binding.textinput.setEnabled(canWrite);
    }

    public void updateSendButton() {
        final Conversation c = this.conversation;
        final Presence.Status status;
        final String text = this.binding.textinput == null ? "" : this.binding.textinput.getText().toString();
        final SendButtonAction action = SendButtonTool.getAction(getActivity(), c, text);
        if (c.getAccount().getStatus() == Account.State.ONLINE) {
            if (activity != null && activity.xmppConnectionService != null && activity.xmppConnectionService.getMessageArchiveService().isCatchingUp(c)) {
                status = Presence.Status.OFFLINE;
            } else {
                status = Presence.Status.OFFLINE;
            }
        } else {
            status = Presence.Status.OFFLINE;
        }
        this.binding.textSendButton.setTag(action);
        final Activity activity = getActivity();
        if (activity != null) {
            this.binding.textSendButton.setImageResource(SendButtonTool.getSendButtonImageResource(activity, action, status));
        }
    }

    protected void updateStatusMessages() {
        DateSeparator.addAll(this.messageList);
        if (showLoadMoreMessages(conversation)) {
            this.messageList.add(0, Message.createLoadMoreMessage(conversation));
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            ChatState state = conversation.getIncomingChatState();
            if (state == ChatState.COMPOSING) {
                this.messageList.add(Message.createStatusMessage(conversation, getString(R.string.contact_is_typing, conversation.getName())));
            } else if (state == ChatState.PAUSED) {
                this.messageList.add(Message.createStatusMessage(conversation, getString(R.string.contact_has_stopped_typing, conversation.getName())));
            } else {
                for (int i = this.messageList.size() - 1; i >= 0; --i) {
                    final Message message = this.messageList.get(i);
                    if (message.getType() != Message.TYPE_STATUS) {
                        if (message.getStatus() == Message.STATUS_RECEIVED) {
                            return;
                        } else {
                            if (message.getStatus() == Message.STATUS_SEND_DISPLAYED) {
                                this.messageList.add(i + 1,
                                        Message.createStatusMessage(conversation, getString(R.string.contact_has_read_up_to_this_point, conversation.getName())));
                                return;
                            }
                        }
                    }
                }
            }
        } else {
            // skip the logic about Multi User Chat conversation mode.
        }
    }

    private void stopScrolling() {
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        binding.messagesView.dispatchTouchEvent(cancel);
    }

    private boolean showLoadMoreMessages(final Conversation c) {
        if (activity == null || activity.xmppConnectionService == null) {
            return false;
        }
        final boolean mam = hasMamSupport(c) && !c.getContact().isBlocked();
        final MessageArchiveService service = activity.xmppConnectionService.getMessageArchiveService();
        return mam && (c.getLastClearHistory().getTimestamp() != 0 || (c.countMessages() == 0 && c.messagesLoaded.get() && c.hasMessagesLeftOnServer() && !service.queryInProgress(c)));
    }

    private boolean hasMamSupport(final Conversation c) {
        if (c.getMode() == Conversation.MODE_SINGLE) {
            final XmppConnection connection = c.getAccount().getXmppConnection();
            return connection != null && connection.getFeatures().mam();
        } else {
            // set the value which relate to Multi User Chat to false by default.
            return false;
        }
    }

    protected void hideSnackBar() {
        this.binding.snackbar.setVisibility(View.GONE);
    }

    protected void sendMessage(Message message) {
        activity.xmppConnectionService.sendMessage(message);
        messageSent();
    }

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.COMPOSING)) {
            service.sendChatState(conversation);
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTypingStopped() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(ChatState.PAUSED)) {
            service.sendChatState(conversation);
        }
    }

    @Override
    public void onTextDeleted() {
        final XmppConnectionService service = activity == null ? null : activity.xmppConnectionService;
        if (service == null) {
            return;
        }
        Account.State status = conversation.getAccount().getStatus();
        if (status == Account.State.ONLINE && conversation.setOutgoingChatState(Config.DEFAULT_CHAT_STATE)) {
            service.sendChatState(conversation);
        }
        if(storeNextMessage()) {
            runOnUiThread(() -> {
                if (activity == null) {
                    return;
                }
                activity.onConversationsListItemUpdated();
            });
        }
        runOnUiThread(this::updateSendButton);
    }

    @Override
    public void onTextChanged() {

    }

    @Override
    public boolean onTabPressed(boolean repeated) {
        // skip Multi User Chat part.
        return false;
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationMainViewFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            if (!findAndReInitByUuidOrArchive(uuid)) {
                return;
            }
        } else {
            if (!activity.xmppConnectionService.isConversationStillOpen(conversation)) {
                clearPending();
                activity.onConversationArchived(conversation);
                return;
            }
        }
        clearPending();
    }

    private boolean findAndReInitByUuidOrArchive(@NonNull final String uuid) {
        Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
        if (conversation == null) {
            clearPending();
            activity.onConversationArchived(null);
            return false;
        }
        reInit(conversation);
        ScrollState scrollState = pendingScrollState.pop();
        String lastMessageUuid = pendingLastMessageUuid.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState, lastMessageUuid);
        }
        return true;
    }

    private void clearPending() {
        if (pendingScrollState.clear()) {
            Log.e(Config.LOGTAG, "cleared scroll state");
        }
        if (pendingConversationsUuid.clear()) {
            Log.e(Config.LOGTAG,"cleared pending conversations uuid");
        }
    }

    public Conversation getConversation() {
        return conversation;
    }
}

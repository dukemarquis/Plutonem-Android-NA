package com.plutonem.xmpp.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.databinding.FragmentConversationsOverviewBinding;
import com.plutonem.ui.main.BottomNavController;
import com.plutonem.ui.main.MainToolbarFragment;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.ui.adapter.ConversationAdapter;
import com.plutonem.xmpp.ui.interfaces.OnConversationArchived;
import com.plutonem.xmpp.ui.interfaces.OnConversationSelected;
import com.plutonem.xmpp.ui.util.PendingActionHelper;
import com.plutonem.xmpp.ui.util.PendingItem;
import com.plutonem.xmpp.ui.util.ScrollState;
import com.plutonem.xmpp.ui.util.StyledAttributes;
import com.plutonem.xmpp.utils.EmojiWrapper;
import com.plutonem.xmpp.utils.ThemeHelper;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

public class ConversationsOverviewFragment extends XmppFragment implements MainToolbarFragment {

    private static final String STATE_SCROLL_POSITION = ConversationsOverviewFragment.class.getName() + ".scroll_state";

    private final List<Conversation> conversations = new ArrayList<>();
    private final PendingItem<Conversation> swipedConversation = new PendingItem<>();
    private final PendingItem<ScrollState> pendingScrollState = new PendingItem<>();
    private FragmentConversationsOverviewBinding binding;
    private ConversationAdapter conversationAdapter;
    private XmppActivity activity;
    private float mSwipeEscapeVelocity = 0f;
    private PendingActionHelper pendingActionHelper = new PendingActionHelper();

    @NonNull
    private Toolbar mToolbar = null;
    private String mToolbarTitle;

    private BottomNavController mBottomNavController;

    private ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, LEFT|RIGHT) {
        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
            // todo maybe we can manually changing the position of the conversation
            return false;
        }

        @Override
        public float getSwipeEscapeVelocity (float defaultValue) {
            return mSwipeEscapeVelocity;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                Paint paint = new Paint();
                paint.setColor(StyledAttributes.getColor(activity, R.attr.conversations_overview_background));
                paint.setStyle(Paint.Style.FILL);
                c.drawRect(viewHolder.itemView.getLeft(), viewHolder.itemView.getTop(),
                            viewHolder.itemView.getRight(), viewHolder.itemView.getBottom(), paint);
            }
        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setAlpha(1f);
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            pendingActionHelper.execute();
            int position = viewHolder.getLayoutPosition();
            try {
                swipedConversation.push(conversations.get(position));
            } catch (IndexOutOfBoundsException e) {
                return;
            }
            conversationAdapter.remove(swipedConversation.peek(), position);
            activity.xmppConnectionService.markRead(swipedConversation.peek());

            if (position == 0 && conversationAdapter.getItemCount() == 0) {
                final Conversation c = swipedConversation.pop();
                activity.xmppConnectionService.archiveConversation(c);
                return;
            }

            // skip Tablet Layout Part.
            // skip Multi User Chat part.

            if (activity instanceof OnConversationArchived) {
                ((OnConversationArchived) activity).onConversationArchived(swipedConversation.peek());
            }
            final Conversation c = swipedConversation.peek();
            final int title;
            title = R.string.title_undo_swipe_out_conversation;

            final Snackbar snackbar = Snackbar.make(binding.list, title, 5000)
                    .setAction(R.string.undo, v -> {
                        pendingActionHelper.undo();
                        Conversation conversation = swipedConversation.pop();
                        conversationAdapter.insert(conversation, position);
                        LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
                        if (position > layoutManager.findLastVisibleItemPosition()) {
                            binding.list.smoothScrollToPosition(position);
                        }
                    })
                    .addCallback(new Snackbar.Callback() {
                        @SuppressLint("SwitchIntDef")
                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            switch (event) {
                                case BaseTransientBottomBar.BaseCallback
                                        .DISMISS_EVENT_SWIPE:
                                case BaseTransientBottomBar.BaseCallback
                                        .DISMISS_EVENT_TIMEOUT:
                                    pendingActionHelper.execute();
                                    break;
                            }
                        }
                    });

            pendingActionHelper.push(() -> {
                if (snackbar.isShownOrQueued()) {
                    snackbar.dismiss();
                }
                final Conversation conversation = swipedConversation.pop();
                if (conversation != null) {
                    if (!conversation.isRead() && conversation.getMode() == Conversation.MODE_SINGLE) {
                        return;
                    }
                    activity.xmppConnectionService.archiveConversation(c);
                }
            });

            ThemeHelper.fix(snackbar);
            snackbar.show();
        }
    };

    private ItemTouchHelper touchHelper = new ItemTouchHelper(callback);

    public static ConversationsOverviewFragment newInstance() {
        return new ConversationsOverviewFragment();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        pendingScrollState.push(savedInstanceState.getParcelable(STATE_SCROLL_POSITION));
    }

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);

        if (context instanceof XmppActivity) {
            this.activity = (XmppActivity) context;
        } else {
            throw new IllegalStateException("Trying to attach fragment to activity that is not an XmppActivity");
        }

        // detect the bottom nav controller when this fragment is hosted in the main activity - this is used to
        // hide the bottom nav when the user clicks conversation item
        if (context instanceof BottomNavController) {
            mBottomNavController = (BottomNavController) context;
        }
    }

    @Override
    public void onPause() {
        Log.d(Config.LOGTAG,"ConversationsOverviewFragment.onPause()");
        pendingActionHelper.execute();
        super.onPause();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        this.activity = null;
        mBottomNavController = null;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.mSwipeEscapeVelocity = getResources().getDimension(R.dimen.swipe_escape_velocity);
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversations_overview, container, false);

        this.conversationAdapter = new ConversationAdapter(this.activity, this.conversations);
        this.conversationAdapter.setConversationClickListener((view, conversation) -> {
            if (activity instanceof OnConversationSelected) {
                ((OnConversationSelected) activity).onConversationSelected(conversation);
            } else {
                Log.w(ConversationsOverviewFragment.class.getCanonicalName(), "Activity does not implement OnConversationSelected");
            }
            if (mBottomNavController != null) {
                mBottomNavController.onRequestHideBottomNavigation();
            }
        });
        this.binding.list.setAdapter(conversationAdapter);
        this.binding.list.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        this.touchHelper.attachToRecyclerView(this.binding.list);

        // after switch pages, this fragment hasn't been created yet so we will have null Toolbar reference.
        // but in the setTitle method we already set the ToolbarTitle value so all we need to do is assigning the value to Toolbar in this moment.
        mToolbar = this.binding.toolbarMain;
        ((AppCompatActivity) getActivity()).setSupportActionBar((mToolbar));

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(mToolbarTitle);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        // we need to show the Bottom Navigation Bar manually in case it is hide due to users select the conversation item.
        if (mBottomNavController != null) {
            mBottomNavController.onRequestShowBottomNavigation();
        }

        return binding.getRoot();
    }

    @Override
    public void onBackendConnected() {
        refresh();
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        ScrollState scrollState = getScrollState();
        if (scrollState != null) {
            bundle.putParcelable(STATE_SCROLL_POSITION, scrollState);
        }
    }

    private ScrollState getScrollState() {
        if (this.binding == null) {
            return null;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) this.binding.list.getLayoutManager();
        int position = layoutManager.findFirstVisibleItemPosition();
        final View view = this.binding.list.getChildAt(0);
        if (view != null) {
            return new ScrollState(position, view.getTop());
        } else {
            return new ScrollState(position, 0);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onStart()");
        if (activity.xmppConnectionService != null) {
            refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(Config.LOGTAG, "ConversationsOverviewFragment.onResume()");
    }

    @Override
    public void refresh() {
        if (this.binding == null || this.activity == null) {
            Log.d(Config.LOGTAG,"ConversationsOverviewFragment.refresh() skipped updated because view binding or activity was null");
            return;
        }
        this.activity.xmppConnectionService.populateWithOrderedConversations(this.conversations);
        Conversation removed = this.swipedConversation.peek();
        if (removed != null) {
            if (removed.isRead()) {
                this.conversations.remove(removed);
            } else {
                pendingActionHelper.execute();
            }
        }
        this.conversationAdapter.notifyDataSetChanged();
        ScrollState scrollState = pendingScrollState.pop();
        if (scrollState != null) {
            setScrollPosition(scrollState);
        }
    }

    private void setScrollPosition(ScrollState scrollPosition) {
        if (scrollPosition != null) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) binding.list.getLayoutManager();
            layoutManager.scrollToPositionWithOffset(scrollPosition.position, scrollPosition.offset);
        }
    }

    @Override
    public void setTitle(@NonNull final String title) {
        mToolbarTitle = (title.isEmpty()) ? getString(R.string.plutonem) : title;

        if (mToolbar != null) {
            mToolbar.setTitle(mToolbarTitle);
        }
    }
}

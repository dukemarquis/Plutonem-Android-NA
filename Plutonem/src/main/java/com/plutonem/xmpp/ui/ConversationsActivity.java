package com.plutonem.xmpp.ui;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.databinding.DataBindingUtil;

import com.plutonem.Config;
import com.plutonem.R;
import com.plutonem.databinding.ActivityConversationsBinding;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.ui.interfaces.OnBackendConnected;
import com.plutonem.xmpp.ui.interfaces.OnConversationArchived;
import com.plutonem.xmpp.ui.interfaces.OnConversationRead;
import com.plutonem.xmpp.ui.interfaces.OnConversationsListItemUpdated;
import com.plutonem.xmpp.ui.util.PendingItem;
import com.plutonem.xmpp.utils.EmojiWrapper;

import java.util.Arrays;
import java.util.List;

public class ConversationsActivity extends XmppActivity implements OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnShowErrorToast {

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
    public static final String EXTRA_DO_NOT_APPEND = "do_not_append";

    private static List<String> VIEW_AND_SHARE_ACTIONS = Arrays.asList(
            ACTION_VIEW_CONVERSATION,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    );

    private static final @IdRes int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.main_fragment};
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private ActivityConversationsBinding binding;
    private boolean mActivityPaused = true;

    public static void configureActionBar(ActionBar actionBar) {
        configureActionBar(actionBar, true);
    }

    public static void configureActionBar(ActionBar actionBar, boolean upNavigation) {
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(upNavigation);
            actionBar.setDisplayHomeAsUpEnabled(upNavigation);
        }
    }

    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null && VIEW_AND_SHARE_ACTIONS.contains(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    protected void refreshUiReal() {
        for(@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
    }

    @Override
    public void onBackendConnected() {
        xmppConnectionService.getNotificationService().setIsInForeground(true);
        Intent intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                invalidateActionBarTitle();
                return;
            }
        }
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            notifyFragmentOfBackendConnected(id);
        }

        invalidateActionBarTitle();
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected) {
            ((OnBackendConnected) fragment).onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof XmppFragment) {
            ((XmppFragment) fragment).refresh();
        }
    }

    private boolean processViewIntent(Intent intent) {
        String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
            return false;
        }
        openConversation(conversation, intent.getExtras());
        return true;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
//        setSupportActionBar((Toolbar) binding.toolbarMain);
        configureActionBar(getSupportActionBar());
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewOrShareIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        ConversationFragment conversationFragment = (ConversationFragment) mainFragment;
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        invalidateActionBarTitle();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onNewIntent(final Intent intent) {
        if (isViewOrShareIntent(intent)) {
            if (xmppConnectionService != null) {
                clearPendingViewIntent();
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
        setIntent(createLauncherIntent(this));
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.main_fragment, new ConversationFragment());
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
            if (mainFragment instanceof ConversationFragment) {
                final Conversation conversation = ((ConversationFragment) mainFragment).getConversation();
                if (conversation != null) {
                    actionBar.setTitle(EmojiWrapper.transform(conversation.getName()));
                    actionBar.setDisplayHomeAsUpEnabled(true);
                    return;
                }
            }
            actionBar.setTitle(R.string.app_name);
            actionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        // skip Perform Redirect part.
        Fragment mainFragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            try {
                getFragmentManager().popBackStack();
            } catch (IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while popping back state after archiving conversation", e);
                //this usually means activity is no longer active; meaning on the next open we will run through this again
            }
            return;
        }
        // skip Tablet Layout part.
    }

    @Override
    public void onConversationsListItemUpdated() {
        // skip Tablet Layout part.
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + Boolean.toString(mActivityPaused));
        }
    }

    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate() {
        this.refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }
}

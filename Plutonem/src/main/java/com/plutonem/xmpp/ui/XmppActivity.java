package com.plutonem.xmpp.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.plutonem.Config;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.services.AvatarService;
import com.plutonem.xmpp.services.EmojiService;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.services.XmppConnectionService.XmppConnectionBinder;
import com.plutonem.xmpp.utils.ExceptionHelper;

import java.lang.ref.WeakReference;

public abstract class XmppActivity extends AppCompatActivity {

    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;

    protected ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            XmppConnectionBinder binder = (XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            registerListeners();
            onBackendConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            xmppConnectionServiceBound = false;
        }
    };
    private long mLastUiRefresh = 0;
    private Handler mRefreshUiHandler = new Handler();
    private Runnable mRefreshUiRunnable = () -> {
        mLastUiRefresh = SystemClock.elapsedRealtime();
        refreshUiReal();
    };
    public boolean mSkipBackgroundBinding = false;

    protected final void refreshUi() {
        final long diff = SystemClock.elapsedRealtime() - mLastUiRefresh;
        if (diff > Config.REFRESH_UI_INTERVAL) {
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            runOnUiThread(mRefreshUiRunnable);
        } else {
            final long next = Config.REFRESH_UI_INTERVAL - diff;
            mRefreshUiHandler.removeCallbacks(mRefreshUiRunnable);
            mRefreshUiHandler.postDelayed(mRefreshUiRunnable, next);
        }
    }

    abstract protected void refreshUiReal();

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            if (this.mSkipBackgroundBinding) {
                Log.d(Config.LOGTAG, "skipping background binding");
            } else {
                connectToBackend();
            }
        } else {
            this.registerListeners();
            this.onBackendConnected();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            this.unregisterListeners();
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    public void connectToBackend() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction("ui");
        try {
            startService(intent);
        } catch (IllegalStateException e) {
            Log.w(Config.LOGTAG, "unable to start service from " + getClass().getSimpleName());
        }
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    public abstract void onBackendConnected();

    protected void registerListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.setOnConversationListChangedListener((XmppConnectionService.OnConversationUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.setOnAccountListChangedListener((XmppConnectionService.OnAccountUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.setOnShowErrorToastListener((XmppConnectionService.OnShowErrorToast) this);
        }
    }

    protected void unregisterListeners() {
        if (this instanceof XmppConnectionService.OnConversationUpdate) {
            this.xmppConnectionService.removeOnConversationListChangedListener((XmppConnectionService.OnConversationUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnAccountUpdate) {
            this.xmppConnectionService.removeOnAccountListChangedListener((XmppConnectionService.OnAccountUpdate) this);
        }
        if (this instanceof XmppConnectionService.OnShowErrorToast) {
            this.xmppConnectionService.removeOnShowErrorToastListener((XmppConnectionService.OnShowErrorToast) this);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ExceptionHelper.init(getApplicationContext());
        new EmojiService(this).init();
    }

    public boolean isDarkTheme() {
        // skip the Dark Theme part
        return false;
    }

    public int getThemeResource(int r_attr_name, int r_drawable_def) {
        int[] attrs = {r_attr_name};
        TypedArray ta = this.getTheme().obtainStyledAttributes(attrs);

        int res = ta.getResourceId(0, r_drawable_def);
        ta.recycle();

        return res;
    }

    public void switchToConversationDoNotAppend(Conversation conversation, String text) {
        switchToConversation(conversation, text, false, null, false, true);
    }

    private void switchToConversation(Conversation conversation, String text, boolean asQuote, String nick, boolean pm, boolean doNotAppend) {
        Intent intent = new Intent(this, ConversationsActivity.class);
        intent.setAction(ConversationsActivity.ACTION_VIEW_CONVERSATION);
        intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getUuid());
        if (text != null) {
            // skip Text Within New Conversation part.
        }
        if (nick != null) {
            // skip Nick Within New Conversation part.
        }
        if (doNotAppend) {
            intent.putExtra(ConversationsActivity.EXTRA_DO_NOT_APPEND, true);
        }
        intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public AvatarService avatarService() {
        return xmppConnectionService.getAvatarService();
    }

    public static XmppActivity find(@NonNull WeakReference<ImageView> viewWeakReference) {
        final View view = viewWeakReference.get();
        return view == null ? null : find(view);
    }

    public static XmppActivity find(@NonNull final View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof XmppActivity) {
                return (XmppActivity) context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }
}

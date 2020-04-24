package com.plutonem.ui.main;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;

import com.plutonem.Config;
import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.generated.AccountActionBuilder;
import com.plutonem.android.fluxc.generated.BuyerActionBuilder;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.android.fluxc.store.AccountStore.OnAccountChanged;
import com.plutonem.android.fluxc.store.AccountStore.OnAuthenticationChanged;
import com.plutonem.android.fluxc.store.BuyerStore;
import com.plutonem.android.fluxc.store.BuyerStore.OnBuyerChanged;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderSubmitted;
import com.plutonem.ui.ActivityLauncher;
import com.plutonem.ui.RequestCodes;
import com.plutonem.ui.main.PMainNavigationView.OnPageListener;
import com.plutonem.ui.main.PMainNavigationView.PageType;
import com.plutonem.ui.nemur.NemurOrderListFragment;
import com.plutonem.ui.news.NewsManager;
import com.plutonem.ui.prefs.AppPrefs;
import com.plutonem.ui.submits.SubmitUtilsWrapper;
import com.plutonem.utilities.FluxCUtils;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.services.XmppConnectionService;
import com.plutonem.xmpp.ui.ConversationMainViewFragment;
import com.plutonem.xmpp.ui.ConversationsOverviewFragment;
import com.plutonem.xmpp.ui.XmppActivity;
import com.plutonem.xmpp.ui.XmppFragment;
import com.plutonem.xmpp.ui.interfaces.OnBackendConnected;
import com.plutonem.xmpp.ui.interfaces.OnConversationArchived;
import com.plutonem.xmpp.ui.interfaces.OnConversationRead;
import com.plutonem.xmpp.ui.interfaces.OnConversationSelected;
import com.plutonem.xmpp.ui.interfaces.OnConversationsListItemUpdated;
import com.plutonem.xmpp.ui.util.MenuDoubleTabUtil;
import com.plutonem.xmpp.ui.util.PendingItem;
import com.plutonem.xmpp.utils.EmojiWrapper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.DeviceUtils;
import org.wordpress.android.util.ProfilingUtils;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import static androidx.lifecycle.Lifecycle.State.STARTED;

/**
 * Main activity which hosts homepage and me pages
 */
public class PMainActivity extends XmppActivity implements
        OnPageListener,
        BottomNavController,
        OnConversationSelected,
        OnConversationArchived,
        OnConversationsListItemUpdated,
        OnConversationRead,
        XmppConnectionService.OnAccountUpdate,
        XmppConnectionService.OnConversationUpdate {

    private PMainNavigationView mBottomNav;

    private BuyerModel mSelectedBuyer;

    @Inject AccountStore mAccountStore;
    @Inject BuyerStore mBuyerStore;
    @Inject Dispatcher mDispatcher;
    @Inject NewsManager mNewsManager;
    @Inject SubmitUtilsWrapper mSubmitUtilsWrapper;

    // Xmpp Chat Specification

    public static final String ACTION_VIEW_CONVERSATION = "com.plutonem.action.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";

    private static List<String> VIEW_AND_SHARE_ACTIONS = Arrays.asList(
            ACTION_VIEW_CONVERSATION,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    );

    // for Tablet Layout Only: secondary fragment (when holding the conversation, must be initialized before refreshing the overview fragment
    private static final @IdRes
    int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.fragment_container};
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private boolean mActivityPaused = true;

    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null && VIEW_AND_SHARE_ACTIONS.contains(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, PMainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    /*
     * fragments implement this if their contents can be scrolled, called when user
     * requests to scroll to the top
     */
    public interface OnScrollToTopListener {
        void onScrollToTop();
    }

    /*
     * fragments implement this and return true if the fragment handles the back button
     * and doesn't want the activity to handle it as well
     */
    public interface OnActivityBackPressedListener {
        boolean onActivityBackPressed();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ProfilingUtils.split("PNMainActivity.onCreate");
        ((Plutonem) getApplication()).component().inject(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mBottomNav = findViewById(R.id.bottom_navigation);
        mBottomNav.init(getSupportFragmentManager(), this);

        registeNewsItemObserver();

        // We need to register the dispatcher here.
        mDispatcher.register(this);
        EventBus.getDefault().register(this);

        // Xmpp Chat Specification

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

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onNewIntent(Intent intent) {

        // skip Nemur Normal Intent part, include Notification.

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

    private void registeNewsItemObserver() {
        mNewsManager.notificationBadgeVisibility().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable Boolean showBadge) {
                mBottomNav.showNemurBadge(showBadge != null ? showBadge : false);
            }
        });
        mNewsManager.pull(false);
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault().unregister(this);
        mDispatcher.unregister(this);
        super.onDestroy();
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Load selected buyer
        initSelectedBuyer();

        // We need to track the current item on the screen when this activity is resumed.
        PageType currentPageType = mBottomNav.getCurrentSelectedPage();

        announceTitleForAccessibility(currentPageType);

        // Update account
        if (mAccountStore.hasAccessToken()) {
            mDispatcher.dispatch(AccountActionBuilder.newFetchAccountAction());
        }

        ProfilingUtils.split("PMainActivity.onResume");
        ProfilingUtils.dump();
        ProfilingUtils.stop();

        // Xmpp Chat Specification
        this.mActivityPaused = false;
    }

    private void announceTitleForAccessibility(PageType pageType) {
        getWindow().getDecorView().announceForAccessibility(mBottomNav.getContentDescriptionForPageType(pageType));
    }

    @Override
    public void onBackPressed() {
        // let the fragment handle the back button if it implements our OnParentBackPressedListener
        Fragment fragment = mBottomNav.getActiveFragment();
        if (fragment instanceof OnActivityBackPressedListener) {
            boolean handled = ((OnActivityBackPressedListener) fragment).onActivityBackPressed();
            if (handled) {
                return;
            }
        }

        if (isTaskRoot() && DeviceUtils.getInstance().isChromebook(this)) {
            return; // don't close app in Main Activity
        }
        super.onBackPressed();
    }

    @Override
    public void onRequestShowBottomNavigation() {
        showBottomNav(true);
    }

    @Override
    public void onRequestHideBottomNavigation() {
        showBottomNav(false);
    }

    private void showBottomNav(boolean show) {
        mBottomNav.setVisibility(show ? View.VISIBLE : View.GONE);
        findViewById(R.id.navbar_separator).setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // user switched pages in the bottom navbar
    @Override
    public void onPageChanged(int position) {
        PageType pageType = PMainNavigationView.getPageType(position);
        updateTitle(pageType);
    }

    // user tapped the me button in the bottom navbar
    @Override
    public boolean onMeAndChatButtonClicked() {
        if (FluxCUtils.isSignedInPN(mAccountStore)) {
            return true;
        } else {
            ActivityLauncher.showSignInForResult(this);
            return false;
        }
    }

    private void updateTitle() {
        updateTitle(mBottomNav.getCurrentSelectedPage());
    }

    private void updateTitle(PageType pageType) {
        if (pageType == PageType.ME && mSelectedBuyer != null) {
            ((MainToolbarFragment) mBottomNav.getActiveFragment()).setTitle(mSelectedBuyer.getName());
        } else {
            ((MainToolbarFragment) mBottomNav.getActiveFragment())
                    .setTitle(mBottomNav.getTitleForPageType(pageType).toString());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case RequestCodes.ADD_ACCOUNT:
                if (resultCode == RESULT_OK) {
                    startWithNewAccount();
                }
        }
    }

    private void startWithNewAccount() {
        NemurOrderListFragment.resetLastUpdateDate();
    }

    private MyBuyerFragment getMyBuyerFragment() {
        Fragment fragment = mBottomNav.getFragment(PageType.ME);
        if (fragment instanceof MyBuyerFragment) {
            return (MyBuyerFragment) fragment;
        }
        return null;
    }

    // Events

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthenticationChanged(OnAuthenticationChanged event) {
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAccountChanged(OnAccountChanged event) {
    }

    /**
     * @return null if there is no buyer or if there is no selected buyer
     */
    public @Nullable
    BuyerModel getSelectedBuyer() {
        return mSelectedBuyer;
    }

    public void setSelectedBuyer(@Nullable BuyerModel selectedBuyer) {
        mSelectedBuyer = selectedBuyer;
        if (selectedBuyer == null) {
            AppPrefs.setSelectedBuyer(-1);
            return;
        }

        // When we select a buyer, we want to update its information or options
        mDispatcher.dispatch(BuyerActionBuilder.newFetchBuyerAction(selectedBuyer));

        // Make selected buyer visible
        selectedBuyer.setIsVisible(true);
        AppPrefs.setSelectedBuyer(selectedBuyer.getId());

        updateTitle();
    }

    /**
     * This should not be moved to a BuyerUtils.getSelectedBuyer() or similar static method. We don't want
     * this to be used globally. The state is maintained by this Activity and the selected buyer parameter
     * is passed along to other activities / fragments.
     */
    public void initSelectedBuyer() {
        int buyerLocalId = AppPrefs.getSelectedBuyer();

        if (buyerLocalId != -1) {
            // Buyer previously selected, use it
            mSelectedBuyer = mBuyerStore.getBuyerByLocalId(buyerLocalId);
            // If saved buyer exist, then return, else (buyer has been removed?) try to select another buyer
            if (mSelectedBuyer != null) {
                updateTitle();
                return;
            }
        }

        // Try to select the primary pn buyer
        long buyerId = mAccountStore.getAccount().getPrimaryBuyerId();
        BuyerModel primaryBuyer = mBuyerStore.getBuyerByBuyerId(buyerId);
        // Primary buyer found, select it
        if (primaryBuyer != null) {
            setSelectedBuyer(primaryBuyer);
            return;
        }

        // Else select the first visible buyer in the list
        List<BuyerModel> buyers = mBuyerStore.getVisibleBuyers();
        if (buyers.size() != 0) {
            setSelectedBuyer(buyers.get(0));
            return;
        }

        // Else select the first in the list
        buyers = mBuyerStore.getBuyers();
        if (buyers.size() != 0) {
            setSelectedBuyer(buyers.get(0));
        }

        // Else no buyer selected
    }

    // FluxC events
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderSubmitted(OnOrderSubmitted event) {
        // PMainActivity never stops listening for the Dispatcher events and as a result it tries to show the
        // SnackBar even when another activity is in the foreground. However, this has a tricky side effect, as if
        // the Activity in the foreground is showing a Snackbar the SnackBar is dismissed as soon as the
        // PMainActivity invokes show(). This condition makes sure, the PMainActivity invokes show() only when
        // it's visible. For more info see https://github.com/wordpress-mobile/WordPress-Android/issues/9604
        if (getLifecycle().getCurrentState().isAtLeast(STARTED)) {
            BuyerModel buyer = getSelectedBuyer();
            if (buyer != null && event.order != null && event.order.getLocalBuyerId() == buyer.getId()) {
                mSubmitUtilsWrapper.onOrderSubmittedSnackbarHandler(
                        this,
                        findViewById(R.id.coordinator),
                        event.isError(),
                        event.order,
                        null,
                        buyer);
            }
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBuyerChanged(OnBuyerChanged event) {
        // "Reload" selected buyer from the db
        // It would be better if the OnBuyerChanged provided the list of changed buyers.
        if (getSelectedBuyer() == null && mBuyerStore.hasBuyer()) {
            setSelectedBuyer(mBuyerStore.getBuyers().get(0));
        }
        if (getSelectedBuyer() == null) {
            return;
        }

        BuyerModel buyer = mBuyerStore.getBuyerByLocalId(getSelectedBuyer().getId());
        if (buyer != null) {
            mSelectedBuyer = buyer;
        }
        if (getMyBuyerFragment() != null) {
            getMyBuyerFragment().onBuyerChanged(buyer);
        }
    }

    // Xmpp Chat Specification

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    try {
                        fm.popBackStack();
                    } catch (IllegalStateException e) {
                        Log.w(Config.LOGTAG, "Unable to pop back stack after pressing home button");
                    }
                    return true;
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void refreshUiReal() {
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
    }

    @Override
    public void onBackendConnected() {

        // skip Redirect Perform Action part.
        // skip Tablet Layout part.
        // skip ActionBar Manage Action part.
        // skip Activity Result for Xmpp part.

        xmppConnectionService.getNotificationService().setIsInForeground(true);
        Intent intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                return;
            }
        }
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            notifyFragmentOfBackendConnected(id);
        }
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected) {
            ((OnBackendConnected) fragment).onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getSupportFragmentManager().findFragmentById(id);
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
    public void onConversationSelected(Conversation conversation) {
        clearPendingViewIntent();

        // skip Tablet Layout part.

        openConversation(conversation, null);
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }

    private void openConversation(Conversation conversation, Bundle extras) {

        // skip Tablet Layout part.

        ConversationMainViewFragment conversationMainViewFragment;
        Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (mainFragment instanceof ConversationMainViewFragment) {
            conversationMainViewFragment = (ConversationMainViewFragment) mainFragment;
        } else {
            conversationMainViewFragment = new ConversationMainViewFragment();
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, conversationMainViewFragment);
            fragmentTransaction.addToBackStack(null);
            try {
                fragmentTransaction.commit();
            } catch (IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while opening conversation", e);
                // allowing state loss is probably fine since view intents et all are already stored and a click can probably be 'ignored'
                return;
            }
        }
        conversationMainViewFragment.reInit(conversation, extras == null ? new Bundle() : extras);
    }

    @Override
    public void onConversationArchived(Conversation conversation) {

        // skip Tablet Layout part.
        // skip Redirect Perform Action part.

        Fragment mainFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (mainFragment instanceof ConversationMainViewFragment) {
            try {
                getSupportFragmentManager().popBackStack();
            } catch (IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while popping back state after archiving conversation", e);
                //this usually means activity is no longer active; meaning on the next open we will run through this again
            }
            return;
        }
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

        // skip Redirection Perform part.

        this.refreshUi();
    }
}
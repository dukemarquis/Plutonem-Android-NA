package com.plutonem.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;

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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DeviceUtils;
import org.wordpress.android.util.ProfilingUtils;

import java.util.List;

import javax.inject.Inject;

import static androidx.lifecycle.Lifecycle.State.STARTED;

/**
 * Main activity which hosts homepage and me pages
 */
public class PMainActivity extends AppCompatActivity implements
        OnPageListener,
        BottomNavController {
    private PMainNavigationView mBottomNav;

    private BuyerModel mSelectedBuyer;

    @Inject AccountStore mAccountStore;
    @Inject BuyerStore mBuyerStore;
    @Inject Dispatcher mDispatcher;
    @Inject NewsManager mNewsManager;
    @Inject SubmitUtilsWrapper mSubmitUtilsWrapper;

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

        // We need to register the dispatcher here otherwise it won't trigger if for example Buyer Picker is present
        mDispatcher.register(this);
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        AppLog.i(T.MAIN, "main activity > new intent");
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
    protected void onResume() {
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
    public boolean onMeButtonClicked() {
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

    // (weird) - XD
    @Override
    protected void onPause() {
        super.onPause();
    }
}
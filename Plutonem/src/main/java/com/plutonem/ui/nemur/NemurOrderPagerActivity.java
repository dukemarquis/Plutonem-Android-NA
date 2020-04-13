package com.plutonem.ui.nemur;

import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.datasets.NemurOrderTable;
import com.plutonem.models.NemurTag;
import com.plutonem.ui.ActivityLauncher;
import com.plutonem.ui.RequestCodes;
import com.plutonem.ui.nemur.NemurTypes.NemurOrderListType;
import com.plutonem.ui.nemur.actions.NemurActions;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderId;
import com.plutonem.ui.nemur.models.NemurBuyerIdOrderIdList;
import com.plutonem.ui.nemur.services.order.NemurOrderServiceStarter;
import com.plutonem.ui.prefs.AppPrefs;
import com.plutonem.utilities.AniUtils;
import com.plutonem.utilities.FluxCUtils;
import com.plutonem.widgets.PNSwipeSnackbar;
import com.plutonem.widgets.PNViewPager;
import com.plutonem.widgets.PNViewPagerTransformer;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Conversation;
import com.plutonem.xmpp.ui.XmppActivity;
import com.plutonem.xmpp.utils.AccountUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import kohii.v1.core.MemoryMode;
import kohii.v1.exoplayer.Kohii;
import rocks.xmpp.addr.Jid;

/*
 * shows nemur order detail fragments in a ViewPager - primarily used for easy swiping between
 * orders with a specific tag or in a specific buyer, but can also be used to show a single
 * order detail.
 */
public class NemurOrderPagerActivity extends XmppActivity
        implements NemurInterfaces.AutoHideToolbarListener,
        NemurInterfaces.ChatInterfaceListener {
    private PNViewPager mViewPager;
    private ProgressBar mProgress;
    private Toolbar mToolbar;

    private NemurTag mCurrentTag;
    private long mBuyerId;
    private long mOrderId;
    private NemurOrderListType mOrderListType;

    private boolean mIsRequestingMoreOrders;
    private boolean mIsSingleOrderView;

    // Kohii Video Specification
    private Kohii kohii;

    // Xmpp Chat Specification
    private static final String XMPP_CONNECTION_SELLER_UNIQUE_ID = "seller@3.15.14.1";

    private List<String> mActivatedAccounts = new ArrayList<>();

    @Inject AccountStore mAccountStore;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((Plutonem) getApplication()).component().inject(this);

        setContentView(R.layout.nemur_activity_order_pager);

        mToolbar = findViewById(R.id.toolbar_main);
        setSupportActionBar(mToolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mViewPager = findViewById(R.id.viewpager);
        mProgress = findViewById(R.id.progress_loading);

        if (savedInstanceState != null) {
            mBuyerId = savedInstanceState.getLong(NemurConstants.ARG_BUYER_ID);
            mOrderId = savedInstanceState.getLong(NemurConstants.ARG_ORDER_ID);
            mIsSingleOrderView = savedInstanceState.getBoolean(NemurConstants.ARG_IS_SINGLE_ORDER);
            if (savedInstanceState.containsKey(NemurConstants.ARG_ORDER_LIST_TYPE)) {
                mOrderListType =
                        (NemurOrderListType) savedInstanceState.getSerializable(NemurConstants.ARG_ORDER_LIST_TYPE);
            }
            if (savedInstanceState.containsKey(NemurConstants.ARG_TAG)) {
                mCurrentTag = (NemurTag) savedInstanceState.getSerializable(NemurConstants.ARG_TAG);
            }
        } else {
            mBuyerId = getIntent().getLongExtra(NemurConstants.ARG_BUYER_ID, 0);
            mOrderId = getIntent().getLongExtra(NemurConstants.ARG_ORDER_ID, 0);
            mIsSingleOrderView = getIntent().getBooleanExtra(NemurConstants.ARG_IS_SINGLE_ORDER, false);
            if (getIntent().hasExtra(NemurConstants.ARG_ORDER_LIST_TYPE)) {
                mOrderListType =
                        (NemurOrderListType) getIntent().getSerializableExtra(NemurConstants.ARG_ORDER_LIST_TYPE);
            }
            if (getIntent().hasExtra(NemurConstants.ARG_TAG)) {
                mCurrentTag = (NemurTag) getIntent().getSerializableExtra(NemurConstants.ARG_TAG);
            }
        }

        if (mOrderListType == null) {
            mOrderListType = NemurOrderListType.TAG_DEFAULT;
        }

        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                onShowHideToolbar(true);
                updateTitle(position);
            }
        });

        mViewPager.setPageTransformer(false,
                new PNViewPagerTransformer(PNViewPagerTransformer.TransformType.SLIDE_OVER));

        kohii = Kohii.get(this);
        kohii.register(this, MemoryMode.HIGH)
                .addBucket(this.mViewPager);
    }

    /*
     * set the activity title based on the order at the passed position
     */
    private void updateTitle(int position) {
        // set the title to the title of the order
        NemurBuyerIdOrderId ids = getAdapterBuyerIdOrderIdAtPosition(position);
        if (ids != null) {
            String title = NemurOrderTable.getOrderTitle(ids.getBuyerId(), ids.getOrderId());
            if (!title.isEmpty()) {
                setTitle(title);
                return;
            }
        }

        // default when order hasn't been retrieved yet
        setTitle(R.string.nemur_title_order_detail);
    }

    /*
     * used by the detail fragment when a order was requested due to not existing locally
     */
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(NemurEvents.SingleOrderDownloaded event) {
        if (!isFinishing()) {
            updateTitle(mViewPager.getCurrentItem());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);

        if (!hasPagerAdapter()) {
            loadOrders(mBuyerId, mOrderId);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPagerAdapter() {
        return (mViewPager != null && mViewPager.getAdapter() != null);
    }

    private OrderPagerAdapter getPagerAdapter() {
        if (mViewPager != null && mViewPager.getAdapter() != null) {
            return (OrderPagerAdapter) mViewPager.getAdapter();
        } else {
            return null;
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(NemurConstants.ARG_IS_SINGLE_ORDER, mIsSingleOrderView);

        if (hasCurrentTag()) {
            outState.putSerializable(NemurConstants.ARG_TAG, getCurrentTag());
        }
        if (getOrderListType() != null) {
            outState.putSerializable(NemurConstants.ARG_ORDER_LIST_TYPE, getOrderListType());
        }

        NemurBuyerIdOrderId id = getAdapterCurrentBuyerIdOrderId();
        if (id != null) {
            outState.putLong(NemurConstants.ARG_BUYER_ID, id.getBuyerId());
            outState.putLong(NemurConstants.ARG_ORDER_ID, id.getOrderId());
        }

        super.onSaveInstanceState(outState);
    }

    private NemurBuyerIdOrderId getAdapterCurrentBuyerIdOrderId() {
        OrderPagerAdapter adapter = getPagerAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getCurrentBuyerIdOrderId();
    }

    private NemurBuyerIdOrderId getAdapterBuyerIdOrderIdAtPosition(int position) {
        OrderPagerAdapter adapter = getPagerAdapter();
        if (adapter == null) {
            return null;
        }
        return adapter.getBuyerIdOrderIdAtPosition(position);
    }

    /*
     * loads the buyerId/orderId pairs used to populate the pager adapter - passed buyerId/orderId will
     * be made active after loading unless gotoNext=true, in which case the order after the passed
     * one will be made active
     */
    private void loadOrders(final long buyerId, final long orderId) {
        new Thread() {
            @Override
            public void run() {
                final NemurBuyerIdOrderIdList idList;
                if (mIsSingleOrderView) {
                    idList = new NemurBuyerIdOrderIdList();
                    idList.add(new NemurBuyerIdOrderId(buyerId, orderId));
                } else {
                    int maxOrders = NemurConstants.NEMUR_MAX_ORDERS_TO_DISPLAY;
                    switch (getOrderListType()) {
                        case TAG_DEFAULT:
                            idList = NemurOrderTable.getBuyerIdOrderIdsWithTag(getCurrentTag(), maxOrders);
                            break;
                        case SEARCH_RESULTS:
                        default:
                            return;
                    }
                }

                final int currentPosition = mViewPager.getCurrentItem();
                final int newPosition = idList.indexOf(buyerId, orderId);

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) {
                            return;
                        }

                        AppLog.d(AppLog.T.NEMUR, "nemur pager > creating adapter");
                        OrderPagerAdapter adapter =
                                new OrderPagerAdapter(getSupportFragmentManager(), idList);
                        mViewPager.setAdapter(adapter);
                        if (adapter.isValidPosition(newPosition)) {
                            mViewPager.setCurrentItem(newPosition);
                            updateTitle(newPosition);
                        } else if (adapter.isValidPosition(currentPosition)) {
                            mViewPager.setCurrentItem(currentPosition);
                            updateTitle(currentPosition);
                        }

                        // let the user know they can swipe between orders
                        if (adapter.getCount() > 1 && !AppPrefs.isNemurSwipeToNavigateShown()) {
                            PNSwipeSnackbar.show(mViewPager);
                            AppPrefs.setNemurSwipeToNavigateShown(true);
                        }
                    }
                });
            }
        }.start();
    }

    private NemurTag getCurrentTag() {
        return mCurrentTag;
    }

    private boolean hasCurrentTag() {
        return mCurrentTag != null;
    }

    private NemurOrderListType getOrderListType() {
        return mOrderListType;
    }

    /*
     * called when user scrolls towards the last orders - requests older orders with the
     * current tag or in the current buyer
     */
    private void requestMoreOrders() {
        if (mIsRequestingMoreOrders) {
            return;
        }

        AppLog.d(AppLog.T.NEMUR, "nemur pager > requesting older orders");
        switch (getOrderListType()) {
            case TAG_DEFAULT:
                NemurOrderServiceStarter.startServiceForTag(
                        this,
                        getCurrentTag(),
                        NemurOrderServiceStarter.UpdateAction.REQUEST_OLDER);
                break;
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(NemurEvents.UpdateOrdersStarted event) {
        if (isFinishing()) {
            return;
        }

        mIsRequestingMoreOrders = true;
        mProgress.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventMainThread(NemurEvents.UpdateOrdersEnded event) {
        if (isFinishing()) {
            return;
        }

        OrderPagerAdapter adapter = getPagerAdapter();
        if (adapter == null) {
            return;
        }

        mIsRequestingMoreOrders = false;
        mProgress.setVisibility(View.GONE);

        if (event.getResult() == NemurActions.UpdateResult.HAS_NEW) {
            AppLog.d(AppLog.T.NEMUR, "nemur pager > older orders received");
            // remember which post to keep active
            NemurBuyerIdOrderId id = adapter.getCurrentBuyerIdOrderId();
            long blogId = (id != null ? id.getBuyerId() : 0);
            long postId = (id != null ? id.getOrderId() : 0);
            loadOrders(blogId, postId);
        } else {
            AppLog.d(AppLog.T.NEMUR, "nemur pager > all orders loaded");
            adapter.mAllOrdersLoaded = true;
        }
    }

    /*
     * called by detail fragment to show/hide the toolbar when user scrolls
     */
    @Override
    public void onShowHideToolbar(boolean show) {
        if (!isFinishing()) {
            AniUtils.animateTopBar(mToolbar, show);
        }
    }

    /**
     * pager adapter containing order detail fragments
     **/
    private class OrderPagerAdapter extends FragmentStatePagerAdapter {
        private NemurBuyerIdOrderIdList mIdList;
        private boolean mAllOrdersLoaded;

        // this is used to retain created fragments so we can access them in
        // getFragmentAtPosition() - necessary because the pager provides no
        // built-in way to do this - note that destroyItem() removes fragments
        // from this map when they're removed from the adapter, so this doesn't
        // retain *every* fragment
        private final SparseArray<Fragment> mFragmentMap = new SparseArray<>();

        OrderPagerAdapter(FragmentManager fm, NemurBuyerIdOrderIdList ids) {
            super(fm);
            mIdList = (NemurBuyerIdOrderIdList) ids.clone();
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {
            // work around "Fragement no longer exists for key" Android bug
            // by catching the IllegalStateException
            // https://code.google.com/p/android/issues/detail?id=42601
            try {
                AppLog.d(AppLog.T.NEMUR, "nemur pager > adapter restoreState");
                super.restoreState(state, loader);
            } catch (IllegalStateException e) {
                AppLog.e(AppLog.T.NEMUR, e);
            }
        }

        @Override
        public Parcelable saveState() {
            AppLog.d(AppLog.T.NEMUR, "nemur pager > adapter saveState");
            return super.saveState();
        }

        private boolean canRequestMostOrders() {
            return !mAllOrdersLoaded
                    && !mIsSingleOrderView
                    && (mIdList != null && mIdList.size() < NemurConstants.NEMUR_MAX_ORDERS_TO_DISPLAY)
                    && NetworkUtils.isNetworkAvailable(NemurOrderPagerActivity.this);
        }

        boolean isValidPosition(int position) {
            return (position >= 0 && position < getCount());
        }

        @Override
        public int getCount() {
            return mIdList.size();
        }

        @Override
        public Fragment getItem(int position) {
            if ((position == getCount() - 1) && canRequestMostOrders()) {
                requestMoreOrders();
            }

            return NemurOrderDetailFragment.Companion.newInstance(
                    mIdList.get(position).getBuyerId(),
                    mIdList.get(position).getOrderId(),
                    getOrderListType(),
                    position);
        }

        @Override
        public @NonNull Object instantiateItem(ViewGroup container, int position) {
            Object item = super.instantiateItem(container, position);
            if (item instanceof Fragment) {
                mFragmentMap.put(position, (Fragment) item);
            }
            return item;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            mFragmentMap.remove(position);
            super.destroyItem(container, position, object);
        }

        private NemurBuyerIdOrderId getCurrentBuyerIdOrderId() {
            return getBuyerIdOrderIdAtPosition(mViewPager.getCurrentItem());
        }

        NemurBuyerIdOrderId getBuyerIdOrderIdAtPosition(int position) {
            if (isValidPosition(position)) {
                return mIdList.get(position);
            } else {
                return null;
            }
        }
    }

    // Xmpp Chat Specification Part.

    @Override
    protected void refreshUiReal() {}

    @Override
    public void onBackendConnected() {
        this.mActivatedAccounts.clear();
        this.mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(xmppConnectionService));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RequestCodes.DO_LOGIN) {
            if (resultCode == RESULT_OK) {
                // login successfully,
                // skip Show Chat Ui part.
            }
        }
    }

    @Override
    public void onShowChat() {
        if (FluxCUtils.isSignedInPN(mAccountStore)) {

            final Jid accountJid;
            final Jid contactJid;

            if (this.mActivatedAccounts.size() != 1) {

                // skip Multi User Chat part.
                accountJid = null;
                contactJid = null;
            } else {
                accountJid = Jid.of(mActivatedAccounts.get(0));
                contactJid = Jid.of(XMPP_CONNECTION_SELLER_UNIQUE_ID);
            }

            if (!xmppConnectionServiceBound) {
                return;
            }

            final Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }

            // skip Create Contact Part.
            switchToConversationDoNotAppend(account, contactJid, null);
        } else {
            // start the login flow and wait onActivityResult
            ActivityLauncher.loginForChatIntent(this);
        }
    }

    protected void switchToConversationDoNotAppend(Account account, Jid contactJid, String body) {
        Conversation conversation = xmppConnectionService.findOrCreateConversation(account, contactJid, false, true);
        switchToConversationDoNotAppend(conversation, body);
    }
}

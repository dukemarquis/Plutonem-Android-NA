package com.plutonem.ui.products;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.alipay.sdk.app.EnvUtils;
import com.alipay.sdk.app.PayTask;
import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.generated.OrderActionBuilder;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.fluxc.model.CauseOfOnOrderChanged;
import com.plutonem.android.fluxc.model.OrderModel;
import com.plutonem.android.fluxc.model.order.OrderStatus;
import com.plutonem.android.fluxc.store.OrderStore;
import com.plutonem.android.fluxc.store.OrderStore.OnInfoEncrypted;
import com.plutonem.android.fluxc.store.OrderStore.OnOrderChanged;
import com.plutonem.android.fluxc.store.OrderStore.OnResultDecrypted;
import com.plutonem.android.fluxc.store.OrderStore.RemoteResultPayload;
import com.plutonem.ui.ActivityId;
import com.plutonem.ui.products.EditOrderSettingsFragment.EditorFragmentListener;
import com.plutonem.ui.products.editor.EditorActionsProvider;
import com.plutonem.ui.products.editor.PrimaryEditorAction;
import com.plutonem.ui.submits.ConfirmUtils;
import com.plutonem.ui.submits.OrderEvents;
import com.plutonem.ui.submits.SubmitService;
import com.plutonem.widgets.PNViewPager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.DateTimeUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;

import java.util.Map;

import javax.inject.Inject;

public class EditOrderActivity extends AppCompatActivity implements
        EditorFragmentListener,
        EditOrderSettingsFragment.EditOrderActivityHook,
        BasicFragmentDialog.BasicDialogPositiveClickInterface,
        BasicFragmentDialog.BasicDialogNegativeClickInterface,
        OrderSettingsListDialogFragment.OnOrderSettingsDialogFragmentListener {
    public static final String EXTRA_ORDER_LOCAL_ID = "orderModelLocalId";
    public static final String EXTRA_ORDER_REMOTE_ID = "orderModelRemoteId";
    public static final String EXTRA_SAVED_AS_LOCAL_DRAFT = "savedAsLocalDraft";
    public static final String EXTRA_HAS_CHANGES = "hasChanges";
    public static final String EXTRA_IS_DISCARDABLE = "isDiscardable";
    public static final String EXTRA_IS_NEW_ORDER = "isNewOrder";
    private static final String STATE_KEY_IS_NEW_ORDER = "stateKeyIsNewOrder";
    private static final String STATE_KEY_ORDER_LOCAL_ID = "stateKeyOrderModelLocalId";
    private static final String TAG_BUY_NOW_CONFIRMATION_DIALOG = "tag_buy_now_confirmation_dialog";

    private static final int PAGE_SETTINGS = 0;

    private static final int SDK_PAY_FLAG = 1;

    private boolean mIsConfigChange = false;

    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    PNViewPager mViewPager;

    private EditOrderSettingsFragment mEditOrderSettingsFragment;

    private boolean mIsNewOrder;

    @Inject
    Dispatcher mDispatcher;
    @Inject
    OrderStore mOrderStore;
    @Inject
    EditOrderRepository mEditOrderRepository;
    @Inject
    OrderUtilsWrapper mOrderUtils;
    @Inject
    EditorActionsProvider mEditorActionsProvider;

    private BuyerModel mBuyer;
    private String mInfoDispatch;
    private OrderModel mOrderDispatch;
    private BuyerModel mBuyerDispatch;

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @SuppressWarnings("unused")
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDK_PAY_FLAG: {
                    @SuppressWarnings("unchecked")
                    PayResult payResult = new PayResult((Map<String, String>) msg.obj);
                    /**
                     * 对于支付结果，请商户依赖服务端的异步通知结果。同步通知结果，仅作为支付结束的通知。
                     */
                    String resultInfo = payResult.getResult();// 同步返回需要验证的信息
                    String resultStatus = payResult.getResultStatus();

                    RemoteResultPayload payload = new RemoteResultPayload(resultInfo, resultStatus, mInfoDispatch, mOrderDispatch, mBuyerDispatch);
                    mDispatcher.dispatch(OrderActionBuilder.newDecryptResultAction(payload));

                    break;
                }
                default:
                    break;
            }
        }
    };

    private void newOrderSetup() {
        mIsNewOrder = true;

        if (mBuyer == null) {
            showErrorAndFinish(R.string.buyer_not_found);
            return;
        }
        if (!mBuyer.isVisible()) {
            showErrorAndFinish(R.string.error_buyer_hidden);
            return;
        }

        // Create a new order
        mEditOrderRepository.set(() -> {
            OrderModel order = mOrderStore.instantiateOrderModel(mBuyer, null);
            order.setStatus(OrderStatus.PAYING.toString());
            order.setOrderNumber(1);
            return order;
        });
        EventBus.getDefault().postSticky(
                new OrderEvents.OrderOpenedInEditor(mEditOrderRepository.getLocalBuyerId(), mEditOrderRepository.getId()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        EnvUtils.setEnv(EnvUtils.EnvEnum.SANDBOX);
        super.onCreate(savedInstanceState);
        ((Plutonem) getApplication()).component().inject(this);
        mDispatcher.register(this);
        setContentView(R.layout.new_edit_order_activity);

        if (savedInstanceState == null) {
            mBuyer = (BuyerModel) getIntent().getSerializableExtra(Plutonem.BUYER);
        } else {
            mBuyer = (BuyerModel) savedInstanceState.getSerializable(Plutonem.BUYER);
        }

        // Set up the action bar.
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        Bundle extras = getIntent().getExtras();
        String action = getIntent().getAction();
        if (savedInstanceState == null) {
            newOrderSetup();
        } else {
            mIsNewOrder = savedInstanceState.getBoolean(STATE_KEY_IS_NEW_ORDER, false);

            if (savedInstanceState.containsKey(STATE_KEY_ORDER_LOCAL_ID)) {
                mEditOrderRepository.loadOrderByLocalOrderId(savedInstanceState.getInt(STATE_KEY_ORDER_LOCAL_ID));
                initializeOrderObject();
            }
        }

        if (mBuyer == null) {
            ToastUtils.showToast(this, R.string.buyer_not_found, ToastUtils.Duration.SHORT);
            finish();
            return;
        }

        // Ensure we have a valid order
        if (!mEditOrderRepository.hasOrder()) {
            showErrorAndFinish(R.string.order_not_found);
            return;
        }

        setTitle(R.string.confirm_orders);
        mSectionsPagerAdapter = new SectionsPagerAdapter(fragmentManager);

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(1);
        mViewPager.setPagingEnabled(false);

        // When swiping between different sections, select the corresponding tab. We can also use ActionBar.Tab#select()
        // to do this if we have a reference to the Tab.
        mViewPager.clearOnPageChangeListeners();
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                invalidateOptionsMenu();
                if (position == PAGE_SETTINGS) {
                    setTitle(R.string.confirm_orders);
                }
            }
        });

        ActivityId.trackLastActivity(ActivityId.POST_EDITOR);
    }

    private void initializeOrderObject() {
        if (mEditOrderRepository.hasOrder()) {
            mEditOrderRepository.saveSnapshot();

            EventBus.getDefault().postSticky(new OrderEvents.OrderOpenedInEditor(mEditOrderRepository.getLocalBuyerId(),
                    mEditOrderRepository.getId()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        EventBus.getDefault().register(this);

        mIsConfigChange = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        mDispatcher.unregister(this);
        removeOrderOpenInEditorStickyEvent();

        super.onDestroy();
    }

    private void removeOrderOpenInEditorStickyEvent() {
        OrderEvents.OrderOpenedInEditor stickyEvent =
                EventBus.getDefault().getStickyEvent(OrderEvents.OrderOpenedInEditor.class);
        if (stickyEvent != null) {
            // "Consume" the sticky event
            EventBus.getDefault().removeStickyEvent(stickyEvent);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Saves both order objects so we can restore them in onCreate()
        saveOrderAsync(null);
        outState.putInt(STATE_KEY_ORDER_LOCAL_ID, mEditOrderRepository.getId());
        outState.putBoolean(STATE_KEY_IS_NEW_ORDER, mIsNewOrder);
        outState.putSerializable(Plutonem.BUYER, mBuyer);

        mIsConfigChange = true;
    }

    private PrimaryEditorAction getPrimaryAction() {
        return mEditorActionsProvider
                .getPrimaryAction(mEditOrderRepository.getStatus(), ConfirmUtils.userCanConfirm(mBuyer));
    }

    private String getPrimaryActionText() {
        return getString(getPrimaryAction().getTitleResource());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_order, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Set text of the primary action button in the ActionBar
        if (mEditOrderRepository.hasOrder()) {
            MenuItem primaryAction = menu.findItem(R.id.menu_primary_action);
            if (primaryAction != null) {
                primaryAction.setTitle(getPrimaryActionText());
                primaryAction.setVisible(mViewPager != null);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private boolean handleBackPressed() {
        saveOrderAndOptionallyFinish(true);

        return true;
    }

    // Menu actions
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == android.R.id.home) {
            return handleBackPressed();
        }

        if (itemId == R.id.menu_primary_action) {
            performPrimaryAction();
        }
        return false;
    }

    private void refreshEditorContent() {
        fillContentEditorFields();
    }

    private void showBuyNowConfirmationDialogAndBuyNowOrder() {
        showConfirmationDialogAndConfirmOrder(TAG_BUY_NOW_CONFIRMATION_DIALOG,
                getString(R.string.dialog_confirm_buy_now_title),
                getString(R.string.dialog_confirm_buy_now_message_order),
                getString(R.string.dialog_confirm_buy_now_yes),
                getString(R.string.keep_checking));
    }

    private void showConfirmationDialogAndConfirmOrder(@NonNull String identifier, @NonNull String title,
                                                       @NonNull String description, @NonNull String positiveButton,
                                                       @NonNull String negativeButton) {
        BasicFragmentDialog buyConfirmationDialog = new BasicFragmentDialog();
        buyConfirmationDialog.initialize(identifier, title, description, positiveButton, negativeButton, null);
        buyConfirmationDialog.show(getSupportFragmentManager(), identifier);
    }

    private void performPrimaryAction() {
        switch (getPrimaryAction()) {
            case CONFIRM_NOW:
                showBuyNowConfirmationDialogAndBuyNowOrder();
                return;
        }
    }

    private void saveOrderOnlineAndFinishAsync(
            boolean doFinishActivity
    ) {
        new SaveOrderOnlineAndFinishTask(doFinishActivity)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void showErrorAndFinish(int errorMessageId) {
        ToastUtils.showToast(this, errorMessageId, ToastUtils.Duration.LONG);
        finish();
    }

    private boolean updateOrderObject(boolean isAutosave) {
        if (!mEditOrderRepository.hasOrder() || mEditOrderSettingsFragment == null) {
            AppLog.e(AppLog.T.PRODUCTS, "Attempted to save an invalid Order.");
            return false;
        }
        return true;
    }

    private void saveOrderAsync(final AfterSaveOrderListener listener) {
        new Thread(() -> {
            if (updateOrderObject(false)) {
                saveOrderToDb();
                if (listener != null) {
                    listener.onOrderSave();
                }
            }
        }).start();
    }

    @Override
    public void onNegativeClicked(@NonNull String instanceTag) {
        switch (instanceTag) {
            case TAG_BUY_NOW_CONFIRMATION_DIALOG:
                break;
            default:
                AppLog.e(T.EDITOR, "Dialog instanceTag is not recognized");
                throw new UnsupportedOperationException("Dialog instanceTag is not recognized");
        }
    }

    @Override
    public void onPositiveClicked(@NonNull String instanceTag) {
        switch (instanceTag) {
            case TAG_BUY_NOW_CONFIRMATION_DIALOG:
                submitOrder(true);
                break;
            default:
                AppLog.e(T.EDITOR, "Dialog instanceTag is not recognized");
                throw new UnsupportedOperationException("Dialog instanceTag is not recognized");
        }
    }

    /*
     * user clicked OK on a settings list dialog displayed from the settings fragment - pass the event
     * along to the settings fragment
     */
    @Override
    public void onOrderSettingsFragmentPositiveButtonClicked(@NonNull OrderSettingsListDialogFragment dialog) {
        if (mEditOrderSettingsFragment != null) {
            mEditOrderSettingsFragment.onOrderSettingsFragmentPositiveButtonClicked(dialog);
        }
    }

    public interface AfterSaveOrderListener {
        void onOrderSave();
    }

    private synchronized void saveOrderToDb() {
        mDispatcher.dispatch(OrderActionBuilder.newUpdateOrderAction(mEditOrderRepository.getEditableOrder()));
    }

    private boolean isNewOrder() {
        return mIsNewOrder;
    }

    private class SaveOrderOnlineAndFinishTask extends AsyncTask<Void, Void, Void> {
        boolean mDoFinishActivity;

        SaveOrderOnlineAndFinishTask(boolean doFinishActivity) {
            this.mDoFinishActivity = doFinishActivity;
        }

        @Override
        protected Void doInBackground(Void... params) {
            // mark if the user doesn't have publishing rights
            if (!ConfirmUtils.userCanConfirm(mBuyer)) {
                switch (mEditOrderRepository.getStatus()) {
                    case UNKNOWN:
                    case FINISHED:
                    case RECEIVING:
                    case DELIVERING:
                    case PAYING:
                        break;
                }
            }

            saveOrderToDb();

            SubmitService.submitOrder(EditOrderActivity.this, mEditOrderRepository.getId());

            return null;
        }

        @Override
        protected void onPostExecute(Void saved) {
            if (mDoFinishActivity) {
//                saveResult(true, false, false);
//                removeOrderOpenInEditorStickyEvent();
//                finish();
            }
        }
    }

    private class SaveOrderLocallyAndFinishTask extends AsyncTask<Void, Void, Boolean> {
        boolean mDoFinishActivity;

        SaveOrderLocallyAndFinishTask(boolean doFinishActivity) {
            this.mDoFinishActivity = doFinishActivity;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            if (mEditOrderRepository.orderHasEdits()) {
                saveOrderToDb();
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean saved) {
            if (mDoFinishActivity) {
                saveResult(saved, false, true);
                removeOrderOpenInEditorStickyEvent();
                finish();
            }
        }
    }

    private void saveResult(boolean saved, boolean discardable, boolean savedLocally) {
        Intent i = getIntent();
        i.putExtra(EXTRA_SAVED_AS_LOCAL_DRAFT, savedLocally);
        i.putExtra(EXTRA_HAS_CHANGES, saved);
        i.putExtra(EXTRA_ORDER_LOCAL_ID, mEditOrderRepository.getId());
        i.putExtra(EXTRA_ORDER_REMOTE_ID, mEditOrderRepository.getRemoteOrderId());
        i.putExtra(EXTRA_IS_DISCARDABLE, discardable);
        i.putExtra(EXTRA_IS_NEW_ORDER, mIsNewOrder);
        setResult(RESULT_OK, i);
    }

    private void submitOrder(final boolean confirmOrder) {
        // Update order, save to db and confirm in its own Thread, because 1. update can be pretty slow with a lot of
        // ... 2. better not to call `updateOrderObject()` from the UI thread due to weird thread blocking behavior
        // on API 16 (and 21) with the visual editor.
        new Thread(() -> {
            mEditOrderRepository.update(orderModel -> {
                if (confirmOrder) {
                    // now set status to DELIVERING - only do this AFTER we have run the isFirstTimeConfirm() check,
                    // otherwise we'd have an incorrect value
                    orderModel.setStatus(OrderStatus.DELIVERING.toString());
                }

                boolean orderUpdateSuccessful = updateOrderObject();
                if (!orderUpdateSuccessful) {
                    // just return, since the only case updateOrderObject() can fail is when the editor
                    // fragment is not added to the activity
                    return false;
                }

                boolean isConfirmable = mOrderUtils.isConfirmable(orderModel);

                AppLog.d(T.PRODUCTS, "User explicitly confirmed changes. Order Product Name: " + orderModel.getProductDetail());
                // the user explicitly confirmed an intention to submit the order
                orderModel.setChangesConfirmedContentHashcode(orderModel.contentHashcode());

                // if order was modified or has unsaved local changes and is confirmable, save it
                saveResult(isConfirmable, false, false);

                if (isConfirmable) {
                    if (NetworkUtils.isNetworkAvailable(getBaseContext())) {
                        saveOrderOnlineAndFinishAsync(true);
                    } else {
                        saveOrderLocallyAndFinishAsync(true);
                    }
                } else {
                    mEditOrderRepository.updateStatusFromSnapshot(orderModel);
                    EditOrderActivity.this.runOnUiThread(() -> {
                        String message = getString(
                                R.string.error_confirm_incomplete_order);
                        ToastUtils.showToast(EditOrderActivity.this, message, ToastUtils.Duration.SHORT);
                    });
                }
                return true;
            });
        }).start();
    }

    private void saveOrderAndOptionallyFinish(final boolean doFinish) {
        saveOrderAndOptionallyFinish(doFinish, false);
    }

    private void saveOrderAndOptionallyFinish(final boolean doFinish, final boolean forceSave) {
        // Update order, save to db and order online in its own Thread, because 1. update can be pretty slow with a lot of
        // ... 2. better not to call `updateOrderObject()` from the UI thread due to weird thread blocking behavior
        // on API 16 (and 21) with the visual editor.
        new Thread(() -> {
            boolean orderUpdateSuccessful = updateOrderObject();
            if (!orderUpdateSuccessful) {
                // just return, since the only case updateOrderObject() can fail is when the editor
                // fragment is not added to the activity
                return;
            }

            boolean isConfirmable = mEditOrderRepository.isOrderConfirmable();

            // if order was modified during this editing session, save it
            boolean shouldSave = shouldSaveOrder() || forceSave;

            if (shouldSave) {
                if (doFinish) {
                    removeOrderOpenInEditorStickyEvent();
                    finish();
                }
            } else {
                // discard order if new & empty
                if (isDiscardable()) {
                    mDispatcher.dispatch(OrderActionBuilder.newRemoveOrderAction(mEditOrderRepository.getEditableOrder()));
                }
                removeOrderOpenInEditorStickyEvent();
                if (doFinish) {
                    finish();
                }
            }
        }).start();
    }

    private boolean shouldSaveOrder() {
        boolean hasChanges = mEditOrderRepository.orderHasEdits();
        boolean isConfirmable = mEditOrderRepository.isOrderConfirmable();

        // if order was modified during this editing session, save it
        return (mEditOrderRepository.hasSnapshot() && hasChanges) || (isConfirmable && isNewOrder());
    }

    private boolean isDiscardable() {
        return !mEditOrderRepository.isOrderConfirmable() && isNewOrder();
    }

    private boolean updateOrderObject() {
        return updateOrderObject(false);
    }

    private void saveOrderLocallyAndFinishAsync(boolean doFinishActivity) {
        new SaveOrderLocallyAndFinishTask(doFinishActivity).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        private static final int NUM_PAGES_EDITOR = 1;

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            switch (position) {
                case PAGE_SETTINGS:
                    return EditOrderSettingsFragment.newInstance();
                default:
                    throw new IllegalArgumentException("Unexpected page type");
            }
        }

        @Override
        public @NotNull
        Object instantiateItem(@NotNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            switch (position) {
                case PAGE_SETTINGS:
                    mEditOrderSettingsFragment = (EditOrderSettingsFragment) fragment;
                    break;
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return NUM_PAGES_EDITOR;
        }
    }

    private void fillContentEditorFields() {
        // Special actions - these only make sense for empty orders that are going to be populated now
        setOrderContentFromSubmitAction();
    }

    protected void setOrderContentFromSubmitAction() {
        Intent intent = getIntent();

        // Check for transferred information
        final String shopName = intent.getStringExtra(OrderConstants.ARG_ORDER_SHOP_NAME);
        final String productName = intent.getStringExtra(OrderConstants.ARG_ORDER_PRODUCT_NAME);
        final String itemSalesPrice = intent.getStringExtra(OrderConstants.ARG_ORDER_ITEM_PRICE);
        final String itemDistributionMode = intent.getStringExtra(OrderConstants.ARG_ORDER_ITEM_DISTRIBUTION_MODE);
        if (shopName != null) {
            mEditOrderRepository.update(orderModel -> {
                if (productName != null) {
                    mEditOrderSettingsFragment.setProductName(productName);
                    orderModel.setProductDetail(productName);
                }

                mEditOrderSettingsFragment.setShopName(shopName);
                mEditOrderSettingsFragment.setItemSalesPrice(itemSalesPrice);
                mEditOrderSettingsFragment.setItemDistributionMode(itemDistributionMode);

                // update OrderModel
                orderModel.setShopTitle(shopName);
                orderModel.setItemSalesPrice(itemSalesPrice);
                orderModel.setItemDistributionMode(itemDistributionMode);
                mEditOrderRepository.updateConfirmDateIfShouldBeConfirmedImmediately(orderModel);
                orderModel
                        .setDateLocallyChanged(DateTimeUtils.iso8601FromTimestamp(System.currentTimeMillis() / 1000));
                return true;
            });
        }
    }

    @Override
    public void onEditorFragmentInitialized() {
        onEditorFinalTouchesBeforeShowing();
    }

    private void onEditorFinalTouchesBeforeShowing() {
        refreshEditorContent();
    }

    // FluxC events
    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderChanged(OnOrderChanged event) {
        if (event.causeOfChange instanceof CauseOfOnOrderChanged.UpdateOrder) {
            if (!event.isError()) {
                // here update the menu if it's not a paying anymore
                invalidateOptionsMenu();
            } else {
                AppLog.e(AppLog.T.PRODUCTS, "UPDATE_ORDER failed: " + event.error.type + " - " + event.error.message);
            }
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onInfoEncrypted(OnInfoEncrypted event) {
        this.mInfoDispatch = event.info;
        this.mOrderDispatch = event.order;
        this.mBuyerDispatch = event.buyer;

        final String orderInfo = event.info;

        final Runnable payRunnable = new Runnable() {

            @Override
            public void run() {
                PayTask alipay = new PayTask(EditOrderActivity.this);
                Map<String, String> result = alipay.payV2(orderInfo, true);
                Log.i("msp", result.toString());

                Message msg = new Message();
                msg.what = SDK_PAY_FLAG;
                msg.obj = result;
                mHandler.sendMessage(msg);
            }
        };

        // 必须异步调用
        Thread payThread = new Thread(payRunnable);
        payThread.start();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onResultDecrypted(OnResultDecrypted event) {
        if (!event.isError()) {
            saveResult(true, false, false);
            removeOrderOpenInEditorStickyEvent();
            finish();
        } else {
            EditOrderActivity.this.runOnUiThread(() -> {
                String message = getString(
                        R.string.error_pay_order);
                ToastUtils.showToast(EditOrderActivity.this, message, ToastUtils.Duration.SHORT);
            });
        }
    }

    // EditOrderActivityHook methods

    @Override
    public EditOrderRepository getEditOrderRepository() {
        return mEditOrderRepository;
    }
}

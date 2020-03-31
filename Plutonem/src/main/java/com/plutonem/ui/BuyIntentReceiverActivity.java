package com.plutonem.ui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.TaskStackBuilder;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.android.fluxc.store.BuyerStore;
import com.plutonem.ui.BuyIntentReceiverFragment.BuyAction;
import com.plutonem.ui.BuyIntentReceiverFragment.BuyIntentFragmentListener;
import com.plutonem.ui.main.PMainActivity;
import com.plutonem.ui.nemur.NemurConstants;
import com.plutonem.ui.products.OrderConstants;
import com.plutonem.utilities.FluxCUtils;

import org.wordpress.android.util.ToastUtils;

import java.util.List;

import javax.inject.Inject;

/**
 * An activity to handle buy intents, since there are multiple actions possible.
 * If the user is not logged in, redirects the user to the LoginFlow. When the user is logged in,
 * displays BuyIntentReceiverFragment. The fragment lets the user choose which buyer to submit to.
 * Moreover it lists what actions the user can perform and redirects the user to the activity,
 * along with the content passed in the intent.
 */
public class BuyIntentReceiverActivity extends AppCompatActivity implements BuyIntentFragmentListener {
    private static final String BUY_LAST_USED_BUYER_ID_KEY = "pn-settings-buy-last-used-text-buyerid";

    @Inject AccountStore mAccountStore;
    @Inject BuyerStore mBuyerStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((Plutonem) getApplication()).component().inject(this);
        setContentView(R.layout.buy_intent_receiver_activity);

        if (savedInstanceState == null) {
            refreshContent();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // keep setBackground in the onResume, otherwise the transition between activities is visible to the user when
        // sharing text with an account with one visible site
        findViewById(R.id.main_view).setBackgroundResource(R.color.background_default);
    }

    private void refreshContent() {
        if (FluxCUtils.isSignedInPN(mAccountStore)) {
            List<BuyerModel> visibleBuyers = mBuyerStore.getVisibleBuyers();
            if (visibleBuyers.size() == 0) {
                ToastUtils.showToast(this, R.string.cant_buy_no_visible_buyer, ToastUtils.Duration.LONG);
                finish();
            } else if (visibleBuyers.size() == 1) {
                // if only one buyer, then don't show the fragment, buy it directly within edit order view
                buy(BuyAction.BUY_WITHIN_EDIT_ORDER_VIEW, visibleBuyers.get(0).getId());
            }
        } else {
            // start the login flow and wait onActivityResult
            ActivityLauncher.loginForBuyIntent(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RequestCodes.DO_LOGIN) {
            if (resultCode == RESULT_OK) {
                // login successful
                refreshContent();
            } else {
                finish();
            }
        }
    }

    @Override
    public void buy(BuyAction buyAction, int selectedBuyerLocalId) {
        if (checkAndRequestPermissions()) {
            Intent intent = new Intent(this, buyAction.targetClass);
            startActivityAndFinish(intent, selectedBuyerLocalId);
        }
    }

    private boolean checkAndRequestPermissions() {
        return true;
    }

    private void startActivityAndFinish(@NonNull Intent intent, int mSelectedBuyerLocalId) {
        String action = getIntent().getAction();
        intent.setAction(action);
        intent.setType(getIntent().getType());

        intent.putExtra(Plutonem.BUYER, mBuyerStore.getBuyerByLocalId(mSelectedBuyerLocalId));

        intent.putExtra(OrderConstants.ARG_ORDER_SHOP_NAME, getIntent().getStringExtra(NemurConstants.ARG_SHOP_NAME));
        intent.putExtra(OrderConstants.ARG_ORDER_PRODUCT_NAME, getIntent().getStringExtra(NemurConstants.ARG_PRODUCT_NAME));
        intent.putExtra(OrderConstants.ARG_ORDER_ITEM_PRICE, getIntent().getStringExtra(NemurConstants.ARG_ITEM_PRICE));
        intent.putExtra(OrderConstants.ARG_ORDER_ITEM_DISTRIBUTION_MODE, getIntent().getStringExtra(NemurConstants.ARG_ITEM_DISTRIBUTION_MODE));

        // save preferences
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(BUY_LAST_USED_BUYER_ID_KEY, mSelectedBuyerLocalId)
                .apply();

        startActivityWithSyntheticBackstack(intent);
        finish();
    }

    private void startActivityWithSyntheticBackstack(@NonNull Intent intent) {
        Intent parentIntent = new Intent(this, PMainActivity.class);
        parentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        parentIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        parentIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        TaskStackBuilder.create(this).addNextIntent(parentIntent).addNextIntent(intent).startActivities();
    }
}

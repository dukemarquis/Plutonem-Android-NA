package com.plutonem.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.model.BuyerModel;
import com.plutonem.android.login.LoginMode;
import com.plutonem.ui.accounts.LoginActivity;
import com.plutonem.ui.main.PMainActivity;
import com.plutonem.ui.products.ProductsListActivity;

import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.ToastUtils;

public class ActivityLauncher {
    public static void showMainActivity(Activity activity) {
        Intent intent = new Intent(activity, PMainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }

    public static void viewCurrentBuyerProducts(Context context, BuyerModel buyer) {
        if (buyer == null) {
            AppLog.e(T.PRODUCTS, "buyer cannot be null when opening products");
            ToastUtils.showToast(context, R.string.orders_cannot_be_started, ToastUtils.Duration.SHORT);
        }
        Intent intent = new Intent(context, ProductsListActivity.class);
        intent.putExtra(Plutonem.BUYER, buyer);
        context.startActivity(intent);
    }

    public static void showSignInForResult(Activity activity) {
        Intent intent = new Intent(activity, LoginActivity.class);
        activity.startActivityForResult(intent, RequestCodes.ADD_ACCOUNT);
    }

    public static void loginForBuyIntent(Activity activity) {
        Intent intent = new Intent(activity, LoginActivity.class);
        LoginMode.BUY_INTENT.putInto(intent);
        activity.startActivityForResult(intent, RequestCodes.DO_LOGIN);
    }
}

package com.plutonem.ui.nemur;

import android.content.Context;
import android.content.Intent;

import com.plutonem.models.NemurTag;
import com.plutonem.ui.BuyIntentReceiverActivity;
import com.plutonem.ui.nemur.NemurTypes.NemurOrderListType;

public class NemurActivityLauncher {
    /*
     * show a single nemur order in the detail view - simply calls showNemurOrderPager
     * with a single order
     */
    public static void showNemurOrderDetail(Context context, long buyerId, long orderId) {
        showNemurOrderDetail(context, buyerId, orderId, new Object());
    }

    public static void showNemurOrderDetail(Context context,
                                            long buyerId,
                                            long orderId,
                                            Object conservativeParam) {
        Intent intent = new Intent(context, NemurOrderPagerActivity.class);
        intent.putExtra(NemurConstants.ARG_BUYER_ID, buyerId);
        intent.putExtra(NemurConstants.ARG_ORDER_ID, orderId);
        intent.putExtra(NemurConstants.ARG_IS_SINGLE_ORDER, true);
        context.startActivity(intent);
    }

    /*
     * show pager view of orders with a specific tag - passed buyerId/orderId is the order
     * to select after the pager is populated
     */
    public static void showNemurOrderPagerForTag(Context context,
                                                 NemurTag tag,
                                                 NemurOrderListType orderListType,
                                                 long buyerId,
                                                 long orderId) {
        if (tag == null) {
            return;
        }

        Intent intent = new Intent(context, NemurOrderPagerActivity.class);
        intent.putExtra(NemurConstants.ARG_ORDER_LIST_TYPE, orderListType);
        intent.putExtra(NemurConstants.ARG_TAG, tag);
        intent.putExtra(NemurConstants.ARG_BUYER_ID, buyerId);
        intent.putExtra(NemurConstants.ARG_ORDER_ID, orderId);
        context.startActivity(intent);
    }

    /*
     * show order edit view with the passed shop name and product name
     */
    public static void showEditOrderView(Context context,
                                         String shopName,
                                         String productName,
                                         String itemPrice,
                                         String itemDistributionMode) {
        showEditOrderView(context, shopName, productName, itemPrice, itemDistributionMode, 0);
    }

    /**
     * Show edit order view and directly perform an action to confirm an order
     *
     * @param context context to use to start the activity
     * @param shopName shop name
     * @param productName product name
     * @param itemPrice item sales price
     * @param itemDistributionMode item distribution mode
     * @param reservedArgument reserved argument
     */
    public static void showEditOrderView(Context context,
                                         String shopName,
                                         String productName,
                                         String itemPrice,
                                         String itemDistributionMode,
                                         long reservedArgument) {
        Intent intent = new Intent(context, BuyIntentReceiverActivity.class);
        intent.putExtra(NemurConstants.ARG_SHOP_NAME, shopName);
        intent.putExtra(NemurConstants.ARG_PRODUCT_NAME, productName);
        intent.putExtra(NemurConstants.ARG_ITEM_PRICE, itemPrice);
        intent.putExtra(NemurConstants.ARG_ITEM_DISTRIBUTION_MODE, itemDistributionMode);
        context.startActivity(intent);
    }
}

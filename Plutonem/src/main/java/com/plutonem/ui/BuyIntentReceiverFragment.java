package com.plutonem.ui;

import androidx.fragment.app.Fragment;

import com.plutonem.ui.products.EditOrderActivity;

public class BuyIntentReceiverFragment extends Fragment {
    enum BuyAction {
        BUY_WITHIN_EDIT_ORDER_VIEW("new_order", EditOrderActivity.class);

        public final Class targetClass;
        public final String analyticsName;

        BuyAction(String analyticsName, Class targetClass) {
            this.targetClass = targetClass;
            this.analyticsName = analyticsName;
        }
    }

    interface BuyIntentFragmentListener {
        void buy(BuyAction buyAction, int selectedBuyerLocalId);
    }
}

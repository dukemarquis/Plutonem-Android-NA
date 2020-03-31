package com.plutonem.ui.products;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.ui.products.OrderSettingsListDialogFragment.DialogType;

import org.wordpress.android.util.AccessibilityUtils;

import javax.inject.Inject;

public class EditOrderSettingsFragment extends Fragment {
    private LinearLayout mNameContainer;
    private LinearLayout mPhoneNumberContainer;
    private LinearLayout mAddressContainer;
    private TextView mNameTextView;
    private TextView mPhoneNumberTextView;
    private TextView mAddressTextView;
    private TextView mShopTitleTextView;
    private TextView mProductNameTextView;
    private TextView mItemSalesPriceTextView;
    private TextView mNumberTextView;
    private TextView mItemDistributionModeTextView;
    private TextView mTotalPriceTextView;
    private TextView mDeliveryInformationHeaderTextView;
    private TextView mDetailHeaderTextView;

//    @Inject Dispatcher mDispatcher;
    @Inject OrderSettingsUtils mOrderSettingsUtils;

    protected EditorFragmentListener mEditorFragmentListener;

    interface EditOrderActivityHook {
        EditOrderRepository getEditOrderRepository();
    }

    public static EditOrderSettingsFragment newInstance() {
        return new EditOrderSettingsFragment();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mEditorFragmentListener = (EditorFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement EditorFragmentListener");
        }

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((Plutonem) getActivity().getApplicationContext()).component().inject(this);
//        mDispatcher.register(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        refreshViews();
    }

    @Override
    public void onDestroy() {
//        mDispatcher.unregister(this);
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.edit_order_settings_fragment, container, false);

        if (rootView == null) {
            return null;
        }

        mNameTextView = rootView.findViewById(R.id.order_name);
        mPhoneNumberTextView = rootView.findViewById(R.id.order_phone_number);
        mAddressTextView = rootView.findViewById(R.id.order_address);
        mShopTitleTextView = rootView.findViewById(R.id.order_shop_name);
        mProductNameTextView = rootView.findViewById(R.id.order_good_name);
        mItemSalesPriceTextView = rootView.findViewById(R.id.order_item_sales_price);
        mNumberTextView = rootView.findViewById(R.id.order_number);
        mItemDistributionModeTextView = rootView.findViewById(R.id.order_item_distribution_mode);
        mTotalPriceTextView = rootView.findViewById(R.id.total_price);
        mDeliveryInformationHeaderTextView = rootView.findViewById(R.id.order_settings_delivery_information_header);
        mDetailHeaderTextView = rootView.findViewById(R.id.order_settings_detail);

        mNameContainer = rootView.findViewById(R.id.order_name_container);
        mNameContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOrderNameDialog();
            }
        });

        mPhoneNumberContainer = rootView.findViewById(R.id.order_phone_number_container);
        mPhoneNumberContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOrderPhoneNumberDialog();
            }
        });

        mAddressContainer = rootView.findViewById(R.id.order_address_container);
        mAddressContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOrderAddressDialog();
            }
        });

        final LinearLayout numberContainer = rootView.findViewById(R.id.order_number_container);
        numberContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNumberDialog();
            }
        });


        setupSettingHintsForAccessibility();
        applyAccessibilityHeadingToSettings();

        // container is empty, which means it's a fresh instance so, signal to complete its init
        mEditorFragmentListener.onEditorFragmentInitialized();

        return rootView;
    }

    private void setupSettingHintsForAccessibility() {
        AccessibilityUtils.disableHintAnnouncement(mShopTitleTextView);
        AccessibilityUtils.disableHintAnnouncement(mProductNameTextView);
        AccessibilityUtils.disableHintAnnouncement(mItemSalesPriceTextView);
        AccessibilityUtils.disableHintAnnouncement(mItemDistributionModeTextView);
        AccessibilityUtils.disableHintAnnouncement(mTotalPriceTextView);
        AccessibilityUtils.disableHintAnnouncement(mNameTextView);
        AccessibilityUtils.disableHintAnnouncement(mPhoneNumberTextView);
        AccessibilityUtils.disableHintAnnouncement(mAddressTextView);
    }

    private void applyAccessibilityHeadingToSettings() {
        AccessibilityUtils.enableAccessibilityHeading(mDetailHeaderTextView);
        AccessibilityUtils.enableAccessibilityHeading(mDeliveryInformationHeaderTextView);
    }

    public void refreshViews() {
        if (!isAdded()) {
            return;
        }

        mShopTitleTextView.setText(getEditOrderRepository().getShopTitle());
        mProductNameTextView.setText(getEditOrderRepository().getProductName());
        mItemSalesPriceTextView.setText(getEditOrderRepository().getItemSalesPrice());
        mItemDistributionModeTextView.setText(getEditOrderRepository().getItemDistributionMode());
        mNameTextView.setText(getEditOrderRepository().getOrderName());
        mPhoneNumberTextView.setText(getEditOrderRepository().getOrderPhoneNumber());
        mAddressTextView.setText(getEditOrderRepository().getOrderAddress());
        updateNumberTextView();
        updateTotalPriceTextView();
    }

    public void setShopName(String shopName) {
        EditOrderRepository editorderRepository = getEditOrderRepository();
        if (editorderRepository != null) {
            editorderRepository.update(orderModel -> {
                orderModel.setShopTitle(shopName);
                mShopTitleTextView.setText(shopName);
                return true;
            });
        }
    }

    public void setProductName(String productName) {
        EditOrderRepository editorderRepository = getEditOrderRepository();
        if (editorderRepository != null) {
            editorderRepository.update(orderModel -> {
                orderModel.setProductDetail(productName);
                mProductNameTextView.setText(productName);
                return true;
            });
        }
    }

    public void setItemSalesPrice(String itemSalesPrice) {
        EditOrderRepository editorderRepository = getEditOrderRepository();
        if (editorderRepository != null) {
            editorderRepository.update(orderModel -> {
                orderModel.setItemSalesPrice(itemSalesPrice);
                mItemSalesPriceTextView.setText(itemSalesPrice);
                return true;
            });
        }
    }

    public void setItemDistributionMode(String itemDistributionMode) {
        EditOrderRepository editorderRepository = getEditOrderRepository();
        if (editorderRepository != null) {
            editorderRepository.update(orderModel -> {
                orderModel.setItemDistributionMode(itemDistributionMode);
                mItemDistributionModeTextView.setText(itemDistributionMode);
                return true;
            });
        }
    }

    private void showOrderNameDialog() {
        if (!isAdded()) {
            return;
        }
        OrderSettingsInputDialogFragment dialog = OrderSettingsInputDialogFragment.newInstance(
                getEditOrderRepository().getOrderName(), getString(R.string.order_settings_name),
                getString(R.string.order_settings_name_dialog_hint), false);
        dialog.setOrderSettingsInputDialogListener(
                new OrderSettingsInputDialogFragment.OrderSettingsInputDialogListener() {
                    @Override
                    public void onInputUpdated(String input) {
                        updateName(input);
                    }
                });
        dialog.show(getChildFragmentManager(), null);
    }

    private void showOrderPhoneNumberDialog() {
        if (!isAdded()) {
            return;
        }
        OrderSettingsInputDialogFragment dialog = OrderSettingsInputDialogFragment.newInstance(
                getEditOrderRepository().getOrderPhoneNumber(), getString(R.string.order_settings_phone_number),
                getString(R.string.order_settings_phone_number_dialog_hint), false);
        dialog.setOrderSettingsInputDialogListener(
                new OrderSettingsInputDialogFragment.OrderSettingsInputDialogListener() {
                    @Override
                    public void onInputUpdated(String input) {
                        updatePhoneNumber(input);
                    }
                });
        dialog.show(getChildFragmentManager(), null);
    }

    private void showOrderAddressDialog() {
        if (!isAdded()) {
            return;
        }
        OrderSettingsInputDialogFragment dialog = OrderSettingsInputDialogFragment.newInstance(
                getEditOrderRepository().getOrderAddress(), getString(R.string.order_settings_address),
                getString(R.string.order_settings_address_dialog_hint), false);
        dialog.setOrderSettingsInputDialogListener(
                new OrderSettingsInputDialogFragment.OrderSettingsInputDialogListener() {
                    @Override
                    public void onInputUpdated(String input) {
                        updateAddress(input);
                    }
                });
        dialog.show(getChildFragmentManager(), null);
    }

    /*
     * called by the activity when the user taps OK on a OrderSettingsDialogFragment
     */
    public void onOrderSettingsFragmentPositiveButtonClicked(@NonNull OrderSettingsListDialogFragment fragment) {
        switch (fragment.getDialogType()) {
            case ORDER_NUMBER:
                int index = fragment.getCheckedIndex();
                long number = getOrderNumberAtIndex(index);
                updateOrderNumber(number);
                break;
        }
    }

    private void showNumberDialog() {
        if (!isAdded()) {
            return;
        }

        int index = getCurrentOrderNumberIndex();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        OrderSettingsListDialogFragment fragment =
                OrderSettingsListDialogFragment.newInstance(DialogType.ORDER_NUMBER, index);
        fragment.show(fm, OrderSettingsListDialogFragment.TAG);
    }

    // Helpers

    private EditOrderRepository getEditOrderRepository() {
        if (getEditOrderActivityHook() == null) {
            // This can only happen during a callback while activity is re-created for some reason (config changes etc)
            return null;
        }
        return getEditOrderActivityHook().getEditOrderRepository();
    }

    private EditOrderActivityHook getEditOrderActivityHook() {
        Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        if (activity instanceof EditOrderActivityHook) {
            return (EditOrderActivityHook) activity;
        } else {
            throw new RuntimeException(activity.toString() + " must implement EditOrderActivityHook");
        }
    }

    private void updateName(String name) {
        EditOrderRepository editOrderRepository = getEditOrderRepository();
        if (editOrderRepository != null) {
            editOrderRepository.update(orderModel -> {
                orderModel.setOrderName(name);
                mNameTextView.setText(name);
                return true;
            });
        }
    }

    private void updatePhoneNumber(String phoneNumber) {
        EditOrderRepository editOrderRepository = getEditOrderRepository();
        if (editOrderRepository != null) {
            editOrderRepository.update(orderModel -> {
                orderModel.setOrderPhoneNumber(phoneNumber);
                mPhoneNumberTextView.setText(phoneNumber);
                return true;
            });
        }
    }

    private void updateAddress(String address) {
        EditOrderRepository editOrderRepository = getEditOrderRepository();
        if (editOrderRepository != null) {
            editOrderRepository.update(orderModel -> {
                orderModel.setOrderAddress(address);
                mAddressTextView.setText(address);
                return true;
            });
        }
    }

    public void updateOrderNumber(long orderNumber) {
        EditOrderRepository editOrderRepository = getEditOrderRepository();
        if (editOrderRepository != null) {
            editOrderRepository.update(orderModel -> {
                orderModel.setOrderNumber(orderNumber);
                updateOrderNumberRelatedViews();
                return true;
            });
        }
    }

    public void updateOrderNumberRelatedViews() {
        updateNumberTextView();
        updateTotalPriceTextView();
    }

    private void updateNumberTextView() {
        if (!isAdded()) {
            return;
        }
        String[] numbers = getResources().getStringArray(R.array.order_settings_numbers);
        int index = getCurrentOrderNumberIndex();
        // We should never get an OutOfBoundsException here, but if we do,
        // we should let it crash so we can fix the underlying issue
        mNumberTextView.setText(numbers[index]);
    }

    private void updateTotalPriceTextView() {
        if (!isAdded()) {
            return;
        }
        EditOrderRepository orderRepository = getEditOrderRepository();
        if (orderRepository != null) {
            String labelToUse = mOrderSettingsUtils.getTotalPriceLabel(orderRepository);
            mTotalPriceTextView.setText(labelToUse);
        }
    }

    // Order Number Helpers

    private long getOrderNumberAtIndex(int index) {
        switch (index) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            default:
                return 5;
        }
    }

    private int getCurrentOrderNumberIndex() {
        switch ((int) getEditOrderRepository().getNumber()) {
            case 1:
                return 0;
            case 2:
                return 1;
            case 3:
                return 2;
            case 4:
                return 3;
            case 5:
                return 4;
        }
        return 0;
    }

    /**
     * Callbacks used to communicate with the parent Activity
     */
    public interface EditorFragmentListener {
        void onEditorFragmentInitialized();
    }
}

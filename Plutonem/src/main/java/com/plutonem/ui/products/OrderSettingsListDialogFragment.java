package com.plutonem.ui.products;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.plutonem.R;

import org.wordpress.android.util.AppLog;

public class OrderSettingsListDialogFragment extends DialogFragment {
    private static final String ARG_DIALOG_TYPE = "dialog_type";
    private static final String ARG_CHECKED_INDEX = "checked_index";

    public static final String TAG = "order_list_settings_dialog_fragment";

    enum DialogType {
        ORDER_NUMBER
    }

    interface OnOrderSettingsDialogFragmentListener {
        void onOrderSettingsFragmentPositiveButtonClicked(@NonNull OrderSettingsListDialogFragment fragment);
    }

    private DialogType mDialogType;
    private int mCheckedIndex;
    private OnOrderSettingsDialogFragmentListener mListener;

    public static OrderSettingsListDialogFragment newInstance(@NonNull DialogType dialogType, int index) {
        OrderSettingsListDialogFragment fragment = new OrderSettingsListDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_DIALOG_TYPE, dialogType);
        args.putInt(ARG_CHECKED_INDEX, index);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
    }

    @Override
    public void setArguments(Bundle args) {
        super.setArguments(args);
        mDialogType = (DialogType) args.getSerializable(ARG_DIALOG_TYPE);
        mCheckedIndex = args.getInt(ARG_CHECKED_INDEX);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (OnOrderSettingsDialogFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnPostSettingsDialogFragmentListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(getActivity(), R.style.Calypso_Dialog_Alert));

        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mCheckedIndex = which;
                getArguments().putInt(ARG_CHECKED_INDEX, mCheckedIndex);
            }
        };

        switch (mDialogType) {
            case ORDER_NUMBER:
                builder.setTitle(R.string.order_settings_number);
                builder.setSingleChoiceItems(
                        R.array.order_settings_numbers,
                        mCheckedIndex,
                        clickListener);
                break;
        }

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mListener.onOrderSettingsFragmentPositiveButtonClicked(OrderSettingsListDialogFragment.this);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);

        return builder.create();
    }

    public DialogType getDialogType() {
        return mDialogType;
    }

    public int getCheckedIndex() {
        return mCheckedIndex;
    }

    public @Nullable
    String getSelectedItem() {
        ListView listView = ((AlertDialog) getDialog()).getListView();
        if (listView != null) {
            try {
                return (String) listView.getItemAtPosition(mCheckedIndex);
            } catch (IndexOutOfBoundsException e) {
                AppLog.e(AppLog.T.PRODUCTS, e);
            }
        }
        return null;
    }
}

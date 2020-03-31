package com.plutonem.android.login;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.plutonem.android.fluxc.generated.AccountActionBuilder;
import com.plutonem.android.fluxc.store.AccountStore.OnAvailabilityChecked;
import com.plutonem.android.login.widgets.PLoginInputRow;
import com.plutonem.android.login.widgets.PLoginInputRow.OnEditorCommitListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.ActivityUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.EditTextUtils;
import org.wordpress.android.util.NetworkUtils;

import dagger.android.support.AndroidSupportInjection;

public class SignupPhoneFragment extends LoginBaseFormFragment<LoginListener> implements TextWatcher,
        OnEditorCommitListener {
    private static final String KEY_REQUESTED_PHONE = "KEY_REQUESTED_PHONE";

    public static final String TAG = "signup_phone_fragment_tag";
    public static final int MAX_PHONE_LENGTH = 11;

    private String mRequestedPhone;

    protected Button mPrimaryButton;
    protected PLoginInputRow mPhoneInput;

    @Override
    protected @LayoutRes int getContentLayout() {
        return R.layout.signup_phone_fragment;
    }

    @Override
    protected @LayoutRes int getProgressBarText() {
        return R.string.checking_phone;
    }

    @Override
    protected void setupLabel(@NonNull TextView label) {
        label.setText(R.string.signup_phone_header);
    }

    @Override
    protected void setupContent(ViewGroup rootView) {
        // important for accessibility - talkback
        getActivity().setTitle(R.string.signup_phone_screen_title);
        mPhoneInput = rootView.findViewById(R.id.login_phone_row);
        mPhoneInput.addTextChangedListener(this);
        mPhoneInput.setOnEditorCommitListener(this);
    }

    @Override
    protected void setupBottomButtons(Button primaryButton) {
        mPrimaryButton = primaryButton;
        mPrimaryButton.setEnabled(false);
        mPrimaryButton.setOnClickListener(new View.OnClickListener() {
            @SuppressWarnings("PrivateMemberAccessBetweenOuterAndInnerClass")
            public void onClick(View view) {
                next(getCleanedPhone());
            }
        });
    }

    @Override
    protected EditText getEditTextToFocusOnStart() {
        return mPhoneInput.getEditText();
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mRequestedPhone = savedInstanceState.getString(KEY_REQUESTED_PHONE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REQUESTED_PHONE, mRequestedPhone);
    }

    protected void next(String phone) {
        if (NetworkUtils.checkConnection(getActivity())) {
            if (isValidPhone(phone)) {
                startProgress();
                mRequestedPhone = phone;
                mDispatcher.dispatch(AccountActionBuilder.newIsAvailablePhoneAction(phone));
            } else {
                showErrorPhone(getString(R.string.phone_invalid));
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mLoginListener = null;
    }

    private String getCleanedPhone() {
        return EditTextUtils.getText(mPhoneInput.getEditText()).trim();
    }

    private boolean isValidPhone(String phone) {
        return phone.length() <= MAX_PHONE_LENGTH;
    }

    @Override
    public void onEditorCommit() {
        next(getCleanedPhone());
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mPhoneInput.setError(null);
        mPrimaryButton.setEnabled(!s.toString().trim().isEmpty());
    }

    protected void showErrorDialog(String message) {
        AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.LoginTheme))
                .setMessage(message)
                .setPositiveButton(R.string.login_error_button, null)
                .create();
        dialog.show();
    }

    private void showErrorPhone(String message) {
        mPhoneInput.setError(message);
    }

    @Override
    protected void endProgress() {
        super.endProgress();
        mRequestedPhone = null;
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAvailabilityChecked(OnAvailabilityChecked event) {
        if (mRequestedPhone != null && mRequestedPhone.equalsIgnoreCase(event.value)) {
            if (isInProgress()) {
                endProgress();
            }

            if (event.isError()) {
                AppLog.e(AppLog.T.API, "OnAvailabilityChecked error: " + event.error.type + " - " + event.error.message);
                showErrorDialog(getString(R.string.signup_phone_error_generic));
            } else {
                switch (event.type) {
                    case PHONE:
                        ActivityUtils.hideKeyboard(getActivity());

                        if (mLoginListener != null) {
                            if (event.isAvailable) {
                                mLoginListener.showSignupPhonePassword(event.value);
                            } else {

                            }
                        }

                        break;
                    default:
                        AppLog.e(AppLog.T.API, "OnAvailabilityChecked unhandled event: " + event.error.type);
                        break;
                }
            }
        }
    }
}

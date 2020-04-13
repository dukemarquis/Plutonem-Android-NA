package com.plutonem.android.login;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.plutonem.android.fluxc.generated.AccountActionBuilder;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.android.fluxc.store.AccountStore.OnAvailabilityChecked;
import com.plutonem.android.login.widgets.PNLoginInputRow;
import com.plutonem.android.login.widgets.PNLoginInputRow.OnEditorCommitListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.ActivityUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.EditTextUtils;
import org.wordpress.android.util.NetworkUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.android.support.AndroidSupportInjection;

public class LoginPhoneFragment extends LoginBaseFormFragment<LoginListener> implements TextWatcher,
        OnEditorCommitListener {
    private static final String KEY_IS_SOCIAL = "KEY_IS_SOCIAL";
    private static final String KEY_REQUESTED_PHONE = "KEY_REQUESTED_PHONE";

    public static final String TAG = "login_email_fragment_tag";
    public static final int MAX_PHONE_LENGTH = 11;

    private String mRequestedPhone;
    private boolean mIsSocialLogin;

    protected PNLoginInputRow mPhoneInput;

    @Override
    protected @LayoutRes
    int getContentLayout() {
        return R.layout.login_phone_screen;
    }

    @Override
    protected @LayoutRes
    int getProgressBarText() {
        return mIsSocialLogin ? R.string.logging_in : R.string.checking_phone;
    }

    @Override
    protected void setupLabel(@NonNull TextView label) {
        switch (mLoginListener.getLoginMode()) {
            case FULL:
            case BUY_INTENT:
            case CHAT_INTENT:
                label.setText(R.string.enter_phone_plutonem);
                break;
        }
    }

    @Override
    protected void setupContent(ViewGroup rootView) {
        // important for accessibility - talkback
        getActivity().setTitle(R.string.phone_number_login_title);
        mPhoneInput = rootView.findViewById(R.id.login_phone_row);
        mPhoneInput.addTextChangedListener(this);
        mPhoneInput.setOnEditorCommitListener(this);

        LinearLayout phoneSignupButton = rootView.findViewById(R.id.signup_phone_button);
        phoneSignupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mLoginListener != null) {
                        mLoginListener.doStartSignup();
                }
            }
        });
    }

    @Override
    protected void setupBottomButtons(Button primaryButton) {
        primaryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                next(getCleanedPhone());
            }
        });
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
            mIsSocialLogin = savedInstanceState.getBoolean(KEY_IS_SOCIAL);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REQUESTED_PHONE, mRequestedPhone);
        outState.putBoolean(KEY_IS_SOCIAL, mIsSocialLogin);
    }

    protected void next(String phone) {
        if (!NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        if (isValidPhone(phone)) {
            startProgress();
            mRequestedPhone = phone;
            mDispatcher.dispatch(AccountActionBuilder.newIsAvailablePhoneAction(phone));
        } else {
            showPhoneError(R.string.phone_invalid);
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
        Pattern phoneRegExPattern = Patterns.PHONE;
        Matcher matcher = phoneRegExPattern.matcher(phone);

        return matcher.find() && phone.length() <= MAX_PHONE_LENGTH;
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
        mIsSocialLogin = false;
    }

    private void showPhoneError(int messageId) {
        mPhoneInput.setError(getString(messageId));
    }

    private void showErrorDialog(String message) {
        AlertDialog dialog = new AlertDialog.Builder(new ContextThemeWrapper(getActivity(), R.style.LoginTheme))
                .setMessage(message)
                .setPositiveButton(R.string.login_error_button, null)
                .create();
        dialog.show();
    }

    @Override
    protected void endProgress() {
        super.endProgress();
        mRequestedPhone = null;
    }

    // OnChanged events

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAvailabilityChecked(OnAvailabilityChecked event) {
        if (mRequestedPhone == null || !mRequestedPhone.equalsIgnoreCase(event.value)) {
            // bail if user canceled or a different phone request is outstanding
            return;
        }

        if (isInProgress()) {
            endProgress();
        }

        if (event.isError()) {
            // report the error but don't bail yet.
            AppLog.e(AppLog.T.API, "OnAvailabilityChecked has error: " + event.error.type + " - " + event.error.message);
            // hide the keyboard
            ActivityUtils.hideKeyboardForced(mPhoneInput);
            // we validate the phone prior to making the request, but just to be safe...
            if (event.error.type == AccountStore.IsAvailableErrorType.INVALID) {
                showPhoneError(R.string.phone_invalid);
            } else {
                showErrorDialog(getString(R.string.error_generic_network));
            }
            return;
        }

        switch (event.type) {
            case PHONE:
                if (event.isAvailable) {
                    // phone number is available on plutonem, so apparently the user can't login with that one.
                    ActivityUtils.hideKeyboardForced(mPhoneInput);
                    showPhoneError(R.string.phone_not_registered_plutonem);
                } else if (mLoginListener != null) {
                    ActivityUtils.hideKeyboardForced(mPhoneInput);
                    mLoginListener.gotPncomPhone(event.value, false);
                }
                break;
            default:
                AppLog.e(AppLog.T.API, "OnAvailabilityChecked unhandled event type: " + event.error.type);
                break;
        }
    }
}

package com.plutonem.android.login;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AutoForeground;
import org.wordpress.android.util.NetworkUtils;

import com.google.android.material.button.MaterialButton;
import com.plutonem.android.login.LoginPncomService.LoginState;
import com.plutonem.android.login.widgets.PNLoginInputRow;
import com.plutonem.android.login.widgets.PNLoginInputRow.OnEditorCommitListener;

import dagger.android.support.AndroidSupportInjection;

public class LoginPhonePasswordFragment extends LoginBaseFormFragment<LoginListener> implements TextWatcher,
        OnEditorCommitListener {
    private static final String KEY_REQUESTED_PASSWORD = "KEY_REQUESTED_PASSWORD";

    private static final String ARG_PHONE_NUMBER = "ARG_PHONE_NUMBER";
    private static final String ARG_PASSWORD = "ARG_PASSWORD";
    private static final String ARG_SOCIAL_ID_TOKEN = "ARG_SOCIAL_ID_TOKEN";
    private static final String ARG_SOCIAL_LOGIN = "ARG_SOCIAL_LOGIN";
    private static final String ARG_SOCIAL_SERVICE = "ARG_SOCIAL_SERVICE";

    public static final String TAG = "login_phone_password_fragment_tag";

    private PNLoginInputRow mPasswordInput;
    private MaterialButton mNextButton;

    private String mRequestedPassword;

    private String mPhoneNumber;
    private String mIdToken;
    private String mPassword;
    private String mService;
    private boolean mIsSocialLogin;

    private AutoForeground.ServiceEventConnection mServiceEventConnection;

    public static LoginPhonePasswordFragment newInstance(String phoneNumber, String password,
                                                         String idToken, String service,
                                                         boolean isSocialLogin) {
        LoginPhonePasswordFragment fragment = new LoginPhonePasswordFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHONE_NUMBER, phoneNumber);
        args.putString(ARG_PASSWORD, password);
        args.putString(ARG_SOCIAL_ID_TOKEN, idToken);
        args.putString(ARG_SOCIAL_SERVICE, service);
        args.putBoolean(ARG_SOCIAL_LOGIN, isSocialLogin);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPhoneNumber = getArguments().getString(ARG_PHONE_NUMBER);
        mPassword = getArguments().getString(ARG_PASSWORD);
        mIdToken = getArguments().getString(ARG_SOCIAL_ID_TOKEN);
        mService = getArguments().getString(ARG_SOCIAL_SERVICE);
        mIsSocialLogin = getArguments().getBoolean(ARG_SOCIAL_LOGIN);

        if (savedInstanceState == null) {
            // cleanup the service state on first appearance
            LoginPncomService.clearLoginServiceState();
        } else {
            mRequestedPassword = savedInstanceState.getString(KEY_REQUESTED_PASSWORD);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // connect to the Service. We'll receive updates via EventBus.
        mServiceEventConnection = new AutoForeground.ServiceEventConnection(getContext(),
                LoginPncomService.class, this);

        // install the change listener as late as possible so the UI can be setup (updated from the Service state)
        //  before triggering the state cleanup happening in the change listener.
        mPasswordInput.addTextChangedListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();

        // disconnect from the Service
        mServiceEventConnection.disconnect(getContext(), this);
    }

    @Override
    protected boolean listenForLogin() {
        return false;
    }

    @Override
    protected @LayoutRes
    int getContentLayout() {
        return R.layout.phone_password_screen;
    }

    @Override
    protected @LayoutRes
    int getProgressBarText() {
        return R.string.logging_in;
    }

    @Override
    protected void setupLabel(@NonNull TextView label) {
        label.setText(mIsSocialLogin ? R.string.enter_pn_password_google : R.string.enter_pn_password);
    }

    @Override
    protected void setupContent(ViewGroup rootView) {
        // important for accessibility - talkback
        getActivity().setTitle(R.string.pn_login_title);
        ((TextView) rootView.findViewById(R.id.phone)).setText(mPhoneNumber);

        mPasswordInput = rootView.findViewById(R.id.password_row);
        mPasswordInput.setOnEditorCommitListener(this);

        mNextButton = rootView.findViewById(R.id.primary_button);
    }

    @Override
    protected void setupBottomButtons(Button primaryButton) {
        primaryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                next();
            }
        });
    }

    @Override
    protected EditText getEditTextToFocusOnStart() {
        return mPasswordInput.getEditText();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {

            if (!TextUtils.isEmpty(mPassword)) {
                mPasswordInput.setText(mPassword);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(KEY_REQUESTED_PASSWORD, mRequestedPassword);
    }

    protected void next() {
        if (!NetworkUtils.checkConnection(getActivity())) {
            return;
        }

        startProgress(false);

        mRequestedPassword = mPasswordInput.getEditText().getText().toString();

        LoginPncomService.loginWithPhoneAndPassword(getContext(), mPhoneNumber, mRequestedPassword, mIdToken, mService,
                mIsSocialLogin);
    }

    @Override
    public void onEditorCommit() {
        mPasswordInput.setError(null);
        next();
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        mPasswordInput.setError(null);

        LoginPncomService.clearLoginServiceState();
    }

    private void showPasswordError() {
        mPasswordInput.setError(getString(R.string.password_incorrect));
    }

    private void showError(String error) {
        mPasswordInput.setError(error);
    }

    @Override
    protected void onLoginFinished() {
        if (mIsSocialLogin) {
            mLoginListener.logInXmppAccount(mPhoneNumber, mRequestedPassword, mNextButton, false);
        } else {
            mLoginListener.logInXmppAccount(mPhoneNumber, mRequestedPassword, mNextButton, false);
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onLoginStateUpdated(LoginState loginState) {
        AppLog.i(AppLog.T.NUX, "Received state: " + loginState.getStepName());

        switch (loginState.getStep()) {
            case IDLE:
                // nothing special to do, we'll start the service on next()
                break;
            case AUTHENTICATING:
            case FETCHING_ACCOUNT:
            case FETCHING_SETTINGS:
            case FETCHING_BUYERS:
                if (!isInProgress()) {
                    startProgress();
                }
                break;
            case FAILURE_PHONE_WRONG_PASSWORD:
                onLoginFinished(false);
                showPasswordError();
                break;
            case FAILURE_FETCHING_ACCOUNT:
                onLoginFinished(false);
                showError(getString(R.string.error_fetch_my_profile));
                break;
            case FAILURE_CANNOT_ADD_DUPLICATE_BUYER:
                onLoginFinished(false);
                showError(getString(R.string.cannot_add_duplicate_buyer));
                break;
            case FAILURE:
                onLoginFinished(false);
                showError(getString(R.string.error_generic));
                break;
            case SUCCESS:
                onLoginFinished(true);
                break;
        }
    }
}

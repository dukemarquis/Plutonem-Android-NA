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

import com.plutonem.android.login.SignupPnService.SignupState;
import com.plutonem.android.login.widgets.PLoginInputRow;
import com.plutonem.android.login.widgets.PLoginInputRow.OnEditorCommitListener;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.AutoForeground;
import org.wordpress.android.util.NetworkUtils;

import dagger.android.support.AndroidSupportInjection;

public class SignupPhonePasswordFragment extends LoginBaseFormFragment<LoginListener> implements TextWatcher,
        OnEditorCommitListener {
    private static final String KEY_REQUESTED_PASSWORD = "KEY_REQUESTED_PASSWORD";

    private static final String ARG_PHONE_NUMBER = "ARG_PHONE_NUMBER";
    private static final String ARG_PASSWORD = "ARG_PASSWORD";
    private static final String ARG_SOCIAL_ID_TOKEN = "ARG_SOCIAL_ID_TOKEN";
    private static final String ARG_SOCIAL_LOGIN = "ARG_SOCIAL_LOGIN";
    private static final String ARG_SOCIAL_SERVICE = "ARG_SOCIAL_SERVICE";

    public static final String TAG = "signup_phone_password_fragment_tag";

    private PLoginInputRow mPasswordInput;

    private String mRequestedPassword;

    private String mPhoneNumber;
    private String mIdToken;
    private String mPassword;
    private String mService;
    private boolean mIsSocialLogin;

    private AutoForeground.ServiceEventConnection mServiceEventConnection;

    public static SignupPhonePasswordFragment newInstance(String emailAddress, String password,
                                                          String idToken, String service,
                                                          boolean isSocialLogin) {
        SignupPhonePasswordFragment fragment = new SignupPhonePasswordFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PHONE_NUMBER, emailAddress);
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
            SignupPnService.clearSignupServiceState();
        } else {
            mRequestedPassword = savedInstanceState.getString(KEY_REQUESTED_PASSWORD);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // connect to the Service. We'll receive updates via EventBus.
        mServiceEventConnection = new AutoForeground.ServiceEventConnection(getContext(),
                SignupPnService.class, this);

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
        return R.string.Signing_up;
    }

    @Override
    protected void setupLabel(@NonNull TextView label) {
        label.setText(mIsSocialLogin ? R.string.enter_pn_password_google : R.string.enter_pn_password);
    }

    @Override
    protected void setupContent(ViewGroup rootView) {
        // important for accessibility - talkback
        getActivity().setTitle(R.string.pn_signup_title);
        ((TextView) rootView.findViewById(R.id.phone)).setText(mPhoneNumber);

        mPasswordInput = rootView.findViewById(R.id.password_row);
        mPasswordInput.setOnEditorCommitListener(this);
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

        SignupPnService.signupWithPhoneAndPassword(getContext(), mPhoneNumber, mRequestedPassword, mIdToken, mService,
                mIsSocialLogin);
//        mOldSitesIDs = SiteUtils.getCurrentSiteIds(mSiteStore, false);
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

        SignupPnService.clearSignupServiceState();
    }

    private void showRegistrationError() {
        mPasswordInput.setError(getString(R.string.registration_fail));
    }

    private void showError(String error) {
        mPasswordInput.setError(error);
    }

    @Override
    protected void onLoginFinished() {
        if (mIsSocialLogin) {
            mLoginListener.loggedInViaSocialAccount();
        } else {
            mLoginListener.loggedInViaPassword();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    public void onSignupStateUpdated(SignupState signupState) {
        AppLog.i(T.NUX, "Received state: " + signupState.getStepName());

        switch (signupState.getStep()) {
            case IDLE:
                // nothing special to do, we'll start the service on next()
                break;
            case REGISTERING:
            case FETCHING_XMPP:
            case FETCHING_ACCOUNT:
            case FETCHING_SETTINGS:
            case FETCHING_BUYERS:
                if (!isInProgress()) {
                    startProgress();
                }
                break;
            case FAILURE_REGISTERING:
                onLoginFinished(false);
                showRegistrationError();
                break;
            case FAILUER_FETCHING_XMPP:
                onLoginFinished(false);
                showError(getString(R.string.error_fetch_xmpp_account));
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

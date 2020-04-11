package com.plutonem.android.login;

import android.app.Notification;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.action.AccountAction;
import com.plutonem.android.fluxc.generated.AccountActionBuilder;
import com.plutonem.android.fluxc.generated.BuyerActionBuilder;
import com.plutonem.android.fluxc.generated.RegistrationActionBuilder;
import com.plutonem.android.fluxc.store.AccountStore.OnAccountChanged;
import com.plutonem.android.fluxc.store.AccountStore.OnRegistrationChanged;
import com.plutonem.android.fluxc.store.AccountStore.RegisterPayload;
import com.plutonem.android.fluxc.store.AccountStore.RegistrationErrorType;
import com.plutonem.android.fluxc.store.BuyerStore.BuyerErrorType;
import com.plutonem.android.fluxc.store.BuyerStore.OnBuyerChanged;
import com.plutonem.android.login.SignupPnService.SignupState;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.AutoForeground;
import org.wordpress.android.util.ToastUtils;

import java.net.URL;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.AndroidInjection;

public class SignupPnService extends AutoForeground<SignupState> {
    private static final String ARG_PHONE = "ARG_PHONE";
    private static final String ARG_PASSWORD = "ARG_PASSWORD";
    private static final String ARG_SOCIAL_ID_TOKEN = "ARG_SOCIAL_ID_TOKEN";
    private static final String ARG_SOCIAL_LOGIN = "ARG_SOCIAL_LOGIN";
    private static final String ARG_SOCIAL_SERVICE = "ARG_SOCIAL_SERVICE";

//    private static final String XMPP_SERVER_DOMAIN = "3.15.14.1";
//
//    private boolean mInitMode = true;
//    private Boolean mForceRegister = true;
//    private boolean mUsernameMode = Config.DOMAIN_LOCK != null;
//    private Account mAccount;

    public enum SignupStep {
        IDLE,
        REGISTERING(20),
        FETCHING_XMPP(40),
        FETCHING_ACCOUNT(60),
        FETCHING_SETTINGS(80),
        FETCHING_BUYERS(100),
        SUCCESS,
        FAILURE_REGISTERING,
        FAILUER_FETCHING_XMPP,
        FAILURE_FETCHING_ACCOUNT,
        FAILURE_CANNOT_ADD_DUPLICATE_BUYER,
        FAILURE;

        public final int progressPercent;

        SignupStep() {
            this.progressPercent = 0;
        }

        SignupStep(int progressPercent) {
            this.progressPercent = progressPercent;
        }
    }

    public static class SignupState implements AutoForeground.ServiceState {
        private final SignupStep mStep;

        SignupState(@NonNull SignupStep step) {
            this.mStep = step;
        }

        SignupStep getStep() {
            return mStep;
        }

        @Override
        public boolean isIdle() {
            return mStep == SignupStep.IDLE;
        }

        @Override
        public boolean isInProgress() {
            return mStep != SignupStep.IDLE && !isTerminal();
        }

        @Override
        public boolean isError() {
            return mStep == SignupStep.FAILURE
                    || mStep == SignupStep.FAILURE_REGISTERING
                    || mStep == SignupStep.FAILUER_FETCHING_XMPP
                    || mStep == SignupStep.FAILURE_FETCHING_ACCOUNT;
        }

        @Override
        public boolean isTerminal() {
            return mStep == SignupStep.SUCCESS || isError();
        }

        @Override
        public String getStepName() {
            return mStep.name();
        }
    }

    static class OnCredentialsOK {
        OnCredentialsOK() {
        }
    }

    @Inject
    Dispatcher mDispatcher;

    private String mIdToken;
    private String mService;
    private boolean mIsSocialLogin;

    public static void signupWithPhoneAndPassword(
            Context context,
            String phone,
            String password,
            String idToken, String service,
            boolean isSocialLogin) {
        Intent intent = new Intent(context, SignupPnService.class);
        intent.putExtra(ARG_PHONE, phone);
        intent.putExtra(ARG_PASSWORD, password);
        intent.putExtra(ARG_SOCIAL_ID_TOKEN, idToken);
        intent.putExtra(ARG_SOCIAL_SERVICE, service);
        intent.putExtra(ARG_SOCIAL_LOGIN, isSocialLogin);
        context.startService(intent);
    }

    public static void clearSignupServiceState() {
        clearServiceState(SignupState.class);
    }

    public SignupPnService() {
        super(new SignupState(SignupStep.IDLE));
    }

    @Override
    protected void onProgressStart() {
        mDispatcher.register(this);
    }

    @Override
    protected void onProgressEnd() {
        mDispatcher.unregister(this);
    }

    @Override
    public Notification getNotification(SignupState state) {
        return null;
    }

    @Override
    protected void trackStateUpdate(Map<String, ?> props) {
    }

    private void setState(SignupStep phase) {
        setState(new SignupState(phase));
    }

    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();

        AppLog.i(AppLog.T.MAIN, "SignupPnService > Created");

        // TODO: Recover any signup attempts that were interrupted by the service being stopped?
    }

    @Override
    public void onDestroy() {
        AppLog.i(AppLog.T.MAIN, "SignupPnService > Destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        setState(SignupStep.REGISTERING);

        String phone = intent.getStringExtra(ARG_PHONE);
        String password = intent.getStringExtra(ARG_PASSWORD);

        mIdToken = intent.getStringExtra(ARG_SOCIAL_ID_TOKEN);
        mService = intent.getStringExtra(ARG_SOCIAL_SERVICE);
        mIsSocialLogin = intent.getBooleanExtra(ARG_SOCIAL_LOGIN, false);

        RegisterPayload payload = new RegisterPayload(phone, password);
        mDispatcher.dispatch(RegistrationActionBuilder.newRegisterAction(payload));
        AppLog.i(T.NUX, "User tries to sign up pn. Phone: " + phone);

        return START_REDELIVER_INTENT;
    }

    private void handleRegError(RegistrationErrorType error, String errorMessage) {
        switch (error) {
            case FAILURE_REGISTERING:
                setState(SignupStep.FAILURE_REGISTERING);
                break;
            case INVALID_REQUEST:
                // TODO: FluxC: could be specific?
            default:
                setState(SignupStep.FAILURE);
                AppLog.e(T.NUX, "Server response: " + errorMessage);

                ToastUtils.showToast(this, errorMessage == null ? getString(R.string.error_generic) : errorMessage);
                break;
        }
    }

    private void fetchAccount() {
        setState(SignupStep.FETCHING_ACCOUNT);
        mDispatcher.dispatch(AccountActionBuilder.newFetchAccountAction());
    }

    // OnChanged events

//    @SuppressWarnings("unused")
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onAuthenticationChanged(OnAuthenticationChanged event) {
//        if (event.isError()) {
//            AppLog.e(T.API, "onAuthenticationChanged has error: " + event.error.type + " - " + event.error.message);
//            handleAuthError(event.error.type, event.error.message);
//            return;
//        }
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRegistrationChanged(OnRegistrationChanged event) {
        if (event.isError()) {
            AppLog.e(T.API, "onRegistrationChanged has error: " + event.error.type + " - " + event.error.message);
            handleRegError(event.error.type, event.error.message);
            return;
        }

        AppLog.i(T.NUX, "onRegistrationChanged: " + event.toString());

        if (mIsSocialLogin) {

        } else {
            fetchAccount();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAccountChanged(OnAccountChanged event) {
        if (event.isError()) {
            AppLog.e(T.API, "onAccountChanged has error: " + event.error.type + " - " + event.error.message);
            setState(SignupStep.FAILURE_FETCHING_ACCOUNT);
            return;
        }

        if (event.causeOfChange == AccountAction.FETCH_ACCOUNT) {
            setState(SignupStep.FETCHING_SETTINGS);
            // The user's account info has been fetched and stored - next, fetch the user's settings
            mDispatcher.dispatch(AccountActionBuilder.newFetchSettingsAction());
        } else if (event.causeOfChange == AccountAction.FETCH_SETTINGS) {
            setState(SignupStep.FETCHING_BUYERS);
            // The user's account settings have also been fetched and stored - now we can fetch the user's buyers
            mDispatcher.dispatch(BuyerActionBuilder.newFetchBuyersAction());
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBuyerChanged(OnBuyerChanged event) {
        if (event.isError()) {
            AppLog.e(T.API, "onBuyerChanged has error: " + event.error.type + " - " + event.error.toString());
            if (event.error.type != BuyerErrorType.DUPLICATE_BUYER) {
                setState(SignupStep.FAILURE);
                return;
            }

            if (event.rowsAffected == 0) {
                // If there is a duplicate buyer and not any buyer has been added, show an error and
                // stop the sign in process
                setState(SignupStep.FAILURE_CANNOT_ADD_DUPLICATE_BUYER);
                return;
            } else {
                // If there is a duplicate buyer, notify the user something could be wrong,
                // but continue the sign in process
                ToastUtils.showToast(this, R.string.duplicate_buyer_detected);
            }
        }

        setState(SignupStep.SUCCESS);
    }
}

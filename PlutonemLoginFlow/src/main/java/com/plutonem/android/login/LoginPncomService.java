package com.plutonem.android.login;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.action.AccountAction;
import com.plutonem.android.fluxc.generated.AccountActionBuilder;
import com.plutonem.android.fluxc.generated.AuthenticationActionBuilder;
import com.plutonem.android.fluxc.generated.BuyerActionBuilder;
import com.plutonem.android.fluxc.store.AccountStore.AuthenticatePayload;
import com.plutonem.android.fluxc.store.AccountStore.OnAccountChanged;
import com.plutonem.android.fluxc.store.AccountStore.AuthenticationErrorType;
import com.plutonem.android.fluxc.store.AccountStore.OnAuthenticationChanged;
import com.plutonem.android.fluxc.store.BuyerStore;
import com.plutonem.android.login.LoginPncomService.LoginState;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.AppLog.T;
import org.wordpress.android.util.AutoForeground;
import org.wordpress.android.util.ToastUtils;

import java.util.Map;

import javax.inject.Inject;

import dagger.android.AndroidInjection;


public class LoginPncomService extends AutoForeground<LoginState> {
    private static final String ARG_PHONE = "ARG_PHONE";
    private static final String ARG_PASSWORD = "ARG_PASSWORD";
    private static final String ARG_SOCIAL_ID_TOKEN = "ARG_SOCIAL_ID_TOKEN";
    private static final String ARG_SOCIAL_LOGIN = "ARG_SOCIAL_LOGIN";
    private static final String ARG_SOCIAL_SERVICE = "ARG_SOCIAL_SERVICE";

    public enum LoginStep {
        IDLE,
        AUTHENTICATING(25),
        FETCHING_ACCOUNT(50),
        FETCHING_SETTINGS(75),
        FETCHING_BUYERS(100),
        SUCCESS,
        FAILURE_PHONE_WRONG_PASSWORD,
        FAILURE_FETCHING_ACCOUNT,
        FAILURE_CANNOT_ADD_DUPLICATE_BUYER,
        FAILURE;

        public final int progressPercent;

        LoginStep() {
            this.progressPercent = 0;
        }

        LoginStep(int progressPercent) {
            this.progressPercent = progressPercent;
        }
    }

    public static class LoginState implements AutoForeground.ServiceState {
        private final LoginStep mStep;

        LoginState(@NonNull LoginStep step) {
            this.mStep = step;
        }

        LoginStep getStep() {
            return mStep;
        }

        @Override
        public boolean isIdle() {
            return mStep == LoginStep.IDLE;
        }

        @Override
        public boolean isInProgress() {
            return mStep != LoginStep.IDLE && !isTerminal();
        }

        @Override
        public boolean isError() {
            return mStep == LoginStep.FAILURE
                    || mStep == LoginStep.FAILURE_PHONE_WRONG_PASSWORD
                    || mStep == LoginStep.FAILURE_FETCHING_ACCOUNT;
        }

        @Override
        public boolean isTerminal() {
            return mStep == LoginStep.SUCCESS || isError();
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

    private boolean mIsSocialLogin;

    public static void loginWithPhoneAndPassword(
            Context context,
            String phone,
            String password,
            String idToken, String service,
            boolean isSocialLogin) {
        Intent intent = new Intent(context, LoginPncomService.class);
        intent.putExtra(ARG_PHONE, phone);
        intent.putExtra(ARG_PASSWORD, password);
        intent.putExtra(ARG_SOCIAL_ID_TOKEN, idToken);
        intent.putExtra(ARG_SOCIAL_SERVICE, service);
        intent.putExtra(ARG_SOCIAL_LOGIN, isSocialLogin);
        context.startService(intent);
    }

    public static void clearLoginServiceState() {
        clearServiceState(LoginState.class);
    }

    public LoginPncomService() {
        super(new LoginState(LoginStep.IDLE));
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
    public Notification getNotification(LoginPncomService.LoginState state) {
        return null;
    }

    @Override
    protected void trackStateUpdate(Map<String, ?> props) {

    }

    private void setState(LoginStep phase) {
        setState(new LoginState(phase));
    }

    @Override
    public void onCreate() {
        AndroidInjection.inject(this);
        super.onCreate();

        AppLog.i(T.MAIN, "LoginPncomService > Created");

        // TODO: Recover any login attempts that were interrupted by the service being stopped?
    }

    @Override
    public void onDestroy() {
        AppLog.i(T.MAIN, "LoginPncomService > Destroyed");
        super.onDestroy();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        setState(LoginStep.AUTHENTICATING);

        String phone = intent.getStringExtra(ARG_PHONE);
        String password = intent.getStringExtra(ARG_PASSWORD);

        mIsSocialLogin = intent.getBooleanExtra(ARG_SOCIAL_LOGIN, false);

        AuthenticatePayload payload = new AuthenticatePayload(phone, password);
        mDispatcher.dispatch(AuthenticationActionBuilder.newAuthenticateAction(payload));
        AppLog.i(T.NUX, "User tries to log in pncom. Phone: " + phone);

        return START_REDELIVER_INTENT;
    }

    private void handleAuthError(AuthenticationErrorType error, String errorMessage) {
        switch (error) {
            case INCORRECT_USERNAME_OR_PASSWORD:
                setState(LoginStep.FAILURE_PHONE_WRONG_PASSWORD);
                break;
            case INVALID_REQUEST:
                // TODO: FluxC: could be specific?
            default:
                setState(LoginStep.FAILURE);
                AppLog.e(T.NUX, "Server response: " + errorMessage);

                ToastUtils.showToast(this, errorMessage == null ? getString(R.string.error_generic) : errorMessage);
                break;
        }
    }

    private void fetchAccount() {
        setState(LoginStep.FETCHING_ACCOUNT);
        mDispatcher.dispatch(AccountActionBuilder.newFetchAccountAction());
    }

    // OnChanged events

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthenticationChanged(OnAuthenticationChanged event) {
        if (event.isError()) {
            AppLog.e(T.API, "onAuthenticationChanged has error: " + event.error.type + " - " + event.error.message);
            handleAuthError(event.error.type, event.error.message);
            return;
        }

        AppLog.i(T.NUX, "onAuthenticationChanged: " + event.toString());

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
            setState(LoginStep.FAILURE_FETCHING_ACCOUNT);
            return;
        }

        if (event.causeOfChange == AccountAction.FETCH_ACCOUNT) {
            setState(LoginStep.FETCHING_SETTINGS);
            // The user's account info has been fetched and stored - next, fetch the user's settings
            mDispatcher.dispatch(AccountActionBuilder.newFetchSettingsAction());
        } else if (event.causeOfChange == AccountAction.FETCH_SETTINGS) {
            setState(LoginStep.FETCHING_BUYERS);
            // The user's account settings have also been fetched and stored - now we can fetch the user's buyers
            mDispatcher.dispatch(BuyerActionBuilder.newFetchBuyersAction());
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onBuyerChanged(BuyerStore.OnBuyerChanged event) {
        if (event.isError()) {
            AppLog.e(T.API, "onBuyerChanged has error: " + event.error.type + " - " + event.error.toString());
            if (event.error.type != BuyerStore.BuyerErrorType.DUPLICATE_BUYER) {
                setState(LoginStep.FAILURE);
                return;
            }

            if (event.rowsAffected == 0) {
                // If there is a duplicate buyer and not any buyer has been added, show an error and
                // stop the sign in process
                setState(LoginStep.FAILURE_CANNOT_ADD_DUPLICATE_BUYER);
                return;
            } else {
                // If there is a duplicate buyer, notify the user something could be wrong,
                // but continue the sign in process
                ToastUtils.showToast(this, R.string.duplicate_buyer_detected);
            }
        }

        setState(LoginStep.SUCCESS);
    }
}

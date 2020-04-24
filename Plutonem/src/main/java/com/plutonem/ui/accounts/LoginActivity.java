package com.plutonem.ui.accounts;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.button.MaterialButton;
import com.plutonem.Config;
import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.login.LoginListener;
import com.plutonem.android.login.LoginMode;
import com.plutonem.android.login.LoginPhoneFragment;
import com.plutonem.android.login.LoginPhonePasswordFragment;
import com.plutonem.android.login.SignupPhoneFragment;
import com.plutonem.android.login.SignupPhonePasswordFragment;
import com.plutonem.ui.ActivityLauncher;
import com.plutonem.xmpp.entities.Account;
import com.plutonem.xmpp.entities.Avatar;
import com.plutonem.xmpp.services.XmppConnectionService.OnAccountUpdate;
import com.plutonem.xmpp.ui.UiCallback;
import com.plutonem.xmpp.ui.XmppActivity;
import com.plutonem.xmpp.ui.util.MenuDoubleTabUtil;
import com.plutonem.xmpp.ui.util.SoftKeyboardUtils;
import com.plutonem.xmpp.xmpp.XmppConnection;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;
import rocks.xmpp.addr.Jid;

public class LoginActivity extends XmppActivity implements LoginListener, HasSupportFragmentInjector,
        OnAccountUpdate {
    public static final String EXTRA_FORCE_REGISTER = "force_register";
    public static final String XMPP_NA_SERVER_DOMAIN = "3.15.14.1";

    private LoginMode mLoginMode;

//    private Jid jidToEdit;
    private boolean mInitMode = false;
    private Boolean mForceRegister = null;
    private Account mAccount;
    private MaterialButton mNextButton;
    private final UiCallback<Avatar> mAvatarFetchCallback = new UiCallback<Avatar>() {
        @Override
        public void success(Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void error(int errorCode, Avatar avatar) {
            finishInitialSetup(avatar);
        }

        @Override
        public void userInputRequired(PendingIntent pi, Avatar avatar) {
            finishInitialSetup(avatar);
        }
    };
    private boolean mFetchingAvatar = false;
    private String mSavedInstanceAccount;
    private boolean mSavedInstanceInit = false;

    @Inject DispatchingAndroidInjector<Fragment> mFragmentInjector;

    @Override
    public boolean onNavigateUp() {
        deleteAccountAndReturnIfNecessary();
        return super.onNavigateUp();
    }

    @Override
    public void onBackPressed() {
        deleteAccountAndReturnIfNecessary();
        super.onBackPressed();
    }

    private void deleteAccountAndReturnIfNecessary() {
        if (mInitMode && mAccount != null && !mAccount.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY)) {
            xmppConnectionService.deleteAccount(mAccount);
        }

        // Skip the logic part about MAGIC CREATE as pLutonem don't have one.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Plutonem) getApplication()).component().inject(this);
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            this.mSavedInstanceAccount = savedInstanceState.getString("account");
            this.mSavedInstanceInit = savedInstanceState.getBoolean("initMode", false);
        }

        setContentView(R.layout.login_activity);

        if (savedInstanceState == null) {
            switch (getLoginMode()) {
                case FULL:
                    showFragment(new LoginPhoneFragment(), LoginPhoneFragment.TAG);
                    break;
                case BUY_INTENT:
                case CHAT_INTENT:
                    checkNothingAndStartLogin();
                    break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        final Intent intent = getIntent();
//        if (intent != null) {

//            try {
//                this.jidToEdit = Jid.of(intent.getStringExtra("jid"));
//            } catch (final IllegalArgumentException | NullPointerException ignored) {
//                this.jidToEdit = null;
//            }

//            boolean init = intent.getBooleanExtra("init", false);
//            Log.d(Config.LOGTAG, "extras " + intent.getExtras());
//            this.mForceRegister = intent.hasExtra(EXTRA_FORCE_REGISTER) ? intent.getBooleanExtra(EXTRA_FORCE_REGISTER, false) : null;
//            Log.d(Config.LOGTAG, "force register=" + mForceRegister);
//            this.mInitMode = init || this.jidToEdit == null;
//        }

        this.mForceRegister = false;
        this.mInitMode = true;
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        if (mAccount != null) {
            savedInstanceState.putString("account", mAccount.getJid().asBareJid().toString());
            savedInstanceState.putBoolean("initMode", mInitMode);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    private void showFragment(Fragment fragment, String tag) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment, tag);
        fragmentTransaction.commit();
    }

    private void slideInFragment(Fragment fragment, boolean shouldAddToBackStack, String tag) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.setCustomAnimations(R.anim.activity_slide_in_from_right, R.anim.activity_slide_out_to_left,
                R.anim.activity_slide_in_from_left, R.anim.activity_slide_out_to_right);
        fragmentTransaction.replace(R.id.fragment_container, fragment, tag);
        if (shouldAddToBackStack) {
            fragmentTransaction.addToBackStack(null);
        }
        fragmentTransaction.commitAllowingStateLoss();
    }

    private boolean getLoginPrologueFragment() {
        return false;
    }

    private LoginPhoneFragment getLoginPhoneFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(LoginPhoneFragment.TAG);
        return fragment == null ? null : (LoginPhoneFragment) fragment;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public LoginMode getLoginMode() {
        if (mLoginMode != null) {
            // returned the cached value
            return mLoginMode;
        }

        // compute and cache the Login mode
        mLoginMode = LoginMode.fromIntent(getIntent());

        return mLoginMode;
    }

    private void loggedInAndFinish() {
        switch (getLoginMode()) {
            case FULL:
                ActivityLauncher.showMainActivity(this);
                setResult(Activity.RESULT_OK);
                finish();
                break;
            case BUY_INTENT:
            case CHAT_INTENT:
                setResult(Activity.RESULT_OK);
                finish();
                break;
        }
    }

    private void checkNothingAndStartLogin() {
        startLogin();
    }

    private void startLogin() {
        if (getLoginPhoneFragment() != null) {
            // email screen is already shown so, login has already started. Just bail.
            return;
        }

        if (!getLoginPrologueFragment()) {
            // prologue fragment is not shown so, the phone screen will be the initial screen on the fragment container
            showFragment(new LoginPhoneFragment(), LoginPhoneFragment.TAG);
        } else {
            // prologue fragment is shown so, slide in the phone screen (and add to history)
        }
    }

    @Override
    public void doStartSignup() {
        SignupPhoneFragment signupPhoneFragment = new SignupPhoneFragment();
        slideInFragment(signupPhoneFragment, true, SignupPhoneFragment.TAG);
    }

    // LoginListener implementation methods

    @Override
    public void gotPncomPhone(String phone, boolean verifyPhone) {
        LoginPhonePasswordFragment loginPhonePasswordFragment =
                LoginPhonePasswordFragment.newInstance(phone, null, null, null, false);
        slideInFragment(loginPhonePasswordFragment, true, LoginPhonePasswordFragment.TAG);
    }

    @Override
    public void loggedInViaSocialAccount() {
        loggedInAndFinish();
    }

    @Override
    public void showSignupPhonePassword(String phone) {
        SignupPhonePasswordFragment signupPhonePasswordFragment =
                SignupPhonePasswordFragment.newInstance(phone, null, null, null, false);
        slideInFragment(signupPhonePasswordFragment, true, SignupPhonePasswordFragment.TAG);
    }

    @Override
    public void loggedInViaPassword() {
        loggedInAndFinish();
    }

    @Override
    public void logInXmppAccount(String phone, String xmppPassword, MaterialButton nextButton, boolean forceRegister) {
        final String account = phone + '@' + XMPP_NA_SERVER_DOMAIN;
        mForceRegister = forceRegister;
        final boolean accountInfoEdited = accountInfoEdited(account, xmppPassword);

        if (mInitMode && mAccount != null) {
            mAccount.setOption(Account.OPTION_DISABLED, false);
        }

        if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED && !accountInfoEdited) {
            mAccount.setOption(Account.OPTION_DISABLED, false);
            if (!xmppConnectionService.updateAccount(mAccount)) {
                Toast.makeText(LoginActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final boolean registerNewAccount;
        if (mForceRegister != null) {
            registerNewAccount = mForceRegister;
        } else {
            registerNewAccount = !Config.DISALLOW_REGISTRATION_IN_UI;
        }

        if (inNeedOfSaslAccept(account, xmppPassword)) {
            mAccount.setKey(Account.PINNED_MECHANISM_KEY, String.valueOf(-1));
            if (!xmppConnectionService.updateAccount(mAccount)) {
                Toast.makeText(LoginActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final Jid jid;
        try {
            jid = Jid.of(account);
        } catch (final NullPointerException | IllegalArgumentException e) {
            Toast.makeText(LoginActivity.this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return;
        }
        String hostname = null;
        int numericPort = 5222;
        if (jid.getLocal() == null) {
            Toast.makeText(LoginActivity.this, R.string.invalid_jid, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mAccount != null) {
            if (mAccount.isOptionSet(Account.OPTION_MAGIC_CREATE)) {
                mAccount.setOption(Account.OPTION_MAGIC_CREATE, mAccount.getPassword().contains(xmppPassword));
            }
            mAccount.setJid(jid);
            mAccount.setPort(numericPort);
            mAccount.setHostname(hostname);
            mAccount.setPassword(xmppPassword);
            mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
            if (!xmppConnectionService.updateAccount(mAccount)) {
                Toast.makeText(LoginActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            if (xmppConnectionService.findAccountByJid(jid) != null) {
                Toast.makeText(LoginActivity.this, R.string.unable_to_update_account, Toast.LENGTH_SHORT).show();
                return;
            }
            mAccount = new Account(jid.asBareJid(), xmppPassword);
            mAccount.setPort(numericPort);
            mAccount.setHostname(hostname);
            mAccount.setOption(Account.OPTION_USETLS, true);
            mAccount.setOption(Account.OPTION_USECOMPRESSION, true);
            mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);
            xmppConnectionService.createAccount(mAccount);
        }

        mNextButton = nextButton;
        updateNextButton(mNextButton);
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return mFragmentInjector;
    }

    @Override
    public void onBackendConnected() {
//        boolean init = true;

        if (mSavedInstanceAccount != null) {
            try {
                this.mAccount = xmppConnectionService.findAccountByJid(Jid.of(mSavedInstanceAccount));
                this.mInitMode = mSavedInstanceInit;
//                init = false;
            } catch (IllegalArgumentException e) {
                this.mAccount = null;
            }

        }

//        else if (this.jidToEdit != null) {
//            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
//        }
    }

    public void refreshUiReal() {
        invalidateOptionsMenu();
        if (mAccount != null
                && mAccount.getStatus() != Account.State.ONLINE
                && mFetchingAvatar) {
            // skip this logic right now
        } else if (mInitMode && mAccount != null && mAccount.getStatus() == Account.State.ONLINE) {
            if (!mFetchingAvatar) {
                mFetchingAvatar = true;
                xmppConnectionService.checkForAvatar(mAccount, mAvatarFetchCallback);
            }
        }
        updateNextButton(mNextButton);
    }

    protected void updateNextButton(MaterialButton nextButton) {
        if (nextButton == null) {
            return;
        }

        if (mAccount != null
            && (mAccount.getStatus() == Account.State.CONNECTING || mAccount.getStatus() == Account.State.REGISTRATION_SUCCESSFUL || mFetchingAvatar)) {
            nextButton.setEnabled(false);
            nextButton.setText(R.string.account_status_connecting);
        } else {
            nextButton.setEnabled(true);
            nextButton.setText(R.string.next);
        }
    }

    protected boolean accountInfoEdited(String xmppAccount, String xmppPassword) {
        if (this.mAccount == null) {
            return false;
        }
        return jidEdited(xmppAccount) ||
                !this.mAccount.getPassword().equals(xmppPassword);
    }

    protected boolean jidEdited(String xmppAccount) {
        final String unmodified;

        unmodified = this.mAccount.getJid().asBareJid().toString();
        return !unmodified.equals(xmppAccount);
    }

    private boolean inNeedOfSaslAccept(String xmppAccount, String xmppPassword) {
        return mAccount != null && mAccount.getLastErrorStatus() == Account.State.DOWNGRADE_ATTACK && mAccount.getKeyAsInt(Account.PINNED_MECHANISM_KEY, -1) >= 0 && !accountInfoEdited(xmppAccount, xmppPassword);
    }

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(() -> {
            SoftKeyboardUtils.hideSoftKeyboard(LoginActivity.this);
            loggedInAndFinish();
        });
    }
}

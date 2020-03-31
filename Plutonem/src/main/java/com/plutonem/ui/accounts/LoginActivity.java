package com.plutonem.ui.accounts;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.plutonem.Plutonem;
import com.plutonem.R;
import com.plutonem.android.login.LoginListener;
import com.plutonem.android.login.LoginMode;
import com.plutonem.android.login.LoginPhoneFragment;
import com.plutonem.android.login.LoginPhonePasswordFragment;
import com.plutonem.android.login.SignupPhoneFragment;
import com.plutonem.android.login.SignupPhonePasswordFragment;
import com.plutonem.ui.ActivityLauncher;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.support.HasSupportFragmentInjector;

public class LoginActivity extends AppCompatActivity implements LoginListener, HasSupportFragmentInjector {
    private LoginMode mLoginMode;

    @Inject DispatchingAndroidInjector<Fragment> mFragmentInjector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ((Plutonem) getApplication()).component().inject(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        if (savedInstanceState == null) {
            switch (getLoginMode()) {
                case FULL:
                    showFragment(new LoginPhoneFragment(), LoginPhoneFragment.TAG);
                    break;
                case BUY_INTENT:
                    checkNothingAndStartLogin();
                    break;
            }
        }
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
    public AndroidInjector<Fragment> supportFragmentInjector() {
        return mFragmentInjector;
    }
}

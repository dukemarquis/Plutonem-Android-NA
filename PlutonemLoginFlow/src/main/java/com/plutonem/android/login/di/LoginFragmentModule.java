package com.plutonem.android.login.di;

import com.plutonem.android.login.LoginPhoneFragment;
import com.plutonem.android.login.LoginPhonePasswordFragment;
import com.plutonem.android.login.SignupPhoneFragment;
import com.plutonem.android.login.SignupPhonePasswordFragment;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class LoginFragmentModule {
    @ContributesAndroidInjector
    abstract LoginPhoneFragment loginPhoneFragment();

    @ContributesAndroidInjector
    abstract LoginPhonePasswordFragment loginPhonePasswordFragment();

    @ContributesAndroidInjector
    abstract SignupPhoneFragment signupPhoneFragment();

    @ContributesAndroidInjector
    abstract SignupPhonePasswordFragment signupPhonePasswordFragment();
}

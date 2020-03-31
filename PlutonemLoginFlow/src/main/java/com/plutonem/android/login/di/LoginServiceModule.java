package com.plutonem.android.login.di;

import com.plutonem.android.login.LoginPncomService;
import com.plutonem.android.login.SignupPnService;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class LoginServiceModule {
    @ContributesAndroidInjector
    abstract LoginPncomService loginPncomService();

    @ContributesAndroidInjector
    abstract SignupPnService signupPnService();
}


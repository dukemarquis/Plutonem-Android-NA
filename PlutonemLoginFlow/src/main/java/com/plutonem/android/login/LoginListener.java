package com.plutonem.android.login;

import java.util.ArrayList;

public interface LoginListener {
    LoginMode getLoginMode();

    // Login Phone input callbacks
    void gotPncomPhone(String phone, boolean verifyPhone);
    void loggedInViaSocialAccount();

    // Login phone password callbacks
    void loggedInViaPassword();

    // Signup
    void doStartSignup();
    void showSignupPhonePassword(String phone);
}

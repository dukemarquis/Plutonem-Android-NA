package com.plutonem.android.login;

import com.google.android.material.button.MaterialButton;

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

    // Xmpp specification
    void logInXmppAccount(String phone, String password, MaterialButton materialButton, boolean forceRegister);
}

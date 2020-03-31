package com.plutonem.android.login;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.plutonem.android.fluxc.Dispatcher;
import com.plutonem.android.fluxc.store.AccountStore;
import com.plutonem.android.fluxc.store.BuyerStore;

import org.wordpress.android.util.EditTextUtils;

import javax.inject.Inject;

public abstract class LoginBaseFormFragment<LoginListenerType> extends Fragment implements TextWatcher {
    private static final String KEY_IN_PROGRESS = "KEY_IN_PROGRESS";
    private static final String KEY_LOGIN_FINISHED = "KEY_LOGIN_FINISHED";

    private Button mPrimaryButton;
    private ProgressDialog mProgressDialog;

    protected LoginListenerType mLoginListener;

    private boolean mInProgress;
    private boolean mLoginFinished;

    @Inject protected Dispatcher mDispatcher;
    @Inject protected BuyerStore mBuyerStore;
    @Inject protected AccountStore mAccountStore;


    protected abstract @LayoutRes int getContentLayout();
    protected abstract void setupLabel(@NonNull TextView label);
    protected abstract void setupContent(ViewGroup rootView);
    protected abstract void setupBottomButtons(Button primaryButton);
    protected abstract @StringRes int getProgressBarText();

    protected boolean listenForLogin() {
        return true;
    }

    protected EditText getEditTextToFocusOnStart() {
        return null;
    }

    protected boolean isInProgress() {
        return mInProgress;
    }

    protected Button getPrimaryButton() {
        return mPrimaryButton;
    }

    protected ViewGroup createMainView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.login_form_screen, container, false);
        ViewStub formContainer = ((ViewStub) rootView.findViewById(R.id.login_form_content_stub));
        formContainer.setLayoutResource(getContentLayout());
        formContainer.inflate();
        return rootView;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup rootView = createMainView(inflater, container, savedInstanceState);

        setupLabel((TextView) rootView.findViewById(R.id.label));

        setupContent(rootView);

        mPrimaryButton = (Button) rootView.findViewById(R.id.primary_button);
        setupBottomButtons(mPrimaryButton);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            EditTextUtils.showSoftInput(getEditTextToFocusOnStart());
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mInProgress = savedInstanceState.getBoolean(KEY_IN_PROGRESS);
            mLoginFinished = savedInstanceState.getBoolean(KEY_LOGIN_FINISHED);

            if (mInProgress) {
                startProgress();
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onAttach(Context context) {
        super.onAttach(context);

        // this will throw if parent activity doesn't implement the login listener interface
        mLoginListener = (LoginListenerType) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mLoginListener = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (listenForLogin()) {
            mDispatcher.register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (listenForLogin()) {
            mDispatcher.unregister(this);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IN_PROGRESS, mInProgress);
        outState.putBoolean(KEY_LOGIN_FINISHED, mLoginFinished);
    }

    protected void startProgress() {
        startProgress(true);
    }

    protected void startProgress(boolean cancellable) {
        mPrimaryButton.setEnabled(false);

        mProgressDialog =
                ProgressDialog.show(getActivity(), "", getActivity().getString(getProgressBarText()), true, cancellable,
                        new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                if (isInProgress()) {
                                    endProgress();
                                }
                            }
                        });
        mInProgress = true;
    }

    @CallSuper
    protected void endProgress() {
        mInProgress = false;

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }

        mPrimaryButton.setEnabled(true);
    }

    protected void onLoginFinished() {
    }

    protected void onLoginFinished(boolean success) {
        mLoginFinished = true;

        if (success && mLoginListener != null) {
            onLoginFinished();
        }

        endProgress();
    }
}

package com.plutonem.xmpp.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.plutonem.android.login.R;
import com.plutonem.xmpp.entities.MTMDecision;
import com.plutonem.xmpp.services.MemorizingTrustManager;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MemorizingActivity extends AppCompatActivity implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

    private final static Logger LOGGER = Logger.getLogger(MemorizingActivity.class.getName());

    int decisionId;

    AlertDialog dialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        LOGGER.log(Level.FINE, "onCreate");
        super.onCreate(savedInstanceState);

        // apparently we don't want the user to choose this part so leave it.
    }

    @Override
    public void onResume() {
        super.onResume();
        Intent i = getIntent();
        decisionId = i.getIntExtra(MemorizingTrustManager.DECISION_INTENT_ID, MTMDecision.DECISION_INVALID);
        int titleId = i.getIntExtra(MemorizingTrustManager.DECISION_TITLE_ID, R.string.mtm_accept_cert);
        String cert = i.getStringExtra(MemorizingTrustManager.DECISION_INTENT_CERT);
        LOGGER.log(Level.FINE, "onResume with " + i.getExtras() + " decId=" + decisionId + " data: " + i.getData());
        dialog = new AlertDialog.Builder(this).setTitle(titleId)
                .setMessage(cert)
                .setPositiveButton(R.string.always, this)
                .setNeutralButton(R.string.once, this)
                .setNegativeButton(R.string.cancel, this)
                .setOnCancelListener(this)
                .create();
        dialog.show();
    }

    @Override
    protected void onPause() {
        if (dialog.isShowing())
            dialog.dismiss();
        super.onPause();
    }

    void sendDecision(int decision) {
        LOGGER.log(Level.FINE, "Sending decision: " + decision);
        MemorizingTrustManager.interactResult(decisionId, decision);
        finish();
    }

    // react on AlertDialog button press
    public void onClick(DialogInterface dialog, int btnId) {
        int decision;
        dialog.dismiss();
        switch (btnId) {
            case DialogInterface.BUTTON_POSITIVE:
                decision = MTMDecision.DECISION_ALWAYS;
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                decision = MTMDecision.DECISION_ONCE;
                break;
            default:
                decision = MTMDecision.DECISION_ABORT;
        }
        sendDecision(decision);
    }

    public void onCancel(DialogInterface dialog) {
        sendDecision(MTMDecision.DECISION_ABORT);
    }
}

package com.plutonem.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.plutonem.ui.main.PMainActivity;

public class PLaunchActivity extends AppCompatActivity {
    /*
     * this the main (default) activity, which does nothing more than launch the
     * previously active activity on startup - note that it's defined in the
     * manifest to have no UI
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launchPMainActivity();
    }

    private void launchPMainActivity() {
        Intent intent = new Intent(this, PMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(getIntent().getAction());
        intent.setData(getIntent().getData());
        startActivity(intent);
        finish();
    }
}

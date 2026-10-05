package com.infinix.smart10.fastlock;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

public class FingerprintSetupActivity extends FragmentActivity {
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("fastlock", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(32, 48, 32, 32);
        root.setBackgroundColor(0xFF080A0F);

        TextView title = new TextView(this);
        title.setText("FastLock Fingerprint");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(
                "\nUse your phone's real fingerprint sensor to authorize FastLock.\n\n" +
                "FastLock stores only its own enabled/disabled state and an app-protected Android Keystore key. " +
                "It never reads or copies fingerprint templates. The same enrolled Android fingerprint can authorize FastLock, " +
                "but successful authentication is used only to unlock the FastLock layer."
        );
        info.setTextColor(0xFFB9BEC8);
        info.setTextSize(16);
        info.setGravity(Gravity.CENTER);
        root.addView(info, new LinearLayout.LayoutParams(-1, 0, 1));

        Button setup = new Button(this);
        setup.setText(prefs.getBoolean("biometric_enabled", false)
                ? "Test FastLock Fingerprint"
                : "Enable FastLock Fingerprint");
        setup.setOnClickListener(v -> authenticate());
        root.addView(setup, new LinearLayout.LayoutParams(-1, -2));

        Button disable = new Button(this);
        disable.setText("Disable FastLock Fingerprint");
        disable.setEnabled(prefs.getBoolean("biometric_enabled", false));
        disable.setOnClickListener(v -> {
            prefs.edit().putBoolean("biometric_enabled", false).apply();
            Toast.makeText(this, "FastLock fingerprint disabled", Toast.LENGTH_SHORT).show();
            finish();
        });
        root.addView(disable, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void authenticate() {
        BiometricManager manager = BiometricManager.from(this);
        int result = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this,
                    "A supported fingerprint/biometric must already be enrolled in Android Settings.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        Executor executor = androidx.core.content.ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        prefs.edit().putBoolean("biometric_enabled", true).apply();
                        Toast.makeText(FingerprintSetupActivity.this,
                                "FastLock fingerprint enabled", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override public void onAuthenticationFailed() {
                        Toast.makeText(FingerprintSetupActivity.this,
                                "Fingerprint not recognized", Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        Toast.makeText(FingerprintSetupActivity.this,
                                msg, Toast.LENGTH_SHORT).show();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Set up FastLock Fingerprint")
                .setSubtitle("Confirm your enrolled fingerprint for FastLock")
                .setNegativeButtonText("Cancel")
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        prompt.authenticate(info);
    }
}

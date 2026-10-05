package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;

public class AuthActivity extends Activity {
    private android.content.SharedPreferences prefs;
    private EditText pass;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(42, 38, 42, 38);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B0E14);
        bg.setCornerRadius(38);
        box.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("FastLockApp");
        title.setTextColor(Color.WHITE);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("\nFingerprint or your FastLockApp passcode\n\nYour phone's real lock remains unchanged.");
        sub.setTextColor(0xFFB9BEC8);
        sub.setGravity(Gravity.CENTER);

        Button fingerprint = new Button(this);
        fingerprint.setText("Use fingerprint");
        fingerprint.setOnClickListener(v -> authenticateBiometric());

        pass = new EditText(this);
        pass.setHint("FastLockApp passcode");
        pass.setInputType(2);
        pass.setTextColor(Color.WHITE);
        pass.setHintTextColor(0xFF777D88);
        pass.setGravity(Gravity.CENTER);

        Button unlock = new Button(this);
        unlock.setText("Unlock with passcode");
        unlock.setOnClickListener(v -> {
            if (checkPasscode(pass.getText().toString())) finish();
            else { pass.setError("Incorrect FastLockApp passcode"); pass.setText(""); }
        });

        box.addView(title); box.addView(sub);
        if (prefs.getBoolean("biometric_enabled", false)) box.addView(fingerprint);
        box.addView(pass, new LinearLayout.LayoutParams(-1, -2));
        box.addView(unlock);
        setContentView(box);
    }

    private boolean checkPasscode(String value) {
        try {
            String salt = prefs.getString("passcode_salt", "");
            String expected = prefs.getString("passcode_hash", "");
            if (salt.isEmpty() || expected.isEmpty()) return false;
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    value.toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), 120000, 256);
            byte[] out = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            String actual = android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP);
            return java.security.MessageDigest.isEqual(
                    actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { return false; }
    }

    private void authenticateBiometric() {
        int result = BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (result != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "No enrolled biometric is available", Toast.LENGTH_SHORT).show();
            return;
        }
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        runOnUiThread(() -> { Toast.makeText(AuthActivity.this,
                                "FastLockApp fingerprint verified", Toast.LENGTH_SHORT).show(); finish(); });
                    }
                    @Override public void onAuthenticationError(int code, CharSequence message) {
                        Toast.makeText(AuthActivity.this, "Fingerprint not verified", Toast.LENGTH_SHORT).show();
                    }
                });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("FastLockApp fingerprint")
                .setSubtitle("Verify to unlock FastLockApp only")
                .setNegativeButtonText("Use passcode")
                .setConfirmationRequired(false).build();
        prompt.authenticate(info);
    }
}

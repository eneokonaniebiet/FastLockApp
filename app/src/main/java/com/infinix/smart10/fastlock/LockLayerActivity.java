package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

public class LockLayerActivity extends FragmentActivity {
    private float downY;
    private ImageView fingerprint;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        try {
            Drawable wallpaper = android.app.WallpaperManager.getInstance(this).getDrawable();
            if (wallpaper != null) root.setBackground(wallpaper);
            else root.setBackgroundColor(0xFF080A0F);
        } catch (Exception ignored) {
            root.setBackgroundColor(0xFF080A0F);
        }

        View scrim = new View(this);
        scrim.setBackgroundColor(0x33000000);
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(28, 16, 28, 42);

        TextView title = new TextView(this);
        title.setText("FastLock");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        bottom.addView(title);

        TextView hint = new TextView(this);
        hint.setText("\nTouch fingerprint to unlock\nSwipe up for FastLock PIN");
        hint.setTextColor(0xEEFFFFFF);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        bottom.addView(hint);

        fingerprint = new ImageView(this);
        fingerprint.setImageResource(R.drawable.ic_fastlock);
        fingerprint.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fingerprint.setPadding(24, 24, 24, 24);
        fingerprint.setContentDescription("FastLock fingerprint authentication");
        bottom.addView(fingerprint, new LinearLayout.LayoutParams(118, 118));

        FrameLayout.LayoutParams bp =
                new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        root.addView(bottom, bp);

        root.setOnTouchListener(this::handleSwipe);
        fingerprint.setOnClickListener(v -> authenticateFingerprint());

        setContentView(root);
    }

    private boolean handleSwipe(View v, MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downY = e.getRawY();
            return true;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            if (e.getRawY() - downY < -90f) openPin();
            return true;
        }
        return true;
    }

    private void authenticateFingerprint() {
        if (!getSharedPreferences("fastlock", MODE_PRIVATE)
                .getBoolean("biometric_enabled", false)) {
            openPin();
            return;
        }

        BiometricManager manager = BiometricManager.from(this);
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "FastLock fingerprint is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result) {
                        completeUnlock();
                    }

                    @Override public void onAuthenticationError(int code, CharSequence msg) {
                        if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON) openPin();
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("FastLock")
                .setSubtitle("Unlock FastLock layer with your fingerprint")
                .setNegativeButtonText("Use FastLock PIN")
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();

        prompt.authenticate(info);
    }

    private void openPin() {
        Intent i = new Intent(this, AuthActivity.class);
        i.putExtra("pin_only", true);
        startActivityForResult(i, 78);
    }

    private void completeUnlock() {
        getSharedPreferences("fastlock", MODE_PRIVATE)
                .edit().putBoolean("fastlock_authenticated", true).apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == 78 && c == RESULT_OK) completeUnlock();
    }
}

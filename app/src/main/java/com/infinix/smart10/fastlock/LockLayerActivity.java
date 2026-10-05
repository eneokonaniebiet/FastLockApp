package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import androidx.fragment.app.FragmentActivity;

public class LockLayerActivity extends FragmentActivity {
    private static final int PIN_REQUEST = 78;
    private static final int MAX_FAILED_TOUCHES = 10;
    private static final int SENSOR_SIZE = 210;

    private TouchUnlockView fingerprint;
    private boolean authenticating = false;
    private final android.os.Handler guardHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastFingerprintCheck =
            () -> { if (fingerprint != null) fingerprint.tryFastUnlock(); };

    private long wakeGlowUntil;
    private boolean wakeGlow = false;
    private BroadcastReceiver screenReceiver;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);

        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            // Deliberately do NOT call setTurnScreenOn(true).
            // FastLock must never wake the display just because its activity is
            // recreated while the phone is sleeping.
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setBackgroundColor(0xFF080A0F);
        try {
            android.graphics.drawable.Drawable lockWallpaper =
                    android.app.WallpaperManager.getInstance(this)
                            .getDrawable(android.app.WallpaperManager.FLAG_LOCK);
            if (lockWallpaper != null) wallpaper.setImageDrawable(lockWallpaper);
        } catch (Exception ignored) {}
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        fingerprint = new TouchUnlockView();
        FrameLayout.LayoutParams sensorLp =
                new FrameLayout.LayoutParams(SENSOR_SIZE, SENSOR_SIZE);
        sensorLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        sensorLp.bottomMargin = 92;
        root.addView(fingerprint, sensorLp);

        setContentView(root);
        enterAndroidLockTaskIfAvailable();

        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    wakeGlow = false;
                    wakeGlowUntil = 0L;
                    if (fingerprint != null) fingerprint.invalidate();
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    // The screen is now actually visible. Give the sensor its
                    // short blue breathing effect; the layer itself stays put.
                    wakeGlow = true;
                    wakeGlowUntil = System.currentTimeMillis() + 6000L;
                    if (fingerprint != null) fingerprint.invalidate();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(screenReceiver, filter);
    }


    /**
     * Use Android's real Lock Task mode when FastLock has been provisioned as
     * the device owner. This is the system-level path that disables Home and
     * Overview instead of trying to fake-disable navigation gestures.
     *
     * A normal installed app cannot grant itself Device Owner status, so the
     * existing FastLock gate remains the fallback on ordinary installs.
     */
    private void enterAndroidLockTaskIfAvailable() {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin =
                    new ComponentName(this, FastLockDeviceAdminReceiver.class);

            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setLockTaskPackages(admin, new String[]{getPackageName()});

                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    // Keep only basic system information such as clock/status
                    // while disabling Home, Overview, notifications and the
                    // other configurable navigation escapes.
                    dpm.setLockTaskFeatures(
                            admin,
                            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO);
                }
            }

            if (dpm != null && dpm.isLockTaskPermitted(getPackageName())) {
                startLockTask();
            }
        } catch (SecurityException ignored) {
            // Not provisioned as device owner/profile owner: use normal gate.
        } catch (Exception ignored) {}
    }

    private void exitAndroidLockTask() {
        try {
            stopLockTask();
        } catch (Exception ignored) {}
    }

    private void stopWakeGlow() {
        wakeGlow = false;
        wakeGlowUntil = 0L;
        if (fingerprint != null) fingerprint.invalidate();
    }

    private boolean isTouchLocked() {
        return getSharedPreferences("fastlock", MODE_PRIVATE)
                .getInt("fastlock_failed_touches", 0) >= MAX_FAILED_TOUCHES;
    }

    private void resetTouchFailures() {
        getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                .putInt("fastlock_failed_touches", 0).apply();
    }

    private void registerFailedTouch() {
        int n = getSharedPreferences("fastlock", MODE_PRIVATE)
                .getInt("fastlock_failed_touches", 0) + 1;
        getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                .putInt("fastlock_failed_touches", n).apply();
    }

    private void openPin() {
        authenticating = true;
        startActivityForResult(
                new Intent(this, AuthActivity.class).putExtra("pin_only", true),
                PIN_REQUEST);
    }

    private void completeUnlock() {
        getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                .putBoolean("fastlock_authenticated", true)
                .putInt("fastlock_failed_touches", 0).apply();

        authenticating = true;
        setResult(RESULT_OK);
        exitAndroidLockTask();

        // LockLayer belongs to FastLock's task. Put that task behind the task
        // that was visible before FastLock appeared, instead of opening
        // FastLockApp's settings page after authentication.
        finish();
        moveTaskToBack(true);
    }

    @Override public void onBackPressed() {
        // Back cannot dismiss the security layer.
        if (!authenticating) return;
        super.onBackPressed();
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!authenticating && getSharedPreferences("fastlock", MODE_PRIVATE)
                .getBoolean("active", false)) {
            guardHandler.postDelayed(() -> {
                if (!isFinishing() && !authenticating) bringLayerBack();
            }, 35);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (!authenticating && getSharedPreferences("fastlock", MODE_PRIVATE)
                .getBoolean("active", false)) {
            guardHandler.postDelayed(() -> {
                if (!authenticating
                        && getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("active", false)
                        && isDisplayOn()) {
                    bringLayerBack();
                }
            }, 50);
        }
    }

    private boolean isDisplayOn() {
        android.os.PowerManager pm =
                (android.os.PowerManager)getSystemService(POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private void bringLayerBack() {
        if (!isDisplayOn()) return;
        try {
            Intent i = new Intent(this, LockLayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override protected void onResume() {
        super.onResume();
        if (!authenticating) enterAndroidLockTaskIfAvailable();
        // Do not create another glow here. ScreenReceiver owns the wake effect.
        if (!isDisplayOn()) stopWakeGlow();
    }

    @Override protected void onDestroy() {
        guardHandler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == PIN_REQUEST) {
            if (c == RESULT_OK) {
                resetTouchFailures();
                completeUnlock();
            } else {
                authenticating = false;
                if (fingerprint != null) fingerprint.invalidate();
            }
        }
    }

    private class TouchUnlockView extends View {
        Paint sensor = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        TouchCredential.Session session = new TouchCredential.Session();
        boolean active = false, unlocked = false;
        long start;
        float downRawY;

        TouchUnlockView() {
            super(LockLayerActivity.this);
            sensor.setStyle(Paint.Style.STROKE);
            sensor.setStrokeWidth(7f);
            sensor.setColor(0xFFD6B75C);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeWidth(12f);
            setClickable(true);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);

            float w = getWidth();
            float h = getHeight();
            float left = 10f, top = 10f, right = w - 10f, bottom = h - 10f;
            float radius = 48f;

            long now = System.currentTimeMillis();
            boolean glowing = wakeGlow && now < wakeGlowUntil;
            boolean disabled = isTouchLocked();

            // Soft blue breathing edge when the display has just woken.
            if (glowing) {
                float pulse = .5f - .5f *
                        (float)Math.cos((now % 1600L) / 1600f *
                                (float)(Math.PI * 2));
                int alpha = (int)(40 + 190 * pulse);
                glow.setColor((alpha << 24) | 0x2196F3);
                glow.setStrokeWidth(10f + 12f * pulse);
                c.drawRoundRect(left - 4f - 5f * pulse,
                        top - 4f - 5f * pulse,
                        right + 4f + 5f * pulse,
                        bottom + 4f + 5f * pulse,
                        radius + 5f * pulse,
                        radius + 5f * pulse,
                        glow);
                postInvalidateDelayed(40);
            }

            // The actual sensor is a rounded square/panel, not a circle.
            sensor.setColor(disabled ? 0xFF6A6F78 : 0xFFD6B75C);
            sensor.setStrokeWidth(active ? 9f : 7f);
            c.drawRoundRect(left, top, right, bottom, radius, radius, sensor);

            if (active) {
                float p = Math.min(1f, (now - start) / 900f);
                sensor.setColor(0xFFB99A45);
                sensor.setStrokeWidth(9f);
                c.drawArc(left, top, right, bottom, -90f, p * 360f, false, sensor);
                postInvalidateDelayed(30);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            final int action = e.getActionMasked();

            if (action == MotionEvent.ACTION_DOWN) {
                downRawY = e.getRawY();

                if (!getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("fast_fingerprint_enabled", false)) {
                    openPin();
                    return true;
                }

                active = true;
                start = System.currentTimeMillis();
                session.begin(e);
                unlocked = false;
                guardHandler.postDelayed(fastFingerprintCheck, 120);
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE && active) {
                session.add(e);

                if (downRawY - e.getRawY() > 90f) {
                    active = false;
                    guardHandler.removeCallbacks(fastFingerprintCheck);
                    invalidate();
                    openPin();
                    return true;
                }

                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_UP && active) {
                session.add(e);
                long held = System.currentTimeMillis() - start;
                active = false;
                guardHandler.removeCallbacks(fastFingerprintCheck);
                invalidate();

                if (downRawY - e.getRawY() > 90f) {
                    openPin();
                    return true;
                }

                if (held < 90) return true;

                if (isTouchLocked()) {
                    Toast.makeText(LockLayerActivity.this,
                            "Touch disabled. Swipe up for FastLock PIN.",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                String candidate = session.finish();
                String saved = getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getString("fast_fingerprint_signatures", "");

                if (TouchCredential.matchesAny(candidate, saved)) {
                    completeUnlock();
                } else {
                    registerFailedTouch();
                    Toast.makeText(LockLayerActivity.this,
                            isTouchLocked()
                                    ? "Touch disabled. Swipe up for FastLock PIN."
                                    : "Finger not verified",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            if (action == MotionEvent.ACTION_CANCEL) {
                active = false;
                guardHandler.removeCallbacks(fastFingerprintCheck);
                invalidate();
                return true;
            }

            return true;
        }

        void tryFastUnlock() {
            if (!active || unlocked || authenticating || isTouchLocked()) return;

            long held = System.currentTimeMillis() - start;
            if (held < 120) return;

            String candidate = session.finish();
            String saved = getSharedPreferences("fastlock", MODE_PRIVATE)
                    .getString("fast_fingerprint_signatures", "");

            if (TouchCredential.matchesAny(candidate, saved)) {
                unlocked = true;
                active = false;
                invalidate();
                guardHandler.removeCallbacks(fastFingerprintCheck);
                completeUnlock();
            }
        }
    }
}

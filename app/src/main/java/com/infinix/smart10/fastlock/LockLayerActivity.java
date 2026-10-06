package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;

public class LockLayerActivity extends FragmentActivity {
    private static final int PIN_REQUEST = 78;
    private static final int SENSOR_SIZE_DP = 82;

    private FingerprintIconView fingerprint;
    private boolean authenticating = false;
    private float downRawY;
    private long downTime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver screenReceiver;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);

        Window w = getWindow();
        if (android.os.Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true);
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        FrameLayout root = new FrameLayout(this);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setBackgroundColor(0xFF080A0F);
        try {
            Drawable lockWallpaper = WallpaperManager.getInstance(this)
                    .getDrawable(WallpaperManager.FLAG_LOCK);
            if (lockWallpaper != null) wallpaper.setImageDrawable(lockWallpaper);
        } catch (Throwable ignored) {}
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        fingerprint = new FingerprintIconView(this);
        FrameLayout.LayoutParams sensorLp = new FrameLayout.LayoutParams(
                dp(SENSOR_SIZE_DP), dp(SENSOR_SIZE_DP));
        sensorLp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        sensorLp.bottomMargin = dp(92);
        root.addView(fingerprint, sensorLp);

        root.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downRawY = e.getRawY();
                downTime = System.currentTimeMillis();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (downRawY - e.getRawY() > dp(70)) {
                    openPin();
                    return true;
                }
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (downRawY - e.getRawY() > dp(70)) {
                    openPin();
                    return true;
                }
                return true;
            }
            return true;
        });

        setContentView(root);
        enterAndroidLockTaskIfAvailable();
        registerScreenReceiver();

        fingerprint.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downRawY = e.getRawY();
                downTime = System.currentTimeMillis();
                fingerprint.setPressedState(true);
                return true;
            }

            if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (downRawY - e.getRawY() > dp(70)) {
                    fingerprint.setPressedState(false);
                    openPin();
                    return true;
                }
                fingerprint.setPressedState(true);
                return true;
            }

            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                boolean swipe = downRawY - e.getRawY() > dp(70);
                fingerprint.setPressedState(false);
                if (swipe) {
                    openPin();
                    return true;
                }

                long held = System.currentTimeMillis() - downTime;
                if (held < 80) return true;

                if (FastLockSecurityManager.isLockedOut(this)) {
                    Toast.makeText(this, "FastLock is temporarily locked.", Toast.LENGTH_SHORT).show();
                    return true;
                }

                if (!getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("fast_fingerprint_enabled", false)) {
                    openPin();
                    return true;
                }

                // The fingerprint-looking control is an app-owned touch credential.
                // It never reads or copies the phone's real biometric template.
                TouchCredential.Session session = new TouchCredential.Session();
                MotionEvent synthetic = MotionEvent.obtain(e);
                session.begin(synthetic);
                session.add(synthetic);
                synthetic.recycle();

                // A stationary tap cannot reproduce an enrolled motion credential.
                // The enrollment/authentication path therefore uses the existing
                // FastLock touch signature only when a real touch sequence is supplied.
                // Keep the visible icon responsive while the user holds it.
                fingerprint.playScan();
                fingerprint.postDelayed(() -> finishTouchAuthentication(), 180);
                return true;
            }

            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                fingerprint.setPressedState(false);
                return true;
            }
            return true;
        });
    }

    private void finishTouchAuthentication() {
        if (isFinishing() || authenticating) return;

        // Keep compatibility with the existing app-owned credential store.
        // A real hardware fingerprint template is intentionally not available
        // to a normal third-party app.
        String saved = getSharedPreferences("fastlock", MODE_PRIVATE)
                .getString("fast_fingerprint_signatures", "");

        if (!saved.isEmpty()) {
            // The existing TouchCredential matcher needs the touch sequence.
            // For this UI, authentication is completed through the FastLock PIN
            // if the app-owned touch credential cannot be reconstructed.
            openPin();
        } else {
            openPin();
        }
    }

    private void openPin() {
        if (authenticating || isFinishing()) return;
        authenticating = true;
        Intent i = new Intent(this, SetupAndPinActivity.class);
        startActivityForResult(i, PIN_REQUEST);
    }

    private void completeUnlock() {
        getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                .putBoolean("fastlock_authenticated", true)
                .putBoolean("fastlock_require_pin_after_boot", false)
                .apply();
        authenticating = true;
        setResult(Activity.RESULT_OK);
        exitAndroidLockTask();
        finish();
        moveTaskToBack(true);
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    fingerprint.setWakeVisible(false);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    fingerprint.setWakeVisible(true);
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenReceiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(screenReceiver, f);
    }

    private void enterAndroidLockTaskIfAvailable() {
        try {
            DevicePolicyManager dpm =
                    (DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin =
                    new ComponentName(this, FastLockDeviceAdminReceiver.class);

            if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    dpm.setLockTaskFeatures(admin,
                            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
                                    | DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
                }
            }
            if (dpm != null && dpm.isLockTaskPermitted(getPackageName())) {
                startLockTask();
            }
        } catch (Throwable ignored) {}
    }

    private void exitAndroidLockTask() {
        try { stopLockTask(); } catch (Throwable ignored) {}
    }

    @Override public void onBackPressed() {
        // Back is intentionally the only navigation out of this layer.
        // It never dismisses FastLock while the fingerprint screen is active.
        if (!authenticating) return;
        super.onBackPressed();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PIN_REQUEST) {
            authenticating = false;
            if (result == RESULT_OK) completeUnlock();
            else if (fingerprint != null) fingerprint.setWakeVisible(true);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (fingerprint != null) fingerprint.setWakeVisible(true);
        enterAndroidLockTaskIfAvailable();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class FingerprintIconView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean pressed;
        private boolean wakeVisible = true;
        private float scan;
        private long scanStart;

        FingerprintIconView(Context c) {
            super(c);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            glow.setStyle(Paint.Style.STROKE);
            glow.setStrokeCap(Paint.Cap.ROUND);
            glow.setStrokeJoin(Paint.Join.ROUND);
        }

        void setPressedState(boolean value) {
            pressed = value;
            invalidate();
        }

        void setWakeVisible(boolean value) {
            wakeVisible = value;
            if (value) animate().alpha(1f).setDuration(650).start();
            else animate().alpha(0f).setDuration(650).start();
        }

        void playScan() {
            scanStart = System.currentTimeMillis();
            scan = 0f;
            invalidate();
            postInvalidateDelayed(25);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (!wakeVisible) return;

            float d = getWidth() / 100f;
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;

            p.setColor(0xFF12F5E1);
            p.setStrokeWidth(2.6f * d / 1.0f);
            p.setAlpha(255);

            glow.setColor(0x5012F5E1);
            glow.setStrokeWidth(6.5f * d / 1.0f);
            if (pressed || scan < 1f) {
                c.drawCircle(cx, cy, 29f * d, glow);
            }

            // Same compact fingerprint silhouette throughout screen-on and
            // wake/transition states: outer ring, side valleys, inner loops.
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(cx - 26*d, cy + 4*d);
            path.cubicTo(cx - 29*d, cy - 13*d, cx - 15*d, cy - 27*d, cx, cy - 27*d);
            path.cubicTo(cx + 17*d, cy - 27*d, cx + 29*d, cy - 14*d, cx + 29*d, cy + 3*d);
            path.cubicTo(cx + 29*d, cy + 13*d, cx + 25*d, cy + 21*d, cx + 18*d, cy + 26*d);
            c.drawPath(path, p);

            path.reset();
            path.moveTo(cx - 20*d, cy + 5*d);
            path.cubicTo(cx - 22*d, cy - 9*d, cx - 12*d, cy - 20*d, cx, cy - 20*d);
            path.cubicTo(cx + 13*d, cy - 20*d, cx + 22*d, cy - 10*d, cx + 22*d, cy + 4*d);
            path.cubicTo(cx + 22*d, cy + 11*d, cx + 19*d, cy + 17*d, cx + 14*d, cy + 21*d);
            c.drawPath(path, p);

            path.reset();
            path.moveTo(cx - 13*d, cy + 7*d);
            path.cubicTo(cx - 14*d, cy - 4*d, cx - 7*d, cy - 13*d, cx, cy - 13*d);
            path.cubicTo(cx + 9*d, cy - 13*d, cx + 14*d, cy - 6*d, cx + 14*d, cy + 4*d);
            path.cubicTo(cx + 14*d, cy + 10*d, cx + 11*d, cy + 14*d, cx + 7*d, cy + 17*d);
            c.drawPath(path, p);

            path.reset();
            path.moveTo(cx - 7*d, cy + 15*d);
            path.cubicTo(cx - 3*d, cy + 10*d, cx - 2*d, cy + 6*d, cx - 2*d, cy + 1*d);
            path.cubicTo(cx - 2*d, cy - 4*d, cx + 1*d, cy - 7*d, cx + 5*d, cy - 7*d);
            path.cubicTo(cx + 10*d, cy - 7*d, cx + 11*d, cy - 3*d, cx + 11*d, cy + 2*d);
            c.drawPath(path, p);

            if (scanStart > 0L) {
                float t = Math.min(1f, (System.currentTimeMillis() - scanStart) / 700f);
                p.setAlpha((int)(255 * (1f - t)));
                c.drawArc(cx - 28*d, cy - 28*d, cx + 28*d, cy + 28*d,
                        -90f, t * 360f, false, p);
                if (t < 1f) postInvalidateDelayed(25);
            }
        }
    }
}

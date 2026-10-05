package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import android.net.Uri;
import java.io.InputStream;

public class LockLayerActivity extends Activity {
    private float downY;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        android.content.SharedPreferences prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        FrameLayout root = new FrameLayout(this);

        // Mirror the device's current Android wallpaper automatically.
        // FastLock does not replace or modify the system wallpaper.
        try {
            android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(this);
            Drawable currentWallpaper = wm.getDrawable();
            if (currentWallpaper != null) {
                root.setBackground(currentWallpaper);
            } else {
                root.setBackgroundColor(Color.TRANSPARENT);
            }
        } catch (Exception e) {
            root.setBackgroundColor(Color.TRANSPARENT);
        }

        TextView hint = new TextView(this);
        hint.setText("FastLock\nSwipe up to unlock");
        hint.setTextColor(0xDDFFFFFF);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);

        ImageView fingerprint = new ImageView(this);
        fingerprint.setImageResource(R.drawable.ic_fastlock);
        fingerprint.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fingerprint.setPadding(7,7,7,7);
        fingerprint.setContentDescription("FastLock unlock");

        GradientDrawable ring = new GradientDrawable();
        ring.setColor(0xCC080A0F);
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(1, 0xFFD8B35A);
        fingerprint.setBackground(ring);

        fingerprint.setOnClickListener(v -> openAuth());

        View.OnTouchListener swipe = (v,event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawY() - downY < -100f) openAuth();
                return true;
            }
            return true;
        };
        root.setOnTouchListener(swipe);

        fingerprint.setOnTouchListener((v,event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getRawY() - downY < -80f) openAuth();
                else v.performClick();
                return true;
            }
            return true;
        });

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                54,54,Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = 62;

        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                -2,-2,Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = 128;

        root.addView(hint,hp);
        root.addView(fingerprint,fp);
        setContentView(root);
    }

    private void openAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        android.content.SharedPreferences prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        if (prefs.getBoolean("fastlock_authenticated", false)) {
            prefs.edit().putBoolean("fastlock_authenticated", false).apply();
            finish();
        }
    }
}

package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.content.Intent;

public class LockLayerActivity extends Activity {
    private float downY;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);

        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.TRANSPARENT);

        TextView hint = new TextView(this);
        hint.setText("FastLock\nSwipe up for phone lock");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(8, 8, 8, 8);

        TextView button = new TextView(this);
        button.setText("⌾");
        button.setTextColor(Color.WHITE);
        button.setTextSize(27);
        button.setGravity(Gravity.CENTER);

        GradientDrawable circle = new GradientDrawable();
        circle.setColor(0xEE171A21);
        circle.setShape(GradientDrawable.OVAL);
        circle.setStroke(2, 0xFFD8B35A);
        button.setBackground(circle);

        button.setOnClickListener(v ->
                startActivity(new Intent(this, AuthActivity.class))
        );

        View.OnTouchListener swipe = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getRawY() - downY;
                if (dy < -120f) {
                    // Only FastLock closes. Android Keyguard stays independent underneath.
                    finish();
                    return true;
                }
            }
            return false;
        };

        root.setOnTouchListener(swipe);

        button.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY = event.getRawY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getRawY() - downY;
                if (dy < -120f) {
                    finish();
                } else {
                    v.performClick();
                }
                return true;
            }
            return true;
        });

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                76, 76, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = 28;

        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                -2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = 108;

        root.addView(hint, hp);
        root.addView(button, bp);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();

        android.content.SharedPreferences prefs =
                getSharedPreferences("fastlock", MODE_PRIVATE);

        // Successful FastLock authentication closes this layer completely.
        // If Android is still locked, its own PIN/pattern/password screen remains.
        // If Android is not locked, the normal phone screen is revealed.
        if (prefs.getBoolean("fastlock_authenticated", false)) {
            prefs.edit().putBoolean("fastlock_authenticated", false).apply();
            finish();
        }
    }
}

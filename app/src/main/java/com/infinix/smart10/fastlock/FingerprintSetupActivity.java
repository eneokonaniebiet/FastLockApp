package com.infinix.smart10.fastlock;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.FragmentActivity;

public class FingerprintSetupActivity extends FragmentActivity {
    private android.content.SharedPreferences prefs;
    private TextView status;
    private TouchEnrollView enrollView;
    private int fingerCount;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        fingerCount = countSaved();
        buildUi();
    }

    private int countSaved() {
        String s = prefs.getString("fast_fingerprint_signatures", "");
        if (s.isEmpty()) return 0;
        return s.split(",").length;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding(28, 40, 28, 28);
        root.setBackgroundColor(0xFF080A0F);

        TextView title = new TextView(this);
        title.setText("FastLock Fingerprint");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setGravity(17);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(0xFFB9BEC8);
        status.setTextSize(15);
        status.setGravity(17);
        status.setPadding(0, 18, 0, 18);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        enrollView = new TouchEnrollView();
        root.addView(enrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView note = new TextView(this);
        note.setText("FastLock uses an app-owned touch credential. Android does not expose a real fingerprint template to this app.");
        note.setTextColor(0xFF777D88);
        note.setTextSize(13);
        note.setGravity(17);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        Button clear = new Button(this);
        clear.setText("Clear all FastLock fingerprints");
        clear.setEnabled(fingerCount > 0);
        clear.setOnClickListener(v -> {
            prefs.edit().remove("fast_fingerprint_signatures").putBoolean(
                    "fast_fingerprint_enabled", false).apply();
            fingerCount = 0;
            updateStatus();
            clear.setEnabled(false);
            Toast.makeText(this, "FastLock fingerprints cleared",
                    Toast.LENGTH_SHORT).show();
        });
        root.addView(clear, new LinearLayout.LayoutParams(-1, -2));

        updateStatus();
        setContentView(root);
    }

    private void updateStatus() {
        status.setText("Saved FastLock fingers: " + fingerCount
                + "/5\nPress and hold the sensor, keeping your finger in one place.");
    }

    private void saveSignature(String sig) {
        if (sig.isEmpty()) {
            Toast.makeText(this,
                    "Hold the sensor a little longer and release normally.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String old = prefs.getString("fast_fingerprint_signatures", "");
        String next = old.isEmpty() ? sig : old + "," + sig;
        String[] all = next.split(",");

        if (all.length > 5) {
            Toast.makeText(this, "Maximum of 5 fingers reached.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        prefs.edit()
                .putString("fast_fingerprint_signatures", next)
                .putBoolean("fast_fingerprint_enabled", true)
                .apply();

        fingerCount = all.length;
        updateStatus();
        Toast.makeText(this, "FastLock finger " + fingerCount + " saved",
                Toast.LENGTH_SHORT).show();
    }

    private class TouchEnrollView extends View {
        Paint sensor = new Paint(1);
        Paint progress = new Paint(1);
        TouchCredential.Session session = new TouchCredential.Session();
        boolean active = false;
        long start;

        TouchEnrollView() {
            super(FingerprintSetupActivity.this);
            sensor.setStyle(Paint.Style.STROKE);
            sensor.setStrokeWidth(7f);
            sensor.setColor(0xFFD6B75C);
            progress.setStyle(Paint.Style.STROKE);
            progress.setStrokeWidth(8f);
            progress.setColor(0xFFB99A45);
            setFocusable(true);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float left = cx - 105f, top = cy - 105f;
            float right = cx + 105f, bottom = cy + 105f;
            float radius = 48f;

            c.drawRoundRect(left, top, right, bottom, radius, radius, sensor);

            float p = active ? Math.min(1f,
                    (System.currentTimeMillis() - start) / 1400f) : 0f;
            c.drawArc(left, top, right, bottom, -90f, p * 360f,
                    false, progress);

            Paint t = new Paint(1);
            t.setColor(Color.WHITE);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTextSize(18);
            c.drawText(active ? "Keep holding" : "Touch to enroll",
                    cx, cy + 8, t);

            if (active) postInvalidateDelayed(30);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                active = true;
                start = System.currentTimeMillis();
                session.begin(e);
                invalidate();
                return true;
            }

            if (active && (e.getActionMasked() == MotionEvent.ACTION_MOVE
                    || e.getActionMasked() == MotionEvent.ACTION_UP)) {
                session.add(e);

                if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    String sig = session.finish();
                    active = false;
                    invalidate();
                    saveSignature(sig);
                } else {
                    invalidate();
                }
                return true;
            }

            if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                active = false;
                invalidate();
                return true;
            }

            return true;
        }
    }
}

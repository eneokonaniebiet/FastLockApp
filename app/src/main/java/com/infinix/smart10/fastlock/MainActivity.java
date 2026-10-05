package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQ = 4101;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 60, 48, 40);
        root.setBackgroundColor(0xFF080A0F);

        TextView title = new TextView(this);
        title.setText("FastLockApp");
        title.setTextColor(0xFFF2F2F2);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView status = new TextView(this);
        status.setText("\nApp-owned lock layer\n\n" +
                "Passcode: " + (prefs.contains("passcode_hash") ? "Set" : "Not set") +
                "\nFingerprint: " + (prefs.getBoolean("biometric_enabled", false) ? "Enabled" : "Not enabled") +
                "\n\nYour phone's normal lock screen is untouched.");
        status.setTextColor(0xFFB9BEC8);
        status.setTextSize(16);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));

        Button setup = new Button(this);
        setup.setText("Set / change passcode");
        setup.setOnClickListener(v -> showPasscodeDialog());
        root.addView(setup, new LinearLayout.LayoutParams(-1, -2));

        Button fingerprint = new Button(this);
        fingerprint.setText(prefs.getBoolean("biometric_enabled", false)
                ? "Fingerprint enabled"
                : "Enable fingerprint");
        fingerprint.setOnClickListener(v -> {
            prefs.edit().putBoolean("biometric_enabled", true).apply();
            Toast.makeText(this, "Fingerprint authentication enabled for FastLockApp", Toast.LENGTH_SHORT).show();
            buildUi();
        });
        root.addView(fingerprint, new LinearLayout.LayoutParams(-1, -2));

        Button activate = new Button(this);
        activate.setText(prefs.getBoolean("active", false) ? "Deactivate lock layer" : "Activate lock layer");
        activate.setOnClickListener(v -> {
            if (!prefs.contains("passcode_hash")) {
                Toast.makeText(this, "Set your passcode first", Toast.LENGTH_SHORT).show();
                showPasscodeDialog();
                return;
            }
            boolean active = !prefs.getBoolean("active", false);
            prefs.edit().putBoolean("active", active).apply();
            if (active) {
                if (!Settings.canDrawOverlays(this)) {
                    startActivityForResult(new Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())), OVERLAY_REQ);
                    return;
                }
                startLayer();
            } else {
                stopService(new Intent(this, FastOverlayService.class));
            }
            buildUi();
        });
        root.addView(activate, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("\nFingerprint is verified by Android and used only to unlock this app layer.");
        note.setTextColor(0xFF777D88);
        note.setGravity(Gravity.CENTER);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private void showPasscodeDialog() {
        final EditText input = new EditText(this);
        input.setHint("4–12 digit passcode");
        input.setInputType(2);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF777D88);
        new AlertDialog.Builder(this)
                .setTitle("FastLockApp passcode")
                .setMessage("Create a passcode used only by the FastLockApp layer.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String value = input.getText().toString();
                    if (value.length() < 4 || value.length() > 12) {
                        Toast.makeText(this, "Use 4–12 digits", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    android.content.SharedPreferences.Editor e = prefs.edit();
                    String salt = java.util.UUID.randomUUID().toString();
                    e.putString("passcode_salt", salt);
                    e.putString("passcode_hash", hash(value, salt));
                    e.apply();
                    Toast.makeText(this, "Passcode saved", Toast.LENGTH_SHORT).show();
                    buildUi();
                }).show();
    }

    private String hash(String value, String salt) {
        try {
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                    value.toCharArray(), salt.getBytes(java.nio.charset.StandardCharsets.UTF_8), 120000, 256);
            byte[] out = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to protect passcode", e);
        }
    }

    private void startLayer() {
        Intent i = new Intent(this, FastOverlayService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    @Override protected void onActivityResult(int r, int c, Intent d) {
        super.onActivityResult(r, c, d);
        if (r == OVERLAY_REQ && Settings.canDrawOverlays(this)
                && prefs.getBoolean("active", false)) startLayer();
        buildUi();
    }
}

package com.infinix.smart10.fastlock;

import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.widget.*;

public class AuthActivity extends FragmentActivity {
    private android.content.SharedPreferences prefs;
    private EditText pass;
    private LinearLayout pinArea;
    private float downY;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        prefs = getSharedPreferences("fastlock", MODE_PRIVATE);
        buildUi();
    }

    private void completeFastLockUnlock() {
        prefs.edit().putBoolean("fastlock_authenticated", true).apply();
        setResult(RESULT_OK);
        finish();
    }

    private void buildUi() {
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(0xF9080A0F);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(36, 32, 36, 28);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF20B0E14);
        bg.setCornerRadius(36);
        box.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("FastLock");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("\nUse your FastLock Fingerprint\n\nSwipe up for FastLock PIN");
        sub.setTextColor(0xFFB9BEC8);
        sub.setGravity(Gravity.CENTER);

        ImageButton fingerprint = new ImageButton(this);
        fingerprint.setImageResource(R.drawable.ic_fastlock);
        fingerprint.setContentDescription("Use FastLock Fingerprint");
        fingerprint.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        fingerprint.setPadding(18,18,18,18);
        GradientDrawable fpbg = new GradientDrawable();
        fpbg.setColor(0xFF11151D);
        fpbg.setShape(GradientDrawable.OVAL);
        fpbg.setStroke(2,0xFFD8B35A);
        fingerprint.setBackground(fpbg);

        fingerprint.setOnClickListener(v -> verifyFingerprint());

        pinArea = new LinearLayout(this);
        pinArea.setOrientation(LinearLayout.VERTICAL);
        pinArea.setGravity(Gravity.CENTER);
        pinArea.setVisibility(View.GONE);

        pass = new EditText(this);
        pass.setHint("FastLock PIN");
        pass.setInputType(2);
        pass.setTextColor(Color.WHITE);
        pass.setHintTextColor(0xFF777D88);
        pass.setGravity(Gravity.CENTER);

        Button unlock = new Button(this);
        unlock.setText("Unlock with FastLock PIN");
        unlock.setOnClickListener(v -> {
            if (checkPasscode(pass.getText().toString())) completeFastLockUnlock();
            else {
                pass.setError("Incorrect FastLock PIN");
                pass.setText("");
            }
        });

        pinArea.addView(pass,new LinearLayout.LayoutParams(-1,-2));
        pinArea.addView(unlock,new LinearLayout.LayoutParams(-1,-2));

        box.addView(title);
        box.addView(sub);
        box.addView(fingerprint,new LinearLayout.LayoutParams(62,62));
        box.addView(pinArea,new LinearLayout.LayoutParams(-1,-2));

        View.OnTouchListener swipe = (v,event) -> {
            if (event.getAction()==MotionEvent.ACTION_DOWN) {
                downY=event.getRawY();
                return true;
            }
            if (event.getAction()==MotionEvent.ACTION_UP) {
                if (event.getRawY()-downY < -80f) showPin();
                return true;
            }
            return true;
        };
        screen.setOnTouchListener(swipe);
        box.setOnTouchListener(swipe);

        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        bp.leftMargin=28; bp.rightMargin=28;
        screen.addView(box,bp);
        setContentView(screen);
    }

    private void showPin() {
        if (pinArea.getVisibility()==View.VISIBLE) return;
        pinArea.setVisibility(View.VISIBLE);
        AlphaAnimation a = new AlphaAnimation(0f,1f);
        a.setDuration(180);
        pinArea.startAnimation(a);
        pass.requestFocus();
    }

    private void verifyFingerprint() {
        Intent i = new Intent(this,FingerprintSetupActivity.class);
        i.putExtra("verify",true);
        startActivityForResult(i,77);
    }

    private boolean checkPasscode(String value) {
        try {
            String salt=prefs.getString("passcode_salt","");
            String expected=prefs.getString("passcode_hash","");
            if(salt.isEmpty()||expected.isEmpty()) return false;
            javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec(
                    value.toCharArray(),salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),120000,256);
            byte[] out=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            String actual=android.util.Base64.encodeToString(out,android.util.Base64.NO_WRAP);
            return java.security.MessageDigest.isEqual(
                    actual.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch(Exception e){ return false; }
    }

    @Override protected void onActivityResult(int r,int c,Intent d) {
        super.onActivityResult(r,c,d);
        if(r==77 && c==RESULT_OK) completeFastLockUnlock();
    }
}

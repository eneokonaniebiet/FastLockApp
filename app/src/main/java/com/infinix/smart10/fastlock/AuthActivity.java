package com.infinix.smart10.fastlock;

import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import android.view.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.widget.*;

public class AuthActivity extends FragmentActivity {
    private android.content.SharedPreferences prefs;
    private EditText pass;
    private boolean pinOnly;
    private float downY;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs=getSharedPreferences("fastlock",MODE_PRIVATE);
        pinOnly=getIntent().getBooleanExtra("pin_only",false);
        buildUi();
    }

    private void completeFastLockUnlock(){
        prefs.edit().putBoolean("fastlock_authenticated",true).apply();
        setResult(RESULT_OK);
        finish();
    }

    private void buildUi(){
        FrameLayout screen=new FrameLayout(this);
        screen.setBackgroundColor(0xF9080A0F);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(36,32,36,28);

        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xF20B0E14);
        bg.setCornerRadius(34);
        box.setBackground(bg);

        TextView title=new TextView(this);
        title.setText("Enter FastLock PIN");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);

        TextView sub=new TextView(this);
        sub.setText("\nUse the PIN you created for FastLock.");
        sub.setTextColor(0xFFB9BEC8);
        sub.setGravity(Gravity.CENTER);

        pass=new EditText(this);
        pass.setHint("PIN");
        pass.setInputType(2);
        pass.setTextColor(Color.WHITE);
        pass.setHintTextColor(0xFF777D88);
        pass.setGravity(Gravity.CENTER);
        pass.setTextSize(22);

        Button unlock=new Button(this);
        unlock.setText("Unlock");
        unlock.setOnClickListener(v->{
            if(checkPasscode(pass.getText().toString())) completeFastLockUnlock();
            else { pass.setError("Incorrect PIN"); pass.setText(""); }
        });

        box.addView(title);
        box.addView(sub);
        box.addView(pass,new LinearLayout.LayoutParams(-1,-2));
        box.addView(unlock,new LinearLayout.LayoutParams(-1,-2));

        View.OnTouchListener swipe=(v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downY=e.getRawY();return true;}
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                if(e.getRawY()-downY>90f && !pinOnly){ finish(); }
                return true;
            }
            return true;
        };
        screen.setOnTouchListener(swipe);

        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        bp.leftMargin=28;bp.rightMargin=28;
        screen.addView(box,bp);
        setContentView(screen);
        pass.requestFocus();
    }

    private boolean checkPasscode(String value){
        try{
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
        }catch(Exception e){return false;}
    }
}

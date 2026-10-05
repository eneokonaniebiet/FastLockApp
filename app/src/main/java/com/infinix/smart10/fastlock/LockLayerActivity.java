package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import android.content.Intent;

public class LockLayerActivity extends Activity {
    private float downY;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xFF080A0F);
        try {
            Drawable wallpaper=android.app.WallpaperManager.getInstance(this).getDrawable();
            if(wallpaper!=null) root.setBackground(wallpaper);
        } catch(Exception ignored) {}

        View scrim=new View(this);
        scrim.setBackgroundColor(0x88000000);
        root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout center=new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(28,28,28,28);

        TextView title=new TextView(this);
        title.setText("FastLock");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        center.addView(title);

        TextView instruction=new TextView(this);
        instruction.setText("\nTouch the fingerprint area to unlock\n\nSwipe up for PIN");
        instruction.setTextColor(0xE6FFFFFF);
        instruction.setTextSize(14);
        instruction.setGravity(Gravity.CENTER);
        center.addView(instruction);

        ImageView fingerprint=new ImageView(this);
        fingerprint.setImageResource(R.drawable.ic_fastlock);
        fingerprint.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fingerprint.setPadding(28,28,28,28);
        fingerprint.setContentDescription("FastLock fingerprint unlock");
        center.addView(fingerprint,new LinearLayout.LayoutParams(150,150));

        TextView swipe=new TextView(this);
        swipe.setText("↑");
        swipe.setTextColor(0xCCFFFFFF);
        swipe.setTextSize(28);
        swipe.setGravity(Gravity.CENTER);
        center.addView(swipe);

        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
        root.addView(center,cp);

        root.setOnTouchListener(this::handleSwipe);
        fingerprint.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_UP){
                v.performClick();
                openFingerprintVerification();
            }
            return true;
        });

        setContentView(root);
    }

    private boolean handleSwipe(View v,MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            downY=e.getRawY();
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP){
            if(e.getRawY()-downY < -90f) openPin();
            return true;
        }
        return true;
    }

    private void openFingerprintVerification(){
        Intent i=new Intent(this,FingerprintSetupActivity.class);
        i.putExtra("verify",true);
        startActivityForResult(i,77);
    }

    private void openPin(){
        Intent i=new Intent(this,AuthActivity.class);
        i.putExtra("pin_only",true);
        startActivityForResult(i,78);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if((r==77||r==78)&&c==RESULT_OK){
            getSharedPreferences("fastlock",MODE_PRIVATE).edit().putBoolean("fastlock_authenticated",true).apply();
            finish();
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(getSharedPreferences("fastlock",MODE_PRIVATE).getBoolean("fastlock_authenticated",false)){
            getSharedPreferences("fastlock",MODE_PRIVATE).edit().putBoolean("fastlock_authenticated",false).apply();
            finish();
        }
    }
}

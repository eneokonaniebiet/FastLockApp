package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import androidx.fragment.app.FragmentActivity;

public class LockLayerActivity extends FragmentActivity {
    private TouchUnlockView fingerprint;
    private boolean authenticating = false;
    private final android.os.Handler guardHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastFingerprintCheck = () -> {
        if(fingerprint!=null) fingerprint.tryFastUnlock();
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if(android.os.Build.VERSION.SDK_INT>=27){
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Keep Android's real status bar visible so time, battery and connectivity remain visible.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root=new FrameLayout(this);
        try{
            android.graphics.drawable.Drawable wallpaper=
                    android.app.WallpaperManager.getInstance(this).getDrawable(android.app.WallpaperManager.FLAG_LOCK);
            root.setBackground(wallpaper!=null?wallpaper:color(0xFF080A0F));
        }catch(Exception e){root.setBackgroundColor(0xFF080A0F);}

        View scrim=new View(this);
        scrim.setBackgroundColor(0x33000000);
        root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(28,16,28,78);

        TextView title=new TextView(this);
        title.setText("FastLock");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(17);
        bottom.addView(title);

        TextView hint=new TextView(this);
        hint.setText("\nPlace your enrolled finger on the FastLock sensor\nSwipe up from the button for FastLock PIN");
        hint.setTextColor(0xEEFFFFFF);
        hint.setTextSize(14);
        hint.setGravity(17);
        bottom.addView(hint);

        fingerprint=new TouchUnlockView();
        bottom.addView(fingerprint,new LinearLayout.LayoutParams(210,210));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));

        // Do not put a touch listener on the full root. That could steal the
        // fingerprint button's MotionEvents. Swipe-up is handled by the button itself.
        setContentView(root);
        wakePulseUntil=System.currentTimeMillis()+900L;
        fingerprint.postInvalidateDelayed(40);
    }

    private android.graphics.drawable.ColorDrawable color(int c){
        return new android.graphics.drawable.ColorDrawable(c);
    }

    private void openPin(){
        authenticating = true;
        startActivityForResult(
                new Intent(this,AuthActivity.class).putExtra("pin_only",true),78);
    }

    private void completeUnlock(){
        getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putBoolean("fastlock_authenticated",true).apply();
        authenticating = true;

        // After successful FastLock authentication, show the normal app/launcher
        // screen instead of returning to the exact screen that was underneath.
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);

        setResult(RESULT_OK);
        finish();
    }

    @Override public void onBackPressed(){
        // Back must never dismiss/bypass the FastLock layer.
        if(!authenticating) return;
        super.onBackPressed();
    }

    @Override protected void onUserLeaveHint(){
        super.onUserLeaveHint();
        // Home/gesture navigation must not leave an unauthenticated FastLock
        // session sitting behind the launcher. Bring the layer back.
        if(!authenticating && getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)){
            guardHandler.postDelayed(() -> {
                if(!isFinishing() && !authenticating)
                    bringLayerBack();
            },35);
        }
    }

    @Override protected void onPause(){
        super.onPause();
        if(!authenticating && getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)){
            guardHandler.postDelayed(() -> {
                if(!authenticating && getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)){
                    bringLayerBack();
                }
            },50);
        }
    }

    private void bringLayerBack(){
        try{
            Intent i=new Intent(this,LockLayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                    Intent.FLAG_ACTIVITY_SINGLE_TOP|
                    Intent.FLAG_ACTIVITY_CLEAR_TOP|
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        }catch(Exception ignored){}
    }

    @Override protected void onDestroy(){
        guardHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==78){
            if(c==RESULT_OK) completeUnlock();
            else authenticating=false;
        }
    }

    private class TouchUnlockView extends View {
        Paint ring=new Paint(1), progress=new Paint(1), text=new Paint(1);
        TouchCredential.Session session=new TouchCredential.Session();
        boolean active=false;
        boolean unlocked=false;
        long start;
        long wakePulseUntil;
        float downRawY;

        TouchUnlockView(){
            super(LockLayerActivity.this);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(12);
            ring.setColor(0xFF444B58);
            progress.setStyle(Paint.Style.STROKE);
            progress.setStrokeWidth(12);
            progress.setColor(0xFFB99A45);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(16);
            setClickable(true);
        }

        protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,
                    r=Math.min(getWidth(),getHeight())*.34f;
            c.drawCircle(cx,cy,r,ring);
            long now=System.currentTimeMillis();
            boolean wakePulse=now<wakePulseUntil;
            float p=active?Math.min(1f,(now-start)/1200f):0f;
            if(wakePulse){
                float pulse=.55f + .45f*(float)Math.sin((now%500)/500f*Math.PI);
                progress.setColor(0xFFB99A45);
                progress.setStrokeWidth(12f + 5f*pulse);
                c.drawCircle(cx,cy,r + 3f*pulse,progress);
                postInvalidateDelayed(40);
            }
            c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,p*360,false,progress);
            c.drawText("FastLock",cx,cy+6,text);
            if(active) postInvalidateDelayed(30);
        }

        public boolean onTouchEvent(MotionEvent e){
            final int action=e.getActionMasked();

            if(action==MotionEvent.ACTION_DOWN){
                downRawY=e.getRawY();

                if(!getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("fast_fingerprint_enabled",false)){
                    openPin();
                    return true;
                }

                active=true;
                start=System.currentTimeMillis();
                session.begin(e);
                unlocked=false;
                guardHandler.postDelayed(fastFingerprintCheck,90);
                invalidate();
                return true;
            }

            if(action==MotionEvent.ACTION_MOVE && active){
                // A deliberate upward drag on the FastLock button opens the PIN.
                if(downRawY-e.getRawY()>90f){
                    active=false;
                    invalidate();
                    openPin();
                    return true;
                }
                session.add(e);
                invalidate();
                return true;
            }

            if(action==MotionEvent.ACTION_UP && active){
                session.add(e);
                long held=System.currentTimeMillis()-start;

                if(held<90){
                    active=false;
                    invalidate();
                    Toast.makeText(LockLayerActivity.this,
                            "Touch the FastLock sensor.",Toast.LENGTH_SHORT).show();
                    return true;
                }

                String candidate=session.finish();
                active=false;
                invalidate();

                String saved=getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getString("fast_fingerprint_signatures","");
                if(TouchCredential.matchesAny(candidate,saved)){
                    completeUnlock();
                }else{
                    Toast.makeText(LockLayerActivity.this,
                            "Finger not verified",Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            
            void tryFastUnlock(){
                if(!active || unlocked || authenticating) return;
                long held=System.currentTimeMillis()-start;
                if(held<90) return;
                String candidate=session.finish();
                String saved=getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getString("fast_fingerprint_signatures","");
                if(TouchCredential.matchesAny(candidate,saved)){
                    unlocked=true;
                    active=false;
                    invalidate();
                    guardHandler.removeCallbacks(fastFingerprintCheck);
                    completeUnlock();
                }
            }
        }
    }
}

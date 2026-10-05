package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.content.Intent;
import androidx.fragment.app.FragmentActivity;

public class LockLayerActivity extends FragmentActivity {
    private static final int PIN_REQUEST=78;
    private static final int MAX_FAILED_TOUCHES=10;
    private TouchUnlockView fingerprint;
    private boolean authenticating=false;
    private final android.os.Handler guardHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable fastFingerprintCheck=()->{ if(fingerprint!=null) fingerprint.tryFastUnlock(); };
    private long wakePulseUntil;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(android.os.Build.VERSION.SDK_INT>=27){
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root=new FrameLayout(this);
        try{
            android.graphics.drawable.Drawable wallpaper=
                    android.app.WallpaperManager.getInstance(this)
                    .getDrawable(android.app.WallpaperManager.FLAG_LOCK);
            root.setBackground(wallpaper!=null?wallpaper:color(0xFF080A0F));
        }catch(Exception e){root.setBackgroundColor(0xFF080A0F);}

        fingerprint=new TouchUnlockView();
        FrameLayout.LayoutParams sensorLp=new FrameLayout.LayoutParams(210,210);
        sensorLp.gravity=Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM;
        sensorLp.bottomMargin=92;
        root.addView(fingerprint,sensorLp);
        setContentView(root);

        startWakePulse();
        try{
            android.media.ToneGenerator tone=new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION,70);
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP,90);
            fingerprint.postDelayed(tone::release,180);
        }catch(Exception ignored){}
    }

    private void startWakePulse(){
        wakePulseUntil=System.currentTimeMillis()+7000L;
        if(fingerprint!=null){
            fingerprint.setVisibility(View.VISIBLE);
            fingerprint.postInvalidateDelayed(16);
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(fingerprint!=null && !authenticating) startWakePulse();
    }

    private android.graphics.drawable.ColorDrawable color(int c){
        return new android.graphics.drawable.ColorDrawable(c);
    }

    private boolean isTouchLocked(){
        return getSharedPreferences("fastlock",MODE_PRIVATE)
                .getInt("fastlock_failed_touches",0)>=MAX_FAILED_TOUCHES;
    }

    private void resetTouchFailures(){
        getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putInt("fastlock_failed_touches",0).apply();
    }

    private void registerFailedTouch(){
        int n=getSharedPreferences("fastlock",MODE_PRIVATE)
                .getInt("fastlock_failed_touches",0)+1;
        getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putInt("fastlock_failed_touches",n).apply();
    }

    private void openPin(){
        authenticating=true;
        startActivityForResult(
                new Intent(this,AuthActivity.class).putExtra("pin_only",true),PIN_REQUEST);
    }

    private void completeUnlock(){
        getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putBoolean("fastlock_authenticated",true)
                .putInt("fastlock_failed_touches",0).apply();
        authenticating=true;
        setResult(RESULT_OK);
        finish();
    }

    @Override public void onBackPressed(){
        if(!authenticating) return;
        super.onBackPressed();
    }

    @Override protected void onUserLeaveHint(){
        super.onUserLeaveHint();
        if(!authenticating && getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)){
            guardHandler.postDelayed(()->{
                if(!isFinishing()&&!authenticating) bringLayerBack();
            },35);
        }
    }

    @Override protected void onPause(){
        super.onPause();
        if(!authenticating && getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)){
            guardHandler.postDelayed(()->{
                if(!authenticating&&getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)) bringLayerBack();
            },50);
        }
    }

    private void bringLayerBack(){
        try{
            Intent i=new Intent(this,LockLayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|
                    Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        }catch(Exception ignored){}
    }

    @Override protected void onDestroy(){
        guardHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==PIN_REQUEST){
            if(c==RESULT_OK){
                resetTouchFailures();
                completeUnlock();
            }else{
                authenticating=false;
                if(fingerprint!=null) fingerprint.invalidate();
            }
        }
    }

    private class TouchUnlockView extends View {
        Paint ring=new Paint(1),progress=new Paint(1);
        TouchCredential.Session session=new TouchCredential.Session();
        boolean active=false,unlocked=false;
        long start;
        float downRawY;

        TouchUnlockView(){
            super(LockLayerActivity.this);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(12);
            ring.setColor(0xFF444B58);
            progress.setStyle(Paint.Style.STROKE);
            progress.setStrokeWidth(12);
            progress.setColor(0xFFB99A45);
            setClickable(true);
        }

        protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f;
            float r=Math.min(getWidth(),getHeight())*.34f;
            c.drawCircle(cx,cy,r,ring);
            long now=System.currentTimeMillis();
            boolean wakePulse=now<wakePulseUntil;
            boolean disabled=isTouchLocked();

            if(wakePulse||active){
                float pulse=.5f-.5f*(float)Math.cos((now%1200L)/1200f*(float)(Math.PI*2));
                int base=disabled?0x777777:0xB99A45;
                int alpha=(int)(55+200*pulse);
                progress.setColor((alpha<<24)|base);
                progress.setStrokeWidth(disabled?12f:10f+7f*pulse);
                c.drawCircle(cx,cy,r+4f*pulse,progress);
                postInvalidateDelayed(40);
            }
            if(active){
                float p=Math.min(1f,(now-start)/1200f);
                progress.setColor(0xFFB99A45);
                progress.setStrokeWidth(12f);
                c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,p*360,false,progress);
                postInvalidateDelayed(30);
            }
        }

        public boolean onTouchEvent(MotionEvent e){
            final int action=e.getActionMasked();

            if(action==MotionEvent.ACTION_DOWN){
                downRawY=e.getRawY();

                // Swipe-up remains the PIN escape route even when touch matching
                // has been temporarily disabled after repeated failures.
                if(isTouchLocked()){
                    active=true;
                    start=System.currentTimeMillis();
                    session.begin(e);
                    invalidate();
                    return true;
                }

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

            if(action==MotionEvent.ACTION_MOVE&&active){
                if(downRawY-e.getRawY()>90f){
                    active=false;
                    guardHandler.removeCallbacks(fastFingerprintCheck);
                    invalidate();
                    openPin();
                    return true;
                }
                session.add(e);
                invalidate();
                return true;
            }

            if(action==MotionEvent.ACTION_UP&&active){
                session.add(e);
                long held=System.currentTimeMillis()-start;
                active=false;
                guardHandler.removeCallbacks(fastFingerprintCheck);
                invalidate();

                if(downRawY-e.getRawY()>90f){
                    openPin();
                    return true;
                }

                if(held<90){
                    return true;
                }

                if(isTouchLocked()){
                    Toast.makeText(LockLayerActivity.this,
                            "FastLock touch is disabled. Swipe up for PIN.",
                            Toast.LENGTH_SHORT).show();
                    return true;
                }

                String candidate=session.finish();
                String saved=getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getString("fast_fingerprint_signatures","");
                if(TouchCredential.matchesAny(candidate,saved)){
                    completeUnlock();
                }else{
                    registerFailedTouch();
                    Toast.makeText(LockLayerActivity.this,
                            isTouchLocked()?
                            "Touch disabled. Swipe up for FastLock PIN.":
                            "Finger not verified",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return true;
        }

        void tryFastUnlock(){
            if(!active||unlocked||authenticating||isTouchLocked()) return;
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

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

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        if(android.os.Build.VERSION.SDK_INT>=27){
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON|
                WindowManager.LayoutParams.FLAG_FULLSCREEN|
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root=new FrameLayout(this);
        try{
            android.graphics.drawable.Drawable wallpaper=
                    android.app.WallpaperManager.getInstance(this).getDrawable();
            root.setBackground(wallpaper!=null?wallpaper:color(0xFF080A0F));
        }catch(Exception e){root.setBackgroundColor(0xFF080A0F);}

        View scrim=new View(this);
        scrim.setBackgroundColor(0x33000000);
        root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        bottom.setPadding(28,16,28,42);

        TextView title=new TextView(this);
        title.setText("FastLock");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(17);
        bottom.addView(title);

        TextView hint=new TextView(this);
        hint.setText("\nTouch and hold to use FastLock Fingerprint\nSwipe up from the button for FastLock PIN");
        hint.setTextColor(0xEEFFFFFF);
        hint.setTextSize(14);
        hint.setGravity(17);
        bottom.addView(hint);

        fingerprint=new TouchUnlockView();
        bottom.addView(fingerprint,new LinearLayout.LayoutParams(150,150));
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));

        // Do not put a touch listener on the full root. That could steal the
        // fingerprint button's MotionEvents. Swipe-up is handled by the button itself.
        setContentView(root);
    }

    private android.graphics.drawable.ColorDrawable color(int c){
        return new android.graphics.drawable.ColorDrawable(c);
    }

    private void openPin(){
        startActivityForResult(
                new Intent(this,AuthActivity.class).putExtra("pin_only",true),78);
    }

    private void completeUnlock(){
        getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putBoolean("fastlock_authenticated",true).apply();
        setResult(RESULT_OK);
        finish();
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==78 && c==RESULT_OK) completeUnlock();
    }

    private class TouchUnlockView extends View {
        Paint ring=new Paint(1), progress=new Paint(1), text=new Paint(1);
        TouchCredential.Session session=new TouchCredential.Session();
        boolean active=false;
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
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(16);
            setClickable(true);
        }

        protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,
                    r=Math.min(getWidth(),getHeight())*.34f;
            c.drawCircle(cx,cy,r,ring);
            float p=active?Math.min(1f,
                    (System.currentTimeMillis()-start)/1200f):0f;
            c.drawArc(cx-r,cy-r,cx+r,cy+r,-90,p*360,false,progress);
            c.drawText(active?"Keep holding":"FastLock",cx,cy+6,text);
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

                if(held<900){
                    active=false;
                    invalidate();
                    Toast.makeText(LockLayerActivity.this,
                            "Hold until the circle completes.",Toast.LENGTH_SHORT).show();
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
                            "FastLock fingerprint not recognized",Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            return true;
        }
    }
}

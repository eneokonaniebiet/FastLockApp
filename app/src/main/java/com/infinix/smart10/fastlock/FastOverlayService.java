package com.infinix.smart10.fastlock;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class FastOverlayService extends Service {
    private WindowManager wm;
    private View iconView;
    private BroadcastReceiver screenReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        startForeground(91, notification());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerScreenReceiver();
    }

    private Notification notification() {
        String channel = "fastlock";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    channel,"FastLockApp",NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(c);
        }
        return new Notification.Builder(this,channel)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("FastLockApp active")
                .setContentText("FastLock lock layer is active")
                .setOngoing(true)
                .build();
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    // Every new screen wake must require FastLock again.
                    getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                            .putBoolean("fastlock_authenticated",false).apply();
                    removeIcon();
                } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())
                        && getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)) {
                    getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                            .putBoolean("fastlock_authenticated",false).apply();
                    // Android can briefly delay background activity launches around
                    // the keyguard transition, so retry instead of giving up once.
                    scheduleLayerAttempts();
                }
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenReceiver,f,RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(screenReceiver,f);
    }

    private void scheduleLayerAttempts() {
        handler.removeCallbacksAndMessages(null);
        // Keep a lightweight guard running while FastLock is active. This is a
        // fallback for launcher/home navigation on Android versions that allow
        // the system gesture to briefly move another app to the foreground.
        handler.postDelayed(new Runnable(){
            @Override public void run(){
                if(getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)
                        && !getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("fastlock_authenticated",false)){
                    showLockLayer();
                }
                if(getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)){
                    handler.postDelayed(this,900);
                }
            }
        },180);

        for(int n=0;n<8;n++){
            final int attempt=n;
            handler.postDelayed(() -> {
                if (getSharedPreferences("fastlock",MODE_PRIVATE)
                        .getBoolean("active",false)) {
                    showLockLayer();
                }
            },120L + n*450L);
        }
    }

    private void showLockLayer() {
        if (!getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)) return;
        try {
            Intent i = new Intent(this,LockLayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {
            // The activity guard will retry while FastLock remains active.
        }
    }

    private int type() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams lp(int w,int h) {
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(
                w,h,type(),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;
        p.y=28;
        return p;
    }

    private void showIcon() {
        return;
        TextView icon=new TextView(this);
        icon.setText("⌾");
        icon.setTextColor(Color.WHITE);
        icon.setTextSize(27);
        icon.setGravity(Gravity.CENTER);

        GradientDrawable bg=new GradientDrawable();
        bg.setColor(0xEE171A21);
        bg.setShape(GradientDrawable.OVAL);
        bg.setStroke(2,0xFFD8B35A);
        icon.setBackground(bg);
        icon.setOnClickListener(v -> {
            removeIcon();
            showLockLayer();
        });

        iconView=icon;
        try {
            wm.addView(iconView,lp(76,76));
        } catch(Exception e) {
            iconView=null;
        }
    }

    private void removeIcon() {
        if(iconView!=null){
            try { wm.removeView(iconView); } catch(Exception ignored) {}
            iconView=null;
        }
    }

    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)) {
            scheduleLayerAttempts();
        }
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if(getSharedPreferences("fastlock",MODE_PRIVATE)
                .getBoolean("active",false)) {
            Intent restart=new Intent(getApplicationContext(),FastOverlayService.class);
            if(Build.VERSION.SDK_INT>=26)
                getApplicationContext().startForegroundService(restart);
            else
                getApplicationContext().startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        removeIcon();
        handler.removeCallbacksAndMessages(null);
        if(screenReceiver!=null){
            try { unregisterReceiver(screenReceiver); } catch(Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

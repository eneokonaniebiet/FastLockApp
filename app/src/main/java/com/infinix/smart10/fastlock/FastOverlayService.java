package com.infinix.smart10.fastlock;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;

public class FastOverlayService extends Service {
    private BroadcastReceiver screenReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean screenOn = true;

    @Override public void onCreate() {
        super.onCreate();
        startForeground(91, notification());
        screenOn = isDisplayOn();
        registerScreenReceiver();
    }

    private Notification notification() {
        String channel = "fastlock";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                    channel, "FastLockApp", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(c);
        }
        return new Notification.Builder(this, channel)
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
                    screenOn = false;
                    handler.removeCallbacksAndMessages(null);
                    getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                            .putBoolean("fastlock_authenticated", false).apply();
                } else if (Intent.ACTION_SCREEN_ON.equals(i.getAction())
                        && getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("active", false)) {
                    screenOn = true;
                    getSharedPreferences("fastlock", MODE_PRIVATE).edit()
                            .putBoolean("fastlock_authenticated", false).apply();
                    scheduleLayerAttempts();
                }
            }
        };

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33)
            registerReceiver(screenReceiver, f, RECEIVER_NOT_EXPORTED);
        else
            registerReceiver(screenReceiver, f);
    }

    private boolean isDisplayOn() {
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        return pm != null && pm.isInteractive();
    }

    private void scheduleLayerAttempts() {
        handler.removeCallbacksAndMessages(null);
        if (!screenOn || !isDisplayOn()) return;

        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!screenOn || !isDisplayOn()) return;

                android.content.SharedPreferences p =
                        getSharedPreferences("fastlock", MODE_PRIVATE);

                if (p.getBoolean("active", false)
                        && !p.getBoolean("fastlock_authenticated", false)) {
                    showLockLayer();
                    handler.postDelayed(this, 900);
                }
            }
        }, 50);

        for (int n = 0; n < 8; n++) {
            final int attempt = n;
            handler.postDelayed(() -> {
                if (screenOn && isDisplayOn()
                        && getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("active", false)
                        && !getSharedPreferences("fastlock", MODE_PRIVATE)
                        .getBoolean("fastlock_authenticated", false)) {
                    showLockLayer();
                }
            }, 120L + attempt * 450L);
        }
    }

    private void showLockLayer() {
        if (!screenOn || !isDisplayOn()) return;
        android.content.SharedPreferences p =
                getSharedPreferences("fastlock", MODE_PRIVATE);
        if (!p.getBoolean("active", false)
                || p.getBoolean("fastlock_authenticated", false)) return;

        try {
            Intent i = new Intent(this, LockLayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (getSharedPreferences("fastlock", MODE_PRIVATE)
                .getBoolean("active", false) && isDisplayOn()) {
            screenOn = true;
            scheduleLayerAttempts();
        }
        return START_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        if (getSharedPreferences("fastlock", MODE_PRIVATE)
                .getBoolean("active", false)) {
            Intent restart = new Intent(getApplicationContext(),
                    FastOverlayService.class);
            if (Build.VERSION.SDK_INT >= 26)
                getApplicationContext().startForegroundService(restart);
            else
                getApplicationContext().startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

package com.infinix.smart10.fastlock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.UserManager;

public class ScreenReceiver extends BroadcastReceiver {
    private static final String PREFS = "fastlock";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (action == null) return;

        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            // Direct-Boot phase: keep the fact that FastLock was active in
            // device-protected storage and require the FastLock PIN after
            // Android's own device credential has been cleared.
            Context dp = context.createDeviceProtectedStorageContext();
            boolean active = dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean("active", false);
            if (active) {
                dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean("fastlock_require_pin_after_boot", true)
                        .apply();
            }
            return;
        }

        if (Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Context dp = context.createDeviceProtectedStorageContext();
            boolean active = dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean("active", false);

            if (active) {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean("fastlock_authenticated", false)
                        .apply();

                Intent service = new Intent(context, FastOverlayService.class);
                try {
                    if (Build.VERSION.SDK_INT >= 26) {
                        context.startForegroundService(service);
                    } else {
                        context.startService(service);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}

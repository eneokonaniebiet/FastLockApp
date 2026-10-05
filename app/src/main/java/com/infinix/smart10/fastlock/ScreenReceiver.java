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
            // Direct-Boot storage is available before the user unlocks Android.
            // Record that the first FastLock authentication after reboot must
            // use the FastLock PIN.
            Context dp = context.createDeviceProtectedStorageContext();
            boolean active = dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean("active", false);
            if (active) {
                dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean("fastlock_require_pin_after_boot", true)
                        .putBoolean("fastlock_authenticated", false)
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
                boolean requirePin = dp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getBoolean("fastlock_require_pin_after_boot", false);

                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean("fastlock_authenticated", false)
                        .putBoolean("fastlock_require_pin_after_boot", requirePin)
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

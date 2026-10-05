package com.infinix.smart10.fastlock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * FastLock's device-policy component.
 *
 * Enabling this as a normal Device Admin is not enough to grant Lock Task
 * policy. Android requires the app to be a Device Owner/Profile Owner for
 * setLockTaskPackages/setLockTaskFeatures.
 */
public class FastLockDeviceAdminReceiver extends DeviceAdminReceiver {
    @Override
    public void onEnabled(Context context, Intent intent) {
        Toast.makeText(context, "FastLock Android admin enabled", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        Toast.makeText(context, "FastLock Android admin disabled", Toast.LENGTH_SHORT).show();
    }
}

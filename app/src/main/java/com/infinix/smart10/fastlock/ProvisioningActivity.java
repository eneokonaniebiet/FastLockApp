package com.infinix.smart10.fastlock;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

public class ProvisioningActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getAction();
        if (DevicePolicyManager.ACTION_GET_PROVISIONING_MODE.equals(action)) {
            Intent result = new Intent();
            result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
            setResult(RESULT_OK, result);
        } else if (DevicePolicyManager.ACTION_ADMIN_POLICY_COMPLIANCE.equals(action)) {
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }
}

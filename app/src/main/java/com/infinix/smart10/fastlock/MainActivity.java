package com.infinix.smart10.fastlock;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    private static final int OVERLAY_REQ=4101;
    private android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle s){super.onCreate(s);prefs=getSharedPreferences("fastlock",MODE_PRIVATE);migrateOldTouchCredential();
        if(prefs.getBoolean("active",false) && !prefs.getBoolean("fastlock_authenticated",false)){
            startActivity(new Intent(this,LockLayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
            return;
        }
        buildUi();}

    private void migrateOldTouchCredential(){
        String saved=prefs.getString("fast_fingerprint_signatures","");
        if(!saved.isEmpty() && !saved.trim().startsWith("v2;")){
            prefs.edit().remove("fast_fingerprint_signatures").putBoolean("fast_fingerprint_enabled",false).apply();
            Toast.makeText(this,"FastLock fingerprint updated. Please enroll it again.",Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,48,36,32); root.setBackgroundColor(0xFF080A0F);
        TextView title=new TextView(this); title.setText("FastLockApp"); title.setTextColor(0xFFF2F2F2); title.setTextSize(28); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView status=new TextView(this);
        status.setText("\nFastLock layer: "+(prefs.getBoolean("active",false)?"ACTIVE":"OFF")+
                "\n\nFastLock PIN: "+(prefs.contains("passcode_hash")?"Set":"Not set")+
                "\nFastLock fingerprints: "+fingerCount()+"/5"+
                "\n\nAuthentication is owned by FastLock. Android fingerprint enrollment is not used.");
        status.setTextColor(0xFFB9BEC8); status.setTextSize(16); status.setGravity(Gravity.CENTER); root.addView(status,new LinearLayout.LayoutParams(-1,0,1));

        TextView lockTaskStatus=new TextView(this);
        DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        boolean deviceOwner=dpm!=null && dpm.isDeviceOwnerApp(getPackageName());
        boolean adminActive=dpm!=null && dpm.isAdminActive(new ComponentName(this,FastLockDeviceAdminReceiver.class));
        lockTaskStatus.setText("\nAndroid Lock Task: "+(deviceOwner?"READY (Device Owner)":(adminActive?"ADMIN ENABLED — Device Owner still required":"NOT PROVISIONED"))+
                "\nHome/Overview blocking uses Android Lock Task when FastLock is Device Owner.");
        lockTaskStatus.setTextColor(0xFFB99A45); lockTaskStatus.setTextSize(14); lockTaskStatus.setGravity(Gravity.CENTER);
        root.addView(lockTaskStatus,new LinearLayout.LayoutParams(-1,-2));

        Button admin=new Button(this);
        admin.setText(adminActive?"Android Device Admin enabled":"Enable Android Device Admin");
        admin.setOnClickListener(v->enableAndroidAdmin());
        root.addView(admin,new LinearLayout.LayoutParams(-1,-2));

        Button setup=new Button(this); setup.setText("Set / change FastLock PIN"); setup.setOnClickListener(v->showPasscodeDialog()); root.addView(setup,new LinearLayout.LayoutParams(-1,-2));
        Button finger=new Button(this); finger.setText(fingerCount()>0?"Manage FastLock Fingerprints":"Set up FastLock Fingerprint");
        finger.setOnClickListener(v->startActivity(new Intent(this,FingerprintSetupActivity.class))); root.addView(finger,new LinearLayout.LayoutParams(-1,-2));
        Button activate=new Button(this); activate.setText(prefs.getBoolean("active",false)?"STOP FASTLOCK":"ACTIVATE FASTLOCK"); activate.setOnClickListener(v->toggleFastLock()); root.addView(activate,new LinearLayout.LayoutParams(-1,-2));

        TextView note=new TextView(this); note.setText("\nWhen active, FastLock presents its own full-screen layer over the current screen/wake state. The layer uses the current Android wallpaper automatically. Swipe up for the FastLock PIN. Successful authentication closes only the FastLock layer."); note.setTextColor(0xFF777D88); note.setGravity(Gravity.CENTER); root.addView(note,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
    }

    private int fingerCount(){
        String s=prefs.getString("fast_fingerprint_signatures","");
        return s.isEmpty()?0:s.split(",").length;
    }

    private void setActive(boolean active){
        prefs.edit().putBoolean("active",active).putBoolean("fastlock_authenticated",false).apply();
        getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putBoolean("active",active)
                .putBoolean("fastlock_authenticated",false)
                .apply();
    }

    private void toggleFastLock(){
        if(prefs.getBoolean("active",false)){
            setActive(false);
            stopService(new Intent(this,FastOverlayService.class));
            buildUi();
            return;
        }
        if(!prefs.contains("passcode_hash")){Toast.makeText(this,"Set your FastLock PIN first.",Toast.LENGTH_LONG).show();return;}
        if(!prefs.getBoolean("fast_fingerprint_enabled",false)){Toast.makeText(this,"Set up at least one FastLock fingerprint first.",Toast.LENGTH_LONG).show();return;}
        if(!Settings.canDrawOverlays(this)){startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),OVERLAY_REQ);return;}
        setActive(true);
        getApplicationContext().createDeviceProtectedStorageContext()
                .getSharedPreferences("fastlock",MODE_PRIVATE).edit()
                .putBoolean("fastlock_require_pin_after_boot",false).apply();
        startLayer();
        buildUi();
    }

    private void enableAndroidAdmin(){
        DevicePolicyManager dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName admin=new ComponentName(this,FastLockDeviceAdminReceiver.class);
        if(dpm!=null && dpm.isAdminActive(admin)){Toast.makeText(this,"FastLock Android admin is already enabled.",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"FastLock uses Android device policy for its optional Lock Task security mode.");
        startActivityForResult(i,4301);
    }

    private void showPasscodeDialog(){
        final EditText input=new EditText(this); input.setHint("4–12 digit FastLock PIN"); input.setInputType(2); input.setTextColor(0xFFFFFFFF); input.setHintTextColor(0xFF777D88);
        new AlertDialog.Builder(this).setTitle("Create FastLock PIN").setMessage("This PIN belongs only to FastLockApp.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String value=input.getText().toString();
            if(value.length()<4||value.length()>12){Toast.makeText(this,"Use 4–12 digits",Toast.LENGTH_SHORT).show();return;}
            String salt=java.util.UUID.randomUUID().toString();
            prefs.edit().putString("passcode_salt",salt).putString("passcode_hash",hash(value,salt)).apply();
            Toast.makeText(this,"FastLock PIN saved",Toast.LENGTH_SHORT).show();buildUi();
        }).show();
    }

    private String hash(String value,String salt){
        try{
            javax.crypto.spec.PBEKeySpec spec=new javax.crypto.spec.PBEKeySpec(value.toCharArray(),salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),120000,256);
            byte[] out=javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return android.util.Base64.encodeToString(out,android.util.Base64.NO_WRAP);
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private void startLayer(){
        Intent i=new Intent(this,FastOverlayService.class);
        if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==OVERLAY_REQ&&Settings.canDrawOverlays(this)&&prefs.getBoolean("active",false))startLayer();
        buildUi();
    }
}
